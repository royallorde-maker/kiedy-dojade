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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Planner
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.SavedPlace
import com.kiedziedojade.app.core.Stop
import com.kiedziedojade.app.core.Trip
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.minutesUntil
import com.kiedziedojade.app.core.nowMinutes
import com.kiedziedojade.app.core.todayStr
import kotlinx.coroutines.delay

private data class TimetableRow(val trip: Trip, val depMin: Int, val seq: Int)

@Composable
fun StopScreen(
    world: World,
    stopIndex: Int,
    embedded: Boolean,
    prefs: Prefs,
    onBack: (() -> Unit)?,
    onPickStop: () -> Unit,
    /** YYYY-MM-DD for a timetable opened from a future plan; null = today */
    serviceDate: String? = null,
) {
    if (stopIndex < 0 || world.stops.getOrNull(stopIndex) == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!embedded && onBack != null) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    ScreenHeader(t("Tablica", "Board"), onBack = onBack)
                }
            }
            EmptyState(
                icon = Icons.Filled.DirectionsBus,
                title = t("Nie wybrano przystanku", "No stop selected"),
                text = t(
                    "Wybierz przystanek, żeby zobaczyć odjazdy na żywo i rozkład.",
                    "Pick a stop to see live departures and the timetable.",
                ),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onPickStop) {
                    Text(t("Wybierz przystanek", "Pick a stop"))
                }
            }
        }
        return
    }

    val stop = world.stops[stopIndex]
    val date = serviceDate ?: todayStr()
    val isToday = date == todayStr()
    val lifecycleOwner = LocalLifecycleOwner.current
    var board by remember(stop.id, date) { mutableStateOf<List<Api.LiveRow>?>(null) }
    var boardLoaded by remember(stop.id, date) { mutableStateOf(false) }
    var boardError by remember(stop.id, date) { mutableStateOf(false) }
    var expandedTrip by remember(stop.id, date) { mutableStateOf<Long?>(null) }
    var now by remember(stop.id, date) { mutableIntStateOf(nowMinutes()) }

    LaunchedEffect(stop.id, lifecycleOwner, isToday) {
        if (!isToday) {
            boardLoaded = true
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    board = Api.stopBoard(stop.id)
                    boardError = false
                } catch (_: Exception) {
                    boardError = true
                }
                boardLoaded = true
                delay(30_000)
            }
        }
    }

    LaunchedEffect(stopIndex, lifecycleOwner, isToday) {
        if (!isToday) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = nowMinutes()
                delay(20_000)
            }
        }
    }

    val pattern = remember(world, date) { Planner.resolvePattern(world, date) }
    val timetable = remember(world, stopIndex, pattern.first) {
        world.trips[pattern.first].orEmpty().mapNotNull { tr ->
            val idx = tr.times.indexOfFirst { it.first == stopIndex }
            if (idx < 0) null else TimetableRow(tr, tr.times[idx].second, idx)
        }.sortedBy { it.depMin }
    }
    val saved = SavedPlace("stop", stop.name, index = stopIndex, stopId = stop.id)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Appear(0) {
                Column {
                    ScreenHeader(
                        title = stop.name,
                        subtitle = t("Przystanek ", "Stop ") + stop.code +
                            (if (stop.station) " · " + t("dworzec", "station") else "") +
                            (if (stop.onlyDisembarking) " · " + t("tylko wysiadanie", "drop-off only") else "") +
                            (if (!isToday) " · " + relativeDay(date) else ""),
                        onBack = onBack,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stop.onDemand) {
                            StatusChip("NŻ", MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (embedded) {
                            TextButton(onClick = onPickStop) {
                                Text(t("Zmień przystanek", "Change stop"))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        FavoriteStar(prefs, saved)
                    }
                }
            }
        }

        if (isToday) {
            item { SectionTitle(t("Odjazdy na żywo", "Live departures")) }

            item {
                Appear(1) {
                    KdCard {
                        val rows = board.orEmpty()
                            .filter { it.depMin != null }
                            .sortedBy { it.depMin }
                            .take(14)
                        when {
                            !boardLoaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    t("Ładowanie odjazdów…", "Loading departures…"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            boardError && rows.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    t(
                                        "Brak łączności — odjazdy na żywo niedostępne. Rozkład poniżej działa offline.",
                                        "No connection — live departures unavailable. The timetable below works offline.",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            rows.isEmpty() -> Text(
                                t(
                                    "Teraz nie ma zaplanowanych odjazdów — zajrzyj do rozkładu.",
                                    "Nothing scheduled right now — check the timetable.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            else -> Column {
                                rows.forEachIndexed { i, row ->
                                    if (i > 0) Spacer(Modifier.height(6.dp))
                                    LiveRow(row, now)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    if (isToday) {
                        t("Rozkład na dziś", "Today's timetable")
                    } else {
                        t("Rozkład · ", "Timetable · ") + relativeDay(date)
                    },
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(
                    text = patternLabel(pattern.third),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!pattern.second) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip(
                        text = t("wg dnia tygodnia", "by weekday"),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        if (timetable.isEmpty()) {
            item {
                Text(
                    if (isToday) {
                        t("Ten przystanek nie ma kursów w dzisiejszym rozkładzie.", "No trips at this stop today.")
                    } else {
                        t(
                            "Ten przystanek nie ma kursów w rozkładzie na ten dzień.",
                            "No trips at this stop on that day.",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        items(timetable, key = { "t" + it.trip.id + "-" + it.depMin }) { row ->
            Appear(2) {
                TimetableCard(
                    world = world,
                    row = row,
                    now = if (isToday) now else -1,
                    expanded = expandedTrip == row.trip.id,
                    onToggle = {
                        expandedTrip = if (expandedTrip == row.trip.id) null else row.trip.id
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveRow(row: Api.LiveRow, now: Int) {
    val canceled = row.canceled
    val inMin = row.depMin?.let { minutesUntil(it, now) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineChip(row.line ?: "?")
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.direction ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inMin != null) {
                    Text(
                        if (inMin <= 0) t("teraz", "now") else t("za $inMin min", "in $inMin min"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DelayChip(row.delayMin, row.estimated, row.canceled)
                if (row.platform != null) {
                    StatusChip(
                        t("peron ", "platform ") + row.platform!!,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val dep = if (row.delayMin != null && row.delayMin != 0 && row.depMin != null && !canceled) {
                row.depMin!!
            } else {
                row.schedMin ?: row.depMin
            }
            Text(
                dep?.let { fmtTime(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (canceled) TextDecoration.LineThrough else null,
                color = if (canceled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (row.delayMin != null && row.delayMin != 0 && row.schedMin != null) {
                Text(
                    fmtTime(row.schedMin!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
    }
}

@Composable
private fun TimetableCard(
    world: World,
    row: TimetableRow,
    now: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val past = row.depMin < now - 1
    KdCard(
        onClick = onToggle,
        modifier = Modifier.alpha(if (past) 0.55f else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtTime(row.depMin),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            LineChip(row.trip.line)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.trip.dir,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    t("${row.trip.times.size - row.seq - 1} przyst. do końca", "${row.trip.times.size - row.seq - 1} stops to the end"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (expanded) "−" else "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            for (k in row.seq until row.trip.times.size) {
                val (si, min) = row.trip.times[k]
                val s: Stop = world.stops.getOrNull(si) ?: continue
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(if (k == row.seq) 9.dp else 6.dp)
                            .background(
                                color = if (k == row.seq) lineColor(row.trip.line) else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        s.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (k == row.seq) FontWeight.Medium else FontWeight.Normal,
                        color = if (k == row.seq) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        fmtTime(min),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
