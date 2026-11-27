package com.hui1601.quickyandroid.util

import com.hui1601.quickyandroid.ble.Protocol
import org.junit.Assert.assertEquals
import org.junit.Test

class EqResponseTest {

    private fun band(freq: Int, gain: Float, q: Float) = Protocol.EqBand(
        freq = freq,
        gainDb = gain,
        q = q,
        bandType = Protocol.EqFilterType.PEAKING.wireValue
    )

    @Test
    fun `peaking band reaches its gain at the center frequency`() {
        assertEquals(6f, eqResponseDb(listOf(band(1_000, 6f, 1f)), 0f, 1_000f), 0.05f)
        assertEquals(-4.5f, eqResponseDb(listOf(band(100, -4.5f, 0.7f)), 0f, 100f), 0.05f)
    }

    @Test
    fun `pre-gain offsets the summed response`() {
        assertEquals(-3.5f, eqResponseDb(emptyList(), -3.5f, 1_000f), 0.001f)
        assertEquals(
            2.5f,
            eqResponseDb(listOf(band(1_000, 6f, 1f)), -3.5f, 1_000f),
            0.05f
        )
    }

    @Test
    fun `adjacent bands add in dB at the shared center`() {
        val bands = listOf(band(1_000, 3f, 1f), band(1_000, 2f, 1f))
        assertEquals(5f, eqResponseDb(bands, 0f, 1_000f), 0.05f)
    }

    @Test
    fun `response is near flat far from a narrow band`() {
        assertEquals(0f, eqResponseDb(listOf(band(1_000, 6f, 3f)), 0f, 20f), 0.2f)
    }
}
