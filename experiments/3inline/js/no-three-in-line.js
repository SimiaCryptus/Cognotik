// No-Three-in-Line problem solver via continuous potential-well relaxation.
//
// Each tracked line ℓ gets a soft population p(ℓ) = Σ exp(-dᵢ²/2σ²),
// with fitness f(p) = (p-2)² so the force changes sign at p = 2:
//   p < 2 → attractive, p = 2 → neutral, p > 2 → repulsive.
//
// Total energy:
//   E = λ_line Σ_lines f(p) + λ_grid Σ_pts [1-cos(2πx)]+[1-cos(2πy)]
//       + λ_box  Σ_pts boundary(x)
//
// Lines are a dynamic set: rows, columns, ±1 diagonals, plus lines through
// every pair of currently-active points (rational slopes that matter now).
import { OptimizerLbfgs } from './optimizer-lbfgs.js';
import { OptimizerQQN } from './optimizer-qqn.js';

export class NoThreeInLine {
  constructor(params) {
    this.params = { ...params };
    this.step0 = 0;
    // Spatial dimension: 2 (planar) or 3 (cubic lattice). The angle-based
    // collinearity fitness is dimension-agnostic (it only depends on edge
    // lengths and triangle area), so 3D reuses the same math.
    this.dim = params.dim === 3 ? 3 : 2;
    // Angle-based collinearity fitness selection. All functions diverge as
    // an interior angle θ → 0 or π (i.e. as a triplet becomes collinear).
    // `angleFn` picks the functional form; `angleEps` softens the divergence
    // and `angleSharp` tunes how aggressively it climbs near collinearity.
    this.angleFn = params.angleFn || 'invsin';
    this.angleEps = params.angleEps !== undefined ? params.angleEps : 1e-4;
    this.angleSharp = params.angleSharp !== undefined ? params.angleSharp : 1.0;
    // When enabled, the angle-fitness penalty is negated (multiplied by -1),
    // flipping attraction/repulsion behaviour near collinearity.
    this.angleInvert = params.angleInvert !== undefined ? params.angleInvert : false;
    // Point-to-point repulsion: a force that phases in linearly once two
    // points come within `repelRadius`, weighted by 1/d² so it grows
    // sharply as points overlap. Energy contribution per pair:
    //   λ_repel * max(0, r - d) / (d² + eps)
    this.lambdaRepel = params.lambdaRepel !== undefined ? params.lambdaRepel : 0;
    this.repelRadius = params.repelRadius !== undefined ? params.repelRadius : 1.0;
    this.metrics = {
      step: 0,
      energy: 0,
      numLines: 0,
      violating: 0,
      validPoints: 0,
    };
    // Rescaling: when enabled (or invoked manually), points are linearly
    // remapped so their bounding box fills the [0, n-1]² grid exactly.
    this.autoRescale = params.autoRescale !== undefined ? params.autoRescale : true;
    this.rescaleEvery = params.rescaleEvery || 25;

    const { n, k } = params;
    const dim = this.dim;
    // Random initial positions in [0, n-1]^dim
    const init = [];
    for (let i = 0; i < k; i++) {
      const row = [];
      for (let d = 0; d < dim; d++) row.push(Math.random() * (n - 1));
      init.push(row);
    }
    this.points = tf.variable(tf.tensor2d(init));

    this.lines = []; // {a,b,c} normalized so a²+b²=1 (2D only, for viz)
    if (this.dim === 2) this.rebuildLines();
    this.lineRebuildEvery = 25;

    this.makeOptimizer();
  }

