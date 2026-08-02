// js/resolve.js
// Collision resolution rules (math_outline.md §4).

import { add, sub, scale, dot, normalize } from './vec.js';

// §4.1 / §4.3  Point-plane projection with safety offset. n must be unit.
//   p <- p - (n.(p - q) - delta_safe) n
// Zeroes only the normal component; tangential (sliding) motion survives.
export function projectPointToPlane(p, n, q, deltaSafe = 0) {
  const un = normalize(n);
  const d = dot(un, sub(p, q)) - deltaSafe;
  return sub(p, scale(un, d));
}

// §4.2  Edge-edge delta scaling: retract to eta * t* of the step. Returns
// the scaled deltas (direction preserved, magnitude truncated short of TOI).
export function scaleEdgeEdgeDeltas(deltas, toi, eta = 0.9) {
  const f = eta * toi;
  return deltas.map((dp) => scale(dp, f));
}

// §4.2 (analysis note) When a vertex participates in several events in one
// round, take the MINIMUM eta*t* across all of them.
export function combineScaleFactors(factors) {
  return factors.length ? Math.min(...factors) : 1;
}

// §4.4  Resolve a batch of events in earliest-TOI order, applying the
// per-vertex minimum scale to displacements. `events` is an array of
// { toi, verts: [i,...] }. Mutates and returns the scale map (Map<index,f>).
export function resolveByEarliestTOI(events, eta = 0.9) {
  const scaleOf = new Map();
  const sorted = events.slice().sort((a, b) => a.toi - b.toi);
  for (const ev of sorted) {
    const f = eta * ev.toi;
    for (const i of ev.verts) {
      const prev = scaleOf.has(i) ? scaleOf.get(i) : 1;
      scaleOf.set(i, Math.min(prev, f));
    }
  }
  return scaleOf;
}

// Residual penetration predicate for §4.4 termination: true if any depth
// exceeds eps_resid (i.e. the step must be rejected and trust radii shrunk).
export function hasResidualPenetration(depths, epsResid) {
  return depths.some((d) => d > epsResid);
}
