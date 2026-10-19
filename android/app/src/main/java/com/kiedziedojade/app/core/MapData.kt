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
