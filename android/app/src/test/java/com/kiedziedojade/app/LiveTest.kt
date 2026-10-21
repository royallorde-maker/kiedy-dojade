package com.kiedziedojade.app

import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.RideStop
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.matchLiveRide
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live trip execution: alignment of estimates, countdowns and live matching. */
class LiveTest {

    private fun stop(i: Int, id: String, name: String) = Stop(
        i = i, id = id, code = 100 + i, name = name,
        lat = 53.70 + i * 0.001, lng = 16.68 + i * 0.001,
        onDemand = false, station = false, onlyDisembarking = false,
    )

    private fun json(
        next: Int,
        atStop: Boolean,
        vehicleIndex: Int,
        estimates: String,
        canceled: String = "[]",
    ) = JSONObject(
        """
        {
          "trip": {
            "line": "7",
            "direction": "DWORCOWA",
            "times": [
              {"index":0,"place_id":"a","stop_name":"A","departure_time":"12:00"},
              {"index":1,"place_id":"b","stop_name":"B","departure_time":"12:02"},
              {"index":2,"place_id":"c","stop_name":"C","departure_time":"12:04"},
              {"index":3,"place_id":"d","stop_name":"D","departure_time":"12:06"},
              {"index":4,"place_id":"e","stop_name":"E","departure_time":"12:09"}
            ]
          },
          "canceled": $canceled,
          "estimated": true,
          "at_stop": $atStop,
          "vehicle": {"lat": 53.71, "lon": 16.69},
          "next_departure_index": $next,
          "vehicle_trip_index": $vehicleIndex,
          "estimates": $estimates
        }
        """
    )

    @Test
    fun `estimates are aligned to the next stop while the bus is running`() {
        val ex = Api.parseTripExec(
            json(
                next = 2, atStop = false, vehicleIndex = 1,
                estimates = """
                    [
                      {"time_abs":null,"to_go":0,"arrival_abs":"12:04"},
                      {"time_abs":null,"to_go":2,"arrival_abs":"12:06"},
                      {"time_abs":null,"to_go":5,"arrival_abs":"12:09"}
                    ]
                """,
            )
        )
        assertEquals(2, ex.offset)
        assertEquals(0, ex.minutesTo(2, now = 720))
        assertEquals(2, ex.minutesTo(3, now = 720))
        assertEquals(5, ex.minutesTo(4, now = 720))
        assertEquals(12 * 60 + 9, ex.clockTo(4))
        assertEquals(3, ex.stopsTo(4))
        assertEquals(1, ex.stopsTo(2))
        assertTrue(ex.passed(1))
        assertFalse(ex.passed(2))
        assertEquals(53.71 to 16.69, ex.vehicle)
        assertEquals(4, ex.indexOfPlace("e"))
        assertNull(ex.indexOfPlace("zzz"))
    }

    @Test
    fun `standing at a stop counts it as reached`() {
        val ex = Api.parseTripExec(
            json(
                next = 2, atStop = true, vehicleIndex = 2,
                estimates = """
                    [
                      {"time_abs":null,"to_go":0,"arrival_abs":"12:04"},
                      {"time_abs":null,"to_go":2,"arrival_abs":"12:06"},
                      {"time_abs":null,"to_go":5,"arrival_abs":"12:09"}
                    ]
                """,
            )
        )
        assertEquals(0, ex.stopsTo(2))
        assertEquals(2, ex.stopsTo(4))
        assertTrue(ex.hasProgress)
    }

