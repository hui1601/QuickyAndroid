package com.hui1601.quickyandroid.data.repository

import android.content.Context
import com.hui1601.quickyandroid.data.model.ProductMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ProductRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val database: Map<String, ProductMetadata> by lazy {
        loadDatabase(context)
    }

    private fun loadDatabase(context: Context): Map<String, ProductMetadata> {
        return try {
            val text = context.assets.open("android_products.json").bufferedReader().use { it.readText() }
            val element = json.parseToJsonElement(text)
            val map = mutableMapOf<String, ProductMetadata>()
            element.jsonObject.forEach { (key, value) ->
                try {
                    val product = json.decodeFromJsonElement(ProductMetadata.serializer(), value)
                    // If top-level images are empty but colors are available, use first color's images
                    val defaultColor = product.colors.firstOrNull()
                    val iconUrl = product.iconUrl.ifBlank { defaultColor?.iconUrl ?: "" }
                    val leftImgUrl = product.leftImgUrl.ifBlank { defaultColor?.leftImgUrl ?: "" }
                    val rightImgUrl = product.rightImgUrl.ifBlank { defaultColor?.rightImgUrl ?: "" }
                    val boxImgUrl = product.boxImgUrl.ifBlank { defaultColor?.boxImgUrl ?: "" }
                    val animZip = product.animationImgZip.ifBlank { defaultColor?.animationImgZip ?: "" }

                    map[key] = product.copy(
                        iconUrl = rewriteUrl(iconUrl),
                        leftImgUrl = rewriteUrl(leftImgUrl),
                        rightImgUrl = rewriteUrl(rightImgUrl),
                        boxImgUrl = rewriteUrl(boxImgUrl),
                        animationImgZip = rewriteUrl(animZip)
                    )
                } catch (_: Exception) {
                    // skip malformed entries
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun lookup(vendorId: Int): ProductMetadata? {
        return database[vendorId.toString()]
    }

    fun lookupAll(): List<ProductMetadata> = database.values.toList()

    fun count(): Int = database.size

    companion object {
        /**
         * Rewrite remote HTTP image URLs to local bundled asset paths.
         * Falls back to the original URL if the local asset doesn't exist.
         */
        fun rewriteUrl(originalUrl: String): String {
            if (originalUrl.isBlank()) return originalUrl
            if (!originalUrl.startsWith("http")) return originalUrl
            val fileName = originalUrl.substringAfterLast('/')
            if (fileName.isBlank()) return originalUrl
            // Coil loads assets via file:///android_asset/ prefix
            return "file:///android_asset/product_images/$fileName"
        }
    }
}
