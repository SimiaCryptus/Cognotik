/**
 * QQN — Quadratic-interpolation Quasi-Newton.
 *
 * A simple variant: at each step, build a quadratic path that interpolates
 * between the negative gradient direction (small t) and the L-BFGS quasi-Newton
 * direction (t=1):
 *
 *     p(t) = t * (1 - t) * (-g_normalized) + t^2 * d_qn
 *
 * Then search along t in (0,1]; this preserves descent properties of -g near 0
 * while permitting Newton-like jumps near t=1.
 *
 * For the first iteration (no curvature history), p(t) = -t*g.
 */
export class OptimizerQQN {
  constructor(opts = {}) {
    this.name = 'qqn';
    this.m = opts.historySize ?? 10;
    this.maxLineSearch = opts.maxLineSearch ?? 20;
    this.c1 = opts.c1 ?? 1e-4;
    this.initStep = opts.initStep ?? 1.0;
    this._s = [];
    this._y = [];
    this._rho = [];
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
    this.initStep = lr > 0 ? lr * 50 : 1.0;
  }

  dispose() {
    this.reset();
  }

  _flatten(vars) {
    const sizes = vars.map((v) => v.size);
    const total = sizes.reduce((a, b) => a + b, 0);
    const out = new Float32Array(total);
    let off = 0;
    for (let i = 0; i < vars.length; i++) {
      out.set(vars[i].dataSync(), off);
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
    const sizes = vars.map((v) => v.size);
    const total = sizes.reduce((a, b) => a + b, 0);
    const g = new Float32Array(total);
    let off = 0;
    for (const v of vars) {
      const gT = grads[v.name];
      g.set(gT.dataSync(), off);
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
    // -q = quasi-Newton search direction
    const qn = new Float32Array(q.length);
    for (let j = 0; j < q.length; j++) qn[j] = -q[j];
    return qn;
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

    // Update curvature pairs from previous step
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

    // Normalize gradient direction
    let gnorm = 0;
    for (let j = 0; j < g0.length; j++) gnorm += g0[j] * g0[j];
    gnorm = Math.sqrt(gnorm) + 1e-12;
    const gHat = new Float32Array(g0.length);
    const scale = this.initStep / gnorm; // scale gradient leg to a reasonable size
    for (let j = 0; j < g0.length; j++) gHat[j] = g0[j] * scale; // small step in g

    let dQN = null;
    if (this._s.length > 0) {
      dQN = this._twoLoop(g0);
      // Ensure descent
      let gd = 0;
      for (let j = 0; j < dQN.length; j++) gd += g0[j] * dQN[j];
      if (gd >= 0) dQN = null;
    }

    // Path p(t):
    //   if dQN available:  p(t) = -t*(1-t)*gHat + t^2 * dQN
    //   else:              p(t) = -t*gHat   (pure gradient)
    const xNew = new Float32Array(x.length);
    const tryT = (t) => {
      if (dQN) {
        const a = t * (1 - t),
          b = t * t;
        for (let j = 0; j < x.length; j++) xNew[j] = x[j] + (-a * gHat[j] + b * dQN[j]);
      } else {
        for (let j = 0; j < x.length; j++) xNew[j] = x[j] - t * gHat[j];
      }
    };

    // Approx directional derivative at t=0:
    //   d/dt p(t)|_{t=0} = -gHat (both branches)
    //   so g·p'(0) = -g·gHat = -gnorm * initStep < 0 (descent guaranteed)
    const gd0 = -gnorm * this.initStep;

    let t = 1.0,
      fNew = f0,
      ok = false;
    for (let ls = 0; ls < this.maxLineSearch; ls++) {
      tryT(t);
      this._unflattenAssign(activeVars, xNew, sizes);
      const lossT = tf.tidy(() => lossFn());
      fNew = lossT.dataSync()[0];
      lossT.dispose();
      if (isFinite(fNew) && fNew <= f0 + this.c1 * t * gd0) {
        ok = true;
        break;
      }
      t *= 0.5;
    }
    if (!ok) {
      this._unflattenAssign(activeVars, x, sizes);
      fNew = f0;
    }
    this._prevX = ok ? xNew.slice() : x;
    this._prevG = g0;
    return fNew;
  }
}
