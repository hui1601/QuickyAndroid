package com.hui1601.quickyandroid.ble

/**
 * WuQi SoundProtocol3936 wire helpers shared by [WuqiGattClient] (WuQi-only
 * devices) and the opportunistic 0x2001 bridge on [QcyGattClient].
 *
 * Frame layout (handleDataPackaging, SoundProtocol3936):
 *   [0-1] 0x70 0x33   [2] 0xFF   [3-4] total length (LE, incl. checksum)
 *   [5-6] command key (2 bytes)  [7..] payload
 *   [last] checksum = low byte of the sum of all preceding bytes
 *
 * Response: header mirrored (0x33 0x70 0xFF), status at [5] (1 = success),
 * key at [6-7], payload from [9] up to the trailing checksum byte.
 *
 * The command table below is the firmware-side ID → prefix map recovered
 * from the HT18 firmware (catalog/wuqi_sound_protocol_command_map.tsv,
 * /data/reversing/qcy-ht18). Prefixes marked HIDDEN are parsed by the
 * firmware but unused by the retail HT18 control panel
 * (catalog/hidden_features.md §2).
 */
object WuqiSoundProtocol {

    /** App-internal routing opcode for hearing protection. DataBean 0x26 is
     * not in the QCY namespace and has no firmware case, so it cannot
     * collide with a real device command. */
    val QCY_SYNTH_HEARING_PROTECTION: Byte = 0x26

    const val CMD_SPATIAL_AUDIO_STATUS = 0x1F      // A1 59 (hidden)
    const val CMD_HEARING_PROTECTION_STATUS = 0x20 // A1 5A (hidden)
    const val CMD_SET_SPATIAL_AUDIO_STATUS = 0x21  // A1 F8 (hidden)
    const val CMD_SET_HEARING_PROTECTION_STATUS = 0x22 // A1 F9 (hidden)

