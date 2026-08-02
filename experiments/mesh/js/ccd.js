// js/ccd.js
// Hand-rolled continuous collision detection (math_outline.md §3).
//
//   - pointTriangleStaticTOI : affine signed-distance TOI vs. static K (§3.2a)
//   - movingPointTriangleTOI : cubic coplanarity self-collision       (§3.2b)
//   - segSegDist2            : segment-segment closest points (Ericson) (§3.3.1)
//   - edgeEdgeTOI            : epsilon-shell edge-edge TOI              (§3.3.3)
//   - rootsInUnit            : robust real roots of deg<=3 poly on [0,1]
//
// "t" parameterizes the optimization step, not a clock (idea.md §3.2).

import { add, sub, scale, dot, cross, triNormal, clamp } from './vec.js';

const EPS = 1e-14;

// ---------------------------------------------------------------------------
// Polynomial root isolation on [0,1] for degree <= 3 (used by symbolic checks
// and any caller that prefers exact coplanarity coefficients over sampling).
// Coefficients are ascending: c[0] + c[1] t + c[2] t^2 + c[3] t^3.
// ---------------------------------------------------------------------------
export function polyEval(c, t) {
  let r = 0;
  for (let i = c.length - 1; i >= 0; i--) r = r * t + c[i];
  return r;
}

function quadraticRoots(a, b, c) {
  const roots = [];
  if (Math.abs(a) < EPS) {
    if (Math.abs(b) > EPS) roots.push(-c / b);
    return roots;
  }
  const disc = b * b - 4 * a * c;
  if (disc < 0) return roots;
  const sq = Math.sqrt(disc);
  const q = -0.5 * (b + (b >= 0 ? 1 : -1) * sq); // numerically stable
  roots.push(q / a);
  if (Math.abs(q) > EPS) roots.push(c / q);
  return roots;
}

export function rootsInUnit(coeffs) {
  const c = coeffs.slice();
  while (c.length > 1 && Math.abs(c[c.length - 1]) < EPS) c.pop();
  const deg = c.length - 1;
  if (deg <= 0) return [];

  // Derivative -> monotone breakpoints in (0,1).
  const d = [];
  for (let i = 1; i < c.length; i++) d.push(i * c[i]);
  let crit = [];
  if (d.length === 2) crit = quadraticRoots(0, d[1], d[0]);
  else if (d.length === 3) crit = quadraticRoots(d[2], d[1], d[0]);

  const bps = [0, 1];
  for (const t of crit) if (t > 0 && t < 1) bps.push(t);
  bps.sort((x, y) => x - y);

  const out = [];
  for (let i = 0; i + 1 < bps.length; i++) {
    let lo = bps[i],
      hi = bps[i + 1];
    let flo = polyEval(c, lo),
      fhi = polyEval(c, hi);
    if (flo === 0 || fhi === 0 || flo * fhi < 0) {
      let a = lo,
        b = hi,
        fa = flo;
      for (let it = 0; it < 60; it++) {
        const m = 0.5 * (a + b);
        const fm = polyEval(c, m);
        if (fa * fm <= 0) b = m;
        else {
          a = m;
          fa = fm;
        }
      }
      const r = 0.5 * (a + b);
      if (out.length === 0 || Math.abs(out[out.length - 1] - r) > 1e-7) out.push(r);
    }
  }
  return out;
}

// ---------------------------------------------------------------------------
// §3.2a  Point vs. STATIC triangle: signed distance is affine in t.
//   Crossing of the offset half-space d(t) = offset (entering, decreasing).
//   Returns TOI in [0,1] confirmed by barycentric containment, else null.
// ---------------------------------------------------------------------------
export function pointPlaneTOI(p0, dp, n, q, offset = 0) {
  const d0 = dot(n, sub(p0, q));
  const d1 = dot(n, sub(add(p0, dp), q));
  if (d0 <= offset) return null; // start already at/inside the shell
  if (d1 > offset) return null; // never reaches the shell this step
  const denom = d0 - d1;
  if (Math.abs(denom) < EPS) return null; // grazing / parallel motion
  const t = (d0 - offset) / denom;
  return t >= 0 && t <= 1 ? t : null;
}

export function barycentric(p, a, b, c) {
  const v0 = sub(b, a),
    v1 = sub(c, a),
    v2 = sub(p, a);
  const d00 = dot(v0, v0),
    d01 = dot(v0, v1),
    d11 = dot(v1, v1);
  const d20 = dot(v2, v0),
    d21 = dot(v2, v1);
  const denom = d00 * d11 - d01 * d01;
  if (Math.abs(denom) < 1e-20) return null; // degenerate triangle
  const v = (d11 * d20 - d01 * d21) / denom;
  const w = (d00 * d21 - d01 * d20) / denom;
  return [1 - v - w, v, w];
}

export function pointTriangleStaticTOI(p0, dp, a, b, c, offset = 0, epsBary = 1e-6) {
  const n = triNormal(a, b, c);
  const t = pointPlaneTOI(p0, dp, n, a, offset);
  if (t === null) return null;
  const p = add(p0, scale(dp, t));
  const bc = barycentric(sub(p, scale(n, offset)), a, b, c);
  if (!bc) return null;
  const inside = bc.every((x) => x >= -epsBary && x <= 1 + epsBary);
  return inside ? t : null;
}

