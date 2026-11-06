package dev.efah.nextbox.bg

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.libbox.RoutePrefix
import dev.efah.nextbox.libbox.TunOptions
import android.net.IpPrefix
import java.net.InetAddress

/** VPN mode: sing-box's TUN inbound is backed by Android's VpnService. */
class VpnBoxService : VpnService(), Platform {
    private val runner = BoxRunner(this, this, isVpn = true)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = runner.onStartCommand()

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onRevoke() = runner.stop()

    override fun onDestroy() {
        runner.destroy()
        super.onDestroy()
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing VPN permission")
        val builder = Builder()
            .setSession("NextBox")
            .setMtu(options.mtu)
            .setMetered(false)

        val v4 = options.inet4Address
        while (v4.hasNext()) v4.next().let { builder.addAddress(it.address(), it.prefix()) }
        val v6 = options.inet6Address
        while (v6.hasNext()) v6.next().let { builder.addAddress(it.address(), it.prefix()) }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dns = options.dnsServerAddress
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val r4 = options.inet4RouteAddress
                if (r4.hasNext()) {
                    while (r4.hasNext()) builder.addRoute(r4.next().toIpPrefix())
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }
                val r6 = options.inet6RouteAddress
                if (r6.hasNext()) {
                    while (r6.hasNext()) builder.addRoute(r6.next().toIpPrefix())
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }
                val x4 = options.inet4RouteExcludeAddress
                while (x4.hasNext()) builder.excludeRoute(x4.next().toIpPrefix())
                val x6 = options.inet6RouteExcludeAddress
                while (x6.hasNext()) builder.excludeRoute(x6.next().toIpPrefix())
            } else {
                val r4 = options.inet4RouteRange
                while (r4.hasNext()) r4.next().let { builder.addRoute(it.address(), it.prefix()) }
                val r6 = options.inet6RouteRange
                while (r6.hasNext()) r6.next().let { builder.addRoute(it.address(), it.prefix()) }
            }

            val include = options.includePackage
            if (include.hasNext()) {
                while (include.hasNext()) {
                    val pkg = include.next()
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w("VpnBoxService", "allowed app not installed: $pkg")
                    }
                }
            } else {
                val exclude = options.excludePackage
                while (exclude.hasNext()) {
                    val pkg = exclude.next()
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w("VpnBoxService", "excluded app not installed: $pkg")
                    }
                }
            }
        }

        val pfd = builder.establish() ?: error("android: VPN permission was revoked")
        // a reload establishes a new interface; the old one can go now
        val old = runner.tunFd
        runner.tunFd = pfd
        runCatching { old?.close() }
        return pfd.fd
    }
}

private fun RoutePrefix.toIpPrefix() = IpPrefix(InetAddress.getByName(address()), prefix())

/** Proxy-only mode: just the local mixed (HTTP + SOCKS5) port, no VPN. */
class ProxyBoxService : Service(), Platform {
    private val runner = BoxRunner(this, this, isVpn = false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = runner.onStartCommand()

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        runner.destroy()
        super.onDestroy()
    }
}
