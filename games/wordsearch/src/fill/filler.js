import { latticeDirections, readContext, readLineAround } from '../grid/directions.js';
import { combine } from './combiners.js';
import { pickNextCell } from './adjacency.js';
import { buildForbiddenIndex } from '../grid/wordlist.js';

/**
 * Select a character from a distribution.
 * @param {Map<string,number>} dist
 * @param {'weighted'|'argmax'} mode
 * @param {() => number} rng
 * @param {string[]} fallbackAlphabet
 * @param {Set<string>} [avoid]
 */
export function select(dist, mode, rng, fallbackAlphabet, avoid = null) {
  if (!dist || dist.size === 0) {
    // No usable distribution: pick a random alphabet char that isn't forbidden.
    let a = fallbackAlphabet;
    if (avoid && avoid.size) {
      const safe = a.filter((c) => !avoid.has(c));
      if (safe.length) a = safe;
    }
    return a.length ? a[Math.floor(rng() * a.length)] : 'x';
  }
  if (mode === 'argmax') {
    let bestChar = null;
    let bestP = -1;
    for (const [c, p] of dist) {
      if (p > bestP) {
        bestP = p;
        bestChar = c;
      }
    }
    return bestChar;
  }
  // weighted sampling
  let r = rng();
  for (const [c, p] of dist) {
    r -= p;
    if (r <= 0) return c;
  }
  // floating point fallback
  return [...dist.keys()].pop();
}
/**
 * Determine which candidate characters at (x, y) would accidentally
 * complete a forbidden word along any lattice direction, given the
 * already-filled neighbours.
 *
 * For each direction we read the filled run behind and ahead of the cell,
 * then for every possible split position within a window of (maxLen) we
 * check whether placing a character would form a forbidden word that spans
 * the cell. Returns a Set of characters to avoid.
 *
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {number} x
 * @param {number} y
 * @param {Array<{name:string,dx:number,dy:number}>} dirs
 * @param {{set:Set<string>, maxLen:number}} forbidden
 * @param {'square'|'hex'|'triangular'} lattice
 * @returns {Set<string>}
 */
