package io.github.geekdex.subout.presentation.ui.nodes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("节点列表", fontWeight = FontWeight.Bold)
                        Text(
                            "共 ${uiState.rawNodes.size} 个，已启用 ${uiState.rawNodes.count { it.enabled }} 个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.testAllVisibleNodes() },
                        enabled = !uiState.isTestingAll && uiState.filteredNodes.isNotEmpty()
                    ) {
                        if (uiState.isTestingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = "一键测速")
                        }
                    }
                },
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.setAllEnabled(true) }) {
                        Text("全选", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { viewModel.setAllEnabled(false) }) {
                        Text("全禁", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { viewModel.clearLatencies() }) {
                        Text("清空延迟", style = MaterialTheme.typography.labelMedium)
                    }
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
                            onToggleEnabled = { viewModel.toggleNodeEnabled(node.id, it) },
                            onTestPing = { viewModel.testNode(node) },
                            onClick = { inspectingNode = node }
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
                TextButton(onClick = { inspectingNode = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
fun NodeItem(
    node: Node,
    isTesting: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onTestPing: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (node.enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = node.enabled,
                onCheckedChange = onToggleEnabled
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (node.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )

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
    if (isTesting) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
    } else {
        val (text, color) = when {
            latency == null -> Pair("测速", MaterialTheme.colorScheme.outline)
            latency < 160 -> Pair("${latency}ms", Color(0xFF2E7D32))
            latency < 360 -> Pair("${latency}ms", Color(0xFFEF6C00))
            else -> Pair("${latency}ms", Color(0xFFC62828))
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.15f),
            modifier = Modifier
                .clickable { onClick() }
                .padding(2.dp)
        ) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
