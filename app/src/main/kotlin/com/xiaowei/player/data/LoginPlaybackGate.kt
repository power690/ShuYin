package com.xiaowei.player.data

import android.content.Context
import com.xiaowei.player.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginPlaybackGate(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val requiredPlayMillis = 120_000L

    private var isForeground = false
    private var isActivePlayback = false
    private var isEligible = false
    private var triggered = false
    private var elapsedMillis = 0L
    private var timerJob: Job? = null
    private val gateLock = Any()

    private val _countdownMillis = MutableStateFlow(0L)
    val countdownMillis: StateFlow<Long> = _countdownMillis.asStateFlow()

    fun onForegroundStateChanged(foreground: Boolean) {
        synchronized(gateLock) {
            if (isForeground == foreground) return
            isForeground = foreground
            if (foreground) isEligible = checkEligible()
            refreshTimerLocked()
        }
    }

    fun onPlaybackStateChanged(active: Boolean) {
        synchronized(gateLock) {
            if (isActivePlayback == active) return
            isActivePlayback = active
            if (active) isEligible = checkEligible()
            refreshTimerLocked()
        }
    }

    fun onEligibilityChanged() {
        synchronized(gateLock) {
            isEligible = checkEligible()
            refreshTimerLocked()
        }
    }

    private fun checkEligible(): Boolean {
        return try {
            val entity = AppDatabase.get(context).userProfileDao().getSync() ?: return false
            val today = dayIndexFromEpoch(System.currentTimeMillis())
            val signedToday = entity.lastLoginDay != null && entity.lastLoginDay >= today
            if (signedToday) return false
            if (entity.profileCompletedAt != null) {
                true
            } else {
                canBecomeAdvanced(entity)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun refreshTimerLocked() {
        val shouldRun = isForeground && isActivePlayback && isEligible && !triggered
        if (shouldRun) {
            if (timerJob == null) {
                startTimerLocked()
            }
        } else {
            stopTimerLocked()
        }
    }

    private fun startTimerLocked() {
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val finished = synchronized(gateLock) {
                    elapsedMillis += 1000L
                    val remaining = requiredPlayMillis - elapsedMillis
                    if (remaining <= 0L) {
                        _countdownMillis.value = -1L
                        triggered = true
                        timerJob = null
                        true
                    } else {
                        _countdownMillis.value = remaining
                        false
                    }
                }
                if (finished) {
                    LoginStreakTracker.recordColdStartLogin(context)
                    scope.launch {
                        delay(3000L)
                        synchronized(gateLock) {
                            if (_countdownMillis.value == -1L) {
                                _countdownMillis.value = 0L
                            }
                        }
                    }
                    break
                }
            }
        }
    }

    private fun stopTimerLocked() {
        timerJob?.cancel()
        timerJob = null
        elapsedMillis = 0L
        if (!triggered) {
            _countdownMillis.value = 0L
        }
    }
}
