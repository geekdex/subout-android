package io.github.geekdex.subout.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.geekdex.subout.SuboutApplication
import io.github.geekdex.subout.data.repository.ConfigRepository
import io.github.geekdex.subout.data.repository.NodeRepository
import io.github.geekdex.subout.domain.model.SimpleConfig
import io.github.geekdex.subout.domain.model.SimpleDnsConfig
import io.github.geekdex.subout.domain.model.SimpleInboundConfig
import io.github.geekdex.subout.domain.model.SimpleLogConfig
import io.github.geekdex.subout.domain.model.SimpleRouteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SimpleConfigUiState(
    val config: SimpleConfig = SimpleConfig(),
    val availableOutbounds: List<String> = listOf("AUTO-Test", "proxy", "direct"),
    val message: String? = null
)

class SimpleConfigViewModel(
    private val configRepository: ConfigRepository,
    private val nodeRepository: NodeRepository
) : ViewModel() {

    private val _currentConfig = MutableStateFlow(configRepository.configState.value)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SimpleConfigUiState> = combine(
        _currentConfig,
        nodeRepository.nodes,
        _message
    ) { cfg, nodes, msg ->
        val outbounds = mutableListOf("AUTO-Test", "proxy", "direct")
        val enabledNodeTags = nodes.filter { it.enabled }.map { it.tag }
        outbounds.addAll(enabledNodeTags)
        SimpleConfigUiState(
            config = cfg,
            availableOutbounds = outbounds,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SimpleConfigUiState()
    )

    fun updateDns(
        mode: String = _currentConfig.value.dns.mode,
        domesticDns: String = _currentConfig.value.dns.domestic_dns,
        foreignDns: String = _currentConfig.value.dns.foreign_dns
    ) {
        val updatedDns = _currentConfig.value.dns.copy(
            mode = mode,
            domestic_dns = domesticDns,
            foreign_dns = foreignDns
        )
        _currentConfig.value = _currentConfig.value.copy(dns = updatedDns)
    }

    fun updateInbound(
        inboundType: String = _currentConfig.value.inbound.inbound_type,
        mixedPort: Int = _currentConfig.value.inbound.mixed_port,
        allowLan: Boolean = _currentConfig.value.inbound.allow_lan,
        tunStack: String = _currentConfig.value.inbound.tun_stack,
        tunAutoRoute: Boolean = _currentConfig.value.inbound.tun_auto_route
    ) {
        val updatedInbound = _currentConfig.value.inbound.copy(
            inbound_type = inboundType,
            mixed_port = mixedPort,
            allow_lan = allowLan,
            tun_stack = tunStack,
            tun_auto_route = tunAutoRoute
        )
        _currentConfig.value = _currentConfig.value.copy(inbound = updatedInbound)
    }

    fun updateRoute(
        mode: String = _currentConfig.value.route.mode,
        blockAds: Boolean = _currentConfig.value.route.block_ads,
        bypassLan: Boolean = _currentConfig.value.route.bypass_lan,
        blockQuic: Boolean = _currentConfig.value.route.isBlockQuic,
        defaultOutbound: String = _currentConfig.value.route.default_outbound,
        routeGoogle: Boolean = _currentConfig.value.route.isRouteGoogle,
        googleOutbound: String = _currentConfig.value.route.googleOutbound,
        routeSocial: Boolean = _currentConfig.value.route.isRouteSocial,
        socialOutbound: String = _currentConfig.value.route.socialOutbound,
        routeAi: Boolean = _currentConfig.value.route.isRouteAi,
        aiOutbound: String = _currentConfig.value.route.aiOutbound
    ) {
        val updatedRoute = _currentConfig.value.route.copy(
            mode = mode,
            block_ads = blockAds,
            bypass_lan = bypassLan,
            block_quic = blockQuic,
            default_outbound = defaultOutbound,
            route_google = routeGoogle,
            google_outbound = googleOutbound,
            route_social = routeSocial,
            social_outbound = socialOutbound,
            route_ai = routeAi,
            ai_outbound = aiOutbound
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
    }

    fun enableAllRecommended() {
        val currentRoute = _currentConfig.value.route
        val updatedRoute = currentRoute.copy(
            block_ads = true,
            bypass_lan = true,
            block_quic = true,
            route_google = true,
            route_social = true,
            route_ai = true
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
        _message.value = "已一键开启全部推荐规则 (Google/社交/AI/阻断QUIC)"
    }

    fun forceAllProxy() {
        val currentRoute = _currentConfig.value.route
        val updatedRoute = currentRoute.copy(
            mode = "smart",
            block_ads = true,
            bypass_lan = true,
            block_quic = true,
            route_google = true,
            google_outbound = "proxy",
            route_social = true,
            social_outbound = "proxy",
            route_ai = true,
            ai_outbound = "proxy"
        )
        _currentConfig.value = _currentConfig.value.copy(route = updatedRoute)
        _message.value = "已强制将 Google、社交与 AI 路由至代理出站"
    }

    fun updateLog(
        level: String = _currentConfig.value.log.level,
        timestamp: Boolean = _currentConfig.value.log.timestamp
    ) {
        val updatedLog = _currentConfig.value.log.copy(
            level = level,
            timestamp = timestamp
        )
        _currentConfig.value = _currentConfig.value.copy(log = updatedLog)
    }

    fun saveConfig() {
        viewModelScope.launch {
            configRepository.saveConfig(_currentConfig.value)
            _message.value = "配置已保存"
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun Factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = SuboutApplication.instance
                return SimpleConfigViewModel(app.configRepository, app.nodeRepository) as T
            }
        }
    }
}
