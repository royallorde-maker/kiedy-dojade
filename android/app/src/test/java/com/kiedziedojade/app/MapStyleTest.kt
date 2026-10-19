package com.kiedziedojade.app

import com.kiedziedojade.app.core.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {

    @Test
    fun `unknown or missing style falls back to the clean default`() {
        assertEquals(MapStyle.STANDARD, MapStyle.from(null))
        assertEquals(MapStyle.STANDARD, MapStyle.from(""))
        assertEquals(MapStyle.STANDARD, MapStyle.from("old-unknown-style"))
    }

    @Test
    fun `saved style ids round-trip`() {
        MapStyle.entries.forEach { style ->
            assertEquals(style, MapStyle.from(style.id))
        }
    }

    @Test
    fun `styles have unique ids and tile endpoints`() {
        assertEquals(MapStyle.entries.size, MapStyle.entries.map { it.id }.distinct().size)
        assertEquals(MapStyle.entries.size, MapStyle.entries.map { it.tileBaseUrl }.distinct().size)
        MapStyle.entries.forEach { style ->
            assertTrue(style.tileBaseUrl.startsWith("https://"))
            assertTrue(style.tileBaseUrl.endsWith("/"))
            assertTrue(style.attribution.contains("OpenStreetMap"))
        }
    }
}
