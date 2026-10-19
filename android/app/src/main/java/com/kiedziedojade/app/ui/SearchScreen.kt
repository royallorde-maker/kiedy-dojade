package com.kiedziedojade.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.SearchItem
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.buildIndex
import com.kiedziedojade.app.core.firstStopItem
import com.kiedziedojade.app.core.fmtDate
import com.kiedziedojade.app.core.todayStr
import com.kiedziedojade.app.update.rememberIsOnline

@Composable
fun SearchScreen(
    world: World,
    from: PlaceRef?,
    to: PlaceRef?,
    date: String?,
    announcements: List<Api.Announcement>,
    onDismissAnnouncement: (Int) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onSwap: () -> Unit,
    onUseGps: () -> Unit,
    onPickDate: () -> Unit,
    onClearDate: () -> Unit,
    onFind: () -> Unit,
    onQuickDestination: (SearchItem) -> Unit,
) {
    val online = rememberIsOnline()
    val index = remember(world) { buildIndex(world) }
    val quick = remember(world) {
        listOf("Dworzec PKP", "Szpital", "Targowisko", "Rynek")
            .mapNotNull { firstStopItem(index, it) }
            .distinctBy { it.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Appear(0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "KiedyDojadę",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            t("SZCZECINEK · autobusy miejskie", "SZCZECINEK · city buses"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                        )
                    }
                    if (!online) {
                        StatusChip(
                            t("offline", "offline"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        items(announcements, key = { "a" + it.id }) { a ->
            Appear(1) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t("Komunikat", "Announcement"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                a.plain(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onDismissAnnouncement(a.id) }) {
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
        }

        item {
            Appear(1) {
                KdCard {
                    Column {
                        RouteField(
                            label = t("Skąd", "From"),
                            value = from?.name,
                            placeholder = t("Wybierz start", "Pick a start"),
                            icon = Icons.Filled.ArrowUpward,
                            onClick = onPickFrom,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box {
                            RouteField(
                                label = t("Dokąd", "To"),
                                value = to?.name,
                                placeholder = t("Dokąd jedziesz?", "Where to?"),
                                icon = Icons.Filled.ArrowDownward,
                                onClick = onPickTo,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onSwap) {
                                Icon(Icons.Filled.SwapVert, contentDescription = t("Zamień", "Swap"))
                            }
                            TimeChip(
                                label = t("Teraz", "Now"),
                                icon = Icons.Filled.Schedule,
                                selected = date == null,
                                onClick = onClearDate,
                            )
                            TimeChip(
                                label = date?.let { fmtDate(it, I18n.pl) } ?: t("Inny dzień", "Another day"),
                                icon = Icons.Filled.CalendarMonth,
                                selected = date != null,
                                onClick = onPickDate,
                            )
                        }
                    }
                }
            }
        }

        item {
            Appear(2) {
                Button(
                    onClick = onFind,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        t("Znajdź połączenie", "Find a connection"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        item {
            Appear(3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    TimeChip(
                        label = t("Moja lokalizacja", "My location"),
                        icon = Icons.Filled.MyLocation,
                        selected = from is PlaceRef.Gps,
                        onClick = onUseGps,
                    )
                }
            }
        }

        item { SectionTitle(t("Popularne cele", "Popular destinations")) }

        items(quick, key = { "q" + it.id }) { item ->
            val stopIdx = (item.ref as? PlaceRef.StopRef)?.index
            Appear(4) {
                KdCard(onClick = { onQuickDestination(item) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.DirectionsBus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                t("Przystanek ", "Stop ") + item.code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            t("wybierz →", "pick →"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier.padding(top = 10.dp),
            )
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
        }
    }
}
