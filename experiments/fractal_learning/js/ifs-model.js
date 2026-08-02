// IFS model: K affine maps as TF.js variables, with per-transform freeze flags.
// Each transform: A_k (2x2), b_k (2,). Stored as a single flat variable per k
// so the optimizer interface is uniform.

import { makeRng } from './rng.js';
import { computeCommutativeOrbit } from './orbit-commutative.js';

export class IFSModel {
  /**
   * @param {number} K  number of transforms
   * @param {number} seed RNG seed
   */
  constructor(K, seed = 1) {
    this.K = K;
    this.seed = seed;
    this.transforms = []; // [{A: tf.Variable[4], b: tf.Variable[2], frozen:bool}]
    this._initSmall();
  }

  dispose() {
    for (const t of this.transforms) {
      t.A.dispose();
      t.b.dispose();
    }
    this.transforms = [];
    if (this._stepIdx) {
      for (const t of this._stepIdx) t.dispose();
      this._stepIdx = null;
    }
    this._wordsCacheKey = null;
  }

  /** Replace K transforms. arr is list of {A:[4], b:[2]} plain arrays. */
  setFromArrays(arr) {
    this.dispose();
    this.K = arr.length;
    for (let k = 0; k < arr.length; k++) {
      const A = tf.variable(tf.tensor1d(arr[k].A), true, `A_${k}`);
      const b = tf.variable(tf.tensor1d(arr[k].b), true, `b_${k}`);
      this.transforms.push({ A, b, frozen: false });
    }
  }

  /** All trainable tf.Variables (regardless of freeze). */
  allVariables() {
    const out = [];
    for (const t of this.transforms) {
      out.push(t.A);
      out.push(t.b);
    }
    return out;
  }

  /** Only the variables for non-frozen transforms. */
  activeVariables() {
    const out = [];
    for (const t of this.transforms) {
      if (!t.frozen) {
        out.push(t.A);
        out.push(t.b);
      }
    }
    return out;
  }

  activeCount() {
    return this.transforms.filter((t) => !t.frozen).length;
  }

  setFrozen(k, frozen) {
    if (k >= 0 && k < this.transforms.length) {
      this.transforms[k].frozen = !!frozen;
    }
  }

  toggleFrozen(k) {
    this.setFrozen(k, !this.transforms[k].frozen);
  }

  freezeAll(v) {
    for (const t of this.transforms) t.frozen = v;
  }

  invertFreeze() {
    for (const t of this.transforms) t.frozen = !t.frozen;
  }

  _initSmall() {
    const { randn } = makeRng(this.seed);
    const arr = [];
    for (let k = 0; k < this.K; k++) {
      arr.push({
        A: [0.5 + 0.05 * randn(), 0.05 * randn(), 0.05 * randn(), 0.5 + 0.05 * randn()],
        b: [0.2 * randn(), 0.2 * randn()],
      });
    }
    this.setFromArrays(arr);
  }

  initSmall(seed) {
    this.seed = seed ?? this.seed;
    this.K = this.K;
    const { randn } = makeRng(this.seed);
    const arr = [];
    for (let k = 0; k < this.K; k++) {
      arr.push({
        A: [0.5 + 0.05 * randn(), 0.05 * randn(), 0.05 * randn(), 0.5 + 0.05 * randn()],
        b: [0.2 * randn(), 0.2 * randn()],
      });
    }
    this.setFromArrays(arr);
  }

  initRotations(seed) {
    this.seed = seed ?? this.seed;
    const { randn } = makeRng(this.seed);
    const arr = [];
    for (let k = 0; k < this.K; k++) {
      const a = (2 * Math.PI * k) / this.K + 0.1 * randn();
      const s = 0.5;
      arr.push({
        A: [s * Math.cos(a), -s * Math.sin(a), s * Math.sin(a), s * Math.cos(a)],
        b: [0.3 * Math.cos(a), 0.3 * Math.sin(a)],
      });
    }
    this.setFromArrays(arr);
  }

