// Default network tracking and the platform DNS resolver for sing-box.
// Adapted from sing-box-for-android (GPL-3.0), which itself builds on
// shadowsocks-android's DefaultNetworkListener.
package dev.efah.nextbox.bg

import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.system.ErrnoException
import dev.efah.nextbox.App
import dev.efah.nextbox.libbox.ExchangeContext
import dev.efah.nextbox.libbox.InterfaceUpdateListener
import dev.efah.nextbox.libbox.LocalDNSTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** Follows the underlying (non-VPN) default network and reports it to sing-box. */
object DefaultNetworkMonitor {
    @Volatile
    var defaultNetwork: Network? = null
        private set

    @Volatile
    private var listener: InterfaceUpdateListener? = null
    private var registered = false

    private val handler by lazy { Handler(HandlerThread("network-monitor").apply { start() }.looper) }

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            notifyListener(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (network == defaultNetwork) notifyListener(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (network == defaultNetwork) notifyListener(network)
        }

        override fun onLost(network: Network) {
            if (network == defaultNetwork) {
                defaultNetwork = null
                notifyListener(null)
            }
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        // registerDefaultNetworkCallback would report the VPN itself, so ask
        // for the best matching real network instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            App.connectivity.registerBestMatchingNetworkCallback(request, callback, handler)
        } else {
            App.connectivity.requestNetwork(request, callback, handler)
        }
        registered = true
        if (defaultNetwork == null) defaultNetwork = App.connectivity.activeNetwork
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        runCatching { App.connectivity.unregisterNetworkCallback(callback) }
        registered = false
        defaultNetwork = null
    }

    fun setListener(l: InterfaceUpdateListener?) {
        listener = l
        notifyListener(defaultNetwork)
    }

    @Synchronized
    fun removeListener(l: InterfaceUpdateListener) {
        if (listener === l) listener = null
    }

    private fun notifyListener(network: Network?) {
        val l = listener ?: return
        if (network == null) {
            l.updateDefaultInterface("", -1, false, false)
            return
        }
        repeat(10) {
            val lp = App.connectivity.getLinkProperties(network)
            val index = runCatching { NetworkInterface.getByName(lp?.interfaceName).index }.getOrNull()
            if (lp != null && index != null) {
                val caps = App.connectivity.getNetworkCapabilities(network)
                val expensive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
                l.updateDefaultInterface(lp.interfaceName, index, expensive, false)
                return
            }
            Thread.sleep(100)
        }
    }
}

/** Resolves "local" DNS servers through Android's DnsResolver on the real network. */
@Suppress("DEPRECATION") // DnsResolver.getInstance: still the only entry point below API 37
object LocalResolver : LocalDNSTransport {
    override fun raw(): Boolean = true

    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        val network = DefaultNetworkMonitor.defaultNetwork ?: error("missing default interface")
        val done = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        var failure: Throwable? = null
        val signal = CancellationSignal()
        ctx.onCancel {
            signal.cancel()
            if (finished.compareAndSet(false, true)) done.countDown()
        }
        DnsResolver.getInstance().rawQuery(
            network, message, DnsResolver.FLAG_NO_RETRY, Dispatchers.IO.asExecutor(), signal,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    if (!finished.compareAndSet(false, true)) return
                    if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                    done.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    if (!finished.compareAndSet(false, true)) return
                    val cause = error.cause
                    if (cause is ErrnoException) ctx.errnoCode(cause.errno) else failure = error
                    done.countDown()
                }
            },
        )
        done.await()
        failure?.let { throw it }
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        val net = DefaultNetworkMonitor.defaultNetwork ?: error("missing default interface")
        val done = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        var failure: Throwable? = null
        val signal = CancellationSignal()
        ctx.onCancel {
            signal.cancel()
            if (finished.compareAndSet(false, true)) done.countDown()
        }
        val callback = object : DnsResolver.Callback<List<InetAddress>> {
            override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                if (!finished.compareAndSet(false, true)) return
                if (rcode == 0) {
                    ctx.success(answer.mapNotNull { it.hostAddress }.joinToString("\n"))
                } else {
                    ctx.errorCode(rcode)
                }
                done.countDown()
            }

            override fun onError(error: DnsResolver.DnsException) {
                if (!finished.compareAndSet(false, true)) return
                val cause = error.cause
                if (cause is ErrnoException) ctx.errnoCode(cause.errno) else failure = error
                done.countDown()
            }
        }
        val executor = Dispatchers.IO.asExecutor()
        when {
            network.endsWith("4") -> DnsResolver.getInstance().query(
                net, domain, DnsResolver.TYPE_A, DnsResolver.FLAG_NO_RETRY, executor, signal, callback,
            )
            network.endsWith("6") -> DnsResolver.getInstance().query(
                net, domain, DnsResolver.TYPE_AAAA, DnsResolver.FLAG_NO_RETRY, executor, signal, callback,
            )
            else -> DnsResolver.getInstance().query(
                net, domain, DnsResolver.FLAG_NO_RETRY, executor, signal, callback,
            )
        }
        done.await()
        failure?.let { throw it }
    }
}
