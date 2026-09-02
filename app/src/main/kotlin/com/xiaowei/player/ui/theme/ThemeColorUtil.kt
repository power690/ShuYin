package com.xiaowei.player.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.xiaowei.player.data.EmbeddedCoverFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemeColorUtil {

    private const val COVER_SAMPLE_SIZE = 96
    private val cache = LruCache<String, Long>(64)

    suspend fun extractFromFilePath(filePath: String?): Long? {
        if (filePath.isNullOrBlank()) return null
        cache.get(filePath)?.let { return it }

        val result = runCatching {
            val bytes = withContext(Dispatchers.IO) {
                EmbeddedCoverFetcher.loadCoverBytes(filePath)
            } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeSample(bounds.outWidth, bounds.outHeight, COVER_SAMPLE_SIZE)
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@runCatching null
            withContext(Dispatchers.Default) { extract(bitmap) }
        }.getOrNull()

        if (result != null) cache.put(filePath, result)
        return result
    }

    private fun computeSample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    fun seedLongToColor(argb: Long): Color = Color(argb.toInt())

    private fun extract(bitmap: Bitmap): Long {
        val palette = Palette.from(bitmap)
            .clearFilters()
            .generate()

        val baseColor = palette.getVibrantColor(
            palette.getMutedColor(
                palette.getDominantColor(0xFF808080.toInt())
            )
        )
        bitmap.recycle()
        return baseColor.toLong() and 0xFFFFFFFFL
    }
}
