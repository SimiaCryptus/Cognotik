// Entry point: wire global controls + story.
import { GlobalControls } from './controls.js';
import { buildSections } from './sections.js';
import { Story } from './story.js';

function boot() {
  const global = new GlobalControls();
  const sections = buildSections(global);
  const story = new Story(document.getElementById('story'), sections, global);

  // Resize handling: nudge sections to re-observe/redraw on resize.
  let rt = null;
  window.addEventListener('resize', () => {
    clearTimeout(rt);
    rt = setTimeout(() => {
      // force remount of currently-visible sections by dispatching a layers event
      global.dispatchEvent(new CustomEvent('layers', { detail: { layers: global.layers } }));
    }, 200);
  });

  // Expose for debugging / export hooks
  window.__qqn = { global, story, sections };
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot);
} else {
  boot();
}
