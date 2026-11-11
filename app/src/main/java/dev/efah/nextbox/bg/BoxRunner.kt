package dev.efah.nextbox.bg

import dev.efah.nextbox.ui.T
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.efah.nextbox.data.NbJson
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.UsageReport
import dev.efah.nextbox.libbox.CommandClient
import dev.efah.nextbox.libbox.CommandClientHandler
import dev.efah.nextbox.libbox.CommandClientOptions
import dev.efah.nextbox.libbox.CommandServer
import dev.efah.nextbox.libbox.CommandServerHandler
import dev.efah.nextbox.libbox.ConnectionEvents
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.libbox.LogIterator
import dev.efah.nextbox.libbox.OutboundGroupItemIterator
import dev.efah.nextbox.libbox.OutboundGroupIterator
import dev.efah.nextbox.libbox.OverrideOptions
import dev.efah.nextbox.libbox.PlatformInterface
import dev.efah.nextbox.libbox.StatusMessage
import dev.efah.nextbox.libbox.SystemProxyStatus
import dev.efah.nextbox.nbcore.Nbcore
import dev.efah.nextbox.nbtest.Nbtest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs sing-box for one service instance (VPN or proxy-only): the libbox
 * command server, the status stream that feeds the notification and usage
 * statistics, and the start / reload / stop life cycle.
 */
