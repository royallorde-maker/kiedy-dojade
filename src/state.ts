/** App state kept in memory, mirrored to sessionStorage where useful. */

import type { Itinerary, PlaceRef } from './types';

export interface PlanQuery {
  from: PlaceRef | null;
  to: PlaceRef | null;
  /** custom departure, minutes since midnight; null = now */
  departMin: number | null;
  date: string | null;
}

export interface PlanResults {
  itineraries: Itinerary[];
  walkOnly: { minutes: number; meters: number; leaveAt: number; arriveAt: number } | null;
  empty: boolean;
  pattern: string;
  exactDay: boolean;
  at: number;
}

export const state: {
  query: PlanQuery;
  results: PlanResults | null;
  userPos: { lat: number; lng: number } | null;
} = {
  query: { from: null, to: null, departMin: null, date: null },
  results: null,
  userPos: null,
};

const KEY = 'plan-state-v1';

export function persistQuery() {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(state.query));
  } catch {
    /* ignore */
  }
}

export function restoreQuery() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw) state.query = { ...state.query, ...JSON.parse(raw) };
  } catch {
    /* ignore */
  }
}

// ---------------------------------------------------------------- favorites & recents

export interface SavedPlace {
  name: string;
  ref: PlaceRef;
  kind: string;
}

const FAV_KEY = 'favorites-v1';
const REC_KEY = 'recents-v1';

export function favorites(): SavedPlace[] {
  try {
    return JSON.parse(localStorage.getItem(FAV_KEY) ?? '[]');
  } catch {
    return [];
  }
}

export function isFavorite(ref: PlaceRef): boolean {
  return favorites().some((f) => samePlace(f.ref, ref));
}

export function toggleFavorite(item: SavedPlace): boolean {
  const list = favorites();
  const idx = list.findIndex((f) => samePlace(f.ref, item.ref));
  if (idx >= 0) list.splice(idx, 1);
  else list.unshift(item);
  localStorage.setItem(FAV_KEY, JSON.stringify(list.slice(0, 30)));
  return idx < 0;
}

export function recents(): SavedPlace[] {
  try {
    return JSON.parse(localStorage.getItem(REC_KEY) ?? '[]');
  } catch {
    return [];
  }
}

export function pushRecent(item: SavedPlace) {
  const list = recents().filter((r) => !samePlace(r.ref, item.ref));
  list.unshift(item);
  localStorage.setItem(REC_KEY, JSON.stringify(list.slice(0, 12)));
}

export function samePlace(a: PlaceRef, b: PlaceRef): boolean {
  if (a.kind !== b.kind) return false;
  if (a.kind === 'stop' && b.kind === 'stop') return a.stop === b.stop;
  if (a.kind === 'street' && b.kind === 'street') return a.name === b.name;
  if ((a.kind === 'gps' || a.kind === 'place') && (b.kind === 'gps' || b.kind === 'place'))
    return a.name === b.name && Math.abs(a.lat - b.lat) < 1e-4;
  return false;
}
