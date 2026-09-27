package com.hui1601.quickyandroid.ble

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * ZR (Zhongke Ruike) vendor payload codecs, ported byte-exactly from
 * ZRDeviceImpl.smali (/data/projects/Quicky/tmp/smali/classes2/com/qcymall/
 * earphonesetup/manager/ZRDeviceImpl.smali).
 *
 * ZR devices share the standard QCY GATT service (0xA001); only EQ writes,
 * key-function writes and time sync differ — they go RAW (unframed) to
 * characteristics 0x000B / 0x000D / 0x000E with ZR-specific payloads.
 */
object ZrProtocol {

    // ── Key-id remap (converCmdIDToZR / converCmdIDToQCY, smali:441/392) ──
    // App key ids: 1=L-single 2=R-single 3=L-double 4=R-double 5=L-triple 6=R-triple
    // ZR wire ids: 1=L-single 2=L-double 3=L-triple 4=R-single 5=R-double 6=R-triple

    private val appToZr = mapOf(1 to 1, 2 to 4, 3 to 2, 4 to 5, 5 to 3, 6 to 6)
    private val zrToApp = mapOf(1 to 1, 2 to 3, 3 to 5, 4 to 2, 5 to 4, 6 to 6)

    fun keyIdToZr(keyId: Int): Int = appToZr[keyId] ?: keyId
    fun keyIdFromZr(zrKeyId: Int): Int = zrToApp[zrKeyId] ?: zrKeyId

    /**
     * Raw (unframed) time-sync payload for characteristic 0x000E:
     * [0x00, 0x00, epochSeconds BE32] — QCYConnectManager.smali:4103-4121.
     */
    fun timeSyncPayload(epochSeconds: Long): ByteArray = byteArrayOf(
        0x00, 0x00,
        ((epochSeconds shr 24) and 0xFF).toByte(),
        ((epochSeconds shr 16) and 0xFF).toByte(),
        ((epochSeconds shr 8) and 0xFF).toByte(),
        (epochSeconds and 0xFF).toByte()
    )

    // ── 144-byte EQ packet (ZRDeviceImpl.setEQ, smali:941-1404) ──────────

    private const val COEFF_SHIFT = 21 // Q10.21
    private val DEFAULT_Q = doubleArrayOf(1.5, 1.5, 3.0, 3.0, 3.0)
    private val DEFAULT_FREQ = intArrayOf(62, 250, 1000, 4000, 8000)
    private val IDENTITY = intArrayOf(0x200000, 0, 0, 0x200000, 0, 0)

    /**
     * Peaking biquad coefficients, ZR variant (getCoeff, smali:600-805):
     *   A     = 10^(gain/40)
     *   w0    = 2*pi*freq/44100
     *   alpha = A*sin(w0) / ((10*q/7)*sqrt(2))
     *   denom = 1 + sin(w0)/A
     *   [b0, b1, b2, 1, -a1, -a2] =
     *     [(1+alpha), -2cos(w0), (1-alpha), 1, -(-2cos(w0)), -(1-sin(w0)/A)] / denom
     * gain == 0 yields the identity filter. All values fixed-point Q10.21
     * (QCONST32: trunc(x * 2^21 + 0.5)).
     */
    fun getCoeff(gainDb: Double, q: Double, freq: Int): IntArray {
        if (gainDb == 0.0) return IDENTITY.copyOf()
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = freq * 2.0 * PI / 44100.0
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / ((10.0 * q / 7.0) * kotlin.math.sqrt(2.0)) * a
        val sinOverA = sinW0 / a
        val denom = 1.0 + sinOverA
        return intArrayOf(
            qconst32((1.0 + alpha) / denom),
            qconst32((-2.0 * cosW0) / denom),
            qconst32((1.0 - alpha) / denom),
            qconst32(1.0),
            -qconst32((-2.0 * cosW0) / denom),
            -qconst32((1.0 - sinOverA) / denom)
        )
    }

    private fun qconst32(x: Double): Int = (x * (1L shl COEFF_SHIFT) + 0.5).toLong().toInt()

