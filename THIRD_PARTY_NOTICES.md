# Third-Party Notices

NextBox for Android is licensed under the GNU General Public License v3.0 or later (see `LICENSE`).
It includes the following third-party software.

## Native library (`libbox.so`, built by `core/build-core.ps1`)

| Component | Version | License | Source |
|---|---|---|---|
| sing-box (proxy core, `experimental/libbox`) | 1.14.5 | GPL-3.0-or-later, with an additional naming clause (see below) | https://github.com/SagerNet/sing-box/tree/v1.14.5 |
| gomobile bindings (`github.com/sagernet/gomobile`) | 0.1.12 | BSD-3-Clause | https://github.com/SagerNet/gomobile |
| cronet-go (NaiveProxy outbound, `with_naive_outbound`) | 0.0.0-20261126083600 | GPL-3.0-or-later | https://github.com/SagerNet/cronet-go |
| Cronet (Chromium network stack, statically linked by cronet-go) | as built by cronet-go | BSD-3-Clause (Chromium) | https://chromium.googlesource.com/chromium/src/+/main/components/cronet/ |
| Gorilla WebSocket (the gost-plugin relay) | 1.5.3 | BSD-2-Clause | https://github.com/gorilla/websocket |
| smux (the gost-plugin relay) | 1.5.50 | MIT | https://github.com/xtaci/smux |
| Other Go modules (see `core/go.mod` / `core/go.sum`) | — | MIT / BSD / Apache-2.0 / MPL-2.0 | — |

sing-box is used **without modification** from the official module `github.com/sagernet/sing-box v1.14.5`;
its complete corresponding source code is available at the link above. The sing-box license adds:
*"no derivative work may use the name or imply association with this application without prior consent."*
NextBox is an independent project and is **not affiliated with or endorsed by** SagerNet or the sing-box project.

## Android app

| Component | License |
|---|---|
| Jetpack Compose, AndroidX (activity, lifecycle, core, CameraX) | Apache-2.0 |
| ZXing core (QR code scanning and generation) | Apache-2.0 |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |

Parts of the Android platform integration (`bg/Network.kt`, `bg/Platform.kt`, `bg/Services.kt`) are adapted
from [sing-box-for-android](https://github.com/SagerNet/sing-box-for-android) (GPL-3.0), whose default network
listener in turn derives from [shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android) (GPL-3.0).

## Rule-set data

| Data | License | Source |
|---|---|---|
| `geosite-cn.srs`, `geoip-cn.srs` (embedded, unmodified) | See the upstream repositories | `rule-set` branches of https://github.com/SagerNet/sing-geosite and https://github.com/SagerNet/sing-geoip |
| Other geosite / geoip rule-sets (downloaded on demand) | See the upstream repositories | same as above |

## Origin

NextBox is based on NekoBox for PC (nekoray, GPL-3.0) by MatsuriDayo: https://github.com/MatsuriDayo/nekoray
