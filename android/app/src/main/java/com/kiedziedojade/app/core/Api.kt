package com.kiedziedojade.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
        val index: Int?,
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
        val root = JSONObject(json)
        val arr = root.optJSONArray("departures") ?: return@withContext emptyList()
        val out = ArrayList<LiveRow>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            out += LiveRow(
                tripId = if (r.has("trip_id")) r.getLong("trip_id") else null,
                tripExecutionId = r.optStringOrNull("trip_execution_id"),
                line = r.optStringOrNull("line_name"),
                direction = r.optStringOrNull("direction"),
                departureSec = r.optIntOrNull("real_departure") ?: r.optIntOrNull("departure"),
                scheduledSec = r.optIntOrNull("timetable_departure") ?: r.optIntOrNull("departure"),
                delayMin = r.optDoubleOrNull("time_diff")?.let { Math.round(it).toInt() },
                canceled = r.optBoolean("canceled", false),
                estimated = r.optBoolean("is_estimated", false),
                load = r.optStringOrNull("passenger_load"),
                platform = r.optStringOrNull("platform"),
                index = r.optIntOrNull("index"),
            )
        }
        out
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

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null
