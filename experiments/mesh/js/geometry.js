// js/geometry.js
// Mesh generation + topology helpers (idea.md §1.1, §6; math_outline §2).
// Not autodiff — pure JS scaffolding consumed by mesh-energy.js and the loop.

import { add, sub, scale, cross, dot, norm, normalize, triNormal } from './vec.js';

// --- Icosphere (used for both the moving mesh M and a smooth K) ----------
export function icosphere(order = 1, radius = 1) {
  const t = (1 + Math.sqrt(5)) / 2;
  let P = [
    [-1, t, 0],
    [1, t, 0],
    [-1, -t, 0],
    [1, -t, 0],
    [0, -1, t],
    [0, 1, t],
    [0, -1, -t],
    [0, 1, -t],
    [t, 0, -1],
    [t, 0, 1],
    [-t, 0, -1],
    [-t, 0, 1],
  ];
  let F = [
    [0, 11, 5],
    [0, 5, 1],
    [0, 1, 7],
    [0, 7, 10],
    [0, 10, 11],
    [1, 5, 9],
    [5, 11, 4],
    [11, 10, 2],
    [10, 7, 6],
    [7, 1, 8],
    [3, 9, 4],
    [3, 4, 2],
    [3, 2, 6],
    [3, 6, 8],
    [3, 8, 9],
    [4, 9, 5],
    [2, 4, 11],
    [6, 2, 10],
    [8, 6, 7],
    [9, 8, 1],
  ];
  const cache = new Map();
  const mid = (a, b) => {
    const key = a < b ? a * 1e6 + b : b * 1e6 + a;
    if (cache.has(key)) return cache.get(key);
    const va = P[a],
      vb = P[b];
    P.push([(va[0] + vb[0]) / 2, (va[1] + vb[1]) / 2, (va[2] + vb[2]) / 2]);
    const idx = P.length - 1;
    cache.set(key, idx);
    return idx;
  };
  for (let s = 0; s < order; s++) {
    const nf = [];
    for (const [a, b, c] of F) {
      const ab = mid(a, b),
        bc = mid(b, c),
        ca = mid(c, a);
      nf.push([a, ab, ca], [b, bc, ab], [c, ca, bc], [ab, bc, ca]);
    }
    F = nf;
  }
  P = P.map((v) => {
    const n = Math.hypot(v[0], v[1], v[2]) || 1;
    return [(v[0] / n) * radius, (v[1] / n) * radius, (v[2] / n) * radius];
  });
  return { P, F };
}

// Axis-aligned box constraint mesh with outward-oriented faces.
export function box(sx = 1, sy = 1, sz = 1) {
  const x = sx / 2,
    y = sy / 2,
    z = sz / 2;
  const P = [
    [-x, -y, -z],
    [x, -y, -z],
    [x, y, -z],
    [-x, y, -z],
    [-x, -y, z],
    [x, -y, z],
    [x, y, z],
    [-x, y, z],
  ];
  // outward CCW winding
  const F = [
    [0, 3, 2],
    [0, 2, 1], // -z
    [4, 5, 6],
    [4, 6, 7], // +z
    [0, 1, 5],
    [0, 5, 4], // -y
    [3, 7, 6],
    [3, 6, 2], // +y
    [0, 4, 7],
    [0, 7, 3], // -x
    [1, 2, 6],
    [1, 6, 5], // +x
  ];
  return { P, F };
}

// --- Topology extraction -------------------------------------------------
export function buildEdges(F) {
  const seen = new Map();
  const edges = [];
  for (const [a, b, c] of F) {
    for (const [i, j] of [
      [a, b],
      [b, c],
      [c, a],
    ]) {
      const k = i < j ? i + '_' + j : j + '_' + i;
      if (!seen.has(k)) {
        seen.set(k, edges.length);
        edges.push([Math.min(i, j), Math.max(i, j)]);
      }
    }
  }
  return edges;
}

export function buildNeighbors(F, V) {
  const nb = Array.from({ length: V }, () => new Set());
  for (const [a, b, c] of F) {
    nb[a].add(b);
    nb[a].add(c);
    nb[b].add(a);
    nb[b].add(c);
    nb[c].add(a);
    nb[c].add(b);
  }
  return nb.map((s) => [...s]);
}

// Uniform (umbrella) Laplacian matrix L = I - D^{-1}A  (math_outline §1.6).
export function buildLaplacian(F, V) {
  const nb = buildNeighbors(F, V);
  const L = [];
  for (let i = 0; i < V; i++) {
    const row = new Float32Array(V);
    row[i] = 1;
    const d = nb[i].length || 1;
    for (const j of nb[i]) row[j] -= 1 / d;
    L.push(row);
  }
  return L;
}

