package com.xiaowei.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import com.xiaowei.player.data.LyricsParser
import com.xiaowei.player.data.Song
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.ui.components.formatDuration
import com.xiaowei.player.ui.shapes.MaterialStarShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import android.net.Uri
import com.xiaowei.player.R
import androidx.compose.ui.res.stringResource
import com.xiaowei.player.i18n.Strings
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    song: Song,
    playerState: MusicPlayerManager.PlayerState,
    playlist: List<Song>,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onPlayAtIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMinimize: () -> Unit,
    floatingLyricEnabled: Boolean = false,
    onToggleFloatingLyric: () -> Unit = {}
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePrefs = remember { com.xiaowei.player.data.ThemePrefs.get(context) }
    val immersiveLyrics = themePrefs.immersiveLyricsState.value
    val controlsVisible = !(immersiveLyrics && showLyrics)

    val orientationConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
    val windowContainerInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isLandscape =
        orientationConfiguration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                (windowContainerInfo.containerSize.width > 0 &&
                        windowContainerInfo.containerSize.width > windowContainerInfo.containerSize.height)

    val orientationView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val orientationActivity = orientationView.context as? android.app.Activity
        val resolver = context.contentResolver
        fun applyOrientation() {
            val locked = android.provider.Settings.System.getInt(
                resolver,
                android.provider.Settings.System.ACCELEROMETER_ROTATION,
                1
            ) == 0
            orientationActivity?.requestedOrientation =
                if (locked) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        applyOrientation()
        val rotationObserver = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                applyOrientation()
            }
        }
        resolver.registerContentObserver(
            android.provider.Settings.System.getUriFor(android.provider.Settings.System.ACCELEROMETER_ROTATION),
            false,
            rotationObserver
        )
        onDispose {
            resolver.unregisterContentObserver(rotationObserver)
            try {
                val win = orientationActivity?.window
                if (win != null) {
                    val controller = androidx.core.view.WindowCompat.getInsetsController(win, orientationView)
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    orientationView.post {
                        try {
                            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) { }
            orientationActivity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    LaunchedEffect(isLandscape) {
        try {
            val window = (orientationView.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, orientationView)
            if (isLandscape) {
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } catch (_: Exception) { }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    var lastPreloadedSongId by remember { mutableStateOf(-1L) }
    LaunchedEffect(song.id) {
        if (song.id != lastPreloadedSongId) {

            if (lastPreloadedSongId != -1L) {
                val prevSong = playlist.firstOrNull { it.id == lastPreloadedSongId }
                if (prevSong != null) {
                    com.xiaowei.player.data.EmbeddedCoverFetcher.evictOldCovers(
                        listOf(prevSong.data)
                    )
                }
            }

            val nextSong = playlist.getOrNull(playerState.currentIndex + 1)
            com.xiaowei.player.data.EmbeddedCoverFetcher.preloadPlayingCovers(
                currentFilePath = song.data,
                nextFilePath = nextSong?.data
            )
            lastPreloadedSongId = song.id
        }
    }

    val lyricsListState = rememberLazyListState()
    var lyricsCurrentIdx by remember { mutableStateOf(-1) }

    var lyricsInitialized by remember { mutableStateOf(false) }

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density * 0.97f,
        fontScale = currentDensity.fontScale
    )

    val onFavoriteClick: () -> Unit = {
        val willBeFavorite = !isFavorite
        onToggleFavorite()
        android.widget.Toast.makeText(
            context,
            if (willBeFavorite) Strings.get("favorite_added")
            else Strings.get("favorite_removed"),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        val physicalSquare = with(scaledDensity) {
            val maxPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val wm = context.getSystemService(android.view.WindowManager::class.java)
                val b = wm.maximumWindowMetrics.bounds
                maxOf(b.width(), b.height())
            } else {
                val dm = context.resources.displayMetrics
                maxOf(dm.widthPixels, dm.heightPixels)
            }
            maxPx.toDp()
        }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .requiredSize(physicalSquare)
                    .align(Alignment.Center)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
            MD3LandscapeContent(
                song = song,
                playerState = playerState,
                onCyclePlayMode = onCyclePlayMode,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onPrev = onPrev,
                onSeek = onSeek,
                onShowPlaylist = { showPlaylist = true },
                lyricsListState = lyricsListState,
                lyricsCurrentIdx = lyricsCurrentIdx,
                onLyricsCurrentIdxChange = { lyricsCurrentIdx = it },
                lyricsInitialized = lyricsInitialized,
                onLyricsInitialized = { lyricsInitialized = true }
            )
        } else {
        SharedTransitionLayout {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {

                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        fadeIn(tween(350, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(350, easing = FastOutSlowInEasing))
                    },
                    modifier = Modifier.weight(0.85f).fillMaxWidth(),
                    label = "playerMode"
                ) { isLyrics ->
                    Column(modifier = Modifier.fillMaxSize()) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLyrics) {

                                Card(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .sharedElement(
                                            rememberSharedContentState(key = "cover"),
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { showLyrics = !showLyrics },
                                    shape = RoundedCornerShape(6.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    PlayerCover(
                                        modifier = Modifier.fillMaxSize(),
                                        filePath = song.data
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = song.displayAlbumDashArtist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                }

                                IconButton(
                                    onClick = onFavoriteClick,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                        contentDescription = Strings.get("favorite"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                if (song.hasLyrics) {
                                    IconButton(
                                        onClick = onToggleFloatingLyric,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lyrics,
                                            contentDescription = Strings.get("floating_lyrics"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }

                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = Strings.get("collapse"),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (isLyrics) {

                            Spacer(Modifier.height(16.dp))
                            LyricsView(
                                song = song,
                                positionMs = playerState.positionMs,
                                positionUpdateNanos = playerState.positionUpdateNanos,
                                isPlaying = playerState.isPlaying,
                                isBuffering = playerState.isBuffering,
                                onToggle = { showLyrics = !showLyrics },
                                onSeek = onSeek,
                                listState = lyricsListState,
                                currentIdx = lyricsCurrentIdx,
                                onCurrentIdxChange = { lyricsCurrentIdx = it },
                                initialized = lyricsInitialized,
                                onInitialized = { lyricsInitialized = true },
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        } else {

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {

                                val coverSize = (if (maxWidth < maxHeight) maxWidth else maxHeight * 0.82f).coerceAtMost(480.dp)
                                Card(
                                    modifier = Modifier
                                        .size(coverSize)
                                        .sharedElement(
                                            rememberSharedContentState(key = "cover"),
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDrag = 0f },
                                                onDragEnd = {
                                                    if (totalDrag > 80f) showLyrics = true
                                                }
                                            ) { change, dragAmount ->
                                                totalDrag += dragAmount
                                                change.consume()
                                            }
                                        }
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { showLyrics = !showLyrics },
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    PlayerCover(
                                        modifier = Modifier.fillMaxSize(),
                                        filePath = song.data
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = song.displayAlbumDashArtist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                }

                                IconButton(
                                    onClick = onFavoriteClick,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Favorite
                                        else Icons.Outlined.FavoriteBorder,
                                        contentDescription = Strings.get("favorite"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(350, easing = FastOutSlowInEasing)) +
                            expandVertically(tween(350, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(350, easing = FastOutSlowInEasing)) +
                           shrinkVertically(tween(350, easing = FastOutSlowInEasing))
                ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    val draggingValue = remember { mutableStateOf<Float?>(null) }
                    val progress = draggingValue.value ?: if (playerState.durationMs > 0)
                        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                    else 0f
                    Slider(
                        value = progress,
                        onValueChange = { draggingValue.value = it },
                        onValueChangeFinished = {
                            draggingValue.value?.let { v ->
                                onSeek((v * playerState.durationMs).toLong())
                            }
                            draggingValue.value = null
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playerState.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(playerState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCyclePlayMode,
                        modifier = Modifier.size(44.dp)
                    ) {
                        val (icon, labelKey, tint) = when (playerState.playMode) {
                            MusicPlayerManager.PlayMode.SEQUENCE -> Triple(
                                Icons.Filled.Repeat, "play_mode_sequence",
                                MaterialTheme.colorScheme.onBackground
                            )
                            MusicPlayerManager.PlayMode.SHUFFLE -> Triple(
                                Icons.Filled.Shuffle, "play_mode_shuffle",
                                MaterialTheme.colorScheme.primary
                            )
                            MusicPlayerManager.PlayMode.REPEAT_ONE -> Triple(
                                Icons.Filled.RepeatOne, "play_mode_repeat_one",
                                MaterialTheme.colorScheme.primary
                            )
                        }
                        val label = Strings.get(labelKey)
                        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious, Strings.get("previous"),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))

                    val isPlaying = playerState.isPlaying

                    val rotation by animateFloatAsState(
                        targetValue = if (isPlaying) 0f else 360f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        ),
                        label = "PlayButtonRotation"
                    )

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .rotate(rotation)
                            .clip(if (isPlaying) MaterialStarShape else CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null  
                            ) { onTogglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(initialScale = 0.9f) togetherWith
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.9f)
                            },
                            label = "PlayButtonIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) Strings.get("pause") else Strings.get("play"),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, Strings.get("next"),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { showPlaylist = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert, Strings.get("playlist"),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                }
                }

                Spacer(Modifier.weight(0.05f))
                Spacer(Modifier.height(8.dp))
            }
        }
        }

        if (showPlaylist) {
            BackHandler { showPlaylist = false }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showPlaylist,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showPlaylist = false }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showPlaylist,
            enter = androidx.compose.animation.slideInVertically(
                animationSpec = androidx.compose.animation.core.tween(280),
                initialOffsetY = { it }
            ),
            exit = androidx.compose.animation.slideOutVertically(
                animationSpec = androidx.compose.animation.core.tween(280),
                targetOffsetY = { it }
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val sheetWidthFraction = if (isLandscape) 0.6f else 1f
            val sheetHeightFraction = if (isLandscape) 0.68f else 0.45f
            val sheetShape = if (isLandscape) RoundedCornerShape(24.dp)
            else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth(sheetWidthFraction)
                    .fillMaxHeight(sheetHeightFraction)
                    .clip(sheetShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        sheetShape
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {  }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    com.xiaowei.player.ui.PlaylistSheetContent(
                        playlist = playlist,
                        currentIndex = playerState.currentIndex,
                        onPlayAtIndex = { idx ->
                            onPlayAtIndex(idx)
                            showPlaylist = false
                        },
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue
                    )
                }
            }
        }
    }
    } 
}

@Composable
private fun LyricsView(
    song: Song,
    positionMs: Long,
    positionUpdateNanos: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    listState: LazyListState,
    currentIdx: Int,
    onCurrentIdxChange: (Int) -> Unit,
    initialized: Boolean,
    onInitialized: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(song.id, song.lyrics) {
        LyricsParser.parse(song.lyrics)
    }
    val scope = rememberCoroutineScope()
    val currentIdxState = rememberUpdatedState(currentIdx)
    val initializedState = rememberUpdatedState(initialized)
    val onCurrentIdxChangeState = rememberUpdatedState(onCurrentIdxChange)
    val onInitializedState = rememberUpdatedState(onInitialized)

    val livePositionMs by produceState(
        initialValue = positionMs,
        positionMs,
        positionUpdateNanos,
        isPlaying,
        isBuffering
    ) {
        if (!isPlaying || isBuffering || positionUpdateNanos == 0L) {
            value = positionMs
            return@produceState
        }
        value = positionMs
        while (true) {
            withFrameNanos {
                val elapsedMs = (System.nanoTime() - positionUpdateNanos) / 1_000_000L
                if (elapsedMs in 0..2000L) {
                    value = positionMs + elapsedMs
                }
            }
        }
    }

    LaunchedEffect(lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        snapshotFlow { LyricsParser.findCurrentLine(lines, livePositionMs) }
            .distinctUntilChanged()
            .collect { idx ->
                if (idx >= 0 && idx != currentIdxState.value) {
                    val previousIdx = currentIdxState.value
                    val wasInitialized = initializedState.value
                    onCurrentIdxChangeState.value(idx)
                    scope.launch {
                        if (!wasInitialized) {
                            listState.scrollToItem(idx)
                            onInitializedState.value()
                        } else if (idx - previousIdx == 1) {
                            listState.animateScrollToItem(idx)
                        } else {
                            listState.scrollToItem(idx)
                        }
                    }
                }
            }
    }

    if (lines.isEmpty()) {
        Box(
            modifier = modifier
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag < -80f) onToggle()
                        }
                    ) { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onToggle() }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = Strings.get("no_lyrics"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Strings.get("no_lyrics_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -80f) onToggle()
                    }
                ) { change, dragAmount ->
                    totalDrag += dragAmount
                    change.consume()
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggle() }
    ) {
        val viewportHeightDp = maxHeight
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = size.minDimension * 0.16f
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            (fade / size.height) to Color.Black,
                            (1f - fade / size.height) to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = viewportHeightDp / 2
            )
        ) {
            items(lines.size, key = { it }) { i ->
                val line = lines[i]
                val isCurrent = i == currentIdx
                val isBlank = line.text.isBlank()
                val primaryColor = MaterialTheme.colorScheme.primary
                val dimColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                val sungColor = primaryColor
                val unsungColor = primaryColor.copy(alpha = 0.35f)

                val lineLivePositionMs = if (isCurrent && line.isWordByWord && isPlaying) livePositionMs else positionMs
                val useKaraoke = isCurrent && line.isWordByWord && !isBlank
                val targetSize = if (isCurrent) 21f else 16f
                val animatedSize by animateFloatAsState(
                    targetValue = targetSize,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "lyricLineSize"
                )
                val animatedDimColor by animateColorAsState(
                    targetValue = if (isCurrent) primaryColor else dimColor,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "lyricLineDimColor"
                )
                val animatedUnsungColor by animateColorAsState(
                    targetValue = if (isCurrent) unsungColor else dimColor,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "lyricLineUnsungColor"
                )
                val nextLineTimeMs = if (i + 1 < lines.size) lines[i + 1].timeMs else (line.timeMs + 4000L)
                val displayText = if (isBlank) "♪" else line.text

                KaraokeLineAndroidView(
                    text = displayText,
                    words = if (useKaraoke) line.words else null,
                    positionMs = lineLivePositionMs,
                    nextLineTimeMs = nextLineTimeMs,
                    sungColor = sungColor,
                    unsungColor = animatedUnsungColor,
                    dimColor = animatedDimColor,
                    fontSize = targetSize,
                    maxFontSize = 21f,
                    scale = animatedSize / targetSize,
                    fontWeightValue = if (isCurrent) 700f else 400f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSeek(line.timeMs.coerceAtLeast(0L)) }
                        .padding(
                            vertical = if (isBlank) 2.dp else 8.dp,
                            horizontal = 24.dp
                        )
                )
            }
        }
    }
}

@Composable
private fun KaraokeLineAndroidView(
    text: String,
    words: List<com.xiaowei.player.data.LyricWord>?,
    positionMs: Long,
    nextLineTimeMs: Long,
    sungColor: Color,
    unsungColor: Color,
    dimColor: Color,
    fontSize: Float,
    maxFontSize: Float,
    fontWeightValue: Float,
    scale: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val textMeasurer = rememberTextMeasurer()
        val baseStyle = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight(fontWeightValue.toInt()),
            textAlign = TextAlign.Center
        )
        val annotatedText = remember(text) { AnnotatedString(text) }
        val layoutResult = remember(text, fontSize, fontWeightValue, maxWidthPx) {
            textMeasurer.measure(
                text = annotatedText,
                style = baseStyle,
                overflow = TextOverflow.Visible,
                softWrap = true,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
        }
        val frameStyle = TextStyle(
            fontSize = maxFontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val frameResult = remember(text, maxFontSize, maxWidthPx) {
            textMeasurer.measure(
                text = annotatedText,
                style = frameStyle,
                overflow = TextOverflow.Visible,
                softWrap = true,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
        }
        val layoutWidthPx = layoutResult.size.width
        val layoutHeightPx = layoutResult.size.height.coerceAtLeast(1)
        val frameHeightPx = frameResult.size.height.coerceAtLeast(layoutHeightPx)
        val frameHeightDp = with(density) { frameHeightPx.toDp() }
        val sungPath = remember { Path() }
        val unsungPath = remember { Path() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(frameHeightDp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            val centerOffsetX = ((size.width - layoutWidthPx) / 2f).coerceAtLeast(0f)
            val centerOffsetY = ((size.height - layoutHeightPx) / 2f).coerceAtLeast(0f)

            if (words == null || words.isEmpty()) {
                drawText(
                    textLayoutResult = layoutResult,
                    color = dimColor,
                    topLeft = Offset(centerOffsetX, centerOffsetY)
                )
                return@Canvas
            }

            var avgSpan = 300L
            if (words.size >= 2) {
                var totalSpan = 0L
                for (i in 0 until words.size - 1) {
                    totalSpan += words[i + 1].timeMs - words[i].timeMs
                }
                avgSpan = (totalSpan / (words.size - 1)).coerceIn(80L, 800L)
            }
            val lastWordEndTime = words.last().timeMs + avgSpan
            sungPath.rewind()
            unsungPath.rewind()

            var charOffset = 0
            for (i in words.indices) {
                val w = words[i]
                val startChar = charOffset.coerceAtMost(text.length)
                val endChar = (charOffset + w.text.length).coerceAtMost(text.length)
                charOffset = endChar
                if (endChar <= startChar) continue

                val startLine = layoutResult.getLineForOffset(startChar)
                val endLine = layoutResult.getLineForOffset(endChar - 1)

                val segments = ArrayList<Rect>()
                if (startLine == endLine) {
                    val firstBox = layoutResult.getBoundingBox(startChar)
                    val lastBox = layoutResult.getBoundingBox(endChar - 1)
                    val left = kotlin.math.min(firstBox.left, lastBox.left)
                    val right = kotlin.math.max(firstBox.right, lastBox.right)
                    segments.add(
                        Rect(
                            centerOffsetX + left,
                            layoutResult.getLineTop(startLine),
                            centerOffsetX + right,
                            layoutResult.getLineBottom(startLine)
                        )
                    )
                } else {
                    for (line in startLine..endLine) {
                        val lineLeft = if (line == startLine) {
                            layoutResult.getBoundingBox(startChar).left
                        } else {
                            layoutResult.getLineLeft(line)
                        }
                        val lineRight = if (line == endLine) {
                            layoutResult.getBoundingBox(endChar - 1).right
                        } else {
                            layoutResult.getLineRight(line)
                        }
                        segments.add(
                            Rect(
                                centerOffsetX + kotlin.math.min(lineLeft, lineRight),
                                layoutResult.getLineTop(line),
                                centerOffsetX + kotlin.math.max(lineLeft, lineRight),
                                layoutResult.getLineBottom(line)
                            )
                        )
                    }
                }

                val endTime = if (i + 1 < words.size) words[i + 1].timeMs else lastWordEndTime
                val span = (endTime - w.timeMs).coerceAtLeast(1L)

                if (positionMs >= endTime) {
                    for (seg in segments) sungPath.addRect(seg)
                } else if (positionMs < w.timeMs) {
                    for (seg in segments) unsungPath.addRect(seg)
                } else {
                    val progress = ((positionMs - w.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    val totalWidth = segments.sumOf { it.width.toDouble() }.toFloat()
                    var remaining = totalWidth * progress
                    for (seg in segments) {
                        if (remaining <= 0f && seg.width <= 0f) continue
                        val sungPart = seg.width.coerceAtMost(remaining)
                        remaining -= sungPart
                        if (seg.width > 0f && sungPart < seg.width) {
                            val splitX = seg.left + sungPart
                            sungPath.addRect(Rect(seg.left, seg.top, splitX, seg.bottom))
                            unsungPath.addRect(Rect(splitX, seg.top, seg.right, seg.bottom))
                        } else {
                            sungPath.addRect(seg)
                        }
                    }
                }
            }
            if (!sungPath.isEmpty) {
                clipPath(sungPath) {
                    drawText(
                        textLayoutResult = layoutResult,
                        color = sungColor,
                        topLeft = Offset(centerOffsetX, centerOffsetY)
                    )
                }
            }
            if (!unsungPath.isEmpty) {
                clipPath(unsungPath) {
                    drawText(
                        textLayoutResult = layoutResult,
                        color = unsungColor,
                        topLeft = Offset(centerOffsetX, centerOffsetY)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCover(
    modifier: Modifier = Modifier,
    filePath: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var shownBytes by remember {
        mutableStateOf(com.xiaowei.player.data.EmbeddedCoverFetcher.getCachedBytesSync(filePath))
    }
    var incomingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var incomingReady by remember { mutableStateOf(false) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(filePath) {
        if (filePath.isNullOrBlank()) return@LaunchedEffect
        val cached = com.xiaowei.player.data.EmbeddedCoverFetcher.getCachedBytesSync(filePath)
        val bytes = cached ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.xiaowei.player.data.EmbeddedCoverFetcher.loadCoverBytes(filePath)
        }
        if (bytes == null) {
            shownBytes = null
            return@LaunchedEffect
        }
        if (shownBytes == null || bytes === shownBytes) {
            shownBytes = bytes
            return@LaunchedEffect
        }
        incomingReady = false
        incomingBytes = bytes
    }

    LaunchedEffect(incomingBytes) {
        val bytes = incomingBytes ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { incomingReady }.first { it }
        fade.snapTo(0f)
        fade.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        shownBytes = bytes
        incomingBytes = null
        incomingReady = false
        fade.snapTo(0f)
    }

    val shownRequest = remember(shownBytes) {
        if (shownBytes != null) {
            coil.request.ImageRequest.Builder(context)
                .data(shownBytes)
                .size(1200)
                .memoryCacheKey("full\u0000$filePath")
                .build()
        } else null
    }

    val incomingRequest = remember(incomingBytes) {
        if (incomingBytes != null) {
            coil.request.ImageRequest.Builder(context)
                .data(incomingBytes)
                .size(1200)
                .memoryCacheKey("full\u0000$filePath")
                .build()
        } else null
    }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (shownRequest != null) {
            AsyncImage(
                model = shownRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {

            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(96.dp)
            )
        }
        if (incomingRequest != null) {
            AsyncImage(
                model = incomingRequest,
                contentDescription = null,
                onSuccess = { incomingReady = true },
                onError = { incomingBytes = null },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fade.value },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun MD3LandscapeContent(
    song: Song,
    playerState: MusicPlayerManager.PlayerState,
    onCyclePlayMode: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onShowPlaylist: () -> Unit,
    lyricsListState: LazyListState,
    lyricsCurrentIdx: Int,
    onLyricsCurrentIdxChange: (Int) -> Unit,
    lyricsInitialized: Boolean,
    onLyricsInitialized: () -> Unit
) {
    var controlsHidden by rememberSaveable { mutableStateOf(false) }
    val controlsVisible = !controlsHidden
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "LandscapeControlsAlpha"
    )
    val progressBlockHeight by animateDpAsState(
        targetValue = if (controlsVisible) 40.dp else 0.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "LandscapeProgressBlockHeight"
    )
    val progressSlide by animateDpAsState(
        targetValue = if (controlsVisible) 0.dp else 20.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "LandscapeProgressSlide"
    )
    val buttonBlockHeight by animateDpAsState(
        targetValue = if (controlsVisible) 54.dp else 0.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "LandscapeButtonBlockHeight"
    )
    val buttonSlide by animateDpAsState(
        targetValue = if (controlsVisible) 0.dp else 27.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "LandscapeButtonSlide"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (totalDrag < -60f) controlsHidden = true
                        else if (totalDrag > 60f) controlsHidden = false
                    }
                ) { _, dragAmount ->
                    totalDrag += dragAmount
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 8.dp)
        ) {
            val leftWidth = (maxWidth - 48.dp) * 0.38f
            val coverSize = minOf(
                leftWidth * 0.88f,
                maxHeight * 0.70f
            ).coerceAtMost(300.dp)
            val landscapeBottomInset = (((maxHeight - coverSize - 12.dp - 28.dp) / 2f) - 14.dp).coerceAtLeast(0.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            modifier = Modifier
                                .size(coverSize)
                                .clip(RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            PlayerCover(
                                modifier = Modifier.fillMaxSize(),
                                filePath = song.data
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(progressBlockHeight)
                                .graphicsLayer {
                                    translationY = progressSlide.toPx()
                                    alpha = controlsAlpha
                                }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(12.dp))
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .width(coverSize)
                                        .height(28.dp)
                                ) {
                                    val draggingValue = remember { mutableStateOf<Float?>(null) }
                                    val progress = draggingValue.value ?: if (playerState.durationMs > 0)
                                        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                                    else 0f
                                    Slider(
                                        value = progress,
                                        onValueChange = { draggingValue.value = it },
                                        onValueChangeFinished = {
                                            draggingValue.value?.let { v ->
                                                onSeek((v * playerState.durationMs).toLong())
                                            }
                                            draggingValue.value = null
                                        },
                                        valueRange = 0f..1f,
                                        enabled = controlsVisible,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Text(
                                        text = formatDuration(playerState.positionMs) + "/" + formatDuration(playerState.durationMs),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = maxWidth + 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(48.dp))
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, textAlign = TextAlign.Center),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.displayAlbumDashArtist,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, textAlign = TextAlign.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                    LyricsView(
                        song = song,
                        positionMs = playerState.positionMs,
                        positionUpdateNanos = playerState.positionUpdateNanos,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        onToggle = {},
                        onSeek = onSeek,
                        listState = lyricsListState,
                        currentIdx = lyricsCurrentIdx,
                        onCurrentIdxChange = onLyricsCurrentIdxChange,
                        initialized = lyricsInitialized,
                        onInitialized = onLyricsInitialized,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .height(buttonBlockHeight)
                            .clipToBounds()
                            .graphicsLayer {
                                translationY = buttonSlide.toPx()
                                alpha = controlsAlpha
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                IconButton(
                                    onClick = onPrev,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.SkipPrevious,
                                        Strings.get("previous"),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            val isPlaying = playerState.isPlaying
                            val rotation by animateFloatAsState(
                                targetValue = if (isPlaying) 0f else 360f,
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMedium,
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                ),
                                label = "LandscapePlayButtonRotation"
                            )
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .rotate(rotation)
                                    .clip(if (isPlaying) MaterialStarShape else CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onTogglePlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = {
                                        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.9f) togetherWith
                                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleOut(targetScale = 0.9f)
                                    },
                                    label = "LandscapePlayButtonIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (playing) Strings.get("pause") else Strings.get("play"),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onNext,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.SkipNext,
                                            Strings.get("next"),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    IconButton(
                                        onClick = onCyclePlayMode,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        val (modeIcon, modeLabelKey, modeTint) = when (playerState.playMode) {
                                            MusicPlayerManager.PlayMode.SEQUENCE -> Triple(
                                                Icons.Filled.Repeat, "play_mode_sequence",
                                                MaterialTheme.colorScheme.onBackground
                                            )
                                            MusicPlayerManager.PlayMode.SHUFFLE -> Triple(
                                                Icons.Filled.Shuffle, "play_mode_shuffle",
                                                MaterialTheme.colorScheme.primary
                                            )
                                            MusicPlayerManager.PlayMode.REPEAT_ONE -> Triple(
                                                Icons.Filled.RepeatOne, "play_mode_repeat_one",
                                                MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Icon(
                                            modeIcon,
                                            Strings.get(modeLabelKey),
                                            tint = modeTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    IconButton(
                                        onClick = onShowPlaylist,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            Strings.get("playlist"),
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(landscapeBottomInset))
                }
            }
        }
    }
}
