/**
 * Minimal limited-memory BFGS (L-BFGS) optimizer for tf.js variables.
 * - Flattens active variables into a single state vector.
 * - Uses a simple backtracking line-search with Armijo condition.
 * - History size m controls memory of curvature pairs (s, y).
 *
 * This is intentionally compact and not as robust as scipy's;
 * it is intended for interactive lab use.
 */
export class OptimizerLbfgs {
  constructor(opts = {}) {
    this.name = 'lbfgs';
    this.m = opts.historySize ?? 10;
    this.maxLineSearch = opts.maxLineSearch ?? 20;
    this.c1 = opts.c1 ?? 1e-4;
    this.initStep = opts.initStep ?? 1.0;
    this._s = []; // list of Float32Array deltas
    this._y = []; // list of Float32Array grad deltas
    this._rho = []; // list of 1/(y.s)
    this._prevX = null;
    this._prevG = null;
  }

  reset() {
    this._s = [];
    this._y = [];
    this._rho = [];
    this._prevX = null;
    this._prevG = null;
  }

  setLearningRate(lr) {
    // Used as an initial step scale.
    this.initStep = lr > 0 ? lr * 50 : 1.0;
  }

  dispose() {
    this.reset();
  }

  // ---- internals ----
  _flatten(vars) {
    const sizes = vars.map((v) => v.size);
    const total = sizes.reduce((a, b) => a + b, 0);
    const out = new Float32Array(total);
    let off = 0;
    for (let i = 0; i < vars.length; i++) {
      const arr = vars[i].dataSync();
      out.set(arr, off);
      off += sizes[i];
    }
    return { vec: out, sizes };
  }

  _unflattenAssign(vars, vec, sizes) {
    let off = 0;
    for (let i = 0; i < vars.length; i++) {
      const sl = vec.subarray(off, off + sizes[i]);
      vars[i].assign(tf.tensor(sl, vars[i].shape));
      off += sizes[i];
    }
  }

  _computeLossAndGrad(lossFn, vars) {
    const { value, grads } = tf.variableGrads(lossFn, vars);
    const loss = value.dataSync()[0];
    value.dispose();
    // gradient flat
    const sizes = vars.map((v) => v.size);
    const total = sizes.reduce((a, b) => a + b, 0);
    const g = new Float32Array(total);
    let off = 0;
    for (const v of vars) {
      const name = v.name;
      const gT = grads[name];
      const arr = gT.dataSync();
      g.set(arr, off);
      off += v.size;
      gT.dispose();
    }
    return { loss, g };
  }

  _twoLoop(g) {
    const q = new Float32Array(g);
    const k = this._s.length;
    const alpha = new Float64Array(k);
    for (let i = k - 1; i >= 0; i--) {
      let dot = 0;
      const s = this._s[i];
      for (let j = 0; j < s.length; j++) dot += s[j] * q[j];
      alpha[i] = this._rho[i] * dot;
      const y = this._y[i];
      for (let j = 0; j < q.length; j++) q[j] -= alpha[i] * y[j];
    }
    // Initial Hessian scaling H0 = (s_k . y_k) / (y_k . y_k)
    let gamma = 1.0;
    if (k > 0) {
      const s = this._s[k - 1],
        y = this._y[k - 1];
      let sy = 0,
        yy = 0;
      for (let j = 0; j < s.length; j++) {
        sy += s[j] * y[j];
        yy += y[j] * y[j];
      }
      if (yy > 1e-18) gamma = sy / yy;
    }
    for (let j = 0; j < q.length; j++) q[j] *= gamma;
    for (let i = 0; i < k; i++) {
      let dot = 0;
      const y = this._y[i];
      for (let j = 0; j < y.length; j++) dot += y[j] * q[j];
      const beta = this._rho[i] * dot;
      const s = this._s[i];
      for (let j = 0; j < q.length; j++) q[j] += (alpha[i] - beta) * s[j];
    }
    // search dir is -q
    for (let j = 0; j < q.length; j++) q[j] = -q[j];
    return q;
  }

  step(lossFn, activeVars) {
    if (activeVars.length === 0) {
      const lossT = tf.tidy(() => lossFn());
      const v = lossT.dataSync()[0];
      lossT.dispose();
      return v;
    }
    const { vec: x, sizes } = this._flatten(activeVars);
    const { loss: f0, g: g0 } = this._computeLossAndGrad(lossFn, activeVars);

    // Update curvature pairs s_k, y_k from previous step
    if (this._prevX && this._prevG) {
      const s = new Float32Array(x.length);
      const y = new Float32Array(x.length);
      let sy = 0;
      for (let j = 0; j < x.length; j++) {
        s[j] = x[j] - this._prevX[j];
        y[j] = g0[j] - this._prevG[j];
        sy += s[j] * y[j];
      }
      if (sy > 1e-10) {
        this._s.push(s);
        this._y.push(y);
        this._rho.push(1.0 / sy);
        if (this._s.length > this.m) {
          this._s.shift();
          this._y.shift();
          this._rho.shift();
        }
      }
    }

    // Search direction
    const d = this._twoLoop(g0);
    // Directional derivative
    let gd = 0;
    for (let j = 0; j < d.length; j++) gd += g0[j] * d[j];
    if (gd >= 0) {
      // Not a descent direction. Reset memory and use -grad.
      this._s = [];
      this._y = [];
      this._rho = [];
      for (let j = 0; j < d.length; j++) d[j] = -g0[j];
      gd = 0;
      for (let j = 0; j < d.length; j++) gd += g0[j] * d[j];
    }

    // Backtracking line search
    let step = this.initStep;
    const xNew = new Float32Array(x.length);
    let fNew = f0;
    let ok = false;
    for (let ls = 0; ls < this.maxLineSearch; ls++) {
      for (let j = 0; j < x.length; j++) xNew[j] = x[j] + step * d[j];
      this._unflattenAssign(activeVars, xNew, sizes);
      const lossT = tf.tidy(() => lossFn());
      fNew = lossT.dataSync()[0];
      lossT.dispose();
      if (isFinite(fNew) && fNew <= f0 + this.c1 * step * gd) {
        ok = true;
        break;
      }
      step *= 0.5;
    }
    if (!ok) {
      // Revert
      this._unflattenAssign(activeVars, x, sizes);
      fNew = f0;
    }

    // Save for next iteration
    this._prevX = ok ? xNew.slice() : x;
    this._prevG = g0;
    return fNew;
  }
}
