package com.hui1601.quickyandroid.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AncModeSelectionTest {

    private fun mode(
        name: String,
        start: Int,
        end: Int = start,
        default: Int = start,
        items: List<AncItem> = emptyList()
    ) = AncMode(
        name = name,
        startCmdId = start,
        endCmdId = end,
        defaultCmd = default,
        items = items
    )

    private fun item(name: String, start: Int, end: Int = start) =
        AncItem(name = name, startCmdId = start, endCmdId = end)

    /** MeloBuds Pro / N60 family: packed 24-bit catalog commands. */
    private val packedModes = listOf(
        mode(
            name = "Noise canceling",
            start = 65_792,
            end = 131_071,
            default = 65_794,
            items = listOf(
                item("Indoor", 65_792, 65_794),
                item("Commuting", 66_048, 66_050)
            )
        ),
        mode("Transparency", 196_864, 196_870, 196_868),
        mode("Normal", 131_072)
    )

    /** HT07 / ArcBuds family: legacy single-byte range commands. */
    private val legacyModes = listOf(
        mode(
            name = "Noise canceling",
            start = 65,
            end = 159,
            default = 99,
            items = listOf(item("Indoor", 65, 67), item("Noisy", 97, 99))
        ),
        mode("Transparency", 160, 166, 164),
        mode("Normal", 2, 2, 2)
    )

    @Test
    fun `device mode byte wins over a stale ANC selection`() {
        // Earbud switched to Transparency; 0x0C left selectedAncCmdId at Indoor.
        val active = packedModes.findActiveAncMode(
            ancMode = 0x03,
            selectedCmdId = 65_794
        )

        assertEquals("Transparency", active?.name)
        assertEquals(196_868, active?.effectiveCmdId(65_794))
        assertNull(active?.findSelectedSubItem(196_868))
    }

    @Test
    fun `off hides every catalog mode`() {
        assertNull(packedModes.findActiveAncMode(ancMode = 0, selectedCmdId = 65_794))
    }

    @Test
    fun `legacy range commands resolve directly`() {
        assertEquals(
            "Noise canceling",
            legacyModes.findActiveAncMode(ancMode = 99, selectedCmdId = 99)?.name
        )
        assertEquals(
            "Transparency",
            legacyModes.findActiveAncMode(ancMode = 164, selectedCmdId = 164)?.name
        )
    }

    @Test
    fun `basic mode byte resolves legacy products`() {
        assertEquals(
            "Transparency",
            legacyModes.findActiveAncMode(ancMode = 0x03, selectedCmdId = 99)?.name
        )
    }

    @Test
    fun `vocal boost maps to transparency`() {
        assertEquals(
            "Transparency",
            packedModes.findActiveAncMode(ancMode = 0x0E, selectedCmdId = 65_794)?.name
        )
    }

    @Test
    fun `sub-item lookup stays inside the active mode`() {
        val anc = packedModes.first { it.name == "Noise canceling" }
        assertEquals("Indoor", anc.findSelectedSubItem(65_792)?.name)
        assertEquals("Indoor", anc.findSelectedSubItem(65_794)?.name)
        assertNull(anc.findSelectedSubItem(65_795))

        val transparency = packedModes.first { it.name == "Transparency" }
        assertNull(transparency.findSelectedSubItem(65_794))
    }

    @Test
    fun `effective command falls back to the mode default`() {
        val transparency = packedModes.first { it.name == "Transparency" }
        assertEquals(196_868, transparency.effectiveCmdId(65_794))
        assertEquals(196_869, transparency.effectiveCmdId(196_869))
    }
}
