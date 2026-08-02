// manifolds.js
// Each manifold provides:
//   dim: intrinsic parameter count per point stored in the variable
//   embed(params): tf.Tensor [k,3] world-space coordinates (differentiable)
//   seed(k): Float32Array initial params
// Points live in a variable of shape [k, paramDim].

import * as tf from 'https://esm.sh/@tensorflow/tfjs@4.20.0';

const TAU = Math.PI * 2;

function rand(n) {
  const a = new Float32Array(n);
  for (let i = 0; i < n; i++) a[i] = Math.random();
  return a;
}

export const MANIFOLDS = {
  plane: {
    paramDim: 2,
    is2D: true,
    embed(params) {
      // params [k,2] in roughly [-1,1]; z = 0
      return tf.tidy(() => {
        const z = tf.zerosLike(params.slice([0, 0], [-1, 1]));
        return tf.concat([params, z], 1);
      });
    },
    seed(k) {
      const a = new Float32Array(k * 2);
      for (let i = 0; i < k * 2; i++) a[i] = (Math.random() * 2 - 1) * 0.9;
      return a;
    },
  },

  sphere: {
    paramDim: 3,
    is2D: false,
    embed(params) {
      // normalize each row to unit sphere
      return tf.tidy(() => {
        const norm = params.norm('euclidean', 1, true).add(1e-8);
        return params.div(norm);
      });
    },
    seed(k) {
      const a = new Float32Array(k * 3);
      for (let i = 0; i < k; i++) {
        let x = Math.random() * 2 - 1,
          y = Math.random() * 2 - 1,
          z = Math.random() * 2 - 1;
        const n = Math.hypot(x, y, z) || 1;
        a[i * 3] = x / n;
        a[i * 3 + 1] = y / n;
        a[i * 3 + 2] = z / n;
      }
      return a;
    },
  },

  torus: {
    paramDim: 2, // (u,v) angles
    is2D: false,
    R: 1.0,
    r: 0.4,
    embed(params) {
      const R = 1.0,
        r = 0.4;
      return tf.tidy(() => {
        const u = params.slice([0, 0], [-1, 1]).mul(TAU);
        const v = params.slice([0, 1], [-1, 1]).mul(TAU);
        const cu = tf.cos(u),
          su = tf.sin(u);
        const cv = tf.cos(v),
          sv = tf.sin(v);
        const x = cu.mul(tf.scalar(R).add(cv.mul(r)));
        const y = su.mul(tf.scalar(R).add(cv.mul(r)));
        const z = sv.mul(r);
        return tf.concat([x, y, z], 1);
      });
    },
    seed(k) {
      return rand(k * 2);
    },
  },

  saddle: {
    paramDim: 2,
    is2D: false,
    embed(params) {
      // z = x^2 - y^2
      return tf.tidy(() => {
        const x = params.slice([0, 0], [-1, 1]);
        const y = params.slice([0, 1], [-1, 1]);
        const z = x.square().sub(y.square());
        return tf.concat([x, y, z], 1);
      });
    },
    seed(k) {
      const a = new Float32Array(k * 2);
      for (let i = 0; i < k * 2; i++) a[i] = (Math.random() * 2 - 1) * 0.9;
      return a;
    },
  },
};
