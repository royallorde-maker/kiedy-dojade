package com.kiedziedojade.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.DataRepo
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Itinerary
import com.kiedziedojade.app.core.Locator
import com.kiedziedojade.app.core.MapStyle
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.SavedPlace
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.nearestStops
import com.kiedziedojade.app.tracking.RideTrackingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ekrany w zakładce „Szukaj” (stos: plan → wyniki → przystanek). */
sealed interface Route {
    data object Plan : Route
    data class Results(val query: JourneyQuery) : Route
    data class Stop(val index: Int, val serviceDate: String? = null) : Route
}

data class JourneyQuery(
    val from: PlaceRef,
    val to: PlaceRef,
    /** YYYY-MM-DD; wewnętrzny dzień rozkładu, nigdy nie wybierany przez użytkownika */
    val serviceDate: String,
    val departMin: Int,
)

/**
 * Stos ekranów „Szukaj” przeżywa obrót i odtworzenie procesu. Wcześniej
 * `remember` gubił wyniki i wracał do pustego planowania.
 */
private val StackSaver: Saver<SnapshotStateList<Route>, String> = Saver(
    save = { RouteStateCodec.encode(it) },
    restore = { RouteStateCodec.decode(it).toMutableStateList() },
)

enum class PickerTarget { FROM, TO, BOARD }

