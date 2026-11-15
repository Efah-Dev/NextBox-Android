package dev.efah.nextbox

import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the stack trace of the last crash, so the next start can offer to
 * copy it for a bug report (problems on phones we cannot test on, like the
 * 1.0 crash on Android 11, are otherwise invisible).
 */
object CrashLog {
    private val file: File get() = File(App.app.filesDir, "last-crash.txt")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file.writeText(
                    "NextBox ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n" + trace.take(16_000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The report of a crash not dealt with yet, or null. */
    fun pending(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }
}
