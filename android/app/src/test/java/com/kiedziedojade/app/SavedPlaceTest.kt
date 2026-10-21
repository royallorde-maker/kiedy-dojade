package com.kiedziedojade.app

import com.kiedziedojade.app.core.CalendarData
import com.kiedziedojade.app.core.Meta
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.PlacesData
import com.kiedziedojade.app.core.SavedPlace
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.Street
import com.kiedziedojade.app.core.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved stops follow a stable id when the dataset reorders stops. */
class SavedPlaceTest {

    private fun stop(i: Int, id: String, name: String) = Stop(
        i = i, id = id, code = 100 + i, name = name,
        lat = 53.70 + i * 0.001, lng = 16.68 + i * 0.001,
        onDemand = false, station = false, onlyDisembarking = false,
    )

    private val world = World(
        stops = listOf(stop(0, "a", "A"), stop(1, "b", "B")),
        meta = Meta(
            generatedAt = "", source = "", city = "", timezone = "Europe/Warsaw",
            horizonFrom = "", horizonTo = "", patterns = emptyMap(),
            stopsCount = 2, tripsCount = 0,
        ),
        calendar = CalendarData("", emptyMap()),
        places = PlacesData(emptyList(), emptyList()),
        trips = emptyMap(),
    )

    @Test
    fun `saved stop resolves by id after reordering`() {
        val saved = SavedPlace("stop", "B", index = 0, stopId = "b")
        val ref = saved.toRef(world) as PlaceRef.StopRef
        assertEquals(1, ref.index)
        assertEquals("B", ref.name)
    }

    @Test
    fun `missing id does not fall back to a stale index`() {
        val saved = SavedPlace("stop", "B", index = 0, stopId = "gone")
        assertNull(saved.toRef(world))
    }

    @Test
    fun `an old save with an out of range index is discarded`() {
        assertNull(SavedPlace("stop", "B", index = 9).toRef(world))
    }

    @Test
    fun `old saves get an id and still match new saves`() {
        val old = SavedPlace("stop", "B", index = 1)
        val migrated = old.withStopId(world)
        assertEquals("b", migrated.stopId)
        assertTrue(migrated.samePlace(SavedPlace("stop", "B", index = 0, stopId = "b")))
    }

    @Test
    fun `a point picked on the map keeps its coordinates and kind`() {
        val saved = SavedPlace.from(PlaceRef.Gps("Punkt na mapie", 53.71, 16.69, custom = true))
        assertEquals("point", saved?.kind)
        val ref = saved?.toRef(world) as PlaceRef.Gps
        assertTrue(ref.custom)
        assertEquals(53.71, ref.lat, 0.0)
        assertEquals(16.69, ref.lng, 0.0)
    }

    @Test
    fun `a street can be remembered as a last place but not favourited`() {
        val street = PlaceRef.StreetRef("Rynek", listOf(53.70 to 16.69, 53.71 to 16.70))
        assertNull(SavedPlace.from(street))
        val saved = SavedPlace.forLast(street)!!
        assertEquals("street", saved.kind)
        val withStreet = world.copy(
            places = PlacesData(listOf(Street(street.name, street.pts)), emptyList()),
        )
        val ref = saved.toRef(withStreet) as PlaceRef.StreetRef
        assertEquals(street.name, ref.name)
        assertEquals(street.pts, ref.pts)
    }

    @Test
    fun `the same map point matches after switching the app language`() {
        val pl = SavedPlace("point", "Punkt na mapie", lat = 53.71, lng = 16.69)
        val en = SavedPlace("point", "Map point", lat = 53.71, lng = 16.69)
        assertTrue(pl.samePlace(en))
    }

    @Test
    fun `a phone location is still saved as gps`() {
        val saved = SavedPlace.from(PlaceRef.Gps("Moja lokalizacja", 53.70, 16.68))
        assertEquals("gps", saved?.kind)
        val ref = saved?.toRef(world) as PlaceRef.Gps
        assertTrue(!ref.custom)
    }
}
