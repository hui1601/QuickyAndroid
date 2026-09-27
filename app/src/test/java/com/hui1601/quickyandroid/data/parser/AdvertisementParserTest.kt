package com.hui1601.quickyandroid.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Advertisement payload parser tests — layouts verified against
 * Devicebind.refreshInfoFromAVData (smali:3382). The >24-byte case is the
 * regression for the crash reported when a real scan result arrived
 * (System.arraycopy into a fixed 24-byte staging buffer threw).
 */
class AdvertisementParserTest {

    private fun payload(len: Int, fill: (Int) -> Int): ByteArray =
        ByteArray(len) { fill(it).toByte() }

    @Test
    fun `payloads shorter than 20 bytes are ignored`() {
        assertNull(AdvertisementParser.parsePayload(ByteArray(19), "AA", "x", -60))
        assertNull(AdvertisementParser.parsePayload(ByteArray(0), "AA", "x", -60))
    }

    @Test
    fun `payloads longer than 24 bytes parse without crashing`() {
        // 30-byte advertisement: the pre-fix code copied this into a 24-byte
        // buffer and threw ArrayIndexOutOfBoundsException.
        val long = payload(30) { i ->
            when (i) {
                0 -> 0x11; 1 -> 0xE7                    // vendorId 0x11E7
                5 -> 0x80 or 80                         // left 80, charging
                6 -> 70                                 // right 70
                7 -> 0x80 or 60                         // box 60, charging
                11 -> 0x11; 12 -> 0x22; 13 -> 0x33      // control MAC bytes
                14 -> 0x44; 15 -> 0x55; 16 -> 0x66
                18 -> 0xAA; 19 -> 0xBB; 20 -> 0xCC      // other MAC bytes
                21 -> 0xDD; 22 -> 0xEE; 23 -> 0xFF
                else -> 0x00
            }
        }
        val device = AdvertisementParser.parsePayload(long, "11:22:33:44:55:66", "QCY", -55)
        assertNotNull(device)
        assertEquals(0x11E7, device!!.vendorId)
        assertEquals(80, device.leftBattery)
        assertTrue(device.isLeftCharging)
        assertEquals(70, device.rightBattery)
        assertEquals(60, device.caseBattery)
        assertTrue(device.isCaseCharging)
        // MACs printed as [12],[11],[13],[16],[15],[14]
        assertEquals("22:11:33:66:55:44", device.controlMac)
        // Other MAC printed as [19],[18],[20],[23],[22],[21] — non-zero, kept
        assertEquals("BB:AA:CC:FF:EE:DD", device.otherMac)
    }

    @Test
    fun `short payload zero-pads macs like the original`() {
        val short = payload(20) { i ->
            when (i) {
                0 -> 0x11; 1 -> 0xE7
                12 -> 0x22; 13 -> 0x33
                else -> 0x00
            }
        }
        val device = AdvertisementParser.parsePayload(short, "AA:BB:CC:DD:EE:FF", null, -60)
        assertNotNull(device)
        // offsets 11,14,15,16 read as 0 (padded); 12,13 carry real bytes
        assertEquals("22:00:33:00:00:00", device!!.controlMac)
        // other MAC is all zeros -> substituted with controlMac
        assertEquals(device.controlMac, device.otherMac)
    }

    @Test
    fun `box battery hidden for 0xBC0 family and T11S`() {
        fun boxFor(vendorIdHi: Int, vendorIdLo: Int): Int {
            val data = payload(20) { i -> if (i == 0) vendorIdHi else if (i == 1) vendorIdLo else if (i == 7) 0x80 or 50 else 0x00 }
            return AdvertisementParser.parsePayload(data, "AA", null, -60)!!.caseBattery
        }
        assertEquals(0, boxFor(0x0B, 0xC5)) // 0x0BC5 & 0xFF0 == 0xBC0
        assertEquals(0, boxFor(0x4D, 0x17)) // T11S
        assertEquals(50, boxFor(0x11, 0xE7)) // everyone else keeps the level
    }

    @Test
    fun `charging flag cleared when battery level is out of range`() {
        val data = payload(20) { i ->
            when (i) {
                0 -> 0x11; 1 -> 0xE7
                5 -> 0x80 or 0x7F          // level 127 -> invalid, charging dropped
                6 -> 0x80 or 0x00          // level 0 -> invalid, charging dropped
                7 -> 0x80 or 50            // box keeps flag (original behavior)
                else -> 0x00
            }
        }
        val device = AdvertisementParser.parsePayload(data, "AA", null, -60)!!
        assertEquals(127, device.leftBattery)
        assertEquals(true, !device.isLeftCharging)
        assertEquals(true, !device.isRightCharging)
        assertEquals(true, device.isCaseCharging)
    }
}
