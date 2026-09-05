package com.xiaowei.player.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.xiaowei.player.BuildConfig
import com.xiaowei.player.data.AudioMixPrefs
import com.xiaowei.player.data.CustomPathPrefs
import com.xiaowei.player.data.LocalePrefs
import com.xiaowei.player.data.ThemePrefs
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.components.M3ExpressiveSwitch
import com.xiaowei.player.ui.theme.PRESET_THEME_COLORS

private enum class SettingIconTone {
    PRIMARY, SECONDARY, TERTIARY, ERROR, NEUTRAL
}

@Composable
private fun SettingIconTone.toneContainerColor(): Color = when (this) {
    SettingIconTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    SettingIconTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    SettingIconTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    SettingIconTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    SettingIconTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun SettingIconTone.toneContentColor(): Color = when (this) {
    SettingIconTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    SettingIconTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    SettingIconTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    SettingIconTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    SettingIconTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun SettingIconBadge(
    icon: ImageVector,
    tone: SettingIconTone
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
private fun SettingsCategoryHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp)
    )
}

@Composable
private fun ExpressiveSettingItem(
    icon: ImageVector,
    tone: SettingIconTone,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueText: String? = null,
    checked: Boolean? = null,
    switchEnabled: Boolean = true,
    itemEnabled: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "settingItemScale"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (itemEnabled) 1f else 0.4f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onClick != null && itemEnabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingIconBadge(icon = icon, tone = tone)
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
            } else if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ThemeColorPickerItem(
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
            SettingIconBadge(icon = Icons.Outlined.Palette, tone = SettingIconTone.PRIMARY)
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
                ColorBall(
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
fun SettingsScreen(
    onBack: () -> Unit,
    onCustomPathConfirm: (String) -> Unit = {},
    onToggleMixWithOthers: (Boolean) -> Unit = {},
    onOpenMaterialSettings: () -> Unit = {},
    onOpenPlayerStyle: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePrefs = remember { ThemePrefs.get(context) }
    val localePrefs = remember { LocalePrefs.get(context) }
    val customPathPrefs = remember { CustomPathPrefs.get(context) }
    val audioMixPrefs = remember { AudioMixPrefs.get(context) }

    val dynamicColorEnabled = themePrefs.dynamicColorEnabledState.value
    val coverColorEnabled = themePrefs.coverColorEnabledState.value
    val themeColorIndex = themePrefs.themeColorIndexState.value
    val currentLangCode = localePrefs.languageCodeState.value

    var showLanguagePicker by remember { mutableStateOf(false) }

    var showCustomPathDialog by remember { mutableStateOf(false) }

    var customPathInput by remember { mutableStateOf("") }

    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {

            showCustomPathDialog = true
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {

            val realPath = uriToFilePath(uri, context)
            if (realPath != null) {
                customPathInput = realPath
            } else {

                Toast.makeText(
                    context,
                    Strings.get("system_not_supported"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun onNativeFilePickerClick() {
        try {
            openDocumentTreeLauncher.launch(null)
        } catch (_: Exception) {

            Toast.makeText(
                context,
                Strings.get("system_not_supported"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onCustomPathClick() {
        if (hasManageStoragePermission()) {

            customPathInput = customPathPrefs.path
            showCustomPathDialog = true
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {

                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            } else {

                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Strings.get("settings_title"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 96.dp)
        ) {

            SettingsCategoryHeader(
                text = Strings.get("settings_category_theme"),
                modifier = Modifier.padding(top = 8.dp)
            )

            ThemeColorPickerItem(
                enabled = !dynamicColorEnabled && !coverColorEnabled,
                selectedIndex = themeColorIndex,
                onSelect = { index ->
                    if (!dynamicColorEnabled && !coverColorEnabled) {
                        themePrefs.themeColorIndex = index
                    }
                }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Album,
                tone = SettingIconTone.TERTIARY,
                title = Strings.get("settings_cover_color"),
                subtitle = Strings.get("settings_cover_color_desc"),
                checked = coverColorEnabled,
                switchEnabled = !dynamicColorEnabled,
                itemEnabled = !dynamicColorEnabled,
                showDivider = dynamicColorSupported,
                onCheckedChange = { newValue ->
                    if (!dynamicColorEnabled) {
                        themePrefs.coverColorEnabled = newValue
                    }
                }
            )

            if (dynamicColorSupported) {
                ExpressiveSettingItem(
                    icon = Icons.Outlined.ColorLens,
                    tone = SettingIconTone.PRIMARY,
                    title = Strings.get("settings_dynamic_color"),
                    subtitle = Strings.get("settings_dynamic_color_desc"),
                    checked = dynamicColorEnabled,
                    switchEnabled = !coverColorEnabled,
                    itemEnabled = !coverColorEnabled,
                    showDivider = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    onCheckedChange = { newValue ->
                        if (!coverColorEnabled) {
                            themePrefs.dynamicColorEnabled = newValue
                        }
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ExpressiveSettingItem(
                    icon = Icons.Outlined.Wallpaper,
                    tone = SettingIconTone.TERTIARY,
                    title = Strings.get("settings_material_settings"),
                    showDivider = false,
                    onClick = { onOpenMaterialSettings() }
                )
            }

            SettingsCategoryHeader(
                text = Strings.get("settings_category_general")
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Language,
                tone = SettingIconTone.SECONDARY,
                title = Strings.get("settings_language"),
                onClick = { showLanguagePicker = true }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.MusicNote,
                tone = SettingIconTone.PRIMARY,
                title = Strings.get("settings_player_style"),
                onClick = onOpenPlayerStyle
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Lyrics,
                tone = SettingIconTone.SECONDARY,
                title = Strings.get("settings_immersive_lyrics"),
                subtitle = Strings.get("settings_immersive_lyrics_desc"),
                checked = themePrefs.immersiveLyricsState.value,
                onCheckedChange = { newValue ->
                    themePrefs.immersiveLyrics = newValue
                }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Folder,
                tone = SettingIconTone.TERTIARY,
                title = Strings.get("settings_custom_path"),
                onClick = { onCustomPathClick() }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.VolumeUp,
                tone = SettingIconTone.SECONDARY,
                title = Strings.get("settings_mix_with_others"),
                subtitle = Strings.get("settings_mix_with_others_desc"),
                checked = audioMixPrefs.mixWithOthersState.value,
                showDivider = false,
                onCheckedChange = { newValue ->
                    audioMixPrefs.mixWithOthers = newValue
                    onToggleMixWithOthers(newValue)
                }
            )

            SettingsCategoryHeader(
                text = Strings.get("settings_category_about")
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Code,
                tone = SettingIconTone.PRIMARY,
                title = Strings.get("mine_developer"),
                valueText = Strings.get("mine_developer_name"),
                onClick = {
                    val devUrl = "https://github.com/power690"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(devUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(Strings.get("mine_developer"), devUrl))
                        Toast.makeText(context, devUrl, Toast.LENGTH_LONG).show()
                    }
                }
            )

            val qqGroupNumber = "767301251"
            ExpressiveSettingItem(
                icon = Icons.Outlined.Feedback,
                tone = SettingIconTone.ERROR,
                title = Strings.get("mine_help_feedback"),
                valueText = qqGroupNumber,
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(Strings.get("mine_qq_group"), qqGroupNumber))
                    Toast.makeText(context, Strings.get("mine_qq_copied", qqGroupNumber), Toast.LENGTH_SHORT).show()
                }
            )

            val projectUrl = "https://github.com/power690/ShuYin"
            ExpressiveSettingItem(
                icon = Icons.Outlined.OpenInNew,
                tone = SettingIconTone.TERTIARY,
                title = Strings.get("mine_project_url"),
                valueText = "GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(Strings.get("mine_project_url"), projectUrl))
                        Toast.makeText(context, Strings.get("mine_project_url") + ": " + projectUrl, Toast.LENGTH_LONG).show()
                    }
                }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Info,
                tone = SettingIconTone.SECONDARY,
                title = Strings.get("mine_version"),
                valueText = BuildConfig.VERSION_NAME,
                showDivider = false,
                onClick = {
                    com.xiaowei.player.ui.screens.UpdateCheckerState.requestManualCheck()
                }
            )
        }
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            currentLangCode = currentLangCode,
            onConfirm = { selectedCode ->
                localePrefs.languageCode = selectedCode
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showCustomPathDialog) {
        Dialog(
            onDismissRequest = {
                showCustomPathDialog = false
                customPathInput = ""
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = Strings.get("custom_path_dialog_title"),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = Strings.get("custom_path_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = Strings.get("custom_path_input_hint"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = androidx.compose.ui.unit.TextUnit(
                                when {
                                    customPathInput.length > 60 -> 11f
                                    customPathInput.length > 40 -> 12f
                                    customPathInput.length > 20 -> 13f
                                    else -> 15f
                                },
                                androidx.compose.ui.unit.TextUnitType.Sp
                            )
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNativeFilePickerClick() },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = Strings.get("custom_path_pick_native"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showCustomPathDialog = false
                                customPathInput = ""
                            }
                        ) {
                            Text(
                                text = Strings.get("custom_path_cancel"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val path = customPathInput.trim()
                                customPathPrefs.path = path
                                onCustomPathConfirm(path)
                                showCustomPathDialog = false
                                customPathInput = ""
                            }
                        ) {
                            Text(
                                text = Strings.get("custom_path_confirm"),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorBall(
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

private fun uriToFilePath(uri: Uri, context: android.content.Context): String? {
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    val split = docId.split(":", limit = 2)
    if (split.size < 2) return null

    val type = split[0]
    val relativePath = split[1]
    val decoded = try {
        java.net.URLDecoder.decode(relativePath, "UTF-8")
    } catch (_: Exception) {
        relativePath
    }

    return when (type) {
        "primary", "home" -> {
            "${Environment.getExternalStorageDirectory().absolutePath}/$decoded"
        }
        else -> {
            var baseDir: java.io.File? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val sm = context.getSystemService(android.content.Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                    for (vol in sm.storageVolumes) {
                        if (vol.uuid == type) {
                            baseDir = vol.directory
                            break
                        }
                    }
                } catch (_: Exception) {
                }
            }
            if (baseDir == null) {
                val fallback = java.io.File("/storage/$type")
                if (fallback.isDirectory) {
                    baseDir = fallback
                }
            }
            if (baseDir != null && baseDir.isDirectory) {
                java.io.File(baseDir, decoded).absolutePath
            } else {
                null
            }
        }
    }
}
