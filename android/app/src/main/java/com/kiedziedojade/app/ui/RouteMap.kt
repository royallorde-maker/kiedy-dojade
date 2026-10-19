package com.kiedziedojade.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.PathSeg
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
        it.rides.forEach { leg ->
            leg.stops.forEachIndexed { idx, rs ->
                val edge = idx == 0 || idx == leg.stops.size - 1
                out += RouteDot(
                    pt = rs.stop.lat to rs.stop.lng,
                    color = lineColor(leg.line),
                    sizeDp = if (edge) 9f else 6f,
                    ringDp = if (edge) 2f else 0f,
                )
            }
        }
        // start: początek marszu albo przystanek, z którego jedziemy
        val firstWalk = it.legs.firstOrNull() as? WalkLeg
        val firstPt = firstWalk?.fromPt ?: it.firstRide?.boardStop?.let { s -> s.lat to s.lng }
        firstPt?.let { p -> out += RouteDot(pt = p, color = startColor, sizeDp = 13f, ringDp = 2f) }
        // koniec: cel marszu albo ostatni przystanek
        val lastWalk = it.legs.lastOrNull() as? WalkLeg
        val lastPt = lastWalk?.toPt ?: it.lastRide?.alightStop?.let { s -> s.lat to s.lng }
        lastPt?.let { p -> out += RouteDot(pt = p, color = endColor, sizeDp = 13f, ringDp = 2f) }
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
