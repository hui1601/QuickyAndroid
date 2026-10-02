package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the shared SoundProtocol3936 frame builder/parser and the
 * firmware-recovered command table ([WuqiSoundProtocol]).
 *
 * Framing reference: handleDataPackaging (SoundProtocol3936) —
 *   [70 33] [FF] [totalLen LE] [key 2B] [reserved 2B] [payload] [checksum]
 * Command table reference: /data/reversing/qcy-ht18/catalog/
 * wuqi_sound_protocol_command_map.tsv + hidden_features.md §2.
 */
class WuqiSoundProtocolTest {

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    // ── command table ─────────────────────────────────────────────────

    @Test
    fun `command table carries all 38 firmware ids`() {
        assertEquals(38, WuqiSoundProtocol.COMMANDS.size)
        assertEquals(38, WuqiSoundProtocol.COMMANDS.map { it.id }.distinct().size)
    }

    @Test
    fun `hidden hearing protection prefixes match the firmware table`() {
        // wuqi_sound_protocol_command_map.tsv: 0x20 -> a15a, 0x22 -> a1f9
        assertArrayEquals(bytes(0xA1, 0x5A), WuqiSoundProtocol.prefixFor(0x20))
        assertArrayEquals(bytes(0xA1, 0xF9), WuqiSoundProtocol.prefixFor(0x22))
        // 0x1F -> a159, 0x21 -> a1f8
        assertArrayEquals(bytes(0xA1, 0x59), WuqiSoundProtocol.prefixFor(0x1F))
        assertArrayEquals(bytes(0xA1, 0xF8), WuqiSoundProtocol.prefixFor(0x21))
        assertNull(WuqiSoundProtocol.prefixFor(0x99))
    }

    @Test
    fun `known names resolve by id`() {
        assertEquals("HEARING_PROTECTION_STATUS", WuqiSoundProtocol.commandById(0x20)?.name)
        assertEquals("SET_HEARING_PROTECTION_STATUS", WuqiSoundProtocol.commandById(0x22)?.name)
        assertNotNull(WuqiSoundProtocol.commandById(0x00))
    }

    // ── buildPacket ───────────────────────────────────────────────────

    @Test
    fun `buildPacket produces header length key payload checksum`() {
        // SET_HEARING_PROTECTION_STATUS (A1 F9), on-payload [01]:
        // totalLen = 3 + 2 + 2 + 1 + 1 = 9; checksum = low byte of the
        // sum of the first 8 bytes.
        val packet = WuqiSoundProtocol.buildPacket(bytes(0xA1, 0xF9), bytes(0x01))
        val expectedChecksum =
            (0x70 + 0x33 + 0xFF + 0x09 + 0x00 + 0xA1 + 0xF9 + 0x01) and 0xFF
        assertArrayEquals(
            bytes(0x70, 0x33, 0xFF, 0x09, 0x00, 0xA1, 0xF9, 0x01, expectedChecksum),
            packet
        )
    }

    @Test
    fun `buildPacket empty payload yields 8-byte status frame`() {
        // SPATIAL_AUDIO_STATUS (A1 59) with no payload
        val packet = WuqiSoundProtocol.buildPacket(bytes(0xA1, 0x59), byteArrayOf())
        assertEquals(8, packet.size)
        assertEquals(8, (packet[3].toInt() and 0xFF) or ((packet[4].toInt() and 0xFF) shl 8))
        val expectedChecksum = (0x70 + 0x33 + 0xFF + 0x08 + 0x00 + 0xA1 + 0x59) and 0xFF
        assertEquals(expectedChecksum.toByte(), packet[7])
    }

    @Test
    fun `buildPacket rejects malformed keys`() {
        try {
            WuqiSoundProtocol.buildPacket(bytes(0xA1), byteArrayOf())
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    // ── parseResponse ─────────────────────────────────────────────────

    private fun responseFrame(status: Int, key: Int, payload: List<Int>): ByteArray {
        // response layout: 33 70 FF lenLE status key0 key1 <reserved 1B> payload
        val body = bytes(0x33, 0x70, 0xFF, 0x00, 0x00, status, key shr 8, key and 0xFF, 0x00) +
            payload.map { it.toByte() }.toByteArray()
        // length field counts everything after itself incl. checksum
        val totalLen = body.size - 3 + 1
        body[3] = (totalLen and 0xFF).toByte()
        body[4] = ((totalLen shr 8) and 0xFF).toByte()
        val checksum = body.sumOf { it.toInt() and 0xFF } and 0xFF
        return body + byteArrayOf(checksum.toByte())
    }

    @Test
    fun `parseResponse decodes hearing protection status`() {
        val frame = responseFrame(status = 1, key = 0xA15A, payload = listOf(0x01))
        val response = WuqiSoundProtocol.parseResponse(frame)
        assertEquals(0x20, response?.commandId)
        assertArrayEquals(bytes(0x01), response?.payload)
    }

    @Test
    fun `parseResponse strips trailing checksum from payload`() {
        val frame = responseFrame(status = 1, key = 0xA159, payload = listOf(0x00, 0x7B))
        val response = WuqiSoundProtocol.parseResponse(frame)
        assertEquals(0x1F, response?.commandId)
        assertArrayEquals(bytes(0x00, 0x7B), response?.payload)
    }

    @Test
    fun `parseResponse rejects non-sound frames and short frames`() {
        assertNull(WuqiSoundProtocol.parseResponse(bytes(0xFF, 0x91, 0x22, 0x8F, 0x01)))
        assertNull(WuqiSoundProtocol.parseResponse(bytes(0x33, 0x70, 0xFF)))
        assertNull(WuqiSoundProtocol.parseResponse(byteArrayOf()))
    }

    @Test
    fun `parseResponse reports failure status as command 0`() {
        val frame = responseFrame(status = 0, key = 0xA1F9, payload = listOf(0x01))
        val response = WuqiSoundProtocol.parseResponse(frame)
        // well-formed but rejected by the device — distinct from "not a sound frame"
        assertNotNull(response)
        assertEquals(0x00, response?.commandId)
    }

    // ── responseToQcyToggle ───────────────────────────────────────────

    @Test
    fun `hidden toggles map to QCY opcodes with on-off values`() {
        val spatial = WuqiSoundProtocol.responseToQcyToggle(
            WuqiSoundProtocol.Response(0x1F, bytes(0x01))
        )
        assertEquals(0x2D.toByte(), spatial?.first)
        assertEquals(0x01.toByte(), spatial?.second)

        val hearingOff = WuqiSoundProtocol.responseToQcyToggle(
            WuqiSoundProtocol.Response(0x22, bytes(0x00))
        )
        assertEquals(0x26.toByte(), hearingOff?.first)
        assertEquals(0x02.toByte(), hearingOff?.second)
    }

    @Test
    fun `unmapped commands and empty payloads return null`() {
        assertNull(
            WuqiSoundProtocol.responseToQcyToggle(WuqiSoundProtocol.Response(0x00, bytes(0x01)))
        )
        assertNull(
            WuqiSoundProtocol.responseToQcyToggle(WuqiSoundProtocol.Response(0x20, byteArrayOf()))
        )
    }
}
