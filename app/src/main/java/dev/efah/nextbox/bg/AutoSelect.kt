package dev.efah.nextbox.bg

import android.net.NetworkCapabilities
import dev.efah.nextbox.App
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Profile
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.nbtest.LatencyHandler
import dev.efah.nextbox.nbtest.Nbtest
import dev.efah.nextbox.ui.T
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Automatically select a node": before connecting (and, if asked for, now
 * and then while connected) the selected node is tested; when it does not
 * answer, the fastest working node of its group (or of all groups) takes over.
 */
object AutoSelect {
    sealed interface Outcome {
        /** The selected node works. */
        data object Kept : Outcome

        /** Another node was selected. */
        data class Switched(val from: String, val to: String, val ms: Int) : Outcome

        /** Nothing works; message says why. */
        data class NoNode(val message: String) : Outcome
    }

    /** At most this many other nodes are tried, fastest last result first. */
    private const val MAX_CANDIDATES = 64

    suspend fun check(): Outcome = withContext(Dispatchers.IO) {
        val st = Repo.state.value
        if (st.settings.routeMode == "direct") return@withContext Outcome.Kept
        if (!hasNetwork()) return@withContext Outcome.NoNode(T("手机现在没有联网，请检查 Wi-Fi 或移动数据。"))
        val all = Repo.allProfiles()
        val current = all.firstOrNull { it.id == st.selectedId }
        if (current != null && test(listOf(current))[current.id]?.let { it > 0 } == true) {
            return@withContext Outcome.Kept
        }
        val pool = if (Prefs.autoSelectAll.value || current == null) all else all.filter { it.groupId == current.groupId }
        val candidates = pool.filter { it.id != current?.id }
            .sortedBy { if (it.latency > 0) it.latency else Int.MAX_VALUE }
            .take(MAX_CANDIDATES)
        val results = if (candidates.isEmpty()) emptyMap() else test(candidates)
        val best = results.filterValues { it > 0 }.minByOrNull { it.value }
        if (best != null) {
            val to = candidates.first { it.id == best.key }
            Repo.select(to.id)
            return@withContext Outcome.Switched(current?.name.orEmpty(), to.name, best.value)
        }
        // nothing answered: tell a dead network from dead nodes
        if (!directReachable()) return@withContext Outcome.NoNode(T("手机网络连不上外网，请检查 Wi-Fi 或移动数据。"))
        val tested = candidates.size + if (current != null) 1 else 0
        val where = if (Prefs.autoSelectAll.value || current == null) {
            T("所有分组")
        } else {
            T("「%s」", st.groups.firstOrNull { it.id == current.groupId }?.displayName.orEmpty())
        }
        Outcome.NoNode(
            T("%s里试了 %d 个节点，都连不上。手机网络正常，可能是订阅过期或节点在维护，可以先更新订阅再试。", where, tested),
        )
    }

    /** Whether the selected node answers a URL test right now. */
    suspend fun selectedWorks(): Boolean = withContext(Dispatchers.IO) {
        val id = Repo.state.value.selectedId
        val p = Repo.allProfiles().firstOrNull { it.id == id } ?: return@withContext false
        (test(listOf(p))[id] ?: -1) > 0
    }

    /** URL-tests the nodes with the auto select timeout; id -> ms (-1 failed). */
    private fun test(nodes: List<Profile>): Map<Long, Int> {
        val out = ConcurrentHashMap<Long, Int>()
        val s = Repo.state.value.settings
        runCatching {
            Nbtest.urlTest(
                nodes.joinToString(",") { it.id.toString() }, s.testUrl, Prefs.autoSelectTimeout.value,
                s.testConcurrency.coerceAtLeast(4),
                object : LatencyHandler {
                    override fun onStart(id: Long) {}
                    override fun onResult(id: Long, latency: Int, message: String) {
                        out[id] = latency
                    }
                },
            )
        }
        return out
    }

    private fun hasNetwork(): Boolean {
        val cm = App.connectivity
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** A TCP handshake with public DNS servers in and outside mainland China. */
    private fun directReachable(): Boolean = listOf("223.5.5.5" to 53, "1.1.1.1" to 53, "119.29.29.29" to 53).any { (host, port) ->
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 2000) } }.isSuccess
    }
}
