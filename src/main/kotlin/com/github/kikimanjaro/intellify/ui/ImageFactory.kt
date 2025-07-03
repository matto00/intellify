package com.github.kikimanjaro.intellify.ui

import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

val PLACEHOLDER_IMAGE: BufferedImage = UIUtil.createImage(100, 100, BufferedImage.TYPE_INT_ARGB)

fun createBufferedImageFromUrl (url: String): BufferedImage {
    if (url.isEmpty()) return PLACEHOLDER_IMAGE
    val image: BufferedImage = try {
        ImageIO.read(URL(url))
    } catch (e: Exception) {
        // Handle invalid URL or loading error
        PLACEHOLDER_IMAGE
    }
    return image
}

data class BufferedImageCacheValue (
    val bufferedImage: BufferedImage,
    var createdAt: Long = System.currentTimeMillis(),
    var lastUse: Long = System.currentTimeMillis()
)

object ImageFactory {
    private const val retentionTimeMillis: Long = 5 * 60 * 1000 // 5 minutes

    private val imageCache = mutableMapOf<String, BufferedImageCacheValue>()

    private val lock = Any()

    fun getImage(url: String): BufferedImage {
        synchronized(lock) {
            if (!imageCache.containsKey(url)) {
                val bufferedImage = createBufferedImageFromUrl(url)
                imageCache[url] = BufferedImageCacheValue(bufferedImage)
            }
            return imageCache[url]!!.bufferedImage
        }
    }

    fun putImage(url: String, image: BufferedImage) {
        synchronized(lock) {
            if (imageCache.containsKey(url)) {
                imageCache[url]!!.lastUse = System.currentTimeMillis()
                return
            }
            imageCache[url] = BufferedImageCacheValue(image)
        }
    }

    fun updateCache() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            imageCache.entries.removeIf { entry ->
                now - entry.value.lastUse > retentionTimeMillis
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            imageCache.clear()
        }
    }
}