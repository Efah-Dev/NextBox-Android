package dev.efah.nextbox.data

import dev.efah.nextbox.ui.T
import android.content.Context
import dev.efah.nextbox.bg.Box
import dev.efah.nextbox.nbcore.LatencyHandler
import dev.efah.nextbox.nbcore.Nbcore
import dev.efah.nextbox.nbtest.Nbtest
import dev.efah.nextbox.nbtest.LatencyHandler as NbtestHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.TimeZone

/**
 * The app's single source of data: a thin, coroutine friendly wrapper around
 * nbcore. Every write refreshes the exposed flows; changes that affect the
 * running core reload it.
 */
object Repo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    /** Nodes whose latency test is in progress. */
    private val _testing = MutableStateFlow<Set<Long>>(emptySet())
    val testing: StateFlow<Set<Long>> = _testing.asStateFlow()

    lateinit var ruleSetDir: String
        private set

    fun init(context: Context) {
        val dir = File(context.filesDir, "data")
        ruleSetDir = File(dir, "rule-set").path
        Nbcore.open(dir.path)
        val tz = TimeZone.getDefault()
        Nbcore.setTimeZone(tz.id, tz.getOffset(System.currentTimeMillis()) / 1000)
        refreshNow()
        Prefs.migrateRoute(_state.value.settings.routeMode)
    }

    /** Android-only switches passed to nbcore.BuildConfig. */
    fun coreOptions(): String = buildJsonObject {
        put("fakeIP", Prefs.fakeIp.value)
        put("findProcess", Prefs.showApps.value)
        if (dev.efah.nextbox.BuildConfig.DEBUG) {
            // debug builds keep a full core log in the cache dir (adb run-as)
            put("logLevel", "debug")
            put("logFile", File(dev.efah.nextbox.App.app.cacheDir, "box.log").path)
        } else {
            put("logLevel", "warn")
        }
    }.toString()

    fun buildConfig(): String = Nbcore.buildConfig(ruleSetDir, coreOptions())

    private fun refreshNow() {
        val st = NbJson.decodeFromString<AppState>(Nbcore.state())
        _state.value = st
        _profiles.value = NbJson.decodeFromString<List<Profile>>(Nbcore.profiles(st.currentGroupId))
    }

    /** Runs a nbcore call on the IO dispatcher, then refreshes the flows. */
    suspend fun <T> call(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        val r = runCatching(block)
        runCatching { refreshNow() }
        r
    }

    // ---------------------------------------------------------------- settings

    /** Merges the given keys into settings.json; reloads the core when it runs. */
    suspend fun updateSettings(patch: JsonObject, reload: Boolean = true): Result<Unit> {
        val r = call { Nbcore.saveSettings(patch.toString()) }
        if (r.isSuccess && reload) Box.reloadIfRunning()
        return r
    }

    /**
     * Routing as the user sees it: a scope (every app / selected apps / all
     * direct) plus the "bypass mainland China" switch, which applies to both
     * proxied scopes. The core only knows global / rule / direct.
     */
    suspend fun setRoute(scope: String = Prefs.routeScope.value, bypass: Boolean = Prefs.bypassCn.value): Result<Unit> {
        Prefs.setRouteScope(scope)
        Prefs.setBypassCn(bypass)
        val mode = when {
            scope == Prefs.SCOPE_DIRECT -> "direct"
            bypass -> "rule"
            else -> "global"
        }
        return updateSettings(buildJsonObject { put("routeMode", mode) })
    }

    // ---------------------------------------------------------------- groups & nodes

    suspend fun selectGroup(id: Long) = call { Nbcore.setCurrentGroup(id) }

    suspend fun select(id: Long): Result<Unit> {
        val r = call { Nbcore.setSelected(id) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    suspend fun importText(text: String): Result<String> =
        call { Nbcore.importText(text, _state.value.currentGroupId) }

    suspend fun addGroup(name: String) = call { Nbcore.addGroup(name) }

    suspend fun editGroup(g: Group, name: String, url: String) = call { Nbcore.editGroup(g.id, name, url, g.userAgent) }

    suspend fun deleteGroup(id: Long) = call { Nbcore.deleteGroup(id) }

    suspend fun addSubscription(name: String, url: String): Result<String> =
        call { Nbcore.addSubscription(name, url, Box.subscriptionProxy()) }

    suspend fun updateSubscription(groupId: Long): Result<String> =
        call { Nbcore.updateSubscription(groupId, Box.subscriptionProxy()) }

    private val autoUpdating = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lastAttempt = HashMap<Long, Long>()

    /**
     * Updates subscriptions older than the configured interval. Runs when the
     * app starts and every few minutes while connected; a failed update is
     * retried at most once an hour.
     */
    suspend fun autoUpdateSubscriptions() = withContext(Dispatchers.IO) {
        val hours = Prefs.subAutoHours.value
        if (hours <= 0 || !autoUpdating.compareAndSet(false, true)) return@withContext
        try {
            val now = System.currentTimeMillis() / 1000
            val due = _state.value.groups.filter {
                it.isSubscription && now - it.updatedAt >= hours * 3600L && now - (lastAttempt[it.id] ?: 0) >= 3600
            }
            for (g in due) {
                lastAttempt[g.id] = now
                runCatching { Nbcore.updateSubscription(g.id, Box.subscriptionProxy()) }
            }
            if (due.isNotEmpty()) runCatching { refreshNow() }
        } finally {
            autoUpdating.set(false)
        }
    }

    suspend fun saveProfile(p: Profile): Result<Profile> {
        val r = call { NbJson.decodeFromString<Profile>(Nbcore.saveProfile(NbJson.encodeToString(Profile.serializer(), p))) }
        if (r.isSuccess && r.getOrNull()?.id == _state.value.selectedId) Box.reloadIfRunning()
        return r
    }

    suspend fun deleteProfiles(ids: Collection<Long>) = call { Nbcore.deleteProfiles(ids.joinToString(",")) }

    suspend fun sortGroup(by: String) = call { Nbcore.sortGroup(_state.value.currentGroupId, by) }

    suspend fun exportLinks(ids: Collection<Long>): Result<String> = call { Nbcore.exportLinks(ids.joinToString(",")) }

    suspend fun profile(id: Long): Profile? = withContext(Dispatchers.IO) {
        if (id == 0L) null else runCatching { NbJson.decodeFromString<Profile>(Nbcore.profile(id)) }.getOrNull()
    }

    // ---------------------------------------------------------------- latency

    /** Protocols that run over UDP: a TCP handshake says nothing about them. */
    private val udpTypes = setOf("hysteria2", "hysteria", "tuic", "wireguard")

    /**
     * Tests the given nodes with the configured method; UDP based protocols
     * always use the URL test. Results show up in [profiles] as they arrive.
     */
    fun testLatency(ids: List<Long>) {
        if (ids.isEmpty()) return
        val byId = _profiles.value.associateBy { it.id }
        val (urlIds, tcpIds) = if (Prefs.latencyMode.value == Prefs.TEST_URL) {
            ids to emptyList()
        } else {
            ids.partition { byId[it]?.type in udpTypes }
        }
        val s = _state.value.settings
        _testing.value = ids.toSet()
        fun onResult(id: Long, latency: Int, message: String) {
            _testing.update { it - id }
            _profiles.update { list -> list.map { if (it.id == id) it.copy(latency = latency, latencyError = message) else it } }
        }
        scope.launch {
            val jobs = listOf(
                launch {
                    if (tcpIds.isNotEmpty()) runCatching {
                        Nbcore.tcpPing(tcpIds.joinToString(","), object : LatencyHandler {
                            override fun onStart(id: Long) {}
                            override fun onResult(id: Long, latency: Int, message: String) = onResult(id, latency, message)
                        })
                    }
                },
                launch {
                    if (urlIds.isNotEmpty()) runCatching {
                        Nbtest.urlTest(urlIds.joinToString(","), s.testUrl, s.testTimeout, s.testConcurrency, object : NbtestHandler {
                            override fun onStart(id: Long) {}
                            override fun onResult(id: Long, latency: Int, message: String) = onResult(id, latency, message)
                        })
                    }.onFailure { e -> urlIds.forEach { onResult(it, -1, e.message ?: T("测速失败")) } }
                },
            )
            jobs.forEach { it.join() }
            _testing.value = emptySet()
            runCatching { refreshNow() }
        }
    }

    fun cancelTest() {
        Nbcore.cancelTest()
        Nbtest.cancel()
    }

    // ---------------------------------------------------------------- usage & backup

    suspend fun usage(): UsageReport = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<UsageReport>(Nbcore.usage(Prefs.billingDay.value)) }
            .getOrDefault(UsageReport())
    }

    suspend fun resetUsage() = call { Nbcore.resetUsage() }

    suspend fun backup(): Result<String> = call { Nbcore.backup() }

    suspend fun restore(json: String): Result<Unit> {
        val r = call { Nbcore.restore(json) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    suspend fun updateRuleSets(): Result<String> = call { Nbcore.updateRuleSets(ruleSetDir, Box.subscriptionProxy()) }
}
