package dev.efah.nextbox.bg

import dev.efah.nextbox.ui.T
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.efah.nextbox.App
import dev.efah.nextbox.R
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.data.UsageReport
import dev.efah.nextbox.ui.MainActivity
import dev.efah.nextbox.ui.fmtBytes
import dev.efah.nextbox.ui.fmtSpeed
import dev.efah.nextbox.ui.theme.parseHex
import androidx.compose.ui.graphics.toArgb

/** The ongoing notification: live speed, usage and quick actions. */
class Notifier(private val service: Service, private val isVpn: Boolean) {
    private var lastUpdate = 0L
    private var shown = false

    private fun pending(action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        service, code, Intent(service, ActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(tab: String?, code: Int): PendingIntent = PendingIntent.getActivity(
        service, code,
        Intent(service, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { if (tab != null) putExtra(MainActivity.EXTRA_TAB, tab) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun builder(title: String, text: String, big: String?): NotificationCompat.Builder {
        val bypass = Prefs.bypassCn.value
        return NotificationCompat.Builder(service, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(parseHex(Repo.state.value.settings.accentColor).toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(big?.let { NotificationCompat.BigTextStyle().bigText(it) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(null, 1))
            .addAction(0, T("断开"), pending(ActionReceiver.STOP, 2))
            .addAction(0, T("切换节点"), openApp("nodes", 3))
            .addAction(0, if (bypass) T("关闭绕过大陆") else T("绕过大陆"), pending(ActionReceiver.TOGGLE_ROUTE, 4))
    }

    fun startForeground(node: String, text: String) {
        val n = builder(node.ifEmpty { "NextBox" }, text, null).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (isVpn) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            ServiceCompat.startForeground(service, ID, n, type)
        } else {
            service.startForeground(ID, n)
        }
        shown = true
    }

    /** Called about once a second; skipped when nothing would be visible. */
    fun update(node: String, speed: Speed, session: Speed, usage: UsageReport) {
        if (!shown || !permitted()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdate < 900) return
        lastUpdate = now
        val showSpeed = Prefs.notifySpeed.value
        val showStats = Prefs.notifyStats.value
        val title = T("%s · 已连接", node.ifEmpty { "NextBox" })
        val speedLine = "↑ ${fmtSpeed(speed.up)}   ↓ ${fmtSpeed(speed.down)}"
        val periodLabel = if (Prefs.billingDay.value == 1) T("本月") else T("本期")
        val statsLine = T("本次 %s · 今日 %s · %s %s", fmtBytes(session.up + session.down), fmtBytes(usage.today.total), periodLabel, fmtBytes(usage.period.total))
        val text = when {
            showSpeed && showStats -> T("↓ %s  ↑ %s · 今日 %s", fmtSpeed(speed.down), fmtSpeed(speed.up), fmtBytes(usage.today.total))
            showSpeed -> speedLine
            showStats -> statsLine
            else -> if (isVpn) T("VPN 已开启") else T("本地代理已开启")
        }
        val big = when {
            showSpeed && showStats -> "$speedLine\n$statsLine"
            else -> null
        }
        App.notifications.notify(ID, builder(title, text, big).build())
    }

    fun stop() {
        shown = false
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val CHANNEL = "service"
        private const val ID = 1

        fun createChannel(context: Context) {
            val ch = NotificationChannel(CHANNEL, T("连接状态"), NotificationManager.IMPORTANCE_LOW).apply {
                description = T("常驻通知：实时网速、流量统计和快捷操作")
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        fun permitted(): Boolean = App.notifications.areNotificationsEnabled()
    }
}
