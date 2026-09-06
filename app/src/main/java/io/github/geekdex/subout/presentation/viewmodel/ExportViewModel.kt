package io.github.geekdex.subout.presentation.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.server.ConfigServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExportUiState(
    val configJson: String = "",
    val isGenerating: Boolean = false,
    val isServerRunning: Boolean = false,
    val serverUrl: String = "",
    val qrBitmap: Bitmap? = null,
    val exportedFile: File? = null,
    val message: String? = null
)

class ExportViewModel(
    private val configRepository: ConfigRepository,
    private val nodeRepository: NodeRepository,
    private val configExporter: ConfigExporter,
    private val configServer: ConfigServer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExportUiState(
            isServerRunning = configServer.isRunning,
            serverUrl = if (configServer.isRunning) configServer.currentUrl else ""
        )
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        refreshConfig()
    }

    fun refreshConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true)
            try {
                val config = configRepository.configState.value
                val enabledNodes = nodeRepository.getEnabledNodes()
                val jsonStr = withContext(Dispatchers.Default) {
                    SimpleConfigGenerator.generatePrettyString(config, enabledNodes)
                }
                _uiState.value = _uiState.value.copy(
                    configJson = jsonStr,
                    isGenerating = false
                )
                // If server is currently running, update the served content
                if (configServer.isRunning) {
                    configServer.start(jsonStr, configServer.currentPort)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    message = "生成配置失败: ${e.message}"
                )
            }
        }
    }

    fun exportToFile(): File? {
        val json = _uiState.value.configJson
        if (json.isBlank()) return null
        return try {
            val file = configExporter.exportToFile(json)
            _uiState.value = _uiState.value.copy(
                exportedFile = file,
                message = "已导出至: ${file.absolutePath}"
            )
            file
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(message = "文件导出失败: ${e.message}")
            null
        }
    }

    fun shareToSFA() {
        val file = _uiState.value.exportedFile ?: exportToFile() ?: return
        try {
            configExporter.shareToSFA(file)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(message = "分享失败: ${e.message}")
        }
    }

    fun toggleServer(port: Int = 8888) {
        if (configServer.isRunning) {
            configServer.stop()
            _uiState.value = _uiState.value.copy(
                isServerRunning = false,
                serverUrl = "",
                qrBitmap = null,
                message = "服务已停止"
            )
        } else {
            val json = _uiState.value.configJson
            val result = configServer.start(json, port)
            if (result.isSuccess) {
                val url = result.getOrThrow()
                val qr = ConfigServer.generateQrCode(url)
                _uiState.value = _uiState.value.copy(
                    isServerRunning = true,
                    serverUrl = url,
                    qrBitmap = qr,
                    message = "临时配置服务已启动"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isServerRunning = false,
                    message = "启动失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        // Note: we can keep server running if desired or stop it
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SuboutApplication.instance
                return ExportViewModel(
                    app.configRepository,
                    app.nodeRepository,
                    app.configExporter,
                    app.configServer
                ) as T
            }
        }
    }
}
