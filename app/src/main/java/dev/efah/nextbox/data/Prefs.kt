package dev.efah.nextbox.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    const val TEST_TCP = "tcp"
    const val TEST_URL = "url"

    private class Pref<T>(val key: String, val default: T) {
        val flow = MutableStateFlow(default)
    }

    private val scope = Pref("route_scope", SCOPE_GLOBAL)
    private val bypass = Pref("bypass_cn", false)
    private val apps = Pref("per_app_list", emptySet<String>())
    private val speed = Pref("notify_speed", true)
    private val stats = Pref("notify_stats", true)
    private val billing = Pref("billing_day", 1)
    private val testMode = Pref("test_mode", TEST_URL)
    private val fake = Pref("fake_ip", true)
    private val owner = Pref("show_apps", false)
    private val glassPref = Pref("glass", true)
    private val lang = Pref("language", "auto")
    private val subHours = Pref("sub_auto_hours", 24)

    val routeScope: StateFlow<String> = scope.flow.asStateFlow()
    val bypassCn: StateFlow<Boolean> = bypass.flow.asStateFlow()
    val perAppList: StateFlow<Set<String>> = apps.flow.asStateFlow()
    val notifySpeed: StateFlow<Boolean> = speed.flow.asStateFlow()
    val notifyStats: StateFlow<Boolean> = stats.flow.asStateFlow()
    val billingDay: StateFlow<Int> = billing.flow.asStateFlow()
    val latencyMode: StateFlow<String> = testMode.flow.asStateFlow()
    val fakeIp: StateFlow<Boolean> = fake.flow.asStateFlow()
    val showApps: StateFlow<Boolean> = owner.flow.asStateFlow()

    /** Liquid glass effects (blur and refraction) on bars, buttons and dialogs. */
    val glass: StateFlow<Boolean> = glassPref.flow.asStateFlow()

    /** "auto" (follow the system), "zh-CN", "zh-TW" or "en". */
    val language: StateFlow<String> = lang.flow.asStateFlow()

    /** Update subscriptions older than this many hours; 0 turns it off. */
    val subAutoHours: StateFlow<Int> = subHours.flow.asStateFlow()

    private var migrated = true

    fun init(context: Context) {
        sp = context.getSharedPreferences("nextbox", Context.MODE_PRIVATE)
        migrated = sp.contains(scope.key)
        scope.flow.value = sp.getString(scope.key, scope.default) ?: scope.default
        bypass.flow.value = sp.getBoolean(bypass.key, bypass.default)
        apps.flow.value = sp.getStringSet(apps.key, emptySet()).orEmpty().toSet()
        speed.flow.value = sp.getBoolean(speed.key, speed.default)
        stats.flow.value = sp.getBoolean(stats.key, stats.default)
        billing.flow.value = sp.getInt(billing.key, billing.default)
        testMode.flow.value = sp.getString(testMode.key, testMode.default) ?: testMode.default
        fake.flow.value = sp.getBoolean(fake.key, fake.default)
        owner.flow.value = sp.getBoolean(owner.key, owner.default)
        glassPref.flow.value = sp.getBoolean(glassPref.key, glassPref.default)
        lang.flow.value = sp.getString(lang.key, lang.default) ?: lang.default
        subHours.flow.value = sp.getInt(subHours.key, subHours.default)
        autoUpdatePref.flow.value = sp.getBoolean(autoUpdatePref.key, autoUpdatePref.default)
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

    fun setLatencyMode(v: String) {
        testMode.flow.value = v
        sp.edit().putString(testMode.key, v).apply()
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

    /** One-time hints ("notify_guide" ...). */
    fun shown(key: String): Boolean = sp.getBoolean("shown_$key", false)

    fun markShown(key: String) = sp.edit().putBoolean("shown_$key", true).apply()
}
