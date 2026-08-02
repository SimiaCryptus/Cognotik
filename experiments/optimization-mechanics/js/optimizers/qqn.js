// QQN: quadratic path between steepest descent and L-BFGS directions,
// with a configurable line search over t in [0,1].
import { LBFGS } from './lbfgs.js';

export class QQN {
  constructor(params = {}) {
    this.name = 'QQN';
    this.params = {
      lr: params.lr ?? 0.05,
      m: params.m ?? 8,
      // oracle: which second direction to blend against steepest descent.
      //   'lbfgs'    -> quasi-Newton (default)
      //   'momentum' -> heavy-ball velocity
      //   'gradient' -> degenerate: oracle == steepest descent
      oracle: params.oracle ?? 'lbfgs',
      momentumBeta: params.momentumBeta ?? 0.9,
      // line search configuration
      lineSearch: params.lineSearch ?? 'golden', // 'golden' | 'backtracking'
      maxLineSearch: params.maxLineSearch ?? 30,
      lineSearchTol: params.lineSearchTol ?? 1e-3,
      // backtracking-specific
      btShrink: params.btShrink ?? 0.5,
      btC: params.btC ?? 1e-4,
      verbose: params.verbose ?? false,
    };
    this.lbfgs = new LBFGS({ lr: 1.0, m: this.params.m });
    this.velocity = [0, 0];
    this.stepCount = 0;
    this.reset(0, 0);
  }

  reset(x0, y0) {
    this.x = x0;
    this.y = y0;
    this.velocity = [0, 0];
    this.stepCount = 0;
    this.lbfgs.reset(x0, y0);
  }

  // Compute the oracle direction (the t=1 endpoint) for the chosen oracle.
  oracleDirection(g, d_sd) {
    const p = this.params;
    if (p.oracle === 'gradient') {
      return d_sd.slice();
    }
    if (p.oracle === 'momentum') {
      const b = p.momentumBeta;
      this.velocity[0] = b * this.velocity[0] + (1 - b) * g[0];
      this.velocity[1] = b * this.velocity[1] + (1 - b) * g[1];
      return [-p.lr * this.velocity[0], -p.lr * this.velocity[1]];
    }
    // default: L-BFGS
    let d_lbfgs = this.lbfgs.direction(g);
    if (this.lbfgs.s.length === 0) d_lbfgs = d_sd.slice();
    if (!isFiniteVec(d_lbfgs)) {
      console.warn(
        '[QQN] oracle produced non-finite direction; ' + 'falling back to steepest descent',
        { d_lbfgs, g }
      );
      d_lbfgs = d_sd.slice();
    }
    return d_lbfgs;
  }

  step(obj) {
    this.stepCount++;
    const from = [this.x, this.y];
    const g = obj.grad(this.x, this.y);
    if (!isFiniteVec(g)) {
      console.warn('[QQN] non-finite gradient; skipping step', { from, g });
      return { from, to: from.slice(), grad: [0, 0] };
    }

    // update L-BFGS history from previous move
    if (this.lbfgs.prevX) {
      const s = [this.x - this.lbfgs.prevX[0], this.y - this.lbfgs.prevX[1]];
      const yv = [g[0] - this.lbfgs.prevG[0], g[1] - this.lbfgs.prevG[1]];
      const sy = dot(s, yv);
      if (sy > 1e-10) {
        this.lbfgs.s.push(s);
        this.lbfgs.yv.push(yv);
        if (this.lbfgs.s.length > this.params.m) {
          this.lbfgs.s.shift();
          this.lbfgs.yv.shift();
        }
      } else if (this.params.verbose) {
        console.debug('[QQN] curvature condition failed, skipping pair', { sy });
      }
    }

    // steepest descent direction (scaled by lr)
    const d_sd = [-this.params.lr * g[0], -this.params.lr * g[1]];

    // oracle direction (t=1 endpoint)
    const d_lbfgs = this.oracleDirection(g, d_sd);

    // quadratic path: step(t) = t(1-t) d_sd + t^2 d_oracle
    const pathPoint = (t) => [
      this.x + t * (1 - t) * d_sd[0] + t * t * d_lbfgs[0],
      this.y + t * (1 - t) * d_sd[1] + t * t * d_lbfgs[1],
    ];

    // sample path for rendering
    const path = [];
    for (let i = 0; i <= 20; i++) {
      const t = i / 20;
      const pt = pathPoint(t);
      path.push([pt[0], pt[1]]);
    }

    // line search over t in [0,1]
    const probes = [];
    const f0 = obj.value(this.x, this.y);
    const f = (t) => {
      const pt = pathPoint(t);
      let loss = obj.value(pt[0], pt[1]);
      if (!Number.isFinite(loss)) loss = Infinity;
      probes.push({ x: pt[0], y: pt[1], t, loss });
      return loss;
    };

    let chosenT;
    const p = this.params;
    if (p.lineSearch === 'backtracking') {
      chosenT = backtracking(f, f0, p.maxLineSearch, p.btShrink, p.btC);
    } else {
      chosenT = goldenSection(f, 0, 1, p.lineSearchTol, p.maxLineSearch);
    }
    if (!Number.isFinite(chosenT)) {
      console.warn('[QQN] line search returned non-finite t; using 0', { chosenT });
      chosenT = 0;
    }

    const to = pathPoint(chosenT);
    if (!isFiniteVec(to)) {
      console.warn('[QQN] non-finite candidate; staying put', { to, chosenT });
      return { from, to: from.slice(), grad: g, path, probes, chosenT: 0 };
    }

    const oracle = [this.x + d_lbfgs[0], this.y + d_lbfgs[1]];

    this.lbfgs.prevX = [this.x, this.y];
    this.lbfgs.prevG = g;
    this.x = to[0];
    this.y = to[1];

    if (p.verbose) {
      console.debug('[QQN] step', {
        n: this.stepCount,
        chosenT,
        f0,
        f1: obj.value(to[0], to[1]),
        probes: probes.length,
        histLen: this.lbfgs.s.length,
      });
    }

    return { from, to, grad: g, oracle, path, probes, chosenT };
  }

  getState() {
    return { pos: [this.x, this.y], histLen: this.lbfgs.s.length };
  }
}

function dot(a, b) {
  return a[0] * b[0] + a[1] * b[1];
}

function isFiniteVec(v) {
  return Number.isFinite(v[0]) && Number.isFinite(v[1]);
}

function goldenSection(f, a, b, tol, maxIter) {
  const gr = (Math.sqrt(5) - 1) / 2;
  let c = b - gr * (b - a);
  let d = a + gr * (b - a);
  let fc = f(c),
    fd = f(d);
  for (let i = 0; i < maxIter && b - a > tol; i++) {
    if (fc < fd) {
      b = d;
      d = c;
      fd = fc;
      c = b - gr * (b - a);
      fc = f(c);
    } else {
      a = c;
      c = d;
      fc = fd;
      d = a + gr * (b - a);
      fd = f(d);
    }
  }
  return (a + b) / 2;
}

// Backtracking along t in [0,1]: start at t=1, shrink until sufficient
// decrease vs. f0 is achieved (or max iterations reached).
function backtracking(f, f0, maxIter, shrink, c) {
  let t = 1;
  for (let i = 0; i < maxIter; i++) {
    const fv = f(t);
    // simple sufficient-decrease relative to f0
    if (fv <= f0 - c * t * Math.abs(f0 - fv) || fv < f0) return t;
    t *= shrink;
    if (t < 1e-6) break;
  }
  return t;
}
