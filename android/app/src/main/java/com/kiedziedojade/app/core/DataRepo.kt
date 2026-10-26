package com.kiedziedojade.app.core

import android.content.Context
import com.kiedziedojade.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The schedule dataset: seeded from assets (fresh install works offline),
 * refreshed from the harvested files on GitHub. Refresh is atomic — new files
 * land in `data.tmp/`, then the directory is swapped, so a half-finished
 * download can never produce a mixed dataset.
 */
class DataRepo(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var cached: World? = null

    private fun dir() = File(context.filesDir, "data")
    private fun tmpDir() = File(context.filesDir, "data.tmp")
    private fun oldDir() = File(context.filesDir, "data.old")

    private fun readFrom(base: File): ((String) -> String?)? {
        if (!base.isDirectory) return null
        return { name ->
            val f = File(base, name)
            if (f.isFile) runCatching { f.readText() }.getOrNull() else null
        }
    }

    private fun loadLocal(): World {
        readFrom(dir())?.let { reader ->
            DataJson.world("cache", reader)?.let { return it }
        }
        val assets = context.assets
        val reader: (String) -> String? = { name ->
            runCatching { assets.open("data/$name").bufferedReader().use { it.readText() } }.getOrNull()
        }
        return DataJson.world("assets", reader)
            ?: error("Bundled schedule data is unreadable")
    }

    /** The dataset — instant after the first call. */
    suspend fun world(): World {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: loadLocal().also { cached = it }
        }
    }

    data class Refresh(val changed: Boolean, val error: String? = null, val world: World? = null)

    /**
     * Pull the harvested dataset from GitHub. Compares `meta.json` first —
     * when the timestamp matches, nothing is downloaded beyond that one file.
     */
    suspend fun refresh(force: Boolean = false): Refresh = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val metaText = Net.getText("${BuildConfig.DATA_URL}/meta.json")
                val remoteMeta = DataJson.parseMeta(metaText)
                val current = cached ?: loadLocal().also { cached = it }
                if (!force && remoteMeta.generatedAt.isNotEmpty() &&
                    remoteMeta.generatedAt == current.meta.generatedAt
                ) {
                    return@withLock Refresh(changed = false, world = current)
                }

                val files = LinkedHashMap<String, String>()
                files["stops.json"] = Net.getText("${BuildConfig.DATA_URL}/stops.json")
                files["calendar.json"] = Net.getText("${BuildConfig.DATA_URL}/calendar.json")
                // Places are optional. If their request fails, keep the copy we
                // already have instead of shipping a dataset without shops/POIs.
                val previousPlaces = File(dir(), "places.json")
                    .takeIf { it.isFile }
                    ?.let { runCatching { it.readText() }.getOrNull() }
                val places = runCatching { Net.getText("${BuildConfig.DATA_URL}/places.json") }
                    .getOrNull() ?: previousPlaces
                if (places != null) files["places.json"] = places
                for (pid in remoteMeta.patterns.keys) {
                    files["trips-$pid.json"] = Net.getText("${BuildConfig.DATA_URL}/trips-$pid.json")
                }
                // meta LAST: a complete set of files is what the swap makes visible
                files["meta.json"] = metaText

                val tmp = tmpDir()
                tmp.deleteRecursively()
                if (!tmp.mkdirs()) throw IllegalStateException("mkdir ${tmp.name} failed")
                for ((name, content) in files) {
                    val f = File(tmp, name)
                    f.writeText(content)
                }
                val target = dir()
                val old = oldDir()
                old.deleteRecursively()
                if (target.exists() && !target.renameTo(old)) {
                    throw IllegalStateException("could not move the old dataset away")
                }
                if (!tmp.renameTo(target)) {
                    // Restore old data if swap failed
                    if (old.exists()) old.renameTo(target)
                    throw IllegalStateException("could not swap the dataset")
                }
                old.deleteRecursively()

                val reader = readFrom(target)
                    ?: throw IllegalStateException("cannot read from new dataset directory")
                val fresh = DataJson.world("cache", reader)
                    ?: throw IllegalStateException("downloaded dataset is unreadable")
                cached = fresh
                Refresh(changed = true, world = fresh)
            } catch (e: Exception) {
                Refresh(changed = false, error = e.message ?: "błąd sieci", world = cached)
            }
        }
    }
}

/** Tiny HTTP helper: browser UA (the city WAF 404s non-browser agents), timeouts. */
object Net {
    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    fun getText(url: String, timeoutMs: Int = 10_000): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", UA)
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
