package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.geekdex.subout.data.db.entities.Node
import io.github.geekdex.subout.data.db.entities.NodeGroup
import io.github.geekdex.subout.domain.model.CustomAppGroup
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.InstalledAppInfo
import io.github.geekdex.subout.domain.model.RouteRuleItem
import io.github.geekdex.subout.domain.model.SimpleRouteConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteConfigTab(
    routeConfig: SimpleRouteConfig,
    installedApps: List<InstalledAppInfo>,
    nodeGroups: List<NodeGroup>,
    nodes: List<Node>,
    onUpdateRouteMode: (String) -> Unit,
    onUpdateDefaultOutbound: (String) -> Unit,
    onToggleRule: (RouteRuleItem, Boolean) -> Unit,
    onRuleOutboundChange: (RouteRuleItem, String) -> Unit,
    onReorderRules: (List<String>) -> Unit,
    onResetOrder: () -> Unit,
    onEnableRecommended: () -> Unit,
    onForceAllProxy: () -> Unit,
    onAddAppGroup: () -> Unit,
    onAddDomainGroup: () -> Unit,
    onManageGroupApps: (String) -> Unit,
    onManageGroupDomains: (String) -> Unit,
    onRenameAppGroup: (CustomAppGroup) -> Unit,
    onDeleteAppGroup: (String) -> Unit,
    onRenameDomainGroup: (CustomDomainGroup) -> Unit,
    onDeleteDomainGroup: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val initialRules = remember(routeConfig, installedApps) {
        RouteRuleItem.buildList(routeConfig)
    }

    var localRules by remember(initialRules) {
        mutableStateOf(initialRules)
    }

    var draggedRuleId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header Banner
        item(key = "header_banner") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
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
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "路由分流与规则编排",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "内核从上至下首条命中匹配 · 拖动手柄调整执行优先级",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Global Route Policy Card
        item(key = "global_policy_card") {
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
                            Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "全局路由策略",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var routeModeExpanded by remember { mutableStateOf(false) }
                    val routeModes = listOf(
                        Triple("smart", "智能分流 (绕过大陆/国内直连，推荐)", "国内网站直连，海外流量走代理节点"),
                        Triple("global", "全局代理 (所有流量走代理)", "所有非局域网流量统一经由代理节点"),
                        Triple("gfw", "GFW 列表代理 (仅被封锁域名走代理)", "命中 GFW 清单走代理，其余一律直连"),
                        Triple("direct", "全部直连 (不走代理)", "所有网络请求直接联网，完全不走代理")
                    )

                    ExposedDropdownMenuBox(
                        expanded = routeModeExpanded,
                        onExpandedChange = { routeModeExpanded = !routeModeExpanded }
                    ) {
                        OutlinedTextField(
                            value = routeModes.find { it.first == routeConfig.mode }?.second ?: routeConfig.mode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("路由总模式") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = routeModeExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = routeModeExpanded,
                            onDismissRequest = { routeModeExpanded = false }
                        ) {
                            routeModes.forEach { (modeKey, modeName, desc) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(modeName, fontWeight = FontWeight.SemiBold)
                                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        onUpdateRouteMode(modeKey)
                                        routeModeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutboundSelectorDropdown(
                        selectedOutbound = routeConfig.default_outbound,
                        onOutboundSelected = onUpdateDefaultOutbound,
                        nodeGroups = nodeGroups,
                        nodes = nodes,
                        allowDefaultFollow = false,
                        allowBlock = false,
                        label = "默认出站 (Default Outbound)"
                    )
                }
            }
        }

        // 3. Quick Action Buttons Bar
        item(key = "quick_actions_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onAddAppGroup,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("应用分流", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = onAddDomainGroup,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("域名分流", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onEnableRecommended,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("一键推荐", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onForceAllProxy,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("强制代理", style = MaterialTheme.typography.labelMedium)
                    }

                    AssistChip(
                        onClick = onResetOrder,
                        label = { Text("重置排序", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }
        }

        // 4. Rule List Section Header
        item(key = "rule_list_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "分流规则匹配列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "共 ${localRules.size} 条规则 · 拖动手柄排序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 5. Reorderable Rule Items
        itemsIndexed(
            items = localRules,
            key = { _, item -> item.id }
        ) { index, rule ->
            val isDragging = draggedRuleId == rule.id

            // Drag handle pointer input on the drag icon
            val dragModifier = Modifier.pointerInput(rule.id) {
                detectDragGestures(
                    onDragStart = {
                        draggedRuleId = rule.id
                        dragAccumulatedY = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatedY += dragAmount.y

                        val currIdx = localRules.indexOfFirst { it.id == draggedRuleId }
                        val threshold = 180f // Threshold in pixels for swapping
                        if (currIdx != -1) {
                            if (dragAccumulatedY > threshold && currIdx < localRules.size - 1) {
                                val targetIdx = currIdx + 1
                                val mutable = localRules.toMutableList()
                                val itemToMove = mutable.removeAt(currIdx)
                                mutable.add(targetIdx, itemToMove)
                                localRules = mutable
                                dragAccumulatedY -= threshold
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (dragAccumulatedY < -threshold && currIdx > 0) {
                                val targetIdx = currIdx - 1
                                val mutable = localRules.toMutableList()
                                val itemToMove = mutable.removeAt(currIdx)
                                mutable.add(targetIdx, itemToMove)
                                localRules = mutable
                                dragAccumulatedY += threshold
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        draggedRuleId = null
                        dragAccumulatedY = 0f
                        onReorderRules(localRules.map { it.id })
                    },
                    onDragCancel = {
                        draggedRuleId = null
                        dragAccumulatedY = 0f
                        onReorderRules(localRules.map { it.id })
                    }
                )
            }

            val cardModifier = Modifier
                .animateItem()
                .graphicsLayer {
                    if (isDragging) {
                        translationY = dragAccumulatedY
                    }
                }
                .zIndex(if (isDragging) 10f else 1f)

            RouteRuleCard(
                item = rule,
                index = index,
                isDragging = isDragging,
                canMoveUp = index > 0,
                canMoveDown = index < localRules.size - 1,
                installedApps = installedApps,
                nodeGroups = nodeGroups,
                nodes = nodes,
                dragHandleModifier = dragModifier,
                onMoveUp = {
                    if (index > 0) {
                        val mutable = localRules.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index - 1, item)
                        localRules = mutable
                        onReorderRules(mutable.map { it.id })
                    }
                },
                onMoveDown = {
                    if (index < localRules.size - 1) {
                        val mutable = localRules.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(index + 1, item)
                        localRules = mutable
                        onReorderRules(mutable.map { it.id })
                    }
                },
                onToggle = { isChecked ->
                    onToggleRule(rule, isChecked)
                },
                onOutboundChange = { newOutbound ->
                    onRuleOutboundChange(rule, newOutbound)
                },
                onManageApps = {
                    if (rule is RouteRuleItem.AppGroupRule) {
                        onManageGroupApps(rule.group.id)
                    }
                },
                onManageDomains = {
                    if (rule is RouteRuleItem.DomainGroupRule) {
                        onManageGroupDomains(rule.group.id)
                    }
                },
                onRename = {
                    when (rule) {
                        is RouteRuleItem.AppGroupRule -> onRenameAppGroup(rule.group)
                        is RouteRuleItem.DomainGroupRule -> onRenameDomainGroup(rule.group)
                        else -> {}
                    }
                },
                onDelete = {
                    when (rule) {
                        is RouteRuleItem.AppGroupRule -> onDeleteAppGroup(rule.group.id)
                        is RouteRuleItem.DomainGroupRule -> onDeleteDomainGroup(rule.group.id)
                        else -> {}
                    }
                },
                modifier = cardModifier
            )
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
