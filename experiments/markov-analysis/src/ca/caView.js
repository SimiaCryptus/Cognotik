// caView.js — renders CA generations as a spacetime diagram

import { el, clear } from '../util/dom.js';
import { colorFor } from '../util/colorScale.js';

// A row = tape at generation t. Cells colored by mode:
//  'change'    — flipped this step (highlighted) vs stable
//  'surprise'  — probability under model (needs perCellP)
//  'stability' — steps since last change (needs perCellAge)
export function createCaView(container, opts = {}) {
  const cfg = opts.config || {};
  let colorMode = opts.colorMode || 'change';

  container.appendChild(el('h2', { text: 'Spacetime' }));
  const controlsRow = el('div', { class: 'ca-view-controls' });
  const modeSelect = el('select', {}, [
    el('option', { value: 'change', text: 'Color: change' }),
    el('option', { value: 'surprise', text: 'Color: surprise' }),
    el('option', { value: 'stability', text: 'Color: stability' }),
  ]);
  modeSelect.value = colorMode;
  modeSelect.addEventListener('change', () => {
    colorMode = modeSelect.value;
    rerender();
  });
  controlsRow.appendChild(modeSelect);
  container.appendChild(controlsRow);

  const grid = el('div', { class: 'ca-spacetime' });
  container.appendChild(grid);
  // Zoom toggle: expand the spacetime grid to its full (unbounded) size.
  let zoomed = false;
  const zoomBtn = el('button', {
    type: 'button',
    text: 'Zoom in',
    title: 'Expand the spacetime view to its full size',
  });
  zoomBtn.addEventListener('click', () => {
    zoomed = !zoomed;
    grid.classList.toggle('zoomed', zoomed);
    zoomBtn.textContent = zoomed ? 'Zoom out' : 'Zoom in';
  });
  controlsRow.appendChild(zoomBtn);

  // rows: [{ tape, changed, perCellP?, age? }]
  let rows = [];
  let onCellClick = opts.onCellClick || null;

  function cellColor(row, i) {
    if (colorMode === 'surprise' && row.perCellP) {
      return colorFor(row.perCellP[i], cfg);
    }
    if (colorMode === 'stability' && row.age) {
      const a = Math.min(1, row.age[i] / 10);
      const g = Math.round(200 * a);
      return `rgba(80,${120 + g / 2},${200 * a},0.6)`;
    }
    // change mode (default)
    return row.changed && row.changed[i] ? 'rgba(255,90,60,0.85)' : 'rgba(0,0,0,0)';
  }

  function renderRow(row, t) {
    const rowEl = el('div', { class: 'ca-row' });
    for (let i = 0; i < row.tape.length; i++) {
      const raw = row.tape[i];
      // Render newlines/whitespace-with-newlines as a visible placeholder so
      // the spacetime grid stays a single-spaced compact grid.
      const display = typeof raw === 'string' ? raw.replace(/\r/g, '').replace(/\n/g, '\\n') : raw;
      const chip = el('span', {
        class: 'ca-cell',
        style: `background:${cellColor(row, i)}`,
        title: `gen ${t}, pos ${i}: ${JSON.stringify(raw)}`,
        text: display,
      });
      if (onCellClick) {
        chip.addEventListener('click', () => onCellClick(t, i, row));
      }
      rowEl.appendChild(chip);
    }
    return rowEl;
  }

  function rerender() {
    clear(grid);
    const frag = document.createDocumentFragment();
    rows.forEach((row, t) => frag.appendChild(renderRow(row, t)));
    grid.appendChild(frag);
  }

  return {
    setRows(newRows) {
      rows = newRows;
      rerender();
    },
    appendRow(row) {
      const nearBottom = grid.scrollTop + grid.clientHeight >= grid.scrollHeight - 4;
      const pageNearBottom = window.innerHeight + window.scrollY >= document.body.scrollHeight - 4;
      rows.push(row);
      grid.appendChild(renderRow(row, rows.length - 1));
      // Follow the simulation only if the user was already at the bottom.
      if (nearBottom) grid.scrollTop = grid.scrollHeight;
      // If the whole page was scrolled to the bottom, keep it pinned there so
      // the newest generation stays visible while the CA is playing.
      if (pageNearBottom) {
        window.scrollTo(0, document.body.scrollHeight);
      }
    },
    clear() {
      rows = [];
      clear(grid);
    },
    getRows: () => rows,
  };
}
