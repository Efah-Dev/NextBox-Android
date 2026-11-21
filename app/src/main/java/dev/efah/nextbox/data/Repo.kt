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
import kotlinx.serialization.encodeToString
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
        // 2.0 routes like NextBox for Windows: the rules are checked in the
        // order the user gives them. Data of a 1.x version (which marked
        // route_v2) gets its rules in the order the old route path checked
        // them, once, so that traffic goes where it went before; "all direct"
        // is a routing mode now, no longer a scope.
        if (!Prefs.shown("route_v3")) {
            if (Prefs.shown("route_v2")) {
                runCatching { Nbcore.migrateRoute(Prefs.routeScope.value == Prefs.SCOPE_DIRECT) }
            }
            if (Prefs.routeScope.value == Prefs.SCOPE_DIRECT) Prefs.setRouteScope(Prefs.SCOPE_GLOBAL)
            Prefs.markShown("route_v2")
            Prefs.markShown("route_v3")
            refreshNow()
        }
    }

    /** Android-only switches passed to nbcore.BuildConfig. */
    fun coreOptions(): String = buildJsonObject {
        put("fakeIP", Prefs.fakeIp.value)
        put("findProcess", Prefs.showApps.value)
        if (dev.efah.nextbox.BuildConfig.DEBUG) {
            // debug builds keep a full core log (see bg.DebugLog)
            put("logLevel", "debug")
            put("logFile", dev.efah.nextbox.bg.DebugLog.file.path)
        } else {
            put("logLevel", "warn")
        }
    }.toString()

    fun buildConfig(): String = Nbcore.buildConfig(ruleSetDir, coreOptions())

    /** Name of the node selected on the home page ("" when none). */
    fun selectedNodeName(): String = runCatching { Nbcore.selectedNodeName() }.getOrDefault("")

    /** The selected node as lists show it: its name with the flag of its region ("" when none). */
    fun selectedNodeLabel(): String = runCatching {
        val id = _state.value.selectedId
        if (id == 0L) "" else NbJson.decodeFromString<Profile>(Nbcore.profile(id)).let { dev.efah.nextbox.ui.flagged(it.name, it.region) }
    }.getOrDefault("")

    private fun refreshNow() {
        val st = NbJson.decodeFromString<AppState>(Nbcore.state())
        _state.value = st
        // the switch can also change on the rules page or by importing a backup
        if (st.settings.bypassCn != Prefs.bypassCn.value) Prefs.setBypassCn(st.settings.bypassCn)
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

    /** The routing mode, as on NextBox for Windows: "global", "rule" or "direct". */
    suspend fun setRouteMode(mode: String): Result<Unit> {
        val r = call { Nbcore.setRouteMode(mode) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    /**
     * Switches the "bypass mainland China" step (the "cn" scene). It is a step
     * of rule mode, so switching it on in global mode switches to rule mode
     * (with no rules of your own that routes like global mode plus the bypass).
     */
    suspend fun setBypass(on: Boolean): Result<Unit> {
        Prefs.setBypassCn(on)
        val s = _state.value.settings
        if (on && s.routeMode == "global") call { Nbcore.setRouteMode("rule") }
        return saveRules(s.rules, s.scenes.map { if (it.id == "cn") it.copy(enabled = on) else it })
    }

    /** Which apps use the VPN: every app, or the selected ones (see Prefs.perAppMode). */
    fun setScope(scope: String) {
        Prefs.setRouteScope(scope)
        Box.reloadIfRunning()
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

    suspend fun updateSubscription(groupId: Long): Result<String> = reloadIfConfigChanges {
        call { Nbcore.updateSubscription(groupId, Box.subscriptionProxy()) }
    }

    /**
     * What the Clash parts of a subscription would make; null when it has none.
     * Without [fetch] only its last update is looked at.
     */
    suspend fun clashPreview(groupId: Long, fetch: Boolean = false): Result<ClashInfo?> = withContext(Dispatchers.IO) {
        runCatching { Nbcore.clashPreview(groupId, fetch, Box.subscriptionProxy()).takeIf { it.isNotEmpty() }?.let { NbJson.decodeFromString<ClashInfo>(it) } }
    }

    /** The subscription's own DNS (empty values: the global DNS). */
    suspend fun setGroupDns(groupId: Long, remote: String, resolver: String): Result<Unit> {
        val r = call { Nbcore.setGroupDNS(groupId, remote, resolver) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    /** Switches a subscription between the DNS it names and the global one. */
    suspend fun useSubscriptionDns(groupId: Long, on: Boolean): Result<Unit> {
        val r = call { Nbcore.useSubscriptionDNS(groupId, on) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    /** Takes over the chosen parts of a Clash subscription, now and on every update. */
    suspend fun applyClash(groupId: Long, opt: ClashImport): Result<String> = reloadIfConfigChanges {
        call { Nbcore.applyClash(groupId, NbJson.encodeToString(ClashImport.serializer(), opt), Box.subscriptionProxy()) }
    }

    /** Where the connected node's traffic leaves; takes a few seconds. */
    suspend fun checkExit(): Result<ExitInfo> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<ExitInfo>(Nbcore.checkExit()) }
    }

    /**
     * The last exit check, kept here rather than on the home page, so that
     * coming back to the page (from settings, or into the app) shows it
     * instead of checking again. [session] is the connection it belongs to
     * (Box.startedAt): a new connection checks anew.
     */
    data class ExitState(val nodeId: Long, val session: Long, val info: ExitInfo?, val checking: Boolean, val at: Long) {
        val ok: Boolean get() = info != null && info.error.isEmpty()
        /** How long a result stands: 5 minutes, 30 seconds after a failure. */
        val validFor: Long get() = if (ok) 5 * 60_000L else 30_000L
    }

    private val _exit = MutableStateFlow<ExitState?>(null)
    val exit: StateFlow<ExitState?> = _exit.asStateFlow()
    private val exitLock = kotlinx.coroutines.sync.Mutex()

    /** Milliseconds until the result for this node and connection is due again; 0 = now. */
    fun exitDueIn(nodeId: Long, session: Long): Long {
        val e = _exit.value ?: return 0
        if (e.nodeId != nodeId || e.session != session || e.info == null) return 0
        return (e.at + e.validFor - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /** Checks again when the result is due (or [force]); one check at a time. */
    suspend fun refreshExit(nodeId: Long, session: Long, force: Boolean = false) {
        exitLock.lock()
        try {
            if (!force && exitDueIn(nodeId, session) > 0) return
            val prev = _exit.value?.takeIf { it.nodeId == nodeId && it.session == session }
            _exit.value = ExitState(nodeId, session, prev?.info, checking = true, at = prev?.at ?: 0)
            val info = checkExit().getOrElse { ExitInfo(latency = -1, error = it.message ?: T("检测失败")) }
            _exit.value = ExitState(nodeId, session, info, checking = false, at = System.currentTimeMillis())
        } finally {
            exitLock.unlock()
        }
    }

    /**
     * Runs an update of the node list (a subscription) and, while connected,
     * reloads the core when the configuration it would now build differs: a
     * node's address, port or password changed, or the node servers the route
     * sends direct. Without it the core keeps using the old node until the
     * user reconnects.
     */
    private suspend fun <T> reloadIfConfigChanges(block: suspend () -> T): T {
        val before = if (Box.isRunning) withContext(Dispatchers.IO) { runCatching { buildConfig() }.getOrNull() } else null
        val result = block()
        if (before != null && Box.isRunning) {
            val after = withContext(Dispatchers.IO) { runCatching { buildConfig() }.getOrNull() }
            if (after != null && after != before) Box.reloadIfRunning()
        }
        return result
    }

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
            if (due.isEmpty()) return@withContext
            reloadIfConfigChanges {
                for (g in due) {
                    lastAttempt[g.id] = now
                    runCatching { Nbcore.updateSubscription(g.id, Box.subscriptionProxy()) }
                }
            }
            runCatching { refreshNow() }
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

    /** The nodes of all groups that connect the same way as another one, each set with the one kept. */
    suspend fun findDuplicates(): List<DupSet> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<List<DupSet>>(Nbcore.findDuplicates()) }.getOrDefault(emptyList())
    }

    /** Removes the copies and returns how many went; chains and policy groups move to the kept nodes. */
    suspend fun deleteDuplicates(): Result<Long> = reloadIfConfigChanges {
        call { Nbcore.deleteDuplicates() }
    }

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
     * [sortAfter] sorts the current group by latency once the test is done.
     */
    fun testLatency(ids: List<Long>, sortAfter: Boolean = false) {
        if (ids.isEmpty()) return
        val byId = _profiles.value.associateBy { it.id }
        val (urlIds, tcpIds) = if (Prefs.latencyMode.value == Prefs.TEST_URL) {
            ids to emptyList()
        } else {
            ids.partition { id -> byId[id]?.let { it.type in udpTypes || (it.type == "naive" && it.outbound["quic"]?.toString() == "true") } == true }
        }
        val s = _state.value.settings
        _testing.value = ids.toSet()
        cancelled = false
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
            val stopped = cancelled
            cancelled = false
            _testing.value = emptySet()
            if (sortAfter && !stopped) runCatching { Nbcore.sortGroup(_state.value.currentGroupId, "latency") }
            runCatching { refreshNow() }
        }
    }

    @Volatile private var cancelled = false

    fun cancelTest() {
        cancelled = true
        Nbcore.cancelTest()
        Nbtest.cancel()
    }

    // ---------------------------------------------------------------- usage & backup

    suspend fun usage(): UsageReport = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<UsageReport>(Nbcore.usage(Prefs.billingDay.value)) }
            .getOrDefault(UsageReport())
    }

    suspend fun resetUsage() = call { Nbcore.resetUsage() }

    /** A backup of the chosen parts, in the format both NextBox apps read, sealed with the password. */
    suspend fun exportBackup(parts: BackupParts): Result<String> = withContext(Dispatchers.IO) {
        runCatching { Nbcore.exportBackup(NbJson.encodeToString(parts), Prefs.exportJson(parts.appearance, parts.settings)) }
    }

    suspend fun inspectBackup(content: String, password: String): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching { NbJson.decodeFromString<BackupInfo>(Nbcore.inspectBackup(content, password)) }
    }

    @kotlinx.serialization.Serializable
    private data class ImportResult(val message: String = "", val android: JsonObject? = null)

    /**
     * Imports the chosen parts of a backup (nodes added or replacing the list;
     * rules replacing the current ones) and returns the message to show.
     */
    suspend fun importBackup(content: String, parts: BackupParts): Result<String> {
        val r = call {
            val res = NbJson.decodeFromString<ImportResult>(Nbcore.importBackup(content, NbJson.encodeToString(parts)))
            res.android?.let { Prefs.importJson(it.toString(), parts.appearance, parts.settings) }
            res.message
        }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    // ---------------------------------------------------------------- import

    /** What a scanned code, the clipboard or a shared text holds. */
    fun classify(text: String): Classified =
        runCatching { NbJson.decodeFromString<Classified>(Nbcore.classify(text)) }.getOrDefault(Classified())

    // ---------------------------------------------------------------- rules

    /**
     * Saves the rules in the order they are checked (with the places of ad
     * blocking and the scenes), and the scenes when given; reloads the core.
     */
    suspend fun saveRules(rules: List<Rule>, scenes: List<Scene>? = null): Result<Unit> {
        val js = NbJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Rule.serializer()), rules)
        val sc = scenes?.let { NbJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(Scene.serializer()), it) } ?: ""
        val r = call { Nbcore.saveRules(js, sc) }
        if (r.isSuccess) Box.reloadIfRunning()
        return r
    }

    /** Downloads a rule-set a rule set names by its address, to see that it works. */
    suspend fun checkRuleSetUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { Nbcore.checkRuleSetURL(ruleSetDir, url, Box.subscriptionProxy()) }
    }

    /** Which step of the route a website or IP (and optionally an app) leaves at. */
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
