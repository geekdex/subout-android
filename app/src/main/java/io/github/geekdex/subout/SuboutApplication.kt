package io.github.geekdex.subout

import android.app.Application
import io.github.geekdex.subout.data.db.AppDatabase
import io.github.geekdex.subout.data.network.SubscriptionFetcher
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.data.repository.SubscriptionRepository
import io.github.geekdex.subout.domain.generator.ConfigExporter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SuboutApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val fetcher by lazy { SubscriptionFetcher() }
    val subscriptionRepository by lazy {
        SubscriptionRepository(database.subscriptionDao(), database.nodeDao(), fetcher)
    }
    val nodeRepository by lazy {
        NodeRepository(database.nodeDao())
    }
    val configRepository by lazy {
        ConfigRepository(this)
    }
    val configExporter by lazy {
        ConfigExporter(this)
    }
    val configServer by lazy {
        io.github.geekdex.subout.domain.server.ConfigServer()
    }

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        configServer.contentProvider = {
            val config = configRepository.configState.value
            val enabledNodes = nodeRepository.getEnabledNodes()
            io.github.geekdex.subout.domain.generator.SimpleConfigGenerator.generatePrettyString(config, enabledNodes)
        }

        // 全局响应式同步：无论是节点变更（新增、删除、启用禁用、订阅更新替换）还是配置变更（调整下拉、修改应用组），
        // 自动后台生成最新 sing-box 配置，并实时更新 ConfigServer 内存缓存及 Download 导出文件。
        appScope.launch {
            kotlinx.coroutines.flow.combine(configRepository.configState, nodeRepository.nodes) { cfg, nodes ->
                Pair(cfg, nodes)
            }.collect { (cfg, nodes) ->
                try {
                    val enabledNodes = nodes.filter { it.enabled }
                    val jsonStr = io.github.geekdex.subout.domain.generator.SimpleConfigGenerator.generatePrettyString(cfg, enabledNodes)
                    configServer.updateContent(jsonStr)
                    configExporter.exportToFile(jsonStr)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        lateinit var instance: SuboutApplication
            private set
    }
}