    @Test
    fun `before the run estimates start at zero and stops are unknown`() {
        val ex = Api.parseTripExec(
            json(
                next = 0, atStop = false, vehicleIndex = -1,
                estimates = """
                    [
                      {"time_abs":null,"to_go":5,"arrival_abs":"12:05"},
                      {"time_abs":null,"to_go":6,"arrival_abs":"12:07"},
                      {"time_abs":null,"to_go":7,"arrival_abs":"12:09"},
                      {"time_abs":null,"to_go":8,"arrival_abs":"12:11"},
                      {"time_abs":null,"to_go":9,"arrival_abs":"12:14"}
                    ]
                """,
            )
        )
        assertEquals(0, ex.offset)
        assertEquals(5, ex.minutesTo(0, now = 720))
        assertEquals(9, ex.minutesTo(4, now = 720))
        assertFalse(ex.hasProgress)
        assertNull(ex.stopsTo(4))
        assertEquals(12 * 60 + 4, ex.clockTo(2))
    }

    @Test
    fun `canceled stop indexes are parsed`() {
        val ex = Api.parseTripExec(
            json(
                next = 0, atStop = false, vehicleIndex = -1,
                estimates = "[]",
                canceled = "[2, 4]",
            )
        )
        assertEquals(setOf(2, 4), ex.canceledStops)
    }

    @Test
    fun `nested line name is read from trip execution`() {
        val ex = Api.parseTripExec(
            JSONObject(
                """
                {
                  "trip": {
                    "line": {"name": "1A"},
                    "direction": "PILSKA",
                    "times": [{"index":0,"place_id":"a","stop_name":"A","departure_time":"12:00"}]
                  },
                  "estimated": true
                }
                """
            )
        )
        assertEquals("1A", ex.line)
    }

    @Test
    fun `multi stop departures are flattened with directions`() {
        val rows = Api.parseLiveRows(
            JSONObject(
                """
                {
                  "status": "ok",
                  "departures": [
                    {"designator": 133, "rows": [
                      {"time":"2 min","static_time":"2 min","time_diff":1,"line_name":"7",
                       "direction_id":56491000,"trip_execution_id":"7.1:x:1","trip_index":4,
                       "at_stop":false,"canceled":false,"is_estimated":true}
                    ]},
                    {"designator": 251, "rows": [
                      {"time":"13:15","static_time":"13:15","time_diff":-1,"line_name":"1A",
                       "trip_execution_id":"1.2:x:2","trip_index":6,
                       "at_stop":true,"canceled":false,"is_estimated":false}
                    ]}
                  ],
                  "directions": {"56491000": "DWORCOWA"}
                }
                """
            )
        )
        assertEquals(2, rows.size)
        assertEquals("DWORCOWA", rows[0].direction)
        assertEquals(1, rows[0].delayMin)
        assertTrue(rows[1].atStop)
        assertEquals(-1, rows[1].delayMin)
        assertEquals("1.2:x:2", rows[1].tripExecutionId)
    }

    @Test
    fun `single stop departures still use the same parser`() {
        val rows = Api.parseLiveRows(
            JSONObject(
                """
                {
                  "departures": [
                    {"time":"3 min","static_time":"4 min","time_diff":-1,"line_name":"9",
                     "trip_execution_id":"9.1:x:1","trip_index":2,"at_stop":false,
                     "canceled":false,"is_estimated":true}
                  ]
                }
                """
            )
        )
        assertEquals(1, rows.size)
        assertEquals("9", rows[0].line)
        assertEquals(-1, rows[0].delayMin)
    }

    @Test
    fun `live candidates filter line, canceled, horizon and duplicate executions`() {
        val now = 720
        val rows = listOf(
            row(null, "1", 725).copy(tripExecutionId = "a"),
            row(null, "1", 730).copy(tripExecutionId = "a"),
            row(null, "1A", 725).copy(tripExecutionId = "b"),
            row(null, "1", 725).copy(tripExecutionId = "c", canceled = true),
            row(null, "1", 900).copy(tripExecutionId = "d"),
            row(null, "1", 725).copy(tripExecutionId = null),
            row(null, "1", 900).copy(tripExecutionId = "at-stop", atStop = true),
        )
        val out = Api.liveCandidates("1", rows, now)
        assertEquals(listOf("at-stop", "a"), out.map { it.tripExecutionId })
        assertEquals(725 * 60, out[1].departureSec)
    }

