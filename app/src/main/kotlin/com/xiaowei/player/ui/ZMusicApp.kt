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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
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
import com.xiaowei.player.ui.screens.AlbumDetailScreen
import com.xiaowei.player.ui.screens.ArtistDetailScreen
import com.xiaowei.player.ui.screens.EmptyScanScreen
import com.xiaowei.player.ui.screens.FavoriteScreen
import com.xiaowei.player.ui.screens.LibraryScreen
import com.xiaowei.player.ui.screens.LoadingScreen
import com.xiaowei.player.ui.screens.MineScreen
import com.xiaowei.player.ui.screens.NoPermissionScreen
import com.xiaowei.player.ui.screens.PlayerScreen
import com.xiaowei.player.ui.screens.RecommendDetailScreen
import com.xiaowei.player.ui.screens.RecommendScreen
import com.xiaowei.player.ui.screens.SearchScreen
import com.xiaowei.player.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import kotlin.math.pow

private enum class Tab(val labelKey: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Recommend("tab_recommend", Icons.Filled.Home),
    Library("tab_library", Icons.Filled.LibraryMusic),
    Mine("tab_mine", Icons.Filled.Person)
}

sealed class Detail {
    data class Artist(val name: String) : Detail()
    data class Album(val albumId: Long) : Detail()

    data class RecommendDetail(val card: com.xiaowei.player.data.RecommendCard) : Detail()

    object Search : Detail()
    object Favorite : Detail()
    object Settings : Detail()
    object None : Detail()
}

