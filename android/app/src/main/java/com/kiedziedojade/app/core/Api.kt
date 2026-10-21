package com.kiedziedojade.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.Base64

/**
 * Live data from the city's KiedyPrzyjedzie passenger API — the same public
 * endpoints as the official web app. The API's WAF rejects non-browser agents,
 * so every call carries a browser User-Agent.
 */
object Api {
    private const val BASE = "https://szczecinek.kiedyprzyjedzie.pl/api"

    data class LiveRow(
        val tripId: Long?,
        val tripExecutionId: String?,
        val line: String?,
        val direction: String?,
        /** expected departure, seconds since midnight */
        val departureSec: Int?,
        /** scheduled departure, seconds since midnight */
        val scheduledSec: Int?,
        val delayMin: Int?,
        val canceled: Boolean,
        val estimated: Boolean,
        val load: String?,
        val platform: String?,
        /** index of this stop in the trip */
        val index: Int?,
        /** vehicle is standing at the stop right now */
        val atStop: Boolean = false,
    ) {
        val depMin: Int? get() = departureSec?.let { it / 60 }
        val schedMin: Int? get() = scheduledSec?.let { it / 60 }
    }

    data class Announcement(val id: Int, val markdown: String) {
        /** Very light markdown → plain text (headings, emphasis, links). */
        fun plain(): String = markdown
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1 ($2)")
            .replace(Regex("[#*_>`]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun stopBoard(placeId: String): List<LiveRow> = withContext(Dispatchers.IO) {
        val encoded = placeId.replace(":", "%3A")
        val json = Net.getText("$BASE/departures/$encoded", timeoutMs = 8000)
        parseLiveRows(JSONObject(json))
    }

    /**
     * One request for up to six stops. The city API returns the same rows as
     * [stopBoard], but grouped under `departures[].rows`; flattening them lets
     * the map discover every live run approaching the probe stops.
     */
    suspend fun stopBoards(placeIds: List<String>): List<LiveRow> = withContext(Dispatchers.IO) {
        if (placeIds.isEmpty()) return@withContext emptyList()
        val places = placeIds.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
        val json = Net.getText("$BASE/departures?places=$places", timeoutMs = 8000)
        parseLiveRows(JSONObject(json))
    }

    internal fun parseLiveRows(root: JSONObject): List<LiveRow> {
        val dirs = root.optJSONObject("directions")
        // "2 min" is relative to the server clock, "13:15" is a clock time.
        val baseMin = root.optLong("timestamp", 0L).takeIf { it > 0 }
            ?.let { Instant.ofEpochSecond(it).atZone(WARSAW).let { t -> t.hour * 60 + t.minute } }
            ?: nowMinutes()
        val out = ArrayList<LiveRow>()

        fun append(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                out += parseLiveRow(r, dirs, baseMin)
            }
        }

        // Single-stop responses put rows directly in "departures"; multi-stop
        // responses put one object with a "rows" array per place.
        val groups = root.optJSONArray("departures")
        if (groups != null) {
            val grouped = (0 until groups.length()).any { i ->
                (groups.opt(i) as? JSONObject)?.has("rows") == true
            }
            if (grouped) {
                for (i in 0 until groups.length()) {
                    groups.optJSONObject(i)?.optJSONArray("rows")?.let { append(it) }
                }
            } else {
                append(groups)
            }
        } else {
            root.optJSONArray("rows")?.let { append(it) }
        }
        return out
    }