    @Test
    fun `live bus needs a started run, gps and a next stop`() {
        val row = row(null, "7", 600).copy(
            tripExecutionId = "exec",
            delayMin = 2,
            index = 2,
        )
        val ex = Api.TripExec(
            line = "7",
            direction = "DWORCOWA",
            times = (0..4).map {
                Api.TripStopTime(it, "p$it", "S$it", 600 + it * 2)
            },
            estimates = listOf(
                Api.TripEstimate(null, 0, 12 * 60 + 4),
                Api.TripEstimate(null, 2, 12 * 60 + 6),
                Api.TripEstimate(null, 5, 12 * 60 + 9),
            ),
            vehicle = 53.71 to 16.69,
            atStop = false,
            nextIndex = 2,
            vehicleIndex = 1,
            canceledStops = emptySet(),
            estimated = true,
        )
        val bus = Api.liveBus("7", row, ex, now = 720)!!
        assertEquals("exec", bus.executionId)
        assertEquals("S2", bus.nextStopName)
        assertEquals("p2", bus.nextStopPlaceId)
        assertEquals(0, bus.minutesToNext)
        assertEquals(12 * 60 + 4, bus.clockToNext)
        assertEquals(2, bus.delayMin)
        assertFalse(bus.atStop)

        assertNull(Api.liveBus("7", row, ex.copy(nextIndex = 0, vehicleIndex = -1), now = 720))
        assertNull(Api.liveBus("7", row, ex.copy(vehicle = null), now = 720))
        assertNull(Api.liveBus("7", row, ex.copy(nextIndex = 5), now = 720))
    }

    private fun ride(tripId: Long = 7L, depMin: Int = 600): RideLeg {
        val a = stop(0, "a", "A")
        val b = stop(1, "b", "B")
        return RideLeg(
            tripId = tripId, line = "7", dir = "DWORCOWA",
            boardStop = a, alightStop = b,
            depMin = depMin, arrMin = 615,
            stops = listOf(RideStop(a, depMin), RideStop(b, 615)),
        )
    }

    private fun row(
        tripId: Long?,
        line: String,
        schedMin: Int?,
        depMin: Int? = schedMin,
        direction: String? = "DWORCOWA",
    ) = Api.LiveRow(
        tripId = tripId,
        tripExecutionId = "x",
        line = line,
        direction = direction,
        departureSec = depMin?.times(60),
        scheduledSec = schedMin?.times(60),
        delayMin = 0,
        canceled = false,
        estimated = true,
        load = null,
        platform = null,
        index = 0,
    )

    @Test
    fun `live row prefers the same trip id`() {
        val rows = listOf(row(7, "9", 700), row(8, "7", 600))
        assertEquals(700, matchLiveRide(rows, ride())?.schedMin)
    }

    @Test
    fun `live row falls back to line and departure time`() {
        val rows = listOf(row(null, "9", 600), row(null, "7", 601))
        assertEquals(601, matchLiveRide(rows, ride())?.schedMin)
    }

    @Test
    fun `live row does not match a different departure`() {
        assertNull(matchLiveRide(listOf(row(null, "7", 630)), ride()))
    }

    @Test
    fun `live row prefers the matching direction over the opposite one`() {
        val rows = listOf(
            row(null, "7", 600, direction = "OSIEDLE"),
            row(null, "7", 601, direction = "dworcowa"),
        )
        assertEquals(601, matchLiveRide(rows, ride())?.schedMin)
    }

    @Test
    fun `live row ignores an opposite direction when no row matches`() {
        assertNull(matchLiveRide(listOf(row(null, "7", 600, direction = "OSIEDLE")), ride()))
    }

    @Test
    fun `live row falls back when the API leaves direction empty`() {
        val rows = listOf(
            row(null, "7", 600, direction = null),
            row(null, "7", 601, direction = "OSIEDLE"),
        )
        assertEquals(600, matchLiveRide(rows, ride())?.schedMin)
    }
}
