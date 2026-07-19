package com.xiaowei.player.data

import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.data.db.PlaybackStateEntity
import org.json.JSONArray

class PlaybackPrefs(private val db: AppDatabase) {

    private val dao get() = db.playbackStateDao()

    data class SavedState(
        val songIds: List<Long>,
        val currentIndex: Int,
        val positionMs: Long,
        val playModeName: String
    )

    suspend fun save(state: SavedState) {
        dao.upsert(
            PlaybackStateEntity(
                id = 0,
                songIdsJson = longListToJson(state.songIds),
                currentIndex = state.currentIndex,
                positionMs = state.positionMs,
                playModeName = state.playModeName
            )
        )
    }

    fun saveSync(state: SavedState) {
        dao.upsertSync(
            PlaybackStateEntity(
                id = 0,
                songIdsJson = longListToJson(state.songIds),
                currentIndex = state.currentIndex,
                positionMs = state.positionMs,
                playModeName = state.playModeName
            )
        )
    }

    suspend fun load(): SavedState? {
        val entity = dao.get() ?: return null
        val ids = jsonToLongList(entity.songIdsJson)
        if (ids.isEmpty()) return null
        return SavedState(
            songIds = ids,
            currentIndex = entity.currentIndex,
            positionMs = entity.positionMs,
            playModeName = entity.playModeName
        )
    }

    fun loadSync(): SavedState? {
        val entity = dao.getSync() ?: return null
        val ids = jsonToLongList(entity.songIdsJson)
        if (ids.isEmpty()) return null
        return SavedState(
            songIds = ids,
            currentIndex = entity.currentIndex,
            positionMs = entity.positionMs,
            playModeName = entity.playModeName
        )
    }

    suspend fun clear() = dao.clear()

    fun clearSync() = dao.clearSync()

    private fun longListToJson(list: List<Long>): String {
        val arr = JSONArray()
        for (id in list) arr.put(id)
        return arr.toString()
    }

    private fun jsonToLongList(json: String): List<Long> {
        return try {
            val arr = JSONArray(json)
            val result = ArrayList<Long>(arr.length())
            for (i in 0 until arr.length()) {
                result.add(arr.getLong(i))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