function forbiddenChars(grid, x, y, dirs, forbidden, lattice) {
  const avoid = new Set();
  if (!forbidden || forbidden.maxLen < 2 || forbidden.set.size === 0) return avoid;
  const reach = forbidden.maxLen - 1;
  for (const d of dirs) {
    const { before, after } = readLineAround(grid, x, y, d, reach, reach, lattice);
    if ((before && before.length) || (after && after.length)) {
      // Trace what context the avoidance check is actually seeing.
      //console.debug(`[fill] (${x},${y}) dir=${d.name} before="${before}" after="${after}"`);
    }
    // The candidate char sits between `before` and `after`. Any contiguous
    // substring of `${before}${candidate}${after}` that includes the
    // candidate and matches a forbidden word is disallowed.
    for (const word of forbidden.set) {
      const L = word.length;
      if (L < 2 || L > before.length + 1 + after.length) continue;
      // The candidate occupies index `before.length` in the combined line.
      // Try every alignment of `word` over the combined line that covers it.
      for (let start = before.length - (L - 1); start <= before.length; start++) {
        if (start < 0) continue;
        const candIdx = before.length - start; // position of candidate within word
        if (candIdx < 0 || candIdx >= L) continue;
        let ok = true;
        for (let i = 0; i < L; i++) {
          if (i === candIdx) continue; // this is the candidate slot
          const lineIdx = start + i; // index within combined line
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
  if (avoid.size) {
    //    console.debug(`[fill] (${x},${y}) avoiding chars: ${[...avoid].join(',')}`);
  }
  return avoid;
}
/**
 * Return a copy of `dist` with forbidden characters removed and the
 * remaining mass re-normalised. If everything would be removed, the
 * original distribution is returned unchanged (we'd rather risk a word
 * than fail to fill a cell).
 * @param {Map<string,number>} dist
 * @returns {Map<string,number>}
 */
function pruneDistribution(dist, avoid) {
  if (!avoid || avoid.size === 0 || !dist || dist.size === 0) return dist;
  const out = new Map();
  let total = 0;
  for (const [c, p] of dist) {
    if (avoid.has(c)) continue;
    out.set(c, p);
    total += p;
  }
  if (out.size === 0 || total <= 0) {
    // Everything in the model distribution was forbidden. Before giving up,
    // try ANY alphabet character that isn't in the avoid set — the model
    // distribution is only a subset of the alphabet, so there are usually
    // plenty of safe (if unlikely) letters available.
    console.warn('[fill] all model candidates forbidden; searching alphabet for a safe char');
    return new Map(); // signal caller to fall back to alphabet-level selection
  }
  for (const [c, p] of out) out.set(c, p / total);
  return out;
}

/**
 * Step-by-step generator version of fillGrid. Yields after each cell
 * is filled so callers can visualise the buildout.
 *
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {import('../markov/MarkovModel.js').MarkovModel} model
 * @param {object} config
 * @yields {{x:number,y:number,ch:string,contexts:Array}}
 */
export function* fillGridSteps(grid, model, config = {}) {
  const {
    combiner = 'product',
    sampling = 'weighted',
    rng = Math.random,
    lattice = 'square',
    includeBackwards = true,
    reverseModel = null,
    words = [],
    avoidWords = true,
  } = config;
  const alphabet = [...model.alphabet];
  const forbidden = avoidWords ? buildForbiddenIndex(words) : { set: new Set(), maxLen: 0 };
  let cell;
  let guard = grid.width * grid.height + 1;
  while ((cell = pickNextCell(grid, rng, lattice)) && guard-- > 0) {
    const { x, y } = cell;
    const dists = [];
    const contexts = [];
    const dirs = latticeDirections(lattice, y, { includeBackwards });
    for (const d of dirs) {
      const ctx = readContext(grid, x, y, d, model.order, lattice);
      if (ctx) {
        // Backward-oriented vectors are best predicted by a model trained on
        // the reversed stream; their context must be reversed to match.
        const useReverse = d.forward === false && reverseModel;
        const queryCtx = useReverse ? [...ctx].reverse().join('') : ctx;
        const dist = (useReverse ? reverseModel : model).predict(queryCtx);
        if (dist.size) {
          dists.push(dist);
          contexts.push({ dir: d.name, ctx });
        }
      }
    }
    let combined = dists.length ? combine(dists, combiner) : model.predict('');
    // Avoid accidentally constructing any target word in the filler.
    const avoid = forbiddenChars(grid, x, y, dirs, forbidden, lattice);
    combined = pruneDistribution(combined, avoid);
    const ch = select(combined, sampling, rng, alphabet, avoid);
    grid.set(x, y, ch);
    yield { x, y, ch, contexts };
  }
  return grid;
}
/**
 * Fill all empty cells of the grid using directional Markov
 * predictions combined per config.
 *
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {import('../markov/MarkovModel.js').MarkovModel} model
 * @param {object} config
 * @param {string} [config.combiner]
 * @param {string} [config.sampling]
 * @param {() => number} [config.rng]
 */
export function fillGrid(grid, model, config = {}) {
  const {
    combiner = 'product',
    sampling = 'weighted',
    rng = Math.random,
    lattice = 'square',
    includeBackwards = true,
    reverseModel = null,
    words = [],
    avoidWords = true,
  } = config;
  const alphabet = [...model.alphabet];
  const forbidden = avoidWords ? buildForbiddenIndex(words) : { set: new Set(), maxLen: 0 };

  let cell;
  let guard = grid.width * grid.height + 1;
  while ((cell = pickNextCell(grid, rng, lattice)) && guard-- > 0) {
    const { x, y } = cell;
    const dists = [];
    const dirs = latticeDirections(lattice, y, { includeBackwards });
    for (const d of dirs) {
      const ctx = readContext(grid, x, y, d, model.order, lattice);
      if (ctx) {
        // Use the reverse-trained model for backward-oriented vectors.
        const useReverse = d.forward === false && reverseModel;
        const queryCtx = useReverse ? [...ctx].reverse().join('') : ctx;
        const dist = (useReverse ? reverseModel : model).predict(queryCtx);
        if (dist.size) dists.push(dist);
      }
    }
    // If no directional context available, fall back to unigram.
    let combined = dists.length ? combine(dists, combiner) : model.predict('');
    // Avoid accidentally constructing any target word in the filler.
    const avoid = forbiddenChars(grid, x, y, dirs, forbidden, lattice);
    combined = pruneDistribution(combined, avoid);
    const ch = select(combined, sampling, rng, alphabet, avoid);
    grid.set(x, y, ch);
  }
  return grid;
}
