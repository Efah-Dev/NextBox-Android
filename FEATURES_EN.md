# NextBox for Android: features and FAQ

[Back to README](README_EN.md) · [简体中文](FEATURES.md)

- [Features](#features)
- [FAQ](#faq)
- [Known issues](#known-issues)
- [What each version added](#what-each-version-added)

## Features

### Nodes and subscriptions

- **Protocols**: Shadowsocks (incl. obfs, v2ray-plugin, ShadowTLS), VMess, VLESS (incl. Reality / Vision), Trojan, Hysteria2, TUIC, AnyTLS, NaiveProxy (HTTPS and QUIC), SOCKS, HTTP; chain proxies
- **Subscriptions**: Base64 link lists, Clash / Clash.Meta YAML, sing-box JSON; used traffic and expiry date; automatic updates. Updates keep your order and add new nodes at the end
- **Clash conversion**: the groups of a subscription become policy groups, its rules become rule sets (one per target), its DNS settings DNS overrides; you tick what to take the first time (rules are off by default), and it follows every update. *Clash subscription content* in the group menu chooses again
- **Subscription DNS**: when a subscription names its provider's own DNS servers, the import offers them; they are used only for that subscription's nodes. Switch them per subscription in *Settings → Advanced → DNS → Subscription DNS*
- **Import**: QR codes, the clipboard, share links opened from other apps (confirmed first); `clash://` and `sing-box://` links too
- **Nodes**: form or JSON editing, search, multi-select delete and test, QR sharing; remove duplicates; flags before node names; drag to reorder, or sort by latency, name or region
- **Chain proxies**: 2 to 8 nodes in turn; or long-press a node and pick *Connect through other nodes*
- **Latency tests**: URL and TCP tests, UDP protocols always use the URL test. After testing a whole group the nodes can be sorted by latency, asked each time or remembered (*Settings → Test → After a test*)
- **Automatic node choice** (optional): if the selected node does not answer when connecting, the fastest working node of its group is used

### Routing

- **Route modes**: global / rule / direct, as on Windows
- **Route path**: every connection is checked from top to bottom. The first step (LAN) and the last (everything else) stay in place; ad blocking, scenes (bypass mainland China, AI services, streaming), rules and rule sets can be dragged into order, and steps that are off can be hidden
- **Rules**: sites (common services in one tap), apps (searchable, or type a package name), IPs and IP regions; rules can be named; an optional text mode
- **Rule sets**: domains, IPs, apps, site categories, IP regions and online sing-box rule-sets or Clash rule providers (YAML / text) in one named set with one target
- **Tester**: type an address or pick an app, and the path shows the step that catches it, and why
- **Bypass mainland China**: built-in Chinese domain and IP rules, no download. Known foreign sites and sites on the mainland China list are not resolved; the rest are resolved with the remote DNS through the proxy (default, never poisoned) or the direct DNS, switched with the small tag on the home page
- **Policy groups**: nodes picked by region, keywords or by hand, the fastest or a chosen one
- **Proxy scope**: all apps, only the selected apps, or all but the selected apps

### Everyday use

- **Home page**: one screen, no scrolling; once connected, the status shows the exit IP (masked, tap to show), country, network (ASN) and latency; the result is kept, so coming back does not check again
- **Swipe** between Home, Nodes, Usage, Activity and Settings
- **DNS overrides**: chosen domains answered by another DNS server, or given fixed IPs
- **Notification**: live speed, session / today / month usage; disconnect, switch nodes or the bypass from it
- **Quick settings tile**: connect / disconnect in one tap
- **Usage**: today, this month (billing day of your choice), the last 30 days, and per node
- **Activity**: live connections (with the matched rule and exit node) and the core log; tap a connection to add its site or app to a rule
- **Modes**: VPN, or proxy only (local HTTP + SOCKS5 port)
- **Appearance**: light / dark / system, custom accent colour; glass effect (can be turned off)
- **Backups**: shared with the Windows app; nodes and subscriptions, routing rules, appearance and other settings each optional; local settings such as ports and TUN stay as they are
- **Updates**: check in *About*, or turn on the automatic check (off by default)
- **Languages**: Simplified Chinese, Traditional Chinese, English

### Light

- One process, only the protocols in use compiled in
- FakeIP saves a DNS round trip through the proxy
- No lookup of the app behind each connection by default; the core logs warnings only
- The speed stops refreshing with the screen off; the exit check runs only while the home page is shown

## FAQ

**A Chinese site still goes through the proxy with the bypass on?** Sites on the mainland China list go direct; others are resolved first and judged by the region of their IP.
The default remote DNS is never poisoned, but a few Chinese sites on foreign CDNs may look foreign. Add a direct rule for it, or switch the resolving to the direct DNS on the home page (it warns first).

**What is subscription DNS?** Some providers name their own DNS servers in Clash or sing-box subscriptions. NextBox offers them on import (ticked by default);
while one of that subscription's nodes is connected they replace the remote DNS and look up its nodes' addresses. Other subscriptions are not affected.

**Do the rules of a Clash subscription replace mine?** No. They are not imported by default; when ticked they become rule sets after your own rules, and updates replace only those.

**Does a subscription update undo my order?** No. Your order stays, new nodes go to the end, nodes the subscription dropped are removed.

**Do backups work on the PC?** Yes, in both directions, with the same rule order, rule sets and rule names; from the other system only settings that mean the same are taken.

**Why is the exit info right even with only some apps in the VPN?** The exit check goes through the app's own local entrance (random port and password), always through the connected node, whatever the proxy scope.

## Known issues

- Subscriptions update automatically only while the app is open or connected.
- The form covers the common fields of each protocol; the rest need the JSON editor.
- Checking for updates while disconnected goes straight to GitHub and can fail; connect and check again.

## What each version added

- **2.1.0**: Clash conversion, subscription DNS, DNS overrides, exit IP and ASN on the home page, a one-screen home page, swiping between pages, sorting by latency after a test, updates keep the order
- **2.0.0**: routing as on Windows (route path, drag to reorder), rule sets, rule names, IP regions, the resolving choice, matched rules on the activity page, appearance in backups
- **1.1.3**: removing duplicate nodes, custom accent colours, backups compatible with Windows 2.2
