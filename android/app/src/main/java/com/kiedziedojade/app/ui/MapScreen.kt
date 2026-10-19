package com.kiedziedojade.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiedziedojade.app.core.MapIndex
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.haversine
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.mapIndex
import com.kiedziedojade.app.core.walkMinutes
import com.kiedziedojade.app.update.rememberIsOnline
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** Trzyma obiekty osmdroid między rekompozycjami (mapa żyje dłużej niż jeden frame). */
private class MapHolder {
    var map: MapView? = null
    val stopMarkers = mutableListOf<Marker>()
    var userMarker: Marker? = null
    var lineOverlay: Polyline? = null
}

/**
 * Kropka przystanku — własny Drawable, bo GradientDrawable nie ma
 * rozmiaru intrinsic, a Marker go wymaga.
 */
private class DotDrawable(
    private val fill: Int,
    private val ring: Int,
    private val ringPx: Int,
    private val sizePx: Int,
) : Drawable() {
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    private val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = bounds.width() / 2f
        if (ringPx > 0) {
            ringPaint.color = ring
            ringPaint.strokeWidth = ringPx.toFloat()
            canvas.drawCircle(cx, cy, r - ringPx / 2f, ringPaint)
        }
        fillPaint.color = fill
        canvas.drawCircle(cx, cy, (r - ringPx).coerceAtLeast(0f), fillPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}

/**
 * Mapa przystanków (kafelki OpenStreetMap przez osmdroid — bez klucza API).
 * Filtr linii rysuje trasę i zawęża przystanki; dotknięcie przystanku pokazuje
 * kartę z odjazdami/planowaniem. Mapa wymaga internetu, rozkłady nie.
 */
@Composable
fun MapScreen(
    world: World,
    dark: Boolean,
    requester: LocationRequester,
    onOpenBoard: (Int) -> Unit,
    onPlanTo: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val index: MapIndex = remember(world) { mapIndex(world) }
    val holder = remember { MapHolder() }
    var line by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var userFix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    val online = rememberIsOnline()

    // osmdroid: cache w plikach aplikacji (bez uprawnień do pamięci) + własny UA.
    remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        true
    }

    val center = remember(world) {
        GeoPoint(
            world.stops.map { it.lat }.average(),
            world.stops.map { it.lng }.average(),
        )
    }

    // nocny filtr kafelków — mapa pasuje do ciemnych motywów
    val nightFilter = remember {
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -0.92f, 0f, 0f, 0f, 235f,
                    0f, -0.92f, 0f, 0f, 235f,
                    0f, 0f, -0.92f, 0f, 235f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    val fill = scheme.primary.toArgb()
    val fillSelected = scheme.tertiary.toArgb()
    val ring = scheme.onPrimary.toArgb()
    val ringPx = with(density) { 2.dp.roundToPx() }
    val dotNormal = with(density) { 13.dp.roundToPx() }
    val dotSelected = with(density) { 19.dp.roundToPx() }
    val userPx = with(density) { 16.dp.roundToPx() }
    val userFill = scheme.tertiary.toArgb()

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setTilesScaledToDpi(true)
                    setBuiltInZoomControls(false) // pinch/2×tap wystarczą; białe +/- psują wygląd
                    minZoomLevel = 10.0
                    maxZoomLevel = 19.0
                    controller.setZoom(13.5)
                    controller.setCenter(center)
                    holder.map = this
                    if (!mapReady) mapReady = true
                    onResume()
                }
            },
            // Widok powstaje już PO pierwszej kompozycji — efekty poniżej muszą
            // wiedzieć, kiedy mapa istnieje, inaczej nie ma do czego dodawać.
            update = { map ->
                holder.map = map
                if (!mapReady) mapReady = true
            },
        )

        DisposableEffect(Unit) {
            onDispose { holder.map?.onDetach() }
        }

        // jasny/ciemny filtr kafelków wg motywu
        LaunchedEffect(dark, mapReady) {
            if (!mapReady) return@LaunchedEffect
            holder.map?.overlayManager?.tilesOverlay?.setColorFilter(if (dark) nightFilter else null)
            holder.map?.invalidate()
        }

        // znaczniki przystanków (przebudowa przy zmianie filtra/selection/kolorów)
        val shown = remember(index, line, world) { index.stopsFor(world, line) }
        LaunchedEffect(shown, selected, fill, fillSelected, ring, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            holder.stopMarkers.forEach { map.overlays.remove(it) }
            holder.stopMarkers.clear()
            shown.forEach { idx ->
                val stop = world.stops.getOrNull(idx) ?: return@forEach
                val isSel = idx == selected
                val marker = Marker(map).apply {
                    position = GeoPoint(stop.lat, stop.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = DotDrawable(
                        fill = if (isSel) fillSelected else fill,
                        ring = ring,
                        ringPx = ringPx,
                        sizePx = if (isSel) dotSelected else dotNormal,
                    )
                    title = stop.name
                    relatedObject = idx
                    setOnMarkerClickListener { _, _ ->
                        selected = idx
                        true
                    }
                }
                holder.stopMarkers += marker
                map.overlays.add(marker)
            }
            map.invalidate()
        }

        // trasa wybranej linii (polilinia po przystankach najdłuższego kursu)
        LaunchedEffect(line, dark, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            holder.lineOverlay?.let { map.overlays.remove(it) }
            holder.lineOverlay = null
            val l = line ?: return@LaunchedEffect
            val pts = index.route[l].orEmpty()
            if (pts.size < 2) return@LaunchedEffect
            val geo = pts.map { GeoPoint(it.lat, it.lng) }
            val poly = Polyline(map).apply {
                setPoints(geo)
                outlinePaint.color = lineColor(l).toArgb()
                outlinePaint.strokeWidth = with(density) { 6.dp.toPx() }
                outlinePaint.isAntiAlias = true
            }
            holder.lineOverlay = poly
            map.overlays.add(0, poly) // pod znacznikami — kropki zostają czytelne
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), true, with(density) { 56.dp.roundToPx() })
            map.invalidate()
        }

        // pozycja użytkownika
        LaunchedEffect(userFix, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            val fix = userFix ?: return@LaunchedEffect
            val marker = holder.userMarker?.also {
                it.icon = DotDrawable(userFill, ring, ringPx, userPx)
            } ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = DotDrawable(userFill, ring, ringPx, userPx)
                title = t("Moja lokalizacja", "My location")
                holder.userMarker = this
                map.overlays.add(this)
            }
            marker.position = GeoPoint(fix.first, fix.second)
            map.controller.animateTo(marker.position, 15.5, 500L)
            map.invalidate()
        }

        // pasek filtrów linii + akcje
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        MapChip(
                            label = t("Wszystkie", "All"),
                            selected = line == null,
                            color = scheme.primary,
                            onClick = { line = null },
                        )
                    }
                    items(index.lines, key = { "l$it" }) { l ->
                        MapChip(
                            label = l,
                            selected = line == l,
                            color = lineColor(l),
                            onClick = { line = if (line == l) null else l },
                        )
                    }
                }
                SmallFloatingActionButton(
                    modifier = Modifier.padding(end = 12.dp),
                    onClick = {
                        requester.request { fix ->
                            if (fix != null) {
                                userFix = fix
                            } else {
                                Toast.makeText(
                                    context,
                                    t("Nie udało się ustalić lokalizacji", "Could not get the location"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = t("Moja lokalizacja", "My location"))
                }
            }
            if (!online) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainer.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    Text(
                        t("Mapa wymaga internetu — rozkłady działają offline", "Map needs internet — schedules work offline"),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // atrybucja kafelków
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        ) {
            Text(
                "© OpenStreetMap",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // karta wybranego przystanku
        val sel = selected?.takeIf { it in world.stops.indices }
        if (sel != null) {
            val stop = world.stops[sel]
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surfaceContainer,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 32.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val (first, second) = stop.nameParts
                            Text(
                                first,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (second != null) {
                                Text(
                                    second,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { selected = null }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = t("Zamknij", "Close"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    val stopLines = index.stopLines[sel].orEmpty()
                    if (stopLines.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            stopLines.take(6).forEach { LineChip(it) }
                            if (stopLines.size > 6) {
                                Text(
                                    "+${stopLines.size - 6}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusChip(t("Przystanek ", "Stop ") + stop.code)
                        if (stop.onDemand) StatusChip(t("na żądanie", "on demand"))
                        if (stop.station) StatusChip(t("dworzec", "station"))
                    }
                    userFix?.let { fix ->
                        Spacer(Modifier.height(8.dp))
                        val meters = haversine(fix.first, fix.second, stop.lat, stop.lng)
                        val dist = fmtMeters(meters)
                        val walk = walkMinutes(meters)
                        WalkRow(t("$dist · $walk min pieszo", "$dist · $walk min walk"))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onOpenBoard(sel) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.DirectionsBus, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Tablica", "Board"))
                        }
                        FilledTonalButton(
                            onClick = { onPlanTo(sel) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Zaplanuj", "Plan"))
                        }
                    }
                }
            }
        }
    }
}

/** Pigułka filtra linii — wypełniona kolorem linii, gdy wybrana. */
@Composable
private fun MapChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color else scheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = if (selected) {
            if (color.luminance() > 0.55f) Color(0xFF11151C) else Color.White
        } else {
            scheme.onSurfaceVariant
        },
        shadowElevation = 2.dp,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
