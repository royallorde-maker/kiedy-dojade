/**
 * Journey planner.
 *
 * Every ride between two consecutive stops of a trip is a "connection" with a
 * departure and arrival time. A query relaxes connections in time order until
 * arrivals stop improving (Bellman-Ford over the time-expanded graph). That
 * gives the earliest-arrival journeys with walking to/from stops and a bounded
 * number of boardings. Running the search again slightly later yields the next
 * departures.
 */

import { WALK_DETOUR, haversine, walkMinutes } from './geo';
import type { Itinerary, Leg, PlaceRef, RideLeg, Stop, Trip, WalkLeg, World } from './types';

export interface PlanInput {
  origin: PlaceRef;
  dest: PlaceRef;
  /** YYYY-MM-DD, defaults to today (Europe/Warsaw) */
  date?: string;
  /** earliest departure, minutes since midnight */
  departMin: number;
  maxTransfers?: number;
  /** crow-flies meters one is willing to walk to reach a stop */
  walkRadiusM?: number;
  maxResults?: number;
}

export interface PlanOutcome {
  itineraries: Itinerary[];
  pattern: string;
  exactDay: boolean;
  /** truthy when walking straight is a sensible option */
  walkOnly: { minutes: number; meters: number; leaveAt: number; arriveAt: number } | null;
  /** true when no journey was found within the walk radius */
  empty: boolean;
}

interface Connection {
  from: number;
  to: number;
  dep: number;
  arr: number;
  tripIdx: number;
  seq: number;
}

interface Label {
  arr: number;
  boardings: number;
  stopIdx: number;
  prev: Label | null;
  /** ride used to arrive, if any */
  ride: Connection | null;
  /** walking meters accumulated up to (and including) arrival here */
  walkMeters: number;
  /** the walking that led into the network (set on access labels) */
  access: { meters: number; minutes: number } | null;
}

const MAX_RELAX_PASSES = 10;
/** walking shorter than this is "you are at the stop" — no walk leg, no time cost */
const TRIVIAL_WALK_M = 25;

// ---------------------------------------------------------------- cache

interface Built {
  trips: Trip[];
  conns: Connection[];
}

const builtCache = new Map<string, Built>();

function connectionsFor(pattern: string, trips: Trip[]): Built {
  let b = builtCache.get(pattern);
  if (b && b.trips === trips) return b;
  const conns: Connection[] = [];
  trips.forEach((t, tripIdx) => {
    for (let i = 0; i < t.times.length - 1; i++) {
      const [from, dep] = t.times[i];
      const [to, arr] = t.times[i + 1];
      conns.push({ from, to, dep, arr, tripIdx, seq: i });
    }
  });
  conns.sort((a, b2) => a.dep - b2.dep || a.arr - b2.arr);
  b = { trips, conns };
  builtCache.set(pattern, b);
  return b;
}

// ---------------------------------------------------------------- place resolution

export interface Resolved {
  name: string;
  lat: number;
  lng: number;
  /** polyline (for streets) */
  pts?: [number, number][];
}

export function resolvePlace(ref: PlaceRef, stops: Stop[]): Resolved {
  switch (ref.kind) {
    case 'gps':
    case 'place':
      return { name: ref.name, lat: ref.lat, lng: ref.lng };
    case 'stop': {
      const s = stops[ref.stop];
      return { name: s.name, lat: s.lat, lng: s.lng };
    }
    case 'street':
      return { name: ref.name, lat: ref.pts[0][0], lng: ref.pts[0][1], pts: ref.pts };
  }
}

/** Closest point of a place (point or street) to a given coordinate. */
export function closestPointOf(
  place: Resolved,
  lat: number,
  lng: number,
): { meters: number; point: [number, number] } {
  if (!place.pts || place.pts.length === 0) {
    return { meters: haversine(lat, lng, place.lat, place.lng), point: [place.lat, place.lng] };
  }
  let best: { meters: number; point: [number, number] } | null = null;
  for (const p of place.pts) {
    const m = haversine(lat, lng, p[0], p[1]);
    if (!best || m < best.meters) best = { meters: m, point: p };
  }
  return best!;
}

// ---------------------------------------------------------------- search

