/** Geodesy + walking helpers. */

const R = 6371000; // earth radius, meters

export function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const toRad = Math.PI / 180;
  const dLat = (lat2 - lat1) * toRad;
  const dLng = (lng2 - lng1) * toRad;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** Detour factor: real walking distance is typically ~25% longer than the straight line. */
export const WALK_DETOUR = 1.25;
/** Comfortable walking speed in meters per minute (~4.7 km/h). */
export const WALK_SPEED_MPM = 78;
/** Flat time cost for entering/leaving a stop (crossing, finding the pole). */
export const WALK_BUFFER_MIN = 1;

export function walkMinutes(meters: number): number {
  return Math.max(1, Math.round((meters * WALK_DETOUR) / WALK_SPEED_MPM + WALK_BUFFER_MIN));
}

export function walkMetersAsTheCrowFlies(meters: number): number {
  return meters * WALK_DETOUR;
}

/** Nearest vertex of a polyline to a point. Returns index + distance in meters. */
export function nearestOnPolyline(
  lat: number,
  lng: number,
  pts: [number, number][],
): { idx: number; dist: number } {
  let best = { idx: 0, dist: Infinity };
  for (let i = 0; i < pts.length; i++) {
    const d = haversine(lat, lng, pts[i][0], pts[i][1]);
    if (d < best.dist) best = { idx: i, dist: d };
  }
  return best;
}

/** Distance in meters from a point to a street (min over its vertices). */
export function distanceToStreet(lat: number, lng: number, pts: [number, number][]): number {
  return nearestOnPolyline(lat, lng, pts).dist;
}

export function fmtMeters(m: number): string {
  if (m < 950) return `${Math.round(m / 10) * 10} m`;
  return `${(m / 1000).toFixed(1)} km`;
}

/** minutes since midnight -> "HH:MM" (handles past-midnight by wrapping) */
export function fmtTime(min: number): string {
  const m = ((Math.round(min) % 1440) + 1440) % 1440;
  const h = Math.floor(m / 60);
  return `${String(h).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
}

export function fmtDuration(min: number): string {
  const m = Math.round(min);
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${m % 60} min`;
}

/** "HH:MM" or "H:MM" -> minutes since midnight */
export function parseTime(s: string): number {
  const [h, m] = s.split(':').map(Number);
  return h * 60 + (m || 0);
}

export function nowMinutes(d: Date = new Date()): number {
  return d.getHours() * 60 + d.getMinutes();
}
