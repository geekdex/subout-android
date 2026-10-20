package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.domain.model.SimpleInboundConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InboundConfigTab(
    inboundConfig: SimpleInboundConfig,
    onUpdateInbound: (inboundType: String, mixedPort: Int, allowLan: Boolean, tunStack: String, tunAutoRoute: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Header Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Input,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "流量入站方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "控制 SFA 如何接管 Android 系统的网络连接与流量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Inbound Type Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "入站模式选择",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                var inboundExpanded by remember { mutableStateOf(false) }
                val inboundTypes = listOf(
                    Triple("tun", "TUN 模式 (推荐，生成虚拟网卡配置)", "由 SFA 创建 Android VpnService 虚拟网卡，全系统无感接管"),
                    Triple("mixed", "Mixed 端口 (HTTP/SOCKS5 混合代理)", "仅开启本地代理端口，需手动在 Wi-Fi 或应用设置代理")
                )

                ExposedDropdownMenuBox(
                    expanded = inboundExpanded,
                    onExpandedChange = { inboundExpanded = !inboundExpanded }
                ) {
                    OutlinedTextField(
                        value = inboundTypes.find { it.first == inboundConfig.inbound_type }?.second ?: inboundConfig.inbound_type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("入站类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inboundExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = inboundExpanded,
                        onDismissRequest = { inboundExpanded = false }
                    ) {
                        inboundTypes.forEach { (typeKey, typeName, desc) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(typeName, fontWeight = FontWeight.SemiBold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = {
                                    onUpdateInbound(typeKey, inboundConfig.mixed_port, inboundConfig.allow_lan, inboundConfig.tun_stack, inboundConfig.tun_auto_route)
                                    inboundExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Specific Settings Card (TUN or Mixed)
        if (inboundConfig.inbound_type == "tun") {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TUN 虚拟网卡高级参数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var stackExpanded by remember { mutableStateOf(false) }
                    val stacks = listOf(
                        Triple("mixed", "mixed (推荐，混合内核与虚拟栈)", "TCP走系统原生内核保障极速吞吐，UDP/DNS走 gVisor 独立处理杜绝丢包"),
                        Triple("system", "system (纯系统原生栈)", "全量依靠系统内核栈，速度极快，但部分定制 ROM 的 UDP 会异常中断"),
                        Triple("gvisor", "gvisor (Google 虚拟网络栈)", "纯用户态实现，兼容性最广泛，但 CPU 与内存开销略高")
                    )

                    ExposedDropdownMenuBox(
                        expanded = stackExpanded,
                        onExpandedChange = { stackExpanded = !stackExpanded }
                    ) {
                        OutlinedTextField(
                            value = stacks.find { it.first == inboundConfig.tun_stack }?.second ?: inboundConfig.tun_stack,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("TUN 堆栈 (Stack)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stackExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = stackExpanded,
                            onDismissRequest = { stackExpanded = false }
                        ) {
                            stacks.forEach { (stackKey, stackTitle, stackDesc) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stackTitle, fontWeight = FontWeight.SemiBold)
                                            Text(stackDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        onUpdateInbound(inboundConfig.inbound_type, inboundConfig.mixed_port, inboundConfig.allow_lan, stackKey, inboundConfig.tun_auto_route)
                                        stackExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "提示：Android SFA 黄金标准是 mixed 栈。系统内核负责 TCP 吞吐，gVisor 独立负责 UDP/DNS 解析，既省电又杜绝了权限受限。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("自动配置路由 (Auto Route)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("自动接管系统路由表，将匹配流量引导至虚拟网卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = inboundConfig.tun_auto_route,
                            onCheckedChange = { onUpdateInbound(inboundConfig.inbound_type, inboundConfig.mixed_port, inboundConfig.allow_lan, inboundConfig.tun_stack, it) }
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "分配虚拟网段: 172.19.0.1/30 (sing-box tun-in 默认网段)",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        } else {
            // Mixed Port Settings
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mixed 端口设置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = inboundConfig.mixed_port.toString(),
                            onValueChange = {
                                val p = it.toIntOrNull() ?: inboundConfig.mixed_port
                                onUpdateInbound(inboundConfig.inbound_type, p, inboundConfig.allow_lan, inboundConfig.tun_stack, inboundConfig.tun_auto_route)
                            },
                            label = { Text("监听端口 (Mixed Port)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(2080, 7890, 10808).forEach { port ->
                                AssistChip(
                                    onClick = { onUpdateInbound(inboundConfig.inbound_type, port, inboundConfig.allow_lan, inboundConfig.tun_stack, inboundConfig.tun_auto_route) },
                                    label = { Text("端口 $port", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("允许局域网连接 (Allow LAN)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("监听 0.0.0.0，允许同 Wi-Fi 或热点设备通过此手机代理上网", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = inboundConfig.allow_lan,
                            onCheckedChange = { onUpdateInbound(inboundConfig.inbound_type, inboundConfig.mixed_port, it, inboundConfig.tun_stack, inboundConfig.tun_auto_route) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