    private fun parseLiveRow(r: JSONObject, dirs: JSONObject?, baseMin: Int): LiveRow {
        val timeText = r.optStringOrNull("time")
        val staticText = r.optStringOrNull("static_time")
        val depMin = clockOrRelative(timeText, baseMin) ?: clockOrRelative(staticText, baseMin)
        val schedMin = clockOrRelative(staticText, baseMin) ?: depMin
        val dirId = r.optIntOrNull("direction_id")
        return LiveRow(
            tripId = if (r.has("trip_id") && !r.isNull("trip_id")) {
                r.optLong("trip_id").takeIf { it > 0 }
            } else {
                null
            },
            tripExecutionId = r.optStringOrNull("trip_execution_id"),
            line = r.optStringOrNull("line_name"),
            direction = r.optStringOrNull("direction")
                ?: dirId?.let { id -> dirs?.optString(id.toString())?.takeIf { it.isNotBlank() } },
            departureSec = r.optIntOrNull("real_departure") ?: r.optIntOrNull("departure")
                ?: depMin?.let { it * 60 },
            scheduledSec = r.optIntOrNull("timetable_departure") ?: r.optIntOrNull("departure")
                ?: schedMin?.let { it * 60 },
            delayMin = r.optDoubleOrNull("time_diff")?.let { Math.round(it).toInt() },
            canceled = r.optBoolean("canceled", false),
            estimated = r.optBoolean("is_estimated", false),
            load = r.optStringOrNull("passenger_load"),
            platform = r.optStringOrNull("platform"),
            index = r.optIntOrNull("trip_index"),
            atStop = r.optBoolean("at_stop", false),
        )
    }

    // ------------------------------------------------------------ trip execution

    data class TripStopTime(
        val index: Int,
        val placeId: String,
        val name: String,
        val departureMin: Int?,
    )

    data class TripEstimate(
        /** absolute clock of the estimate, when the API gives one */
        val timeAbs: Int?,
        /** minutes until the vehicle reaches this stop */
        val toGo: Int?,
        /** "HH:MM" the vehicle should arrive */
        val arrivalAbs: Int?,
    )

    /**
     * Live state of one bus run: where the vehicle is, which stop it is
     * heading to and how far away every upcoming stop is.
     *
     * [estimates] is trimmed to the stops still ahead, so it is aligned to
     * [times] starting at [nextIndex] (and not at zero) whenever the bus is
     * already running.
     */
    data class TripExec(
        val line: String?,
        val direction: String?,
        val times: List<TripStopTime>,
        val estimates: List<TripEstimate>,
        /** vehicle position as (lat, lng), null when unknown */
        val vehicle: Pair<Double, Double>?,
        val atStop: Boolean,
        /** index in [times] of the stop the bus is approaching / standing at */
        val nextIndex: Int,
        /** index in [times] of the last stop the vehicle reached (-1 before the run) */
        val vehicleIndex: Int,
        val canceledStops: Set<Int>,
        val estimated: Boolean,
    ) {
        /** first [times] index described by [estimates] */
        val offset: Int
            get() = when {
                estimates.isEmpty() -> 0
                estimates.size >= times.size -> 0
                nextIndex in 0..times.size && nextIndex == times.size - estimates.size -> nextIndex
                else -> (times.size - estimates.size).coerceIn(0, times.size)
            }

        /** the run has started (the vehicle left the depot / first stop) */
        val hasProgress: Boolean get() = atStop || nextIndex > 0 || vehicleIndex >= 0

        fun indexOfPlace(placeId: String): Int? {
            if (placeId.isEmpty()) return null
            val i = times.indexOfFirst { it.placeId == placeId }
            return i.takeIf { it >= 0 }
        }

        fun estimateAt(index: Int): TripEstimate? =
            if (index < 0) null else estimates.getOrNull(index - offset)

        /** minutes from [now] until the vehicle reaches [index] */
        fun minutesTo(index: Int, now: Int): Int? {
            if (index < 0 || index !in times.indices) return null
            if (index < offset) return 0
            val e = estimateAt(index) ?: return null
            e.toGo?.let { return it.coerceAtLeast(0) }
            val clock = e.arrivalAbs ?: e.timeAbs ?: return null
            return minutesUntil(clock, now)
        }

        /** clock time the vehicle reaches [index], or the timetable when unknown */
        fun clockTo(index: Int): Int? {
            if (index < 0 || index !in times.indices) return null
            if (hasProgress) {
                val e = estimateAt(index)
                e?.arrivalAbs?.let { return it }
                e?.timeAbs?.let { return it }
            }
            // Before the run starts the estimates describe the depot, not the
            // passenger stops, so the timetable is the honest clock.
            return times[index].departureMin
        }

        /**
         * Stops between the vehicle and [index] (0 = the stop it is at / just
         * passed). Null before the run starts, where a count makes no sense.
         */
        fun stopsTo(index: Int): Int? {
            if (index !in times.indices || !hasProgress) return null
            val n = nextIndex.coerceIn(0, times.size)
            return when {
                n > index -> 0
                atStop && n == index -> 0
                atStop -> index - n
                else -> index - n + 1
            }.coerceAtLeast(0)
        }

        /** the vehicle already went past [index] */
        fun passed(index: Int): Boolean =
            hasProgress && index >= 0 && nextIndex > index
    }

