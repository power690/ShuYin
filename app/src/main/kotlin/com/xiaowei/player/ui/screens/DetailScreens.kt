package com.xiaowei.player.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.Song
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.ui.components.BlurTopBarLayout
import com.xiaowei.player.ui.components.GradientScrim
import com.xiaowei.player.ui.components.SortButton
import com.xiaowei.player.ui.components.SortOption
import com.xiaowei.player.ui.components.SongRow
import com.xiaowei.player.ui.components.blurTopBar
import com.xiaowei.player.ui.components.sortSongs
import com.xiaowei.player.R
import com.xiaowei.player.i18n.Strings
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
private fun DetailHeaderCard(
    title: String,
    subtitle: String,
    filePath: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumCover(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 16,
                coverSizePx = 384,
                filePath = filePath
            )
            GradientScrim(
                modifier = Modifier.fillMaxSize(),
                colors = listOf(
                    Color.Black.copy(alpha = 0.0f),
                    Color.Black.copy(alpha = 0.7f)
                )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistName: String,
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddSong: (Song) -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val artist = library.artists.firstOrNull { it.displayName == artistName }

    val songs = library.artistSongMap[artistName]
        ?.sortedWith(compareBy({ it.album }, { it.track }))
        ?: emptyList()

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val sortedSongs = remember(songs, sortOption) { sortSongs(songs, sortOption) }

    val hazeState = rememberHazeState()
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()
    val titleScrolled by remember {
        derivedStateOf { blurSupported && listState.canScrollBackward }
    }

    BlurTopBarLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .blurTopBar(hazeState, titleScrolled)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get("back"),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        content = { topBarHeight ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurSupported) Modifier.hazeSource(hazeState) else Modifier),
                contentPadding = PaddingValues(top = topBarHeight, bottom = 80.dp)
            ) {

            item {
                DetailHeaderCard(
                    title = artistName,
                    subtitle = Strings.get("song_count", artist?.songCount ?: songs.size),
                    filePath = artist?.firstSongData ?: songs.firstOrNull()?.data
                )
            }

            item {
                Text(
                    text = Strings.get("all_songs"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (songs.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SortButton(
                            sortOption = sortOption,
                            onSortOptionChange = { sortOption = it }
                        )
                    }
                }
            }

            items(sortedSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                    isCurrent = playerState.currentSong?.id == song.id,
                    onClick = {
                        if (playerState.currentSong?.id == song.id) onOpenPlayer()
                        else onPlaySong(song, sortedSongs)
                    },
                    onAdd = { onAddSong(song) }
                )
            }
            }
        }
    )
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddSong: (Song) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val album = library.albums.firstOrNull { it.id == albumId }
    val songs = library.songs.filter { it.albumId == albumId }
        .sortedWith(compareBy({ it.track }, { it.title }))

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val sortedSongs = remember(songs, sortOption) { sortSongs(songs, sortOption) }

    val hazeState = rememberHazeState()
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()
    val titleScrolled by remember {
        derivedStateOf { blurSupported && listState.canScrollBackward }
    }

    BlurTopBarLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .blurTopBar(hazeState, titleScrolled)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get("back"),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = album?.displayName ?: Strings.get("album"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        content = { topBarHeight ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurSupported) Modifier.hazeSource(hazeState) else Modifier),
                contentPadding = PaddingValues(top = topBarHeight, bottom = 80.dp)
            ) {

            item {
                DetailHeaderCard(
                    title = album?.displayName ?: Strings.get("unknown_album"),
                    subtitle = album?.displayAlbumDashArtist ?: Strings.get("unknown_artist"),
                    filePath = album?.firstSongData ?: songs.firstOrNull()?.data
                )
            }

            item {
                Text(
                    text = Strings.get("all_songs"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            if (songs.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SortButton(
                            sortOption = sortOption,
                            onSortOptionChange = { sortOption = it }
                        )
                    }
                }
            }

            items(sortedSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                    isCurrent = playerState.currentSong?.id == song.id,
                    onClick = {
                        if (playerState.currentSong?.id == song.id) onOpenPlayer()
                        else onPlaySong(song, sortedSongs)
                    },
                    onAdd = { onAddSong(song) }
                )
            }
            }
        }
    )
}
