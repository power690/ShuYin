package com.xiaowei.player.data

import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object EmbeddedCoverFetcher {

    private const val TAG = "EmbeddedCoverFetcher"

    private val byteCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun getCachedBytesSync(filePath: String?): ByteArray? {
        if (filePath.isNullOrBlank()) return null
        return byteCache[filePath]
    }

    fun loadCoverBytes(filePath: String?): ByteArray? {
        if (filePath.isNullOrBlank()) return null
        byteCache[filePath]?.let { return it }

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

    suspend fun preloadPlayingCovers(
        currentFilePath: String?,
        nextFilePath: String?
    ) = withContext(Dispatchers.IO) {
        val tasks = listOfNotNull(currentFilePath, nextFilePath).filter { it.isNotBlank() }
        if (tasks.isEmpty()) return@withContext

        coroutineScope {
            tasks.forEach { path ->
                launch {
                    try {
                        loadCoverBytes(path)
                    } catch (_: Exception) {
                    }
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
    }

    private val negativeCache = HashSet<String>()
    fun markNoCover(filePath: String) {
        synchronized(negativeCache) { negativeCache.add(filePath) }
    }
    fun hasKnownNoCover(filePath: String): Boolean =
        synchronized(negativeCache) { negativeCache.contains(filePath) }
}
