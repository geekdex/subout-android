package io.github.geekdex.subout.domain.generator

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class ExportResult(
    val file: File?,
    val displayPath: String
)

class ConfigExporter(private val context: Context) {

    /**
     * 导出配置文件至公共下载目录中的 subout 子目录 (Download/subout/sing-box.json)。
     * 兼容 Android 10+ 分区存储 (Scoped Storage) 与直接文件访问，确保覆写时不产生 (1) 副本。
     */
    fun exportToFile(configJson: String, fileName: String = "sing-box.json"): ExportResult {
        val relativeSubDir = "${Environment.DIRECTORY_DOWNLOADS}/subout"
        var directFile: File? = null
        var directWriteSuccess = false

        // 1. 优先尝试直接通过文件系统写入 Download/subout
        try {
            val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val suboutDir = File(publicDownloadDir, "subout")
            if (!suboutDir.exists()) {
                suboutDir.mkdirs()
            }
            val f = File(suboutDir, fileName)
            f.writeText(configJson)
            directFile = f
            directWriteSuccess = true

            // 通知系统媒体扫描器索引/更新此文件，保证其它应用及系统文件选择器可见
            MediaScannerConnection.scanFile(
                context,
                arrayOf(f.absolutePath),
                arrayOf("application/json"),
                null
            )
        } catch (_: Exception) {
            directWriteSuccess = false
        }

        // 2. 如果直接 File 写入受限，则使用 MediaStore.Downloads 写入
        if (!directWriteSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
}
