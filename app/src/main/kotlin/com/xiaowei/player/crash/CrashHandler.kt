package com.xiaowei.player.crash

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.xiaowei.player.BuildConfig
import com.xiaowei.player.ShuYinApp
import com.xiaowei.player.i18n.Strings
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object CrashHandler {

    private const val TAG = "ShuYinCrash"
    private const val COLLECTOR_VERSION = "2.0.0"
    private const val SCHEMA_VERSION = 2
    private const val MAX_STACK_DEPTH_PER_THREAD = 128
    private const val MAX_ALL_THREADS = 512
    private const val DESENSITIZE_RULE_VERSION = "1.0"
    private const val CRASH_NOTICE_DELAY_MS = 3000L

    @Volatile private var installed = false
    private val crashing = AtomicBoolean(false)
    private val crashCount = AtomicLong(0L)
    private val captureStartMillis = AtomicLong(0L)

    private val SENSITIVE_KEY_KEYWORDS = listOf(
        "password", "passwd", "pwd", "token", "secret", "auth", "cookie",
        "session", "credential", "apikey", "api_key", "bearer"
    )

    fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (crashing.compareAndSet(false, true)) {
                try {
                    captureStartMillis.set(System.currentTimeMillis())
                    saveCrashLog(context.applicationContext, throwable, thread)
                } catch (_: Throwable) {
                }
                showCrashNoticeAndExit(context.applicationContext, thread, throwable, previous)
            } else {
                try {
                    previous?.uncaughtException(thread, throwable)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun showCrashNoticeAndExit(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        previous: Thread.UncaughtExceptionHandler?
    ) {
        val exit = Runnable {
            try {
                previous?.uncaughtException(thread, throwable)
            } catch (_: Throwable) {
            }
            try {
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (_: Throwable) {
            }
        }
        val notice = Runnable {
            val message = try {
                Strings.get("crash_toast_message")
            } catch (_: Throwable) {
                "鼠鼠崩溃请联系开发者"
            }
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
            }
        }
        try {
            val mainLooper = Looper.getMainLooper()
            if (mainLooper != null && mainLooper.thread !== thread && mainLooper.thread.isAlive) {
                val handler = Handler(mainLooper)
                handler.post(notice)
                handler.postDelayed(exit, CRASH_NOTICE_DELAY_MS)
                return
            }
        } catch (_: Throwable) {
        }
        try {
            val notifier = HandlerThread("ShuYinCrashNotice")
            notifier.start()
            val handler = Handler(notifier.looper)
            handler.post(notice)
            handler.postDelayed(exit, CRASH_NOTICE_DELAY_MS)
        } catch (_: Throwable) {
            exit.run()
        }
    }

    fun recordUserAction(action: String) {
        try {
            if (ShuYinApp.isInitialized()) {
                ShuYinApp.instance.lastUserAction = action
                ShuYinApp.instance.operationSequence = ShuYinApp.instance.operationSequence + 1L
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveCrashLog(context: Context, throwable: Throwable, crashThread: Thread) {
        val captureThread = Thread.currentThread()
        val now = System.currentTimeMillis()
        val startMs = if (captureStartMillis.get() == 0L) now else captureStartMillis.get()
        val timeStr = formatTime(startMs)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date(now))
        val fileName = "crash_$stamp.txt"
        val crashNumber = crashCount.incrementAndGet()

        val sb = StringBuilder(65536)

        sb.appendLine("==== ShuYin Crash Report ====")
        sb.appendLine("schemaVersion=$SCHEMA_VERSION")
        sb.appendLine("collectorVersion=$COLLECTOR_VERSION")
        sb.appendLine("sampleType=realtime")
        sb.appendLine("sampleVersion=1.0")
        sb.appendLine("generatedAt=$timeStr")
        sb.appendLine()

        sb.appendLine("==== Capture Metadata ====")
        sb.appendLine("captureStartTime=$timeStr")
        sb.appendLine("captureStage=uncaughtException")
        sb.appendLine("logLevel=ERROR")
        sb.appendLine("logTruncated=false")
        sb.appendLine("logSizeBytes=__PENDING__")
        sb.appendLine("captureEndTime=__PENDING__")
        sb.appendLine("captureDurationMs=__PENDING__")
        sb.appendLine()

        sb.appendLine("==== Crash Thread ====")
        sb.appendLine("crashThreadName=${crashThread.name}")
        sb.appendLine("crashThreadId=${crashThread.id}")
        sb.appendLine("crashThreadState=${safeState(crashThread)}")
        sb.appendLine("crashThreadPriority=${crashThread.priority}")
        sb.appendLine("crashThreadGroup=${safeGroupName(crashThread)}")
        sb.appendLine("isCrashThreadCapturing=${crashThread === captureThread}")
        sb.appendLine()

        sb.appendLine("==== Capture Thread ====")
        sb.appendLine("captureThreadName=${captureThread.name}")
        sb.appendLine("captureThreadId=${captureThread.id}")
        sb.appendLine("captureThreadState=${safeState(captureThread)}")
        sb.appendLine("captureThreadPriority=${captureThread.priority}")
        sb.appendLine("captureThreadGroup=${safeGroupName(captureThread)}")
        sb.appendLine()

        sb.appendLine("==== Device Info ====")
        for ((k, v) in collectDeviceInfo(context)) sb.appendLine("$k=$v")
        sb.appendLine()

        sb.appendLine("==== App Info ====")
        for ((k, v) in collectAppInfo(context)) sb.appendLine("$k=$v")
        sb.appendLine()

        sb.appendLine("==== Process Info ====")
        for ((k, v) in collectProcessInfo(context)) sb.appendLine("$k=$v")
        sb.appendLine()

        sb.appendLine("==== Install Info ====")
        for ((k, v) in collectInstallInfo(context)) sb.appendLine("$k=$v")
        sb.appendLine()

        sb.appendLine("==== Runtime State ====")
        for ((k, v) in collectRuntimeState()) sb.appendLine("$k=$v")
        sb.appendLine()

        sb.appendLine("==== Upload ====")
        sb.appendLine("uploadStatus=not_attempted")
        sb.appendLine("retryCount=0")
        sb.appendLine("lastUploadError=")
        sb.appendLine()

        sb.appendLine("==== Desensitize ====")
        sb.appendLine("isDesensitized=true")
        sb.appendLine("desensitizeRuleVersion=$DESENSITIZE_RULE_VERSION")
        sb.appendLine()

        sb.appendLine("==== Local File ====")
        sb.appendLine("localFilePath=__PENDING__")
        sb.appendLine("localLocationFile=${fileName}.location.txt")
        sb.appendLine("fileHash=__PENDING__")
        sb.appendLine()

        sb.appendLine("==== Crash Signature ====")
        sb.appendLine("crashSignature=${digest(throwable.javaClass.name + ":" + (throwable.message ?: ""))}")
        sb.appendLine("previousCrashInSession=${crashNumber - 1}")
        sb.appendLine()

        sb.appendLine("==== Crash Thread Stack ====")
        writeThrowableFull(sb, throwable)
        sb.appendLine()

        sb.appendLine("==== Capture Thread Stack ====")
        writeStackTrace(sb, captureThread.stackTrace, "    ")
        sb.appendLine()

        val allThreads = collectAllThreads(crashThread, captureThread)
        sb.appendLine("==== All Threads ====")
        sb.appendLine("threadStackTotal=${allThreads.total}")
        sb.appendLine("threadStackSuccess=${allThreads.success}")
        sb.appendLine("threadStackUnavailable=${allThreads.unavailable}")
        sb.appendLine("threadStackTruncated=${allThreads.truncated}")
        sb.appendLine()
        sb.append(allThreads.text)
        sb.appendLine()

        sb.appendLine("==== SharedPreferences Snapshot ====")
        sb.append(collectSharedPrefs(context))
        sb.appendLine()

        sb.appendLine("==== Logcat Snapshot ====")
        sb.appendLine("logcatStatus=unavailable")
        sb.appendLine("logcatReason=permission_denied_READ_LOGS")
        sb.appendLine()

        sb.appendLine("==== System Properties (Selected) ====")
        for ((k, v) in collectSystemProperties()) sb.appendLine("$k=$v")
        sb.appendLine()

        var body = sb.toString()
        val captureEnd = System.currentTimeMillis()
        val finalHash = digest(body)
        body = body.replace("fileHash=__PENDING__", "fileHash=$finalHash")
        body = body.replace("captureEndTime=__PENDING__", "captureEndTime=${formatTime(captureEnd)}")
        body = body.replace("captureDurationMs=__PENDING__", "captureDurationMs=${captureEnd - startMs}")
        body = body.replace("logSizeBytes=__PENDING__", "logSizeBytes=${body.toByteArray(Charsets.UTF_8).size}")

        val bytes = body.toByteArray(Charsets.UTF_8)
        val written = writeLog(context, fileName, bytes)
        body = body.replace("localFilePath=__PENDING__", "localFilePath=" + (written ?: "WRITE_FAILED"))
        val finalBytes = body.toByteArray(Charsets.UTF_8)
        writeLog(context, fileName, finalBytes)
        writeSidecar(context, fileName, written)
        Log.e(TAG, "Crash log written to: ${written ?: "FAILED"}")
    }

    private fun writeSidecar(context: Context, fileName: String, writtenPath: String?) {
        try {
            val sidecarName = "$fileName.location.txt"
            val content = "fileName=$fileName\nwrittenPath=${writtenPath ?: "WRITE_FAILED"}\n"
            writeLog(context, sidecarName, content.toByteArray(Charsets.UTF_8))
        } catch (_: Throwable) {
        }
    }

    private fun writeLog(context: Context, fileName: String, bytes: ByteArray): String? {
        val direct = tryWriteDirect(fileName, bytes)
        if (direct != null) return direct
        val ms = tryWriteMediaStore(context, fileName, bytes)
        if (ms != null) return ms
        val ext = tryWriteAppExternal(context, fileName, bytes)
        if (ext != null) return ext
        return tryWriteInternal(context, fileName, bytes)
    }

    private fun tryWriteDirect(fileName: String, bytes: ByteArray): String? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val dir = File(downloads, "ShuYinCrash")
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created && !dir.exists()) return null
            }
            if (!dir.isDirectory || !dir.canWrite()) return null
            val target = File(dir, fileName)
            atomicWrite(target, bytes)
            target.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWriteMediaStore(context: Context, fileName: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ShuYinCrash")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
                uri.toString()
            } catch (e: Throwable) {
                try { resolver.delete(uri, null, null) } catch (_: Throwable) {}
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWriteAppExternal(context: Context, fileName: String, bytes: ByteArray): String? {
        return try {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            val dir = File(base, "ShuYinCrash")
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created && !dir.exists()) return null
            }
            val target = File(dir, fileName)
            atomicWrite(target, bytes)
            target.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWriteInternal(context: Context, fileName: String, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "crashes")
            if (!dir.exists()) {
                val created = dir.mkdirs()
                if (!created && !dir.exists()) return null
            }
            val target = File(dir, fileName)
            atomicWrite(target, bytes)
            target.absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            try {
                target.writeBytes(bytes)
                tmp.delete()
            } catch (e: Throwable) {
                try { tmp.delete() } catch (_: Throwable) {}
                throw e
            }
        }
    }

    private fun collectDeviceInfo(context: Context): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        list.add("manufacturer" to Build.MANUFACTURER)
        list.add("brand" to Build.BRAND)
        list.add("model" to Build.MODEL)
        list.add("device" to Build.DEVICE)
        list.add("product" to Build.PRODUCT)
        list.add("hardware" to Build.HARDWARE)
        list.add("board" to Build.BOARD)
        list.add("androidRelease" to Build.VERSION.RELEASE)
        list.add("sdkInt" to Build.VERSION.SDK_INT.toString())
        list.add("fingerprint" to Build.FINGERPRINT)
        list.add("supportedAbis" to safeJoin(Build.SUPPORTED_ABIS))
        list.add("supported32BitAbis" to safeJoin(Build.SUPPORTED_32_BIT_ABIS))
        list.add("supported64BitAbis" to safeJoin(Build.SUPPORTED_64_BIT_ABIS))
        val pageSize = readPageSize()
        list.add("devicePageSize" to pageSize.toString())
        list.add("is16KBPageSize" to (pageSize >= 16384L).toString())
        list.add("locale" to Locale.getDefault().toString())
        list.add("timezone" to TimeZone.getDefault().id)
        try {
            val dm = context.resources.displayMetrics
            list.add("screenDensityDpi" to dm.densityDpi.toString())
            list.add("screenWidthPx" to dm.widthPixels.toString())
            list.add("screenHeightPx" to dm.heightPixels.toString())
        } catch (_: Throwable) {
            list.add("screenDensityDpi" to "unknown")
            list.add("screenWidthPx" to "unknown")
            list.add("screenHeightPx" to "unknown")
        }
        try {
            list.add("osArch" to (System.getProperty("os.arch") ?: "unknown"))
            list.add("javaVmVersion" to (System.getProperty("java.vm.version") ?: "unknown"))
        } catch (_: Throwable) {
            list.add("osArch" to "unknown")
            list.add("javaVmVersion" to "unknown")
        }
        return list
    }

    private fun collectAppInfo(context: Context): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        list.add("packageName" to context.packageName)
        list.add("versionName" to BuildConfig.VERSION_NAME)
        list.add("versionCode" to BuildConfig.VERSION_CODE.toString())
        val ai = context.applicationInfo
        list.add("debuggable" to ((ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0).toString())
        list.add("dataDir" to ai.dataDir)
        list.add("sourceDir" to (ai.sourceDir ?: "unknown"))
        list.add("publicSourceDir" to (ai.publicSourceDir ?: "unknown"))
        return list
    }

    private fun collectProcessInfo(context: Context): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        list.add("pid" to android.os.Process.myPid().toString())
        list.add("tid" to android.os.Process.myTid().toString())
        list.add("uid" to android.os.Process.myUid().toString())
        val procName = readProcessName()
        list.add("processName" to procName)
        val mainProcessName = context.packageName
        list.add("mainProcessName" to mainProcessName)
        val isMain = procName == mainProcessName
        list.add("isMainProcess" to isMain.toString())
        list.add("multiProcessRole" to if (isMain) "main" else "secondary")
        return list
    }

    private fun readProcessName(): String {
        return try {
            val f = File("/proc/self/cmdline")
            if (!f.exists()) return "unknown"
            val raw = f.readText().trim { it <= ' ' }
            if (raw.isEmpty()) "unknown" else raw
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun collectInstallInfo(context: Context): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        val ai = context.applicationInfo
        list.add("nativeLibraryDir" to (ai.nativeLibraryDir ?: "unknown"))
        list.add("splitApks" to safeJoin(ai.splitSourceDirs ?: ai.splitPublicSourceDirs))
        list.add("abiFilters" to safeJoin(Build.SUPPORTED_ABIS))
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(context.packageName)
                val installer = info.installingPackageName ?: "unknown"
                list.add("installSource" to installer)
                val initiator = info.initiatingPackageName ?: "unknown"
                list.add("installInitiator" to initiator)
                val origin = info.originatingPackageName ?: "unknown"
                list.add("installOrigin" to origin)
            } else {
                @Suppress("DEPRECATION")
                val installer = pm.getInstallerPackageName(context.packageName) ?: "unknown"
                list.add("installSource" to installer)
                list.add("installInitiator" to "unknown")
                list.add("installOrigin" to "unknown")
            }
        } catch (_: Throwable) {
            list.add("installSource" to "unknown")
            list.add("installInitiator" to "unknown")
            list.add("installOrigin" to "unknown")
        }
        try {
            list.add("isExternalStorageManager" to Environment.isExternalStorageManager().toString())
        } catch (_: Throwable) {
            list.add("isExternalStorageManager" to "unknown")
        }
        return list
    }

    private fun collectRuntimeState(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        try {
            val rt = Runtime.getRuntime()
            list.add("memoryMax" to rt.maxMemory().toString())
            list.add("memoryTotal" to rt.totalMemory().toString())
            list.add("memoryFree" to rt.freeMemory().toString())
            list.add("memoryUsed" to (rt.totalMemory() - rt.freeMemory()).toString())
        } catch (_: Throwable) {
            list.add("memoryMax" to "unknown")
            list.add("memoryTotal" to "unknown")
            list.add("memoryFree" to "unknown")
            list.add("memoryUsed" to "unknown")
        }
        try {
            if (ShuYinApp.isInitialized()) {
                val app = ShuYinApp.instance
                list.add("appForeground" to app.appForeground.toString())
                list.add("currentPage" to (app.currentPage.ifEmpty { "unknown" }))
                list.add("playerState" to readPlayerState(app))
                list.add("lastUserAction" to (app.lastUserAction.ifEmpty { "none" }))
                list.add("operationSequence" to app.operationSequence.toString())
            } else {
                list.add("appForeground" to "unknown")
                list.add("currentPage" to "unknown")
                list.add("playerState" to "unknown")
                list.add("lastUserAction" to "none")
                list.add("operationSequence" to "0")
            }
        } catch (_: Throwable) {
            list.add("appForeground" to "unknown")
            list.add("currentPage" to "unknown")
            list.add("playerState" to "unknown")
            list.add("lastUserAction" to "none")
            list.add("operationSequence" to "0")
        }
        try {
            list.add("uptimeMs" to SystemClock.elapsedRealtime().toString())
        } catch (_: Throwable) {
            list.add("uptimeMs" to "unknown")
        }
        return list
    }

    private fun readPlayerState(app: ShuYinApp): String {
        return try {
            val st = app.playerManager.state.value
            val song = st.currentSong
            if (song == null) {
                "idle"
            } else {
                "playing=${st.isPlaying} buffering=${st.isBuffering} positionMs=${st.positionMs} durationMs=${st.durationMs} songId=${song.id} songTitle=${song.title}"
            }
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private class AllThreadsResult(
        val total: Int,
        val success: Int,
        val unavailable: Int,
        val truncated: Int,
        val text: String
    )

    private fun collectAllThreads(crashThread: Thread, captureThread: Thread): AllThreadsResult {
        val sb = StringBuilder()
        var total = 0
        var success = 0
        var unavailable = 0
        var truncated = 0
        try {
            val all = Thread.getAllStackTraces()
            if (all == null) {
                sb.appendLine("allThreadsStatus=unavailable")
                sb.appendLine("allThreadsReason=null_map")
                return AllThreadsResult(0, 0, 0, 0, sb.toString())
            }
            val sorted = all.entries.sortedBy { it.key.name }
            if (sorted.size > MAX_ALL_THREADS) {
                sb.appendLine("allThreadsLimited=true")
                sb.appendLine("allThreadsOmitted=${sorted.size - MAX_ALL_THREADS}")
            }
            val limited = if (sorted.size > MAX_ALL_THREADS) sorted.subList(0, MAX_ALL_THREADS) else sorted
            for (entry in limited) {
                val t = entry.key
                val stack = entry.value
                total++
                val isCrash = (t === crashThread)
                val isCapture = (t === captureThread)
                val tag = when {
                    isCrash -> " [CRASH_THREAD]"
                    isCapture -> " [CAPTURE_THREAD]"
                    else -> ""
                }
                sb.appendLine("Thread: ${t.name} (id=${t.id} state=${safeState(t)} priority=${t.priority} group=${safeGroupName(t)} daemon=${t.isDaemon} alive=${t.isAlive})$tag")
                if (stack == null || stack.isEmpty()) {
                    unavailable++
                    sb.appendLine("    stackStatus=UNAVAILABLE")
                    sb.appendLine("    stackReason=${inferStackUnavailableReason(safeState(t))}")
                    sb.appendLine()
                    continue
                }
                success++
                val depth = stack.size
                val actual = if (depth > MAX_STACK_DEPTH_PER_THREAD) MAX_STACK_DEPTH_PER_THREAD else depth
                if (depth > actual) truncated++
                sb.appendLine("    stackStatus=OK")
                sb.appendLine("    stackDepth=$depth")
                for (i in 0 until actual) {
                    sb.appendLine("    at ${stack[i]}")
                }
                if (depth > actual) {
                    sb.appendLine("    ... truncated ${depth - actual} frames")
                }
                sb.appendLine()
            }
        } catch (e: Throwable) {
            sb.appendLine("allThreadsStatus=error")
            sb.appendLine("allThreadsError=${e.javaClass.name}: ${e.message}")
        }
        return AllThreadsResult(total, success, unavailable, truncated, sb.toString())
    }

    private fun inferStackUnavailableReason(state: String): String {
        return when (state) {
            "RUNNABLE" -> "native_or_vm_internal"
            "WAITING" -> "waiting_without_java_frames"
            "TIMED_WAITING" -> "timed_waiting_without_java_frames"
            "BLOCKED" -> "blocked_without_java_frames"
            "NEW" -> "not_started"
            "TERMINATED" -> "terminated"
            else -> "unknown"
        }
    }

    private fun writeThrowableFull(sb: StringBuilder, throwable: Throwable) {
        sb.appendLine("class=${throwable.javaClass.name}")
        sb.appendLine("message=${throwable.message ?: ""}")
        sb.appendLine("stackTrace:")
        writeStackTrace(sb, throwable.stackTrace, "    ")
        val topSuppressed = throwable.suppressed
        if (topSuppressed != null && topSuppressed.isNotEmpty()) {
            sb.appendLine("suppressedCount=${topSuppressed.size}")
            topSuppressed.forEachIndexed { idx, s ->
                sb.appendLine("---- Suppressed[$idx] ----")
                sb.appendLine("class=${s.javaClass.name}")
                sb.appendLine("message=${s.message ?: ""}")
                writeStackTrace(sb, s.stackTrace, "    ")
            }
        }
        var cause: Throwable? = throwable.cause
        var depth = 1
        var guard = 0
        while (cause != null && guard < 30) {
            sb.appendLine()
            sb.appendLine("==== Cause[$depth] ====")
            sb.appendLine("class=${cause.javaClass.name}")
            sb.appendLine("message=${cause.message ?: ""}")
            writeStackTrace(sb, cause.stackTrace, "    ")
            val sup = cause.suppressed
            if (sup != null && sup.isNotEmpty()) {
                sb.appendLine("suppressedCount=${sup.size}")
                sup.forEachIndexed { idx, s ->
                    sb.appendLine("---- Suppressed[$idx] ----")
                    sb.appendLine("class=${s.javaClass.name}")
                    sb.appendLine("message=${s.message ?: ""}")
                    writeStackTrace(sb, s.stackTrace, "    ")
                }
            }
            cause = cause.cause
            depth++
            guard++
        }
    }

    private fun writeStackTrace(sb: StringBuilder, stack: Array<StackTraceElement>?, prefix: String = "    ") {
        if (stack == null || stack.isEmpty()) {
            sb.appendLine("${prefix}stackStatus=UNAVAILABLE")
            return
        }
        for (el in stack) {
            sb.appendLine("$prefix at $el")
        }
    }

    private fun collectSharedPrefs(context: Context): String {
        val sb = StringBuilder()
        try {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (!prefsDir.isDirectory) {
                sb.appendLine("sharedPrefsStatus=empty")
                return sb.toString()
            }
            val files = prefsDir.listFiles() ?: emptyArray()
            sb.appendLine("sharedPrefsFileCount=${files.size}")
            for (f in files) {
                if (!f.name.endsWith(".xml")) continue
                val name = f.name.removeSuffix(".xml")
                sb.appendLine("---- prefs: $name ----")
                try {
                    val text = f.readText()
                    sb.append(parseXmlPrefs(text))
                } catch (e: Throwable) {
                    sb.appendLine("prefsError=${e.javaClass.simpleName}")
                }
            }
        } catch (e: Throwable) {
            sb.appendLine("sharedPrefsStatus=error")
            sb.appendLine("sharedPrefsError=${e.javaClass.simpleName}")
        }
        return sb.toString()
    }

    private fun parseXmlPrefs(xml: String): String {
        val sb = StringBuilder()
        val patterns = listOf(
            Regex("""<string name="([^"]+)">([^<]*)</string>"""),
            Regex("""<boolean name="([^"]+)" value="([^"]*)" ?/>"""),
            Regex("""<int name="([^"]+)" value="([^"]*)" ?/>"""),
            Regex("""<long name="([^"]+)" value="([^"]*)" ?/>"""),
            Regex("""<float name="([^"]+)" value="([^"]*)" ?/>"""),
            Regex("""<set name="([^"]+)">""")
        )
        val entries = ArrayList<String>()
        for (p in patterns) {
            for (m in p.findAll(xml)) {
                val key = m.groupValues[1]
                val value = m.groupValues.getOrNull(2) ?: ""
                entries.add("$key=${desensitizeValue(key, desensitizeString(value))}")
            }
        }
        if (entries.isEmpty()) sb.appendLine("(empty)")
        else entries.forEach { sb.appendLine(it) }
        return sb.toString()
    }

    private fun desensitizeValue(key: String, value: String): String {
        val lower = key.lowercase(Locale.ROOT)
        val sensitive = SENSITIVE_KEY_KEYWORDS.any { lower.contains(it) }
        return if (sensitive) "***REDACTED***" else value
    }

    private fun desensitizeString(input: String): String {
        if (input.isEmpty()) return input
        var out = input
        out = out.replace(
            Regex("(?i)(password|pwd|token|secret|auth|cookie|session|apikey|api_key|bearer|credential)=([^&\\s,;}]+)"),
            "$1=***"
        )
        out = out.replace(
            Regex("(?i)(\"(?:password|pwd|token|secret|auth|cookie|session|apikey|api_key|bearer|credential)\"\\s*:\\s*\")([^\"]+)(\")"),
            "$1***$3"
        )
        return out
    }

    private fun collectSystemProperties(): List<Pair<String, String>> {
        val list = ArrayList<Pair<String, String>>()
        val keys = listOf(
            "ro.build.version.sdk",
            "ro.build.version.release",
            "ro.product.cpu.abilist",
            "ro.product.cpu.abilist32",
            "ro.product.cpu.abilist64",
            "ro.build.characteristics",
            "ro.kernel.qemu"
        )
        for (key in keys) {
            list.add(key to readSystemProperty(key))
        }
        return list
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readPageSize(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
            } else {
                4096L
            }
        } catch (_: Throwable) {
            4096L
        }
    }

    private fun safeState(t: Thread): String {
        return try {
            t.state?.name ?: "UNKNOWN"
        } catch (_: Throwable) {
            "UNKNOWN"
        }
    }

    private fun safeGroupName(t: Thread): String {
        return try {
            t.threadGroup?.name ?: "unknown"
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun safeJoin(arr: Array<String>?): String {
        if (arr == null || arr.isEmpty()) return "unknown"
        return arr.joinToString(",")
    }

    private fun formatTime(ms: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date(ms))
        } catch (_: Throwable) {
            ms.toString()
        }
    }

    private fun digest(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            "unavailable"
        }
    }
}
