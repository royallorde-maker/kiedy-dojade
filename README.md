# KiedyDojadę — Szczecinek

Planer podróży autobusem miejskim (**Komunikacja Miejska Szczecinek**).
Mówisz, **dokąd chcesz jechać** — aplikacja znajduje **najbliższy przystanek**
Twojej lokalizacji i najlepsze połączenie (z przesiadkami), podobnie jak
wyszukiwarki połączeń kolejowych.

**Aplikacja:** https://royallorde-maker.github.io/kiedy-dojade/

> Aplikacja nieoficjalna. Dane rozkładowe pochodzą z publicznego API systemu
> [KiedyPrzyjedzie.pl](https://szczecinek.kiedyprzyjedzie.pl) (KM Szczecinek),
> a lokalizacje ulic i punktów — z OpenStreetMap.

## Funkcje

- **Cel podróży zamiast rozkładu** — wpisujesz ulicę, miejsce lub przystanek;
  GPS podpowiada najbliższy przystanek wyjściowy (promień ~900 m).
- **Planowanie połączeń** — najszybszy przyjazd z maks. 2 przesiadkami,
  z dojściem pieszo (prędkość ~4,7 km/h, współczynnik objazdu 1,25).
- **Opcja „tylko pieszo”**, gdy spacer jest szybszy od autobusu.
- **Na żywo** — opóźnienia, odwołania, niskopodłogowość i zapełnienie
  (publiczne API departures/trip_execution), odświeżane co 20 s.
- **Rozkłady z wyprzedzeniem** — kalendarz dni (robocze / robocze bez szkoły /
  sobota / niedziele i święta) na ~2,5 miesiąca wprzód.
- **PWA** — działa offline (cache rozkładów), instaluje się na ekranie głównym.
- PL / EN.

## Jak to działa

```
scripts/harvest.mjs   →  public/data/*.json   ← odświeżane codziennie (GitHub Actions)
        (API KM Szczecinek)     │
                                ▼
src/planner.ts  ── skanowanie połączeń (Bellman-Ford po grafie czasu) + dojścia pieszo
src/realtime.ts ── odjazdy i pozycje pojazdów „na żywo” (bezpośrednio z przeglądarki)
```

Dane rozkładowe są pobierane raz (wszystkie przystanki × wzorce dni), zapisywane
jako zwarte pliki JSON i serwowane statycznie — aplikacja nie potrzebuje
własnego backendu.

## Rozwój

```bash
npm install
npm run dev        # http://localhost:5173
npm run harvest    # odśwież dane rozkładowe (public/data/)
npm run build      # produkcyjny build (dist/)
```

Zmienna `BASE_PATH` ustawia ścieżkę bazową (np. `/kiedy-dojade/` na GitHub Pages).

## Wdrożenie

`.github/workflows/refresh-and-deploy.yml`:

1. codziennie o 02:17 UTC (oraz przy pushu na `main` i ręcznie) odświeża dane,
2. commituje zmiany w `public/data/`,
3. buduje aplikację i publikuje `dist/` na GitHub Pages.

## Licencja / źródła

- Rozkłady i dane „na żywo”: system KiedyPrzyjedzie.pl (KM Szczecinek) — dane publiczne.
- Ulice i punkty: © OpenStreetMap contributors.
- Kod: MIT.
