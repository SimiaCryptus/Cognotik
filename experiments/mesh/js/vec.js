// js/vec.js
// Minimal, allocation-friendly 3-vector helpers shared by the CCD /
// resolution / trust modules. Vectors are plain arrays [x, y, z].

export const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const scale = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
export const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
export const cross = (a, b) => [
  a[1] * b[2] - a[2] * b[1],
  a[2] * b[0] - a[0] * b[2],
  a[0] * b[1] - a[1] * b[0],
];
export const norm = (a) => Math.sqrt(dot(a, a));
export const normalize = (a) => {
  const n = norm(a);
  return n > 0 ? scale(a, 1 / n) : [0, 0, 0];
};
export const clamp = (x, lo, hi) => (x < lo ? lo : x > hi ? hi : x);

// Unit outward normal of triangle (a,b,c) — orientation from vertex order.
export const triNormal = (a, b, c) => normalize(cross(sub(b, a), sub(c, a)));