  makeOptimizer() {
    // Dispose any previous custom/TF optimizer cleanly.
    if (this.optimizer && this.optimizer.dispose) this.optimizer.dispose();
    if (this.optimizer && this.optimizer.setLearningRate && this.optimizer.history) {
      // custom optimizer: reset its internal state
      this.optimizer.setLearningRate(this.params.lr);
    }

    switch (this.params.optimizerType) {
      case 'sgd':
        this.optimizer = tf.train.momentum(this.params.lr, 0.9);
        this.isCustomOptimizer = false;
        break;
      case 'lbfgs':
        this.optimizer = new OptimizerLbfgs(this.params.lr);
        this.isCustomOptimizer = true;
        break;
      case 'qqn':
        this.optimizer = new OptimizerQQN(this.params.lr);
        this.isCustomOptimizer = true;
        break;
      case 'adam':
      default:
        this.optimizer = tf.train.adam(this.params.lr);
        this.isCustomOptimizer = false;
        break;
    }
  }
  // Reset optimizer history/momentum. Stateful optimizers (L-BFGS, QQN,
  // Adam, SGD-momentum) track deltas of the parameter vector. Any external
  // teleport of `this.points` (rescale or manual drag) makes those deltas
  // meaningless and causes the search direction to explode, so we must
  // clear that state whenever points are reassigned out-of-band.
  resetOptimizerState() {
    if (!this.optimizer) return;
    if (this.isCustomOptimizer && this.optimizer.setLearningRate) {
      // Clears kept history tensors and lastX/lastGrad.
      this.optimizer.setLearningRate(this.params.lr);
    } else {
      // Rebuild TF optimizer to drop accumulated moments/momentum.
      this.makeOptimizer();
    }
  }

  updateParams(params) {
    const typeChanged =
      params.optimizerType !== undefined && params.optimizerType !== this.params.optimizerType;
    const lrChanged = params.lr !== undefined && params.lr !== this.params.lr;
    this.params = { ...this.params, ...params };
    if (params.angleFn !== undefined) this.angleFn = params.angleFn;
    if (params.angleEps !== undefined) this.angleEps = params.angleEps;
    if (params.angleSharp !== undefined) this.angleSharp = params.angleSharp;
    if (params.angleInvert !== undefined) this.angleInvert = params.angleInvert;
    if (params.lambdaRepel !== undefined) this.lambdaRepel = params.lambdaRepel;
    if (params.repelRadius !== undefined) this.repelRadius = params.repelRadius;
    if (params.autoRescale !== undefined) {
      this.autoRescale = params.autoRescale;
    }
    if (params.rescaleEvery !== undefined) {
      this.rescaleEvery = params.rescaleEvery;
    }
    if (typeChanged) {
      this.makeOptimizer();
    } else if (lrChanged) {
      // Update LR in place when possible to preserve optimizer state.
      if (this.isCustomOptimizer && this.optimizer.setLearningRate) {
        this.optimizer.setLearningRate(this.params.lr);
      } else {
        this.makeOptimizer();
      }
    }
  }

  // Effective σ and λ_grid given annealing schedule.
  schedule() {
    // `sharp` controls how aggressively the angle-fitness climbs near
    // collinearity (replaces the obsolete σ well-width). λ_grid grows so
    // points snap harder onto the lattice as the search progresses.
    let sharp = this.angleSharp;
    let lambdaGrid = this.params.lambdaGrid;
    if (this.params.anneal) {
      // Grow sharpness toward 3x, grow λ_grid toward 3x over ~600 steps.
      const t = Math.min(1, this.metrics.step / 600);
      sharp = this.angleSharp * (1 + 2 * t);
      lambdaGrid = this.params.lambdaGrid * (1 + 2 * t);
    }
    return { sharp, lambdaGrid };
  }

