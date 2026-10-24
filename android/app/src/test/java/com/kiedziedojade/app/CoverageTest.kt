package com.kiedziedojade.app

import com.kiedziedojade.app.core.DataJson
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.isOutsideCoverage
import com.kiedziedojade.app.core.nearestStopMeters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("data/$name")!!.bufferedReader().use { it.readText() }

    private val world: World = DataJson.world("test") { name -> resource(name) }
        ?: error("dataset unreadable")

    @Test
    fun `Szczecinek is inside the timetable coverage`() {
        val center = PlaceRef.Gps("Szczecinek", 53.710, 16.699)
        assertFalse(isOutsideCoverage(world, center))
    }

    @Test
    fun `Swidwin is outside the timetable coverage`() {
        val swidwin = PlaceRef.Gps("Świdwin", 53.774, 15.778)
        assertTrue(isOutsideCoverage(world, swidwin))
        val distance = nearestStopMeters(world, swidwin)
        assertNotNull(distance)
        assertTrue("expected > 25 km, got $distance", distance!! > 25_000.0)
    }

    @Test
    fun `a missing place is not reported as outside coverage`() {
        assertFalse(isOutsideCoverage(world, null))
    }
}
