/* KiedyDojadę service worker: offline app shell + cached schedule data. */
const VERSION = 'v1';
const SHELL = `${VERSION}-shell`;
const DATA = `${VERSION}-data`;

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(SHELL).then((cache) => cache.addAll(['./', './manifest.webmanifest'])),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys.filter((k) => !k.startsWith(VERSION)).map((k) => caches.delete(k)),
      );
      await self.clients.claim();
    })(),
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  // never touch cross-origin (live API, tiles, …)
  if (url.origin !== location.origin) return;
  if (event.request.method !== 'GET') return;

  // schedule data: serve from cache, refresh in background
  if (url.pathname.includes('/data/')) {
    event.respondWith(
      (async () => {
        const cache = await caches.open(DATA);
        const cached = await cache.match(event.request);
        const network = fetch(event.request)
          .then((res) => {
            if (res.ok) cache.put(event.request, res.clone());
            return res;
          })
          .catch(() => cached);
        return cached ?? network;
      })(),
    );
    return;
  }

  // navigations: network first, shell fallback (offline)
  if (event.request.mode === 'navigate') {
    event.respondWith(
      (async () => {
        try {
          const res = await fetch(event.request);
          const cache = await caches.open(SHELL);
          cache.put('./', res.clone());
          return res;
        } catch {
          const cache = await caches.open(SHELL);
          return (await cache.match('./')) ?? Response.error();
        }
      })(),
    );
    return;
  }

  // static assets: cache-first (hashed by the build)
  event.respondWith(
    (async () => {
      const cache = await caches.open(SHELL);
      const cached = await cache.match(event.request);
      if (cached) return cached;
      const res = await fetch(event.request);
      if (res.ok) cache.put(event.request, res.clone());
      return res;
    })(),
  );
});
