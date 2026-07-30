package com.xiaowei.player.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryLight = Color(0xFF4D5C92)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFDBE1FF)
val OnPrimaryContainerLight = Color(0xFF00164A)
val SecondaryLight = Color(0xFF5A5D72)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDFE1F9)
val OnSecondaryContainerLight = Color(0xFF171A2C)
val TertiaryLight = Color(0xFF765571)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD7F2)
val OnTertiaryContainerLight = Color(0xFF2D1428)
val BackgroundLight = Color(0xFFF1EDFA)
val OnBackgroundLight = Color(0xFF1B1B21)
val SurfaceLight = Color(0xFFF1EDFA)
val OnSurfaceLight = Color(0xFF1B1B21)
val SurfaceVariantLight = Color(0xFFE2E1EC)
val OnSurfaceVariantLight = Color(0xFF45464F)
val OutlineLight = Color(0xFF767680)
val OutlineVariantLight = Color(0xFFC6C5D0)

val MineCardLight = Color(0xFFFBFAFF)

val PrimaryDark = Color(0xFFB5C5FF)
val OnPrimaryDark = Color(0xFF1A2E60)
val PrimaryContainerDark = Color(0xFF334578)
val OnPrimaryContainerDark = Color(0xFFDBE1FF)
val SecondaryDark = Color(0xFFC3C5DD)
val OnSecondaryDark = Color(0xFF2C2F42)
val SecondaryContainerDark = Color(0xFF434659)
val OnSecondaryContainerDark = Color(0xFFDFE1F9)
val TertiaryDark = Color(0xFFE5BBDA)
val OnTertiaryDark = Color(0xFF44263E)
val TertiaryContainerDark = Color(0xFF5C3C56)
val OnTertiaryContainerDark = Color(0xFFFFD7F2)
val BackgroundDark = Color(0xFF121318)
val OnBackgroundDark = Color(0xFFE3E2E9)
val SurfaceDark = Color(0xFF121318)
val OnSurfaceDark = Color(0xFFE3E2E9)
val SurfaceVariantDark = Color(0xFF45464F)
val OnSurfaceVariantDark = Color(0xFFC6C5D0)
val OutlineDark = Color(0xFF8F909A)
val OutlineVariantDark = Color(0xFF45464F)

val MineCardDark = Color(0xFF1E1F26)

val PlayerGradientStart = Color(0xFF4D5C92)
val PlayerGradientEnd = Color(0xFF1A1F33)

data class PresetThemeColor(
    val swatch: Color,

    val lightPrimary: Color, val lightOnPrimary: Color,
    val lightPrimaryContainer: Color, val lightOnPrimaryContainer: Color,
    val lightSecondary: Color, val lightOnSecondary: Color,
    val lightSecondaryContainer: Color, val lightOnSecondaryContainer: Color,
    val lightTertiary: Color, val lightOnTertiary: Color,
    val lightTertiaryContainer: Color, val lightOnTertiaryContainer: Color,
    val lightBackground: Color, val lightOnBackground: Color,
    val lightSurface: Color, val lightOnSurface: Color,
    val lightSurfaceVariant: Color, val lightOnSurfaceVariant: Color,
    val lightOutline: Color, val lightOutlineVariant: Color,

    val darkPrimary: Color, val darkOnPrimary: Color,
    val darkPrimaryContainer: Color, val darkOnPrimaryContainer: Color,
    val darkSecondary: Color, val darkOnSecondary: Color,
    val darkSecondaryContainer: Color, val darkOnSecondaryContainer: Color,
    val darkTertiary: Color, val darkOnTertiary: Color,
    val darkTertiaryContainer: Color, val darkOnTertiaryContainer: Color,
    val darkBackground: Color, val darkOnBackground: Color,
    val darkSurface: Color, val darkOnSurface: Color,
    val darkSurfaceVariant: Color, val darkOnSurfaceVariant: Color,
    val darkOutline: Color, val darkOutlineVariant: Color,
)