// ---------------------------------------------------------------------------
// §3.2b  Point vs. MOVING triangle (self-collision). Coplanarity triple
//   product is a cubic in t; we find the earliest sign change, then confirm
//   barycentric containment at that root. Sampling + bisection is robust to
//   the piecewise structure and cheap since sweeps are trust-radius bounded.
// ---------------------------------------------------------------------------
export function movingPointTriangleTOI(
  p0,
  dp,
  a0,
  da,
  b0,
  db,
  c0,
  dc,
  epsBary = 1e-6,
  samples = 16
) {
  const eval_ = (t) => {
    const p = add(p0, scale(dp, t));
    const a = add(a0, scale(da, t));
    const b = add(b0, scale(db, t));
    const c = add(c0, scale(dc, t));
    return { val: dot(sub(p, a), cross(sub(b, a), sub(c, a))), p, a, b, c };
  };
  let prev = eval_(0);
  for (let i = 1; i <= samples; i++) {
    const t = i / samples;
    const cur = eval_(t);
    if (prev.val === 0 || prev.val * cur.val < 0) {
      let lo = (i - 1) / samples,
        hi = t,
        flo = prev.val;
      for (let it = 0; it < 50; it++) {
        const m = 0.5 * (lo + hi);
        const fm = eval_(m).val;
        if (flo * fm <= 0) hi = m;
        else {
          lo = m;
          flo = fm;
        }
      }
      const tstar = 0.5 * (lo + hi);
      const s = eval_(tstar);
      const bc = barycentric(s.p, s.a, s.b, s.c);
      if (bc && bc.every((x) => x >= -epsBary && x <= 1 + epsBary)) return tstar;
    }
    prev = cur;
  }
  return null;
}

// ---------------------------------------------------------------------------
// §3.3.1  Closed segment-segment squared distance (Ericson, Real-Time
//   Collision Detection). Falls back gracefully on the parallel/degenerate
//   determinant (e.e)(f.f)-(e.f)^2 -> 0.
// ---------------------------------------------------------------------------
export function segSegDist2(p1, q1, p2, q2) {
  const d1 = sub(q1, p1),
    d2 = sub(q2, p2),
    r = sub(p1, p2);
  const a = dot(d1, d1),
    e = dot(d2, d2),
    f = dot(d2, r);
  let s, t;
  if (a <= EPS && e <= EPS) {
    s = 0;
    t = 0;
  } else if (a <= EPS) {
    s = 0;
    t = clamp(f / e, 0, 1);
  } else {
    const c = dot(d1, r);
    if (e <= EPS) {
      t = 0;
      s = clamp(-c / a, 0, 1);
    } else {
      const b = dot(d1, d2);
      const denom = a * e - b * b;
      s = denom > EPS ? clamp((b * f - c * e) / denom, 0, 1) : 0;
      t = (b * s + f) / e;
      if (t < 0) {
        t = 0;
        s = clamp(-c / a, 0, 1);
      } else if (t > 1) {
        t = 1;
        s = clamp((b - c) / a, 0, 1);
      }
    }
  }
  const c1 = add(p1, scale(d1, s));
  const c2 = add(p2, scale(d2, t));
  const diff = sub(c1, c2);
  return { dist2: dot(diff, diff), s, t, c1, c2 };
}

// ---------------------------------------------------------------------------
// §3.3.3  Edge-edge epsilon-shell TOI: earliest t in [0,1] where the
//   segment-segment distance falls to eps_shell (entering / decreasing).
//   Returns the TOI, 0 if already within the shell, or null.
// ---------------------------------------------------------------------------
export function edgeEdgeTOI(A0, A1, dA0, dA1, B0, B1, dB0, dB1, epsShell, samples = 16) {
  const eps2 = epsShell * epsShell;
  const g = (t) =>
    segSegDist2(
      add(A0, scale(dA0, t)),
      add(A1, scale(dA1, t)),
      add(B0, scale(dB0, t)),
      add(B1, scale(dB1, t))
    ).dist2 - eps2;

  let prevT = 0,
    prevG = g(0);
  if (prevG <= 0) return 0; // already grazing / inside shell
  for (let i = 1; i <= samples; i++) {
    const t = i / samples;
    const gt = g(t);
    if (gt <= 0) {
      let lo = prevT,
        hi = t,
        glo = prevG;
      for (let it = 0; it < 50; it++) {
        const m = 0.5 * (lo + hi);
        const gm = g(m);
        if (glo * gm <= 0) hi = m;
        else {
          lo = m;
          glo = gm;
        }
      }
      return 0.5 * (lo + hi);
    }
    prevT = t;
    prevG = gt;
  }
  return null;
}

// ---------------------------------------------------------------------------
// §3.1  Swept AABB for the broad phase (inflated by delta_safe + eps_shell).
// ---------------------------------------------------------------------------
export function sweptAABB(points0, deltas, pad = 0) {
  const lo = [Infinity, Infinity, Infinity];
  const hi = [-Infinity, -Infinity, -Infinity];
  const push = (p) => {
    for (let k = 0; k < 3; k++) {
      if (p[k] < lo[k]) lo[k] = p[k];
      if (p[k] > hi[k]) hi[k] = p[k];
    }
  };
  for (let i = 0; i < points0.length; i++) {
    push(points0[i]);
    push(add(points0[i], deltas[i]));
  }
  for (let k = 0; k < 3; k++) {
    lo[k] -= pad;
    hi[k] += pad;
  }
  return { lo, hi };
}

export function aabbOverlap(a, b) {
  return (
    a.lo[0] <= b.hi[0] &&
    a.hi[0] >= b.lo[0] &&
    a.lo[1] <= b.hi[1] &&
    a.hi[1] >= b.lo[1] &&
    a.lo[2] <= b.hi[2] &&
    a.hi[2] >= b.lo[2]
  );
}
