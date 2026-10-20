# KiedyDojadę — Szczecinek

Planer podróży autobusem miejskim (**Komunikacja Miejska Szczecinek**) — natywna
aplikacja Android (Kotlin + Jetpack Compose) plus wersja web (PWA).

Mówisz, **dokąd chcesz jechać** — aplikacja znajduje **najbliższy przystanek**
Twojej lokalizacji i najlepsze połączenie (z przesiadkami), podobnie jak
wyszukiwarki połączeń kolejowych.

- **Android (główna aplikacja):** [pobierz APK](https://github.com/royallorde-maker/kiedy-dojade/releases/latest/download/app-release.apk)
  (aktualizacja w aplikacji — zakładka *Zmiany*). Kod: [`android/`](android/).
- **Web (PWA):** https://royallorde-maker.github.io/kiedy-dojade/

> Aplikacja nieoficjalna. Dane rozkładowe pochodzą z publicznego API systemu
> [KiedyPrzyjedzie.pl](https://szczecinek.kiedyprzyjedzie.pl) (KM Szczecinek),
> a lokalizacje ulic i punktów — z OpenStreetMap.

## Funkcje

- **Cel podróży zamiast rozkładu** — wpisujesz ulicę, miejsce lub przystanek;
  GPS podpowiada najbliższy przystanek wyjściowy (promień ~900 m).
- **Planowanie połączeń** — najszybszy przyjazd z maks. 2 przesiadkami,
  z dojściem pieszo (prędkość ~4,7 km/h, współczynnik objazdu 1,25).
- **Opcja „tylko pieszo”**, gdy spacer jest szybszy od autobusu.
- **Mapa przystanków i tras** (OpenStreetMap, bez klucza API) — filtr linii,
  pozycja GPS, karta przystanku pod ręką.
- **Trasa połączenia na mapie** — marsz, przesiadki i linie w kolorach,
  rysowane wprost w liście wyników planera.
- **Na żywo** — opóźnienia, odwołania, odjazdy z przystanku (publiczne API
  KiedyPrzyjedzie.pl), odświeżane co 30 s.
- **Prowadzenie w tle** — po wybraniu połączenia GPS działa dalej po wyjściu
  z aplikacji. Stałe powiadomienie pokazuje linię, następny przystanek
  i wysiadkę, aplikacja sama wykrywa wsiadanie, a po powrocie wraca do
  prowadzenia. „Ukryj” zostawia śledzenie, „Zakończ” je zatrzymuje.
- **Rozkłady offline** — dane wbudowane w aplikację i odświeżane z GitHuba;
  bez internetu planer i rozkłady nadal działają.
- **Motywy i języki** — 8 motywów, PL / EN, godziny zawsze w strefie Szczecinka.
- **Aktualizacje OTA** — nowa wersja pojawia się w aplikacji i instaluje się
  jednym dotknięciem (GitHub Releases + `backend/api/version.json`).

## Jak to działa

```
scripts/harvest.mjs   →  public/data/*.json   ← odświeżane codziennie (GitHub Actions)
        (API KM Szczecinek)     │
                                ├─▶ web PWA (GitHub Pages)
                                ├─▶ assets aplikacji Android (offline na start)
                                └─▶ pobierane przez aplikację (atomowa podmiana kopii)
```

- `src/planner.ts` (web) i `android/.../core/Planner.kt` (Kotlin) to ten sam
  algorytm: skanowanie połączeń (Bellman–Ford po grafie czasu) + dojścia pieszo.
- Dane rozkładowe są pobierane raz (wszystkie przystanki × wzorce dni), zapisywane
  jako zwarte pliki JSON i serwowane statycznie — bez własnego backendu.
- Aplikacja Android czyta te same pliki (`meta.json` porównuje wersję, resztę
  dociąga tylko gdy się zmieniły).

## Rozwój

Web:

```bash
npm install
npm run dev        # http://localhost:5173
npm run harvest    # odśwież dane rozkładowe (public/data/)
npm run build      # produkcyjny build (dist/)
```

Android:

```bash
cd android
gradle :app:testDebugUnitTest    # testy planera na prawdziwych danych
gradle :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Szczegóły (podpisywanie, wydania, architektura): [`android/README.md`](android/README.md).

## Wdrożenie

- **Web:** `.github/workflows/refresh-and-deploy.yml` — codziennie o 02:17 UTC
  (oraz przy pushu na `main` i ręcznie) odświeża dane, commituje `public/data/`
  i publikuje `dist/` na GitHub Pages.
- **Android:** `.github/workflows/android-release.yml` — przy zmianach w `android/`
  buduje podpisany APK (klucz z sekretów repo), tworzy GitHub Release
  `v<versionName>` z assetem `app-release.apk` i aktualizuje
  `backend/api/version.json` (manifest aktualizacji OTA).

## Licencja / źródła

- Rozkłady i dane „na żywo”: system KiedyPrzyjedzie.pl (KM Szczecinek) — dane publiczne.
- Ulice i punkty: © OpenStreetMap contributors.
- Kod: MIT.
