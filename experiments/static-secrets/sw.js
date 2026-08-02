// Service worker: caches the app shell for offline launch.
// Content blobs (fetched via ?url=) are NOT cached by default.

const CACHE = 'static-secrets-shell-v2';
const SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  './vendor/marked.min.js',
  './js/app.js',
  './js/router.js',
  './js/fetcher.js',
  './js/crypto.js',
  './js/format.js',
  './js/keychain.js',
  './js/renderer.js',
  './js/ui.js',
  './js/sw-register.js',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) =>
        // Ignore individual failures (e.g. missing icons in dev).
        Promise.allSettled(SHELL.map((u) => cache.add(u)))
      )
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Only serve same-origin app-shell requests from cache. Cross-origin
  // content blobs always go straight to the network (never cached).
  if (url.origin !== self.location.origin) {
    return; // default network behavior
  }

  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).catch(() => {
        // Offline fallback to the app shell for navigations.
        if (event.request.mode === 'navigate') {
          return caches.match('./index.html');
        }
        return new Response('Offline', { status: 503 });
      });
    })
  );
});
