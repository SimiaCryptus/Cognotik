// Service worker for offline PWA support.
const CACHE_NAME = 'laser-cam-v1';
const CORE_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './styles/app.css',
  './src/main.js',
  './src/camera/deviceManager.js',
  './src/camera/capture.js',
  './src/profile/profileModel.js',
  './src/analysis/accumulator.js',
  './src/analysis/defectDetector.js',
  './src/analysis/defectMap.js',
  './src/ui/components.js',
  './src/ui/views.js',
  './src/util/imageData.js',
  './src/util/math.js',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) =>
        // Use addAll but tolerate failures (e.g. missing icons) gracefully.
        Promise.allSettled(CORE_ASSETS.map((url) => cache.add(url)))
      )
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request)
        .then((response) => {
          // Cache same-origin GET responses opportunistically.
          if (response.ok && new URL(request.url).origin === self.location.origin) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, clone));
          }
          return response;
        })
        .catch(() => cached);
    })
  );
});
