package com.kiedziedojade.app

import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.Fix
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.RideStop
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.TrackPhase
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.trackingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wykrywanie fazy podróży i wsiadania na podstawie planu, GPS i kursu live. */
class TrackingTest {

    private val board = 53.7000 to 16.68407
    private val mid = 53.7000 to 16.69469
    private val alight = 53.7000 to 16.70000
    private val origin = 53.7000 to 16.68000

    private val boardStop = Stop(
        i = 0, id = "31830:1", code = 101, name = "A",
        lat = board.first, lng = board.second,
        onDemand = false, station = false, onlyDisembarking = false,
    )
    private val midStop = Stop(
        i = 1, id = "31830:2", code = 102, name = "M",
        lat = mid.first, lng = mid.second,
        onDemand = false, station = false, onlyDisembarking = false,
    )
    private val alightStop = Stop(
        i = 2, id = "31830:3", code = 103, name = "B",
        lat = alight.first, lng = alight.second,
        onDemand = false, station = false, onlyDisembarking = false,
    )

    private fun itinerary(withWalk: Boolean = true): Itinerary {
        val legs = ArrayList<com.kiedziedojade.app.core.Leg>()
        if (withWalk) {
            legs += WalkLeg(
                fromName = "Dom", toName = "A", meters = 420.0, minutes = 6,
                depMin = 598, arrMin = 604,
                fromPt = origin, toPt = board,
            )
        }
        legs += RideLeg(
            tripId = 7L, line = "2", dir = "Dworzec",
            boardStop = boardStop, alightStop = alightStop,
            depMin = 604, arrMin = 615,
            stops = listOf(
                RideStop(boardStop, 604),
                RideStop(midStop, 610),
                RideStop(alightStop, 615),
            ),
        )
        return Itinerary(
            id = "trip-1",
            legs = legs,
            leaveAt = 598,
            arriveAt = 615,
            transfers = 0,
            walkMeters = 420.0,
            walkMinutes = 6,
            firstRideLeg = if (withWalk) 1 else 0,
            pattern = "workday",
            tag = "best",
        )
    }

    private fun fix(pt: Pair<Double, Double>) = Fix(pt.first, pt.second)

    private fun exec(
        nextIndex: Int,
        atStop: Boolean = false,
        vehicle: Pair<Double, Double>? = null,
    ) = Api.TripExec(
        line = "2",
        direction = "Dworzec",
        times = listOf(
            Api.TripStopTime(0, boardStop.id, boardStop.name, 604),
            Api.TripStopTime(1, midStop.id, midStop.name, 610),
            Api.TripStopTime(2, alightStop.id, alightStop.name, 615),
        ),
        estimates = emptyList(),
        vehicle = vehicle,
        atStop = atStop,
        nextIndex = nextIndex,
        vehicleIndex = if (atStop) nextIndex - 1 else -1,
        canceledStops = emptySet(),
        estimated = true,
    )

    @Test
    fun `walking to the stop still shows the upcoming line`() {
        val s = trackingSnapshot(itinerary(), fix(origin), nowMin = 600)
        assertEquals(TrackPhase.WALKING, s.phase)
        assertEquals(1, s.rideLegIndex)
        assertEquals("2", s.line)
        assertEquals("A", s.walkTo)
        assertTrue(s.walkMeters!! > 0)
        assertFalse(s.onBus)
    }

    @Test
    fun `waiting at the board stop does not count as on board`() {
        val s = trackingSnapshot(itinerary(), fix(board), nowMin = 603)
        assertEquals(TrackPhase.WAITING, s.phase)
        assertEquals("2", s.line)
        assertEquals("A", s.boardStop)
        assertEquals(604, s.boardClock)
        assertFalse(s.onBus)
        assertTrue(s.waiting)
    }

    @Test
    fun `moving to a later stop confirms the ride`() {
        val s = trackingSnapshot(itinerary(), fix(mid), nowMin = 610, fromStep = 1)
        assertEquals(TrackPhase.ON_BUS, s.phase)
        assertTrue(s.onBus)
        assertEquals(1, s.stopsLeft)
        assertEquals("B", s.alightStop)
    }

    @Test
    fun `arriving at the alight stop finishes the trip`() {
        val s = trackingSnapshot(itinerary(), fix(alight), nowMin = 615, fromStep = 1)
        assertEquals(TrackPhase.FINISHED, s.phase)
        assertTrue(s.finished)
        assertFalse(s.onBus)
    }

    @Test
    fun `live vehicle leaving the board stop can confirm boarding`() {
        val justAfterBoard = 53.7000 to (16.68407 + 0.0012) // ~80 m
        val s = trackingSnapshot(
            itinerary = itinerary(),
            fix = fix(justAfterBoard),
            nowMin = 605,
            fromStep = 1,
            liveExec = exec(nextIndex = 2, vehicle = justAfterBoard),
        )
        assertTrue(s.live)
        assertFalse(s.busAtBoard)
        assertTrue(s.onBus)
        assertEquals(TrackPhase.ON_BUS, s.phase)
    }

    @Test
    fun `bus standing at the stop is waiting, not boarding`() {
        val s = trackingSnapshot(
            itinerary = itinerary(),
            fix = fix(board),
            nowMin = 603,
            fromStep = 1,
            liveExec = exec(nextIndex = 0, atStop = true, vehicle = board),
        )
        assertTrue(s.live)
        assertTrue(s.busAtBoard)
        assertTrue(s.waiting)
        assertFalse(s.onBus)
        assertEquals(TrackPhase.WAITING, s.phase)
    }

    @Test
    fun `arrival after a final walk uses the whole itinerary`() {
        val home = 53.6980 to 16.7050
        val withWalk = itinerary().let { it.copy(
            legs = it.legs + WalkLeg(
                fromName = "B", toName = "Cel", meters = 250.0, minutes = 4,
                depMin = 615, arrMin = 619, fromPt = alight, toPt = home,
            ),
            arriveAt = 619,
        ) }
        val s = trackingSnapshot(withWalk, fix(home), nowMin = 619, fromStep = 2)
        assertEquals(TrackPhase.FINISHED, s.phase)
        assertEquals(619, s.etaMin)
    }
}
