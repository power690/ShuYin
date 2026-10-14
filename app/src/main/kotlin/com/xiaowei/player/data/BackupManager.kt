package com.xiaowei.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    const val MARKER_FILE = "shuyin_backup_marker"
    private const val TAG = "BackupManager"
    private const val MARKER_CONTENT = "ShuYin-Backup-v1"
    private const val SHARE_DIR_NAME = "backup_share"
    private const val RESTORE_DIR_NAME = "backup_restore"
    private val EXCLUDED_DATA_DIRS = setOf("cache", "code_cache")

    fun newBackupFile(context: Context): File {
        val dir = File(context.cacheDir, SHARE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return File(dir, "$stamp.zip")
    }

    fun createBackup(context: Context, output: File): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zos ->
                zos.putNextEntry(ZipEntry(MARKER_FILE))
                zos.write(MARKER_CONTENT.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                val dataDir = context.filesDir.parentFile
                if (dataDir != null && dataDir.isDirectory) {
                    val children = dataDir.listFiles()
                    if (children != null) {
                        for (child in children) {
                            if (child.name in EXCLUDED_DATA_DIRS) continue
                            addFileToZip(zos, child, child.name)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "create backup failed", e)
            output.delete()
            false
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    addFileToZip(zos, child, "$entryPath/${child.name}")
                }
            }
        } else {
            try {
                zos.putNextEntry(ZipEntry(entryPath))
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            } catch (e: Exception) {
                Log.e(TAG, "skip entry $entryPath", e)
            }
        }
    }

    fun isShuYinBackup(file: File): Boolean {
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.name == MARKER_FILE) return true
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun copyToTemp(context: Context, uri: Uri): File? {
        return try {
            val dir = File(context.cacheDir, RESTORE_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, "import.zip")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src ->
                FileOutputStream(target).use { dst ->
                    src.copyTo(dst)
                }
            }
            target
        } catch (e: Exception) {
            Log.e(TAG, "copy backup failed", e)
            null
        }
    }

    fun restoreBackup(context: Context, zipFile: File): Boolean {
        return try {
            val tempDir = File(context.cacheDir, RESTORE_DIR_NAME)
            val imported = File(tempDir, "import.zip")
            val source = if (imported.exists()) imported else zipFile
            val extractDir = File(tempDir, "extracted")
            extractDir.deleteRecursively()
            if (!extractDir.mkdirs()) return false
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val outFile = safeResolve(extractDir, entry.name) ?: return false
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
            }
            if (!File(extractDir, MARKER_FILE).isFile) {
                extractDir.deleteRecursively()
                return false
            }
            val dataDir = context.filesDir.parentFile ?: return false
            val tops = extractDir.listFiles()
            if (tops != null) {
                for (top in tops) {
                    if (top.name == MARKER_FILE) continue
                    val target = File(dataDir, top.name)
                    target.deleteRecursively()
                    if (top.isDirectory) {
                        top.copyRecursively(target, overwrite = true)
                    } else {
                        top.copyTo(target, overwrite = true)
                    }
                }
            }
            extractDir.deleteRecursively()
            source.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "restore backup failed", e)
            false
        }
    }

    fun cleanupShareDir(context: Context) {
        try {
            File(context.cacheDir, SHARE_DIR_NAME).deleteRecursively()
        } catch (_: Exception) {
        }
    }

    fun restartApp(context: Context) {
        val intent = Intent(context, com.xiaowei.player.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun safeResolve(root: File, entryName: String): File? {
        return try {
            val target = File(root, entryName)
            val rootPath = root.canonicalPath
            val targetPath = target.canonicalPath
            if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) null else target
        } catch (e: Exception) {
            null
        }
    }
}
