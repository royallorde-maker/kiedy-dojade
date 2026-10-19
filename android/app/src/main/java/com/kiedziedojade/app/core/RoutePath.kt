package com.kiedziedojade.app.core

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

/**
 * Zamienia nogi połączenia na odcinki mapy: marsz do przystanku (przerywana
 * kreska) i przejazdy autobusem (linia po przystankach kursu). Punkty marszu
 * biorą się z [WalkLeg.fromPt]/[WalkLeg.toPt] — planer je wylicza — a gdy
 * znamy trasę po ulicach ([walkPaths], klucz = indeks nogi), rysujemy ją
 * zamiast linii prostej.
 */
fun itinerarySegments(
    it: Itinerary,
    walkPaths: Map<Int, List<Pair<Double, Double>>> = emptyMap(),
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
                val pts = leg.stops.map { st -> st.stop.lat to st.stop.lng }
                if (pts.size >= 2) out += PathSeg(pts, walk = false, line = leg.line, leg = i)
                cur = pts.lastOrNull() ?: cur
            }
        }
    }
    return out
}