    data class SoundCommand(val id: Int, val prefix: ByteArray, val name: String?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SoundCommand) return false
            return id == other.id && prefix.contentEquals(other.prefix)
        }
        override fun hashCode(): Int = 31 * id + prefix.contentHashCode()
    }

    /** Full 38-entry ID → prefix table from the HT18 firmware catalog. */
    val COMMANDS: List<SoundCommand> = listOf(
        SoundCommand(0x00, byteArrayOf(0xA1.toByte(), 0x51), "ALL_DEVICE_INFORMATION"),
        SoundCommand(0x01, byteArrayOf(0xA1.toByte(), 0x52), null),
        SoundCommand(0x02, byteArrayOf(0xA1.toByte(), 0x53), "DEVICE_ELECTRICAL_VALUE"),
        SoundCommand(0x03, byteArrayOf(0xA1.toByte(), 0x54), "DEVICE_CHARGING_STATUS"),
        SoundCommand(0x04, byteArrayOf(0xA1.toByte(), 0x55), "DEVICE_FIRMWARE_VERSION"),
        SoundCommand(0x05, byteArrayOf(0xA1.toByte(), 0xF4.toByte()), null),
        SoundCommand(0x06, byteArrayOf(0xA1.toByte(), 0xF3.toByte()), null),
        SoundCommand(0x07, byteArrayOf(0xA1.toByte(), 0xF6.toByte()), "SETTING_AUTO_POWER_OFF"),
        SoundCommand(0x08, byteArrayOf(0xA1.toByte(), 0xF7.toByte()), "SETTING_GAME_MODE"),
        SoundCommand(0x09, byteArrayOf(0xA2.toByte(), 0x51), "EQ_INFORMATION"),
        SoundCommand(0x0A, byteArrayOf(0xA2.toByte(), 0xF1.toByte()), "CHANGE_EQ"),
        SoundCommand(0x0B, byteArrayOf(0xA4.toByte(), 0xF1.toByte()), null),
        SoundCommand(0x0C, byteArrayOf(0xA4.toByte(), 0xF2.toByte()), null),
        SoundCommand(0x0D, byteArrayOf(0xA4.toByte(), 0xF3.toByte()), null),
        SoundCommand(0x0E, byteArrayOf(0xA5.toByte(), 0x51), null),
        SoundCommand(0x0F, byteArrayOf(0xA5.toByte(), 0xF1.toByte()), null),
        SoundCommand(0x10, byteArrayOf(0xA6.toByte(), 0x51), "ACCESS_TO_ANC_STATUS"),
        SoundCommand(0x11, byteArrayOf(0xA6.toByte(), 0xF1.toByte()), "SETTING_ANC_STATUS"),
        SoundCommand(0x12, byteArrayOf(0xA6.toByte(), 0x52), null),
        SoundCommand(0x13, byteArrayOf(0xA6.toByte(), 0xF2.toByte()), null),
        SoundCommand(0x14, byteArrayOf(0xA9.toByte(), 0x51), null),
        SoundCommand(0x15, byteArrayOf(0xA1.toByte(), 0x57), "READ_THE_LDAC_STATUS"),
        SoundCommand(0x16, byteArrayOf(0xA1.toByte(), 0xFF.toByte()), "SET_THE_LDAC_STATUS"),
        SoundCommand(0x17, byteArrayOf(0xA1.toByte(), 0x58), "READ_GAME_MODE"),
        SoundCommand(0x18, byteArrayOf(0xAB.toByte(), 0x51), null),
        SoundCommand(0x19, byteArrayOf(0xAB.toByte(), 0xF1.toByte()), null),
        SoundCommand(0x1A, byteArrayOf(0xAB.toByte(), 0xF2.toByte()), null),
        SoundCommand(0x1B, byteArrayOf(0xAB.toByte(), 0xF3.toByte()), null),
        SoundCommand(0x1C, byteArrayOf(0xAB.toByte(), 0xF4.toByte()), null),
        SoundCommand(0x1D, byteArrayOf(0xAB.toByte(), 0xF5.toByte()), null),
        SoundCommand(0x1E, byteArrayOf(0xAB.toByte(), 0xF6.toByte()), null),
        SoundCommand(CMD_SPATIAL_AUDIO_STATUS, byteArrayOf(0xA1.toByte(), 0x59), "SPATIAL_AUDIO_STATUS"),
        SoundCommand(CMD_HEARING_PROTECTION_STATUS, byteArrayOf(0xA1.toByte(), 0x5A), "HEARING_PROTECTION_STATUS"),
        SoundCommand(CMD_SET_SPATIAL_AUDIO_STATUS, byteArrayOf(0xA1.toByte(), 0xF8.toByte()), "SET_SPATIAL_AUDIO_STATUS"),
        SoundCommand(CMD_SET_HEARING_PROTECTION_STATUS, byteArrayOf(0xA1.toByte(), 0xF9.toByte()), "SET_HEARING_PROTECTION_STATUS"),
        SoundCommand(0x23, byteArrayOf(0xAC.toByte(), 0xF1.toByte()), null),
        SoundCommand(0x24, byteArrayOf(0xAC.toByte(), 0xF2.toByte()), null),
        SoundCommand(0x25, byteArrayOf(0xAC.toByte(), 0xF3.toByte()), null)
    )

    private val byId: Map<Int, SoundCommand> = COMMANDS.associateBy { it.id }

    fun commandById(id: Int): SoundCommand? = byId[id]

    fun prefixFor(id: Int): ByteArray? = byId[id]?.prefix

    /**
     * Build a SoundProtocol3936 frame: 70 33 FF lenLE key payload checksum.
     * The length field counts everything after itself, including the
     * checksum byte (3 + 2 + payload + 1).
     */
    fun buildPacket(cmdKey: ByteArray, payload: ByteArray): ByteArray {
        require(cmdKey.size == 2) { "command key must be 2 bytes" }
        val totalLen = 3 + 2 + 2 + payload.size + 1
        val packet = ByteArray(7 + payload.size + 1)
        packet[0] = 0x70
        packet[1] = 0x33
        packet[2] = 0xFF.toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[4] = (totalLen shr 8).toByte()
        packet[5] = cmdKey[0]
        packet[6] = cmdKey[1]
        payload.copyInto(packet, 7)
        var sum = 0
        for (i in 0 until packet.size - 1) sum += packet[i].toInt() and 0xFF
        packet[packet.size - 1] = (sum and 0xFF).toByte()
        return packet
    }

    data class Response(val commandId: Int, val payload: ByteArray)

    /**
     * Parse a device response frame (33 70 FF lenLE status key … payload …
     * checksum). Mirrors the original receiveOriginalData leniency: only the
     * 3-byte header and a minimum size of 8 are enforced; the trailing
     * checksum byte is not verified. Returns null when the frame is not a
     * sound-protocol response, [RESULT_FAILED] when status != 1.
     */
    fun parseResponse(value: ByteArray): Response? {
        if (value.size <= 7) return null
        if (value[0] != 0x33.toByte() || value[1] != 0x70.toByte() || value[2] != 0xFF.toByte()) return null
        if (value[5] != 0x01.toByte()) return RESULT_FAILED
        val key = byteArrayOf(value[6], value[7])
        val cmd = COMMANDS.firstOrNull { it.prefix.contentEquals(key) } ?: return null
        val payload = if (value.size > 9) value.copyOfRange(9, value.size - 1) else byteArrayOf()
        return Response(cmd.id, payload)
    }

    /** Sentinel returned for well-formed frames whose status byte reports
     * failure — distinguishes "rejected" from "not a sound frame". */
    private val RESULT_FAILED = Response(0x00, byteArrayOf())

    /**
     * Map a sound-command response to the QCY opcode space the ViewModel
     * understands: spatial audio surfaces under 0x2D, hearing protection
     * under the synthetic [QCY_SYNTH_HEARING_PROTECTION]. Returns the QCY
     * toggle value (1 = on, 2 = off), or null for other commands.
     */
    fun responseToQcyToggle(response: Response): Pair<Byte, Byte>? {
        val qcyCmd = when (response.commandId) {
            CMD_SPATIAL_AUDIO_STATUS, CMD_SET_SPATIAL_AUDIO_STATUS -> 0x2D
            CMD_HEARING_PROTECTION_STATUS, CMD_SET_HEARING_PROTECTION_STATUS -> QCY_SYNTH_HEARING_PROTECTION.toInt() and 0xFF
            else -> return null
        }
        if (response.payload.isEmpty()) return null
        val on = response.payload[0] == 0x01.toByte()
        return qcyCmd.toByte() to if (on) 0x01 else 0x02
    }
}
