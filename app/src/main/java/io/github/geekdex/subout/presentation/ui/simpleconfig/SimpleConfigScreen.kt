package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.domain.model.AppRulePresets
import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.InstalledAppInfo
import io.github.geekdex.subout.domain.model.PresetCategory
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

    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var groupToRename by remember { mutableStateOf<CustomAppGroup?>(null) }
    var renameGroupName by remember { mutableStateOf("") }
    var activePickingGroupId by remember { mutableStateOf<String?>(null) }

    var showAddDomainGroupDialog by remember { mutableStateOf(false) }
    var newDomainGroupName by remember { mutableStateOf("") }
    var domainGroupToRename by remember { mutableStateOf<CustomDomainGroup?>(null) }
    var renameDomainGroupName by remember { mutableStateOf("") }
    var activePickingDomainGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Add App Group Dialog
    if (showAddGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAddGroupDialog = false },
            title = { Text("新建应用分流分组") },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomGroup(newGroupName)
                        showAddGroupDialog = false
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Rename App Group Dialog
    groupToRename?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToRename = null },
            title = { Text("重命名分组") },
            text = {
                OutlinedTextField(
                    value = renameGroupName,
                    onValueChange = { renameGroupName = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCustomGroupName(group.id, renameGroupName)
                        groupToRename = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Add Domain Group Dialog
    if (showAddDomainGroupDialog) {
        AlertDialog(
            onDismissRequest = { showAddDomainGroupDialog = false },
            title = { Text("新建域名后缀分组") },
            text = {
                OutlinedTextField(
                    value = newDomainGroupName,
                    onValueChange = { newDomainGroupName = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomDomainGroup(newDomainGroupName)
                        showAddDomainGroupDialog = false
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDomainGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Rename Domain Group Dialog
    domainGroupToRename?.let { group ->
        AlertDialog(
            onDismissRequest = { domainGroupToRename = null },
            title = { Text("重命名域名分组") },
            text = {
                OutlinedTextField(
                    value = renameDomainGroupName,
                    onValueChange = { renameDomainGroupName = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCustomDomainGroupName(group.id, renameDomainGroupName)
                        domainGroupToRename = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { domainGroupToRename = null }) {
                    Text("取消")
                }
            }
        )
    }

    // App Picker BottomSheet
    activePickingGroupId?.let { groupId ->
        val group = config.route.customGroups.find { it.id == groupId }
        if (group != null) {
            val occupiedMap = remember(config.route, groupId) {
                viewModel.getOccupiedAppMap(excludeGroupId = groupId)
            }
            AppPickerSheet(
                groupName = group.name,
                initialSelected = group.packageNames,
                installedApps = uiState.installedApps,
                isLoadingApps = uiState.isLoadingApps,
                occupiedMap = occupiedMap,
                onDismiss = { activePickingGroupId = null },
                onConfirm = { selectedList ->
                    viewModel.updateCustomGroupPackages(groupId, selectedList)
                    activePickingGroupId = null
                }
            )
        }
    }

    // Domain Picker BottomSheet
    activePickingDomainGroupId?.let { groupId ->
        val group = config.route.customDomainGroups.find { it.id == groupId }
        if (group != null) {
            DomainPickerSheet(
                group = group,
                viewModel = viewModel,
                onDismiss = { activePickingDomainGroupId = null }
            )
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("分流与规则配置", fontWeight = FontWeight.Bold) },
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
                title = "路由基础配置",
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

            // 4. WYSIWYG App Routing Section
            ConfigSectionCard(
                title = "常用应用分流 (所见即所得)",
                icon = Icons.Default.Apps
            ) {
                Text(
                    text = "透明直观的海外应用专属规则，支持一键配置。自动接管 Google 全家桶、主流海外社交与热门 AI，防止握手超时与白屏转圈。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 一键配置快捷按钮栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.enableAllRecommended() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("一键开启推荐", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { viewModel.forceAllProxy() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("一键强制代理", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 1) Google 全家桶
                PresetAppRuleItem(
                    category = AppRulePresets.google,
                    enabled = config.route.isRouteGoogle,
                    onEnabledChange = { viewModel.updateRoute(routeGoogle = it) },
                    customOutbound = config.route.googleOutbound,
                    onOutboundChange = { viewModel.updateRoute(googleOutbound = it) },
                    availableOutbounds = uiState.availableOutbounds
                )

                // 2) 国际社交与通讯
                PresetAppRuleItem(
                    category = AppRulePresets.social,
                    enabled = config.route.isRouteSocial,
                    onEnabledChange = { viewModel.updateRoute(routeSocial = it) },
                    customOutbound = config.route.socialOutbound,
                    onOutboundChange = { viewModel.updateRoute(socialOutbound = it) },
                    availableOutbounds = uiState.availableOutbounds
                )

                // 3) 热门 AI 助手
                PresetAppRuleItem(
                    category = AppRulePresets.ai,
                    enabled = config.route.isRouteAi,
                    onEnabledChange = { viewModel.updateRoute(routeAi = it) },
                    customOutbound = config.route.aiOutbound,
                    onOutboundChange = { viewModel.updateRoute(aiOutbound = it) },
                    availableOutbounds = uiState.availableOutbounds
                )

                // 4) 阻断 QUIC (UDP 443)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("阻断 QUIC (UDP 443 协议)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("强迫 YouTube / Play 商店立即回退至高速稳定 TCP 隧道，彻底解决视频首包无限转圈缓冲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = config.route.isBlockQuic,
                        onCheckedChange = { viewModel.updateRoute(blockQuic = it) }
                    )
                }
            }

            // 5. Custom App Groups Section
            ConfigSectionCard(
                title = "自定义应用分流分组",
                icon = Icons.Default.Category
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "手动选择手机应用到分组，自定义走哪个流量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "严格互斥：一个应用只能归属一个组，无冗余冲突",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            newGroupName = "自定义分组 ${config.route.customGroups.size + 1}"
                            showAddGroupDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加分组")
                    }
                }

                if (config.route.customGroups.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无自定义分组",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击上方“添加分组”，可将特定应用指定走独立节点、直连或拦截",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    config.route.customGroups.forEach { group ->
                        CustomAppGroupItem(
                            group = group,
                            installedApps = uiState.installedApps,
                            availableOutbounds = uiState.availableOutbounds,
                            onRename = {
                                renameGroupName = group.name
                                groupToRename = group
                            },
                            onDelete = { viewModel.deleteCustomGroup(group.id) },
                            onEnabledChange = { viewModel.updateCustomGroupEnabled(group.id, it) },
                            onOutboundChange = { viewModel.updateCustomGroupOutbound(group.id, it) },
                            onManageApps = { activePickingGroupId = group.id }
                        )
                    }
                }
            }

            // 6. Custom Domain Groups Section
            ConfigSectionCard(
                title = "自定义域名后缀分组",
                icon = Icons.Default.Language
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "手动添加域名后缀到分组，自定义走哪个流量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "严格互斥：一个域名只能归属一个组，无冗余冲突",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            newDomainGroupName = "域名分组 ${config.route.customDomainGroups.size + 1}"
                            showAddDomainGroupDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加分组")
                    }
                }

                if (config.route.customDomainGroups.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无自定义域名分组",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击上方“添加分组”，可将特定域名（如 github.com）指定走独立节点、直连或拦截",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    config.route.customDomainGroups.forEach { group ->
                        CustomDomainGroupItem(
                            group = group,
                            availableOutbounds = uiState.availableOutbounds,
                            onRename = {
                                renameDomainGroupName = group.name
                                domainGroupToRename = group
                            },
                            onDelete = { viewModel.deleteCustomDomainGroup(group.id) },
                            onEnabledChange = { viewModel.updateCustomDomainGroupEnabled(group.id, it) },
                            onOutboundChange = { viewModel.updateCustomDomainGroupOutbound(group.id, it) },
                            onManageDomains = { activePickingDomainGroupId = group.id }
                        )
                    }
                }
            }

            // 7. Log Section
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

            // 实时生效状态与快捷跳转导出
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "配置实时生效，无需手动保存",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onNavigateToExport,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("导出与服务")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetAppRuleItem(
    category: PresetCategory,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    customOutbound: String,
    onOutboundChange: (String) -> Unit,
    availableOutbounds: List<String>
) {
    var expandedDetails by remember { mutableStateOf(false) }
    var outboundMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(category.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (enabled) {
                // Outbound selector
                val displayOutbound = when {
                    customOutbound.isBlank() -> "默认跟随主代理 (推荐)"
                    customOutbound == "direct" -> "直连 (direct - 不走代理)"
                    customOutbound == "block" -> "拦截 (block - 禁止联网)"
                    customOutbound == "proxy" -> "主代理策略 (proxy)"
                    customOutbound == "AUTO-Test" -> "自动测速优选 (AUTO-Test)"
                    else -> customOutbound
                }
                ExposedDropdownMenuBox(
                    expanded = outboundMenuExpanded,
                    onExpandedChange = { outboundMenuExpanded = !outboundMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = displayOutbound,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("指定出站节点 / 策略") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = outboundMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = outboundMenuExpanded,
                        onDismissRequest = { outboundMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认跟随主代理 (推荐)") },
                            onClick = {
                                onOutboundChange("")
                                outboundMenuExpanded = false
                            }
                        )
                        availableOutbounds.forEach { outbound ->
                            val label = when (outbound) {
                                "direct" -> "直连 (direct - 不走代理)"
                                "block" -> "拦截 (block - 禁止联网)"
                                "proxy" -> "主代理策略 (proxy)"
                                "AUTO-Test" -> "自动测速优选 (AUTO-Test)"
                                else -> outbound
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onOutboundChange(outbound)
                                    outboundMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Expand/Collapse Details Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedDetails = !expandedDetails }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (expandedDetails) "收起规则明细" else "查看包含应用与域名 (${category.apps.size} 个应用)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (expandedDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("包含应用与包名 (package_name):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        category.apps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("• ${app.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(${app.packageName})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text("包含规则集 (rule_set):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(category.ruleSets.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                        Text("核心域名后缀 (domain_suffix):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(category.domainSuffixes.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAppGroupItem(
    group: CustomAppGroup,
    installedApps: List<InstalledAppInfo>,
    availableOutbounds: List<String>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOutboundChange: (String) -> Unit,
    onManageApps: () -> Unit
) {
    var outboundMenuExpanded by remember { mutableStateOf(false) }
    val displayOutbound = when {
        group.outboundTag.isBlank() -> "默认跟随主代理 (推荐)"
        group.outboundTag == "direct" -> "直连 (direct)"
        group.outboundTag == "block" -> "拦截 (block)"
        else -> group.outboundTag
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Name, Rename, Switch, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "重命名",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = group.isEnabled,
                        onCheckedChange = onEnabledChange
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除分组",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (group.isEnabled) {
                // Outbound selector
                ExposedDropdownMenuBox(
                    expanded = outboundMenuExpanded,
                    onExpandedChange = { outboundMenuExpanded = !outboundMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = displayOutbound,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分流走向 (走哪个流量)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = outboundMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = outboundMenuExpanded,
                        onDismissRequest = { outboundMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认跟随主代理 (推荐)") },
                            onClick = {
                                onOutboundChange("")
                                outboundMenuExpanded = false
                            }
                        )
                        availableOutbounds.forEach { outbound ->
                            val label = when (outbound) {
                                "direct" -> "直连 (direct - 不走代理)"
                                "block" -> "拦截 (block - 禁止联网)"
                                "proxy" -> "主代理策略 (proxy)"
                                "AUTO-Test" -> "自动测速优选 (AUTO-Test)"
                                else -> outbound
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onOutboundChange(outbound)
                                    outboundMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Selected Apps Chips Preview
                val appNames = remember(group.packageNames, installedApps) {
                    group.packageNames.map { pkg ->
                        installedApps.find { it.packageName == pkg }?.name ?: pkg
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "已包含 ${group.packageNames.size} 款应用:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (appNames.isEmpty()) {
                        Text(
                            text = "尚未添加应用，点击下方按钮从手机应用中挑选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            appNames.take(3).forEach { name ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (appNames.size > 3) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "+${appNames.size - 3} 更多",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onManageApps,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("选择手机应用 (已选 ${group.packageNames.size} 款)")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDomainGroupItem(
    group: CustomDomainGroup,
    availableOutbounds: List<String>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOutboundChange: (String) -> Unit,
    onManageDomains: () -> Unit
) {
    var outboundMenuExpanded by remember { mutableStateOf(false) }
    val displayOutbound = when {
        group.outboundTag.isBlank() -> "默认跟随主代理 (推荐)"
        group.outboundTag == "direct" -> "直连 (direct)"
        group.outboundTag == "block" -> "拦截 (block)"
        else -> group.outboundTag
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Name, Rename, Switch, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "重命名",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = group.isEnabled,
                        onCheckedChange = onEnabledChange
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除分组",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (group.isEnabled) {
                // Outbound selector
                ExposedDropdownMenuBox(
                    expanded = outboundMenuExpanded,
                    onExpandedChange = { outboundMenuExpanded = !outboundMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = displayOutbound,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分流走向 (走哪个流量)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = outboundMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = outboundMenuExpanded,
                        onDismissRequest = { outboundMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认跟随主代理 (推荐)") },
                            onClick = {
                                onOutboundChange("")
                                outboundMenuExpanded = false
                            }
                        )
                        availableOutbounds.forEach { outbound ->
                            val label = when (outbound) {
                                "direct" -> "直连 (direct - 不走代理)"
                                "block" -> "拦截 (block - 禁止联网)"
                                "proxy" -> "主代理策略 (proxy)"
                                "AUTO-Test" -> "自动测速优选 (AUTO-Test)"
                                else -> outbound
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onOutboundChange(outbound)
                                    outboundMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Selected Domains Chips Preview
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "已包含 ${group.domainSuffixes.size} 个域名后缀:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (group.domainSuffixes.isEmpty()) {
                        Text(
                            text = "尚未添加域名，点击下方按钮添加自定义域名后缀",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.domainSuffixes.take(3).forEach { domain ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = domain,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (group.domainSuffixes.size > 3) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "+${group.domainSuffixes.size - 3} 更多",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onManageDomains,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("管理域名后缀 (已添加 ${group.domainSuffixes.size} 个)")
                    }
                }
            }
        }
    }
}

