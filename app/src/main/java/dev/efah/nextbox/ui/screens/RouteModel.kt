package dev.efah.nextbox.ui.screens

import dev.efah.nextbox.data.PolicyView
import dev.efah.nextbox.data.Rule
import dev.efah.nextbox.data.Scene
import dev.efah.nextbox.data.SetItem
import dev.efah.nextbox.data.Settings
import dev.efah.nextbox.ui.T

/**
 * The route as NextBox for Windows has it (2.0): the steps between the fixed
 * first one (LAN) and the fixed last one (everything else) are
 * Settings.rules in order — ad blocking ("ads"), the scenes ("scene",
 * values[0] is the id), rules and rule sets ("set").
 */
object Route {
    val sceneIds = listOf("ai", "media", "games", "cn")

    /**
     * Scenes of NextBox for Windows that the phone neither shows nor routes
     * by (game downloads). They stay in the settings, so backups carry them
     * back to Windows unchanged (see phoneSettings in the core).
     */
    private val pcOnlyScenes = setOf("games")

    fun pcOnly(r: Rule): Boolean = r.kind == "scene" && r.values.firstOrNull() in pcOnlyScenes

    fun sceneTitle(id: String): String = when (id) {
        "cn" -> T("绕过中国大陆")
        "ai" -> T("AI 服务")
        "media" -> T("流媒体")
        "games" -> T("游戏下载直连")
        else -> id
    }

    fun sceneDesc(id: String): String = when (id) {
        "cn" -> T("国内网站和国内 IP")
        "ai" -> "ChatGPT · Claude · Gemini"
        "media" -> "YouTube · Netflix · Disney+"
        "games" -> T("Steam 等国内下载节点")
        else -> ""
    }

    /** Where a scene goes: "bypass mainland China" always direct. */
    fun sceneTarget(s: Settings, id: String): String =
        if (id == "cn") "direct" else s.scenes.firstOrNull { it.id == id }?.target?.ifEmpty { "proxy" } ?: "proxy"

    fun sceneOn(s: Settings, id: String): Boolean = s.scenes.firstOrNull { it.id == id }?.enabled == true

    /** Kinds a rule set holds, in the order its editor lists them. */
    val setKinds = listOf("suffix", "domain", "keyword", "regex", "geosite", "cidr", "geoip", "package", "url")

    fun kindLabel(kind: String): String = when (kind) {
        "url" -> T("规则集网址")
        "set" -> T("规则集")
        else -> RuleUi.kindLabel(kind)
    }

    /** A rule set's contents in a few words, e.g. "域名 3 · 应用 1". */
    fun itemsSummary(items: List<SetItem>): String =
        items.filter { it.values.isNotEmpty() }.joinToString(" · ") { "${kindLabel(it.kind)} ${it.values.size}" }

    /** How a rule is named everywhere: its own name, its catalog service, or what it matches. */
    fun title(r: Rule, appLabel: (String) -> String): String {
        if (r.name.isNotEmpty()) return r.name
        if (r.isSet) return T("规则集")
        RuleUi.services.firstOrNull { it.id == r.app }?.let { return T(it.label) }
        val shown = r.values.take(2).map { valueLabel(r.kind, it, appLabel) }
        val more = if (r.values.size > 2) T(" 等 %d 个", r.values.size) else ""
        return kindLabel(r.kind) + " " + shown.joinToString("、") + more
    }

    fun valueLabel(kind: String, v: String, appLabel: (String) -> String): String = when (kind) {
        "package" -> appLabel(v)
        "geoip" -> Regions.name(v)
        "geosite" -> RuleUi.services.firstOrNull { v in it.geosite }?.let { T(it.label) } ?: v
        else -> v
    }

    /** The line under a step's title. */
    fun summary(r: Rule, appLabel: (String) -> String): String = when {
        r.isSet -> itemsSummary(r.items).ifEmpty { T("还没有内容") }
        r.kind == "process" -> T("电脑程序，只在 Windows 版生效")
        r.name.isNotEmpty() || r.app.isNotEmpty() ->
            kindLabel(r.kind) + " · " + r.values.take(3).joinToString("、") { valueLabel(r.kind, it, appLabel) } + if (r.values.size > 3) "…" else ""
        else -> RuleUi.siteKindLabel(r.kind).takeIf { r.kind in RuleUi.siteKinds } ?: kindLabel(r.kind)
    }

    /** Whether a step takes part with the current settings. */
    fun active(s: Settings, r: Rule): Boolean = when {
        r.kind == "ads" -> s.blockAds
        s.routeMode != "rule" -> false
        r.kind == "scene" -> sceneOn(s, r.values.firstOrNull().orEmpty())
        r.kind == "process" -> false
        else -> !r.disabled && r.filled
    }

