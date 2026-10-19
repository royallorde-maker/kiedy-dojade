package com.kiedziedojade.app.core

/**
 * Finds the live departure row for a planned ride. Local trips normally keep
 * the same id as the live ones, but the timetable can be regenerated between
 * planning and boarding — the fallback matches line, direction and departure.
 */
fun matchLiveRide(rows: List<Api.LiveRow>, ride: RideLeg): Api.LiveRow? {
    rows.firstOrNull { it.tripId == ride.tripId }?.let { return it }
    val sameLine = rows.filter { it.line?.equals(ride.line, ignoreCase = true) == true }
    if (sameLine.isEmpty()) return null
    val best = sameLine.minByOrNull { row ->
        val m = row.schedMin ?: row.depMin ?: return@minByOrNull 9999
        kotlin.math.abs(minuteDiff(m, ride.depMin))
    } ?: return null
    val m = best.schedMin ?: best.depMin ?: return null
    return best.takeIf { kotlin.math.abs(minuteDiff(m, ride.depMin)) <= 5 }
}

/**
 * Difference between two clock times in minutes, wrapped to ±720 so a ride
 * just after midnight still matches its live row.
 */
fun minuteDiff(a: Int, b: Int): Int = ((a - b + 720) % 1440 + 1440) % 1440 - 720
