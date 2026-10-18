package com.kiedziedojade.app.core

/** Core model types — a Kotlin port of the web app's types.ts. */

data class Stop(
    /** index in the stops list — trips refer to this */
    val i: Int,
    /** upstream place id ("31830:56491") — live endpoints need it */
    val id: String,
    val code: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    /** "na żądanie" */
    val onDemand: Boolean,
    val station: Boolean,
    /** boarding not allowed (terminus) */
    val onlyDisembarking: Boolean,
) {
    /** "Miasto/ Ulica" na dwa wiersze w UI */
    val nameParts: Pair<String, String?>
        get() {
            val idx = name.indexOf('/')
            if (idx < 0) return name to null
            return name.substring(0, idx).trim() to name.substring(idx + 1).trim()
        }
}

data class Trip(
    val id: Long,
    val line: String,
    val dir: String,
    val vt: Int,
    /** [stopIdx, departure minute] pairs, in order */
    val times: List<Pair<Int, Int>>,
)

data class PatternInfo(val type: String, val days: Int, val sampleDay: String)

data class Meta(
    val generatedAt: String,
    val source: String,
    val city: String,
    val timezone: String,
    val horizonFrom: String,
    val horizonTo: String,
    val patterns: Map<String, PatternInfo>,
    val stopsCount: Int,
    val tripsCount: Int,
)

data class CalendarData(val updatedAt: String, val days: Map<String, String>)

data class Street(val name: String, val pts: List<Pair<Double, Double>>)

data class Poi(val name: String, val kind: String, val lat: Double, val lng: Double)

data class PlacesData(val streets: List<Street>, val pois: List<Poi>)

/** Everything the app needs to plan: the whole dataset is small (~300 KB). */
data class World(
    val stops: List<Stop>,
    val meta: Meta,
    val calendar: CalendarData,
    val places: PlacesData,
    /** pattern id -> trips */
    val trips: Map<String, List<Trip>>,
    /** where this copy came from: "assets" or "cache" */
    val origin: String = "assets",
) {
    fun stopAt(idx: Int): Stop = stops[idx]
}

// ---------------------------------------------------------------- places

sealed interface PlaceRef {
    val name: String

    /** GPS / arbitrary coordinates */
    data class Gps(override val name: String, val lat: Double, val lng: Double) : PlaceRef

    /** a bus stop from the dataset */
    data class StopRef(override val name: String, val index: Int) : PlaceRef

    /** a POI (shop, school, …) */
    data class PoiRef(
        override val name: String,
        val kind: String,
        val lat: Double,
        val lng: Double,
    ) : PlaceRef

    /** a street (polyline of points) */
    data class StreetRef(override val name: String, val pts: List<Pair<Double, Double>>) : PlaceRef
}

// ---------------------------------------------------------------- legs

sealed interface Leg {
    val depMin: Int
    val arrMin: Int
}

data class WalkLeg(
    val fromName: String,
    val toName: String,
    val meters: Double,
    val minutes: Int,
    override val depMin: Int,
    override val arrMin: Int,
) : Leg

data class RideStop(val stop: Stop, val min: Int)

data class RideLeg(
    val tripId: Long,
    val line: String,
    val dir: String,
    val boardStop: Stop,
    val alightStop: Stop,
    override val depMin: Int,
    override val arrMin: Int,
    /** intermediate stops including board & alight */
    val stops: List<RideStop>,
) : Leg

data class Itinerary(
    val id: String,
    val legs: List<Leg>,
    val leaveAt: Int,
    val arriveAt: Int,
    val transfers: Int,
    val walkMeters: Double,
    val walkMinutes: Int,
    /** index of the leg where you board the first vehicle */
    val firstRideLeg: Int,
    val pattern: String,
    /** "best" | "next" | "fewer-transfers" — assigned while assembling */
    var tag: String,
) {
    val rides: List<RideLeg> get() = legs.filterIsInstance<RideLeg>()
    val firstRide: RideLeg? get() = rides.firstOrNull()
    val lastRide: RideLeg? get() = rides.lastOrNull()
}

data class WalkOnly(val minutes: Int, val meters: Double, val leaveAt: Int, val arriveAt: Int)

data class PlanOutcome(
    val itineraries: List<Itinerary>,
    val pattern: String,
    val exactDay: Boolean,
    val patternType: String,
    val walkOnly: WalkOnly?,
    val empty: Boolean,
)

enum class Tag(val value: String) {
    BEST("best"),
    NEXT("next"),
    FEWER_TRANSFERS("fewer-transfers"),
}
