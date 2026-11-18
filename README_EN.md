<p align="center">
  <img src="docs/logo.png" width="120" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  A lightweight Android proxy client powered by sing-box
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
  <a href="README.md">简体中文</a> · <b>English</b> · <a href="https://nextbox.efah.dev">Website</a> · <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest">Download</a>
</p>

<p align="center">
  <img src="docs/preview-en.png" alt="NextBox for Android home screen">
</p>

> NextBox is an independent project. It is **not affiliated with or endorsed by** SagerNet / sing-box
> or MatsuriDayo / nekoray.

## ✨ Features

- 🔌 **Protocols**: Shadowsocks (incl. obfs, v2ray-plugin, ShadowTLS), VMess, VLESS (incl. Reality / Vision), Trojan, Hysteria2, TUIC, AnyTLS, NaiveProxy (HTTPS and QUIC), SOCKS, HTTP; chain proxies
- 📡 **Subscriptions**: Base64 link lists, Clash / Clash.Meta YAML, sing-box JSON; shows used traffic and expiry; automatic updates that leave out duplicates within a subscription, and node changes take effect at once while connected
- 📥 **Import**: QR code scan, clipboard, share links opened from other apps (asks first); a scanned or copied subscription address is added as a subscription, and `clash://` / `sing-box://` one-click links work too
- 🗂️ **Nodes**: Form or JSON editor, search, multi-select for batch delete and tests, QR code sharing; remove duplicate nodes across all groups in one tap; region flags in front of node names; drag to reorder, or sort by latency, name or region in one tap (kept across subscription updates); chain proxies (2 to 8 nodes in a row, picked several at once and filtered by subscription and region, drag to reorder, or long-press a node and pick "Connect through other nodes")
- 🔀 **Automatic node selection** (optional, off by default): when the selected node does not answer on connecting, the fastest working node of its group takes over; if none works it does not connect and says why; can also check while connected
- 🧭 **Routing**: global / rule-based / all direct, as in NextBox for Windows; a "bypass mainland China" switch on the home screen (bundled China domain and IP rules, no download); ad blocking
- 📐 **Routing rules**: the route of NextBox for Windows: every connection is checked from top to bottom and leaves at the first step that matches. The first step (local network) and the last (everything else) are fixed; ad blocking, the scenes (bypass mainland China, AI services, streaming, game downloads direct), rules and rule sets in between are dragged into any order, and steps that are off can be hidden. Adding a rule asks what and where: websites (common services in one tap; Steam as store and downloads), apps (searchable, or a package name typed by hand), IPs and IP regions (every country and region, searchable by name, code or pinyin initials); rules can have names; an optional text mode with one rule per line
- 🗂️ **Rule sets**: put domains, IPs, apps, site categories, IP regions and sing-box rule-sets from the web (.srs / .json URLs) into one named rule set with one target, pasting many at once; a rule-set URL can be downloaded once to check it
- 🔍 **Try it**: type a website or pick an app and the route marks the step that catches it, and why; as the core does, a domain is resolved with the direct DNS before the steps that match IP addresses; the Activity page shows the rule each connection matched and its exit node, and a tap adds its website or app to the rules
- 🧩 **Policy groups**: nodes picked by region (the regions your nodes are in, with how many each), keyword or by hand, the fastest used automatically or chosen by hand; rules can send traffic to them
- 📱 **Apps**: all apps, only the selected ones, or all but the selected ones (every other app uses the VPN); search, select all, invert
- ⚡ **Latency test**: URL test (real request) and TCP test; UDP protocols such as Hysteria2 and TUIC always use the URL test; configurable URL, timeout and concurrency
- 🔔 **Notification**: Ongoing notification with live speed and session / today / month usage; disconnect, switch node or toggle the China bypass from it
- 🎛️ **Quick settings tile**: Connect / disconnect with one tap; shows "NextBox not connected" while off and the node's name while connected
- 📊 **Usage**: Today, this month (with a billing day), the last 30 days, and the proxied traffic of each node
- 📜 **Activity**: Live connections and the core log (with times; new lines do not pull you down while you read older ones; long-press to copy a line)
- 🛡️ **Modes**: VPN, or proxy-only (a local HTTP + SOCKS5 port)
- 🎨 **Appearance**: Light / dark / system, accent colors from presets or your own (color panel, HEX; shared with the Windows version through backups); glass effects (blur on Android 12+, refraction on 13+, can be turned off)
- 🌐 **Languages**: 简体中文, 繁體中文, English
- 💾 **Backup**: interchangeable with NextBox for Windows: either side imports the other's encrypted backups; export nodes and subscriptions, routing rules and other settings, or any of them; a preview before importing, with the parts to take and nodes added or replacing the list; the rule order, rule sets and rule names are the same on both, and program rules of the computer are kept, so they go back unchanged; this device's own settings (ports, TUN, start at login) stay
- 🔄 **Updates**: Check by hand in About (compares with the latest GitHub release), or turn on the automatic check (off by default) to be told about a new version
- ⚙️ **More**: connect when the app opens (optional); after a crash the error details can be copied for a report

