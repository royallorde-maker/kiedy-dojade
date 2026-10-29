package com.kiedziedojade.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

        // ------ stos wyszukiwania: jedno szkło, dwa pola, przekładka na szwie ------
        item {
            Appear(1) {
                Column {
                    SearchStack(
                        fromValue = from?.name?.let(::compactPlaceName),
                        toValue = to?.name?.let(::compactPlaceName),
                        onPickFrom = onPickFrom,
                        onPickTo = onPickTo,
                        onUseGps = onUseGps,
                        gpsSelected = from is PlaceRef.Gps && !from.custom,
                        onSwap = onSwap,
                    )
                    Spacer(Modifier.height(10.dp))
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

        item {
            Appear(2) {
                // CTA: pełna pigułka w sygnaturowej bieli
                Surface(
                    onClick = onFind,
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = KD_ON_HERO,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(kdHeroBrush())
                            .border(1.dp, kdHeroEdge(), CircleShape),
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

        // ------ pozioma szyna celów: wysokie kafelki, ikona nad nazwą ------
        item {
            Appear(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    quick.forEach { entry ->
                        QuickDestination(
                            entry = entry,
                            onClick = { onQuickDestination(entry.item) },
                        )
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
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
            .clip(RoundedCornerShape(30.dp))
            .background(kdHeroBrush())
            .border(1.dp, kdHeroEdge(), RoundedCornerShape(30.dp)),
    ) {
        // mały „bilet” w prawym dolnym rogu
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = KD_ON_HERO.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, KD_ON_HERO.copy(alpha = 0.12f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
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
        Column(modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 78.dp)) {
            Text(
                greeting,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = KD_ON_HERO.copy(alpha = 0.72f),
            )
            Text(
                headline,
                style = MaterialTheme.typography.headlineMedium,
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
                    background = KD_ON_HERO.copy(alpha = 0.06f),
                    icon = Icons.Filled.WifiOff,
                )
            }
        }
    }
}

/** Wspólne szkło stosu wyszukiwania — patrz GlassPane w Common.kt. */

/**
 * Stos „skąd / dokąd” w jednym szkle: pola przedzielone włoską kreską,
 * a przycisk zamiany siedzi dokładnie na środku szwu — z dala od przycisku
 * GPS w polu „skąd”, żeby nic się nie nakładało.
 */
@Composable
private fun SearchStack(
    fromValue: String?,
    toValue: String?,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onUseGps: () -> Unit,
    gpsSelected: Boolean,
    onSwap: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        GlassPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                StackField(
                    label = t("Skąd", "From"),
                    value = fromValue,
                    placeholder = t("Wybierz start", "Pick a start"),
                    icon = Icons.Filled.ArrowUpward,
                    onClick = onPickFrom,
                    trailingIcon = Icons.Filled.MyLocation,
                    trailingDescription = t("Użyj mojej lokalizacji", "Use my location"),
                    trailingSelected = gpsSelected,
                    onTrailingClick = onUseGps,
                )
                // szew: włoska kreska, którą w środku przecina bursztynowa przekładka
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = 56.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                )
                StackField(
                    label = t("Dokąd", "To"),
                    value = toValue,
                    placeholder = t("Dokąd jedziemy?", "Where to?"),
                    icon = Icons.Filled.ArrowDownward,
                    onClick = onPickTo,
                )
            }
        }
        // przekładka na szwie — mała, biała, dokładnie na środku
        Surface(
            onClick = onSwap,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(40.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(kdHeroBrush(), CircleShape)
                        .border(1.dp, kdHeroEdge(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = t("Zamień", "Swap"),
                        tint = KD_ON_HERO,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StackField(
    label: String,
    value: String?,
    placeholder: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    trailingSelected: Boolean = false,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
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
                fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null && onTrailingClick != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onTrailingClick,
                shape = CircleShape,
                color = if (trailingSelected) {
                    MaterialTheme.colorScheme.primary
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
                            MaterialTheme.colorScheme.onPrimary
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

@Composable
private fun AnnouncementCard(announcement: Api.Announcement, onDismiss: () -> Unit) {
    var expanded by rememberSaveable(announcement.id) { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    GlassPane(
        shape = RoundedCornerShape(24.dp),
        fill = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
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

/** Wysoki kafel na poziomej szynie: ikona nad krótką nazwą. */
@Composable
private fun QuickDestination(
    entry: QuickDestinationEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Krótki podpis z kodem zamiast ucinanej nazwy ulicy — kod i tak jest
    // najważniejszy na tablicy przystankowej.
    val subtitle = entry.item.code?.let { t("Przystanek ", "Stop ") + it } ?: entry.item.name
    GlassPane(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .width(118.dp)
            .clickable { onClick() },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(kdHeroBrush(), RoundedCornerShape(14.dp))
                    .border(1.dp, kdHeroEdge(), RoundedCornerShape(14.dp))
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = KD_ON_HERO,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                entry.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
