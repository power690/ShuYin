package com.xiaowei.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.rememberDynamicColorScheme
import com.xiaowei.player.data.ThemePrefs

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

object StatusBarStyle {
    private var refCount = 0
    val forceLightIcons = mutableStateOf(false)

    fun acquire() {
        refCount++
        forceLightIcons.value = true
    }

    fun release(): Boolean {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0) forceLightIcons.value = false
        return refCount == 0
    }

    fun ensureFlag() {
        if (refCount > 0) forceLightIcons.value = true
    }
}

@Composable
fun ZMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themePrefs = ThemePrefs.get(context)

    val dynamicColorEnabled = themePrefs.dynamicColorEnabledState.value
    val themeColorIndex = themePrefs.themeColorIndexState.value
    val coverColorEnabled = themePrefs.coverColorEnabledState.value
    val coverColor = themePrefs.coverColorState.value

    val colorScheme = rememberZMusicColorScheme(
        darkTheme = darkTheme,
        dynamicColorEnabled = dynamicColorEnabled,
        themeColorIndex = themeColorIndex,
        coverColorEnabled = coverColorEnabled,
        coverColor = coverColor
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                window.navigationBarColor = 0x80000000.toInt()
            }
            themePrefs.lastPrimaryColor = colorScheme.primary.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun rememberZMusicColorScheme(
    darkTheme: Boolean,
    dynamicColorEnabled: Boolean,
    themeColorIndex: Int,
    coverColorEnabled: Boolean = false,
    coverColor: Int? = null
): androidx.compose.material3.ColorScheme {
    val context = LocalContext.current
    return when {

        coverColorEnabled -> {
            if (coverColor != null) {
                rememberDynamicColorScheme(seedColor = Color(coverColor), isDark = darkTheme)
            } else {
                val preset = PRESET_THEME_COLORS.getOrElse(themeColorIndex) { PRESET_THEME_COLORS[DEFAULT_THEME_COLOR_INDEX] }
                if (darkTheme) buildDarkColorSchemeFromPreset(preset)
                else buildLightColorSchemeFromPreset(preset)
            }
        }

        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> buildDarkColorSchemeFromPreset(
            PRESET_THEME_COLORS.getOrElse(themeColorIndex) { PRESET_THEME_COLORS[DEFAULT_THEME_COLOR_INDEX] }
        )
        else -> buildLightColorSchemeFromPreset(
            PRESET_THEME_COLORS.getOrElse(themeColorIndex) { PRESET_THEME_COLORS[DEFAULT_THEME_COLOR_INDEX] }
        )
    }
}

private fun buildLightColorSchemeFromPreset(preset: PresetThemeColor): androidx.compose.material3.ColorScheme {
    val surface = preset.lightSurface
    val surfaceVariant = preset.lightSurfaceVariant
    val white = Color.White
    val black = Color.Black
    return lightColorScheme(

        primary = preset.lightPrimary,
        onPrimary = preset.lightOnPrimary,
        primaryContainer = preset.lightPrimaryContainer,
        onPrimaryContainer = preset.lightOnPrimaryContainer,
        inversePrimary = preset.darkPrimary,

        secondary = preset.lightSecondary,
        onSecondary = preset.lightOnSecondary,
        secondaryContainer = preset.lightSecondaryContainer,
        onSecondaryContainer = preset.lightOnSecondaryContainer,

        tertiary = preset.lightTertiary,
        onTertiary = preset.lightOnTertiary,
        tertiaryContainer = preset.lightTertiaryContainer,
        onTertiaryContainer = preset.lightOnTertiaryContainer,

        background = preset.lightBackground,
        onBackground = preset.lightOnBackground,
        surface = surface,
        onSurface = preset.lightOnSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = preset.lightOnSurfaceVariant,
        surfaceTint = preset.lightPrimary,

        inverseSurface = preset.lightOnSurface,
        inverseOnSurface = preset.lightSurface,

        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        outline = preset.lightOutline,
        outlineVariant = preset.lightOutlineVariant,

        scrim = black,

        surfaceBright = lerp(surfaceVariant, white, 0.50f),
        surfaceDim = lerp(surfaceVariant, black, 0.10f),
        surfaceContainer = lerp(surfaceVariant, white, 0.40f),
        surfaceContainerHigh = lerp(surfaceVariant, white, 0.55f),
        surfaceContainerHighest = lerp(surfaceVariant, white, 0.70f),
        surfaceContainerLow = lerp(surfaceVariant, white, 0.20f),
        surfaceContainerLowest = lerp(surfaceVariant, black, 0.05f),

        primaryFixed = preset.lightPrimaryContainer,
        primaryFixedDim = preset.darkPrimary,
        onPrimaryFixed = preset.lightOnPrimaryContainer,
        onPrimaryFixedVariant = preset.darkOnPrimaryContainer,

        secondaryFixed = preset.lightSecondaryContainer,
        secondaryFixedDim = preset.darkSecondary,
        onSecondaryFixed = preset.lightOnSecondaryContainer,
        onSecondaryFixedVariant = preset.darkOnSecondaryContainer,

        tertiaryFixed = preset.lightTertiaryContainer,
        tertiaryFixedDim = preset.darkTertiary,
        onTertiaryFixed = preset.lightOnTertiaryContainer,
        onTertiaryFixedVariant = preset.darkOnTertiaryContainer,
    )
}

