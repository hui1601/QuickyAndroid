package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Protocol] framing, command builders and round-trips.
 * Expected byte values are taken from docs/protocol.md (Quicky spec).
 */
class ProtocolTest {

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    // ── packPacket framing ────────────────────────────────────────────

    @Test
    fun `packPacket frames single command with empty params`() {
        // [0xFF] [bodyLen=2] [cmd] [paramLen=0]
        assertArrayEquals(bytes(0xFF, 0x02, 0x2F, 0x00), Protocol.packPacket(0x2F, byteArrayOf()))
    }

    @Test
    fun `packPacket frames single command with params`() {
        // Music control play: [0xFF, 0x03, 0x04, 0x01, 0x01]
        assertArrayEquals(bytes(0xFF, 0x03, 0x04, 0x01, 0x01), Protocol.packPacket(0x04, bytes(0x01)))
    }

    @Test
    fun `packPacket body length equals total minus two`() {
        val params = bytes(0x0A, 0x14, 0x00)
        val packet = Protocol.packPacket(0x08, params)
        assertEquals(0xFF.toByte(), packet[0])
        assertEquals(packet.size - 2, packet[1].toInt() and 0xFF)
        assertEquals(0x08.toByte(), packet[2])
        assertEquals(params.size, packet[3].toInt() and 0xFF)
    }

    // ── parsePacket ───────────────────────────────────────────────────

    @Test
    fun `parsePacket round-trips single command`() {
        val packet = Protocol.packPacket(0x17, bytes(0x02, 0x03, 0x32))
        val blocks = Protocol.parsePacket(packet)
        assertEquals(listOf(Protocol.CommandBlock(0x17, bytes(0x02, 0x03, 0x32))), blocks)
    }

    @Test
    fun `parsePacket handles multiple command blocks in one packet`() {
        // body: [0x04, 0x01, 0x01] [0x09, 0x00] -> bodyLen = 5
        val packet = bytes(0xFF, 0x05, 0x04, 0x01, 0x01, 0x09, 0x00)
        val blocks = Protocol.parsePacket(packet)
        assertEquals(2, blocks.size)
        assertEquals(Protocol.CommandBlock(0x04, bytes(0x01)), blocks[0])
        assertEquals(Protocol.CommandBlock(0x09, byteArrayOf()), blocks[1])
    }

    @Test
    fun `parsePacket rejects wrong start-of-frame byte`() {
        assertTrue(Protocol.parsePacket(bytes(0xFE, 0x02, 0x2F, 0x00)).isEmpty())
    }

    @Test
    fun `parsePacket rejects packets shorter than 4 bytes`() {
        assertTrue(Protocol.parsePacket(byteArrayOf()).isEmpty())
        assertTrue(Protocol.parsePacket(bytes(0xFF)).isEmpty())
        assertTrue(Protocol.parsePacket(bytes(0xFF, 0x02, 0x2F)).isEmpty())
    }

    @Test
    fun `parsePacket rejects truncated packet with mismatched body length`() {
        // Declares bodyLen 5 but only 3 body bytes present
        assertTrue(Protocol.parsePacket(bytes(0xFF, 0x05, 0x04, 0x01, 0x01)).isEmpty())
        // Declares bodyLen 2 but extra trailing byte
        assertTrue(Protocol.parsePacket(bytes(0xFF, 0x02, 0x2F, 0x00, 0x00)).isEmpty())
    }

