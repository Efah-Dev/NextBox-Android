<p align="center">
  <img src="docs/logo.png" width="120" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  基于 sing-box 内核的 Android 代理客户端，规则与 Windows 版通用
</p>

<p align="center">
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest"><img src="https://img.shields.io/github/v/release/Efah-Dev/NextBox-Android?label=Release&color=f472b6" alt="Release"></a>
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases"><img src="https://img.shields.io/github/downloads/Efah-Dev/NextBox-Android/total?label=Downloads&color=8b8cf8" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/sing--box-1.14.5-5B6678" alt="sing-box">
  <a href="https://t.me/nextboxofficial"><img src="https://img.shields.io/badge/Telegram-nextboxofficial-26A5E4?logo=telegram&logoColor=white" alt="Telegram"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
</p>

<p align="center">
  <b>简体中文</b> · <a href="README_EN.md">English</a> · <a href="https://nextbox.efah.dev">官网</a> · <a href="https://t.me/nextboxofficial">Telegram 频道</a> · <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest">下载</a> · <a href="https://github.com/Efah-Dev/NextBox">Windows 版</a>
</p>

<p align="center">
  <img src="docs/preview.png" alt="NextBox for Android 首页与路由路径">
</p>

NextBox for Android 和 [NextBox for Windows](https://github.com/Efah-Dev/NextBox) 用的是同一套规则和配置生成代码，
同样的规则在手机和电脑上分流结果一致，加密备份可以两边互相导入。

> NextBox 是独立项目，与 SagerNet / sing-box 以及 MatsuriDayo / nekoray 没有隶属或背书关系。

## 2.3.0 更新

**新增**

- 可以直接用分组代理连接：首页显示它正在用的节点，落地 IP 前标出分组名称，通知和快捷磁贴同步显示；流量仍按实际节点统计
- 自动选最快的分组代理可以设置测试间隔，和 Windows 版共用
- 「关于」里加入 Telegram 频道

**改进**

- 新安装默认使用 URL 测试
- 部分设置说明重写，更简短准确
- 用分组代理连接时，「自动选节点」暂停
- 内核升级到 sing-box 1.14.5

**修复**

- 首页落地信息里的运营商名称没有去掉 Inc.、Ltd. 等公司后缀

完整记录见 [Releases](https://github.com/Efah-Dev/NextBox-Android/releases)。

## 功能

**分流**

- 路由路径：屏蔽广告、绕过中国大陆、规则和规则集按顺序排列，拖动即可调整
- 「试一试」：输入网址或选一个应用，看它在哪一步被接住
- 规则集：域名、IP、应用、网站分类、IP 归属地和网上的规则集归到一起，统一指定去向
- 代理范围：所有应用、只代理选中的，或排除选中的
- 分组代理：按地区、关键字或手动挑选，自动选最快或手动指定

**订阅与节点**

- Base64、Clash、sing-box 格式的订阅；Clash 订阅的分组、规则和 DNS 可以一起转换
- 扫码、剪贴板、点击分享链接导入
- 延迟测试，测完可以按延迟排序；长按拖动排序
- 订阅自带的专属 DNS 只用于它自己的节点

**日常使用**

- 首页一屏看完：连接按钮、落地 IP、国家和所属网络（ASN）
- 通知栏实时网速和流量，快捷磁贴一键开关
- 单进程、FakeIP，省电不折腾
- 密码加密的备份，和 Windows 版互相导入

所有功能和常见问题见 [功能与常见问题](FEATURES.md)。

## 下载安装

在 [Releases](https://github.com/Efah-Dev/NextBox-Android/releases/latest) 下载对应架构的安装包，一般手机选 `arm64-v8a`：

| 文件 | 适用 |
|---|---|
| `NextBox-<版本>-arm64-v8a.apk` | 绝大多数手机 |
| `NextBox-<版本>-armeabi-v7a.apk` | 较老的 32 位手机 |
| `NextBox-<版本>-x86_64.apk` | 模拟器、x86 平板 |

需要 Android 10 及以上。可以直接覆盖安装旧版本，节点和设置都会保留。

## 上手

1. 「节点」页点右下角「+」，添加订阅、扫码，或从剪贴板导入分享链接。
2. 点一个节点选中它，可以先点右上角的闪电图标测一下。
3. 回到首页点连接按钮。第一次会请求 VPN 权限，Android 13 及以上还会询问通知权限（用来显示网速）。
4. 想让国内网站直连，打开首页的「绕过中国大陆」；只想让部分应用走代理，在「代理范围」里选「只代理选中的」。

## 交流

- Telegram 频道：[t.me/nextboxofficial](https://t.me/nextboxofficial)，发布新版本和使用说明
- 遇到问题或有建议，请提 [Issue](https://github.com/Efah-Dev/NextBox-Android/issues)

## 从源码编译

需要 Go 1.25+、Android SDK（Platform 36+）、Android NDK（r28+）、JDK 17+（用 Android Studio 自带的即可）。

```bash
git clone https://github.com/Efah-Dev/NextBox-Android.git
cd NextBox-Android
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
powershell -ExecutionPolicy Bypass -File core/build-core.ps1        # 内核库 app/libs/libbox.aar
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release     # APK
```

Linux / macOS 用 `bash core/build-core.sh` 和 `./gradlew assembleRelease`。正式版签名：在仓库根目录放 `keystore.properties`
（`storeFile`、`storePassword`、`keyAlias`、`keyPassword`），没有时使用调试密钥。

sing-box 直接使用官方模块 `github.com/sagernet/sing-box v1.14.5`，没有修改，编译标签为 `with_gvisor, with_quic, with_utls, with_naive_outbound`。

<details>
<summary>目录结构</summary>

```
core/             把 sing-box libbox、nbcore、nbtest 编译成一个 AAR
  nextbox/        Go 模块：mobile/ 是给 App 用的接口（nbcore）；internal/ 是和 Windows 版
                  共用的代码（规则、配置生成、订阅、Clash 转换、备份格式），由 Windows 仓库的
                  scripts/sync-android.ps1 同步
  nbtest/         URL 延迟测试（进程内临时 sing-box 实例）、代理流量统计
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / 代理服务、平台接口、通知、快捷磁贴
  data/           数据仓库（封装 nbcore）、本地设置、更新检查
  ui/             Jetpack Compose 界面、玻璃效果、多语言
docs/render/      README 主页图的源文件（HTML 绘制，用无头 Chrome / Edge 渲染）
```

</details>

## 致谢

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)：代理内核；部分 Android 平台代码参考了 [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray)：NextBox 的原项目
- [SagerNet/sing-geosite](https://github.com/SagerNet/sing-geosite)、[sing-geoip](https://github.com/SagerNet/sing-geoip)：规则集
- 由 Claude（Anthropic）协助开发

各组件的许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

[GPL-3.0-or-later](LICENSE)。请在遵守所在国家或地区法律法规的前提下使用。
