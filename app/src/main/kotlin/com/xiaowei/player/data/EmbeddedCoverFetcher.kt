package com.xiaowei.player.data

import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object EmbeddedCoverFetcher {

    private const val TAG = "EmbeddedCoverFetcher"
    private const val BYTE_CACHE_BYTES = 6 * 1024 * 1024
    private const val NEGATIVE_CACHE_LIMIT = 512

    private val byteCache = object : LruCache<String, ByteArray>(BYTE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val negativeCache = HashSet<String>()

    private val networkCoverExecutor: ExecutorService = Executors.newFixedThreadPool(4) { r ->
        Thread(r).apply { isDaemon = true }
    }

    fun purgeLegacyDiskCache(context: android.content.Context) {
        try {
            val dir = File(context.cacheDir, "embedded_covers")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "purge legacy cover cache failed: ${e.message}")
        }
    }

    fun getCachedBytesSync(filePath: String?): ByteArray? {
        if (filePath.isNullOrBlank()) return null
        return byteCache[filePath]
    }

    fun loadCoverBytes(filePath: String?): ByteArray? {
        if (filePath.isNullOrBlank()) return null
        byteCache[filePath]?.let { return it }

        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return loadNetworkCoverBytes(filePath)
        }

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(filePath)
                val data = mmr.embeddedPicture
                if (data != null && data.isNotEmpty()) {
                    byteCache.put(filePath, data)
                    data
                } else null
            } finally {
                try { mmr.release() } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract bytes failed: $filePath - ${e.message}")
            null
        } catch (e: NoClassDefFoundError) {
            null
        }
    }

    private fun loadNetworkCoverBytes(url: String): ByteArray? {
        if (hasKnownNoCover(url)) return null
        return try {
            val account = com.xiaowei.player.data.WebDavPrefs.get(
                com.xiaowei.player.ShuYinApp.instance
            ).activeAccount()
            val future = networkCoverExecutor.submit(Callable {
                val mmr = MediaMetadataRetriever()
                try {
                    val headers = account?.let { mapOf("Authorization" to it.authHeader()) } ?: emptyMap()
                    mmr.setDataSource(url, headers)
                    mmr.embeddedPicture
                } finally {
                    try { mmr.release() } catch (_: Throwable) {}
                }
            })
            try {
                val data = future.get(20, TimeUnit.SECONDS)
                if (data != null && data.isNotEmpty()) {
                    byteCache.put(url, data)
                    data
                } else {
                    markNoCover(url)
                    null
                }
            } catch (_: Throwable) {
                future.cancel(true)
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun preloadPlayingCovers(
        currentFilePath: String?,
        nextFilePath: String?
    ) = withContext(Dispatchers.IO) {
        listOfNotNull(currentFilePath, nextFilePath).forEach { path ->
            if (path.isNotBlank()) {
                try {
                    loadCoverBytes(path)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun evictOldCovers(filePaths: List<String?>) {
        for (path in filePaths) {
            if (!path.isNullOrBlank()) {
                byteCache.remove(path)
            }
        }
    }

    fun clearAll() {
        byteCache.evictAll()
        synchronized(negativeCache) { negativeCache.clear() }
    }

    fun markNoCover(filePath: String) {
        synchronized(negativeCache) {
            if (negativeCache.size >= NEGATIVE_CACHE_LIMIT) {
                negativeCache.clear()
            }
            negativeCache.add(filePath)
        }
    }

    fun hasKnownNoCover(filePath: String): Boolean =
        synchronized(negativeCache) { negativeCache.contains(filePath) }
}
