package com.kiedziedojade.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Wspólne kawałki map (osmdroid): konfiguracja kafelków, filtr nocny i kropka
 * znacznika. Korzysta z tego ekran mapy i miniatury tras w wynikach.
 */

/** osmdroid: cache w plikach aplikacji (bez uprawnień do pamięci) + własny UA. */
fun initOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.filesDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
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