    /** Where a step's traffic goes. */
    fun target(s: Settings, r: Rule): String = when (r.kind) {
        "ads" -> "block"
        "scene" -> sceneTarget(s, r.values.firstOrNull().orEmpty())
        else -> r.target
    }

    /** The steps the "hide steps that are off" switch hides: ad blocking and scenes while off. */
    fun hidden(s: Settings, r: Rule): Boolean =
        pcOnly(r) || s.routeHideOff && ((r.kind == "ads" && !s.blockAds) || (r.kind == "scene" && !sceneOn(s, r.values.firstOrNull().orEmpty())))

    /** The rules where a new one goes: before "bypass mainland China", so that it applies. */
    fun insertAt(rules: List<Rule>): Int =
        rules.indexOfFirst { it.kind == "scene" && it.values.firstOrNull() == "cn" }.takeIf { it >= 0 } ?: rules.size

    /** The route of a new installation: ad blocking first, the scenes last, only the bypass on. */
    fun defaults(): Pair<List<Rule>, List<Scene>> =
        (listOf(Rule(kind = "ads", target = "")) + sceneIds.map { Rule(kind = "scene", values = listOf(it), target = "") }) to
            listOf(Scene("cn", true, "direct"), Scene("ai", false, "proxy"), Scene("media", false, "proxy"), Scene("games", false, "direct"))

    /** Moves the step shown at [from] to where the one shown at [to] is (shown are indexes into rules). */
    fun move(rules: List<Rule>, shown: List<Int>, from: Int, to: Int): List<Rule> {
        if (from == to) return rules
        val out = rules.toMutableList()
        val item = out.removeAt(shown[from])
        var at = shown[to]
        if (shown[to] > shown[from]) at -= 1
        out.add(if (to > from) at + 1 else at, item)
        return out
    }

    /** Stable keys for the rows while they are dragged: the content, and which copy of it. */
    fun keys(rules: List<Rule>): List<String> {
        val seen = HashMap<Int, Int>()
        return rules.map { r ->
            val h = r.hashCode()
            val n = (seen[h] ?: 0) + 1
            seen[h] = n
            "$h#$n"
        }
    }

    // ---------------------------------------------------------------- text mode
    // One "kind:value, target" per line, as on NextBox for Windows. A rule set
    // is a line "set:name, target" followed by its items, indented. "!" in
    // front switches a rule off; "ads" and "scene:…" are the places of ad
    // blocking and the scenes.

    private val kindAliases = mapOf(
        "geosite" to "geosite", "suffix" to "suffix", "domain_suffix" to "suffix", "domain" to "domain", "full" to "domain",
        "keyword" to "keyword", "domain_keyword" to "keyword", "regex" to "regex", "regexp" to "regex", "geoip" to "geoip",
        "cidr" to "cidr", "ip" to "cidr", "ip_cidr" to "cidr", "process" to "process", "process_name" to "process",
        "package" to "package", "package_name" to "package", "app" to "package", "url" to "url",
    )
    private val ipLike = Regex("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F]*:[0-9a-fA-F:]+)(/\\d{1,3})?$")

    private fun targetText(t: String, policies: List<PolicyView>): String {
        if (!t.startsWith("policy:")) return t
        return policies.firstOrNull { it.id == RuleUi.policyId(t) }?.let { "policy:" + it.name } ?: "proxy"
    }

    fun toText(rules: List<Rule>, s: Settings, policies: List<PolicyView>): String = buildList {
        add(T("# 每行一条：类型:值, 去向（proxy 当前节点 / direct 直连 / block 拦截 / policy:分组代理名称）"))
        add(T("# ads 是屏蔽广告的位置，scene:… 是常用场景的位置，可以上下移动"))
        add(T("# 规则集：set:名称, 去向，下面缩进的行是它的内容；行首加 ! 表示停用"))
        for (r in rules) {
            val off = if (r.disabled) "!" else ""
            when {
                r.kind == "ads" -> add("ads")
                pcOnly(r) -> Unit
                r.kind == "scene" -> {
                    val id = r.values.firstOrNull().orEmpty()
                    add(if (id == "cn") "scene:cn" else "scene:$id, " + targetText(sceneTarget(s, id), policies))
                }
                r.isSet -> {
                    add("${off}set:${r.name}, " + targetText(r.target, policies))
                    r.items.forEach { it.values.forEach { v -> add("  ${it.kind}:$v") } }
                }
                else -> r.values.forEach { add("$off${r.kind}:$it, " + targetText(r.target, policies)) }
            }
        }
    }.joinToString("\n")

