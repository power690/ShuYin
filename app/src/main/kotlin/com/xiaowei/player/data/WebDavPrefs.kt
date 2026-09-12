package com.xiaowei.player.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WebDavAccount(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val path: String
) {
    val normalizedUrl: String
        get() = WebDavPrefs.normalizeUrl(url)

    val normalizedPath: String
        get() {
            val trimmed = path.trim().trim('/')
            return if (trimmed.isEmpty()) "/" else "/$trimmed"
        }

    fun authHeader(): String {
        val token = java.util.Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }
}

class WebDavPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val accountsState = mutableStateOf(loadAccounts())

    val activeIdState = mutableStateOf(prefs.getString(KEY_ACTIVE_ID, null))

    val accounts: List<WebDavAccount>
        get() = accountsState.value

    val activeId: String?
        get() = activeIdState.value

    fun activeAccount(): WebDavAccount? {
        val id = activeIdState.value ?: return null
        return accountsState.value.firstOrNull { it.id == id }
    }

    fun isActive(accountId: String): Boolean = activeIdState.value == accountId

    fun addAccount(name: String, url: String, username: String, password: String, path: String): WebDavAccount {
        val account = WebDavAccount(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            url = normalizeUrl(url),
            username = username.trim(),
            password = password,
            path = path.trim()
        )
        val next = accountsState.value + account
        accountsState.value = next
        persistAccounts(next)
        return account
    }

    fun updateAccount(id: String, name: String, url: String, username: String, password: String, path: String) {
        val next = accountsState.value.map {
            if (it.id == id) {
                WebDavAccount(
                    id = id,
                    name = name.trim(),
                    url = normalizeUrl(url),
                    username = username.trim(),
                    password = password,
                    path = path.trim()
                )
            } else {
                it
            }
        }
        accountsState.value = next
        persistAccounts(next)
    }

    fun removeAccount(accountId: String) {
        val next = accountsState.value.filter { it.id != accountId }
        accountsState.value = next
        persistAccounts(next)
        if (activeIdState.value == accountId) {
            setActiveId(null)
        }
    }

    fun setActive(accountId: String?) {
        if (accountId != null && accountsState.value.none { it.id == accountId }) return
        setActiveId(accountId)
    }

    private fun setActiveId(accountId: String?) {
        activeIdState.value = accountId
        prefs.edit().putString(KEY_ACTIVE_ID, accountId).apply()
    }

    private fun loadAccounts(): List<WebDavAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                WebDavAccount(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    username = obj.optString("username"),
                    password = obj.optString("password"),
                    path = obj.optString("path")
                )
            }.filter { it.id.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistAccounts(accounts: List<WebDavAccount>) {
        val arr = JSONArray()
        for (account in accounts) {
            arr.put(JSONObject().apply {
                put("id", account.id)
                put("name", account.name)
                put("url", account.url)
                put("username", account.username)
                put("password", account.password)
                put("path", account.path)
            })
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "webdav_prefs"
        private const val KEY_ACCOUNTS = "webdav_accounts"
        private const val KEY_ACTIVE_ID = "webdav_active_id"

        @Volatile
        private var instance: WebDavPrefs? = null

        fun get(context: Context): WebDavPrefs {
            return instance ?: synchronized(this) {
                instance ?: WebDavPrefs(context.applicationContext).also { instance = it }
            }
        }

        fun normalizeUrl(raw: String): String {
            var u = raw.trim()
            if (u.isBlank()) return ""
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                u = "https://$u"
            }
            u = u.trimEnd('/')
            val lower = u.lowercase()
            if (lower.contains("webdav.123pan.cn")) {
                return "https://webdav.123pan.cn/webdav"
            }
            return u
        }
    }
}
