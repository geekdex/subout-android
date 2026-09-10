package io.github.geekdex.subout.presentation.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
import io.github.geekdex.subout.domain.server.ConfigServer
import io.github.geekdex.subout.domain.server.ConfigServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExportUiState(
    val configJson: String = "",
    val isGenerating: Boolean = false,
    val isServerRunning: Boolean = false,
    val serverUrl: String = "",
    val currentPort: Int = 8888,
    val qrBitmap: Bitmap? = null,
    val sfaImportUri: String = "",
    val exportedFile: File? = null,
    val exportedPath: String? = null,
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
            serverUrl = if (configServer.isRunning) configServer.currentUrl else "",
            currentPort = configServer.currentPort
        )
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        // 自动响应配置或节点变更，实时更新预览与本地 HTTP 服务内容
        viewModelScope.launch {
            combine(
                configRepository.configState,
                nodeRepository.nodes
            ) { config, nodes ->
                val enabledNodes = nodes.filter { it.enabled }
                withContext(Dispatchers.Default) {
                    SimpleConfigGenerator.generatePrettyString(config, enabledNodes)
                }
            }.collect { jsonStr ->
                _uiState.value = _uiState.value.copy(
                    configJson = jsonStr,
                    isGenerating = false
                )
                configServer.updateContent(jsonStr)
            }
        }

        // 监听前台服务状态
        viewModelScope.launch {
            combine(
                ConfigServerService.isRunningState,
                ConfigServerService.serverUrlState
            ) { isRunning, url ->
                val activeUrl = if (isRunning) url.ifEmpty { configServer.currentUrl } else ""
                val sfaUri = if (activeUrl.isNotEmpty()) ConfigServer.getSfaImportUri(activeUrl, "Subout") else ""
                val qr = if (sfaUri.isNotEmpty()) ConfigServer.generateQrCode(sfaUri) else null
                Pair(isRunning, Pair(activeUrl, Pair(sfaUri, qr)))
            }.collect { (isRunning, data) ->
                val (url, uriAndQr) = data
                val (sfaUri, qr) = uriAndQr
                _uiState.value = _uiState.value.copy(
                    isServerRunning = isRunning,
                    serverUrl = url,
                    sfaImportUri = sfaUri,
                    qrBitmap = qr
                )
            }
        }
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
                // 动态更新已在运行的服务内容，外部客户端刷新立即获得最新配置
                configServer.updateContent(jsonStr)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    message = "生成配置失败: ${e.message}"
                )
            }
        }
    }

    fun toggleServer(port: Int = 8888) {
        val app = SuboutApplication.instance
        if (_uiState.value.isServerRunning) {
            ConfigServerService.stop(app)
            _uiState.value = _uiState.value.copy(
                isServerRunning = false,
                serverUrl = "",
                qrBitmap = null,
                sfaImportUri = "",
                message = "配置服务已停止"
            )
        } else {
            val json = _uiState.value.configJson
            ConfigServerService.start(app, port, json)
            _uiState.value = _uiState.value.copy(
                currentPort = port,
                message = "正在启动本地配置服务..."
            )
        }
    }

    fun importToSFA(context: Context) {
        val url = _uiState.value.serverUrl
        if (url.isEmpty() || !_uiState.value.isServerRunning) {
            _uiState.value = _uiState.value.copy(message = "请先启动本地配置服务")
            return
        }

        val sfaUri = ConfigServer.getSfaImportUri(url, "Subout")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(sfaUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            _uiState.value = _uiState.value.copy(message = "已调用 SFA 导入界面，请点击保存")
        } catch (_: Exception) {
            // SFA 未安装或无法响应 URL Scheme，兜底复制 URL
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Config URL", url))
            _uiState.value = _uiState.value.copy(message = "未检测到 SFA 应用，已复制导入地址到剪贴板")
        }
    }

    fun exportToFile(): File? {
        val json = _uiState.value.configJson
        if (json.isBlank()) return null
        return try {
            val result = configExporter.exportToFile(json)
            _uiState.value = _uiState.value.copy(
                exportedFile = result.file,
                exportedPath = result.displayPath,
                message = "已导出至: ${result.displayPath}"
            )
            result.file
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(message = "文件导出失败: ${e.message}")
            null
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
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
