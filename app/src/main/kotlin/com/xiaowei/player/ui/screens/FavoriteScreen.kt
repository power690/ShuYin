package com.xiaowei.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.Song
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.SongRow
import com.xiaowei.player.R
import com.xiaowei.player.i18n.Strings

@Composable
fun FavoriteScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {

    val favoriteVersion = library.favoriteVersion
    val favoriteIds = library.favoriteIds
    val favoriteSongs = remember(library.songs, favoriteIds, favoriteVersion) {
        library.songs.filter { favoriteIds.contains(it.id) }
            .sortedBy { it.title }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
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
                text = Strings.get("favorite"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (favoriteSongs.isEmpty()) {

            androidx.compose.foundation.layout.Box(
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(favoriteSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                        isCurrent = playerState.currentSong?.id == song.id,
                        onClick = {
                            if (playerState.currentSong?.id == song.id) onOpenPlayer()
                            else onPlaySong(song, favoriteSongs)
                        }
                    )
                }
            }
        }
    }
}