    /**
     * A vehicle that has actually started its run. Future departures also carry
     * a depot position in the API, so the map must not draw those as live buses.
     */
    data class LiveBus(
        val executionId: String,
        val line: String,
        val direction: String?,
        /** vehicle position as (lat, lng) */
        val vehicle: Pair<Double, Double>,
        val nextIndex: Int,
        val nextStopName: String?,
        val nextStopPlaceId: String?,
        val minutesToNext: Int?,
        val clockToNext: Int?,
        val atStop: Boolean,
        val delayMin: Int?,
        val estimated: Boolean,
    )

    private const val MAX_LIVE_BUSES = 8
    private const val LIVE_BUS_LOOKAHEAD_MIN = 45
    private const val LIVE_BUS_CONCURRENCY = 4

    /**
     * Finds live vehicles of [line] by asking a few stops along its route.
     * The public API has no fleet endpoint, so this is the same departure data
     * the boards use; trip executions are then fetched for their GPS positions.
     */
    suspend fun liveBuses(line: String, probePlaceIds: List<String>): List<LiveBus> {
        if (probePlaceIds.isEmpty()) return emptyList()
        val now = nowMinutes()
        val candidates = liveCandidates(line, stopBoards(probePlaceIds), now)
            .mapNotNull { row ->
                val id = row.tripExecutionId?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                id to row
            }
        if (candidates.isEmpty()) return emptyList()
        val found = ArrayList<LiveBus>()
        // Maks. kilka równoległych zapytań, żeby nie zalewać miejskiego API.
        for (chunk in candidates.chunked(LIVE_BUS_CONCURRENCY)) {
            found += coroutineScope {
                chunk.map { (id, row) ->
                    async(Dispatchers.IO) {
                        val exec = tripExecution(id, row.index ?: 0) ?: return@async null
                        liveBus(line, row, exec, now)
                    }
                }.awaitAll().filterNotNull()
            }
        }
        return found.sortedWith(compareBy({ it.nextIndex }, { it.executionId }))
    }

    /**
     * Keeps rows of [line] with a real execution id, drops canceled and
     * far-future departures, and removes the same vehicle reported at several
     * probe stops. Pure, so the map's line filtering is covered by tests.
     */
    internal fun liveCandidates(
        line: String,
        rows: List<LiveRow>,
        now: Int,
        limit: Int = MAX_LIVE_BUSES,
    ): List<LiveRow> {
        val byExecution = LinkedHashMap<String, LiveRow>()
        rows.forEach { row ->
            val dep = row.depMin
            if (row.line?.equals(line, ignoreCase = true) != true) return@forEach
            if (row.canceled) return@forEach
            if (!row.atStop && dep != null &&
                minuteDiff(dep, now) !in -2..LIVE_BUS_LOOKAHEAD_MIN
            ) {
                return@forEach
            }
            val id = row.tripExecutionId?.takeIf { it.isNotBlank() } ?: return@forEach
            val old = byExecution[id]
            if (old == null || candidateRank(row) < candidateRank(old)) {
                byExecution[id] = row
            }
        }
        return byExecution.values
            .sortedBy { candidateRank(it) }
            .take(limit)
    }

    /** At-stop rows with no countdown still describe a vehicle we can show. */
    private fun candidateRank(row: LiveRow): Long = when {
        row.atStop -> 0L
        row.depMin == null -> 1_000_000L
        else -> 2_000_000L + (row.depMin ?: 0)
    }

