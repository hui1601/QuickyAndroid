package com.hui1601.quickyandroid

import com.hui1601.quickyandroid.ui.screens.projectEqGains
import org.junit.Assert.assertEquals
import org.junit.Test

class EqGainsTest {

    @Test
    fun `projects a 20-slot device read-back onto 10 UI bands`() {
        val gains = (1..20).map { it.toFloat() }
        val projected = projectEqGains(gains, 10)
        assertEquals(10, projected.size)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f), projected)
    }

    @Test
    fun `pads short device read-backs with 0 dB`() {
        val projected = projectEqGains(listOf(2f, -3f), 5)
        assertEquals(listOf(2f, -3f, 0f, 0f, 0f), projected)
    }

    @Test
    fun `keeps exact-size read-backs unchanged`() {
        val gains = listOf(1f, 2f, 3f)
        assertEquals(gains, projectEqGains(gains, 3))
    }
}
