package com.xiaowei.player.data

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object EmbeddedCoverFetcher {

    private const val TAG = "EmbeddedCoverFetcher"

    private val uriCache = HashMap<String, Uri>()

    private val byteCache = object : LruCache<String, ByteArray>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private fun diskCacheDir(context: android.content.Context): File =
        File(context.cacheDir, "embedded_covers").apply { if (!exists()) mkdirs() }

    fun getCachedUriSync(filePath: String?): Uri? {
        if (filePath.isNullOrBlank()) return null
        return synchronized(uriCache) { uriCache[filePath] }
    }

    fun loadCoverUri(filePath: String?, context: android.content.Context): Uri? {
        if (filePath.isNullOrBlank()) return null

        synchronized(uriCache) { uriCache[filePath]?.let { return it } }

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return null

        val cacheKey = md5(filePath)
        val cacheFile = File(diskCacheDir(context), "$cacheKey.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            val uri = Uri.fromFile(cacheFile)
            synchronized(uriCache) { uriCache[filePath] = uri }

            if (byteCache[filePath] == null) {
                try {
                    val bytes = cacheFile.readBytes()
                    if (bytes.isNotEmpty()) byteCache.put(filePath, bytes)
                } catch (_: Exception) {}
            }
            return uri
        }

        val bytes = byteCache[filePath] ?: try {
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
            Log.w(TAG, "extract embedded cover failed: $filePath - ${e.message}")
            null
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "MediaMetadataRetriever unavailable on this device")
            null
        }

        if (bytes == null) return null

        return try {
            cacheFile.writeBytes(bytes)
            val uri = Uri.fromFile(cacheFile)
            synchronized(uriCache) { uriCache[filePath] = uri }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "write cover cache failed: ${e.message}")
            null
        }
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
        nextFilePath: String?,
        context: android.content.Context
    ) = withContext(Dispatchers.IO) {
        val tasks = listOfNotNull(currentFilePath, nextFilePath).filter { it.isNotBlank() }
        if (tasks.isEmpty()) return@withContext

        coroutineScope {
            tasks.forEach { path ->
                launch {
                    try {
                        loadCoverUri(path, context)
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
                synchronized(uriCache) { uriCache.remove(path) }
            }
        }
    }

    fun clearAll() {
        byteCache.evictAll()
        synchronized(uriCache) { uriCache.clear() }
    }

    private val negativeCache = HashSet<String>()
    fun markNoCover(filePath: String) {
        synchronized(negativeCache) { negativeCache.add(filePath) }
    }
    fun hasKnownNoCover(filePath: String): Boolean =
        synchronized(negativeCache) { negativeCache.contains(filePath) }

    private fun md5(input: String): String = try {
        val md = MessageDigest.getInstance("MD5")
        md.update(input.toByteArray(Charsets.UTF_8))
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        input.hashCode().toString(16)
    }
}
