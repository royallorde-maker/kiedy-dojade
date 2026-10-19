package com.kiedziedojade.app

import com.kiedziedojade.app.core.Merc
import com.kiedziedojade.app.core.fitStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Miniatura trasy: projekcja i dopasowanie punktów do prostokąta. */
class StaticMapTest {

    @Test
    fun `mercator maps the world onto 0-1`() {
        assertEquals(0.0, Merc.x(-180.0), 1e-9)
        assertEquals(1.0, Merc.x(180.0), 1e-9)
        assertEquals(0.5, Merc.y(0.0), 1e-9)
        // północ ma mniejsze y (wyżej na kafelku)
        assertTrue(Merc.y(53.71) < Merc.y(53.67))
    }

    @Test
    fun `fit keeps the whole route inside the miniature`() {
        val pts = listOf(53.7103 to 16.6994, 53.673083 to 16.71878)
        val w = 900
        val h = 330
        val fit = fitStatic(pts, w, h, 58)
        assertTrue("zoom ${fit.zoom}", fit.zoom in 9..15)
        assertTrue("scale ${fit.scale}", fit.scale in 1f..1.6f)
        val xs = pts.map { fit.screenX(it.second, w) }
        val ys = pts.map { fit.screenY(it.first, h) }
        xs.forEach { assertTrue("x=$it", it >= 0f && it <= w) }
        ys.forEach { assertTrue("y=$it", it >= 0f && it <= h) }
    }

    @Test
    fun `fit centres the route in the miniature`() {
        val pts = listOf(53.70 to 16.69, 53.71 to 16.71)
        val w = 600
        val h = 400
        val fit = fitStatic(pts, w, h, 20)
        val xs = pts.map { fit.screenX(it.second, w) }
        val ys = pts.map { fit.screenY(it.first, h) }
        assertEquals(w / 2f, (xs[0] + xs[1]) / 2f, 1.5f)
        assertEquals(h / 2f, (ys[0] + ys[1]) / 2f, 1.5f)
    }

    @Test
    fun `short routes zoom in but not further than maxZoom`() {
        // dwa punkty ~150 m od siebie
        val pts = listOf(53.7103 to 16.6994, 53.7112 to 16.7005)
        val fit = fitStatic(pts, 900, 330, 58)
        assertEquals(15, fit.zoom)
        // trasa wypełnia miniaturę (skalowanie dobrane do brzegów)
        assertTrue("scale ${fit.scale}", fit.scale > 1f)
    }

    @Test
    fun `very long routes fall back to the minimum zoom`() {
        val pts = listOf(53.71 to 16.69, 52.0 to 21.0)
        val fit = fitStatic(pts, 900, 330, 58)
        assertEquals(9, fit.zoom)
    }

    @Test
    fun `no points means a sane default view`() {
        val fit = fitStatic(emptyList(), 900, 330, 58)
        assertEquals(15, fit.zoom)
        assertEquals(0.5, fit.cx, 1e-9)
        assertEquals(0.5, fit.cy, 1e-9)
    }
}
