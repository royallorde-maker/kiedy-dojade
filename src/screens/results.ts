/** Results screen: connection cards + expandable itinerary details with live info. */

import { setCleanup } from '../cleanup';
import { dayTypeLabel, intermediateLabel, stopsLabel, t, transfersLabel } from '../i18n';
import { state, pushRecent } from '../state';
import { planJourney, todayStr } from '../planner';
import { fetchStopBoard, fetchTripExecution, type LiveRow } from '../realtime';
import { fmtDuration, fmtMeters, fmtTime, nowMinutes } from '../geo';
import type { Itinerary, RideLeg, World } from '../types';
import { ICONS, clear, el, lineBadge, svg } from '../ui';

export function renderResults(root: HTMLElement, world: World) {
  const res = state.results;
  if (!res) {
    location.hash = '#/';
    return;
  }
  const main = el('div', { class: 'screen screen--results' });

  // header
  const back = el('button', { class: 'icon-btn', 'aria-label': t('back') }, svg(ICONS.back));
  back.addEventListener('click', () => history.back());
  const fromName = state.query.from?.name ?? t('myLocation');
  const toName = state.query.to?.name ?? '';
  const head = el(
    'div',
    { class: 'results-head' },
    back,
    el('div', { class: 'results-head__text' },
      el('div', { class: 'results-head__to', text: toName }),
      el('div', { class: 'results-head__from', text: `${t('from')}: ${fromName}` }),
    ),
  );
  main.append(head);

  // day-type chip / warning
  const ptype = world.meta.patterns[res.pattern]?.type ?? '';
  const chips = el('div', { class: 'results-meta' });
  chips.append(el('span', { class: 'chip chip--soft', text: `📅 ${dayTypeLabel(ptype)}` }));
  const when = state.query.departMin;
  chips.append(el('span', { class: 'chip chip--soft', text: when === null ? `🕐 ${t('now')}` : `🕐 ${fmtTime(when)}` }));
  if (!res.exactDay) {
    chips.append(
      el('span', {
        class: 'chip chip--warn',
        text: t('scheduleGuess', { type: dayTypeLabel(ptype) }),
      }),
    );
  }
  main.append(chips);

  if (res.empty && !res.walkOnly) {
    const box = el('div', { class: 'empty-box' },
      el('div', { class: 'empty-box__title', text: t('noResults') }),
      el('div', { class: 'empty-box__hint', text: t('noResultsHint') }),
    );
    main.append(box);
    root.append(main);
    wireMore(main, world);
    return;
  }

  // itinerary cards
  const list = el('div', { class: 'itineraries' });
  const cards: HTMLElement[] = [];
  const detailHost = el('div', { class: 'detail-host' });

  const walk = res.walkOnly;
  const walkBetter = !!walk && (res.itineraries.length === 0 || walk.arriveAt <= res.itineraries[0].arriveAt);

  if (walk && walkBetter) list.append(walkCard(walk, true));

  res.itineraries.forEach((it, idx) => {
    const card = itineraryCard(it, idx === 0 && !walkBetter, () => openDetail(it, card));
    cards.push(card);
    list.append(card);
  });

  // walk-only option
  if (walk && !walkBetter) {
    list.append(walkCard(walk, false));
  }

  main.append(list, detailHost);

  function openDetail(it: Itinerary, card: HTMLElement, keepOpen = false) {
    const open = card.classList.contains('it-card--open');
    cards.forEach((c) => c.classList.remove('it-card--open'));
    clear(detailHost);
    setCleanup(null);
    if (open && !keepOpen) return;
    card.classList.add('it-card--open');
    const detail = itineraryDetail(it, world);
    detailHost.append(detail);
    detail.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    wireLive(it, detail);
  }

  root.append(main);
  wireMore(main, world, () => {
    cards.forEach((c) => c.classList.remove('it-card--open'));
    clear(detailHost);
    setCleanup(null);
  });

  // remember the destination
  if (state.query.to && state.query.to.kind !== 'gps') {
    pushRecent({ name: state.query.to.name, ref: state.query.to, kind: state.query.to.kind });
  }
}

