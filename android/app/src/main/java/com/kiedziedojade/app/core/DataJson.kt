package com.kiedziedojade.app.core

import org.json.JSONArray
import org.json.JSONObject

/** Parsing of the harvested schedule JSON (the same files the web app uses). */
object DataJson {

    fun parseStops(json: String): List<Stop> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("stops")
        val out = ArrayList<Stop>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.getJSONArray(i)
            val flags = if (s.length() > 4) s.optInt(4, 0) else 0
            out += Stop(
                i = i,
                id = if (s.length() > 5) s.getString(5) else "",
                code = s.getInt(0),
                name = s.getString(1),
                lat = s.getDouble(2),
                lng = s.getDouble(3),
                onDemand = (flags and 1) != 0,
                station = (flags and 2) != 0,
                onlyDisembarking = (flags and 4) != 0,
            )
        }
        return out
    }

    fun parseMeta(json: String): Meta {
        val o = JSONObject(json)
        val horizon = o.optJSONObject("horizon") ?: JSONObject()
        val patternsObj = o.optJSONObject("patterns") ?: JSONObject()
        val patterns = LinkedHashMap<String, PatternInfo>()
        for (key in patternsObj.keys()) {
            val p = patternsObj.getJSONObject(key)
            patterns[key] = PatternInfo(
                type = p.optString("type", ""),
                days = p.optInt("days", 0),
                sampleDay = p.optString("sampleDay", ""),
            )
        }
        val counts = o.optJSONObject("counts") ?: JSONObject()
        return Meta(
            generatedAt = o.optString("generatedAt", ""),
            source = o.optString("source", ""),
            city = o.optString("city", "Szczecinek"),
            timezone = o.optString("timezone", "Europe/Warsaw"),
            horizonFrom = horizon.optString("from", ""),
            horizonTo = horizon.optString("to", ""),
            patterns = patterns,
            stopsCount = counts.optInt("stops", 0),
            tripsCount = counts.optInt("trips", 0),
        )
    }

    fun parseCalendar(json: String): CalendarData {
        val o = JSONObject(json)
        val daysObj = o.optJSONObject("days") ?: JSONObject()
        val days = HashMap<String, String>(daysObj.length() * 2)
        for (key in daysObj.keys()) days[key] = daysObj.getString(key)
        return CalendarData(updatedAt = o.optString("updatedAt", ""), days = days)
    }

    fun parsePlaces(json: String): PlacesData {
        val o = JSONObject(json)
        val streetsArr = o.optJSONArray("streets") ?: JSONArray()
        val streets = ArrayList<Street>(streetsArr.length())
        for (i in 0 until streetsArr.length()) {
            val row = streetsArr.getJSONArray(i)
            val name = row.getString(0)
            val ptsArr = row.optJSONArray(1) ?: JSONArray()
            val pts = ArrayList<Pair<Double, Double>>(ptsArr.length())
            for (k in 0 until ptsArr.length()) {
                val pt = ptsArr.getJSONArray(k)
                pts += pt.getDouble(0) to pt.getDouble(1)
            }
            streets += Street(name, pts)
        }
        val poisArr = o.optJSONArray("pois") ?: JSONArray()
        val pois = ArrayList<Poi>(poisArr.length())
        for (i in 0 until poisArr.length()) {
            val row = poisArr.getJSONArray(i)
            pois += Poi(
                name = row.getString(0),
                kind = if (row.length() > 1) row.optString(1, "") else "",
                lat = row.getDouble(2),
                lng = row.getDouble(3),
            )
        }
        return PlacesData(streets, pois)
    }

    fun parseTrips(json: String): List<Trip> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("trips")
        val out = ArrayList<Trip>(arr.length())
        for (i in 0 until arr.length()) {
            val t = arr.getJSONArray(i)
            val timesArr = t.optJSONArray(4) ?: JSONArray()
            val times = ArrayList<Pair<Int, Int>>(timesArr.length())
            for (k in 0 until timesArr.length()) {
                val pair = timesArr.getJSONArray(k)
                times += pair.getInt(0) to pair.getInt(1)
            }
            out += Trip(
                id = t.getLong(0),
                line = t.getString(1),
                dir = t.getString(2),
                vt = t.optInt(3, 0),
                times = times,
            )
        }
        return out
    }

    /**
     * Build a [World] from a file reader (`name -> content`).
     * Returns null when a required file is missing or unparsable.
     */
    fun world(origin: String, read: (String) -> String?): World? = try {
        val metaJson = read("meta.json") ?: return null
        val meta = parseMeta(metaJson)
        val stops = parseStops(read("stops.json") ?: return null)
        val calendar = parseCalendar(read("calendar.json") ?: return null)
        val places = try {
            read("places.json")?.let { parsePlaces(it) } ?: PlacesData(emptyList(), emptyList())
        } catch (_: Exception) {
            PlacesData(emptyList(), emptyList())
        }
        val trips = HashMap<String, List<Trip>>()
        val stopCount = stops.size
        for (pid in meta.patterns.keys) {
            val content = read("trips-$pid.json") ?: return null
            // przystanki w trips.json są indeksami stops.json; rozjazd wersji
            // obu plików (np. po częściowym pobraniu) nie może wywrócić mapy
            // i planera — wpisy spoza zakresu odrzucamy, kursy wyrzucamy
            trips[pid] = parseTrips(content).mapNotNull { tr ->
                // para to (indeks przystanku, minuta): walidujemy tylko indeks
                val valid = tr.times.filter { (stop, _) -> stop in 0 until stopCount }
                if (valid.size < 2) {
                    null
                } else if (valid.size == tr.times.size) {
                    tr
                } else {
                    tr.copy(times = valid)
                }
            }
        }
        World(stops = stops, meta = meta, calendar = calendar, places = places, trips = trips, origin = origin)
    } catch (_: Exception) {
        null
    }
}
