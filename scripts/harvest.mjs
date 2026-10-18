#!/usr/bin/env node
/**
 * KiedyDojadę — schedule harvester.
 *
 * Pulls the complete KM Szczecinek bus schedule from the public KiedyPrzyjedzie
 * passenger API (the same JSON endpoints the official web app uses) and writes
 * compact static data files into public/data/.
 *
 * Output files
 *   stops.json          all stops with coordinates (index = stop idx used in trips)
 *   calendar.json       date (YYYY-MM-DD) -> pattern id ("which schedule runs that day")
 *   trips-<pattern>.json  trips of one schedule pattern: line, direction, stop+time list
 *   meta.json           generation metadata, pattern labels, fingerprints
 *
 * Modes
 *   default   probe the calendar, then fetch everything that is missing/changed
 *   --full    ignore existing data, refetch all patterns' trips
 *
 * Idempotent & incremental: running it again on unchanged upstream data only
 * re-probes the calendar and checks fingerprints, which is cheap.
 */

import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';

const BASE = process.env.KP_BASE ?? 'https://szczecinek.kiedyprzyjedzie.pl';
const ROOT = path.resolve(import.meta.dirname, '..');
const DATA_DIR = path.join(ROOT, 'public', 'data');
const UA =
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

const FULL = process.argv.includes('--full');
const MAX_HORIZON_DAYS = 200; // how far ahead we are willing to look
const MIN_HORIZON_DAYS = 21; // extend the calendar at least this far if possible
const PROBE_STOPS = 4; // how many stops sampled to fingerprint a day
const REQ_DELAY_MS = 250; // politeness delay between request starts
const REQ_CONCURRENCY = 2;

// ---------------------------------------------------------------- http

let queue = Promise.resolve();
let active = 0;
const waiters = [];

function pump() {
  if (waiters.length && active < REQ_CONCURRENCY) {
    active++;
    const w = waiters.shift();
    w();
  }
}
function acquire() {
  return new Promise((resolve) => {
    waiters.push(resolve);
    pump();
  });
}
function release() {
  active--;
  setTimeout(pump, REQ_DELAY_MS);
}

let requestCount = 0;