  // Build candidate line set from current point positions.
  rebuildLines() {
    // Line tracking/visualization is 2D-only.
    if (this.dim !== 2) {
      this.lines = [];
      this.metrics.numLines = 0;
      return;
    }
    const { n } = this.params;
    const seen = new Set();
    const lines = [];
    const addLine = (a, b, c) => {
      // normalize so a²+b²=1 and leading sign canonical
      const norm = Math.hypot(a, b);
      if (norm < 1e-9) return;
      a /= norm;
      b /= norm;
      c /= norm;
      if (a < -1e-9 || (Math.abs(a) < 1e-9 && b < 0)) {
        a = -a;
        b = -b;
        c = -c;
      }
      const key = `${a.toFixed(4)},${b.toFixed(4)},${c.toFixed(4)}`;
      if (seen.has(key)) return;
      seen.add(key);
      lines.push({ a, b, c });
    };

    // Rows: y = j  → 0*x + 1*y = j
    for (let j = 0; j < n; j++) addLine(0, 1, j);
    // Columns: x = i → 1*x + 0*y = i
    for (let i = 0; i < n; i++) addLine(1, 0, i);
    // Diagonals slope +1: x - y = c
    for (let c = -(n - 1); c <= n - 1; c++) addLine(1, -1, c);
    // Diagonals slope -1: x + y = c
    for (let c = 0; c <= 2 * (n - 1); c++) addLine(1, 1, c);

    // Dynamic: lines through pairs of current points.
    const pts = this.points.arraySync();
    const K = pts.length;
    for (let i = 0; i < K; i++) {
      for (let j = i + 1; j < K; j++) {
        const dx = pts[j][0] - pts[i][0];
        const dy = pts[j][1] - pts[i][1];
        if (Math.hypot(dx, dy) < 0.3) continue; // too close, skip
        // normal (a,b) = (dy, -dx); line through point i
        const a = dy;
        const b = -dx;
        const c = a * pts[i][0] + b * pts[i][1];
        addLine(a, b, c);
      }
    }

    this.lines = lines;
    // Pack as tensors for the loss.
    this.lineA = tf.tensor1d(lines.map((l) => l.a));
    this.lineB = tf.tensor1d(lines.map((l) => l.b));
    this.lineC = tf.tensor1d(lines.map((l) => l.c));
    this.metrics.numLines = lines.length;
  }

  // Soft populations p(ℓ) for all lines given point positions.
  // points: [K,2]; returns [L]
  computePopulations(points, sigma) {
    // signed distance dᵢ(ℓ) = a*x + b*y - c
    const x = points.slice([0, 0], [-1, 1]); // [K,1]
    const y = points.slice([0, 1], [-1, 1]); // [K,1]
    // [K, L]
    const ax = tf.matMul(x, this.lineA.reshape([1, -1]));
    const by = tf.matMul(y, this.lineB.reshape([1, -1]));
    const d = tf.sub(tf.add(ax, by), this.lineC.reshape([1, -1])); // [K,L]
    const w = tf.exp(tf.div(tf.neg(tf.square(d)), 2 * sigma * sigma));
    // population per line = sum over points
    return tf.sum(w, 0); // [L]
  }

