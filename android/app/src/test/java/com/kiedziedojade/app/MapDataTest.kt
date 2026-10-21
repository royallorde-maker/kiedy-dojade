package com.kiedziedojade.app

import com.kiedziedojade.app.core.DataJson
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.mapIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Helpers mapy na prawdziwych danych: lista linii, trasy, przystanki linii. */
class MapDataTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("data/$name")!!.bufferedReader().use { it.readText() }

    private val world: World = DataJson.world("test") { name -> resource(name) }
        ?: error("dataset unreadable")

    private val index = mapIndex(world)

    @Test
    fun `lines are non-empty and sorted naturally`() {
        assertTrue(index.lines.isNotEmpty())
        assertTrue(index.lines.all { it.isNotBlank() })
        val ints = index.lines.mapNotNull { it.toIntOrNull() }
        assertEquals("numeryczne linie w kolejności", ints.sorted(), ints)
    }

    @Test
    fun `stopsFor covers all stops and each line`() {
        assertEquals(world.stops.indices.toSet(), index.stopsFor(world, null))
        index.lines.forEach { line ->
            val stops = index.stopsFor(world, line)
            assertTrue("linia $line bez przystanków", stops.isNotEmpty())
            assertTrue("linia $line wskazuje nieistniejący przystanek", stops.all { it in world.stops.indices })
        }
    }

    @Test
    fun `every line has a route within the city bbox`() {
        index.lines.forEach { line ->
            val pts = index.route[line].orEmpty()
            assertTrue("linia $line bez trasy", pts.size >= 2)
            pts.forEach { s ->
                assertTrue(
                    "przystanek ${s.name} poza Szczecinkiem (${s.lat}, ${s.lng})",
                    s.lat in 53.60..53.82 && s.lng in 16.55..16.85,
                )
            }
        }
    }

    @Test
    fun `stop lines are consistent with line stops`() {
        index.lines.forEach { line ->
            index.lineStops.getValue(line).forEach { idx ->
                assertTrue(index.stopLines.getValue(idx).contains(line))
            }
        }
        val some = index.stopLines.keys.first()
        assertTrue(index.stopLines.getValue(some).isNotEmpty())
    }

    @Test
    fun `live probes stay on the route and are limited`() {
        assertTrue(index.probeStops(null).isEmpty())
        index.lines.forEach { line ->
            val route = index.route.getValue(line)
            val probes = index.probeStops(line)
            assertTrue("linia $line bez sond", probes.isNotEmpty())
            assertTrue("linia $line ma za dużo sond", probes.size <= 6)
            assertEquals("linia $line powtarza sondy", probes.size, probes.distinctBy { it.id }.size)
            assertTrue(
                "sonda poza trasą linii $line",
                probes.all { probe -> route.any { it.id == probe.id } },
            )
        }
    }

    @Test
    fun `live probes keep both ends of a long route`() {
        val line = index.lines.first { index.route.getValue(it).size > 6 }
        val route = index.route.getValue(line)
        val probes = index.probeStops(line)
        assertEquals(route.first().id, probes.first().id)
        assertEquals(route.last().id, probes.last().id)
    }
}
