package com.xiaowei.player.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.ArtistCoverPrefs
import com.xiaowei.player.data.EmbeddedCoverFetcher
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeDeckHeaderCard(
    title: String,
    subtitle: String,
    covers: List<String>,
    initialIndex: Int,
    onCoverChange: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var deckOffset by remember { mutableStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf(initialIndex) }
    var userTouched by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val count = covers.size
    val safeIndex = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val currentCovers by rememberUpdatedState(covers)

    var loadedCovers by remember(covers) {
        mutableStateOf<Set<String>>(
            covers.filterTo(HashSet()) { EmbeddedCoverFetcher.getCachedBytesSync(it) != null }
        )
    }

    LaunchedEffect(initialIndex) {
        if (!userTouched && initialIndex in 0..(count - 1).coerceAtLeast(0)) {
            index = initialIndex
        }
    }

    LaunchedEffect(covers, safeIndex) {
        if (count < 2) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (i in 0 until count) {
                if (!isActive) break
                val path = covers[(safeIndex + i) % count]
                try {
                    EmbeddedCoverFetcher.loadCoverBytes(path)
                } catch (_: Throwable) {
                }
                if (!loadedCovers.contains(path)) {
                    loadedCovers = loadedCovers + path
                }
            }
        }
    }

    val step = 1
    val below1Path = if (count >= 2) covers[(safeIndex + step + count) % count] else null
    val below2Path = if (count >= 3) covers[(safeIndex + 2 * step + count) % count] else null

    val density = LocalDensity.current
    val flingTriggerPx = with(density) { 800.dp.toPx() }
    var widthPx by remember { mutableStateOf(0) }

    val deckConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val deckMaxHeight = maxOf(240, minOf(420, (deckConfiguration.screenHeightDp * 0.45f).roundToInt()))
    val deckCardHeight = if (deckConfiguration.screenWidthDp >= 600) {
        (((deckConfiguration.screenWidthDp - 24) * 0.5f).roundToInt()).coerceIn(240, deckMaxHeight)
    } else {
        220
    }

    val dragState = rememberDraggableState { delta ->
        if (count >= 2 && widthPx > 0) {
            val bound = widthPx * 1.15f
            deckOffset = (deckOffset + delta).coerceIn(-bound, bound)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(deckCardHeight.dp)
            .onSizeChanged { widthPx = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = count >= 2,
                onDragStarted = {
                    settleJob?.cancel()
                    settling = false
                    userTouched = true
                },
                onDragStopped = { velocity ->
                    if (count < 2 || widthPx <= 0) {
                        deckOffset = 0f
                        return@draggable
                    }
                    val w = widthPx.toFloat()
                    val trigger = w * 0.12f
                    val start = deckOffset
                    settleJob = coroutineScope.launch {
                        settling = true
                        offset.snapTo(start)
                        if (velocity <= -flingTriggerPx || start <= -trigger) {
                            offset.animateTo(-w * 1.6f, tween(260))
                            val n = currentCovers.size
                            if (n >= 2) {
                                val cur = index.coerceIn(0, n - 1)
                                val next = (cur + 1) % n
                                if (next in currentCovers.indices) {
                                    index = next
                                    onCoverChange(currentCovers[next])
                                }
                            }
                        } else if (velocity >= flingTriggerPx || start >= trigger) {
                            offset.animateTo(w * 1.6f, tween(260))
                            val n = currentCovers.size
                            if (n >= 2) {
                                val cur = index.coerceIn(0, n - 1)
                                val next = (cur + 1) % n
                                if (next in currentCovers.indices) {
                                    index = next
                                    onCoverChange(currentCovers[next])
                                }
                            }
                        } else {
                            offset.animateTo(
                                0f,
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = 800f
                                )
                            )
                        }
                        deckOffset = 0f
                        settling = false
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (count < 2) {
                DeckCoverLayer(
                    modifier = Modifier.fillMaxSize(),
                    filePath = covers.firstOrNull()
                )
            } else {
                if (below2Path != null && loadedCovers.contains(below2Path)) {
                    DeckCoverLayer(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val w = size.width.coerceAtLeast(1f)
                                val o = if (settling) offset.value else deckOffset
                                val p = (abs(o) / w).coerceIn(0f, 1f)
                                val s = 0.88f + 0.06f * p
                                scaleX = s
                                scaleY = s
                            },
                        filePath = below2Path
                    )
                }
                if (below1Path != null && loadedCovers.contains(below1Path)) {
                    DeckCoverLayer(
                        modifier = Modifier.fillMaxSize(),
                        filePath = below1Path
                    )
                }
            }
        }
        if (count >= 2) {
            DeckCoverLayer(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val w = size.width.coerceAtLeast(1f)
                        val o = if (settling) offset.value else deckOffset
                        val p = (abs(o) / w).coerceIn(0f, 1f)
                        translationX = o
                        rotationZ = o / w * 10f
                        val s = 1f - 0.06f * p
                        scaleX = s
                        scaleY = s
                    },
                filePath = covers[safeIndex]
            )
        }
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

@Composable
private fun DeckCoverLayer(
    filePath: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) {
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
    }
}