    /**
     * Serialize 6 int coefficients as 24 LE bytes (getByteFromArray, smali:483-598).
     * The 25th byte is a per-band running checksum — excluded from the packet
     * body but folded into the global checksum by the caller.
     */
    private fun bandBytes(coeffs: IntArray): ByteArray {
        val out = ByteArray(coeffs.size * 4 + 1)
        var sum = 0
        coeffs.forEachIndexed { i, c ->
            for (b in 0 until 4) {
                val v = (c shr (8 * b)) and 0xFF
                out[i * 4 + b] = v.toByte()
                sum = (sum + v) and 0xFF
            }
        }
        out[out.size - 1] = sum.toByte()
        return out
    }

    /**
     * Build the raw 144-byte EQ packet written to characteristic 0x000B.
     *
     * Layout (ZRDeviceImpl.setEQ, smali:941-1404):
     *   [0..119]   5 bands x 24 bytes: 6 int32 LE biquad coefficients
     *              eqType == 1 -> identity coefficients (defaultEQ arrays)
     *   [120]      eqType byte
     *   [121...]   raw eqData gain bytes (dB*10, as passed)
     *   trailer    0x5A 0x5A
     *   [freqs]    5 x uint16 LE (only when freqs != null)
     *   [143]      checksum: byte-truncated sum of (per-band checksum bytes +
     *              0x5A + 0x5A + eqData bytes + freq lo+hi bytes)
     *
     * @param eqType preset index; 1 selects the identity/default curve
     * @param eqData gain bytes (dB*10) exactly as the standard protocol uses them
     */
    fun buildEqPacket(eqType: Int, eqData: ByteArray, freqs: IntArray? = null): ByteArray {
        val packet = ByteArray(144)
        var offset = 0
        var checksum = 0

        if (eqType == 1) {
            repeat(5) {
                val band = bandBytes(IDENTITY)
                System.arraycopy(band, 0, packet, offset, 24)
                checksum = (checksum + (band[24].toInt() and 0xFF)) and 0xFF
                offset += 24
            }
        } else {
            for (i in eqData.indices) {
                if (offset + 25 - 1 >= 144) break
                val q = DEFAULT_Q.getOrElse(i) { 3.0 }
                val freq = freqs?.getOrElse(i) { i * 1000 } ?: DEFAULT_FREQ.getOrElse(i) { i * 1000 }
                val band = bandBytes(getCoeff(eqData[i].toDouble(), q, freq))
                System.arraycopy(band, 0, packet, offset, 24)
                checksum = (checksum + (band[24].toInt() and 0xFF)) and 0xFF
                offset += 24
            }
        }

        packet[offset] = eqType.toByte()
        offset += 1
        System.arraycopy(eqData, 0, packet, offset, eqData.size)
        offset += eqData.size
        packet[offset] = 0x5A
        packet[offset + 1] = 0x5A
        offset += 2
        checksum = (checksum + 0x5A + 0x5A) and 0xFF
        eqData.forEach { checksum = (checksum + (it.toInt() and 0xFF)) and 0xFF }

        freqs?.forEach { f ->
            val lo = (f and 0xFF).toByte()
            val hi = ((f shr 8) and 0xFF).toByte()
            packet[offset] = lo
            packet[offset + 1] = hi
            offset += 2
            checksum = (checksum + (lo.toInt() and 0xFF) + (hi.toInt() and 0xFF)) and 0xFF
        }

        packet[143] = checksum.toByte()
        return packet
    }

    /**
     * Parse a ZR EQ read (characteristic 0x000B): the app-facing state lives at
     * bytes [0x78..0x7D] = [eqType, 5 gain bytes] (QCYConnectManager$3.smali:55-101).
     */
    fun parseEqRead(value: ByteArray): Pair<Int, List<Float>>? {
        if (value.size < 0x7E) return null
        val eqType = value[0x78].toInt() and 0xFF
        val gains = (0 until 5).map { value[0x79 + it].toInt() / 10f }
        return eqType to gains
    }
}
