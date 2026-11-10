package com.xiaowei.player.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.ArtistCoverPrefs
import com.xiaowei.player.data.LibraryGridPrefs
import com.xiaowei.player.data.Song
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.BlurTopBarLayout
import com.xiaowei.player.ui.components.ColumnsButton
import com.xiaowei.player.ui.components.GridColumnsOption
import com.xiaowei.player.ui.components.SortButton
import com.xiaowei.player.ui.components.SortOption
import com.xiaowei.player.ui.components.SongRow
import com.xiaowei.player.ui.components.blurTopBar
import com.xiaowei.player.ui.components.sortSongs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private enum class LibraryTab(val labelKey: String) {
    Songs("library_songs"),
    Network("library_network"),
    Artists("library_artists"),
    Albums("library_albums")
}

@Composable
fun LibraryScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddSong: (Song) -> Unit,
    onSearch: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},

    bottomPadding: Dp = 168.dp
) {
    val tabs = if (library.webDavActive) {
        LibraryTab.entries
    } else {
        LibraryTab.entries.filter { it != LibraryTab.Network }
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, tabs.lastIndex)
    ) { tabs.size }
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }
    val scope = rememberCoroutineScope()

    val hazeState = rememberHazeState()
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val gridPrefs = LibraryGridPrefs.get(LocalContext.current)
    val gridColumnCount = gridPrefs.gridColumnsState.value
    val songsListState = rememberLazyListState()
    val artistsGridState = rememberLazyGridState()
    val albumsGridState = rememberLazyGridState()
    val networkListState = rememberLazyListState()
    val titleScrolled by remember {
        derivedStateOf {
            if (!blurSupported) false
            else when (tabs[pagerState.currentPage]) {
                LibraryTab.Songs -> songsListState.canScrollBackward
                LibraryTab.Artists -> artistsGridState.canScrollBackward
                LibraryTab.Albums -> albumsGridState.canScrollBackward
                LibraryTab.Network -> networkListState.canScrollBackward
            }
        }
    }

    BlurTopBarLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .blurTopBar(hazeState, titleScrolled)
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
                    containerColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = { Text(Strings.get(tab.labelKey), fontWeight = if (pagerState.currentPage == i) FontWeight.SemiBold else FontWeight.Normal) }
                        )
                    }
                }
            }
        },
        content = { topBarHeight ->
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
                    onAddSong = onAddSong,
                    onOpenPlayer = onOpenPlayer,
                    hazeState = hazeState,
                    blurEnabled = blurSupported,
                    listState = songsListState,
                    topPadding = topBarHeight + 4.dp,
                    bottomPadding = bottomPadding
                )
                LibraryTab.Artists -> ArtistsPane(
                    library = library,
                    onOpenArtist = onOpenArtist,
                    hazeState = hazeState,
                    blurEnabled = blurSupported,
                    gridState = artistsGridState,
                    columnCount = gridColumnCount,
                    onColumnCountChange = { gridPrefs.gridColumns = it },
                    topPadding = topBarHeight + 4.dp,
                    bottomPadding = bottomPadding
                )
                LibraryTab.Albums -> AlbumsPane(
                    library = library,
                    onOpenAlbum = onOpenAlbum,
                    hazeState = hazeState,
                    blurEnabled = blurSupported,
                    gridState = albumsGridState,
                    columnCount = gridColumnCount,
                    onColumnCountChange = { gridPrefs.gridColumns = it },
                    topPadding = topBarHeight + 4.dp,
                    bottomPadding = bottomPadding
                )
                LibraryTab.Network -> SongsPane(
                    library = library,
                    playerState = playerState,
                    onPlaySong = onPlaySong,
                    onAddSong = onAddSong,
                    onOpenPlayer = onOpenPlayer,
                    hazeState = hazeState,
                    blurEnabled = blurSupported,
                    listState = networkListState,
                    topPadding = topBarHeight + 4.dp,
                    bottomPadding = bottomPadding,
                    network = true
                )
            }
            }
        }
    )
}

