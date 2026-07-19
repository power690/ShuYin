package com.xiaowei.player.navkit

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.progressiveBlur(
    progress: Float,
    maxRadiusDp: Float = 24f,
    tileMode: TileMode = TileMode.Clamp,
    density: Float,
): Modifier = this.graphicsLayer {
    val p = progress.coerceIn(0f, 1f)
    if (p > 0f) {
        val sigma = p * density * maxRadiusDp
        renderEffect = BlurEffect(sigma, sigma, tileMode)
    }
}

@Composable
fun Modifier.progressiveBlur(
    progress: Float,
    maxRadiusDp: Dp = 24.dp,
    tileMode: TileMode = TileMode.Clamp,
): Modifier {
    val density = LocalDensity.current.density
    return this.graphicsLayer {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            val sigma = p * density * maxRadiusDp.value
            renderEffect = BlurEffect(sigma, sigma, tileMode)
        }
    }
}

fun Modifier.staticBlur(
    radiusDp: Dp,
    tileMode: TileMode = TileMode.Clamp,
    density: Float,
): Modifier = this.graphicsLayer {
    if (radiusDp.value > 0f) {
        val sigma = radiusDp.value * density
        renderEffect = BlurEffect(sigma, sigma, tileMode)
    }
}

@Composable
fun Modifier.staticBlur(
    radiusDp: Dp,
    tileMode: TileMode = TileMode.Clamp,
): Modifier {
    val density = LocalDensity.current.density
    return this.staticBlur(radiusDp, tileMode, density)
}

fun Modifier.blurWithDesaturate(
    progress: Float,
    maxRadiusDp: Float = 24f,
    density: Float,
    tileMode: TileMode = TileMode.Clamp,
): Modifier = this.graphicsLayer {
    val p = progress.coerceIn(0f, 1f)
    if (p > 0f) {
        val sigma = p * density * maxRadiusDp
        renderEffect = BlurEffect(sigma, sigma, tileMode)

        val g = p
        val c = 1f - g
        colorFilter = ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.213f * g + c, 0.715f * g, 0.072f * g, 0f, 0f,
                    0.213f * g, 0.715f * g + c, 0.072f * g, 0f, 0f,
                    0.213f * g, 0.715f * g, 0.072f * g + c, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }
}

@Composable
fun Modifier.blurWithDesaturate(
    progress: Float,
    maxRadiusDp: Dp = 24.dp,
    tileMode: TileMode = TileMode.Clamp,
): Modifier {
    val density = LocalDensity.current.density
    return this.blurWithDesaturate(progress, maxRadiusDp.value, density, tileMode)
}

val supportsHardwareBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
