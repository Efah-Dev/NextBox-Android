package dev.efah.nextbox.data

import dev.efah.nextbox.ui.T
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/** JSON shapes produced by nbcore (same as the Windows data files). */

val NbJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class SubUsage(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
)

@Serializable
data class Group(
    val id: Long,
    val name: String,
    val url: String = "",
    val userAgent: String = "",
    val updatedAt: Long = 0,
    val usage: SubUsage? = null,
    val default: Boolean = false,
    /** The parts of a Clash subscription taken over on updates; null until asked. */
    val clash: ClashImport? = null,
    /** The subscription's own DNS (订阅专属 DNS); null = the global one. */
    val dns: GroupDns? = null,
    /** The DNS the subscription names, as of its last update (offered in Settings → 高级 → DNS). */
    val suggestedDns: GroupDns? = null,
) {
    val isSubscription: Boolean get() = url.isNotEmpty()
    val displayName: String get() = if (default) T("默认分组") else name
}

@Serializable
data class Profile(
    val id: Long = 0,
    val groupId: Long = 0,
    val name: String = "",
    val type: String = "",
    val outbound: JsonObject = JsonObject(emptyMap()),
    val latency: Int = 0,
    val latencyError: String = "",
    val testedAt: Long = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val latencyKind: String = "",
    /** Member node ids of a chain proxy, entry first. */
    val chain: List<Long> = emptyList(),
    /** Members of a chain proxy as nbcore describes them (not saved back). */
    val members: List<ChainHop> = emptyList(),
    /** ISO code of the node's region, from the desktop app's detection or the name. */
    val region: String = "",
) {
    val isChain: Boolean get() = type == "chain"
}

@Serializable
data class ChainHop(val id: Long, val name: String = "", val type: String = "", val groupId: Long = 0, val missing: Boolean = false)

@Serializable
data class Rule(
    val kind: String,
    val values: List<String> = emptyList(),
    val target: String = "proxy",
    val disabled: Boolean = false,
    /** The service of the desktop app's catalog the rule was set from ("youtube"); kept for the way back. */
    val app: String = "",
    /** What the user calls the rule (shown on the activity page); every rule set (kind "set") has one. */
    val name: String = "",
    /** A rule set's contents: values of several kinds, any of which matches; all go to [target]. */
    val items: List<SetItem> = emptyList(),
    /** The subscription whose Clash rules made this rule set (0 = the user); its updates replace it. */
    val from: Long = 0,
) {
    val isStep: Boolean get() = kind == "ads" || kind == "scene"
    val isSet: Boolean get() = kind == "set"

    /** Whether the rule matches anything yet (values, or a rule set's items). */
    val filled: Boolean get() = if (isSet) items.any { it.values.isNotEmpty() } else values.isNotEmpty()
}

/** One kind of values in a rule set (store.RuleItem); kind "url" is a sing-box rule-set at an address. */
@Serializable
data class SetItem(val kind: String, val values: List<String> = emptyList())

/** One node kept and its copies that go (store.DupSet). */
@Serializable
data class DupSet(val keep: Profile, val drop: List<Profile> = emptyList(), val running: Boolean = false)

@Serializable
data class Scene(val id: String, val enabled: Boolean = false, val target: String = "proxy")

@Serializable
data class Policy(
    val id: Long = 0,
    val name: String = "",
    val match: String = "country", // country | keyword | manual
    val country: String = "",
    val keyword: String = "",
    val members: List<Long> = emptyList(),
    val groupId: Long = 0,
    val mode: String = "auto", // auto | select
    val selected: Long = 0,
    /** The subscription whose Clash proxy group made it (0 = the user). */
    val from: Long = 0,
)

@Serializable
data class PolicyNode(val id: Long, val name: String = "", val type: String = "", val latency: Int = 0, val region: String = "")

@Serializable
data class PolicyView(
    val id: Long = 0,
    val name: String = "",
    val match: String = "country",
    val country: String = "",
    val keyword: String = "",
    val members: List<Long> = emptyList(),
    val groupId: Long = 0,
    val mode: String = "auto",
    val selected: Long = 0,
    val nodes: List<PolicyNode> = emptyList(),
    val rules: Int = 0,
) {
    fun toPolicy() = Policy(id, name, match, country, keyword, members, groupId, mode, selected)
}

@Serializable
data class Place(val code: String, val name: String)

@Serializable
data class Classified(val kind: String = "none", val url: String = "", val name: String = "", val count: Int = 0, val likelyProxy: Boolean = false)

@Serializable
data class RouteMatch(
    val input: String = "",
    /** Step of the route: lan, ads, rule ([rule] is its index), scene ([scene]) or final. */
    val step: String = "",
    val scene: String = "",
    val target: String = "",
    /** Index of the matching rule in Settings.rules, -1 for other steps. */
    val rule: Int = -1,
    val kind: String = "",
    /** What matched: a domain, rule-set name, app package ... */
    val value: String = "",
    /** The address the domain was resolved to before the steps that match IP addresses. */
    val resolved: String = "",
    val note: String = "",
)

@Serializable
data class DataSummary(val groups: Int = 0, val subscriptions: Int = 0, val nodes: Int = 0, val chains: Int = 0, val policies: Int = 0)

@Serializable
data class BackupInfo(
    val encrypted: Boolean = false,
    val needPassword: Boolean = false,
    val platform: String = "",
    val version: Int = 0,
    val hasNodes: Boolean = false,
    val hasRules: Boolean = false,
    /** Settings other than the routing rules and the look. */
    val hasSettings: Boolean = false,
    /** The look of the app (theme, colours, language). */
    val hasAppearance: Boolean = false,
    val summary: DataSummary = DataSummary(),
    val rules: Int = 0,
    /** Program rules of the desktop app: kept, but only Windows uses them. */
    val desktopRules: Int = 0,
    /** The desktop app's scenes that are on and become rule sets here ("ai", "media"). */
    val scenes: List<String> = emptyList(),
)

