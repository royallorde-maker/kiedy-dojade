package com.kiedziedojade.app.core

/** Offline place search over stops, streets and POIs — port of search.ts. */

class SearchItem(
    val id: String,
    /** "stop" | "street" | "poi" */
    val kind: String,
    val pkind: String?,
    val name: String,
    val code: Int?,
    val onDemand: Boolean,
    val lat: Double,
    val lng: Double,
    val norm: String,
    val ref: PlaceRef,
)

fun norm(s: String): String = s.lowercase()
    .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
    .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
    .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
    .replace(Regex("[^a-z0-9\\s./-]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

fun buildIndex(world: World): List<SearchItem> {
    val items = ArrayList<SearchItem>(world.stops.size + world.places.streets.size + world.places.pois.size)
    world.stops.forEach { s ->
        items += SearchItem(
            id = "stop:${s.i}", kind = "stop", pkind = null, name = s.name, code = s.code,
            onDemand = s.onDemand, lat = s.lat, lng = s.lng,
            norm = norm(s.name + " " + s.code),
            ref = PlaceRef.StopRef(s.name, s.i, s.id),
        )
    }
    world.places.streets.forEachIndexed { idx, street ->
        val mid = street.pts.getOrNull(street.pts.size / 2) ?: (0.0 to 0.0)
        items += SearchItem(
            id = "street:$idx", kind = "street", pkind = null, name = street.name, code = null,
            onDemand = false, lat = mid.first, lng = mid.second, norm = norm(street.name),
            ref = PlaceRef.StreetRef(street.name, street.pts),
        )
    }
    world.places.pois.forEachIndexed { idx, poi ->
        items += SearchItem(
            id = "poi:$idx", kind = "poi", pkind = poi.kind, name = poi.name, code = null,
            onDemand = false, lat = poi.lat, lng = poi.lng, norm = norm(poi.name),
            ref = PlaceRef.PoiRef(poi.name, poi.kind, poi.lat, poi.lng),
        )
    }
    return items
}

private fun score(item: SearchItem, tokens: List<String>): Double {
    var total = 0.0
    val words = item.norm.split(Regex("[\\s./-]+")).filter { it.isNotEmpty() }
    for (tok in tokens) {
        when {
            item.norm == tok -> total += 400
            words.any { it == tok } -> total += 220
            words.any { it.startsWith(tok) } -> total += 120
            item.norm.startsWith(tok) -> total += 100
            item.norm.contains(tok) -> total += 45
            else -> return -1.0
        }
    }
    total -= minOf(60.0, item.norm.length.toDouble())
    if (item.kind == "stop") total += 12
    return total
}

fun searchPlaces(
    index: List<SearchItem>,
    query: String,
    near: Pair<Double, Double>? = null,
    limit: Int = 14,
): List<SearchItem> {
    val q = norm(query)
    if (q.length < 2) return emptyList()
    val tokens = q.split(" ").filter { it.isNotEmpty() }
    val scored = ArrayList<Triple<SearchItem, Double, Double>>()
    for (item in index) {
        val s = score(item, tokens)
        if (s < 0) continue
        val d = if (near != null) haversine(near.first, near.second, item.lat, item.lng) else 0.0
        scored += Triple(item, s, d)
    }
    scored.sortWith(compareByDescending<Triple<SearchItem, Double, Double>> { it.second }.thenBy { it.third })
    val out = ArrayList<SearchItem>()
    val seen = HashSet<String>()
    for ((item, _, _) in scored) {
        val key = item.kind + "|" + item.norm
        if (!seen.add(key)) continue
        out += item
        if (out.size >= limit) break
    }
    return out
}

/** Nearest stops to a point, sorted by walking distance. */
fun nearestStops(
    world: World,
    lat: Double,
    lng: Double,
    limit: Int = 6,
    maxMeters: Double = 1500.0,
): List<Pair<Stop, Double>> = world.stops
    .filter { !it.onlyDisembarking }
    .map { it to haversine(lat, lng, it.lat, it.lng) }
    .filter { it.second <= maxMeters }
    .sortedBy { it.second }
    .take(limit)

/** The stop a quick chip means: first stop matching a name query. */
fun firstStopItem(index: List<SearchItem>, query: String): SearchItem? =
    searchPlaces(index, query, limit = 8).firstOrNull { it.kind == "stop" }
