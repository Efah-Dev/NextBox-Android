package dev.efah.nextbox.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-only preferences. Everything shared with the Windows app (nodes,
 * DNS, theme, test URL / timeout) lives in nbcore's settings.json instead.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    /** Which traffic goes through NextBox. */
    const val SCOPE_GLOBAL = "global" // every app
    const val SCOPE_APPS = "apps" // only the selected apps
    const val SCOPE_DIRECT = "direct" // everything direct (VPN stays on, e.g. for ad blocking)

    private class Pref<T>(val key: String, val default: T) {
        val flow = MutableStateFlow(default)
    }

    private val scope = Pref("route_scope", SCOPE_GLOBAL)
    private val bypass = Pref("bypass_cn", false)
    private val apps = Pref("per_app_list", emptySet<String>())
    private val speed = Pref("notify_speed", true)
    private val stats = Pref("notify_stats", true)
    private val billing = Pref("billing_day", 1)
    private val fake = Pref("fake_ip", true)
    private val owner = Pref("show_apps", false)
    private val glassPref = Pref("glass", true)
    private val lang = Pref("language", "auto")
    private val subHours = Pref("sub_auto_hours", 24)
    private val hideRecentsPref = Pref("hide_recents", false)

    val routeScope: StateFlow<String> = scope.flow.asStateFlow()
    val bypassCn: StateFlow<Boolean> = bypass.flow.asStateFlow()
    val perAppList: StateFlow<Set<String>> = apps.flow.asStateFlow()
    val notifySpeed: StateFlow<Boolean> = speed.flow.asStateFlow()
    val notifyStats: StateFlow<Boolean> = stats.flow.asStateFlow()
    val billingDay: StateFlow<Int> = billing.flow.asStateFlow()
    val fakeIp: StateFlow<Boolean> = fake.flow.asStateFlow()
    val showApps: StateFlow<Boolean> = owner.flow.asStateFlow()

    /** Liquid glass effects (blur and refraction) on bars, buttons and dialogs. */
    val glass: StateFlow<Boolean> = glassPref.flow.asStateFlow()

    /** "auto" (follow the system), "zh-CN", "zh-TW" or "en". */
    val language: StateFlow<String> = lang.flow.asStateFlow()

    /** Update subscriptions older than this many hours; 0 turns it off. */
    val subAutoHours: StateFlow<Int> = subHours.flow.asStateFlow()

    /** Keep NextBox out of the recent apps (隐藏后台); the VPN keeps running. */
    val hideRecents: StateFlow<Boolean> = hideRecentsPref.flow.asStateFlow()

    private var migrated = true

    /** An earlier version ran here (its preferences were there before this start). */
    private var hadData = false

    fun init(context: Context) {
        sp = context.getSharedPreferences("nextbox", Context.MODE_PRIVATE)
        hadData = sp.all.isNotEmpty()
        migrated = sp.contains(scope.key)
        scope.flow.value = sp.getString(scope.key, scope.default) ?: scope.default
        bypass.flow.value = sp.getBoolean(bypass.key, bypass.default)
        apps.flow.value = sp.getStringSet(apps.key, emptySet()).orEmpty().toSet()
        speed.flow.value = sp.getBoolean(speed.key, speed.default)
        stats.flow.value = sp.getBoolean(stats.key, stats.default)
        billing.flow.value = sp.getInt(billing.key, billing.default)
        fake.flow.value = sp.getBoolean(fake.key, fake.default)
        owner.flow.value = sp.getBoolean(owner.key, owner.default)
        glassPref.flow.value = sp.getBoolean(glassPref.key, glassPref.default)
        lang.flow.value = sp.getString(lang.key, lang.default) ?: lang.default
        subHours.flow.value = sp.getInt(subHours.key, subHours.default)
        hideRecentsPref.flow.value = sp.getBoolean(hideRecentsPref.key, hideRecentsPref.default)
        autoUpdatePref.flow.value = sp.getBoolean(autoUpdatePref.key, autoUpdatePref.default)
        listOf(textModePref, appModePref, autoConnectPref, autoSelectPref, autoSelectAllPref, autoSelectTimeoutPref, autoSelectWatchPref).forEach { load(it) }
    }

    /** First start of this version: derive the route scope from the core setting. */
    fun migrateRoute(coreRouteMode: String) {
        if (migrated) return
        migrated = true
        when (coreRouteMode) {
            "direct" -> setRouteScope(SCOPE_DIRECT)
            "rule" -> {
                setRouteScope(SCOPE_GLOBAL)
                setBypassCn(true)
            }
            else -> setRouteScope(SCOPE_GLOBAL)
        }
    }

    fun setRouteScope(v: String) {
        scope.flow.value = v
        sp.edit().putString(scope.key, v).apply()
    }

    fun setBypassCn(v: Boolean) {
        bypass.flow.value = v
        sp.edit().putBoolean(bypass.key, v).apply()
    }

    fun setPerAppList(v: Set<String>) {
        apps.flow.value = v
        sp.edit().putStringSet(apps.key, v).apply()
    }

    fun setNotifySpeed(v: Boolean) {
        speed.flow.value = v
        sp.edit().putBoolean(speed.key, v).apply()
    }

    fun setNotifyStats(v: Boolean) {
        stats.flow.value = v
        sp.edit().putBoolean(stats.key, v).apply()
    }

    fun setBillingDay(v: Int) {
        billing.flow.value = v.coerceIn(1, 28)
        sp.edit().putInt(billing.key, billing.flow.value).apply()
    }

    /**
     * The speed test method of a version before 2.2.1, which kept it here
     * (now Settings.testMode, shared with NextBox for Windows), once: what
     * the user chose, or URL, the default of those versions, when they never
     * chose (only a choice was stored). null for a new install (URL, the
     * default again since 2.3.0) and after the first call.
     */
    fun takeLegacyTestMode(): String? {
        if (shown("test_mode_shared")) return null
        val v = sp.getString("test_mode", null)
        sp.edit().remove("test_mode").putBoolean("shown_test_mode_shared", true).apply()
        return when {
            v == "tcp" || v == "url" -> v
            hadData -> "url"
            else -> null
        }
    }

    fun setHideRecents(v: Boolean) {
        hideRecentsPref.flow.value = v
        sp.edit().putBoolean(hideRecentsPref.key, v).apply()
    }

    fun setFakeIp(v: Boolean) {
        fake.flow.value = v
        sp.edit().putBoolean(fake.key, v).apply()
    }

    fun setShowApps(v: Boolean) {
        owner.flow.value = v
        sp.edit().putBoolean(owner.key, v).apply()
    }

    fun setGlass(v: Boolean) {
        glassPref.flow.value = v
        sp.edit().putBoolean(glassPref.key, v).apply()
    }

    fun setLanguage(v: String) {
        lang.flow.value = v
        sp.edit().putString(lang.key, v).apply()
    }

    fun setSubAutoHours(v: Int) {
        subHours.flow.value = v
        sp.edit().putInt(subHours.key, v).apply()
    }

    private val autoUpdatePref = Pref("auto_update", false)

    /** The rules page shows the rules as text (one per line) instead of the route. */
    private val textModePref = Pref("rule_text_mode", false)
    val ruleTextMode: StateFlow<Boolean> = textModePref.flow.asStateFlow()
    fun setRuleTextMode(v: Boolean) = put(textModePref, v)

    /** Look for a newer release on GitHub when the app starts (off unless the user turns it on). */
    val autoUpdate: StateFlow<Boolean> = autoUpdatePref.flow.asStateFlow()

    fun setAutoUpdate(v: Boolean) {
        autoUpdatePref.flow.value = v
        sp.edit().putBoolean(autoUpdatePref.key, v).apply()
    }

    /** When the last successful update check ran (epoch milliseconds). */
    var updateChecked: Long
        get() = sp.getLong("update_checked", 0)
        set(v) = sp.edit().putLong("update_checked", v).apply()

    /** The release the user answered "later" to; the automatic check stays quiet about it. */
    var skippedVersion: String
        get() = sp.getString("update_skipped", "").orEmpty()
        set(v) = sp.edit().putString("update_skipped", v).apply()

    // ---- added in 1.1.0 ----

    const val APPS_INCLUDE = "include" // only the selected apps use the VPN
    const val APPS_EXCLUDE = "exclude" // every app except the selected ones

    private val appModePref = Pref("per_app_mode", APPS_INCLUDE)
    private val autoConnectPref = Pref("auto_connect", false)
    private val autoSelectPref = Pref("auto_select", false)
    private val autoSelectAllPref = Pref("auto_select_all", false)
    private val autoSelectTimeoutPref = Pref("auto_select_timeout", 3000)
    private val autoSelectWatchPref = Pref("auto_select_watch", false)

    /** How the selected apps are used in the "selected apps" scope. */
    val perAppMode: StateFlow<String> = appModePref.flow.asStateFlow()

    /** Connect when the user opens the app. */
    val autoConnect: StateFlow<Boolean> = autoConnectPref.flow.asStateFlow()

    /** Switch to a working node when the selected one does not answer. */
    val autoSelect: StateFlow<Boolean> = autoSelectPref.flow.asStateFlow()

    /** Look for a replacement among all nodes instead of the node's own group. */
    val autoSelectAll: StateFlow<Boolean> = autoSelectAllPref.flow.asStateFlow()

    /** How long a node may take before it counts as unreachable (ms). */
    val autoSelectTimeout: StateFlow<Int> = autoSelectTimeoutPref.flow.asStateFlow()

    /** Also check the node while connected and switch when it stops working. */
    val autoSelectWatch: StateFlow<Boolean> = autoSelectWatchPref.flow.asStateFlow()

    fun setPerAppMode(v: String) = put(appModePref, v)
    fun setAutoConnect(v: Boolean) = put(autoConnectPref, v)
    fun setAutoSelect(v: Boolean) = put(autoSelectPref, v)
    fun setAutoSelectAll(v: Boolean) = put(autoSelectAllPref, v)
    fun setAutoSelectTimeout(v: Int) = put(autoSelectTimeoutPref, v.coerceIn(1000, 10000))
    fun setAutoSelectWatch(v: Boolean) = put(autoSelectWatchPref, v)

    private fun <T> put(p: Pref<T>, v: T) {
        p.flow.value = v
        sp.edit().apply {
            when (v) {
                is Boolean -> putBoolean(p.key, v)
                is Int -> putInt(p.key, v)
                is String -> putString(p.key, v)
            }
        }.apply()
    }

    private fun <T> load(p: Pref<T>) {
        @Suppress("UNCHECKED_CAST")
        p.flow.value = when (val d = p.default) {
            is Boolean -> sp.getBoolean(p.key, d) as T
            is Int -> sp.getInt(p.key, d) as T
            is String -> (sp.getString(p.key, d) ?: d) as T
            else -> d
        }
    }

    /**
     * The Android-only preferences that travel in a backup ("android" in the
     * file): the look (glass, language) with the appearance part, the rest
     * with the other settings.
     */
    fun exportJson(appearance: Boolean = true, settings: Boolean = true): String = kotlinx.serialization.json.buildJsonObject {
        if (appearance) {
            put("glass", kotlinx.serialization.json.JsonPrimitive(glass.value))
            put("language", kotlinx.serialization.json.JsonPrimitive(language.value))
            put("hideRecents", kotlinx.serialization.json.JsonPrimitive(hideRecents.value))
        }
        if (!settings) return@buildJsonObject
        put("routeScope", kotlinx.serialization.json.JsonPrimitive(routeScope.value))
        put("perAppMode", kotlinx.serialization.json.JsonPrimitive(perAppMode.value))
        put("perAppList", kotlinx.serialization.json.JsonArray(perAppList.value.sorted().map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("notifySpeed", kotlinx.serialization.json.JsonPrimitive(notifySpeed.value))
        put("notifyStats", kotlinx.serialization.json.JsonPrimitive(notifyStats.value))
        put("billingDay", kotlinx.serialization.json.JsonPrimitive(billingDay.value))
        // for versions before 2.2.1, which kept the test method here
        put("latencyMode", kotlinx.serialization.json.JsonPrimitive(Repo.state.value.settings.testMode))
        put("fakeIp", kotlinx.serialization.json.JsonPrimitive(fakeIp.value))
        put("subAutoHours", kotlinx.serialization.json.JsonPrimitive(subAutoHours.value))
        put("autoConnect", kotlinx.serialization.json.JsonPrimitive(autoConnect.value))
        put("autoSelect", kotlinx.serialization.json.JsonPrimitive(autoSelect.value))
        put("autoSelectAll", kotlinx.serialization.json.JsonPrimitive(autoSelectAll.value))
        put("autoSelectTimeout", kotlinx.serialization.json.JsonPrimitive(autoSelectTimeout.value))
        put("autoSelectWatch", kotlinx.serialization.json.JsonPrimitive(autoSelectWatch.value))
    }.toString()

    /** Applies preferences of the chosen parts from a backup; unknown or missing keys are left alone. */
    fun importJson(js: String, appearance: Boolean = true, settings: Boolean = true) {
        val o = runCatching { NbJson.parseToJsonElement(js) as kotlinx.serialization.json.JsonObject }.getOrNull() ?: return
        fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        fun bool(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
        fun int(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
        if (appearance) {
            bool("glass")?.let { setGlass(it) }
            str("language")?.takeIf { it == "auto" || it == "zh-CN" || it == "zh-TW" || it == "en" }?.let { setLanguage(it); dev.efah.nextbox.ui.Lang.apply(it) }
            bool("hideRecents")?.let { setHideRecents(it) }
        }
        if (!settings) return
        // "direct" (everything direct) was a scope in 1.x; it is a routing mode now, set with the rules (see ImportBackup)
        str("routeScope")?.let { if (it == SCOPE_DIRECT) SCOPE_GLOBAL else it }?.takeIf { it == SCOPE_GLOBAL || it == SCOPE_APPS }?.let { setRouteScope(it) }
        str("perAppMode")?.takeIf { it == APPS_INCLUDE || it == APPS_EXCLUDE }?.let { setPerAppMode(it) }
        (o["perAppList"] as? kotlinx.serialization.json.JsonArray)?.let { a -> setPerAppList(a.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.toSet()) }
        bool("notifySpeed")?.let { setNotifySpeed(it) }
        bool("notifyStats")?.let { setNotifyStats(it) }
        int("billingDay")?.let { setBillingDay(it) }
        bool("fakeIp")?.let { setFakeIp(it) }
        int("subAutoHours")?.let { setSubAutoHours(it) }
        bool("autoConnect")?.let { setAutoConnect(it) }
        bool("autoSelect")?.let { setAutoSelect(it) }
        bool("autoSelectAll")?.let { setAutoSelectAll(it) }
        int("autoSelectTimeout")?.let { setAutoSelectTimeout(it) }
        bool("autoSelectWatch")?.let { setAutoSelectWatch(it) }
    }

    /** One-time hints ("notify_guide" ...). */
    fun shown(key: String): Boolean = sp.getBoolean("shown_$key", false)

    fun markShown(key: String) = sp.edit().putBoolean("shown_$key", true).apply()
}
