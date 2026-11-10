package com.xiaowei.player.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.xiaowei.player.data.CustomPathPrefs
import com.xiaowei.player.data.RecommendPriorityPrefs
import com.xiaowei.player.data.WebDavPrefs
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.ui.components.EdgeToEdgeSheetWindow

@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
    onOpenWebDav: () -> Unit = {},
    onCustomPathConfirm: (String) -> Unit = {},
    onPriorityChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val customPathPrefs = remember { CustomPathPrefs.get(context) }
    val webDavPrefs = remember { WebDavPrefs.get(context) }
    val priorityPrefs = remember { RecommendPriorityPrefs.get(context) }
    val webDavActive = webDavPrefs.activeIdState.value != null

    var showCustomPathDialog by remember { mutableStateOf(false) }
    var customPathInput by remember { mutableStateOf("") }
    var showPriorityPicker by remember { mutableStateOf(false) }

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

    DetailScaffold(
        title = Strings.get("settings_source"),
        onBack = onBack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ExpressiveSettingItem(
                icon = Icons.Outlined.Folder,
                tone = SettingIconTone.TERTIARY,
                title = Strings.get("settings_custom_path"),
                onClick = { onCustomPathClick() }
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Cloud,
                tone = SettingIconTone.SECONDARY,
                title = Strings.get("settings_webdav"),
                onClick = onOpenWebDav
            )

            ExpressiveSettingItem(
                icon = Icons.Outlined.Tune,
                tone = SettingIconTone.PRIMARY,
                title = Strings.get("source_priority"),
                showDivider = false,
                onClick = { showPriorityPicker = true }
            )
        }

        if (showPriorityPicker) {
            RecommendPriorityPickerSheet(
                webDavActive = webDavActive,
                currentPriority = priorityPrefs.priorityState.value,
                onConfirm = { selected ->
                    priorityPrefs.priority = selected
                    showPriorityPicker = false
                    onPriorityChanged()
                },
                onDismiss = { showPriorityPicker = false }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendPriorityPickerSheet(
    webDavActive: Boolean,
    currentPriority: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = buildList {
        add(RecommendPriorityPrefs.PRIORITY_LOCAL to "source_priority_local")
        if (webDavActive) {
            add(RecommendPriorityPrefs.PRIORITY_NETWORK to "source_priority_network")
            add(RecommendPriorityPrefs.PRIORITY_MIXED to "source_priority_mixed")
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) }
    ) {
        EdgeToEdgeSheetWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Text(
                text = Strings.get("source_priority"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.size(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(options) { option ->
                    PriorityRow(
                        label = Strings.get(option.second),
                        isSelected = option.first == currentPriority,
                        onClick = { onConfirm(option.first) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityRow(
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
