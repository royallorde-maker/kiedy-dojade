package com.kiedziedojade.app.core

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

/**
 * Projekcja Web Mercator (ta sama, której używa OSM) — potrzebna, żeby
 * narysować trasę na kafelkach bez MapView.
 */
object Merc {
    /** długość geograficzna → 0..1 (zachód → wschód) */
    fun x(lng: Double): Double = (lng + 180.0) / 360.0

    /** szerokość geograficzna → 0..1 (północ → południe) */
    fun y(lat: Double): Double {
        val s = sin(lat * PI / 180.0)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }
}

/**
 * Dopasowanie punktów trasy do prostokąta miniatury: poziom kafelków, ich
 * dodatkowe skalowanie i środek mapy. Współrzędne ekranu liczy się przez
 * [screenX]/[screenY].
 */
data class StaticFit(
    val zoom: Int,
    /** skalowanie kafelków (1..maxScale) — dobiera się tak, by trasa wypełniała miniaturę */
    val scale: Float,
    /** środek mapy w świecie (0..1) */
    val cx: Double,
    val cy: Double,
) {
    /** bok świata w pikselach dla tego zoomu i skalowania */
    val world: Double get() = 256.0 * (1 shl zoom) * scale

    fun screenX(lng: Double, widthPx: Int): Float =
        ((Merc.x(lng) - cx) * world + widthPx / 2.0).toFloat()

    fun screenY(lat: Double, heightPx: Int): Float =
        ((Merc.y(lat) - cy) * world + heightPx / 2.0).toFloat()
}

/**
 * Największy zoom kafelków (do [maxZoom]) mieszczący wszystkie punkty
 * w prostokącie [widthPx]×[heightPx] z marginesem [padPx]; gdy trasa jest
 * krótsza, kafelki są dodatkowo powiększane (do [maxScale]) tak, by zajmowała
 * możliwie dużo miejsca. Pusta lista punktów → widok domyślny (środek świata).
 */
fun fitStatic(
    pts: List<Pair<Double, Double>>,
    widthPx: Int,
    heightPx: Int,
    padPx: Int,
    minZoom: Int = 9,
    maxZoom: Int = 15,
    maxScale: Float = 1.6f,
): StaticFit {
    val xs = pts.map { Merc.x(it.second) }
    val ys = pts.map { Merc.y(it.first) }
    val x0 = xs.minOrNull() ?: 0.5
    val x1 = xs.maxOrNull() ?: 0.5
    val y0 = ys.minOrNull() ?: 0.5
    val y1 = ys.maxOrNull() ?: 0.5
    val cx = (x0 + x1) / 2
    val cy = (y0 + y1) / 2
    val availW = (widthPx - 2 * padPx).coerceAtLeast(1)
    val availH = (heightPx - 2 * padPx).coerceAtLeast(1)

    var zoom = minZoom
    for (z in maxZoom downTo minZoom) {
        val world = 256.0 * (1 shl z)
        if ((x1 - x0) * world <= availW && (y1 - y0) * world <= availH) {
            zoom = z
            break
        }
    }

    // dociągnięcie do brzegów: ile razy można powiększyć kafelki, żeby trasa
    // nadal się mieściła (krótkie trasy nie zostają malutkie)
    val world = 256.0 * (1 shl zoom)
    val dxPx = (x1 - x0) * world
    val dyPx = (y1 - y0) * world
    val sx = if (dxPx > 0.0) availW / dxPx else maxScale.toDouble()
    val sy = if (dyPx > 0.0) availH / dyPx else maxScale.toDouble()
    val scale = minOf(sx, sy).coerceIn(1.0, maxScale.toDouble()).toFloat()
    return StaticFit(zoom = zoom, scale = scale, cx = cx, cy = cy)
}
