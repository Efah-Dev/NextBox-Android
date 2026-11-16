<p align="center">
  <img src="docs/logo.png" width="120" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  基于 sing-box 内核的轻量 Android 代理客户端
</p>

<p align="center">
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest"><img src="https://img.shields.io/github/v/release/Efah-Dev/NextBox-Android?label=Release&color=f472b6" alt="Release"></a>
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases"><img src="https://img.shields.io/github/downloads/Efah-Dev/NextBox-Android/total?label=Downloads&color=8b8cf8" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Go-core-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/sing--box-1.14.4-5B6678" alt="sing-box">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
</p>

<p align="center">
  <b>简体中文</b> · <a href="README_EN.md">English</a> · <a href="https://nextbox.efah.dev">官网</a> · <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest">下载</a>
</p>

<p align="center">
  <img src="docs/preview.png" alt="NextBox for Android 主页">
</p>

> NextBox 是独立项目，与 SagerNet / sing-box 以及 MatsuriDayo / nekoray **没有任何隶属或背书关系**。

## ✨ 功能

- 🔌 **协议**：Shadowsocks（含 obfs、v2ray-plugin、ShadowTLS）、VMess、VLESS（含 Reality / Vision）、Trojan、Hysteria2、TUIC、AnyTLS、NaiveProxy（HTTPS 和 QUIC）、SOCKS、HTTP；链式代理
- 📡 **订阅**：Base64 链接列表、Clash / Clash.Meta YAML、sing-box JSON；显示已用流量和到期时间；自动更新，连接中更新时节点有变化会立即生效
- 📥 **导入**：扫描二维码、剪贴板、点击其他应用里的分享链接（导入前会确认）；扫到或复制的订阅地址会添加为订阅，也认识 `clash://`、`sing-box://` 一键导入链接
- 🗂️ **节点**：表单或 JSON 编辑、搜索、多选批量删除和测速、二维码分享；节点名前显示地区国旗；拖动调整顺序，或一键按延迟、名称、地区排列（更新订阅后保持顺序）；链式代理（2 到 8 个节点依次中转，选节点时可按订阅和地区筛选、一次多选，拖动调整顺序，也可以在节点上长按「经过其他节点连接」）
- 🔀 **自动选择节点**（可选，默认关闭）：连接时当前节点不通，就换成同组里最快的可用节点；都不通时不连接并说明原因；也可以在连接期间定时检查
- 🧭 **路由**：全局代理 / 指定应用 / 全部直连；「绕过中国大陆」开关对前两种模式生效（内置中国域名和中国 IP 规则，无需下载）；屏蔽广告
- 📐 **分流规则**：一张「路由路径」图直接显示每个连接从上到下经过哪几步（屏蔽广告 → 局域网 → 屏蔽 → 直连 → 策略组 → 代理 → 绕过中国大陆 → 其余），改设置时路径跟着变；规则按去向分成直连、代理、屏蔽三页，添加时只选「加什么」和「去哪」：网站（常用服务一点即加、一次粘贴多行域名）、应用（可搜索、可手动输入包名）、IP；同一个网站写在几处时更具体的域名优先
- 🔍 **试一试**：输入网址或选一个应用，路径上标出它在哪一步被接住、为什么；「活动」页点一条连接，可以直接把它的网站或应用加进规则
- 🧩 **策略组**：按地区（直接列出你的节点所在的地区和数量）、关键字或手动挑选一组节点，自动用最快的或手动选择，规则可以指向它
- 📱 **指定应用**：只代理选中的应用，或排除选中的应用（其余都走代理），支持搜索、全选、反选
- ⚡ **测速**：URL 测试（真实请求）和 TCP 测试；Hysteria2、TUIC 等 UDP 协议始终使用 URL 测试；可配置测试地址、超时、并发数
- 🔔 **通知栏**：常驻通知显示实时网速、本次 / 今日 / 本月流量，可直接断开、切换节点、切换「绕过大陆」
- 🎛️ **快捷开关**：控制中心的快捷设置磁贴，一键连接 / 断开；未连接时显示「NextBox 未连接」，连接后显示节点名称
- 📊 **用量**：今日、本月（可设结算日）、最近 30 天，以及每个节点实际走代理的流量
- 📜 **活动**：实时连接列表、内核日志（带时间，往上翻看时不会被新日志打断，长按可复制一行）
- 🛡️ **模式**：VPN 模式，或「仅代理」（只开本地 HTTP + SOCKS5 端口）
- 🎨 **外观**：浅色 / 深色 / 跟随系统，可选主题色；玻璃效果（Android 12 起模糊，Android 13 起带折射，可关闭）
- 🌐 **语言**：简体中文、繁體中文、English
- 💾 **备份**：和 NextBox for Windows 互通——两边导出的加密备份都能互相导入；导出时可以选节点与订阅、分流规则、其他设置；导入前预览内容，勾选要导入的部分，节点可以合并或替换；电脑上调好的规则顺序和按程序名的规则原样保留，再导回电脑不会变；端口、TUN、开机自启这类本机设置不会被覆盖
- 🔄 **更新**：在「关于」里手动检查，与 GitHub Releases 的最新版本比对；也可以打开「自动检查更新」（默认关闭），有新版本时提醒
- ⚙️ **其他**：打开 App 时自动连接（可选）；异常退出后可以复制错误信息反馈

