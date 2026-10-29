package com.karelisio.canif.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.karelisio.canif.BuildConfig
import com.karelisio.canif.CanifApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(
    val version: String,
    val name: String,
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * In-app updates from GitHub Releases: checks releases/latest (at most every 6 h at launch),
 * downloads the signed APK with progress and installs it through PackageInstaller.
 */
object Updater {
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private val api = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** Set when the user dismisses the sheet, so it does not reappear during this session. */
    var dismissedVersion: String? = null

    suspend fun checkIfDue() {
        val settings = CanifApp.instance.settings
        val last = settings.long(KEY_LAST_CHECK, 0L).first()
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    suspend fun check(silent: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        runCatching { fetchLatest() }
            .onSuccess { release ->
                CanifApp.instance.settings.setLong(KEY_LAST_CHECK, System.currentTimeMillis())
                _state.value = if (release != null && isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate
                }
            }
            .onFailure { _state.value = if (silent) UpdateState.Idle else UpdateState.Failed(it.message ?: "network") }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Canif/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode == 404) return@withContext null
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var url: String? = null
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url"); size = a.optLong("size"); break
                    }
                }
            }
            url ?: return@withContext null
            val tag = json.optString("tag_name").removePrefix("v")
            Release(tag, json.optString("name", tag), json.optString("body", ""), url, size)
        } finally {
            conn.disconnect()
        }
    }

    /** Semantic version comparison ("1.10.0" > "1.9.3"); suffixes like "-debug" are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun downloadAndInstall(context: Context, release: Release) {
        _state.value = UpdateState.Downloading(release, 0f)
        val file = runCatching { download(context, release) }.getOrElse {
            _state.value = UpdateState.Failed(it.message ?: "download", release); return
        }
        _state.value = UpdateState.Installing(release)
        runCatching { install(context, file) }.onFailure {
            _state.value = UpdateState.Failed(it.message ?: "install", release)
        }
    }

    private suspend fun download(context: Context, release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "canif-${release.version}.apk")
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Canif/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            _state.value = UpdateState.Downloading(release, 1f)
            out
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("canif.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val current = _state.value
        val release = (current as? UpdateState.Installing)?.release
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            } else {
                UpdateState.Failed(message ?: "install ($status)", release)
            }
        }
    }
}

/** Receives PackageInstaller callbacks; launches the system confirmation when needed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
        }
        Updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
