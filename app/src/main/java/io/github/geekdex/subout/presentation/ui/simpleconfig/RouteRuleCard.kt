package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup
import io.github.geekdex.subout.domain.model.InstalledAppInfo
import io.github.geekdex.subout.domain.model.RouteRuleItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteRuleCard(
    item: RouteRuleItem,
    index: Int,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    installedApps: List<InstalledAppInfo>,
    nodeGroups: List<NodeGroup>,
    nodes: List<Node>,
    dragHandleModifier: Modifier = Modifier,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onOutboundChange: (String) -> Unit,
    onManageApps: () -> Unit,
    onManageDomains: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = item.isEnabled

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.surfaceVariant
                !enabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isDragging) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
            )
        } else {
            CardDefaults.outlinedCardBorder()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Priority Badge + Type Tag + Title + Move Up/Down + Switch + Drag Handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (index == 0) Color(0xFFFFECB3) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = if (index == 0) "#1 优先" else "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Category Tag
                val (tagLabel, tagColor, tagContainer) = when (item) {
                    is RouteRuleItem.SystemRule -> Triple("🛡️ 流量控制", Color(0xFF2E7D32), Color(0xFFE8F5E9))
                    is RouteRuleItem.PresetRule -> Triple("⭐ 常用预设", Color(0xFFE65100), Color(0xFFFFF3E0))
                    is RouteRuleItem.AppGroupRule -> Triple("📱 应用分流", Color(0xFF6A1B9A), Color(0xFFF3E5F5))
                    is RouteRuleItem.DomainGroupRule -> Triple("🌐 域名分流", Color(0xFF0277BD), Color(0xFFE1F5FE))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tagContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Rule Title
                val title = when (item) {
                    is RouteRuleItem.SystemRule -> item.title
                    is RouteRuleItem.PresetRule -> item.category.title
                    is RouteRuleItem.AppGroupRule -> item.group.name
                    is RouteRuleItem.DomainGroupRule -> item.group.name
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Move Up / Move Down buttons for precise one-tap reordering
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "向上移动优先级",
                        tint = if (canMoveUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "向下移动优先级",
                        tint = if (canMoveDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Switch
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.size(width = 46.dp, height = 28.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Tactile Drag Handle Icon with specific drag pointer input
                Box(
                    modifier = dragHandleModifier
                        .size(36.dp)
                        .background(
                            color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "拖拽调整匹配顺序",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Summary description
            val summaryText = when (item) {
                is RouteRuleItem.SystemRule -> item.summary
                is RouteRuleItem.PresetRule -> item.category.summary
                is RouteRuleItem.AppGroupRule -> "包含 ${item.group.packageNames.size} 款应用 · 严格互斥去重"
                is RouteRuleItem.DomainGroupRule -> "包含 ${item.group.domainSuffixes.size} 个域名后缀 · 防父子域名冲突"
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // Dynamic Content based on item type
            when (item) {
                is RouteRuleItem.SystemRule -> {
                    // System rules do not require outbound selector (they have fixed targets: reject or direct)
                }

                is RouteRuleItem.PresetRule -> {
                    if (enabled) {
                        OutboundSelectorDropdown(
                            selectedOutbound = item.outbound,
                            onOutboundSelected = onOutboundChange,
                            nodeGroups = nodeGroups,
                            nodes = nodes,
                            allowDefaultFollow = true,
                            allowBlock = true,
                            label = "指定出站节点 / 组合策略"
                        )

                        var showDetails by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDetails = !showDetails }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (showDetails) "收起规则明细" else "查看包含应用与域名 (${item.category.apps.size} 款应用)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("包含应用与包名 (package_name):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                item.category.apps.forEach { app ->
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
                                Text(item.category.ruleSets.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                                Text("核心域名后缀 (domain_suffix):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(item.category.domainSuffixes.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                is RouteRuleItem.AppGroupRule -> {
                    if (enabled) {
                        OutboundSelectorDropdown(
                            selectedOutbound = item.group.outboundTag,
                            onOutboundSelected = onOutboundChange,
                            nodeGroups = nodeGroups,
                            nodes = nodes,
                            allowDefaultFollow = true,
                            allowBlock = true,
                            label = "分流走向 (走哪个出站)"
                        )

                        // App chips preview
                        val appNames = remember(item.group.packageNames, installedApps) {
                            item.group.packageNames.map { pkg ->
                                installedApps.find { it.packageName == pkg }?.name ?: pkg
                            }
                        }

                        if (appNames.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                appNames.take(3).forEach { name ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (appNames.size > 3) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "+${appNames.size - 3} 更多",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons row: Select Apps, Rename, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onManageApps,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("选择应用 (${item.group.packageNames.size})", style = MaterialTheme.typography.labelMedium)
                            }

                            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "重命名",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除分组",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                is RouteRuleItem.DomainGroupRule -> {
                    if (enabled) {
                        OutboundSelectorDropdown(
                            selectedOutbound = item.group.outboundTag,
                            onOutboundSelected = onOutboundChange,
                            nodeGroups = nodeGroups,
                            nodes = nodes,
                            allowDefaultFollow = true,
                            allowBlock = true,
                            label = "分流走向 (走哪个出站)"
                        )

                        // Domain chips preview
                        if (item.group.domainSuffixes.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item.group.domainSuffixes.take(3).forEach { domain ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (item.group.domainSuffixes.size > 3) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "+${item.group.domainSuffixes.size - 3} 更多",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons row: Manage Domains, Rename, Delete
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onManageDomains,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("管理域名 (${item.group.domainSuffixes.size})", style = MaterialTheme.typography.labelMedium)
                            }

                            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "重命名",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除分组",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
