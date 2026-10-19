package com.kiedziedojade.app.core

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
