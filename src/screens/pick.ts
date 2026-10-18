/** Full-screen place picker: search stops, streets and places. */

import { nearestStops, searchPlaces, buildIndex } from '../search';
import { favorites, isFavorite, pushRecent, recents, toggleFavorite, type SavedPlace } from '../state';
import { t } from '../i18n';
import type { PlaceRef, World } from '../types';
import { ICONS, clear, el, svg, toast } from '../ui';
import { fmtMeters, walkMinutes } from '../geo';

export interface PickerOptions {
  world: World;
  title: string;
  /** offer "my location" as first entry (origin picker) */
  allowGps?: boolean;
  userPos?: { lat: number; lng: number } | null;
  current?: PlaceRef | null;
  onPick: (ref: PlaceRef | null) => void;
  /** request GPS; resolves to coordinates or null */
  requestGps?: () => Promise<{ lat: number; lng: number } | null>;
}

export function openPicker(opts: PickerOptions) {
  const index = buildIndex(opts.world);

  const overlay = el('div', { class: 'picker' });
  const input = el('input', {
    class: 'picker__input',
    type: 'search',
    placeholder: t('searchPlaceholder'),
    autocomplete: 'off',
    autocapitalize: 'off',
  });
  const list = el('div', { class: 'picker__list' });

  const header = el(
    'div',
    { class: 'picker__header' },
    el('button', { class: 'icon-btn', 'aria-label': t('close'), onclick: close }, svg(ICONS.close)),
    el('div', { class: 'picker__title', text: opts.title }),
  );

  overlay.append(header, el('div', { class: 'picker__search' }, input), list);
  document.body.append(overlay);
  overlay.classList.add('picker--open');
  setTimeout(() => input.focus(), 30);

  function close() {
    overlay.remove();
  }

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') close();
  });
  input.addEventListener('input', () => render());

  function gpsRow(): HTMLElement {
    return el(
      'button',
      { class: 'pick-row pick-row--gps' },
      el('span', { class: 'pick-row__icon' }, svg(ICONS.pin)),
      el('span', { class: 'pick-row__body' },
        el('span', { class: 'pick-row__name', text: t('myLocation') }),
        el('span', { class: 'pick-row__sub', text: t('useGps') }),
      ),
    );
  }

  function row(item: {
    name: string;
    sub?: string;
    kind: string;
    ref: PlaceRef;
    dist?: number;
  }): HTMLElement {
    const starBtn = el('button', {
      class: 'pick-row__star' + (isFavorite(item.ref) ? ' pick-row__star--on' : ''),
      'aria-label': t('save'),
    });
    starBtn.append(svg(ICONS.star));
    starBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const added = toggleFavorite({ name: item.name, ref: item.ref, kind: item.kind });
      starBtn.classList.toggle('pick-row__star--on', added);
      starBtn.title = added ? t('saved') : t('remove');
    });

    const btn = el(
      'button',
      { class: 'pick-row' },
      el('span', { class: `pick-row__icon pick-row__icon--${item.kind}` },
        svg(item.kind === 'stop' ? ICONS.bus : ICONS.pin)),
      el('span', { class: 'pick-row__body' },
        el('span', { class: 'pick-row__name', text: item.name }),
        item.sub ? el('span', { class: 'pick-row__sub', text: item.sub }) : null,
      ),
      item.dist !== undefined
        ? el('span', { class: 'pick-row__dist', text: `${fmtMeters(item.dist)} · ${walkMinutes(item.dist)} min` })
        : null,
      starBtn,
    );
    btn.addEventListener('click', () => {
      if (item.kind !== 'gps') pushRecent({ name: item.name, ref: item.ref, kind: item.kind });
      close();
      opts.onPick(item.ref);
    });
    return btn;
  }

  function section(title: string, nodes: (Node | null)[]) {
    const real = nodes.filter(Boolean) as Node[];
    if (!real.length) return null;
    return el('div', { class: 'picker__section' },
      el('div', { class: 'picker__section-title', text: title }),
      ...real,
    );
  }

  function render() {
    clear(list);
    const q = input.value.trim();

    if (q.length >= 2) {
      const res = searchPlaces(index, q, opts.userPos ?? undefined);
      if (!res.length) {
        list.append(el('div', { class: 'picker__empty', text: t('noResults') }));
      }
      for (const r of res) {
        const dist = opts.userPos
          ? Math.round(
              Math.min(
                ...(r.kind === 'street'
                  ? (r.ref as { pts: [number, number][] }).pts.map((p: [number, number]) =>
                      Math.hypot((p[0] - opts.userPos!.lat) * 111320, (p[1] - opts.userPos!.lng) * 66000),
                    )
                  : [Math.hypot((r.lat - opts.userPos.lat) * 111320, (r.lng - opts.userPos.lng) * 66000)]),
              ),
            )
          : undefined;
        list.append(
          row({
            name: r.name,
            sub:
              r.kind === 'stop'
                ? `${t('stop')} ${r.code}${r.onDemand ? ' · NŻ' : ''}`
                : r.kind === 'street'
                  ? t('street')
                  : r.pkind,
            kind: r.kind,
            ref: r.ref,
            dist,
          }),
        );
      }
      return;
    }

    // empty query: gps, favorites, recents, nearest stops
    const frag: (Node | null)[] = [];
    if (opts.allowGps) {
      const g = gpsRow();
      g.addEventListener('click', async () => {
        const pos = await opts.requestGps?.();
        if (!pos) {
          toast(t('gpsDenied'));
          return;
        }
        close();
        opts.onPick({ kind: 'gps', name: t('myLocation'), lat: pos.lat, lng: pos.lng });
      });
      frag.push(g);
    }
    frag.push(
      section(
        t('favorites'),
        favorites().map((f: SavedPlace) =>
          row({ name: f.name, kind: f.kind, ref: f.ref, sub: undefined }),
        ),
      ),
      section(
        t('recent'),
        recents().map((f: SavedPlace) => row({ name: f.name, kind: f.kind, ref: f.ref })),
      ),
    );
    if (opts.userPos) {
      const near = nearestStops(opts.world, opts.userPos.lat, opts.userPos.lng, 6, 1200);
      frag.push(
        section(
          t('stopsNearYou'),
          near.map((n) =>
            row({
              name: n.stop.name,
              sub: `${t('stop')} ${n.stop.code}${n.stop.onDemand ? ' · NŻ' : ''}`,
              kind: 'stop',
              ref: { kind: 'stop', name: n.stop.name, stop: n.stop.i },
              dist: n.meters,
            }),
          ),
        ),
      );
    } else {
      const near = [...opts.world.stops]
        .filter((s) => !s.onlyDisembarking)
        .sort((a, b) => a.name.localeCompare(b.name, 'pl'))
        .slice(0, 40);
      frag.push(
        section(
          t('stops'),
          near.map((s) =>
            row({
              name: s.name,
              sub: `${t('stop')} ${s.code}${s.onDemand ? ' · NŻ' : ''}`,
              kind: 'stop',
              ref: { kind: 'stop', name: s.name, stop: s.i },
            }),
          ),
        ),
      );
    }
    for (const f of frag) if (f) list.append(f);
  }

  render();
}
