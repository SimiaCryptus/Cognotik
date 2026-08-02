// Example of how to wire the live font controls in your app setup.
// Import alongside your existing imports:
import { readConfig, wireFileUpload, wireLiveFontControls } from './ui/controls.js';
import { initApp } from './ui/app.js';

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('./src/pwa/sw.js')
      .catch((e) => console.warn('SW registration failed', e));
  });
}

document.addEventListener('DOMContentLoaded', () => initApp(document));

// After you grab your grid container element, e.g.:
const gridContainer = document.querySelector('#grid');

// Wire the file upload (existing) and the new live font controls:
wireFileUpload();
wireLiveFontControls(gridContainer);

// Note: wireLiveFontControls only needs to be called ONCE at startup.
// It mutates CSS variables on whatever table currently exists in the
// container, so it keeps working across re-renders/regenerations.
