// Collapse mode: find ANY word from the global wordlist (or a run of 3+
// identical/sequential letters) in the grid. Matched tiles are removed, the
// tiles above drop down, and the vacated top cells are refilled using the
// same Markov fill algorithm.

import { renderInteractiveGrid, cellAt } from './render.js';
import { latticeDirections, step, readContext, readLineAround } from '../grid/directions.js';
import { getExternalWordList, cleanWordList, buildForbiddenIndex } from '../grid/wordlist.js';
import { combine } from '../fill/combiners.js';
import { select } from '../fill/filler.js';

let state = null;

const MIN_WORD_LEN = 3;
const MIN_RUN_LEN = 3;

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
  if (state.paused) return state.accumulatedMs;
  return state.accumulatedMs + (Date.now() - state.segmentStart);
}

function updateTimer(root) {
  if (!state) return;
  const el = root.querySelector('#collapse-timer');
  if (el) el.textContent = formatTime(elapsed());
}

function updateScore(root) {
  if (!state) return;
  const el = root.querySelector('#collapse-score');
  if (el) el.textContent = String(state.score);
}

function updateFoundList(root) {
  const list = root.querySelector('#collapse-found');
  if (!list) return;
  list.innerHTML = '';
  for (const entry of state.foundList) {
    const li = document.createElement('li');
    li.textContent = `${entry.word.toUpperCase()} (+${entry.points})`;
    li.classList.add('found');
    if (entry.type === 'run') li.classList.add('bonus');
    list.appendChild(li);
  }
}

