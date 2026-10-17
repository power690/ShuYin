package com.xiaowei.player.data

import android.content.Context
import android.net.Uri
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.data.db.UserProfileEntity
import com.xiaowei.player.i18n.Strings
import java.io.File
import java.io.FileOutputStream

private const val AVATAR_DIR_NAME = "avatars"

private fun avatarDir(context: Context): File = File(context.filesDir, AVATAR_DIR_NAME)

private fun isLocalAvatarFile(context: Context, file: File): Boolean {
    val dir = avatarDir(context)
    return runCatching {
        val parent = file.parentFile
        parent != null && parent.canonicalPath == dir.canonicalPath
    }.getOrDefault(false)
}

private fun avatarExtension(context: Context, uri: Uri): String {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val fixed = when (mime) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/bmp" -> ".bmp"
        "image/heic", "image/heif" -> ".heic"
        else -> null
    }
    if (fixed != null) return fixed
    val path = uri.path
    if (path != null) {
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        if (dot > slash) {
            val ext = path.substring(dot).lowercase()
            if (ext.length in 2..5) return ext
        }
    }
    return ".jpg"
}

private fun persistAvatar(context: Context, uri: Uri?): Uri? {
    if (uri == null) return null
    val scheme = uri.scheme ?: return uri
    if (scheme != "content" && scheme != "file") return uri
    if (scheme == "file") {
        val path = uri.path
        if (path != null) {
            val file = File(path)
            if (file.isFile && isLocalAvatarFile(context, file)) return uri
        }
    }
    return try {
        val dir = avatarDir(context)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "avatar_" + System.currentTimeMillis() + avatarExtension(context, uri))
        val input = context.contentResolver.openInputStream(uri) ?: return uri
        input.use { src ->
            FileOutputStream(target).use { dst ->
                src.copyTo(dst)
            }
        }
        if (target.length() == 0L) {
            target.delete()
            uri
        } else {
            Uri.fromFile(target)
        }
    } catch (e: Exception) {
        uri
    }
}

private fun resolveAvatar(context: Context, uri: Uri?): Uri? {
    if (uri == null) return null
    val scheme = uri.scheme ?: return uri
    if (scheme != "file") return uri
    val path = uri.path ?: return null
    val file = File(path)
    if (file.isFile) return uri
    val name = file.name
    if (name.isBlank()) return null
    val fallback = File(avatarDir(context), name)
    return if (fallback.isFile) Uri.fromFile(fallback) else null
}

private fun deleteLocalAvatar(context: Context, uri: Uri?) {
    if (uri == null) return
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    val file = File(path)
    val dir = avatarDir(context)
    val inside = runCatching { file.canonicalPath.startsWith(dir.canonicalPath + File.separator) }.getOrDefault(false)
    if (inside) file.delete()
}

class UserProfileRepository(private val context: Context, private val db: AppDatabase) {

    private val dao get() = db.userProfileDao()

    enum class UserLevel { NORMAL, ADVANCED, SUPREME }

    data class UserProfile(
        val name: String,
        val avatarUri: Uri?,
        val level: UserLevel,
        val loginStreak: Int,
        val hasCustomName: Boolean = false
    )

    private fun resolveLevel(entity: UserProfileEntity?): UserLevel {
        if (entity == null) return UserLevel.NORMAL
        if (entity.profileCompletedAt == null) {
            if (canBecomeAdvanced(entity)) {
                dao.updateProfileCompletedAtSync(System.currentTimeMillis())
                com.xiaowei.player.ShuYinApp.instance.loginPlaybackGate.onEligibilityChanged()
                return UserLevel.ADVANCED
            }
            return UserLevel.NORMAL
        }
        return if (entity.loginStreak >= SUPREME_LOGIN_DAYS) UserLevel.SUPREME else UserLevel.ADVANCED
    }

    fun getSync(): UserProfile {
        val entity = dao.getSync()
        val level = resolveLevel(entity)
        val name = entity?.name?.takeIf { it.isNotBlank() } ?: Strings.get("mine_user")
        val avatar = entity?.avatarUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { resolveAvatar(context, it) }
        return UserProfile(name = name, avatarUri = avatar, level = level, loginStreak = entity?.loginStreak ?: 0, hasCustomName = !entity?.name.isNullOrBlank())
    }

    suspend fun get(): UserProfile {
        val entity = dao.get()
        val level = resolveLevel(entity)
        val name = entity?.name?.takeIf { it.isNotBlank() } ?: Strings.get("mine_user")
        val avatar = entity?.avatarUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { resolveAvatar(context, it) }
        return UserProfile(name = name, avatarUri = avatar, level = level, loginStreak = entity?.loginStreak ?: 0, hasCustomName = !entity?.name.isNullOrBlank())
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
        val entity = dao.getSync()
        val old = entity?.avatarUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val persisted = persistAvatar(context, uri)
        val uriStr = persisted?.toString()
        if (entity == null) {
            dao.upsertSync(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
        } else {
            dao.updateAvatarUriSync(uriStr)
        }
        if (persisted != old) deleteLocalAvatar(context, old)
    }

    suspend fun saveAvatarUri(uri: Uri?) {
        val entity = dao.get()
        val old = entity?.avatarUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val persisted = persistAvatar(context, uri)
        val uriStr = persisted?.toString()
        if (entity == null) {
            dao.upsert(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
        } else {
            dao.updateAvatarUri(uriStr)
        }
        if (persisted != old) deleteLocalAvatar(context, old)
    }
}

fun readUserName(context: Context): String {
    val db = AppDatabase.get(context)
    val entity = db.userProfileDao().getSync()
    val saved = entity?.name
    return if (saved.isNullOrBlank()) Strings.get("mine_user") else saved
}

fun updateSongCountSnapshot(context: Context, count: Int) {
    val dao = AppDatabase.get(context).userProfileDao()
    val entity = dao.getSync()
    if (entity == null) {
        if (count > 0) {
            dao.upsertSync(UserProfileEntity(id = 0, name = "", songCount = count))
        }
        return
    }
    if (entity.songCount != count) {
        dao.updateSongCountSync(count)
    }
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
    val uri = runCatching { Uri.parse(s) }.getOrNull() ?: return null
    return resolveAvatar(context, uri)
}

fun writeAvatarUri(context: Context, uri: Uri?) {
    val db = AppDatabase.get(context)
    val dao = db.userProfileDao()
    val old = dao.getSync()?.avatarUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
    val persisted = persistAvatar(context, uri)
    val uriStr = persisted?.toString()
    if (dao.getSync() == null) {
        dao.upsertSync(UserProfileEntity(id = 0, name = "", avatarUri = uriStr))
    } else {
        dao.updateAvatarUriSync(uriStr)
    }
    if (persisted != old) deleteLocalAvatar(context, old)
}
