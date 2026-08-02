// Play mode: solve the generated wordsearch with timer + selection.

import { renderInteractiveGrid, cellAt } from './render.js';
import { latticeDirections, step } from '../grid/directions.js';
import { getExternalWordList, cleanWordList } from '../grid/wordlist.js';

let state = null;

const MIN_BONUS_LEN = 4;

function key(x, y) {
  return `${x},${y}`;
}

function formatTime(ms) {
  const s = Math.floor(ms / 1000);
  const mm = String(Math.floor(s / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

function elapsed() {
  if (!state) return 0;
  // accumulatedMs holds time banked before the current running segment.
  if (state.paused) return state.accumulatedMs;
  return state.accumulatedMs + (Date.now() - state.segmentStart);
}

function updateTimer(root) {
  if (!state) return;
  const el = root.querySelector('#play-timer');
  if (el) el.textContent = formatTime(elapsed());
}

function updateWordList(root) {
  const list = root.querySelector('#play-words');
  if (!list) return;
  list.innerHTML = '';
  for (const p of state.placements) {
    const li = document.createElement('li');
    li.textContent = p.word.toUpperCase();
    if (state.found.has(p.word)) li.classList.add('found');
    list.appendChild(li);
  }
}

function updateBonusList(root) {
  const wrap = root.querySelector('#play-bonus-wrap');
  const list = root.querySelector('#play-bonus');
  if (!list) return;
  list.innerHTML = '';
  if (state.bonus.size === 0) {
    if (wrap) wrap.hidden = true;
    return;
  }
  if (wrap) wrap.hidden = false;
  for (const w of state.bonus) {
    const li = document.createElement('li');
    li.textContent = w.toUpperCase();
    li.classList.add('found', 'bonus');
    list.appendChild(li);
  }
}

function lineCells(a, b) {
  // Returns the straight-line set of cells from a to b along a valid lattice
  // direction, else null. For hex/triangular lattices the per-step offsets
  // depend on row parity, so we walk each direction using the lattice-aware
  // `step()` helper rather than assuming constant dx/dy.
  const lattice = (state && state.lattice) || 'square';
  if (a.x === b.x && a.y === b.y) return [{ x: a.x, y: a.y }];
  // Maximum possible run length on this grid.
  const maxLen = Math.max(state.grid.width, state.grid.height);
  // Direction names available on this lattice (row parity doesn't change the
  // set of names, only the vectors, so any row works for enumeration).
  const dirs = latticeDirections(lattice, a.y, { includeBackwards: true });
  for (const dir of dirs) {
    const cells = [{ x: a.x, y: a.y }];
    let cx = a.x;
    let cy = a.y;
    for (let i = 0; i < maxLen; i++) {
      const next = step(lattice, cx, cy, dir.name, 1);
      if (!next) break;
      cx = next.x;
      cy = next.y;
      if (!state.grid.inBounds(cx, cy)) break;
      cells.push({ x: cx, y: cy });
      if (cx === b.x && cy === b.y) return cells;
    }
  }
  return null;
}

function readSelection(cells) {
  return cells.map((c) => state.grid.get(c.x, c.y) || '').join('');
}

function clearSelectionClasses() {
  for (const td of state.table.querySelectorAll('td.selecting')) {
    td.classList.remove('selecting');
  }
}
function flashWrong(cells) {
  for (const c of cells) {
    const td = cellAt(state.table, c.x, c.y);
    if (td) {
      td.classList.add('wrong-flash');
      setTimeout(() => td.classList.remove('wrong-flash'), 400);
    }
  }
}
function launchConfetti(root) {
  const host = root.querySelector('#grid') || document.body;
  const colors = ['#7c5cff', '#36d1ff', '#ff5cae', '#ff9d5c', '#38e8a0', '#ffd166'];
  const layer = document.createElement('div');
  layer.className = 'confetti-layer';
  for (let i = 0; i < 80; i++) {
    const bit = document.createElement('span');
    bit.className = 'confetti';
    bit.style.left = `${Math.random() * 100}%`;
    bit.style.background = colors[Math.floor(Math.random() * colors.length)];
    bit.style.animationDelay = `${Math.random() * 0.4}s`;
    bit.style.animationDuration = `${1.6 + Math.random() * 1.4}s`;
    bit.style.transform = `rotate(${Math.random() * 360}deg)`;
    layer.appendChild(bit);
  }
  host.appendChild(layer);
  setTimeout(() => layer.remove(), 3200);
}

function markFound(cells, cls = 'found-cell') {
  for (const c of cells) {
    const td = cellAt(state.table, c.x, c.y);
    if (td) td.classList.add(cls);
  }
}
/**
 * Show or hide an anti-cheating overlay that obscures the grid while the
 * game is paused, so players can't study the board with the clock stopped.
 * @param {Document|HTMLElement} root
 * @param {boolean} show
 */
function setPauseOverlay(root, show) {
  const container = root.querySelector('#grid');
  if (!container) return;
  let overlay = container.querySelector('.pause-overlay');
  if (show) {
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.className = 'pause-overlay';
      overlay.innerHTML =
        '<div class="pause-overlay-inner">' +
        '<span class="pause-icon">⏸</span>' +
        '<strong>Paused</strong>' +
        '<span class="pause-sub">Press Resume to keep playing</span>' +
        '</div>';
      container.appendChild(overlay);
    }
    overlay.hidden = false;
  } else if (overlay) {
    overlay.remove();
  }
}

/**
 * Does the candidate selection lie INLINE and OVERLAPPING with one of the
 * placed target words? Bonus words are only granted when they do NOT sit
 * along the same line as (and sharing cells with) an expected placement.
 *
 * A selection is considered "inline overlapping" with a placement when the
 * placement's cell set is a contiguous subset of the selection's cells (or
 * vice versa) — i.e. they run along the same straight line and share cells.
 * Merely crossing a target word at a single intersection point is allowed.
 * @param {Array<{x:number,y:number}>} cells
 */
function overlapsPlacement(cells) {
  const sel = new Set(cells.map((c) => key(c.x, c.y)));
  for (const p of state.placements) {
    const coords = p.coords || [];
    if (!coords.length) continue;
    // Count how many of the placement's cells are part of the selection.
    let shared = 0;
    for (const c of coords) {
      if (sel.has(key(c.x, c.y))) shared += 1;
    }
    // If the selection shares more than a single cell with a placed word,
    // it's running inline/along that word — disallow the bonus. A lone
    // crossing (one shared cell) is fine.
    if (shared > 1) return true;
  }
  return false;
}

function tryMatch(cells) {
  const word = readSelection(cells);
  const rev = [...word].reverse().join('');
  // First, check the expected target words.
  for (const p of state.placements) {
    if (state.found.has(p.word)) continue;
    if (p.word === word || p.word === rev) {
      state.found.add(p.word);
      markFound(cells);
      return { type: 'target', word: p.word };
    }
  }
  // Otherwise, see if the selection forms a real dictionary word. Grant a
  // bonus only when the selection does NOT lie inline/overlapping with an
  // expected placement (so players can't re-claim inline letters of an
  // existing word). A single crossing intersection is allowed.
  if (word.length >= MIN_BONUS_LEN && cells.length >= MIN_BONUS_LEN && !overlapsPlacement(cells)) {
    const candidate = state.dict.has(word) ? word : state.dict.has(rev) ? rev : null;
    if (candidate && !state.bonus.has(candidate)) {
      state.bonus.add(candidate);
      markFound(cells, 'bonus-cell');
      return { type: 'bonus', word: candidate };
    }
  }
  return null;
}

function finishIfDone(root) {
  if (state.found.size === state.placements.length && state.placements.length) {
    clearInterval(state.tick);
    const status = root.querySelector('#play-status');
    if (status) {
      const bonusNote = state.bonus.size ? ` (+${state.bonus.size} bonus)` : '';
      status.textContent = `🎉 Solved in ${formatTime(elapsed())}${bonusNote}! 🎉`;
      status.classList.add('win');
    }
    launchConfetti(root);
  }
}

export function initPlay(root, grid, placement, cfg = {}) {
  if (state && state.tick) clearInterval(state.tick);
  const container = root.querySelector('#grid');
  for (const c of container.querySelectorAll('.confetti-layer')) c.remove();
  // Remove any leftover pause overlay from a previous game.
  setPauseOverlay(root, false);
  const prevStatus = root.querySelector('#play-status');
  if (prevStatus) {
    prevStatus.classList.remove('win');
    prevStatus.textContent = '';
  }
  // Reset the pause button label.
  const pauseBtn = root.querySelector('#btn-play-pause');
  if (pauseBtn) pauseBtn.textContent = 'Pause';

  const table = renderInteractiveGrid(container, grid, {
    lattice: grid.lattice || 'square',
    fontScale: cfg.fontScale,
    fontFamily: cfg.fontFamily,
  });

  // Build the bonus dictionary: external wordlist + the target words
  // themselves (so reversed/alternate finds of targets aren't mis-flagged).
  const dictWords = cleanWordList([
    ...getExternalWordList(),
    ...(placement.placed || []).map((p) => p.word),
  ]);
  const dict = new Set(dictWords.filter((w) => w.length >= MIN_BONUS_LEN));

  state = {
    grid,
    table,
    lattice: grid.lattice || 'square',
    placements: placement.placed.slice(),
    found: new Set(),
    bonus: new Set(),
    dict,
    anchor: null,
    tick: null,
    paused: false,
    accumulatedMs: 0,
    segmentStart: Date.now(),
  };

  state.tick = setInterval(() => updateTimer(root), 250);
  updateTimer(root);
  updateWordList(root);
  updateBonusList(root);

  const onCell = (td) => {
    if (state.paused) return;
    const x = parseInt(td.dataset.x, 10);
    const y = parseInt(td.dataset.y, 10);
    if (!state.anchor) {
      state.anchor = { x, y };
      clearSelectionClasses();
      td.classList.add('selecting');
      return;
    }
    const cells = lineCells(state.anchor, { x, y });
    state.anchor = null;
    clearSelectionClasses();
    if (!cells) return;
    const matched = tryMatch(cells);
    const status = root.querySelector('#play-status');
    if (matched && matched.type === 'target') {
      if (status) status.textContent = `✨ Found ${matched.word.toUpperCase()}!`;
    } else if (matched && matched.type === 'bonus') {
      if (status) status.textContent = `🌟 Bonus word: ${matched.word.toUpperCase()}!`;
    } else {
      if (status) status.textContent = `Not quite — keep looking!`;
      flashWrong(cells);
    }
    updateWordList(root);
    updateBonusList(root);
    finishIfDone(root);
  };

  table.addEventListener('click', (e) => {
    const td = e.target.closest('td');
    if (td) onCell(td);
  });
}

/**
 * Toggle pause state for the active game. Pausing banks the elapsed time and
 * stops the ticking clock; resuming restarts the segment timer.
 * @returns {boolean} the new paused state
 */
export function togglePausePlay() {
  if (!state) return false;
  const root = (state.table && state.table.ownerDocument) || document;
  if (state.paused) {
    // Resume.
    state.paused = false;
    state.segmentStart = Date.now();
    state.anchor = null;
    setPauseOverlay(root, false);
  } else {
    // Pause: bank elapsed time.
    state.accumulatedMs += Date.now() - state.segmentStart;
    state.paused = true;
    state.anchor = null;
    if (state.table) {
      for (const td of state.table.querySelectorAll('td.selecting')) {
        td.classList.remove('selecting');
      }
    }
    setPauseOverlay(root, true);
  }
  return state.paused;
}

export function stopPlay() {
  if (state && state.table) {
    const root = state.table.ownerDocument || document;
    setPauseOverlay(root, false);
  }
  if (state && state.tick) clearInterval(state.tick);
  state = null;
}
