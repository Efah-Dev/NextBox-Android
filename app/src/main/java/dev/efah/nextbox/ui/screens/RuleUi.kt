package dev.efah.nextbox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.efah.nextbox.data.PolicyView
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.ui.T
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbColors

/** A ready-made group of websites (and the IP ranges some apps connect to directly). */
data class Service(
    val id: String,
    val label: String,
    val geosite: List<String>,
    val cidrs: List<String> = emptyList(),
    val suffix: List<String> = emptyList(),
    /** Off: only names the rules that already use it (game downloads matter on a PC only). */
    val offered: Boolean = true,
)

/** Labels, colours and list operations shared by the rules screens. */
object RuleUi {
    fun kindLabel(kind: String): String = when (kind) {
        "suffix" -> T("域名")
        "domain" -> T("完整域名")
        "keyword" -> T("关键字")
        "regex" -> T("正则")
        "geosite" -> T("网站分类")
        "cidr" -> T("IP 段")
        "geoip" -> T("IP 归属地")
        "package" -> T("应用")
        "process" -> T("电脑程序")
        else -> kind
    }

    /** How a website value is matched, for the add sheet. */
    val siteKinds = listOf("suffix", "domain", "keyword", "regex")

    fun siteKindLabel(kind: String): String = when (kind) {
        "suffix" -> T("域名及所有子域名")
        "domain" -> T("只匹配这个域名")
        "keyword" -> T("包含关键字")
        "regex" -> T("正则表达式")
        else -> kindLabel(kind)
    }

    private val telegramIPs = listOf(
        "91.105.192.0/23", "91.108.4.0/22", "91.108.8.0/22", "91.108.12.0/22", "91.108.16.0/22", "91.108.20.0/22",
        "91.108.56.0/22", "95.161.64.0/20", "149.154.160.0/20", "185.76.151.0/24",
        "2001:67c:4e8::/48", "2001:b28:f23c::/47", "2001:b28:f23f::/48",
    )

    private val steamStore = listOf(
        "steampowered.com", "steamcommunity.com", "steamstatic.com", "steamusercontent.com",
        "steam-chat.com", "steam-api.com", "steamgames.com", "steamserver.net", "s.team", "steam.tv",
        "steamdeck.com", "valvesoftware.com",
        "steamcdn-a.akamaihd.net", "steamcommunity-a.akamaihd.net", "steamstore-a.akamaihd.net",
        "steamuserimages-a.akamaihd.net", "steamusercontent-a.akamaihd.net", "steamvideo-a.akamaihd.net",
        "steambroadcast.akamaized.net", "steammobile.akamaized.net",
    )
    private val steamDownload = listOf(
        "steamcontent.com", "steampipe.akamaized.net", "steampipe-kr.akamaized.net", "steampipe-partner.akamaized.net",
        "steampowered.com.8686c.com", "steamstatic.com.8686c.com", "dl.steam.clngaa.com",
        "st.dl.bscstorage.net", "st.dl.eccdnx.com", "st.dl.pinyuncloud.com", "xz.pphimalayanrt.com",
        "lv.queniujq.cn", "client-update.queniuqe.com", "steamchina.com", "wmsjsteam.com",
    )

    /** Common services offered as one tap in the add sheet (sing-geosite names). */
    val services = listOf(
        Service("google", "Google", listOf("google")),
        Service("youtube", "YouTube", listOf("youtube")),
        Service("telegram", "Telegram", listOf("telegram"), telegramIPs),
        Service("openai", "ChatGPT / OpenAI", listOf("openai")),
        Service("anthropic", "Claude", listOf("anthropic")),
        Service("gemini", "Gemini", listOf("google-gemini")),
        Service("ai", "AI 服务（境外）", listOf("category-ai-!cn")), // i18n:skip (translated where shown)
        Service("netflix", "Netflix", listOf("netflix")),
        Service("disney", "Disney+", listOf("disney")),
        Service("spotify", "Spotify", listOf("spotify")),
        Service("tiktok", "TikTok", listOf("tiktok")),
        Service("github", "GitHub", listOf("github")),
        Service("twitter", "X / Twitter", listOf("twitter")),
        Service("instagram", "Instagram", listOf("instagram")),
        Service("facebook", "Facebook", listOf("facebook")),
        Service("apple", "Apple 服务", listOf("apple")), // i18n:skip (translated where shown)
        Service("microsoft", "微软", listOf("microsoft")), // i18n:skip (translated where shown)
        // the store and community want the proxy, the downloads come from CDNs in China (as on NextBox for Windows)
        Service("steam", "Steam", emptyList(), suffix = steamStore),
        Service("bilibili", "哔哩哔哩", listOf("bilibili")), // i18n:skip (translated where shown)
        Service("steam-download", "Steam 下载", emptyList(), suffix = steamDownload, offered = false), // i18n:skip (translated where shown)
        Service("games-cn", "游戏下载（国内 CDN）", listOf("category-games@cn"), offered = false), // i18n:skip (translated where shown)
    )

