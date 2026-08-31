package com.xiaowei.player.ui.screens

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaowei.player.LyricFacade
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.scanner.MusicScanner
import com.xiaowei.player.ui.components.AlbumCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

private data class SynthItem(
    val musicFile: MusicScanner.MusicFile,
    val filePath: String
)

private data class SynthProgress(val total: Int, val done: Int)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricSynthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<SynthItem>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var synthProgress by remember { mutableStateOf<SynthProgress?>(null) }

    fun doScan(path: String) {
        scanning = true
        items = emptyList()
        scope.launch {
            val result = withContext(Dispatchers.IO) { scanForSynth(path) }
            items = result
            scanning = false
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val realPath = uriToFilePath(uri)
            if (realPath != null) {
                selectedPath = realPath
                selectedIds = emptySet()
                doScan(realPath)
            } else {
                Toast.makeText(context, Strings.get("system_not_supported"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pickFolder() {
        try {
            openDocumentTreeLauncher.launch(null)
        } catch (_: Exception) {
            Toast.makeText(context, Strings.get("system_not_supported"), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Strings.get("lyric_synth_title"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                IconButton(onClick = {
                    selectedIds = if (selectedIds.size == items.size) emptySet()
                    else items.map { it.filePath }.toSet()
                }) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = Strings.get("lyric_synth_select_all"),
                        tint = if (selectedIds.size == items.size) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when {
            selectedPath == null -> PickerCenter(pickFolder = { pickFolder() })
            scanning -> ScanningCenter()
            items.isEmpty() -> EmptyCenter(pickFolder = { pickFolder() })
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
                ) {
                    items(items, key = { it.filePath }) { item ->
                        val isSelected = selectedIds.contains(item.filePath)
                        SynthRow(
                            item = item,
                            isSelected = isSelected,
                            showCheck = true,
                            onClick = {
                                selectedIds = if (isSelected) selectedIds - item.filePath
                                else selectedIds + item.filePath
                            },
                            onLongClick = {
                                if (!isSelected) selectedIds = selectedIds + item.filePath
                            }
                        )
                    }
                }

                val selectedCount by remember(selectedIds) {
                    derivedStateOf { selectedIds.size }
                }
                if (selectedCount > 0) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = 4.dp
                    ) {
                        Button(
                            onClick = {
                                val toProcess = items.filter { selectedIds.contains(it.filePath) }
                                if (toProcess.isEmpty()) return@Button
                                scope.launch {
                                    synthProgress = SynthProgress(total = toProcess.size, done = 0)
                                    val total = toProcess.size
                                    val doneCount = java.util.concurrent.atomic.AtomicInteger(0)
                                    val semaphore = kotlinx.coroutines.sync.Semaphore(5)
                                    kotlinx.coroutines.coroutineScope {
                                        toProcess.forEach { item ->
                                            launch {
                                                semaphore.withPermit {
                                                    withContext(Dispatchers.IO) {
                                                        try { LyricFacade.processFile(item.musicFile.file) } catch (_: Exception) {}
                                                    }
                                                    val d = doneCount.incrementAndGet()
                                                    synthProgress = SynthProgress(total = total, done = d)
                                                }
                                            }
                                        }
                                    }
                                    synthProgress = null
                                    Toast.makeText(
                                        context,
                                        Strings.get("lyric_synth_completed"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    selectedIds = emptySet()
                                    selectedPath?.let { p -> doScan(p) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = Strings.get("lyric_synth_start") + " ($selectedCount)",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    synthProgress?.let { prog ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Strings.get("lyric_synth_progress_title"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { if (prog.total > 0) prog.done.toFloat() / prog.total else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = Strings.get("lyric_synth_progress_done", prog.done, prog.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Strings.get("lyric_synth_progress_remaining", prog.total - prog.done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NativePickerButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = Strings.get("custom_path_pick_native"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PickerCenter(pickFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NativePickerButton(onClick = pickFolder)
            Spacer(Modifier.height(16.dp))
            Text(
                text = Strings.get("lyric_synth_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanningCenter() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = Strings.get("lyric_synth_scanning"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCenter(pickFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NativePickerButton(onClick = pickFolder)
            Spacer(Modifier.height(20.dp))
            Text(
                text = Strings.get("lyric_synth_empty"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SynthRow(
    item: SynthItem,
    isSelected: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val mf = item.musicFile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
            coverUri = null,
            modifier = Modifier.size(48.dp),
            cornerRadius = 8,
            filePath = mf.file.absolutePath
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mf.metaTitle ?: mf.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOfNotNull(mf.metaArtist, mf.metaAlbum).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showCheck) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

private fun scanForSynth(path: String): List<SynthItem> {
    val dir = File(path)
    if (!dir.isDirectory) return emptyList()
    val files = try {
        LyricFacade.scanDirectory(dir)
    } catch (_: Exception) {
        return emptyList()
    }
    val result = mutableListOf<SynthItem>()
    for (mf in files) {
        if (mf.ext != "flac" && mf.ext != "mp3") continue
        val type = try {
            LyricFacade.detectLyricType(mf.file)
        } catch (_: Exception) {
            null
        }
        if (type == null) continue
        if (type == com.xiaowei.player.lyric.LyricTypeDetector.LyricType.ALREADY_OK) continue
        result.add(SynthItem(mf, mf.file.absolutePath))
    }
    return result
}

private fun uriToFilePath(uri: Uri): String? {
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    val split = docId.split(":")
    if (split.size < 2) return null
    val type = split[0]
    val relativePath = split[1]
    return when (type) {
        "primary", "home" -> {
            val decoded = try {
                java.net.URLDecoder.decode(relativePath, "UTF-8")
            } catch (_: Exception) {
                relativePath
            }
            "${Environment.getExternalStorageDirectory().absolutePath}/$decoded"
        }
        else -> null
    }
}
