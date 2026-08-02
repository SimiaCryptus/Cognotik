// js/retriangulate.js
// Conservative connectivity edits (idea.md §6, math_outline §8).
//
// Only Delaunay-style edge flips are implemented, and only when:
//   1. the shared edge is interior (exactly 2 incident faces),
//   2. both incident 1-rings pass the caller's keepOut() predicate
//      (strictly outside all constraint ε-shells → edit ≠ boundary edit),
//   3. the flip strictly improves the local minimum corner angle, and
//   4. the new diagonal does not already exist (manifold safety).
// Orientation is preserved (needed for the signed volume of §1.2).

import { sub, dot, norm } from './vec.js';

function cornerAngle(A, B, C) {
  const u = sub(B, A),
    v = sub(C, A);
  const nu = norm(u),
    nv = norm(v);
  if (nu < 1e-12 || nv < 1e-12) return 0;
  const c = Math.max(-1, Math.min(1, dot(u, v) / (nu * nv)));
  return Math.acos(c);
}
function triMinAngle(P, a, b, c) {
  return Math.min(
    cornerAngle(P[a], P[b], P[c]),
    cornerAngle(P[b], P[a], P[c]),
    cornerAngle(P[c], P[a], P[b])
  );
}
const third = (f, u, v) => f.find((x) => x !== u && x !== v);
function directed(f, u, v) {
  for (let k = 0; k < 3; k++) if (f[k] === u && f[(k + 1) % 3] === v) return true;
  return false;
}
const key = (i, j) => (i < j ? i + '_' + j : j + '_' + i);

export function retriangulate(P, F, opts = {}) {
  const { keepOut = () => false, maxFlips = Infinity } = opts;
  const newF = F.map((f) => f.slice());

  // undirected edge -> incident face indices
  const em = new Map();
  newF.forEach((f, fi) => {
    for (const [i, j] of [
      [f[0], f[1]],
      [f[1], f[2]],
      [f[2], f[0]],
    ]) {
      const k = key(i, j);
      if (!em.has(k)) em.set(k, []);
      em.get(k).push(fi);
    }
  });
  const edgeExists = (i, j) => em.has(key(i, j));

  const touched = new Set();
  const lockedFace = new Set();
  let flips = 0;

  for (const [, fs] of em) {
    if (flips >= maxFlips) break;
    if (fs.length !== 2) continue;
    let [fa, fb] = fs;
    if (lockedFace.has(fa) || lockedFace.has(fb)) continue;

    // recover the undirected edge {u,v}
    const shared = newF[fa].filter((x) => newF[fb].includes(x));
    if (shared.length !== 2) continue;
    let [u, v] = shared;
    if (!directed(newF[fa], u, v)) [fa, fb] = [fb, fa];
    if (!directed(newF[fa], u, v)) continue; // inconsistent orientation

    const w1 = third(newF[fa], u, v);
    const w2 = third(newF[fb], u, v);
    if (w1 === undefined || w2 === undefined || w1 === w2) continue;
    if (edgeExists(w1, w2)) continue; // would create a non-manifold fold

    if (keepOut(u) || keepOut(v) || keepOut(w1) || keepOut(w2)) continue;

    const before = Math.min(triMinAngle(P, u, v, w1), triMinAngle(P, v, u, w2));
    const after = Math.min(triMinAngle(P, u, w2, w1), triMinAngle(P, v, w1, w2));
    if (after <= before + 1e-6) continue;

    // apply orientation-preserving flip (derivation in the header)
    newF[fa] = [u, w2, w1];
    newF[fb] = [v, w1, w2];
    lockedFace.add(fa);
    lockedFace.add(fb);
    [u, v, w1, w2].forEach((x) => touched.add(x));
    flips++;
  }

  return { F: newF, touched: [...touched], flips };
}