@Composable
fun ShuYinApp(
    library: LibraryState,
    playerState: MusicPlayerManager.PlayerState,
    playerPlaylist: List<com.xiaowei.player.data.Song>,
    onPlaySong: (com.xiaowei.player.data.Song, List<com.xiaowei.player.data.Song>) -> Unit,
    onPlayAll: (List<com.xiaowei.player.data.Song>, Int) -> Unit,
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
    floatingLyricEnabled: Boolean = false,
    onToggleFloatingLyric: () -> Unit = {},
    onCustomPathConfirm: (String) -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Recommend.name) }

    var detail by remember { mutableStateOf<Detail>(Detail.None) }

    var displayedDetail by remember { mutableStateOf<Detail>(Detail.None) }
    var playerExpanded by rememberSaveable { mutableStateOf(false) }

    val enterProgress = remember { Animatable(0f) }

    val supportsBlur = remember { supportsHardwareBlur }

    var detailNonce by remember { mutableStateOf(0) }

    fun requestDetail(newDetail: Detail) {
        if (detail == newDetail && newDetail != Detail.None) {

            detailNonce++
        } else {
            detail = newDetail
            detailNonce++
        }
    }
    fun popDetail() {
        if (detail == Detail.None) return  
        detail = Detail.None
        detailNonce++
    }

    LaunchedEffect(detailNonce) {
        val target = detail
        if (target != Detail.None) {

            if (displayedDetail != target) {

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
    LaunchedEffect(playerExpanded) {
        currentView.keepScreenOn = playerExpanded
    }

    val context = androidx.compose.ui.platform.LocalContext.current

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
    val inDetail = displayedDetail != Detail.None

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

        val showMiniPlayer = currentSong != null && !playerExpanded && !inDetail
        val miniPlayerHeight: Dp = if (showMiniPlayer) 80.dp else 0.dp
        val bottomReserved = navBarHeight + miniPlayerHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                !library.permissionGranted -> {

                    Box(modifier = Modifier.fillMaxSize()) {
                        NoPermissionScreen(onRequest = onRefresh)

                        if (!playerExpanded) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = mainPagerState.currentPage == tab.ordinal,
                                        onClick = {
                                            if (mainPagerState.currentPage != tab.ordinal) {
                                                mainScope.launch {
                                                    mainPagerState.animateScrollToPage(tab.ordinal)
                                                }
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(Strings.get(tab.labelKey)) }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val compression = enterProgress.value.coerceIn(0f, 1f)

                                val scale = COMPRESS_SCALE_MIN +
                                        (1f - COMPRESS_SCALE_MIN) * (1f - compression)
                                scaleX = scale
                                scaleY = scale

                                translationX = -compression * size.width * COMPRESS_TRANSLATE_FRACTION

                                if (supportsBlur && enterProgress.isRunning && compression > 0f) {
                                    val blurSigma = compression * density * BLUR_MAX_DP
                                    renderEffect = BlurEffect(blurSigma, blurSigma, TileMode.Clamp)
                                }
                            }
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
                                        onPlayAll = onPlayAll,
                                        onOpenArtist = { requestDetail(Detail.Artist(it)) },
                                        onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                        onOpenPlayer = { playerExpanded = true },
                                        onRefresh = onRefresh,
                                        onOpenRecommendCard = { card -> requestDetail(Detail.RecommendDetail(card)) },
                                        onOpenSearch = { requestDetail(Detail.Search) },
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
                                        onPlayAll = onPlayAll,
                                        onSearch = onSearch,
                                        onOpenArtist = { requestDetail(Detail.Artist(it)) },
                                        onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                        onOpenPlayer = { playerExpanded = true },
                                        initialPage = libraryPage,
                                        onPageChanged = { libraryPage = it },
                                        bottomPadding = bottomReserved
                                    )
                                }
                                Tab.Mine -> MineScreen(
                                    onOpenFavorite = { requestDetail(Detail.Favorite) },
                                    onOpenSettings = { requestDetail(Detail.Settings) }
                                )
                            }
                        }

                        if (currentSong != null && !playerExpanded) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = navBarHeight)
                            ) {
                                MiniPlayerBar(
                                    song = currentSong,
                                    isPlaying = playerState.isPlaying,
                                    positionMs = playerState.positionMs,
                                    durationMs = playerState.durationMs,
                                    onPlayPause = onTogglePlayPause,
                                    onPrev = onSkipPrev,
                                    onNext = onSkipNext,
                                    onSeek = onSeek,
                                    onClick = { playerExpanded = true }
                                )
                            }
                        }

                        if (!playerExpanded) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                Tab.values().forEach { tab ->
                                    NavigationBarItem(
                                        selected = mainPagerState.currentPage == tab.ordinal,
                                        onClick = {

                                            if (mainPagerState.currentPage != tab.ordinal) {
                                                mainScope.launch {
                                                    mainPagerState.animateScrollToPage(tab.ordinal)
                                                }
                                            }
                                            if (detail != Detail.None) {
                                                popDetail()
                                            }
                                        },
                                        icon = { Icon(tab.icon, contentDescription = null) },
                                        label = { Text(Strings.get(tab.labelKey)) }
                                    )
                                }
                            }
                        }
                    }

                    if (displayedDetail != Detail.None) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val enter = enterProgress.value

                                    translationX = (1f - enter) * size.width

                                    val scale = ENTER_SCALE_MIN + (1f - ENTER_SCALE_MIN) * enter
                                    scaleX = scale
                                    scaleY = scale

                                    val radiusDp = ENTER_RADIUS_DP -
                                        enter.toDouble().pow(8.0).toFloat() * ENTER_RADIUS_DP
                                    shape = RoundedCornerShape(radiusDp.dp)
                                    clip = true

                                    shadowElevation = ENTER_SHADOW_MAX * (1f - enter)
                                }
                        ) {
                            when (displayedDetail) {
                                is Detail.Artist -> ArtistDetailScreen(
                                    artistName = (displayedDetail as Detail.Artist).name,
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onPlayAll = onPlayAll,
                                    onBack = { popDetail() },
                                    onOpenAlbum = { requestDetail(Detail.Album(it)) },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                is Detail.Album -> AlbumDetailScreen(
                                    albumId = (displayedDetail as Detail.Album).albumId,
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onPlayAll = onPlayAll,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                is Detail.RecommendDetail -> RecommendDetailScreen(
                                    card = (displayedDetail as Detail.RecommendDetail).card,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onPlayAll = onPlayAll,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Search -> SearchScreen(
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onPlayAll = onPlayAll,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Favorite -> FavoriteScreen(
                                    library = library,
                                    playerState = playerState,
                                    onPlaySong = onPlaySong,
                                    onBack = { popDetail() },
                                    onOpenPlayer = { playerExpanded = true }
                                )
                                Detail.Settings -> SettingsScreen(
                                    onBack = { popDetail() },
                                    onCustomPathConfirm = onCustomPathConfirm
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
