/** Home screen: "Where do you want to go?" */

import { nearestStops, buildIndex, searchPlaces } from '../search';
import { persistQuery, state } from '../state';
import { requestGps } from '../gps';
import { getLang, setLang, t, dayTypeLabel } from '../i18n';
import { planJourney, todayStr } from '../planner';
import { fetchAnnouncements } from '../realtime';
import { fmtMeters, nowMinutes, walkMinutes } from '../geo';
import type { PlaceRef, World } from '../types';
import { ICONS, clear, el, markdownLite, svg, toast } from '../ui';
import { openPicker } from './pick';

function describe(ref: PlaceRef): string {
  switch (ref.kind) {
    case 'gps':
      return t('myLocationShort');
    case 'stop':
      return t('stop');
    case 'street':
      return t('street');
    case 'place':
      return ref.pkind;
  }
}

export function renderPlan(root: HTMLElement, world: World) {
  const main = el('div', { class: 'screen screen--plan' });
  root.append(main);

  const { query } = state;

  // ---- service alerts banner
  renderAlerts(main);

  // ---- main plan card
  const card = el('div', { class: 'card plan-card' });

  const fromLabel = query.from ? query.from.name : t('myLocation');
  const toLabel = query.to ? query.to.name : t('to');

  const fromRow = row(
    ICONS.pin,
    fromLabel,
    query.from ? describe(query.from) : t('useGps'),
    () => openFromPicker(),
    true,
  );
  const toRow = row(ICONS.pin, toLabel, query.to ? describe(query.to) : '', () => openToPicker(), false);
  toRow.classList.add('field-row--to');
  if (!query.to) toRow.querySelector('.field-row__name')?.classList.add('muted');

  const swapBtn = el('button', { class: 'swap-btn', 'aria-label': t('swap') }, svg(ICONS.swap));
  swapBtn.addEventListener('click', () => {
    const f = query.from;
    query.from = query.to;
    query.to = f;
    persistQuery();
    rerender();
  });

  const cardBody = el('div', { class: 'plan-card__body' }, fromRow, toRow, swapBtn);
  card.append(cardBody);

  // ---- when row
  const whenRow = el('div', { class: 'when-row' });

  const timeChip = el('button', { class: 'chip' });
  const dateChip = el('button', { class: 'chip' });
  const setTimeChips = () => {
    timeChip.textContent = query.departMin === null ? `🕐 ${t('now')}` : `🕐 ${fmtMin(query.departMin)}`;
    const today = todayStr();
    dateChip.textContent =
      !query.date || query.date === today
        ? `📅 ${t('today')}`
        : `📅 ${query.date === addDay(today) ? t('tomorrow') : query.date}`;
    timeChip.classList.toggle('chip--active', query.departMin !== null);
    dateChip.classList.toggle('chip--active', !!query.date && query.date !== today);
  };
  setTimeChips();

  const timeInput = el('input', { class: 'input', type: 'time' });
  const dateInput = el('input', { class: 'input', type: 'date' });
  dateInput.min = todayStr();
  dateInput.max = world.meta.horizon.to;
  const customPanel = el(
    'div',
    { class: 'when-panel hidden' },
    el('label', { class: 'when-panel__label' }, t('departAt'), timeInput),
    el('label', { class: 'when-panel__label' }, t('date'), dateInput),
    el('button', {
      class: 'btn btn--ghost btn--small',
      text: t('now'),
      onclick: () => {
        query.departMin = null;
        query.date = null;
        timeInput.value = '';
        dateInput.value = '';
        setTimeChips();
      },
    }),
  );

  timeChip.addEventListener('click', () => {
    customPanel.classList.toggle('hidden');
    if (!customPanel.classList.contains('hidden')) {
      if (!timeInput.value) {
        const n = nowMinutes();
        timeInput.value = fmtMin(Math.min(n + 60, 23 * 60 + 30));
      }
      timeInput.focus();
    }
  });
  dateChip.addEventListener('click', () => {
    customPanel.classList.remove('hidden');
    dateInput.focus();
  });
  const onTimeChange = () => {
    if (timeInput.value) {
      const [h, m] = timeInput.value.split(':').map(Number);
      query.departMin = h * 60 + m;
      if (!query.date) query.date = todayStr();
      if (dateInput.value) query.date = dateInput.value;
    }
    setTimeChips();
    persistQuery();
  };
  timeInput.addEventListener('change', onTimeChange);
  dateInput.addEventListener('change', onTimeChange);

  whenRow.append(timeChip, dateChip);
  card.append(whenRow, customPanel);

  // ---- find button
  const findBtn = el('button', { class: 'btn btn--primary btn--big' }, '🔍 ', t('find'));
  findBtn.addEventListener('click', () => void find());
  card.append(findBtn);

  main.append(card);

  // ---- quick destinations
  const quick = el('div', { class: 'quick-chips' });
  const quickDefs: [string, string][] = [
    ['dworzec', '🚉 Dworzec PKP'],
    ['szpital', '🏥 Szpital'],
    ['targowisko', '🛒 Targowisko'],
  ];
  for (const [needle, label] of quickDefs) {
    const btn = el('button', { class: 'chip chip--soft', text: label });
    btn.addEventListener('click', () => {
      const index = buildIndex(world);
      const hit = searchPlaces(index, needle, state.userPos ?? undefined, 20).find(
        (i) => i.kind === 'stop',
      );
      if (hit) {
        query.to = hit.ref;
        persistQuery();
        rerender();
      }
    });
    quick.append(btn);
  }
  main.append(quick);

  // ---- nearby stops
  const nearBox = el('div', { class: 'near' });
  main.append(nearBox);
  renderNear();

  function renderNear() {
    clear(nearBox);
    if (!state.userPos) {
      const b = el('button', { class: 'btn btn--ghost' }, `📍 ${t('useGps')}`);
      b.addEventListener('click', async () => {
        const pos = await requestGps();
        if (pos) {
          state.userPos = pos;
          renderNear();
        } else toast(t('gpsDenied'));
      });
      nearBox.append(b);
      return;
    }
    const near = nearestStops(world, state.userPos.lat, state.userPos.lng, 5, 1200);
    if (!near.length) return;
    const list = el('div', { class: 'near__list' });
    list.append(el('div', { class: 'near__title', text: t('stopsNearYou') }));
    for (const n of near) {
      const btn = el(
        'button',
        { class: 'near__row' },
        svg(ICONS.bus),
        el('span', { class: 'near__name', text: n.stop.name }),
        el('span', { class: 'near__dist', text: `${fmtMeters(n.meters)} · ${walkMinutes(n.meters)} ${t('min')}` }),
      );
      btn.addEventListener('click', () => {
        location.hash = `#/stop/${n.stop.i}`;
      });
      list.append(btn);
    }
    nearBox.append(list);
  }

  function rerender() {
    clear(main);
    renderPlan(root, world);
  }

  function openFromPicker() {
    openPicker({
      world,
      title: t('from'),
      allowGps: true,
      userPos: state.userPos,
      current: query.from,
      requestGps: async () => {
        const pos = await requestGps();
        if (pos) state.userPos = pos;
        return pos;
      },
      onPick: (ref) => {
        query.from = ref;
        persistQuery();
        rerender();
      },
    });
  }

  function openToPicker() {
    openPicker({
      world,
      title: t('to'),
      allowGps: false,
      userPos: state.userPos,
      current: query.to,
      onPick: (ref) => {
        query.to = ref;
        persistQuery();
        rerender();
      },
    });
  }

  async function find() {
    // resolve origin
    if (!query.from || query.from.kind === 'gps') {
      if (query.from?.kind === 'gps') {
        // keep coords if we have them; otherwise request
      }
      if (!state.userPos) {
        findBtn.textContent = '📍 …';
        const pos = await requestGps();
        if (pos) state.userPos = pos;
      }
      if (state.userPos) {
        query.from = {
          kind: 'gps',
          name: t('myLocation'),
          lat: state.userPos.lat,
          lng: state.userPos.lng,
        };
      } else {
        toast(t('gpsDenied'));
        openFromPicker();
        findBtn.textContent = `🔍 ${t('find')}`;
        return;
      }
    }
    if (!query.to) {
      openToPicker();
      return;
    }
    findBtn.textContent = t('loading');
    try {
      const departMin = query.departMin ?? nowMinutes();
      const outcome = await planJourney(world, {
        origin: query.from,
        dest: query.to,
        date: query.date ?? todayStr(),
        departMin,
      });
      state.results = {
        ...outcome,
        at: Date.now(),
      };
      persistQuery();
      location.hash = '#/results';
    } catch (err) {
      console.error(err);
      toast(t('errorLoad'));
    } finally {
      findBtn.textContent = `🔍 ${t('find')}`;
    }
  }
}

