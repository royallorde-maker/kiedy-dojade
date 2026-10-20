package com.kiedziedojade.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** A place the user can get back to (favourites / recents). */
data class SavedPlace(
    /** "stop" | "poi" | "gps" */
    val kind: String,
    val name: String,
    val index: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val pkind: String? = null,
    /** stabilny identyfikator przystanku — indeks może się zmienić po aktualizacji danych */
    val stopId: String? = null,
) {
    fun toRef(world: World? = null): PlaceRef? = when (kind) {
        "stop" -> {
            val resolved = if (stopId != null) {
                // a known id must still exist; never silently fall back to a
                // possibly outdated index from another dataset version
                world?.stops?.indexOfFirst { it.id == stopId }?.takeIf { it >= 0 }
            } else {
                index?.takeIf { world == null || it in world.stops.indices }
            }
            resolved?.let { PlaceRef.StopRef(name, it) }
        }

        "poi" -> if (lat != null && lng != null) {
            PlaceRef.PoiRef(name, pkind ?: "", lat, lng)
        } else null

        "gps" -> if (lat != null && lng != null) PlaceRef.Gps(name, lat, lng) else null
        else -> null
    }

    /** Fill a missing stable id from the current dataset (migration of old saves). */
    fun withStopId(world: World): SavedPlace {
        if (kind != "stop" || stopId != null) return this
        val id = world.stops.getOrNull(index ?: -1)?.id ?: return this
        return copy(stopId = id)
    }

    fun samePlace(other: SavedPlace): Boolean {
        if (kind != other.kind) return false
        if (kind == "stop") {
            val a = stopId
            val b = other.stopId
            if (a != null && b != null) return a == b
            return index == other.index && name == other.name
        }
        return name == other.name && lat == other.lat && lng == other.lng
    }

    fun key(): String = "$kind|${stopId ?: index ?: ""}|$name|${lat ?: ""}|${lng ?: ""}"

    companion object {
        fun from(ref: PlaceRef, world: World? = null): SavedPlace? = when (ref) {
            is PlaceRef.StopRef -> SavedPlace(
                "stop",
                ref.name,
                index = ref.index,
                stopId = world?.stops?.getOrNull(ref.index)?.id,
            )

            is PlaceRef.PoiRef -> SavedPlace("poi", ref.name, lat = ref.lat, lng = ref.lng, pkind = ref.kind)
            is PlaceRef.Gps -> SavedPlace("gps", ref.name, lat = ref.lat, lng = ref.lng)
            is PlaceRef.StreetRef -> null // ulice nie są zapisywane jako „ulubione”
        }
    }
}

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("kd_settings", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString("theme", v).apply()

    var darkOverride: Boolean
        get() = sp.getBoolean("dark_override", false)
        set(v) = sp.edit().putBoolean("dark_override", v).apply()

    /** id stylu kafelków mapy; nieznany/brak = domyślny OpenStreetMap */
    var mapStyle: String
        get() = sp.getString("map_style", "standard") ?: "standard"
        set(v) = sp.edit().putString("map_style", v).apply()

    var lang: String
        get() = sp.getString("lang", "pl") ?: "pl"
        set(v) = sp.edit().putString("lang", v).apply()

    /** index of the stop shown on the "Tablica" tab; -1 = none */
    var boardStop: Int
        get() = sp.getInt("board_stop", -1)
        set(v) = sp.edit().putInt("board_stop", v).apply()

    /** stable id of the board stop; survives dataset refreshes that reorder stops */
    var boardStopId: String?
        get() = sp.getString("board_stop_id", null)
        set(v) = sp.edit().putString("board_stop_id", v).apply()

    /** ostatnio używane „skąd” / „dokąd” — wracają po restarcie */
    var lastFrom: SavedPlace?
        get() = decodeOne(sp.getString("last_from", null))
        set(v) = sp.edit().putString("last_from", v?.let { encodeOne(it) }).apply()

    var lastTo: SavedPlace?
        get() = decodeOne(sp.getString("last_to", null))
        set(v) = sp.edit().putString("last_to", v?.let { encodeOne(it) }).apply()

    var favorites: List<SavedPlace>
        get() {
            favoritesVersion // Compose observes the version, then reads storage
            return decode(sp.getString("favorites", null))
        }
        set(v) {
            sp.edit().putString("favorites", encode(v)).apply()
            favoritesVersion++
        }

    var recents: List<SavedPlace>
        get() {
            recentsVersion // Compose observes the version, then reads storage
            return decode(sp.getString("recents", null))
        }
        set(v) {
            sp.edit().putString("recents", encode(v)).apply()
            recentsVersion++
        }

    /** Liczniki zmian — pozwalają Compose odświeżyć gwiazdki i listy zapisanych miejsc. */
    var favoritesVersion by mutableIntStateOf(0)
        private set
    var recentsVersion by mutableIntStateOf(0)
        private set

    fun isFavorite(p: SavedPlace): Boolean = favorites.any { it.samePlace(p) }

    fun toggleFavorite(p: SavedPlace) {
        val cur = favorites
        val has = cur.any { it.samePlace(p) }
        favorites = if (has) {
            cur.filterNot { it.samePlace(p) }
        } else {
            (cur + p).takeLast(24)
        }
    }

    fun pushRecent(p: SavedPlace) {
        val cur = recents.filterNot { it.samePlace(p) }
        recents = (listOf(p) + cur).take(8)
    }

    /** Add stable stop ids to old saves; safe to call after every data refresh. */
    fun migrateStopIds(world: World) {
        lastFrom = lastFrom?.withStopId(world)
        lastTo = lastTo?.withStopId(world)
        favorites = favorites.map { it.withStopId(world) }
        recents = recents.map { it.withStopId(world) }
    }

    private fun encodeOne(p: SavedPlace): String {
        val o = JSONObject()
        o.put("kind", p.kind)
        o.put("name", p.name)
        p.index?.let { o.put("index", it) }
        p.lat?.let { o.put("lat", it) }
        p.lng?.let { o.put("lng", it) }
        p.pkind?.let { o.put("pkind", it) }
        p.stopId?.let { o.put("stop_id", it) }
        return o.toString()
    }

    private fun decodeOne(json: String?): SavedPlace? {
        if (json.isNullOrEmpty()) return null
        return try {
            val o = JSONObject(json)
            SavedPlace(
                kind = o.optString("kind", ""),
                name = o.optString("name", ""),
                index = if (o.has("index")) o.optInt("index") else null,
                lat = if (o.has("lat")) o.optDouble("lat") else null,
                lng = if (o.has("lng")) o.optDouble("lng") else null,
                pkind = if (o.has("pkind")) o.optString("pkind") else null,
                stopId = if (o.has("stop_id")) o.optString("stop_id") else null,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encode(list: List<SavedPlace>): String {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("kind", p.kind)
            o.put("name", p.name)
            p.index?.let { o.put("index", it) }
            p.lat?.let { o.put("lat", it) }
            p.lng?.let { o.put("lng", it) }
            p.pkind?.let { o.put("pkind", it) }
            p.stopId?.let { o.put("stop_id", it) }
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decode(json: String?): List<SavedPlace> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedPlace(
                    kind = o.optString("kind", ""),
                    name = o.optString("name", ""),
                    index = if (o.has("index")) o.optInt("index") else null,
                    lat = if (o.has("lat")) o.optDouble("lat") else null,
                    lng = if (o.has("lng")) o.optDouble("lng") else null,
                    pkind = if (o.has("pkind")) o.optString("pkind") else null,
                    stopId = if (o.has("stop_id")) o.optString("stop_id") else null,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
