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
                title = "1.5.3",
                lines = listOf(
                    t(
                        "Jasne motywy: karty i pasek nawigacji mają teraz prawdziwy kontur — biała obwódka znikała na jasnym tle i wszystko wyglądało płasko. Przycisk zamiany miejsc nie wchodzi już na przycisk GPS — siedzi na środku szwu, więc nic się nie nakłada.",
                        "Light themes: cards and the nav bar now have a real outline — the white rim disappeared on light backgrounds and everything looked flat. The from/to swap button no longer collides with the GPS button — it sits in the middle of the seam, so nothing overlaps.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.5.2",
                lines = listOf(
                    t(
                        "Szkło bez mgły: karty nie są już zalane białą poświatą — mają prawdziwą, cienką obwódkę, więc mapa i tło prześwitują, a tekst pozostaje ostry. Naprawiony czarny tekst w ciemnych motywach na ekranie prowadzenia, wyborze miejsca i wyborze punktu na mapie. Przełącznik „skąd/dokąd” dostał więcej powietrza.",
                        "Glass without fog: cards are no longer washed with a white sheen — they have a real thin rim, so the map and background show through while text stays crisp. Fixed black text in dark themes on navigation, the place picker and the map-point picker. The from/to swap knob got more breathing room.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.5.1",
                lines = listOf(
                    t(
                        "Szklarz dospadł reszty ekranów: karta instrukcji prowadzenia i pasek przyjazdu to teraz szkło nad mapą (przycisk „Google Maps” dostał bursztyn), filtr linii na mapie, przyciski zbliżenia i lokalizacji zmieniły materiał, okno wyszukiwania miejsc jest pełną pigułką, a wybór punktu na mapie zamyka się w szklanej karcie z bursztynowym przyciskiem.",
                        "The glassmaker finished the remaining surfaces: the navigation instruction card and arrival bar are glass over the map (the \"Google Maps\" spot got amber), the map's line filter, zoom and locate controls got the new material, the place-search field is a full pill, and the map-point picker now sits in a glass card with an amber confirm button.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.5.0",
                lines = listOf(
                    t(
                        "Przebudowany interfejs od zera: pływająca szklana kapsuła nawigacji zamiast klasycznego paska, pole „skąd/dokąd” w jednej szybie z bursztynową przekładką na szwie, popularne cele na poziomej szynie kafelków, pełne kolorowe znaczki linii jak na autobusach i nowe otwarcie aplikacji. Cała apka ma teraz wspólny język szkła: poświata, oświetlona krawędź i duże zaokrąglenia.",
                        "The interface rebuilt from scratch: a floating glass capsule for navigation instead of the classic bar, a single-glass \"from/to\" stack with an amber swap knob on the seam, popular destinations on a horizontal tile rail, solid colored line badges like on the buses, and a new splash. The whole app now speaks one glass language: sheen, top-lit edges and big rounds.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.24",
                lines = listOf(
                    t(
                        "Nowy ekran startowy w klimacie kart płatniczych: ciepła bursztynowo-pomarańczowa karta „hero” ze szklanym refleksem i biletem, przycisk „Znajdź połączenie” w tym samym stylu, a kafelki popularnych celów dostały delikatne obwódki i większe zaokrąglenia. Karty w całej aplikacji mają teraz miększe krawędzie.",
                        "A start screen in the banking-app spirit: a warm amber-orange \"hero\" card with a glass reflection and a little ticket, the \"Find a connection\" button in the same style, and popular-destination tiles with subtle outlines and rounder corners. Cards across the whole app got softer edges.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.23",
                lines = listOf(
                    t(
                        "Zoom prowadzenia po ludzku: podczas jazdy autobusem mapa cofa się szerzej (widać przejazd i kilka przystanków przed sobą), a marsz i czekanie na przystanku trzymają bliski kadr. Z karty prowadzenia zniknęło też odliczanie „Dojazd” do następnego przystanku — bywało mylące; nazwa przystanku i godzina wysiadki zostały.",
                        "Sane navigation zoom: while riding the bus the map widens (you can see the ride and a few stops ahead), while walking and waiting keep a close view. The \"Due\" countdown to the next stop is gone from the navigation card — it was confusing; the stop name and get-off time remain.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.22",
                lines = listOf(
                    t(
                        "Podglądasz w zoomie — mapa dopisuje ostrość: kafelki są teraz renderowane z najbliższego poziomu, więc powiększanie pomiędzy pełnymi stopniami nie rozmywa ulic. Znacznik Twojej pozycji w prowadzeniu zastąpiła elegancka iglica kursu (ikonka „navigation” Material Symbols) z białą obwódką, cieniem i łagodniejszą poświatą.",
                        "Zoom in and the map sharpens: tiles now render from the nearest level, so zooming between full steps no longer blurs the streets. Your position marker in navigation got an elegant course needle (the Material Symbols \"navigation\" icon) with a white casing, a soft shadow and a gentler glow.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.21",
                lines = listOf(
                    t(
                        "Poprawka znacznika na żywo: przy zmianie częstotliwości odświeżania (przystanek, zbliżenie) znacznik autobusu potrafił zamrzeć w połowie drogi i cofnąć się przy następnym odczycie. Ponadto karta „Pieszo” nie chwali się już, że jest „szybsza niż autobus”, gdy żadna linia na trasie nie kursuje.",
                        "Live marker fix: when the refresh cadence changed (a stop, getting closer), the bus marker could freeze mid-glide and hop back on the next update. Also, the walk card no longer claims it's \"faster than the bus\" when no line is running at all.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.20",
                lines = listOf(
                    t(
                        "Prowadzenie na żywo płynniej: autobus jedzie po mapie cały czas między odczytami (bez zamierania i przeskoku przy każdej aktualizacji), a strzałka kursu obraca się spokojniej. Mapa w jasnym motywie ma teraz lekko wyciszone kolory — beże OSM tonem zsiadają się z aplikacją; etykiety i linie zostały równie czytelne.",
                        "Smoother live tracking: the bus now moves continuously on the map between updates (no freezing mid-glide or snapping on each refresh), and its heading wheel turns more gently. The light-theme map got subtly muted colors — OSM's beiges now sit in tune with the app; labels and lines are just as readable.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.19",
                lines = listOf(
                    t(
                        "Lista wyników bez zbędnej karty „Pieszo”: pojawia się dopiero wtedy, gdy żadna linia nie jedzie w stronę celu. Opcja piesza nie znika — jest po prostu tam, gdzie jej szukasz.",
                        "Results list without the unnecessary walk card: it now appears only when no bus heads toward your destination. The walking option didn't disappear — it just lives where you'd look for it.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.18",
                lines = listOf(
                    t(
                        "Spokojniejsza tablica i wyniki: odjazdy mają ożywioną kropkę zamiast stosu etykiet, ostatnie minuty przed odjazdem podświetlają się, a najlepsze połączenie pokazuje odliczanie do wyjazdu.",
                        "Calm board and results: departures carry a pulsing live dot instead of chip rows, the last minutes before departure light up, and the best connection shows a departure countdown.",
                    ),
                    t(
                        "Drobiazgi wizualne: puste stany dostały miękkie tło, rozwijanie kursów i ikony ogłoszeń są spójniejsze z resztą aplikacji. Bez zmian w funkcjach.",
                        "Visual touches: soft backgrounds for empty states, and trip/announcement icons now match the rest of the app. No functional changes.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.17",
                lines = listOf(
                    t(
                        "Zabezpieczenia danych: rozjazd wersji plików rozkładu między przystankami a kursami nie rozwija już mapy ani planera — podejrzane wpisy są odrzucane, a nie powodują awarii.",
                        "Data hardening: a version mismatch between stops and trips files can no longer crash the map or planner — suspicious entries are dropped instead of failing.",
                    ),
                    t(
                        "Bezpieczny dostęp równoległy w planerze i drobne szlify wizualne: animacja wejścia listy nie powtarza się przy przewijaniu, a mapa radzi sobie z pustą listą przystanków.",
                        "Thread-safe planner access and visual polish: list entrance animation no longer replays when scrolling, and the map survives an empty stop list.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.16",
                lines = listOf(
                    t(
                        "Poprawki stabilności: prowadzenie nie utyka już po trzech błędach pobierania kursu na żywo, a nieudana aktualizacja rozkładu nie zostawia pustego folderu z danymi.",
                        "Stability fixes: live-run tracking no longer gets stuck after three failed downloads, and a failed schedule update no longer leaves an empty data folder.",
                    ),
                    t(
                        "Zabezpieczenia bez zmian w funkcjach: ekrany prowadzenia, wyników i przystanku są odporne na nietypowe dane, a pamięć planera nie rośnie bez końca.",
                        "Hardening with no feature changes: the navigation, results and stop screens handle unusual data safely, and the planner's memory no longer grows without bound.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.15",
                lines = listOf(
                    t(
                        "Cała aplikacja ma teraz spokojniejszy, bardziej spójny wygląd: karty lepiej odcinają się od tła, nagłówki i marginesy są wspólne, a przyciski wyboru używają łagodnego błękitu zamiast wielu mocnych akcentów.",
                        "The whole app now looks calmer and more consistent: cards stand out from the background, headers and margins match, and selection buttons use a soft blue instead of many strong accents.",
                    ),
                    t(
                        "Mapa: lżejszy pasek filtrów linii, kompaktowa karta przystanku i autobusu oraz przycisk lokalizacji obok filtrów. Ulubione, ustawienia i tablica dostały krótsze, czytelniejsze karty.",
                        "Map: a lighter line-filter bar, a compact stop and bus card, and a locate button next to the filters. Saved routes, settings and the board got shorter, clearer cards.",
                    ),
                    t(
                        "Prowadzenie: niższa karta, Google Maps obok linii i „za chwilę” zamiast „za 0 minut”. Poza Szczecinkiem aplikacja od razu wyjaśnia, że obsługuje tylko autobusy miejskie w Szczecinku — GPS działa, ale nie ma tam przystanków z naszego rozkładu. Komunikaty o lokalizacji i odświeżaniu danych są teraz konkretniejsze.",
                        "Navigation: a shorter card, Google Maps next to the line, and “in a moment” instead of “in 0 minutes”. Outside Szczecinek the app now explains that it only covers Szczecinek city buses — GPS works, but there are no stops from our timetable there. Location and data-refresh messages are now more specific.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.14",
                lines = listOf(
                    t(
                        "Wiersz „Marsz” pokazuje teraz godzinę dojścia do przystanku i odliczanie, tak samo jak „Odjazd” — np. „Marsz: 19:20 · za 9 minut” obok „Odjazd: 19:21 · za 10 minut”.",
                        "The “Walk” row now shows the time you reach the stop and the countdown, just like “Departure” — e.g. “Walk: 19:20 · in 9 minutes” next to “Departure: 19:21 · in 10 minutes”.",
                    ),
                    t(
                        "Odległość w metrach zniknęła z tego wiersza. Pozostałe informacje — autobus i opóźnienie — zostają bez zmian.",
                        "The distance in metres is gone from that row. The other information — bus and delay — stays the same.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.13",
                lines = listOf(
                    t(
                        "Gdy telefon nie ma pozycji GPS, mapa nie centruje się już na autobusie. Pokazuje trasę i komunikat „GPS wyłączony” lub „szukam pozycji…”, więc znacznik autobusu nie wygląda jak Twoja lokalizacja.",
                        "When the phone has no GPS fix, the map no longer centers on the bus. It shows the route and a “location off” or “finding location…” message, so the bus marker no longer looks like your position.",
                    ),
                    t(
                        "Przy działającym GPS nic się nie zmienia: niebieska strzałka nadal pokazuje Twoją pozycję i jedzie razem z Tobą.",
                        "With a working GPS nothing changes: the blue arrow still shows your position and follows you.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.12",
                lines = listOf(
                    t(
                        "Karta prowadzenia pokazuje teraz każdą wartość pod własną etykietą: Marsz, Odjazd, Autobus, Wysiadka i Opóźnienie. Koniec z ciągiem liczb, w którym nie wiadomo, która jest która.",
                        "The navigation card now labels every value: Walk, Departure, Bus, Get off and Delay. No more a run of numbers where you cannot tell which is which.",
                    ),
                    t(
                        "Linia ma podpis „Linia 1”, odległość autobusu brzmi „6 przystanków przed Tobą”, a zamiast samego „+1 min” widać „Opóźnienie: +1 min”. Poprawiliśmy też odmianę „za 1 minutę”.",
                        "The line is labelled “Line 1”, the bus distance reads “6 stops ahead”, and instead of a bare “+1 min” you see “Delay: +1 min”. We also fixed the Polish “za 1 minutę”.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.11",
                lines = listOf(
                    t(
                        "Nawigacja pokazuje odjazd autobusu i odliczanie juz podczas marszu do przystanku, a w trakcie jazdy nazwe nastepnego przystanku i godzine. Bledna lokalizacja z emulatora lub GPS ponad 50 km od trasy nie psuje juz wskazowek.",
                        "Navigation now shows the bus departure and countdown while walking to the stop, and the next stop with its time while riding. A bogus emulator or GPS fix more than 50 km from the route no longer breaks the directions.",
                    ),
                    t(
                        "Gdy miasto nie podaje jeszcze kursu na zywo, korzystamy z planowego rozkladu, wiec odjazd i nastepny przystanek sa widoczne od razu.",
                        "Where the city has no live run yet, we fall back to the timetable, so the departure and next stop still appear.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.10",
                lines = listOf(
                    t(
                        "Dolna nawigacja i kafelki motywów mieszczą się teraz w 360 dp, a pola trasy pokazują krótsze nazwy miejsc. Przycisk GPS przeniósł się do pola „Skąd”, więc „Znajdź połączenie” widać bez przewijania.",
                        "Bottom navigation and theme tiles now fit at 360 dp, and route fields use shorter place names. The GPS button moved into the “From” field, so “Find a connection” is visible without scrolling.",
                    ),
                    t(
                        "Komunikaty zwijają się do dwóch linii z „Czytaj dalej”. W angielskiej wersji zniknęły polskie liczby mnogie, a „Moja lokalizacja” tłumaczy się po zmianie języka. Mapa ma mniejsze kropki i bliższy domyślny zoom.",
                        "Announcements collapse to two lines with “Read more”. English no longer shows Polish plurals, and “My location” retranslates after switching language. The map has smaller dots and a closer default zoom.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.9",
                lines = listOf(
                    t(
                        "Ulubione trasy mają teraz własną godzinę odjazdu. Dotknij zegara na karcie trasy, wybierz godzinę i zapisz ją — przy ponownym wyszukaniu z Ulubionych od razu użyjemy tej godziny. „Teraz” czyści ustawienie.",
                        "Favourite routes can now store their own departure time. Tap the clock on a route card, choose a time and save it — opening the route from Favourites will search with that time. “Now” clears the setting.",
                    ),
                    t(
                        "Godzina jest zapisywana osobno dla każdej trasy i pozostaje po restarcie aplikacji. Dotknięcie karty nadal otwiera wyniki; dotknięcie godziny tylko ją zmienia. Jeśli godzina już minęła, pokażemy najbliższy kurs następnego dnia.",
                        "The time is saved per route and survives app restarts. Tapping the card still opens results; tapping the time only changes it. If the time has already passed, we show the next trip the following day.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.8",
                lines = listOf(
                    t(
                        "Autobusy na żywo na mapie: po wybraniu linii autobusy w trasie są oznaczone numerem linii na kolorowym znaczniku. Dotknij autobusu, aby zobaczyć kierunek, następny przystanek i czas dojazdu.",
                        "Live buses on the map: after choosing a line, buses on the route are marked with the line number on a coloured marker. Tap a bus to see its direction, next stop and arrival time.",
                    ),
                    t(
                        "Pokazujemy tylko autobusy, które już rozpoczęły kurs — pojazdy czekające w zajezdni nie trafiają na mapę. Pozycje odświeżają się co około 20 sekund tylko przy widocznym ekranie, a bez internetu mapa nie wysyła zapytań.",
                        "Only buses that have already started their run appear — vehicles waiting at the depot are not shown. Positions refresh about every 20 seconds only while the screen is visible, and without internet the map sends no requests.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.7",
                lines = listOf(
                    t(
                        "Prowadzenie nie kończy się przedwcześnie, gdy autobus mija cel późniejszego marszu. Przystanki są rozpoznawane po stabilnym identyfikatorze, więc po aktualizacji danych wyniki i tablice nie wskazują już złego przystanku.",
                        "Navigation no longer ends early when the bus passes the destination of a later walk. Stops are recognised by a stable identifier, so after a data update results and boards no longer point at the wrong stop.",
                    ),
                    t(
                        "Porzucona podróż wygasa trzy godziny po planowym przyjeździe — także po północy — więc GPS nie działa już przez resztę dnia. Jakość sygnału jest oceniana tak samo w prowadzeniu i w powiadomieniu.",
                        "An abandoned trip expires three hours after its scheduled arrival, including across midnight, so GPS no longer runs for the rest of the day. Signal quality is judged the same way in navigation and in the notification.",
                    ),
                    t(
                        "Obrót ekranu nie gubi wyników ani otwartego przystanku, „Zakończ” w powiadomieniu zamyka prowadzenie, a ulica wybrana jako skąd lub dokąd jest pamiętana po restarcie.",
                        "Rotating the screen no longer loses results or an open stop, “Finish” in the notification closes navigation, and a street chosen as the start or destination survives a restart.",
                    ),
                ),
                index = 0,
            )
        }

        item {
            InfoCard(
                title = "1.4.6",
                lines = listOf(
                    t(
                        "Przebyta część trasy znika z mapy prowadzenia: minione odcinki nie są już rysowane, a bieżący kończy się tuż za Tobą. Widzisz tylko drogę przed sobą, bez ogona za plecami.",
                        "The traveled part of the route disappears from the navigation map: completed legs are no longer drawn and the current one ends just behind you. You only see the road ahead, with no tail behind you.",
                    ),
                    t(
                        "Nowa zakładka „Ulubione”: gwiazdka w nagłówku wyników zapisuje trasę skąd → dokąd. Dotknij zapisanej trasy, aby od razu wyszukać połączenie, a kosz usuwa ją z listy. Działa też dla dowolnych punktów wskazanych na mapie.",
                        "New “Favourites” tab: the star in the results header saves a from → to route. Tap a saved route to search it again, and the bin removes it. It also works for any custom point picked on the map.",
                    ),
                ),
                index = 1,
            )
        }

        item {
            InfoCard(
                title = "1.4.5",
                lines = listOf(
                    t(
                        "Znacznik pozycji nie skacze już po błędnych odczytach GPS: nieświeże i niemożliwe ramki są odrzucane, a znacznik płynnie przejeżdża między poprawnymi pozycjami.",
                        "The position marker no longer jumps on bad GPS readings: stale and impossible fixes are dropped, and the marker glides smoothly between valid positions.",
                    ),
                    t(
                        "Kierunek jest stabilny: w ruchu strzałka bierze kurs GPS, w miejscu — kompas telefonu, a gdy czujnik chwilowo zawiedzie, zostaje ostatni znany kierunek.",
                        "Heading is stable: while moving the arrow follows the GPS course, when standing it follows the phone compass, and if the sensor drops out it keeps the last known direction.",
                    ),
                    t(
                        "Nowy cel: „Wskaż na mapie” pozwala wybrać dowolny punkt jako start lub dokąd — nie tylko duże przystanki. Punkt zapisuje się w ostatnio używanych razem ze współrzędnymi.",
                        "New destination: “Pick on map” lets you choose any point as start or destination — not just the big stops. The point is saved in recents together with its coordinates.",
                    ),
                ),
                index = 1,
            )
        }

        item {
            InfoCard(
                title = "1.4.4",
                lines = listOf(
                    t(
                        "Prowadzenie działa dalej po wyjściu z aplikacji. Foreground service utrzymuje GPS i dane na żywo, a stałe powiadomienie pokazuje linię, następny przystanek i wysiadkę.",
                        "Navigation keeps running after you leave the app. A foreground service maintains GPS and live data, and an ongoing notification shows the line, next stop and get-off point.",
                    ),
                    t(
                        "Aplikacja sama wykrywa wsiadanie na podstawie pozycji i kursu na żywo — bez potwierdzania. Po powrocie ekran prowadzenia wraca w tym samym miejscu; „Ukryj” zostawia śledzenie, a „Zakończ” je czyści.",
                        "The app detects boarding on its own from your position and the live trip — no confirmation needed. When you return, navigation resumes where it left off; “Hide” keeps tracking, “Finish” clears it.",
                    ),
                    t(
                        "Przy starcie prowadzenia Android 13+ poprosi o zgodę na powiadomienia. Bez niej śledzenie nadal działa, ale nie zobaczysz stałego powiadomienia.",
                        "On Android 13+, starting navigation asks for notification permission. Without it tracking still works, but the ongoing notification is hidden.",
                    ),
                ),
                index = 1,
            )
        }

        item {
            InfoCard(
                title = "1.4.3",
                lines = listOf(
                    t(
                        "Nie wybierasz już dnia. Podajesz godzinę odjazdu — jeśli już minęła, aplikacja pokaże najbliższy kurs jutro, a nagłówek zawsze mówi, czy plan dotyczy dziś czy jutra.",
                        "No more day picker. Choose a departure time — if it has already passed, the app shows tomorrow's next trip, and the header always says whether the plan is for today or tomorrow.",
                    ),
                    t(
                        "Plan na jutro nie udaje dnia bieżącego: dane na żywo i prowadzenie są dostępne w dniu podróży, a rozkład przystanku pokazuje właściwy dzień.",
                        "Tomorrow's plan no longer pretends to be today: live data and navigation are available on the travel day, and the stop timetable shows the correct day.",
                    ),
                    t(
                        "Autobus na żywo jest dopasowany także po kierunku linii — kurs jadący w przeciwną stronę nie podmienia już Twojego odjazdu.",
                        "Live buses are matched by line direction too, so a trip going the opposite way no longer replaces your departure.",
                    ),
                    t(
                        "Ulubione i ostatnie przystanki przeżywają aktualizację danych dzięki stabilnym identyfikatorom, a gwiazdka odświeża się od razu.",
                        "Favourite and recent stops survive data refreshes thanks to stable identifiers, and the star updates immediately.",
                    ),
                    t(
                        "Prowadzenie nie twierdzi już, że jesteś w autobusie, gdy brak potwierdzenia z GPS lub API — zamiast tego prosi o sprawdzenie tablicy.",
                        "Navigation no longer claims you are on the bus without GPS or live confirmation — it asks you to check the board instead.",
                    ),
                ),
                index = 1,
            )
        }

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