    @Test
    fun `parsePacket skips block whose paramLen overruns the body`() {
        // bodyLen consistent with size, but the single block claims 5 params with only 1 available
        val blocks = Protocol.parsePacket(bytes(0xFF, 0x03, 0x04, 0x05, 0x01))
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `parsePacket keeps blocks parsed before a truncated trailing block`() {
        // One full block [0x04, 0x01, 0x01] followed by a lone trailing cmd byte
        val blocks = Protocol.parsePacket(bytes(0xFF, 0x04, 0x04, 0x01, 0x01, 0x09))
        assertEquals(listOf(Protocol.CommandBlock(0x04, bytes(0x01))), blocks)
    }

    @Test
    fun `CommandBlock equality compares params by content`() {
        assertEquals(Protocol.CommandBlock(0x01, bytes(0x02)), Protocol.CommandBlock(0x01, bytes(0x02)))
        assertNotEquals(Protocol.CommandBlock(0x01, bytes(0x02)), Protocol.CommandBlock(0x01, bytes(0x03)))
        assertNotEquals(Protocol.CommandBlock(0x01, bytes(0x02)), Protocol.CommandBlock(0x02, bytes(0x02)))
        assertEquals(
            Protocol.CommandBlock(0x01, bytes(0x02)).hashCode(),
            Protocol.CommandBlock(0x01, bytes(0x02)).hashCode()
        )
    }

    // ── Command builders (exact bytes per protocol.md) ────────────────

    @Test
    fun `resetDefault clearPairing factoryReset are zero-param commands`() {
        assertArrayEquals(bytes(0xFF, 0x02, 0x01, 0x00), Protocol.resetDefault())
        assertArrayEquals(bytes(0xFF, 0x02, 0x02, 0x00), Protocol.clearPairing())
        assertArrayEquals(bytes(0xFF, 0x02, 0x03, 0x00), Protocol.factoryReset())
    }

    @Test
    fun `musicControl packs action byte`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x04, 0x01, 0x01), Protocol.musicControl(0x01)) // play
        assertArrayEquals(bytes(0xFF, 0x03, 0x04, 0x01, 0x04), Protocol.musicControl(0x04)) // next
    }

    @Test
    fun `lightFlash uses 0x01 for on and 0x00 for off`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x05, 0x01, 0x01), Protocol.lightFlash(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x05, 0x01, 0x00), Protocol.lightFlash(false))
    }

    @Test
    fun `toggle commands use 0x01 enable and 0x02 disable`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x06, 0x01, 0x01), Protocol.inEarDetection(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x06, 0x01, 0x02), Protocol.inEarDetection(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x09, 0x01, 0x01), Protocol.lowLatency(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x09, 0x01, 0x02), Protocol.lowLatency(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x0D, 0x01, 0x01), Protocol.testMode(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x10, 0x01, 0x02), Protocol.sleepMode(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x12, 0x01, 0x01), Protocol.ledMode(true))
    }

    @Test
    fun `noiseValue packs raw byte and 0xFF read request`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x07, 0x01, 0x32), Protocol.noiseValue(0x32))
        assertArrayEquals(bytes(0xFF, 0x03, 0x07, 0x01, 0xFF), Protocol.noiseValue(0xFF.toByte()))
    }

    @Test
    fun `volume packs left right and reserved zero byte`() {
        assertArrayEquals(bytes(0xFF, 0x05, 0x08, 0x03, 10, 20, 0x00), Protocol.volume(10, 20))
    }

    @Test
    fun `noiseCancelMode packs single mode byte`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x0C, 0x01, 0x00), Protocol.noiseCancelMode(0x00)) // off
        assertArrayEquals(bytes(0xFF, 0x03, 0x0C, 0x01, 0x01), Protocol.noiseCancelMode(0x01)) // ANC
        assertArrayEquals(bytes(0xFF, 0x03, 0x0C, 0x01, 0x03), Protocol.noiseCancelMode(0x03)) // transparency
    }

    @Test
    fun `ancSetting packs mode subScene and noiseValue`() {
        // Silent Environment ANC level 3, noise depth 0x32
        assertArrayEquals(bytes(0xFF, 0x05, 0x17, 0x03, 0x02, 0x03, 0x32), Protocol.ancSetting(0x02, 0x03, 0x32))
        // Read current value with noiseValue = 0xFF
        assertArrayEquals(
            bytes(0xFF, 0x05, 0x17, 0x03, 0x0A, 0x01, 0xFF),
            Protocol.ancSetting(0x0A, 0x01, 0xFF.toByte())
        )
    }

    @Test
    fun `earTipFitTest packs three identical enable bytes`() {
        // QCYConnectManager.setCompactnessEnable: FF 05 11 03 <1|0> x3
        assertArrayEquals(bytes(0xFF, 0x05, 0x11, 0x03, 0x01, 0x01, 0x01), Protocol.earTipFitTest(true))
        assertArrayEquals(bytes(0xFF, 0x05, 0x11, 0x03, 0x00, 0x00, 0x00), Protocol.earTipFitTest(false))
    }
    @Test
    fun `powerManager packs 16-bit little-endian minutes plus two reserved bytes`() {
        // 300 minutes = 0x012C -> lo=0x2C, hi=0x01
        assertArrayEquals(bytes(0xFF, 0x06, 0x14, 0x04, 0x2C, 0x01, 0x00, 0x00), Protocol.powerManager(300))
        assertArrayEquals(bytes(0xFF, 0x06, 0x14, 0x04, 0x00, 0x00, 0x00, 0x00), Protocol.powerManager(0))
    }

    @Test
    fun `soundBalance packs single value byte`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x16, 0x01, 0x32), Protocol.soundBalance(0x32)) // center
    }

    @Test
    fun `rename packs UTF-8 bytes with string length as paramLen`() {
        assertArrayEquals(bytes(0xFF, 0x04, 0x18, 0x02, 0x41, 0x42), Protocol.rename("AB"))
        // Multi-byte UTF-8: "é" is 2 bytes (0xC3 0xA9)
        assertArrayEquals(bytes(0xFF, 0x04, 0x18, 0x02, 0xC3, 0xA9), Protocol.rename("é"))
    }

    @Test
    fun `voiceLanguage packs language string`() {
        assertArrayEquals(bytes(0xFF, 0x04, 0x19, 0x02, 0x65, 0x6E), Protocol.voiceLanguage("en"))
    }

    @Test
    fun `misc single-byte builders`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x1D, 0x01, 0x50), Protocol.toneVolume(0x50))
        assertArrayEquals(bytes(0xFF, 0x03, 0x1E, 0x01, 0x01), Protocol.takePhoto(0x01))
        assertArrayEquals(bytes(0xFF, 0x03, 0x1F, 0x01, 0x01), Protocol.standby(0x01))
        assertArrayEquals(bytes(0xFF, 0x03, 0x0A, 0x01, 0x05), Protocol.monitoring(0x05))
        assertArrayEquals(bytes(0xFF, 0x03, 0x2E, 0x01, 0x02), Protocol.musicMode(0x02))
        assertArrayEquals(bytes(0xFF, 0x03, 0x37, 0x01, 0x01), Protocol.playMode(0x01))
        assertArrayEquals(bytes(0xFF, 0x03, 0x3D, 0x01, 0x07), Protocol.tonePlay(0x07))
        assertArrayEquals(bytes(0xFF, 0x03, 0x43, 0x01, 0x01), Protocol.aiTrigger(0x01))
        assertArrayEquals(bytes(0xFF, 0x03, 0x48, 0x01, 0x02), Protocol.inEarSensitivity(0x02))
        assertArrayEquals(bytes(0xFF, 0x03, 0x4A, 0x01, 0x01), Protocol.gameConfig(0x01))
        assertArrayEquals(bytes(0xFF, 0x03, 0x29, 0x01, 0x01), Protocol.ancWear(0x01))
    }

    @Test
    fun `boolean toggle builders use 0x01 on and 0x02 off`() {
        assertArrayEquals(bytes(0xFF, 0x03, 0x23, 0x01, 0x01), Protocol.ldac(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x23, 0x01, 0x02), Protocol.ldac(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x27, 0x01, 0x01), Protocol.adaptiveEq(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x2D, 0x01, 0x02), Protocol.spatialAudio(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x32, 0x01, 0x01), Protocol.envAdaptation(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x34, 0x01, 0x02), Protocol.twsEnable(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x35, 0x01, 0x01), Protocol.ledSwitch(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x39, 0x01, 0x02), Protocol.focusMode(false))
        assertArrayEquals(bytes(0xFF, 0x03, 0x45, 0x01, 0x01), Protocol.customEqTest(true))
        assertArrayEquals(bytes(0xFF, 0x03, 0x45, 0x01, 0x02), Protocol.customEqTest(false))
    }

    @Test
    fun `wearingDetection packs 3 bytes v1 and 4 bytes v2`() {
        assertArrayEquals(
            bytes(0xFF, 0x05, 0x2C, 0x03, 0x01, 0x02, 0x03),
            Protocol.wearingDetection(0x01, 0x02, 0x03)
        )
        assertArrayEquals(
            bytes(0xFF, 0x06, 0x2C, 0x04, 0x01, 0x02, 0x03, 0x01),
            Protocol.wearingDetection(0x01, 0x02, 0x03, 0x01)
        )
    }

    @Test
    fun `ledEffect packs speed brightness effect and RGB triplets`() {
        // paramLen = 3 + 3 * numColors
        val packet = Protocol.ledEffect(0x01, 0x64, 0x02, listOf(
            Triple(0xFF.toByte(), 0x00.toByte(), 0x00.toByte()),
            Triple(0x00.toByte(), 0xFF.toByte(), 0x00.toByte())
        ))
        assertArrayEquals(
            bytes(0xFF, 0x0B, 0x36, 0x09, 0x01, 0x64, 0x02, 0xFF, 0x00, 0x00, 0x00, 0xFF, 0x00),
            packet
        )
    }

    @Test
    fun `syncTime packs seven time bytes`() {
        assertArrayEquals(
            bytes(0xFF, 0x09, 0x3E, 0x07, 25, 8, 12, 14, 30, 0, 0x02),
            Protocol.syncTime(25, 8, 12, 14, 30, 0, 0x02)
        )
    }

    @Test
    fun `batteryRead versionRead and maxEqCount are zero-param queries`() {
        assertArrayEquals(bytes(0xFF, 0x02, 0x2F, 0x00), Protocol.batteryRead())
        assertArrayEquals(bytes(0xFF, 0x02, 0x30, 0x00), Protocol.versionRead())
        assertArrayEquals(bytes(0xFF, 0x02, 0x44, 0x00), Protocol.maxEqCount())
    }

    @Test
    fun `requestData wraps target cmdId in 0xFE`() {
        // Request current battery (0x2F) via 0xFE
        assertArrayEquals(bytes(0xFF, 0x03, 0xFE, 0x01, 0x2F), Protocol.requestData(0x2F))
        assertArrayEquals(bytes(0xFF, 0x03, 0xFE, 0x01, 0x0C), Protocol.requestData(0x0C))
    }

    // ── Key function helpers ──────────────────────────────────────────

    @Test
    fun `buildKeyFunctionMap flattens key-function pairs`() {
        val raw = Protocol.buildKeyFunctionMap(listOf(
            Protocol.KEY_LEFT_SINGLE to Protocol.FUN_PLAY_PAUSE,
            Protocol.KEY_RIGHT_DOUBLE to Protocol.FUN_NEXT
        ))
        assertArrayEquals(bytes(0x01, 0x01, 0x04, 0x03), raw)
    }

    @Test
    fun `key and function id constants match protocol tables`() {
        assertEquals(0x0A.toByte(), Protocol.KEY_RIGHT_LONG)
        assertEquals(0x15.toByte(), Protocol.KEY_CALL_LEFT_SINGLE)
        assertEquals(0x1E.toByte(), Protocol.KEY_CALL_RIGHT_LONG)
        assertEquals(0x00.toByte(), Protocol.FUN_NONE)
        assertEquals(0x0B.toByte(), Protocol.FUN_REDIAL)
    }

    // ── Response framing (parsePacket applied to spec example responses) ──
    // Protocol.kt contains no dedicated response decoders; these tests only
    // verify that framed notification payloads are split correctly. Bit-level
    // decoding below follows docs/protocol.md and lives inline in
    // DeviceViewModel/QcyGattClient (not unit-testable here).

    @Test
    fun `battery notification 0x2F yields three param bytes`() {
        // [0xFF, 0x05, 0x2F, 0x03, left, right, box]
        val packet = bytes(0xFF, 0x05, 0x2F, 0x03, 0x80 or 82, 60, 0x80 or 100)
        val blocks = Protocol.parsePacket(packet)
        assertEquals(1, blocks.size)
        assertEquals(0x2F.toByte(), blocks[0].cmdId)
        val p = blocks[0].params
        assertEquals(3, p.size)
        // Decode per spec: bit7 charging, bits0-6 level
        assertEquals(82, p[0].toInt() and 0x7F)
        assertTrue(p[0].toInt() and 0x80 != 0)
        assertEquals(60, p[1].toInt() and 0x7F)
        assertTrue(p[1].toInt() and 0x80 == 0)
        assertEquals(100, p[2].toInt() and 0x7F)
        assertTrue(p[2].toInt() and 0x80 != 0)
    }

    @Test
    fun `version notification 0x30 supports 3-byte and 6-byte forms`() {
        val v3 = Protocol.parsePacket(bytes(0xFF, 0x05, 0x30, 0x03, 1, 0, 5))
        assertEquals(1, v3.size)
        assertEquals(bytes(1, 0, 5).toList(), v3[0].params.toList())

        val v6 = Protocol.parsePacket(bytes(0xFF, 0x08, 0x30, 0x06, 1, 0, 5, 2, 1, 0))
        assertEquals(1, v6.size)
        assertEquals(bytes(1, 0, 5, 2, 1, 0).toList(), v6[0].params.toList())
    }

    @Test
    fun `anc notification 0x17 yields mode subScene noiseValue`() {
        val blocks = Protocol.parsePacket(bytes(0xFF, 0x05, 0x17, 0x03, 0x02, 0x03, 0x50))
        assertEquals(listOf(Protocol.CommandBlock(0x17, bytes(0x02, 0x03, 0x50))), blocks)
    }

    // ── Parametric EQ helpers (0x20 v1, 0x22/0x46/0x47 v2) ────────────

    @Test
    fun `packParametricEq v1 uses 6 bytes per band with 16-bit LE fields`() {
        // cmd 0x20, eqIndex 1, master +2.0 dB, one band: 1000 Hz, -3.5 dB, Q 1.5
        val packet = Protocol.packParametricEq(0x20, 1, 2.0f, listOf(Protocol.EqBand(1000, -3.5f, 1.5f)))
        // paramLen = 6 * 1 + 3 = 9
        assertArrayEquals(
            bytes(
                0xFF, 0x0B, 0x20, 0x09,
                0x01,             // eqIndex
                0xC8, 0x00,       // masterGain 200 = 2.0 dB * 100, LE
                0xE8, 0x03,       // freq 1000 LE
                0xA2, 0xFE,       // gain -350 (0xFEA2) LE
                0x96, 0x00        // q 150 LE
            ),
            packet
        )
    }

    @Test
    fun `packParametricEq v2 appends bandType byte per band`() {
        val packet = Protocol.packParametricEq(0x22, 0, 0.0f, listOf(Protocol.EqBand(100, 1.0f, 1.0f, 5)))
        // paramLen = 7 * 1 + 3 = 10
        assertArrayEquals(
            bytes(
                0xFF, 0x0C, 0x22, 0x0A,
                0x00, 0x00, 0x00, // eqIndex, masterGain 0
                0x64, 0x00,       // freq 100 LE
                0x64, 0x00,       // gain 100 = 1.0 dB * 100
                0x64, 0x00,       // q 100
                0x05              // bandType
            ),
            packet
        )
        // 0x46 / 0x47 are also v2 format
        assertEquals(10 + 2, Protocol.packParametricEq(0x46, 0, 0f, listOf(Protocol.EqBand(100, 1f, 1f))).size - 2)
    }

    @Test
    fun `packParametricEq sends band gain unclamped like the official app`() {
        // Official setEQWtihQFG sends raw dB*100 with no +-12.7 clamp
        // (smali QCONST path); 20 dB -> 2000 = 0x07D0 LE -> bytes D0 07.
        val packet = Protocol.packParametricEq(0x20, 0, 0f, listOf(Protocol.EqBand(500, 20.0f, 1.0f)))
        assertEquals(0xD0.toByte(), packet[9])
        assertEquals(0x07.toByte(), packet[10])
    }

    @Test
    fun `parseParametricEq round-trips v1 and v2 packets`() {
        val bands = listOf(
            Protocol.EqBand(1000, -3.5f, 1.5f),
            Protocol.EqBand(125, 2.0f, 1.0f, 3)
        )
        val v1 = Protocol.parsePacket(Protocol.packParametricEq(0x20, 4, 0f, bands)).single()
        val decoded1 = Protocol.parseParametricEq(0x20, v1.params)!!
        assertEquals(4, decoded1.eqIndex)
        assertEquals(0f, decoded1.masterGainDb, 0.001f)
        assertEquals(2, decoded1.bands.size)
        assertEquals(1000, decoded1.bands[0].freq)
        assertEquals(-3.5f, decoded1.bands[0].gainDb, 0.001f)
        assertEquals(1.5f, decoded1.bands[0].q, 0.001f)
        assertEquals(0, decoded1.bands[0].bandType) // v1 carries no bandType

        val v2 = Protocol.parsePacket(Protocol.packParametricEq(0x47, 2, 0f, bands)).single()
        val decoded2 = Protocol.parseParametricEq(0x47, v2.params)!!
        assertEquals(2, decoded2.eqIndex)
        assertEquals(3, decoded2.bands[1].bandType)
        assertEquals(125, decoded2.bands[1].freq)
        assertEquals(2.0f, decoded2.bands[1].gainDb, 0.001f)
    }

    @Test
    fun `parseParametricEq rejects malformed params`() {
        assertEquals(null, Protocol.parseParametricEq(0x20, bytes(0x01, 0x00))) // < 3 bytes
        assertEquals(null, Protocol.parseParametricEq(0x20, bytes(0x01, 0x00, 0x00, 0x01))) // remainder 1 not divisible by 6
        assertEquals(null, Protocol.parseParametricEq(0x22, bytes(0x01, 0x00, 0x00, 0x01, 0x02, 0x03))) // remainder 3 not divisible by 7
        // Official EQParamListBean requires length > 9: a single v1 band
        // (3 + 6 = 9 bytes) is rejected too.
        assertEquals(null, Protocol.parseParametricEq(0x20, bytes(0x00, 0x00, 0x00, 0x64, 0x00, 0x64, 0x00, 0x64, 0x00)))
    }

    @Test
    fun `parseParametricEq stride is selected by cmdId not by length`() {
        // 12 bytes of band data: valid v1 0x20 (two bands) but rejected for
        // v2 0x22 (12 not divisible by 7).
        val params = bytes(
            0x00, 0x00, 0x00,
            0x64, 0x00, 0x64, 0x00, 0x64, 0x00,
            0x64, 0x00, 0x64, 0x00, 0x64, 0x00
        )
        assertEquals(null, Protocol.parseParametricEq(0x22, params))
        val parsed = Protocol.parseParametricEq(0x20, params)
        assertEquals(2, parsed!!.bands.size)
    }

    // ── alarm / music builders (AlarmDataBean + setMusicStatus/Info) ──

    @Test
    fun `alarm add packs op 1 with literal trailing 0x05`() {
        val packet = Protocol.alarmAdd(0x02, true, 7, 30, 0x1F)
        // FF 09 3F 07 [01 02 01 07 1E 1F 05]
        assertEquals(0xFF.toByte(), packet[0])
        assertEquals(9, packet[1].toInt() and 0xFF)
        assertEquals(0x3F.toByte(), packet[2])
        assertEquals(7, packet[3].toInt() and 0xFF)
        assertArrayEquals(
            bytes(0x01, 0x02, 0x01, 0x07, 0x1E, 0x1F, 0x05),
            packet.copyOfRange(4, 11)
        )
    }

    @Test
    fun `alarm delete packs seven zero params`() {
        val packet = Protocol.alarmDelete(0x02)
        assertEquals(0x3F.toByte(), packet[2])
        assertEquals(7, packet[3].toInt() and 0xFF)
        assertArrayEquals(bytes(0x02, 0x02, 0, 0, 0, 0, 0), packet.copyOfRange(4, 11))
    }

    @Test
    fun `music status packs id little-endian with status and p4`() {
        val packet = Protocol.musicStatus(0x01020304L, 0x01)
        assertEquals(0x3A.toByte(), packet[2])
        assertEquals(6, packet[3].toInt() and 0xFF)
        assertArrayEquals(bytes(0x04, 0x03, 0x02, 0x01, 0x01, 0x00), packet.copyOfRange(4, 10))
    }

    @Test
    fun `music info packs leading zero and six-byte entries`() {
        val packet = Protocol.musicInfo(listOf(0x01020304L to 0x0123))
        assertEquals(0x3B.toByte(), packet[2])
        assertEquals(7, packet[3].toInt() and 0xFF)
        assertArrayEquals(
            bytes(0x00, 0x04, 0x03, 0x02, 0x01, 0x23, 0x01),
            packet.copyOfRange(4, 11)
        )
    }
}
