// Uniform / adaptive / curvature-based sampling along a manifold.
//
// A manifold is any object exposing `at(t)` for t in [0,1] returning an
// OKLab point. Samplers return arrays of OKLab points.

import { deltaEOK } from '../colorspace/distance.js';

// Evenly spaced in parameter t (endpoints included when `closed` is false).
export function uniform(manifold, count, { closed = false } = {}) {
  if (count < 1) throw new Error('uniform: count must be >= 1');
  const out = [];
  for (let i = 0; i < count; i++) {
    const denom = closed ? count : Math.max(1, count - 1);
    const t = closed ? i / denom : i / denom;
    out.push(manifold.at(t));
  }
  return out;
}

// Approximately arc-length-uniform sampling: densely sample the parameter,
// accumulate perceptual arc length, then pick `count` points equally spaced
// along that length.
export function arcLength(manifold, count, { resolution = 512 } = {}) {
  if (count < 2) return uniform(manifold, count);
  const ts = [];
  const pts = [];
  const cum = [0];
  for (let i = 0; i <= resolution; i++) {
    const t = i / resolution;
    const p = manifold.at(t);
    ts.push(t);
    pts.push(p);
    if (i > 0) {
      cum.push(cum[i - 1] + deltaEOK(pts[i - 1], pts[i]));
    }
  }
  const total = cum[cum.length - 1];
  const out = [];
  for (let k = 0; k < count; k++) {
    const target = (total * k) / (count - 1);
    // binary search for the segment containing `target`
    let lo = 0;
    let hi = cum.length - 1;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (cum[mid] < target) lo = mid + 1;
      else hi = mid;
    }
    const i = Math.max(1, lo);
    const segLen = cum[i] - cum[i - 1] || 1;
    const frac = (target - cum[i - 1]) / segLen;
    const t = ts[i - 1] + (ts[i] - ts[i - 1]) * frac;
    out.push(manifold.at(t));
  }
  return out;
}

// Curvature-adaptive: sample more densely where the second difference (a
// proxy for curvature) in OKLab is large. Returns approximately `count`
// points. Falls back to uniform if the manifold is nearly straight.
export function curvature(manifold, count, { resolution = 256 } = {}) {
  if (count < 3) return uniform(manifold, count);
  const pts = [];
  for (let i = 0; i <= resolution; i++) {
    pts.push(manifold.at(i / resolution));
  }
  const weights = new Array(resolution + 1).fill(0);
  for (let i = 1; i < resolution; i++) {
    const prev = pts[i - 1];
    const cur = pts[i];
    const next = pts[i + 1];
    const curv =
      Math.abs(next.L - 2 * cur.L + prev.L) +
      Math.abs(next.a - 2 * cur.a + prev.a) +
      Math.abs(next.b - 2 * cur.b + prev.b);
    weights[i] = curv + 1e-6;
  }
  weights[0] = weights[1];
  weights[resolution] = weights[resolution - 1];

  const cum = [0];
  for (let i = 1; i <= resolution; i++) {
    cum.push(cum[i - 1] + weights[i]);
  }
  const total = cum[resolution];
  const out = [];
  for (let k = 0; k < count; k++) {
    const target = (total * k) / (count - 1);
    let lo = 0;
    let hi = resolution;
    while (lo < hi) {
      const mid = (lo + hi) >> 1;
      if (cum[mid] < target) lo = mid + 1;
      else hi = mid;
    }
    out.push(pts[Math.min(lo, resolution)]);
  }
  return out;
}
