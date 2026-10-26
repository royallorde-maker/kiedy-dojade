package com.kiedziedojade.app.core

/**
 * Journey planner — a Kotlin port of planner.ts.
 *
 * Every ride between two consecutive stops of a trip is a "connection" with a
 * departure and arrival time. A query relaxes connections in time order until
 * arrivals stop improving (Bellman–Ford over the time-expanded graph). That
 * gives earliest-arrival journeys with walking to/from stops and a bounded
 * number of boardings; running the search slightly later yields the next
 * departures.
 */

data class PlanInput(
    val origin: PlaceRef,
    val dest: PlaceRef,
    /** YYYY-MM-DD; defaults to today (Europe/Warsaw) */
    val date: String? = null,
    /** earliest departure, minutes since midnight */
    val departMin: Int,
    val maxTransfers: Int = 2,
    /** crow-flies metres one is willing to walk to reach a stop */
    val walkRadiusM: Double = 900.0,
    val maxResults: Int = 4,
)

private class Connection(
    val from: Int,
    val to: Int,
    val dep: Int,
    val arr: Int,
    val tripIdx: Int,
    val seq: Int,
)

private class Access(val meters: Double, val minutes: Int)

private class Label(
    val arr: Int,
    val boardings: Int,
    val stopIdx: Int,
    val prev: Label?,
    val ride: Connection?,
    val walkMeters: Double,
    val access: Access?,
)

private class Built(val trips: List<Trip>, val conns: List<Connection>)

/** Resolved place: a point, or a polyline for streets. */
class ResolvedPlace(
    val name: String,
    val lat: Double,
    val lng: Double,
    val pts: List<Pair<Double, Double>>? = null,
)

object Planner {
    private const val MAX_RELAX_PASSES = 10

    /** Walking shorter than this is "you are at the stop" — no leg, no time cost. */
    const val TRIVIAL_WALK_M = 25.0

