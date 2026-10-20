package com.kiedziedojade.app

import com.kiedziedojade.app.core.PathSeg
import com.kiedziedojade.app.core.projectOnPath
import com.kiedziedojade.app.core.traveledCut
import com.kiedziedojade.app.core.trimTraveled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chowanie przebytej części trasy podczas prowadzenia. */
class RoutePathTest {

    // prosta linia na północ: ~111 m między punktami
    private val line = listOf(
        53.7000 to 16.6880,
        53.7010 to 16.6880,
        53.7020 to 16.6880,
    )

    private fun seg(pts: List<Pair<Double, Double>>, leg: Int) =
        PathSeg(pts, walk = true, leg = leg)

    @Test
    fun `minione nogi znikaja a przyszle zostaja`() {
        val segs = listOf(seg(line, 0), seg(line, 1), seg(line, 2))
        val out = trimTraveled(segs, activeLeg = 1, cut = null)
        assertEquals(listOf(1, 2), out.map { it.leg })
    }

    @Test
    fun `aktywna noga jest ucinana na przebytej odleglosci`() {
        val segs = listOf(seg(line, 0), seg(line, 1))
        val out = trimTraveled(segs, activeLeg = 1, cut = 111.0)
        assertEquals(1, out.size)
        val pts = out[0].pts
        assertEquals(3, pts.size)
        assertTrue(
            "pierwszy punkt powinien być przy ~53.701, był ${pts.first().first}",
            pts.first().first in 53.7009..53.7011,
        )
        assertEquals(53.7020, pts.last().first, 1e-9)
    }

    @Test
    fun `cut rowny zero zostawia cala aktywna noge`() {
        val segs = listOf(seg(line, 0), seg(line, 1))
        assertEquals(segs, trimTraveled(segs, activeLeg = 0, cut = 0.0))
    }

    @Test
    fun `cut liczy sie od poczatku aktywnej nogi`() {
        val a = seg(listOf(53.7000 to 16.6880, 53.7005 to 16.6880), 0)
        val b = seg(listOf(53.7005 to 16.6880, 53.7010 to 16.6880), 0)
        val out = trimTraveled(listOf(a, b), activeLeg = 0, cut = 60.0)
        assertEquals(1, out.size)
        assertEquals(2, out[0].pts.size)
        assertTrue(out[0].pts.first().first > 53.7005)
        assertEquals(53.7010, out[0].pts.last().first, 1e-9)
    }

    @Test
    fun `pozycja na trasie daje przebyty dystans`() {
        val segs = listOf(seg(line, 0))
        val cut = traveledCut(segs, 53.7010 to 16.6880, activeLeg = 0, marginM = 12.0)
        assertEquals(111.2 - 12.0, cut!!, 2.0)
    }

    @Test
    fun `pozycja daleko od trasy nie ucina jej`() {
        val segs = listOf(seg(line, 0))
        assertNull(traveledCut(segs, 53.7010 to 16.7000, activeLeg = 0))
    }

    @Test
    fun `brak pozycji nie ucina aktywnej nogi`() {
        val segs = listOf(seg(line, 0), seg(line, 1))
        assertNull(traveledCut(segs, null, activeLeg = 0))
        assertEquals(2, trimTraveled(segs, activeLeg = 0, cut = null).size)
    }

    @Test
    fun `rzut na trase zna odleglosc wzdluz i od trasy`() {
        val p = projectOnPath(line, 53.7005, 16.6882)!!
        assertEquals(55.6, p.along, 2.0)
        assertEquals(13.2, p.off, 3.0)
    }
}
