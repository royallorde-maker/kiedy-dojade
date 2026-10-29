package com.kiedziedojade.app.ui

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.withFrameNanos
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.DriveRoute
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Locator
import com.kiedziedojade.app.core.MapIndex
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.haversine
import com.kiedziedojade.app.core.mapIndex
import com.kiedziedojade.app.core.walkMinutes
import com.kiedziedojade.app.update.rememberIsOnline
import kotlinx.coroutines.delay
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Trzyma obiekty osmdroid między rekompozycjami (mapa żyje dłużej niż jeden frame). */
private class MapHolder {
    var map: MapView? = null
    val stopMarkers = mutableListOf<Marker>()
    val busMarkers = mutableListOf<Marker>()
    var userMarker: Marker? = null
    var lineOverlay: Polyline? = null
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
    mapStyle: MapStyle = MapStyle.STANDARD,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val index: MapIndex = remember(world) { mapIndex(world) }
    val holder = remember { MapHolder() }
    val locator = remember { Locator(context) }
    var line by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedBusId by rememberSaveable { mutableStateOf<String?>(null) }
    var buses by remember(line) { mutableStateOf<List<Api.LiveBus>>(emptyList()) }
    var busesLoaded by remember(line) { mutableStateOf(false) }
    var busesError by remember(line) { mutableStateOf(false) }
    var userFix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var canLocate by remember { mutableStateOf(locator.hasPermission()) }
    var mapReady by remember { mutableStateOf(false) }
    val online = rememberIsOnline()
    val stopIndexById = remember(world) {
        world.stops.withIndex().associate { (i, stop) -> stop.id to i }
    }

    // osmdroid: cache w plikach aplikacji (bez uprawnień do pamięci) + własny UA.
    remember(context) {
        initOsmdroid(context)
        true
    }

    val center = remember(world) {
        // pusta lista przystanków (uszkodzone dane) nie może rozwalić mapy
        val s = world.stops
        GeoPoint(
            if (s.isEmpty()) 53.710 else s.map { it.lat }.average(),
            if (s.isEmpty()) 16.699 else s.map { it.lng }.average(),
        )
    }

    // nocny/dzienny filtr kafelków — mapa pasuje do motywu
    val nightFilter = remember { osmNightFilter() }
    val dayFilter = remember { osmDayFilter() }

