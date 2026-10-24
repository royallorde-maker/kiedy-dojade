package com.kiedziedojade.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.SavedPlace
import com.kiedziedojade.app.core.SearchItem
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.buildIndex
import com.kiedziedojade.app.core.firstStopItem
import com.kiedziedojade.app.core.searchPlaces

/** Pełnoekranowy wybór miejsca: przystanki, ulice, punkty (offline). */
@Composable
fun PickerScreen(
    world: World,
    prefs: Prefs,
    title: String,
    current: PlaceRef?,
    allowGps: Boolean,
    onGps: () -> Unit,
    onPickOnMap: (() -> Unit)? = null,
    onPick: (PlaceRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val index = remember(world) { buildIndex(world) }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 10.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = t("Zamknij", "Close"))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .focusRequester(focus),
            placeholder = { Text(t("Szukaj miejsca", "Search places"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        val q = query.trim()
        val favs = prefs.favorites
        val recents = prefs.recents
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            if (allowGps && q.isEmpty()) {
                item {
                    PlaceRow(
                        icon = Icons.Filled.MyLocation,
                        name = t("Moja lokalizacja", "My location"),
                        sub = t("Użyj pozycji telefonu", "Use the phone's position"),
                        selected = (current as? PlaceRef.Gps)?.custom == false,
                        onClick = onGps,
                    )
                }
            }
            if (onPickOnMap != null && q.isEmpty()) {
                item {
                    PlaceRow(
                        icon = Icons.Filled.Map,
                        name = t("Wskaż na mapie", "Pick on map"),
                        sub = t("Dowolny punkt, nie tylko przystanek", "Any point, not just a stop"),
                        onClick = onPickOnMap,
                    )
                }
            }
            if (q.length < 2) {
                if (favs.isNotEmpty()) {
                    item { SectionTitle(t("Ulubione", "Favourites")) }
                    items(favs, key = { "f" + it.key() }) { p ->
                        SavedRow(world, prefs, p, current, onPick)
                    }
                }
                if (recents.isNotEmpty()) {
                    item { SectionTitle(t("Ostatnio używane", "Recently used")) }
                    items(recents.filter { r -> favs.none { it.samePlace(r) } }, key = { "r" + it.key() }) { p ->
                        SavedRow(world, prefs, p, current, onPick)
                    }
                }
                if (favs.isEmpty() && recents.isEmpty()) {
                    item {
                        SectionTitle(t("Podpowiedzi", "Suggestions"))
                    }
                    val hints = quickStops(world, index)
                    items(hints, key = { "q" + it.id }) { item ->
                        ResultRow(world, prefs, item, onPick)
                    }
                }
            } else {
                val results = searchPlaces(index, q, limit = 20)
                if (results.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                t("Brak wyników dla „$q”", "No results for \u201E$q\u201D"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(results, key = { it.id }) { item ->
                    ResultRow(world, prefs, item, onPick)
                }
            }
        }
    }
}

private fun quickStops(world: World, index: List<SearchItem>): List<SearchItem> =
    listOf("Dworzec PKP", "Szpital", "Targowisko", "Rynek")
        .mapNotNull { firstStopItem(index, it) }
        .distinctBy { it.id }

@Composable
private fun SavedRow(
    world: World,
    prefs: Prefs,
    place: SavedPlace,
    current: PlaceRef?,
    onPick: (PlaceRef) -> Unit,
) {
    val icon = when (place.kind) {
        "stop" -> Icons.Filled.DirectionsBus
        "poi" -> Icons.Filled.Storefront
        "point" -> Icons.Filled.Map
        "street" -> Icons.Filled.Place
        else -> Icons.Filled.MyLocation
    }
    val sub = when (place.kind) {
        "stop" -> (place.toRef(world) as? PlaceRef.StopRef)?.let { ref ->
            world.stops.getOrNull(ref.index)
        }?.let {
            t("Przystanek ", "Stop ") + it.code +
                if (it.onDemand) t(" · na żądanie", " · on request") else ""
        }
        "poi" -> poiKindLabel(place.pkind ?: "")
        "point" -> if (place.lat != null && place.lng != null) {
            String.format(java.util.Locale.US, "%.5f, %.5f", place.lat, place.lng)
        } else {
            t("punkt na mapie", "map point")
        }
        "street" -> t("Ulica", "Street")
        else -> t("Moja lokalizacja", "My location")
    }
    PlaceRow(
        icon = icon,
        name = place.name,
        sub = sub,
        selected = place.toRef(world) == current,
        onClick = { place.toRef(world)?.let(onPick) },
    )
}

@Composable
private fun ResultRow(world: World, prefs: Prefs, item: SearchItem, onPick: (PlaceRef) -> Unit) {
    val icon = when (item.kind) {
        "stop" -> Icons.Filled.DirectionsBus
        "street" -> Icons.Filled.Place
        else -> kindIcon(item.pkind)
    }
    val sub = when (item.kind) {
        "stop" -> t("Przystanek ", "Stop ") + item.code +
            if (item.onDemand) t(" · na żądanie", " · on request") else ""
        "street" -> t("ulica", "street")
        else -> poiKindLabel(item.pkind ?: "")
    }
    val saved = SavedPlace.from(item.ref, world)
    PlaceRow(
        icon = icon,
        name = item.name,
        sub = sub,
        trailing = if (saved != null && item.kind != "street") {
            {
                val isFav = prefs.isFavorite(saved)
                IconButton(onClick = {
                    prefs.toggleFavorite(saved)
                }) {
                    Icon(
                        if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = t("Ulubione", "Favourite"),
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        onClick = {
            val savedPlace = SavedPlace.from(item.ref, world)
            if (savedPlace != null) prefs.pushRecent(savedPlace)
            onPick(item.ref)
        },
    )
}

@Composable
private fun PlaceRow(
    icon: ImageVector,
    name: String,
    sub: String?,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

fun kindIcon(kind: String?): ImageVector = when (kind) {
    "food" -> Icons.Filled.LocalFireDepartment
    "shop" -> Icons.Filled.Storefront
    "transit" -> Icons.Filled.DirectionsBus
    "school", "culture" -> Icons.Filled.Place
    else -> Icons.Filled.Place
}

fun poiKindLabel(kind: String): String = when (kind) {
    "office" -> t("biuro / urząd", "office")
    "shop" -> t("sklep", "shop")
    "hotel" -> t("hotel", "hotel")
    "services" -> t("usługi", "services")
    "leisure" -> t("rekreacja", "leisure")
    "health" -> t("zdrowie", "health")
    "food" -> t("gastronomia", "food")
    "place" -> t("miejsce", "place")
    "culture" -> t("kultura", "culture")
    "transit" -> t("komunikacja", "transit")
    "school" -> t("szkoła", "school")
    "worship" -> t("kult religijny", "worship")
    "fuel" -> t("stacja paliw", "fuel")
    "parking" -> t("parking", "parking")
    else -> t("punkt", "point")
}

/** Ikona ulubionych w wierszu przystanku (używana na ekranie przystanku). */
@Composable
fun FavoriteStar(prefs: Prefs, place: SavedPlace) {
    val isFav = remember(prefs.favoritesVersion, place) { prefs.isFavorite(place) }
    IconButton(onClick = { prefs.toggleFavorite(place) }) {
        Icon(
            if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = t("Ulubione", "Favourite"),
            tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chip wyboru (godzina, język, styl mapy). Zaznaczenie jest tonalne, żeby
 * mocny kolor podstawowy zostawał dla głównego przycisku akcji.
 */
@Composable
fun TimeChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

internal val hourGlass = Icons.Filled.AccessTime
