// Minimal offline-first service worker for the app shell.

const CACHE = 'mws-v1';
const ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './src/index.js',
  './src/ui/app.js',
  './src/ui/render.js',
  './src/ui/controls.js',
  './src/ui/remoteText.js',
  './src/ui/presets.js',
  './src/ui/urlState.js',
  './src/ui/watchMode.js',
  './src/ui/playMode.js',
  './src/ui/collapseMode.js',
  './src/ui/sidebarResize.js',
  './src/generator.js',
  './src/grid/Grid.js',
  './src/grid/directions.js',
  './src/grid/placement.js',
  './src/grid/wordlist.js',
  './src/fill/adjacency.js',
  './src/fill/combiners.js',
  './src/fill/filler.js',
  './src/markov/MarkovModel.js',
  './src/markov/textPipeline.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then(async (c) => {
        // Cache individually so a single failed asset doesn't abort install.
        await Promise.all(
          ASSETS.map((url) =>
            c.add(url).catch((err) => {
              console.warn('[sw] Failed to cache:', url, err);
            })
          )
        );
      })
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
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request).then(
      (cached) =>
        cached ||
        fetch(event.request)
          .then((resp) => {
            const copy = resp.clone();
            caches.open(CACHE).then((c) => c.put(event.request, copy));
            return resp;
          })
          .catch(() => cached)
    )
  );
});
