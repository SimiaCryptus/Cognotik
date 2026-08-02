// Landscape library: each exposes f, grad, hess (closed form) in 2D.
import { makeRng } from './mathlib.js';

function makeLandscape(name, def) {
  return { name, ...def };
}

export const LANDSCAPES = {
  quadratic: makeLandscape('Quadratic bowl', {
    label: 'Quadratic bowl',
    domain: [-2, 2, -2, 2],
    f: (x, y) => x * x + y * y,
    grad: (x, y) => [2 * x, 2 * y],
    hess: () => [
      [2, 0],
      [0, 2],
    ],
    start: [-1.5, 1.3],
  }),

  illcond: makeLandscape('Ill-conditioned', {
    label: 'Ill-conditioned',
    domain: [-2, 2, -2, 2],
    f: (x, y) => 0.5 * x * x + 25 * y * y,
    grad: (x, y) => [x, 50 * y],
    hess: () => [
      [1, 0],
      [0, 50],
    ],
    start: [-1.8, 0.9],
  }),

  rosenbrock: makeLandscape('Rosenbrock', {
    label: 'Rosenbrock',
    domain: [-2, 2, -1, 3],
    f: (x, y) => (1 - x) ** 2 + 100 * (y - x * x) ** 2,
    grad: (x, y) => [-2 * (1 - x) - 400 * x * (y - x * x), 200 * (y - x * x)],
    hess: (x, y) => [
      [2 - 400 * (y - 3 * x * x), -400 * x],
      [-400 * x, 200],
    ],
    start: [-1.2, 1.0],
  }),

  saddle: makeLandscape('Saddle', {
    label: 'Saddle',
    domain: [-2, 2, -2, 2],
    f: (x, y) => x * x - y * y,
    grad: (x, y) => [2 * x, -2 * y],
    hess: () => [
      [2, 0],
      [0, -2],
    ],
    start: [-1.4, 0.15],
  }),

  multimodal: makeLandscape('Multi-modal', {
    label: 'Multi-modal',
    domain: [-3, 3, -3, 3],
    f: (x, y) => Math.sin(x) * Math.cos(y) + 0.1 * (x * x + y * y),
    grad: (x, y) => [Math.cos(x) * Math.cos(y) + 0.2 * x, -Math.sin(x) * Math.sin(y) + 0.2 * y],
    hess: (x, y) => [
      [-Math.sin(x) * Math.cos(y) + 0.2, -Math.cos(x) * Math.sin(y)],
      [-Math.cos(x) * Math.sin(y), -Math.sin(x) * Math.cos(y) + 0.2],
    ],
    start: [-2.0, 2.0],
  }),

  noisy: makeLandscape('Noisy convex', {
    label: 'Noisy convex',
    domain: [-2, 2, -2, 2],
    noisy: true,
    f: (x, y) => x * x + y * y,
    // Deterministic-per-seed noisy gradient
    grad(x, y, seed = 1) {
      const rng = makeRng(seed + Math.round(x * 1000) + Math.round(y * 1000) * 7919);
      const nx = (rng() - 0.5) * 1.5;
      const ny = (rng() - 0.5) * 1.5;
      return [2 * x + nx, 2 * y + ny];
    },
    hess: () => [
      [2, 0],
      [0, 2],
    ],
    start: [-1.6, 1.4],
  }),
};

export const LANDSCAPE_KEYS = Object.keys(LANDSCAPES);
