/** Minimal PL/EN i18n. */

export type Lang = 'pl' | 'en';

const STRINGS = {
  pl: {
    appName: 'KiedyDojadę',
    tagline: 'Szczecinek · autobusy miejskie',
    to: 'Dokąd jedziesz?',
    from: 'Skąd',
    dest: 'Dokąd',
    myLocation: 'Moja lokalizacja',
    myLocationShort: 'Tu jestem',
    useGps: 'Użyj mojej lokalizacji',
    searchPlaceholder: 'Ulica, miejsce lub przystanek…',
    find: 'Znajdź połączenie',
    leave: 'Wyjazd',
    now: 'Teraz',
    departAt: 'Wyjazd o',
    date: 'Data',
    today: 'Dziś',
    tomorrow: 'Jutro',
    results: 'Połączenia',
    noResults: 'Nie znaleziono połączenia w pobliżu.',
    noResultsHint: 'Spróbuj wybrać inne miejsce, zwiększyć promień dojścia lub wybrać późniejszą godzinę.',
    walkTo: 'Dojście',
    walk: 'pieszo',
    board: 'Wsiądź',
    alight: 'Wysiądź',
    direction: 'kierunek',
    transfer: 'przesiadka',
    transfers: 'przesiadki',
    transfersMany: 'przesiadek',
    noTransfers: 'bez przesiadek',
    fewerTransfers: 'mniej przesiadek',
    stopsOne: 'przystanek',
    stopsMany: 'przystanków',
    intermediateOne: 'przystanek pośredni',
    intermediateMany: 'przystanków pośrednich',
    busesOne: '1 autobus',
    busesMany: 'autobusy',
    min: 'min',
    walkOnly: 'Tylko pieszo',
    live: 'na żywo',
    fired: 'odjazd',
    delayed: 'opóźniony',
    canceled: 'kurs odwołany',
    canceledShort: 'odwołany',
    scheduled: 'rozkładowy',
    realtime: 'czas rzeczywisty',
    platform: 'peron',
    stop: 'Przystanek',
    stops: 'przystanki',
    street: 'ulica',
    departures: 'Odjazdy',
    nextDepartures: 'Najbliższe odjazdy',
    fullTimetable: 'Cały rozkład dnia',
    noDepartures: 'Brak odjazdów w najbliższym czasie.',
    showStops: 'pokaż przystanki',
    hideStops: 'ukryj przystanki',
    intermediate: 'przystanki pośrednie',
    walkMeters: 'm pieszo',
    leaveIn: 'wyjdź za',
    leaveNow: 'wyjdź teraz!',
    travelTime: 'czas jazdy',
    arrives: 'przyjazd',
    departs: 'odjazd',
    dataFrom: 'Dane rozkładowe z systemu KiedyPrzyjedzie.pl (Komunikacja Miejska Szczecinek), uzupełnione o OpenStreetMap. Aplikacja nieoficjalna.',
    dataUpdated: 'Dane zaktualizowano',
    scheduleDate: 'Rozkład dla',
    scheduleGuess: 'Uwaga: rozkład poza zasięgiem danych — użyto rozkładu „{type}”.',
    dayTypes: {
      workday: 'dni powszednie',
      workday_noschool: 'dni powszednie (wolne od nauki)',
      sat: 'sobota',
      sun: 'niedziele i święta',
      special: 'dzień specjalny',
    } as Record<string, string>,
    settings: 'Ustawienia',
    language: 'Język',
    install: 'Dodaj do ekranu głównego',
    installHint: 'W przeglądarce wybierz „Dodaj do ekranu głównego”, aby korzystać z aplikacji jak z natywnej.',
    alerts: 'Komunikaty',
    source: 'Źródło danych',
    recent: 'Ostatnie',
    favorites: 'Zapisane',
    save: 'Zapisz',
    saved: 'Zapisano',
    remove: 'Usuń',
    swap: 'Zamień',
    close: 'Zamknij',
    back: 'Wstecz',
    stopsNearYou: 'Przystanki w pobliżu',
    walkingDist: 'dojście',
    arriveBy: 'przyjazd',
    allLines: 'wszystkie linie',
    nowAt: 'teraz',
    tapToExpand: 'dotknij, aby rozwinąć',
    onDemand: 'Na żądanie — zatrzymaj autobus',
    station: 'Dworzec',
    loading: 'Ładowanie…',
    errorLoad: 'Nie udało się wczytać danych. Sprawdź połączenie z internetem.',
    gpsDenied: 'Brak dostępu do lokalizacji. Wybierz punkt startowy ręcznie.',
    gpsUnavailable: 'Lokalizacja niedostępna.',
    choose: 'Wybierz',
    noTrips: 'Brak połączeń o tej porze.',
    tripCanceled: 'Ten kurs został odwołany — wybierz inne połączenie.',
    liveLookup: 'Sprawdzam na żywo…',
    vehicleHere: 'Autobus jest już na trasie',
    vehicleIn: 'Autobus {n} przyst. od Ciebie',
    walking: 'Idziesz',
    suggested: 'Proponowane',
    departsIn: 'odjazd za',
    showMap: 'Pokaż na mapie',
    alsoWalk: 'Możesz też dojść pieszo',
    tripsCount: 'kursów',
  },
  en: {
    appName: 'KiedyDojadę',
    tagline: 'Szczecinek · city buses',
    to: 'Where do you want to go?',
    from: 'From',
    dest: 'To',
    myLocation: 'My location',
    myLocationShort: 'I am here',
    useGps: 'Use my location',
    searchPlaceholder: 'Street, place or stop…',
    find: 'Find connection',
    leave: 'Depart',
    now: 'Now',
    departAt: 'Depart at',
    date: 'Date',
    today: 'Today',
    tomorrow: 'Tomorrow',
    results: 'Connections',
    noResults: 'No connection found nearby.',
    noResultsHint: 'Try another destination, a larger walking radius or a later time.',
    walkTo: 'Walk',
    walk: 'walk',
    board: 'Board',
    alight: 'Get off',
    direction: 'to',
    transfer: 'transfer',
    transfers: 'transfers',
    transfersMany: 'transfers',
    noTransfers: 'no transfers',
    fewerTransfers: 'fewer transfers',
    stopsOne: 'stop',
    stopsMany: 'stops',
    intermediateOne: 'intermediate stop',
    intermediateMany: 'intermediate stops',
    busesOne: '1 bus',
    busesMany: 'buses',
    min: 'min',
    walkOnly: 'Walk only',
    live: 'live',
    fired: 'departed',
    delayed: 'delayed',
    canceled: 'trip canceled',
    canceledShort: 'canceled',
    scheduled: 'scheduled',
    realtime: 'realtime',
    platform: 'platform',
    stop: 'Stop',
    stops: 'stops',
    street: 'street',
    departures: 'Departures',
    nextDepartures: 'Next departures',
    fullTimetable: 'Full day timetable',
    noDepartures: 'No departures soon.',
    showStops: 'show stops',
    hideStops: 'hide stops',
    intermediate: 'intermediate stops',
    walkMeters: 'm walk',
    leaveIn: 'leave in',
    leaveNow: 'leave now!',
    travelTime: 'travel time',
    arrives: 'arrive',
    departs: 'depart',
    dataFrom: 'Schedule data from KiedyPrzyjedzie.pl (Komunikacja Miejska Szczecinek), enriched with OpenStreetMap. Unofficial app.',
    dataUpdated: 'Data updated',
    scheduleDate: 'Schedule for',
    scheduleGuess: 'Note: date outside harvested data — using the “{type}” schedule.',
    dayTypes: {
      workday: 'weekdays',
      workday_noschool: 'weekdays (school holidays)',
      sat: 'Saturday',
      sun: 'Sundays & holidays',
      special: 'special day',
    } as Record<string, string>,
    settings: 'Settings',
    language: 'Language',
    install: 'Add to home screen',
    installHint: 'Use “Add to home screen” in your browser to use this as a native app.',
    alerts: 'Service alerts',
    source: 'Data source',
    recent: 'Recent',
    favorites: 'Saved',
    save: 'Save',
    saved: 'Saved',
    remove: 'Remove',
    swap: 'Swap',
    close: 'Close',
    back: 'Back',
    stopsNearYou: 'Stops near you',
    walkingDist: 'walk',
    arriveBy: 'arrive',
    allLines: 'all lines',
    nowAt: 'now',
    tapToExpand: 'tap to expand',
    onDemand: 'Request stop — hail the bus',
    station: 'Station',
    loading: 'Loading…',
    errorLoad: 'Could not load data. Check your internet connection.',
    gpsDenied: 'Location permission denied. Pick a starting point manually.',
    gpsUnavailable: 'Location unavailable.',
    choose: 'Choose',
    noTrips: 'No connections at this time.',
    tripCanceled: 'This trip was canceled — pick another connection.',
    liveLookup: 'Checking live…',
    vehicleHere: 'The bus is already on its way',
    vehicleIn: 'Bus is {n} stops away',
    walking: 'Walking',
    suggested: 'Suggested',
    departsIn: 'departs in',
    showMap: 'Show on map',
    alsoWalk: 'You can also walk',
    tripsCount: 'trips',
  },
} as const;

