package dev.efah.nextbox

import android.app.Application
import android.app.NotificationManager
import android.content.ClipboardManager
import android.net.ConnectivityManager
import android.util.Log
import dev.efah.nextbox.bg.Notifier
import dev.efah.nextbox.data.Prefs
import dev.efah.nextbox.data.Repo
import dev.efah.nextbox.libbox.Libbox
import dev.efah.nextbox.libbox.SetupOptions
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        Prefs.init(this)
        Repo.init(this)
        runCatching { setupLibbox() }.onFailure { Log.e("NextBox", "setup libbox", it) }
        Notifier.createChannel(this)
    }

    private fun setupLibbox() {
        val work = File(filesDir, "work").apply { mkdirs() }
        Libbox.setup(
            SetupOptions().also {
                it.basePath = filesDir.path
                it.workingPath = work.path
                it.tempPath = cacheDir.path
                it.fixAndroidStack = BuildConfig.DEBUG
                it.logMaxLines = 1000
                it.debug = false
                it.crashReportSource = "NextBox"
                it.appVersion = BuildConfig.VERSION_CODE.toString()
                it.appMarketingVersion = BuildConfig.VERSION_NAME
            },
        )
        runCatching { Libbox.setLocale("zh-CN") }
    }

    companion object {
        lateinit var app: App
            private set
        val connectivity: ConnectivityManager by lazy { app.getSystemService(ConnectivityManager::class.java) }
        val notifications: NotificationManager by lazy { app.getSystemService(NotificationManager::class.java) }
        val clipboard: ClipboardManager by lazy { app.getSystemService(ClipboardManager::class.java) }
    }
}
