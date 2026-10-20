package io.github.geekdex.subout.presentation.ui.nodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SearchFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedProtocol: String,
    onProtocolChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    sortLabel: String,
    onSortClick: () -> Unit,
    onClearLatencies: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Input and Collapse Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("搜索节点...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.height(24.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(56.dp)
                    .height(56.dp)
            ) {
                IconButton(
                    onClick = { onExpandedChange(!isExpanded) }
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起筛选" else "展开筛选",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // Expandable Filter Options
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Protocol Filters
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "协议筛选",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val protocols = listOf("ALL", "vmess", "vless", "shadowsocks", "trojan", "hysteria", "hysteria2", "socks", "http")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        protocols.forEach { proto ->
                            val label = if (proto == "ALL") "全部协议" else proto.uppercase()
                            FilterChip(
                                selected = selectedProtocol.equals(proto, ignoreCase = true),
                                onClick = { onProtocolChange(proto) },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.height(40.dp)
                            )
                        }
                    }
                }

                // Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = onSortClick,
                        label = { Text(sortLabel, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    )

                    TextButton(
                        onClick = onClearLatencies,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            "清空测速",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
