package com.hui1601.quickyandroid.data.repository

import com.hui1601.quickyandroid.data.model.ProductMetadata
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure parts of [ProductRepository]:
 *  - [ProductRepository.rewriteUrl] (companion, no Android dependencies)
 *  - [ProductMetadata] deserialization against JSON shaped like real
 *    app/src/main/assets/android_products.json entries
 *
 * LIMITATION: [ProductRepository] itself requires android.content.Context /
 * AssetManager to load the JSON, so the load/skip/lookup logic is exercised
 * here by replicating its decode step (Json with ignoreUnknownKeys) rather
 * than by instantiating the repository.
 */
class ProductRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Entry shape copied from a real android_products.json record (vendorId 19797).
    private val sampleEntry = """
        {
          "vendorId": 19797,
          "title": "QCY Crossky C50",
          "subTitle": "QCY Crossky C50",
          "category": "earphones",
          "modelId": 21020,
          "features": {
            "key_function": {
              "events": [
                {
                  "name": "Touch",
                  "functions": ["Not work", "Play/pause", "Previous track", "Next track"]
                }
              ]
            },
            "eq": {
              "bands": 10,
              "mindb": 8,
              "maxdb": 8,
              "freq": "31,62,125,250,500,1000,2000,4000,8000,16000",
              "characteristic": "0000000B-0000-1000-8000-00805f9b34fb",
              "presets": ["Spatial sound effects", "Default", "Pop"]
            },
            "channel_balance": true,
            "device_name": true,
            "auto_off_timer": { "cmdid": 20, "repeat": 50000 },
            "settings": [
              { "name": "Gaming mode", "type": "game_mode", "cmdid": 9, "cmd": null }
            ]
          }
        }
    """.trimIndent()

    // ── rewriteUrl ────────────────────────────────────────────────────

    @Test
    fun `rewriteUrl rewrites http urls to bundled asset paths`() {
        assertEquals(
            "file:///android_asset/product_images/foo.png",
            ProductRepository.rewriteUrl("https://api.watch.qcy.com/images/foo.png")
        )
        assertEquals(
            "file:///android_asset/product_images/bar.zip",
            ProductRepository.rewriteUrl("http://example.com/bar.zip")
        )
    }

    @Test
    fun `rewriteUrl leaves blank and non-http values untouched`() {
        assertEquals("", ProductRepository.rewriteUrl(""))
        assertEquals("   ", ProductRepository.rewriteUrl("   "))
        assertEquals("icon.png", ProductRepository.rewriteUrl("icon.png"))
        assertEquals(
            "file:///android_asset/product_images/already.png",
            ProductRepository.rewriteUrl("file:///android_asset/product_images/already.png")
        )
    }

    @Test
    fun `rewriteUrl falls back to original when no file name present`() {
        assertEquals("https://example.com/", ProductRepository.rewriteUrl("https://example.com/"))
    }

    // ── ProductMetadata decoding (mirrors loadDatabase's decode step) ──

    @Test
    fun `decodes real-shaped product entry with features`() {
        val product = json.decodeFromString(ProductMetadata.serializer(), sampleEntry)
        assertEquals(19797, product.vendorId)
        assertEquals("QCY Crossky C50", product.title)
        assertEquals("QCY Crossky C50", product.subTitle)
        assertEquals("earphones", product.category)
        assertEquals(21020, product.modelId)

        val features = product.features!!
        assertEquals(true, features.channelBalance)
        assertEquals(true, features.deviceName)
        assertEquals(false, features.findEarphone)

        val eq = features.eq!!
        assertEquals(10, eq.bands)
        assertEquals(8, eq.minDb)
        assertEquals(8, eq.maxDb)
        assertEquals(listOf("Spatial sound effects", "Default", "Pop"), eq.presets)

        val keyFunction = features.keyFunction!!
        assertEquals(1, keyFunction.events.size)
        assertEquals("Touch", keyFunction.events[0].name)
        assertEquals("Play/pause", keyFunction.events[0].functions[1])

        val timer = features.autoOffTimer!!
        assertEquals(20, timer.cmdId)
        assertEquals(50000, timer.repeat)

        val settings = features.settings!!
        assertEquals(1, settings.size)
        assertEquals("Gaming mode", settings[0].name)
        assertEquals(9, settings[0].cmdId)
        assertNull(settings[0].cmd)
    }

    @Test
    fun `decodes minimal entry relying on defaults`() {
        val product = json.decodeFromString(
            ProductMetadata.serializer(),
            """{"vendorId": 123, "title": "QCY T13"}"""
        )
        assertEquals(123, product.vendorId)
        assertEquals("QCY T13", product.title)
        assertEquals("", product.subTitle)
        assertEquals("earphones", product.category)
        assertEquals("standard", product.vendorType)
        assertNull(product.modelId)
        assertTrue(product.colors.isEmpty())
        assertNull(product.features)
    }

    @Test
    fun `ignores unknown keys like the repository loader does`() {
        val product = json.decodeFromString(
            ProductMetadata.serializer(),
            """{"vendorId": 1, "title": "X", "someFutureField": {"nested": true}}"""
        )
        assertEquals(1, product.vendorId)
    }

    @Test(expected = SerializationException::class)
    fun `entry missing required vendorId fails decode and would be skipped`() {
        json.decodeFromString(ProductMetadata.serializer(), """{"title": "No vendor id"}""")
    }

    @Test(expected = SerializationException::class)
    fun `entry with non-numeric vendorId fails decode and would be skipped`() {
        // Note: a quoted number like "19797" is accepted by the lenient JSON
        // lexer; a non-numeric token is not.
        json.decodeFromString(
            ProductMetadata.serializer(),
            """{"vendorId": {"nested": true}, "title": "Wrong type"}"""
        )
    }
}
