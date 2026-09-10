package io.github.geekdex.subout.data.repository

import android.content.Context
import com.google.gson.Gson
import io.github.geekdex.subout.domain.model.SimpleConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigRepository(context: Context) {

    private val prefs = context.getSharedPreferences("subout_config_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _configState = MutableStateFlow(loadConfig())
    val configState: StateFlow<SimpleConfig> = _configState.asStateFlow()

    private fun loadConfig(): SimpleConfig {
        val json = prefs.getString(KEY_SIMPLE_CONFIG, null) ?: return SimpleConfig()
        return try {
            gson.fromJson(json, SimpleConfig::class.java) ?: SimpleConfig()
        } catch (_: Exception) {
            SimpleConfig()
        }
    }

    fun saveConfig(config: SimpleConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_SIMPLE_CONFIG, json).commit()
        _configState.value = config
    }

    companion object {
        private const val KEY_SIMPLE_CONFIG = "simple_config_json"
    }
}
