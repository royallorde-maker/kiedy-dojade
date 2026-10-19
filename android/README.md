# KiedyDojadę — aplikacja Android

Natywna aplikacja (Kotlin + Jetpack Compose, Material 3) — planer podróży
autobusami miejskimi w Szczecinku. Siostrzana do strony PWA w katalogu głównym
repozytorium: dzieli z nią dane rozkładowe i styl.

## Co potrafi

- **Cel podróży zamiast rozkładu** — wpisujesz przystanek, ulicę lub punkt;
  GPS podpowiada najbliższy przystanek wyjściowy.
- **Planer połączeń** (port `src/planner.ts`): skanowanie połączeń, maks.
  2 przesiadki, dojścia pieszo (900 m, 4,7 km/h, współczynnik 1,25), opcja
  „tylko pieszo”, gdy spacer jest szybszy.
- **Tablica odjazdów na żywo** — publiczne API KiedyPrzyjedzie.pl (opóźnienia,
  odwołania), odświeżanie co 30 s + rozkład dnia offline.
- **Mapa przystanków** (OpenStreetMap przez osmdroid — bez klucza API): filtr
  linii rysuje trasę i zawęża przystanki, pozycja GPS, karta przystanku z
  przejściem do tablicy/planowania. Kafelki w cache aplikacji, w ciemnych
  motywach przyciemnione filtrem.
- **Trasa połączenia na mapie** — każda propozycja w wynikach pokazuje swoją
  trasę: marsz przerywaną kreską, przejazdy w kolorach linii, kropki
  przystanków oraz start i meta podróży (miniatura OSM).
- **Prowadzenie po połączeniu** — pełnoekranowa mapa jedzie za pozycją GPS,
  instrukcje prowadzą do przystanku i przez przesiadki, a autobus jest śledzony
  na żywo: pozycja, ETA i liczba przystanków do wysiadki. Wibracja ostrzega
  przed przyjazdem autobusu i momentem wysiadki; marsz rysowany jest po ulicach
  (OSRM), a przycisk „Google Maps” przekazuje nawigację dalej.
- **Rozkłady offline** — dane wbudowane w APK (assets) i odświeżane z GitHuba
  (atomowa podmiana katalogu; brak sieci = dalej działa).
- **8 motywów**, PL/EN, aktualizacje w aplikacji (OTA jak w bs-companion).

## Struktura

```
android/app/src/main/java/com/kiedziedojade/app/
├── MainActivity.kt          # motyw, skróty z launchera
├── core/                    # cała logika (bez Androida poza Context/Prefs)
│   ├── Models.kt            # typy danych (port types.ts)
│   ├── Geo.kt               # haversine, czasy, strefa Europe/Warsaw
│   ├── Search.kt            # wyszukiwarka przystanków/ulic/punktów (port search.ts)
│   ├── Planner.kt           # planer połączeń (port planner.ts)
│   ├── MapData.kt           # linie, trasy i przystanki dla mapy
│   ├── RoutePath.kt         # nogi połączenia → odcinki trasy na mapie
│   ├── Foot.kt              # geometria marszu po ulicach (OSRM foot)
│   ├── Live.kt              # dopasowanie kursu na żywo, różnice minut
│   ├── Nav.kt               # kroki prowadzenia, postęp GPS, wysiadka
│   ├── DataJson.kt          # parsery public/data/*.json
│   ├── DataRepo.kt          # assets + atomowe odświeżanie z GitHuba
│   ├── Api.kt               # odjazdy na żywo (WAF wymaga User-Agenta przeglądarki)
│   ├── Prefs.kt             # motyw, język, ulubione, ostatnie zapytanie
│   └── Loc.kt               # lokalizacja jednorazowa i strumień GPS (LocationManager)
├── ui/                      # ekrany Compose (Szukaj, Tablica, Mapa, Zmiany, Ustawienia)
│   ├── NavScreen.kt         # pełnoekranowe prowadzenie i dane autobusu na żywo
│   └── Gmaps.kt             # przekazanie nawigacji do Google Maps
└── update/Update.kt         # OTA: version.json → DownloadManager → instalator
```

Dane rozkładowe: `app/src/main/assets/data/` (kopia `public/data/` z repo — świeży
instal działa od razu offline). Testy jednostkowe czytają tę samą kopię z
`app/src/test/resources/data/`.

## Build lokalnie

```bash
cd android
gradle :app:testDebugUnitTest     # testy planera na prawdziwych danych
gradle :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
gradle :app:assembleRelease       # wymaga keystore.properties
```

Wymagania: JDK 17, Android SDK (platform 35, build-tools 34.0.0), Gradle 8.7.

## Podpisywanie i wydania

- Klucz wydawniczy: `kiedy-dojade-release.keystore` (poza repo; kopia na
  maszynie deweloperskiej w `~/kiedy-dojade-release.keystore`) + `keystore.properties`
  (gitignored). **Ten sam klucz na zawsze** — inny klucz łamie aktualizacje OTA.
- W CI klucz jest w sekretach repo: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
  `KEY_ALIAS`, `KEY_PASSWORD`.
- Wydanie: podbij `versionCode` (+1) i `versionName` w `app/build.gradle.kts`,
  wypchnij na `main`. Workflow `.github/workflows/android-release.yml` zbuduje
  i podpisze APK, utworzy GitHub Release `v<versionName>` z assetem
  `app-release.apk` i zaktualizuje `backend/api/version.json`.
- Aplikacja sprawdza `version.json` (raw.githubusercontent) i pokazuje kartę
  aktualizacji w zakładce **Zmiany**.

## Uwagi techniczne

- **Strefa czasu:** wszystkie obliczenia „teraz” i dat używają `Europe/Warsaw`,
  niezależnie od strefy telefonu (jak w wersji web).
- **WAF API miasta:** żądania bez nagłówka `User-Agent` przeglądarki dostają 404 —
  `Net.UA` w `DataRepo.kt`.
- **Kalendarz:** harmonogram dni (robocze / wakacje / sobota / niedziele) pochodzi
  z `calendar.json`; poza horyzontem danych planer schodzi na wzorzec wg dnia
  tygodnia i UI to zgłasza („rozkład wg dnia tygodnia”).
- **Mapa:** osmdroid + kafelki OpenStreetMap (własny User-Agent = nazwa pakietu,
  zgodnie z polityką OSM). Cache kafelków w `filesDir` — bez uprawnień do pamięci.
  Mapa potrzebuje internetu; rozkłady i planer działają dalej offline.
