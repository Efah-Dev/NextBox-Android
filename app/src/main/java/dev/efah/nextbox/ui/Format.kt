package dev.efah.nextbox.ui

import java.util.Locale

fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (v >= 100) String.format(Locale.US, "%.0f %s", v, units[i]) else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtSpeed(bps: Long): String = fmtBytes(bps) + "/s"

fun fmtDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

fun fmtAgo(unixSeconds: Long): String {
    if (unixSeconds <= 0) return T("从未更新")
    val d = System.currentTimeMillis() / 1000 - unixSeconds
    return when {
        d < 60 -> T("刚刚更新")
        d < 3600 -> T("%d 分钟前更新", d / 60)
        d < 86400 -> T("%d 小时前更新", d / 3600)
        else -> T("%d 天前更新", d / 86400)
    }
}

fun fmtDate(unixSeconds: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = unixSeconds * 1000 }
    return String.format(Locale.US, "%04d-%02d-%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}
