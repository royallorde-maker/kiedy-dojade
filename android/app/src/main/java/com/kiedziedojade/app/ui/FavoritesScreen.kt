package com.kiedziedojade.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.SavedRoute
import com.kiedziedojade.app.core.fmtTime

/** Zakładka „Ulubione”: zapisane trasy z własną godziną odjazdu. */
@Composable
fun FavoritesScreen(
    prefs: Prefs,
    onOpenRoute: (SavedRoute) -> Unit,
) {
    val routes = prefs.favoriteRoutes
    var editing by remember { mutableStateOf<SavedRoute?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = t("Ulubione", "Favourites"),
                subtitle = t("Twoje zapisane trasy i godziny", "Your saved routes and times"),
            )
        }

        if (routes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.StarBorder,
                    title = t("Brak ulubionych tras", "No favourite routes"),
                    text = t(
                        "Wyszukaj połączenie i dotknij gwiazdki w nagłówku wyników, aby zapisać trasę. Potem ustawisz tutaj własną godzinę odjazdu.",
                        "Search a connection and tap the star in the results header to save it. Then set a custom departure time here.",
                    ),
                )
            }
        } else {
            items(routes, key = { "route|" + it.from.key() + "->" + it.to.key() }) { route ->
                Appear(0) {
                    FavoriteRouteCard(
                        route = route,
                        onOpen = { onOpenRoute(route) },
                        onPickTime = { editing = route },
                        onRemove = { prefs.removeFavoriteRoute(route) },
                    )
                }
            }
        }
    }

    editing?.let { route ->
        TimePickDialog(
            initial = route.departMin,
            onDismiss = { editing = null },
            onPick = { minute ->
                prefs.setFavoriteRouteTime(route, minute)
                editing = null
            },
            onUseNow = { prefs.setFavoriteRouteTime(route, null) },
        )
    }
}

@Composable
private fun FavoriteRouteCard(
    route: SavedRoute,
    onOpen: () -> Unit,
    onPickTime: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(
                Icons.Filled.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    route.from.name + "  →  " + route.to.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteTimeChip(
                        departMin = route.departMin,
                        onClick = onPickTime,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t("Dotknij, aby wyszukać", "Tap to search"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = t("Usuń z ulubionych", "Remove from favourites"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Godzina odjazdu zapisanej trasy; dotknięcie otwiera wybór godziny. */
@Composable
private fun FavoriteTimeChip(departMin: Int?, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val custom = departMin != null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (custom) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (custom) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                departMin?.let { fmtTime(it) } ?: t("Teraz", "Now"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
