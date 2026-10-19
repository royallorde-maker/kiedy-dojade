package com.kiedziedojade.app

import com.kiedziedojade.app.core.DataJson
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.PlanInput
import com.kiedziedojade.app.core.Planner
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.buildIndex
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.haversine
import com.kiedziedojade.app.core.itinerarySegments
import com.kiedziedojade.app.core.norm
import com.kiedziedojade.app.core.searchPlaces
import com.kiedziedojade.app.core.walkMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Planner + data tests against the REAL harvested dataset (test resources are a
 * copy of public/data). These run on the JVM in CI — no device needed.
 */
class PlannerTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("data/$name")!!.bufferedReader().use { it.readText() }

    private val world: World = DataJson.world("test") { name -> resource(name) }
        ?: error("dataset unreadable")

    private fun stopIndex(query: String): Int {
        val index = buildIndex(world)
        val item = searchPlaces(index, query, limit = 8).firstOrNull { it.kind == "stop" }
        assertNotNull("no stop matches \"$query\"", item)
        return (item!!.ref as PlaceRef.StopRef).index
    }

    private fun workdayDate(): String {
        val entry = world.calendar.days.entries.firstOrNull {
            world.meta.patterns[it.value]?.type == "workday"
        }
        assertNotNull("no workday in the calendar", entry)
        return entry!!.key
    }

    private fun sundayDate(): String? = world.calendar.days.entries
        .firstOrNull { world.meta.patterns[it.value]?.type == "sun" }?.key

    @Test
    fun `dataset parses and is complete`() {
        assertTrue(world.stops.size > 80)
        assertTrue(world.meta.patterns.size >= 3)
        for ((pid, trips) in world.trips) {
            assertTrue("pattern $pid has no trips", trips.isNotEmpty())
            for (t in trips.take(50)) {
                assertTrue("trip ${t.id} has <2 stops", t.times.size >= 2)
                var prev = -1
                for ((_, min) in t.times) {
                    assertTrue("trip ${t.id} times not ascending", min >= prev)
                    prev = min
                }
            }
        }
    }

    @Test
    fun `workday plan finds connections with bounded transfers`() {
        val from = stopIndex("Dworzec PKP")
        val to = stopIndex("Szpital")
        val outcome = Planner.plan(
            world,
            PlanInput(
                origin = PlaceRef.StopRef("Dworzec PKP", from),
                dest = PlaceRef.StopRef("Szpital", to),
                date = workdayDate(),
                departMin = 7 * 60 + 30,
            ),
        )
        assertTrue("no itineraries", outcome.itineraries.isNotEmpty())
        assertTrue("exact day expected", outcome.exactDay)
        val best = outcome.itineraries.first()
        assertEquals("best", best.tag)
        assertTrue("arrives after leaving", best.arriveAt > best.leaveAt)
        assertTrue("transfers <= 2", best.transfers <= 2)
        assertTrue("has at least one ride", best.rides.isNotEmpty())
        // every ride leg is time-consistent
        for (r in best.rides) {
            assertTrue(r.arrMin > r.depMin)
            assertTrue(r.stops.size >= 2)
        }
        // sorted by arrival
        val arrivals = outcome.itineraries.map { it.arriveAt }
        assertEquals(arrivals.sorted(), arrivals)
    }

    @Test
    fun `planning from gps coordinates works`() {
        val stop = world.stops[stopIndex("Dworzec PKP")]
        val outcome = Planner.plan(
            world,
            PlanInput(
                origin = PlaceRef.Gps("GPS", stop.lat + 0.0012, stop.lng + 0.0012),
                dest = PlaceRef.StopRef("Szpital", stopIndex("Szpital")),
                date = workdayDate(),
                departMin = 12 * 60,
            ),
        )
        assertTrue(outcome.itineraries.isNotEmpty())
        val firstLeg = outcome.itineraries.first().legs.first()
        assertTrue("walk to a stop starts the trip", firstLeg is com.kiedziedojade.app.core.WalkLeg)
    }

    @Test
    fun `walk legs carry points and segments cover the ride`() {
        val stop = world.stops[stopIndex("Dworzec PKP")]
        val gps = PlaceRef.Gps("GPS", stop.lat + 0.0012, stop.lng + 0.0012)
        val outcome = Planner.plan(
            world,
            PlanInput(
                origin = gps,
                dest = PlaceRef.StopRef("Szpital", stopIndex("Szpital")),
                date = workdayDate(),
                departMin = 12 * 60,
            ),
        )
        assertTrue(outcome.itineraries.isNotEmpty())
        val best = outcome.itineraries.first()

        val walk = best.legs.first() as com.kiedziedojade.app.core.WalkLeg
        assertNotNull("walk needs a start point", walk.fromPt)
        assertNotNull("walk needs an end point", walk.toPt)
        assertEquals(stop.lat + 0.0012, walk.fromPt!!.first, 1e-6)
        // marsz kończy się na przystanku, z którego odjeżdża autobus
        assertEquals(best.firstRide!!.boardStop.lat, walk.toPt!!.first, 1e-9)

        val segs = itinerarySegments(best)
        assertTrue("segments expected", segs.isNotEmpty())
        assertTrue("walk is an open dashed line", segs.first().walk && segs.first().pts.size >= 2)
        assertTrue(
            "ride segments are solid and coloured by line",
            segs.any { !it.walk && it.line == best.firstRide!!.line && it.pts.size >= 2 },
        )
        // trasa jest ciągła: koniec odcinka = początek następnego
        for (k in 1 until segs.size) {
            assertEquals(segs[k - 1].pts.last(), segs[k].pts.first())
        }
    }

    @Test
    fun `walk-only estimate appears for short distances`() {
        val a = world.stops[stopIndex("Dworzec PKP")]
        val b = world.stops[stopIndex("Szpital")]
        val straight = haversine(a.lat, a.lng, b.lat, b.lng)
        val outcome = Planner.plan(
            world,
            PlanInput(
                origin = PlaceRef.Gps("A", a.lat, a.lng),
                dest = PlaceRef.Gps("B", b.lat, b.lng),
                date = workdayDate(),
                departMin = 9 * 60,
            ),
        )
        if (straight <= 3000) {
            assertNotNull(outcome.walkOnly)
            assertEquals(walkMinutes(straight), outcome.walkOnly!!.minutes)
        }
    }

    @Test
    fun `outside the horizon falls back by weekday and says so`() {
        val beyond = LocalDate.parse(world.meta.horizonTo).plusDays(60).toString()
        val (pid, exact, type) = Planner.resolvePattern(world, beyond)
        assertTrue(pid.isNotEmpty())
        assertFalse("must not pretend the day is exact", exact)
        assertTrue(type.isNotEmpty())
    }

    @Test
    fun `sunday schedule resolves for a sunday date`() {
        val sunday = sundayDate()
        if (sunday != null) {
            val (_, exact, type) = Planner.resolvePattern(world, sunday)
            assertTrue(exact)
            assertEquals("sun", type)
        }
    }

    @Test
    fun `search hits stops by partial name without diacritics`() {
        val index = buildIndex(world)
        val results = searchPlaces(index, "sikorskiego", limit = 10)
        assertTrue(results.any { it.kind == "stop" && it.name.contains("Sikorskiego") })
        val withDiacritics = searchPlaces(index, "żółkiewskiego", limit = 10)
        val plain = searchPlaces(index, "zolkiewskiego", limit = 10)
        assertEquals(
            withDiacritics.map { it.id }.toSet(),
            plain.map { it.id }.toSet(),
        )
    }

    @Test
    fun `norm strips polish diacritics and punctuation`() {
        assertEquals("zolc", norm("Żółć!"))
        // myślniki i ukośniki zostają (dzielą dopiero tokenizer wyszukiwania)
        assertEquals("1-go maja/ muzeum - nz", norm("1-go Maja/ Muzeum - NŻ"))
    }

    @Test
    fun `walk minutes are sane`() {
        assertEquals(1, walkMinutes(0.0))
        assertEquals(1, walkMinutes(20.0))
        assertTrue(walkMinutes(390.0) in 6..8)
    }

    @Test
    fun `time formatting wraps past midnight`() {
        assertEquals("00:05", fmtTime(1445))
        assertEquals("07:30", fmtTime(450))
        assertEquals("23:59", fmtTime(1439))
    }

    @Test
    fun `polish plurals follow the language rules`() {
        I18n.lang = com.kiedziedojade.app.core.Lang.PL
        assertEquals("1 przystanek", "1 " + I18n.stops(1))
        assertEquals("2 przystanki", "2 " + I18n.stops(2))
        assertEquals("5 przystanków", "5 " + I18n.stops(5))
        assertEquals("12 przystanków", "12 " + I18n.stops(12))
        assertEquals("22 przystanki", "22 " + I18n.stops(22))
    }

    @Test
    fun `trips appear in every pattern at every hour window`() {
        // sanity: each pattern has morning, midday and evening trips
        for ((pid, trips) in world.trips) {
            val firsts = trips.map { it.times.first().second }
            assertTrue("$pid: no morning trips", firsts.any { it < 10 * 60 })
            assertTrue("$pid: no evening trips", firsts.any { it > 16 * 60 })
        }
    }
}
