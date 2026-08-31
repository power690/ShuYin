package com.xiaowei.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.Song
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.SongRow
import kotlinx.coroutines.launch

private enum class LibraryTab(val labelKey: String) {
    Songs("library_songs"),
    Artists("library_artists"),
    Albums("library_albums")
}

@Composable
fun LibraryScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>, Int) -> Unit,
    onSearch: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},

    bottomPadding: Dp = 168.dp
) {
    val tabs = LibraryTab.entries

    val pagerState = rememberPagerState(initialPage = initialPage) { tabs.size }
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.get("tab_library"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            tabs.forEachIndexed { i, tab ->
                Tab(
                    selected = pagerState.currentPage == i,
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                    text = { Text(Strings.get(tab.labelKey), fontWeight = if (pagerState.currentPage == i) FontWeight.SemiBold else FontWeight.Normal) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),

            beyondViewportPageCount = 1
        ) { page ->
            when (tabs[page]) {
                LibraryTab.Songs -> SongsPane(
                    library = library,
                    playerState = playerState,
                    onPlaySong = onPlaySong,
                    onOpenPlayer = onOpenPlayer,
                    bottomPadding = bottomPadding
                )
                LibraryTab.Artists -> ArtistsPane(
                    library = library,
                    onOpenArtist = onOpenArtist,
                    bottomPadding = bottomPadding
                )
                LibraryTab.Albums -> AlbumsPane(
                    library = library,
                    onOpenAlbum = onOpenAlbum,
                    bottomPadding = bottomPadding
                )
            }
        }
    }
}

@Composable
private fun SongsPane(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenPlayer: () -> Unit,
    bottomPadding: Dp = 168.dp
) {
    val songs = library.filteredSongs
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (library.searchQuery.isBlank()) Strings.get("empty_songs")
                else Strings.get("search_no_result", library.searchQuery),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)
    ) {

        items(
            items = songs,
            key = { it.id },
            contentType = { "song_row" }
        ) { song ->
            SongRow(
                song = song,
                isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                isCurrent = playerState.currentSong?.id == song.id,
                onClick = {
                    if (playerState.currentSong?.id == song.id) onOpenPlayer()
                    else onPlaySong(song, songs)
                }
            )
        }
    }
}

@Composable
private fun ArtistsPane(
    library: LibraryState,
    onOpenArtist: (String) -> Unit,
    bottomPadding: Dp = 168.dp
) {
    val artists = library.filteredArtists
    if (artists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (library.searchQuery.isBlank()) Strings.get("empty_artists")
                else Strings.get("search_no_result", library.searchQuery),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding)
    ) {
        items(
            items = artists,
            key = { it.id },
            contentType = { "artist_tile" }
        ) { artist ->
            LibraryArtistTile(
                artist = artist,
                onClick = { onOpenArtist(artist.displayName) }
            )
        }
    }
}

@Composable
private fun AlbumsPane(
    library: LibraryState,
    onOpenAlbum: (Long) -> Unit,
    bottomPadding: Dp = 168.dp
) {
    val albums = library.filteredAlbums
    if (albums.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (library.searchQuery.isBlank()) Strings.get("empty_albums")
                else Strings.get("search_no_result", library.searchQuery),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = bottomPadding)
    ) {
        items(
            items = albums,
            key = { it.id },
            contentType = { "album_tile" }
        ) { album ->
            LibraryAlbumTile(
                album = album,
                onClick = { onOpenAlbum(album.id) }
            )
        }
    }
}

@Composable
fun LibraryArtistTile(
    artist: com.xiaowei.player.data.Artist,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "libraryArtistTileScale"
    )
    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        com.xiaowei.player.ui.components.AlbumCover(
            coverUri = artist.albumArtUri,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            cornerRadius = 16,
            filePath = artist.firstSongData
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun LibraryAlbumTile(
    album: com.xiaowei.player.data.Album,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "libraryAlbumTileScale"
    )
    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        com.xiaowei.player.ui.components.AlbumCover(
            coverUri = album.albumArtUri,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            cornerRadius = 16,
            filePath = album.firstSongData
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            fontWeight = FontWeight.Medium
        )
    }
}
