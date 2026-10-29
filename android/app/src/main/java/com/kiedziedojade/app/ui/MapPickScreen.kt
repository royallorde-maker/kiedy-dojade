package com.kiedziedojade.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.MapStyle
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Wybór dowolnego punktu na mapie (nie tylko przystanku lub POI z danych).
 * Środek mapy wyznacza wybrane miejsce — użytkownik przesuwa mapę pod celownik.
 */
@Composable
fun MapPickScreen(
    title: String,
    initial: Pair<Double, Double>,
    dark: Boolean,
    mapStyle: MapStyle,
    onCancel: () -> Unit,
    onPick: (Double, Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    val nightFilter = remember { osmNightFilter() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                initOsmdroid(ctx)
                MapView(ctx).apply {
                    setTileSource(MapTiles.tileSource(mapStyle))
                    setMultiTouchControls(true)
                    setTilesScaledToDpi(true)
                    setBuiltInZoomControls(false)
                    minZoomLevel = 10.0
                    maxZoomLevel = MapTiles.MAX_ZOOM.toDouble()
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(initial.first, initial.second))
                    map = this
                    if (!mapReady) mapReady = true
                    onResume()
                }
            },
            update = { view ->
                map = view
                if (!mapReady) mapReady = true
            },
        )

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> map?.onResume()
                    Lifecycle.Event.ON_PAUSE -> map?.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                map?.onDetach()
            }
        }

        LaunchedEffect(mapStyle, mapReady) {
            if (!mapReady) return@LaunchedEffect
            val view = map ?: return@LaunchedEffect
            view.setTileSource(MapTiles.tileSource(mapStyle))
            view.overlayManager?.tilesOverlay?.setColorFilter(if (dark) nightFilter else null)
            view.invalidate()
        }

        LaunchedEffect(dark, mapStyle, mapReady) {
            if (!mapReady) return@LaunchedEffect
            map?.overlayManager?.tilesOverlay?.setColorFilter(if (dark) nightFilter else null)
            map?.invalidate()
        }

        // Celownik dokładnie w środku mapy.
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(58.dp),
        ) {
            val c = center
            val r = 10.dp.toPx()
            val tick = 5.dp.toPx()
            val tickOuter = 17.dp.toPx()
            val line = 2.dp.toPx()
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = r + 2.dp.toPx(),
                center = c + Offset(0f, 1.dp.toPx()),
            )
            drawCircle(color = Color.White, radius = r + 1.dp.toPx(), center = c)
            drawCircle(color = scheme.primary, radius = r, center = c)
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = c)
            for (dir in listOf(
                Offset(0f, -1f),
                Offset(0f, 1f),
                Offset(-1f, 0f),
                Offset(1f, 0f),
            )) {
                drawLine(
                    color = Color.White,
                    start = Offset(c.x + dir.x * (r + tick), c.y + dir.y * (r + tick)),
                    end = Offset(c.x + dir.x * (r + tickOuter), c.y + dir.y * (r + tickOuter)),
                    strokeWidth = line,
                )
                drawLine(
                    color = scheme.primary,
                    start = Offset(c.x + dir.x * (r + tick), c.y + dir.y * (r + tick)),
                    end = Offset(c.x + dir.x * (r + tickOuter), c.y + dir.y * (r + tickOuter)),
                    strokeWidth = line / 2f,
                )
            }
            // Delikatny pierścień ułatwia trafienie w punkt na jasnych kafelkach.
            drawCircle(
                color = Color.Black.copy(alpha = 0.2f),
                radius = tickOuter + 1.dp.toPx(),
                center = c,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.94f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = t("Anuluj", "Cancel"),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
        }

        MapZoomControls(
            onZoomIn = {
                map?.controller?.zoomIn()
                map?.invalidate()
            },
            onZoomOut = {
                map?.controller?.zoomOut()
                map?.invalidate()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        )

        GlassPane(
            shape = RoundedCornerShape(26.dp),
            fill = scheme.surfaceContainer.copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(22.dp)) {
                        drawCircle(
                            color = scheme.primary,
                            radius = size.minDimension / 2f,
                        )
                        drawCircle(
                            color = scheme.onPrimary,
                            radius = 3.dp.toPx(),
                            center = center,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Przesuń mapę pod celownik", "Move the map under the crosshair"),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            t(
                                "Możesz wybrać dowolny punkt, także poza przystankami.",
                                "Pick any point, including places away from stops.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(t("Anuluj", "Cancel"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            map?.mapCenter?.let { onPick(it.latitude, it.longitude) }
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        contentColor = KD_ON_HERO,
                    ) {
                        Text(
                            t("Wybierz ten punkt", "Choose this point"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(kdHeroBrush(), CircleShape)
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                }
                MapAttribution(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    style = mapStyle,
                )
            }
        }
    }
}
