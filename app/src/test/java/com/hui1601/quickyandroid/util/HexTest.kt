package com.hui1601.quickyandroid.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexTest {

    @Test
    fun `parses plain and separated hex`() {
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), Hex.parseBytes("0a0b"))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), Hex.parseBytes("0A 0B"))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), Hex.parseBytes("0A:0b"))
        assertArrayEquals(ByteArray(0), Hex.parseBytes(""))
        assertArrayEquals(byteArrayOf(0xFF.toByte()), Hex.parseBytes("ff"))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(Hex.parseBytes("0a0"))      // odd length
        assertNull(Hex.parseBytes("zz"))       // non-hex
        assertNull(Hex.parseBytes("0a-0b"))    // stray separator
    }

    @Test
    fun `formats bytes and single values`() {
        assertEquals("0a 0b ff", Hex.format(byteArrayOf(0x0A, 0x0B, 0xFF.toByte())))
        assertEquals("ff", Hex.format(0xFF))
        assertEquals("07", Hex.format(0x07))
    }
}
