package dev.efah.nextbox.bg

import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.system.OsConstants
import dev.efah.nextbox.App
import dev.efah.nextbox.libbox.BridgeOptions
import dev.efah.nextbox.libbox.BridgeSession
import dev.efah.nextbox.libbox.ConnectionOwner
import dev.efah.nextbox.libbox.InterfaceUpdateListener
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.libbox.LocalDNSTransport
import dev.efah.nextbox.libbox.NeighborUpdateListener
import dev.efah.nextbox.libbox.NetworkInterfaceIterator
import dev.efah.nextbox.libbox.Notification
import dev.efah.nextbox.libbox.PlatformInterface
import dev.efah.nextbox.libbox.PlatformUser
import dev.efah.nextbox.libbox.ShellSession
import dev.efah.nextbox.libbox.StringIterator
import dev.efah.nextbox.libbox.TunOptions
import dev.efah.nextbox.libbox.WIFIState
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import dev.efah.nextbox.libbox.NetworkInterface as BoxInterface

class StringArray(private val items: List<String>) : StringIterator {
    private var i = 0
    override fun len(): Int = items.size
    override fun hasNext(): Boolean = i < items.size
    override fun next(): String = items[i++]
}

fun StringIterator.toList(): List<String> = buildList { while (hasNext()) add(next()) }

/**
 * What sing-box needs from Android. Features NextBox does not use (root
 * shells, bridges, Wi-Fi SSID rules, Tailscale) are reported as unavailable.
 */
interface Platform : PlatformInterface {
    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int = error("android: TUN needs VPN mode")

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        // sing-box asks for every connection on Android, even when nothing
        // uses the answer: skip the binder call unless rules or the activity
        // page need it.
        if (!Box.ownerLookup) return ConnectionOwner().also { it.userId = -1 }
        val uid = App.connectivity.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort),
        )
        if (uid == Process.INVALID_UID) error("android: connection owner not found")
        val packages = App.app.packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        return ConnectionOwner().also {
            it.userId = uid
            it.userName = packages.firstOrNull().orEmpty()
            it.setAndroidPackageNames(StringArray(packages))
        }
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        // on reload the new instance registers before the old one closes
        DefaultNetworkMonitor.removeListener(listener)
    }

    @Suppress("DEPRECATION") // allNetworks: no replacement that lists every network
    override fun getInterfaces(): NetworkInterfaceIterator {
        val javaInterfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val list = mutableListOf<BoxInterface>()
        for (network in App.connectivity.allNetworks) {
            val lp = App.connectivity.getLinkProperties(network) ?: continue
            val caps = App.connectivity.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val ji = javaInterfaces.find { it.name == lp.interfaceName } ?: continue
            val bi = BoxInterface()
            bi.name = lp.interfaceName
            bi.index = ji.index
            runCatching { bi.mtu = ji.mtu }
            bi.dnsServer = StringArray(lp.dnsServers.mapNotNull { it.hostAddress })
            bi.gateway = StringArray(
                lp.routes.filter { it.destination.prefixLength == 0 }.mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }.mapNotNull { it.hostAddress },
            )
            bi.type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            bi.addresses = StringArray(ji.interfaceAddresses.map { it.toPrefix() })
            var flags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            if (ji.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (ji.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (ji.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            bi.flags = flags
            bi.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            list += bi
        }
        val it = list.iterator()
        return object : NetworkInterfaceIterator {
            override fun hasNext(): Boolean = it.hasNext()
            override fun next(): BoxInterface = it.next()
        }
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun localDNSTransport(): LocalDNSTransport = LocalResolver

    override fun sendNotification(notification: Notification) {}

    override fun cancelNotification(identifier: String, typeID: Int) {}

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun registerMyInterface(name: String?) {}

    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() = error("not supported")

    override fun openShellSession(user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int): ShellSession =
        error("not supported")

    override fun lookupUser(username: String?): PlatformUser = error("not supported")

    override fun lookupSFTPServer(): String = error("not supported")

    override fun readSystemSSHHostKey(): String = error("not supported")

    override fun tailscaleHostname(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession = error("not supported")
}

private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
    "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
} else {
    "${address.hostAddress}/$networkPrefixLength"
}
