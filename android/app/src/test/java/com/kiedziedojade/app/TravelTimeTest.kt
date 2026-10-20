package com.kiedziedojade.app

import com.kiedziedojade.app.core.WARSAW
import com.kiedziedojade.app.core.resolveDeparture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

/** Date-less travel time: the clock time decides today or tomorrow. */
class TravelTimeTest {

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 10, 19, hour, minute, 0, 0, WARSAW)

    @Test
    fun `now uses today and the current minute`() {
        val d = resolveDeparture(at(7, 34), null)
        assertEquals("2026-10-19", d.serviceDate)
        assertEquals(7 * 60 + 34, d.departMin)
    }

    @Test
    fun `a later clock time stays today`() {
        val d = resolveDeparture(at(7, 34), 8 * 60 + 15)
        assertEquals("2026-10-19", d.serviceDate)
        assertEquals(8 * 60 + 15, d.departMin)
    }

    @Test
    fun `a clock time that already passed means tomorrow`() {
        val d = resolveDeparture(at(7, 34), 7 * 60)
        assertEquals("2026-10-20", d.serviceDate)
        assertEquals(7 * 60, d.departMin)
    }

    @Test
    fun `the same minute as now is still today`() {
        val d = resolveDeparture(at(7, 34), 7 * 60 + 34)
        assertEquals("2026-10-19", d.serviceDate)
    }

    @Test
    fun `midnight is handled at both ends of the day`() {
        val late = resolveDeparture(at(23, 59), 23 * 60 + 59)
        assertEquals("2026-10-19", late.serviceDate)
        assertEquals(23 * 60 + 59, late.departMin)

        val early = resolveDeparture(at(0, 0), 0)
        assertEquals("2026-10-19", early.serviceDate)
        assertEquals(0, early.departMin)

        val previous = resolveDeparture(at(0, 1), 0)
        assertEquals("2026-10-20", previous.serviceDate)
    }

    @Test
    fun `out of range values are clamped to a real clock time`() {
        assertEquals(0, resolveDeparture(at(12, 0), -10).departMin)
        assertEquals(1439, resolveDeparture(at(12, 0), 2000).departMin)
    }
}
