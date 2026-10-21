package com.kiedziedojade.app

import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.ui.JourneyQuery
import com.kiedziedojade.app.ui.Route
import com.kiedziedojade.app.ui.RouteStateCodec
import org.junit.Assert.assertEquals
import org.junit.Test

/** Obrot ekranu nie może gubić wyników ani otwartego przystanku. */
class RouteStateCodecTest {

    @Test
    fun `round trips the whole search stack`() {
        val routes = listOf(
            Route.Plan,
            Route.Results(
                JourneyQuery(
                    from = PlaceRef.StopRef("Dworzec", 12, "stop-12"),
                    to = PlaceRef.Gps("Punkt na mapie", 53.7, 16.69, custom = true),
                    serviceDate = "2026-10-21",
                    departMin = 615,
                ),
            ),
            Route.Stop(12, "2026-10-22"),
        )
        assertEquals(routes, RouteStateCodec.decode(RouteStateCodec.encode(routes)))
    }

    @Test
    fun `round trips pois and streets`() {
        val routes = listOf(
            Route.Plan,
            Route.Results(
                JourneyQuery(
                    from = PlaceRef.PoiRef("Szkoła", "school", 53.71, 16.70),
                    to = PlaceRef.StreetRef(
                        "Rynek",
                        listOf(53.70 to 16.69, 53.701 to 16.691),
                    ),
                    serviceDate = "2026-10-21",
                    departMin = 0,
                ),
            ),
        )
        assertEquals(routes, RouteStateCodec.decode(RouteStateCodec.encode(routes)))
    }

    @Test
    fun `corrupt state falls back to plan`() {
        assertEquals(listOf(Route.Plan), RouteStateCodec.decode("{"))
    }
}
