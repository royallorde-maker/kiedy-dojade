package com.kiedziedojade.app

import com.kiedziedojade.app.core.Fix
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.NavStep
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.RideStop
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.headingDeg
import com.kiedziedojade.app.core.navDestination
import com.kiedziedojade.app.core.navDistance
import com.kiedziedojade.app.core.navState
import com.kiedziedojade.app.core.navSteps
import com.kiedziedojade.app.core.rideStopsLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prowadzenie: kroki trasy, postęp z GPS i moment wysiadania. */
class NavTest {

    // przystanki na jednej linii: board — 700 m — mid — 350 m — alight
    private val board = 53.7000 to 16.68407
    private val mid = 53.7000 to 16.69469
    private val alight = 53.7000 to 16.70000

    private fun ride(stops: List<Pair<Double, Double>> = listOf(board, mid, alight)) =
        NavStep.Ride(
            legIndex = 1,
            line = "2",
            direction = "Dworzec",
            boardName = "A",
            alightName = "B",
            departMin = 604,
            arriveMin = 615,
            stops = stops,
            target = stops.last(),
        )

    private fun walk(to: Pair<Double, Double>, meters: Double = 420.0) =
        NavStep.Walk(legIndex = 0, toName = "A", meters = meters, target = to)

    private fun fix(pt: Pair<Double, Double>) = Fix(pt.first, pt.second)

    @Test
    fun `walk distance comes from the live fix`() {
        val d = navDistance(walk(board), fix(board.first + 0.001 to board.second))!!
        assertTrue("d=$d", d in 105.0..117.0)
    }

    @Test
    fun `riding counts the stops left to the alight stop`() {
        val r = ride()
        assertEquals(2, rideStopsLeft(r, fix(board)))
        assertEquals(1, rideStopsLeft(r, fix(mid)))
        assertEquals(0, rideStopsLeft(r, fix(alight)))
    }

    @Test
    fun `walk to the stop is skipped when you are already there`() {
        val st = navState(listOf(walk(board), ride()), fix(board))
        assertEquals(1, st.stepIndex)
        assertEquals(2, st.stopsLeft)
        assertTrue(st.distanceToTarget == null || st.distanceToTarget!! <= 45.0)
    }

    @Test
    fun `without a fix the first step and the plan data stand`() {
        val st = navState(listOf(walk(board), ride()), null)
        assertEquals(0, st.stepIndex)
        assertNull(st.distanceToTarget)
        assertNull(st.stopsLeft)
        assertFalse(st.finished)
    }

    @Test
    fun `get off now at the alight stop and the ride ends there`() {
        val st = navState(listOf(ride()), fix(alight))
        assertTrue(st.getOffNow)
        assertTrue(st.finished)
        assertEquals(0, st.stopsLeft)
    }

    @Test
    fun `get off soon one stop before the alight stop`() {
        // 300 m przed wysiadką, najbliższy przystanek to jeszcze „mid”
        val near = 53.7000 to (16.70000 - 300.0 / 65900.0)
        val st = navState(listOf(ride()), fix(near))
        assertEquals(1, st.stopsLeft)
        assertTrue(st.getOffSoon)
        assertFalse(st.getOffNow)
    }

    @Test
    fun `walk to the destination finishes the trip`() {
        val dest = 53.6980 to 16.7050
        val st = navState(listOf(ride(), walk(dest, meters = 300.0)), fix(dest))
        assertTrue(st.finished)
    }

    @Test
    fun `heading from two positions points east`() {
        val h = headingDeg(53.70 to 16.69, 53.70 to 16.70)!!
        assertTrue("h=$h", h in 80f..100f)
    }

    @Test
    fun `heading needs some movement`() {
        assertNull(headingDeg(53.70 to 16.69, 53.70 to 16.69001))
    }

    @Test
    fun `navSteps follow the itinerary legs and know the destination`() {
        val a = Stop(
            i = 0, id = "31830:1", code = 101, name = "A",
            lat = board.first, lng = board.second,
            onDemand = false, station = false, onlyDisembarking = false,
        )
        val b = Stop(
            i = 1, id = "31830:2", code = 102, name = "B",
            lat = alight.first, lng = alight.second,
            onDemand = false, station = false, onlyDisembarking = false,
        )
        val it = Itinerary(
            id = "x",
            legs = listOf(
                WalkLeg(
                    fromName = "Dom", toName = "A", meters = 300.0, minutes = 4,
                    depMin = 600, arrMin = 604,
                    fromPt = 53.7110 to 16.6800, toPt = board,
                ),
                RideLeg(
                    tripId = 7L, line = "2", dir = "Dworzec",
                    boardStop = a, alightStop = b,
                    depMin = 604, arrMin = 615,
                    stops = listOf(RideStop(a, 604), RideStop(b, 615)),
                ),
            ),
            leaveAt = 600,
            arriveAt = 615,
            transfers = 0,
            walkMeters = 300.0,
            walkMinutes = 4,
            firstRideLeg = 1,
            pattern = "workday",
            tag = "best",
        )

        val steps = navSteps(it)
        assertEquals(2, steps.size)
        val w = steps[0] as NavStep.Walk
        assertEquals(300.0, w.meters, 0.001)
        assertEquals(board, w.target)
        val r = steps[1] as NavStep.Ride
        assertEquals("2", r.line)
        assertEquals("B", r.alightName)
        assertEquals(604, r.departMin)
        assertEquals(2, r.stops.size)
        assertEquals("B", navDestination(it))
    }
}
