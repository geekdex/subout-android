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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
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
import io.github.geekdex.subout.domain.model.SimpleDnsConfig

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DnsConfigTab(
    dnsConfig: SimpleDnsConfig,
    onUpdateDns: (mode: String, domesticDns: String, foreignDns: String, suppressEch: Boolean) -> Unit
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
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DNS 解析与防污染防泄漏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "精准编排国内直连与国外代理域名的 DNS 解析链路",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. DNS Mode Card
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
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DNS 解析模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                var dnsModeExpanded by remember { mutableStateOf(false) }
                val dnsModes = listOf(
                    Triple("preset_fakeip", "FakeIP 模式 (推荐)", "纯 IPv4 地址池 198.18.0.0/15，防污染防泄露，极速首包响应"),
                    Triple("preset_domestic_foreign", "国内外分流模式", "国内直连 223.5.5.5，国外通过代理链路查询 DoH"),
                    Triple("fast_public", "快速公共 DNS", "直连国内公共 DNS，解析极速，无 FakeIP 映射"),
                    Triple("custom", "自定义 DNS", "手动指定国内与国外 DNS 服务器地址及协议")
                )

                ExposedDropdownMenuBox(
                    expanded = dnsModeExpanded,
                    onExpandedChange = { dnsModeExpanded = !dnsModeExpanded }
                ) {
                    OutlinedTextField(
                        value = dnsModes.find { it.first == dnsConfig.mode }?.second ?: dnsConfig.mode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("工作模式") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dnsModeExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dnsModeExpanded,
                        onDismissRequest = { dnsModeExpanded = false }
                    ) {
                        dnsModes.forEach { (modeKey, modeName, desc) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(modeName, fontWeight = FontWeight.SemiBold)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                onClick = {
                                    val foreign = when (modeKey) {
                                        "preset_fakeip" -> "fakeip"
                                        "preset_domestic_foreign" -> "https://1.1.1.1/dns-query"
                                        "fast_public" -> "223.5.5.5"
                                        else -> dnsConfig.foreign_dns
                                    }
                                    onUpdateDns(modeKey, dnsConfig.domestic_dns, foreign, dnsConfig.isSuppressEch)
                                    dnsModeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Mode Info Note
                val selectedDesc = dnsModes.find { it.first == dnsConfig.mode }?.third
                    ?: "自定义 DNS 协议，可支持 UDP、TCP、TLS (tls://) 或 DoH (https://)"
                Text(
                    text = selectedDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // 2. DNS Server Addresses Card
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
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "上游服务器配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Domestic DNS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dnsConfig.domestic_dns,
                        onValueChange = { onUpdateDns(dnsConfig.mode, it, dnsConfig.foreign_dns, dnsConfig.isSuppressEch) },
                        label = { Text("国内 DNS 服务器 (直连与局域网解析)") },
                        placeholder = { Text("例如 223.5.5.5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("223.5.5.5", "119.29.29.29", "180.76.76.76").forEach { dns ->
                            AssistChip(
                                onClick = { onUpdateDns(dnsConfig.mode, dns, dnsConfig.foreign_dns, dnsConfig.isSuppressEch) },
                                label = {
                                    val labelName = when (dns) {
                                        "223.5.5.5" -> "阿里"
                                        "119.29.29.29" -> "腾讯"
                                        else -> "百度"
                                    }
                                    Text("$dns ($labelName)", style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }

                // Foreign DNS
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dnsConfig.foreign_dns,
                        onValueChange = { onUpdateDns(dnsConfig.mode, dnsConfig.domestic_dns, it, dnsConfig.isSuppressEch) },
                        label = { Text("国外 DNS 服务器 (代理链路或 FakeIP)") },
                        placeholder = { Text("fakeip 或 https://1.1.1.1/dns-query") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Pair("fakeip", "FakeIP (推荐)"),
                            Pair("https://1.1.1.1/dns-query", "Cloudflare DoH"),
                            Pair("https://8.8.8.8/dns-query", "Google DoH")
                        ).forEach { (dns, name) ->
                            AssistChip(
                                onClick = { onUpdateDns(dnsConfig.mode, dnsConfig.domestic_dns, dns, dnsConfig.isSuppressEch) },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Security & Anti-Leak Rules Card
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
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "安全与防嗅探泄露",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "抑制 HTTPS / SVCB 查询 (防 ECH 加密)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "拦截 Type 65 查询以防 Chrome/Android 加密 SNI 破坏分流嗅探，杜绝 FakeIP 非 IP 查询报错与代理域名泄露",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = dnsConfig.isSuppressEch,
                        onCheckedChange = { onUpdateDns(dnsConfig.mode, dnsConfig.domestic_dns, dnsConfig.foreign_dns, it) }
                    )
                }

                // FakeIP non-IP smart diversion info
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
                            text = "已启用 FakeIP 非 IP 查询智能分流：任何非 A/AAAA 查询（如 PTR、TXT、SRV）会自动引流至本地 DNS，防止向 FakeIP 发起不支持查询。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom banner
    }
}
