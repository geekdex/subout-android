package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.presentation.viewmodel.SimpleConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleConfigScreen(
    viewModel: SimpleConfigViewModel,
    onNavigateToExport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.config
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("小白模式配置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. DNS Section
            ConfigSectionCard(
                title = "DNS 配置",
                icon = Icons.Default.Dns
            ) {
                var dnsModeExpanded by remember { mutableStateOf(false) }
                val dnsModes = listOf(
                    Pair("preset_fakeip", "FakeIP 模式 (推荐)"),
                    Pair("preset_domestic_foreign", "国内外分流模式"),
                    Pair("fast_public", "快速公共 DNS"),
                    Pair("custom", "自定义 DNS")
                )

                ExposedDropdownMenuBox(
                    expanded = dnsModeExpanded,
                    onExpandedChange = { dnsModeExpanded = !dnsModeExpanded }
                ) {
                    OutlinedTextField(
                        value = dnsModes.find { it.first == config.dns.mode }?.second ?: config.dns.mode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("DNS 模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsModeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dnsModeExpanded,
                        onDismissRequest = { dnsModeExpanded = false }
                    ) {
                        dnsModes.forEach { (modeKey, modeName) ->
                            DropdownMenuItem(
                                text = { Text(modeName) },
                                onClick = {
                                    val foreign = when (modeKey) {
                                        "preset_fakeip" -> "fakeip"
                                        "preset_domestic_foreign" -> "https://1.1.1.1/dns-query"
                                        "fast_public" -> "223.5.5.5"
                                        else -> config.dns.foreign_dns
                                    }
                                    viewModel.updateDns(mode = modeKey, foreignDns = foreign)
                                    dnsModeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = config.dns.domestic_dns,
                    onValueChange = { viewModel.updateDns(domesticDns = it) },
                    label = { Text("国内 DNS 服务器") },
                    placeholder = { Text("例如 223.5.5.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = config.dns.foreign_dns,
                    onValueChange = { viewModel.updateDns(foreignDns = it) },
                    label = { Text("国外 DNS 服务器") },
                    placeholder = { Text("fakeip 或 https://1.1.1.1/dns-query") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Inbounds Section
            ConfigSectionCard(
                title = "入站配置",
                icon = Icons.Default.Input
            ) {
                var inboundExpanded by remember { mutableStateOf(false) }
                val inboundTypes = listOf(
                    Pair("tun", "TUN 模式 (推荐，生成虚拟网卡配置)"),
                    Pair("mixed", "Mixed 端口 (HTTP/SOCKS5 混合代理)")
                )

                ExposedDropdownMenuBox(
                    expanded = inboundExpanded,
                    onExpandedChange = { inboundExpanded = !inboundExpanded }
                ) {
                    OutlinedTextField(
                        value = inboundTypes.find { it.first == config.inbound.inbound_type }?.second ?: config.inbound.inbound_type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("入站类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inboundExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = inboundExpanded,
                        onDismissRequest = { inboundExpanded = false }
                    ) {
                        inboundTypes.forEach { (typeKey, typeName) ->
                            DropdownMenuItem(
                                text = { Text(typeName) },
                                onClick = {
                                    viewModel.updateInbound(inboundType = typeKey)
                                    inboundExpanded = false
                                }
                            )
                        }
                    }
                }

                if (config.inbound.inbound_type == "tun") {
                    var stackExpanded by remember { mutableStateOf(false) }
                    val stacks = listOf("system", "gvisor", "mixed")

                    ExposedDropdownMenuBox(
                        expanded = stackExpanded,
                        onExpandedChange = { stackExpanded = !stackExpanded }
                    ) {
                        OutlinedTextField(
                            value = config.inbound.tun_stack,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("TUN 堆栈 (Stack)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stackExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = stackExpanded,
                            onDismissRequest = { stackExpanded = false }
                        ) {
                            stacks.forEach { stack ->
                                DropdownMenuItem(
                                    text = { Text(stack) },
                                    onClick = {
                                        viewModel.updateInbound(tunStack = stack)
                                        stackExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("自动配置路由 (Auto Route)", style = MaterialTheme.typography.bodyMedium)
                            Text("自动接管系统路由表流量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = config.inbound.tun_auto_route,
                            onCheckedChange = { viewModel.updateInbound(tunAutoRoute = it) }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = config.inbound.mixed_port.toString(),
                        onValueChange = {
                            val p = it.toIntOrNull() ?: config.inbound.mixed_port
                            viewModel.updateInbound(mixedPort = p)
                        },
                        label = { Text("Mixed 混合代理端口") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("允许局域网连接 (Allow LAN)", style = MaterialTheme.typography.bodyMedium)
                            Text("监听 0.0.0.0 允许同局域网设备接入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = config.inbound.allow_lan,
                            onCheckedChange = { viewModel.updateInbound(allowLan = it) }
                        )
                    }
                }
            }

            // 3. Route Section
            ConfigSectionCard(
                title = "路由规则配置",
                icon = Icons.Default.AltRoute
            ) {
                var routeModeExpanded by remember { mutableStateOf(false) }
                val routeModes = listOf(
                    Pair("smart", "智能分流 (绕过大陆/国内直连，推荐)"),
                    Pair("global", "全局代理 (所有流量走代理)"),
                    Pair("gfw", "GFW 列表代理 (仅被封锁域名走代理)"),
                    Pair("direct", "全部直连 (不走代理)")
                )

                ExposedDropdownMenuBox(
                    expanded = routeModeExpanded,
                    onExpandedChange = { routeModeExpanded = !routeModeExpanded }
                ) {
                    OutlinedTextField(
                        value = routeModes.find { it.first == config.route.mode }?.second ?: config.route.mode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("路由模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeModeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = routeModeExpanded,
                        onDismissRequest = { routeModeExpanded = false }
                    ) {
                        routeModes.forEach { (modeKey, modeName) ->
                            DropdownMenuItem(
                                text = { Text(modeName) },
                                onClick = {
                                    viewModel.updateRoute(mode = modeKey)
                                    routeModeExpanded = false
                                }
                            )
                        }
                    }
                }

                var defaultOutboundExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = defaultOutboundExpanded,
                    onExpandedChange = { defaultOutboundExpanded = !defaultOutboundExpanded }
                ) {
                    OutlinedTextField(
                        value = config.route.default_outbound,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("默认出站 (Default Outbound)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = defaultOutboundExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = defaultOutboundExpanded,
                        onDismissRequest = { defaultOutboundExpanded = false }
                    ) {
                        uiState.availableOutbounds.forEach { outbound ->
                            DropdownMenuItem(
                                text = { Text(outbound) },
                                onClick = {
                                    viewModel.updateRoute(defaultOutbound = outbound)
                                    defaultOutboundExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("拦截常见广告 (Ad Blocking)", style = MaterialTheme.typography.bodyMedium)
                        Text("使用 geosite-category-ads-all 规则阻断广告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = config.route.block_ads,
                        onCheckedChange = { viewModel.updateRoute(blockAds = it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("绕过局域网流量 (Bypass LAN)", style = MaterialTheme.typography.bodyMedium)
                        Text("私有网络 IP (192.168.x / 10.x 等) 强制直连", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = config.route.bypass_lan,
                        onCheckedChange = { viewModel.updateRoute(bypassLan = it) }
                    )
                }
            }

            // 4. Log Section
            ConfigSectionCard(
                title = "日志配置",
                icon = Icons.Default.Article
            ) {
                var logLevelExpanded by remember { mutableStateOf(false) }
                val logLevels = listOf("trace", "debug", "info", "warn", "error")

                ExposedDropdownMenuBox(
                    expanded = logLevelExpanded,
                    onExpandedChange = { logLevelExpanded = !logLevelExpanded }
                ) {
                    OutlinedTextField(
                        value = config.log.level,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("日志级别") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = logLevelExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = logLevelExpanded,
                        onDismissRequest = { logLevelExpanded = false }
                    ) {
                        logLevels.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level) },
                                onClick = {
                                    viewModel.updateLog(level = level)
                                    logLevelExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("打印时间戳", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.log.timestamp,
                        onCheckedChange = { viewModel.updateLog(timestamp = it) }
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.saveConfig() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存配置")
                }

                Button(
                    onClick = {
                        viewModel.saveConfig()
                        onNavigateToExport()
                    },
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存并导出")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ConfigSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}
