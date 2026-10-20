package com.kiedziedojade.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact JSON for an active trip. The itinerary is stored in preferences so a
 * foreground tracking service can keep working after the activity is closed,
 * and so reopening the app restores the same navigation screen.
 */
object ItineraryJson {

    fun encode(it: Itinerary): String {
        val root = JSONObject()
        root.put("id", it.id)
        root.put("leaveAt", it.leaveAt)
        root.put("arriveAt", it.arriveAt)
        root.put("transfers", it.transfers)
        root.put("walkMeters", it.walkMeters)
        root.put("walkMinutes", it.walkMinutes)
        root.put("firstRideLeg", it.firstRideLeg)
        root.put("pattern", it.pattern)
        root.put("tag", it.tag)
        val legs = JSONArray()
        for (leg in it.legs) legs.put(encodeLeg(leg))
        root.put("legs", legs)
        return root.toString()
    }

    fun decode(raw: String?): Itinerary? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val legsArr = root.optJSONArray("legs") ?: JSONArray()
            val legs = ArrayList<Leg>(legsArr.length())
            for (i in 0 until legsArr.length()) {
                legs += decodeLeg(legsArr.getJSONObject(i)) ?: return null
            }
            Itinerary(
                id = root.optString("id", ""),
                legs = legs,
                leaveAt = root.optInt("leaveAt", 0),
                arriveAt = root.optInt("arriveAt", 0),
                transfers = root.optInt("transfers", 0),
                walkMeters = root.optDouble("walkMeters", 0.0),
                walkMinutes = root.optInt("walkMinutes", 0),
                firstRideLeg = root.optInt("firstRideLeg", 0),
                pattern = root.optString("pattern", ""),
                tag = root.optString("tag", Tag.BEST.value),
            )
        }.getOrNull()
    }

    private fun encodeLeg(leg: Leg): JSONObject = when (leg) {
        is WalkLeg -> JSONObject().apply {
            put("type", "walk")
            put("fromName", leg.fromName)
            put("toName", leg.toName)
            put("meters", leg.meters)
            put("minutes", leg.minutes)
            put("depMin", leg.depMin)
            put("arrMin", leg.arrMin)
            leg.fromPt?.let { put("fromPt", point(it)) }
            leg.toPt?.let { put("toPt", point(it)) }
        }

        is RideLeg -> JSONObject().apply {
            put("type", "ride")
            put("tripId", leg.tripId)
            put("line", leg.line)
            put("dir", leg.dir)
            put("boardStop", encodeStop(leg.boardStop))
            put("alightStop", encodeStop(leg.alightStop))
            put("depMin", leg.depMin)
            put("arrMin", leg.arrMin)
            val stops = JSONArray()
            for (rs in leg.stops) {
                stops.put(JSONObject().apply {
                    put("stop", encodeStop(rs.stop))
                    put("min", rs.min)
                })
            }
            put("stops", stops)
        }
    }

    private fun decodeLeg(o: JSONObject): Leg? = when (o.optString("type")) {
        "walk" -> WalkLeg(
            fromName = o.optString("fromName", ""),
            toName = o.optString("toName", ""),
            meters = o.optDouble("meters", 0.0),
            minutes = o.optInt("minutes", 0),
            depMin = o.optInt("depMin", 0),
            arrMin = o.optInt("arrMin", 0),
            fromPt = point(o.optJSONArray("fromPt")),
            toPt = point(o.optJSONArray("toPt")),
        )

        "ride" -> {
            val stopsArr = o.optJSONArray("stops") ?: JSONArray()
            val stops = ArrayList<RideStop>(stopsArr.length())
            for (i in 0 until stopsArr.length()) {
                val item = stopsArr.optJSONObject(i) ?: return null
                val stop = decodeStop(item.optJSONObject("stop")) ?: return null
                stops += RideStop(stop, item.optInt("min", 0))
            }
            val board = decodeStop(o.optJSONObject("boardStop")) ?: return null
            val alight = decodeStop(o.optJSONObject("alightStop")) ?: return null
            RideLeg(
                tripId = o.optLong("tripId", 0L),
                line = o.optString("line", ""),
                dir = o.optString("dir", ""),
                boardStop = board,
                alightStop = alight,
                depMin = o.optInt("depMin", 0),
                arrMin = o.optInt("arrMin", 0),
                stops = stops,
            )
        }

        else -> null
    }

    private fun encodeStop(s: Stop): JSONObject = JSONObject().apply {
        put("i", s.i)
        put("id", s.id)
        put("code", s.code)
        put("name", s.name)
        put("lat", s.lat)
        put("lng", s.lng)
        put("onDemand", s.onDemand)
        put("station", s.station)
        put("onlyDisembarking", s.onlyDisembarking)
    }

    private fun decodeStop(o: JSONObject?): Stop? {
        if (o == null) return null
        return Stop(
            i = o.optInt("i", -1),
            id = o.optString("id", ""),
            code = o.optInt("code", 0),
            name = o.optString("name", ""),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            onDemand = o.optBoolean("onDemand", false),
            station = o.optBoolean("station", false),
            onlyDisembarking = o.optBoolean("onlyDisembarking", false),
        )
    }

    private fun point(p: Pair<Double, Double>): JSONArray = JSONArray().apply {
        put(p.first)
        put(p.second)
    }

    private fun point(a: JSONArray?): Pair<Double, Double>? {
        if (a == null || a.length() < 2) return null
        return a.optDouble(0) to a.optDouble(1)
    }
}
