/**
 * Realtime client for the KiedyPrzyjedzie passenger API
 * (the same public endpoints the city's official web app uses).
 * The API sends `Access-Control-Allow-Origin: *`, so the browser can call it
 * directly; no proxy is needed.
 */

const API = 'https://szczecinek.kiedyprzyjedzie.pl/api';

export interface LiveRow {
  tripId?: number;
  tripExecutionId?: string | number;
  line?: string;
  direction?: string;
  symbols?: string;
  /** expected departure, seconds since midnight */
  departureSec?: number;
  /** scheduled departure, seconds since midnight (if provided separately) */
  scheduledSec?: number;
  delayMin?: number;
  canceled?: boolean;
  estimated?: boolean;
  load?: string;
  vehicleType?: number;
  attrs?: string[];
  platform?: string;
  index?: number;
  beforeTripStart?: boolean;
}

interface RawRow {
  trip_id?: number;
  trip_execution_id?: string | number;
  line_name?: string;
  direction?: string;
  symbols?: string;
  departure?: number;
  real_departure?: number;
  timetable_departure?: number;
  time_diff?: number;
  canceled?: boolean;
  is_estimated?: boolean;
  passenger_load?: string;
  vehicle_type?: number;
  vehicle_attributes?: string[];
  platform?: string;
  index?: number;
  before_trip_start?: boolean;
}

async function get<T>(path: string, timeoutMs = 8000): Promise<T> {
  const res = await fetch(API + path, {
    headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`API ${path}: HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

function normalize(r: RawRow): LiveRow {
  return {
    tripId: r.trip_id,
    tripExecutionId: r.trip_execution_id,
    line: r.line_name,
    direction: r.direction,
    symbols: r.symbols,
    departureSec: r.real_departure ?? r.departure,
    scheduledSec: r.timetable_departure ?? r.departure,
    delayMin: typeof r.time_diff === 'number' ? Math.round(r.time_diff) : undefined,
    canceled: !!r.canceled,
    estimated: !!r.is_estimated,
    load: r.passenger_load,
    vehicleType: r.vehicle_type,
    attrs: r.vehicle_attributes,
    platform: r.platform,
    index: r.index,
    beforeTripStart: r.before_trip_start,
  };
}

/** Live/virtual departure board for one stop. */
export async function fetchStopBoard(placeId: string): Promise<LiveRow[]> {
  const data = await get<{ departures?: RawRow[] }>(`/departures/${encodeURIComponent(placeId)}`);
  return (data.departures ?? []).map(normalize);
}

export interface Announcement {
  id: number;
  markdown: string;
}

export async function fetchAnnouncements(): Promise<Announcement[]> {
  try {
    const data = await get<{ announcements?: { id: number; content_markdown: string }[] }>(
      '/announcements',
    );
    return (data.announcements ?? []).map((a) => ({ id: a.id, markdown: a.content_markdown }));
  } catch {
    return [];
  }
}

export interface TripExecution {
  vehicle?: { lat: number; lng: number; bearing?: number } | null;
  visits?: { order: number; position: 'Current' | 'Past' | 'Future'; canceled?: boolean; stop?: { name?: string } }[];
  canceled?: number[];
  estimated?: boolean;
  tripEnded?: boolean;
  timeValues?: number[];
  beforeTripStart?: boolean;
}

/**
 * Live state of a single trip (vehicle position, delays, cancellations).
 * Returns null when unavailable (outside service hours the endpoint 400s).
 */
export async function fetchTripExecution(
  tripId: number,
  index: number,
): Promise<TripExecution | null> {
  try {
    const data = await get<Record<string, unknown>>(`/trip_execution/${tripId}/${index}`);
    return {
      vehicle: (data.vehicle as TripExecution['vehicle']) ?? null,
      visits: (data.visits as TripExecution['visits']) ?? undefined,
      canceled: (data.canceled as number[]) ?? undefined,
      estimated: !!data.estimated,
      tripEnded: !!data.trip_ended,
      timeValues: (data.time_values as number[]) ?? undefined,
      beforeTripStart: !!data.before_trip_start,
    };
  } catch {
    return null;
  }
}
