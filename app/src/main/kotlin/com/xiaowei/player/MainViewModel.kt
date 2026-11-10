package com.xiaowei.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaowei.player.data.Album
import com.xiaowei.player.data.Artist
import com.xiaowei.player.data.CustomPathPrefs
import com.xiaowei.player.data.FavoriteRepository
import com.xiaowei.player.data.MusicRepository
import com.xiaowei.player.data.RecommendCard
import com.xiaowei.player.data.RecommendPriorityPrefs
import com.xiaowei.player.data.Song
import com.xiaowei.player.data.WebDavMusicSource
import com.xiaowei.player.data.WebDavPrefs
import com.xiaowei.player.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LibraryState(
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false,
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val recommends: List<RecommendCard> = emptyList(),
    val hotAlbums: List<Album> = emptyList(),
    val hotArtists: List<Artist> = emptyList(),
    val searchQuery: String = "",
    val favoriteIds: Set<Long> = emptySet(),
    val favoriteVersion: Int = 0,
    val artistSongMap: Map<String, List<Song>> = emptyMap(),
    val webDavActive: Boolean = false,
    val webDavErrorKey: String = "",
    val webDavSongs: List<Song> = emptyList(),
    val activeSongs: List<Song> = emptyList(),
) {
    val filteredSongs: List<Song>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(q, true) ||
                        it.artist.contains(q, true) ||
                        it.album.contains(q, true)
                }
            }
        }
    val filteredArtists: List<Artist>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) {
                artists
            } else {
                artists.filter { it.displayName.contains(q, true) }
            }
        }
    val filteredAlbums: List<Album>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) {
                albums
            } else {
                albums.filter { it.displayName.contains(q, true) || it.displayArtist.contains(q, true) }
            }
        }
    val filteredWebDavSongs: List<Song>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) {
                webDavSongs
            } else {
                webDavSongs.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                }
            }
        }
}

class MainViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val webDavSource = WebDavMusicSource(app)
    private val favoriteRepo = (app as ShuYinApp).favoriteRepo
    val playerManager: MusicPlayerManager = (app as ShuYinApp).playerManager

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private var hasRestoredPlayback = false
    private var refreshJob: Job? = null

    init {

        _library.value =
            _library.value.copy(
                favoriteIds = favoriteRepo.getFavoriteIdsSync(),
                webDavActive = WebDavPrefs.get(app).activeAccount() != null,
            )

        (app as ShuYinApp).onNotificationToggleFavorite = { songId ->
            toggleFavorite(songId)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _library.value = _library.value.copy(permissionGranted = granted)
        if (granted) refresh()
    }

    fun refresh(forceRescan: Boolean = false) {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val webDavAccount = WebDavPrefs.get(getApplication()).activeAccount()
                _library.value = _library.value.copy(isLoading = true, webDavActive = webDavAccount != null)

                var webDavError = ""
                val localSongs =
                    withTimeoutOrNull(600_000L) {
                        val customPath = CustomPathPrefs.get(getApplication()).path.trim()
                        if (customPath.isNotBlank()) {
                            repo.loadMusicFromPath(customPath)
                        } else {
                            repo.loadAllMusic()
                        }
                    } ?: emptyList()
                val webDavSongs =
                    if (webDavAccount != null) {
                        withTimeoutOrNull(600_000L) {
                            val result = webDavSource.loadSongs(webDavAccount, forceRescan)
                            webDavError = result.errorKey
                            result.songs
                        } ?: emptyList()
                    } else {
                        emptyList()
                    }

                val priority = RecommendPriorityPrefs.get(getApplication()).priority
                val recommendPool =
                    when {
                        priority == RecommendPriorityPrefs.PRIORITY_NETWORK && webDavSongs.isNotEmpty() -> webDavSongs
                        priority == RecommendPriorityPrefs.PRIORITY_MIXED && webDavSongs.isNotEmpty() -> localSongs + webDavSongs
                        else -> localSongs
                    }

                val (artists, artistSongMap) = repo.buildArtists(recommendPool)
                val albums = repo.buildAlbums(recommendPool)

                val recommends = repo.buildRecommendCards(recommendPool, artists, albums, artistSongMap)
                val hotAlbums = albums.shuffled().take(10)
                val hotArtists = artists.shuffled().take(10)

                if (localSongs.isNotEmpty() || webDavSongs.isNotEmpty() || webDavAccount == null) {
                    com.xiaowei.player.data
                        .updateSongCountSnapshot(getApplication(), localSongs.size + webDavSongs.size)
                }

                _library.value =
                    _library.value.copy(
                        isLoading = false,
                        permissionGranted = true,
                        songs = localSongs,
                        webDavSongs = webDavSongs,
                        artists = artists,
                        albums = albums,
                        recommends = recommends,
                        hotAlbums = hotAlbums,
                        hotArtists = hotArtists,
                        artistSongMap = artistSongMap,
                        webDavActive = webDavAccount != null,
                        webDavErrorKey = webDavError,
                        activeSongs = recommendPool,
                    )

                if (!hasRestoredPlayback) {
                    hasRestoredPlayback = true

                    if (playerManager.playlist.isEmpty()) {
                        playerManager.restoreFromPrefs(localSongs + webDavSongs)
                    }
                }
            }
    }

    fun setSearchQuery(q: String) {
        _library.value = _library.value.copy(searchQuery = q)
    }

    fun refreshFromPath(path: String) {
        viewModelScope.launch {
            if (playerManager.playlist.any { it.source != "webdav" }) {
                playerManager.clearQueue()
            }

            CustomPathPrefs.get(getApplication()).path = path.trim()

            refresh()
        }
    }

    fun refreshFromWebDav() {
        viewModelScope.launch {
            if (playerManager.playlist.any { it.source == "webdav" }) {
                playerManager.clearQueue()
            }

            refresh(true)
        }
    }

    fun toggleFavorite(songId: Long) {
        val isFav = favoriteRepo.toggleSync(songId)
        _library.value =
            _library.value.copy(
                favoriteIds = favoriteRepo.getFavoriteIdsSync(),
                favoriteVersion = _library.value.favoriteVersion + 1,
            )
    }

    fun removeFavorites(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.forEach { favoriteRepo.removeSync(it) }
        _library.value =
            _library.value.copy(
                favoriteIds = favoriteRepo.getFavoriteIdsSync(),
                favoriteVersion = _library.value.favoriteVersion + 1,
            )
    }

    fun isFavorite(songId: Long): Boolean = favoriteRepo.isFavoriteSync(songId)

    fun playAllFrom(
        songs: List<Song>,
        song: Song,
    ) {
        playerManager.playAllFrom(songs, song)
    }

    fun addToQueue(song: Song) {
        playerManager.requestPlaySong(song)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