// Corner index triples (apex, l, r) over all faces for the angular term.
export function buildCorners(F) {
  const apex = [],
    la = [],
    ra = [];
  for (const [a, b, c] of F) {
    apex.push(a, b, c);
    la.push(b, a, a);
    ra.push(c, c, b);
  }
  return { apex, la, ra };
}

export function faceGeom(P, F) {
  return F.map(([a, b, c]) => ({
    a: P[a],
    b: P[b],
    c: P[c],
    ia: a,
    ib: b,
    ic: c,
    n: triNormal(P[a], P[b], P[c]),
  }));
}

// Ericson closest-point-on-triangle → squared distance + plane sign.
export function pointTriInfo(p, a, b, c) {
  const ab = sub(b, a),
    ac = sub(c, a),
    ap = sub(p, a);
  const d1 = dot(ab, ap),
    d2 = dot(ac, ap);
  let close;
  if (d1 <= 0 && d2 <= 0) close = a;
  else {
    const bp = sub(p, b);
    const d3 = dot(ab, bp),
      d4 = dot(ac, bp);
    if (d3 >= 0 && d4 <= d3) close = b;
    else {
      const cp = sub(p, c);
      const d5 = dot(ab, cp),
        d6 = dot(ac, cp);
      if (d6 >= 0 && d5 <= d6) close = c;
      else {
        const vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) {
          const v = d1 / (d1 - d3);
          close = [a[0] + ab[0] * v, a[1] + ab[1] * v, a[2] + ab[2] * v];
        } else {
          const vb = d5 * d2 - d1 * d6;
          if (vb <= 0 && d2 >= 0 && d6 <= 0) {
            const w = d2 / (d2 - d6);
            close = [a[0] + ac[0] * w, a[1] + ac[1] * w, a[2] + ac[2] * w];
          } else {
            const va = d3 * d6 - d5 * d4;
            if (va <= 0 && d4 - d3 >= 0 && d5 - d6 >= 0) {
              const w = (d4 - d3) / (d4 - d3 + (d5 - d6));
              close = [
                b[0] + (c[0] - b[0]) * w,
                b[1] + (c[1] - b[1]) * w,
                b[2] + (c[2] - b[2]) * w,
              ];
            } else {
              const denom = 1 / (va + vb + vc);
              const v = vb * denom,
                w = vc * denom;
              close = [
                a[0] + ab[0] * v + ac[0] * w,
                a[1] + ab[1] * v + ac[1] * w,
                a[2] + ab[2] * v + ac[2] * w,
              ];
            }
          }
        }
      }
    }
  }
  const diff = sub(p, close);
  const n = cross(ab, ac);
  const sign = dot(n, ap) >= 0 ? 1 : -1;
  return { d2: dot(diff, diff), sign, close };
}

// Signed clearance of a point vs. a static mesh (min over faces, + = outside).
export function signedClearance(p, faces) {
  let best = Infinity,
    sign = 1;
  for (const f of faces) {
    const info = pointTriInfo(p, f.a, f.b, f.c);
    const d = Math.sqrt(info.d2);
    if (d < best) {
      best = d;
      sign = info.sign;
    }
  }
  return sign * best;
}
// Hard keep-out safety projection (idea.md §4.1): if p is closer than
// `delta` to the static mesh (or has intruded), push it back out to the
// nearest surface feature + delta. Returns p unchanged when already clear.
export function enforceClearance(p, faces, delta) {
  let best = Infinity,
    bestClose = null,
    bestSign = 1;
  for (const f of faces) {
    const info = pointTriInfo(p, f.a, f.b, f.c);
    const d = Math.sqrt(info.d2);
    if (d < best) {
      best = d;
      bestClose = info.close;
      bestSign = info.sign;
    }
  }
  if (bestClose === null) return p;
  const signed = bestSign * best;
  if (signed >= delta) return p; // already clear
  // outward direction from the nearest surface point
  let dir = sub(p, bestClose);
  let dn = norm(dir);
  if (dn < 1e-9)
    dir = normalize(p); // p ~ on surface: fall back to radial
  else dir = scale(dir, 1 / dn);
  if (bestSign < 0) dir = scale(dir, -1); // p intruded: flip to point outward
  return add(bestClose, scale(dir, delta));
}