/** "just walk" alternative — shown as a card like the bus options. */
function walkCard(
  w: { minutes: number; meters: number; leaveAt: number; arriveAt: number },
  best: boolean,
): HTMLElement {
  const card = el('div', { class: `it-card it-card--walk${best ? ' it-card--best' : ''}` });
  card.append(
    el('div', { class: 'it-card__times' },
      el('span', { class: 'it-card__time', text: fmtTime(w.leaveAt) }),
      el('span', { class: 'it-card__arrow', text: '→' }),
      el('span', { class: 'it-card__time', text: fmtTime(w.arriveAt) }),
      el('span', { class: 'it-card__dur', text: fmtDuration(w.minutes) }),
    ),
    el('div', { class: 'it-card__sub' },
      el('span', { text: `🚶 ${t('walkOnly')}` }),
      el('span', { class: 'dot' }),
      el('span', { text: `${fmtMeters(w.meters)} ${t('walk')}` }),
    ),
    el('div', { class: 'it-card__strip' },
      el('span', { class: 'strip-walk' }, svg(ICONS.walk), el('span', { text: `${w.minutes}` })),
    ),
  );
  if (best) card.append(el('span', { class: 'it-card__tag', text: t('suggested') }));
  return card;
}

function itineraryCard(it: Itinerary, best: boolean, onOpen: () => void): HTMLElement {
  const card = el('button', { class: 'it-card' });
  const rides = it.legs.filter((l) => l.type === 'ride') as RideLeg[];

  const times = el(
    'div',
    { class: 'it-card__times' },
    el('span', { class: 'it-card__time', text: fmtTime(it.leaveAt) }),
    el('span', { class: 'it-card__arrow', text: '→' }),
    el('span', { class: 'it-card__time', text: fmtTime(it.arriveAt) }),
    el('span', { class: 'it-card__dur', text: fmtDuration(it.arriveAt - it.leaveAt) }),
  );

  const sub = el(
    'div',
    { class: 'it-card__sub' },
    el('span', { text: rides.length === 1 ? t('busesOne') : `${rides.length} ${t('busesMany')}` }),
    el('span', { class: 'dot' }),
    el('span', { text: it.transfers > 0 ? transfersLabel(it.transfers) : t('noTransfers') }),
    el('span', { class: 'dot' }),
    el('span', { text: `${fmtMeters(it.walkMeters)} ${t('walk')}` }),
  );

  const strip = el('div', { class: 'it-card__strip' });
  for (const leg of it.legs) {
    if (leg.type === 'walk') {
      strip.append(
        el('span', { class: 'strip-walk' },
          svg(ICONS.walk),
          el('span', { text: `${leg.minutes}` }),
        ),
      );
    } else {
      strip.append(lineBadge(leg.line));
    }
    strip.append(el('span', { class: 'strip-sep' }, svg(ICONS.chevron)));
  }
  strip.lastChild?.remove();

  card.append(times, sub, strip);
  if (it.tag === 'fewer-transfers') {
    card.append(el('span', { class: 'it-card__tag', text: t('fewerTransfers') }));
  }
  if (best) card.classList.add('it-card--best');
  card.addEventListener('click', onOpen);
  return card;
}

