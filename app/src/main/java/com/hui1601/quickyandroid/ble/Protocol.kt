package com.hui1601.quickyandroid.ble

import java.util.UUID

object Protocol {

    // Standard QCY protocol UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("0000a001-0000-1000-8000-00805f9b34fb")
    val COMMAND_UUID: UUID = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb")
    val NOTIFY_UUID: UUID = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb")
    val EQ_UUID: UUID = UUID.fromString("0000000b-0000-1000-8000-00805f9b34fb")
    val KEY_FUNC_UUID: UUID = UUID.fromString("0000000d-0000-1000-8000-00805f9b34fb")
    val BATTERY_UUID: UUID = UUID.fromString("00000008-0000-1000-8000-00805f9b34fb")
    val VERSION_UUID: UUID = UUID.fromString("00000007-0000-1000-8000-00805f9b34fb")
    val SETTINGS_UUID: UUID = UUID.fromString("0000000e-0000-1000-8000-00805f9b34fb")
    val STATE_READ_UUID: UUID = UUID.fromString("0000000f-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // JL protocol UUIDs
    val JL_SERVICE_UUID: UUID = UUID.fromString("0000a002-0000-1000-8000-00805f9b34fb")
    val JL_COMMAND_UUID: UUID = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
    val JL_NOTIFY_UUID: UUID = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")

    // Wuqi protocol UUIDs
    val WUQI_SERVICE_UUID: UUID = UUID.fromString("00007033-0000-1000-8000-00805f9b34fb")
    val WUQI_COMMAND_UUID: UUID = UUID.fromString("00002001-0000-1000-8000-00805f9b34fb")
    val WUQI_NOTIFY_UUID: UUID = UUID.fromString("00002002-0000-1000-8000-00805f9b34fb")

    const val QCY_COMPANY_ID = 0x521c
    const val QCY_WATCH_COMPANY_ID = 0x05D6

    @JvmStatic
    fun packPacket(cmdId: Byte, params: ByteArray): ByteArray {
        val body = ByteArray(2 + params.size)
        body[0] = cmdId
        body[1] = params.size.toByte()
        System.arraycopy(params, 0, body, 2, params.size)
        val packet = ByteArray(2 + body.size)
        packet[0] = 0xFF.toByte()
        packet[1] = body.size.toByte()
        System.arraycopy(body, 0, packet, 2, body.size)
        return packet
    }

    @JvmStatic
    fun parsePacket(packet: ByteArray): List<CommandBlock> {
        if (packet.size < 4 || packet[0] != 0xFF.toByte()) {
            return emptyList()
        }
        val bodyLen = packet[1].toInt() and 0xFF
        if (bodyLen + 2 != packet.size) {
            return emptyList()
        }
        val commands = mutableListOf<CommandBlock>()
        var offset = 2
        while (offset < packet.size) {
            if (offset + 2 > packet.size) break
            val cmdId = packet[offset]
            val paramLen = packet[offset + 1].toInt() and 0xFF
            offset += 2
            if (offset + paramLen > packet.size) break
            val params = packet.copyOfRange(offset, offset + paramLen)
            commands.add(CommandBlock(cmdId, params))
            offset += paramLen
        }
        return commands
    }

    data class CommandBlock(
        val cmdId: Byte,
        val params: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CommandBlock) return false
            return cmdId == other.cmdId && params.contentEquals(other.params)
        }

