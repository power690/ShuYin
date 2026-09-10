package com.xiaowei.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaowei.player.LibraryState
import com.xiaowei.player.i18n.Strings
import com.xiaowei.player.navkit.BLUR_MAX_DP
import com.xiaowei.player.navkit.COMPRESS_SCALE_MIN
import com.xiaowei.player.navkit.COMPRESS_TRANSLATE_FRACTION
import com.xiaowei.player.navkit.ENTER_RADIUS_DP
import com.xiaowei.player.navkit.ENTER_SCALE_MIN
import com.xiaowei.player.navkit.ENTER_SHADOW_MAX
import com.xiaowei.player.navkit.progressiveBlur
import com.xiaowei.player.navkit.stackSceneSpringSpec
import com.xiaowei.player.navkit.supportsHardwareBlur
import com.xiaowei.player.player.MusicPlayerManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaowei.player.ui.screens.AlbumDetailScreen
import com.xiaowei.player.ui.screens.ArtistDetailScreen
import com.xiaowei.player.ui.screens.EmptyScanScreen
import com.xiaowei.player.ui.screens.FavoriteScreen
import com.xiaowei.player.ui.screens.LibraryScreen
import com.xiaowei.player.ui.screens.LoadingScreen
import com.xiaowei.player.ui.screens.LyricSynthScreen
import com.xiaowei.player.ui.screens.PlayerStyleScreen
import com.xiaowei.player.ui.screens.MineScreen
import com.xiaowei.player.ui.screens.NoPermissionScreen
import com.xiaowei.player.ui.screens.PlayerScreen
import com.xiaowei.player.ui.screens.ClassicPlayerScreen
import com.xiaowei.player.ui.screens.RecommendDetailScreen
import com.xiaowei.player.ui.screens.RecommendScreen
import com.xiaowei.player.ui.screens.SearchScreen
import com.xiaowei.player.ui.screens.SettingsScreen
import com.xiaowei.player.ui.screens.SponsorScreen
import com.xiaowei.player.ui.screens.MaterialSettingsScreen
import kotlinx.coroutines.launch
import kotlin.math.pow

private enum class Tab(val labelKey: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Recommend("tab_recommend", Icons.Filled.Home),
    Library("tab_library", Icons.Filled.LibraryMusic),
    Search("tab_search", Icons.Filled.Search),
    Mine("tab_mine", Icons.Filled.Person)
}

sealed class Detail {
    data class Artist(val name: String) : Detail()
    data class Album(val albumId: Long) : Detail()

    data class RecommendDetail(val card: com.xiaowei.player.data.RecommendCard) : Detail()

    object Search : Detail()
    object Favorite : Detail()
    object Settings : Detail()
    object MaterialSettings : Detail()
    object LyricSynth : Detail()
    object PlayerStyle : Detail()
    object Sponsor : Detail()
    object None : Detail()
}

