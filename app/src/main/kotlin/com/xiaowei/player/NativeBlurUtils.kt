package com.xiaowei.player

import android.graphics.Bitmap

object NativeBlurUtils {
    init {
        System.loadLibrary("native-blur")
    }

    fun blur(bitmap: Bitmap?, radius: Int): Bitmap? {
        if (bitmap == null || bitmap.isRecycled || radius < 1) return null
        val argb: Bitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        try {
            return nativeBlur(argb, radius)
        } finally {
            if (argb !== bitmap && !argb.isRecycled) {
                argb.recycle()
            }
        }
    }

    private external fun nativeBlur(bitmap: Bitmap, radius: Int): Bitmap?
}
