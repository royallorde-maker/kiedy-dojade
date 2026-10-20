package com.kiedziedojade.app.core

import kotlin.math.cos
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Odcinek trasy połączenia gotowy do narysowania na mapie.
 * [walk] = odcinek pieszo (linia przerywana), [line] = numer linii autobusu
 * (kolor linii), null przy marszu.
 */
data class PathSeg(
    val pts: List<Pair<Double, Double>>,
    val walk: Boolean,
    val line: String? = null,
    /** indeks nogi połączenia, z której powstał odcinek (prowadzenie po trasie) */
    val leg: Int = 0,
)

/** Geometria nóg połączenia pobrana z routera; puste mapy = linie po przystankach. */
data class RoutedPaths(
    val walk: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
    val ride: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
)

/**
 * Pobiera równolegle trasy ulicami dla wszystkich nóg pieszych i przejazdów.
 * Wynik jest mapowany po indeksie nogi, więc [itinerarySegments] może go
 * podstawić bez zmiany kolejności i bez zgadywania, do czego należy dana linia.
 */
suspend fun routedPaths(it: Itinerary): RoutedPaths = coroutineScope {
    val walks = it.legs.mapIndexedNotNull { i, leg ->
        if (leg !is WalkLeg) return@mapIndexedNotNull null
        val a = leg.fromPt ?: return@mapIndexedNotNull null
        val b = leg.toPt ?: return@mapIndexedNotNull null
        async { i to runCatching { FootRoute.path(a, b) }.getOrNull() }
    }
    val rides = it.legs.mapIndexedNotNull { i, leg ->
        if (leg !is RideLeg) return@mapIndexedNotNull null
        val pts = leg.stops.map { rs -> rs.stop.lat to rs.stop.lng }
        if (pts.size < 2) return@mapIndexedNotNull null
        async { i to runCatching { DriveRoute.path(pts) }.getOrNull() }
    }
    RoutedPaths(
        walk = walks.awaitAll()
            .mapNotNull { (i, pts) -> pts?.takeIf { it.size >= 2 }?.let { i to it } }
            .toMap(),
        ride = rides.awaitAll()
            .mapNotNull { (i, pts) -> pts?.takeIf { it.size >= 2 }?.let { i to it } }
            .toMap(),
    )
}

/**
 * Zamienia nogi połączenia na odcinki mapy: marsz do przystanku (przerywana
 * kreska) i przejazdy autobusem (linia po przystankach kursu). Punkty marszu
 * biorą się z [WalkLeg.fromPt]/[WalkLeg.toPt] — planer je wylicza — a gdy
 * znamy trasę po ulicach ([walkPaths]/[ridePaths]), rysujemy ją zamiast linii
 * prostej.
 */
fun itinerarySegments(
    it: Itinerary,
    walkPaths: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
    ridePaths: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
): List<PathSeg> {
    val out = ArrayList<PathSeg>()
    var cur: Pair<Double, Double>? = null
    it.legs.forEachIndexed { i, leg ->
        when (leg) {
            is WalkLeg -> {
                val a = leg.fromPt ?: cur
                val b = leg.toPt
                val routed = walkPaths[i]?.takeIf { pts -> pts.size >= 2 }
                val pts = routed ?: if (a != null && b != null && a != b) listOf(a, b) else null
                if (pts != null) {
                    out += PathSeg(pts, walk = true, leg = i)
                }
                cur = b ?: a ?: cur
            }

            is RideLeg -> {
                val stops = leg.stops.map { st -> st.stop.lat to st.stop.lng }
                val routed = ridePaths[i]?.takeIf { pts -> pts.size >= 2 }
                val pts = routed ?: stops
                if (pts.size >= 2) out += PathSeg(pts, walk = false, line = leg.line, leg = i)
                cur = stops.lastOrNull() ?: cur
            }
        }
    }
    return out
}

/** Rzut pozycji na polilinię trasy: [along] = metry od początku, [off] = metry od trasy. */
data class PathProjection(val along: Double, val off: Double)

/**
 * Najbliższy punkt trasy względem pozycji: odległość wzdłuż polilinii (metry
 * od pierwszego punktu) i odległość od trasy. Null, gdy trasa jest za krótka.
 */
