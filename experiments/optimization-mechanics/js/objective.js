// Composable loss surface + analytic gradients.
import { valueNoise, latticeMask } from './noise.js';

// Closed-form analytic landscapes (ported from QQN-Visuals).
// Each provides f(x,y) and grad(x,y) => [gx, gy].
export const BASE_FIELDS = {
  bowl: { label: 'Quadratic Bowl' },
  linear: { label: 'Linear Flow' },
  rosenbrock: {
    label: 'Rosenbrock',
    f: (x, y) => (1 - x) ** 2 + 100 * (y - x * x) ** 2,
    grad: (x, y) => [-2 * (1 - x) - 400 * x * (y - x * x), 200 * (y - x * x)],
  },
  saddle: {
    label: 'Saddle',
    f: (x, y) => x * x - y * y,
    grad: (x, y) => [2 * x, -2 * y],
  },
  illcond: {
    label: 'Ill-conditioned',
    f: (x, y) => 0.5 * x * x + 25 * y * y,
    grad: (x, y) => [x, 50 * y],
  },
  multimodal: {
    label: 'Multi-modal',
    f: (x, y) => Math.sin(x) * Math.cos(y) + 0.1 * (x * x + y * y),
    grad: (x, y) => [Math.cos(x) * Math.cos(y) + 0.2 * x, -Math.sin(x) * Math.sin(y) + 0.2 * y],
  },
};

export class Objective {
  constructor(config) {
    this.config = config;
  }

  value(x, y) {
    const c = this.config;
    const g = c.global || { sx: 1, sy: 1, sz: 1 };
    let v = 0;
    if (c.base === 'bowl') {
      const { kx, ky, cx, cy } = c.bowl;
      v += kx * (x - cx) ** 2 + ky * (y - cy) ** 2;
    } else if (c.base === 'linear') {
      const { a, b } = c.linear;
      v += a * x + b * y;
    } else {
      const field = BASE_FIELDS[c.base];
      if (field && field.f) {
        const s = (c.analytic && c.analytic.scale) || 1;
        v += s * field.f(x, y);
      }
    }
    if (c.noiseOn) {
      const { amp, fx, fy, seed } = c.noise;
      v += amp * valueNoise(g.sx * fx * x, g.sy * fy * y, seed).v;
    }
    if (c.latticeOn) {
      const { L, f, cgx, cgy, offset, seed } = c.lattice;
      const m = latticeMask(g.sx * x, g.sy * y, L, f, seed, offset).val;
      v += m * (cgx * x + cgy * y);
    }
    return g.sz * v;
  }

  grad(x, y) {
    const c = this.config;
    const g = c.global || { sx: 1, sy: 1, sz: 1 };
    let gx = 0,
      gy = 0;
    if (c.base === 'bowl') {
      const { kx, ky, cx, cy } = c.bowl;
      gx += 2 * kx * (x - cx);
      gy += 2 * ky * (y - cy);
    } else if (c.base === 'linear') {
      const { a, b } = c.linear;
      gx += a;
      gy += b;
    } else {
      const field = BASE_FIELDS[c.base];
      if (field && field.grad) {
        const s = (c.analytic && c.analytic.scale) || 1;
        const ag = field.grad(x, y);
        gx += s * ag[0];
        gy += s * ag[1];
      }
    }
    if (c.noiseOn) {
      const { amp, fx, fy, seed } = c.noise;
      const n = valueNoise(g.sx * fx * x, g.sy * fy * y, seed);
      gx += amp * n.dvx * fx * g.sx;
      gy += amp * n.dvy * fy * g.sy;
    }
    if (c.latticeOn) {
      const { L, f, cgx, cgy, offset, seed } = c.lattice;
      const m = latticeMask(g.sx * x, g.sy * y, L, f, seed, offset);
      const inner = cgx * x + cgy * y;
      gx += m.dx * g.sx * inner + m.val * cgx;
      gy += m.dy * g.sy * inner + m.val * cgy;
    }
    return [gx * g.sz, gy * g.sz];
  }

  // Numerical gradient for verification.
  gradFD(x, y, h = 1e-4) {
    const dx = (this.value(x + h, y) - this.value(x - h, y)) / (2 * h);
    const dy = (this.value(x, y + h) - this.value(x, y - h)) / (2 * h);
    return [dx, dy];
  }
}