@Composable
fun KdApp(
    prefs: Prefs,
    repo: DataRepo,
    openTab: Int? = null,
    onOpenTabHandled: () -> Unit = {},
    openTracking: Boolean = false,
    onOpenTrackingHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var world by remember { mutableStateOf<World?>(null) }
    var worldError by remember { mutableStateOf<String?>(null) }
    var dataNote by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var announcements by remember { mutableStateOf<List<Api.Announcement>>(emptyList()) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showChanges by rememberSaveable { mutableStateOf(false) }
    val stack = rememberSaveable(saver = StackSaver) {
        mutableStateListOf<Route>(Route.Plan)
    }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var mapPicker by remember { mutableStateOf<PickerTarget?>(null) }

    var from by remember { mutableStateOf(prefs.lastFrom?.toRef()) }
    var to by remember { mutableStateOf(prefs.lastTo?.toRef()) }
    /** -1 = teraz; inaczej minuty od północy wybrane przez użytkownika */
    var departMin by rememberSaveable { mutableIntStateOf(-1) }
    var showTimePicker by remember { mutableStateOf(false) }
    var boardStop by remember { mutableIntStateOf(prefs.boardStop) }
    var mapStyle by remember { mutableStateOf(MapStyle.from(prefs.mapStyle)) }
    var dismissedAlert by remember { mutableStateOf<Int?>(null) }
    var showSplash by remember { mutableStateOf(true) }
    /** prowadzenie po wybranym połączeniu (pełnoekranowe, jak nawigacja) */
    var nav by remember { mutableStateOf(prefs.activeItinerary) }

    val requestLocation = rememberLocationRequester()
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                I18n.t(
                    "Śledzenie działa, ale bez powiadomienia. Włącz je w Ustawieniach, aby widzieć prowadzenie po wyjściu z aplikacji.",
                    "Tracking works, but without a notification. Enable it in Settings to see your trip after leaving the app.",
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * Start prowadzenia: zapis trasy i foreground service muszą powstać razem,
     * żeby po wyjściu z aplikacji GPS i powiadomienie działały dalej.
     */
    fun startNavigation(itinerary: Itinerary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RideTrackingService.start(context, itinerary)
        nav = itinerary
    }

    // Po powrocie do aplikacji wznów dokładnie tę podróż, która jest śledzona.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val active = prefs.activeItinerary
                nav = active
                // proces mógł zostać zabity — upewnij się, że foreground service żyje
                if (active != null) RideTrackingService.start(context, active)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // „Zakończ” w powiadomieniu zatrzymuje usługę poza ekranem aplikacji.
    // Bez tego prowadzenie zostałoby na ekranie mimo braku aktywnej podróży.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == RideTrackingService.ACTION_TRACKING_STOPPED) {
                    nav = null
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(RideTrackingService.ACTION_TRACKING_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Dotknięcie powiadomienia otwiera prowadzenie, nawet gdy aktywność już żyje.
    LaunchedEffect(openTracking) {
        if (openTracking) {
            prefs.activeItinerary?.let { nav = it }
            onOpenTrackingHandled()
        }
    }

    // Grafit jest jedynym motywem, więc mapa zawsze w ciemnym wariancie.
    val mapDark = true

    fun setFrom(ref: PlaceRef?) {
        from = ref
        prefs.lastFrom = ref?.let { SavedPlace.forLast(it, world) }
    }

    fun setTo(ref: PlaceRef?) {
        to = ref
        prefs.lastTo = ref?.let { SavedPlace.forLast(it, world) }
    }

    /** Data nie jest wybierana: godzina, która minęła, oznacza jutro. */
    fun queryAt(f: PlaceRef, d: PlaceRef, minute: Int?): JourneyQuery {
        val dep = com.kiedziedojade.app.core.resolveDeparture(
            java.time.ZonedDateTime.now(com.kiedziedojade.app.core.WARSAW),
            minute,
        )
        return JourneyQuery(
            from = f,
            to = d,
            serviceDate = dep.serviceDate,
            departMin = dep.departMin,
        )
    }

    fun queryFor(f: PlaceRef, d: PlaceRef): JourneyQuery =
        queryAt(f, d, departMin.takeIf { it >= 0 })

    fun setMapStyle(style: MapStyle) {
        mapStyle = style
        prefs.mapStyle = style.id
    }

    fun setBoard(fromRef: PlaceRef?) {
        val w = world
        val idx = fromRef?.let { stopForRef(w, it) }
        if (idx != null && w != null && idx in w.stops.indices) {
            boardStop = idx
            prefs.boardStop = idx
            prefs.boardStopId = w.stops[idx].id
            tab = 1
            stack.clear()
            stack += Route.Plan
        } else if (fromRef != null) {
            Toast.makeText(context, t("Brak przystanku w pobliżu", "No stop nearby"), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val w = repo.world()
            // old saves only have an index; attach the stable id while the
            // dataset they were saved against is still loaded
            prefs.migrateStopIds(w)
            if (world == null) world = w
        } catch (e: Exception) {
            worldError = e.message ?: "błąd danych"
        }
        delay(650)
        showSplash = false
        announcements = Api.announcements()
    }

    // Po aktualizacji danych indeksy przystanków mogą się przesunąć.
    // Stabilny identyfikator wygrywa z zapisanym indeksem.
    LaunchedEffect(world) {
        val w = world ?: return@LaunchedEffect
        prefs.lastFrom?.let { from = it.toRef(w) }
        prefs.lastTo?.let { to = it.toRef(w) }

        val savedBoard = prefs.boardStopId?.let { id ->
            w.stops.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
        boardStop = when {
            prefs.boardStopId != null -> savedBoard ?: -1
            boardStop in w.stops.indices -> {
                prefs.boardStopId = w.stops[boardStop].id
                boardStop
            }
            else -> -1
        }
    }

    // ciche odświeżenie danych rozkładowych przy starcie
    LaunchedEffect(Unit) {
        val res = repo.refresh()
        res.world?.let { world = it }
        dataNote = when {
            res.error != null -> t(
                "Nie udało się odświeżyć danych — używana jest kopia lokalna.",
                "Couldn't refresh data — using the local copy.",
            )
            res.changed -> t("Dane rozkładowe zaktualizowane", "Schedule data updated")
            else -> null
        }
    }

    val onRefreshData: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                val res = repo.refresh(force = true)
                res.world?.let { world = it }
                dataNote = when {
                    res.error != null -> t(
                        "Nie udało się odświeżyć danych. Spróbuj ponownie później.",
                        "Couldn't refresh the data. Try again later.",
                    )
                    else -> t("Dane rozkładowe zaktualizowane", "Schedule data updated")
                }
                refreshing = false
            }
        }
    }

    BackHandler(
        enabled = mapPicker != null ||
            picker != null ||
            (tab == 0 && stack.size > 1) ||
            (tab == 4 && showChanges),
    ) {
        when {
            mapPicker != null -> {
                picker = mapPicker
                mapPicker = null
            }
            picker != null -> picker = null
            tab == 0 && stack.size > 1 -> stack.removeAt(stack.lastIndex)
            tab == 4 && showChanges -> showChanges = false
        }
    }

    // Dolny pasek ma teraz pięć zakładek — „Zmiany” są częścią Ustawień.
    LaunchedEffect(tab) {
        if (tab !in 0..4) tab = 0
        if (tab != 4) showChanges = false
    }

    // skróty launchera (Szukaj / Tablica)
    LaunchedEffect(openTab) {
        if (openTab != null) {
            tab = openTab.coerceIn(0, 4)
            onOpenTabHandled()
        }
    }

    Crossfade(targetState = showSplash, animationSpec = tween(220), label = "splash") { splash ->
        if (splash) {
            SplashScreen()
        } else {
            Scaffold(
                bottomBar = {
                    KdNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        tabs = listOf(
                            Icons.Filled.Search to t("Szukaj", "Plan"),
                            Icons.Filled.DirectionsBus to t("Tablica", "Board"),
                            Icons.Filled.Map to t("Mapa", "Map"),
                            Icons.Filled.Star to t("Ulubione", "Saved"),
                            Icons.Filled.Tune to t("Opcje", "Options"),
                        ),
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    val w = world
                    when {
                        worldError != null -> EmptyState(
                            icon = Icons.Filled.DirectionsBus,
                            title = t("Nie udało się wczytać danych", "Could not load data"),
                            text = worldError ?: "",
                        )

                        w == null -> LoadingBlock(t("Wczytywanie rozkładów…", "Loading schedules…"))

                        else -> when (tab) {
                            0 -> {
                                val route = stack.lastOrNull() ?: Route.Plan
                                when (route) {
                                    is Route.Plan -> SearchScreen(
                                        world = w,
                                        from = from,
                                        to = to,
                                        departMin = departMin.takeIf { it >= 0 },
                                        announcements = announcements.filter { it.id != dismissedAlert },
                                        onDismissAnnouncement = { id -> dismissedAlert = id },
                                        onPickFrom = { picker = PickerTarget.FROM },
                                        onPickTo = { picker = PickerTarget.TO },
                                        onSwap = {
                                            val f = from
                                            setFrom(to)
                                            setTo(f)
                                        },
                                        onUseGps = {
                                            requestLocation.request { fix ->
                                                if (fix != null) {
                                                    setFrom(PlaceRef.Gps(t("Moja lokalizacja", "My location"), fix.first, fix.second))
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        requestLocation.errorText(),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        },
                                        onPickTime = { showTimePicker = true },
                                        onUseNow = { departMin = -1 },
                                        onFind = {
                                            val f = from
                                            val d = to
                                            when {
                                                f == null -> picker = PickerTarget.FROM
                                                d == null -> picker = PickerTarget.TO
                                                else -> stack += Route.Results(queryFor(f, d))
                                            }
                                        },
                                        onQuickDestination = { item ->
                                            val ref = item.ref
                                            SavedPlace.from(ref, w)?.let { prefs.pushRecent(it) }
                                            setTo(ref)
                                            val f = from
                                            if (f != null) {
                                                stack += Route.Results(queryFor(f, ref))
                                            } else {
                                                picker = PickerTarget.FROM
                                            }
                                        },
                                    )

                                    is Route.Results -> {
                                        val favFrom = SavedPlace.from(route.query.from, w)
                                        val favTo = SavedPlace.from(route.query.to, w)
                                        val favPair =
                                            if (favFrom != null && favTo != null) favFrom to favTo else null
                                        ResultsScreen(
                                            world = w,
                                            query = route.query,
                                            dark = mapDark,
                                            mapStyle = mapStyle,
                                            onBack = { stack.removeAt(stack.lastIndex) },
                                            onOpenStop = { idx ->
                                                stack += Route.Stop(idx, serviceDate = route.query.serviceDate)
                                            },
                                            onNavigate = { startNavigation(it) },
                                            isFavorite = favPair != null &&
                                                prefs.isFavoriteRoute(favPair.first, favPair.second),
                                            onToggleFavorite = favPair?.let { pair ->
                                                {
                                                    val added = prefs.toggleFavoriteRoute(pair.first, pair.second)
                                                    Toast.makeText(
                                                        context,
                                                        if (added) {
                                                            t("Dodano trasę do ulubionych", "Route added to favourites")
                                                        } else {
                                                            t("Usunięto trasę z ulubionych", "Route removed from favourites")
                                                        },
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            },
                                        )
                                    }

                                    is Route.Stop -> StopScreen(
                                        world = w,
                                        stopIndex = route.index,
                                        serviceDate = route.serviceDate,
                                        embedded = false,
                                        prefs = prefs,
                                        onBack = { stack.removeAt(stack.lastIndex) },
                                        onPickStop = { picker = PickerTarget.BOARD },
                                    )
                                }
                            }

                            1 -> StopScreen(
                                world = w,
                                stopIndex = boardStop,
                                embedded = true,
                                prefs = prefs,
                                onBack = null,
                                onPickStop = { picker = PickerTarget.BOARD },
                            )

                            2 -> MapScreen(
                                world = w,
                                dark = mapDark,
                                mapStyle = mapStyle,
                                requester = requestLocation,
                                onOpenBoard = { idx ->
                                    boardStop = idx
                                    prefs.boardStop = idx
                                    prefs.boardStopId = w.stops[idx].id
                                    tab = 1
                                },
                                onPlanTo = { idx ->
                                    setTo(PlaceRef.StopRef(w.stops[idx].name, idx, w.stops[idx].id))
                                    tab = 0
                                    stack.clear()
                                    stack += Route.Plan
                                },
                            )

                            3 -> FavoritesScreen(
                                prefs = prefs,
                                onOpenRoute = { route ->
                                    val f = route.from.toRef(w)
                                    val d = route.to.toRef(w)
                                    if (f != null && d != null) {
                                        val minute = route.departMin
                                        setFrom(f)
                                        setTo(d)
                                        departMin = minute ?: -1
                                        stack.clear()
                                        stack += Route.Plan
                                        stack += Route.Results(queryAt(f, d, minute))
                                        tab = 0
                                    } else {
                                        Toast.makeText(
                                            context,
                                            t(
                                                "Nie można odtworzyć trasy — dane przystanku się zmieniły",
                                                "This route cannot be restored — stop data changed",
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                            )

                            4 -> {
                                if (showChanges) {
                                    ChangesScreen(
                                        prefs = prefs,
                                        onBack = { showChanges = false },
                                    )
                                } else {
                                    SettingsScreen(
                                        world = w,
                                        prefs = prefs,
                                        mapStyle = mapStyle,
                                        onMapStyleChange = { setMapStyle(it) },
                                        dataNote = dataNote,
                                        refreshing = refreshing,
                                        onRefreshData = onRefreshData,
                                        onOpenChanges = { showChanges = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // pełnoekranowy wybór miejsca
            val w = world
            val target = picker
            if (target != null && w != null) {
                PickerScreen(
                    world = w,
                    prefs = prefs,
                    title = when (target) {
                        PickerTarget.FROM -> t("Skąd jedziesz?", "Where from?")
                        PickerTarget.TO -> t("Dokąd jedziesz?", "Where to?")
                        else -> t("Wybierz przystanek", "Pick a stop")
                    },
                    current = when (target) {
                        PickerTarget.FROM -> from
                        PickerTarget.TO -> to
                        else -> boardStop.takeIf { it in w.stops.indices }?.let {
                            PlaceRef.StopRef(w.stops[it].name, it, w.stops[it].id)
                        }
                    },
                    allowGps = target != PickerTarget.BOARD,
                    onGps = {
                        requestLocation.request { fix ->
                            if (fix != null) {
                                val ref = PlaceRef.Gps(t("Moja lokalizacja", "My location"), fix.first, fix.second)
                                when (target) {
                                    PickerTarget.FROM -> setFrom(ref)
                                    PickerTarget.TO -> setTo(ref)
                                    else -> setBoard(ref)
                                }
                                picker = null
                            } else {
                                Toast.makeText(
                                    context,
                                    requestLocation.errorText(),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onPickOnMap = if (target == PickerTarget.BOARD) {
                        null
                    } else {
                        {
                            mapPicker = target
                            picker = null
                        }
                    },
                    onPick = { ref ->
                        when (target) {
                            PickerTarget.FROM -> setFrom(ref)
                            PickerTarget.TO -> setTo(ref)
                            else -> setBoard(ref)
                        }
                        picker = null
                    },
                    onDismiss = { picker = null },
                )
            }

            // wybór dowolnego punktu na mapie jako startu / celu
            val targetOnMap = mapPicker
            if (targetOnMap != null && w != null) {
                val currentRef = when (targetOnMap) {
                    PickerTarget.FROM -> from
                    PickerTarget.TO -> to
                    else -> null
                }
                val start = refPoint(currentRef, w) ?: (53.70 to 16.69)
                MapPickScreen(
                    title = when (targetOnMap) {
                        PickerTarget.FROM -> t("Wskaż start", "Pick the start")
                        else -> t("Wskaż cel", "Pick the destination")
                    },
                    initial = start,
                    dark = mapDark,
                    mapStyle = mapStyle,
                    onCancel = {
                        picker = targetOnMap
                        mapPicker = null
                    },
                    onPick = { lat, lng ->
                        val ref = PlaceRef.Gps(
                            t("Punkt na mapie", "Map point"),
                            lat,
                            lng,
                            custom = true,
                        )
                        // zapamiętaj punkt, żeby był też na liście ostatnio używanych
                        SavedPlace.from(ref, w)?.let { prefs.pushRecent(it) }
                        when (targetOnMap) {
                            PickerTarget.FROM -> setFrom(ref)
                            PickerTarget.TO -> setTo(ref)
                            else -> setBoard(ref)
                        }
                        mapPicker = null
                        picker = null
                    },
                )
            }

            // pełnoekranowe prowadzenie po wybranym połączeniu
            val navIt = nav
            if (navIt != null && w != null) {
                NavScreen(
                    world = w,
                    itinerary = navIt,
                    dark = mapDark,
                    mapStyle = mapStyle,
                    initialStepIndex = prefs.activeStepIndex,
                    onClose = {
                        RideTrackingService.stop(context)
                        nav = null
                    },
                    onHide = { nav = null },
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickDialog(
            initial = departMin.takeIf { it >= 0 },
            onDismiss = { showTimePicker = false },
            onPick = { minute ->
                departMin = minute
                showTimePicker = false
            },
        )
    }
}

@Composable
fun LoadingBlock(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // wielka bursztynowa poświata za szyldem — plener zamiast pustki
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        0.6f to Color.Transparent,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(kdHeroBrush())
                        .border(1.dp, kdHeroEdge(), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // refleks na płytce
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.35f),
                                    0.6f to Color.Transparent,
                                ),
                            ),
                    )
                    Icon(
                        Icons.Filled.DirectionsBus,
                        contentDescription = null,
                        tint = KD_ON_HERO,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.size(20.dp))
                Text(
                    "KiedyDojadę",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "SZCZECINEK",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(26.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .size(width = 132.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
        }
    }
}

/** Nearest stop for any place reference (used when picking the board stop). */
fun stopForRef(world: World?, ref: PlaceRef): Int? {
    if (world == null) return null
    return when (ref) {
        is PlaceRef.StopRef -> {
            val byId = ref.stopId?.let { id ->
                world.stops.indexOfFirst { it.id == id }.takeIf { it >= 0 }
            }
            byId ?: ref.index.takeIf { it in world.stops.indices }
        }
        else -> {
            val resolved = com.kiedziedojade.app.core.Planner.resolvePlace(ref, world.stops)
            nearestStops(world, resolved.lat, resolved.lng, limit = 1, maxMeters = 2000.0)
                .firstOrNull()?.first?.i
        }
    }
}

/** Best map point for a place: stop coordinates, POI point, or the middle of a street. */
fun refPoint(ref: PlaceRef?, world: World?): Pair<Double, Double>? = when (ref) {
    null -> null
    is PlaceRef.Gps -> ref.lat to ref.lng
    is PlaceRef.PoiRef -> ref.lat to ref.lng
    is PlaceRef.StopRef -> {
        val stop = ref.stopId?.let { id -> world?.stops?.firstOrNull { it.id == id } }
            ?: world?.stops?.getOrNull(ref.index)
        stop?.let { it.lat to it.lng }
    }
    is PlaceRef.StreetRef -> ref.pts.getOrNull(ref.pts.size / 2)
}

/** Prosi o zgodę na lokalizację i zwraca jednorazowy fix (albo null). */
class LocationRequester(
    private val locator: Locator,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private var pending: ((Pair<Double, Double>?) -> Unit)? = null

    /** Ustawiane przy kompozycji; odpala systemowy dialog zgody. */
    var launchPermission: () -> Unit = {}

    fun request(onResult: (Pair<Double, Double>?) -> Unit) {
        if (locator.hasPermission()) {
            scope.launch { onResult(locator.current()) }
        } else {
            pending = onResult
            launchPermission()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val cb = pending
        pending = null
        if (granted) {
            scope.launch { cb?.invoke(locator.current()) }
        } else {
            cb?.invoke(null)
        }
    }

    /** Konkretna przyczyna zamiast jednego ogólnego „nie udało się”. */
    fun errorText(): String = when {
        !locator.hasPermission() -> t(
            "Brak zgody na lokalizację — włącz ją w ustawieniach telefonu.",
            "No location permission — enable it in your phone settings.",
        )

        !locator.locationEnabled() -> t(
            "Lokalizacja w telefonie jest wyłączona — włącz GPS i spróbuj ponownie.",
            "Location is off on your phone — turn on GPS and try again.",
        )

        else -> t(
            "Nie udało się ustalić pozycji. Spróbuj ponownie, najlepiej na zewnątrz.",
            "Could not get a location fix. Try again, ideally outdoors.",
        )
    }
}

@Composable
fun rememberLocationRequester(): LocationRequester {
    val context = LocalContext.current
    val locator = remember { Locator(context) }
    val scope = rememberCoroutineScope()
    val requester = remember { LocationRequester(locator, scope) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        requester.onPermissionResult(grants.values.any { it })
    }
    requester.launchPermission = {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    return requester
}
