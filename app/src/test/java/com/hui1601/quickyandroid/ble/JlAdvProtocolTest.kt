package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Old-JL ADV-opcode family tests — byte-exact port of JLDeviceImpl /
 * BtRcspOpImpl / ParseHelper (see JlAdvProtocol KDoc for smali citations).
 */
class JlAdvProtocolTest {

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    // ── request builders ──────────────────────────────────────────────

    @Test
    fun `modifyAdvLtv packs len op and data`() {
        // packLTVPacket: len includes the type byte (ParseDataUtil.smali:227)
        assertArrayEquals(bytes(0x02, 0x05, 0x02), JlAdvProtocol.modifyAdvLtv(5, bytes(0x02)))
        assertArrayEquals(
            bytes(0x05, 0x07, 0x63, 0x33, 0x87, 0x21),
            JlAdvProtocol.modifyAdvLtv(7, bytes(0x63, 0x33, 0x87, 0x21))
        )
    }

    @Test
    fun `getAdvMask is big-endian`() {
        assertArrayEquals(bytes(0x00, 0x00, 0x0D, 0x21), JlAdvProtocol.getAdvMask(JlAdvProtocol.MASK_BATTERY))
        assertArrayEquals(bytes(0xFF, 0xFF, 0xFF, 0xFF), JlAdvProtocol.getAdvMask(JlAdvProtocol.MASK_ALL))
    }

    @Test
    fun `game and sleep use inverted polarity`() {
        // setGameMode smali:2517-2526 / setSleepMode smali:3219-3225: on=2, off=1
        assertEquals(0x02.toByte(), JlAdvProtocol.invertedBool(true))
        assertEquals(0x01.toByte(), JlAdvProtocol.invertedBool(false))
    }

    @Test
    fun `function 0 maps to JL 0x7F and back`() {
        assertEquals(0x7F.toByte(), JlAdvProtocol.funcToJl(0x00))
        assertEquals(0x05.toByte(), JlAdvProtocol.funcToJl(0x05))
        assertEquals(0x00.toByte(), JlAdvProtocol.funcFromJl(0x7F))
        assertEquals(0x05.toByte(), JlAdvProtocol.funcFromJl(0x05))
    }

    @Test
    fun `key ids map to side-clicks pairs and back`() {
        assertEquals(1 to 1, JlAdvProtocol.keyIdToSideClicks(1))
        assertEquals(2 to 1, JlAdvProtocol.keyIdToSideClicks(2))
        assertEquals(1 to 2, JlAdvProtocol.keyIdToSideClicks(3))
        assertEquals(2 to 2, JlAdvProtocol.keyIdToSideClicks(4))
        assertEquals(1 to 3, JlAdvProtocol.keyIdToSideClicks(5))
        assertEquals(2 to 3, JlAdvProtocol.keyIdToSideClicks(6))
        assertNull(JlAdvProtocol.keyIdToSideClicks(7)) // quad press: no old-JL encoding

        (1..6).forEach { id ->
            val (side, clicks) = JlAdvProtocol.keyIdToSideClicks(id)!!
            assertEquals(id.toByte(), JlAdvProtocol.sideClicksToKeyId(side, clicks))
        }
        assertNull(JlAdvProtocol.sideClicksToKeyId(3, 1))
    }

    // ── ADV info response parse (parseADVInfo) ────────────────────────

    /** Wrap data in an LTV: [len = data.size + 1, type, data...]. */
    private fun ltv(type: Int, data: ByteArray) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data

    @Test
    fun `parses battery language sleep noise and balance attributes`() {
        val payload = ltv(0x0, bytes(0x52, 0x3C, 0x64)) +          // battery L=82 R=60 box=100
            ltv(0x9, "en".toByteArray()) +                          // language
            ltv(0xA, bytes(0x02)) +                                 // sleep: on (inverted)
            ltv(0xC, bytes(0x32)) +                                 // balance 50
            ltv(0xB, bytes(0x01, 0x06, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x05)) // noise mode (gains BE16)

        val attrs = JlAdvProtocol.parseAdvInfo(payload)
        assertEquals(5, attrs.size)
        val battery = attrs[0] as JlAdvProtocol.AdvAttr.Battery
        assertEquals(0x52.toByte(), battery.left)
        assertEquals(0x3C.toByte(), battery.right)
        assertEquals(0x64.toByte(), battery.box)
        assertEquals("en", (attrs[1] as JlAdvProtocol.AdvAttr.Language).lang)
        assertEquals(2, (attrs[2] as JlAdvProtocol.AdvAttr.SleepMode).raw)
        assertEquals(50, (attrs[3] as JlAdvProtocol.AdvAttr.VolumeBalance).value)
        val noise = attrs[4] as JlAdvProtocol.AdvAttr.NoiseMode
        assertEquals(1, noise.mode)
        assertEquals(0, noise.minGain)
        assertEquals(10, noise.maxGain)
        assertEquals(5, noise.currentGain)
    }

