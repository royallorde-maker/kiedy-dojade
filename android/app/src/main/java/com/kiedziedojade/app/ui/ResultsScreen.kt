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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.PlanInput
import com.kiedziedojade.app.core.PlanOutcome
import com.kiedziedojade.app.core.Planner
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.Tag
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtDuration
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.isOutsideCoverage
import com.kiedziedojade.app.core.nowMinutes
import com.kiedziedojade.app.core.RoutedPaths
import com.kiedziedojade.app.core.itinerarySegments
import com.kiedziedojade.app.core.routedPaths
import com.kiedziedojade.app.core.matchLiveRide
import com.kiedziedojade.app.core.todayStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ResultsScreen(
    world: World,
    query: JourneyQuery,
    dark: Boolean,
    mapStyle: MapStyle = MapStyle.STANDARD,
    onBack: () -> Unit,
    onOpenStop: (Int) -> Unit,
    onNavigate: (Itinerary) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    // Po cichym odświeżeniu danych plan musi powstać od nowa: stare indeksy
    // przystanków mogłyby wskazywać zupełnie inne miejsce.
    val worldRev = world.meta.generatedAt
    var outcome by remember(query, worldRev) { mutableStateOf<PlanOutcome?>(null) }
    var expanded by remember(query, worldRev) { mutableStateOf<String?>(null) }
    val isToday = query.serviceDate == todayStr()
    val outsideCoverage = remember(world, query) {
        isOutsideCoverage(world, query.from) || isOutsideCoverage(world, query.to)
    }

    LaunchedEffect(query, worldRev) {
        outcome = withContext(Dispatchers.Default) {
            Planner.plan(
                world,
                PlanInput(
                    origin = query.from,
                    dest = query.to,
                    date = query.serviceDate,
                    departMin = query.departMin,
                ),
            )
        }
    }

    // live delays for the first vehicle of the best option — only for today
    var live by remember(query, worldRev) { mutableStateOf<Api.LiveRow?>(null) }
    LaunchedEffect(outcome, isToday) {
        if (!isToday) {
            live = null
            return@LaunchedEffect
        }
        val o = outcome ?: return@LaunchedEffect
        val first = o.itineraries.firstOrNull()?.firstRide ?: return@LaunchedEffect
        // opóźnienia zmieniają się w czasie — odświeżamy je po cichu, dopóki
        // ekran jest otwarty, zamiast pokazywać stan z chwili wejścia
        while (true) {
            live = runCatching { Api.stopBoard(first.boardStop.id) }.getOrNull()
                ?.let { matchLiveRide(it, first) }
            delay(30_000)
        }
    }

    val o = outcome

    // odliczanie na najlepszej karcie: zegar tyka dopóki ekran jest otwarty,
    // więc „za 2 min” nie zamiera w zamrożonej wartości z chwili wejścia
    val firstIt = o?.itineraries?.firstOrNull()
    var itinNow by remember(o, query) { mutableIntStateOf(nowMinutes()) }
    LaunchedEffect(o, query) {
        while (true) {
            itinNow = nowMinutes()
            delay(20_000)
        }
    }
    val countdown = if (isToday && firstIt != null) {
        val diff = firstIt.leaveAt - itinNow
        // poniżej zera (odjechał) milknie — nie zostawi zakrzepłego „odjeżdża”
        if (diff in 1..14) t("za $diff min", "in $diff min") else null
    } else {
        null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = t("Połączenia", "Connections"),
                subtitle = compactPlaceName(query.from.name) + "  →  " +
                    compactPlaceName(query.to.name),
                onBack = onBack,
                below = {
                    val title = buildString {
                        append(relativeDay(query.serviceDate))
                        append(' ')
                        append(fmtTime(query.departMin))
                        o?.let {
                            append("  ·  ")
                            append(patternLabel(it.patternType))
                            if (!it.exactDay) {
                                append(" (")
                                append(t("wg dnia tygodnia", "by weekday"))
                                append(')')
                            }
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (o != null && !o.exactDay) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                trailing = if (onToggleFavorite != null) {
                    {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = if (isFavorite) {
                                    t("Usuń z ulubionych", "Remove from favourites")
                                } else {
                                    t("Dodaj do ulubionych", "Add to favourites")
                                },
                                tint = if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }

        if (!isToday) {
            item {
                Text(
                    t("To plan na ", "This is the plan for ") +
                        relativeDay(query.serviceDate).lowercase() + ". " +
                        t(
                            "Dane na żywo i prowadzenie będą dostępne w dniu podróży.",
                            "Live data and navigation are available on the travel day.",
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        if (o == null) {
            item { LoadingBlock(t("Szukam połączeń…", "Searching connections…")) }
        } else {
            val best = o.itineraries.firstOrNull()
            val walkWins = o.walkOnly != null && (best == null || o.walkOnly.arriveAt <= best.arriveAt)

            // karta „Pieszo” tylko gdy autobusy nie jadą w ogóle — przy dostę-
            // pnych autobusach była tylko szumem nad listą wyników
            if (o.walkOnly != null && o.itineraries.isEmpty()) {
                item {
                    Appear(0) {
                        WalkOnlyCard(
                            minutes = o.walkOnly.minutes,
                            meters = o.walkOnly.meters,
                            leaveAt = o.walkOnly.leaveAt,
                            arriveAt = o.walkOnly.arriveAt,
                            highlighted = walkWins,
                        )
                    }
                }
            }

            if (o.itineraries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.DirectionsBus,
                        title = if (outsideCoverage) {
                            t("Poza zasięgiem rozkładu", "Outside the timetable area")
                        } else {
                            t("Brak połączeń autobusem", "No bus connections")
                        },
                        text = when {
                            outsideCoverage -> t(
                                "Aplikacja obsługuje tylko autobusy miejskie w Szczecinku. GPS działa, ale w tej okolicy nie ma przystanków z naszego rozkładu.",
                                "The app only covers Szczecinek city buses. GPS works, but there are no stops from our timetable in this area.",
                            )

                            o.walkOnly != null -> t(
                                "W tym momencie żadna linia nie jedzie w stronę celu — zostaje spacer.",
                                "No bus goes that way right now — walking is your best bet.",
                            )

                            else -> t(
                                "Nic nie jedzie w tę stronę o tej porze. Spróbuj innej godziny.",
                                "Nothing heads that way at this time. Try another hour.",
                            )
                        },
                    )
                }
            }

            items(o.itineraries, key = { it.id }) { it2 ->
                Appear(1) {
                    ItineraryCard(
                        world = world,
                        it = it2,
                        dark = dark,
                        mapStyle = mapStyle,
                        isBest = it2.tag == Tag.BEST.value,
                        live = if (it2 === best) live else null,
                        countdown = if (it2 === best) countdown else null,
                        expanded = expanded == it2.id,
                        onToggle = { expanded = if (expanded == it2.id) null else it2.id },
                        onOpenStop = onOpenStop,
                        canNavigate = isToday,
                        onNavigate = { onNavigate(it2) },
                    )
                }
            }

        }
    }
}

@Composable
private fun WalkOnlyCard(
    minutes: Int,
    meters: Double,
    leaveAt: Int,
    arriveAt: Int,
    highlighted: Boolean,
) {
    KdCard(highlighted = highlighted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegDot(Icons.AutoMirrored.Filled.DirectionsWalk, MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    t("Pieszo", "On foot"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    fmtMeters(meters) + " · " + fmtDuration(minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtTime(arriveAt),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    t("wyjdź ", "leave ") + fmtTime(leaveAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (highlighted) {
            Spacer(Modifier.height(8.dp))
            StatusChip(
                text = t("szybciej niż autobusem", "faster than the bus"),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ItineraryCard(
    world: World,
    it: Itinerary,
    dark: Boolean,
    mapStyle: MapStyle,
    isBest: Boolean,
    live: Api.LiveRow?,
    countdown: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenStop: (Int) -> Unit,
    canNavigate: Boolean,
    onNavigate: () -> Unit,
) {
    var paths by remember(it) { mutableStateOf<RoutedPaths?>(null) }
    LaunchedEffect(expanded, it) {
        if (expanded && paths == null) paths = routedPaths(it)
    }

    KdCard(highlighted = isBest, onClick = onToggle) {
        Row(verticalAlignment = Alignment.Top) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    fmtTime(it.leaveAt),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "  →  ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
                Text(
                    fmtTime(it.arriveAt),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isBest) {
                    StatusChip(t("Najlepsza", "Best"), MaterialTheme.colorScheme.primary)
                }
                when (it.tag) {
                    Tag.FEWER_TRANSFERS.value -> StatusChip(
                        t("Mniej przesiadek", "Fewer changes"),
                        MaterialTheme.colorScheme.tertiary,
                    )

                    Tag.NEXT.value -> StatusChip(
                        t("Później", "Later"),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                live?.let { row -> DelayChip(row.delayMin, row.estimated, row.canceled) }
                if (countdown != null) {
                    Text(
                        countdown,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                metaLine(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    t("Zwiń trasę", "Collapse route")
                } else {
                    t("Pokaż trasę", "Show route")
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        // mapa trasy dopiero po rozwinięciu — lista wyników zostaje lekka
        if (expanded) {
            val segs = remember(it, paths) {
                itinerarySegments(it, paths?.walk.orEmpty(), paths?.ride.orEmpty())
            }
            if (segs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                RouteMiniMap(
                    segs = segs,
                    dots = routeDots(it),
                    dark = dark,
                    mapStyle = mapStyle,
                    height = 138.dp,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        it.legs.forEach { leg ->
            when (leg) {
                is WalkLeg -> LegRowWalk(leg)
                is RideLeg -> LegRowRide(world, leg, expanded, onOpenStop)
            }
        }

        // prowadzenie: mapa za pozycją GPS, instrukcje po drodze i wysiadka
        if (canNavigate) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onNavigate) {
                    Icon(
                        Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(t("Prowadź", "Navigate"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** Jedna cicha linijka faktów (czas, przesiadki, marsz) zamiast rzędu pigułek. */
private fun metaLine(it: Itinerary): String {
    val parts = ArrayList<String>(3)
    parts += fmtDuration(it.arriveAt - it.leaveAt)
    parts += if (it.transfers == 0) {
        t("bez przesiadek", "no changes")
    } else {
        "${it.transfers} " + I18n.transfers(it.transfers)
    }
    if (it.walkMeters > 0) parts += t("pieszo ", "walk ") + fmtMeters(it.walkMeters)
    return parts.joinToString("  ·  ")
}

@Composable
private fun LegRowWalk(leg: WalkLeg) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        LegDot(Icons.AutoMirrored.Filled.DirectionsWalk, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t("Pieszo ", "Walk ") + fmtMeters(leg.meters) + "  ·  " + fmtDuration(leg.minutes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                leg.fromName + "  →  " + leg.toName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LegRowRide(
    world: World,
    leg: RideLeg,
    expanded: Boolean,
    onOpenStop: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineChip(leg.line)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    leg.dir,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    leg.boardStop.name + "  →  " + leg.alightStop.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtTime(leg.depMin) + "–" + fmtTime(leg.arrMin),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                val rideStops = leg.stops.size - 1
                Text(
                    t(
                        "$rideStops przyst.",
                        "$rideStops " + I18n.plural(rideStops, "stop", "stops", "stops"),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            leg.stops.forEachIndexed { idx, rs ->
                val edge = idx == 0 || idx == leg.stops.size - 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(if (edge) 9.dp else 6.dp)
                            .background(
                                color = if (edge) lineColor(leg.line) else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        rs.stop.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (edge) FontWeight.Medium else FontWeight.Normal,
                        color = if (edge) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        fmtTime(rs.min),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

fun patternLabel(type: String): String = when (type) {
    "workday" -> t("dni powszednie", "weekdays")
    "workday_noschool" -> t("dni powszednie (wakacje)", "weekdays (holidays)")
    "sat" -> t("sobota", "Saturday")
    "sun" -> t("niedziele i święta", "Sundays & holidays")
    "special" -> t("specjalny", "special")
    else -> type
}
