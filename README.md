# Subout Android

> 基于现有 Rust + Web 桌面版 subout 项目移植的 Android 原生应用，专注于**小白模式 (Simple Mode)**，为 **sing-box For Android (SFA)** 提供配置生成与本地配置导入服务。

---

## 📱 核心定位

**配置生成器 + 临时配置服务器**，而非重复造轮子的代理客户端内核。Subout Android 专注于高效的订阅管理、节点解析、延迟测速与 sing-box 1.10+ 标准配置生成，通过**本地临时 HTTP 服务**或**文件分享**一键无缝导入至 sing-box For Android (SFA) 运行。

---

## ✨ 核心特性

### 1. 📡 订阅管理 (`SubscriptionsView`)
- **多种源格式支持**：支持 HTTP/HTTPS 远程订阅、Base64 编码订阅、单行或多行明文 URI 粘贴导入。
- **智能节点清洗**：自动过滤流量提醒、到期通知、官网入口等公告垃圾节点。
- **标签冲突消歧**：同名节点自动加序号（如 `HK-01`, `HK-01-2`），保障 sing-box tag 唯一性。
- **抓取日志与详情**：查看每次同步的节点数、状态消息与时间戳。
- **一键全量同步**：在控制中心或订阅页一键并发刷新所有已启用的订阅源。

### 2. 🌐 节点管理与测速 (`NodesView`)
- **多协议全支持**：解析 VMess、VLESS (含 Reality & flow)、Shadowsocks、Trojan、Hysteria、Hysteria2、SOCKS5、HTTP 等协议。
- **TCP 并发测速**：一键对所有可见/启用节点进行轻量 TCP 握手测速，毫秒级反映节点响应速度。
- **多维筛选与排序**：按协议类型筛选（如只看 VLESS 或 Hysteria2）、按延迟高低排序、按名称字母排序。
- **节点详情与 JSON 查看**：点击任一节点可直接查看并复制其在 sing-box 中的完整 outbound JSON 定义。

### 3. ⚙️ 分流与规则配置 (`SimpleConfigView`)
- **DNS 配置**：
  - 模式切换：FakeIP 模式 (推荐，纯 IPv4 地址池 `198.18.0.0/15` 防泄漏)、国内外分流模式、快速公共 DNS、自定义 DNS。
  - 国内/国外 DNS 地址与 Detour 代理链路自动编排。
- **入站配置**：
  - TUN 模式（虚拟网卡接管，支持 system/gvisor/mixed 堆栈与 Auto Route 开关）。
  - Mixed 混合端口模式（HTTP/SOCKS5 混合代理，支持设置端口及局域网设备共享）。
- **路由规则配置**：
  - 路由模式：智能分流 (绕过大陆，国内直连国外代理)、全局代理、GFW 列表代理、全部直连。
  - 拦截广告开关（集成 `geosite-category-ads-all` 二进制规则集阻断）。
  - 绕过局域网私有 IP 流量开关。
  - 默认出站选择：`AUTO-Test` 自动测速优选组、`proxy` 手动选择、`direct` 直连或指定节点。
- **常用应用分流 (所见即所得)**：
  - Google 全家桶 (Play 商店、GMS、YouTube 等) 独立出站路由。
  - 海外社交应用 (X/Twitter、Telegram、Instagram 等) 独立出站路由。
  - 热门 AI 助手 (ChatGPT、Claude、Gemini 等) 独立出站路由。
  - 阻断 QUIC (UDP 443) 规避视频首包卡顿。
- **自定义应用分流分组**：
  - 手动添加手机已安装应用到分组，自定义走指定代理节点、直连或拦截。
  - 严格互斥：一个应用唯一归属一个组，无冗余冲突。
- **自定义域名后缀分组**：
  - 手动添加域名后缀（如 `github.com`、`epicgames.com`）到自定义分组，指定独立出站节点或策略。
  - 严格互斥与层级冲突防范：同一域名及父子域名层级全局互斥，自动阻止与预设规则或其它组的重复与冗余覆盖。
  - 同步生成精准 DNS 规则，杜绝 DNS 泄漏。
- **日志配置**：日志级别 (trace/debug/info/warn/error) 与时间戳开关。