  // Per-angle fitness g(sinθ): given sin(θ) (>0) of an interior angle,
  // returns a penalty that diverges as θ → 0 or θ → π (sinθ → 0). All
  // variants share that limit but differ in shape/curvature near it.
  //   invsin    : 1/sin                       (classic, mild tail)
  //   invsin2   : 1/sin²                       (sharper, area-like)
  //   logsin    : -log(sin)                    (gentle, log divergence)
  //   cotsq     : cos²/sin² = (1/sin²)-1       (cotangent-squared)
  //   exp       : exp(sharp*(1/sin - 1))       (very steep barrier)
  //   cossq     : cos²θ = 1 - sin²θ            (bounded, peaks at collinear)
  //
  // The "angle family" variants operate on the interior angle θ directly
  // (recovered from sinθ via asin), and are summed over each angle of each
  // triplet:
  //   ang_sin     : sin(θ)
  //   ang_cos     : cos(θ)
  //   ang_tanhalf : tan(θ/2)
  //   ang_sq      : θ²
  //   ang_inv     : 1/θ
  //   ang_xlnx    : (θ/π)·ln(θ/π)
  // `sharp` scales the steepness; `eps` softens sinθ at exact collinearity.
  angleFitness(sinTheta, sharp) {
    const eps = this.angleEps;
    // Compute the base fitness, then optionally invert (negate) it.
    const sign = this.angleInvert ? -1 : 1;
    const s = tf.add(sinTheta, eps); // keep strictly positive
    const inv = tf.div(1, s); // 1/sin
    // Angle family: recover θ ∈ (0, π/2] from sinθ. Note that asin only
    // yields the acute branch, which is sufficient since each triangle's
    // interior angles are penalized symmetrically near collinearity.
    const angleFamily = new Set([
      'ang_sin',
      'ang_cos',
      'ang_tanhalf',
      'ang_sq',
      'ang_inv',
      'ang_xlnx',
    ]);
    if (angleFamily.has(this.angleFn)) {
      // Clamp sine into [eps, 1] before asin for numerical safety.
      const sClamped = tf.minimum(tf.maximum(s, eps), 1 - 1e-7);
      const theta = tf.asin(sClamped); // θ ∈ (0, π/2]
      const PI = Math.PI;
      switch (this.angleFn) {
        case 'ang_sin':
          return tf.mul(tf.sin(theta), sharp * sign);
        case 'ang_cos':
          return tf.mul(tf.cos(theta), sharp * sign);
        case 'ang_tanhalf':
          return tf.mul(tf.tan(tf.div(theta, 2)), sharp * sign);
        case 'ang_sq':
          return tf.mul(tf.square(theta), sharp * sign);
        case 'ang_inv':
          return tf.mul(tf.div(1, tf.add(theta, eps)), sharp * sign);
        case 'ang_xlnx': {
          // (θ/π)·ln(θ/π); add eps inside log to avoid -∞ as θ → 0.
          const r = tf.div(theta, PI);
          return tf.mul(tf.mul(r, tf.log(tf.add(r, eps))), sharp * sign);
        }
      }
    }
    switch (this.angleFn) {
      case 'invsin2':
        return tf.mul(tf.square(inv), sharp * sign);
      case 'logsin':
        return tf.mul(tf.neg(tf.log(s)), sharp * sign);
      case 'cotsq':
        return tf.mul(tf.sub(tf.square(inv), 1), sharp * sign);
      case 'exp':
        return tf.mul(tf.exp(tf.mul(tf.sub(inv, 1), sharp)), sign);
      case 'cossq':
        // cos²θ = 1 - sin²θ; maximal (=1) at collinearity, 0 at θ=90°.
        return tf.mul(tf.sub(1, tf.square(s)), sharp * sign);
      case 'invsin':
      default:
        return tf.mul(inv, sharp * sign);
    }
  }

