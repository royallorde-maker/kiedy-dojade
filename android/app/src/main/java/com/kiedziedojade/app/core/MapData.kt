package com.kiedziedojade.app.core

/**
 * Indeks danych dla ekranu mapy: lista linii, przystanki obsługiwane przez
 * każdą linię, trasa (najdłuższy kurs) do narysowania jako polilinia oraz
 * linie przejeżdżające przez dany przystanek. Liczone raz na zestaw danych.
 */
data class MapIndex(
    /** wszystkie linie w kolejności naturalnej: 1, 2, … 10, … */
    val lines: List<String>,
    /** linia → indeksy przystanków (oba kierunki) */
    val lineStops: Map<String, Set<Int>>,
    /** linia → przystanki najdłuższego kursu (zarys trasy na mapie) */
    val route: Map<String, List<Stop>>,
    /** przystanek → linie, które go obsługują */
    val stopLines: Map<Int, List<String>>,
) {
    /** Wszystkie przystanki, gdy żadna linia nie jest wybrana. */
    fun allStops(world: World): Set<Int> = world.stops.indices.toSet()

    /** Przystanki do pokazania przy filtrze [line] (albo wszystkie). */
    fun stopsFor(world: World, line: String?): Set<Int> =
        line?.let { lineStops[it] } ?: allStops(world)

    /**
     * Przystanki, o które pytamy tablice na żywo, aby znaleźć autobusy linii.
     * Interfejs API przyjmuje najwyżej sześć miejsc w jednym zapytaniu, więc
     * wybieramy równomiernie rozłożone przystanki najdłuższego kursu. Dzięki
     * temu każdy autobus w trasie ma szansę pojawić się na jednej z tablic.
     */
    fun probeStops(line: String?, limit: Int = 6): List<Stop> {
        if (limit <= 0) return emptyList()
        val pts = line?.let { route[it] }.orEmpty()
        if (pts.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Stop>()
        if (pts.size <= limit) {
            pts.forEach { out.putIfAbsent(it.id, it) }
        } else if (limit == 1) {
            out[pts.first().id] = pts.first()
        } else {
            for (i in 0 until limit) {
                val at = (i.toLong() * (pts.size - 1) / (limit - 1)).toInt()
                val stop = pts[at]
                out.putIfAbsent(stop.id, stop)
            }
        }
        // pętle i przystanki powtarzane w kursie mogą dać mniej niż [limit]
        if (out.size < limit) {
            pts.forEach { if (out.size < limit) out.putIfAbsent(it.id, it) }
        }
        return out.values.toList()
    }
}

fun mapIndex(world: World): MapIndex {
    val byLine = world.trips.values.asSequence().flatten().groupBy { it.line }
    val lines = byLine.keys.sortedWith(
        compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }),
    )
    val lineStops = byLine.mapValues { (_, trips) ->
        trips.flatMap { trip -> trip.times.map { it.first } }.toSet()
    }
    val route = byLine.mapValues { (_, trips) ->
        trips.maxByOrNull { it.times.size }
            ?.times?.map { world.stopAt(it.first) }
            .orEmpty()
    }
    val stopLines = buildMap<Int, MutableList<String>> {
        lineStops.forEach { (line, stops) ->
            stops.forEach { idx -> getOrPut(idx) { mutableListOf() }.add(line) }
        }
    }.mapValues { (_, v) -> v.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it })) }

    return MapIndex(lines, lineStops, route, stopLines)
}