function lineCells(a, b) {
  const lattice = (state && state.lattice) || 'square';
  if (a.x === b.x && a.y === b.y) return [{ x: a.x, y: a.y }];
  const maxLen = Math.max(state.grid.width, state.grid.height);
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

/**
 * Is the selection a run of 3+ identical letters or a 3+ consecutive
 * alphabetical sequence (forwards or backwards)? Returns a descriptive
 * string ('repeat' | 'sequence') or null.
 */
function detectRun(word) {
  if (word.length < MIN_RUN_LEN) return null;
  // All identical?
  let allSame = true;
  for (let i = 1; i < word.length; i++) {
    if (word[i] !== word[0]) {
      allSame = false;
      break;
    }
  }
  if (allSame) return 'repeat';
  // Strictly ascending or descending consecutive sequence?
  let asc = true;
  let desc = true;
  for (let i = 1; i < word.length; i++) {
    const diff = word.charCodeAt(i) - word.charCodeAt(i - 1);
    if (diff !== 1) asc = false;
    if (diff !== -1) desc = false;
  }
  if (asc || desc) return 'sequence';
  return null;
}

/**
 * Evaluate a selection. Returns { word, points, type } or null.
 * Accepts any dictionary word (length >= MIN_WORD_LEN) in either reading
 * direction, or a qualifying run.
 */
function evaluateSelection(cells) {
  const word = readSelection(cells);
  if (word.length < MIN_WORD_LEN) return null;
  const rev = [...word].reverse().join('');
  // Runs first (cheap to detect and always valid).
  const runType = detectRun(word) || detectRun(rev);
  if (runType) {
    return { word, points: word.length, type: 'run' };
  }
  // Dictionary word in either direction.
  const candidate = state.dict.has(word) ? word : state.dict.has(rev) ? rev : null;
  if (candidate) {
    return { word: candidate, points: candidate.length, type: 'word' };
  }
  return null;
}

/**
 * Remove the given cells, collapse columns downward, and refill the
 * vacated top cells using the Markov model.
 * Returns a map of cellKey -> drop distance (in rows) for animation:
 *   - survivors that fell: positive number of rows dropped
 *   - freshly spawned top cells: marked with `spawn` set
 * @param {Array<{x:number,y:number}>} cells
 */
function collapseAndRefill(cells) {
  const grid = state.grid;
  // Mark removed cells per column.
  const removedByCol = new Map();
  for (const c of cells) {
    let set = removedByCol.get(c.x);
    if (!set) {
      set = new Set();
      removedByCol.set(c.x, set);
    }
    set.add(c.y);
  }
  // Track how far each (new position) cell dropped, and which cells are
  // freshly spawned, so the renderer can animate them.
  const dropRows = new Map(); // key -> rows fallen
  const spawned = new Set(); // key of freshly filled top cells
  // For each affected column, compact the surviving letters toward the
  // bottom, then refill the top with fresh fill letters.
  for (const [x, removed] of removedByCol) {
    const survivors = []; // { ch, srcY }
    for (let y = 0; y < grid.height; y++) {
      if (removed.has(y)) continue;
      const ch = grid.get(x, y);
      survivors.push({ ch, srcY: y }); // may be null but board is fully filled in play
    }
    const newCount = grid.height - survivors.length;
    // Place new (empty) cells on top, survivors fall to the bottom.
    for (let y = 0; y < grid.height; y++) {
      if (y < newCount) {
        grid.set(x, y, null);
        spawned.add(key(x, y));
      } else {
        const s = survivors[y - newCount];
        grid.set(x, y, s.ch);
        const dist = y - s.srcY;
        if (dist > 0) dropRows.set(key(x, y), dist);
      }
    }
  }
  // Refill empty cells (top of affected columns) using the model.
  refillEmpty(grid);
  return { dropRows, spawned };
}

/**
 * Refill all empty cells using the directional Markov model + combiner,
 * avoiding accidentally re-forming forbidden (global) words where possible.
 */
function refillEmpty(grid) {
  const {
    model,
    reverseModel,
    combiner,
    sampling,
    rng,
    lattice,
    includeBackwards,
    forbidden,
    alphabet,
  } = state.fill;
  // Fill from bottom rows upward so freshly placed letters provide context
  // for the cells above them.
  for (let y = grid.height - 1; y >= 0; y--) {
    for (let x = 0; x < grid.width; x++) {
      if (grid.get(x, y)) continue;
      const dists = [];
      const dirs = latticeDirections(lattice, y, { includeBackwards });
      for (const d of dirs) {
        const ctx = readContext(grid, x, y, d, model.order, lattice);
        if (ctx) {
          const useReverse = d.forward === false && reverseModel;
          const queryCtx = useReverse ? [...ctx].reverse().join('') : ctx;
          const dist = (useReverse ? reverseModel : model).predict(queryCtx);
          if (dist.size) dists.push(dist);
        }
      }
      let combined = dists.length ? combine(dists, combiner) : model.predict('');
      const avoid = forbiddenCharsAt(grid, x, y, dirs, forbidden, lattice);
      combined = prune(combined, avoid);
      const ch = select(combined, sampling, rng, alphabet, avoid);
      grid.set(x, y, ch);
    }
  }
}

// Local copies of the filler's avoidance helpers (kept private here to
// avoid widening the filler module's public surface).
function forbiddenCharsAt(grid, x, y, dirs, forbidden, lattice) {
  const avoid = new Set();
  if (!forbidden || forbidden.maxLen < 2 || forbidden.set.size === 0) return avoid;
  const reach = forbidden.maxLen - 1;
  for (const d of dirs) {
    const { before, after } = readLineAround(grid, x, y, d, reach, reach, lattice);
    for (const word of forbidden.set) {
      const L = word.length;
      if (L < 2 || L > before.length + 1 + after.length) continue;
      for (let start = before.length - (L - 1); start <= before.length; start++) {
        if (start < 0) continue;
        const candIdx = before.length - start;
        if (candIdx < 0 || candIdx >= L) continue;
        let ok = true;
        for (let i = 0; i < L; i++) {
          if (i === candIdx) continue;
          const lineIdx = start + i;
          let ch;
          if (lineIdx < before.length) ch = before[lineIdx];
          else ch = after[lineIdx - before.length - 1];
          if (ch == null || ch !== word[i]) {
            ok = false;
            break;
          }
        }
        if (ok) avoid.add(word[candIdx]);
      }
    }
  }
  return avoid;
}

function prune(dist, avoid) {
  if (!avoid || avoid.size === 0 || !dist || dist.size === 0) return dist;
  const out = new Map();
  let total = 0;
  for (const [c, p] of dist) {
    if (avoid.has(c)) continue;
    out.set(c, p);
    total += p;
  }
  if (out.size === 0 || total <= 0) return new Map();
  for (const [c, p] of out) out.set(c, p / total);
  return out;
}

function flashCleared(cells, done) {
  let pending = cells.length;
  if (!pending) {
    done();
    return;
  }
  for (const c of cells) {
    const td = cellAt(state.table, c.x, c.y);
    if (td) td.classList.add('clearing');
  }
  setTimeout(done, 260);
}
/**
 * Animate dropped + spawned tiles into place. Each affected cell starts
 * offset upward by its fall distance (in cell heights) and transitions back
 * to its final position, giving a "tiles falling" effect.
 * @param {HTMLTableElement} table
 * @param {Map<string, number>} dropRows key -> rows fallen
 * @param {Set<string>} spawned keys of freshly spawned top cells
 */
function animateDrops(table, dropRows, spawned) {
  if ((!dropRows || dropRows.size === 0) && (!spawned || spawned.size === 0)) return;
  // Determine the cell height (px) from the table's CSS variable plus the
  // border spacing so the offset lines up with a whole number of rows.
  const sizeRaw = parseFloat(table.style.getPropertyValue('--cell-size'));
  const cell = (Number.isFinite(sizeRaw) && sizeRaw > 0 ? sizeRaw : 32) + 4; // +spacing
  const animated = [];
  const setup = (td, rows) => {
    if (!td || rows <= 0) return;
    td.style.transition = 'none';
    td.style.transform = `translateY(${-rows * cell}px)`;
    td.classList.add('dropping');
    animated.push(td);
  };
  for (let y = 0; y < state.grid.height; y++) {
    for (let x = 0; x < state.grid.width; x++) {
      const k = key(x, y);
      const td = cellAt(table, x, y);
      if (dropRows && dropRows.has(k)) setup(td, dropRows.get(k));
      else if (spawned && spawned.has(k)) {
        // Spawned cells fall from just above the top of their column.
        setup(td, y + 1);
      }
    }
  }
  if (!animated.length) return;
  // Force a reflow so the initial transform is applied before transitioning.
  void table.offsetHeight;
  requestAnimationFrame(() => {
    for (const td of animated) {
      td.style.transition = 'transform 0.32s cubic-bezier(0.22, 1, 0.36, 1)';
      td.style.transform = 'translateY(0)';
    }
    setTimeout(() => {
      for (const td of animated) {
        td.style.transition = '';
        td.style.transform = '';
        td.classList.remove('dropping');
      }
    }, 360);
  });
}

export function initCollapse(root, grid, cfg = {}) {
  if (state && state.tick) clearInterval(state.tick);
  const container = root.querySelector('#grid');
  for (const c of container.querySelectorAll('.confetti-layer')) c.remove();

  const prevStatus = root.querySelector('#collapse-status');
  if (prevStatus) prevStatus.textContent = '';
  const pauseBtn = root.querySelector('#btn-collapse-pause');
  if (pauseBtn) pauseBtn.textContent = 'Pause';

  const table = renderInteractiveGrid(container, grid, {
    lattice: grid.lattice || 'square',
    fontScale: cfg.fontScale,
    fontFamily: cfg.fontFamily,
  });

  const dictWords = cleanWordList(getExternalWordList());
  const dict = new Set(dictWords.filter((w) => w.length >= MIN_WORD_LEN));

  // Build the model + supporting fill state once so collapses can refill.
  const { generateFillState } = buildFillState(grid, cfg, dictWords);

  state = {
    grid,
    table,
    lattice: grid.lattice || 'square',
    dict,
    foundList: [],
    score: 0,
    anchor: null,
    tick: null,
    paused: false,
    accumulatedMs: 0,
    segmentStart: Date.now(),
    busy: false,
    fill: generateFillState,
  };

  state.tick = setInterval(() => updateTimer(root), 250);
  updateTimer(root);
  updateScore(root);
  updateFoundList(root);

  const onCell = (td) => {
    if (state.paused || state.busy) return;
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
    const match = evaluateSelection(cells);
    const status = root.querySelector('#collapse-status');
    if (match) {
      state.score += match.points;
      state.foundList.unshift(match);
      state.foundList = state.foundList.slice(0, 30);
      updateScore(root);
      updateFoundList(root);
      if (status) {
        const label = match.type === 'run' ? 'Run' : 'Word';
        status.textContent = `✨ ${label}: ${match.word.toUpperCase()} (+${match.points})`;
      }
      state.busy = true;
      flashCleared(cells, () => {
        const { dropRows, spawned } = collapseAndRefill(cells);
        // Re-render to reflect the dropped + refilled board.
        state.table = renderInteractiveGrid(container, state.grid, {
          lattice: state.grid.lattice || 'square',
          fontScale: cfg.fontScale,
          fontFamily: cfg.fontFamily,
        });
        animateDrops(state.table, dropRows, spawned);
        wireTable();
        state.busy = false;
      });
    } else {
      if (status) status.textContent = `Not a word — keep looking!`;
      flashWrong(cells);
    }
  };

  const wireTable = () => {
    state.table.addEventListener('click', (e) => {
      const cell = e.target.closest('td');
      if (cell) onCell(cell);
    });
  };
  wireTable();
}

/**
 * Build the reusable fill state (trained model, combiner config, alphabet
 * and forbidden index) used to refill after each collapse.
 */
function buildFillState(grid, cfg, dictWords) {
  // Lazy import avoided: generator already trains models, but to keep this
  // self-contained we (re)train from the supplied reference text.
  const generateFillState = {
    model: cfg.model,
    reverseModel: cfg.reverseModel,
    combiner: cfg.combiner || 'product',
    sampling: cfg.sampling || 'weighted',
    rng: cfg.rng || Math.random,
    lattice: cfg.lattice || 'square',
    includeBackwards: cfg.includeBackwards !== false,
    // Collapse mode is about FINDING words, so we don't avoid forming them.
    forbidden: { set: new Set(), maxLen: 0 },
    alphabet: cfg.model ? [...cfg.model.alphabet] : 'abcdefghijklmnopqrstuvwxyz'.split(''),
  };
  return { generateFillState };
}

export function togglePauseCollapse() {
  if (!state) return false;
  if (state.paused) {
    state.paused = false;
    state.segmentStart = Date.now();
    state.anchor = null;
  } else {
    state.accumulatedMs += Date.now() - state.segmentStart;
    state.paused = true;
    state.anchor = null;
    if (state.table) {
      for (const td of state.table.querySelectorAll('td.selecting')) {
        td.classList.remove('selecting');
      }
    }
  }
  return state.paused;
}

export function stopCollapse() {
  if (state && state.tick) clearInterval(state.tick);
  state = null;
}