fun projectOnPath(pts: List<Pair<Double, Double>>, lat: Double, lng: Double): PathProjection? {
    if (pts.size < 2) return null
    var along = 0.0
    var bestOff = Double.MAX_VALUE
    var bestAlong = 0.0
    for (i in 0 until pts.lastIndex) {
        val a = pts[i]
        val b = pts[i + 1]
        val seg = haversine(a.first, a.second, b.first, b.second)
        val t = nearestFraction(a, b, lat, lng)
        val px = a.first + (b.first - a.first) * t
        val py = a.second + (b.second - a.second) * t
        val off = haversine(lat, lng, px, py)
        if (off < bestOff) {
            bestOff = off
            bestAlong = along + seg * t
        }
        along += seg
    }
    return PathProjection(bestAlong, bestOff)
}

/**
 * Ile metrów aktywnej nogi już za nami. Null, gdy nie ma pozycji albo jesteś
 * daleko od trasy — wtedy nie ruszamy rysunku, żeby GPS nie uciął trasy
 * przypadkiem. [marginM] zostawia kawałek linii tuż za znacznikiem.
 */
fun traveledCut(
    segs: List<PathSeg>,
    fix: Pair<Double, Double>?,
    activeLeg: Int,
    maxOffRouteM: Double = 150.0,
    marginM: Double = 12.0,
): Double? {
    val f = fix ?: return null
    val pts = segs.filter { it.leg == activeLeg }.flatMap { it.pts }
    val proj = projectOnPath(pts, f.first, f.second) ?: return null
    if (proj.off > maxOffRouteM) return null
    return (proj.along - marginM).coerceAtLeast(0.0)
}

/**
 * Zostawia tylko trasę przed użytkownikiem: minione nogi znikają, a z aktywnej
 * nogi zostaje odcinek od [cut] metra do końca. [cut] = null rysuje aktywną
 * nogę w całości (ale minione i tak znikają).
 */
fun trimTraveled(segs: List<PathSeg>, activeLeg: Int, cut: Double?): List<PathSeg> {
    if (cut == null || cut <= 0.0) return segs.filter { it.leg >= activeLeg }
    val out = ArrayList<PathSeg>(segs.size)
    var acc = 0.0
    for (seg in segs) {
        when {
            seg.leg < activeLeg -> Unit
            seg.leg > activeLeg -> out += seg
            else -> {
                val len = polylineMeters(seg.pts)
                val end = acc + len
                when {
                    end <= cut -> Unit
                    acc >= cut -> out += seg
                    else -> {
                        val trimmed = cutPolyline(seg.pts, cut - acc)
                        if (trimmed.size >= 2) out += seg.copy(pts = trimmed)
                    }
                }
                acc = end
            }
        }
    }
    return out
}

/** Polilinia bez pierwszych [cutM] metrów (z punktem przecięcia na początku). */
private fun cutPolyline(pts: List<Pair<Double, Double>>, cutM: Double): List<Pair<Double, Double>> {
    if (cutM <= 0.0 || pts.size < 2) return pts
    val out = ArrayList<Pair<Double, Double>>(pts.size)
    var d = 0.0
    for (i in 0 until pts.lastIndex) {
        val a = pts[i]
        val b = pts[i + 1]
        val step = haversine(a.first, a.second, b.first, b.second)
        if (d + step >= cutM) {
            if (out.isEmpty()) {
                val t = if (step > 0.0) ((cutM - d) / step).coerceIn(0.0, 1.0) else 0.0
                out += (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
            }
            out += b
        }
        d += step
    }
    return out
}

private fun polylineMeters(pts: List<Pair<Double, Double>>): Double {
    var d = 0.0
    for (i in 0 until pts.lastIndex) {
        d += haversine(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
    }
    return d
}

/** Ułamek [0,1] najbliższego punktu na odcinku (lokalny układ metryczny). */
private fun nearestFraction(
    a: Pair<Double, Double>,
    b: Pair<Double, Double>,
    lat: Double,
    lng: Double,
): Double {
    val kLat = 110_540.0
    val kLng = 111_320.0 * cos(a.first * Math.PI / 180.0)
    val abx = (b.second - a.second) * kLng
    val aby = (b.first - a.first) * kLat
    val apx = (lng - a.second) * kLng
    val apy = (lat - a.first) * kLat
    val len2 = abx * abx + aby * aby
    if (len2 <= 1e-9) return 0.0
    return ((apx * abx + apy * aby) / len2).coerceIn(0.0, 1.0)
}