export function todayStr(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Warsaw',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function resolvePatternFor(world: World, date: string): { pattern: string; exact: boolean } {
  const pid = world.calendar.days[date];
  if (pid && world.meta.patterns[pid]) return { pattern: pid, exact: true };
  const d = new Date(date + 'T12:00:00Z');
  const dow = d.getUTCDay();
  const wanted =
    dow === 0 ? ['sun', 'special'] : dow === 6 ? ['sat', 'special'] : ['workday', 'special'];
  for (const w of wanted) {
    const found = Object.entries(world.meta.patterns).find(([, p]) => p.type === w);
    if (found) return { pattern: found[0], exact: false };
  }
  const first = Object.keys(world.meta.patterns)[0];
  return { pattern: first, exact: false };
}

interface StopCandidate {
  stop: Stop;
  meters: number;
  minutes: number;
}

export function planJourney(world: World, input: PlanInput): Promise<PlanOutcome> {
  const maxTransfers = input.maxTransfers ?? 2;
  const maxResults = input.maxResults ?? 4;
  const walkRadiusM = input.walkRadiusM ?? 900;
  const origin = resolvePlace(input.origin, world.stops);
  const dest = resolvePlace(input.dest, world.stops);
  const date = input.date ?? todayStr();
  const { pattern, exact } = resolvePatternFor(world, date);

  return world.loadTrips(pattern).then((trips) => {
    const built = connectionsFor(pattern, trips);
    const stops = world.stops;

    // walking-only option (crow-flies distance is enough for the estimate;
    // walkMinutes and walkMeters each apply the detour factor exactly once)
    const straight = haversine(origin.lat, origin.lng, dest.lat, dest.lng);
    const walkOnly =
      straight <= 3000
        ? {
            minutes: walkMinutes(straight),
            meters: straight * WALK_DETOUR,
            leaveAt: Math.round(input.departMin),
            arriveAt: Math.round(input.departMin) + walkMinutes(straight),
          }
        : null;

    // candidate stops around origin / destination
    // (an "arrival right at the stop" costs 0 minutes — no silly 1-min walk leg)
    const access: StopCandidate[] = [];
    for (const s of stops) {
      if (s.onlyDisembarking) continue;
      const m = closestPointOf(origin, s.lat, s.lng).meters;
      if (m <= walkRadiusM)
        access.push({ stop: s, meters: m, minutes: m < TRIVIAL_WALK_M ? 0 : walkMinutes(m) });
    }
    const egress = new Map<number, { meters: number; minutes: number; point: [number, number] }>();
    for (const s of stops) {
      const c = closestPointOf(dest, s.lat, s.lng);
      if (c.meters <= walkRadiusM)
        egress.set(s.i, {
          meters: c.meters,
          minutes: c.meters < TRIVIAL_WALK_M ? 0 : walkMinutes(c.meters),
          point: c.point,
        });
    }

    const itineraries: Itinerary[] = [];
    if (access.length > 0 && egress.size > 0) {
      const signatures = new Set<string>();
      let t = Math.round(input.departMin);
      for (let round = 0; round < 6 && itineraries.length < maxResults; round++) {
        const { labels } = relax(present(access, t, stops.length), built, maxTransfers + 1);

        const options = evaluate(world, built, labels, egress, origin, dest, pattern);
        if (options.length === 0) break;

        // primary = best arrival; optional secondary = fewer transfers within a small sacrifice
        const primary = options[0];
        const secondary = options.find(
          (o) => o.transfers < primary.transfers && o.arriveAt <= primary.arriveAt + 6,
        );

        let added = false;
        for (const o of [primary, secondary]) {
          if (!o) continue;
          const sig = signature(o);
          if (signatures.has(sig)) continue;
          signatures.add(sig);
          o.tag = itineraries.length === 0 ? 'best' : 'next';
          if (itineraries.length > 0 && o.transfers < itineraries[0].transfers) o.tag = 'fewer-transfers';
          itineraries.push(o);
          added = true;
          if (itineraries.length >= maxResults) break;
        }

        // next round: after this journey's first vehicle
        const firstRide = primary.legs.find((l) => l.type === 'ride') as RideLeg | undefined;
        if (!firstRide) break;
        const nextT = firstRide.depMin + 1;
        if (nextT <= t) break;
        t = nextT;
        if (!added && options.length === 1) {
          // nothing new — still advance and keep going
          continue;
        }
      }
    }

    // when walking straight beats every bus option, no itinerary is "best"
    if (walkOnly && itineraries[0] && walkOnly.arriveAt <= itineraries[0].arriveAt) {
      if (itineraries[0].tag === 'best') itineraries[0].tag = 'next';
    }

    return {
      itineraries,
      pattern,
      exactDay: exact,
      walkOnly,
      empty: itineraries.length === 0,
    };
  });
}

