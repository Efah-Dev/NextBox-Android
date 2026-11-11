package dev.efah.nextbox.bg

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.ui.MainActivity
import dev.efah.nextbox.ui.T
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Quick settings tile: tap to connect / disconnect, long press opens the app. */
class QuickTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(Box.status, Box.nodeName) { s, n -> s to n }.collect { (s, n) -> render(s, n) }
        }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    private fun render(status: Status, node: String) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            Status.Started -> Tile.STATE_ACTIVE
            Status.Stopped -> Tile.STATE_INACTIVE
            else -> Tile.STATE_UNAVAILABLE
        }
        // The label is the line every launcher shows (many hide the subtitle):
        // "NextBox 未连接" while off, the node's name while connected.
        tile.label = when (status) {
            Status.Started -> node.ifEmpty { "NextBox " + T("已连接") }
            Status.Starting -> "NextBox " + T("正在连接…")
            Status.Stopping -> "NextBox " + T("正在断开…")
            Status.Stopped -> "NextBox " + T("未连接")
        }
        tile.subtitle = if (status == Status.Started && node.isNotEmpty()) "NextBox " + T("已连接") else null
        tile.updateTile()
    }

    override fun onClick() {
        when (Box.status.value) {
            Status.Started, Status.Starting -> Box.stop()
            Status.Stopping -> {}
            Status.Stopped -> {
                val needsVpnPermission = Repo.state.value.settings.tunEnabled && VpnService.prepare(this) != null
                if (needsVpnPermission || Repo.state.value.selectedId == 0L) {
                    // the permission dialog (or picking a node) needs the app
                    openApp()
                } else {
                    Box.start(this)
                }
            }
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CONNECT, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
