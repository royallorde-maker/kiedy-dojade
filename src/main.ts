import './styles.css';
import { loadWorld } from './data';
import { getLang, setLang, t } from './i18n';
import { restoreQuery } from './state';
import { runCleanup } from './cleanup';
import { el, clear, ICONS, svg, toast } from './ui';
import { renderPlan } from './screens/plan';
import { renderResults } from './screens/results';
import { renderStop } from './screens/stop';
import { renderSettings } from './screens/settings';
import type { World } from './types';

const app = document.getElementById('app')!;

setLang(getLang());
restoreQuery();

const screenEl = el('div', { id: 'screen' });

function buildShell(): void {
  clear(app);
  const brand = el('button', { class: 'app-brand' },
    el('span', { class: 'app-brand__dot' }),
    el('span', { class: 'app-brand__name', text: t('appName') }),
    el('span', { class: 'app-brand__city', text: 'Szczecinek' }),
  );
  brand.addEventListener('click', () => {
    location.hash = '#/';
  });

  const gear = el('button', { class: 'icon-btn', 'aria-label': t('settings') }, svg(ICONS.gear));
  gear.addEventListener('click', () => {
    location.hash = '#/settings';
  });

  const header = el('header', { class: 'app-header' },
    el('div', { class: 'app-header__inner' }, brand, gear),
  );

  app.append(header, screenEl);
}

async function route(world: World): Promise<void> {
  runCleanup();
  clear(screenEl);
  const hash = location.hash || '#/';
  window.scrollTo({ top: 0 });
  if (hash.startsWith('#/stop/')) {
    const idx = Number(hash.slice('#/stop/'.length));
    renderStop(screenEl, world, idx);
  } else if (hash.startsWith('#/results')) {
    renderResults(screenEl, world);
  } else if (hash.startsWith('#/settings')) {
    renderSettings(screenEl, world);
  } else {
    renderPlan(screenEl, world);
  }
}

async function boot(): Promise<void> {
  buildShell();
  screenEl.append(el('div', { class: 'boot', text: t('loading') }));
  try {
    const world = await loadWorld();
    window.addEventListener('hashchange', () => void route(world));
    await route(world);
  } catch (err) {
    console.error(err);
    clear(screenEl);
    screenEl.append(
      el('div', { class: 'empty-box' },
        el('div', { class: 'empty-box__title', text: t('errorLoad') }),
        el('button', {
          class: 'btn btn--primary',
          text: '↻',
          onclick: () => location.reload(),
        }),
      ),
    );
  }
}

void boot();

// service worker (offline shell + cached schedule data)
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register(import.meta.env.BASE_URL + 'sw.js')
      .catch((e) => console.warn('SW registration failed', e));
  });
}

// small convenience: toast when the app gains an update
navigator.serviceWorker?.addEventListener?.('controllerchange', () => {
  toast('Aktualizacja gotowa — odśwież, aby użyć.', 4000);
});
