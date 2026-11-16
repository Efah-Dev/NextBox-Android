package dev.efah.nextbox.bg

import dev.efah.nextbox.App
import dev.efah.nextbox.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * The full core log of debug builds: files/box.log, read during debugging
 * with `adb shell run-as dev.efah.nextbox cat files/box.log`. Release builds
 * write none.
 *
 * It is kept below [MAX]: past that, its newest [KEEP] bytes move to
 * box.log.1 (replacing the older part) and box.log starts again, so the
 * last hours are always there in box.log.1 + box.log. The core opens the
 * file in append mode, so emptying it under the running core is safe: the
 * next line simply lands at the new end.
 */
object DebugLog {
    private const val MAX = 20L shl 20
    private const val KEEP = 8L shl 20

    // files/, not the cache: a phone cleaner empties the cache
    val file: File get() = File(App.app.filesDir, "box.log")

    /** Moves the older part away once the log is past its size; called at start and every few minutes. */
    fun trim() {
        if (!BuildConfig.DEBUG) return
        // where 1.1.2 debug builds kept it before
        runCatching { File(App.app.cacheDir, "box.log").delete() }
        runCatching {
            val log = file
            if (log.length() <= MAX) return
            RandomAccessFile(log, "rw").use { raf ->
                val len = raf.length()
                // start the kept part at the beginning of a line
                var from = len - KEEP
                raf.seek(from)
                while (from < len && raf.read() != '\n'.code) from++
                FileOutputStream(File(log.parentFile, "box.log.1")).use { out ->
                    raf.channel.transferTo(from + 1, len - from - 1, out.channel)
                }
                raf.setLength(0)
            }
        }
    }
}
