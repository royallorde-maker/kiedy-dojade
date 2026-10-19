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
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.kiedziedojade.app.core.PlanInput
import com.kiedziedojade.app.core.PlanOutcome
import com.kiedziedojade.app.core.Planner
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.Tag
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtDate
import com.kiedziedojade.app.core.fmtDuration
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ResultsScreen(
    world: World,
    query: JourneyQuery,
    onBack: () -> Unit,
    onOpenStop: (Int) -> Unit,
) {
    var outcome by remember(query) { mutableStateOf<PlanOutcome?>(null) }
    var expanded by remember(query) { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        outcome = withContext(Dispatchers.Default) {
            Planner.plan(
                world,
                PlanInput(
                    origin = query.from,
                    dest = query.to,
                    date = query.date,
                    departMin = query.departMin,
                ),
            )
        }
    }

    // live delays for the first vehicle of the best option
    var live by remember(query) { mutableStateOf<Api.LiveRow?>(null) }
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        val first = o.itineraries.firstOrNull()?.firstRide ?: return@LaunchedEffect
        val stop = world.stops[first.boardStop.i]
        live = runCatching { Api.stopBoard(stop.id) }.getOrNull()
            ?.firstOrNull { it.tripId == first.tripId }
    }

    val o = outcome

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                ScreenHeader(
                    title = t("Połączenia", "Connections"),
                    subtitle = query.from.name + "  →  " + query.to.name +
                        (query.date?.let { "  ·  " + fmtDate(it, I18n.pl) } ?: ""),
                    onBack = onBack,
                )
                o?.let { oc ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                        StatusChip(
                            text = patternLabel(oc.patternType),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (!oc.exactDay) {
                            StatusChip(
                                text = t("rozkład wg dnia tygodnia", "schedule by weekday"),
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }

        if (o == null) {
            item { LoadingBlock(t("Szukam połączeń…", "Searching connections…")) }
        } else {
            val best = o.itineraries.firstOrNull()
            val walkWins = o.walkOnly != null && (best == null || o.walkOnly.arriveAt <= best.arriveAt)

            if (o.walkOnly != null) {
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
                        icon = Icons.Filled.EventBusy,
                        title = t("Brak połączeń autobusem", "No bus connections"),
                        text = if (o.walkOnly != null) {
                            t(
                                "W tym momencie żadna linia nie jedzie w stronę celu — zostaje spacer.",
                                "No line heads your way right now — walking is the option.",
                            )
                        } else {
                            t(
                                "Nic nie jedzie w tę stronę o tej porze. Spróbuj innej godziny albo dnia.",
                                "Nothing heads that way at this time. Try another hour or day.",
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
                        isBest = it2.tag == Tag.BEST.value,
                        live = if (it2 === best) live else null,
                        expanded = expanded == it2.id,
                        onToggle = { expanded = if (expanded == it2.id) null else it2.id },
                        onOpenStop = onOpenStop,
                    )
                }
            }

            if (o.itineraries.isNotEmpty()) {
                item {
                    val first = o.itineraries.first().firstRide
                    if (first != null) {
                        Appear(2) {
                            KdCard(onClick = { onOpenStop(first.boardStop.i) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            t("Najbliższe odjazdy", "Upcoming departures"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            first.boardStop.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
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
    isBest: Boolean,
    live: Api.LiveRow?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenStop: (Int) -> Unit,
) {
    KdCard(highlighted = isBest, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmtTime(it.leaveAt),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "  →  ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                    Text(
                        fmtTime(it.arriveAt),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    fmtDuration(it.arriveAt - it.leaveAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isBest) {
                    StatusChip(t("NAJLEPSZE", "BEST"), MaterialTheme.colorScheme.primary)
                }
                when (it.tag) {
                    Tag.FEWER_TRANSFERS.value -> StatusChip(
                        t("MNIEJ PRZESIADEK", "FEWER CHANGES"),
                        MaterialTheme.colorScheme.tertiary,
                    )

                    Tag.NEXT.value -> StatusChip(
                        t("KOLEJNE", "NEXT"),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = if (it.transfers == 0) {
                    t("bez przesiadek", "no changes")
                } else {
                    "${it.transfers} " + I18n.transfers(it.transfers)
                },
                color = MaterialTheme.colorScheme.secondary,
            )
            if (it.walkMeters > 0) {
                StatusChip(
                    text = t("pieszo ", "walk ") + fmtMeters(it.walkMeters),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            live?.let { row ->
                if (row.canceled) {
                    StatusChip(t("odwołany", "canceled"), MaterialTheme.colorScheme.error)
                } else if (row.delayMin != null && row.delayMin != 0) {
                    StatusChip(
                        text = if (row.delayMin > 0) "+${row.delayMin} min" else "${row.delayMin} min",
                        color = if (row.delayMin > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    )
                } else if (row.estimated) {
                    StatusChip(t("na żywo", "live"), MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        it.legs.forEach { leg ->
            when (leg) {
                is WalkLeg -> LegRowWalk(leg)
                is RideLeg -> LegRowRide(world, leg, expanded, onOpenStop)
            }
        }
    }
}

@Composable
private fun LegRowWalk(leg: WalkLeg) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        LegDot(Icons.AutoMirrored.Filled.DirectionsWalk, MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                t("Pieszo ", "Walk ") + fmtMeters(leg.meters) + " · " + fmtDuration(leg.minutes),
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
        Text(
            fmtTime(leg.depMin) + "–" + fmtTime(leg.arrMin),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LegRowRide(
    world: World,
    leg: RideLeg,
    expanded: Boolean,
    onOpenStop: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
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
                Text(
                    t("${leg.stops.size - 1} przyst.", "${leg.stops.size - 1} stops"),
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
