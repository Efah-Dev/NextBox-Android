package dev.efah.nextbox.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.efah.nextbox.data.Repo
import kotlinx.coroutines.launch

/** Buttons of the ongoing notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            STOP -> Box.stop()
            TOGGLE_ROUTE -> {
                val pending = goAsync()
                Repo.scope.launch {
                    Repo.setRoute(bypass = !dev.efah.nextbox.data.Prefs.bypassCn.value)
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val STOP = "dev.efah.nextbox.STOP"
        const val TOGGLE_ROUTE = "dev.efah.nextbox.TOGGLE_ROUTE"
    }
}
