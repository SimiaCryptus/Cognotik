// analysisView.js — heatmap rendering + spans

import { el, clear } from '../util/dom.js';
import { colorFor, legendStops } from '../util/colorScale.js';
import { showPopup } from './popup.js';
import { formatPct } from '../util/dom.js';

export function renderLegend(container, cfg) {
  clear(container);
  const stops = legendStops(cfg);
  const gradient = `linear-gradient(to right, ${stops.join(',')})`;
  container.appendChild(el('span', { text: 'calm' }));
  container.appendChild(
    el('span', {
      class: 'legend-bar',
      style: `background:${gradient}`,
    })
  );
  container.appendChild(el('span', { text: 'surprising' }));
}

// opts.onReplace(result, newToken) — optional callback to edit test text.
export function renderAnalysis(container, results, cfg, opts = {}) {
  clear(container);
  const frag = document.createDocumentFragment();
  for (const r of results) {
    const color = colorFor(r.pCombined, cfg);
    const span = el('span', {
      class: 'tok',
      style: `background:${color}`,
      role: 'button',
      tabindex: '0',
      title: `p=${formatPct(r.pCombined)} (fwd=${formatPct(r.pForward)}, bwd=${formatPct(r.pBackward)})`,
      text: r.token,
    });
    const onReplace = opts.onReplace ? (newToken) => opts.onReplace(r, newToken) : undefined;
    const open = () => showPopup(span, r, onReplace);
    span.addEventListener('click', open);
    span.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        open();
      }
    });
    frag.appendChild(span);
  }
  container.appendChild(frag);
}
