package dev.efah.nextbox.ui

import dev.efah.nextbox.data.Profile

/** The flag emoji of a region code ("JP" → 🇯🇵); "" when the code is not a country. */
fun flagOf(region: String): String {
    val code = region.trim().uppercase().let { if (it == "UK") "GB" else it }
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** Whether a name already carries a flag (subscriptions often put one in front). */
private fun hasFlag(name: String): Boolean {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        if (cp in 0x1F1E6..0x1F1FF) return true
        i += Character.charCount(cp)
    }
    return false
}

/**
 * The first flag in a name and the name without it ("🇭🇰 HK 01" → "🇭🇰", "HK 01");
 * "" and the name when it has none.
 */
fun splitFlag(name: String): Pair<String, String> {
    var i = 0
    while (i < name.length) {
        val cp = name.codePointAt(i)
        val n = Character.charCount(cp)
        if (cp in 0x1F1E6..0x1F1FF && i + n < name.length) {
            val cp2 = name.codePointAt(i + n)
            if (cp2 in 0x1F1E6..0x1F1FF) {
                val end = i + n + Character.charCount(cp2)
                val rest = (name.substring(0, i) + " " + name.substring(end)).replace(Regex("\\s+"), " ").trim()
                return name.substring(i, end) to rest.ifEmpty { name }
            }
        }
        i += n
    }
    return "" to name
}

/** A node name with the flag of its region in front, unless the name has one already. */
fun flagged(name: String, region: String): String {
    val flag = flagOf(region)
    return if (flag.isEmpty() || hasFlag(name)) name else "$flag $name"
}

/** The node's name as lists show it: with the flag of its region. */
val Profile.label: String get() = flagged(name, region)