    // Bounded cache with LRU eviction to prevent memory leaks
    private val builtCache = object : LinkedHashMap<String, Built>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Built>?) = size > 50
    }

    fun resolvePlace(ref: PlaceRef, stops: List<Stop>): ResolvedPlace = when (ref) {
        is PlaceRef.Gps -> ResolvedPlace(ref.name, ref.lat, ref.lng)
        is PlaceRef.PoiRef -> ResolvedPlace(ref.name, ref.lat, ref.lng)
        is PlaceRef.StopRef -> {
            // A stable id wins over an index saved against an older dataset.
            val s = if (ref.stopId != null) {
                stops.firstOrNull { it.id == ref.stopId }
                    ?: stops.firstOrNull { it.name == ref.name }
            } else {
                stops.getOrNull(ref.index) ?: stops.firstOrNull { it.name == ref.name }
            }
            if (s != null) {
                ResolvedPlace(s.name, s.lat, s.lng)
            } else {
                ResolvedPlace(ref.name, 0.0, 0.0)
            }
        }
        is PlaceRef.StreetRef -> {
            val first = ref.pts.firstOrNull() ?: (0.0 to 0.0)
            ResolvedPlace(ref.name, first.first, first.second, ref.pts)
        }
    }

    /** Closest point of a place (point or street) to a coordinate. */
    fun closestPointOf(place: ResolvedPlace, lat: Double, lng: Double): Pair<Pair<Double, Double>, Double> {
        val pts = place.pts
        if (pts.isNullOrEmpty()) {
            val d = haversine(lat, lng, place.lat, place.lng)
            return (place.lat to place.lng) to d
        }
        var best = (place.lat to place.lng) to Double.MAX_VALUE
        for (p in pts) {
            val m = haversine(lat, lng, p.first, p.second)
            if (m < best.second) best = p to m
        }
        return best
    }

    /** Which schedule pattern runs on a date; falls back by weekday outside the horizon. */
    fun resolvePattern(world: World, date: String): Triple<String, Boolean, String> {
        val pid = world.calendar.days[date]
        if (pid != null && world.meta.patterns.containsKey(pid)) {
            return Triple(pid, true, world.meta.patterns.getValue(pid).type)
        }
        val dow = runCatching { isoToDate(date).dayOfWeek.value % 7 }.getOrDefault(1) // 0=Sun
        val wanted = when (dow) {
            0 -> listOf("sun", "special")
            6 -> listOf("sat", "special")
            else -> listOf("workday", "special")
        }
        for (w in wanted) {
            val found = world.meta.patterns.entries.firstOrNull { it.value.type == w }
            if (found != null) return Triple(found.key, false, found.value.type)
        }
        val first = world.meta.patterns.keys.firstOrNull() ?: ""
        return Triple(first, false, world.meta.patterns[first]?.type ?: "")
    }

    fun plan(world: World, input: PlanInput): PlanOutcome {
        val stops = world.stops
        val origin = resolvePlace(input.origin, stops)
        val dest = resolvePlace(input.dest, stops)
        val date = input.date ?: todayStr()
        val (pattern0, exact0, type0) = resolvePattern(world, date)
        val pattern = pattern0.ifEmpty { world.trips.keys.firstOrNull() ?: "" }
        val trips = world.trips[pattern].orEmpty()
        val built = connectionsFor(pattern, trips)

        // walking-only option — measure between the nearest points of streets/POIs,
        // not between the centroids of their bounding polylines
        val straight = run {
            val (originPoint, _) = closestPointOf(origin, dest.lat, dest.lng)
            val (destPoint, _) = closestPointOf(dest, originPoint.first, originPoint.second)
            haversine(originPoint.first, originPoint.second, destPoint.first, destPoint.second)
        }
        val walkOnly = if (straight <= 3000) {
            WalkOnly(
                minutes = walkMinutes(straight),
                meters = straight * WALK_DETOUR,
                leaveAt = input.departMin,
                arriveAt = input.departMin + walkMinutes(straight),
            )
        } else null

        // candidate stops around origin / destination
        val access = ArrayList<Triple<Stop, Double, Int>>() // stop, meters, minutes
        for (s in stops) {
            if (s.onlyDisembarking) continue
            val m = closestPointOf(origin, s.lat, s.lng).second
            if (m <= input.walkRadiusM) {
                access += Triple(s, m, if (m < TRIVIAL_WALK_M) 0 else walkMinutes(m))
            }
        }
        val egress = HashMap<Int, Triple<Double, Int, Pair<Double, Double>>>() // stopIdx -> meters, min, point
        for (s in stops) {
            val (point, m) = closestPointOf(dest, s.lat, s.lng)
            if (m <= input.walkRadiusM) {
                egress[s.i] = Triple(m, if (m < TRIVIAL_WALK_M) 0 else walkMinutes(m), point)
            }
        }

        val itineraries = ArrayList<Itinerary>()
        if (access.isNotEmpty() && egress.isNotEmpty()) {
            val signatures = HashSet<String>()
            var t = input.departMin
            for (round in 0 until 6) {
                if (itineraries.size >= input.maxResults) break
                val labels = relax(present(access, t, stops.size), built, input.maxTransfers + 1)

                val options = evaluate(world, built, labels, egress, origin, dest, pattern)
                if (options.isEmpty()) break

                val primary = options[0]
                val secondary = options.firstOrNull {
                    it.transfers < primary.transfers && it.arriveAt <= primary.arriveAt + 6
                }

                var added = false
                for (o in listOfNotNull(primary, secondary)) {
                    val sig = signature(o)
                    if (!signatures.add(sig)) continue
                    o.tag = if (itineraries.isEmpty()) Tag.BEST.value else Tag.NEXT.value
                    if (itineraries.isNotEmpty() && o.transfers < itineraries[0].transfers) {
                        o.tag = Tag.FEWER_TRANSFERS.value
                    }
                    itineraries += o
                    added = true
                    if (itineraries.size >= input.maxResults) break
                }

                // next round: after this journey's first vehicle
                val firstRide = primary.firstRide ?: break
                val nextT = firstRide.depMin + 1
                if (nextT <= t) break
                t = nextT
                if (!added && options.size == 1) continue
            }
        }

        // when walking straight beats every bus option, nothing is "best"
        if (walkOnly != null && itineraries.isNotEmpty() && walkOnly.arriveAt <= itineraries[0].arriveAt) {
            if (itineraries[0].tag == Tag.BEST.value) itineraries[0].tag = Tag.NEXT.value
        }

        return PlanOutcome(
            itineraries = itineraries,
            pattern = pattern,
            exactDay = exact0,
            patternType = type0,
            walkOnly = walkOnly,
            empty = itineraries.isEmpty(),
        )
    }

    // ------------------------------------------------------------------ guts

    private fun connectionsFor(pattern: String, trips: List<Trip>): Built {
        // plan() działa na Dispatchers.Default i może zostać równolegle przy
        // starcie nowego zapytania — dostęp do cache musi być zsynchronizowany
        val cached = synchronized(builtCache) { builtCache[pattern] }
        if (cached != null && cached.trips === trips) return cached
        val conns = ArrayList<Connection>()
        trips.forEachIndexed { tripIdx, tr ->
            for (i in 0 until tr.times.size - 1) {
                val (from, dep) = tr.times[i]
                val (to, arr) = tr.times[i + 1]
                conns += Connection(from, to, dep, arr, tripIdx, i)
            }
        }
        conns.sortWith(compareBy<Connection> { it.dep }.thenBy { it.arr })
        val built = Built(trips, conns)
        synchronized(builtCache) { builtCache[pattern] = built }
        return built
    }

    /** Access labels for a departure time. */
    private fun present(
        access: List<Triple<Stop, Double, Int>>,
        departMin: Int,
        stopCount: Int,
    ): Array<Label?> {
        val labels = arrayOfNulls<Label>(stopCount)
        for (a in access) {
            val (stop, meters, minutes) = a
            val arr = departMin + minutes
            val existing = labels[stop.i]
            if (existing == null || arr < existing.arr) {
                labels[stop.i] = Label(arr, 0, stop.i, null, null, meters, Access(meters, minutes))
            }
        }
        return labels
    }

    private fun relax(labels: Array<Label?>, built: Built, maxBoardings: Int): Array<Label?> {
        val conns = built.conns
        for (pass in 0 until MAX_RELAX_PASSES) {
            var improved = false
            for (c in conns) {
                val from = labels[c.from] ?: continue
                val sameTrip = from.ride != null && from.ride.tripIdx == c.tripIdx && from.ride.seq == c.seq - 1
                if (c.dep < from.arr) continue
                val boardings = if (sameTrip) from.boardings else from.boardings + 1
                if (boardings > maxBoardings) continue
                val to = labels[c.to]
                if (to == null || c.arr < to.arr || (c.arr == to.arr && boardings < to.boardings)) {
                    labels[c.to] = Label(
                        arr = c.arr, boardings = boardings, stopIdx = c.to, prev = from, ride = c,
                        walkMeters = from.walkMeters, access = null,
                    )
                    improved = true
                }
            }
            if (!improved) break
        }
        return labels
    }

    /** Evaluate arrival candidates and turn the best few into itineraries. */
    private fun evaluate(
        world: World,
        built: Built,
        labels: Array<Label?>,
        egress: Map<Int, Triple<Double, Int, Pair<Double, Double>>>,
        origin: ResolvedPlace,
        dest: ResolvedPlace,
        pattern: String,
    ): List<Itinerary> {
        data class Cand(val stop: Int, val arriveAt: Int, val transfers: Int)

        val cands = ArrayList<Cand>()
        for ((stopIdx, e) in egress) {
            val l = labels[stopIdx] ?: continue
            cands += Cand(stopIdx, l.arr + e.second, maxOf(0, l.boardings - 1))
        }
        cands.sortWith(compareBy<Cand> { it.arriveAt }.thenBy { it.transfers })

        val out = ArrayList<Itinerary>()
        val seenFirst = HashSet<String>()
        for (cand in cands.take(60)) {
            val it = buildItinerary(world, built, labels, egress, origin, dest, pattern, cand.stop) ?: continue
            val first = it.firstRide
            val key = if (first != null) "${first.tripId}:${first.boardStop.i}:${it.leaveAt}" else "w:${it.arriveAt}"
            if (!seenFirst.add(key)) continue
            out += it
            if (out.size >= 8) break
        }
        return out
    }

    private fun buildItinerary(
        world: World,
        built: Built,
        labels: Array<Label?>,
        egress: Map<Int, Triple<Double, Int, Pair<Double, Double>>>,
        origin: ResolvedPlace,
        dest: ResolvedPlace,
        pattern: String,
        finalStop: Int,
    ): Itinerary? {
        val stops = world.stops
        val e = egress[finalStop] ?: return null

        // walk back through labels, oldest first
        val chain = ArrayList<Label>()
        var node = labels[finalStop]
        var guard = 0
        while (node != null && guard++ < 200) {
            chain += node
            node = node.prev
        }
        chain.reverse()
        val accessLabel = chain.firstOrNull() ?: return null
        if (accessLabel.access == null) return null

        // merge consecutive hops of the same trip into one ride
        val runs = ArrayList<ArrayList<Label>>()
        for (l in chain) {
            val ride = l.ride ?: continue
            val run = runs.lastOrNull()
            val prevRide = run?.lastOrNull()?.ride
            if (run != null && prevRide != null && prevRide.tripIdx == ride.tripIdx && ride.from == prevRide.to) {
                run += l
            } else {
                runs += arrayListOf(l)
            }
        }
        if (runs.isEmpty()) return null

        val firstConn = runs[0][0].ride ?: return null
        val boardStop = stops[firstConn.from]
        if (accessLabel.stopIdx != firstConn.from) return null

        val legs = ArrayList<Leg>()
        val accessMinutes = accessLabel.access.minutes
        val firstRideDep = firstConn.dep
        val leaveAt = firstRideDep - accessMinutes

        if (accessLabel.access.meters >= TRIVIAL_WALK_M) {
            legs += WalkLeg(
                fromName = origin.name, toName = boardStop.name,
                meters = accessLabel.access.meters, minutes = accessMinutes,
                depMin = leaveAt, arrMin = firstRideDep,
                // marsz zaczyna się w punkcie najbliższym przystankowi (ulica/sklep/GPS)
                fromPt = closestPointOf(origin, boardStop.lat, boardStop.lng).first,
                toPt = boardStop.lat to boardStop.lng,
            )
        }

        for (run in runs) {
            val c0 = run.first().ride ?: continue
            val cN = run.last().ride ?: continue
            val trip = built.trips[c0.tripIdx]
            val stopList = ArrayList<RideStop>()
            for (k in c0.seq..(cN.seq + 1)) {
                val (si, min) = trip.times[k]
                stopList += RideStop(stops[si], min)
            }
            legs += RideLeg(
                tripId = trip.id, line = trip.line, dir = trip.dir,
                boardStop = stops[c0.from], alightStop = stops[cN.to],
                depMin = c0.dep, arrMin = cN.arr, stops = stopList,
            )
        }

        val lastConn = runs.last().last().ride ?: return null
        val lastStop = stops[lastConn.to]
        val arriveAt = lastConn.arr + e.second
        if (e.first >= TRIVIAL_WALK_M) {
            legs += WalkLeg(
                fromName = lastStop.name, toName = dest.name,
                meters = e.first, minutes = e.second,
                depMin = lastConn.arr, arrMin = arriveAt,
                fromPt = lastStop.lat to lastStop.lng,
                toPt = e.third, // najbliższy punkt celu (ulica/GPS)
            )
        }

        val walkMeters = legs.sumOf { if (it is WalkLeg) it.meters else 0.0 }
        val walkMinutes = legs.sumOf { if (it is WalkLeg) it.minutes else 0 }
        val boardings = legs.count { it is RideLeg }
        val firstRideIdx = legs.indexOfFirst { it is RideLeg }

        // Identyfikator trafia do klucza LazyColumn i stanu rozwinięcia.
        // Sam numer kursu + przystanek startowy nie wystarcza, gdy dwa warianty
        // różnią się dopiero na przesiadce — składamy go z całej trasy.
        val itineraryId = buildString {
            append(pattern).append('-').append(leaveAt).append('-').append(arriveAt)
            for (leg in legs) {
                when (leg) {
                    is RideLeg -> append("-R").append(leg.tripId).append('@').append(leg.boardStop.i)
                    is WalkLeg -> append("-W").append(leg.minutes)
                }
            }
        }

        return Itinerary(
            id = itineraryId,
            legs = legs, leaveAt = leaveAt, arriveAt = arriveAt,
            transfers = maxOf(0, boardings - 1), walkMeters = walkMeters, walkMinutes = walkMinutes,
            firstRideLeg = firstRideIdx, pattern = pattern, tag = Tag.BEST.value,
        )
    }

    private fun signature(it: Itinerary): String =
        it.legs.joinToString(">") { l ->
            when (l) {
                is RideLeg -> "${l.tripId}@${l.boardStop.i}"
                is WalkLeg -> "walk${l.minutes}"
            }
        }
}
