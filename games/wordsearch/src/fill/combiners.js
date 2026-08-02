// Combine multiple Map<char, prob> distributions into one.

const EPS = 1e-9;

function collectChars(dists) {
  const chars = new Set();
  for (const d of dists) for (const c of d.keys()) chars.add(c);
  return chars;
}

function normalise(map) {
  let total = 0;
  for (const v of map.values()) total += v;
  if (total <= 0) return map;
  for (const [k, v] of map) map.set(k, v / total);
  return map;
}

/** Product (log-sum) combiner — AND-like. */
function product(dists) {
  const chars = collectChars(dists);
  const out = new Map();
  for (const c of chars) {
    let logp = 0;
    for (const d of dists) {
      logp += Math.log((d.get(c) || 0) + EPS);
    }
    out.set(c, Math.exp(logp));
  }
  return normalise(out);
}

/** Sum / average combiner — OR-like. */
function sum(dists) {
  const chars = collectChars(dists);
  const out = new Map();
  for (const c of chars) {
    let s = 0;
    for (const d of dists) s += d.get(c) || 0;
    out.set(c, s / dists.length);
  }
  return normalise(out);
}

/** Max combiner — take the strongest single directional vote. */
function max(dists) {
  const chars = collectChars(dists);
  const out = new Map();
  for (const c of chars) {
    let m = 0;
    for (const d of dists) m = Math.max(m, d.get(c) || 0);
    out.set(c, m);
  }
  return normalise(out);
}

/** Vote combiner — each direction votes for its argmax. */
function vote(dists) {
  const out = new Map();
  for (const d of dists) {
    let bestChar = null;
    let bestP = -1;
    for (const [c, p] of d) {
      if (p > bestP) {
        bestP = p;
        bestChar = c;
      }
    }
    if (bestChar != null) {
      out.set(bestChar, (out.get(bestChar) || 0) + 1);
    }
  }
  return normalise(out);
}

export const COMBINERS = { product, sum, max, vote };

/**
 * @param {Map<string,number>[]} dists
 * @param {'product'|'sum'|'max'|'vote'} method
 * @returns {Map<string,number>}
 */
export function combine(dists, method = 'product') {
  if (!dists.length) return new Map();
  const fn = COMBINERS[method] || product;
  return fn(dists);
}
