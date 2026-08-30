package com.xiaowei.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemeColorUtil {

    private const val COVER_SAMPLE_SIZE = 96
    private val cache = LruCache<String, Long>(64)

    suspend fun extractFromUri(context: Context, imageUri: Uri?): Long? {
        if (imageUri == null) return null
        val key = imageUri.toString()
        cache.get(key)?.let { return it }

        val result = runCatching {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUri)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .size(COVER_SAMPLE_SIZE)
                .precision(Precision.INEXACT)
                .build()
            val imgResult = withContext(Dispatchers.IO) { loader.execute(request) }
            val bitmap = drawableToBitmap((imgResult as? SuccessResult)?.drawable ?: return@runCatching null)
            withContext(Dispatchers.Default) { extract(bitmap) }
        }.getOrNull()

        if (result != null) cache.put(key, result)
        return result
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
        return baseColor.toLong() and 0xFFFFFFFFL
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }
}
