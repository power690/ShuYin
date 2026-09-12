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
import com.xiaowei.player.data.Song
import com.xiaowei.player.data.WebDavMusicSource
import com.xiaowei.player.data.WebDavPrefs
import com.xiaowei.player.player.MusicPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LibraryState(
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = false,
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val recommends: List<RecommendCard> = emptyList(),
    val searchQuery: String = "",

    val favoriteIds: Set<Long> = emptySet(),

    val favoriteVersion: Int = 0,

    val artistSongMap: Map<String, List<Song>> = emptyMap(),

    val webDavActive: Boolean = false,
    val webDavErrorKey: String = ""
) {
    val filteredSongs: List<Song>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) songs
            else songs.filter {
                it.title.contains(q, true) ||
                it.artist.contains(q, true) ||
                it.album.contains(q, true)
            }
        }
    val filteredArtists: List<Artist>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) artists
            else artists.filter { it.displayName.contains(q, true) }
        }
    val filteredAlbums: List<Album>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) albums
            else albums.filter { it.displayName.contains(q, true) || it.displayArtist.contains(q, true) }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private val webDavSource = WebDavMusicSource(app)
    private val favoriteRepo = (app as ShuYinApp).favoriteRepo
    val playerManager: MusicPlayerManager = (app as ShuYinApp).playerManager

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private var hasRestoredPlayback = false

    init {

        _library.value = _library.value.copy(favoriteIds = favoriteRepo.getFavoriteIdsSync())

        (app as ShuYinApp).onNotificationToggleFavorite = { songId ->
            toggleFavorite(songId)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _library.value = _library.value.copy(permissionGranted = granted)
        if (granted) refresh()
    }

    fun refresh(forceRescan: Boolean = false) {
        viewModelScope.launch {
            _library.value = _library.value.copy(isLoading = true)

            val webDavAccount = WebDavPrefs.get(getApplication()).activeAccount()
            var webDavError = ""
            val songs = withTimeoutOrNull(600_000L) {
                if (webDavAccount != null) {
                    val result = webDavSource.loadSongs(webDavAccount, forceRescan)
                    webDavError = result.errorKey
                    result.songs
                } else {
                    val customPath = CustomPathPrefs.get(getApplication()).path.trim()
                    if (customPath.isNotBlank()) {
                        repo.loadMusicFromPath(customPath)
                    } else {
                        repo.loadAllMusic()
                    }
                }
            } ?: emptyList()
            val (artists, artistSongMap) = repo.buildArtists(songs)
            val albums = repo.buildAlbums(songs)
            val recommends = repo.buildRecommendCards(songs, artists, albums, artistSongMap)

            _library.value = _library.value.copy(
                isLoading = false,
                permissionGranted = true,
                songs = songs,
                artists = artists,
                albums = albums,
                recommends = recommends,
                artistSongMap = artistSongMap,
                webDavActive = webDavAccount != null,
                webDavErrorKey = webDavError
            )

            if (!hasRestoredPlayback) {
                hasRestoredPlayback = true

                if (playerManager.playlist.isEmpty()) {
                    playerManager.restoreFromPrefs(songs)
                }
            }
        }
    }

    fun setSearchQuery(q: String) {
        _library.value = _library.value.copy(searchQuery = q)
    }

    fun refreshFromPath(path: String) {
        viewModelScope.launch {

            if (playerManager.playlist.isNotEmpty()) {
                playerManager.clearQueue()
            }

            CustomPathPrefs.get(getApplication()).path = path.trim()

            refresh()
        }
    }

    fun refreshFromWebDav() {
        viewModelScope.launch {

            if (playerManager.playlist.isNotEmpty()) {
                playerManager.clearQueue()
            }

            refresh(true)
        }
    }

    fun toggleFavorite(songId: Long) {
        val isFav = favoriteRepo.toggleSync(songId)
        _library.value = _library.value.copy(
            favoriteIds = favoriteRepo.getFavoriteIdsSync(),
            favoriteVersion = _library.value.favoriteVersion + 1
        )

    }

    fun removeFavorites(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.forEach { favoriteRepo.removeSync(it) }
        _library.value = _library.value.copy(
            favoriteIds = favoriteRepo.getFavoriteIdsSync(),
            favoriteVersion = _library.value.favoriteVersion + 1
        )
    }

    fun isFavorite(songId: Long): Boolean = favoriteRepo.isFavoriteSync(songId)

    fun playAllFrom(songs: List<Song>, song: Song) {
        playerManager.playAllFrom(songs, song)
    }

    fun addToQueue(song: Song) {
        playerManager.requestPlaySong(song)
    }

    override fun onCleared() {

        super.onCleared()
    }
}
