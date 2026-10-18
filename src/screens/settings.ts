/** Settings / about screen. */

import { getLang, setLang, stopsLabel, t } from '../i18n';
import type { World } from '../types';
import { ICONS, el, svg } from '../ui';

export function renderSettings(root: HTMLElement, world: World) {
  const main = el('div', { class: 'screen screen--settings' });

  const back = el('button', { class: 'icon-btn', 'aria-label': t('back') }, svg(ICONS.back));
  back.addEventListener('click', () => history.back());
  main.append(el('div', { class: 'settings-head' }, back, el('h1', { text: t('settings') })));

  // language
  const langBox = el('div', { class: 'card settings-card' });
  langBox.append(el('div', { class: 'settings-card__title', text: `🌐 ${t('language')}` }));
  const langRow = el('div', { class: 'chips' });
  for (const l of ['pl', 'en'] as const) {
    const b = el('button', {
      class: `chip${getLang() === l ? ' chip--active' : ''}`,
      text: l === 'pl' ? 'Polski' : 'English',
    });
    b.addEventListener('click', () => {
      setLang(l);
      location.reload();
    });
    langRow.append(b);
  }
  langBox.append(langRow);
  main.append(langBox);

  // install hint
  const installBox = el('div', { class: 'card settings-card' });
  installBox.append(
    el('div', { class: 'settings-card__title', text: `📱 ${t('install')}` }),
    el('div', { class: 'settings-card__text', text: t('installHint') }),
  );
  main.append(installBox);

  // data info
  const gen = new Date(world.meta.generatedAt);
  const dataBox = el('div', { class: 'card settings-card' });
  dataBox.append(
    el('div', { class: 'settings-card__title', text: `🗂 ${t('source')}` }),
    el('div', { class: 'settings-card__text' },
      el('div', { text: `${t('dataUpdated')}: ${gen.toLocaleString(getLang() === 'pl' ? 'pl-PL' : 'en-GB', { timeZone: 'Europe/Warsaw' })}` }),
      el('div', { text: `${stopsLabel(world.meta.counts.stops)} · ${world.meta.counts.trips ?? '?'} ${t('tripsCount')} · ${Object.keys(world.meta.patterns).length} ${t('scheduleVariants')}` }),
      el('div', { text: `${t('scheduleDate')}: ${world.meta.horizon.from} → ${world.meta.horizon.to}` }),
    ),
    el('div', { class: 'settings-card__text settings-card__small' }, t('dataFrom')),
  );
  main.append(dataBox);

  const links = el('div', { class: 'card settings-card' });
  links.append(
    el('a', { class: 'settings-link', href: 'https://szczecinek.kiedyprzyjedzie.pl', target: '_blank', rel: 'noopener', text: '🔗 kiedyprzyjedzie.pl — oficjalna tablica odjazdów' }),
    el('a', { class: 'settings-link', href: 'https://km.szczecinek.pl', target: '_blank', rel: 'noopener', text: '🔗 km.szczecinek.pl — Komunikacja Miejska Szczecinek' }),
    el('a', { class: 'settings-link', href: 'https://github.com/royallorde-maker/kiedy-dojade', target: '_blank', rel: 'noopener', text: '🔗 GitHub — kod źródłowy' }),
  );
  main.append(links);

  root.append(main);
}
