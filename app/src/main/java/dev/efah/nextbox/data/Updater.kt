package dev.efah.nextbox.data

import android.os.Build
import dev.efah.nextbox.BuildConfig
import dev.efah.nextbox.bg.Box
import dev.efah.nextbox.bg.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Looks for a newer release by comparing this build with the latest GitHub release. */
object Updater {
    const val WEBSITE = "https://nextbox.efah.dev"
    const val REPO = "Efah-Dev/NextBox-Android"
    const val GITHUB = "https://github.com/$REPO"

    /** [version] is the tag ("v1.0.1"), [apk] the download for this device (the release page if none fits). */
    data class Release(val version: String, val notes: String, val page: String, val apk: String)

    private val _latest = MutableStateFlow<Release?>(null)
    private val _prompt = MutableStateFlow<Release?>(null)

    /** A release newer than this build, once a check has found one. */
    val latest: StateFlow<Release?> = _latest.asStateFlow()

    /** The release the UI should offer right now. */
    val prompt: StateFlow<Release?> = _prompt.asStateFlow()

    /** Closes the offer; with [later] the automatic check will not bring this release up again. */
    fun close(later: Boolean) {
        if (later) _prompt.value?.let { Prefs.skippedVersion = it.version }
        _prompt.value = null
    }

    /** The automatic check at app start: at most every six hours, silent when it fails. */
    suspend fun autoCheck() {
        if (!Prefs.autoUpdate.value) return
        if (System.currentTimeMillis() - Prefs.updateChecked < 6 * 3600_000L) return
        check(manual = false)
    }

    /** Returns the newer release, or null when this build is up to date. */
    suspend fun check(manual: Boolean): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetch()
            Prefs.updateChecked = System.currentTimeMillis()
            val newer = release.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
            _latest.value = newer
            if (newer != null && (manual || newer.version != Prefs.skippedVersion)) _prompt.value = newer
            newer
        }
    }

    /** Compares "v1.2.3" style versions; a pre-release ("1.0.0-beta") is older than its final version. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return current.contains('-') && !candidate.contains('-')
    }

    private fun numbers(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    private fun fetch(): Release {
        val conn = open("https://api.github.com/repos/$REPO/releases/latest")
        try {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            // the anonymous API allowance is per IP address and a shared proxy can use it up
            if (code == 403 || code == 429) return fromRedirect()
            // no published release at all: nothing newer than this build
            if (code == 404) return Release(BuildConfig.VERSION_NAME, "", GITHUB, GITHUB)
            if (code != 200) error("HTTP $code")
            val o = Json.parseToJsonElement(conn.inputStream.use { it.readBytes().decodeToString() }).jsonObject
            val tag = o["tag_name"]!!.jsonPrimitive.content
            val page = o["html_url"]?.jsonPrimitive?.content ?: "$GITHUB/releases/tag/$tag"
            val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
            val apk = o["assets"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull {
                val name = it["name"]?.jsonPrimitive?.content.orEmpty()
                abi.isNotEmpty() && name.endsWith(".apk") && name.contains(abi)
            }?.get("browser_download_url")?.jsonPrimitive?.content
            return Release(tag, o["body"]?.jsonPrimitive?.content.orEmpty().trim(), page, apk ?: page)
        } finally {
            conn.disconnect()
        }
    }

    /** Without the API: github.com/…/releases/latest redirects to the page of the latest tag. */
    private fun fromRedirect(): Release {
        val conn = open("$GITHUB/releases/latest")
        try {
            conn.instanceFollowRedirects = false
            val location = conn.getHeaderField("Location") ?: error("HTTP ${conn.responseCode}")
            val tag = location.substringAfterLast("/tag/", "")
            if (tag.isEmpty()) error("no release")
            return Release(tag, "", location, location)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        // In proxy-only mode nothing routes this app, so use the local port; in VPN mode the app is inside its own VPN.
        val s = Repo.state.value.settings
        val local = Box.status.value == Status.Started && !s.tunEnabled
        val u = URL(url)
        val conn = if (local) u.openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", s.mixedPort))) else u.openConnection()
        return (conn as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "NextBox-Android/${BuildConfig.VERSION_NAME}")
        }
    }
}
