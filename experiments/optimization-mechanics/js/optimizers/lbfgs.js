// Pure-JS 2D L-BFGS with two-loop recursion.

export class LBFGS {
  constructor(params = {}) {
    this.name = 'L-BFGS';
    this.params = {
      lr: params.lr ?? 1.0,
      m: params.m ?? 8,
    };
    this.reset(0, 0);
  }

  reset(x0, y0) {
    this.x = x0;
    this.y = y0;
    this.s = [];
    this.yv = []; // history vectors
    this.prevX = null;
    this.prevG = null;
  }

  // two-loop recursion: returns search direction (descent)
  direction(g) {
    const q = g.slice();
    const m = this.s.length;
    const alpha = new Array(m);
    const rho = this.s.map((s, i) => 1 / dot(this.yv[i], s));
    for (let i = m - 1; i >= 0; i--) {
      alpha[i] = rho[i] * dot(this.s[i], q);
      q[0] -= alpha[i] * this.yv[i][0];
      q[1] -= alpha[i] * this.yv[i][1];
    }
    // initial Hessian scaling
    let gamma = 1;
    if (m > 0) {
      const last = m - 1;
      gamma = dot(this.s[last], this.yv[last]) / dot(this.yv[last], this.yv[last]);
    }
    const r = [q[0] * gamma, q[1] * gamma];
    for (let i = 0; i < m; i++) {
      const beta = rho[i] * dot(this.yv[i], r);
      r[0] += (alpha[i] - beta) * this.s[i][0];
      r[1] += (alpha[i] - beta) * this.s[i][1];
    }
    return [-r[0], -r[1]]; // descent direction
  }

  step(obj) {
    const from = [this.x, this.y];
    const g = obj.grad(this.x, this.y);

    // update history using previous step
    if (this.prevX) {
      const s = [this.x - this.prevX[0], this.y - this.prevX[1]];
      const yv = [g[0] - this.prevG[0], g[1] - this.prevG[1]];
      if (dot(s, yv) > 1e-10) {
        this.s.push(s);
        this.yv.push(yv);
        if (this.s.length > this.params.m) {
          this.s.shift();
          this.yv.shift();
        }
      }
    }

    let d = this.direction(g);
    if (this.s.length === 0) {
      d = [-g[0], -g[1]];
    }

    // simple backtracking line search for stability
    const alpha = this.backtrack(obj, [this.x, this.y], d, g);
    const oracle = [this.x + d[0], this.y + d[1]];

    this.prevX = [this.x, this.y];
    this.prevG = g;
    this.x += alpha * d[0];
    this.y += alpha * d[1];

    return { from, to: [this.x, this.y], grad: g, oracle };
  }

  backtrack(obj, p, d, g) {
    let alpha = this.params.lr;
    const f0 = obj.value(p[0], p[1]);
    const gd = dot(g, d);
    const c = 1e-4;
    for (let i = 0; i < 20; i++) {
      const nx = p[0] + alpha * d[0],
        ny = p[1] + alpha * d[1];
      if (obj.value(nx, ny) <= f0 + c * alpha * gd) break;
      alpha *= 0.5;
    }
    return alpha;
  }

  getState() {
    return { pos: [this.x, this.y], histLen: this.s.length };
  }
}

function dot(a, b) {
  return a[0] * b[0] + a[1] * b[1];
}
