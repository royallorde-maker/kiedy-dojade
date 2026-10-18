#!/usr/bin/env node
/**
 * KiedyDojadę — places/geocoder harvester.
 *
 * Downloads streets and points of interest in Szczecinek from OpenStreetMap
 * (Overpass API) and writes public/data/places.json, used for the
 * "Where do you want to go?" search — fully offline on the client.
 *
 * Address-like search works on streets: a street is stored as a polyline, and
 * the planner routes to the point of the street closest to your origin/stop.
 */

import fs from 'node:fs/promises';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DATA_DIR = path.join(ROOT, 'public', 'data');

// Szczecinek + immediate surroundings
const BBOX = '53.665,16.585,53.765,16.805';

const QUERY = `[out:json][timeout:120];
(
  nwr["highway"~"^(primary|secondary|tertiary|residential|living_street|pedestrian|unclassified)$"]["name"](${BBOX});
  nwr["amenity"]["name"](${BBOX});
  nwr["shop"]["name"](${BBOX});
  nwr["tourism"]["name"](${BBOX});
  nwr["leisure"]["name"](${BBOX});
  nwr["healthcare"]["name"](${BBOX});
  nwr["office"]["name"](${BBOX});
  nwr["place"]["name"](${BBOX});
  nwr["railway"="station"]["name"](${BBOX});
  nwr["public_transport"="station"]["name"](${BBOX});
);
out center tags;`;

const OVERPASS = process.env.OVERPASS_URL ?? 'https://overpass-api.de/api/interpreter';

function kindOf(tags) {
  const { amenity = '', shop = '', tourism = '', leisure = '', healthcare = '', office = '', railway = '', public_transport = '', highway = '' } = tags;
  if (highway) return 'street';
  if (railway === 'station' || public_transport === 'station' || amenity === 'bus_station') return 'transit';
  if (healthcare || ['hospital', 'clinic', 'doctors', 'pharmacy', 'dentist', 'veterinary'].includes(amenity)) return 'health';
  if (['school', 'kindergarten', 'college', 'university', 'language_school', 'music_school', 'driving_school'].includes(amenity)) return 'school';
  if (['restaurant', 'fast_food', 'cafe', 'bar', 'pub', 'ice_cream', 'biergarten'].includes(amenity)) return 'food';
  if (['bank', 'atm', 'post_office', 'townhall', 'police', 'fire_station', 'courthouse', 'embassy'].includes(amenity)) return 'services';
  if (['library', 'cinema', 'theatre', 'arts_centre', 'community_centre', 'museum', 'townhall'].includes(amenity) || ['museum', 'attraction', 'gallery', 'artwork', 'viewpoint'].includes(tourism)) return 'culture';
  if (amenity === 'place_of_worship') return 'worship';
  if (amenity === 'fuel' || amenity === 'charging_station') return 'fuel';
  if (amenity === 'parking' || amenity === 'bicycle_parking') return 'parking';
  if (amenity === 'marketplace') return 'shop';
  if (shop) return 'shop';
  if (['hotel', 'hostel', 'guest_house', 'motel', 'apartment'].includes(tourism)) return 'hotel';
  if (tourism === 'camp_site' || tourism === 'caravan_site') return 'leisure';
  if (leisure) return 'leisure';
  if (office) return 'office';
  if (tags.place) return 'place';
  return 'other';
}

const norm = (s) =>
  s
    .toLowerCase()
    .replaceAll('ą', 'a').replaceAll('ć', 'c').replaceAll('ę', 'e')
    .replaceAll('ł', 'l').replaceAll('ń', 'n').replaceAll('ó', 'o')
    .replaceAll('ś', 's').replaceAll('ź', 'z').replaceAll('ż', 'z')
    .replace(/\s+/g, ' ')
    .trim();

async function main() {
  console.log('Querying Overpass …');
  const res = await fetch(OVERPASS, {
    method: 'POST',
    headers: {
      'content-type': 'application/x-www-form-urlencoded',
      'user-agent': 'kiedy-dojade/0.1 (github.com/royallorde-maker/kiedy-dojade)',
    },
    body: 'data=' + encodeURIComponent(QUERY),
    signal: AbortSignal.timeout(180000),
  });
  if (!res.ok) throw new Error(`Overpass HTTP ${res.status}`);
  const data = await res.json();
  console.log(`  ${data.elements.length} raw elements`);

  const streets = new Map(); // normalized name -> {name, pts:[[lat,lng]]}
  const pois = [];
  const seenPoi = new Set();

  const pt = (el) => {
    if (el.type === 'node') return [el.lat, el.lon];
    if (el.center) return [el.center.lat, el.center.lon];
    return null;
  };

  for (const el of data.elements) {
    const tags = el.tags ?? {};
    const name = tags.name?.trim();
    if (!name) continue;
    const p = pt(el);
    if (!p) continue;
    const kind = kindOf(tags);

    if (kind === 'street') {
      const key = norm(name);
      if (!streets.has(key)) streets.set(key, { name, pts: [] });
      streets.get(key).pts.push(p);
    } else {
      // keep one POI per name+kind unless they are >120 m apart
      const key = norm(name) + '|' + kind;
      let skip = false;
      for (const q of pois) {
        if (q.key === key) {
          const d = Math.hypot((q.lat - p[0]) * 111320, (q.lng - p[1]) * 66000);
          if (d < 120) {
            skip = true;
            break;
          }
        }
      }
      if (!skip && !seenPoi.has(key + Math.round(p[0] * 1000) + ',' + Math.round(p[1] * 1000))) {
        seenPoi.add(key + Math.round(p[0] * 1000) + ',' + Math.round(p[1] * 1000));
        pois.push({ key, name, kind, lat: +p[0].toFixed(5), lng: +p[1].toFixed(5) });
      }
    }
  }

  // thin street points: drop vertices closer than ~35 m to the previous kept one
  const streetList = [];
  for (const { name, pts } of streets.values()) {
    const kept = [];
    for (const p of pts) {
      if (
        !kept.length ||
        Math.hypot(
          (kept.at(-1)[0] - p[0]) * 111320,
          (kept.at(-1)[1] - p[1]) * 66000,
        ) > 35
      ) {
        kept.push([+p[0].toFixed(5), +p[1].toFixed(5)]);
      }
    }
    if (kept.length) streetList.push([name, kept]);
  }
  streetList.sort((a, b) => a[0].localeCompare(b[0], 'pl'));

  const out = {
    generatedAt: new Date().toISOString(),
    source: 'OpenStreetMap contributors (ODbL), via Overpass API',
    bbox: BBOX,
    streets: streetList,
    pois: pois
      .sort((a, b) => a.name.localeCompare(b.name, 'pl'))
      .map((p) => [p.name, p.kind, p.lat, p.lng]),
  };

  await fs.mkdir(DATA_DIR, { recursive: true });
  await fs.writeFile(path.join(DATA_DIR, 'places.json'), JSON.stringify(out));
  console.log(
    `Wrote places.json: ${streetList.length} streets, ${out.pois.length} POIs.`,
  );
}

main().catch((err) => {
  console.error('GEOCODE FAILED:', err);
  process.exit(1);
});
