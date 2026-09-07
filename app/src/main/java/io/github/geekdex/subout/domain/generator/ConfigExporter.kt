package io.github.geekdex.subout.domain.generator

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class ExportResult(
    val file: File?,
    val displayPath: String
)

class ConfigExporter(private val context: Context) {

    /**
     * 导出配置文件至公共下载目录中的 subout 子目录 (Download/subout/sing-box.json)。
     * 兼容 Android 10+ 分区存储 (Scoped Storage) 与旧版直接路径，并同步在私有目录保留副本以便分享。
     */
    fun exportToFile(configJson: String, fileName: String = "sing-box.json"): ExportResult {
        val relativeSubDir = "${Environment.DIRECTORY_DOWNLOADS}/subout"
        var directFile: File? = null

        // 1. 尝试直接通过文件系统写入 Download/subout
        try {
            val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val suboutDir = File(publicDownloadDir, "subout")
            if (!suboutDir.exists()) {
                suboutDir.mkdirs()
            }
            val f = File(suboutDir, fileName)
            f.writeText(configJson)
            directFile = f
        } catch (_: Exception) {
            // Android 10+ 分区存储可能限制直接 File API 写入，转入下一步 MediaStore
        }

        // 2. Android 10 (API 29) 及以上通过 MediaStore.Downloads 写入公共下载目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, "$relativeSubDir%")

                val existingId: Long? = resolver.query(
                    contentUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    } else null
                }

                val itemUri = if (existingId != null) {
                    ContentUris.withAppendedId(contentUri, existingId)
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, "$relativeSubDir/")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    resolver.insert(contentUri, values)
                }

                if (itemUri != null) {
                    resolver.openOutputStream(itemUri, "wt")?.use { out ->
                        out.write(configJson.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    if (existingId == null) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.IS_PENDING, 0)
                        }
                        resolver.update(itemUri, values, null, null)
                    }
                }
            } catch (_: Exception) {
                // MediaStore 写入异常容错
            }
        }

        // 3. 同时写入私有文件目录供 FileProvider 分享兜底
        val internalFile = try {
            val privateDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }
            val f = File(privateDir, fileName)
            f.writeText(configJson)
            f
        } catch (_: Exception) {
            null
        }

        val displayPath = "Download/subout/$fileName"
        return ExportResult(
            file = directFile ?: internalFile,
            displayPath = displayPath
        )
    }

    fun shareToSFA(configFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            configFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Attempt to target SFA if installed
            setPackage("io.nekohasekai.sfa")
        }

        try {
            val chooser = Intent.createChooser(shareIntent, "导入配置到 SFA").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // If SFA specific package failed, fall back to general chooser
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(fallbackIntent, "分享配置文件").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