/** build access labels for a departure time */
function present(
  access: StopCandidate[],
  departMin: number,
  stopCount: number,
): Label[] {
  const n = stopCount;
  const labels: Label[] = Array.from({ length: n }, (_, stopIdx) => ({
    arr: Infinity,
    boardings: 0,
    stopIdx,
    prev: null,
    ride: null,
    walkMeters: 0,
    access: null,
  }));
  for (const a of access) {
    const arr = departMin + a.minutes;
    if (arr < labels[a.stop.i].arr) {
      labels[a.stop.i] = {
        arr,
        boardings: 0,
        stopIdx: a.stop.i,
        prev: null,
        ride: null,
        walkMeters: a.meters,
        access: { meters: a.meters, minutes: a.minutes },
      };
    }
  }
  return labels;
}

function relax(
  labels: Label[],
  built: Built,
  maxBoardings: number,
): { labels: Label[]; conns: Connection[] } {
  const conns = built.conns;
  for (let pass = 0; pass < MAX_RELAX_PASSES; pass++) {
    let improved = false;
    for (const c of conns) {
      const L = labels[c.from];
      if (L.arr === Infinity) continue;
      const sameTrip = L.ride !== null && L.ride.tripIdx === c.tripIdx && L.ride.seq === c.seq - 1;
      if (c.dep < L.arr) continue;
      const boardings = sameTrip ? L.boardings : L.boardings + 1;
      if (boardings > maxBoardings) continue;
      const T = labels[c.to];
      if (c.arr < T.arr || (c.arr === T.arr && boardings < T.boardings)) {
        labels[c.to] = {
          arr: c.arr,
          boardings,
          stopIdx: c.to,
          prev: L,
          ride: c,
          walkMeters: L.walkMeters,
          access: null,
        };
        improved = true;
      }
    }
    if (!improved) break;
  }
  return { labels, conns };
}

/** Evaluate arrival candidates and turn the best few into itineraries. */
function evaluate(
  world: World,
  built: Built,
  labels: Label[],
  egress: Map<number, { meters: number; minutes: number; point: [number, number] }>,
  origin: Resolved,
  dest: Resolved,
  pattern: string,
): Itinerary[] {
  // one candidate per (final boarding stop) with total arrival time
  const cands: { stop: number; arriveAt: number; transfers: number }[] = [];
  for (const [stopIdx, e] of egress) {
    const L = labels[stopIdx];
    if (!L || L.arr === Infinity) continue;
    cands.push({ stop: stopIdx, arriveAt: L.arr + e.minutes, transfers: Math.max(0, L.boardings - 1) });
  }
  cands.sort((a, b) => a.arriveAt - b.arriveAt || a.transfers - b.transfers);

  const out: Itinerary[] = [];
  const seenFirst = new Set<string>();
  for (const cand of cands.slice(0, 60)) {
    const it = buildItinerary(world, built, labels, egress, origin, dest, pattern, cand.stop);
    if (!it) continue;
    const first = it.legs.find((l) => l.type === 'ride') as RideLeg | undefined;
    const key = first ? `${first.tripId}:${first.boardStop.i}:${it.leaveAt}` : `w:${it.arriveAt}`;
    if (seenFirst.has(key)) continue;
    seenFirst.add(key);
    out.push(it);
    if (out.length >= 8) break; // keep a handful for the caller's choice
  }
  return out;
}

