package dev.efah.nextbox.bg

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.efah.nextbox.App
import dev.efah.nextbox.data.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Status { Stopped, Starting, Started, Stopping }

/** Live traffic numbers, bytes and bytes per second. */
data class Speed(val up: Long = 0, val down: Long = 0)

/**
 * Process-wide state of the proxy core, shared by the services, the
 * notification, the quick settings tile and the UI (everything runs in one
 * process, which keeps memory use low).
 */
object Box {
    internal val _status = MutableStateFlow(Status.Stopped)
    val status: StateFlow<Status> = _status.asStateFlow()

    internal val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt.asStateFlow()

    internal val _speed = MutableStateFlow(Speed())
    val speed: StateFlow<Speed> = _speed.asStateFlow()

    /** Traffic of the current session. */
    internal val _session = MutableStateFlow(Speed())
    val session: StateFlow<Speed> = _session.asStateFlow()

    /** The last 60 speed samples, oldest first, for the home sparkline. */
    internal val _history = MutableStateFlow<List<Speed>>(List(60) { Speed() })
    val history: StateFlow<List<Speed>> = _history.asStateFlow()

    /** Name of the node the running core uses. */
    internal val _nodeName = MutableStateFlow("")
    val nodeName: StateFlow<String> = _nodeName.asStateFlow()

    @Volatile
    internal var runner: BoxRunner? = null

    /** Whether connection owners (apps) must be looked up; see Platform.findConnectionOwner. */
    @Volatile
    internal var ownerLookup = false

    val isRunning: Boolean get() = _status.value == Status.Started

    /**
     * Starts the core in the configured mode. VPN mode needs the VpnService
     * permission, which the caller must have obtained (VpnService.prepare).
     */
    fun start(context: Context = App.app) {
        if (_status.value != Status.Stopped) return
        _error.value = null
        val vpn = Repo.state.value.settings.tunEnabled
        val cls = if (vpn) VpnBoxService::class.java else ProxyBoxService::class.java
        ContextCompat.startForegroundService(context, Intent(context, cls))
    }

    fun stop() {
        runner?.stop()
    }

    /** Applies changed settings or another node to the running core. */
    fun reloadIfRunning() {
        val r = runner ?: return
        if (_status.value != Status.Started) return
        val wantVpn = Repo.state.value.settings.tunEnabled
        if (wantVpn != r.isVpn) {
            // switching between VPN and proxy-only needs the other service
            r.stop {
                start()
            }
        } else {
            r.reload()
        }
    }

    /**
     * Proxy for downloading subscriptions and rule-sets in proxy-only mode.
     * In VPN mode the download goes through the VPN like any other app.
     */
    fun subscriptionProxy(): String =
        if (isRunning && runner?.isVpn == false) "http://127.0.0.1:${Repo.state.value.settings.mixedPort}" else ""
}
