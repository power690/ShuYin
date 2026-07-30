package com.xiaowei.player.data

import android.content.Context
import android.net.Uri
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.data.db.UserProfileEntity
import com.xiaowei.player.i18n.Strings

class UserProfileRepository(private val db: AppDatabase) {

    private val dao get() = db.userProfileDao()

    data class UserProfile(
        val name: String,
        val avatarUri: Uri?
    )

    fun getSync(): UserProfile {
        val entity = dao.getSync()
        val name = entity?.name?.takeIf { it.isNotBlank() } ?: Strings.get("mine_user")
        val avatar = entity?.avatarUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return UserProfile(name = name, avatarUri = avatar)
    }

    suspend fun get(): UserProfile {
        val entity = dao.get()
        val name = entity?.name?.takeIf { it.isNotBlank() } ?: Strings.get("mine_user")
        val avatar = entity?.avatarUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return UserProfile(name = name, avatarUri = avatar)
    }

    fun saveNameSync(name: String) {
        val trimmed = name.trim()

        if (dao.getSync() == null) {
            dao.upsertSync(UserProfileEntity(id = 0, name = trimmed, avatarUri = null))
        } else {
            dao.updateNameSync(trimmed)
        }
    }

    suspend fun saveName(name: String) {
        val trimmed = name.trim()
        if (dao.get() == null) {
            dao.upsert(UserProfileEntity(id = 0, name = trimmed, avatarUri = null))
        } else {
            dao.updateName(trimmed)
        }
    }

    fun saveAvatarUriSync(uri: Uri?) {
        val uriStr = uri?.toString()
        if (dao.getSync() == null) {
            dao.upsertSync(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
        } else {
            dao.updateAvatarUriSync(uriStr)
        }
    }

    suspend fun saveAvatarUri(uri: Uri?) {
        val uriStr = uri?.toString()
        if (dao.get() == null) {
            dao.upsert(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
        } else {
            dao.updateAvatarUri(uriStr)
        }
    }
}

fun readUserName(context: Context): String {
    val db = AppDatabase.get(context)
    val entity = db.userProfileDao().getSync()
    val saved = entity?.name
    return if (saved.isNullOrBlank()) Strings.get("mine_user") else saved
}

fun writeUserName(context: Context, name: String) {
    val db = AppDatabase.get(context)
    val trimmed = name.trim()
    val dao = db.userProfileDao()
    if (dao.getSync() == null) {
        dao.upsertSync(UserProfileEntity(id = 0, name = trimmed, avatarUri = null))
    } else {
        dao.updateNameSync(trimmed)
    }
}

fun readAvatarUri(context: Context): Uri? {
    val db = AppDatabase.get(context)
    val s = db.userProfileDao().getSync()?.avatarUri ?: return null
    return runCatching { Uri.parse(s) }.getOrNull()
}

fun writeAvatarUri(context: Context, uri: Uri?) {
    val db = AppDatabase.get(context)
    val dao = db.userProfileDao()
    val uriStr = uri?.toString()
    if (dao.getSync() == null) {
        dao.upsertSync(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
    } else {
        dao.updateAvatarUriSync(uriStr)
    }
}