## 🪶 Lightweight

- One process, only the protocols NextBox uses are compiled in
- FakeIP saves a DNS round trip through the proxy
- Connection owners are not looked up unless asked for, and the core only logs warnings
- The speed display pauses while the screen is off
- Each per-architecture APK is about 27 MB (arm64-v8a)

## 📦 Download

Get the APK for your CPU from [Releases](https://github.com/Efah-Dev/NextBox-Android/releases/latest); most phones need `arm64-v8a`:

| File | For |
|---|---|
| `NextBox-<version>-arm64-v8a.apk` | Almost all phones (64-bit ARM) |
| `NextBox-<version>-armeabi-v7a.apk` | Old 32-bit ARM phones |
| `NextBox-<version>-x86_64.apk` | Emulators, x86 tablets |

**Requires** Android 10 or later.

Versions are `major.feature.fix`: the first number changes with a rewrite or other important change, the
second with a major feature update, the third with small features and fixes.

## 🚀 Getting started

1. Nodes → the "+" button → add a subscription, scan a QR code, or import share links from the clipboard.
2. Tap a node to select it; the bolt icon at the top right runs a latency test first.
3. Go back to Home and tap the connect button. The first time Android asks for the VPN permission, and on
   Android 13+ for the notification permission (used to show the speed).
4. To reach Chinese sites directly, turn on "Bypass mainland China" on the home screen (it switches to rule-based
   routing). To proxy only some apps, open "Apps" on the home screen and pick "Only selected".

## 🛠️ Building

Needs Go 1.25+, the Android SDK (platform 36+), the Android NDK (r28+) and JDK 17+ (the one bundled with
Android Studio works).

```bash
git clone https://github.com/Efah-Dev/NextBox-Android.git
cd NextBox-Android
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
```

Build the core library (`app/libs/libbox.aar`), then the APKs:

```bash
powershell -ExecutionPolicy Bypass -File core/build-core.ps1
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release
```

On Linux / macOS use `bash core/build-core.sh` and `./gradlew assembleRelease`.

Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
in the repository root; without it the debug key is used. The project also opens in Android Studio.

<details>
<summary>Project layout</summary>

```
core/             binds sing-box libbox, nbcore and nbtest into one AAR
  nextbox/        Go module: mobile/ is the API used by the app (nbcore); internal/ holds the data
                  store, share-link parser, subscription fetcher and sing-box config builder
  nbtest/         URL latency test (a temporary in-process sing-box instance), proxied traffic counters
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / proxy services, platform interface, notification, tile
  data/           repository (wraps nbcore), local preferences, update check
  ui/             Jetpack Compose screens, glass effects, languages
docs/render/      source of the README preview image
```

</details>

The core is the official, **unmodified** module `github.com/sagernet/sing-box v1.14.4`, built with the
tags `with_gvisor, with_quic, with_utls, with_naive_outbound` (NaiveProxy links Chromium's network library, cronet, into the core).

## ⚠️ Known issues

- Subscriptions update automatically only when the app is opened or while connected.
- The form covers the common fields of each protocol; edit the rest as JSON.
- While disconnected the update check reaches GitHub directly and fails if GitHub is unreachable; check
  again once connected.

## 🙏 Credits

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box): the proxy core; parts of the Android platform
  code follow [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray): the project NextBox started from
- [SagerNet/sing-geosite](https://github.com/SagerNet/sing-geosite), [sing-geoip](https://github.com/SagerNet/sing-geoip): rule-sets
- Developed with the help of Claude (Anthropic)

Component licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## 📄 License

[GPL-3.0-or-later](LICENSE).

Please use this software in compliance with the laws of your country or region.
