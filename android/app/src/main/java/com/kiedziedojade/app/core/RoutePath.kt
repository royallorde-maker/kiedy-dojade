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
)

/**
 * Zamienia nogi połączenia na odcinki mapy: marsz do przystanku (przerywana
 * kreska) i przejazdy autobusem (linia po przystankach kursu). Punkty marszu
 * biorą się z [WalkLeg.fromPt]/[WalkLeg.toPt] — planer je wylicza.
 */
fun itinerarySegments(it: Itinerary): List<PathSeg> {
    val out = ArrayList<PathSeg>()
    var cur: Pair<Double, Double>? = null
    for (leg in it.legs) {
        when (leg) {
            is WalkLeg -> {
                val a = leg.fromPt ?: cur
                val b = leg.toPt
                if (a != null && b != null && a != b) {
                    out += PathSeg(listOf(a, b), walk = true)
                }
                cur = b ?: a ?: cur
            }

            is RideLeg -> {
                val pts = leg.stops.map { st -> st.stop.lat to st.stop.lng }
                if (pts.size >= 2) out += PathSeg(pts, walk = false, line = leg.line)
                cur = pts.lastOrNull() ?: cur
            }
        }
    }
    return out
}
