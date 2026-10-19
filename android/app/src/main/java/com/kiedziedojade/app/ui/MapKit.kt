package com.kiedziedojade.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.BuildConfig
import com.kiedziedojade.app.core.I18n.t
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import java.io.File

/**
 * Wspólne kawałki map (osmdroid i mapa kafelkowa w Canvas): styl kafelków,
 * cache, filtr nocny, atrybucja i sterowanie zoomem. Korzysta z tego ekran
 * mapy, miniatury tras i prowadzenie.
 */

/** Styl mapy: Humanitarian (HOT) — jaśniejszy i spokojniejszy od standardowego OSM. */
object MapTiles {
    const val STYLE = "hot"
    const val MAX_ZOOM = 19
    const val MIN_ZOOM = 2
    const val ATTRIBUTION = "© OpenStreetMap · HOT / OSM France"

    /** OSM wymaga kontaktu w User-Agencie, nie samej nazwy pakietu. */
    val USER_AGENT: String
        get() = "KiedyDojade/${BuildConfig.VERSION_NAME} (+${BuildConfig.REPO_URL})"

    fun tileUrl(z: Int, x: Int, y: Int): String =
        "https://a.tile.openstreetmap.fr/$STYLE/$z/$x/$y.png"

    fun tileSource(): XYTileSource = XYTileSource(
        "KiedyDojade-$STYLE",
        MIN_ZOOM,
        MAX_ZOOM,
        256,
        ".png",
        arrayOf("https://a.tile.openstreetmap.fr/$STYLE/"),
    )
}

/** osmdroid: cache w plikach aplikacji (bez uprawnień do pamięci) + własny UA. */
fun initOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = MapTiles.USER_AGENT
        osmdroidBasePath = File(context.filesDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
        // Kafelki potrafią urosnąć do setek MB na dłuższych trasach.
        tileFileSystemCacheMaxBytes = 64L * 1024 * 1024
        tileFileSystemCacheTrimBytes = 48L * 1024 * 1024
    }
}

/** Nocny filtr kafelków — mapa pasuje do ciemnych motywów. */
fun osmNightFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -0.92f, 0f, 0f, 0f, 235f,
            0f, -0.92f, 0f, 0f, 235f,
            0f, 0f, -0.92f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Atrybucja kafelków. Kliknięcie otwiera stronę licencji OSM. */
@Composable
fun MapAttribution(
    modifier: Modifier = Modifier,
    clickable: Boolean = true,
) {
    val uri = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = scheme.surfaceContainer.copy(alpha = 0.86f),
        modifier = modifier.then(
            if (clickable) {
                Modifier.clickable {
                    runCatching { uri.openUri("https://www.openstreetmap.org/copyright") }
                }
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            MapTiles.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Pionowe sterowanie mapą: zoom +/− oraz opcjonalne wrócenie do pozycji.
 * Działa zarówno na MapView (osmdroid), jak i na mapie rysowanej w Canvas.
 */
@Composable
fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    onRecenter: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainer.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = t("Przybliż", "Zoom in"),
                    tint = scheme.onSurface,
                    modifier = Modifier.size(21.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(1.dp)
                    .background(scheme.outlineVariant),
            )
            IconButton(onClick = onZoomOut, modifier = Modifier.size(42.dp)) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = t("Oddal", "Zoom out"),
                    tint = scheme.onSurface,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (onRecenter != null) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(scheme.outlineVariant),
                )
                IconButton(onClick = onRecenter, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = t("Wróć do pozycji", "Recenter"),
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Kropka przystanku — własny Drawable, bo GradientDrawable nie ma
 * rozmiaru intrinsic, a Marker go wymaga.
 */
internal class DotDrawable(
    private val fill: Int,
    private val ring: Int,
    private val ringPx: Int,
    private val sizePx: Int,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
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
