package com.xiaowei.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.data.Album
import com.xiaowei.player.data.Artist
import com.xiaowei.player.data.RecommendCard
import com.xiaowei.player.data.Song
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.ui.components.GradientScrim
import com.xiaowei.player.ui.components.PlayAllButton
import com.xiaowei.player.ui.components.SortButton
import com.xiaowei.player.ui.components.SortOption
import com.xiaowei.player.ui.components.SongRow
import com.xiaowei.player.ui.components.sortSongs
import com.xiaowei.player.R
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecommendScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRecommendCard: (RecommendCard) -> Unit,
    onOpenSearch: () -> Unit,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) },

    bottomPadding: Dp = 168.dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.get("app_name"),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {

            item {
                val searchInteraction = remember { MutableInteractionSource() }
                val searchPressed by searchInteraction.collectIsPressedAsState()
                val searchScale by animateFloatAsState(
                    targetValue = if (searchPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
                    label = "homeSearchScale"
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .graphicsLayer {
                            scaleX = searchScale
                            scaleY = searchScale
                        }
                        .clickable(
                            interactionSource = searchInteraction,
                            indication = ripple(),
                            onClick = onOpenSearch
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = Strings.get("search"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = Strings.get("search_hint"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = Strings.get("recommend_for_you"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val recommendCount = library.recommends.size
                val pagerState = rememberPagerState(pageCount = { recommendCount })
                if (recommendCount > 1) {
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }.collectLatest {
                            var elapsed = 0L
                            while (elapsed < 3000) {
                                if (pagerState.isScrollInProgress) elapsed = 0L
                                delay(200)
                                elapsed += 200
                            }
                            pagerState.animateScrollToPage((pagerState.currentPage + 1) % recommendCount)
                        }
                    }
                }
                Column {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        pageSpacing = 20.dp
                    ) { page ->
                        val card = library.recommends[page]
                        RecommendCardItem(
                            card = card,
                            onClick = { onOpenRecommendCard(card) },
                            fillWidth = true,
                            cardHeight = 170
                        )
                    }
                }
        }

        if (library.albums.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Strings.get("recommend_hot_albums"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(library.albums.take(10), key = { it.id }) { album ->
                        AlbumTile(
                            album = album,
                            onClick = { onOpenAlbum(album.id) }
                        )
                    }
                }
            }
        }

        if (library.artists.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = Strings.get("library_artists"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(library.artists.take(10), key = { it.id }) { artist ->
                        ArtistTile(
                            artist = artist,
                            onClick = { onOpenArtist(artist.displayName) }
                        )
                    }
                }
            }
        }
        }   
    }       
}           

@Composable
private fun RecommendCardItem(
    card: RecommendCard,
    onClick: () -> Unit,
    fillWidth: Boolean = false,
    cardHeight: Int = 140
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "recommendCardScale"
    )
    Card(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth()
                  else Modifier.width(220.dp))
            .height(cardHeight.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlbumCover(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 16,
                coverSizePx = 384,
                filePath = card.songs.firstOrNull()?.data
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
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AlbumTile(album: Album, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "albumTileScale"
    )
    Column(
        modifier = Modifier
            .width(128.dp)
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
        AlbumCover(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            cornerRadius = 16,
            coverSizePx = 384,
            filePath = album.firstSongData
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = album.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = album.displayAlbumDashArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistTile(artist: Artist, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "artistTileScale"
    )
    Column(
        modifier = Modifier
            .width(128.dp)
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
        AlbumCover(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            cornerRadius = 16,
            coverSizePx = 384,
            filePath = artist.firstSongData
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = artist.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RecommendDetailScreen(
    card: RecommendCard,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    val songs = card.songs

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val sortedSongs = remember(songs, sortOption) { sortSongs(songs, sortOption) }

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
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    RecommendCardItem(
                        card = card,
                        onClick = { onPlayAll(sortedSongs) },
                        fillWidth = true,
                        cardHeight = 220
                    )
                }
            }
            item { Spacer(Modifier.height(4.dp)) }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = Strings.get("recommend_songs"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
        }

        if (songs.isNotEmpty()) {
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

        if (songs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Strings.get("recommend_empty"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sortedSongs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                    isCurrent = playerState.currentSong?.id == song.id,
                    onClick = {
                        if (playerState.currentSong?.id == song.id) onOpenPlayer()
                        else onPlaySong(song, sortedSongs)
                    }
                )
            }
        }
        } 
    } 
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val historyDao = remember { com.xiaowei.player.data.db.AppDatabase.get(context).searchHistoryDao() }
    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun hideKeyboardAndClearFocus() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        history = withContext(Dispatchers.IO) { historyDao.getRecent() }
    }

    val hotKeywords = remember {
        Strings.get("search_hot_keywords").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addToHistory(keyword: String) {
        val k = keyword.trim()
        if (k.isEmpty()) return

        history = (listOf(k) + history.filter { it != k }).take(10)
        scope.launch {
            withContext(Dispatchers.IO) {
                historyDao.insert(
                    com.xiaowei.player.data.db.SearchHistoryEntity(
                        keyword = k,
                        addedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearHistory() {
        history = emptyList()
        scope.launch {
            withContext(Dispatchers.IO) { historyDao.clear() }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val results = remember(query, library.songs) {
        val q = query.trim()
        if (q.isEmpty()) emptyList()
        else library.songs.filter {
            it.title.contains(q, true) ||
            it.artist.contains(q, true) ||
            it.album.contains(q, true)
        }
    }

    var sortOption by remember { mutableStateOf(SortOption.DEFAULT) }
    val sortedResults = remember(results, sortOption) { sortSongs(results, sortOption) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(22.dp)
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = Strings.get("search_hint"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Strings.get("search_clear"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val q = query.trim()
        if (q.isEmpty()) {

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {

                if (history.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = Strings.get("search_history"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = Strings.get("search_clear_history"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable(onClick = { clearHistory() })
                                    .padding(4.dp)
                            )
                        }
                    }
                    item {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            history.forEach { keyword ->
                                SearchChip(
                                    text = keyword,
                                    onClick = {
                                        query = keyword
                                        addToHistory(keyword)

                                        hideKeyboardAndClearFocus()
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = Strings.get("search_hot"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        hotKeywords.forEach { keyword ->
                            SearchChip(
                                text = keyword,
                                onClick = {
                                    query = keyword
                                    addToHistory(keyword)

                                    hideKeyboardAndClearFocus()
                                }
                            )
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Strings.get("search_no_result", q),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {

            LaunchedEffect(q) {
                if (q.isNotEmpty()) addToHistory(q)
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Text(
                        text = Strings.get("search_result_count", results.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayAllButton(
                            onPlayAll = { onPlayAll(sortedResults) },
                            modifier = Modifier.weight(1f)
                        )
                        SortButton(
                            sortOption = sortOption,
                            onSortOptionChange = { sortOption = it }
                        )
                    }
                }
                items(sortedResults, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = playerState.isPlaying && playerState.currentSong?.id == song.id,
                        isCurrent = playerState.currentSong?.id == song.id,
                        onClick = {

                            hideKeyboardAndClearFocus()
                            if (playerState.currentSong?.id == song.id) onOpenPlayer()
                            else onPlaySong(song, sortedResults)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChip(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 900f),
        label = "searchChipScale"
    )
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