function itineraryDetail(it: Itinerary, world: World): HTMLElement {
  const box = el('div', { class: 'it-detail' });

  // leave hint
  const hint = el('div', { class: 'it-detail__hint' });
  updateLeaveHint(hint, it);
  box.append(hint);

  const ol = el('ol', { class: 'timeline' });
  for (const leg of it.legs) {
    if (leg.type === 'walk') {
      ol.append(
        el('li', { class: 'tl-item tl-item--walk' },
          el('span', { class: 'tl-icon' }, svg(ICONS.walk)),
          el('div', { class: 'tl-body' },
            el('div', { class: 'tl-title' },
              el('b', { text: `${leg.minutes} ${t('min')}` }),
              ` · ${fmtMeters(leg.meters)} ${t('walk')}`,
            ),
            el('div', { class: 'tl-sub', text: `${leg.fromName} → ${leg.toName}` }),
            el('div', { class: 'tl-times', text: `${fmtTime(leg.depMin)} → ${fmtTime(leg.arrMin)}` }),
          ),
        ),
      );
    } else {
      const item = el('li', { class: 'tl-item tl-item--ride' },
        el('span', { class: 'tl-icon tl-icon--line' }, lineBadge(leg.line, true)),
        el('div', { class: 'tl-body' },
          el('div', { class: 'tl-title' },
            el('b', { text: `${fmtTime(leg.depMin)} → ${fmtTime(leg.arrMin)}` }),
            ` · ${stopsLabel(leg.stops.length - 1)}`,
          ),
          el('div', { class: 'tl-sub' }, `${t('direction')} ${leg.dir}`),
          el('button', { class: 'tl-stop-link', text: `${t('board')}: ${leg.boardStop.name}` }),
          el('button', { class: 'tl-stop-link', text: `${t('alight')}: ${leg.alightStop.name}` }),
        ),
      );
      const stopBtns = item.querySelectorAll('.tl-stop-link');
      (stopBtns[0] as HTMLElement).addEventListener('click', () => {
        location.hash = `#/stop/${leg.boardStop.i}`;
      });
      (stopBtns[1] as HTMLElement).addEventListener('click', () => {
        location.hash = `#/stop/${leg.alightStop.i}`;
      });

      // expandable intermediate stops
      if (leg.stops.length > 2) {
        const toggle = el('button', { class: 'tl-stops-toggle', text: `▾ ${intermediateLabel(leg.stops.length - 2)}` });
        const ul = el('ul', { class: 'tl-stops hidden' });
        for (const s of leg.stops) {
          const isBoard = s.stop.i === leg.boardStop.i;
          const isAlight = s.stop.i === leg.alightStop.i && !isBoard;
          const li = el('li', { class: `tl-stop${isBoard ? ' tl-stop--board' : ''}${isAlight ? ' tl-stop--alight' : ''}` },
            el('span', { class: 'tl-stop__time', text: fmtTime(s.min) }),
            el('span', { class: 'tl-stop__name', text: s.stop.name }),
          );
          li.addEventListener('click', () => {
            location.hash = `#/stop/${s.stop.i}`;
          });
          ul.append(li);
        }
        toggle.addEventListener('click', () => {
          ul.classList.toggle('hidden');
          toggle.textContent = `${ul.classList.contains('hidden') ? '▾' : '▴'} ${intermediateLabel(leg.stops.length - 2)}`;
        });
        item.querySelector('.tl-body')?.append(toggle, ul);
      }
      ol.append(item);
    }
  }
  box.append(ol);

  // live panel host
  box.append(el('div', { class: 'live-box hidden' }));
  void world;
  return box;
}

function updateLeaveHint(hint: HTMLElement, it: Itinerary) {
  const now = nowMinutes();
  const today = state.query.date === null || state.query.date === todayStr();
  clear(hint);
  if (!today) {
    hint.append(el('span', { text: `${t('leave')} ${fmtTime(it.leaveAt)}` }));
    return;
  }
  const diff = it.leaveAt - now;
  if (diff <= 0 && it.leaveAt > now - (it.arriveAt - it.leaveAt)) {
    hint.append(el('span', { class: 'hint-go', text: `🏃 ${t('leaveNow')}` }));
  } else if (diff > 0 && diff <= 90) {
    hint.append(el('span', { class: 'hint-go', text: `🚪 ${t('leaveIn')} ${diff} ${t('min')}` }));
  } else if (diff > 90) {
    hint.append(el('span', { text: `${t('leave')} ${fmtTime(it.leaveAt)}` }));
  }
}

