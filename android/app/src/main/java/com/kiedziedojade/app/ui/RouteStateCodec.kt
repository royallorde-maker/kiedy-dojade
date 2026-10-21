package com.kiedziedojade.app.ui

import com.kiedziedojade.app.core.PlaceRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zapis stosu ekranów „Szukaj” do rememberSaveable. Bez tego obrót urządzenia
 * wracał do planowania i gubił wyniki oraz otwarty przystanek.
 */
internal object RouteStateCodec {

    fun encode(routes: List<Route>): String {
        val arr = JSONArray()
        for (r in routes) arr.put(encodeRoute(r))
        return arr.toString()
    }

    fun decode(raw: String?): List<Route> {
        if (raw.isNullOrBlank()) return listOf(Route.Plan)
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<Route>(arr.length())
            for (i in 0 until arr.length()) {
                out += decodeRoute(arr.getJSONObject(i))
            }
            out.ifEmpty { listOf(Route.Plan) }
        }.getOrDefault(listOf(Route.Plan))
    }

    private fun encodeRoute(r: Route): JSONObject = when (r) {
        Route.Plan -> JSONObject().put("t", "plan")
        is Route.Results -> JSONObject()
            .put("t", "results")
            .put("from", encodePlace(r.query.from))
            .put("to", encodePlace(r.query.to))
            .put("date", r.query.serviceDate)
            .put("dep", r.query.departMin)

        is Route.Stop -> JSONObject()
            .put("t", "stop")
            .put("i", r.index)
            .put("date", r.serviceDate ?: "")
    }

    private fun decodeRoute(o: JSONObject): Route = when (o.optString("t")) {
        "results" -> Route.Results(
            JourneyQuery(
                from = decodePlace(o.getJSONObject("from")),
                to = decodePlace(o.getJSONObject("to")),
                serviceDate = o.optString("date", ""),
                departMin = o.optInt("dep", 0),
            ),
        )

        "stop" -> Route.Stop(
            index = o.optInt("i", -1),
            serviceDate = o.optString("date", "").takeIf { it.isNotBlank() },
        )

        else -> Route.Plan
    }

    private fun encodePlace(p: PlaceRef): JSONObject = when (p) {
        is PlaceRef.StopRef -> JSONObject()
            .put("k", "stop")
            .put("name", p.name)
            .put("i", p.index)
            .put("id", p.stopId ?: "")

        is PlaceRef.Gps -> JSONObject()
            .put("k", "gps")
            .put("name", p.name)
            .put("lat", p.lat)
            .put("lng", p.lng)
            .put("custom", p.custom)

        is PlaceRef.PoiRef -> JSONObject()
            .put("k", "poi")
            .put("name", p.name)
            .put("pkind", p.kind)
            .put("lat", p.lat)
            .put("lng", p.lng)

        is PlaceRef.StreetRef -> {
            val pts = JSONArray()
            for ((lat, lng) in p.pts) pts.put(JSONArray().put(lat).put(lng))
            JSONObject()
                .put("k", "street")
                .put("name", p.name)
                .put("pts", pts)
        }
    }

    private fun decodePlace(o: JSONObject): PlaceRef = when (o.optString("k")) {
        "stop" -> PlaceRef.StopRef(
            name = o.optString("name", ""),
            index = o.optInt("i", 0),
            stopId = o.optString("id", "").takeIf { it.isNotBlank() },
        )

        "gps" -> PlaceRef.Gps(
            name = o.optString("name", ""),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            custom = o.optBoolean("custom", false),
        )

        "poi" -> PlaceRef.PoiRef(
            name = o.optString("name", ""),
            kind = o.optString("pkind", ""),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
        )

        "street" -> {
            val arr = o.optJSONArray("pts") ?: JSONArray()
            val pts = ArrayList<Pair<Double, Double>>(arr.length())
            for (i in 0 until arr.length()) {
                val point = arr.optJSONArray(i) ?: continue
                pts += point.optDouble(0) to point.optDouble(1)
            }
            PlaceRef.StreetRef(o.optString("name", ""), pts)
        }

        else -> error("unknown place kind")
    }
}