    @Test
    fun `parses key settings triplets and device name`() {
        val payload = ltv(0x2, bytes(0x01, 0x01, 0x05, 0x02, 0x02, 0x7F)) +
            ltv(0x1, "QCY T13".toByteArray())
        val attrs = JlAdvProtocol.parseAdvInfo(payload)
        val keys = attrs[0] as JlAdvProtocol.AdvAttr.KeySettings
        assertEquals(listOf(Triple(1, 1, 5), Triple(2, 2, 0x7F)), keys.triplets)
        assertEquals("QCY T13", (attrs[1] as JlAdvProtocol.AdvAttr.DeviceName).name)
    }

    @Test
    fun `unknown attribute types are skipped`() {
        // type 0x6 (vid/uid/pid) and 0x5 (work model) are not surfaced
        val payload = ltv(0x6, bytes(1, 2, 3, 4, 5, 6)) + ltv(0xC, bytes(0x19))
        val attrs = JlAdvProtocol.parseAdvInfo(payload)
        assertEquals(1, attrs.size)
        assertEquals(25, (attrs[0] as JlAdvProtocol.AdvAttr.VolumeBalance).value)
    }

    @Test
    fun `malformed ltv stops the walk`() {
        // len byte promises more data than remains — original bails (cond_5)
        val attrs = JlAdvProtocol.parseAdvInfo(bytes(0x09, 0x0C, 0x32))
        assertEquals(0, attrs.size)
    }

    // ── QCY-command routing (routeSettings) ───────────────────────────

    @Test
    fun `game on routes to ADV op 5 with inverted byte`() {
        val (opcode, paramData) = JlAdvProtocol.routeSettings(0x09, bytes(0x01)).single()
        assertEquals(0xC0.toByte(), opcode)
        // LTV [len 2, op 5, 0x02 = on]
        assertArrayEquals(bytes(0x02, 0x05, 0x02), paramData)
    }

    @Test
    fun `in-ear detect keeps standard 1-2 polarity on op 8`() {
        val (_, on) = JlAdvProtocol.routeSettings(0x06, bytes(0x01)).single()
        assertArrayEquals(bytes(0x02, 0x08, 0x01), on)
        val (_, off) = JlAdvProtocol.routeSettings(0x06, bytes(0x02)).single()
        assertArrayEquals(bytes(0x02, 0x08, 0x02), off)
    }

    @Test
    fun `key map expands per key and maps function 0 to 0x7F`() {
        val writes = JlAdvProtocol.routeSettings(0x2B, bytes(0x01, 0x05, 0x03, 0x00))
        assertEquals(2, writes.size)
        // L1 -> LTV [len 4, op 2, side 1, clicks 1, func 5]; L2 func none -> 0x7F
        assertArrayEquals(bytes(0x04, 0x02, 0x01, 0x01, 0x05), writes[0].second)
        assertArrayEquals(bytes(0x04, 0x02, 0x01, 0x02, 0x7F), writes[1].second)
    }

    @Test
    fun `plain ANC mode and unsupported commands route to nothing`() {
        assertEquals(0, JlAdvProtocol.routeSettings(0x0C, bytes(0x01)).size)
        assertEquals(0, JlAdvProtocol.routeSettings(0x18, "x".toByteArray()).size)
        // quad-press key id has no old-JL encoding — whole write aborts
        assertEquals(0, JlAdvProtocol.routeSettings(0x2B, bytes(0x07, 0x01)).size)
    }

    @Test
    fun `ANC sense mode stays on the 0xFF custom tunnel`() {
        val (opcode, paramData) = JlAdvProtocol.routeSettings(0x17, bytes(0x03, 0x02, 0x00)).single()
        assertEquals(0xFF.toByte(), opcode)
        assertArrayEquals(bytes(0x17, 0x03, 0x03, 0x02, 0x00), paramData)
    }

    @Test
    fun `noiseModeData packs mode length and big-endian gains`() {
        // NoiseMode.toData: [mode, 6, min BE16, max BE16, cur BE16]
        assertArrayEquals(
            bytes(0x01, 0x06, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x05),
            JlAdvProtocol.noiseModeData(mode = 1, minGain = 0, maxGain = 10, currentGain = 5)
        )
        assertArrayEquals(
            bytes(0x02, 0x06, 0x00, 0x64, 0x01, 0x90, 0x00, 0xC8),
            JlAdvProtocol.noiseModeData(mode = 2, minGain = 100, maxGain = 400, currentGain = 200)
        )
    }
}