    val fill = scheme.primary.toArgb()
    val fillSelected = scheme.tertiary.toArgb()
    val ring = scheme.onPrimary.toArgb()
    val ringPx = with(density) { 2.dp.roundToPx() }
    val dotNormal = with(density) { 11.dp.roundToPx() }
    val dotSelected = with(density) { 17.dp.roundToPx() }
    val userPx = with(density) { 16.dp.roundToPx() }
    val userFill = scheme.tertiary.toArgb()
    val selectedBusRing = scheme.tertiary.toArgb()
    val busPx = with(density) { 32.dp.roundToPx() }
    val busSelectedPx = with(density) { 40.dp.roundToPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(MapTiles.tileSource(mapStyle))
                    setMultiTouchControls(true)
                    setTilesScaledToDpi(true)
                    setBuiltInZoomControls(false) // pinch/2×tap i własne +/−
                    minZoomLevel = 10.0
                    maxZoomLevel = MapTiles.MAX_ZOOM.toDouble()
                    // 14 zamiast 13,5 — mniej kropek naraz, miasto nadal w kadrze.
                    controller.setZoom(14.0)
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

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> holder.map?.onResume()
                    Lifecycle.Event.ON_PAUSE -> holder.map?.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                holder.map?.onDetach()
            }
        }

        // Żywa kropka pozycji, ale rzadziej niż w nawigacji i bez ruszania kamery —
        // użytkownik może swobodnie rozglądać się po mapie.
        LaunchedEffect(canLocate, lifecycleOwner) {
            if (!canLocate) return@LaunchedEffect
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                locator.stream(minTimeMs = 4_000L, minDistanceM = 8f).collect { f ->
                    val age = System.currentTimeMillis() - f.timeMs
                    if (f.timeMs <= 0L || age < 120_000L) {
                        userFix = f.lat to f.lng
                    }
                }
            }
        }

        // zmiana stylu kafelków działa od razu, bez ponownego wejścia na mapę
        LaunchedEffect(mapStyle, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            map.setTileSource(MapTiles.tileSource(mapStyle))
            map.overlayManager?.tilesOverlay?.setColorFilter(if (dark) nightFilter else dayFilter)
            map.invalidate()
        }

        // jasny/ciemny filtr kafelków wg motywu
        LaunchedEffect(dark, mapStyle, mapReady) {
            if (!mapReady) return@LaunchedEffect
            holder.map?.overlayManager?.tilesOverlay?.setColorFilter(if (dark) nightFilter else dayFilter)
            holder.map?.invalidate()
        }

        // znaczniki przystanków (przebudowa przy zmianie filtra/selection/kolorów)
        val shown = remember(index, line, world) { index.stopsFor(world, line) }
        LaunchedEffect(shown) {
            if (selected != null && selected !in shown) selected = null
        }
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
                    infoWindow = null
                    relatedObject = idx
                    setOnMarkerClickListener { _, _ ->
                        selected = idx
                        selectedBusId = null
                        true
                    }
                }
                holder.stopMarkers += marker
                map.overlays.add(marker)
            }
            map.invalidate()
        }

        // trasa wybranej linii (najdłuższy kurs); najpierw linia po przystankach,
        // potem geometria po ulicach z OSRM, gdy router odpowie
        LaunchedEffect(line, dark, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            holder.lineOverlay?.let { map.overlays.remove(it) }
            holder.lineOverlay = null
            val l = line ?: return@LaunchedEffect
            val pts = index.route[l].orEmpty()
            if (pts.size < 2) return@LaunchedEffect
            val straight = pts.map { GeoPoint(it.lat, it.lng) }
            val poly = Polyline(map).apply {
                setPoints(straight)
                outlinePaint.color = lineColor(l).toArgb()
                outlinePaint.strokeWidth = with(density) { 6.dp.toPx() }
                outlinePaint.isAntiAlias = true
            }
            holder.lineOverlay = poly
            map.overlays.add(0, poly) // pod znacznikami — kropki zostają czytelne
            map.zoomToBoundingBox(
                BoundingBox.fromGeoPoints(straight),
                true,
                with(density) { 56.dp.roundToPx() },
            )
            map.invalidate()

            val routed = DriveRoute.path(pts.map { it.lat to it.lng })
                ?.map { GeoPoint(it.first, it.second) }
                ?: return@LaunchedEffect
            if (holder.lineOverlay === poly && line == l) {
                poly.setPoints(routed)
                map.invalidate()
            }
        }

        // Pozycja użytkownika. Marker jedzie płynnie do nowej ramki GPS bez
        // rekomponowania całej mapy przy każdej klatce — animujemy wprost
        // pozycję nakładki osmdroid.
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
                infoWindow = null
                position = GeoPoint(fix.first, fix.second)
                holder.userMarker = this
                map.overlays.add(this)
            }

            val start = marker.position
            val startLat = start?.latitude
            val startLng = start?.longitude
            if (startLat == null || startLng == null) {
                marker.position = GeoPoint(fix.first, fix.second)
            } else if (startLat != fix.first || startLng != fix.second) {
                val startedAt = withFrameNanos { it }
                var frameAt = startedAt
                while (true) {
                    val t = (
                        (frameAt - startedAt) /
                            (LOCATION_ANIM_MS * 1_000_000.0)
                        ).coerceIn(0.0, 1.0)
                    val e = t * t * (3.0 - 2.0 * t)
                    marker.position = GeoPoint(
                        startLat + (fix.first - startLat) * e,
                        startLng + (fix.second - startLng) * e,
                    )
                    map.invalidate()
                    if (t >= 1.0) break
                    frameAt = withFrameNanos { it }
                }
            }
            marker.position = GeoPoint(fix.first, fix.second)
            map.invalidate()
        }

        // Autobusy na żywo wybranej linii. API nie ma listy pojazdów, więc
        // pytamy kilka przystanków wzdłuż trasy i pobieramy kursy, które już
        // ruszyły. Odświeżanie zatrzymuje się, gdy ekran jest w tle.
        LaunchedEffect(line, online, lifecycleOwner, index) {
            val l = line
            if (l == null) {
                buses = emptyList()
                busesLoaded = false
                busesError = false
                return@LaunchedEffect
            }
            buses = emptyList()
            busesLoaded = false
            busesError = false
            if (!online) {
                busesLoaded = true
                return@LaunchedEffect
            }
            val probes = index.probeStops(l).map { it.id }
            if (probes.isEmpty()) {
                busesLoaded = true
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    val result = runCatching { Api.liveBuses(l, probes) }.getOrNull()
                    // chwilowy błąd sieci nie usuwa ostatnio znanych autobusów
                    if (result != null) buses = result
                    busesError = result == null
                    busesLoaded = true
                    delay(
                        when {
                            result == null -> 30_000L
                            result.isEmpty() -> 45_000L
                            else -> 20_000L
                        },
                    )
                }
            }
        }

        LaunchedEffect(buses, selectedBusId) {
            val id = selectedBusId ?: return@LaunchedEffect
            if (buses.none { it.executionId == id }) selectedBusId = null
        }

        LaunchedEffect(
            buses, selectedBusId, line, mapReady,
            ring, ringPx, selectedBusRing, busPx, busSelectedPx,
        ) {
            if (!mapReady) return@LaunchedEffect
            val map = holder.map ?: return@LaunchedEffect
            holder.busMarkers.forEach { map.overlays.remove(it) }
            holder.busMarkers.clear()
            val l = line
            if (l != null) {
                val color = lineColor(l).toArgb()
                buses.forEach { bus ->
                    val isSelected = bus.executionId == selectedBusId
                    val marker = Marker(map).apply {
                        position = GeoPoint(bus.vehicle.first, bus.vehicle.second)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BusDrawable(
                            fill = color,
                            ring = if (isSelected) selectedBusRing else ring,
                            ringPx = ringPx,
                            sizePx = if (isSelected) busSelectedPx else busPx,
                            label = bus.line,
                        )
                        title = t("Autobus linii ", "Line ") + bus.line
                        snippet = bus.nextStopName
                        infoWindow = null
                        relatedObject = bus.executionId
                        setOnMarkerClickListener { _, _ ->
                            selectedBusId = bus.executionId
                            selected = null
                            true
                        }
                    }
                    holder.busMarkers += marker
                    map.overlays.add(marker)
                }
            }
            map.invalidate()
        }

        // pasek filtrów linii + akcje
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassPane(
                    shape = RoundedCornerShape(24.dp),
                    fill = scheme.surfaceContainer.copy(alpha = 0.62f),
                    modifier = Modifier.weight(1f),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item(key = "all") {
                            MapChip(
                                label = t("Wszystkie", "All"),
                                selected = line == null,
                                color = scheme.primary,
                                onClick = {
                                    line = null
                                    selectedBusId = null
                                },
                            )
                        }
                        items(index.lines, key = { "l$it" }) { l ->
                            MapChip(
                                label = l,
                                selected = line == l,
                                color = lineColor(l),
                                onClick = {
                                    line = if (line == l) null else l
                                    selectedBusId = null
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = {
                        requester.request { fix ->
                            if (fix != null) {
                                userFix = fix
                                canLocate = true
                                holder.map?.controller?.animateTo(
                                    GeoPoint(fix.first, fix.second),
                                    15.5,
                                    500L,
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    requester.errorText(),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    shape = CircleShape,
                    color = scheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = t("Moja lokalizacja", "My location"),
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            MapAttribution(
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                style = mapStyle,
            )
            if (line != null && online) {
                val liveText = when {
                    !busesLoaded -> t("Szukam autobusów…", "Finding live buses…")
                    busesError && buses.isEmpty() ->
                        t("Nie udało się pobrać autobusów", "Couldn't load live buses")
                    buses.isEmpty() -> t("Brak autobusów na żywo", "No live buses")
                    else -> "${buses.size} ${I18n.rides(buses.size)} " +
                        t("na żywo", "live")
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainer.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                ) {
                    Text(
                        liveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (buses.isEmpty() && busesLoaded) {
                            scheme.onSurfaceVariant
                        } else {
                            scheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (!online) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainer.copy(alpha = 0.92f),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
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

        MapZoomControls(
            onZoomIn = { holder.map?.controller?.zoomIn() },
            onZoomOut = { holder.map?.controller?.zoomOut() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        // karta wybranego autobusu albo przystanku
        val selectedBus = selectedBusId?.let { id ->
            buses.firstOrNull { it.executionId == id }
        }
        val sel = selected?.takeIf { it in world.stops.indices }
        if (selectedBus != null) {
            LiveBusCard(
                bus = selectedBus,
                nextStopIndex = selectedBus.nextStopPlaceId?.let { stopIndexById[it] },
                onOpenBoard = onOpenBoard,
                onClose = { selectedBusId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                    .fillMaxWidth(),
            )
        } else if (sel != null) {
            val stop = world.stops[sel]
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            val sub = buildList {
                                second?.let { add(it) }
                                add(t("Przystanek ", "Stop ") + stop.code)
                                if (stop.onDemand) add(t("na żądanie", "on demand"))
                                if (stop.station) add(t("dworzec", "station"))
                            }.joinToString("  ·  ")
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            stopLines.take(4).forEach { LineChip(it) }
                            if (stopLines.size > 4) {
                                Text(
                                    "+${stopLines.size - 4}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    userFix?.let { fix ->
                        Spacer(Modifier.height(6.dp))
                        val meters = haversine(fix.first, fix.second, stop.lat, stop.lng)
                        val dist = fmtMeters(meters)
                        val walk = walkMinutes(meters)
                        WalkRow(t("$dist · $walk min pieszo", "$dist · $walk min walk"))
                    }
                    Spacer(Modifier.height(10.dp))
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
        color = if (selected) color else scheme.surfaceContainerLowest,
        contentColor = if (selected) {
            if (color.luminance() > 0.55f) Color(0xFF11151C) else Color.White
        } else {
            scheme.onSurfaceVariant
        },
        shadowElevation = 0.dp,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Karta po dotknięciu autobusu: kierunek, następny przystanek i ETA. */
@Composable
private fun LiveBusCard(
    bus: Api.LiveBus,
    nextStopIndex: Int?,
    onOpenBoard: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineChip(bus.line, big = true)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t("Autobus na żywo", "Live bus"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        bus.direction ?: t("Kierunek nieznany", "Direction unknown"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = t("Zamknij", "Close"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DelayChip(bus.delayMin, bus.estimated, canceled = false)
                if (bus.atStop) {
                    StatusChip(
                        t("na przystanku", "at the stop"),
                        scheme.tertiary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (bus.atStop) {
                    t("Na przystanku", "At the stop")
                } else {
                    t("Następny przystanek", "Next stop")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                bus.nextStopName ?: t("Nie znam następnego przystanku", "Next stop unknown"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val eta = buildList {
                bus.minutesToNext?.let { min ->
                    add(if (min <= 0) t("teraz", "now") else t("za $min min", "in $min min"))
                }
                bus.clockToNext?.let { add(fmtTime(it)) }
            }.joinToString("  ·  ")
            if (eta.isNotEmpty()) {
                Text(
                    eta,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { nextStopIndex?.let(onOpenBoard) },
                enabled = nextStopIndex != null,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DirectionsBus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Tablica przystanku", "Stop board"))
            }
        }
    }
}
