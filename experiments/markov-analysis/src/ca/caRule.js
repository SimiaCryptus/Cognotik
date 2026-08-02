// caRule.js — wraps a bidirectional model into a local CA rule

// Reheat a distribution ([{token, p, ...}]) by temperature, renormalize.
// T -> 0 sharpens toward argmax; T = 1 leaves it; T large flattens it.
function reheat(dist, temperature) {
  if (!dist.length) return dist;
  const T = Math.max(temperature, 1e-6);
  const logits = dist.map((d) => Math.log(Math.max(d.p, 1e-12)) / T);
  const maxL = Math.max(...logits);
  let sum = 0;
  const exps = logits.map((l) => {
    const e = Math.exp(l - maxL);
    sum += e;
    return e;
  });
  return dist.map((d, i) => ({ ...d, w: exps[i] / sum }));
}

function sampleFrom(reheated, rng) {
  const r = rng();
  let acc = 0;
  for (const d of reheated) {
    acc += d.w;
    if (r <= acc) return d.token;
  }
  return reheated[reheated.length - 1].token;
}

// opts: {
//   radius, combine, temperature, deterministic, limit,
//   smoothingK, backoff, floorProb, rng
// }
// Returns a function localRule(acc, i) -> nextToken, where `acc` is the
// boundary-aware accessor from caEngine (has .at(i) and .neigh(from, to)).
export function makeRule(model, opts) {
  const {
    radius = model.order,
    combine = 'average',
    temperature = 0.2,
    deterministic = false,
    limit = 32,
    smoothingK = 0,
    backoff = true,
    floorProb = 1e-6,
    rng = Math.random,
  } = opts;

  const distOpts = { combine, limit, smoothingK, backoff, floorProb };

  function localRule(acc, i) {
    // left context: tokens before i, in natural order (most recent last)
    const left = acc.neigh(i - radius, i);
    // right context: tokens after i, closest-first (reversed) for backward
    const right = acc.neigh(i + 1, i + 1 + radius).reverse();
    const dist = model.combinedDistribution(left, right, distOpts);
    if (!dist.length) return acc.at(i);
    if (deterministic) return dist[0].token;
    return sampleFrom(reheat(dist, temperature), rng);
  }

  return localRule;
}