  // Collinearity energy: sum over every distinct point triplet of the
  // selected angle-fitness applied to each interior angle. As any triplet
  // approaches collinearity, sin(θ)→0 for two of its angles, so the term
  // blows up, strongly repelling near-collinear configurations.
  // points: [K,2]; sharp: scalar steepness; returns scalar
  computeTripletEnergy(points, sharp = 1.0) {
    const K = points.shape[0];
    if (K < 3) return tf.scalar(0);
    const eps = this.angleEps;
    const dim = this.dim;

    // Per-axis coordinate vectors [K].
    const coords = [];
    for (let d = 0; d < dim; d++) {
      coords.push(points.slice([0, d], [-1, 1]).reshape([K]));
    }

    // Build index triples (i<j<l).
    const ti = [];
    const tj = [];
    const tl = [];
    for (let i = 0; i < K; i++) {
      for (let j = i + 1; j < K; j++) {
        for (let l = j + 1; l < K; l++) {
          ti.push(i);
          tj.push(j);
          tl.push(l);
        }
      }
    }
    if (ti.length === 0) return tf.scalar(0);

    const idxI = tf.tensor1d(ti, 'int32');
    const idxJ = tf.tensor1d(tj, 'int32');
    const idxL = tf.tensor1d(tl, 'int32');

    // Per-axis vertex coordinates per triplet.
    const A = coords.map((c) => tf.gather(c, idxI));
    const B = coords.map((c) => tf.gather(c, idxJ));
    const C = coords.map((c) => tf.gather(c, idxL));

    // Edge vectors (per axis).
    const eAB = A.map((a, d) => tf.sub(B[d], a)); // B - A
    const eBC = B.map((b, d) => tf.sub(C[d], b)); // C - B
    const eCA = C.map((c, d) => tf.sub(A[d], c)); // A - C

    // Squared edge lengths via per-axis sum of squares.
    const sumSq = (vec) => vec.reduce((acc, v) => tf.add(acc, tf.square(v)), tf.scalar(0));
    const ab = tf.sqrt(tf.add(sumSq(eAB), eps));
    const bc = tf.sqrt(tf.add(sumSq(eBC), eps));
    const ca = tf.sqrt(tf.add(sumSq(eCA), eps));

    // Twice the triangle area = |(B-A) × (C-A)|. In 2D the cross product
    // is a scalar; in 3D it is a vector and we take its norm.
    const u = A.map((a, d) => tf.sub(B[d], a)); // B - A
    const v = A.map((a, d) => tf.sub(C[d], a)); // C - A
    let crossNormSq;
    if (dim === 2) {
      const cz = tf.sub(tf.mul(u[0], v[1]), tf.mul(u[1], v[0]));
      crossNormSq = tf.square(cz);
    } else {
      // 3D cross product components.
      const cx = tf.sub(tf.mul(u[1], v[2]), tf.mul(u[2], v[1]));
      const cy = tf.sub(tf.mul(u[2], v[0]), tf.mul(u[0], v[2]));
      const cz = tf.sub(tf.mul(u[0], v[1]), tf.mul(u[1], v[0]));
      crossNormSq = tf.add(tf.add(tf.square(cx), tf.square(cy)), tf.square(cz));
    }
    const area2 = tf.add(tf.sqrt(tf.add(crossNormSq, eps)), eps); // ≈ 2 * area

    // For a triangle, sin(angle at A) = 2*area / (edge1*edge2). Compute
    // each interior angle's sine, then run it through the chosen fitness.
    const sinA = tf.div(area2, tf.add(tf.mul(ab, ca), eps));
    const sinB = tf.div(area2, tf.add(tf.mul(ab, bc), eps));
    const sinC = tf.div(area2, tf.add(tf.mul(bc, ca), eps));

    const fA = this.angleFitness(sinA, sharp);
    const fB = this.angleFitness(sinB, sharp);
    const fC = this.angleFitness(sinC, sharp);

    return tf.sum(tf.add(tf.add(fA, fB), fC));
  }
  // Point-to-point repulsion energy. For every distinct pair of points,
  // adds λ_repel * max(0, r - d) / (d² + eps), where d is the Euclidean
  // distance. The hinge max(0, r - d) phases the force in linearly as the
  // pair separation drops below `repelRadius`, and the 1/d² factor makes
  // the penalty climb steeply as points approach coincidence.
  // points: [K,2]; returns scalar
  computeRepulsionEnergy(points) {
    const K = points.shape[0];
    if (K < 2 || this.lambdaRepel <= 0) return tf.scalar(0);
    const eps = this.angleEps;
    const r = this.repelRadius;
    const dim = this.dim;
    // Build index pairs (i<j).
    const pi = [];
    const pj = [];
    for (let i = 0; i < K; i++) {
      for (let j = i + 1; j < K; j++) {
        pi.push(i);
        pj.push(j);
      }
    }
    if (pi.length === 0) return tf.scalar(0);
    const idxI = tf.tensor1d(pi, 'int32');
    const idxJ = tf.tensor1d(pj, 'int32');
    // Squared and softened distances across all axes.
    let d2 = tf.scalar(0);
    for (let d = 0; d < dim; d++) {
      const c = points.slice([0, d], [-1, 1]).reshape([K]);
      const a = tf.gather(c, idxI);
      const b = tf.gather(c, idxJ);
      d2 = tf.add(d2, tf.square(tf.sub(b, a)));
    }
    d2 = tf.add(d2, eps);
    const d = tf.sqrt(d2);
    // Linear hinge: max(0, r - d).
    const hinge = tf.relu(tf.sub(r, d));
    // Penalty per pair: hinge / d².
    const penalty = tf.div(hinge, d2);
    return tf.mul(tf.sum(penalty), this.lambdaRepel);
  }

