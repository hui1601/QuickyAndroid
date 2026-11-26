package com.hui1601.quickyandroid.data.parser

import com.hui1601.quickyandroid.ble.Protocol
import com.hui1601.quickyandroid.data.repository.AutoEqEntry
import com.hui1601.quickyandroid.data.repository.normalizeProfileUrl
import com.hui1601.quickyandroid.data.repository.parametricEqUrl
import com.hui1601.quickyandroid.data.repository.parseAutoEqIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqProfileParserTest {

    private fun success(text: String): AutoEqProfile {
        val result = AutoEqProfileParser.parse(text)
        assertTrue("expected success, got $result", result is AutoEqParseResult.Success)
        return (result as AutoEqParseResult.Success).profile
    }

    @Test
    fun `parses a ParametricEQ profile and maps filter types`() {
        val profile = success(
            """
            Preamp: -6.1 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
            Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
            Filter 3: OFF PK Fc 118 Hz Gain -3.1 dB Q 0.50
            Filter 4: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
            """.trimIndent()
        )

        assertEquals(-6.1f, profile.preGainDb, 0.001f)
        assertEquals(3, profile.bands.size)

        val shelf = profile.bands[0]
        assertEquals(105, shelf.freq)
        assertEquals(6.4f, shelf.gainDb, 0.001f)
        assertEquals(0.70f, shelf.q, 0.001f)
        assertEquals(Protocol.EqFilterType.LOW_SHELF.wireValue, shelf.bandType)

        val peak = profile.bands[1]
        assertEquals(8800, peak.freq)
        assertEquals(5.1f, peak.gainDb, 0.001f)
        assertEquals(1.42f, peak.q, 0.001f)
        assertEquals(Protocol.EqFilterType.PEAKING.wireValue, peak.bandType)

        // The firmware has no high-shelf type; HSC becomes a peaking band.
        val highShelf = profile.bands[2]
        assertEquals(10_000, highShelf.freq)
        assertEquals(-2.1f, highShelf.gainDb, 0.001f)
        assertEquals(Protocol.EqFilterType.PEAKING.wireValue, highShelf.bandType)
    }

    @Test
    fun `understands kHz frequencies and a missing preamp`() {
        val profile = success(
            "Filter 1: ON PK Fc 3.5 kHz Gain -2.0 dB Q 1.50"
        )

        assertEquals(0f, profile.preGainDb, 0f)
        assertEquals(3500, profile.bands.single().freq)
        assertEquals(-2.0f, profile.bands.single().gainDb, 0.001f)
    }

    @Test
    fun `skips disabled and unknown filter types`() {
        val profile = success(
            """
            Filter 1: OFF PK Fc 200 Hz Gain 4.0 dB Q 1.0
            Filter 2: ON XX Fc 400 Hz Gain 2.0 dB Q 1.0
            Filter 3: ON PK Fc 800 Hz Gain 1.0 dB Q 1.0
            """.trimIndent()
        )

        assertEquals(listOf(800), profile.bands.map { it.freq })
    }

    @Test
    fun `caps imported bands at the firmware limit`() {
        val text = buildString {
            appendLine("Preamp: -3 dB")
            repeat(25) { index ->
                appendLine(
                    "Filter ${index + 1}: ON PK Fc ${100 + index * 100} Hz " +
                        "Gain 1.0 dB Q 1.00"
                )
            }
        }

        assertEquals(Protocol.EQ_MAX_BANDS, success(text).bands.size)
    }

    @Test
    fun `rejects input without parametric filters`() {
        val result = AutoEqProfileParser.parse(
            "GraphicEQ: 20 1.2; 21 1.1; 22 1.0; 23 0.9"
        )

        assertTrue(result is AutoEqParseResult.Failure)
    }

    @Test
    fun `rejects a profile whose filters are all disabled`() {
        val result = AutoEqProfileParser.parse(
            """
            Preamp: 0 dB
            Filter 1: OFF PK Fc 100 Hz Gain 1.0 dB Q 1.0
            """.trimIndent()
        )

        assertTrue(result is AutoEqParseResult.Failure)
    }

    @Test
    fun `parses the AutoEQ index entries`() {
        val entries = parseAutoEqIndex(
            """
            # Index
            - [Moondrop Starfield](./oratory1990/in-ear/Moondrop%20Starfield) by oratory1990
            - [1MORE Aero (ANC Off)](./HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)) by HypetheSonics on GRAS RA0045
            """.trimIndent()
        )

        assertEquals(2, entries.size)
        assertEquals("Moondrop Starfield", entries[0].name)
        assertEquals("oratory1990/in-ear/Moondrop%20Starfield", entries[0].path)
        assertEquals("oratory1990", entries[0].source)
        assertEquals("HypetheSonics on GRAS RA0045", entries[1].source)
    }

    @Test
    fun `builds the ParametricEQ URL from an index path`() {
        val entry = AutoEqEntry(
            name = "Moondrop Starfield",
            source = "oratory1990",
            path = "oratory1990/in-ear/Moondrop%20Starfield"
        )

        assertEquals(
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/" +
                "oratory1990/in-ear/Moondrop%20Starfield/" +
                "Moondrop%20Starfield%20ParametricEQ.txt",
            entry.parametricEqUrl()
        )
    }

    @Test
    fun `rewrites GitHub blob links to raw links`() {
        assertEquals(
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/x.txt",
            normalizeProfileUrl(
                "https://github.com/jaakkopasanen/AutoEq/blob/master/results/x.txt"
            )
        )
        assertEquals(
            "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/x.txt",
            normalizeProfileUrl(
                "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/x.txt"
            )
        )
    }
}