val PRESET_THEME_COLORS = listOf(

    PresetThemeColor(
        swatch = Color(0xFF4D5C92),

        lightPrimary = Color(0xFF4D5C92), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFDBE1FF), lightOnPrimaryContainer = Color(0xFF00164A),
        lightSecondary = Color(0xFF5A5D72), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFDFE1F9), lightOnSecondaryContainer = Color(0xFF171A2C),
        lightTertiary = Color(0xFF765571), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFD7F2), lightOnTertiaryContainer = Color(0xFF2D1428),
        lightBackground = Color(0xFFF1EDFA), lightOnBackground = Color(0xFF1B1B21),
        lightSurface = Color(0xFFF1EDFA), lightOnSurface = Color(0xFF1B1B21),
        lightSurfaceVariant = Color(0xFFE2E1EC), lightOnSurfaceVariant = Color(0xFF45464F),
        lightOutline = Color(0xFF767680), lightOutlineVariant = Color(0xFFC6C5D0),

        darkPrimary = Color(0xFFB5C5FF), darkOnPrimary = Color(0xFF1A2E60),
        darkPrimaryContainer = Color(0xFF334578), darkOnPrimaryContainer = Color(0xFFDBE1FF),
        darkSecondary = Color(0xFFC3C5DD), darkOnSecondary = Color(0xFF2C2F42),
        darkSecondaryContainer = Color(0xFF434659), darkOnSecondaryContainer = Color(0xFFDFE1F9),
        darkTertiary = Color(0xFFE5BBDA), darkOnTertiary = Color(0xFF44263E),
        darkTertiaryContainer = Color(0xFF5C3C56), darkOnTertiaryContainer = Color(0xFFFFD7F2),
        darkBackground = Color(0xFF121318), darkOnBackground = Color(0xFFE3E2E9),
        darkSurface = Color(0xFF121318), darkOnSurface = Color(0xFFE3E2E9),
        darkSurfaceVariant = Color(0xFF45464F), darkOnSurfaceVariant = Color(0xFFC6C5D0),
        darkOutline = Color(0xFF8F909A), darkOutlineVariant = Color(0xFF45464F),
    ),

    PresetThemeColor(
        swatch = Color(0xFF2196F3),

        lightPrimary = Color(0xFF0061A4), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFD1E4FF), lightOnPrimaryContainer = Color(0xFF001D36),
        lightSecondary = Color(0xFF535F70), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFD7E3F7), lightOnSecondaryContainer = Color(0xFF101C2B),
        lightTertiary = Color(0xFF6B5778), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFF2DAFF), lightOnTertiaryContainer = Color(0xFF251431),
        lightBackground = Color(0xFFFDFCFF), lightOnBackground = Color(0xFF1A1C1E),
        lightSurface = Color(0xFFFDFCFF), lightOnSurface = Color(0xFF1A1C1E),
        lightSurfaceVariant = Color(0xFFDFE2EB), lightOnSurfaceVariant = Color(0xFF43474E),
        lightOutline = Color(0xFF73777F), lightOutlineVariant = Color(0xFFC3C6CF),

        darkPrimary = Color(0xFF9ECAFF), darkOnPrimary = Color(0xFF003258),
        darkPrimaryContainer = Color(0xFF00497D), darkOnPrimaryContainer = Color(0xFFD1E4FF),
        darkSecondary = Color(0xFFBBC7DB), darkOnSecondary = Color(0xFF253140),
        darkSecondaryContainer = Color(0xFF3B4858), darkOnSecondaryContainer = Color(0xFFD7E3F7),
        darkTertiary = Color(0xFFD7BEE4), darkOnTertiary = Color(0xFF3C2948),
        darkTertiaryContainer = Color(0xFF533F5F), darkOnTertiaryContainer = Color(0xFFF2DAFF),
        darkBackground = Color(0xFF1A1C1E), darkOnBackground = Color(0xFFE2E2E5),
        darkSurface = Color(0xFF1A1C1E), darkOnSurface = Color(0xFFE2E2E5),
        darkSurfaceVariant = Color(0xFF43474E), darkOnSurfaceVariant = Color(0xFFC3C6CF),
        darkOutline = Color(0xFF8D9199), darkOutlineVariant = Color(0xFF43474E),
    ),

    PresetThemeColor(
        swatch = Color(0xFF00BCD4),

        lightPrimary = Color(0xFF006877), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF9CEFFF), lightOnPrimaryContainer = Color(0xFF001F24),
        lightSecondary = Color(0xFF4A6268), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFCDE7ED), lightOnSecondaryContainer = Color(0xFF051F23),
        lightTertiary = Color(0xFF525E7D), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFDBE2FF), lightOnTertiaryContainer = Color(0xFF0E1A37),
        lightBackground = Color(0xFFF7FAFC), lightOnBackground = Color(0xFF191C1D),
        lightSurface = Color(0xFFF7FAFC), lightOnSurface = Color(0xFF191C1D),
        lightSurfaceVariant = Color(0xFFDAE4E8), lightOnSurfaceVariant = Color(0xFF3F494B),
        lightOutline = Color(0xFF6F797B), lightOutlineVariant = Color(0xFFBEC8CC),

        darkPrimary = Color(0xFF82D3E6), darkOnPrimary = Color(0xFF00363D),
        darkPrimaryContainer = Color(0xFF004E59), darkOnPrimaryContainer = Color(0xFF9CEFFF),
        darkSecondary = Color(0xFFB1CBD1), darkOnSecondary = Color(0xFF1C3439),
        darkSecondaryContainer = Color(0xFF334A50), darkOnSecondaryContainer = Color(0xFFCDE7ED),
        darkTertiary = Color(0xFFBAC6EB), darkOnTertiary = Color(0xFF24304C),
        darkTertiaryContainer = Color(0xFF3B4663), darkOnTertiaryContainer = Color(0xFFDBE2FF),
        darkBackground = Color(0xFF001F24), darkOnBackground = Color(0xFFE2EDEF),
        darkSurface = Color(0xFF001F24), darkOnSurface = Color(0xFFE2EDEF),
        darkSurfaceVariant = Color(0xFF3F494B), darkOnSurfaceVariant = Color(0xFFBEC8CC),
        darkOutline = Color(0xFF899399), darkOutlineVariant = Color(0xFF3F494B),
    ),

    PresetThemeColor(
        swatch = Color(0xFF00658E),

        lightPrimary = Color(0xFF00658E), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFC6E7FF), lightOnPrimaryContainer = Color(0xFF001E2E),
        lightSecondary = Color(0xFF4F616F), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFD2E5F5), lightOnSecondaryContainer = Color(0xFF0B1D29),
        lightTertiary = Color(0xFF6B5876), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFD3BCDF), lightOnTertiaryContainer = Color(0xFF271630),
        lightBackground = Color(0xFFF4F9FD), lightOnBackground = Color(0xFF171C1F),
        lightSurface = Color(0xFFF4F9FD), lightOnSurface = Color(0xFF171C1F),
        lightSurfaceVariant = Color(0xFFDDE3EA), lightOnSurfaceVariant = Color(0xFF41474D),
        lightOutline = Color(0xFF71787E), lightOutlineVariant = Color(0xFFC0C7CE),

        darkPrimary = Color(0xFF81CFFF), darkOnPrimary = Color(0xFF00344A),
        darkPrimaryContainer = Color(0xFF004B69), darkOnPrimaryContainer = Color(0xFFC6E7FF),
        darkSecondary = Color(0xFFB4C9DC), darkOnSecondary = Color(0xFF1F2F3A),
        darkSecondaryContainer = Color(0xFF384C57), darkOnSecondaryContainer = Color(0xFFD2E5F5),
        darkTertiary = Color(0xFFD7BEE4), darkOnTertiary = Color(0xFF3C2A48),
        darkTertiaryContainer = Color(0xFF53415E), darkOnTertiaryContainer = Color(0xFFD3BCDF),
        darkBackground = Color(0xFF0E1C25), darkOnBackground = Color(0xFFE0E2E8),
        darkSurface = Color(0xFF0E1C25), darkOnSurface = Color(0xFFE0E2E8),
        darkSurfaceVariant = Color(0xFF41474D), darkOnSurfaceVariant = Color(0xFFC0C7CE),
        darkOutline = Color(0xFF8B9299), darkOutlineVariant = Color(0xFF41474D),
    ),

    PresetThemeColor(
        swatch = Color(0xFF7B4FA3),

        lightPrimary = Color(0xFF7B4FA3), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFEDD7FF), lightOnPrimaryContainer = Color(0xFF2C004A),
        lightSecondary = Color(0xFF6A5A7C), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFEFDDF6), lightOnSecondaryContainer = Color(0xFF251731),
        lightTertiary = Color(0xFF9B6F8C), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFD8E8), lightOnTertiaryContainer = Color(0xFF370D27),
        lightBackground = Color(0xFFF6EEFA), lightOnBackground = Color(0xFF1E1A22),
        lightSurface = Color(0xFFF6EEFA), lightOnSurface = Color(0xFF1E1A22),
        lightSurfaceVariant = Color(0xFFE8E0EC), lightOnSurfaceVariant = Color(0xFF4A444E),
        lightOutline = Color(0xFF7B757E), lightOutlineVariant = Color(0xFFCBC4CF),

        darkPrimary = Color(0xFFE0B6FF), darkOnPrimary = Color(0xFF421067),
        darkPrimaryContainer = Color(0xFF5B2A7C), darkOnPrimaryContainer = Color(0xFFEDD7FF),
        darkSecondary = Color(0xFFD3C2DA), darkOnSecondary = Color(0xFF3A2C40),
        darkSecondaryContainer = Color(0xFF524257), darkOnSecondaryContainer = Color(0xFFEFDDF6),
        darkTertiary = Color(0xFFFFB0CD), darkOnTertiary = Color(0xFF53354A),
        darkTertiaryContainer = Color(0xFF7E4B61), darkOnTertiaryContainer = Color(0xFFFFD8E8),
        darkBackground = Color(0xFF161217), darkOnBackground = Color(0xFFE5E0E6),
        darkSurface = Color(0xFF161217), darkOnSurface = Color(0xFFE5E0E6),
        darkSurfaceVariant = Color(0xFF4A444E), darkOnSurfaceVariant = Color(0xFFCBC4CF),
        darkOutline = Color(0xFF948F98), darkOutlineVariant = Color(0xFF4A444E),
    ),

    PresetThemeColor(
        swatch = Color(0xFF673AB7),

        lightPrimary = Color(0xFF673AB7), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFE9DDFF), lightOnPrimaryContainer = Color(0xFF21005D),
        lightSecondary = Color(0xFF625B71), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFE8DEF0), lightOnSecondaryContainer = Color(0xFF1E192B),
        lightTertiary = Color(0xFF7D5260), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFD8E4), lightOnTertiaryContainer = Color(0xFF31111D),
        lightBackground = Color(0xFFFEF7FF), lightOnBackground = Color(0xFF1D1B20),
        lightSurface = Color(0xFFFEF7FF), lightOnSurface = Color(0xFF1D1B20),
        lightSurfaceVariant = Color(0xFFE7E0EB), lightOnSurfaceVariant = Color(0xFF49454E),
        lightOutline = Color(0xFF7A757F), lightOutlineVariant = Color(0xFFCAC4CF),

        darkPrimary = Color(0xFFCFBCFF), darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378A), darkOnPrimaryContainer = Color(0xFFE9DDFF),
        darkSecondary = Color(0xFFCCC2DC), darkOnSecondary = Color(0xFF332D41),
        darkSecondaryContainer = Color(0xFF4A4458), darkOnSecondaryContainer = Color(0xFFE8DEF0),
        darkTertiary = Color(0xFFEFB8C8), darkOnTertiary = Color(0xFF492532),
        darkTertiaryContainer = Color(0xFF633B48), darkOnTertiaryContainer = Color(0xFFFFD8E4),
        darkBackground = Color(0xFF141218), darkOnBackground = Color(0xFFE6E0E9),
        darkSurface = Color(0xFF141218), darkOnSurface = Color(0xFFE6E0E9),
        darkSurfaceVariant = Color(0xFF49454E), darkOnSurfaceVariant = Color(0xFFCAC4CF),
        darkOutline = Color(0xFF948F99), darkOutlineVariant = Color(0xFF49454E),
    ),

    PresetThemeColor(
        swatch = Color(0xFFB3174D),

        lightPrimary = Color(0xFFB3174D), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFD9DD), lightOnPrimaryContainer = Color(0xFF400017),
        lightSecondary = Color(0xFF735263), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFDD8E3), lightOnSecondaryContainer = Color(0xFF2A111C),
        lightTertiary = Color(0xFF9B5C3F), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDBCC), lightOnTertiaryContainer = Color(0xFF371006),
        lightBackground = Color(0xFFFFF8F7), lightOnBackground = Color(0xFF221920),
        lightSurface = Color(0xFFFFF8F7), lightOnSurface = Color(0xFF221920),
        lightSurfaceVariant = Color(0xFFF2DDE0), lightOnSurfaceVariant = Color(0xFF524347),
        lightOutline = Color(0xFF827076), lightOutlineVariant = Color(0xFFD5C2C5),

        darkPrimary = Color(0xFFFFB1B8), darkOnPrimary = Color(0xFF670024),
        darkPrimaryContainer = Color(0xFF8B0037), darkOnPrimaryContainer = Color(0xFFFFD9DD),
        darkSecondary = Color(0xFFE0B9C5), darkOnSecondary = Color(0xFF41252E),
        darkSecondaryContainer = Color(0xFF593B44), darkOnSecondaryContainer = Color(0xFFFDD8E3),
        darkTertiary = Color(0xFFFFB68E), darkOnTertiary = Color(0xFF55200F),
        darkTertiaryContainer = Color(0xFF7E3923), darkOnTertiaryContainer = Color(0xFFFFDBCC),
        darkBackground = Color(0xFF1F1115), darkOnBackground = Color(0xFFEEE0E2),
        darkSurface = Color(0xFF1F1115), darkOnSurface = Color(0xFFEEE0E2),
        darkSurfaceVariant = Color(0xFF514347), darkOnSurfaceVariant = Color(0xFFD5C2C5),
        darkOutline = Color(0xFF9E8C91), darkOutlineVariant = Color(0xFF514347),
    ),

    PresetThemeColor(
        swatch = Color(0xFFE91E63),

        lightPrimary = Color(0xFF9C4146), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDADA), lightOnPrimaryContainer = Color(0xFF410008),
        lightSecondary = Color(0xFF775656), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFFDADA), lightOnSecondaryContainer = Color(0xFF2C1515),
        lightTertiary = Color(0xFF725C71), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFDD8F4), lightOnTertiaryContainer = Color(0xFF2A1527),
        lightBackground = Color(0xFFFFF8F7), lightOnBackground = Color(0xFF221919),
        lightSurface = Color(0xFFFFF8F7), lightOnSurface = Color(0xFF221919),
        lightSurfaceVariant = Color(0xFFF4DDDD), lightOnSurfaceVariant = Color(0xFF524343),
        lightOutline = Color(0xFF857373), lightOutlineVariant = Color(0xFFD7C1BE),

        darkPrimary = Color(0xFFFFB3B5), darkOnPrimary = Color(0xFF5F131B),
        darkPrimaryContainer = Color(0xFF7E2A2F), darkOnPrimaryContainer = Color(0xFFFFDADA),
        darkSecondary = Color(0xFFE7BDB9), darkOnSecondary = Color(0xFF44292A),
        darkSecondaryContainer = Color(0xFF5D3F3F), darkOnSecondaryContainer = Color(0xFFFFDADA),
        darkTertiary = Color(0xFFE0B9CE), darkOnTertiary = Color(0xFF422743),
        darkTertiaryContainer = Color(0xFF5A3D5A), darkOnTertiaryContainer = Color(0xFFFDD8F4),
        darkBackground = Color(0xFF1A1111), darkOnBackground = Color(0xFFF0DEDD),
        darkSurface = Color(0xFF1A1111), darkOnSurface = Color(0xFFF0DEDD),
        darkSurfaceVariant = Color(0xFF524343), darkOnSurfaceVariant = Color(0xFFD7C1BE),
        darkOutline = Color(0xFF9D8C8A), darkOutlineVariant = Color(0xFF524343),
    ),

    PresetThemeColor(
        swatch = Color(0xFFB3261E),

        lightPrimary = Color(0xFFB3261E), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDAD6), lightOnPrimaryContainer = Color(0xFF410002),
        lightSecondary = Color(0xFF9C4645), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFFDAD6), lightOnSecondaryContainer = Color(0xFF410005),
        lightTertiary = Color(0xFF7A5733), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDDB8), lightOnTertiaryContainer = Color(0xFF2B1700),
        lightBackground = Color(0xFFFFF8F7), lightOnBackground = Color(0xFF221918),
        lightSurface = Color(0xFFFFF8F7), lightOnSurface = Color(0xFF221918),
        lightSurfaceVariant = Color(0xFFF5DDDC), lightOnSurfaceVariant = Color(0xFF534342),
        lightOutline = Color(0xFF857373), lightOutlineVariant = Color(0xFFD8C2BE),

        darkPrimary = Color(0xFFFFB4AB), darkOnPrimary = Color(0xFF690005),
        darkPrimaryContainer = Color(0xFF93000A), darkOnPrimaryContainer = Color(0xFFFFDAD6),
        darkSecondary = Color(0xFFE5B3B0), darkOnSecondary = Color(0xFF432523),
        darkSecondaryContainer = Color(0xFF5B3B38), darkOnSecondaryContainer = Color(0xFFFFDAD6),
        darkTertiary = Color(0xFFF4C38B), darkOnTertiary = Color(0xFF4A2800),
        darkTertiaryContainer = Color(0xFF613F17), darkOnTertiaryContainer = Color(0xFFFFDDB8),
        darkBackground = Color(0xFF1F1110), darkOnBackground = Color(0xFFEDE0DD),
        darkSurface = Color(0xFF1F1110), darkOnSurface = Color(0xFFEDE0DD),
        darkSurfaceVariant = Color(0xFF534342), darkOnSurfaceVariant = Color(0xFFD8C2BE),
        darkOutline = Color(0xFF9F8C8A), darkOutlineVariant = Color(0xFF534342),
    ),

    PresetThemeColor(
        swatch = Color(0xFF8C5018),

        lightPrimary = Color(0xFF8C5018), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDCBE), lightOnPrimaryContainer = Color(0xFF2D1600),
        lightSecondary = Color(0xFF745944), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFFDCC1), lightOnSecondaryContainer = Color(0xFF2A1700),
        lightTertiary = Color(0xFF9C584F), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDAD4), lightOnTertiaryContainer = Color(0xFF3B0905),
        lightBackground = Color(0xFFFFF8F6), lightOnBackground = Color(0xFF221A12),
        lightSurface = Color(0xFFFFF8F6), lightOnSurface = Color(0xFF221A12),
        lightSurfaceVariant = Color(0xFFF4DFD2), lightOnSurfaceVariant = Color(0xFF52442F),
        lightOutline = Color(0xFF7F6B5C), lightOutlineVariant = Color(0xFFD8C3B6),

        darkPrimary = Color(0xFFFFB876), darkOnPrimary = Color(0xFF4C2A00),
        darkPrimaryContainer = Color(0xFF6E3F00), darkOnPrimaryContainer = Color(0xFFFFDCBE),
        darkSecondary = Color(0xFFE4C0AC), darkOnSecondary = Color(0xFF432C1A),
        darkSecondaryContainer = Color(0xFF5B422E), darkOnSecondaryContainer = Color(0xFFFFDCC1),
        darkTertiary = Color(0xFFFFB3A6), darkOnTertiary = Color(0xFF5C1B12),
        darkTertiaryContainer = Color(0xFF7E3228), darkOnTertiaryContainer = Color(0xFFFFDAD4),
        darkBackground = Color(0xFF1F1207), darkOnBackground = Color(0xFFEDE0D0),
        darkSurface = Color(0xFF1F1207), darkOnSurface = Color(0xFFEDE0D0),
        darkSurfaceVariant = Color(0xFF52442F), darkOnSurfaceVariant = Color(0xFFD8C3B6),
        darkOutline = Color(0xFF9B8B79), darkOutlineVariant = Color(0xFF52442F),
    ),

    PresetThemeColor(
        swatch = Color(0xFFFF5722),

        lightPrimary = Color(0xFFB5381A), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDBCF), lightOnPrimaryContainer = Color(0xFF390C00),
        lightSecondary = Color(0xFF77574C), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFFDBCF), lightOnSecondaryContainer = Color(0xFF2C150C),
        lightTertiary = Color(0xFF6C5D2F), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFF6E0A7), lightOnTertiaryContainer = Color(0xFF221B00),
        lightBackground = Color(0xFFFFF8F6), lightOnBackground = Color(0xFF221A18),
        lightSurface = Color(0xFFFFF8F6), lightOnSurface = Color(0xFF221A18),
        lightSurfaceVariant = Color(0xFFF5DED8), lightOnSurfaceVariant = Color(0xFF53443F),
        lightOutline = Color(0xFF85746E), lightOutlineVariant = Color(0xFFD8C2BB),

        darkPrimary = Color(0xFFFFB59E), darkOnPrimary = Color(0xFF5D1900),
        darkPrimaryContainer = Color(0xFF832300), darkOnPrimaryContainer = Color(0xFFFFDBCF),
        darkSecondary = Color(0xFFE7BEB0), darkOnSecondary = Color(0xFF442A20),
        darkSecondaryContainer = Color(0xFF5D4035), darkOnSecondaryContainer = Color(0xFFFFDBCF),
        darkTertiary = Color(0xFFDAC38D), darkOnTertiary = Color(0xFF3C2E05),
        darkTertiaryContainer = Color(0xFF544519), darkOnTertiaryContainer = Color(0xFFF6E0A7),
        darkBackground = Color(0xFF1A1100), darkOnBackground = Color(0xFFF1E0D8),
        darkSurface = Color(0xFF1A1100), darkOnSurface = Color(0xFFF1E0D8),
        darkSurfaceVariant = Color(0xFF53443F), darkOnSurfaceVariant = Color(0xFFD8C2BB),
        darkOutline = Color(0xFF9D8E89), darkOutlineVariant = Color(0xFF53443F),
    ),

    PresetThemeColor(
        swatch = Color(0xFF735C00),

        lightPrimary = Color(0xFF735C00), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFE07D), lightOnPrimaryContainer = Color(0xFF231B00),
        lightSecondary = Color(0xFF706040), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFBE3AB), lightOnSecondaryContainer = Color(0xFF261B00),
        lightTertiary = Color(0xFF9C6F44), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDCC2), lightOnTertiaryContainer = Color(0xFF321500),
        lightBackground = Color(0xFFFFF9EC), lightOnBackground = Color(0xFF1E1B00),
        lightSurface = Color(0xFFFFF9EC), lightOnSurface = Color(0xFF1E1B00),
        lightSurfaceVariant = Color(0xFFEDE2C9), lightOnSurfaceVariant = Color(0xFF4B3F1C),
        lightOutline = Color(0xFF7E7762), lightOutlineVariant = Color(0xFFD0C7AC),

        darkPrimary = Color(0xFFE9C34A), darkOnPrimary = Color(0xFF3C2F00),
        darkPrimaryContainer = Color(0xFF564500), darkOnPrimaryContainer = Color(0xFFFFE07D),
        darkSecondary = Color(0xFFDEC58E), darkOnSecondary = Color(0xFF3F2E0F),
        darkSecondaryContainer = Color(0xFF574524), darkOnSecondaryContainer = Color(0xFFFBE3AB),
        darkTertiary = Color(0xFFFFB683), darkOnTertiary = Color(0xFF522100),
        darkTertiaryContainer = Color(0xFF80502B), darkOnTertiaryContainer = Color(0xFFFFDCC2),
        darkBackground = Color(0xFF1B1200), darkOnBackground = Color(0xFFEBE2C4),
        darkSurface = Color(0xFF1B1200), darkOnSurface = Color(0xFFEBE2C4),
        darkSurfaceVariant = Color(0xFF4B3F1C), darkOnSurfaceVariant = Color(0xFFD0C7AC),
        darkOutline = Color(0xFF968F77), darkOutlineVariant = Color(0xFF4B3F1C),
    ),

    PresetThemeColor(
        swatch = Color(0xFF7CB342),

        lightPrimary = Color(0xFF4D6700), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFC7F070), lightOnPrimaryContainer = Color(0xFF131F00),
        lightSecondary = Color(0xFF586249), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFDDE7C8), lightOnSecondaryContainer = Color(0xFF151E0B),
        lightTertiary = Color(0xFF8A6843), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDCBB), lightOnTertiaryContainer = Color(0xFF2D1700),
        lightBackground = Color(0xFFFDF8F0), lightOnBackground = Color(0xFF1A1C12),
        lightSurface = Color(0xFFFDF8F0), lightOnSurface = Color(0xFF1A1C12),
        lightSurfaceVariant = Color(0xFFE0E4D5), lightOnSurfaceVariant = Color(0xFF44483D),
        lightOutline = Color(0xFF747968), lightOutlineVariant = Color(0xFFC4C8B9),

        darkPrimary = Color(0xFFACD449), darkOnPrimary = Color(0xFF253600),
        darkPrimaryContainer = Color(0xFF384E00), darkOnPrimaryContainer = Color(0xFFC7F070),
        darkSecondary = Color(0xFFBFCBAD), darkOnSecondary = Color(0xFF2A331F),
        darkSecondaryContainer = Color(0xFF404A34), darkOnSecondaryContainer = Color(0xFFDDE7C8),
        darkTertiary = Color(0xFFFFB870), darkOnTertiary = Color(0xFF4E2500),
        darkTertiaryContainer = Color(0xFF6E3E00), darkOnTertiaryContainer = Color(0xFFFFDCBB),
        darkBackground = Color(0xFF1A1B12), darkOnBackground = Color(0xFFE3E3D8),
        darkSurface = Color(0xFF1A1B12), darkOnSurface = Color(0xFFE3E3D8),
        darkSurfaceVariant = Color(0xFF44483D), darkOnSurfaceVariant = Color(0xFFC4C8B9),
        darkOutline = Color(0xFF8E9281), darkOutlineVariant = Color(0xFF44483D),
    ),

    PresetThemeColor(
        swatch = Color(0xFF386A20),

        lightPrimary = Color(0xFF386A20), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFB8F397), lightOnPrimaryContainer = Color(0xFF042100),
        lightSecondary = Color(0xFF576249), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFDBE7C8), lightOnSecondaryContainer = Color(0xFF151E0E),
        lightTertiary = Color(0xFF8A6843), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFDCBB), lightOnTertiaryContainer = Color(0xFF2D1700),
        lightBackground = Color(0xFFFDF8F0), lightOnBackground = Color(0xFF1A1C12),
        lightSurface = Color(0xFFFDF8F0), lightOnSurface = Color(0xFF1A1C12),
        lightSurfaceVariant = Color(0xFFE0E4D5), lightOnSurfaceVariant = Color(0xFF44483D),
        lightOutline = Color(0xFF747968), lightOutlineVariant = Color(0xFFC4C8B9),

        darkPrimary = Color(0xFF9DD67E), darkOnPrimary = Color(0xFF0E3900),
        darkPrimaryContainer = Color(0xFF1E5108), darkOnPrimaryContainer = Color(0xFFB8F397),
        darkSecondary = Color(0xFFBFCBAD), darkOnSecondary = Color(0xFF2A331F),
        darkSecondaryContainer = Color(0xFF404A34), darkOnSecondaryContainer = Color(0xFFDBE7C8),
        darkTertiary = Color(0xFFFFB870), darkOnTertiary = Color(0xFF4E2500),
        darkTertiaryContainer = Color(0xFF6E3E00), darkOnTertiaryContainer = Color(0xFFFFDCBB),
        darkBackground = Color(0xFF1A1B12), darkOnBackground = Color(0xFFE3E3D8),
        darkSurface = Color(0xFF1A1B12), darkOnSurface = Color(0xFFE3E3D8),
        darkSurfaceVariant = Color(0xFF44483D), darkOnSurfaceVariant = Color(0xFFC4C8B9),
        darkOutline = Color(0xFF8E9281), darkOutlineVariant = Color(0xFF44483D),
    ),

    PresetThemeColor(
        swatch = Color(0xFF00897B),

        lightPrimary = Color(0xFF006B5C), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF7AF8DE), lightOnPrimaryContainer = Color(0xFF00201B),
        lightSecondary = Color(0xFF4A635F), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFCDE8E2), lightOnSecondaryContainer = Color(0xFF05201C),
        lightTertiary = Color(0xFF476279), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFCDE6FF), lightOnTertiaryContainer = Color(0xFF001E30),
        lightBackground = Color(0xFFF4FBF8), lightOnBackground = Color(0xFF161D1B),
        lightSurface = Color(0xFFF4FBF8), lightOnSurface = Color(0xFF161D1B),
        lightSurfaceVariant = Color(0xFFDAE5E1), lightOnSurfaceVariant = Color(0xFF3F4946),
        lightOutline = Color(0xFF6F7976), lightOutlineVariant = Color(0xFFBEC9C4),

        darkPrimary = Color(0xFF5CDBBE), darkOnPrimary = Color(0xFF00382F),
        darkPrimaryContainer = Color(0xFF005144), darkOnPrimaryContainer = Color(0xFF7AF8DE),
        darkSecondary = Color(0xFFB1CCCA), darkOnSecondary = Color(0xFF1C3531),
        darkSecondaryContainer = Color(0xFF334B48), darkOnSecondaryContainer = Color(0xFFCDE8E2),
        darkTertiary = Color(0xFFAECAE6), darkOnTertiary = Color(0xFF17344A),
        darkTertiaryContainer = Color(0xFF2F4A61), darkOnTertiaryContainer = Color(0xFFCDE6FF),
        darkBackground = Color(0xFF0E1A17), darkOnBackground = Color(0xFFDDE4E0),
        darkSurface = Color(0xFF0E1A17), darkOnSurface = Color(0xFFDDE4E0),
        darkSurfaceVariant = Color(0xFF3F4946), darkOnSurfaceVariant = Color(0xFFBEC9C4),
        darkOutline = Color(0xFF89938F), darkOutlineVariant = Color(0xFF3F4946),
    ),

    PresetThemeColor(
        swatch = Color(0xFF795548),

        lightPrimary = Color(0xFF795548), lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDBCF), lightOnPrimaryContainer = Color(0xFF2C150F),
        lightSecondary = Color(0xFF735E52), lightOnSecondary = Color(0xFFFFFFFF),
        lightSecondaryContainer = Color(0xFFFCDCC8), lightOnSecondaryContainer = Color(0xFF2A1B11),
        lightTertiary = Color(0xFF827067), lightOnTertiary = Color(0xFFFFFFFF),
        lightTertiaryContainer = Color(0xFFFFD7C4), lightOnTertiaryContainer = Color(0xFF31201A),
        lightBackground = Color(0xFFFFF8F6), lightOnBackground = Color(0xFF221A18),
        lightSurface = Color(0xFFFFF8F6), lightOnSurface = Color(0xFF221A18),
        lightSurfaceVariant = Color(0xFFF4DDD7), lightOnSurfaceVariant = Color(0xFF52443F),
        lightOutline = Color(0xFF85746E), lightOutlineVariant = Color(0xFFD8C2BB),

        darkPrimary = Color(0xFFE7B6A1), darkOnPrimary = Color(0xFF451B12),
        darkPrimaryContainer = Color(0xFF5E3025), darkOnPrimaryContainer = Color(0xFFFFDBCF),
        darkSecondary = Color(0xFFE7BFA9), darkOnSecondary = Color(0xFF442C20),
        darkSecondaryContainer = Color(0xFF5D4334), darkOnSecondaryContainer = Color(0xFFFCDCC8),
        darkTertiary = Color(0xFFE7BFA5), darkOnTertiary = Color(0xFF45291E),
        darkTertiaryContainer = Color(0xFF5E3F32), darkOnTertiaryContainer = Color(0xFFFFD7C4),
        darkBackground = Color(0xFF1A1100), darkOnBackground = Color(0xFFF1E0D8),
        darkSurface = Color(0xFF1A1100), darkOnSurface = Color(0xFFF1E0D8),
        darkSurfaceVariant = Color(0xFF52443F), darkOnSurfaceVariant = Color(0xFFD8C2BB),
        darkOutline = Color(0xFF9D8E89), darkOutlineVariant = Color(0xFF52443F),
    ),
)

const val DEFAULT_THEME_COLOR_INDEX = 0