## 🪶 轻量

- 单进程运行，只编译用到的协议
- FakeIP 减少一次经代理的 DNS 往返
- 默认不查询连接所属的应用，内核日志只记警告
- 灭屏时停止刷新网速
- 单个架构的安装包约 22 MB

## 📦 下载

在 [Releases](https://github.com/Efah-Dev/NextBox-Android/releases/latest) 页面下载对应 CPU 架构的安装包，一般手机选 `arm64-v8a`：

| 文件 | 适用 |
|---|---|
| `NextBox-<版本>-arm64-v8a.apk` | 绝大多数手机（64 位 ARM） |
| `NextBox-<版本>-armeabi-v7a.apk` | 老旧的 32 位 ARM 手机 |
| `NextBox-<版本>-x86_64.apk` | 模拟器、x86 平板 |

**系统要求**：Android 10 及以上。

版本号为 `主版本.功能版本.修复版本`：第一位在重构或有重要变更时增加，第二位代表重大功能更新，第三位代表小功能和修复。

## 🚀 快速上手

1. 「节点」页 → 右下角「+」→ 添加订阅、扫描二维码，或从剪贴板导入分享链接。
2. 点一个节点选中它，可以先点右上角的闪电图标测速。
3. 回到「首页」点连接按钮。第一次会请求 VPN 权限；Android 13 及以上还会询问是否允许通知（用于显示网速）。
4. 想让国内网站直连：打开首页的「绕过中国大陆」。只想让部分应用走代理：选「指定应用」。

## 🛠️ 从源码编译

需要：Go 1.25+、Android SDK（Platform 36+）、Android NDK（r28+）、JDK 17+（Android Studio 自带即可）。

```bash
git clone https://github.com/Efah-Dev/NextBox-Android.git
cd NextBox-Android
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
```

编译内核库（输出 `app/libs/libbox.aar`），再编译 APK：

```bash
powershell -ExecutionPolicy Bypass -File core/build-core.ps1
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release
```

Linux / macOS 用 `bash core/build-core.sh` 和 `./gradlew assembleRelease`。

正式版签名：在仓库根目录放 `keystore.properties`（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），
没有时使用调试密钥签名。也可以用 Android Studio 直接打开本目录。

<details>
<summary>目录结构</summary>

```
core/             把 sing-box libbox、nbcore、nbtest 编译成一个 AAR
  nextbox/        Go 模块：mobile/ 是给 App 用的接口（nbcore），internal/ 是数据存储、
                  分享链接解析、订阅下载、sing-box 配置生成
  nbtest/         URL 测速（进程内临时 sing-box 实例）、代理流量统计
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / 代理服务、平台接口、通知、快捷磁贴
  data/           数据仓库（封装 nbcore）、本地设置、更新检查
  ui/             Jetpack Compose 界面、玻璃效果、多语言
docs/render/      README 主页渲染图的源文件
```

</details>

sing-box 内核直接使用官方模块 `github.com/sagernet/sing-box v1.14.4`，**未做修改**，
编译标签：`with_gvisor, with_quic, with_utls, with_naive_outbound`（NaiveProxy 需要把 Chromium 网络库 cronet 编进内核）。

## ⚠️ 已知问题

- 订阅自动更新只在打开 App 或连接期间进行，App 完全没运行时不会更新。
- 表单只覆盖各协议的常用字段，其余字段需要切到 JSON 编辑。
- 未连接时检查更新是直连 GitHub，网络不通时会失败，连接后再检查即可。

## 🙏 致谢

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)：代理内核；部分 Android 平台代码参考
  [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray)：NextBox 的原项目
- [SagerNet/sing-geosite](https://github.com/SagerNet/sing-geosite)、[sing-geoip](https://github.com/SagerNet/sing-geoip)：规则集
- 由 Claude（Anthropic）协助开发

各组件的许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 📄 许可证

[GPL-3.0-or-later](LICENSE)。

请在遵守您所在国家或地区法律法规的前提下使用本软件。
