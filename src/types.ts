/** Core data types shared across the app. */

export interface Stop {
  /** index in the stops array — used by trips */
  i: number;
  /** upstream place id, e.g. "31830:56491" — needed for live endpoints */
  id: string;
  code: number;
  name: string;
  lat: number;
  lng: number;
  /** "na żądanie" — request stop */
  onDemand: boolean;
  station: boolean;
  /** boarding not allowed (terminus) */
  onlyDisembarking: boolean;
}

export interface Trip {
  id: number;
  line: string;
  dir: string;
  vt: number;
  /** [stopIdx, departure minutes since midnight] */
  times: [number, number][];
}

export interface Meta {
  generatedAt: string;
  source: string;
  city: string;
  timezone: string;
  stopsRevision: number;
  horizon: { from: string; to: string };
  fingerprints: Record<string, string>;
  patterns: Record<string, { type: PatternType; days: number; sampleDay: string }>;
  counts: { stops: number; trips?: number };
  requestCount?: number;
}

export type PatternType = 'workday' | 'workday_noschool' | 'sat' | 'sun' | 'special' | string;

export interface CalendarData {
  updatedAt: string;
  /** date (YYYY-MM-DD) -> pattern id */
  days: Record<string, string>;
}

export interface PlacesData {
  generatedAt: string;
  source: string;
  /** [name, pts[[lat,lng]]] */
  streets: [string, [number, number][]][];
  /** [name, kind, lat, lng] */
  pois: [string, string, number, number][];
}

/** What the user typed as origin/destination, resolved to coordinates. */
export type PlaceRef =
  | { kind: 'gps'; name: string; lat: number; lng: number }
  | { kind: 'stop'; name: string; stop: number }
  | { kind: 'place'; name: string; pkind: string; lat: number; lng: number }
  | { kind: 'street'; name: string; pts: [number, number][] };

export interface World {
  stops: Stop[];
  meta: Meta;
  calendar: CalendarData;
  places: PlacesData;
  /** lazy loader for pattern trips */
  loadTrips: (patternId: string) => Promise<Trip[]>;
}

// ---------------------------------------------------------------- planning

export interface WalkLeg {
  type: 'walk';
  fromName: string;
  toName: string;
  meters: number;
  minutes: number;
  /** chronological markers, minutes since midnight */
  depMin: number;
  arrMin: number;
  /** optional geometry hint [lat,lng] */
  from: [number, number];
  to: [number, number];
}

export interface RideLeg {
  type: 'ride';
  tripId: number;
  line: string;
  dir: string;
  boardStop: Stop;
  alightStop: Stop;
  boardIdx: number;
  alightIdx: number;
  depMin: number;
  arrMin: number;
  /** intermediate stops including board & alight */
  stops: { stop: Stop; min: number }[];
}

export type Leg = WalkLeg | RideLeg;

export interface Itinerary {
  id: string;
  legs: Leg[];
  leaveAt: number;
  arriveAt: number;
  transfers: number;
  walkMeters: number;
  walkMinutes: number;
  /** index of leg where you board the first vehicle (for live info) */
  firstRideLeg: number;
  /** pattern id used to compute it */
  pattern: string;
  /** what the itinerary represents */
  tag: 'best' | 'next' | 'fewer-transfers' | 'walk';
}
