<p align="center">
  <img src="docs/logo.png" width="120" alt="NextBox logo">
</p>

<h1 align="center">NextBox for Android</h1>

<p align="center">
  An Android proxy client powered by sing-box, with rules shared with the Windows app
</p>

<p align="center">
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest"><img src="https://img.shields.io/github/v/release/Efah-Dev/NextBox-Android?label=Release&color=f472b6" alt="Release"></a>
  <a href="https://github.com/Efah-Dev/NextBox-Android/releases"><img src="https://img.shields.io/github/downloads/Efah-Dev/NextBox-Android/total?label=Downloads&color=8b8cf8" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/sing--box-1.14.4-5B6678" alt="sing-box">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <b>English</b> · <a href="https://nextbox.efah.dev">Website</a> · <a href="https://github.com/Efah-Dev/NextBox-Android/releases/latest">Download</a> · <a href="https://github.com/Efah-Dev/NextBox">Windows</a>
</p>

<p align="center">
  <img src="docs/preview-en.png" alt="NextBox for Android home page and route path">
</p>

NextBox for Android shares its rules and config code with [NextBox for Windows](https://github.com/Efah-Dev/NextBox):
the same rules route the same way on both, and encrypted backups move between them in both directions.

> NextBox is an independent project. It is **not affiliated with or endorsed by** SagerNet / sing-box or MatsuriDayo / nekoray.

## ✨ Highlights

| Highlight | What it means |
|---|---|
| 🧭 **Routing you can read** | The route path draws the order connections are checked in, and you drag steps into order; the tester tells which step catches a site or an app |
| 🗂️ **Rule sets** | Domains, IPs, apps, site categories, IP regions and online rule-sets in one named set with one target |
| 📡 **Subscriptions** | Base64, Clash and sing-box formats; the groups, rules and DNS of a Clash subscription can be converted too; import by QR code, clipboard or share link |
| 🌍 **DNS** | Bypassing mainland China without DNS poisoning; DNS overrides; a subscription's own DNS used only for its nodes |
| 📍 **Exit info** | The home page shows the exit IP, country and network (ASN); nodes can be sorted by latency after a test |
| 🪶 **Light and handy** | One process, FakeIP; live speed in the notification, a quick settings tile, usage per node; swipe between pages |

The full feature list and FAQ are in **[Features and FAQ](FEATURES_EN.md)**.

## 📦 Download

Get the APK for your CPU from [Releases](https://github.com/Efah-Dev/NextBox-Android/releases/latest); most phones need `arm64-v8a`:

| File | For |
|---|---|
| `NextBox-<version>-arm64-v8a.apk` | Almost all phones (64-bit ARM), about 27 MB |
| `NextBox-<version>-armeabi-v7a.apk` | Old 32-bit ARM phones |
| `NextBox-<version>-x86_64.apk` | Emulators, x86 tablets |

Requires Android 10 or later. Installs over older versions and keeps your nodes and settings.

## 🚀 Quick start

1. *Nodes* → the "+" at the bottom right → add a subscription, scan a QR code, or import share links from the clipboard.
2. Tap a node to select it; the bolt at the top right tests the latency.
3. Back on *Home*, tap the connect button. The first time asks for VPN permission; Android 13+ also asks to allow notifications (for the speed).
4. Chinese sites direct: switch on *Bypass mainland China* on the home page. Only some apps through the proxy: *Proxy scope* → *Only selected apps*.

## 🛠️ Build from source

Requires Go 1.25+, the Android SDK (Platform 36+), the Android NDK (r28+) and JDK 17+ (the one in Android Studio works).

```bash
git clone https://github.com/Efah-Dev/NextBox-Android.git
cd NextBox-Android
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
powershell -ExecutionPolicy Bypass -File core/build-core.ps1        # the core library app/libs/libbox.aar
powershell -ExecutionPolicy Bypass -File build-apk.ps1 -Release     # the APKs
```

On Linux / macOS use `bash core/build-core.sh` and `./gradlew assembleRelease`. For release signing put `keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) in the repository root; without it the debug key is used.

sing-box is the official module `github.com/sagernet/sing-box v1.14.4`, **unmodified**, with the build tags `with_gvisor, with_quic, with_utls, with_naive_outbound`.

<details>
<summary>Project layout</summary>

```
core/             Builds sing-box libbox, nbcore and nbtest into one AAR
  nextbox/        Go module: mobile/ is the API for the app (nbcore); internal/ is the code shared
                  with the Windows app (rules, config builder, subscriptions, Clash conversion,
                  backup format), synced by scripts/sync-android.ps1 in the Windows repository
  nbtest/         URL tests (an in-process sing-box instance), traffic statistics
app/src/main/java/dev/efah/nextbox/
  bg/             VPN / proxy service, platform interface, notification, quick settings tile
  data/           Repository (wraps nbcore), local preferences, update check
  ui/             Jetpack Compose UI, glass effect, translations
docs/render/      Source of the README images (drawn in HTML, rendered with headless Chrome / Edge)
```

</details>

## 🙏 Acknowledgements

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box): the proxy core; some Android platform code follows [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [MatsuriDayo/nekoray](https://github.com/MatsuriDayo/nekoray): the project NextBox continues
- [SagerNet/sing-geosite](https://github.com/SagerNet/sing-geosite), [sing-geoip](https://github.com/SagerNet/sing-geoip): rule-sets
- Developed with the help of Claude (Anthropic)

Licenses of the components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## 📄 License

[GPL-3.0-or-later](LICENSE). Use this software in compliance with the laws of your country or region.