  lossFn(sharp, lambdaGrid) {
    return () => {
      const pts = this.points;
      const { n, lambdaLine } = this.params;

      // Collinearity term: λ_line Σ_triplets Σ_angles 1/sin(θ)
      const tripletEnergy = this.computeTripletEnergy(pts, sharp);
      const lineLoss = tf.mul(tripletEnergy, lambdaLine);

      // Grid snap: [1-cos(2πx)] + [1-cos(2πy)]
      const twoPi = 2 * Math.PI;
      // simpler & correct grid term:
      const g = tf.sub(1, tf.cos(tf.mul(pts, twoPi))); // [K,2]
      const gridTerm = tf.mul(tf.sum(g), lambdaGrid);

      // Point-to-point repulsion to discourage overlapping points.
      const repelTerm = this.computeRepulsionEnergy(pts);

      return tf.add(tf.add(lineLoss, gridTerm), repelTerm);
    };
  }

  // Linearly remap the current points so their bounding box fills the
  // entire [0, n-1]² grid. If all points share an axis coordinate (zero
  // extent on that axis), that axis is centered instead of stretched.
  // Returns true if a rescale was applied.
  rescalePoints() {
    const { n } = this.params;
    const dim = this.dim;
    const pts = this.points.arraySync();
    if (pts.length === 0) return false;

    const min = new Array(dim).fill(Infinity);
    const max = new Array(dim).fill(-Infinity);
    for (const row of pts) {
      for (let d = 0; d < dim; d++) {
        if (row[d] < min[d]) min[d] = row[d];
        if (row[d] > max[d]) max[d] = row[d];
      }
    }

    const target = n - 1;
    const eps = 1e-9;
    const span = min.map((mn, d) => max[d] - mn);

    const remapped = pts.map((row) =>
      row.map((v, d) => {
        let nv = span[d] > eps ? ((v - min[d]) / span[d]) * target : target / 2;
        return Math.max(0, Math.min(target, nv));
      })
    );

    const next = tf.tensor2d(remapped);
    this.points.assign(next);
    next.dispose();
    return true;
  }

  step() {
    const { sharp, lambdaGrid } = this.schedule();

    // Periodically rebuild dynamic line set.
    if (
      this.dim === 2 &&
      this.metrics.step % this.lineRebuildEvery === 0 &&
      this.metrics.step > 0
    ) {
      this.disposeLineTensors();
      this.rebuildLines();
    }
    // Periodically auto-rescale points to fill the grid.
    // NOTE: We no longer mutate stored points here. Display normalization in
    // getViz()/getPoints() keeps the view filling the grid every frame, which
    // avoids the periodic "dizzy vision" jumps that in-place rescaling caused.
    if (
      this.autoRescale &&
      this.rescaleEvery > 0 &&
      this.metrics.step % this.rescaleEvery === 0 &&
      this.metrics.step > 0 &&
      false
    ) {
      if (this.rescalePoints()) {
        // Points teleported; stale optimizer state would explode.
        this.resetOptimizerState();
      }
    }

    const lossFunction = this.lossFn(sharp, lambdaGrid);

    if (this.isCustomOptimizer) {
      // Custom optimizers (L-BFGS / QQN) use the compute/apply API and
      // keep their own internal tensors, so they must not run inside an
      // outer tf.tidy that would dispose that retained state.
      const grads = this.optimizer.computeGradients(lossFunction);
      // grads = { value, grads }; record the loss value for metrics.
      this.metrics.energy = grads.value.dataSync()[0];
      // The 1/sin(θ) triplet energy can produce very large gradients near
      // collinearity; clip them so L-BFGS/QQN curvature stays well-behaved.
      this.clipGrads(grads.grads, 1e3);
      // QQN's line search needs the loss function; L-BFGS ignores extra args.
      this.optimizer.applyGradients(grads.grads, lossFunction);
      tf.dispose(grads.value);
      tf.dispose(Object.values(grads.grads));

      if (this.params.noise > 1e-6) {
        tf.tidy(() => {
          const noise = tf.randomNormal(this.points.shape, 0, this.params.noise);
          this.points.assign(tf.add(this.points, noise));
        });
      }
    } else {
      tf.tidy(() => {
        const value = this.optimizer.minimize(lossFunction, true, [this.points]);
        this.metrics.energy = value.dataSync()[0];

        // Entropic noise injection.
        if (this.params.noise > 1e-6) {
          const noise = tf.randomNormal(this.points.shape, 0, this.params.noise);
          this.points.assign(tf.add(this.points, noise));
        }
      });
    }

    this.metrics.step++;
    this.evaluate();
  }

