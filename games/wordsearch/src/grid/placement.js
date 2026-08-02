import { latticeDirections, step } from './directions.js';
import { normaliseText } from '../markov/textPipeline.js';
/**
 * Count how many distinct already-placed words a candidate placement would
 * touch — either by overlapping a shared cell or by sitting orthogonally/
 * diagonally adjacent to one. Uses the per-cell ownership map.
 *
 * @param {Array<{x:number,y:number,ch:string}>} coords
 * @param {Map<string, Set<number>>} ownership cellKey -> set of word indices
 * @param {import('./Grid.js').Grid} grid
 * @returns {number}
 */
function countAdjacentWords(coords, ownership, grid) {
  const touched = new Set();
  const selfKeys = new Set(coords.map((c) => `${c.x},${c.y}`));
  for (const c of coords) {
    // Check the cell itself (overlap) and its 8 neighbours (adjacency).
    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        const nx = c.x + dx;
        const ny = c.y + dy;
        if (!grid.inBounds(nx, ny)) continue;
        const key = `${nx},${ny}`;
        // Skip cells that are part of this same candidate placement.
        if (selfKeys.has(key)) continue;
        const owners = ownership.get(key);
        if (owners) for (const idx of owners) touched.add(idx);
      }
    }
  }
  return touched.size;
}
/**
 * Fisher–Yates shuffle (in place) using the supplied rng. Returns the array.
 * @template T
 * @param {T[]} arr
 * @param {() => number} rng
 * @returns {T[]}
 */
function shuffle(arr, rng) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}

/**
 * Walk a candidate placement of `word` starting at (x, y) heading in
 * direction `dirName`. Returns a descriptor of the run, or null if the
 * word can't fit / conflicts with an incompatible existing letter.
 *
 * The descriptor includes an `overlap` count: how many cells coincide
 * with an already-filled (matching) letter. Higher overlap means a more
 * interlocked, less predictable layout.
 *
 * @param {import('./Grid.js').Grid} grid
 * @param {string} word
 * @param {number} x
 * @param {number} y
 * @param {string} dirName
 * @param {'square'|'hex'|'triangular'} lattice
 * @returns {{coords:Array<{x:number,y:number,ch:string}>, overlap:number}|null}
 */
function evaluatePlacement(grid, word, x, y, dirName, lattice) {
  const len = word.length;
  const coords = [];
  let overlap = 0;
  let cx = x;
  let cy = y;
  for (let i = 0; i < len; i++) {
    if (!grid.inBounds(cx, cy)) return null;
    const existing = grid.get(cx, cy);
    if (existing) {
      if (existing !== word[i]) return null;
      overlap += 1;
    }
    coords.push({ x: cx, y: cy, ch: word[i] });
    const next = step(lattice, cx, cy, dirName, 1);
    if (!next) return null;
    cx = next.x;
    cy = next.y;
  }
  if (coords.length !== len) return null;
  return { coords, overlap };
}

/**
 * Attempt to place a single word. Rather than picking one random
 * direction per attempt (which biases the resulting pattern), we sample a
 * random starting position then evaluate ALL orientations there in a
 * shuffled order. Across the search we keep the best-scoring valid
 * placement, preferring ones that intersect existing words so the seed
 * words form an interlocked arrangement.
 *
 * @param {import('./Grid.js').Grid} grid
 * @param {string} word
 * @param {() => number} rng
 * @param {object} [opts]
 * @param {'square'|'hex'|'triangular'} [opts.lattice]
 * @param {boolean} [opts.includeBackwards]
 * @param {number} [opts.maxAdjacency]
 * @param {Map<string, Set<number>>} [opts.ownership]
 * @param {number} [opts.wordIndex]
 */
function tryPlaceWord(grid, word, rng, opts = {}, tries = 200) {
  const {
    lattice = 'square',
    includeBackwards = true,
    maxAdjacency = 1,
    ownership = new Map(),
  } = opts;
  const seekIntersections = maxAdjacency > 0;

  let best = null; // { coords, overlap, dirName, x, y }

  for (let t = 0; t < tries; t++) {
    const x = Math.floor(rng() * grid.width);
    const y = Math.floor(rng() * grid.height);
    // Direction vectors depend on the row for hex/tri lattices.
    const dirs = shuffle(latticeDirections(lattice, y, { includeBackwards }).slice(), rng);
    for (const dir of dirs) {
      const res = evaluatePlacement(grid, word, x, y, dir.name, lattice);
      if (!res) continue;
      // Reject placements that touch too many existing words.
      const adj = countAdjacentWords(res.coords, ownership, grid);
      if (adj > maxAdjacency) continue;
      // When adjacency is disallowed entirely, never accept overlaps.
      if (!seekIntersections && res.overlap > 0) continue;
      if (best === null || res.overlap > best.overlap) {
        best = { coords: res.coords, overlap: res.overlap, dirName: dir.name, x, y };
        // An intersecting placement is exactly what we want; commit early
        // so later (larger) words still have room and direction stays varied.
        if (seekIntersections && res.overlap > 0) break;
      }
    }
    // Found an interlocking spot — no need to keep searching.
    if (seekIntersections && best && best.overlap > 0) break;
  }

  if (!best) return null;

  for (const c of best.coords) {
    grid.set(c.x, c.y, c.ch);
    grid.lock(c.x, c.y);
  }
  return { word, dir: best.dirName, x: best.x, y: best.y, coords: best.coords };
}

/**
 * Place all target words onto the grid. Returns { placed, failed }.
 * @param {import('./Grid.js').Grid} grid
 * @param {string[]} words
 * @param {() => number} [rng]
 * @param {object} [opts]
 * @param {'square'|'hex'|'triangular'} [opts.lattice]
 * @param {boolean} [opts.includeBackwards]
 * @param {number} [opts.maxAdjacency]
 */
export function placeWords(grid, words, rng = Math.random, opts = {}) {
  const placed = [];
  const failed = [];
  // Track which placed-word indices occupy each cell so we can measure how
  // many distinct words a candidate placement would touch.
  /** @type {Map<string, Set<number>>} */
  const ownership = new Map();
  // Place longer words first (harder to fit, and they form the backbone
  // that shorter words can interlock with).
  const clean = words
    .map((w) => normaliseText(w))
    .filter(Boolean)
    .sort((a, b) => b.length - a.length);

  for (const w of clean) {
    const wordIndex = placed.length;
    const rec = tryPlaceWord(grid, w, rng, { ...opts, ownership, wordIndex });
    if (rec) {
      placed.push(rec);
      // Record ownership of every cell this word occupies.
      for (const c of rec.coords) {
        const key = `${c.x},${c.y}`;
        let set = ownership.get(key);
        if (!set) {
          set = new Set();
          ownership.set(key, set);
        }
        set.add(wordIndex);
      }
    } else failed.push(w);
  }
  return { placed, failed };
}