class BoxRunner(
    private val service: Service,
    private val platform: PlatformInterface,
    val isVpn: Boolean,
) : CommandServerHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var server: CommandServer? = null
    private var client: CommandClient? = null
    private var statsJob: Job? = null
    private val notifier = Notifier(service, isVpn)

    /** The TUN file descriptor handed out by VpnService (VPN mode only). */
    @Volatile
    var tunFd: ParcelFileDescriptor? = null

    // traffic accounting
    private var lastUp = 0L
    private var lastDown = 0L
    private var lastProxyUp = 0L
    private var lastProxyDown = 0L
    private var nodeId = 0L
    private var usage = UsageReport()

    private var receiverRegistered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> scope.launch { connectStatus() }
                // nobody looks at the speed with the screen off: stop the stream
                // (the totals catch up when it reconnects)
                Intent.ACTION_SCREEN_OFF -> disconnectStatus()
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    val pm = service.getSystemService(PowerManager::class.java)
                    scope.launch { if (pm.isDeviceIdleMode) server?.pause() else server?.wake() }
                }
            }
        }
    }

    fun onStartCommand(): Int {
        if (Box.runner != null && Box.status.value != Status.Stopped) return Service.START_NOT_STICKY
        Box.runner = this
        Box._status.value = Status.Starting
        Box._error.value = null
        notifier.startForeground(Nbcore.selectedNodeName(), T("正在连接…"))
        scope.launch { start() }
        return Service.START_NOT_STICKY
    }

    private suspend fun start() = lock.withLock {
        try {
            val config = Repo.buildConfig()
            Box.ownerLookup = needsOwner(config)
            DefaultNetworkMonitor.start()
            val s = Libbox.newCommandServer(this, platform)
            s.start()
            server = s
            s.startOrReloadService(config, overrideOptions())
            Nbtest.trackProxy(s)
            onStarted()
        } catch (e: Exception) {
            Log.e(TAG, "start", e)
            shutdown(e.message ?: e.toString())
        }
    }

    private suspend fun onStarted() {
        nodeId = Repo.state.value.selectedId
        Box._nodeName.value = Nbcore.selectedNodeName()
        Box._startedAt.value = System.currentTimeMillis()
        Box._session.value = Speed()
        Box._speed.value = Speed()
        Box._history.value = List(60) { Speed() }
        lastUp = 0
        lastDown = 0
        lastProxyUp = Nbtest.proxyUplink()
        lastProxyDown = Nbtest.proxyDownlink()
        Box._status.value = Status.Started
        withContext(Dispatchers.Main) { registerReceiver() }
        refreshUsage()
        notifier.update(Box.nodeName.value, Speed(), Speed(), usage)
        connectStatus()
        statsJob = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(10_000)
                refreshUsage()
                if (++ticks % 6 == 0) runCatching { Nbcore.flush() }
                if (ticks % 60 == 0) Repo.autoUpdateSubscriptions()
            }
        }
    }

    fun reload() {
        scope.launch {
            lock.withLock {
                val s = server ?: return@withLock
                try {
                    val config = Repo.buildConfig()
                    Box.ownerLookup = needsOwner(config)
                    s.startOrReloadService(config, overrideOptions())
                    Nbtest.trackProxy(s)
                    nodeId = Repo.state.value.selectedId
                    Box._nodeName.value = Nbcore.selectedNodeName()
                    lastUp = 0
                    lastDown = 0
                    notifier.update(Box.nodeName.value, Box.speed.value, Box.session.value, usage)
                    // a reload starts a new box instance: reattach the status stream
                    disconnectStatus()
                    connectStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "reload", e)
                    shutdown(e.message ?: e.toString())
                }
            }
        }
    }

    fun stop(then: (() -> Unit)? = null) {
        if (Box.status.value == Status.Stopping || Box.status.value == Status.Stopped) return
        scope.launch {
            lock.withLock { shutdown(null, then) }
        }
    }

    /**
     * Tears everything down; error != null reports a failed start. then runs
     * on the main thread afterwards (posted, so it survives this service's
     * onDestroy cancelling the scope).
     */
    private suspend fun shutdown(error: String?, then: (() -> Unit)? = null) {
        Box._status.value = Status.Stopping
        statsJob?.cancel()
        disconnectStatus()
        runCatching { server?.closeService() }
        runCatching { server?.close() }
        server = null
        runCatching { tunFd?.close() }
        tunFd = null
        DefaultNetworkMonitor.stop()
        runCatching { Nbcore.flush() }
        withContext(Dispatchers.Main) {
            if (receiverRegistered) {
                runCatching { service.unregisterReceiver(receiver) }
                receiverRegistered = false
            }
            notifier.stop()
            Box._error.value = error
            Box._speed.value = Speed()
            Box._status.value = Status.Stopped
            if (Box.runner === this@BoxRunner) Box.runner = null
            if (then != null) android.os.Handler(android.os.Looper.getMainLooper()).post(then)
            service.stopSelf()
        }
    }

    /**
     * Service.onDestroy: normally after [shutdown]; if the system destroys
     * the service on its own, release the core and the TUN interface here.
     */
    fun destroy() {
        if (Box.runner === this && Box.status.value != Status.Stopped) {
            statsJob?.cancel()
            disconnectStatus()
            runCatching { server?.closeService() }
            runCatching { server?.close() }
            server = null
            runCatching { tunFd?.close() }
            tunFd = null
            DefaultNetworkMonitor.stop()
            runCatching { Nbcore.flush() }
            if (receiverRegistered) {
                runCatching { service.unregisterReceiver(receiver) }
                receiverRegistered = false
            }
            Box._speed.value = Speed()
            Box._status.value = Status.Stopped
            Box.runner = null
        }
        scope.cancel()
    }

    private fun needsOwner(config: String) = Prefs.showApps.value || config.contains("\"package_name\"")

    private fun overrideOptions() = OverrideOptions().apply {
        // NextBox itself must stay inside the VPN: the TUN stack's TCP
        // listener lives in this process, and its replies only reach the
        // apps through the TUN interface. Latency tests are not slowed down
        // by this: nbcore routes every node server direct.
        val list = Prefs.perAppList.value
        if (Prefs.routeScope.value == Prefs.SCOPE_APPS && list.isNotEmpty()) {
            includePackage = StringArray((list + service.packageName).toList())
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            service, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun refreshUsage() {
        usage = runCatching { NbJson.decodeFromString<UsageReport>(Nbcore.usage(Prefs.billingDay.value)) }.getOrDefault(usage)
    }

    // ---------------------------------------------------------------- status stream

    private suspend fun connectStatus() {
        if (client != null || Box.status.value != Status.Started) return
        val c = Libbox.newCommandClient(
            StatusHandler(),
            CommandClientOptions().apply {
                addCommand(Libbox.CommandStatus)
                statusInterval = 1_000_000_000L
            },
        )
        // the server may need a moment after a (re)start
        repeat(10) {
            if (runCatching { c.connect() }.isSuccess) {
                client = c
                return
            }
            delay(200)
        }
    }

    private fun disconnectStatus() {
        val c = client ?: return
        client = null
        runCatching { c.disconnect() }
    }

    private fun onStatus(m: StatusMessage) {
        val up = m.uplinkTotal
        val down = m.downlinkTotal
        // totals restart from zero after a reload
        val dUp = if (up >= lastUp) up - lastUp else up
        val dDown = if (down >= lastDown) down - lastDown else down
        lastUp = up
        lastDown = down
        // only what went through the proxy counts towards the node
        val pUp = Nbtest.proxyUplink()
        val pDown = Nbtest.proxyDownlink()
        val nUp = (pUp - lastProxyUp).coerceAtLeast(0)
        val nDown = (pDown - lastProxyDown).coerceAtLeast(0)
        lastProxyUp = pUp
        lastProxyDown = pDown
        if (dUp > 0 || dDown > 0 || nUp > 0 || nDown > 0) {
            Nbcore.addTraffic(nodeId, nUp, nDown, dUp, dDown)
            usage = usage.copy(
                today = usage.today.copy(up = usage.today.up + dUp, down = usage.today.down + dDown),
                period = usage.period.copy(up = usage.period.up + dUp, down = usage.period.down + dDown),
            )
        }
        val speed = Speed(m.uplink, m.downlink)
        val session = Speed(Box.session.value.up + dUp, Box.session.value.down + dDown)
        Box._speed.value = speed
        Box._session.value = session
        Box._history.value = (Box.history.value + speed).takeLast(60)
        notifier.update(Box.nodeName.value, speed, session, usage)
    }

    private inner class StatusHandler : CommandClientHandler {
        override fun connected() {}
        override fun disconnected(message: String?) {}
        override fun setDefaultLogLevel(level: Int) {}
        override fun clearLogs() {}
        override fun writeLogs(messageList: LogIterator?) {}
        override fun writeStatus(message: StatusMessage) = onStatus(message)
        override fun writeGroups(message: OutboundGroupIterator?) {}
        override fun writeOutbounds(message: OutboundGroupItemIterator?) {}
        override fun initializeClashMode(modeList: dev.efah.nextbox.libbox.StringIterator?, currentMode: String?) {}
        override fun updateClashMode(newMode: String?) {}
        override fun writeConnectionEvents(events: ConnectionEvents?) {}
    }

    // ---------------------------------------------------------------- CommandServerHandler

    override fun serviceStop() = stop()

    override fun serviceReload() = reload()

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus()

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun triggerNativeCrash() {}

    override fun writeDebugMessage(message: String?) {
        Log.d("sing-box", message.orEmpty())
    }

    override fun connectSSHAgent(): Int = -1

    companion object {
        private const val TAG = "BoxRunner"
    }
}
