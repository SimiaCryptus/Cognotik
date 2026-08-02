# Icons

Add two PNG icons here for the PWA manifest:

- `icon-192.png` — 192×192
- `icon-512.png` — 512×512

They can be simple (e.g. a padlock glyph on the `#1c1e26` background).
Both are referenced with `purpose: "any maskable"` in
`manifest.webmanifest`. The service worker tolerates their absence
during development (cache adds use `Promise.allSettled`).
