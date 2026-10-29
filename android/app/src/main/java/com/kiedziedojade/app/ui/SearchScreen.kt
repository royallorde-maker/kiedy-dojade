package com.kiedziedojade.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.SearchItem
import com.kiedziedojade.app.core.WARSAW
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.buildIndex
import com.kiedziedojade.app.core.firstStopItem
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.isOutsideCoverage
import com.kiedziedojade.app.core.resolveDeparture
import com.kiedziedojade.app.update.rememberIsOnline
import java.time.ZonedDateTime

@Composable
fun SearchScreen(
    world: World,
    from: PlaceRef?,
    to: PlaceRef?,
    departMin: Int?,
    announcements: List<Api.Announcement>,
    onDismissAnnouncement: (Int) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onUseGps: () -> Unit,
    onPickTime: () -> Unit,
    onUseNow: () -> Unit,
    onFind: () -> Unit,
    onQuickDestination: (SearchItem) -> Unit,
) {
    val online = rememberIsOnline()
    val index = remember(world) { buildIndex(world) }
    val outsideCoverage = isOutsideCoverage(world, from) || isOutsideCoverage(world, to)
    // Zapytanie zostaje po polsku (tak nazywają się przystanki), a etykieta
    // jest krótka i tłumaczona — dzięki temu kafelki nie ucinają nazw ulic.
    val quick = listOf(
        t("Dworzec PKP", "Station") to "Dworzec PKP",
        t("Szpital", "Hospital") to "Szpital",
        t("Targowisko", "Market") to "Targowisko",
        t("Ratusz", "Town hall") to "Ratusz",
    ).mapNotNull { (label, query) ->
        firstStopItem(index, query)?.let { QuickDestinationEntry(label, it) }
    }.distinctBy { it.item.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Appear(0) {
                HeroCard(
                    greeting = greetingPrefix() + "!",
                    headline = t("Dokąd jedziemy?", "Where are we going?"),
                    caption = t("Szczecinek · autobusy miejskie", "Szczecinek · city buses"),
                    offline = !online,
                )
            }
        }

        items(announcements, key = { "a" + it.id }) { a ->
            Appear(1) {
                AnnouncementCard(
                    announcement = a,
                    onDismiss = { onDismissAnnouncement(a.id) },
                )
            }
        }

        item {
            Appear(1) {
                KdCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                RouteField(
                                    label = t("Skąd", "From"),
                                    value = from?.name?.let(::compactPlaceName),
                                    placeholder = t("Wybierz start", "Pick a start"),
                                    icon = Icons.Filled.ArrowUpward,
                                    onClick = onPickFrom,
                                    trailingIcon = Icons.Filled.MyLocation,
                                    trailingDescription = t("Użyj mojej lokalizacji", "Use my location"),
                                    trailingSelected = from is PlaceRef.Gps && !from.custom,
                                    onTrailingClick = onUseGps,
                                )
                                Spacer(Modifier.height(8.dp))
                                RouteField(
                                    label = t("Dokąd", "To"),
                                    value = to?.name?.let(::compactPlaceName),
                                    placeholder = t("Dokąd jedziemy?", "Where to?"),
                                    icon = Icons.Filled.ArrowDownward,
                                    onClick = onPickTo,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = onSwap,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.SwapVert,
                                        contentDescription = t("Zamień", "Swap"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimeChip(
                                label = t("Teraz", "Now"),
                                icon = Icons.Filled.Schedule,
                                selected = departMin == null,
                                onClick = onUseNow,
                            )
                            TimeChip(
                                label = departMin?.let { fmtTime(it) } ?: t("Godzina", "Time"),
                                icon = hourGlass,
                                selected = departMin != null,
                                onClick = onPickTime,
                            )
                        }
                        if (departMin != null) {
                            val dep = resolveDeparture(
                                ZonedDateTime.now(WARSAW),
                                departMin,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                t("Wyjazd ", "Leave ") +
                                    relativeDay(dep.serviceDate).lowercase() +
                                    t(" o ", " at ") +
                                    fmtTime(dep.departMin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                        if (outsideCoverage) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t(
                                    "Ta trasa jest poza Szczecinkiem — aplikacja obsługuje tylko autobusy miejskie w Szczecinku.",
                                    "This route is outside Szczecinek — the app only covers Szczecinek city buses.",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Appear(2) {
                Surface(
                    onClick = onFind,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    contentColor = KD_ON_HERO,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(kdHeroBrush()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                t("Znajdź połączenie", "Find a connection"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        item { SectionTitle(t("Popularne cele", "Popular destinations")) }

        item {
            Appear(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { entry ->
                                QuickDestination(
                                    entry = entry,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onQuickDestination(entry.item) },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Text(
                t(
                    "Dane rozkładowe z KiedyPrzyjedzie.pl (Komunikacja Miejska Szczecinek). Aplikacja nieoficjalna.",
                    "Schedule data from KiedyPrzyjedzie.pl (Szczecinek city transport). Unofficial app.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * Karta „hero” ekranu startowego — ciepły gradient ze szklanym refleksem
 * i małym biletem w rogu (jak karta płatnicza w aplikacjach finansowych).
 */
@Composable
private fun HeroCard(
    greeting: String,
    headline: String,
    caption: String,
    offline: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(kdHeroBrush()),
    ) {
        // szklany refleks od góry — jak światło na wypukłej karcie
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.34f),
                        0.55f to Color.Transparent,
                    ),
                ),
        )
        // mały „bilet” w prawym dolnym rogu
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.20f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = KD_ON_HERO,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 18.dp, top = 16.dp, bottom = 16.dp, end = 76.dp)) {
            Text(
                greeting,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = KD_ON_HERO.copy(alpha = 0.72f),
            )
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KD_ON_HERO,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp,
                color = KD_ON_HERO.copy(alpha = 0.72f),
            )
            if (offline) {
                Spacer(Modifier.height(8.dp))
                StatusChip(
                    text = t("Brak sieci — pokazuję rozkład", "No network — showing the timetable"),
                    color = KD_ON_HERO,
                    background = Color.White.copy(alpha = 0.30f),
                    icon = Icons.Filled.WifiOff,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementCard(    announcement: Api.Announcement,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable(announcement.id) { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.10f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    t("Komunikat", "Announcement"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    announcement.plain(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expanded && hasMore != result.hasVisualOverflow) {
                            hasMore = result.hasVisualOverflow
                        }
                    },
                )
                if (hasMore || expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { expanded = !expanded },
                    ) {
                        Text(
                            if (expanded) t("Zwiń", "Show less") else t("Czytaj dalej", "Read more"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = t("Zamknij", "Close"),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private class QuickDestinationEntry(
    val label: String,
    val item: SearchItem,
)

@Composable
private fun QuickDestination(
    entry: QuickDestinationEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Krótki podpis z kodem zamiast ucinanej nazwy ulicy — kod i tak jest
    // najważniejszy na tablicy przystankowej.
    val subtitle = entry.item.code?.let { t("Przystanek ", "Stop ") + it } ?: entry.item.name
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RouteField(
    label: String,
    value: String?,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingDescription: String? = null,
    trailingSelected: Boolean = false,
    onTrailingClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailingIcon != null && onTrailingClick != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    onClick = onTrailingClick,
                    shape = CircleShape,
                    color = if (trailingSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            trailingIcon,
                            contentDescription = trailingDescription,
                            tint = if (trailingSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
