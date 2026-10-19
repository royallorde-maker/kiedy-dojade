package com.kiedziedojade.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.Merc
import com.kiedziedojade.app.core.PathSeg
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.fitStatic
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Miniatura trasy połączenia rysowana wprost na kanwie: kafelki OSM + kreski
 * i kropki. Bez MapView — w liście wyników kilka map naraz (i ich dotyk)
 * dławiło interfejs, a tutaj wystarczy kilka bitmap.
 */

/** Kropka na miniaturze trasy: punkt, kolor, średnica (dp) i grubość obwódki. */
data class RouteDot(
    val pt: Pair<Double, Double>,
    val color: Color,
    val sizeDp: Float,
    val ringDp: Float = 0f,
    /** indeks nogi połączenia (prowadzenie wygasza kropki za sobą) */
    val leg: Int = 0,
)

/**
 * Kropki trasy połączenia: przystanki przejazdów w kolorze linii (końce
 * większe), a na wierzchu start (marsz) i koniec podróży.
 */
@Composable
fun routeDots(it: Itinerary): List<RouteDot> {
    val scheme = MaterialTheme.colorScheme
    val startColor = scheme.tertiary
    val endColor = scheme.primary
    return remember(it, startColor, endColor) {
        val out = ArrayList<RouteDot>()
        it.legs.forEachIndexed { i, leg ->
            if (leg !is RideLeg) return@forEachIndexed
            leg.stops.forEachIndexed { idx, rs ->
                val edge = idx == 0 || idx == leg.stops.size - 1
                out += RouteDot(
                    pt = rs.stop.lat to rs.stop.lng,
                    color = lineColor(leg.line),
                    sizeDp = if (edge) 9f else 6f,
                    ringDp = if (edge) 2f else 0f,
                    leg = i,
                )
            }
        }
        // start: początek marszu albo przystanek, z którego jedziemy
        val firstWalk = it.legs.firstOrNull() as? WalkLeg
        val firstPt = firstWalk?.fromPt ?: it.firstRide?.boardStop?.let { s -> s.lat to s.lng }
        firstPt?.let { p ->
            out += RouteDot(pt = p, color = startColor, sizeDp = 13f, ringDp = 2f, leg = 0)
        }
        // koniec: cel marszu albo ostatni przystanek
        val lastWalk = it.legs.lastOrNull() as? WalkLeg
        val lastPt = lastWalk?.toPt ?: it.lastRide?.alightStop?.let { s -> s.lat to s.lng }
        lastPt?.let { p ->
            out += RouteDot(
                pt = p,
                color = endColor,
                sizeDp = 13f,
                ringDp = 2f,
                leg = it.legs.lastIndex.coerceAtLeast(0),
            )
        }
        out
    }
}

/** Kafelek wczytany do narysowania (współrzędne kafelkowe na poziomie [zoom]). */
private data class Tile(val x: Int, val y: Int, val bmp: ImageBitmap)

