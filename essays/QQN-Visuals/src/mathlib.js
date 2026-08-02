// Minimal, inspectable linear-algebra helpers (2D focus).
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1];
export const norm = (a) => Math.hypot(a[0], a[1]);
export const scale = (a, s) => [a[0] * s, a[1] * s];
export const add = (a, b) => [a[0] + b[0], a[1] + b[1]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1]];
export const neg = (a) => [-a[0], -a[1]];

// Solve 2x2 linear system A x = b. A = [[a,b],[c,d]].
export function solve2x2(A, rhs) {
  const [a, b, c, d] = [A[0][0], A[0][1], A[1][0], A[1][1]];
  const det = a * d - b * c;
  if (Math.abs(det) < 1e-12) return [0, 0];
  return [(d * rhs[0] - b * rhs[1]) / det, (a * rhs[1] - c * rhs[0]) / det];
}

// Multiply 2x2 matrix (row-major array [[..],[..]]) by vector.
export function matVec(M, v) {
  return [M[0][0] * v[0] + M[0][1] * v[1], M[1][0] * v[0] + M[1][1] * v[1]];
}

// Real roots of a t^2 + b t + c = 0, returned within [lo, hi] if given.
export function quadraticRoots(a, b, c, lo = -Infinity, hi = Infinity) {
  const out = [];
  if (Math.abs(a) < 1e-12) {
    if (Math.abs(b) > 1e-12) out.push(-c / b);
  } else {
    const disc = b * b - 4 * a * c;
    if (disc >= 0) {
      const s = Math.sqrt(disc);
      out.push((-b + s) / (2 * a));
      out.push((-b - s) / (2 * a));
    }
  }
  return out.filter((r) => r >= lo && r <= hi);
}

export const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));

// Deterministic pseudo-random (mulberry32) for reproducible noise.
export function makeRng(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