async function getJson(pathname, { tries = 4, allow404 = false } = {}) {
  let lastErr;
  for (let attempt = 1; attempt <= tries; attempt++) {
    await acquire();
    try {
      requestCount++;
      const res = await fetch(BASE + pathname, {
        headers: {
          'user-agent': UA,
          accept: 'application/json, text/plain, */*',
          referer: BASE + '/',
          'accept-language': 'pl-PL,pl;q=0.9',
        },
        signal: AbortSignal.timeout(30000),
      });
      if (allow404 && res.status === 404) return null;
      if (!res.ok) throw new Error(`HTTP ${res.status} for ${pathname}`);
      const text = await res.text();
      if (!text.trim().startsWith('{') && !text.trim().startsWith('[')) {
        throw new Error(`Non-JSON response for ${pathname}`);
      }
      return JSON.parse(text);
    } catch (err) {
      lastErr = err;
      if (attempt < tries) {
        const backoff = 800 * attempt;
        log(`  ! ${err.message} — retry ${attempt}/${tries - 1} in ${backoff}ms`);
        await sleep(backoff);
      }
    } finally {
      release();
    }
  }
  throw lastErr;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function log(...args) {
  console.log(`[${new Date().toISOString().slice(11, 19)}]`, ...args);
}

// ---------------------------------------------------------------- dates

const TZ = 'Europe/Warsaw';
const dateFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const dowFmt = new Intl.DateTimeFormat('en-US', { timeZone: TZ, weekday: 'short' });

function todayStr() {
  return dateFmt.format(new Date());
}
function addDays(dateStr, days) {
  const d = new Date(dateStr + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
function dow(dateStr) {
  return dowFmt.format(new Date(dateStr + 'T12:00:00Z')); // Mon..Sun
}
const isWeekend = (dateStr) => ['Sat', 'Sun'].includes(dow(dateStr));

// ---------------------------------------------------------------- harvest

async function loadExisting() {
  const read = async (file) => {
    try {
      return JSON.parse(await fs.readFile(path.join(DATA_DIR, file), 'utf8'));
    } catch {
      return null;
    }
  };
  const meta = await read('meta.json');
  const calendar = (await read('calendar.json'))?.days ?? {};
  const tripCache = new Map(); // tripId -> compact trip
  const patternFingerprints = meta?.fingerprints ?? {};
  if (meta?.patterns) {
    for (const p of Object.keys(meta.patterns)) {
      const file = await read(`trips-${p}.json`);
      if (file?.trips) for (const t of file.trips) tripCache.set(t[0], t);
    }
  }
  return { meta, calendar, tripCache, patternFingerprints };
}

async function fetchStops() {
  log('Fetching stop list …');
  const data = await getJson('/stops');
  const stops = data.stops.map((s) => ({
    id: s[0],
    code: s[1],
    name: s[2],
    lat: s[4] / 1e6,
    lng: s[3] / 1e6,
    onDemand: /NŻ/i.test(s[2]), // "na żądanie" — request stop
    station: s[6] === 1,
    onlyDisembarking: s[5] === 1,
  }));
  log(`  ${stops.length} stops (revision ${data.revision}).`);
  // index in this array is what trips reference
  const byId = new Map(stops.map((s, i) => [s.id, i]));
  return { stops, byId, revision: data.revision };
}

/** Find the last date (from today) with published service. */
async function findHorizon(probeIds) {
  const today = todayStr();
  const hasService = async (dateStr) => {
    for (const id of probeIds.slice(0, 2)) {
      const t = await fetchTimetable(id, dateStr);
      if ((t?.departures ?? []).length > 0) return true;
    }
    return false;
  };

  // coarse: check every 14th day up to max horizon, find first empty
  let lastOk = addDays(today, MIN_HORIZON_DAYS);
  if (!(await hasService(lastOk))) return lastOk;
  let lo = lastOk;
  let hi = null;
  for (let d = MIN_HORIZON_DAYS; d <= MAX_HORIZON_DAYS; d += 14) {
    const date = addDays(today, d);
    if (await hasService(date)) lo = date;
    else {
      hi = date;
      break;
    }
  }
  if (!hi) return lo;
  // binary search between lo (ok) and hi (empty)
  while (addDays(lo, 1) < hi) {
    let loDays = daysBetween(today, lo);
    let hiDays = daysBetween(today, hi);
    const mid = addDays(today, Math.floor((loDays + hiDays) / 2));
    if (await hasService(mid)) lo = mid;
    else hi = mid;
  }
  return lo;
}

function daysBetween(a, b) {
  return Math.round(
    (new Date(b + 'T12:00:00Z') - new Date(a + 'T12:00:00Z')) / 86400000,
  );
}

/** pick probe stop ids spread across the network */
function pickProbeStops(stops) {
  const sorted = [...stops]
    .filter((s) => !s.onlyDisembarking)
    .sort((a, b) => a.name.localeCompare(b.name, 'pl'));
  const picks = [];
  const step = Math.max(1, Math.floor(sorted.length / PROBE_STOPS));
  for (let i = 0; i < sorted.length && picks.length < PROBE_STOPS; i += step) {
    picks.push(sorted[i].id);
  }
  return picks;
}

/** Stops where boarding is not allowed have no departure board (404). */
async function fetchTimetable(stopId, dateStr) {
  return getJson(
    `/api/timetables/${encodeURIComponent(stopId)}?date=${dateStr}`,
    { tries: 3, allow404: true },
  );
}

async function dateSignature(dateStr, probeIds) {
  const parts = [];
  for (const id of probeIds) {
    const t = await fetchTimetable(id, dateStr);
    const rows = (t?.departures ?? [])
      .map((d) => `${d.trip_id}:${d.departure}:${d.index}`)
      .sort();
    parts.push(rows.join('|'));
  }
  return parts.join('~');
}

const sha = (s) => crypto.createHash('sha1').update(s).digest('hex').slice(0, 16);

async function harvestCalendar(probeIds, prevCalendar) {
  const today = todayStr();
  log('Finding how far ahead schedules are published …');
  const horizon = await findHorizon(probeIds);
  log(`  Horizon: ${horizon} (${daysBetween(today, horizon)} days ahead).`);

  log('Probing calendar day by day …');
  const sigToPattern = new Map();
  const days = {}; // date -> pattern id
  const patterns = new Map(); // pattern id -> { dates:[], sig, dowCounts }
  let pn = 0;

  const total = daysBetween(today, horizon) + 1;
  let done = 0;
  for (let d = 0; d <= daysBetween(today, horizon); d++) {
    const date = addDays(today, d);
    const sig = await dateSignature(date, probeIds);
    let pid = sigToPattern.get(sig);
    if (!pid) {
      pid = `p${++pn}`;
      sigToPattern.set(sig, pid);
      patterns.set(pid, { dates: [], sig, dow: {} });
    }
    const p = patterns.get(pid);
    p.dates.push(date);
    p.dow[dow(date)] = (p.dow[dow(date)] ?? 0) + 1;
    days[date] = pid;
    if (++done % 10 === 0) log(`  … ${done}/${total} days`);
  }

  // label each pattern
  const worked = new Map(); // pid -> total departures on probe stops (for school heuristic)
  for (const [pid, p] of patterns) {
    const dowNames = Object.keys(p.dow);
    let type = 'special';
    if (dowNames.length === 1 && dowNames[0] === 'Sat') type = 'sat';
    else if (dowNames.length === 1 && dowNames[0] === 'Sun') type = 'sun';
    else if (dowNames.every((n) => !['Sat', 'Sun'].includes(n))) type = 'workday';
    else {
      // mixed pattern (Sundays + public holidays, …): classify by the dominant weekend day
      const n = p.dates.length;
      if ((p.dow['Sun'] ?? 0) >= n * 0.5) type = 'sun';
      else if ((p.dow['Sat'] ?? 0) >= n * 0.5) type = 'sat';
    }
    p.type = type;
  }
  // distinguish school / no-school workday variants by departure volume
  const workdays = [...patterns.entries()].filter(([, p]) => p.type === 'workday');
  if (workdays.length > 1) {
    const sizes = [];
    for (const [pid, p] of workdays) {
      const date = p.dates[0];
      let total = 0;
      for (const id of probeIds) {
        const t = await fetchTimetable(id, date);
        total += (t?.departures ?? []).length;
      }
      sizes.push([pid, total]);
    }
    sizes.sort((a, b) => b[1] - a[1]);
    for (const [pid, total] of sizes) {
      patterns.get(pid).type = 'workday'; // most = school days
      worked.set(pid, total);
    }
    for (const [pid] of sizes.slice(1)) {
      patterns.get(pid).type = 'workday_noschool';
    }
  }
  return { days, patterns, horizon };
}

async function fetchPatternFull(pid, pattern, stopIds, byId) {
  // fetch timetable for every stop on the representative date
  log(`Pattern ${pid} (${pattern.type}): fetching all stop timetables on ${pattern.dates[0]} …`);
  const date = pattern.dates[0];
  const tripIndex = new Map(); // tripId -> stop index (any)
  const fingerprintRows = [];
  let skipped = 0;

  for (const stopId of stopIds) {
    const t = await fetchTimetable(stopId, date);
    if (!t) {
      skipped++; // stop without a departure board (boarding not allowed)
      continue;
    }
    for (const dep of t.departures ?? []) {
      fingerprintRows.push(`${stopId}:${dep.trip_id}:${dep.departure}:${dep.index}`);
      if (!tripIndex.has(dep.trip_id)) tripIndex.set(dep.trip_id, dep.index);
    }
    // main_direction etc. intentionally ignored; direction comes from trip data
  }
  fingerprintRows.sort();
  const fingerprint = sha(fingerprintRows.join('|'));
  return { tripIndex, fingerprint, failedStops: skipped };
}

async function fetchTrips(tripIndex, tripCache, byId) {
  const missing = [...tripIndex.entries()].filter(([id]) => !tripCache.has(id));
  if (missing.length === 0) {
    log('  All trips already cached.');
    return { fetched: 0 };
  }
  log(`  Fetching ${missing.length} trips …`);
  let fetched = 0;
  const errors = [];
  const jobs = missing.map(([tripId, index]) => async () => {
    try {
      const t = await getJson(`/api/trip/${tripId}/${index}`);
      const times = [];
      for (const x of t.times ?? []) {
        const stopIdx = byId.get(x.place_id);
        if (stopIdx === undefined) continue; // unknown stop — should not happen
        const [hh, mm] = x.departure_time.split(':').map(Number);
        times.push([stopIdx, hh * 60 + mm]);
      }
      times.sort((a, b) => a[1] - b[1]);
      const trip = [
        tripId,
        t.line?.name ?? '?',
        t.direction ?? '',
        t.vehicle_type ?? 0,
        times,
      ];
      tripCache.set(tripId, trip);
      fetched++;
      if (fetched % 50 === 0) log(`  … ${fetched}/${missing.length} trips`);
    } catch (err) {
      errors.push(`${tripId}: ${err.message}`);
    }
  });
  // small worker pool
  const workers = Array.from({ length: REQ_CONCURRENCY }, async () => {
    while (jobs.length) await jobs.shift()();
  });
  await Promise.all(workers);
  if (errors.length) {
    log(`  ! ${errors.length} trips failed, e.g. ${errors.slice(0, 3).join('; ')}`);
    if (errors.length > missing.length * 0.02) {
      throw new Error(`too many trip fetch failures (${errors.length})`);
    }
  }
  return { fetched };
}

async function main() {
  await fs.mkdir(DATA_DIR, { recursive: true });
  const started = Date.now();
  const existing = FULL
    ? { meta: null, calendar: {}, tripCache: new Map(), patternFingerprints: {} }
    : await loadExisting();

  const { stops, byId, revision } = await fetchStops();
  const stopIds = stops.map((s) => s.id);
  const probeIds = pickProbeStops(stops);
  log(`Probe stops: ${probeIds.join(', ')}`);

  const { days, patterns, horizon } = await harvestCalendar(probeIds, existing.calendar);

  // per-pattern full harvest
  const patternOut = {};
  const fingerprints = {};
  let totalFetched = 0;
  for (const [pid, p] of patterns) {
    const { tripIndex, fingerprint, failedStops } = await fetchPatternFull(
      pid,
      p,
      stopIds,
      byId,
    );
    fingerprints[pid] = fingerprint;
    const unchanged =
      !FULL &&
      existing.patternFingerprints?.[pid] === fingerprint &&
      existing.meta?.patterns?.[pid];
    if (unchanged) {
      log(`  Fingerprint unchanged — reusing cached trips.`);
    } else {
      // refetch: fetch any trip not already in cache
      const r = await fetchTrips(tripIndex, existing.tripCache, byId);
      totalFetched += r.fetched;
    }
    const trips = [];
    for (const tripId of tripIndex.keys()) {
      const t = existing.tripCache.get(tripId);
      if (t) trips.push(t);
    }
    trips.sort((a, b) => (a[1] + a[2]).localeCompare(b[1] + b[2]));
    patternOut[pid] = { trips };
    if (failedStops > 0)
      log(`  ! pattern ${pid}: ${failedStops} stop timetables failed`);
  }

  // ---- write files
  const meta = {
    generatedAt: new Date().toISOString(),
    source: 'https://szczecinek.kiedyprzyjedzie.pl (Komunikacja Miejska Szczecinek)',
    city: 'Szczecinek',
    timezone: TZ,
    stopsRevision: revision,
    horizon: { from: todayStr(), to: horizon },
    fingerprints,
    patterns: Object.fromEntries(
      [...patterns.entries()].map(([pid, p]) => [
        pid,
        { type: p.type, days: p.dates.length, sampleDay: p.dates[0] },
      ]),
    ),
    counts: { stops: stops.length },
    requestCount,
  };

  await fs.writeFile(
    path.join(DATA_DIR, 'stops.json'),
    JSON.stringify({
      revision,
      stops: stops.map((s) => [
        s.code,
        s.name,
        +s.lat.toFixed(6),
        +s.lng.toFixed(6),
        (s.onDemand ? 1 : 0) | (s.station ? 2 : 0) | (s.onlyDisembarking ? 4 : 0),
        s.id,
      ]),
    }),
  );
  await fs.writeFile(
    path.join(DATA_DIR, 'calendar.json'),
    JSON.stringify({ updatedAt: new Date().toISOString(), days }),
  );
  for (const [pid, p] of Object.entries(patternOut)) {
    await fs.writeFile(
      path.join(DATA_DIR, `trips-${pid}.json`),
      JSON.stringify({ pattern: pid, trips: p.trips }),
    );
    log(`  wrote trips-${pid}.json (${p.trips.length} trips)`);
  }
  // remove stale pattern files
  const keep = new Set(Object.keys(patternOut).map((p) => `trips-${p}.json`));
  for (const f of await fs.readdir(DATA_DIR)) {
    if (f.startsWith('trips-') && !keep.has(f)) {
      await fs.unlink(path.join(DATA_DIR, f));
      log(`  removed stale ${f}`);
    }
  }
  meta.counts.trips = [...existing.tripCache.keys()].length;
  await fs.writeFile(
    path.join(DATA_DIR, 'meta.json'),
    JSON.stringify(meta, null, 2),
  );

  log(
    `Done in ${((Date.now() - started) / 1000).toFixed(0)}s — ` +
      `${requestCount} requests, ${totalFetched} trips fetched, ` +
      `${patterns.size} patterns, ${Object.keys(days).length} days.`,
  );
}

main().catch((err) => {
  console.error('HARVEST FAILED:', err);
  process.exit(1);
});
