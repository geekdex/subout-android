package io.github.geekdex.subout.presentation.ui.nodes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.google.gson.JsonParser
import io.github.geekdex.subout.domain.model.ManualVlessConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.presentation.viewmodel.NodeSortOption
import io.github.geekdex.subout.presentation.viewmodel.NodesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(
    viewModel: NodesViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var inspectingNode by remember { mutableStateOf<Node?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }

    // 删除弹窗状态
    var showDeleteTimeoutConfirm by remember { mutableStateOf(false) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var nodeToDelete by remember { mutableStateOf<Node?>(null) }
    var showAddNodeDialog by remember { mutableStateOf(false) }

    val timeoutCount = remember(uiState.rawNodes) { uiState.rawNodes.count { it.isTimeout } }
    val disabledCount = remember(uiState.rawNodes) { uiState.rawNodes.count { !it.enabled } }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "已选 ${uiState.selectedNodeIds.size} 项",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        val isAllSelected = uiState.selectedNodeIds.size == uiState.filteredNodes.size && uiState.filteredNodes.isNotEmpty()
                        IconButton(onClick = {
                            if (isAllSelected) viewModel.clearSelection() else viewModel.selectAllFilteredNodes()
                        }) {
                            Icon(
                                if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (isAllSelected) "取消全选" else "全选"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteSelectedConfirm = true },
                            enabled = uiState.selectedNodeIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除选中项",
                                tint = if (uiState.selectedNodeIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showSelectionMoreMenu = true },
                                enabled = uiState.selectedNodeIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "批量操作")
                            }
                            DropdownMenu(
                                expanded = showSelectionMoreMenu,
                                onDismissRequest = { showSelectionMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("批量启用 (${uiState.selectedNodeIds.size})") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        viewModel.setSelectedNodesEnabled(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("批量禁用 (${uiState.selectedNodeIds.size})") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        viewModel.setSelectedNodesEnabled(false)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("节点列表", fontWeight = FontWeight.Bold)
                            Text(
                                if (uiState.isTestingAll && uiState.totalTestingCount > 0) {
                                    val finished = uiState.totalTestingCount - uiState.testingNodeIds.size
                                    "测速中 ($finished/${uiState.totalTestingCount})..."
                                } else {
                                    val countDesc = "共 ${uiState.rawNodes.size} 个，已启用 ${uiState.rawNodes.count { it.enabled }} 个"
                                    if (timeoutCount > 0) "$countDesc · 超时 $timeoutCount" else countDesc
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.isTestingAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (uiState.isTestingAll) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = { viewModel.cancelBatchTesting() }) {
                                    Text("停止", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.testAllVisibleNodes() },
                                enabled = uiState.filteredNodes.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = "一键测速")
                            }
                        }

                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (timeoutCount > 0) "清理超时节点 ($timeoutCount)" else "清理超时节点",
                                            color = if (timeoutCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = if (timeoutCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        if (timeoutCount > 0) {
                                            showDeleteTimeoutConfirm = true
                                        } else {
                                            viewModel.deleteTimeoutNodes()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("批量管理") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Checklist, contentDescription = null)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.enterSelectionMode()
                                    },
                                    enabled = uiState.filteredNodes.isNotEmpty()
                                )
                                if (disabledCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("清理已禁用节点 ($disabledCount)") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.deleteDisabledNodes()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { showAddNodeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加节点")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("搜索节点名称或地址...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Protocol Filter Chips
            val protocols = listOf("ALL", "vmess", "vless", "shadowsocks", "trojan", "hysteria", "hysteria2", "socks", "http")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                protocols.forEach { proto ->
                    val label = if (proto == "ALL") "全部协议" else proto.uppercase()
                    FilterChip(
                        selected = uiState.selectedProtocol.equals(proto, ignoreCase = true),
                        onClick = { viewModel.setSelectedProtocol(proto) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Action Toolbar (Sort & Batch switches)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    AssistChip(
                        onClick = { showSortMenu = true },
                        label = {
                            val sortLabel = when (uiState.sortOption) {
                                NodeSortOption.DEFAULT -> "默认排序"
                                NodeSortOption.LATENCY -> "延迟优先"
                                NodeSortOption.NAME -> "名称排序"
                                NodeSortOption.PROTOCOL -> "协议排序"
                            }
                            Text("排序: $sortLabel")
                        }
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认排序") },
                            onClick = {
                                viewModel.setSortOption(NodeSortOption.DEFAULT)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("按延迟优先") },
                            onClick = {
                                viewModel.setSortOption(NodeSortOption.LATENCY)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("按节点名称") },
                            onClick = {
                                viewModel.setSortOption(NodeSortOption.NAME)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("按协议类型") },
                            onClick = {
                                viewModel.setSortOption(NodeSortOption.PROTOCOL)
                                showSortMenu = false
                            }
                        )
                    }
                }

                TextButton(
                    onClick = { viewModel.clearLatencies() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "清空测速",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Node List
            if (uiState.filteredNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (uiState.rawNodes.isEmpty()) "暂无节点，请先在【订阅】中添加或同步" else "未找到符合条件的节点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredNodes, key = { it.id }) { node ->
                        NodeItem(
                            node = node,
                            isTesting = uiState.testingNodeIds.contains(node.id),
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedNodeIds.contains(node.id),
                            onToggleSelection = { viewModel.toggleNodeSelection(node.id) },
                            onTestPing = { viewModel.testNode(node) },
                            onClick = { inspectingNode = node },
                            onLongClick = { viewModel.enterSelectionMode(node.id) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Inspect Node JSON Dialog
    inspectingNode?.let { node ->
        AlertDialog(
            onDismissRequest = { inspectingNode = null },
            title = {
                Text(
                    text = node.tag,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "协议: ${node.protocol.uppercase()} | 端口: ${node.serverPort}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "服务器: ${node.server}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (node.enabled) "节点状态：已启用" else "节点状态：已禁用",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (node.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        androidx.compose.material3.Switch(
                            checked = node.enabled,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleNodeEnabled(node.id, isChecked)
                                inspectingNode = node.copy(enabled = isChecked)
                            }
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = node.rawJson,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Node JSON", node.rawJson))
                    inspectingNode = null
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制 JSON")
                }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { nodeToDelete = node }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除节点", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { inspectingNode = null }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    // 单节点删除确认对话框
    nodeToDelete?.let { node ->
        val usages = remember(node) { viewModel.checkNodeUsage(setOf(node.tag)) }
        AlertDialog(
            onDismissRequest = { nodeToDelete = null },
            title = { Text("删除节点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确定要删除节点「${node.tag}」吗？删除后无法恢复。")
                    if (usages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "⚠️ 提示：该节点正在以下规则中作为出站使用：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                usages.forEach { usage ->
                                    Text(
                                        text = "• $usage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "删除后，上述规则出站将自动重置为「直连 (direct)」。",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNode(node)
                        nodeToDelete = null
                        if (inspectingNode?.id == node.id) {
                            inspectingNode = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 清理超时节点确认对话框
    if (showDeleteTimeoutConfirm) {
        val timeoutNodes = remember(uiState.rawNodes) { uiState.rawNodes.filter { it.isTimeout } }
        val timeoutUsages = remember(timeoutNodes) {
            viewModel.checkNodeUsage(timeoutNodes.map { it.tag }.toSet())
        }
        AlertDialog(
            onDismissRequest = { showDeleteTimeoutConfirm = false },
            title = { Text("清理超时节点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("检测到当前有 $timeoutCount 个连接超时或不可达的节点，确定要全部删除吗？删除后无法恢复。")
                    if (timeoutUsages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "⚠️ 提示：其中包含正在配置中使用的节点，涉及规则：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                timeoutUsages.forEach { usage ->
                                    Text(
                                        text = "• $usage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "删除后，上述规则出站将自动重置为「直连 (direct)」。",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTimeoutNodes()
                        showDeleteTimeoutConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除 ($timeoutCount)", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteTimeoutConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 批量删除选中节点确认对话框
    if (showDeleteSelectedConfirm) {
        val count = uiState.selectedNodeIds.size
        val selectedNodes = remember(uiState.selectedNodeIds, uiState.rawNodes) {
            uiState.rawNodes.filter { it.id in uiState.selectedNodeIds }
        }
        val selectedUsages = remember(selectedNodes) {
            viewModel.checkNodeUsage(selectedNodes.map { it.tag }.toSet())
        }
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text("批量删除节点") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确定要删除选中的 $count 个节点吗？删除后无法恢复。")
                    if (selectedUsages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "⚠️ 提示：选中的节点中包含正在配置中使用的节点，涉及规则：",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                selectedUsages.forEach { usage ->
                                    Text(
                                        text = "• $usage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "删除后，上述规则出站将自动重置为「直连 (direct)」。",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedNodes()
                        showDeleteSelectedConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除 ($count)", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddNodeDialog) {
        AddVlessNodeDialog(
            onDismiss = { showAddNodeDialog = false },
            onSave = { config ->
                viewModel.addManualNode(config) { success, _ ->
                    if (success) {
                        showAddNodeDialog = false
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NodeItem(
    node: Node,
    isTesting: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onTestPing: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = when {
        isSelectionMode && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        node.enabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelection() else onClick()
                },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.tag,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (node.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!node.enabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            modifier = Modifier.height(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "已禁用",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProtocolBadge(protocol = node.protocol)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${node.server}:${node.serverPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isSelectionMode) {
                // Latency Pill & Ping Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LatencyBadge(
                        latency = node.latency,
                        isTesting = isTesting,
                        onClick = onTestPing
                    )
                }
            }
        }
    }
}

@Composable
fun ProtocolBadge(protocol: String) {
    val (bg, fg) = when (protocol.lowercase()) {
        "vmess" -> Pair(Color(0xFFE3F2FD), Color(0xFF1976D2))
        "vless" -> Pair(Color(0xFFE8F5E9), Color(0xFF388E3C))
        "shadowsocks", "ss" -> Pair(Color(0xFFFFF3E0), Color(0xFFF57C00))
        "trojan" -> Pair(Color(0xFFF3E5F5), Color(0xFF7B1FA2))
        "hysteria", "hysteria2" -> Pair(Color(0xFFFFEBEE), Color(0xFFD32F2F))
        else -> Pair(Color(0xFFECEFF1), Color(0xFF455A64))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        modifier = Modifier.height(18.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = protocol.uppercase(),
                color = fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LatencyBadge(
    latency: Int?,
    isTesting: Boolean,
    onClick: () -> Unit
) {
    val (text, color, bgColor) = when {
        isTesting -> Triple(
            "测试中",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
        latency == null -> Triple(
            "测速",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
        latency == Node.LATENCY_TIMEOUT -> Triple(
            "超时",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        )
        latency < 160 -> Triple(
            "${latency}ms",
            Color(0xFF2E7D32),
            Color(0xFF2E7D32).copy(alpha = 0.12f)
        )
        latency < 360 -> Triple(
            "${latency}ms",
            Color(0xFFEF6C00),
            Color(0xFFEF6C00).copy(alpha = 0.12f)
        )
        else -> Triple(
            "${latency}ms",
            Color(0xFFC62828),
            Color(0xFFC62828).copy(alpha = 0.12f)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .then(
                if (!isTesting) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = color
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVlessNodeDialog(
    onDismiss: () -> Unit,
    onSave: (ManualVlessConfig) -> Unit
) {
    var tag by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("443") }
    var uuid by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("xtls-rprx-vision") }
    var tlsEnabled by remember { mutableStateOf(true) }
    var serverName by remember { mutableStateOf("") }
    var utlsEnabled by remember { mutableStateOf(true) }
    var utlsFingerprint by remember { mutableStateOf("chrome") }
    var realityEnabled by remember { mutableStateOf(true) }
    var realityPublicKey by remember { mutableStateOf("") }
    var realityShortId by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pasteSuccessHint by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // 从剪贴板一键快速提取与填入参数 (支持 sing-box JSON 与 vless:// 链接)
    fun parseFromClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (clipText.isNullOrEmpty()) {
                errorMessage = "剪贴板为空"
                return
            }

            if (clipText.startsWith("{") || clipText.contains("\"type\"")) {
                val json = JsonParser.parseString(clipText).asJsonObject
                json.get("tag")?.asString?.let { tag = it }
                json.get("server")?.asString?.let { server = it }
                json.get("server_port")?.asInt?.let { serverPort = it.toString() }
                json.get("uuid")?.asString?.let { uuid = it }
                flow = json.get("flow")?.asString ?: ""

                val tlsObj = json.getAsJsonObject("tls")
                if (tlsObj != null) {
                    tlsEnabled = tlsObj.get("enabled")?.asBoolean ?: true
                    serverName = tlsObj.get("server_name")?.asString ?: ""

                    val utlsObj = tlsObj.getAsJsonObject("utls")
                    if (utlsObj != null) {
                        utlsEnabled = utlsObj.get("enabled")?.asBoolean ?: true
                        utlsFingerprint = utlsObj.get("fingerprint")?.asString ?: "chrome"
                    } else {
                        utlsEnabled = false
                    }

                    val realityObj = tlsObj.getAsJsonObject("reality")
                    if (realityObj != null) {
                        realityEnabled = realityObj.get("enabled")?.asBoolean ?: true
                        realityPublicKey = realityObj.get("public_key")?.asString ?: ""
                        realityShortId = realityObj.get("short_id")?.asString ?: ""
                    } else {
                        realityEnabled = false
                    }
                } else {
                    tlsEnabled = false
                    realityEnabled = false
                    utlsEnabled = false
                }
                errorMessage = null
                pasteSuccessHint = "已从剪贴板 JSON 快速填入参数！"
            } else if (clipText.startsWith("vless://", ignoreCase = true)) {
                val parsed = io.github.geekdex.subout.domain.parser.ProxyParser.parseGenericUri(clipText)
                if (parsed != null) {
                    uuid = parsed.userInfo ?: ""
                    server = parsed.host
                    serverPort = (parsed.port ?: 443).toString()
                    tag = parsed.fragment ?: "${parsed.host}:${serverPort}"
                    flow = parsed.queryParams["flow"] ?: ""
                    val sec = parsed.queryParams["security"] ?: ""
                    tlsEnabled = sec.isNotEmpty() && sec != "none"
                    serverName = parsed.queryParams["sni"] ?: parsed.queryParams["server_name"] ?: ""
                    realityPublicKey = parsed.queryParams["pbk"] ?: parsed.queryParams["public_key"] ?: ""
                    realityShortId = parsed.queryParams["sid"] ?: parsed.queryParams["short_id"] ?: ""
                    realityEnabled = realityPublicKey.isNotEmpty()
                    utlsFingerprint = parsed.queryParams["fp"] ?: "chrome"
                    utlsEnabled = utlsFingerprint.isNotEmpty()
                    errorMessage = null
                    pasteSuccessHint = "已从剪贴板 VLESS 链接快速填入参数！"
                } else {
                    errorMessage = "未能识别剪贴板中的 VLESS 链接"
                }
            } else {
                errorMessage = "剪贴板内容不是有效的 sing-box JSON 或 vless:// 链接"
            }
        } catch (e: Exception) {
            errorMessage = "解析剪贴板出错: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("手动添加 VLESS 节点", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 剪贴板快速填入按钮
                OutlinedButton(
                    onClick = { parseFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从剪贴板快速填入 (JSON / 链接)")
                }

                pasteSuccessHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 节点名称
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it; errorMessage = null },
                    label = { Text("节点名称 (Tag) *") },
                    placeholder = { Text("例如：us_self") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 服务器地址与端口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it; errorMessage = null },
                        label = { Text("服务器地址 *") },
                        placeholder = { Text("IP 或域名") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it; errorMessage = null },
                        label = { Text("端口 *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 用户 UUID
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it; errorMessage = null },
                    label = { Text("用户 ID (UUID) *") },
                    placeholder = { Text("例如：c04d6b79-0803-4cf1-8d76-3cc759ef79a0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 流控
                OutlinedTextField(
                    value = flow,
                    onValueChange = { flow = it },
                    label = { Text("流控 (Flow)") },
                    placeholder = { Text("xtls-rprx-vision (留空则无)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // TLS 基础设置
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用 TLS", fontWeight = FontWeight.Medium)
                    Switch(
                        checked = tlsEnabled,
                        onCheckedChange = { tlsEnabled = it }
                    )
                }

                if (tlsEnabled) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it },
                        label = { Text("SNI (Server Name)") },
                        placeholder = { Text("例如：www.bing.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // uTLS 指纹伪装
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("uTLS 指纹伪装", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = utlsEnabled,
                            onCheckedChange = { utlsEnabled = it }
                        )
                    }

                    if (utlsEnabled) {
                        OutlinedTextField(
                            value = utlsFingerprint,
                            onValueChange = { utlsFingerprint = it },
                            label = { Text("Fingerprint") },
                            placeholder = { Text("chrome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Reality 协议
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reality 安全协议", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = realityEnabled,
                            onCheckedChange = { realityEnabled = it }
                        )
                    }

                    if (realityEnabled) {
                        OutlinedTextField(
                            value = realityPublicKey,
                            onValueChange = { realityPublicKey = it; errorMessage = null },
                            label = { Text("Reality 公钥 (Public Key) *") },
                            placeholder = { Text("例如：hDeF0usWcAOgKqZiygeQG8yPQGurjEhXXRNewBVkuy8") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { realityShortId = it },
                            label = { Text("Reality 简短 ID (Short ID)") },
                            placeholder = { Text("例如：b803b45ccaced487") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tag.trim().isBlank()) {
                        errorMessage = "节点名称 (Tag) 不能为空"
                        return@Button
                    }
                    if (server.trim().isBlank()) {
                        errorMessage = "服务器地址不能为空"
                        return@Button
                    }
                    val port = serverPort.trim().toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        errorMessage = "请输入有效的端口号 (1-65535)"
                        return@Button
                    }
                    if (uuid.trim().isBlank()) {
                        errorMessage = "用户 ID (UUID) 不能为空"
                        return@Button
                    }
                    if (tlsEnabled && realityEnabled && realityPublicKey.trim().isBlank()) {
                        errorMessage = "开启 Reality 时公钥 (Public Key) 不能为空"
                        return@Button
                    }

                    val config = ManualVlessConfig(
                        tag = tag.trim(),
                        server = server.trim(),
                        serverPort = port,
                        uuid = uuid.trim(),
                        flow = flow.trim(),
                        tlsEnabled = tlsEnabled,
                        serverName = serverName.trim(),
                        utlsEnabled = utlsEnabled,
                        utlsFingerprint = utlsFingerprint.trim().ifEmpty { "chrome" },
                        realityEnabled = realityEnabled,
                        realityPublicKey = realityPublicKey.trim(),
                        realityShortId = realityShortId.trim()
                    )
                    onSave(config)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