export type StringKey = keyof (typeof STRINGS)['pl'];

let lang: Lang = (localStorage.getItem('lang') as Lang) || 'pl';

export function getLang(): Lang {
  return lang;
}
export function setLang(l: Lang) {
  lang = l;
  localStorage.setItem('lang', l);
  document.documentElement.lang = l;
}

export function t(key: StringKey, vars?: Record<string, string | number>): string {
  const raw = STRINGS[lang][key];
  let s: string = typeof raw === 'string' ? raw : String(key);
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, String(v));
  return s;
}

/** "1 przesiadka", "2 przesiadki", "5 przesiadek" */
export function transfersLabel(n: number): string {
  if (lang === 'en') return `${n} ${n === 1 ? t('transfer') : t('transfers')}`;
  if (n === 1) return `1 ${t('transfer')}`;
  if (n >= 2 && n <= 4) return `${n} ${t('transfers')}`;
  return `${n} ${t('transfersMany')}`;
}

/** "1 przystanek", "3 przystanki", "6 przystanków" */
export function stopsLabel(n: number): string {
  if (lang === 'en') return `${n} ${n === 1 ? t('stopsOne') : t('stopsMany')}`;
  if (n === 1) return `1 ${t('stopsOne')}`;
  if (n >= 2 && n <= 4) return `${n} ${t('stops')}`;
  return `${n} ${t('stopsMany')}`;
}

/** "1 przystanek pośredni", "3 przystanki pośrednie", "6 przystanków pośrednich" */
export function intermediateLabel(n: number): string {
  if (lang === 'en') return `${n} ${n === 1 ? t('intermediateOne') : t('intermediateMany')}`;
  if (n === 1) return `1 ${t('intermediateOne')}`;
  if (n >= 2 && n <= 4) return `${n} ${t('intermediate')}`;
  return `${n} ${t('intermediateMany')}`;
}

export function dayTypeLabel(type: string): string {
  return (STRINGS[lang].dayTypes as Record<string, string>)[type] ?? type;
}
