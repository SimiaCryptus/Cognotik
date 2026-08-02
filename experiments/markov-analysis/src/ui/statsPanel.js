// statsPanel.js — overall match statistics for the current analysis

import { el, clear, formatPct } from '../util/dom.js';
import { summarizeResults } from '../model/scoring.js';

export function createStatsPanel(container) {
  container.appendChild(el('h2', { text: 'Match Statistics' }));
  const body = el('div', { class: 'stats-body' });
  container.appendChild(body);

  function row(label, value) {
    return el('div', { class: 'stats-row' }, [
      el('span', { class: 'stats-label', text: label }),
      el('span', { class: 'stats-value', text: value }),
    ]);
  }

  function render(results) {
    clear(body);
    if (!results || !results.length) {
      body.appendChild(el('div', { class: 'stats-empty', text: 'No analysis yet.' }));
      return;
    }
    const s = summarizeResults(results);
    body.appendChild(row('Tokens analyzed', String(s.count)));
    body.appendChild(row('Top-1 match rate', formatPct(s.top1)));
    body.appendChild(row('Top-N match rate', formatPct(s.topN)));
    body.appendChild(row('Mean probability', formatPct(s.meanP)));
    body.appendChild(row('Geometric mean', formatPct(s.geoMeanP)));
    body.appendChild(row('Perplexity', s.perplexity.toPrecision(4)));
  }

  render(null);

  return { render };
}
