package com.kiedziedojade.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiedziedojade.app.core.Api
import com.kiedziedojade.app.core.DataRepo
import com.kiedziedojade.app.core.I18n
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Locator
import com.kiedziedojade.app.core.PlaceRef
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.core.SavedPlace
import com.kiedziedojade.app.core.World
import com.kiedziedojade.app.core.nearestStops
import com.kiedziedojade.app.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ekrany w zakładce „Szukaj” (stos: plan → wyniki → przystanek). */
sealed interface Route {
    data object Plan : Route
    data class Results(val query: JourneyQuery) : Route
    data class Stop(val index: Int, val fromBoard: Boolean = false) : Route
}

data class JourneyQuery(
    val from: PlaceRef,
    val to: PlaceRef,
    /** null = dziś */
    val date: String?,
    val departMin: Int,
)

enum class PickerTarget { FROM, TO, BOARD }

@Composable
fun KdApp(
    prefs: Prefs,
    repo: DataRepo,
    appTheme: AppTheme,
    onThemeChange: (String) -> Unit,
    openTab: Int? = null,
    onOpenTabHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var world by remember { mutableStateOf<World?>(null) }
    var worldError by remember { mutableStateOf<String?>(null) }
    var dataNote by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var announcements by remember { mutableStateOf<List<Api.Announcement>>(emptyList()) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>(Route.Plan) }
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    var from by remember { mutableStateOf(prefs.lastFrom?.toRef()) }
    var to by remember { mutableStateOf(prefs.lastTo?.toRef()) }
    var date by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var boardStop by remember { mutableIntStateOf(prefs.boardStop) }
    var dismissedAlert by remember { mutableStateOf<Int?>(null) }
    var showSplash by remember { mutableStateOf(true) }

    val requestLocation = rememberLocationRequester()

    // czy motyw jest ciemny (decyduje m.in. o filtrze kafelków mapy)
    val systemDark = isSystemInDarkTheme()
    val mapDark = when (appTheme) {
        AppTheme.SYSTEM -> systemDark
        AppTheme.MLEKO -> false
        else -> true
    }

    fun setFrom(ref: PlaceRef?) {
        from = ref
        prefs.lastFrom = ref?.let { SavedPlace.from(it) }
    }

    fun setTo(ref: PlaceRef?) {
        to = ref
        prefs.lastTo = ref?.let { SavedPlace.from(it) }
    }

    fun setBoard(fromRef: PlaceRef?) {
        val idx = fromRef?.let { stopForRef(world, it) } ?: -1
        if (idx >= 0) {
            boardStop = idx
            prefs.boardStop = idx
            tab = 1
            stack.clear()
            stack += Route.Plan
        } else if (fromRef != null) {
            Toast.makeText(context, t("Brak przystanku w pobliżu", "No stop nearby"), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            world = repo.world()
        } catch (e: Exception) {
            worldError = e.message ?: "błąd danych"
        }
        delay(650)
        showSplash = false
        announcements = Api.announcements()
    }

    // ciche odświeżenie danych rozkładowych przy starcie
    LaunchedEffect(Unit) {
        val res = repo.refresh()
        res.world?.let { world = it }
        dataNote = when {
            res.error != null -> t("Nie udało się odświeżyć danych: ", "Could not refresh data: ") + res.error
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
                    res.error != null -> t("Błąd odświeżania: ", "Refresh error: ") + res.error
                    else -> t("Dane rozkładowe zaktualizowane", "Schedule data updated")
                }
                refreshing = false
            }
        }
    }

    BackHandler(enabled = picker != null || stack.size > 1) {
        when {
            picker != null -> picker = null
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
        }
    }

    // skróty launchera (Szukaj / Tablica)
    LaunchedEffect(openTab) {
        if (openTab != null) {
            tab = openTab
            onOpenTabHandled()
        }
    }

    Crossfade(targetState = showSplash, animationSpec = tween(220), label = "splash") { splash ->
        if (splash) {
            SplashScreen()
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(t("Szukaj", "Plan")) },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.DirectionsBus, contentDescription = null) },
                            label = { Text(t("Tablica", "Board")) },
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                            label = { Text(t("Mapa", "Map")) },
                        )
                        NavigationBarItem(
                            selected = tab == 3,
                            onClick = { tab = 3 },
                            icon = { Icon(Icons.Filled.NewReleases, contentDescription = null) },
                            label = { Text(t("Zmiany", "Changes")) },
                        )
                        NavigationBarItem(
                            selected = tab == 4,
                            onClick = { tab = 4 },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                            label = { Text(t("Ustawienia", "Settings")) },
                        )
                    }
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
                                        date = date,
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
                                                        t("Nie udało się ustalić lokalizacji", "Could not get the location"),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            }
                                        },
                                        onPickDate = { showDatePicker = true },
                                        onClearDate = { date = null },
                                        onFind = {
                                            val f = from
                                            val d = to
                                            when {
                                                f == null -> picker = PickerTarget.FROM
                                                d == null -> picker = PickerTarget.TO
                                                else -> stack += Route.Results(
                                                    JourneyQuery(
                                                        from = f,
                                                        to = d,
                                                        date = date,
                                                        departMin = com.kiedziedojade.app.core.nowMinutes(),
                                                    ),
                                                )
                                            }
                                        },
                                        onQuickDestination = { item ->
                                            val ref = item.ref
                                            SavedPlace.from(ref)?.let { prefs.pushRecent(it) }
                                            setTo(ref)
                                            val f = from
                                            if (f != null) {
                                                stack += Route.Results(
                                                    JourneyQuery(
                                                        from = f,
                                                        to = ref,
                                                        date = date,
                                                        departMin = com.kiedziedojade.app.core.nowMinutes(),
                                                    ),
                                                )
                                            } else {
                                                picker = PickerTarget.FROM
                                            }
                                        },
                                    )

                                    is Route.Results -> ResultsScreen(
                                        world = w,
                                        query = route.query,
                                        dark = mapDark,
                                        onBack = { stack.removeAt(stack.lastIndex) },
                                        onOpenStop = { idx -> stack += Route.Stop(idx) },
                                    )

                                    is Route.Stop -> StopScreen(
                                        world = w,
                                        stopIndex = route.index,
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
                                requester = requestLocation,
                                onOpenBoard = { idx ->
                                    boardStop = idx
                                    prefs.boardStop = idx
                                    tab = 1
                                },
                                onPlanTo = { idx ->
                                    setTo(PlaceRef.StopRef(w.stops[idx].name, idx))
                                    tab = 0
                                    stack.clear()
                                    stack += Route.Plan
                                },
                            )

                            3 -> ChangesScreen(prefs = prefs)

                            4 -> SettingsScreen(
                                world = w,
                                prefs = prefs,
                                appTheme = appTheme,
                                onThemeChange = onThemeChange,
                                dataNote = dataNote,
                                refreshing = refreshing,
                                onRefreshData = onRefreshData,
                            )
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
                        else -> boardStop.takeIf { it >= 0 }?.let { PlaceRef.StopRef(w.stops[it].name, it) }
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
                                    t("Nie udało się ustalić lokalizacji", "Could not get the location"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
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
        }
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPick = { iso ->
                date = if (iso == com.kiedziedojade.app.core.todayStr()) null else iso
                showDatePicker = false
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
    // Surface ustawia właściwy kolor treści w KAŻDYM motywie — wcześniej tytuł
    // był czarny na ciemnym tle (brak Surface = domyślny czarny contentColor).
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
            Text(
                "KiedyDojadę",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "SZCZECINEK",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(26.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .size(width = 132.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
        }
    }
}

/** Nearest stop for any place reference (used when picking the board stop). */
fun stopForRef(world: World?, ref: PlaceRef): Int? {
    if (world == null) return null
    return when (ref) {
        is PlaceRef.StopRef -> ref.index
        else -> {
            val resolved = com.kiedziedojade.app.core.Planner.resolvePlace(ref, world.stops)
            nearestStops(world, resolved.lat, resolved.lng, limit = 1, maxMeters = 2000.0)
                .firstOrNull()?.first?.i
        }
    }
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