  // Round, dedupe, and check integer collinearity for true validation.
  evaluate() {
    const { n } = this.params;
    const dim = this.dim;
    // Fixed display width for the line-population visualization only. The
    // optimizer no longer depends on σ; this is purely cosmetic so the
    // tracked-line colors still convey crowding.
    const sigma = 0.25;
    const pts = this.points.arraySync();
    // round to lattice, clamp
    const rounded = [];
    const occ = new Set();
    for (const row of pts) {
      const ir = row.map((v) => Math.max(0, Math.min(n - 1, Math.round(v))));
      const key = ir.join(',');
      if (occ.has(key)) continue;
      occ.add(key);
      rounded.push(ir);
    }

    // Check every triple for exact collinearity (cross product == 0).
    const R = rounded.length;
    let violatingTriples = 0;
    const badPoints = new Set();
    for (let i = 0; i < R; i++) {
      for (let j = i + 1; j < R; j++) {
        for (let l = j + 1; l < R; l++) {
          const a = rounded[i];
          const b = rounded[j];
          const c = rounded[l];
          let collinear;
          if (dim === 2) {
            const cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            collinear = cross === 0;
          } else {
            // 3D: collinear iff (B-A) × (C-A) is the zero vector.
            const ux = b[0] - a[0],
              uy = b[1] - a[1],
              uz = b[2] - a[2];
            const vx = c[0] - a[0],
              vy = c[1] - a[1],
              vz = c[2] - a[2];
            const cx = uy * vz - uz * vy;
            const cy = uz * vx - ux * vz;
            const cz = ux * vy - uy * vx;
            collinear = cx === 0 && cy === 0 && cz === 0;
          }
          if (collinear) {
            violatingTriples++;
            badPoints.add(i);
            badPoints.add(j);
            badPoints.add(l);
          }
        }
      }
    }

    this.metrics.validPoints = violatingTriples === 0 ? R : R - badPoints.size;

    // Count soft-violating lines for display (population > 2.5). 2D only;
    // in 3D we report violating triplets directly.
    if (this.dim === 2 && this.lines.length > 0) {
      tf.tidy(() => {
        const pop = this.computePopulations(this.points, sigma).arraySync();
        this._lastPop = pop;
        let v = 0;
        for (const p of pop) if (p > 2.5) v++;
        this.metrics.violating = v;
      });
    } else {
      this._lastPop = [];
      this.metrics.violating = violatingTriples;
    }
  }

