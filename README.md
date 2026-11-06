<p align="center">
  <img src="docs/logo.png" width="128" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  A lightweight Android proxy client powered by sing-box.
</p>

<p align="center">
  <b>English</b> · <a href="README_CN.md">简体中文</a>
</p>

---

> **Beta (v0.0.1)**: still under development and may have rough edges. Please report problems in Issues.
>
> NextBox is an independent project. It is **not affiliated with or endorsed by** SagerNet / sing-box
> or MatsuriDayo / nekoray.

## Features

| | |
|---|---|
| **Protocols** | Shadowsocks, VMess, VLESS (incl. Reality / Vision), Trojan, Hysteria2, TUIC, AnyTLS, SOCKS, HTTP |
| **Subscriptions** | Base64 link lists, Clash / Clash.Meta YAML, sing-box JSON; shows used traffic and expiry |
| **Import** | Clipboard, subscription URL, share links opened from other apps (asks first), raw sing-box outbound JSON |
| **Routing** | Global / selected apps / all direct; a "bypass mainland China" switch for the first two (bundled China domain and IP rules, no download); ad blocking |
| **Latency test** | URL test (real request) and TCP test; UDP protocols such as Hysteria2 and TUIC always use the URL test; configurable URL, timeout and concurrency |
| **Notification** | Ongoing notification with live speed and session / today / month usage; disconnect, switch node or toggle the China bypass from it |
| **Quick settings tile** | Connect / disconnect with one tap |
| **Usage** | Today, this month (with a billing day), the last 30 days, and traffic per node |
| **Activity** | Live connections and the core log |
| **Modes** | VPN, or proxy-only (a local HTTP + SOCKS5 port) |
| **Appearance** | Light / dark / system, accent colors |
| **Backup** | Export / restore as JSON; the NextBox desktop app's `profiles.json` can be imported directly |

**Lightweight**: one process, only the protocols NextBox uses are compiled in, FakeIP saves a DNS round
trip, connection owners are not looked up unless asked for, the core only logs warnings, and the speed
display pauses while the screen is off. Each per-architecture APK is about 20 MB.

## Download

Get the APK for your CPU from [Releases](../../releases); most phones need `arm64-v8a`:

| File | For |
|---|---|
| `NextBox-0.0.1-beta-arm64-v8a.apk` | Almost all phones (64-bit ARM) |
| `NextBox-0.0.1-beta-armeabi-v7a.apk` | Old 32-bit ARM phones |
| `NextBox-0.0.1-beta-x86_64.apk` | Emulators, x86 tablets |

**Requires** Android 10 or later.

## Building

Needs Go 1.25+, the Android SDK (platform 36+), the Android NDK (r28+) and JDK 17+ (the one bundled with
Android Studio works).

```bash
git clone https://github.com/Efah-Dev/NextBox-Android.git
cd NextBox-Android
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
powershell -ExecutionPolicy Bypass -File core/build-core.ps1        # -> app/libs/libbox.aar
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release
```

Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)
in the repository root; without it the debug key is used. The project also opens in Android Studio.

```
core/             binds sing-box libbox, nbcore and nbtest into one AAR
  nextbox/        Go module: mobile/ is the API used by the app (nbcore); internal/ holds the data
                  store, share-link parser, subscription fetcher and sing-box config builder
  nbtest/         URL latency test with a temporary in-process sing-box instance
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / proxy services, platform interface, notification, tile
  data/           repository (wraps nbcore), local preferences
  ui/             Jetpack Compose screens
```

The core is the official, **unmodified** module `github.com/sagernet/sing-box v1.14.4`, built with the
tags `with_gvisor, with_quic, with_utls`.

## Known issues

- Updating a subscription clears the latency results of its nodes (traffic statistics are kept).
- Nodes can only be edited as JSON; a form editor and QR code import are planned.
- Per-node usage counts all traffic while that node is selected, including direct traffic when the China bypass is on.

## Credits

[SagerNet/sing-box](https://github.com/SagerNet/sing-box) (core; parts of the Android platform code follow
[sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)),
[MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray) (the original project),
[sing-geosite](https://github.com/SagerNet/sing-geosite) / [sing-geoip](https://github.com/SagerNet/sing-geoip) (rule-sets).
Developed with the help of Claude (Anthropic). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

[GPL-3.0-or-later](LICENSE).
