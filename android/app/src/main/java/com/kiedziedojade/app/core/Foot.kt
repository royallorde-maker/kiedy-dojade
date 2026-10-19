package com.kiedziedojade.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Street-level walking geometry from the public OSRM foot router. The planner
 * knows stops, not pavements, so navigation draws the walk along real streets
 * instead of a straight line. Failures simply fall back to the straight line.
 */
object FootRoute {
    private const val BASE = "https://routing.openstreetmap.de/routed-foot/route/v1/foot"

    /** Insertion order doubles as the eviction order — 32 walks is plenty. */
    private val cache = object :
        LinkedHashMap<String, List<Pair<Double, Double>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<Pair<Double, Double>>>?,
        ): Boolean = size > 32
    }

    private fun key(from: Pair<Double, Double>, to: Pair<Double, Double>) = String.format(
        Locale.US, "%.5f,%.5f;%.5f,%.5f", from.first, from.second, to.first, to.second,
    )

    suspend fun path(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
    ): List<Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        val k = key(from, to)
        synchronized(cache) { cache[k] }?.let { return@withContext it }
        val pts = runCatching {
            val url = "$BASE/${from.second},${from.first};${to.second},${to.first}" +
                "?overview=full&geometries=geojson&steps=false"
            val root = JSONObject(Net.getText(url, timeoutMs = 7000))
            if (root.optString("code") != "Ok") return@runCatching null
            val coords = root.getJSONArray("routes").getJSONObject(0)
                .getJSONObject("geometry").getJSONArray("coordinates")
            val out = ArrayList<Pair<Double, Double>>(coords.length())
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                out += c.getDouble(1) to c.getDouble(0)
            }
            out.takeIf { it.size >= 2 }
        }.getOrNull()
        if (pts != null) synchronized(cache) { cache[k] = pts }
        pts
    }
}
