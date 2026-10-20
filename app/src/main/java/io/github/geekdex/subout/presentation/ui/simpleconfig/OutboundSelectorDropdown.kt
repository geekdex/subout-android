package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutboundSelectorDropdown(
    selectedOutbound: String,
    onOutboundSelected: (String) -> Unit,
    nodeGroups: List<NodeGroup>,
    nodes: List<Node>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: String = "指定出站 / 分流走向",
    allowDefaultFollow: Boolean = false,
    allowBlock: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val displayInfo = remember(selectedOutbound, nodeGroups, nodes, allowDefaultFollow) {
        when {
            selectedOutbound.isBlank() -> {
                if (allowDefaultFollow) "默认跟随主代理 (推荐)" else "未指定出站 (走默认)"
            }
            selectedOutbound == "AUTO-Test" -> "自动测速优选 (AUTO-Test)"
            selectedOutbound == "proxy" -> "主代理策略 (proxy)"
            selectedOutbound == "direct" -> "直连 (direct - 不走代理)"
            selectedOutbound == "block" -> "拦截 (block - 禁止联网)"
            else -> {
                val group = nodeGroups.find { it.name == selectedOutbound }
                if (group != null) {
                    "组合: ${group.name} (${group.nodeTags.size} 节点)"
                } else {
                    val node = nodes.find { it.tag == selectedOutbound }
                    if (node != null) {
                        "节点: ${node.tag} (${node.protocol})"
                    } else {
                        selectedOutbound
                    }
                }
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayInfo,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 380.dp)
        ) {
            // Section 1: 系统策略
            Text(
                text = "系统策略",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            if (allowDefaultFollow) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    text = {
                        Column {
                            Text("默认跟随主代理 (推荐)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("跟随全局默认路由出站策略", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    trailingIcon = if (selectedOutbound.isBlank()) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        onOutboundSelected("")
                        expanded = false
                    }
                )
            }

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                text = {
                    Column {
                        Text("自动测速优选 (AUTO-Test)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("优选所有可用节点中延迟最低者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                trailingIcon = if (selectedOutbound == "AUTO-Test") {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = {
                    onOutboundSelected("AUTO-Test")
                    expanded = false
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                text = {
                    Column {
                        Text("主代理策略 (proxy)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("在 sing-box / SFA 客户端由用户手动选择节点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                trailingIcon = if (selectedOutbound == "proxy") {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = {
                    onOutboundSelected("proxy")
                    expanded = false
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF2E7D32)
                    )
                },
                text = {
                    Column {
                        Text("直连 (direct)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("直接联网，不通过代理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                },
                trailingIcon = if (selectedOutbound == "direct") {
                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                onClick = {
                    onOutboundSelected("direct")
                    expanded = false
                }
            )

            if (allowBlock) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Column {
                            Text("拦截 (block)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("直接阻断联网请求", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    trailingIcon = if (selectedOutbound == "block") {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        onOutboundSelected("block")
                        expanded = false
                    }
                )
            }

            // Section 2: 节点组合 (Node Groups)
            if (nodeGroups.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "节点组合 (${nodeGroups.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                nodeGroups.forEach { group ->
                    val isSelected = selectedOutbound == group.name
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${group.nodeTags.size} 个节点 · 测速: ${group.testUrlName.ifBlank { "Google 204" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            onOutboundSelected(group.name)
                            expanded = false
                        }
                    )
                }
            }

            // Section 3: 单个节点 (Single Nodes)
            if (nodes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "单个节点 (${nodes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                nodes.forEach { node ->
                    val isSelected = selectedOutbound == node.tag
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = node.tag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${node.protocol.uppercase()} · ${node.server}:${node.serverPort}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            onOutboundSelected(node.tag)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