/** The parts of a backup to save or to take from one. */
@Serializable
data class BackupParts(
    val nodes: Boolean = true,
    val rules: Boolean = true,
    val settings: Boolean = true,
    /** The look: theme, colours, language, glass (a part of its own since 2.0). */
    val appearance: Boolean = true,
    val password: String = "",
    val replace: Boolean = false,
)

@Serializable
data class Settings(
    val theme: String = "auto",
    val accentColor: String = "#F472B6",
    /** Custom accent colours, newest first (shared with NextBox for Windows). */
    val recentColors: List<String> = emptyList(),
    val language: String = "zh-CN",
    val mixedPort: Int = 2080,
    val allowLan: Boolean = false,
    val tunEnabled: Boolean = true,
    val routeMode: String = "rule",
    val blockAds: Boolean = false,
    val rules: List<Rule> = emptyList(),
    /** The rules page hides the steps that are switched off (shared with NextBox for Windows). */
    val routeHideOff: Boolean = false,
    /** How names are resolved for the IP steps ("bypass mainland China"): "remote" (the default) or "direct" (Settings.ResolveDNS in the core). */
    val bypassDns: String = "remote",
    val scenes: List<Scene> = emptyList(),
    val remoteDns: String = "",
    val directDns: String = "",
    val enableIpv6: Boolean = false,
    val testUrl: String = "https://www.google.com/generate_204",
    val testTimeout: Int = 5000,
    val testConcurrency: Int = 16,
    /** After a speed test of a whole group: "" asks, "latency" sorts, "none" keeps the order. */
    val sortAfterTest: String = "",
    /** DNS 复写: domains answered by a DNS server of their own, or with fixed IPs. */
    val dnsOverrides: List<DnsOverride> = emptyList(),
) {
    val bypassCn: Boolean get() = scenes.firstOrNull { it.id == "cn" }?.enabled == true
}

/** DNS 复写 (store.DNSOverride): domains are suffixes with [server], full names with [ips]. */
@Serializable
data class DnsOverride(
    val domains: List<String> = emptyList(),
    val server: String = "",
    val viaProxy: Boolean = false,
    val ips: List<String> = emptyList(),
    val disabled: Boolean = false,
    /** the subscription whose Clash DNS settings made it (0 = the user) */
    val from: Long = 0,
)

/** Which parts of a Clash subscription besides its nodes are taken over. */
@Serializable
data class ClashImport(val groups: Boolean = false, val rules: Boolean = false, val dns: Boolean = false, val subDns: Boolean = false)

/** A subscription's own DNS: [remote] while one of its nodes is connected, [resolver] for its nodes' addresses. */
@Serializable
data class GroupDns(val remote: String = "", val resolver: String = "") {
    val isSet: Boolean get() = remote.isNotEmpty() || resolver.isNotEmpty()
}

@Serializable
data class ClashPreview(
    val groups: List<String>? = null,
    val rules: Int = 0,
    val ruleTotal: Int = 0,
    val sets: Int = 0,
    val dns: Int = 0,
    val skipped: List<String>? = null,
    /** the subscription's own DNS servers, when its config names some */
    val subDns: GroupDns? = null,
)

/** What a Clash import would make, for the dialog that asks. */
@Serializable
data class ClashInfo(val nodes: Int = 0, val preview: ClashPreview = ClashPreview(), val current: ClashImport? = null)

/** Where the connected node's traffic leaves (the home page). */
@Serializable
data class ExitInfo(
    val ip: String = "",
    val country: String = "",
    val asn: Int = 0,
    val org: String = "",
    val latency: Int = 0,
    val error: String = "",
    /** "test" (the test address) or "trace" (Cloudflare) when it failed */
    val failed: String = "",
)

@Serializable
data class AppState(
    val groups: List<Group> = emptyList(),
    val currentGroupId: Long = 0,
    val selectedId: Long = 0,
    val settings: Settings = Settings(),
)

@Serializable
data class Traffic(val up: Long = 0, val down: Long = 0) {
    val total: Long get() = up + down
}

@Serializable
data class DayUsage(val date: String, val up: Long = 0, val down: Long = 0) {
    val total: Long get() = up + down
}

@Serializable
data class NodeUsage(
    val id: Long,
    val name: String,
    val type: String = "",
    val group: String = "",
    val region: String = "",
    val up: Long = 0,
    val down: Long = 0,
) {
    val total: Long get() = up + down
}

@Serializable
data class UsageReport(
    val today: Traffic = Traffic(),
    val period: Traffic = Traffic(),
    val periodFrom: String = "",
    val days: List<DayUsage> = emptyList(),
    val nodes: List<NodeUsage> = emptyList(),
)

/** Display name of a sing-box outbound type. */
fun protocolName(type: String): String = when (type) {
    "shadowsocks" -> "Shadowsocks"
    "vmess" -> "VMess"
    "vless" -> "VLESS"
    "trojan" -> "Trojan"
    "hysteria2" -> "Hysteria2"
    "tuic" -> "TUIC"
    "anytls" -> "AnyTLS"
    "naive" -> "NaiveProxy"
    "socks" -> "SOCKS"
    "http" -> "HTTP"
    "chain" -> T("链式代理")
    "shadowtls" -> "ShadowTLS"
    else -> type
}
