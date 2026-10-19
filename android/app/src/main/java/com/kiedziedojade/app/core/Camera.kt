package com.kiedziedojade.app.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Kamera prowadzenia: wykrywanie zakrętów przed użytkownikiem i dobór zoomu
 * w stylu nawigacji Google Maps. Logika jest niezależna od Androida, więc da
 * się ją testować bez urządzenia.
 */

/** Azymut z punktu [a] do [b] w stopniach (0 = północ, 90 = wschód). */
fun bearingDeg(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(a.first)
    val lat2 = Math.toRadians(b.first)
    val dLng = Math.toRadians(b.second - a.second)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Kąt skrętu na wierzchołku [b] w stopniach (0 = prosto, 180 = zawracanie). */
fun turnAngleDeg(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
    val d = abs(bearingDeg(b, c) - bearingDeg(a, b)) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/**
 * Odległość do najbliższego wyraźnego zakrętu na trasie przed pozycją [fix].
 * Zwraca null, gdy do końca trasy nie ma zakrętu w zasięgu [lookAheadMeters].
 * [minTurnDeg] odsądza łagodne łuki od skrętów wartych przybliżenia.
 */
fun cornerAheadMeters(
    path: List<Pair<Double, Double>>,
    fix: Pair<Double, Double>,
    lookAheadMeters: Double = 95.0,
    minTurnDeg: Double = 26.0,
): Double? {
    if (path.size < 3) return null

    var nearest = -1
    var nearestD = Double.MAX_VALUE
    path.forEachIndexed { i, p ->
        val d = haversine(fix.first, fix.second, p.first, p.second)
        if (d < nearestD) {
            nearestD = d
            nearest = i
        }
    }
    if (nearest < 0 || nearest >= path.lastIndex) return null

    var ahead = 0.0
    var i = nearest
    while (i < path.lastIndex) {
        ahead += haversine(path[i].first, path[i].second, path[i + 1].first, path[i + 1].second)
        if (ahead > lookAheadMeters) return null
        val vertex = i + 1
        if (vertex > 0 && vertex < path.lastIndex) {
            val turn = turnAngleDeg(path[vertex - 1], path[vertex], path[vertex + 1])
            if (turn >= minTurnDeg) return ahead
        }
        i++
    }
    return null
}

/**
 * Docelowy zoom prowadzenia. Im bliżej zakrętu, tym mocniej przybliżamy —
 * jak w nawigacji Google Maps. [baseZoom] to spokojny zoom prostego odcinka.
 */
fun autoNavZoom(
    baseZoom: Float,
    cornerMeters: Double?,
    minZoom: Float = 13.5f,
    maxZoom: Float = 17.8f,
): Float {
    val boost = when {
        cornerMeters == null -> 0f
        cornerMeters <= 20.0 -> 1.35f
        cornerMeters <= 50.0 -> 0.95f
        cornerMeters <= 85.0 -> 0.55f
        else -> 0.2f
    }
    return (baseZoom + boost).coerceIn(minZoom, maxZoom)
}
