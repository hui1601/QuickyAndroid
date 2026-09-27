package com.hui1601.quickyandroid.ble

/**
 * Old-JL (JieLi, vendorId & 0xF0 == 0x70 / 0x200) ADV-opcode settings family,
 * ported byte-exactly from JLDeviceImpl + BtRcspOpImpl + ParseHelper:
 *
 *  - modifyAdvInfo(op, data)   — RCSP opcode 0xC0, LTV [len, op, data...]
 *  - getAdvInfo(mask)          — RCSP opcode 0xC1, mask BE32
 *  - ADV info responses (opcode 0xC1/0xC2) carry an LTV list parsed by
 *    ParseHelper.parseADVInfo (smali:1656-2082).
 *
 * Settings opcodes (JLDeviceImpl smali lines in comments):
 *   2 = key map  [side, clicks, jlFunc]      (setLeftClick1 smali:2611)
 *   5 = game mode [on=2/off=1 — INVERTED]    (setGameMode smali:2474)
 *   7 = time sync [epoch BE32]               (updateDeviceTime)
 *   8 = in-ear detect [on=1/off=2]           (setInEarCheck smali:2569)
 *   0xA = sleep mode [on=2/off=1 — INVERTED] (setSleepMode smali:3176)
 *   0xB = ANC NoiseMode [mode, 6, min/max/cur LE16] (setCurrentNoiseMode)
 *   0xC = volume balance [0-100]             (updateFunctionValue smali:1694)
 *
 * QCY key ids (1=L1,2=R1,3=L2,4=R2,5=L3,6=R3) map to [side, clicks] pairs;
 * QCY function id 0 ("none") maps to JL 0x7F (changeQCY2JL smali:386).
 */
object JlAdvProtocol {

    // ── requests ──────────────────────────────────────────────────────

    /** packLTVPacket (ParseDataUtil.smali:227): [data.len + 1, op, data...]. */
    fun modifyAdvLtv(op: Int, data: ByteArray): ByteArray =
        byteArrayOf((data.size + 1).toByte(), op.toByte()) + data

    /** getAdvInfo paramData: attribute mask, big-endian u32. */
    fun getAdvMask(mask: Int): ByteArray = byteArrayOf(
        ((mask shr 24) and 0xFF).toByte(),
        ((mask shr 16) and 0xFF).toByte(),
        ((mask shr 8) and 0xFF).toByte(),
        (mask and 0xFF).toByte()
    )

    const val MASK_BATTERY = 0xD21 // updateBattery / battery query
    const val MASK_ALL = -1 // updateSetting full read

    /** Old-JL game/sleep enable encoding is inverted (on=2, off=1). */
    fun invertedBool(on: Boolean): Byte = if (on) 0x02 else 0x01

    /** changeQCY2JL: function 0 ("none") becomes 0x7F; everything else passes. */
    fun funcToJl(funcId: Byte): Byte = if (funcId.toInt() == 0) 0x7F else funcId

    /** changeJL2QCY: 0x7F means "no change" (= none/0). */
    fun funcFromJl(jlFunc: Byte): Byte = if (jlFunc.toInt() and 0xFF == 0x7F) 0 else jlFunc

    /**
     * QCY key id (1..6) -> old-JL [side, clicks]: L1=(1,1) R1=(2,1)
     * L2=(1,2) R2=(2,2) L3=(1,3) R3=(2,3) (setLeftClick1/2 smali:2611/2679).
     * Other key ids (quad/long/call-mode) have no old-JL encoding — null.
     */
    fun keyIdToSideClicks(keyId: Int): Pair<Int, Int>? = when (keyId) {
        1 -> 1 to 1
        2 -> 2 to 1
        3 -> 1 to 2
        4 -> 2 to 2
        5 -> 1 to 3
        6 -> 2 to 3
        else -> null
    }

    /** Inverse of [keyIdToSideClicks]; null for unknown pairs. */
    fun sideClicksToKeyId(side: Int, clicks: Int): Byte? = when (side to clicks) {
        1 to 1 -> 0x01
        2 to 1 -> 0x02
        1 to 2 -> 0x03
        2 to 2 -> 0x04
        1 to 3 -> 0x05
        2 to 3 -> 0x06
        else -> null
    }

    // ── ADV info response parse (ParseHelper.parseADVInfo) ────────────

    /** One decoded attribute from an ADV info response. */
    sealed class AdvAttr {
        /** [L, R, box] battery bytes (0x7F level + 0x80 charging). */
        data class Battery(val left: Byte, val right: Byte, val box: Byte) : AdvAttr()
        data class DeviceName(val name: String) : AdvAttr()
        /** Raw [keyNum(side), action(clicks), function] triplets. */
        data class KeySettings(val triplets: List<Triple<Int, Int, Int>>) : AdvAttr()
        data class InEarSettings(val value: Int) : AdvAttr()
        data class Language(val lang: String) : AdvAttr()
        /** Raw JL byte: on=2, off=1 (inverted). */
        data class SleepMode(val raw: Int) : AdvAttr()
        /** [mode, minGain, maxGain, currentGain] (gains u16 BIG-endian,
         * CHexConverter.int2byte2: out[0] = v>>8, out[1] = v). */
        data class NoiseMode(val mode: Int, val minGain: Int, val maxGain: Int, val currentGain: Int) : AdvAttr()
        data class VolumeBalance(val value: Int) : AdvAttr()
    }

