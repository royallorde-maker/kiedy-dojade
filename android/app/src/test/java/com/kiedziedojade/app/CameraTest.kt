package com.kiedziedojade.app

import com.kiedziedojade.app.core.autoNavZoom
import com.kiedziedojade.app.core.cornerAheadMeters
import com.kiedziedojade.app.core.turnAngleDeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kamera prowadzenia: zakręt przed nami i zoom w stylu nawigacji. */
class CameraTest {

    private val a = 53.7000 to 16.6800
    private val b = 53.7000 to 16.6810 // ~66 m na wschód
    private val c = 53.7010 to 16.6810 // ~111 m na północ

    @Test
    fun `turn angle is 90 degrees at a right angle`() {
        assertEquals(90.0, turnAngleDeg(a, b, c), 1.0)
    }

    @Test
    fun `straight line has no corner`() {
        val straight = listOf(a, b, 53.7000 to 16.6820)
        assertNull(cornerAheadMeters(straight, a))
    }

    @Test
    fun `finds the next corner and zooms in before it`() {
        val corner = cornerAheadMeters(listOf(a, b, c), 53.7000 to 16.6802)
        assertTrue("corner=$corner", corner != null && corner in 50.0..80.0)
        val zoom = autoNavZoom(16f, corner)
        assertTrue("zoom=$zoom", zoom > 16.5f && zoom <= 17.8f)
    }

    @Test
    fun `ignores a corner far beyond the look-ahead window`() {
        val farX = 53.7000 to 16.6820 // ~132 m na wschód
        val farY = 53.7010 to 16.6820
        assertNull(cornerAheadMeters(listOf(a, farX, farY), 53.7000 to 16.6801))
    }
}
