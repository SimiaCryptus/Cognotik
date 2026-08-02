// Reference optimizer implementations for the demos.
import { add, sub, scale, neg, dot, norm, matVec, solve2x2 } from './mathlib.js';

// --- Gradient descent ---
export function gdStep(land, x, { eta = 0.1, seed = 1 } = {}) {
  const g = land.noisy ? land.grad(x[0], x[1], seed) : land.grad(x[0], x[1]);
  return add(x, scale(g, -eta));
}

// --- Momentum / Adam (stateful) ---
export function makeAdam({
  mode = 'adam',
  beta1 = 0.9,
  beta2 = 0.999,
  eps = 1e-8,
  lr = 0.05,
} = {}) {
  let m = [0, 0],
    v = [0, 0],
    t = 0,
    vel = [0, 0];
  return {
    step(land, x, seed = 1) {
      t += 1;
      const g = land.noisy ? land.grad(x[0], x[1], seed) : land.grad(x[0], x[1]);
      if (mode === 'momentum') {
        vel = add(scale(vel, beta1), scale(g, 1 - beta1));
        return { next: add(x, scale(vel, -lr)), vel: vel.slice() };
      }
      // adam
      m = add(scale(m, beta1), scale(g, 1 - beta1));
      v = [beta2 * v[0] + (1 - beta2) * g[0] * g[0], beta2 * v[1] + (1 - beta2) * g[1] * g[1]];
      const mh = [m[0] / (1 - beta1 ** t), m[1] / (1 - beta1 ** t)];
      const vh = [v[0] / (1 - beta2 ** t), v[1] / (1 - beta2 ** t)];
      const step = [
        (lr * mh[0]) / (Math.sqrt(vh[0]) + eps),
        (lr * mh[1]) / (Math.sqrt(vh[1]) + eps),
      ];
      return {
        next: sub(x, step),
        vel: m.slice(),
        scale: [Math.sqrt(vh[0]) + eps, Math.sqrt(vh[1]) + eps],
      };
    },
    reset() {
      m = [0, 0];
      v = [0, 0];
      t = 0;
      vel = [0, 0];
    },
  };
}

// --- L-BFGS-style oracle (here: use exact inverse Hessian as approximation
// for pedagogical clarity; falls back gracefully on indefinite H). ---
export function inverseHessianDir(land, x) {
  const g = land.grad(x[0], x[1]);
  const H = land.hess(x[0], x[1]);
  // Solve H p = -g  =>  p = -H^{-1} g  (the oracle direction -H∇f).
  const p = solve2x2(H, neg(g));
  return { g, H, dir: p };
}

// Simple two-pair L-BFGS memory for the L-BFGS section race.
export function makeLbfgs({ historySize = 5 } = {}) {
  let S = [],
    Y = [],
    prevX = null,
    prevG = null;
  return {
    step(land, x, { lineSearch = true } = {}) {
      const g = land.grad(x[0], x[1]);
      if (prevX) {
        const s = sub(x, prevX);
        const y = sub(g, prevG);
        if (dot(s, y) > 1e-8) {
          S.push(s);
          Y.push(y);
          if (S.length > historySize) {
            S.shift();
            Y.shift();
          }
        }
      }
      // two-loop recursion
      let q = g.slice();
      const alpha = [];
      for (let i = S.length - 1; i >= 0; i--) {
        const rho = 1 / dot(Y[i], S[i]);
        const a = rho * dot(S[i], q);
        alpha[i] = a;
        q = sub(q, scale(Y[i], a));
      }
      let gamma = 1;
      if (S.length) {
        const i = S.length - 1;
        gamma = dot(S[i], Y[i]) / dot(Y[i], Y[i]);
      }
      let r = scale(q, gamma);
      for (let i = 0; i < S.length; i++) {
        const rho = 1 / dot(Y[i], S[i]);
        const b = rho * dot(Y[i], r);
        r = add(r, scale(S[i], alpha[i] - b));
      }
      const dir = neg(r); // -H∇f
      // crude backtracking line search along dir
      let step = 1;
      const f0 = land.f(x[0], x[1]);
      const gd = dot(g, dir);
      for (let k = 0; k < 25 && lineSearch; k++) {
        const nx = add(x, scale(dir, step));
        if (land.f(nx[0], nx[1]) <= f0 + 1e-4 * step * gd) break;
        step *= 0.5;
      }
      prevX = x.slice();
      prevG = g.slice();
      return add(x, scale(dir, step));
    },
    reset() {
      S = [];
      Y = [];
      prevX = null;
      prevG = null;
    },
  };
}