@Composable
fun rememberUniqueCoverPaths(songs: List<Song>, preferred: String?): List<String> {
    val first = preferred ?: songs.firstOrNull()?.data
    var covers by remember(songs, preferred) {
        mutableStateOf(if (first != null) listOf(first) else emptyList())
    }
    LaunchedEffect(songs, preferred) {
        val head = preferred ?: songs.firstOrNull()?.data
        if (head == null || songs.size <= 1) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val result = ArrayList<String>()
            result.add(head)
            val seen = HashSet<String>()
            val headBytes = try {
                EmbeddedCoverFetcher.loadCoverBytes(head)
            } catch (_: Throwable) {
                null
            }
            if (headBytes != null) {
                seen.add(coverDigestKey(headBytes))
            }
            for (song in songs) {
                if (!isActive) return@withContext
                val path = song.data
                if (path == head || result.contains(path)) continue
                val bytes = try {
                    EmbeddedCoverFetcher.loadCoverBytes(path)
                } catch (_: Throwable) {
                    null
                }
                if (bytes == null) continue
                if (seen.add(coverDigestKey(bytes))) {
                    result.add(path)
                }
            }
            covers = result
        }
    }
    return covers
}

private fun coverDigestKey(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

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

    val context = LocalContext.current

    val coverPaths = remember(songs, artist) {
        val grouped = LinkedHashMap<String, String>()
        songs.forEach { song ->
            val key = song.album.trim().lowercase()
            if (!grouped.containsKey(key)) grouped[key] = song.data
        }
        val preferred = artist?.firstSongData
        if (preferred != null) {
            songs.firstOrNull { it.data == preferred }?.let { owner ->
                grouped[owner.album.trim().lowercase()] = preferred
            }
        }
        var result = grouped.values.toList()
        if (result.isEmpty()) {
            listOfNotNull(preferred, songs.firstOrNull()?.data)
        } else {
            if (preferred != null) {
                val at = result.indexOf(preferred)
                if (at > 0) result = listOf(preferred) + result.filterIndexed { i, _ -> i != at }
            }
            result
        }
    }

    val initialCoverIndex = remember(artistName, coverPaths) {
        val saved = ArtistCoverPrefs.get(context).getSelectedCover(artistName)
        coverPaths.indexOf(saved).takeIf { it >= 0 } ?: 0
    }

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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = Strings.get("back"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp).offset(x = 4.6.dp)
                        )
                    }
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (blurSupported) Modifier.hazeSource(hazeState) else Modifier),
                contentPadding = PaddingValues(top = topBarHeight, bottom = 80.dp)
            ) {

            item {
                SwipeDeckHeaderCard(
                    title = artistName,
                    subtitle = Strings.get("song_count", artist?.songCount ?: songs.size),
                    covers = coverPaths,
                    initialIndex = initialCoverIndex,
                    onCoverChange = { path ->
                        ArtistCoverPrefs.get(context).setSelectedCover(artistName, path)
                    }
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

            items(sortedSongs, key = { song -> song.id.toString() + "_" + song.data }) { song ->
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

    val context = LocalContext.current

    val uniqueCovers = rememberUniqueCoverPaths(
        songs = songs,
        preferred = album?.firstSongData ?: songs.firstOrNull()?.data
    )

    val initialCoverIndex = remember(albumId, uniqueCovers) {
        val saved = ArtistCoverPrefs.get(context).getSelectedCover(albumId.toString())
        uniqueCovers.indexOf(saved).takeIf { it >= 0 } ?: 0
    }

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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = Strings.get("back"),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp).offset(x = 4.6.dp)
                        )
                    }
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (blurSupported) Modifier.hazeSource(hazeState) else Modifier),
                contentPadding = PaddingValues(top = topBarHeight, bottom = 80.dp)
            ) {

            item {
                SwipeDeckHeaderCard(
                    title = album?.displayName ?: Strings.get("unknown_album"),
                    subtitle = album?.displayAlbumDashArtist ?: Strings.get("unknown_artist"),
                    covers = uniqueCovers,
                    initialIndex = initialCoverIndex,
                    onCoverChange = { path ->
                        ArtistCoverPrefs.get(context).setSelectedCover(albumId.toString(), path)
                    }
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

            items(sortedSongs, key = { song -> song.id.toString() + "_" + song.data }) { song ->
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
    )
}
