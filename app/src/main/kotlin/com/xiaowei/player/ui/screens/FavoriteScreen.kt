package com.xiaowei.player.ui.screens

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.Song
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.ui.components.PlayAllButton
import com.xiaowei.player.ui.components.SortButton
import com.xiaowei.player.ui.components.SortOption
import com.xiaowei.player.ui.components.sortSongs
import java.io.File

@Composable
fun FavoriteScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onRemoveFavorites: (List<Long>) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val context = LocalContext.current

    val favoriteVersion = library.favoriteVersion
    val favoriteIds = library.favoriteIds
    val favoriteSongs = remember(library.songs, favoriteIds, favoriteVersion) {
        library.songs.filter { favoriteIds.contains(it.id) }
            .sortedBy { it.title }
    }

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val sortedSongs = remember(favoriteSongs, sortOption) { sortSongs(favoriteSongs, sortOption) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) {
        exitSelection()
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
            IconButton(onClick = {
                if (selectionMode) exitSelection()
                else onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Strings.get("back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = Strings.get("favorite"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selectionMode) {
                IconButton(onClick = {
                    selectedIds = if (selectedIds.size == favoriteSongs.size) emptySet()
                    else favoriteSongs.map { it.id }.toSet()
                }) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = Strings.get("favorite_select_all"),
                        tint = if (selectedIds.size == favoriteSongs.size && favoriteSongs.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (favoriteSongs.isNotEmpty()) {
                IconButton(onClick = {
                    selectionMode = true
                    selectedIds = emptySet()
                }) {
                    Icon(
                        Icons.Filled.Checklist,
                        contentDescription = Strings.get("favorite_multi_select"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (favoriteSongs.isEmpty()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Strings.get("empty_songs"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = if (selectionMode) 120.dp else 80.dp)
            ) {
                if (!selectionMode) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayAllButton(
                                onPlayAll = { onPlayAll(sortedSongs) },
                                modifier = Modifier.weight(1f)
                            )
                            SortButton(
                                sortOption = sortOption,
                                onSortOptionChange = { sortOption = it }
                            )
                        }
                    }
                }
                items(sortedSongs, key = { it.id }) { song ->
                    val isSelected = selectedIds.contains(song.id)
                    FavoriteSongRow(
                        song = song,
                        isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                        isCurrent = playerState.currentSong?.id == song.id,
                        isSelected = isSelected,
                        showCheck = selectionMode,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (isSelected) selectedIds - song.id
                                else selectedIds + song.id
                            } else {
                                if (playerState.currentSong?.id == song.id) onOpenPlayer()
                                else onPlaySong(song, sortedSongs)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds = setOf(song.id)
                            } else if (!isSelected) {
                                selectedIds = selectedIds + song.id
                            }
                        }
                    )
                }
            }

            if (selectionMode && selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val toShare = sortedSongs.filter { selectedIds.contains(it.id) }
                            shareSongs(context, toShare)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Strings.get("favorite_share_selected", selectedIds.size),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = {
                            val toRemove = sortedSongs.filter { selectedIds.contains(it.id) }
                            if (toRemove.isEmpty()) return@Button
                            val count = toRemove.size
                            onRemoveFavorites(toRemove.map { it.id })
                            exitSelection()
                            Toast.makeText(
                                context,
                                Strings.get("favorite_deleted_toast", count),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = Strings.get("favorite_delete_selected", selectedIds.size),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSongRow(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    isSelected: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
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
            modifier = Modifier.size(48.dp),
            cornerRadius = 8,
            filePath = song.data
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = when {
                    isSelected -> FontWeight.SemiBold
                    isCurrent -> FontWeight.SemiBold
                    else -> FontWeight.Medium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.displayAlbumDashArtist,
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

private fun shareSongs(context: Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    val uris = ArrayList<Uri>()
    songs.forEach { song ->
        val uri = try {
            if (song.source == "mediastore") {
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id
                )
            } else {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    File(song.data)
                )
            }
        } catch (_: Exception) {
            null
        }
        if (uri != null) uris.add(uri)
    }
    if (uris.isEmpty()) {
        Toast.makeText(context, Strings.get("share_failed"), Toast.LENGTH_SHORT).show()
        return
    }
    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            clipData = ClipData.newRawUri("", uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, Strings.get("share")))
    } catch (_: Exception) {
        Toast.makeText(context, Strings.get("share_failed"), Toast.LENGTH_SHORT).show()
    }
}
