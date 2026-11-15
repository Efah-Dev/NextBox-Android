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
        // 1.1.0 moved "bypass mainland China" into the core (the "cn" scene of
        // the shared rule model): carry the old switch over once.
        if (!Prefs.shown("route_v2")) {
            runCatching { Nbcore.setRoute(Prefs.routeScope.value, Prefs.bypassCn.value) }
            Prefs.markShown("route_v2")
            refreshNow()
        }
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

    /** Name of the node selected on the home page ("" when none). */
    fun selectedNodeName(): String = runCatching { Nbcore.selectedNodeName() }.getOrDefault("")

    private fun refreshNow() {
        val st = NbJson.decodeFromString<AppState>(Nbcore.state())
        _state.value = st
        // the switch can also change on the rules page or by importing a backup
        if (st.settings.bypassCn != Prefs.bypassCn.value) Prefs.setBypassCn(st.settings.bypassCn)
        if (st.settings.routeMode == "direct" && Prefs.routeScope.value != Prefs.SCOPE_DIRECT) Prefs.setRouteScope(Prefs.SCOPE_DIRECT)
        if (st.settings.routeMode != "direct" && Prefs.routeScope.value == Prefs.SCOPE_DIRECT) Prefs.setRouteScope(Prefs.SCOPE_GLOBAL)
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
        val r = call { Nbcore.setRoute(scope, bypass) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
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

    /** Sorts the current group by "latency", "name" or "region". */
    suspend fun sortGroup(by: String) = call { Nbcore.sortGroup(_state.value.currentGroupId, by) }

    /** Puts the current group's nodes in the order the user dragged them into. */
    suspend fun reorderProfiles(ids: List<Long>) = call { Nbcore.reorderProfiles(_state.value.currentGroupId, ids.joinToString(",")) }

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
            ids.partition { id -> byId[id]?.let { it.type in udpTypes || (it.type == "naive" && it.outbound["quic"]?.toString() == "true") } == true }
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

    /** A backup of everything, in the format both NextBox apps read, sealed with the password. */
    suspend fun exportBackup(password: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { Nbcore.exportBackup(password, Prefs.exportJson()) }
    }

    suspend fun inspectBackup(content: String, password: String): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<BackupInfo>(Nbcore.inspectBackup(content, password)) }
    }

    @kotlinx.serialization.Serializable
    private data class ImportResult(val message: String = "", val android: JsonObject? = null)

    /** Imports everything a backup holds (replacing nodes and rules, or adding to them) and returns the message to show. */
    suspend fun importBackup(content: String, password: String, replace: Boolean): Result<String> {
        val r = call {
            val res = NbJson.decodeFromString<ImportResult>(Nbcore.importBackup(content, password, replace))
            res.android?.let { Prefs.importJson(it.toString()) }
            res.message
        }
        if (r.isSuccess) {
            // the core now holds the imported route; make the Android side agree
            runCatching { Nbcore.setRoute(Prefs.routeScope.value, _state.value.settings.bypassCn) }
            runCatching { refreshNow() }
            Box.reloadIfRunning()
        }
        return r
    }

    // ---------------------------------------------------------------- import

    /** What a scanned code, the clipboard or a shared text holds. */
    fun classify(text: String): Classified =
        runCatching { NbJson.decodeFromString<Classified>(Nbcore.classify(text)) }.getOrDefault(Classified())

    // ---------------------------------------------------------------- rules

    /** Saves the rules and reloads the core. Their order does not matter: the route path decides it. */
    suspend fun saveRules(rules: List<Rule>): Result<Unit> {
        val js = NbJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Rule.serializer()), rules)
        val r = call { Nbcore.saveRules(js) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    /** Which step of the route path a website or IP (and optionally an app) leaves at. */
    suspend fun testRoute(input: String, pkg: String = ""): Result<RouteMatch> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<RouteMatch>(Nbcore.testRoute(ruleSetDir, input, pkg)) }
    }

    // ---------------------------------------------------------------- policy groups

    private val _policies = MutableStateFlow<List<PolicyView>>(emptyList())
    val policies: StateFlow<List<PolicyView>> = _policies.asStateFlow()

    suspend fun loadPolicies() = withContext(Dispatchers.IO) {
        runCatching { _policies.value = NbJson.decodeFromString<List<PolicyView>>(Nbcore.policies()) }
    }

    suspend fun previewPolicy(p: Policy): List<PolicyNode> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<List<PolicyNode>>(Nbcore.previewPolicy(NbJson.encodeToString(Policy.serializer(), p))) }
            .getOrDefault(emptyList())
    }

    suspend fun savePolicy(p: Policy): Result<Policy> {
        val r = call { NbJson.decodeFromString<Policy>(Nbcore.savePolicy(NbJson.encodeToString(Policy.serializer(), p))) }
        loadPolicies()
        if (r.isSuccess && Nbcore.policyUsed(r.getOrThrow().id)) Box.reloadIfRunning()
        return r
    }

    suspend fun deletePolicy(id: Long): Result<Unit> {
        val used = Nbcore.policyUsed(id)
        val r = call { Nbcore.deletePolicy(id) }
        loadPolicies()
        if (r.isSuccess && used) Box.reloadIfRunning()
        return r
    }

    suspend fun selectPolicyNode(id: Long, node: Long): Result<Unit> {
        val r = call { Nbcore.selectPolicyNode(id, node) }
        loadPolicies()
        if (r.isSuccess && Nbcore.policyUsed(id)) Box.reloadIfRunning()
        return r
    }

    /** The regions a policy group can pick, in the desktop app's order. */
    val places: List<Place> by lazy {
        runCatching { NbJson.decodeFromString<List<Place>>(Nbcore.places()) }.getOrDefault(emptyList())
    }

    // ---------------------------------------------------------------- chains & all nodes

    /** Every node of every group (for pickers). */
    suspend fun allProfiles(): List<Profile> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<List<Profile>>(Nbcore.profiles(0)) }.getOrDefault(emptyList())
    }

    @kotlinx.serialization.Serializable
    private data class ChainInput(val id: Long, val groupId: Long, val name: String, val chain: List<Long>)

    suspend fun saveChain(id: Long, groupId: Long, name: String, chain: List<Long>): Result<Profile> {
        val r = call {
            NbJson.decodeFromString<Profile>(Nbcore.saveChain(NbJson.encodeToString(ChainInput.serializer(), ChainInput(id, groupId, name, chain))))
        }
        if (r.isSuccess && r.getOrThrow().id == _state.value.selectedId) Box.reloadIfRunning()
        return r
    }

    /** Names of the chain proxies that would break if these nodes were deleted. */
    fun chainsUsing(ids: Collection<Long>): List<String> =
        runCatching { Nbcore.chainsUsing(ids.joinToString(",")).lines().filter { it.isNotBlank() } }.getOrDefault(emptyList())

    suspend fun updateRuleSets(): Result<String> = call { Nbcore.updateRuleSets(ruleSetDir, Box.subscriptionProxy()) }
}
