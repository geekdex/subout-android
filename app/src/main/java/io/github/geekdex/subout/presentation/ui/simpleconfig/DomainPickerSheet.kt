package io.github.geekdex.subout.presentation.ui.simpleconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.geekdex.subout.domain.model.CustomDomainGroup
import io.github.geekdex.subout.domain.model.DomainUtils
import io.github.geekdex.subout.presentation.viewmodel.SimpleConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainPickerSheet(
    group: CustomDomainGroup,
    viewModel: SimpleConfigViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputDomainText by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("") }
    var addErrorMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // 实时检测输入域名的合法性与冲突 (当用户正在输入单个域名时给出实时警告)
    val liveConflictError = remember(inputDomainText, group.id) {
        val trimmed = inputDomainText.trim()
        if (trimmed.isEmpty() || trimmed.contains('\n') || trimmed.contains(' ') || trimmed.contains(',')) {
            null
        } else {
            viewModel.checkDomainConflict(trimmed, targetGroupId = group.id)
        }
    }

    val filteredDomains = remember(group.domainSuffixes, searchFilter) {
        val q = searchFilter.trim().lowercase()
        if (q.isEmpty()) {
            group.domainSuffixes
        } else {
            group.domainSuffixes.filter { it.lowercase().contains(q) }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空分组域名") },
            text = { Text("确定要清空「${group.name}」中的全部 ${group.domainSuffixes.size} 个域名吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateGroupDomains(group.id, emptyList())
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "管理域名后缀 - ${group.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "严格互斥：域名唯一归属，防冗余冲突与子域名重复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "关闭")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Domain Input Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputDomainText,
                        onValueChange = {
                            inputDomainText = it
                            addErrorMessages = emptyList()
                        },
                        label = { Text("输入或粘贴域名后缀") },
                        placeholder = { Text("例如: github.com 或每行一个域名") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4,
                        isError = liveConflictError != null,
                        supportingText = {
                            if (liveConflictError != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = liveConflictError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else {
                                Text(
                                    "支持 URL 自动提取、通配符清洗与多行批量粘贴",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    )

                    AnimatedVisibility(visible = addErrorMessages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "部分域名添加失败：",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                addErrorMessages.take(3).forEach { err ->
                                    Text(
                                        text = "• $err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                if (addErrorMessages.size > 3) {
                                    Text(
                                        text = "...还有 ${addErrorMessages.size - 3} 项冲突",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (inputDomainText.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    inputDomainText = ""
                                    addErrorMessages = emptyList()
                                }
                            ) {
                                Text("清空输入")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                val res = viewModel.addDomainsToGroup(group.id, inputDomainText)
                                if (res.errors.isNotEmpty()) {
                                    addErrorMessages = res.errors
                                }
                                if (res.addedCount > 0) {
                                    inputDomainText = ""
                                }
                            },
                            enabled = inputDomainText.isNotBlank() && liveConflictError == null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加到分组")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Domain List Header & Search
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已包含 ${group.domainSuffixes.size} 个域名后缀:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (group.domainSuffixes.isNotEmpty()) {
                    TextButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空全部", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (group.domainSuffixes.size > 6) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("搜索已添加的域名...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchFilter.isNotEmpty()) {
                            IconButton(onClick = { searchFilter = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Domain List
            if (filteredDomains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchFilter.isNotEmpty()) "未找到匹配的域名" else "暂无域名后缀，请在上方输入添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredDomains, key = { it }) { domain ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "匹配 $domain 及所有子域名 (*.$domain)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeDomainFromGroup(group.id, domain) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("完成")
            }
        }
    }
}
