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
    val routeMode: String = "global",
    val blockAds: Boolean = false,
    val directRules: String = "",
    val proxyRules: String = "",
    val blockRules: String = "",
    val remoteDns: String = "",
    val directDns: String = "",
    val enableIpv6: Boolean = false,
    val sniff: Boolean = true,
    val testUrl: String = "https://www.google.com/generate_204",
    val testTimeout: Int = 5000,
    val testConcurrency: Int = 16,
    val subUpdateByProxy: Boolean = false,
    val logLevel: String = "info",
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
    "socks" -> "SOCKS"
    "http" -> "HTTP"
    else -> type
}
