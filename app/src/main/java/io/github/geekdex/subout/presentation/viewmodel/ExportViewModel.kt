package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import io.github.geekdex.subout.domain.generator.SimpleConfigGenerator
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
    val exportedFile: File? = null,
    val exportedPath: String? = null,
    val message: String? = null
)

class ExportViewModel(
    private val configRepository: ConfigRepository,
    private val nodeRepository: NodeRepository,
    private val configExporter: ConfigExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
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
                    app.configExporter
                ) as T
            }
        }
    }
}
