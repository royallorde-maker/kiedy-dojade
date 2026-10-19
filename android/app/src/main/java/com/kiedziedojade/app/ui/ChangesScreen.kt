package com.kiedziedojade.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiedziedojade.app.BuildConfig
import com.kiedziedojade.app.core.I18n.t
import com.kiedziedojade.app.core.Prefs
import com.kiedziedojade.app.update.DlState
import com.kiedziedojade.app.update.DownloadStatus
import com.kiedziedojade.app.update.UpdateState
import com.kiedziedojade.app.update.checkForUpdate
import com.kiedziedojade.app.update.downloadAndInstall
import com.kiedziedojade.app.update.installApkFile
import com.kiedziedojade.app.update.queryDownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChangesScreen(prefs: Prefs, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var dl by remember { mutableStateOf<DownloadStatus?>(null) }

    fun check() {
        scope.launch {
            state = UpdateState.Checking
            state = checkForUpdate(context)
        }
    }

    LaunchedEffect(Unit) {
        if (state is UpdateState.Idle) check()
    }

    val info = (state as? UpdateState.Available)?.info
    var pollDownload by remember(info) { mutableStateOf(false) }
    LaunchedEffect(info, pollDownload) {
        if (info == null) {
            dl = null
            return@LaunchedEffect
        }
        dl = queryDownloadStatus(context, info.versionName)
        if (!pollDownload) {
            // pobieranie mogło zacząć się przed wejściem w ten ekran
            if (dl?.state == DlState.RUNNING) pollDownload = true
            return@LaunchedEffect
        }
        while (true) {
            dl = queryDownloadStatus(context, info.versionName)
            val current = dl?.state
            if (current == DlState.SUCCESS || current == DlState.FAILED) {
                pollDownload = false
                break
            }
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Appear(0) {
                ScreenHeader(
                    title = t("Zmiany", "Changes"),
                    subtitle = t("Historia wersji i aktualizacje", "Version history and updates"),
                    onBack = onBack,
                )
            }
        }

        item {
            Appear(1) {
                KdCard(highlighted = info != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when {
                                info != null -> Icons.Filled.RocketLaunch
                                state is UpdateState.UpToDate -> Icons.Filled.CheckCircle
                                state is UpdateState.Offline -> Icons.Filled.WifiOff
                                state is UpdateState.Error -> Icons.Filled.ErrorOutline
                                else -> Icons.Filled.CloudDownload
                            },
                            contentDescription = null,
                            tint = when {
                                info != null -> MaterialTheme.colorScheme.primary
                                state is UpdateState.UpToDate -> MaterialTheme.colorScheme.tertiary
                                state is UpdateState.Error || state is UpdateState.Offline -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when (val s = state) {
                                    is UpdateState.Available -> t("Dostępna wersja ", "Version ") + s.info.versionName
                                    is UpdateState.UpToDate -> t("Masz najnowszą wersję", "You are up to date")
                                    is UpdateState.Checking -> t("Sprawdzam aktualizacje…", "Checking for updates…")
                                    is UpdateState.Offline -> t("Brak połączenia", "No connection")
                                    is UpdateState.Error -> t("Nie udało się sprawdzić", "Check failed")
                                    UpdateState.Idle -> t("Aktualizacje", "Updates")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                when (val s = state) {
                                    is UpdateState.Available -> s.info.changelog.ifEmpty {
                                        t("Dotknij, aby pobrać i zainstalować.", "Tap to download and install.")
                                    }

                                    is UpdateState.UpToDate -> t("Wersja ", "Version ") + BuildConfig.VERSION_NAME
                                    is UpdateState.Error -> s.message
                                    is UpdateState.Offline -> t("Sprawdź internet i spróbuj ponownie.", "Check the internet and retry.")
                                    else -> t("Wersja ", "Version ") + BuildConfig.VERSION_NAME
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state is UpdateState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    when (val s = state) {
                        is UpdateState.Available -> {
                            if (dl?.state == DlState.RUNNING) {
                                val frac = dl?.fraction
                                if (frac != null) {
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    t("Pobieranie aktualizacji…", "Downloading the update…"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Button(
                                    onClick = {
                                        when (dl?.state) {
                                            DlState.SUCCESS -> installApkFile(context, s.info.versionName)
                                            else -> {
                                                downloadAndInstall(context, s.info)
                                                pollDownload = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        when (dl?.state) {
                                            DlState.SUCCESS -> t("Zainstaluj ", "Install ") + s.info.versionName
                                            DlState.FAILED -> t("Pobierz ponownie", "Download again")
                                            DlState.PAUSED -> t("Wznów pobieranie", "Resume the download")
                                            else -> t("Pobierz i zainstaluj", "Download and install")
                                        },
                                    )
                                }
                                if (s.info.force) {
                                    Spacer(Modifier.height(6.dp))
                                    StatusChip(
                                        t("Wymagana aktualizacja", "Update required"),
                                        MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        is UpdateState.UpToDate -> TextButton(onClick = { check() }, modifier = Modifier.fillMaxWidth()) {
                            Text(t("Sprawdź ponownie", "Check again"))
                        }

                        else -> TextButton(onClick = { check() }, modifier = Modifier.fillMaxWidth()) {
                            Text(t("Sprawdź aktualizacje", "Check for updates"))
                        }
                    }
                }
            }
        }

        item { SectionTitle(t("Historia wersji", "Version history")) }

        item {
            InfoCard(
                title = "1.4.2",
                lines = listOf(
                    t(
                        "Trasa autobusu na mapie prowadzi teraz po ulicach, a nie prosto przez budynki — w prowadzeniu, po wybraniu linii na mapie i w miniaturach wyników.",
                        "The bus route on the map now follows streets instead of cutting through buildings — in navigation, on the line map and in result thumbnails.",
                    ),
                    t(
                        "Geometria przejazdów pochodzi z routera OSRM i jest cache'owana; bez internetu zostaje dotychczasowa linia po przystankach.",
                        "Ride geometry comes from the OSRM router and is cached; offline it falls back to the stop-to-stop line.",
                    ),
                ),
                index = 1,
            )
        }

        item {
            InfoCard(
                title = "1.4.1",
                lines = listOf(
                    t(
                        "Nowy domyślny styl mapy: czytelny OpenStreetMap zamiast pastelowego HOT.",
                        "A new default map style: clean OpenStreetMap instead of pastel HOT.",
                    ),
                    t(
                        "Styl mapy wybierzesz w Ustawieniach: Domyślny, Kolorowy i HOT. Działa na mapie przystanków, miniaturach tras i w prowadzeniu.",
                        "Pick the map style in Settings: Default, Colourful and HOT. It applies to the stop map, route thumbnails and navigation.",
                    ),
                    t(
                        "Wybrany styl jest zapamiętywany i działa od razu, bez ponownego uruchamiania.",
                        "Your choice is remembered and applies immediately, without restarting.",
                    ),
                ),
                index = 1,
            )
        }

        item {
            InfoCard(
                title = "1.4.0",
                lines = listOf(
                    t(
                        "Mniej zbędnych ekranów: cztery zakładki, a aktualizacje i historię wersji znajdziesz w Ustawieniach.",
                        "Fewer screens: four tabs, with updates and version history moved into Settings.",
                    ),
                    t(
                        "Mapa prowadzenia reaguje na gesty: przeciągnij, aby rozejrzeć się, i użyj pinch lub przycisków +/−. Przycisk lokalizacji wraca do śledzenia.",
                        "The navigation map responds to gestures: drag to look around, pinch or use +/−. The location button returns to following.",
                    ),
                    t(
                        "Automatyczny zoom przed zakrętami, jak w Mapach Google — bliżej na skręcie, spokojniej na prostej.",
                        "Automatic zoom before corners, like Google Maps — closer at a turn, calmer on a straight.",
                    ),
                    t(
                        "Jaśniejszy styl mapy Humanitarian (HOT), pełna atrybucja OpenStreetMap i ograniczony cache kafelków.",
                        "A brighter Humanitarian (HOT) map style, full OpenStreetMap attribution and a bounded tile cache.",
                    ),
                    t(
                        "Oszczędniejsze odświeżanie: GPS i autobus na żywo pauzują w tle, a zapytania są częstsze tylko przy przystanku lub blisko autobusu.",
                        "Leaner refreshing: GPS and live buses pause in the background, and polling speeds up only at a stop or near the bus.",
                    ),
                    t(
                        "Lżejsze wyniki i tablica: mapa trasy dopiero po rozwinięciu, kompaktowe popularne cele, czytelniejsze stany ładowania.",
                        "Lighter results and board: the route map appears on expand, compact popular destinations and clearer loading states.",
                    ),
                ),
                index = 2,
            )
        }

        item {
            InfoCard(
                title = "1.3.0",
                lines = listOf(
                    t(
                        "Autobus na żywo na mapie: znacznik pokazuje bieżącą pozycję pojazdu i płynnie jedzie po trasie.",
                        "Live bus on the map: the marker shows the vehicle's current position and moves smoothly along the route.",
                    ),
                    t(
                        "ETA i przystanki na żywo: opóźnienie, godzinę przyjazdu i liczbę przystanków do wysiadki liczymy z kursu, a nie z rozkładu.",
                        "Live ETA and stops: delay, arrival time and the number of stops left come from the actual run, not the timetable.",
                    ),
                    t(
                        "Gdy autobus podjedzie na Twój przystanek, poczujesz wibrację i zobaczysz „Wsiądź do linii”.",
                        "When the bus reaches your stop, you feel a vibration and see “Board line”.",
                    ),
                    t(
                        "Prowadzenie po połączeniu: dotknij „Prowadź” w karcie wyniku, a mapa pojedzie za Twoją pozycją GPS.",
                        "Turn-by-turn guidance: tap “Navigate” on a result card and the map follows your GPS position.",
                    ),
                    t(
                        "Instrukcja na żywo: ile metrów do przystanku, o której odjeżdża autobus i za ile przystanków wysiąść.",
                        "Live instructions: how far to the stop, when the bus leaves and in how many stops to get off.",
                    ),
                    t(
                        "Wibracja i hasło „Wysiądź teraz”, gdy dojeżdżasz do swojego przystanku, oraz ekran, który nie gaśnie po drodze.",
                        "A vibration and a “Get off now” prompt at your stop, and a screen that stays awake on the way.",
                    ),
                    t(
                        "Przycisk „Google Maps” przekazuje marsz do przystanku lub do celu nawigacji Google.",
                        "A “Google Maps” button hands the walk to the stop or destination over to Google navigation.",
                    ),
                ),
                index = 2,
            )
        }

        item {
            InfoCard(
                title = "1.2.0",
                lines = listOf(
                    t(
                        "Trasa każdego połączenia na mapie — marsz, przesiadki i linie w kolorach, prosto w liście wyników.",
                        "Every connection shows its route on a map — walk, changes and lines in colour, right in the results.",
                    ),
                    t(
                        "Kropka startu i mety podróży, przystanki pośrednie jak w opisie trasy.",
                        "Start and finish dots, intermediate stops just like in the route description.",
                    ),
                    t(
                        "Przesuwanie palcem po mapce przewija wyniki, więc nic nie blokuje listy.",
                        "Dragging across the mini map scrolls the results, so nothing blocks the list.",
                    ),
                    t(
                        "Lżejsze karty wyników: godziny, jedna linijka faktów (czas, przesiadki, marsz) i spokojniejsze wiersze przystanków.",
                        "Lighter result cards: times, one line of facts (time, changes, walk) and calmer stop rows.",
                    ),
                ),
                index = 2,
            )
        }

        item {
            InfoCard(
                title = "1.1.0",
                lines = listOf(
                    t(
                        "Mapa przystanków i tras (OpenStreetMap) — filtr linii, pozycja GPS, karta przystanku.",
                        "Stop and route map (OpenStreetMap) — line filter, GPS position, stop card.",
                    ),
                    t(
                        "„Tablica” i „Zaplanuj” prosto z mapy, jednym dotknięciem.",
                        "“Board” and “Plan” straight from the map, with one tap.",
                    ),
                    t(
                        "Poprawione kolory ekranu startowego w ciemnych motywach (czarny tekst → jasny).",
                        "Fixed splash screen colors in dark themes (black text → light).",
                    ),
                    t(
                        "Drobne poprawki interfejsu: ikony, etykiety, spójność motywów.",
                        "Small interface fixes: icons, labels, theme consistency.",
                    ),
                ),
                index = 2,
            )
        }

        item {
            InfoCard(
                title = "1.0.0",
                lines = listOf(
                    t(
                        "Pierwsze wydanie: planer połączeń KM Szczecinek.",
                        "First release: a trip planner for Szczecinek city buses.",
                    ),
                    t(
                        "Najbliższy przystanek z Twojej lokalizacji i plan podróży z przesiadkami.",
                        "Nearest stop from your location and a journey plan with transfers.",
                    ),
                    t(
                        "Tablica odjazdów na żywo (opóźnienia, odwołania) i rozkłady offline.",
                        "Live departure board (delays, cancellations) and offline schedules.",
                    ),
                    t(
                        "Wybór spośród 8 motywów, polski i angielski, aktualizacje w aplikacji.",
                        "8 themes to choose from, Polish and English, in-app updates.",
                    ),
                ),
                index = 3,
            )
        }

        item {
            InfoCard(
                title = t("Jak działa wersjonowanie?", "How versioning works"),
                lines = listOf(
                    t(
                        "Nowa wersja pojawia się tutaj jako karta z przyciskiem pobierania.",
                        "A new version shows up here as a card with a download button.",
                    ),
                    t(
                        "Dane rozkładowe odświeżają się codziennie rano bez aktualizacji aplikacji.",
                        "Schedule data refreshes every morning without an app update.",
                    ),
                ),
                index = 4,
            )
        }
    }
}
