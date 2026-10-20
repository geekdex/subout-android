package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.RouteRuleItem
import io.github.geekdex.subout.presentation.viewmodel.SimpleConfigViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleConfigScreen(
    viewModel: SimpleConfigViewModel,
    onNavigateToExport: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val config = uiState.config
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 4 Tabs: 0: DNS配置, 1: 入站配置, 2: 路由配置 (Default/Focus), 3: 日志配置
    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 4 })

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Column {
                            Text("分流与规则配置", fontWeight = FontWeight.Bold)
                            Text(
                                "sing-box 1.10+ 标准配置生成器",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 4-Tab Navigation Bar
                val tabs = listOf(
                    Pair("DNS配置", Icons.Default.Dns),
                    Pair("入站配置", Icons.AutoMirrored.Filled.Input),
                    Pair("路由配置", Icons.AutoMirrored.Filled.AltRoute),
                    Pair("日志配置", Icons.AutoMirrored.Filled.Article)
                )

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
                ) {
                    tabs.forEachIndexed { index, (title, icon) ->
                        val isSelected = pagerState.currentPage == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Live Status & Export Button Bottom Docked Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "配置实时生效",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "无需手动保存，一键即可导出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToExport,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("导出与服务", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> DnsConfigTab(
                        dnsConfig = config.dns,
                        onUpdateDns = { mode, domestic, foreign, suppressEch ->
                            viewModel.updateDns(mode, domestic, foreign, suppressEch)
                        }
                    )
                    1 -> InboundConfigTab(
                        inboundConfig = config.inbound,
                        onUpdateInbound = { inboundType, mixedPort, allowLan, tunStack, tunAutoRoute ->
                            viewModel.updateInbound(inboundType, mixedPort, allowLan, tunStack, tunAutoRoute)
                        }
                    )
                    2 -> RouteConfigTab(
                        routeConfig = config.route,
                        installedApps = uiState.installedApps,
                        nodeGroups = uiState.nodeGroups,
                        nodes = uiState.nodes,
                        onUpdateRouteMode = { viewModel.updateRoute(mode = it) },
                        onUpdateDefaultOutbound = { viewModel.updateRoute(defaultOutbound = it) },
                        onToggleRule = { rule, isChecked ->
                            when (rule) {
                                is RouteRuleItem.SystemRule -> {
                                    when (rule.systemId) {
                                        "sys_block_quic" -> viewModel.updateRoute(blockQuic = isChecked)
                                        "sys_block_ads" -> viewModel.updateRoute(blockAds = isChecked)
                                        "sys_bypass_lan" -> viewModel.updateRoute(bypassLan = isChecked)
                                    }
                                }
                                is RouteRuleItem.PresetRule -> {
                                    when (rule.presetId) {
                                        "preset_google" -> viewModel.updateRoute(routeGoogle = isChecked)
                                        "preset_social" -> viewModel.updateRoute(routeSocial = isChecked)
                                        "preset_ai" -> viewModel.updateRoute(routeAi = isChecked)
                                    }
                                }
                                is RouteRuleItem.AppGroupRule -> {
                                    viewModel.updateCustomGroupEnabled(rule.group.id, isChecked)
                                }
                                is RouteRuleItem.DomainGroupRule -> {
                                    viewModel.updateCustomDomainGroupEnabled(rule.group.id, isChecked)
                                }
                            }
                        },
                        onRuleOutboundChange = { rule, newOutbound ->
                            when (rule) {
                                is RouteRuleItem.PresetRule -> {
                                    when (rule.presetId) {
                                        "preset_google" -> viewModel.updateRoute(googleOutbound = newOutbound)
                                        "preset_social" -> viewModel.updateRoute(socialOutbound = newOutbound)
                                        "preset_ai" -> viewModel.updateRoute(aiOutbound = newOutbound)
                                    }
                                }
                                is RouteRuleItem.AppGroupRule -> {
                                    viewModel.updateCustomGroupOutbound(rule.group.id, newOutbound)
                                }
                                is RouteRuleItem.DomainGroupRule -> {
                                    viewModel.updateCustomDomainGroupOutbound(rule.group.id, newOutbound)
                                }
                                else -> {}
                            }
                        },
                        onReorderRules = { newOrder ->
                            viewModel.reorderRules(newOrder)
                        },
                        onResetOrder = {
                            viewModel.resetRuleOrder()
                        },
                        onEnableRecommended = {
                            viewModel.enableAllRecommended()
                        },
                        onForceAllProxy = {
                            viewModel.forceAllProxy()
                        },
                        onAddAppGroup = {
                            newGroupName = "自定义分组 ${config.route.customGroups.size + 1}"
                            showAddGroupDialog = true
                        },
                        onAddDomainGroup = {
                            newDomainGroupName = "域名分组 ${config.route.customDomainGroups.size + 1}"
                            showAddDomainGroupDialog = true
                        },
                        onManageGroupApps = { groupId ->
                            activePickingGroupId = groupId
                        },
                        onManageGroupDomains = { groupId ->
                            activePickingDomainGroupId = groupId
                        },
                        onRenameAppGroup = { group ->
                            renameGroupName = group.name
                            groupToRename = group
                        },
                        onDeleteAppGroup = { groupId ->
                            viewModel.deleteCustomGroup(groupId)
                        },
                        onRenameDomainGroup = { group ->
                            renameDomainGroupName = group.name
                            domainGroupToRename = group
                        },
                        onDeleteDomainGroup = { groupId ->
                            viewModel.deleteCustomDomainGroup(groupId)
                        }
                    )
                    3 -> LogConfigTab(
                        logConfig = config.log,
                        onUpdateLog = { level, timestamp ->
                            viewModel.updateLog(level, timestamp)
                        }
                    )
                }
            }
        }
    }
}
