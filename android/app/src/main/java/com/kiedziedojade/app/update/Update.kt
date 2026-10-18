package com.kiedziedojade.app.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.kiedziedojade.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * OTA jak w bs-companion: `version.json` w repo → DownloadManager → instalator.
 * Wersje wydawane są jako GitHub Releases; apk ZAWSZE podpisany tym samym kluczem.
 */

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String,
    val force: Boolean,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data object Offline : UpdateState
    data class Error(val message: String) : UpdateState
}

fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
} catch (_: Exception) {
    false
}

/** Subtelny obserwator sieci — kropka „offline” w nagłówku. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var online by remember { mutableStateOf(isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
            }

            override fun onLost(network: Network) {
                online = isOnline(context)
            }

            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                online = isOnline(context)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(cb)
            } else {
                @Suppress("DEPRECATION")
                val req = android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
                cm.registerNetworkCallback(req, cb)
            }
        } catch (_: Exception) {
        }
        onDispose {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
    }
    return online
}

private fun parseUpdate(json: String): UpdateInfo? = try {
    val o = JSONObject(json)
    val code = o.optInt("versionCode", -1)
    val name = o.optString("versionName", "")
    val url = o.optString("apkUrl", "")
    if (code <= 0 || name.isEmpty() || url.isEmpty()) {
        null
    } else {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val host = (uri?.host ?: "").lowercase(Locale.ROOT)
        // Tylko https z domen GitHuba (DownloadManager podąża za redirectem
        // releases/download → objects.githubusercontent.com).
        val okHost = host == "github.com" || host.endsWith(".github.com") ||
            host.endsWith(".githubusercontent.com")
        if (uri?.scheme != "https" || !okHost) {
            null
        } else {
            UpdateInfo(
                versionCode = code,
                versionName = name,
                apkUrl = url,
                changelog = o.optString("changelog", ""),
                force = o.optBoolean("force", false),
            )
        }
    }
} catch (_: Exception) {
    null
}

suspend fun checkForUpdate(context: Context): UpdateState = withContext(Dispatchers.IO) {
    if (!isOnline(context)) return@withContext UpdateState.Offline
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "KiedyDojade/${BuildConfig.VERSION_NAME}")
        }
        if (conn.responseCode !in 200..299) {
            return@withContext UpdateState.Error("HTTP ${conn.responseCode}")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }.take(20_000)
        val info = parseUpdate(body) ?: return@withContext UpdateState.Error("zły version.json")
        if (info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
    } catch (e: Exception) {
        if (!isOnline(context)) UpdateState.Offline
        else UpdateState.Error(e.message?.take(80) ?: "błąd sieci")
    } finally {
        runCatching { conn?.disconnect() }
    }
}

private const val PREFS_UPDATE = "kd_update"
private const val KEY_DL_ID = "dl_id"
private const val KEY_DL_VERSION = "dl_version"

private fun apkFile(context: Context, versionName: String): File {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
    val safe = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(32)
    return File(dir, "kiedy-dojade-$safe.apk")
}

private fun isIntactApk(context: Context, f: File): Boolean = try {
    if (!f.exists() || f.length() <= 0) false
    else context.packageManager.getPackageArchiveInfo(f.absolutePath, 0) != null
} catch (_: Exception) {
    false
}

enum class DlState { MISSING, RUNNING, PAUSED, SUCCESS, FAILED }

data class DownloadStatus(val state: DlState, val downloaded: Long = 0L, val total: Long = -1L) {
    val fraction: Float? get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
}

/** Pobiera apk przez DownloadManager i otwiera instalator po tapnięciu. */
fun downloadAndInstall(context: Context, info: UpdateInfo) {
    try {
        if (queryDownloadStatus(context, info.versionName).state == DlState.RUNNING) {
            android.widget.Toast.makeText(context, "Już się pobiera…", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val dest = apkFile(context, info.versionName)
        runCatching { dest.parentFile?.mkdirs() }
        if (dest.exists()) runCatching { dest.delete() }
        val req = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("KiedyDojadę ${info.versionName}")
            setDescription("Pobieranie aktualizacji")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/${dest.name}")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setMimeType("application/vnd.android.package-archive")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
            val oldId = prefs.getLong(KEY_DL_ID, -1L)
            if (oldId >= 0) runCatching { dm.remove(oldId) }
        }
        deleteStaleApks(context, keepVersion = info.versionName)
        val id = dm.enqueue(req)
        runCatching {
            context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DL_ID, id)
                .putString(KEY_DL_VERSION, info.versionName)
                .apply()
        }
        android.widget.Toast.makeText(context, "Pobieranie ${info.versionName}…", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.util.Log.w("Update", "download failed", e)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            android.widget.Toast.makeText(context, "Nie można pobrać aktualizacji", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

fun queryDownloadStatus(context: Context, versionName: String): DownloadStatus = try {
    val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
    val f = apkFile(context, versionName)
    if (prefs.getString(KEY_DL_VERSION, "") != versionName) {
        if (isIntactApk(context, f)) DownloadStatus(DlState.SUCCESS, f.length(), f.length())
        else DownloadStatus(DlState.MISSING)
    } else {
        val id = prefs.getLong(KEY_DL_ID, -1L)
        if (id < 0) {
            if (isIntactApk(context, f)) DownloadStatus(DlState.SUCCESS, f.length(), f.length())
            else DownloadStatus(DlState.MISSING)
        } else {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val c = dm.query(DownloadManager.Query().setFilterById(id))
            try {
                if (!c.moveToFirst()) {
                    if (isIntactApk(context, f)) DownloadStatus(DlState.SUCCESS, f.length(), f.length())
                    else DownloadStatus(DlState.MISSING)
                } else {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (f.exists() && f.length() > 0) DownloadStatus(DlState.SUCCESS, f.length(), f.length())
                            else DownloadStatus(DlState.MISSING)
                        }
                        DownloadManager.STATUS_FAILED -> DownloadStatus(DlState.FAILED)
                        DownloadManager.STATUS_PAUSED -> DownloadStatus(DlState.PAUSED)
                        else -> {
                            val done = runCatching {
                                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            }.getOrDefault(0L)
                            val total = runCatching {
                                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            }.getOrDefault(-1L)
                            DownloadStatus(DlState.RUNNING, done.coerceAtLeast(0L), total)
                        }
                    }
                }
            } finally {
                runCatching { c.close() }
            }
        }
    }
} catch (_: Exception) {
    DownloadStatus(DlState.MISSING)
}

/** Kasuje apk wszystkich wersji poza wskazaną. */
private fun deleteStaleApks(context: Context, keepVersion: String?) {
    runCatching {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        val keepName = keepVersion?.let { apkFile(context, it).name }
        dir.listFiles { f -> f.isFile && f.name.startsWith("kiedy-dojade-") && f.name.endsWith(".apk") }
            ?.forEach { if (keepName == null || it.name != keepName) runCatching { it.delete() } }
    }
}

fun installApkFile(context: Context, versionName: String) {
    try {
        val f = apkFile(context, versionName)
        if (queryDownloadStatus(context, versionName).state != DlState.SUCCESS) {
            if (!f.exists()) {
                android.widget.Toast.makeText(context, "Plik zniknął — pobierz ponownie", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Plik jeszcze się pobiera", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", f)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "Brak instalatora pakietów", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.util.Log.w("Update", "install failed", e)
        android.widget.Toast.makeText(context, "Nie można otworzyć pliku", android.widget.Toast.LENGTH_SHORT).show()
    }
}
