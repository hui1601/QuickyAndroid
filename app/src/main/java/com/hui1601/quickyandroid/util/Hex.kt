package com.hui1601.quickyandroid.util

/** Hex helpers for the developer tools. */
object Hex {

    /** "0A 0B" or "0a0b" → bytes; null when malformed (odd length, non-hex). */
    fun parseBytes(text: String): ByteArray? {
        val cleaned = text.replace(" ", "").replace(":", "").lowercase()
        if (cleaned.isEmpty()) return ByteArray(0)
        if (cleaned.length % 2 != 0) return null
        if (cleaned.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return ByteArray(cleaned.length / 2) { i ->
            ((Character.digit(cleaned[i * 2], 16) shl 4) or Character.digit(cleaned[i * 2 + 1], 16)).toByte()
        }
    }

    /** bytes → "0a 0b" (space-separated, lowercase). */
    fun format(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    fun format(value: Int): String = "%02x".format(value and 0xFF)
}
