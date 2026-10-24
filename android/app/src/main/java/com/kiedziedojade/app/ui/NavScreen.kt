package com.kiedziedojade.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.Fix
import com.kiedziedojade.app.core.RoutedPaths
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.MAX_FIX_ACCURACY_M
import com.kiedziedojade.app.core.Locator
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.NavStep
import com.kiedziedojade.app.core.RideLeg
import com.kiedziedojade.app.core.WALK_DETOUR
import com.kiedziedojade.app.core.WalkLeg
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.courseHeading
import com.kiedziedojade.app.core.fmtMeters
import com.kiedziedojade.app.core.fmtTime
import com.kiedziedojade.app.core.haversine
import com.kiedziedojade.app.core.headingDeg
import com.kiedziedojade.app.core.itinerarySegments
import com.kiedziedojade.app.core.markerHeading
import com.kiedziedojade.app.core.matchLiveRide
import com.kiedziedojade.app.core.navFixPlausible
import com.kiedziedojade.app.core.rideNextStopIndex
import com.kiedziedojade.app.core.routedPaths
import com.kiedziedojade.app.core.minuteDiff
import com.kiedziedojade.app.core.minutesUntil
import com.kiedziedojade.app.core.navDestination
import com.kiedziedojade.app.core.navState
import com.kiedziedojade.app.core.navSteps
import com.kiedziedojade.app.core.nowMinutes
import com.kiedziedojade.app.core.walkMinutes
import kotlinx.coroutines.delay

/**
 * Prowadzenie po wybranym połączeniu: pełnoekranowa mapa w trybie „za mną”
 * (jak w nawigacji Google Maps), instrukcja na żywo — marsz do przystanku,
 * odjazd, wysiadka — oraz śledzenie autobusu (pozycja, ETA, liczba przystanków)
 * z miejskiego API na żywo.
 */