    /**
     * Parse an ADV info payload: a sequence of [len, type, data(len-1)] LTVs.
     * Known types (packed-switch, ParseHelper.smali:2068-2082):
     * 0=battery 1=name 2=keys 4=mic 5=work model 6=vid/uid/pid 8=in-ear
     * 9=language 0xA=sleep 0xB=noise mode 0xC=balance. Unknown types are
     * skipped, like the original.
     */
    fun parseAdvInfo(payload: ByteArray): List<AdvAttr> {
        val out = mutableListOf<AdvAttr>()
        var i = 0
        while (i + 2 <= payload.size) {
            val len = payload[i].toInt() and 0xFF
            if (len <= 0 || len >= 512) return out // over limit — original bails
            val type = payload[i + 1].toInt() and 0xFF
            val dataLen = len - 1
            if (i + 2 + dataLen > payload.size) return out
            val data = payload.copyOfRange(i + 2, i + 2 + dataLen)
            i += len + 1
            when (type) {
                0x0 -> if (data.isNotEmpty()) {
                    out += AdvAttr.Battery(data[0], data.getOrElse(1) { data[0] }, data.getOrElse(2) { data[0] })
                }
                0x1 -> out += AdvAttr.DeviceName(String(data))
                0x2 -> {
                    val triplets = mutableListOf<Triple<Int, Int, Int>>()
                    var k = 0
                    while (k + 3 <= data.size) {
                        triplets += Triple(
                            data[k].toInt() and 0xFF,
                            data[k + 1].toInt() and 0xFF,
                            data[k + 2].toInt() and 0xFF
                        )
                        k += 3
                    }
                    out += AdvAttr.KeySettings(triplets)
                }
                0x8 -> if (data.isNotEmpty()) out += AdvAttr.InEarSettings(data[0].toInt() and 0xFF)
                0x9 -> out += AdvAttr.Language(String(data))
                0xA -> if (data.isNotEmpty()) out += AdvAttr.SleepMode(data[0].toInt() and 0xFF)
                0xB -> if (data.isNotEmpty()) {
                    val mode = data[0].toInt() and 0xFF
                    fun u16(o: Int): Int = if (o + 1 < data.size) {
                        ((data[o].toInt() and 0xFF) shl 8) or (data[o + 1].toInt() and 0xFF)
                    } else 0
                    out += AdvAttr.NoiseMode(mode, u16(2), u16(4), u16(6))
                }
                0xC -> if (data.isNotEmpty()) out += AdvAttr.VolumeBalance(data[0].toInt() and 0xFF)
                else -> Unit // mic/work model/vid — not surfaced
            }
        }
        return out
    }

    /**
     * One old-JL write derived from a QCY command: the RCSP opcode plus its
     * paramData. Key-map (0x2B) commands expand to multiple writes; an empty
     * list means the command is unsupported on this dialect (including plain
     * ANC mode 0x0C, which the official app never sends to JL devices).
     */
    fun routeSettings(cmdId: Byte, params: ByteArray): List<Pair<Byte, ByteArray>> {
        return when (cmdId.toInt() and 0xFF) {
            0x06 -> listOf(0xC0.toByte() to modifyAdvLtv(0x08, byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 1 else 2)))
            0x09 -> listOf(0xC0.toByte() to modifyAdvLtv(0x05, byteArrayOf(invertedBool(params.getOrNull(0) == 0x01.toByte()))))
            0x10 -> listOf(0xC0.toByte() to modifyAdvLtv(0x0A, byteArrayOf(invertedBool(params.getOrNull(0) == 0x01.toByte()))))
            0x16 -> params.getOrNull(0)?.let {
                listOf(0xC0.toByte() to modifyAdvLtv(0x0C, byteArrayOf(it)))
            } ?: emptyList()
            0x17 -> listOf(0xFF.toByte() to (byteArrayOf(0x17, 0x03) + params))
            0x2B -> {
                val writes = mutableListOf<Pair<Byte, ByteArray>>()
                var i = 0
                while (i + 1 < params.size) {
                    val pair = keyIdToSideClicks(params[i].toInt() and 0xFF) ?: return emptyList()
                    writes += 0xC0.toByte() to modifyAdvLtv(
                        0x02,
                        byteArrayOf(pair.first.toByte(), pair.second.toByte(), funcToJl(params[i + 1]))
                    )
                    i += 2
                }
                writes
            }
            else -> emptyList()
        }
    }

    /**
     * ADV op 0xB ANC NoiseMode write payload (setCurrentNoiseMode /
     * NoiseMode.toData): [mode, length 6, minGain BE16, maxGain BE16,
     * currentGain BE16]. The official app reads the current mode first and
     * clamps the new gain into [min, max] before sending
     * (QCYConnectManager.smali:5870-5912).
     */
    fun noiseModeData(mode: Int, minGain: Int, maxGain: Int, currentGain: Int): ByteArray {
        fun be16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
        return byteArrayOf(mode.toByte(), 0x06) + be16(minGain) + be16(maxGain) + be16(currentGain)
    }
}