// ---------------------------------------------------------------- helpers

function row(
  icon: string,
  name: string,
  sub: string,
  onclick: () => void,
  isFrom: boolean,
): HTMLElement {
  const btn = el(
    'button',
    { class: 'field-row' },
    el('span', { class: 'field-row__icon' }, svg(icon)),
    el('span', { class: 'field-row__text' },
      el('span', { class: 'field-row__name', text: name }),
      sub ? el('span', { class: 'field-row__sub', text: sub }) : null,
    ),
    el('span', { class: 'field-row__chev' }, svg(ICONS.chevron)),
  );
  btn.addEventListener('click', onclick);
  if (isFrom) btn.classList.add('field-row--from');
  return btn;
}

function renderAlerts(main: HTMLElement) {
  void fetchAnnouncements().then((list) => {
    if (!list.length) return;
    const dismissed = sessionStorage.getItem('alert-dismissed');
    const a = list[0];
    if (dismissed === String(a.id)) return;
    const banner = el('div', { class: 'alert-banner' });
    banner.append(el('div', { class: 'alert-banner__title', text: `📢 ${t('alerts')}` }));
    const body = el('div', { class: 'alert-banner__body' });
    body.append(markdownLite(a.markdown));
    banner.append(body);
    const close = el('button', { class: 'alert-banner__close', 'aria-label': t('close') }, svg(ICONS.close));
    close.addEventListener('click', () => {
      sessionStorage.setItem('alert-dismissed', String(a.id));
      banner.remove();
    });
    banner.append(close);
    main.prepend(banner);
  });
}

function fmtMin(min: number): string {
  const h = Math.floor(min / 60);
  return `${String(h).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;
}

function addDay(dateStr: string): string {
  const d = new Date(dateStr + 'T12:00:00Z');
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

export function langSwitch(): HTMLElement {
  const wrap = el('div', { class: 'lang-switch' });
  for (const l of ['pl', 'en'] as const) {
    const b = el('button', {
      class: `lang-switch__btn${getLang() === l ? ' lang-switch__btn--on' : ''}`,
      text: l.toUpperCase(),
    });
    b.addEventListener('click', () => {
      setLang(l);
      location.reload();
    });
    wrap.append(b);
  }
  return wrap;
}

export { dayTypeLabel };