/** Wspólny cache kafelków w pamięci (kilka miniatur dzieli te same kafelki). */
private val tileMem = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Filtr nocny kafelków — jak na ekranie mapy. */
private val nightMatrix = ColorMatrix(
    floatArrayOf(
        -0.92f, 0f, 0f, 0f, 235f,
        0f, -0.92f, 0f, 0f, 235f,
        0f, 0f, -0.92f, 0f, 235f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private fun fetchTile(context: Context, z: Int, x: Int, y: Int): ByteArray? = runCatching {
    val conn = URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection
    conn.setRequestProperty("User-Agent", context.packageName)
    conn.connectTimeout = 8000
    conn.readTimeout = 12000
    conn.inputStream.use { it.readBytes() }
}.getOrNull()

private suspend fun loadTile(context: Context, z: Int, x: Int, y: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val name = "$z/$x/$y"
        tileMem.get(name)?.let { return@withContext it }
        val file = File(context.filesDir, "map-tiles/$name.png")
        val cached = if (file.isFile) runCatching { file.readBytes() }.getOrNull() else null
        val data = cached ?: fetchTile(context, z, x, y)?.also { bytes ->
            runCatching {
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
        } ?: return@withContext null
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@withContext null
        val img = bmp.asImageBitmap()
        tileMem.put(name, img)
        img
    }

/**
 * Miniatura trasy połączenia: kafelki OpenStreetMap, przejazdy w kolorach
 * linii, marsz przerywaną kreską, kropki przystanków. Rysowana na kanwie,
 * więc przesuwanie palcem po mapce przewija listę wyników.
 */
@Composable
fun RouteMiniMap(
    segs: List<PathSeg>,
    dots: List<RouteDot>,
    dark: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 138.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val walkColor = scheme.tertiary
    val ringColor = scheme.onPrimary
    val night = if (dark) ColorFilter.colorMatrix(nightMatrix) else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHighest),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = with(density) { maxWidth.toPx() }.toInt()
            val h = with(density) { maxHeight.toPx() }.toInt()
            val padPx = with(density) { 22.dp.toPx() }.toInt()

            val pts = remember(segs, dots) { segs.flatMap { it.pts } + dots.map { it.pt } }
            val fit = remember(pts, w, h, padPx) { fitStatic(pts, w, h, padPx) }

            var tiles by remember { mutableStateOf<List<Tile>>(emptyList()) }
            LaunchedEffect(fit, w, h) {
                tiles = emptyList()
                val world = fit.world
                val n = 1 shl fit.zoom
                val tilePx = 256.0 * fit.scale
                val originX = fit.cx * world - w / 2.0
                val originY = fit.cy * world - h / 2.0
                val tx0 = floor(originX / tilePx).toInt()
                val ty0 = floor(originY / tilePx).toInt()
                val tx1 = floor((originX + w) / tilePx).toInt()
                val ty1 = floor((originY + h) / tilePx).toInt()
                val out = ArrayList<Tile>()
                for (ty in ty0..ty1) {
                    if (ty < 0 || ty >= n) continue
                    for (tx in tx0..tx1) {
                        val wrapped = ((tx % n) + n) % n
                        val bmp = loadTile(context, fit.zoom, wrapped, ty) ?: continue
                        out += Tile(x = tx, y = ty, bmp = bmp)
                        tiles = ArrayList(out) // kafelki pojawiają się w miarę pobierania
                    }
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val tilePx = 256f * fit.scale
                val side = tilePx.roundToInt()
                val originX = (fit.cx * fit.world - size.width / 2.0).toFloat()
                val originY = (fit.cy * fit.world - size.height / 2.0).toFloat()

                tiles.forEach { tile ->
                    drawImage(
                        image = tile.bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(tile.bmp.width, tile.bmp.height),
                        dstOffset = IntOffset(
                            (tile.x * tilePx - originX).roundToInt(),
                            (tile.y * tilePx - originY).roundToInt(),
                        ),
                        dstSize = IntSize(side, side),
                        colorFilter = night,
                    )
                }

                // najpierw przejazdy, na wierzchu marsz — żeby kresek marszu
                // nie zasłoniły grubsze linie autobusów
                val rides = segs.filter { !it.walk }
                val walks = segs.filter { it.walk }
                rides.forEach { seg ->
                    val path = Path()
                    seg.pts.forEachIndexed { i, p ->
                        val x = fit.screenX(p.second, w)
                        val y = fit.screenY(p.first, h)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor(seg.line.orEmpty()),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                walks.forEach { seg ->
                    val path = Path()
                    seg.pts.forEachIndexed { i, p ->
                        val x = fit.screenX(p.second, w)
                        val y = fit.screenY(p.first, h)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = walkColor,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                }
                dots.forEach { dot ->
                    val cx = fit.screenX(dot.pt.second, w)
                    val cy = fit.screenY(dot.pt.first, h)
                    val r = dot.sizeDp.dp.toPx() / 2f
                    val ring = dot.ringDp.dp.toPx()
                    if (ring > 0f) drawCircle(color = ringColor, radius = r, center = Offset(cx, cy))
                    drawCircle(
                        color = dot.color,
                        radius = (r - ring).coerceAtLeast(0.5f),
                        center = Offset(cx, cy),
                    )
                }
            }
        }

        // atrybucja kafelków
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        ) {
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Mapa prowadzenia (tryb „za mną”): kafelki OpenStreetMap wokół bieżącej
 * pozycji, trasa połączenia z wygaszoną przebytą częścią, kropki przystanków
 * i znacznik pozycji ze strzałką kierunku. Bez gestów — mapa jedzie za
 * użytkownikiem, jak w nawigacji Google Maps.
 *
 * @param fix bieżąca pozycja albo null (wtedy widok pokazuje środek aktywnego odcinka)
 * @param activeLeg indeks nogi, na której jesteśmy — wcześniejsze są wygaszone
 * @param bus pozycja autobusu na żywo (null, gdy nie znamy)
 */
@Composable
fun RouteNavMap(
    segs: List<PathSeg>,
    dots: List<RouteDot>,
    fix: Pair<Double, Double>?,
    heading: Float?,
    activeLeg: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
    zoom: Int = 15,
    bus: Pair<Double, Double>? = null,
    busHeading: Float? = null,
    busColor: Color = Color(0xFF1E88E5),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val walkColor = scheme.tertiary
    val ringColor = scheme.onPrimary
    val night = if (dark) ColorFilter.colorMatrix(nightMatrix) else null
    val world = 256.0 * (1 shl zoom)
    val dim = 0.3f

    // płynne przejście znacznika autobusu między kolejnymi odczytami API
    var prevBus by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var curBus by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val busMix = remember { Animatable(1f) }
    LaunchedEffect(bus) {
        if (bus == null) {
            prevBus = null
            curBus = null
            busMix.snapTo(1f)
        } else if (bus != curBus) {
            prevBus = curBus
            curBus = bus
            if (prevBus == null) {
                busMix.snapTo(1f)
            } else {
                busMix.snapTo(0f)
                busMix.animateTo(1f, tween(durationMillis = 11_000, easing = LinearEasing))
            }
        }
    }

    // bez pozycji GPS centrujemy na środku aktywnego (a potem pierwszego) odcinka
    val fallback = remember(segs, activeLeg) {
        val seg = segs.firstOrNull { it.leg == activeLeg } ?: segs.firstOrNull()
        val pts = seg?.pts.orEmpty()
        if (pts.isEmpty()) null
        else ((pts.first().first + pts.last().first) / 2.0) to
            ((pts.first().second + pts.last().second) / 2.0)
    }
    val center = fix ?: bus ?: fallback
    if (center == null) {
        Box(modifier.background(scheme.surfaceContainerHighest))
        return
    }
    val cx = Merc.x(center.second)
    val cy = Merc.y(center.first)

    Box(modifier = modifier.background(scheme.surfaceContainerHighest)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = with(density) { maxWidth.toPx() }.toInt()
            val h = with(density) { maxHeight.toPx() }.toInt()

            // trwały zestaw kafelków wokół środka: mapa jedzie za pozycją,
            // więc kafelki dokładamy, a dalekie przycinamy
            var tiles by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

            val originX = cx * world - w / 2.0
            val originY = cy * world - h / 2.0
            val tx0 = floor(originX / 256.0).toInt() - 1
            val ty0 = floor(originY / 256.0).toInt() - 1
            val tx1 = floor((originX + w) / 256.0).toInt() + 1
            val ty1 = floor((originY + h) / 256.0).toInt() + 1

            LaunchedEffect(zoom, tx0, ty0, tx1, ty1) {
                val n = 1 shl zoom
                val want = LinkedHashSet<String>()
                for (ty in ty0..ty1) {
                    if (ty < 0 || ty >= n) continue
                    for (tx in tx0..tx1) want += "$zoom/${((tx % n) + n) % n}/$ty"
                }
                var map = tiles
                for (name in want) {
                    if (map.containsKey(name)) continue
                    val (z, x, y) = name.split("/")
                    val bmp = loadTile(context, z.toInt(), x.toInt(), y.toInt()) ?: continue
                    map = map + (name to bmp)
                    tiles = map // kafelki pojawiają się w miarę pobierania
                }
                if (map.size > 64) tiles = map.filterKeys { it in want }
            }

            Canvas(Modifier.fillMaxSize()) {
                val halfW = size.width / 2.0
                val halfH = size.height / 2.0
                val left = cx * world - halfW
                val top = cy * world - halfH
                val n = 1 shl zoom
                val ix0 = floor(left / 256.0).toInt() - 1
                val iy0 = floor(top / 256.0).toInt() - 1
                val ix1 = floor((left + size.width) / 256.0).toInt() + 1
                val iy1 = floor((top + size.height) / 256.0).toInt() + 1

                for (ty in iy0..iy1) {
                    if (ty < 0 || ty >= n) continue
                    for (tx in ix0..ix1) {
                        val bmp = tiles["$zoom/${((tx % n) + n) % n}/$ty"] ?: continue
                        drawImage(
                            image = bmp,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bmp.width, bmp.height),
                            dstOffset = IntOffset(
                                (tx * 256.0 - left).roundToInt(),
                                (ty * 256.0 - top).roundToInt(),
                            ),
                            dstSize = IntSize(256, 256),
                            colorFilter = night,
                        )
                    }
                }

                fun sxOf(lng: Double) = ((Merc.x(lng) - cx) * world + halfW).toFloat()
                fun syOf(lat: Double) = ((Merc.y(lat) - cy) * world + halfH).toFloat()
                fun pathOf(seg: PathSeg): Path {
                    val p = Path()
                    seg.pts.forEachIndexed { i, pt ->
                        val x = sxOf(pt.second)
                        val y = syOf(pt.first)
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    return p
                }

                // najpierw przejazdy, na wierzchu marsz — jak na miniaturze
                segs.sortedBy { it.walk }.forEach { seg ->
                    val passed = seg.leg < activeLeg
                    drawPath(
                        path = pathOf(seg),
                        color = (if (seg.walk) walkColor else lineColor(seg.line.orEmpty()))
                            .copy(alpha = if (passed) dim else 1f),
                        style = Stroke(
                            width = (if (seg.walk) 5.dp else 6.dp).toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = if (seg.walk) {
                                PathEffect.dashPathEffect(
                                    floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                                )
                            } else {
                                null
                            },
                        ),
                    )
                }

                dots.forEach { dot ->
                    val alpha = if (dot.leg < activeLeg) dim else 1f
                    val x = sxOf(dot.pt.second)
                    val y = syOf(dot.pt.first)
                    val r = dot.sizeDp.dp.toPx() / 2f
                    val ring = dot.ringDp.dp.toPx()
                    if (ring > 0f) {
                        drawCircle(
                            color = ringColor.copy(alpha = alpha),
                            radius = r,
                            center = Offset(x, y),
                        )
                    }
                    drawCircle(
                        color = dot.color.copy(alpha = alpha),
                        radius = (r - ring).coerceAtLeast(0.5f),
                        center = Offset(x, y),
                    )
                }

                // autobus na żywo: pełny znacznik, a gdy jest poza kadrem —
                // kropka przy krawędzi, żeby było wiadomo, z której strony nadjeżdża
                val shownBus = curBus?.let { b ->
                    val a = prevBus
                    if (a == null) {
                        b
                    } else {
                        (a.first + (b.first - a.first) * busMix.value) to
                            (a.second + (b.second - a.second) * busMix.value)
                    }
                }
                shownBus?.let { b ->
                    val bx = ((Merc.x(b.second) - cx) * world + halfW).toFloat()
                    val by = ((Merc.y(b.first) - cy) * world + halfH).toFloat()
                    val edge = 30.dp.toPx()
                    if (bx in -edge..(size.width + edge) && by in -edge..(size.height + edge)) {
                        drawBusMarker(Offset(bx, by), busHeading, busColor, ringColor)
                    } else {
                        val ex = bx.coerceIn(edge, size.width - edge)
                        val ey = by.coerceIn(edge, size.height - edge)
                        drawCircle(color = ringColor, radius = 9.dp.toPx(), center = Offset(ex, ey))
                        drawCircle(color = busColor, radius = 6.5.dp.toPx(), center = Offset(ex, ey))
                    }
                }

                // pozycja użytkownika: poświata, strzałka kierunku i kropka
                fix?.let { f ->
                    val px = ((Merc.x(f.second) - cx) * world + halfW).toFloat()
                    val py = ((Merc.y(f.first) - cy) * world + halfH).toFloat()
                    val p = Offset(px, py)
                    drawCircle(
                        color = scheme.primary.copy(alpha = 0.16f),
                        radius = 30.dp.toPx(),
                        center = p,
                    )
                    if (heading != null) {
                        rotate(degrees = heading, pivot = p) {
                            val arrow = Path()
                            arrow.moveTo(px, py - 34.dp.toPx())
                            arrow.lineTo(px - 13.dp.toPx(), py - 11.dp.toPx())
                            arrow.lineTo(px + 13.dp.toPx(), py - 11.dp.toPx())
                            arrow.close()
                            drawPath(arrow, color = scheme.primary)
                        }
                    }
                    drawCircle(color = ringColor, radius = 11.dp.toPx(), center = p)
                    drawCircle(color = scheme.primary, radius = 8.dp.toPx(), center = p)
                }
            }
        }

        // atrybucja kafelków
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp),
        ) {
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Znacznik autobusu: zaokrąglony kształt z szybą i kołami, obrócony w stronę
 * jazdy. Rysowany na kanwie, więc nie wymaga ikon ani dodatkowych bitmap.
 */
private fun DrawScope.drawBusMarker(
    at: Offset,
    heading: Float?,
    color: Color,
    ring: Color,
) {
    rotate(degrees = heading ?: 0f, pivot = at) {
        val w = 19.dp.toPx()
        val h = 25.dp.toPx()
        drawRoundRect(
            color = ring,
            topLeft = Offset(at.x - w / 2f, at.y - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        val bw = w - 4.dp.toPx()
        val bh = h - 4.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(at.x - bw / 2f, at.y - bh / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(4.5.dp.toPx()),
        )
        // przednia szyba i koła
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(at.x - bw / 2f + 3.dp.toPx(), at.y - bh / 2f + 3.dp.toPx()),
            size = Size(bw - 6.dp.toPx(), 3.5.dp.toPx()),
            cornerRadius = CornerRadius(1.2.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 1.6.dp.toPx(),
            center = Offset(at.x - bw / 4f, at.y + bh / 2f - 1.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 1.6.dp.toPx(),
            center = Offset(at.x + bw / 4f, at.y + bh / 2f - 1.dp.toPx()),
        )
    }
}