  initContractions() {
    const arr = [];
    for (let k = 0; k < this.K; k++) {
      const cx = 0.5 * Math.cos((2 * Math.PI * k) / this.K);
      const cy = 0.5 * Math.sin((2 * Math.PI * k) / this.K);
      arr.push({ A: [0.5, 0, 0, 0.5], b: [0.5 * cx, 0.5 * cy] });
    }
    this.setFromArrays(arr);
  }

  initSierpinski3() {
    this.K = 3;
    const verts = [
      [-0.6, -0.5],
      [0.6, -0.5],
      [0, 0.6],
    ];
    const arr = verts.map((v) => ({
      A: [0.5, 0, 0, 0.5],
      b: [0.5 * v[0], 0.5 * v[1]],
    }));
    this.setFromArrays(arr);
  }

  initBarnsley() {
    this.K = 4;
    const arr = [
      { A: [0, 0, 0, 0.16], b: [0, -0.7] },
      { A: [0.85, 0.04, -0.04, 0.85], b: [0, 0.27] },
      { A: [0.2, -0.26, 0.23, 0.22], b: [0, 0.27] },
      { A: [-0.15, 0.28, 0.26, 0.24], b: [0, -0.07] },
    ];
    this.setFromArrays(arr);
  }

  /** Pure-JS read of current values (for UI / rendering / fixed points). */
  readTransforms() {
    const out = [];
    for (const t of this.transforms) {
      out.push({
        A: t.A.arraySync(),
        b: t.b.arraySync(),
        frozen: t.frozen,
      });
    }
    return out;
  }
  /** Precompute per-step gather indices for vectorized orbit computation. */
  _wordsToStepIndices(words) {
    // words: [P][N]. Returns array of length N, each a tf.Tensor1D of int32, shape [P].
    const P = words.length;
    if (P === 0) return { steps: [], N: 0, P: 0 };
    const N = words[0].length;
    const steps = [];
    for (let s = 0; s < N; s++) {
      const col = new Int32Array(P);
      for (let i = 0; i < P; i++) col[i] = words[i][s];
      steps.push(tf.tensor1d(col, 'int32'));
    }
    return { steps, N, P };
  }

  /**
   * Compute the orbit P_N(θ) as a tf tensor [P, 2] given list of words.
   * Each word = array of K-indices length N. Differentiable.
   * @param {number[][]} words
   * @returns {tf.Tensor} shape [words.length, 2]
   */
  computeOrbit(words) {
    // Cache step indices keyed by words identity.
    if (this._wordsCacheKey !== words) {
      if (this._stepIdx) for (const t of this._stepIdx) t.dispose();
      const { steps } = this._wordsToStepIndices(words);
      this._stepIdx = steps;
      this._wordsCacheKey = words;
      this._wordsP = words.length;
      this._wordsN = words.length ? words[0].length : 0;
    }
    const P = this._wordsP;
    const N = this._wordsN;
    if (P === 0) return tf.zeros([0, 2]);

    return tf.tidy(() => {
      // Stack A's into [K,2,2], b's into [K,2]
      const Astack = tf.stack(this.transforms.map((t) => t.A.reshape([2, 2]))); // [K,2,2]
      const bstack = tf.stack(this.transforms.map((t) => t.b)); // [K,2]

      // x: [P, 2], start at origin
      let x = tf.zeros([P, 2]);
      for (let s = 0; s < N; s++) {
        const idx = this._stepIdx[s]; // [P] int32
        const Aw = Astack.gather(idx); // [P,2,2]
        const bw = bstack.gather(idx); // [P,2]
        // batched matvec: [P,2,2] @ [P,2,1] -> [P,2,1] -> [P,2]
        const xCol = x.reshape([P, 2, 1]);
        const Ax = tf.matMul(Aw, xCol).reshape([P, 2]);
        x = tf.add(Ax, bw);
      }
      return x;
    });
  }
  /**
   * Efficient commutative orbit via binary powers + DP (see algo.md).
   * Returns [|P_N|, 2] tensor. Use this when the enumeration is commutative
   * and N is the uniform word length.
   */
  computeCommutativeOrbit(N) {
    return computeCommutativeOrbit(this, N, 2);
  }
}
