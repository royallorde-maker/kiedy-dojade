/** Stop screen: live departure board + full-day timetable for one stop. */

import { setCleanup } from '../cleanup';
import { dayTypeLabel, t } from '../i18n';
import { resolvePattern, todayStr } from '../data';
import { fetchStopBoard, type LiveRow } from '../realtime';
import { fmtTime, nowMinutes } from '../geo';
import { isFavorite, toggleFavorite } from '../state';
import type { Stop, World } from '../types';
import { ICONS, clear, el, lineBadge, svg } from '../ui';

export function renderStop(root: HTMLElement, world: World, stopIdx: number) {
  const stop = world.stops[stopIdx];
  if (!stop) {
    location.hash = '#/';
    return;
  }
  const main = el('div', { class: 'screen screen--stop' });

  const back = el('button', { class: 'icon-btn', 'aria-label': t('back') }, svg(ICONS.back));
  back.addEventListener('click', () => history.back());
  const star = el('button', { class: 'icon-btn', 'aria-label': t('save') }, svg(ICONS.star));
  const favState = () => star.classList.toggle('icon-btn--on', isFavorite({ kind: 'stop', name: stop.name, stop: stop.i }));
  star.addEventListener('click', () => {
    toggleFavorite({ name: stop.name, ref: { kind: 'stop', name: stop.name, stop: stop.i }, kind: 'stop' });
    favState();
  });

  const badges = el('div', { class: 'stop-badges' },
    el('span', { class: 'chip chip--soft', text: `${t('stop')} ${stop.code}` }),
    stop.onDemand ? el('span', { class: 'chip chip--warn', text: 'NŻ' }) : null,
    stop.station ? el('span', { class: 'chip chip--soft', text: `🚉 ${t('station')}` }) : null,
    stop.onlyDisembarking ? el('span', { class: 'chip chip--warn', text: 'wysiadanie' }) : null,
  );

  main.append(
    el('div', { class: 'stop-head' },
      back,
      el('div', { class: 'stop-head__text' },
        el('h1', { class: 'stop-head__name', text: stop.name }),
        badges,
      ),
      star,
    ),
  );
  favState();

  // live board
  const liveBox = el('div', { class: 'board card' });
  liveBox.append(el('div', { class: 'board__title' }, `⏱ ${t('nextDepartures')}`, el('span', { class: 'live-dot' })));
  const liveList = el('div', { class: 'board__list' });
  liveBox.append(liveList);
  main.append(liveBox);

  // full day
  const dayBox = el('div', { class: 'day card' });
  main.append(dayBox);

  root.append(main);

  let stopped = false;
  async function poll() {
    if (stopped) return;
    try {
      const rows = await fetchStopBoard(stop.id);
      renderLive(liveList, rows);
    } catch {
      clear(liveList);
      liveList.append(el('div', { class: 'board__empty', text: t('noDepartures') }));
    }
  }
  void poll();
  const timer = setInterval(poll, 20000);

  void renderDay(dayBox, world, stop);

  setCleanup(() => {
    stopped = true;
    clearInterval(timer);
  });
}

function renderLive(list: HTMLElement, rows: LiveRow[]) {
  clear(list);
  const now = nowMinutes();
  const soon = rows
    .filter((r) => !r.beforeTripStart)
    .slice(0, 12);
  if (!soon.length) {
    list.append(el('div', { class: 'board__empty', text: t('noDepartures') }));
    return;
  }
  for (const r of soon) {
    const expected = r.departureSec !== undefined ? Math.round(r.departureSec / 60) : null;
    const inMin = expected !== null ? expected - now : null;
    const row = el(
      'div',
      { class: `board__row${r.canceled ? ' board__row--canceled' : ''}` },
      lineBadge(r.line ?? '?'),
      el('div', { class: 'board__dir', text: r.direction ?? '' }),
      r.canceled
        ? el('span', { class: 'board__canceled', text: t('canceledShort') })
        : el('div', { class: 'board__time' },
            el('span', { class: 'board__hhmm', text: expected !== null ? fmtTime(expected) : '—' }),
            inMin !== null && inMin >= 0
              ? el('span', { class: 'board__in', text: inMin === 0 ? t('nowAt') : `${inMin} ${t('min')}` })
              : null,
            r.delayMin
              ? el('span', { class: r.delayMin > 0 ? 'live-delay' : 'live-early', text: `${r.delayMin > 0 ? '+' : ''}${r.delayMin}` })
              : null,
          ),
      r.attrs?.includes('low_floor') ? el('span', { class: 'board__attr', text: '♿' }) : null,
    );
    list.append(row);
  }
}

async function renderDay(box: HTMLElement, world: World, stop: Stop) {
  clear(box);
  const today = todayStr();
  const { pattern, exact, type } = resolvePattern(world, today);
  box.append(
    el('div', { class: 'board__title' },
      `📋 ${t('fullTimetable')}`,
      el('span', { class: 'day__type', text: `${t('scheduleDate')}: ${dayTypeLabel(type)}` }),
    ),
  );
  if (!exact) {
    box.append(el('div', { class: 'day__warn', text: t('scheduleGuess', { type: dayTypeLabel(type) }) }));
  }
  const trips = await world.loadTrips(pattern);
  const byLine = new Map<string, { dir: string; times: number[] }>();
  for (const trip of trips) {
    const idx = trip.times.findIndex(([si]) => si === stop.i);
    if (idx < 0) continue;
    // boarding not possible at onlyDisembarking stops, but showing is fine
    const key = trip.line;
    if (!byLine.has(key)) byLine.set(key, { dir: trip.dir, times: [] });
    const entry = byLine.get(key)!;
    entry.times.push(trip.times[idx][1]);
  }
  const sorted = [...byLine.entries()].sort((a, b) => lineSortKey(a[0]) - lineSortKey(b[0]));
  if (!sorted.length) {
    box.append(el('div', { class: 'board__empty', text: t('noDepartures') }));
    return;
  }
  for (const [line, { dir, times }] of sorted) {
    times.sort((a, b) => a - b);
    box.append(
      el('div', { class: 'day__line' },
        el('div', { class: 'day__line-head' },
          lineBadge(line),
          el('span', { class: 'day__line-dir', text: `${t('direction')} ${dir}` }),
        ),
        el('div', { class: 'day__times' }, ...times.map((m) =>
          el('span', { class: 'day__time', text: fmtTime(m) }),
        )),
      ),
    );
  }
}

function lineSortKey(line: string): number {
  const m = line.match(/^(\d+)([A-Z]*)$/);
  if (!m) return 999;
  return Number(m[1]) * 10 + (m[2] ? 1 : 0);
}
