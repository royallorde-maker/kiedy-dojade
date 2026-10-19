package com.kiedziedojade.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Road geometry for bus legs. The planner only knows the stops a bus calls at,
 * so drawing stop-to-stop straight lines can cut corners, parks and buildings.
 * The public OSRM car router fills the gaps along real streets. Failures fall
 * back to the stop polyline, exactly like [FootRoute] does for walking.
 */
object DriveRoute {
    private const val BASE = "https://routing.openstreetmap.de/routed-car/route/v1/driving"

    /** Keep URLs short and well below the public server's waypoint limit. */
    private const val MAX_WAYPOINTS = 24

    private val cache = object :
        LinkedHashMap<String, List<Pair<Double, Double>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<Pair<Double, Double>>>?,
        ): Boolean = size > 32
    }

    private fun key(points: List<Pair<Double, Double>>) = points.joinToString(";") {
        String.format(Locale.US, "%.5f,%.5f", it.first, it.second)
    }

    suspend fun path(
        points: List<Pair<Double, Double>>,
    ): List<Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        val clean = points.filterIndexed { i, p -> i == 0 || p != points[i - 1] }
        if (clean.size < 2) return@withContext null

        val k = key(clean)
        synchronized(cache) { cache[k] }?.let { return@withContext it }

        val route = runCatching { route(clean) }.getOrNull()
        if (route != null) synchronized(cache) { cache[k] = route }
        route
    }

    /** Routes through every stop, in chunks so long bus lines still fit. */
    private fun route(points: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        val out = ArrayList<Pair<Double, Double>>()
        var start = 0
        while (start < points.lastIndex) {
            val end = (start + MAX_WAYPOINTS - 1).coerceAtMost(points.lastIndex)
            val leg = fetch(points.subList(start, end + 1)) ?: return null
            // the shared waypoint is already the last point of the previous chunk
            out += if (out.isEmpty()) leg else leg.drop(1)
            start = end
        }
        return out.takeIf { it.size >= 2 }
    }

    private fun fetch(points: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        val coords = points.joinToString(";") { "${it.second},${it.first}" }
        val url = "$BASE/$coords?overview=full&geometries=geojson&steps=false"
        val root = JSONObject(Net.getText(url, timeoutMs = 12_000))
        if (root.optString("code") != "Ok") return null
        val arr = root.getJSONArray("routes").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        val out = ArrayList<Pair<Double, Double>>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.getJSONArray(i)
            out += c.getDouble(1) to c.getDouble(0)
        }
        return out.takeIf { it.size >= 2 }
    }
}
