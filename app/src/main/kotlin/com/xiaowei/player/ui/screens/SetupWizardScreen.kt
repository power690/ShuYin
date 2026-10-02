package com.xiaowei.player.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaowei.player.R
import com.xiaowei.player.data.AudioMixPrefs
import com.xiaowei.player.data.DarkModePrefs
import com.xiaowei.player.data.LocalePrefs
import com.xiaowei.player.data.ThemePrefs
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.components.M3ExpressiveSwitch
import com.xiaowei.player.ui.theme.PRESET_THEME_COLORS

private enum class WizardPage {
    LANGUAGE, THEME, MATERIAL, PLAYER_STYLE, PLAYBACK, WELCOME
}

private fun wizardPageList(): List<WizardPage> = buildList {
    add(WizardPage.LANGUAGE)
    add(WizardPage.THEME)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(WizardPage.MATERIAL)
    }
    add(WizardPage.PLAYER_STYLE)
    add(WizardPage.PLAYBACK)
    add(WizardPage.WELCOME)
}

private enum class WizardIconTone {
    PRIMARY, SECONDARY, TERTIARY
}

@Composable
private fun WizardIconTone.toneContainerColor(): Color = when (this) {
    WizardIconTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    WizardIconTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    WizardIconTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun WizardIconTone.toneContentColor(): Color = when (this) {
    WizardIconTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    WizardIconTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    WizardIconTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun WizardIconBadge(
    icon: ImageVector,
    tone: WizardIconTone
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(tone.toneContainerColor()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.toneContentColor(),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WizardSettingItem(
    icon: ImageVector,
    tone: WizardIconTone,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    checked: Boolean? = null,
    switchEnabled: Boolean = true,
    itemEnabled: Boolean = true,
    showDivider: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "wizardItemScale"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (itemEnabled) 1f else 0.4f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WizardIconBadge(icon = icon, tone = tone)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (checked != null) {
                M3ExpressiveSwitch(
                    checked = checked,
                    enabled = switchEnabled,
                    onCheckedChange = onCheckedChange
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun WizardDarkModeItem(
    currentMode: String,
    showDivider: Boolean = true,
    onSelect: (String) -> Unit
) {
    val followSystemLabel = Strings.get("dark_mode_follow_system")
    val lightLabel = Strings.get("dark_mode_light")
    val darkLabel = Strings.get("dark_mode_dark")
    var optionFontSize by remember(followSystemLabel, lightLabel, darkLabel) {
        mutableFloatStateOf(14f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WizardIconBadge(icon = Icons.Outlined.DarkMode, tone = WizardIconTone.PRIMARY)
            Spacer(Modifier.width(12.dp))
            Text(
                text = Strings.get("settings_dark_theme"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WizardDarkModeOptionCard(
                title = followSystemLabel,
                selected = currentMode == DarkModePrefs.MODE_SYSTEM,
                fontSize = optionFontSize,
                onTextOverflow = { optionFontSize = (optionFontSize - 0.5f).coerceAtLeast(9f) },
                onClick = { onSelect(DarkModePrefs.MODE_SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            WizardDarkModeOptionCard(
                title = lightLabel,
                selected = currentMode == DarkModePrefs.MODE_LIGHT,
                fontSize = optionFontSize,
                onTextOverflow = { optionFontSize = (optionFontSize - 0.5f).coerceAtLeast(9f) },
                onClick = { onSelect(DarkModePrefs.MODE_LIGHT) },
                modifier = Modifier.weight(1f)
            )
            WizardDarkModeOptionCard(
                title = darkLabel,
                selected = currentMode == DarkModePrefs.MODE_DARK,
                fontSize = optionFontSize,
                onTextOverflow = { optionFontSize = (optionFontSize - 0.5f).coerceAtLeast(9f) },
                onClick = { onSelect(DarkModePrefs.MODE_DARK) },
                modifier = Modifier.weight(1f)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun WizardDarkModeOptionCard(
    title: String,
    selected: Boolean,
    fontSize: Float,
    onTextOverflow: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 1.5.dp else 0.5.dp

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            fontSize = fontSize.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result ->
                if (result.hasVisualOverflow) {
                    onTextOverflow()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WizardThemeColorItem(
    enabled: Boolean,
    selectedIndex: Int,
    showDivider: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WizardIconBadge(icon = Icons.Outlined.Palette, tone = WizardIconTone.PRIMARY)
            Spacer(Modifier.width(12.dp))
            Text(
                text = Strings.get("settings_theme_color"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(PRESET_THEME_COLORS.size) { index ->
                val preset = PRESET_THEME_COLORS[index]
                WizardColorBall(
                    color = preset.swatch,
                    isSelected = index == selectedIndex,
                    enabled = enabled,
                    onClick = { onSelect(index) }
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun WizardColorBall(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SetupWizardScreen(onFinish: () -> Unit) {
    val pages = remember { wizardPageList() }
    val pageCount = pages.size
    var step by rememberSaveable { mutableIntStateOf(0) }
    var navDirection by remember { mutableIntStateOf(1) }

    BackHandler(enabled = step > 0) {
        navDirection = -1
        step -= 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
    ) {
        WizardStepDots(step = step, pageCount = pageCount)

        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                val direction = navDirection
                val enter = slideInHorizontally(tween(260)) { it / 5 * direction } + fadeIn(tween(260))
                val exit = slideOutHorizontally(tween(180)) { -it / 5 * direction } + fadeOut(tween(180))
                enter togetherWith exit
            },
            label = "wizardStep"
        ) { currentStep ->
            when (pages[currentStep]) {
                WizardPage.LANGUAGE -> WizardLanguagePage()
                WizardPage.THEME -> WizardThemePage()
                WizardPage.MATERIAL -> WizardMaterialPage()
                WizardPage.PLAYER_STYLE -> WizardPlayerStylePage()
                WizardPage.PLAYBACK -> WizardPlaybackPage()
                WizardPage.WELCOME -> WizardWelcomePage()
            }
        }

        WizardBottomBar(
            step = step,
            pageCount = pageCount,
            onPrev = {
                navDirection = -1
                step -= 1
            },
            onNext = {
                navDirection = 1
                step += 1
            },
            onFinish = onFinish
        )
    }
}

@Composable
private fun WizardStepDots(step: Int, pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(pageCount) { index ->
            val selected = index == step
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "wizardDotWidth"
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun WizardPageFrame(
    title: String,
    desc: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun WizardOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun WizardLanguagePage() {
    val context = LocalContext.current
    val localePrefs = remember { LocalePrefs.get(context) }
    val currentLangCode by localePrefs.languageCodeState

    WizardPageFrame(
        title = Strings.get("settings_language"),
        desc = Strings.get("wizard_step_language_desc")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(LANGUAGE_OPTIONS) { option ->
                val label = if (option.code == null) {
                    Strings.get("language_follow_system")
                } else {
                    option.displayName
                }
                WizardOptionRow(
                    label = label,
                    isSelected = option.code == currentLangCode,
                    onClick = { localePrefs.languageCode = option.code }
                )
            }
        }
    }
}

@Composable
private fun WizardThemePage() {
    val context = LocalContext.current
    val darkModePrefs = remember { DarkModePrefs.get(context) }
    val themePrefs = remember { ThemePrefs.get(context) }

    val darkMode by darkModePrefs.darkModeState
    val coverColorEnabled by themePrefs.coverColorEnabledState
    val dynamicColorEnabled by themePrefs.dynamicColorEnabledState
    val themeColorIndex by themePrefs.themeColorIndexState

    WizardPageFrame(
        title = Strings.get("settings_category_theme"),
        desc = Strings.get("wizard_step_theme_desc")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            WizardDarkModeItem(
                currentMode = darkMode,
                onSelect = { mode ->
                    darkModePrefs.darkMode = mode
                }
            )

            WizardThemeColorItem(
                enabled = !coverColorEnabled && !dynamicColorEnabled,
                selectedIndex = themeColorIndex,
                onSelect = { index ->
                    themePrefs.themeColorIndex = index
                }
            )

            WizardSettingItem(
                icon = Icons.Outlined.Album,
                tone = WizardIconTone.TERTIARY,
                title = Strings.get("settings_cover_color"),
                subtitle = Strings.get("settings_cover_color_desc"),
                checked = coverColorEnabled,
                switchEnabled = !dynamicColorEnabled,
                itemEnabled = !dynamicColorEnabled,
                showDivider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = { enabled ->
                    if (!dynamicColorEnabled) {
                        themePrefs.coverColorEnabled = enabled
                    }
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WizardSettingItem(
                    icon = Icons.Outlined.ColorLens,
                    tone = WizardIconTone.PRIMARY,
                    title = Strings.get("settings_dynamic_color"),
                    subtitle = Strings.get("settings_dynamic_color_desc"),
                    checked = dynamicColorEnabled,
                    switchEnabled = !coverColorEnabled,
                    itemEnabled = !coverColorEnabled,
                    showDivider = false,
                    onCheckedChange = { enabled ->
                        if (!coverColorEnabled) {
                            themePrefs.dynamicColorEnabled = enabled
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardMaterialPage() {
    WizardPageFrame(
        title = Strings.get("material_settings_title"),
        desc = Strings.get("wizard_step_material_desc")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MaterialSettingsContent()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WizardPlayerStylePage() {
    WizardPageFrame(
        title = Strings.get("settings_player_style"),
        desc = Strings.get("wizard_step_player_style_desc")
    ) {
        PlayerStyleContent(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun WizardPlaybackPage() {
    val context = LocalContext.current
    val themePrefs = remember { ThemePrefs.get(context) }
    val audioMixPrefs = remember { AudioMixPrefs.get(context) }

    val immersiveLyrics by themePrefs.immersiveLyricsState
    val mixWithOthers by audioMixPrefs.mixWithOthersState

    WizardPageFrame(
        title = Strings.get("wizard_step_playback_title"),
        desc = Strings.get("wizard_step_playback_desc")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            WizardSettingItem(
                icon = Icons.Outlined.Lyrics,
                tone = WizardIconTone.SECONDARY,
                title = Strings.get("settings_immersive_lyrics"),
                subtitle = Strings.get("settings_immersive_lyrics_desc"),
                checked = immersiveLyrics,
                onCheckedChange = { enabled ->
                    themePrefs.immersiveLyrics = enabled
                }
            )

            WizardSettingItem(
                icon = Icons.Outlined.VolumeUp,
                tone = WizardIconTone.SECONDARY,
                title = Strings.get("settings_mix_with_others"),
                subtitle = Strings.get("settings_mix_with_others_desc"),
                checked = mixWithOthers,
                showDivider = false,
                onCheckedChange = { enabled ->
                    audioMixPrefs.mixWithOthers = enabled
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WizardWelcomePage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(32.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = Strings.get("wizard_welcome_title"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = Strings.get("wizard_welcome_message"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WizardBottomBar(
    step: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val isLast = step == pageCount - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (step > 0) {
            Button(
                onClick = onPrev,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = Strings.get("wizard_prev"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Button(
            onClick = if (isLast) onFinish else onNext,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (isLast) Strings.get("wizard_start") else Strings.get("wizard_next"),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isLast) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
