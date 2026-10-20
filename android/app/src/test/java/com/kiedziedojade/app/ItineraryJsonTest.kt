package com.kiedziedojade.app

import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.ItineraryJson
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.RideStop
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.WalkLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItineraryJsonTest {

    private val a = Stop(0, "31830:1", 101, "A", 53.70, 16.68, false, true, false)
    private val b = Stop(1, "31830:2", 102, "B", 53.71, 16.70, false, false, true)

    private val itinerary = Itinerary(
        id = "plan-1",
        legs = listOf(
            WalkLeg(
                fromName = "Dom", toName = "A", meters = 420.0, minutes = 6,
                depMin = 598, arrMin = 604,
                fromPt = 53.699 to 16.679, toPt = 53.70 to 16.68,
            ),
            RideLeg(
                tripId = 7L, line = "2", dir = "Dworzec",
                boardStop = a, alightStop = b,
                depMin = 604, arrMin = 615,
                stops = listOf(RideStop(a, 604), RideStop(b, 615)),
            ),
        ),
        leaveAt = 598,
        arriveAt = 615,
        transfers = 0,
        walkMeters = 420.0,
        walkMinutes = 6,
        firstRideLeg = 1,
        pattern = "workday",
        tag = "best",
    )

    @Test
    fun `active itinerary survives a JSON round trip`() {
        val decoded = ItineraryJson.decode(ItineraryJson.encode(itinerary))
        assertEquals(itinerary, decoded)
    }

    @Test
    fun `missing or broken JSON is ignored`() {
        assertNull(ItineraryJson.decode(null))
        assertNull(ItineraryJson.decode(""))
        assertNull(ItineraryJson.decode("not json"))
    }
}
