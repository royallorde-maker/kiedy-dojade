package com.kiedziedojade.app.core

/**
 * Planer i rozkłady obejmują wyłącznie Szczecinek. Punkt dalej niż ten promień
 * od każdego przystanku jest poza zasięgiem danych — GPS sam w sobie działa
 * wszędzie, ale nie ma tam czego planować.
 */
const val COVERAGE_RADIUS_M = 25_000.0

fun nearestStopMeters(world: World, ref: PlaceRef): Double? {
    if (world.stops.isEmpty()) return null
    val place = Planner.resolvePlace(ref, world.stops)
    return world.stops.minOfOrNull { stop ->
        Planner.closestPointOf(place, stop.lat, stop.lng).second
    }
}

fun isOutsideCoverage(world: World, ref: PlaceRef?): Boolean {
    if (ref == null) return false
    val distance = nearestStopMeters(world, ref) ?: return false
    return distance > COVERAGE_RADIUS_M
}
