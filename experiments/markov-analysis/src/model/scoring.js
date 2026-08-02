// scoring.js — combine strategies + full-text analysis

export function combine(pF, pB, strategy) {
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

// Analyze test tokens against a bidirectional model.
// tokenSpans: [{ token, start, end }]
export function analyze(model, tokenSpans, cfg, onProgress) {
  const tokens = tokenSpans.map((s) => s.token);
  const probOpts = {
    smoothingK: cfg.smoothingK,
    backoff: cfg.backoff,
    floorProb: cfg.floorProb,
  };
  const results = new Array(tokenSpans.length);
  const step = Math.max(1, Math.floor(tokenSpans.length / 100));

  for (let i = 0; i < tokenSpans.length; i++) {
    const span = tokenSpans[i];
    const leftCtx = model.leftContext(tokens, i);
    const rightCtx = model.rightContext(tokens, i);

    const pForward = model.forward.prob(leftCtx, span.token, probOpts);
    const pBackward = model.backward.prob(rightCtx, span.token, probOpts);
    const pCombined = combine(pForward, pBackward, cfg.combine);

    const topReplacements = topN(model, leftCtx, rightCtx, cfg, span.token);

    results[i] = {
      index: i,
      token: span.token,
      start: span.start,
      end: span.end,
      pForward,
      pBackward,
      pCombined,
      topReplacements,
    };

    if (onProgress && i % step === 0) {
      onProgress(i / tokenSpans.length);
    }
  }
  if (onProgress) onProgress(1);
  return results;
}

function topN(model, leftCtx, rightCtx, cfg, actualToken) {
  const opts = { backoff: cfg.backoff };
  const fCands = model.forward.topCandidates(leftCtx, cfg.topN * 3, opts);
  const bCands = model.backward.topCandidates(rightCtx, cfg.topN * 3, opts);

  const union = new Map();
  for (const c of fCands) union.set(c.token, true);
  for (const c of bCands) union.set(c.token, true);
  union.set(actualToken, true);

  const probOpts = {
    smoothingK: cfg.smoothingK,
    backoff: cfg.backoff,
    floorProb: cfg.floorProb,
  };

  const scored = [];
  for (const tok of union.keys()) {
    const pF = model.forward.prob(leftCtx, tok, probOpts);
    const pB = model.backward.prob(rightCtx, tok, probOpts);
    const p = combine(pF, pB, cfg.combine);
    scored.push({ token: tok, p, pForward: pF, pBackward: pB });
  }
  scored.sort((a, b) => b.p - a.p);
  return scored.slice(0, cfg.topN);
}
// Compute overall match statistics from analysis results.
// A token is a "top-1 match" if it equals the highest-scoring candidate.
export function summarizeResults(results) {
  const n = results.length;
  if (n === 0) {
    return { count: 0, top1: 0, topN: 0, meanP: 0, geoMeanP: 0, perplexity: 0 };
  }
  let top1 = 0;
  let inTopN = 0;
  let sumP = 0;
  let sumLogP = 0;
  for (const r of results) {
    const cands = r.topReplacements || [];
    if (cands.length && cands[0].token === r.token) top1++;
    if (cands.some((c) => c.token === r.token)) inTopN++;
    const p = r.pCombined > 0 ? r.pCombined : 1e-12;
    sumP += p;
    sumLogP += Math.log(p);
  }
  const meanP = sumP / n;
  const geoMeanP = Math.exp(sumLogP / n);
  const perplexity = Math.exp(-sumLogP / n);
  return {
    count: n,
    top1: top1 / n,
    topN: inTopN / n,
    meanP,
    geoMeanP,
    perplexity,
  };
}
