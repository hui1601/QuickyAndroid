package com.hui1601.quickyandroid.util

import com.hui1601.quickyandroid.ble.Protocol
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Summed magnitude response (dB) of the parametric editor's bands, including
 * pre-gain. This is an RBJ biquad preview at 48 kHz — enough to show the shape
 * the DSP will apply; it is not a measurement of the device output.
 *
 * TILT has no standard RBJ equivalent in this app, so it is approximated with
 * a peaking band. High-/low-pass bands ignore their gain field, matching the
 * coefficients the firmware builds.
 */
internal fun eqResponseDb(
    bands: List<Protocol.EqBand>,
    preGainDb: Float,
    frequencyHz: Float,
    sampleRateHz: Float = 48_000f
): Float {
    if (frequencyHz <= 0f) return preGainDb
    var total = preGainDb
    for (band in bands) {
        if (band.freq <= 0 || band.q <= 0f) continue
        total += bandResponseDb(band, frequencyHz, sampleRateHz)
    }
    return total
}

private fun bandResponseDb(
    band: Protocol.EqBand,
    frequencyHz: Float,
    sampleRateHz: Float
): Float {
    val w0 = 2.0 * PI * band.freq / sampleRateHz
    if (w0 <= 0.0 || w0 >= PI) return 0f

    val cosW0 = cos(w0)
    val sinW0 = sin(w0)
    val q = band.q.toDouble().coerceAtLeast(0.001)
    val a = Math.pow(10.0, band.gainDb / 40.0)
    val alpha = sinW0 / (2.0 * q)

    val b0: Double
    val b1: Double
    val b2: Double
    val a0: Double
    val a1: Double
    val a2: Double

    when (band.bandType) {
        Protocol.EqFilterType.LOW_SHELF.wireValue -> {
            val sqrtA = sqrt(a)
            b0 = a * ((a + 1) - (a - 1) * cosW0 + 2 * sqrtA * alpha)
            b1 = 2 * a * ((a - 1) - (a + 1) * cosW0)
            b2 = a * ((a + 1) - (a - 1) * cosW0 - 2 * sqrtA * alpha)
            a0 = (a + 1) + (a - 1) * cosW0 + 2 * sqrtA * alpha
            a1 = -2 * ((a - 1) + (a + 1) * cosW0)
            a2 = (a + 1) + (a - 1) * cosW0 - 2 * sqrtA * alpha
        }

        Protocol.EqFilterType.HIGH_PASS.wireValue -> {
            b0 = (1 + cosW0) / 2
            b1 = -(1 + cosW0)
            b2 = (1 + cosW0) / 2
            a0 = 1 + alpha
            a1 = -2 * cosW0
            a2 = 1 - alpha
        }

        Protocol.EqFilterType.LOW_PASS.wireValue -> {
            b0 = (1 - cosW0) / 2
            b1 = 1 - cosW0
            b2 = (1 - cosW0) / 2
            a0 = 1 + alpha
            a1 = -2 * cosW0
            a2 = 1 - alpha
        }

        // Peaking, plus TILT as the closest available approximation.
        else -> {
            b0 = 1 + alpha * a
            b1 = -2 * cosW0
            b2 = 1 - alpha * a
            a0 = 1 + alpha / a
            a1 = -2 * cosW0
            a2 = 1 - alpha / a
        }
    }

    val w = 2.0 * PI * frequencyHz / sampleRateHz
    val cosW = cos(w)
    val sinW = sin(w)
    val cos2W = cos(2 * w)
    val sin2W = sin(2 * w)

    val numRe = b0 + b1 * cosW + b2 * cos2W
    val numIm = -b1 * sinW - b2 * sin2W
    val denRe = a0 + a1 * cosW + a2 * cos2W
    val denIm = -a1 * sinW - a2 * sin2W
    val denominator = (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)
    val magnitude = sqrt((numRe * numRe + numIm * numIm) / denominator)
    return (20.0 * log10(magnitude.coerceAtLeast(1e-9))).toFloat()
}