    internal fun liveBus(line: String, row: LiveRow, exec: TripExec, now: Int): LiveBus? {
        val vehicle = exec.vehicle ?: return null
        if (!exec.hasProgress) return null
        val index = exec.nextIndex
        if (index !in exec.times.indices) return null
        val next = exec.times[index]
        return LiveBus(
            executionId = row.tripExecutionId.orEmpty(),
            line = row.line?.takeIf { it.isNotBlank() } ?: line,
            direction = exec.direction?.takeIf { it.isNotBlank() } ?: row.direction,
            vehicle = vehicle,
            nextIndex = index,
            nextStopName = next.name.takeIf { it.isNotBlank() },
            nextStopPlaceId = next.placeId.takeIf { it.isNotBlank() },
            minutesToNext = exec.minutesTo(index, now),
            clockToNext = exec.clockTo(index),
            atStop = exec.atStop,
            delayMin = row.delayMin,
            estimated = exec.estimated || row.estimated,
        )
    }

    suspend fun tripExecution(executionId: String, index: Int = 0): TripExec? =
        withContext(Dispatchers.IO) {
            runCatching {
                val b64 = Base64.getEncoder()
                    .encodeToString(executionId.toByteArray(Charsets.UTF_8))
                val encoded = URLEncoder.encode(b64, "UTF-8")
                val json = Net.getText("$BASE/trip_execution/$encoded/$index", timeoutMs = 8000)
                parseTripExec(JSONObject(json))
            }.getOrNull()
        }

    internal fun parseTripExec(root: JSONObject): TripExec {
        val trip = root.optJSONObject("trip")
        val times = ArrayList<TripStopTime>()
        trip?.optJSONArray("times")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                times += TripStopTime(
                    index = t.optInt("index", i),
                    placeId = t.optStringOrNull("place_id").orEmpty(),
                    name = t.optStringOrNull("stop_name").orEmpty(),
                    departureMin = parseTimeOrNull(t.optStringOrNull("departure_time")),
                )
            }
        }
        val estimates = ArrayList<TripEstimate>()
        root.optJSONArray("estimates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                estimates += TripEstimate(
                    timeAbs = parseTimeOrNull(e.optStringOrNull("time_abs")),
                    toGo = e.optIntOrNull("to_go"),
                    arrivalAbs = parseTimeOrNull(e.optStringOrNull("arrival_abs")),
                )
            }
        }
        val canceled = HashSet<Int>()
        root.optJSONArray("canceled")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                if (item is Number) canceled += item.toInt()
            }
        }
        // Current API nests the line as {"name":"1A"}; older responses used a
        // plain string. Read both without changing the public shape.
        val line = trip?.optJSONObject("line")?.optStringOrNull("name")
            ?: trip?.optStringOrNull("line")
        val vehicleJson = root.optJSONObject("vehicle")
        val vehicle = vehicleJson?.let { v ->
            val lat = v.optDouble("lat", Double.NaN)
            val lng = v.optDouble("lon", v.optDouble("lng", Double.NaN))
            if (lat in -90.0..90.0 && lng in -180.0..180.0 && (lat != 0.0 || lng != 0.0)) {
                lat to lng
            } else {
                null
            }
        }
        return TripExec(
            line = line,
            direction = trip?.optStringOrNull("direction"),
            times = times,
            estimates = estimates,
            vehicle = vehicle,
            atStop = root.optBoolean("at_stop", false),
            nextIndex = root.optInt("next_departure_index", 0),
            vehicleIndex = root.optInt("vehicle_trip_index", -1),
            canceledStops = canceled,
            estimated = root.optBoolean("estimated", false),
        )
    }

    suspend fun announcements(): List<Announcement> = withContext(Dispatchers.IO) {
        try {
            val json = Net.getText("$BASE/announcements", timeoutMs = 8000)
            val arr = JSONObject(json).optJSONArray("announcements") ?: return@withContext emptyList()
            val out = ArrayList<Announcement>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                out += Announcement(a.optInt("id", 0), a.optString("content_markdown", ""))
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** "2 min" / "13:15" / "teraz" → minutes since midnight, relative to [base]. */
private fun clockOrRelative(text: String?, base: Int): Int? {
    if (text.isNullOrBlank()) return null
    val t = text.trim().lowercase()
    if (t == "teraz" || t == "now") return base
    val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return if (t.contains("min")) ((base + n) % 1440 + 1440) % 1440 else parseTimeOrNull(t)
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null
