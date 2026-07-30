package com.xiaowei.player.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.xiaowei.player.BuildConfig
import com.xiaowei.player.data.UpdateChecker
import com.xiaowei.player.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateCheckerState {

    private val _manualTrigger = MutableStateFlow(0L)
    val manualTrigger: StateFlow<Long> = _manualTrigger.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun requestManualCheck() {
        _manualTrigger.value = _manualTrigger.value + 1
    }

    fun setChecking(v: Boolean) {
        _checking.value = v
    }
}

@Composable
fun UpdateCheckerHost(
    onCheckRequested: () -> Unit,
    manualTrigger: Long?
) {
    val context = LocalContext.current
    val trigger = UpdateCheckerState.manualTrigger.collectAsState()

    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var showCheckingToast by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cleanupInstalledApks(context)
        }
        val info = withContext(Dispatchers.IO) {
            UpdateChecker.check(BuildConfig.VERSION_CODE)
        }
        if (info != null) updateInfo = info
    }

    LaunchedEffect(trigger.value) {
        if (trigger.value == 0L) return@LaunchedEffect
        showCheckingToast = true
        val info = withContext(Dispatchers.IO) {
            UpdateChecker.check(BuildConfig.VERSION_CODE)
        }
        showCheckingToast = false
        if (info != null) {
            updateInfo = info
        } else {
            toastMsg = Strings.get("update_no_new")
        }
    }

    LaunchedEffect(showCheckingToast) {
        if (showCheckingToast) {
            android.widget.Toast.makeText(context, Strings.get("update_checking"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(toastMsg) {
        if (toastMsg != null) {
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(250)
            toastMsg = null
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            onDismiss = { updateInfo = null }
        )
    }
}

@Composable
fun UpdateDialog(
    updateInfo: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val expectedFileName = expectedApkName(updateInfo.versionName)
    val existingFile = remember(updateInfo.versionName) { findExistingApk(context, expectedFileName) }

    var downloadState by remember {
        mutableStateOf<DownloadState>(
            if (existingFile != null) DownloadState.Done else DownloadState.Idle
        )
    }
    var downloadedFile by remember { mutableStateOf(existingFile) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val httpCallRef = remember { java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>(null) }

    Dialog(
        onDismissRequest = {
            if (downloadState !is DownloadState.Downloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState !is DownloadState.Downloading,
            dismissOnClickOutside = downloadState !is DownloadState.Downloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "v" + updateInfo.versionName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = updateInfo.updateLog.ifBlank { Strings.get("update_no_new") },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                }

                val dl = downloadState
                if (dl is DownloadState.Downloading) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { dl.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = Strings.get("update_downloading") + " " + (dl.progress * 100).toInt() + "%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (downloadState is DownloadState.Downloading) {
                                httpCallRef.getAndSet(null)?.cancel()
                                downloadJob?.cancel()
                                downloadJob = null
                                downloadState = DownloadState.Idle
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Text(
                            text = Strings.get("update_cancel"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val file = downloadedFile
                            if (file != null && file.exists() && downloadState is DownloadState.Done) {
                                installApk(context, file)
                            } else if (downloadState !is DownloadState.Downloading) {
                                downloadJob?.cancel()
                                val job = scope.launch {
                                    downloadState = DownloadState.Downloading(0f)
                                    val saved = downloadApk(
                                        context,
                                        updateInfo.downloadUrl,
                                        updateInfo.versionName,
                                        httpCallRef
                                    ) { p ->
                                        downloadState = DownloadState.Downloading(p)
                                    }
                                    httpCallRef.set(null)
                                    if (saved != null) {
                                        downloadedFile = saved
                                        downloadState = DownloadState.Done
                                    } else {
                                        downloadState = DownloadState.Failed
                                    }
                                }
                                downloadJob = job
                            }
                        },
                        enabled = downloadState !is DownloadState.Downloading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        val btnText = when (downloadState) {
                            is DownloadState.Downloading -> Strings.get("update_downloading")
                            is DownloadState.Done -> Strings.get("update_install")
                            else -> Strings.get("update_download")
                        }
                        Text(
                            text = btnText,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Done : DownloadState()
    object Failed : DownloadState()
}

private fun expectedApkName(versionName: String): String = "shuyin-$versionName.apk"

private fun findExistingApk(context: Context, fileName: String): File? {
    return try {
        val f = File(updatesDir(context), fileName)
        if (f.exists() && f.length() > 0) f else null
    } catch (_: Exception) {
        null
    }
}

private fun updatesDir(context: Context): File {
    val dir = File(context.filesDir, "updates")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun cleanupOldApks(context: Context, keepFileName: String? = null) {
    try {
        updatesDir(context).listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".apk")) {
                if (keepFileName == null || f.name != keepFileName) {
                    f.delete()
                }
            }
        }
    } catch (_: Exception) {
    }
}

private fun cleanupInstalledApks(context: Context) {
    try {
        updatesDir(context).listFiles()?.forEach { f ->
            if (f.isFile && (f.name.endsWith(".apk") || f.name.endsWith(".tmp"))) {
                f.delete()
            }
        }
    } catch (_: Exception) {
    }
}

private suspend fun downloadApk(
    context: Context,
    url: String,
    versionName: String,
    callRef: java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>,
    onProgress: (Float) -> Unit
): File? = withContext(Dispatchers.IO) {
    try {
        val outFile = File(updatesDir(context), expectedApkName(versionName))
        cleanupOldApks(context, keepFileName = outFile.name)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .build()
        val call = client.newCall(request)
        callRef.set(call)
        call.execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            val total = body.contentLength()
            val tmp = File(outFile.parentFile, outFile.name + ".tmp")
            if (tmp.exists()) tmp.delete()
            body.byteStream().use { input ->
                java.io.FileOutputStream(tmp).use { output ->
                    val buf = ByteArray(8 * 1024)
                    var read: Int
                    var sum = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        sum += read
                        if (total > 0) onProgress(sum.toFloat() / total)
                    }
                }
            }
            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) return@withContext null
            outFile
        }
    } catch (e: Exception) {
        null
    }
}

private fun canInstallApk(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            context.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) {
            false
        }
    } else true
}

private fun installApk(context: Context, file: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !canInstallApk(context)) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