/** Poll live data for the first ride of the opened itinerary. */
function wireLive(it: Itinerary, detail: HTMLElement) {
  const liveBox = detail.querySelector('.live-box') as HTMLElement;
  const firstRide = it.legs.find((l) => l.type === 'ride') as RideLeg | undefined;
  if (!firstRide || !liveBox) return;

  const isToday = !state.query.date || state.query.date === todayStr();
  if (!isToday) return;

  let stopped = false;
  let executionIndex = -1;

  async function poll() {
    if (stopped) return;
    try {
      const rows = await fetchStopBoard(firstRide!.boardStop.id);
      const row = matchRow(rows, firstRide!);
      renderLive(liveBox, firstRide!, row);
      // vehicle position
      if (row?.tripExecutionId !== undefined && executionIndex < 0) {
        executionIndex = 0;
        void fetchTripExecution(firstRide!.tripId, firstRide!.boardIdx).then((ex) => {
          if (!ex || stopped) return;
          const extra = vehicleNote(ex, firstRide!);
          if (extra) liveBox.append(extra);
        });
      }
    } catch {
      /* ignore live errors */
    }
  }

  void poll();
  const timer = setInterval(poll, 20000);
  setCleanup(() => {
    stopped = true;
    clearInterval(timer);
  });
}

function matchRow(rows: LiveRow[], ride: RideLeg): LiveRow | undefined {
  return rows.find((r) => r.tripId === ride.tripId) ?? rows.find((r) => r.line === ride.line);
}

function renderLive(box: HTMLElement, ride: RideLeg, row?: LiveRow) {
  clear(box);
  if (!row) {
    box.classList.add('hidden');
    return;
  }
  box.classList.remove('hidden');
  const expected = row.departureSec !== undefined ? Math.round(row.departureSec / 60) : ride.depMin;
  const wrap = el('div', { class: 'live-row' });
  wrap.append(el('span', { class: 'live-dot' }), el('span', { class: 'live-label', text: t('live') }));
  if (row.canceled) {
    wrap.append(el('span', { class: 'live-canceled', text: `⚠ ${t('tripCanceled')}` }));
  } else {
    wrap.append(
      el('span', { class: 'live-time', text: `${t('board')} ${fmtTime(expected)}` }),
    );
    if (row.delayMin !== undefined && row.delayMin !== 0) {
      const cls = row.delayMin > 0 ? 'live-delay' : 'live-early';
      wrap.append(
        el('span', { class: cls, text: `${row.delayMin > 0 ? '+' : ''}${row.delayMin} ${t('min')}` }),
      );
    }
    if (row.attrs?.includes('low_floor')) {
      wrap.append(el('span', { class: 'live-attr', text: '♿' }));
    }
    if (row.load) {
      wrap.append(el('span', { class: 'live-attr', text: `👥 ${row.load}` }));
    }
  }
  box.append(wrap);
}

function vehicleNote(ex: { visits?: { order: number; position: string }[] }, ride: RideLeg): HTMLElement | null {
  if (!ex.visits) return null;
  const current = ex.visits.find((v) => v.position === 'Current');
  if (!current) return null;
  const away = ride.boardIdx - current.order;
  if (away <= 0) return el('div', { class: 'live-note', text: `🚌 ${t('vehicleHere')}` });
  if (away <= 12) return el('div', { class: 'live-note', text: t('vehicleIn', { n: away }) });
  return null;
}

/** "Later departures" / refresh controls under the list. */
function wireMore(
  main: HTMLElement,
  world: World,
  before?: () => void,
) {
  const bar = el('div', { class: 'more-bar' });
  const later = el('button', { class: 'btn btn--ghost' }, '⏭ ', t('nextDepartures'));
  later.addEventListener('click', async () => {
    const res = state.results;
    if (!res) return;
    const last = res.itineraries.at(-1);
    const base = last
      ? ((last.legs.find((l) => l.type === 'ride') as RideLeg | undefined)?.depMin ?? last.leaveAt)
      : nowMinutes();
    const anchor = Math.max(base, nowMinutes()) + 1;
    before?.();
    const outcome = await planJourney(world, {
      origin: state.query.from!,
      dest: state.query.to!,
      date: state.query.date ?? todayStr(),
      departMin: anchor,
    });
    state.results = { ...outcome, at: Date.now() };
    location.hash = '#/results';
    // force re-render: replace hash listener won't fire when already on #/results
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
  bar.append(later);
  main.append(bar);
}
