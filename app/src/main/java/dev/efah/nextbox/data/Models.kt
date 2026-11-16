package dev.efah.nextbox.data

import dev.efah.nextbox.ui.T
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
)

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
    /** Step of the route path: ads, lan, block, direct, policy, proxy, cn or final. */
    val step: String = "",
    val target: String = "",
    /** Index of the matching rule in Settings.rules, -1 for other steps. */
    val rule: Int = -1,
    val kind: String = "",
    /** What matched: a domain, rule-set name, app package ... */
    val value: String = "",
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
    /** Settings other than the routing rules. */
    val hasSettings: Boolean = false,
    val summary: DataSummary = DataSummary(),
    val rules: Int = 0,
    /** Program rules of the desktop app: kept, but only Windows uses them. */
    val desktopRules: Int = 0,
    /** The desktop app's ready-made scenes that are on ("ai", "media", "games"); they become rules. */
    val scenes: List<String> = emptyList(),
)

/** The parts of a backup to save or to take from one. */
@Serializable
data class BackupParts(
    val nodes: Boolean = true,
    val rules: Boolean = true,
    val settings: Boolean = true,
    val password: String = "",
    val replace: Boolean = false,
)

@Serializable
data class Settings(
    val theme: String = "auto",
    val accentColor: String = "#F472B6",
    val language: String = "zh-CN",
    val mixedPort: Int = 2080,
    val allowLan: Boolean = false,
    val tunEnabled: Boolean = true,
    val tunStack: String = "mixed",
    val tunMtu: Int = 9000,
    val routeMode: String = "rule",
    val blockAds: Boolean = false,
    val rules: List<Rule> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val remoteDns: String = "",
    val directDns: String = "",
    val enableIpv6: Boolean = false,
    val sniff: Boolean = true,
    val testUrl: String = "https://www.google.com/generate_204",
    val testTimeout: Int = 5000,
    val testConcurrency: Int = 16,
    val subUpdateByProxy: Boolean = false,
    val logLevel: String = "info",
) {
    val bypassCn: Boolean get() = scenes.firstOrNull { it.id == "cn" }?.enabled == true
}

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
