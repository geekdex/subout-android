package io.github.geekdex.subout.domain.generator

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class ConfigExporter(private val context: Context) {

    fun exportToFile(configJson: String, fileName: String = "sing-box.json"): File {
        val exportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val configFile = File(exportDir, fileName)
        configFile.writeText(configJson)
        return configFile
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