@Composable
private fun SongsPane(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onAddSong: (Song) -> Unit,
    onOpenPlayer: () -> Unit,
    hazeState: HazeState,
    blurEnabled: Boolean,
    listState: LazyListState,
    topPadding: Dp,
    bottomPadding: Dp = 168.dp,
    network: Boolean = false
) {
    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val songs = if (network) library.filteredWebDavSongs else library.filteredSongs
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (library.searchQuery.isBlank()) {
                    if (network) Strings.get("empty_network_songs") else Strings.get("empty_songs")
                } else Strings.get("search_no_result", library.searchQuery),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val sortedSongs = remember(songs, sortOption) { sortSongs(songs, sortOption) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(if (blurEnabled) Modifier.hazeSource(hazeState) else Modifier),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortButton(
                    sortOption = sortOption,
                    onSortOptionChange = { sortOption = it }
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Strings.get("library_song_count", sortedSongs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
        items(
            items = sortedSongs,
            key = { song -> song.id.toString() + "_" + song.data },
            contentType = { "song_row" }
        ) { song ->
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
}

@Composable
private fun ArtistsPane(
    library: LibraryState,
    onOpenArtist: (String) -> Unit,
    hazeState: HazeState,
    blurEnabled: Boolean,
    gridState: LazyGridState,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    topPadding: Dp,
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
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize()
            .then(if (blurEnabled) Modifier.hazeSource(hazeState) else Modifier),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = bottomPadding)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GridHeaderRow(
                count = artists.size,
                countLabelKey = "grid_artist_count",
                icon = Icons.Filled.Person,
                columnCount = columnCount,
                onColumnCountChange = onColumnCountChange
            )
        }
        items(
            items = artists,
            key = { artist -> artist.id.toString() + "_" + artist.displayName },
            contentType = { "artist_tile" }
        ) { artist ->
            val context = LocalContext.current
            val selectionVersion = ArtistCoverPrefs.get(context).selectionVersionState.value
            val tileCoverPath = remember(artist, library, selectionVersion) {
                val saved = ArtistCoverPrefs.get(context).getSelectedCover(artist.displayName)
                val belongs = saved != null &&
                    library.artistSongMap[artist.displayName]?.any { it.data == saved } == true
                if (belongs) saved else artist.firstSongData
            }
            LibraryArtistTile(
                artist = artist,
                coverPath = tileCoverPath,
                onClick = { onOpenArtist(artist.displayName) }
            )
        }
    }
}

@Composable
private fun AlbumsPane(
    library: LibraryState,
    onOpenAlbum: (Long) -> Unit,
    hazeState: HazeState,
    blurEnabled: Boolean,
    gridState: LazyGridState,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    topPadding: Dp,
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
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize()
            .then(if (blurEnabled) Modifier.hazeSource(hazeState) else Modifier),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding, bottom = bottomPadding)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GridHeaderRow(
                count = albums.size,
                countLabelKey = "grid_album_count",
                icon = Icons.Filled.Album,
                columnCount = columnCount,
                onColumnCountChange = onColumnCountChange
            )
        }
        items(
            items = albums,
            key = { album -> album.id.toString() + "_" + album.displayName },
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
private fun GridHeaderRow(
    count: Int,
    countLabelKey: String,
    icon: ImageVector,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit
) {
    val columns = GridColumnsOption.entries.firstOrNull { it.count == columnCount } ?: GridColumnsOption.TWO
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColumnsButton(
            columns = columns,
            onColumnsChange = { onColumnCountChange(it.count) }
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = Strings.get(countLabelKey, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun LibraryArtistTile(
    artist: com.xiaowei.player.data.Artist,
    coverPath: String? = null,
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
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        com.xiaowei.player.ui.components.AlbumCover(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16,
            coverSizePx = 384,
            filePath = coverPath ?: artist.firstSongData
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
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        com.xiaowei.player.ui.components.AlbumCover(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16,
            coverSizePx = 384,
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