### 4. 🚀 配置导出与 SFA 联动 (`ExportView`) ⭐ **核心亮点**
- **本地临时 HTTP 服务（推荐）**：
  - 在 Android 设备本地启动无依赖轻量 HTTP 服务（如 `http://127.0.0.1:8888/config`）。
  - 端口被占用时自动递增重试（8888 ➔ 8889 ➔ 8890）。
  - 生成动态二维码与一键复制链接，供 SFA 扫描或从 URL 导入。
  - **优势**：无需申请 Android 任何存储或外部文件读写权限，完全符合 Android 14+ 最新安全沙箱要求。
- **文件导出与分享**：生成 `sing-box.json` 并通过系统 FileProvider 直接调用 Intent 分享至 `io.nekohasekai.sfa`。
- **实时配置预览**：支持在应用内直接查看生成的完整 JSON 格式树，并支持一键复制配置内容。

---

## 🛠️ 技术架构

```
app/
├── data/
│   ├── db/                          # Room 2.7+ 数据库持久化
│   │   ├── AppDatabase.kt
│   │   ├── dao/                     # SubscriptionDao, NodeDao
│   │   └── entities/                # Subscription, Node
│   ├── network/
│   │   └── SubscriptionFetcher.kt   # OkHttp 网络抓取与重定向处理
│   └── repository/                  # 数据仓库层 (Flow 响应式更新)
├── domain/
│   ├── generator/
│   │   ├── ConfigExporter.kt        # 公共存储目录文件导出 (Download/subout)
│   │   └── SimpleConfigGenerator.kt # sing-box 1.10+ 标准配置组装器
│   ├── model/                       # ProxyNode, SimpleConfig 领域模型
│   ├── parser/
│   │   └── ProxyParser.kt           # 跨平台 URI/Base64/协议解析器
│   └── tester/
│       └── NodeTester.kt            # TCP / TLS 握手延迟测试
└── presentation/
    ├── ui/                          # Jetpack Compose (Material 3)
    │   ├── dashboard/               # 控制中心
    │   ├── subscriptions/           # 订阅管理
    │   ├── nodes/                   # 节点列表、批量管理与测速
    │   ├── simpleconfig/            # 小白模式配置表单
    │   ├── export/                  # 配置文件导出与 JSON 预览
    │   └── MainScreen.kt            # 底部导航栏与主界面
    └── viewmodel/                   # MVVM 状态流 (StateFlow)
```

---

## 📖 与 SFA (sing-box For Android) 协作使用教程

### 导出并从本地导入 SFA (稳定推荐)

1. 在 Subout Android 的【订阅】页面添加机场订阅并点击【同步】。
2. 在【配置】页面按需选择（默认已配置好最推荐的 FakeIP + TUN + 智能分流）。
3. 切换至【导出】页面，点击【导出到下载目录 (Download/subout)】。
   - 文件将保存至系统公共下载目录：`Download/subout/sing-box.json`。
4. 打开 **sing-box For Android (SFA)** 客户端：
   - 切换到底部【Profiles / 配置文件】标签页。
   - 点击【New Profile / 新建配置】。
   - 类型选择【Local / 本地】或【Import / 导入】。
   - 使用系统文件选择器浏览至 `下载 (Download) -> subout -> sing-box.json` 并选中。
   - 保存配置。
5. 在 SFA 中点击开关启动代理即可！

---

## 📦 编译与打包

### 环境需求
- JDK 17+
- Android SDK 26+ (编译目标 Target SDK 37, 最低运行 Min SDK 26)
- Gradle 9.5+ (已包含在 Gradle Wrapper `gradlew` 中)

### 构建命令

```bash
# 运行单元测试
./gradlew test

# 编译 Debug APK
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk

# 编译已签名的 Release APK
./gradlew assembleRelease
# 输出: app/build/outputs/apk/release/app-release.apk
```

---

## 📄 配置文件样例

请查阅根目录下的 [`example-sing-box.json`](./example-sing-box.json)，该文件由测试套件依据内置协议真实节点完整组装生成，包含标准：
- `log` (信息输出与时间戳)
- `dns` (`dns_local` 223.5.5.5 + `dns_fakeip` 198.18.0.0/15 + 广告拦截 + 域名分流)
- `inbounds` (`tun-in` 172.19.0.1/30 auto_route)
- `outbounds` (`direct`, `block`, `proxy` 策略组, `AUTO-Test` urltest 自动优选, 节点列表)
- `route` (sniff, hijack-dns, QUIC 拦截, Google Play/GMS 包名与 geosite-google 专项代理, 规则集 geosite-cn, geosite-geolocation-!cn, geoip-cn 等)
- `experimental`
