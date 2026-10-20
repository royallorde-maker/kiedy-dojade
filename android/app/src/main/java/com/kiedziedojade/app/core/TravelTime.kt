package com.kiedziedojade.app.core

import java.time.ZonedDateTime

/**
 * A concrete departure chosen from a clock time.
 *
 * The app no longer asks for a calendar day. A time that has already passed
 * today means the next occurrence, i.e. tomorrow at the same time. The planner
 * still needs a real date because the timetable distinguishes weekdays,
 * holidays and school-free days, so [serviceDate] stays internal.
 */
data class PlannedDeparture(
    /** YYYY-MM-DD in Europe/Warsaw */
    val serviceDate: String,
    /** minutes since midnight */
    val departMin: Int,
)

/** Resolves "now" or a custom clock time to the next real service moment. */
fun resolveDeparture(now: ZonedDateTime, selectedMinute: Int?): PlannedDeparture {
    val nowMin = now.hour * 60 + now.minute
    val minute = (selectedMinute ?: nowMin).coerceIn(0, 1439)
    val date = if (selectedMinute != null && minute < nowMin) {
        now.toLocalDate().plusDays(1)
    } else {
        now.toLocalDate()
    }
    return PlannedDeparture(date.toString(), minute)
}