function buildItinerary(
  world: World,
  built: Built,
  labels: Label[],
  egress: Map<number, { meters: number; minutes: number; point: [number, number] }>,
  origin: Resolved,
  dest: Resolved,
  pattern: string,
  finalStop: number,
): Itinerary | null {
  const stops = world.stops;
  const e = egress.get(finalStop);
  if (!e) return null;

  // walk back through labels, oldest first
  const chain: Label[] = [];
  let node: Label | null = labels[finalStop];
  let guard = 0;
  while (node && guard++ < 200) {
    chain.push(node);
    node = node.prev;
  }
  chain.reverse();
  const accessLabel = chain[0];
  if (!accessLabel?.access) return null;

  // merge consecutive hops of the same trip into one ride
  const runs: Label[][] = [];
  for (const l of chain) {
    if (!l.ride) continue; // the access label
    const run = runs.at(-1);
    const prevRide = run?.at(-1)?.ride;
    if (run && prevRide && prevRide.tripIdx === l.ride.tripIdx && l.ride.from === prevRide.to) {
      run.push(l);
    } else {
      runs.push([l]);
    }
  }
  if (runs.length === 0) return null;

  const legs: Leg[] = [];
  const firstConn = runs[0][0].ride!;
  const boardStop = stops[firstConn.from];
  if (accessLabel.stopIdx !== firstConn.from) return null;

  const accessMinutes = accessLabel.access.minutes;
  const firstRideDep = firstConn.dep;
  const leaveAt = firstRideDep - accessMinutes;

  if (accessLabel.access.meters >= TRIVIAL_WALK_M) {
    legs.push({
      type: 'walk',
      fromName: origin.name,
      toName: boardStop.name,
      meters: accessLabel.access.meters,
      minutes: accessMinutes,
      depMin: leaveAt,
      arrMin: firstRideDep,
      from: closestPointOf(origin, boardStop.lat, boardStop.lng).point,
      to: [boardStop.lat, boardStop.lng],
    } satisfies WalkLeg);
  }

  for (const run of runs) {
    const c0 = run[0].ride!;
    const cN = run.at(-1)!.ride!;
    const trip = built.trips[c0.tripIdx];
    const boardIdx = c0.seq;
    const alightIdx = cN.seq + 1;
    const stopList: { stop: Stop; min: number }[] = [];
    for (let k = boardIdx; k <= alightIdx; k++) {
      const [si, min] = trip.times[k];
      stopList.push({ stop: stops[si], min });
    }
    legs.push({
      type: 'ride',
      tripId: trip.id,
      line: trip.line,
      dir: trip.dir,
      boardStop: stops[c0.from],
      alightStop: stops[cN.to],
      boardIdx,
      alightIdx,
      depMin: c0.dep,
      arrMin: cN.arr,
      stops: stopList,
    } satisfies RideLeg);
  }

  const lastConn = runs.at(-1)!.at(-1)!.ride!;
  const lastStop = stops[lastConn.to];
  const arriveAt = lastConn.arr + e.minutes;
  if (e.meters >= TRIVIAL_WALK_M) {
    legs.push({
      type: 'walk',
      fromName: lastStop.name,
      toName: dest.name,
      meters: e.meters,
      minutes: e.minutes,
      depMin: lastConn.arr,
      arrMin: arriveAt,
      from: [lastStop.lat, lastStop.lng],
      to: e.point,
    } satisfies WalkLeg);
  }

  const walkMeters = legs.reduce((s, l) => (l.type === 'walk' ? s + l.meters : s), 0);
  const walkMinutes = legs.reduce((s, l) => (l.type === 'walk' ? s + l.minutes : s), 0);
  const boardings = legs.filter((l) => l.type === 'ride').length;
  const firstRideIdx = legs.findIndex((l) => l.type === 'ride');

  return {
    id: `${pattern}-${runs[0][0].ride!.tripIdx}-${runs[0][0].ride!.from}-${leaveAt}`,
    legs,
    leaveAt,
    arriveAt,
    transfers: Math.max(0, boardings - 1),
    walkMeters,
    walkMinutes,
    firstRideLeg: firstRideIdx,
    pattern,
    tag: 'best',
  };
}

function signature(it: Itinerary): string {
  return it.legs
    .map((l) => (l.type === 'ride' ? `${l.tripId}@${l.boardStop.i}` : `walk${l.minutes}`))
    .join('>');
}
