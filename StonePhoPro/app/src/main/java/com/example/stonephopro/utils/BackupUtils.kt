package com.example.stonephopro.utils

import android.content.Context
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

object BackupUtils {
    private const val TAG = "BackupUtils"
    private const val BACKUP_FOLDER = "StonePhoBackup"

    fun createBackupZip(context: Context): File? {
        val exportDir = File(context.getExternalFilesDir(null), BACKUP_FOLDER)
        if (!exportDir.exists()) exportDir.mkdirs()

        val sdf = SimpleDateFormat("MM-dd-yyyy_HH-mm", Locale.US)
        val zipFile = File(exportDir, "stonepho_backup_${sdf.format(Date())}.zip")

        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                zipFolder(context.filesDir, context.filesDir.absolutePath, zos)
            }
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Backup failed: ${e.message}")
            null
        }
    }


    private fun zipFolder(folder: File, basePath: String, zos: ZipOutputStream) {
        folder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                zipFolder(file, basePath, zos)
            } else {
                val entryName = file.absolutePath.removePrefix(basePath).removePrefix("/")
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                file.inputStream().copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    fun restoreFromZip(context: Context, zipFile: File): Boolean {
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val file = File(context.filesDir, entry!!.name)
                    if (entry!!.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Restore failed: ${e.message}")
            false
        }
    }
}
