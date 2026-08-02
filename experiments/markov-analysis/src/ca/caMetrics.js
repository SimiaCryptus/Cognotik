// caMetrics.js — per-generation statistics for CA runs

// changed: boolean[] of cells that flipped this step.
export function activity(changed) {
  if (!changed.length) return 0;
  let c = 0;
  for (const b of changed) if (b) c++;
  return c / changed.length;
}

// Token-level edit (Hamming) distance between two equal-length tapes,
// normalized to [0,1]. Used as "distance from original".
export function hammingDistance(a, b) {
  const n = Math.min(a.length, b.length);
  if (n === 0) return 0;
  let d = Math.abs(a.length - b.length);
  for (let i = 0; i < n; i++) if (a[i] !== b[i]) d++;
  return d / Math.max(a.length, b.length);
}

// Shannon entropy (bits) of the token distribution on a tape.
export function vocabEntropy(tape) {
  const counts = new Map();
  for (const t of tape) counts.set(t, (counts.get(t) || 0) + 1);
  const n = tape.length || 1;
  let h = 0;
  for (const c of counts.values()) {
    const p = c / n;
    h -= p * Math.log2(p);
  }
  return h;
}

// Mean combined probability of a tape under the model (self-consistency).
// model: BidirectionalModel; opts passed to combinedDistribution/prob.
export function meanProbability(model, tape, opts = {}) {
  const {
    combine,
    smoothingK = 0,
    backoff = true,
    floorProb = 1e-6,
    radius = model.order,
    boundary = 'periodic',
  } = opts;
  const n = tape.length;
  if (!n) return { meanP: 0, perplexity: 0 };
  const probOpts = { smoothingK, backoff, floorProb };
  let sumLog = 0;
  for (let i = 0; i < n; i++) {
    const left = boundedNeigh(tape, i - radius, i, boundary);
    const right = boundedNeigh(tape, i + 1, i + 1 + radius, boundary).reverse();
    const pF = model.forward.prob(left, tape[i], probOpts);
    const pB = model.backward.prob(right, tape[i], probOpts);
    const p = combineP(pF, pB, combine);
    sumLog += Math.log(Math.max(p, 1e-12));
  }
  const meanLog = sumLog / n;
  return { meanP: Math.exp(meanLog), perplexity: Math.exp(-meanLog) };
}

function combineP(pF, pB, strategy) {
  switch (strategy) {
    case 'forward':
      return pF;
    case 'backward':
      return pB;
    case 'min':
      return Math.min(pF, pB);
    case 'max':
      return Math.max(pF, pB);
    case 'geometric-mean':
      return Math.sqrt(pF * pB);
    case 'average':
    default:
      return (pF + pB) / 2;
  }
}

function boundedNeigh(arr, from, to, boundary) {
  const n = arr.length;
  const out = [];
  for (let j = from; j < to; j++) {
    if (j >= 0 && j < n) out.push(arr[j]);
    else if (boundary === 'periodic') out.push(arr[((j % n) + n) % n]);
    else if (boundary === 'reflective') {
      let idx = j;
      if (idx < 0) idx = -idx - 1;
      if (idx >= n) idx = 2 * n - idx - 1;
      idx = Math.max(0, Math.min(n - 1, idx));
      out.push(arr[idx]);
    }
  }
  return out;
}
