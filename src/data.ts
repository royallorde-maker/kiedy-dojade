/** Loading of the static schedule data (harvested into public/data/). */

import type { CalendarData, Meta, PlacesData, Stop, Trip, World } from './types';

const BASE = import.meta.env.BASE_URL;

async function getJson<T>(file: string): Promise<T> {
  const res = await fetch(`${BASE}data/${file}`, { cache: 'default' });
  if (!res.ok) throw new Error(`Failed to load ${file}: HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

let worldPromise: Promise<World> | null = null;

export function loadWorld(): Promise<World> {
  if (!worldPromise) worldPromise = boot();
  return worldPromise;
}

const tripsCache = new Map<string, Promise<Trip[]>>();

type RawTrip = [number, string, string, number, [number, number][]];

async function boot(): Promise<World> {
  const [stopsRaw, meta, calendar, places] = await Promise.all([
    getJson<{ revision: number; stops: [number, string, number, number, number, string][] }>('stops.json'),
    getJson<Meta>('meta.json'),
    getJson<CalendarData>('calendar.json'),
    getJson<PlacesData>('places.json').catch(
      () => ({ generatedAt: '', source: '', streets: [], pois: [] }) as PlacesData,
    ),
  ]);

  const stops: Stop[] = stopsRaw.stops.map((s, i) => {
    const flags = s[4] ?? 0;
    return {
      i,
      id: s[5],
      code: s[0],
      name: s[1],
      lat: s[2],
      lng: s[3],
      onDemand: !!(flags & 1),
      station: !!(flags & 2),
      onlyDisembarking: !!(flags & 4),
    };
  });

  return {
    stops,
    meta,
    calendar,
    places,
    loadTrips: (patternId: string) => {
      let p = tripsCache.get(patternId);
      if (!p) {
        p = getJson<{ pattern: string; trips: RawTrip[] }>(`trips-${patternId}.json`).then((d) =>
          d.trips.map((t) => ({
            id: t[0],
            line: t[1],
            dir: t[2],
            vt: t[3],
            times: t[4] ?? [],
          })),
        );
        tripsCache.set(patternId, p);
      }
      return p;
    },
  };
}

/** Which schedule pattern runs on a given date? Falls back by weekday outside the horizon. */
export function resolvePattern(
  world: World,
  date: string,
): { pattern: string; exact: boolean; type: string } {
  const pid = world.calendar.days[date];
  if (pid && world.meta.patterns[pid]) {
    return { pattern: pid, exact: true, type: world.meta.patterns[pid].type };
  }
  // outside the harvested calendar: guess by weekday
  const d = new Date(date + 'T12:00:00Z');
  const dow = d.getUTCDay(); // 0=Sun
  const wanted = dow === 0 ? ['sun', 'special'] : dow === 6 ? ['sat', 'special'] : ['workday', 'special'];
  for (const w of wanted) {
    const found = Object.entries(world.meta.patterns).find(([, p]) => p.type === w);
    if (found) return { pattern: found[0], exact: false, type: found[1].type };
  }
  const first = Object.keys(world.meta.patterns)[0];
  return { pattern: first, exact: false, type: world.meta.patterns[first]?.type ?? '' };
}

export function todayStr(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Warsaw',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}
