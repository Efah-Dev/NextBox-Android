package dev.efah.nextbox.ui.screens

import android.icu.text.Transliterator
import dev.efah.nextbox.ui.Lang
import dev.efah.nextbox.ui.T
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * IP regions for "IP 归属地" rules: every country and region sing-geoip has a
 * rule-set for (ISO 3166-1), the ones people pick most first, the rest by
 * name. Names come from the system in the UI language; a search finds a
 * region by its name in Chinese or English, its code, or the pinyin
 * initials of its Chinese name ("rb" → 日本).
 */
object Regions {
    data class Region(val code: String, val name: String, private val terms: String) {
        // inside a word only for longer text: "us" would find Russia and Australia
        fun matches(q: String): Boolean = q.isEmpty() || name.lowercase().contains(q) ||
            terms.split(' ').any { it.startsWith(q) } || (q.length > 3 && terms.contains(q))
        /** Closest matches first: the code, the start of the name or of its initials. */
        fun rank(q: String): Int = when {
            q.isEmpty() -> 0
            code == q || name.lowercase().startsWith(q) || terms.split(' ').any { it.startsWith(q) } -> 0
            else -> 1
        }
    }

    private val common = listOf("cn", "hk", "tw", "mo", "jp", "sg", "kr", "us", "gb", "de", "fr", "nl", "ca", "au", "ru", "in", "th", "my", "vn", "ph", "id", "tr", "ae")
    private val all = ("ad ae af ag ai al am ao aq ar as at au aw ax az ba bb bd be bf bg bh bi bj bl bm bn bo bq br bs bt bw by bz ca cc cd cf cg ch ci ck cl cm cn co cr cu cv cw cx cy cz de dj dk dm do dz ec ee eg eh er es et fi fj fk fm fo fr ga gb gd ge gf gg gh gi gl gm gn gp gq gr gs gt gu gw gy hk hn hr ht hu id ie il im in io iq ir is it je jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr ls lt lu lv ly ma mc md me mf mg mh mk ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph pk pl pm pn pr ps pt pw py qa re ro rs ru rw sa sb sc sd se sg sh si sj sk sl sm sn so sr ss st sv sx sy sz tc td tf tg th tj tk tl tm tn to tr tt tv tw tz ua ug um us uy uz va vc ve vg vi vn vu wf ws ye yt za zm zw").split(' ')

    // what people call these, rather than the system's long official names
    private val own = mapOf("cn" to "中国大陆", "hk" to "中国香港", "tw" to "中国台湾", "mo" to "中国澳门") // i18n:skip (translated where shown)

    private val cache = ConcurrentHashMap<String, List<Region>>()

    private val pinyin: Transliterator? by lazy { runCatching { Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }.getOrNull() }

    /** "日本" → "rb". */
    fun initials(text: String): String {
        val t = pinyin ?: return ""
        return text.map { ch ->
            if (Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN) t.transliterate(ch.toString()).firstOrNull { it.isLetter() } ?: ' ' else ' '
        }.joinToString("").filter { it != ' ' }
    }

    private fun countryLocale(code: String): Locale = Locale.Builder().setRegion(code.uppercase()).build()

    private fun locale(lang: String): Locale = when (lang) {
        "zh-TW" -> Locale.TRADITIONAL_CHINESE
        "en" -> Locale.ENGLISH
        else -> Locale.SIMPLIFIED_CHINESE
    }

    fun name(code: String): String = own[code]?.let { T(it) } ?: countryLocale(code).getDisplayCountry(locale(Lang.current)).ifEmpty { code.uppercase() }

    /** Every region, named in the UI language. */
    fun list(): List<Region> = cache.getOrPut(Lang.current) {
        fun region(code: String): Region {
            val zh = own[code] ?: countryLocale(code).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
            val en = countryLocale(code).getDisplayCountry(Locale.ENGLISH)
            val shown = name(code)
            val terms = listOf(code, zh, en, shown, initials(zh)).joinToString(" ").lowercase()
            return Region(code, shown, terms)
        }
        val order = java.text.Collator.getInstance(locale(Lang.current))
        common.map(::region) + all.filter { it !in common }.map(::region).sortedWith { a, b -> order.compare(a.name, b.name) }
    }

    /** The regions a search finds, closest first. */
    fun search(query: String): List<Region> {
        val q = query.trim().lowercase()
        return list().filter { it.matches(q) }.sortedBy { it.rank(q) }
    }
}
