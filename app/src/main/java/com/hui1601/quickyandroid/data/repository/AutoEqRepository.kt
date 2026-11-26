package com.hui1601.quickyandroid.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One AutoEQ profile directory from `results/INDEX.md`. */
data class AutoEqEntry(
    val name: String,
    /** Measurement source line, e.g. `crinacle on 711`. */
    val source: String,
    /** URL-encoded path relative to `results/`, e.g. `crinacle/711 in-ear/...`. */
    val path: String
)

/**
 * Read-only client for the AutoEQ result database. Index entries and profile
 * text are fetched from the project's GitHub raw host on demand; the index is
 * cached for the process lifetime so the search sheet only downloads it once.
 */
private const val RAW_ROOT =
    "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results"
private const val INDEX_URL = "$RAW_ROOT/INDEX.md"
private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

/**
 * `<model> ParametricEQ.txt` lives next to the model directory named by the
 * index entry: `results/<path>/<last-segment> ParametricEQ.txt`.
 */
internal fun AutoEqEntry.parametricEqUrl(): String {
    val model = path.substringAfterLast('/')
    return "$RAW_ROOT/$path/$model%20ParametricEQ.txt"
}

object AutoEqRepository {

    private val indexMutex = Mutex()
    @Volatile
    private var cachedIndex: List<AutoEqEntry>? = null

    suspend fun index(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        cachedIndex ?: indexMutex.withLock {
            cachedIndex ?: fetch(INDEX_URL).let { markdown ->
                parseAutoEqIndex(markdown).also { entries ->
                    if (entries.isEmpty()) {
                        throw IOException("The AutoEQ index is empty or unreadable.")
                    }
                    cachedIndex = entries
                }
            }
        }
    }

    /** Download `<model> ParametricEQ.txt` for [entry]. */
    suspend fun profileText(entry: AutoEqEntry): String = withContext(Dispatchers.IO) {
        fetch(entry.parametricEqUrl())
    }

    /** Download a profile from any http(s) URL; GitHub blob links are
     * rewritten to their raw equivalent. */
    suspend fun profileTextFromUrl(url: String): String = withContext(Dispatchers.IO) {
        fetch(normalizeProfileUrl(url))
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as? HttpURLConnection
            ?: throw IOException("Only http(s) URLs are supported.")
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "QuickyAndroid/1.0 (+https://github.com/hui1601/QuickyAndroid)"
            )
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("AutoEQ request failed (HTTP $code).")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        throw IOException("AutoEQ response is too large.")
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }
}

/** Parse the markdown index that lists every profile directory:
 *
 * ```
 * - [Moondrop Starfield](./oratory1990/in-ear/Moondrop%20Starfield) by oratory1990
 * ```
 */
internal fun parseAutoEqIndex(markdown: String): List<AutoEqEntry> {
    val pattern = Regex("""^-\s+\[(.+?)\]\(\./(.+?)\)\s+by\s+(.+?)\s*$""")
    return markdown.lineSequence().mapNotNull { line ->
        pattern.find(line)?.let { match ->
            AutoEqEntry(
                name = match.groupValues[1],
                path = match.groupValues[2],
                source = match.groupValues[3]
            )
        }
    }.toList()
}

/** Accept GitHub blob links as well as raw links. */
internal fun normalizeProfileUrl(input: String): String {
    val url = input.trim()
    return if (url.contains("://github.com/") && url.contains("/blob/")) {
        url.replaceFirst("://github.com/", "://raw.githubusercontent.com/")
            .replaceFirst("/blob/", "/")
    } else {
        url
    }
}
