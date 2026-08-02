// App entry: nav wiring, view orchestration, service worker registration.
import { el, clear } from './ui/components.js';
import { AppState, renderHome, renderProfiling } from './ui/views.js';

const appRoot = document.getElementById('app');
const navRoot = document.getElementById('nav');
const statusEl = document.getElementById('status');

function setStatus(msg, isError) {
  statusEl.textContent = msg;
  statusEl.classList.toggle('error', !!isError);
  if (isError) console.error(msg);
}

const state = new AppState(setStatus);

const VIEWS = {
  home: { label: 'Home', render: renderHome },
  profiling: { label: 'Profiling', render: renderProfiling },
};

let currentView = 'home';

function buildNav() {
  clear(navRoot);
  for (const [key, view] of Object.entries(VIEWS)) {
    const btn = el('button', {
      text: view.label,
      class: key === currentView ? 'active' : '',
    });
    btn.addEventListener('click', () => navigate(key));
    navRoot.appendChild(btn);
  }
}

function navigate(key) {
  if (!VIEWS[key]) return;
  currentView = key;
  buildNav();
  try {
    VIEWS[key].render(appRoot, state);
  } catch (e) {
    setStatus('View error: ' + e.message, true);
  }
}

buildNav();
navigate('home');

// Register service worker (PWA). Ignore failures in dev / non-HTTPS.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('sw.js').catch((e) => {
      console.warn('SW registration failed:', e.message);
    });
  });
}
