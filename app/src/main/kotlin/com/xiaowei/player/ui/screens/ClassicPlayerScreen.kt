package com.xiaowei.player.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import com.xiaowei.player.data.LyricsParser
import com.xiaowei.player.data.Song
import com.xiaowei.player.player.MusicPlayerManager
import com.xiaowei.player.ui.components.AlbumCover
import com.xiaowei.player.ui.components.formatDuration
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.xiaowei.player.R
import androidx.compose.ui.res.stringResource
import com.xiaowei.player.i18n.Strings

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ClassicPlayerScreen(
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

    // 播放页永远深色背景：acquire 全局标志让主题强制白色系统栏图标（MD3 页不受影响）。
    // 版本兼容走 WindowInsetsControllerCompat（API23-29 旧 flag / API30+ InsetsController /
    // Android15+ edge-to-edge，同一入口覆盖 6~17）。
    val statusBarView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        // 只维护全局标志（供 MainActivity.reassert 读取），不再直接写窗口图标色：
        // 图标色的权威写入由 ZMusicApp 的 LaunchedEffect(playerExpanded) 在
        // 进入/退出压缩动画彻底结束后执行（参考项目同款时序）。动画期间抢写会被
        // Android 16 系统按内容重新取色吃掉（"发了也白发"），且是历史 Bug 源头。
        // 计数立即 +1（普通 Int 写入，任何时机都生效）；
        // 标志位通过 view.post 在 apply 阶段之外写入，避免快照丢写。
        com.xiaowei.player.ui.theme.StatusBarStyle.acquire()
        statusBarView.post { com.xiaowei.player.ui.theme.StatusBarStyle.ensureFlag() }
        onDispose {
            // 退出立即按当前系统深浅色还原（直接写窗口，即时生效）
            val exitWindow = (statusBarView.context as? android.app.Activity)?.window
            exitWindow?.let {
                val systemDarkNow = (statusBarView.resources.configuration.uiMode
                        and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                val exitController = androidx.core.view.WindowCompat.getInsetsController(it, statusBarView)
                exitController.isAppearanceLightStatusBars = !systemDarkNow
                exitController.isAppearanceLightNavigationBars = !systemDarkNow
            }
            // 计数释放也放到 apply 之外，保证标志位干净地翻回 false
            statusBarView.post {
                if (com.xiaowei.player.ui.theme.StatusBarStyle.release()) {
                    val w = (statusBarView.context as? android.app.Activity)?.window ?: return@post
                    val dark = (statusBarView.resources.configuration.uiMode
                            and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val c = androidx.core.view.WindowCompat.getInsetsController(w, statusBarView)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                }
            }
        }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2E2E2E),
                        Color(0xFF161616)
                    )
                )
            )
    ) {
        ClassicBlurredBackground(filePath = song.data)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.13f),
                            Color.Black.copy(alpha = 0.27f),
                            Color.Black.copy(alpha = 0.46f)
                        )
                    )
                )
        )
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
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    ClassicPlayerCover(
                                        modifier = Modifier.fillMaxSize(),
                                        filePath = song.data
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = song.displayAlbumDashArtist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = onFavoriteClick,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = Strings.get("favorite"),
                                        tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.7f),
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
                                            tint = Color.White.copy(alpha = 0.7f),
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
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (isLyrics) {

                            Spacer(Modifier.height(16.dp))
                            ClassicLyricsView(
                                song = song,
                                positionMs = playerState.positionMs,
                                positionUpdateNanos = playerState.positionUpdateNanos,
                                isPlaying = playerState.isPlaying,
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

                                val coverSize = (if (maxWidth < maxHeight) maxWidth * 0.93f else maxHeight * 0.75f).coerceAtMost(450.dp)
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
                                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    ClassicPlayerCover(
                                        modifier = Modifier.fillMaxSize(),
                                        filePath = song.data
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .offset(y = (-6).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = song.displayAlbumDashArtist,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = onFavoriteClick,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(x = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = Strings.get("favorite"),
                                        tint = if (isFavorite) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    val draggingValue = remember { mutableStateOf<Float?>(null) }
                    val progress = draggingValue.value ?: if (playerState.durationMs > 0)
                        (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
                    else 0f
                    val sliderInteraction = remember { MutableInteractionSource() }
                    val sliderInset = 4.dp
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sliderInset)
                            .height(28.dp)
                    ) {
                        val trackWidthPx = constraints.maxWidth.toFloat()
                        val density = LocalDensity.current
                        val centerY = with(density) { 14.dp.toPx() }
                        val thumbRadius = with(density) { 7.dp.toPx() }
                        val trackHeight = with(density) { 4.dp.toPx() }
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.25f),
                                start = Offset(0f, centerY),
                                end = Offset(trackWidthPx, centerY),
                                strokeWidth = trackHeight
                            )
                            drawLine(
                                color = Color.White,
                                start = Offset(0f, centerY),
                                end = Offset(trackWidthPx * progress, centerY),
                                strokeWidth = trackHeight
                            )
                            drawCircle(
                                color = Color.White,
                                radius = thumbRadius,
                                center = Offset(trackWidthPx * progress, centerY)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { pos ->
                                        val ratio = (pos.x / trackWidthPx).coerceIn(0f, 1f)
                                        onSeek((ratio * playerState.durationMs).toLong())
                                    })
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val ratio = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                            draggingValue.value = ratio
                                        },
                                        onDragEnd = {
                                            draggingValue.value?.let { v ->
                                                onSeek((v * playerState.durationMs).toLong())
                                            }
                                            draggingValue.value = null
                                        }
                                    )
                                }
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sliderInset),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playerState.positionMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatDuration(playerState.durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.5f)
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
                                Color.White.copy(alpha = 0.7f)
                            )
                            MusicPlayerManager.PlayMode.SHUFFLE -> Triple(
                                Icons.Filled.Shuffle, "play_mode_shuffle",
                                Color.White.copy(alpha = 0.7f)
                            )
                            MusicPlayerManager.PlayMode.REPEAT_ONE -> Triple(
                                Icons.Filled.RepeatOne, "play_mode_repeat_one",
                                Color.White.copy(alpha = 0.7f)
                            )
                        }
                        val label = Strings.get(labelKey)
                        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onPrev,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious, Strings.get("previous"),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))

                    val isPlaying = playerState.isPlaying

                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext, Strings.get("next"),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { showPlaylist = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert, Strings.get("playlist"),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(0.05f))
                Spacer(Modifier.height(8.dp))
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
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
private fun ClassicLyricsView(
    song: Song,
    positionMs: Long,
    positionUpdateNanos: Long,
    isPlaying: Boolean,
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

    val livePositionMs by produceState(
        initialValue = positionMs,
        positionMs,
        positionUpdateNanos,
        isPlaying
    ) {
        if (!isPlaying || positionUpdateNanos == 0L) {
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

    LaunchedEffect(livePositionMs, lines) {
        if (lines.isEmpty()) return@LaunchedEffect
        val idx = LyricsParser.findCurrentLine(lines, livePositionMs)
        if (idx != currentIdx && idx >= 0) {
            onCurrentIdxChange(idx)
            scope.launch {
                if (!initialized) {

                    listState.scrollToItem(idx)
                    onInitialized()
                } else {

                    listState.animateScrollToItem(idx)
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
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Strings.get("no_lyrics_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
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
                val distance = kotlin.math.abs(i - currentIdx)
                val lineAlpha = when {
                    currentIdx < 0 -> 0.6f
                    isCurrent -> 1f
                    distance <= 1 -> 0.6f
                    distance <= 2 -> 0.42f
                    distance <= 4 -> 0.28f
                    else -> 0.2f
                }
                val dimColor = Color.White.copy(alpha = lineAlpha)
                val sungColor = Color.White
                val unsungColor = Color.White.copy(alpha = 0.6f)

                val lineLivePositionMs = if (isCurrent && line.isWordByWord && isPlaying) livePositionMs else positionMs
                val useKaraoke = isCurrent && line.isWordByWord && !isBlank
                val baseSize = if (isCurrent) 18f else 16f
                val nextLineTimeMs = if (i + 1 < lines.size) lines[i + 1].timeMs else (line.timeMs + 4000L)
                val displayText = if (isBlank) "♪" else line.text

                ClassicKaraokeLineAndroidView(
                    text = displayText,
                    words = if (useKaraoke) line.words else null,
                    positionMs = lineLivePositionMs,
                    nextLineTimeMs = nextLineTimeMs,
                    sungColor = sungColor,
                    unsungColor = unsungColor,
                    dimColor = if (isCurrent) Color.White else dimColor,
                    fontSize = baseSize,
                    isBold = isCurrent,
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
private fun ClassicKaraokeLineAndroidView(
    text: String,
    words: List<com.xiaowei.player.data.LyricWord>?,
    positionMs: Long,
    nextLineTimeMs: Long,
    sungColor: Color,
    unsungColor: Color,
    dimColor: Color,
    fontSize: Float,
    isBold: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val textMeasurer = rememberTextMeasurer()
        val baseStyle = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        val annotatedText = remember(text) { AnnotatedString(text) }
        val layoutResult = remember(text, fontSize, isBold, maxWidthPx) {
            textMeasurer.measure(
                text = annotatedText,
                style = baseStyle,
                overflow = TextOverflow.Visible,
                softWrap = true,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
        }
        val layoutWidthPx = layoutResult.size.width
        val layoutHeightPx = layoutResult.size.height.coerceAtLeast(1)
        val layoutHeightDp = with(density) { layoutHeightPx.toDp() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(layoutHeightDp)
        ) {
            val centerOffsetX = ((size.width - layoutWidthPx) / 2f).coerceAtLeast(0f)

            if (words == null || words.isEmpty()) {
                drawText(
                    textLayoutResult = layoutResult,
                    color = dimColor,
                    topLeft = Offset(centerOffsetX, 0f)
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

                when {
                    positionMs >= endTime -> {
                        for (seg in segments) {
                            clipRect(seg.left, seg.top, seg.right, seg.bottom) {
                                drawText(
                                    textLayoutResult = layoutResult,
                                    color = sungColor,
                                    topLeft = Offset(centerOffsetX, 0f)
                                )
                            }
                        }
                    }
                    positionMs < w.timeMs -> {
                        for (seg in segments) {
                            clipRect(seg.left, seg.top, seg.right, seg.bottom) {
                                drawText(
                                    textLayoutResult = layoutResult,
                                    color = unsungColor,
                                    topLeft = Offset(centerOffsetX, 0f)
                                )
                            }
                        }
                    }
                    else -> {
                        val progress = ((positionMs - w.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                        val totalWidth = segments.sumOf { it.width.toDouble() }.toFloat()
                        var remaining = totalWidth * progress
                        for (seg in segments) {
                            if (remaining <= 0f && seg.width <= 0f) continue
                            val sungPart = seg.width.coerceAtMost(remaining)
                            remaining -= sungPart
                            if (seg.width > 0f && sungPart < seg.width) {
                                val splitX = seg.left + sungPart
                                clipRect(seg.left, seg.top, splitX, seg.bottom) {
                                    drawText(
                                        textLayoutResult = layoutResult,
                                        color = sungColor,
                                        topLeft = Offset(centerOffsetX, 0f)
                                    )
                                }
                                clipRect(splitX, seg.top, seg.right, seg.bottom) {
                                    drawText(
                                        textLayoutResult = layoutResult,
                                        color = unsungColor,
                                        topLeft = Offset(centerOffsetX, 0f)
                                    )
                                }
                            } else {
                                clipRect(seg.left, seg.top, seg.right, seg.bottom) {
                                    drawText(
                                        textLayoutResult = layoutResult,
                                        color = sungColor,
                                        topLeft = Offset(centerOffsetX, 0f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicBlurredBackground(filePath: String?) {
    // 双缓冲：remember 不随 filePath 重置，切歌时旧模糊图保留在屏上，新图加载完成后交叉淡入，不闪灰底
    var shownBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadedFile by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath) {
        if (filePath.isNullOrBlank()) {
            shownBitmap = null
            loadedFile = null
            return@LaunchedEffect
        }
        if (loadedFile == filePath && shownBitmap != null) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = com.xiaowei.player.data.EmbeddedCoverFetcher.loadCoverBytes(filePath)
            if (bytes == null) return@withContext
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext
            val reqWidth = 1200
            val reqHeight = 1200
            var sample = 1
            val halfWidth = bounds.outWidth / 2
            val halfHeight = bounds.outHeight / 2
            if (bounds.outHeight > reqHeight || bounds.outWidth > reqWidth) {
                while (halfHeight / sample >= reqHeight && halfWidth / sample >= reqWidth) {
                    sample *= 2
                }
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (source == null) return@withContext
            val result = com.xiaowei.player.NativeBlurUtils.blur(source, 25)
            if (source !== result && !source.isRecycled) source.recycle()
            shownBitmap = result
            loadedFile = filePath
        }
    }

    Crossfade(
        targetState = shownBitmap,
        animationSpec = tween(450),
        label = "BlurredBgCrossfade"
    ) { bmp ->
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ClassicPlayerCover(
    modifier: Modifier = Modifier,
    filePath: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var coverBytes by remember(filePath) {
        mutableStateOf(com.xiaowei.player.data.EmbeddedCoverFetcher.getCachedBytesSync(filePath))
    }

    if (coverBytes == null && !filePath.isNullOrBlank()) {
        LaunchedEffect(filePath) {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.xiaowei.player.data.EmbeddedCoverFetcher.loadCoverBytes(filePath)
            }
            coverBytes = bytes
        }
    }

    val imageRequest = remember(coverBytes) {
        if (coverBytes != null && !filePath.isNullOrBlank()) {
            coil.request.ImageRequest.Builder(context)
                .data(coverBytes)
                .size(1200)
                .memoryCacheKey("full\u0000$filePath")
                .build()
        } else null
    }

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF2E2E2E),
                    Color(0xFF161616)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {

            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(96.dp)
            )
        }
    }
}
