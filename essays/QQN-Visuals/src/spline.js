// Cubic Hermite spline over 1-D probes (t, f, slope) with reflection rule.
import { quadraticRoots } from './mathlib.js';

// Evaluate a Hermite segment between (t0,f0,m0) and (t1,f1,m1).
function hermite(t0, f0, m0, t1, f1, m1, t) {
  const h = t1 - t0;
  const s = (t - t0) / h;
  const h00 = 2 * s ** 3 - 3 * s ** 2 + 1;
  const h10 = s ** 3 - 2 * s ** 2 + s;
  const h01 = -2 * s ** 3 + 3 * s ** 2;
  const h11 = s ** 3 - s ** 2;
  return h00 * f0 + h10 * h * m0 + h01 * f1 + h11 * h * m1;
}

// Stationary points of a Hermite segment (derivative = 0), a quadratic in s.
function segmentStationary(t0, f0, m0, t1, f1, m1) {
  const h = t1 - t0;
  // dP/ds = 6(s^2-s)(f0? ...) — express derivative w.r.t s and solve.
  // P(s) = h00 f0 + h10 h m0 + h01 f1 + h11 h m1
  // dP/ds coefficients (in s): a s^2 + b s + c
  const a = 6 * f0 + 3 * h * m0 - 6 * f1 + 3 * h * m1;
  const b = -6 * f0 - 4 * h * m0 + 6 * f1 - 2 * h * m1;
  const c = h * m0;
  const roots = quadraticRoots(a, b, c, 0, 1);
  return roots.map((s) => t0 + s * h);
}

// Apply the upstream/downstream symmetry (reflection) rule.
// If a tangent opposes the segment's secant slope, reflect its sign.
export function applyReflection(points, enable) {
  const out = points.map((p) => ({ ...p }));
  if (!enable || out.length < 2) return out;
  for (let i = 0; i < out.length - 1; i++) {
    const p0 = out[i],
      p1 = out[i + 1];
    const h = p1.t - p0.t;
    const secant = Math.abs(h) < 1e-9 ? 0 : (p1.f - p0.f) / h;
    // Flat-secant caveat: near a minimum (Δ≈0) reflection is disabled.
    if (Math.abs(secant) < 1e-6) continue;
    // downstream tangent of p0 opposes secant?
    if (p0.m * secant < 0) p0.mReflected = -p0.m;
    // upstream tangent of p1 opposes secant?
    if (p1.m * secant < 0) p1.mReflected = -p1.m;
  }
  return out;
}

export function buildSpline(rawPoints, { reflect = true } = {}) {
  const pts = [...rawPoints].sort((a, b) => a.t - b.t);
  const reflected = applyReflection(pts, reflect);
  const stationary = [];
  for (let i = 0; i < reflected.length - 1; i++) {
    const p0 = reflected[i],
      p1 = reflected[i + 1];
    const m0 = p0.mReflected != null ? p0.mReflected : p0.m;
    const m1 = p1.mReflected != null ? p1.mReflected : p1.m;
    stationary.push(...segmentStationary(p0.t, p0.f, m0, p1.t, p1.f, m1));
  }
  return {
    points: reflected,
    stationary,
    eval(t) {
      for (let i = 0; i < reflected.length - 1; i++) {
        const p0 = reflected[i],
          p1 = reflected[i + 1];
        if (t >= p0.t && t <= p1.t) {
          const m0 = p0.mReflected != null ? p0.mReflected : p0.m;
          const m1 = p1.mReflected != null ? p1.mReflected : p1.m;
          return hermite(p0.t, p0.f, m0, p1.t, p1.f, m1, t);
        }
      }
      return NaN;
    },
  };
}