    /** What the service picker offers, by topic. */
    val serviceGroups: List<Pair<String, List<String>>> = listOf(
        "AI" to listOf("openai", "anthropic", "gemini", "ai"),
        "视频与音乐" to listOf("youtube", "netflix", "disney", "spotify", "tiktok", "bilibili"), // i18n:skip (translated where shown)
        "社交" to listOf("telegram", "twitter", "instagram", "facebook"), // i18n:skip (translated where shown)
        "常用平台" to listOf("google", "github", "apple", "microsoft", "steam"), // i18n:skip (translated where shown)
    )

    fun service(id: String): Service? = services.firstOrNull { it.id == id }


    fun policyId(target: String): Long = target.removePrefix("policy:").takeIf { target.startsWith("policy:") }?.toLongOrNull() ?: 0

    fun targetLabel(target: String, policies: List<PolicyView>): String = when (target) {
        "proxy" -> T("代理")
        "direct" -> T("直连")
        "block" -> T("屏蔽")
        else -> policies.firstOrNull { it.id == policyId(target) }?.name ?: T("代理")
    }

    /** Background and text colour of a target pill. */
    fun targetColors(target: String, c: NbColors): Pair<Color, Color> = when {
        target == "direct" -> if (c.dark) Color(0xFF1F3B2C) to Color(0xFF86E0A8) else Color(0xFFE2F3E8) to Color(0xFF0F6A3A)
        target == "block" -> if (c.dark) Color(0xFF452327) to Color(0xFFF7A1A6) else Color(0xFFFBE4E5) to Color(0xFFA32A30)
        target.startsWith("policy:") -> if (c.dark) Color(0xFF233552) to Color(0xFFA9C4F5) else Color(0xFFE6EEFC) to Color(0xFF2C4F8A)
        else -> c.accentSoft to c.accentInk
    }

    /**
     * Rules the phone checks: not the places of ad blocking and the scenes
     * that the desktop app keeps among them, nor its program rules.
     */
    fun applies(r: Rule): Boolean = r.kind != "ads" && r.kind != "scene" && r.kind != "process"

    /** How many values (services counted once) a target has. */
    fun count(rules: List<Rule>, target: (String) -> Boolean): Int =
        rules.filter { !it.disabled && applies(it) && target(it.target) }.sumOf { r ->
            if (r.isSet) r.items.sumOf { it.values.size } else if (r.kind != "cidr") r.values.size else {
                val grouped = services.filter { it.cidrs.isNotEmpty() && r.values.containsAll(it.cidrs) }
                r.values.size - grouped.sumOf { it.cidrs.size } + grouped.size
            }
        }

    // ---------------------------------------------------------------- edits

    /** A value as the core stores it (see store.NormalizeValue), so lookups find it. */
    fun normalize(kind: String, v: String): String {
        val t = v.trim()
        return when (kind) {
            "suffix" -> t.lowercase().trimStart('.')
            "domain", "keyword", "geosite", "geoip" -> t.lowercase()
            "cidr" -> if (t.isEmpty() || '/' in t) t else if (':' in t) "$t/128" else "$t/32"
            else -> t
        }
    }

    /** The rules without these values of this kind (rules left empty are dropped). */
    fun remove(rules: List<Rule>, kind: String, values: Collection<String>): List<Rule> {
        val drop = values.toSet()
        return rules.mapNotNull { r ->
            if (r.kind != kind) return@mapNotNull r
            val left = r.values.filter { it !in drop }
            when {
                left.size == r.values.size -> r
                left.isEmpty() -> null
                else -> r.copy(values = left)
            }
        }
    }

    /** Puts values of a kind under target (moving them from wherever they were). */
    fun place(rules: List<Rule>, kind: String, raw: Collection<String>, target: String, disabled: Boolean = false): List<Rule> {
        val values = raw.map { normalize(kind, it) }.filter { it.isNotEmpty() }.distinct()
        if (values.isEmpty()) return rules
        val out = remove(rules, kind, values).toMutableList()
        // joins an unnamed rule of the same kind and target; a new one goes
        // before "bypass mainland China", so that it applies (Route.insertAt)
        val i = out.indexOfFirst { it.kind == kind && it.target == target && it.disabled == disabled && it.name.isEmpty() && it.app.isEmpty() }
        if (i >= 0) {
            out[i] = out[i].copy(values = (out[i].values + values).distinct())
        } else {
            out.add(Route.insertAt(out), Rule(kind = kind, values = values.distinct(), target = target, disabled = disabled))
        }
        return out
    }

    /** The values that are already in the rules under another target, with that target. */
    fun conflicts(rules: List<Rule>, kind: String, values: Collection<String>, target: String): Map<String, String> {
        val want = values.map { normalize(kind, it) }.toSet()
        val out = linkedMapOf<String, String>()
        for (r in rules) if (r.kind == kind && r.target != target) r.values.filter { it in want }.forEach { out[it] = r.target }
        return out
    }
}

@Composable
fun TargetPill(target: String, policies: List<PolicyView>, modifier: Modifier = Modifier, label: String? = null) {
    val (bg, fg) = RuleUi.targetColors(target, Nb.c)
    Text(
        label ?: RuleUi.targetLabel(target, policies), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