private fun buildDarkColorSchemeFromPreset(preset: PresetThemeColor): androidx.compose.material3.ColorScheme {
    val surface = preset.darkSurface
    val white = Color.White
    val black = Color.Black
    return darkColorScheme(

        primary = preset.darkPrimary,
        onPrimary = preset.darkOnPrimary,
        primaryContainer = preset.darkPrimaryContainer,
        onPrimaryContainer = preset.darkOnPrimaryContainer,
        inversePrimary = preset.lightPrimary,

        secondary = preset.darkSecondary,
        onSecondary = preset.darkOnSecondary,
        secondaryContainer = preset.darkSecondaryContainer,
        onSecondaryContainer = preset.darkOnSecondaryContainer,

        tertiary = preset.darkTertiary,
        onTertiary = preset.darkOnTertiary,
        tertiaryContainer = preset.darkTertiaryContainer,
        onTertiaryContainer = preset.darkOnTertiaryContainer,

        background = preset.darkBackground,
        onBackground = preset.darkOnBackground,
        surface = surface,
        onSurface = preset.darkOnSurface,
        surfaceVariant = preset.darkSurfaceVariant,
        onSurfaceVariant = preset.darkOnSurfaceVariant,
        surfaceTint = preset.darkPrimary,

        inverseSurface = preset.darkOnSurface,
        inverseOnSurface = preset.darkSurface,

        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        outline = preset.darkOutline,
        outlineVariant = preset.darkOutlineVariant,

        scrim = black,

        surfaceBright = lerp(surface, white, 0.15f),
        surfaceDim = lerp(surface, black, 0.05f),
        surfaceContainer = lerp(surface, white, 0.08f),
        surfaceContainerHigh = lerp(surface, white, 0.15f),
        surfaceContainerHighest = lerp(surface, white, 0.22f),
        surfaceContainerLow = surface,
        surfaceContainerLowest = lerp(surface, black, 0.05f),

        primaryFixed = preset.lightPrimaryContainer,
        primaryFixedDim = preset.darkPrimary,
        onPrimaryFixed = preset.lightOnPrimaryContainer,
        onPrimaryFixedVariant = preset.darkOnPrimaryContainer,

        secondaryFixed = preset.lightSecondaryContainer,
        secondaryFixedDim = preset.darkSecondary,
        onSecondaryFixed = preset.lightOnSecondaryContainer,
        onSecondaryFixedVariant = preset.darkOnSecondaryContainer,

        tertiaryFixed = preset.lightTertiaryContainer,
        tertiaryFixedDim = preset.darkTertiary,
        onTertiaryFixed = preset.lightOnTertiaryContainer,
        onTertiaryFixedVariant = preset.darkOnTertiaryContainer,
    )
}
