<p align="center">
  <img src="docs/logo.png" width="128" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  基于 sing-box 内核的轻量 Android 代理客户端
</p>

<p align="center">
  <a href="README.md">English</a> · <b>简体中文</b>
</p>

---

> **Beta 测试版（v0.0.1）**：功能还在完善中，可能有问题，欢迎在 Issues 里反馈。
>
> NextBox 是独立项目，与 SagerNet / sing-box 以及 MatsuriDayo / nekoray **没有任何隶属或背书关系**。

## 功能

| | |
|---|---|
| **协议** | Shadowsocks、VMess、VLESS（含 Reality / Vision）、Trojan、Hysteria2、TUIC、AnyTLS、SOCKS、HTTP |
| **订阅** | Base64 链接列表、Clash / Clash.Meta YAML、sing-box JSON；显示已用流量和到期时间 |
| **导入** | 剪贴板、订阅链接、点击其他应用里的分享链接（导入前会确认）、手动编辑 sing-box 出站 JSON |
| **路由** | 全局代理 / 指定应用 / 全部直连；「绕过中国大陆」开关对前两种模式生效（内置中国域名和中国 IP 规则，无需下载）；屏蔽广告 |
| **测速** | URL 测试（真实请求）和 TCP 测试；Hysteria2、TUIC 等 UDP 协议始终使用 URL 测试；可配置测试地址、超时、并发数 |
| **通知栏** | 常驻通知显示实时网速、本次 / 今日 / 本月流量，可直接断开、切换节点、切换「绕过大陆」 |
| **快捷开关** | 快捷设置磁贴一键连接 / 断开 |
| **用量** | 今日、本月（可设结算日）、最近 30 天，以及每个节点用了多少流量 |
| **活动** | 实时连接列表、内核日志 |
| **模式** | VPN 模式，或「仅代理」（只开本地 HTTP + SOCKS5 端口） |
| **外观** | 浅色 / 深色 / 跟随系统，可选主题色 |
| **备份** | 导出 / 恢复备份（JSON），也可以直接导入 NextBox 桌面版的 `profiles.json` |

**轻量**：单进程运行；只编译用到的协议；FakeIP 减少 DNS 往返；默认不查询连接所属应用；
内核日志只记警告；灭屏时停止刷新网速。单个架构的安装包约 20 MB。

## 下载

在 [Releases](../../releases) 页面下载对应 CPU 架构的安装包，一般手机选 `arm64-v8a`：

| 文件 | 适用 |
|---|---|
| `NextBox-0.0.1-beta-arm64-v8a.apk` | 绝大多数手机（64 位 ARM） |
| `NextBox-0.0.1-beta-armeabi-v7a.apk` | 老旧的 32 位 ARM 手机 |
| `NextBox-0.0.1-beta-x86_64.apk` | 模拟器、x86 平板 |

**系统要求**：Android 10 及以上。

## 快速上手

1. 「节点」页 →「添加」→ 添加订阅，或从剪贴板导入分享链接。
2. 点一个节点选中它，可以先点右上角闪电图标测速。
3. 回到「首页」点连接按钮。第一次会请求 VPN 权限；Android 13 及以上还会询问是否允许通知（用于显示网速）。
4. 想让国内网站直连：打开首页的「绕过中国大陆」。只想让部分应用走代理：选「指定应用」。

## 从源码编译

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

正式版签名：在仓库根目录放 `keystore.properties`（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），
没有时使用调试密钥签名。也可以用 Android Studio 直接打开本目录。

### 目录结构

```
core/             把 sing-box libbox、nbcore、nbtest 编译成一个 AAR
  nextbox/        Go 模块：mobile/ 是给 App 用的接口（nbcore），internal/ 是数据存储、
                  分享链接解析、订阅下载、sing-box 配置生成
  nbtest/         URL 测速：进程内临时 sing-box 实例
  build-core.ps1  gomobile 编译脚本
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / 代理服务、平台接口、通知、快捷磁贴
  data/           数据仓库（封装 nbcore）、本地设置
  ui/             Jetpack Compose 界面
```

sing-box 内核直接使用官方模块 `github.com/sagernet/sing-box v1.14.4`，**未做修改**，
编译标签：`with_gvisor, with_quic, with_utls`。

## 已知问题

- 更新订阅后，节点的测速结果会被清空（流量统计保留）。
- 节点只能以 JSON 方式编辑，表单编辑和扫码导入会在后续版本加入。
- 节点用量统计的是连接该节点期间的全部流量，开启「绕过大陆」时也包含直连部分。

## 致谢

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)：代理内核；部分 Android 平台代码参考
  [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray)：NextBox 的原项目
- [SagerNet/sing-geosite](https://github.com/SagerNet/sing-geosite)、[sing-geoip](https://github.com/SagerNet/sing-geoip)：规则集
- 由 Claude（Anthropic）协助开发

各组件的许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

[GPL-3.0-or-later](LICENSE)。

请在遵守您所在国家或地区法律法规的前提下使用本软件。