@Composable
fun NavScreen(
    world: World,
    itinerary: Itinerary,
    dark: Boolean,
    onClose: () -> Unit,
    onHide: () -> Unit = onClose,
    initialStepIndex: Int = 0,
    mapStyle: MapStyle = MapStyle.STANDARD,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val locator = remember { Locator(context) }

    var fix by remember { mutableStateOf<Fix?>(null) }
    var prev by remember { mutableStateOf<Fix?>(null) }
    var canLocate by remember { mutableStateOf(locator.hasPermission()) }
    var nowMin by remember { mutableIntStateOf(nowMinutes()) }
    var delayMin by remember { mutableIntStateOf(0) }
    var canceled by remember { mutableStateOf(false) }
    var vibrated by remember { mutableStateOf(false) }
    var boardVibrated by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { canLocate = locator.hasPermission() }

    BackHandler { onHide() }

    // ekran nie gaśnie, dopóki prowadzimy
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // dostęp do GPS: poproś o zgodę przy wejściu, a potem słuchaj pozycji
    // przez cały czas prowadzenia (nasłuch kończy się razem z ekranem)
    LaunchedEffect(Unit) {
        if (!canLocate) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    LaunchedEffect(canLocate, lifecycleOwner) {
        if (!canLocate) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            locator.stream().collect { f ->
                val last = fix
                // Ostatnia znana pozycja bywa stara — nie zaczynajmy od niej,
                // jeśli telefon ma już świeższy odczyt za chwilę.
                if (last == null && f.timeMs > 0L &&
                    System.currentTimeMillis() - f.timeMs > 15_000L
                ) {
                    return@collect
                }
                // GPS pod drzewami potrafi mieć 100 m błędu; taka ramka nie
                // powinna przesuwać kamery ani zaliczać przystanku.
                if (f.accuracy != null && f.accuracy > MAX_FIX_ACCURACY_M) return@collect
                // Emulator albo mock location potrafi podać współrzędne z
                // drugiego końca świata. Wtedy lepiej pokazać planowany marsz
                // niż „11 419 km · 146 399 minut”.
                if (!navFixPlausible(itinerary, f)) return@collect
                val moved = last == null ||
                    haversine(last.lat, last.lng, f.lat, f.lng) >= 0.8
                if (moved) {
                    prev = last
                    fix = f
                }
            }
        }
    }

    // zegar: odjazd/przyjazd liczone na bieżąco
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                nowMin = nowMinutes()
                delay(20_000)
            }
        }
    }

    val steps = remember(itinerary) { navSteps(itinerary) }
    var stepIndex by remember(itinerary) {
        mutableIntStateOf(initialStepIndex.coerceIn(0, steps.lastIndex.coerceAtLeast(0)))
    }
    val state = remember(steps, fix, stepIndex) { navState(steps, fix, stepIndex) }
    // prowadzenie nie cofa się: gdy autobus odjedzie z przystanku, marsz do
    // niego nie wraca, mimo że GPS jest już od niego daleko
    LaunchedEffect(state.stepIndex) {
        if (state.stepIndex != stepIndex) stepIndex = state.stepIndex
    }
    val step = steps.getOrNull(state.stepIndex)
    val destName = remember(itinerary) { navDestination(itinerary) }

    // trasa po ulicach dla marszu i przejazdów (OSRM); linia po przystankach,
    // gdy router nie odpowie
    var paths by remember(itinerary) { mutableStateOf(RoutedPaths()) }
    LaunchedEffect(itinerary) {
        paths = routedPaths(itinerary)
    }

    // najbliższy przejazd — także ten, do którego dopiero idziemy
    val liveRide = remember(itinerary, state.stepIndex) {
        itinerary.legs.drop(state.stepIndex).filterIsInstance<RideLeg>().firstOrNull()
    }

    var live by remember { mutableStateOf<Api.TripExec?>(null) }
    var busPos by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var busHeading by remember { mutableStateOf<Float?>(null) }

    // autobus na żywo: pozycja pojazdu, ETA i postęp kursu. Częściej pytamy,
    // gdy pojazd jest blisko lub stoi na przystanku; rzadziej w trasie.
    LaunchedEffect(liveRide, state.finished, lifecycleOwner) {
        val r = liveRide
        if (r == null || state.finished) {
            live = null
            busPos = null
            busHeading = null
            delayMin = 0
            canceled = false
            return@LaunchedEffect
        }
        live = null
        busPos = null
        busHeading = null
        delayMin = 0
        canceled = false
        boardVibrated = false
        vibrated = false
        var execId: String? = null
        var execIndex = 0
        var fails = 0
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (execId == null) {
                    val rows = runCatching {
                        Api.stopBoard(r.boardStop.id)
                    }.getOrDefault(emptyList())
                    val row = matchLiveRide(rows, r)
                    if (row != null) {
                        delayMin = row.delayMin ?: 0
                        canceled = row.canceled
                        execId = row.tripExecutionId
                        execIndex = row.index ?: 0
                    }
                }
                val id = execId
                if (id != null) {
                    val ex = runCatching { Api.tripExecution(id, execIndex) }.getOrNull()
                    if (ex != null) {
                        fails = 0
                        live = ex
                        val pos = ex.vehicle
                        if (pos != null && pos != busPos) {
                            busHeading = busPos?.let { headingDeg(it, pos) } ?: busHeading
                            busPos = pos
                        }
                    } else if (++fails >= 3) {
                        live = null
                        busPos = null
                        busHeading = null
                        delayMin = 0
                        canceled = false
                        execId = null
                        fails = 0
                    }
                }
                val proximity = if (busPos != null && fix != null) {
                    haversine(fix!!.lat, fix!!.lng, busPos!!.first, busPos!!.second)
                } else {
                    null
                }
                val wait = when {
                    live?.atStop == true -> 8_000L
                    proximity != null && proximity < 1_200.0 -> 10_000L
                    else -> 15_000L
                }
                delay(wait)
            }
        }
    }

    // najbliższy kurs z planu — także wtedy, gdy nie ma jeszcze wykonania na żywo
    val nextRideStep = remember(steps, state.stepIndex) {
        steps.drop(state.stepIndex).filterIsInstance<NavStep.Ride>().firstOrNull()
    }

    // wartości na żywo: indeksy przystanków w kursie i to, co z nich wynika
    val liveExec = live?.takeIf { it.times.isNotEmpty() }
    val boardIdx = remember(liveExec, liveRide) {
        liveExec?.indexOfPlace(liveRide?.boardStop?.id.orEmpty())
    }
    val alightIdx = remember(liveExec, liveRide) {
        liveExec?.indexOfPlace(liveRide?.alightStop?.id.orEmpty())
    }
    // Gdy autobus stoi na przystanku, „następny” to ten po nim — nie ten,
    // na którym właśnie otworzyły się drzwi.
    val liveNextIdx = remember(liveExec) {
        liveExec?.takeIf { it.hasProgress }?.let { ex ->
            val i = if (ex.atStop) ex.nextIndex + 1 else ex.nextIndex
            i.takeIf { it in ex.times.indices }
        }
    }
    val liveNextStopName = remember(liveExec, liveNextIdx) {
        liveNextIdx?.let { i -> liveExec?.times?.getOrNull(i)?.name?.takeIf { it.isNotBlank() } }
    }
    val liveNextClock = remember(liveExec, liveNextIdx) {
        liveNextIdx?.let { liveExec?.clockTo(it) }
    }
    val liveNextInMin = remember(liveExec, liveNextIdx, nowMin) {
        liveNextIdx?.let { liveExec?.minutesTo(it, nowMin) }
    }
    val liveVisible = liveExec != null && boardIdx != null
    val boardAtStop = liveVisible && liveExec!!.atStop && liveExec.nextIndex == boardIdx
    val busPassedBoard = liveVisible && liveExec!!.passed(boardIdx!!)
    val boardClock = if (liveVisible) liveExec!!.clockTo(boardIdx!!) else null
    val alightClock = if (liveVisible && alightIdx != null) liveExec!!.clockTo(alightIdx) else null
    val boardInMin = if (liveVisible) liveExec!!.minutesTo(boardIdx!!, nowMin) else null
    val liveStopsLeft = if (liveVisible && alightIdx != null && liveExec!!.hasProgress) {
        liveExec.stopsTo(alightIdx)
    } else {
        null
    }
    val stopsToBoard = if (liveVisible && liveExec!!.hasProgress) {
        liveExec.stopsTo(boardIdx!!)
    } else {
        null
    }

    // opóźnienie z ETA przy wysiadce, a gdy go nie ma — z tablicy przystanku
    val liveDelay = if (alightClock != null && liveRide != null) {
        minuteDiff(alightClock, liveRide.arrMin).coerceIn(-30, 90)
    } else {
        null
    }
    val shownDelay = liveDelay ?: delayMin
    val liveCanceled = canceled || (liveExec != null &&
        ((boardIdx != null && boardIdx in liveExec.canceledStops) ||
            (alightIdx != null && alightIdx in liveExec.canceledStops)))

    val rideStep = step as? NavStep.Ride
    // planowany następny przystanek — widoczny także bez wykonania kursu na żywo
    val plannedNextStop = remember(rideStep, fix, liveRide) {
        val idx = rideStep?.let { rideNextStopIndex(it, fix) } ?: return@remember null
        liveRide?.stops?.getOrNull(idx)
    }
    val nextStopName = liveNextStopName ?: plannedNextStop?.stop?.name
    val nextStopClock = if (liveNextStopName != null) {
        liveNextClock
    } else {
        plannedNextStop?.let { it.min + shownDelay }
    }
    val nextStopInMin = if (liveNextStopName != null) {
        liveNextInMin
    } else {
        nextStopClock?.let { minutesUntil(it, nowMin) }
    }
    val busDist = if (busPos != null && fix != null) {
        haversine(fix!!.lat, fix!!.lng, busPos!!.first, busPos!!.second)
    } else {
        null
    }
    val boardDist = if (liveRide != null && fix != null) {
        haversine(
            fix!!.lat, fix!!.lng,
            liveRide.boardStop.lat, liveRide.boardStop.lng,
        )
    } else {
        null
    }
    val busNear = busDist != null && busDist <= 90.0
    val movedFromBoard = boardDist != null && boardDist > 50.0
    val progressed = rideStep != null && state.stopsLeft != null &&
        state.stopsLeft!! < rideStep.stops.size - 1
    // Sam autobus obok przystanku nie znaczy jeszcze, że jesteśmy w środku.
    // Wsiadanie potwierdza dopiero ruszenie razem z pojazdem albo minięcie
    // kolejnego przystanku.
    val onBus = rideStep != null && (
        progressed || (busPassedBoard && busNear && movedFromBoard)
        )
    // tuż po odjeździe autobus bywa jeszcze w zasięgu GPS — nie oskarżajmy
    // wtedy pasażera o spóźnienie, dopóki pojazd nie odjedzie dalej
    val missed = rideStep != null && busPassedBoard && !onBus && fix != null && !busNear
    val waiting = rideStep != null && !onBus && !missed && (
        if (liveVisible) !busPassedBoard || busNear
        else nowMin <= rideStep.departMin + shownDelay
        )
    // bez potwierdzenia z GPS/API nie wolno twierdzić, że pasażer jest w autobusie
    val departedUnknown = rideStep != null && !onBus && !missed && !waiting &&
        ((liveVisible && busPassedBoard) || (!liveVisible && nowMin >= rideStep.departMin + 1))

    // state.getOffNow obejmuje też pierwsze metry po wysiadce, kiedy
    // bieżącym krokiem jest już marsz — nie gubmy wtedy „wysiądź teraz”.
    val getOffNow = !waiting && !missed && !state.finished &&
        (state.getOffNow || (rideStep != null && liveStopsLeft != null && liveStopsLeft == 0))
    val getOffSoon = rideStep != null && !waiting && !missed && !getOffNow &&
        (state.getOffSoon || (liveStopsLeft != null && liveStopsLeft == 1))
    val getOffRide = rideStep ?: (steps.getOrNull(state.stepIndex - 1) as? NavStep.Ride)
    val getOffClock = getOffRide?.let { r ->
        if (rideStep != null) alightClock ?: (r.arriveMin + shownDelay)
        // po wysiadce liveRide może już dotyczyć przesiadki — nie mieszaj
        // jej opóźnienia z zegarem dopiero co opuszczonego kursu
        else r.arriveMin + delayMin
    }

    // wibracja: autobus przy przystanku, wysiadka i koniec podróży
    LaunchedEffect(boardAtStop, waiting) {
        if (waiting && boardAtStop && !boardVibrated) {
            boardVibrated = true
            vibrate(context)
        } else if (!boardAtStop) {
            boardVibrated = false
        }
    }
    val alert = (getOffNow && !waiting && !missed) || state.finished
    LaunchedEffect(alert) {
        if (alert && !vibrated) {
            vibrated = true
            vibrate(context)
        } else if (!alert) {
            vibrated = false
        }
    }

    // Kierunek znacznika: w ruchu kurs GPS, w miejscu kompas telefonu.
    // Gdy kompas chwilowo nie ma danych, zostaje ostatni znany kurs — dzięki
    // temu strzałka nie znika przy prędkości poniżej progu.
    val compassHeading = rememberCompassHeading()
    var lastCourse by remember { mutableStateOf<Float?>(null) }
    val course = remember(fix, prev) { courseHeading(fix, prev) }
    LaunchedEffect(course) {
        if (course != null) lastCourse = course
    }
    val heading = markerHeading(course, compassHeading, lastCourse)

    val rideDelay = if (liveRide != null) shownDelay else 0
    val liveFinal = liveRide != null &&
        itinerary.legs.indexOf(liveRide) == itinerary.legs.lastIndex &&
        itinerary.legs.lastOrNull() is RideLeg
    val eta = if (liveFinal && alightClock != null) alightClock else itinerary.arriveAt + rideDelay
    val leftMin = minutesUntil(eta, nowMin)
    val chipLine = rideStep?.line ?: liveRide?.line

    val instruction = navInstruction(
        step = step,
        finished = state.finished,
        getOffNow = getOffNow,
        getOffSoon = getOffSoon,
        getOffName = getOffRide?.alightName,
        getOffClock = getOffClock,
        stopsLeft = liveStopsLeft ?: state.stopsLeft,
        distance = state.distanceToTarget,
        nowMin = nowMin,
        delayMin = shownDelay,
        canceled = liveCanceled,
        destName = destName,
        live = LiveInfo(
            atBoard = boardAtStop,
            passedBoard = busPassedBoard,
            stopsToBoard = stopsToBoard,
            boardInMin = boardInMin,
        ),
        waiting = waiting,
        missed = missed,
        departedUnknown = departedUnknown,
        boardClock = boardClock,
        plannedBoardClock = nextRideStep?.departMin,
        alightClock = alightClock,
        nextStopName = nextStopName,
        nextStopClock = nextStopClock,
        nextStopInMin = nextStopInMin,
        primary = scheme.primary,
        tertiary = scheme.tertiary,
        error = scheme.error,
    )

    val gTarget = step?.target ?: itinerary.lastRide?.alightStop?.let { it.lat to it.lng }

    Box(Modifier.fillMaxSize()) {
        RouteNavMap(
            segs = remember(itinerary, paths) { itinerarySegments(itinerary, paths.walk, paths.ride) },
            dots = routeDots(itinerary),
            fix = fix?.let { it.lat to it.lng },
            heading = heading,
            activeLeg = state.stepIndex,
            dark = dark,
            baseZoom = when {
                state.finished -> 16.5f
                getOffNow -> 16.8f
                getOffSoon -> 16.2f
                step is NavStep.Walk -> 16f
                waiting || boardAtStop -> 15.8f
                else -> 15f
            },
            bus = busPos.takeIf { liveVisible },
            busHeading = busHeading,
            busColor = lineColor(chipLine.orEmpty()),
            showAttribution = false,
            mapStyle = mapStyle,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainer,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        LegDot(instruction.icon, instruction.accent)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                instruction.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                            )
                            Spacer(Modifier.size(2.dp))
                            if (instruction.facts.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    instruction.facts.forEach { fact ->
                                        Text(
                                            buildAnnotatedString {
                                                withStyle(
                                                    SpanStyle(
                                                        color = scheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Medium,
                                                    ),
                                                ) {
                                                    append(fact.label)
                                                    append(": ")
                                                }
                                                append(fact.value)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurface,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    instruction.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (chipLine != null && !state.finished) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        t("Linia", "Line"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    LineChip(chipLine)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        if (gTarget != null) {
                            TextButton(onClick = { openGoogleMapsNav(context, gTarget.first, gTarget.second) }) {
                                Icon(
                                    Icons.Filled.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t("Google Maps", "Google Maps"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            MapAttribution(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 2.dp, bottom = 4.dp),
                style = mapStyle,
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainer,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("Przyjazd ", "Arrival ") + fmtTime(eta),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row {
                            Text(
                                if (state.finished) {
                                    t("jesteś na miejscu", "you are there")
                                } else {
                                    t("za ", "in ") + leftMin + " " + I18n.minutesAfter(leftMin)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            if (!canLocate) {
                                Text(
                                    "  ·  " + t("brak GPS", "no GPS"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.error,
                                )
                            }
                            if (liveVisible) {
                                Text(
                                    "  ·  " + t("autobus na żywo", "bus live"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }
                    TextButton(onClick = onHide) {
                        Text(t("Ukryj", "Hide"))
                    }
                    TextButton(onClick = onClose) {
                        Text(t("Zakończ", "Finish"))
                    }
                }
            }
        }
    }
}

/** Jedna etykieta z wartością w karcie prowadzenia. */
private data class NavFact(val label: String, val value: String)

/** Tekst instrukcji na żywo: co robić teraz. */
private data class NavInstruction(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val accent: Color,
    val facts: List<NavFact> = emptyList(),
)

/** To, co wiemy o autobusie z API na żywo i co ma trafić do instrukcji. */
private data class LiveInfo(
    val atBoard: Boolean,
    val passedBoard: Boolean,
    val stopsToBoard: Int?,
    val boardInMin: Int?,
)

/** Opóźnienie jako osobny wiersz — samo „+1 min” nie mówi, czego dotyczy. */
private fun delayFact(delayMin: Int): NavFact? = when {
    delayMin > 0 -> NavFact(t("Opóźnienie", "Delay"), "+$delayMin min")
    delayMin < 0 -> NavFact(t("Przed czasem", "Early"), "${-delayMin} min")
    else -> null
}

private fun navInstruction(
    step: NavStep?,
    finished: Boolean,
    getOffNow: Boolean,
    getOffSoon: Boolean,
    getOffName: String?,
    getOffClock: Int?,
    stopsLeft: Int?,
    distance: Double?,
    nowMin: Int,
    delayMin: Int,
    canceled: Boolean,
    destName: String,
    live: LiveInfo?,
    waiting: Boolean,
    missed: Boolean,
    departedUnknown: Boolean,
    boardClock: Int?,
    plannedBoardClock: Int?,
    alightClock: Int?,
    nextStopName: String?,
    nextStopClock: Int?,
    nextStopInMin: Int?,
    primary: Color,
    tertiary: Color,
    error: Color,
): NavInstruction {
    val bus = Icons.Filled.DirectionsBus
    val walk = Icons.AutoMirrored.Filled.DirectionsWalk
    if (finished) {
        return NavInstruction(Icons.Filled.Flag, t("Jesteś na miejscu", "You have arrived"), destName, tertiary)
    }
    // Po zaliczeniu przystanku bieżącym krokiem jest już marsz, ale stoimy
    // jeszcze przy drzwiach — przez chwilę liczy się wysiadka, nie marsz.
    if (getOffNow && getOffName != null) {
        return NavInstruction(
            bus,
            t("Wysiądź teraz", "Get off now"),
            "",
            error,
            listOf(
                NavFact(
                    t("Wysiadka", "Get off"),
                    getOffName + (getOffClock?.let { " · " + fmtTime(it) } ?: ""),
                ),
            ),
        )
    }
    when (step) {
        is NavStep.Walk -> {
            val meters = distance ?: step.meters
            val mins = walkMinutes(meters)
            // Odjazd jest ważniejszy niż długość marszu — pokazujemy go nawet
            // wtedy, gdy miasto nie ma jeszcze wykonania kursu na żywo.
            val departure = boardClock ?: plannedBoardClock
            val facts = buildList {
                add(
                    NavFact(
                        t("Marsz", "Walk"),
                        fmtMeters(meters * WALK_DETOUR) + " · " + mins + " " + I18n.minutes(mins),
                    ),
                )
                if (departure != null) {
                    val inMin = live?.boardInMin ?: minutesUntil(departure, nowMin)
                    add(
                        NavFact(
                            t("Odjazd", "Departure"),
                            fmtTime(departure) + " · " +
                                if (inMin <= 0) {
                                    t("teraz", "now")
                                } else {
                                    t("za ", "in ") + inMin + " " + I18n.minutesAfter(inMin)
                                },
                        ),
                    )
                }
                live?.let { l ->
                    val n = l.stopsToBoard
                    val busText = when {
                        l.passedBoard -> t("już odjechał", "has left")
                        l.atBoard -> t("na przystanku", "at the stop")
                        n != null && n in 1..12 ->
                            n.toString() + " " + I18n.stops(n) + " " + t("przed Tobą", "ahead")
                        else -> null
                    }
                    if (busText != null) add(NavFact(t("Autobus", "Bus"), busText))
                }
                delayFact(delayMin)?.let { add(it) }
            }
            val urgent = live?.boardInMin != null && !live.passedBoard && live.boardInMin < mins
            return NavInstruction(
                walk,
                t("Pieszo do ", "Walk to ") + step.toName,
                "",
                if (urgent) error else tertiary,
                facts,
            )
        }

        is NavStep.Ride -> {
            val arr = alightClock ?: (step.arriveMin + delayMin)
            val dep = boardClock ?: (step.departMin + delayMin)
            val nextWhen = buildList {
                nextStopClock?.let { add(fmtTime(it)) }
                nextStopInMin?.let {
                    add(
                        if (it <= 0) {
                            t("teraz", "now")
                        } else {
                            t("za ", "in ") + it + " " + I18n.minutesAfter(it)
                        },
                    )
                }
            }.joinToString(" · ")
            val alightFact = NavFact(
                t("Wysiadka", "Get off"),
                step.alightName + " · " + fmtTime(arr),
            )
            val nextFacts = buildList {
                if (nextStopName != null && nextStopName != step.alightName) {
                    add(NavFact(t("Następny", "Next"), nextStopName))
                    if (nextWhen.isNotEmpty()) add(NavFact(t("Dojazd", "Due"), nextWhen))
                }
                add(alightFact)
                delayFact(delayMin)?.let { add(it) }
            }
            return when {
                canceled -> NavInstruction(
                    bus,
                    t("Kurs odwołany", "Trip canceled"),
                    t("Sprawdź kolejne odjazdy na tablicy", "Check the next departures on the board"),
                    error,
                )

                missed -> NavInstruction(
                    bus,
                    t("Autobus odjechał", "The bus has left"),
                    t("Sprawdź kolejne odjazdy na tablicy", "Check the next departures on the board"),
                    error,
                )

                getOffSoon -> {
                    val facts = buildList {
                        stopsLeft?.let { left ->
                            add(NavFact(t("Za", "In"), left.toString() + " " + I18n.stops(left)))
                        }
                        add(alightFact)
                    }
                    NavInstruction(
                        bus,
                        t("Przygotuj się do wysiadki", "Get ready to get off"),
                        "",
                        error,
                        facts,
                    )
                }

                waiting -> {
                    val inMin = minutesUntil(dep, nowMin)
                    val facts = buildList {
                        add(NavFact(t("Kierunek", "Direction"), step.direction))
                        if (live?.atBoard == true) {
                            add(NavFact(t("Autobus", "Bus"), t("na przystanku", "at the stop")))
                        } else {
                            add(
                                NavFact(
                                    t("Odjazd", "Departure"),
                                    fmtTime(dep) + " · " +
                                        if (inMin <= 0) {
                                            t("teraz", "now")
                                        } else {
                                            t("za ", "in ") + inMin + " " + I18n.minutesAfter(inMin)
                                        },
                                ),
                            )
                            live?.stopsToBoard?.takeIf { it in 1..12 }?.let { n ->
                                add(
                                    NavFact(
                                        t("Autobus", "Bus"),
                                        n.toString() + " " + I18n.stops(n) + " " + t("przed Tobą", "ahead"),
                                    ),
                                )
                            }
                        }
                        delayFact(delayMin)?.let { add(it) }
                    }
                    NavInstruction(
                        bus,
                        if (live?.atBoard == true) {
                            t("Wsiądź do linii ", "Board line ") + step.line
                        } else {
                            t("Czekaj na linię ", "Wait for line ") + step.line
                        },
                        "",
                        if (live?.atBoard == true) tertiary else primary,
                        facts,
                    )
                }

                departedUnknown -> NavInstruction(
                    bus,
                    t("Autobus niepotwierdzony", "Bus not confirmed"),
                    t(
                        "Kurs mógł odjechać — sprawdź tablicę i następny odjazd",
                        "The trip may have left — check the board and the next departure",
                    ),
                    error,
                )

                stopsLeft == null -> NavInstruction(
                    bus,
                    t("Jedziesz linią ", "You are on line ") + step.line,
                    "",
                    primary,
                    nextFacts,
                )

                stopsLeft <= 0 -> NavInstruction(
                    bus,
                    t("Wysiądź na następnym przystanku", "Get off at the next stop"),
                    "",
                    error,
                    listOf(alightFact),
                )

                else -> NavInstruction(
                    bus,
                    t("Wysiądź za ", "Get off in ") + stopsLeft + " " + I18n.stops(stopsLeft),
                    "",
                    lineColor(step.line),
                    nextFacts,
                )
            }
        }

        else -> return NavInstruction(bus, t("Prowadzenie", "Navigation"), destName, primary)
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220, 120, 320), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
        }
    }
}
