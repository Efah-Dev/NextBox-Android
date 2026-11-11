package dev.efah.nextbox.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.efah.nextbox.nbcore.Nbcore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * UI language. Source strings are Simplified Chinese and double as keys; the
 * dictionaries live in the Go core (shared with the desktop app), so a string
 * crosses JNI once and is cached here afterwards.
 */
object Lang {
    /** "zh-CN", "zh-TW" or "en". Compose state: reading it in a composable follows language changes. */
    var current by mutableStateOf("zh-CN")
        private set

    private val cache = ConcurrentHashMap<String, String>()

    /** Applies the language preference ("auto" follows the system language). */
    fun apply(pref: String) {
        val lang = if (pref == "auto") fromSystem() else pref
        Nbcore.setLanguage(lang)
        cache.clear()
        current = lang
    }

    private fun fromSystem(): String {
        val l = Locale.getDefault()
        return when {
            l.language != "zh" -> "en"
            l.script == "Hant" || l.country in setOf("TW", "HK", "MO") -> "zh-TW"
            else -> "zh-CN"
        }
    }

    internal fun translate(s: String): String {
        if (current == "zh-CN" || s.isEmpty()) return s
        return cache.getOrPut(s) { runCatching { Nbcore.translate(s) }.getOrDefault(s) }
    }
}

/** Translates a Simplified Chinese source string into the current UI language. */
fun T(s: String): String = Lang.translate(s)

/** [T] for format strings: T("已选 %d 个", n). */
fun T(s: String, vararg args: Any?): String = String.format(Locale.US, Lang.translate(s), *args)