    /** Reads one "kind:value", or a bare domain / IP / address. Null for an unknown kind. */
    fun parseItem(text: String): Pair<String, String>? {
        val t = text.trim()
        if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(t)) return "url" to t
        val c = t.indexOf(':')
        val k = if (c > 0) kindAliases[t.substring(0, c).lowercase()] else null
        if (k != null) return k to t.substring(c + 1).trim()
        if (c > 0 && !ipLike.matches(t) && '.' !in t.substring(0, c)) return null
        return (if (ipLike.matches(t)) "cidr" else "suffix") to t
    }

    class Parsed(val rules: List<Rule>, val sceneTargets: Map<String, String>, val errors: List<String>)

    fun fromText(text: String, policies: List<PolicyView>, before: List<Rule>): Parsed {
        val rules = mutableListOf<Rule>()
        val errors = mutableListOf<String>()
        val sceneTargets = mutableMapOf<String, String>()
        var set: Int = -1
        fun target(raw: String, n: Int): String? {
            val l = raw.lowercase()
            if (l == "proxy" || l == "direct" || l == "block") return l
            if (l.startsWith("policy:")) {
                val name = raw.substring(7).trim()
                policies.firstOrNull { it.name == name }?.let { return "policy:${it.id}" }
                errors += T("第 %d 行：没有叫“%s”的分组代理", n, name)
                return null
            }
            errors += T("第 %d 行：不认识的去向“%s”", n, raw)
            return null
        }
        text.split('\n').forEachIndexed { i, raw ->
            val n = i + 1
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            if (raw.first().isWhitespace() && set >= 0) {
                val it = parseItem(line)
                if (it == null || it.second.isEmpty()) { errors += T("第 %d 行：不认识“%s”", n, line); return@forEachIndexed }
                val r = rules[set]
                val items = r.items.toMutableList()
                val at = items.indexOfFirst { x -> x.kind == it.first }
                if (at >= 0) items[at] = items[at].copy(values = (items[at].values + it.second).distinct()) else items += SetItem(it.first, listOf(it.second))
                rules[set] = r.copy(items = items)
                return@forEachIndexed
            }
            set = -1
            val off = line.startsWith("!")
            if (off) line = line.substring(1).trim()
            if (line.lowercase() == "ads") { rules += Rule(kind = "ads", target = ""); return@forEachIndexed }
            if (line.lowercase().startsWith("scene:")) {
                val parts = line.substring(6).split(',')
                val id = parts[0].trim().lowercase()
                if (id !in sceneIds) { errors += T("第 %d 行：没有叫“%s”的常用场景", n, parts[0].trim()); return@forEachIndexed }
                if (parts.size > 1 && parts[1].isNotBlank() && id != "cn") {
                    val t = target(parts[1].trim(), n) ?: return@forEachIndexed
                    if (t == "block") { errors += T("第 %d 行：常用场景不能设为拦截", n); return@forEachIndexed }
                    sceneTargets[id] = t
                }
                rules += Rule(kind = "scene", values = listOf(id), target = "")
                return@forEachIndexed
            }
            val comma = line.lastIndexOf(',')
            if (comma < 0) { errors += T("第 %d 行：缺少去向，例如 “%s, direct”", n, line); return@forEachIndexed }
            val left = line.substring(0, comma).trim()
            val t = target(line.substring(comma + 1).trim(), n) ?: return@forEachIndexed
            if (left.lowercase().startsWith("set:")) {
                val name = left.substring(4).trim()
                if (name.isEmpty()) { errors += T("第 %d 行：规则集需要名称", n); return@forEachIndexed }
                rules += Rule(kind = "set", name = name, target = t, disabled = off)
                set = rules.lastIndex
                return@forEachIndexed
            }
            val it = parseItem(left)
            if (it == null) { errors += T("第 %d 行：不认识“%s”", n, left); return@forEachIndexed }
            if (it.first == "url") { errors += T("第 %d 行：规则集网址只能写在规则集里", n); return@forEachIndexed }
            if (it.second.isEmpty()) { errors += T("第 %d 行：缺少值", n); return@forEachIndexed }
            val last = rules.lastOrNull()
            if (last != null && !last.isStep && !last.isSet && last.kind == it.first && last.target == t && last.disabled == off) {
                rules[rules.lastIndex] = last.copy(values = last.values + it.second)
            } else {
                rules += Rule(kind = it.first, values = listOf(it.second), target = t, disabled = off)
            }
        }
        // the text only says what rules match: names and services come back from the same rules
        val labels = before.filter { !it.isSet && !it.isStep && (it.name.isNotEmpty() || it.app.isNotEmpty()) }
            .associateBy { "${it.kind}|${it.target}|${it.values.sorted()}" }
        val out = rules.map { r ->
            if (r.isSet || r.isStep) r else labels["${r.kind}|${r.target}|${r.values.sorted()}"]?.let { r.copy(name = it.name, app = it.app) } ?: r
        }.toMutableList()
        // the PC-only scenes are not in the text: they keep their places
        before.forEachIndexed { i, r -> if (pcOnly(r) && out.none { it == r }) out.add(i.coerceAtMost(out.size), r) }
        return Parsed(out, sceneTargets, errors)
    }
}
