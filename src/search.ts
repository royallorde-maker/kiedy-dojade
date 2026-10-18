/** Offline place search over stops, streets and POIs (index built from the harvested data). */

import { haversine } from './geo';
import type { PlaceRef, World } from './types';

export interface SearchItem {
  id: string;
  /** kind of the target */
  kind: 'stop' | 'street' | 'poi';
  /** POI category, when applicable */
  pkind?: string;
  name: string;
  sub?: string;
  /** stop details (rendered per-language by the UI) */
  code?: number;
  onDemand?: boolean;
  lat: number;
  lng: number;
  norm: string;
  ref: PlaceRef;
}

export function norm(s: string): string {
  return s
    .toLowerCase()
    .replaceAll('ą', 'a').replaceAll('ć', 'c').replaceAll('ę', 'e')
    .replaceAll('ł', 'l').replaceAll('ń', 'n').replaceAll('ó', 'o')
    .replaceAll('ś', 's').replaceAll('ź', 'z').replaceAll('ż', 'z')
    .replace(/[^a-z0-9\s./-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

let indexCache: SearchItem[] | null = null;

export function buildIndex(world: World): SearchItem[] {
  if (indexCache) return indexCache;
  const items: SearchItem[] = [];

  world.stops.forEach((s) => {
    items.push({
      id: `stop:${s.i}`,
      kind: 'stop',
      name: s.name,
      code: s.code,
      onDemand: s.onDemand,
      lat: s.lat,
      lng: s.lng,
      norm: norm(s.name + ' ' + s.code),
      ref: { kind: 'stop', name: s.name, stop: s.i },
    });
  });

  world.places.streets.forEach(([name, pts], idx) => {
    const mid = pts[Math.floor(pts.length / 2)];
    items.push({
      id: `street:${idx}`,
      kind: 'street',
      name,
      lat: mid[0],
      lng: mid[1],
      norm: norm(name),
      ref: { kind: 'street', name, pts },
    });
  });

  world.places.pois.forEach(([name, pkind, lat, lng], idx) => {
    items.push({
      id: `poi:${idx}`,
      kind: 'poi',
      pkind,
      name,
      lat,
      lng,
      norm: norm(name),
      ref: { kind: 'place', name, pkind, lat, lng },
    });
  });

  indexCache = items;
  return items;
}

function score(item: SearchItem, tokens: string[]): number {
  let total = 0;
  const words = item.norm.split(/[\s./-]+/).filter(Boolean);
  for (const tok of tokens) {
    if (item.norm === tok) total += 400;
    else if (words.some((w) => w === tok)) total += 220;
    else if (words.some((w) => w.startsWith(tok))) total += 120;
    else if (item.norm.startsWith(tok)) total += 100;
    else if (item.norm.includes(tok)) total += 45;
    else return -1; // every token must match something
  }
  // prefer shorter names (more specific) slightly
  total -= Math.min(60, item.norm.length);
  if (item.kind === 'stop') total += 12;
  return total;
}

export function searchPlaces(
  index: SearchItem[],
  query: string,
  near?: { lat: number; lng: number },
  limit = 14,
): SearchItem[] {
  const q = norm(query);
  if (q.length < 2) return [];
  const tokens = q.split(' ').filter(Boolean);
  const scored: { item: SearchItem; s: number; d: number }[] = [];
  for (const item of index) {
    const s = score(item, tokens);
    if (s < 0) continue;
    const d = near ? haversine(near.lat, near.lng, item.lat, item.lng) : 0;
    scored.push({ item, s, d });
  }
  scored.sort((a, b) => b.s - a.s || a.d - b.d);
  // de-duplicate by name+kind (streets with many points, POIs repeated)
  const out: SearchItem[] = [];
  const seen = new Set<string>();
  for (const { item } of scored) {
    const key = item.kind + '|' + item.norm;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(item);
    if (out.length >= limit) break;
  }
  return out;
}

/** Nearest stops to a point, sorted by walking distance. */
export function nearestStops(
  world: World,
  lat: number,
  lng: number,
  limit = 6,
  maxMeters = 1500,
): { stop: World['stops'][number]; meters: number }[] {
  const list = world.stops
    .filter((s) => !s.onlyDisembarking)
    .map((s) => ({ stop: s, meters: haversine(lat, lng, s.lat, s.lng) }))
    .filter((x) => x.meters <= maxMeters)
    .sort((a, b) => a.meters - b.meters);
  return list.slice(0, limit);
}
