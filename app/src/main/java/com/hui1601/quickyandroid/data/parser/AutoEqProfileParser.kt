package com.hui1601.quickyandroid.data.parser

import com.hui1601.quickyandroid.ble.Protocol
import kotlin.math.roundToInt

/**
 * One AutoEQ parametric profile mapped onto the firmware's custom-EQ wire
 * format. [preGainDb] is AutoEQ's `Preamp:` headroom compensation.
 */
data class AutoEqProfile(
    val preGainDb: Float,
    val bands: List<Protocol.EqBand>
)

sealed interface AutoEqParseResult {
    data class Success(val profile: AutoEqProfile) : AutoEqParseResult
    data class Failure(val message: String) : AutoEqParseResult
}

/**
 * Parser for AutoEQ's Equalizer APO style `* ParametricEQ.txt` export:
 *
 * ```
 * Preamp: -6.1 dB
 * Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
 * Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
 * ```
 *
 * AutoEQ's `HSC` (high shelf) has no equivalent in the firmware's filter
 * table, so it is folded into a broad peaking band at the same corner
 * frequency — the closest shape the wire format can express.
 */
object AutoEqProfileParser {

    private const val NUMBER = """[-+]?(?:\d+(?:\.\d*)?|\.\d+)"""

    private val preampPattern = Regex(
        """Preamp\s*:\s*($NUMBER)\s*dB""",
        RegexOption.IGNORE_CASE
    )

    private val filterPattern = Regex(
        """Filter\s*\d+\s*:\s*(ON|OFF)\s+([A-Za-z_]+)\s+Fc\s+($NUMBER)\s*(kHz|Hz)\s+Gain\s+($NUMBER)\s*dB\s+Q\s+($NUMBER)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): AutoEqParseResult {
        var preGainDb = 0f
        var recognizedFilters = 0
        val bands = mutableListOf<Protocol.EqBand>()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            val preampMatch = preampPattern.find(line)
            if (preampMatch != null) {
                preGainDb = preampMatch.groupValues[1].toFloatOrNull() ?: 0f
                continue
            }

            val match = filterPattern.find(line) ?: continue
            recognizedFilters++

            if (!match.groupValues[1].equals("ON", ignoreCase = true)) continue
            val type = filterType(match.groupValues[2]) ?: continue
            val value = match.groupValues[3].toFloatOrNull() ?: continue
            val gainDb = match.groupValues[5].toFloatOrNull() ?: continue
            val q = match.groupValues[6].toFloatOrNull() ?: continue
            val freqHz = if (match.groupValues[4].equals("kHz", ignoreCase = true)) {
                value * 1000f
            } else {
                value
            }

            bands += Protocol.EqBand(
                freq = freqHz.roundToInt(),
                gainDb = gainDb,
                q = q,
                bandType = type.wireValue
            )
        }

        if (recognizedFilters == 0) {
            return AutoEqParseResult.Failure(
                "No AutoEQ filters found. Paste a ParametricEQ.txt profile or a profile URL."
            )
        }

        val sanitized = Protocol.sanitizeEqBands(bands)
        if (sanitized.isEmpty()) {
            return AutoEqParseResult.Failure(
                "The profile has no usable filters in the 20 Hz – 20 kHz range."
            )
        }

        return AutoEqParseResult.Success(AutoEqProfile(preGainDb, sanitized))
    }

    private fun filterType(name: String): Protocol.EqFilterType? = when (name.uppercase()) {
        "PK", "PEAK", "PEAKING" -> Protocol.EqFilterType.PEAKING
        "LS", "LSC", "LOWSHELF", "LOW_SHELF" -> Protocol.EqFilterType.LOW_SHELF
        // No high-shelf wire type exists; approximate with a broad peaking band.
        "HS", "HSC", "HIGHSHELF", "HIGH_SHELF" -> Protocol.EqFilterType.PEAKING
        "HP", "HPF", "HIGHPASS", "HIGH_PASS" -> Protocol.EqFilterType.HIGH_PASS
        "LP", "LPF", "LOWPASS", "LOW_PASS" -> Protocol.EqFilterType.LOW_PASS
        "TILT" -> Protocol.EqFilterType.TILT
        else -> null
    }
}
