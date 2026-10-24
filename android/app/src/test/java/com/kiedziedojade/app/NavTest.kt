package com.kiedziedojade.app

import com.kiedziedojade.app.core.Fix
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.NavStep
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.RideStop
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.WARSAW
import com.kiedziedojade.app.core.courseHeading
import com.kiedziedojade.app.core.headingDeg
import com.kiedziedojade.app.core.headingDelta
import com.kiedziedojade.app.core.markerHeading
import com.kiedziedojade.app.core.navDestination
import com.kiedziedojade.app.core.navDistance
import com.kiedziedojade.app.core.navFixPlausible
import com.kiedziedojade.app.core.navState
import com.kiedziedojade.app.core.navSteps
import com.kiedziedojade.app.core.normalizeHeading
import com.kiedziedojade.app.core.rideNextStopIndex
import com.kiedziedojade.app.core.rideStopsLeft
import com.kiedziedojade.app.core.screenCompassHeading
import com.kiedziedojade.app.core.trackingExpired
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
    fun `the next planned stop follows the closest one`() {
        val r = ride()
        assertEquals(1, rideNextStopIndex(r, fix(board)))
        assertEquals(2, rideNextStopIndex(r, fix(mid)))
        assertNull(rideNextStopIndex(r, fix(alight)))
        assertNull(rideNextStopIndex(r, null))
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
        val st = navState(
            listOf(ride(), walk(dest, meters = 300.0)),
            fix(dest),
            fromIndex = 1,
        )
        assertTrue(st.finished)
    }

    @Test
    fun `bus passing the final destination does not end the ride`() {
        val dest = 53.7020 to 16.7040
        val st = navState(listOf(ride(), walk(dest, meters = 300.0)), fix(dest))
        assertEquals(0, st.stepIndex)
        assertFalse(st.finished)
        assertFalse(st.getOffNow)
    }

    @Test
    fun `right after the alight stop the walk step still says get off now`() {
        val dest = 53.6980 to 16.7050
        val st = navState(listOf(ride(), walk(dest, meters = 300.0)), fix(alight))
        assertEquals(1, st.stepIndex)
        assertTrue(st.getOffNow)
        assertFalse(st.finished)
    }

    @Test
    fun `abandoned tracking expires three hours after a late arrival`() {
        val started = warsaw(21, 0)
        assertFalse(trackingExpired(started, 22 * 60 + 50, warsaw(1, 49, day = 22)))
        assertTrue(trackingExpired(started, 22 * 60 + 50, warsaw(1, 51, day = 22)))
    }

    @Test
    fun `abandoned tracking handles a trip crossing midnight`() {
        val started = warsaw(23, 30)
        assertFalse(trackingExpired(started, 30, warsaw(3, 29, day = 22)))
        assertTrue(trackingExpired(started, 30, warsaw(3, 31, day = 22)))
    }

    private fun warsaw(hour: Int, minute: Int, day: Int = 21): Long =
        java.time.ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, WARSAW)
            .toInstant()
            .toEpochMilli()

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
    fun `gps course is used while moving`() {
        val moving = Fix(
            lat = 53.70, lng = 16.69,
            bearing = 90f, bearingAccuracy = 8f,
            speed = 4.5f, timeMs = 2_000L,
        )
        assertEquals(90f, courseHeading(moving, null)!!, 0.01f)
    }

    @Test
    fun `stationary gps does not produce a random course`() {
        val still = Fix(
            lat = 53.70, lng = 16.69,
            bearing = 90f, speed = 0.1f, timeMs = 2_000L,
        )
        assertNull(courseHeading(still, Fix(53.70, 16.69, timeMs = 1_000L)))
    }

    @Test
    fun `course can fall back to displacement without gps bearing`() {
        val previous = Fix(53.70, 16.69, speed = 1.4f, timeMs = 1_000L)
        val next = Fix(53.70, 16.70, speed = 1.4f, timeMs = 2_000L)
        val h = courseHeading(next, previous)!!
        assertTrue("h=$h", h in 80f..100f)
    }

    @Test
    fun `marker uses compass at rest and keeps the last course as fallback`() {
        assertEquals(270f, markerHeading(null, 270f, 90f)!!, 0.01f)
        assertEquals(45f, markerHeading(45f, 270f, 90f)!!, 0.01f)
        assertEquals(90f, markerHeading(null, null, 90f)!!, 0.01f)
        assertNull(markerHeading(null, null, null))
    }

    @Test
    fun `heading smoothing takes the shortest way around north`() {
        assertEquals(20f, headingDelta(350f, 10f), 0.01f)
        assertEquals(-20f, headingDelta(10f, 350f), 0.01f)
        assertEquals(350f, normalizeHeading(-10f), 0.01f)
    }

    @Test
    fun `compass heading follows screen rotation`() {
        assertEquals(0f, screenCompassHeading(270f, 1), 0.01f)
        assertEquals(0f, screenCompassHeading(90f, 3), 0.01f)
        assertEquals(180f, screenCompassHeading(180f, 0), 0.01f)
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

    private fun guardItinerary(): Itinerary {
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
        return Itinerary(
            id = "guard",
            legs = listOf(
                RideLeg(
                    tripId = 7L, line = "2", dir = "Dworzec",
                    boardStop = a, alightStop = b,
                    depMin = 604, arrMin = 615,
                    stops = listOf(RideStop(a, 604), RideStop(b, 615)),
                ),
            ),
            leaveAt = 604,
            arriveAt = 615,
            transfers = 0,
            walkMeters = 0.0,
            walkMinutes = 0,
            firstRideLeg = 0,
            pattern = "workday",
            tag = "best",
        )
    }

    @Test
    fun `a fix far from the planned journey is ignored`() {
        // Warszawa, ~400 km od Szczecinka — taki fix dawał 11 419 km marszu.
        assertFalse(navFixPlausible(guardItinerary(), fix(52.2297 to 21.0122)))
    }

    @Test
    fun `a fix at the journey is used for navigation`() {
        val it = guardItinerary()
        assertTrue(navFixPlausible(it, fix(board)))
        assertTrue(navFixPlausible(it, fix(alight)))
    }
}
