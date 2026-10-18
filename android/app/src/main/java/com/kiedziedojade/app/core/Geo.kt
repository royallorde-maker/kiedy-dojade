package com.kiedziedojade.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Geodesy, walking maths and time formatting — port of geo.ts. */

const val EARTH_R = 6371000.0

/** Real walking distance is typically ~25% longer than the straight line. */
const val WALK_DETOUR = 1.25

/** Comfortable walking speed in metres per minute (~4.7 km/h). */
const val WALK_SPEED_MPM = 78.0

/** Flat cost for entering/leaving a stop (crossing, finding the pole). */
const val WALK_BUFFER_MIN = 1

val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val toRad = Math.PI / 180
    val dLat = (lat2 - lat1) * toRad
    val dLng = (lng2 - lng1) * toRad
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_R * asin(sqrt(min(1.0, a)))
}

fun walkMinutes(meters: Double): Int =
    maxOf(1, ((meters * WALK_DETOUR) / WALK_SPEED_MPM + WALK_BUFFER_MIN).roundToInt())

fun fmtMeters(m: Double): String =
    if (m < 950) "${(m / 10).roundToInt() * 10} m" else String.format(Locale.US, "%.1f km", m / 1000)

/** minutes since midnight -> "HH:MM" (wraps past midnight) */
fun fmtTime(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
}

fun fmtDuration(min: Int): String =
    if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

/** "HH:MM" -> minutes since midnight */
fun parseTime(s: String): Int {
    val parts = s.split(':')
    val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}

/** Minutes since midnight in Szczecinek, no matter the device timezone. */
fun nowMinutes(): Int {
    val t = LocalTime.now(WARSAW)
    return t.hour * 60 + t.minute
}

fun todayStr(): String = LocalDate.now(WARSAW).toString()

fun isoToDate(iso: String): LocalDate = LocalDate.parse(iso)

/** "pon., 19 paź" */
fun fmtDate(iso: String, pl: Boolean): String {
    val d = runCatching { isoToDate(iso) }.getOrNull() ?: return iso
    val loc = if (pl) Locale("pl", "PL") else Locale.ENGLISH
    val pattern = if (pl) "EEE, d MMM" else "EEE, d MMM"
    return d.format(DateTimeFormatter.ofPattern(pattern, loc))
}

/** "19.10.2026, 12:34" for the data timestamp (Warsaw time). */
fun fmtTimestamp(iso: String, pl: Boolean): String {
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return iso
    val loc = if (pl) Locale("pl", "PL") else Locale.ENGLISH
    val fmt = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm", loc).withZone(WARSAW)
    return fmt.format(instant)
}

/** Nearest vertex of a polyline to a point. Used by the search + planner. */
fun nearestOnPolyline(lat: Double, lng: Double, pts: List<Pair<Double, Double>>): Double {
    var best = Double.MAX_VALUE
    for (p in pts) {
        val d = haversine(lat, lng, p.first, p.second)
        if (d < best) best = d
    }
    return best
}