  getViz() {
    const raw = this.points.arraySync();
    // Always normalize the displayed coordinates against the current point
    // bounding box so the view fills the grid smoothly. This is purely a
    // display transform (it does not mutate stored points), avoiding the
    // periodic-teleport "dizzy vision" caused by mutating positions.
    const norm = this.normalizedCoords(raw);
    const pts =
      this.dim === 3 ? norm.map(([x, y, z]) => ({ x, y, z })) : norm.map(([x, y]) => ({ x, y }));
    const pop = this._lastPop || this.lines.map(() => 0);
    const lines = this.lines.map((l, i) => ({
      a: l.a,
      b: l.b,
      c: l.c,
      pop: pop[i] || 0,
    }));
    return { points: pts, lines };
  }
  // Return current point positions as plain array [[x,y],...].
  getPoints() {
    // Return display-normalized coordinates so picking/dragging matches the
    // rendered positions (which are always bounding-box normalized).
    return this.normalizedCoords(this.points.arraySync());
  }
  // Linearly remap a list of point coordinates so their bounding box fills
  // the [0, n-1]^dim grid. Pure function (no mutation of stored points),
  // used by the renderer and hit-testing so the view is always normalized
  // without periodically teleporting the underlying optimization state.
  normalizedCoords(pts) {
    const { n } = this.params;
    const dim = this.dim;
    if (!pts || pts.length === 0) return pts;
    const min = new Array(dim).fill(Infinity);
    const max = new Array(dim).fill(-Infinity);
    for (const row of pts) {
      for (let d = 0; d < dim; d++) {
        if (row[d] < min[d]) min[d] = row[d];
        if (row[d] > max[d]) max[d] = row[d];
      }
    }
    const target = n - 1;
    const eps = 1e-9;
    const span = min.map((mn, d) => max[d] - mn);
    return pts.map((row) =>
      row.map((v, d) => {
        const nv = span[d] > eps ? ((v - min[d]) / span[d]) * target : target / 2;
        return Math.max(0, Math.min(target, nv));
      })
    );
  }
  // Move a single point (by index) to a new (x,y) position, clamped to grid.
  setPoint(index, x, y, z) {
    const { n } = this.params;
    const pts = this.points.arraySync();
    if (index < 0 || index >= pts.length) return;
    // Incoming (x,y[,z]) are in display (normalized) space. Writing them as
    // raw coordinates keeps the point under the cursor; the next getViz()
    // re-normalizes the whole set consistently.
    pts[index][0] = Math.max(0, Math.min(n - 1, x));
    pts[index][1] = Math.max(0, Math.min(n - 1, y));
    if (this.dim === 3 && z !== undefined) {
      pts[index][2] = Math.max(0, Math.min(n - 1, z));
    }
    const next = tf.tensor2d(pts);
    this.points.assign(next);
    next.dispose();
    // The point teleported; invalidate stale optimizer state.
    this.resetOptimizerState();
    // Refresh metrics/visuals against the new position.
    this.evaluate();
  }

  disposeLineTensors() {
    if (this.lineA) this.lineA.dispose();
    if (this.lineB) this.lineB.dispose();
    if (this.lineC) this.lineC.dispose();
  }
  // Clip gradients in-place (by global norm) to keep stateful optimizers
  // stable when the triplet energy spikes. Mutates the grads map.
  clipGrads(grads, maxNorm) {
    const names = Object.keys(grads);
    tf.tidy(() => {
      let sumSq = tf.scalar(0);
      for (const name of names) {
        sumSq = sumSq.add(tf.sum(tf.square(grads[name])));
      }
      const norm = tf.sqrt(sumSq).dataSync()[0];
      if (norm > maxNorm && norm > 0) {
        const scale = maxNorm / norm;
        for (const name of names) {
          const clipped = tf.keep(grads[name].mul(scale));
          grads[name].dispose();
          grads[name] = clipped;
        }
      }
    });
  }

  dispose() {
    this.disposeLineTensors();
    if (this.points) this.points.dispose();
    if (this.optimizer && this.optimizer.dispose) {
      this.optimizer.dispose();
    } else if (this.optimizer && this.optimizer.setLearningRate && this.isCustomOptimizer) {
      // Custom optimizer: clearing the learning rate frees kept history tensors.
      this.optimizer.setLearningRate(this.optimizer.learningRate);
    }
  }
}