        override fun hashCode(): Int {
            var result = cmdId.toInt()
            result = 31 * result + params.contentHashCode()
            return result
        }
    }

    // ── Parametric EQ helpers (cmd 0x20 v1, 0x22/0x46/0x47 v2) ────────

    data class EqBand(val freq: Int, val gainDb: Float, val q: Float, val bandType: Int = 0)

    /**
     * Result of parsing a 0x20/0x22/0x46/0x47 parametric-EQ response.
     * masterGainDb is the device-reported master gain (signed s16LE / 100).
     */
    data class ParametricEq(
        val eqIndex: Int,
        val masterGainDb: Float,
        val bands: List<EqBand>
    )

    /**
     * Pack parametric EQ parameters. v1 (0x20) uses 6 bytes/band, v2 (0x22/0x46/0x47)
     * appends a bandType byte (7 bytes/band). paramLen = N * stride + 3.
     * Master gain is dB*100 truncated; band gain and q are dB*100 / Q*100 ROUNDED
     * (Math.round), all as 16-bit LE and sent unclamped — matching setEQWtihQFG /
     * setEQWtihQFG2 in the official app.
     */
    fun packParametricEq(cmdId: Byte, eqIndex: Int, masterGainDb: Float, bands: List<EqBand>): ByteArray {
        val v2 = cmdId.toInt() and 0xFF != 0x20
        val stride = if (v2) 7 else 6
        val params = ByteArray(3 + bands.size * stride)
        params[0] = eqIndex.toByte()
        val masterGain = (masterGainDb * 100).toInt()
        params[1] = (masterGain and 0xFF).toByte()
        params[2] = ((masterGain shr 8) and 0xFF).toByte()
        bands.forEachIndexed { i, band ->
            val offset = 3 + i * stride
            val gain = Math.round(band.gainDb * 100)
            val q = Math.round(band.q * 100)
            params[offset] = (band.freq and 0xFF).toByte()
            params[offset + 1] = ((band.freq shr 8) and 0xFF).toByte()
            params[offset + 2] = (gain and 0xFF).toByte()
            params[offset + 3] = ((gain shr 8) and 0xFF).toByte()
            params[offset + 4] = (q and 0xFF).toByte()
            params[offset + 5] = ((q shr 8) and 0xFF).toByte()
            if (v2) params[offset + 6] = band.bandType.toByte()
        }
        return packPacket(cmdId, params)
    }

    /**
     * Parse the params of a 0x20/0x22/0x46/0x47 response:
     * [eqType, masterGainLo, masterGainHi, bands...]. The cmdId selects the stride:
     * 0x20 forces 6 bytes/band (v1), 0x22/0x46/0x47 force 7 bytes/band (v2).
     * The official EQParamListBean requires params.size > 9 and walks the
     * remainder in stride chunks; returns null otherwise. Gains and masterGain
     * are signed s16LE decoded (/100f); freq and q are u16LE.
     */
    fun parseParametricEq(cmdId: Byte, params: ByteArray): ParametricEq? {
        val stride = if (cmdId.toInt() and 0xFF == 0x20) 6 else 7
        val remaining = params.size - 3
        if (params.size <= 9 || remaining < 0 || remaining % stride != 0) return null
        val eqType = params[0].toInt() and 0xFF
        val masterGain = (params[1].toInt() and 0xFF) or ((params[2].toInt() and 0xFF) shl 8)
        val masterGainSigned = if (masterGain >= 0x8000) masterGain - 0x10000 else masterGain
        val bandCount = remaining / stride
        val bands = (0 until bandCount).map { i ->
            val offset = 3 + i * stride
            val freq = (params[offset].toInt() and 0xFF) or ((params[offset + 1].toInt() and 0xFF) shl 8)
            val gain = (params[offset + 2].toInt() and 0xFF) or ((params[offset + 3].toInt() and 0xFF) shl 8)
            val gainSigned = if (gain >= 0x8000) gain - 0x10000 else gain
            val q = (params[offset + 4].toInt() and 0xFF) or ((params[offset + 5].toInt() and 0xFF) shl 8)
            val bandType = if (stride == 7) params[offset + 6].toInt() and 0xFF else 0
            EqBand(freq, gainSigned / 100f, q / 100f, bandType)
        }
        return ParametricEq(eqType, masterGainSigned / 100f, bands)
    }

    // ── Command builders ──────────────────────────────────────────────

    fun resetDefault() = packPacket(0x01, byteArrayOf())
    fun clearPairing() = packPacket(0x02, byteArrayOf())
    fun factoryReset() = packPacket(0x03, byteArrayOf())
    fun musicControl(action: Byte) = packPacket(0x04, byteArrayOf(action))
    fun lightFlash(on: Boolean) = packPacket(0x05, byteArrayOf(if (on) 0x01 else 0x00))
    fun inEarDetection(on: Boolean) = packPacket(0x06, byteArrayOf(if (on) 0x01 else 0x02))
    fun noiseValue(value: Byte) = packPacket(0x07, byteArrayOf(value))
    fun volume(left: Byte, right: Byte) = packPacket(0x08, byteArrayOf(left, right, 0x00))
    fun lowLatency(on: Boolean) = packPacket(0x09, byteArrayOf(if (on) 0x01 else 0x02))
    fun monitoring(value: Byte) = packPacket(0x0A, byteArrayOf(value))
    fun noiseCancelMode(mode: Byte) = packPacket(0x0C, byteArrayOf(mode))
    fun testMode(on: Boolean) = packPacket(0x0D, byteArrayOf(if (on) 0x01 else 0x02))
    fun sleepMode(on: Boolean) = packPacket(0x10, byteArrayOf(if (on) 0x01 else 0x02))
    /**
     * Ear-tip fit test enable/disable — the app-level form used by
     * QCYConnectManager.setCompactnessEnable (smali:4540-4650): three
     * identical bytes, 1 = start, 0 = stop. (The library's
     * CompactnessDataBean.getCompactnessCMD two-byte [left?1:2, right?1:2]
     * form is NOT what the app sends.)
     */
    fun earTipFitTest(start: Boolean) = packPacket(0x11, ByteArray(3) { if (start) 0x01 else 0x00 })
    fun ledMode(on: Boolean) = packPacket(0x12, byteArrayOf(if (on) 0x01 else 0x02))
    fun powerManager(minutes: Int): ByteArray {
        val lo = (minutes and 0xFF).toByte()
        val hi = ((minutes shr 8) and 0xFF).toByte()
        return packPacket(0x14, byteArrayOf(lo, hi, 0x00, 0x00))
    }
    fun soundBalance(value: Byte) = packPacket(0x16, byteArrayOf(value))
    fun ancSetting(mode: Byte, subScene: Byte, noiseValue: Byte) =
        packPacket(0x17, byteArrayOf(mode, subScene, noiseValue))
    fun rename(name: String): ByteArray {
        val bytes = name.toByteArray(Charsets.UTF_8)
        return packPacket(0x18, bytes)
    }
    fun voiceLanguage(lang: String): ByteArray {
        val bytes = lang.toByteArray(Charsets.UTF_8)
        return packPacket(0x19, bytes)
    }
    fun toneVolume(volume: Byte) = packPacket(0x1D, byteArrayOf(volume))
    fun takePhoto(action: Byte) = packPacket(0x1E, byteArrayOf(action))
    fun standby(state: Byte) = packPacket(0x1F, byteArrayOf(state))
    fun ldac(on: Boolean) = packPacket(0x23, byteArrayOf(if (on) 0x01 else 0x02))
    fun adaptiveEq(on: Boolean) = packPacket(0x27, byteArrayOf(if (on) 0x01 else 0x02))
    fun ancWear(value: Byte) = packPacket(0x29, byteArrayOf(value))
    fun wearingDetection(enable: Byte, musicIndex: Byte, ancIndex: Byte, toneEnable: Byte? = null): ByteArray {
        return if (toneEnable != null) {
            packPacket(0x2C, byteArrayOf(enable, musicIndex, ancIndex, toneEnable))
        } else {
            packPacket(0x2C, byteArrayOf(enable, musicIndex, ancIndex))
        }
    }
    fun spatialAudio(on: Boolean) = packPacket(0x2D, byteArrayOf(if (on) 0x01 else 0x02))
    fun musicMode(mode: Byte) = packPacket(0x2E, byteArrayOf(mode))
    fun batteryRead() = packPacket(0x2F, byteArrayOf())
    fun versionRead() = packPacket(0x30, byteArrayOf())
    fun envAdaptation(on: Boolean) = packPacket(0x32, byteArrayOf(if (on) 0x01 else 0x02))
    fun twsEnable(on: Boolean) = packPacket(0x34, byteArrayOf(if (on) 0x01 else 0x02))
    fun ledSwitch(on: Boolean) = packPacket(0x35, byteArrayOf(if (on) 0x01 else 0x02))
    fun ledEffect(speed: Byte, brightness: Byte, effectIndex: Byte, colors: List<Triple<Byte, Byte, Byte>>): ByteArray {
        val colorBytes = colors.flatMap { listOf(it.first, it.second, it.third) }.toByteArray()
        val params = byteArrayOf(speed, brightness, effectIndex, *colorBytes)
        return packPacket(0x36, params)
    }
    fun playMode(mode: Byte) = packPacket(0x37, byteArrayOf(mode))
    fun focusMode(on: Boolean) = packPacket(0x39, byteArrayOf(if (on) 0x01 else 0x02))
    fun tonePlay(toneId: Byte) = packPacket(0x3D, byteArrayOf(toneId))
    fun syncTime(year: Byte, month: Byte, day: Byte, hour: Byte, minute: Byte, second: Byte, dayOfWeek: Byte) =
        packPacket(0x3E, byteArrayOf(year, month, day, hour, minute, second, dayOfWeek))

    // ── Music player / alarm (QCYHeadsetClient.setMusicStatus/.setMusicInfo/
    // setAlarmInfo, smali .line 699-743 + AlarmDataBean .line 24-31) ──────

    /**
     * setMusicStatus (cmd 0x3A): [id u32 LE, status u8, p4 u8], paramLen 6.
     */
    fun musicStatus(id: Long, status: Byte, p4: Byte = 0): ByteArray {
        val params = byteArrayOf(
            (id and 0xFF).toByte(),
            ((id shr 8) and 0xFF).toByte(),
            ((id shr 16) and 0xFF).toByte(),
            ((id shr 24) and 0xFF).toByte(),
            status,
            p4
        )
        return packPacket(0x3A, params)
    }

    /**
     * setMusicInfo (cmd 0x3B): [0x00, entries...] where each entry is
     * [musicID u32 LE, totalTime u16 LE] (paramLen = 1 + 6N).
     */
    fun musicInfo(entries: List<Pair<Long, Int>>): ByteArray {
        val params = ByteArray(1 + entries.size * 6)
        params[0] = 0
        entries.forEachIndexed { i, (id, total) ->
            val o = 1 + i * 6
            params[o] = (id and 0xFF).toByte()
            params[o + 1] = ((id shr 8) and 0xFF).toByte()
            params[o + 2] = ((id shr 16) and 0xFF).toByte()
            params[o + 3] = ((id shr 24) and 0xFF).toByte()
            params[o + 4] = (total and 0xFF).toByte()
            params[o + 5] = ((total shr 8) and 0xFF).toByte()
        }
        return packPacket(0x3B, params)
    }

    /**
     * Alarm management (cmd 0x3F, AlarmDataBean.smali getAddAlarmCMD /
     * getDeleteAlarmCMD / getEditAlarmCMD — paramLen is always 7 and the ADD
     * form carries a literal trailing 0x05 byte).
     */
    fun alarmAdd(id: Byte, enabled: Boolean, hour: Byte, minute: Byte, cycle: Byte): ByteArray =
        packPacket(0x3F, byteArrayOf(0x01, id, if (enabled) 1 else 0, hour, minute, cycle, 0x05))

    fun alarmDelete(id: Byte): ByteArray =
        packPacket(0x3F, byteArrayOf(0x02, id, 0, 0, 0, 0, 0))

    fun alarmEdit(id: Byte, enabled: Boolean, hour: Byte, minute: Byte, cycle: Byte, p4: Byte): ByteArray =
        packPacket(0x3F, byteArrayOf(0x03, id, if (enabled) 1 else 0, hour, minute, cycle, p4))
    fun aiTrigger(action: Byte) = packPacket(0x43, byteArrayOf(action))
    fun maxEqCount() = packPacket(0x44, byteArrayOf())
    fun customEqTest(on: Boolean) = packPacket(0x45, byteArrayOf(if (on) 0x01 else 0x02))
    fun inEarSensitivity(level: Byte) = packPacket(0x48, byteArrayOf(level))
    fun gameConfig(config: Byte) = packPacket(0x4A, byteArrayOf(config))
    fun requestData(cmdId: Byte) = packPacket(0xFE.toByte(), byteArrayOf(cmdId))

    // ── Key function helpers ──────────────────────────────────────────

    fun buildKeyFunctionMap(pairs: List<Pair<Byte, Byte>>): ByteArray {
        return pairs.flatMap { listOf(it.first, it.second) }.toByteArray()
    }

    // Key IDs (music mode)
    const val KEY_LEFT_SINGLE = 0x01.toByte()
    const val KEY_RIGHT_SINGLE = 0x02.toByte()
    const val KEY_LEFT_DOUBLE = 0x03.toByte()
    const val KEY_RIGHT_DOUBLE = 0x04.toByte()
    const val KEY_LEFT_TRIPLE = 0x05.toByte()
    const val KEY_RIGHT_TRIPLE = 0x06.toByte()
    const val KEY_LEFT_QUAD = 0x07.toByte()
    const val KEY_RIGHT_QUAD = 0x08.toByte()
    const val KEY_LEFT_LONG = 0x09.toByte()
    const val KEY_RIGHT_LONG = 0x0A.toByte()

    // Key IDs (voice/call mode)
    const val KEY_CALL_LEFT_SINGLE = 0x15.toByte()
    const val KEY_CALL_RIGHT_SINGLE = 0x16.toByte()
    const val KEY_CALL_LEFT_DOUBLE = 0x17.toByte()
    const val KEY_CALL_RIGHT_DOUBLE = 0x18.toByte()
    const val KEY_CALL_LEFT_TRIPLE = 0x19.toByte()
    const val KEY_CALL_RIGHT_TRIPLE = 0x1A.toByte()
    const val KEY_CALL_LEFT_QUAD = 0x1B.toByte()
    const val KEY_CALL_RIGHT_QUAD = 0x1C.toByte()
    const val KEY_CALL_LEFT_LONG = 0x1D.toByte()
    const val KEY_CALL_RIGHT_LONG = 0x1E.toByte()

    // Function IDs
    const val FUN_NONE = 0x00.toByte()
    const val FUN_PLAY_PAUSE = 0x01.toByte()
    const val FUN_PREV = 0x02.toByte()
    const val FUN_NEXT = 0x03.toByte()
    const val FUN_VOICE_ASSISTANT = 0x04.toByte()
    const val FUN_VOL_UP = 0x05.toByte()
    const val FUN_VOL_DOWN = 0x06.toByte()
    const val FUN_GAME_MODE = 0x07.toByte()
    const val FUN_ANSWER = 0x08.toByte()
    const val FUN_REJECT = 0x09.toByte()
    const val FUN_HOLD = 0x0A.toByte()
    const val FUN_REDIAL = 0x0B.toByte()
}
