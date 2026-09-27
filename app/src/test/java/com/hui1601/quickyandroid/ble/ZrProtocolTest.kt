package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ZR vendor codec tests — ZrProtocol is a byte-exact port of ZRDeviceImpl
 * (/data/projects/Quicky/tmp/smali/classes2/com/qcymall/earphonesetup/manager/
 * ZRDeviceImpl.smali): key-id remaps (smali:392/441), 6-byte time sync
 * (QCYConnectManager.smali:4103-4121) and the 144-byte biquad EQ packet
 * (setEQ smali:941-1404).
 */
class ZrProtocolTest {

    // ── key-id remap ──────────────────────────────────────────────────

    @Test
    fun `key ids remap to ZR wire numbering and back`() {
        // converCmdIDToZR: 1->1, 2->4, 3->2, 4->5, 5->3, 6->6
        assertEquals(1, ZrProtocol.keyIdToZr(1))
        assertEquals(4, ZrProtocol.keyIdToZr(2))
        assertEquals(2, ZrProtocol.keyIdToZr(3))
        assertEquals(5, ZrProtocol.keyIdToZr(4))
        assertEquals(3, ZrProtocol.keyIdToZr(5))
        assertEquals(6, ZrProtocol.keyIdToZr(6))
        // others pass through
        assertEquals(9, ZrProtocol.keyIdToZr(9))

        // converCmdIDToQCY is the exact inverse
        assertEquals(1, ZrProtocol.keyIdFromZr(1))
        assertEquals(3, ZrProtocol.keyIdFromZr(2))
        assertEquals(5, ZrProtocol.keyIdFromZr(3))
        assertEquals(2, ZrProtocol.keyIdFromZr(4))
        assertEquals(4, ZrProtocol.keyIdFromZr(5))
        assertEquals(6, ZrProtocol.keyIdFromZr(6))
        assertEquals(9, ZrProtocol.keyIdFromZr(9))
    }

    @Test
    fun `remap round-trips for all six supported keys`() {
        (1..6).forEach { key ->
            assertEquals(key, ZrProtocol.keyIdFromZr(ZrProtocol.keyIdToZr(key)))
        }
    }

    // ── time sync ─────────────────────────────────────────────────────

    @Test
    fun `time sync payload is 2 zero bytes plus big-endian epoch seconds`() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()),
            ZrProtocol.timeSyncPayload(0x12345678L)
        )
    }

    // ── EQ packet ─────────────────────────────────────────────────────

    @Test
    fun `identity coefficients are Q10_21 unity`() {
        // QCONST32(1.0) = 0x200000, LE bytes 00 00 20 00
        val packet = ZrProtocol.buildEqPacket(1, byteArrayOf(0, 0, 0, 0, 0))
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x20, 0x00),
            packet.copyOfRange(0, 4)
        )
        // fifth band starts at 4*24 = 96
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x20, 0x00),
            packet.copyOfRange(96, 100)
        )
        // a0 slot of band 0 at offset 12 is unity too
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x20, 0x00),
            packet.copyOfRange(12, 16)
        )
    }

    @Test
    fun `eqType 1 packet carries type byte gains trailer and checksum`() {
        val gains = byteArrayOf(5, 10, -3, 0, 1)
        val packet = ZrProtocol.buildEqPacket(1, gains)
        assertEquals(144, packet.size)
        assertEquals(1, packet[120].toInt())
        assertArrayEquals(gains, packet.copyOfRange(121, 126))
        assertEquals(0x5A, packet[126].toInt())
        assertEquals(0x5A, packet[127].toInt())
        // checksum: 5 identity band checksums (0x40 each, mod 256 -> 0x40)
        // + 0x5A + 0x5A + sum of gain bytes (unsigned)
        val gainSum = gains.sumOf { it.toInt() and 0xFF }
        val expected = (0x40 + 0x5A + 0x5A + gainSum) and 0xFF
        assertEquals(expected.toByte(), packet[143])
    }

    @Test
    fun `custom packet uses default freq table and zero-gain bands are identity`() {
        val packet = ZrProtocol.buildEqPacket(2, byteArrayOf(0, 8, 0, 0, 0))
        assertEquals(2, packet[120].toInt())
        // zero-gain band -> identity coefficients (band 0 and band 2)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x20, 0x00), packet.copyOfRange(0, 4))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x20, 0x00), packet.copyOfRange(48, 52))
        // non-zero band (gain 8 at q 1.5 / 250 Hz) must differ from identity
        val nonIdentity = packet.copyOfRange(24, 28)
        org.junit.Assert.assertFalse(java.util.Arrays.equals(byteArrayOf(0x00, 0x00, 0x20, 0x00), nonIdentity))
        // a0 slot of a computed band is still unity
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x20, 0x00), packet.copyOfRange(36, 40))
    }

    @Test
    fun `explicit freq table is appended little-endian and folded into checksum`() {
        val gains = byteArrayOf(1, 2, 3, 4, 5)
        val freqs = intArrayOf(62, 250, 1000, 4000, 8000)
        val packet = ZrProtocol.buildEqPacket(3, gains, freqs)
        assertEquals(3, packet[120].toInt())
        // trailer at 121 + 5 = 126..127, freq table from 128
        assertEquals(0x5A, packet[126].toInt())
        assertEquals(0x5A, packet[127].toInt())
        assertEquals(0x3E, packet[128].toInt() and 0xFF) // 62 lo
        assertEquals(0x00, packet[129].toInt() and 0xFF) // 62 hi
        assertEquals(0xFA, packet[130].toInt() and 0xFF) // 250 lo
        assertEquals(0x40, packet[136].toInt() and 0xFF) // 8000 = 0x1F40, lo byte (entry 4 at 128+2*4)
        assertEquals(0x1F, packet[137].toInt() and 0xFF) // high byte
    }

    @Test
    fun `getCoeff returns identity for zero gain`() {
        assertArrayEquals(
            intArrayOf(0x200000, 0, 0, 0x200000, 0, 0),
            ZrProtocol.getCoeff(0.0, 1.5, 1000)
        )
    }

    // ── EQ read parse ─────────────────────────────────────────────────

    @Test
    fun `eq read extracts eqType and five gains from offset 0x78`() {
        val data = ByteArray(0x7E)
        data[0x78] = 2
        data[0x79] = 10
        data[0x7A] = -5
        data[0x7B] = 0
        data[0x7C] = 3
        data[0x7D] = 12
        val parsed = ZrProtocol.parseEqRead(data)!!
        assertEquals(2, parsed.first)
        assertEquals(listOf(1.0f, -0.5f, 0.0f, 0.3f, 1.2f), parsed.second)
    }

    @Test
    fun `eq read rejects short buffers`() {
        assertNull(ZrProtocol.parseEqRead(ByteArray(0x7D)))
    }

    private fun assertFalse(v: Boolean) = org.junit.Assert.assertFalse(v)
}