@Composable
fun ShuYinApp(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    playerPlaylist: List<com.xiaowei.player.data.Song>,
    onPlaySong: (com.xiaowei.player.data.Song, List<com.xiaowei.player.data.Song>) -> Unit,
    onAddSong: (com.xiaowei.player.data.Song) -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onCyclePlayMode: () -> Unit,
    onPlayAtIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onRemoveFavorites: (List<Long>) -> Unit = {},
    floatingLyricEnabled: Boolean = false,
    onToggleFloatingLyric: () -> Unit = {},
    onCustomPathConfirm: (String) -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Recommend.name) }

    var detail by remember { mutableStateOf<Detail>(Detail.None) }

    var displayedDetail by remember { mutableStateOf<Detail>(Detail.None) }
    var detailBackStack by remember { mutableStateOf<List<Detail>>(emptyList()) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }
    var lastSong by remember { mutableStateOf<com.xiaowei.player.data.Song?>(null) }

    val enterProgress = remember { Animatable(0f) }
    val playerEnterProgress = remember { Animatable(0f) }

    val supportsBlur = remember { supportsHardwareBlur }

    val contentBackdrop = rememberLayerBackdrop()
    val permBackdrop = rememberLayerBackdrop()

    var detailNonce by remember { mutableStateOf(0) }

    fun requestDetail(newDetail: Detail) {
        if (detail == newDetail && newDetail != Detail.None) {

            detailNonce++
        } else {
            if (detail != Detail.None && newDetail != Detail.None) {
                detailBackStack = detailBackStack + detail
            }
            detail = newDetail
            detailNonce++
        }
    }
    fun popDetail() {
        if (detail == Detail.None) return
        if (detailBackStack.isNotEmpty()) {
            detail = detailBackStack.last()
            detailBackStack = detailBackStack.dropLast(1)
        } else {
            detail = Detail.None
        }
        detailNonce++
    }

    LaunchedEffect(detailNonce) {
        val target = detail
        if (target != Detail.None) {

            if (displayedDetail != target) {
                enterProgress.snapTo(0f)
                displayedDetail = target
            }

            enterProgress.animateTo(1f, stackSceneSpringSpec())
        } else if (displayedDetail != Detail.None) {

            enterProgress.animateTo(0f, stackSceneSpringSpec())

            displayedDetail = Detail.None

            enterProgress.snapTo(0f)
        }

    }

    val currentView = androidx.compose.ui.platform.LocalView.current

    val context = androidx.compose.ui.platform.LocalContext.current

    val themePrefs = remember { com.xiaowei.player.data.ThemePrefs.get(context) }
    val userMaterialStyle by themePrefs.materialStyleState
    val materialFrosted = userMaterialStyle == com.xiaowei.player.data.ThemePrefs.MATERIAL_STYLE_FROSTED
    val playerStyle by themePrefs.playerStyleState
    val useClassicPlayer = playerStyle == com.xiaowei.player.data.ThemePrefs.PLAYER_STYLE_CLASSIC

    val systemDarkForBars = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(playerExpanded) {
        currentView.keepScreenOn = playerExpanded
        if (playerExpanded) {
            playerEnterProgress.animateTo(1f, stackSceneSpringSpec())
        } else {
            playerEnterProgress.animateTo(0f, stackSceneSpringSpec())
        }
        try {
            val window = (currentView.context as? android.app.Activity)?.window
                ?: return@LaunchedEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, currentView)
            val classicOpen = playerExpanded && useClassicPlayer
            controller.isAppearanceLightStatusBars = !classicOpen && !systemDarkForBars
            controller.isAppearanceLightNavigationBars = !classicOpen && !systemDarkForBars
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        } catch (_: Exception) {
        }
    }

    val customPathPrefs = remember { com.xiaowei.player.data.CustomPathPrefs.get(context) }
    val hasCustomPath = customPathPrefs.pathState.value.trim().isNotEmpty()
    val hasManagePerm = remember(hasCustomPath) {
        if (hasCustomPath) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        } else false
    }

    val emptyScanButtonText = if (hasCustomPath && !hasManagePerm) {
        Strings.get("grant_permission")
    } else {
        Strings.get("rescan")
    }

    var backPressedOnce by rememberSaveable { mutableStateOf(false) }
    val backScope = rememberCoroutineScope()

    var libraryPage by rememberSaveable { mutableStateOf(0) }

    val recommendListState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState(0, 0)
    }

    val tabs = Tab.values()
    val mainPagerState = rememberPagerState(initialPage = tabs.indexOfFirst { it.name == currentTab }.coerceAtLeast(0)) { tabs.size }

    LaunchedEffect(mainPagerState.currentPage) {
        val newTab = tabs.getOrNull(mainPagerState.currentPage)?.name ?: Tab.Recommend.name
        if (newTab != currentTab) currentTab = newTab
    }

    val mainScope = rememberCoroutineScope()

    val currentSong = playerState.currentSong
    LaunchedEffect(currentSong) {
        if (currentSong != null) {
            lastSong = currentSong
        } else if (playerExpanded) {
            playerExpanded = false
        }
    }
    val inDetail = displayedDetail != Detail.None

    val coverColorEnabled = themePrefs.coverColorEnabledState.value
    LaunchedEffect(currentSong?.data, coverColorEnabled) {
        if (!coverColorEnabled) {
            themePrefs.coverColor = null
            return@LaunchedEffect
        }
        val data = currentSong?.data
        if (data == null) {
            themePrefs.coverColor = null
        } else {
            val seedLong = com.xiaowei.player.ui.theme.ThemeColorUtil.extractFromFilePath(data)
            themePrefs.coverColor = seedLong?.toInt()
        }
    }

    BackHandler(enabled = playerExpanded) {
        playerExpanded = false
    }
    BackHandler(enabled = !playerExpanded && inDetail) {
        popDetail()
    }
    BackHandler(enabled = !playerExpanded && !inDetail) {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            android.widget.Toast.makeText(
                context,
                Strings.get("press_back_again_to_exit"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            backScope.launch {
                kotlinx.coroutines.delay(2000)
                backPressedOnce = false
            }
        }
    }

    Scaffold(
        bottomBar = {

            if (!playerExpanded) {
                Spacer(Modifier.navigationBarsPadding().height(80.dp))
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->

        val navBarHeight = innerPadding.calculateBottomPadding()
        val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        val showMiniPlayer = currentSong != null && !playerExpanded && !inDetail
        val bottomReserved: Dp =
            systemNavBarHeight + (if (showMiniPlayer) 176.dp else 90.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                !library.permissionGranted -> {

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(permBackdrop)
                        ) {
                            NoPermissionScreen(onRequest = onRefresh)
                        }

                        if (!playerExpanded) {
                            LiquidGlassNavBar(
                                backdrop = permBackdrop,
                                tabs = tabs.map { it.icon to it.labelKey },
                                selectedTabIndex = { mainPagerState.targetPage },
                                onTabSelected = { index ->
                                    if (mainPagerState.currentPage != index) {
                                        mainScope.launch {
                                            mainPagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
                                forceFrosted = materialFrosted,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = systemNavBarHeight + 24.dp)
                            )
                        }
                    }
                }
                else -> {

                    val contentAnimLayer = rememberGraphicsLayer()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val detailP = enterProgress.value.coerceIn(0f, 1f)
                                val playerP = playerEnterProgress.value.coerceIn(0f, 1f)
                                val compression = maxOf(detailP, playerP)
                                val animating =
                                    enterProgress.isRunning || playerEnterProgress.isRunning

                                if (compression > 0f || animating) {
                                    contentAnimLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    val scale = COMPRESS_SCALE_MIN +
                                            (1f - COMPRESS_SCALE_MIN) * (1f - compression)
                                    contentAnimLayer.pivotOffset =
                                        Offset(size.width / 2f, size.height / 2f)
                                    contentAnimLayer.scaleX = scale
                                    contentAnimLayer.scaleY = scale

                                    contentAnimLayer.translationX =
                                        -compression * size.width * COMPRESS_TRANSLATE_FRACTION

                                    if (supportsBlur && compression > 0f && animating) {
                                        val blurSigma = compression * density * BLUR_MAX_DP
                                        contentAnimLayer.renderEffect =
                                            BlurEffect(blurSigma, blurSigma, TileMode.Clamp)
                                    }
                                    drawLayer(contentAnimLayer)
                                } else {
                                    drawContent()
                                }
                            }
                    ) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(contentBackdrop)
                        ) {
                            HorizontalPager(
                                state = mainPagerState,
                                modifier = Modifier.fillMaxSize(),

                                beyondViewportPageCount = 1,

                                userScrollEnabled = false
                            ) { page ->
                                val tab = tabs[page]
                                when (tab) {
                                    Tab.Recommend -> {
                                        if (library.isLoading) LoadingScreen()
                                        else if (library.songs.isEmpty()) EmptyScanScreen(onRescan = onRefresh, buttonText = emptyScanButtonText)
                                        else RecommendScreen(
                                            library = library,
                                            playerState = playerState,
                                            onPlaySong = onPlaySong,
                                            onAddSong = onAddSong,
                                            onOpenArtist = { requestDetail(Detail.Artist(it)) },
                                            onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                            onOpenPlayer = { playerExpanded = true },
                                            onRefresh = onRefresh,
                                            onOpenRecommendCard = { card -> requestDetail(Detail.RecommendDetail(card)) },
                                            listState = recommendListState,
                                            bottomPadding = bottomReserved
                                        )
                                    }
                                    Tab.Library -> {
                                        if (library.isLoading) LoadingScreen()
                                        else if (library.songs.isEmpty()) EmptyScanScreen(onRescan = onRefresh, buttonText = emptyScanButtonText)
                                        else LibraryScreen(
                                            library = library,
                                            playerState = playerState,
                                            onPlaySong = onPlaySong,
                                            onAddSong = onAddSong,
                                            onSearch = onSearch,
                                            onOpenArtist = { requestDetail(Detail.Artist(it)) },
                                            onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                            onOpenPlayer = { playerExpanded = true },
                                            initialPage = libraryPage,
                                            onPageChanged = { libraryPage = it },
                                            bottomPadding = bottomReserved
                                        )
                                    }
                                    Tab.Search -> {
                                        if (library.isLoading) LoadingScreen()
                                        else if (library.songs.isEmpty()) EmptyScanScreen(onRescan = onRefresh, buttonText = emptyScanButtonText)
                                        else SearchScreen(
                                            library = library,
                                            playerState = playerState,
                                            onPlaySong = onPlaySong,
                                            onAddSong = onAddSong,
                                            onBack = {},
                                            onOpenPlayer = { playerExpanded = true },
                                            isActive = mainPagerState.currentPage == page,
                                            bottomPadding = bottomReserved
                                        )
                                    }
                                    Tab.Mine -> MineScreen(
                                        onOpenFavorite = { requestDetail(Detail.Favorite) },
                                        onOpenSettings = { requestDetail(Detail.Settings) },
                                        onOpenLyricSynth = { requestDetail(Detail.LyricSynth) },
                                        bottomPadding = bottomReserved + 88.dp
                                    )
                                }
                            }
                        }

                        if (currentSong != null && !playerExpanded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(
                                        bottom = systemNavBarHeight + 94.dp
                                    )
                            ) {
                                MiniPlayerBar(
                                    song = currentSong,
                                    isPlaying = playerState.isPlaying,
                                    onPlayPause = onTogglePlayPause,
                                    onPrev = onSkipPrev,
                                    onNext = onSkipNext,
                                    onClick = { playerExpanded = true },
                                    glassBackdrop = contentBackdrop,
                                    forceFrosted = materialFrosted
                                )
                            }
                        }

                        if (!playerExpanded) {
                            LiquidGlassNavBar(
                                backdrop = contentBackdrop,
                                tabs = tabs.map { it.icon to it.labelKey },
                                selectedTabIndex = { mainPagerState.targetPage },
                                onTabSelected = { index ->
                                    if (mainPagerState.currentPage != index) {
                                        mainScope.launch {
                                            mainPagerState.animateScrollToPage(index)
                                        }
                                    }
                                    if (detail != Detail.None) {
                                        popDetail()
                                    }
                                },
                                forceFrosted = materialFrosted,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = systemNavBarHeight + 24.dp)
                            )
                        }
                    }

                    if (displayedDetail != Detail.None) {
                        val detailAnimLayer = rememberGraphicsLayer()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    val enter = enterProgress.value
                                    val playerP = playerEnterProgress.value.coerceIn(0f, 1f)
                                    val animating =
                                        enterProgress.isRunning || playerEnterProgress.isRunning

                                    if (enter < 1f || playerP > 0f || animating) {
                                        detailAnimLayer.record {
                                            this@drawWithContent.drawContent()
                                        }
                                        val enterScale = ENTER_SCALE_MIN + (1f - ENTER_SCALE_MIN) * enter
                                        val compressScale = COMPRESS_SCALE_MIN +
                                            (1f - COMPRESS_SCALE_MIN) * (1f - playerP)
                                        detailAnimLayer.pivotOffset =
                                            Offset(size.width / 2f, size.height / 2f)
                                        detailAnimLayer.scaleX = enterScale * compressScale
                                        detailAnimLayer.scaleY = enterScale * compressScale

                                        detailAnimLayer.translationX = (1f - enter) * size.width -
                                            playerP * size.width * COMPRESS_TRANSLATE_FRACTION

                                        val radiusDp = ENTER_RADIUS_DP -
                                            enter.toDouble().pow(8.0).toFloat() * ENTER_RADIUS_DP +
                                            playerP * ENTER_RADIUS_DP
                                        detailAnimLayer.setOutline(
                                            RoundedCornerShape(radiusDp.dp).createOutline(
                                                size, layoutDirection, this
                                            )
                                        )
                                        detailAnimLayer.clip = true

                                        detailAnimLayer.shadowElevation =
                                            ENTER_SHADOW_MAX.dp.toPx() * (1f - enter)

                                        if (supportsBlur && playerP > 0f &&
                                            playerEnterProgress.isRunning) {
                                            val blurSigma = playerP * density * BLUR_MAX_DP
                                            detailAnimLayer.renderEffect =
                                                BlurEffect(blurSigma, blurSigma, TileMode.Clamp)
                                        }
                                        drawLayer(detailAnimLayer)
                                    } else {
                                        drawContent()
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {

                                }
                        ) {
                            when (displayedDetail) {
                                is Detail.Artist -> ArtistDetailScreen(
                                    artistName = (displayedDetail as Detail.Artist).name,
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onAddSong = onAddSong,
                                    onBack = { popDetail() },
                                    onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                is Detail.Album -> AlbumDetailScreen(
                                    albumId = (displayedDetail as Detail.Album).albumId,
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onAddSong = onAddSong,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                is Detail.RecommendDetail -> RecommendDetailScreen(
                                    card = (displayedDetail as Detail.RecommendDetail).card,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onAddSong = onAddSong,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Search -> SearchScreen(
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onAddSong = onAddSong,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Favorite -> FavoriteScreen(
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onAddSong = onAddSong,
                                    onRemoveFavorites = onRemoveFavorites,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Settings -> SettingsScreen(
                                    onBack = { popDetail() },
                                    onCustomPathConfirm = onCustomPathConfirm,
                                    onToggleMixWithOthers = { mix ->
                                        try {
                                            com.xiaowei.player.ShuYinApp.instance.playerManager
                                                .updateAudioFocusHandling(mix)
                                        } catch (_: Exception) {
                                        }
                                    },
                                    onOpenMaterialSettings = { requestDetail(Detail.MaterialSettings) },
                                    onOpenPlayerStyle = { requestDetail(Detail.PlayerStyle) },
                                    onOpenSponsor = { requestDetail(Detail.Sponsor) }
                                )
                                Detail.MaterialSettings -> MaterialSettingsScreen(
                                    onBack = { popDetail() }
                                )
                                Detail.LyricSynth -> LyricSynthScreen(
                                    onBack = { popDetail() }
                                )
                                Detail.PlayerStyle -> PlayerStyleScreen(
                                    onBack = { popDetail() }
                                )
                                Detail.Sponsor -> SponsorScreen(
                                    onBack = { popDetail() }
                                )
                                Detail.None -> {  }
                            }
                        }
                    }
                }
            }

        }
    }

    AnimatedVisibility(
        visible = playerExpanded && currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {

                }
        ) {
            currentSong?.let { song ->
                if (useClassicPlayer) {
                    ClassicPlayerScreen(
                        song = song,
                        playerState = playerState,
                        playlist = playerPlaylist,
                        isFavorite = library.favoriteIds.contains(song.id),
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onSkipNext,
                        onPrev = onSkipPrev,
                        onSeek = onSeek,
                        onCyclePlayMode = onCyclePlayMode,
                        onPlayAtIndex = onPlayAtIndex,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onMinimize = { playerExpanded = false },
                        floatingLyricEnabled = floatingLyricEnabled,
                        onToggleFloatingLyric = onToggleFloatingLyric
                    )
                } else {
                    PlayerScreen(
                        song = song,
                        playerState = playerState,
                        playlist = playerPlaylist,
                        isFavorite = library.favoriteIds.contains(song.id),
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onSkipNext,
                        onPrev = onSkipPrev,
                        onSeek = onSeek,
                        onCyclePlayMode = onCyclePlayMode,
                        onPlayAtIndex = onPlayAtIndex,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onMinimize = { playerExpanded = false },
                        floatingLyricEnabled = floatingLyricEnabled,
                        onToggleFloatingLyric = onToggleFloatingLyric
                    )
                }
            } ?: lastSong?.let { song ->
                if (useClassicPlayer) {
                    ClassicPlayerScreen(
                        song = song,
                        playerState = playerState,
                        playlist = playerPlaylist,
                        isFavorite = library.favoriteIds.contains(song.id),
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onSkipNext,
                        onPrev = onSkipPrev,
                        onSeek = onSeek,
                        onCyclePlayMode = onCyclePlayMode,
                        onPlayAtIndex = onPlayAtIndex,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onMinimize = { playerExpanded = false },
                        floatingLyricEnabled = floatingLyricEnabled,
                        onToggleFloatingLyric = onToggleFloatingLyric
                    )
                } else {
                    PlayerScreen(
                        song = song,
                        playerState = playerState,
                        playlist = playerPlaylist,
                        isFavorite = library.favoriteIds.contains(song.id),
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onSkipNext,
                        onPrev = onSkipPrev,
                        onSeek = onSeek,
                        onCyclePlayMode = onCyclePlayMode,
                        onPlayAtIndex = onPlayAtIndex,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        onMinimize = { playerExpanded = false },
                        floatingLyricEnabled = floatingLyricEnabled,
                        onToggleFloatingLyric = onToggleFloatingLyric
                    )
                }
            }
        }
    }
}
