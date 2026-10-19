package com.kiedziedojade.app.core

/**
 * Styl kafelków mapy. Wszystkie warianty pochodzą z OpenStreetMap i nie
 * wymagają klucza API. Domyślny jest czytelny i najbardziej zbliżony do
 * zwykłej mapy miejskiej; pozostałe można wybrać w Ustawieniach.
 */
enum class MapStyle(
    val id: String,
    val tileBaseUrl: String,
    val attribution: String,
    val labelPl: String,
    val labelEn: String,
) {
    STANDARD(
        id = "standard",
        tileBaseUrl = "https://tile.openstreetmap.org/",
        attribution = "© OpenStreetMap contributors",
        labelPl = "Domyślny",
        labelEn = "Default",
    ),
    FRANCE(
        id = "france",
        tileBaseUrl = "https://a.tile.openstreetmap.fr/osmfr/",
        attribution = "© OpenStreetMap · OSM France",
        labelPl = "Kolorowy",
        labelEn = "Colourful",
    ),
    HOT(
        id = "hot",
        tileBaseUrl = "https://a.tile.openstreetmap.fr/hot/",
        attribution = "© OpenStreetMap · HOT / OSM France",
        labelPl = "HOT",
        labelEn = "HOT",
    );

    companion object {
        fun from(id: String?): MapStyle = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
