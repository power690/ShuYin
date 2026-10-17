package com.xiaowei.player.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.xiaowei.player.data.db.AppDatabase
import com.xiaowei.player.data.db.UserProfileEntity
import com.xiaowei.player.i18n.Strings
import java.util.TimeZone

const val ADVANCED_MIN_SONGS = 10

const val SUPREME_LOGIN_DAYS = 100

private const val LOGIN_TIMEOUT_MILLIS = 15000L

fun isAdvancedProfileIntact(entity: UserProfileEntity): Boolean {
    return !entity.name.isNullOrBlank() && !entity.avatarUri.isNullOrBlank()
}

fun canBecomeAdvanced(entity: UserProfileEntity): Boolean {
    return isAdvancedProfileIntact(entity) && entity.songCount >= ADVANCED_MIN_SONGS
}

fun dayIndexFromEpoch(epochMillis: Long): Long {
    val offset = TimeZone.getDefault().getOffset(epochMillis)
    return (epochMillis + offset.toLong()) / 86400000L
}

object LoginStreakTracker {

    private val loginLock = Any()

    fun recordColdStartLogin(context: Context) {
        val appContext = context.applicationContext
        Thread {
            try {
                val serverEpochMillis = ServerTimeClient.fetchEpochMillis(LOGIN_TIMEOUT_MILLIS)
                if (serverEpochMillis == null) return@Thread
                handleSuccessfulLogin(appContext, serverEpochMillis)
            } catch (_: Exception) {
            }
        }.start()
    }

    fun recordBecomeAdvancedLogin(context: Context) {
        recordColdStartLogin(context)
    }

    private fun handleSuccessfulLogin(context: Context, serverEpochMillis: Long) {
        synchronized(loginLock) {
            try {
                val dao = AppDatabase.get(context).userProfileDao()
                val entity = dao.getSync() ?: return
                if (entity.profileCompletedAt == null) {
                    if (!canBecomeAdvanced(entity)) return
                    dao.updateProfileCompletedAtSync(serverEpochMillis)
                }
                val today = dayIndexFromEpoch(serverEpochMillis)
                val last = entity.lastLoginDay
                val prevStreak = entity.loginStreak
                var streak = prevStreak
                when {
                    last == null -> streak = 1
                    today == last -> {
                    }
                    streak >= SUPREME_LOGIN_DAYS -> {
                    }
                    today - last == 1L -> streak = (streak + 1).coerceAtMost(SUPREME_LOGIN_DAYS)
                    today < last -> {
                    }
                    else -> streak = 1
                }
                dao.updateLoginStreakSync(streak)
                dao.updateLastLoginDaySync(maxOf(last ?: Long.MIN_VALUE, today))
                val isNewDayLogin = last == null || today > last
                if (isNewDayLogin && prevStreak < SUPREME_LOGIN_DAYS) {
                    notifyLogin(context, streak, 1600L)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun notifyLogin(context: Context, streak: Int, delayMillis: Long = 0L) {
        val message = if (streak >= SUPREME_LOGIN_DAYS) {
            Strings.get("login_streak_supreme")
        } else {
            Strings.get("login_streak_day", streak)
        }
        val appContext = context.applicationContext
        if (delayMillis > 0L) {
            Handler(Looper.getMainLooper()).postDelayed({
                showToast(appContext, message)
            }, delayMillis)
        } else {
            showToast(appContext, message)
        }
    }

    private fun showToast(context: Context, message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }
}
