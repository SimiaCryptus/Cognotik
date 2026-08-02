// dihedral.js
// Differentiable dihedral-angle energy over a fixed triangulation.
// Given world coords [k,3] and interior edges (each shared by two tris),
// compute the dihedral angle φ per edge and reduce a functional g(φ).

import * as tf from 'https://esm.sh/@tensorflow/tfjs@4.20.0';

// Gather helper: build face-normal tensors for a list of triangles.
// triangles: Int array [[i,j,k]...]; returns tf normals [m,3].
function faceNormals(coords, triIdx) {
  // triIdx: tensor int32 [m,3]
  return tf.tidy(() => {
    const p0 = tf.gather(coords, triIdx.slice([0, 0], [-1, 1]).reshape([-1]));
    const p1 = tf.gather(coords, triIdx.slice([0, 1], [-1, 1]).reshape([-1]));
    const p2 = tf.gather(coords, triIdx.slice([0, 2], [-1, 1]).reshape([-1]));
    const e1 = p1.sub(p0);
    const e2 = p2.sub(p0);
    // cross product e1 x e2
    return cross(e1, e2);
  });
}

function cross(a, b) {
  return tf.tidy(() => {
    const a0 = a.slice([0, 0], [-1, 1]);
    const a1 = a.slice([0, 1], [-1, 1]);
    const a2 = a.slice([0, 2], [-1, 1]);
    const b0 = b.slice([0, 0], [-1, 1]);
    const b1 = b.slice([0, 1], [-1, 1]);
    const b2 = b.slice([0, 2], [-1, 1]);
    const c0 = a1.mul(b2).sub(a2.mul(b1));
    const c1 = a2.mul(b0).sub(a0.mul(b2));
    const c2 = a0.mul(b1).sub(a1.mul(b0));
    return tf.concat([c0, c1, c2], 1);
  });
}

// Returns tensor [numEdges] of dihedral angles φ in [0, π].
export function dihedralAngles(coords, triTensor, edgeT1, edgeT2) {
  return tf.tidy(() => {
    const normals = faceNormals(coords, triTensor); // [m,3]
    const n1 = tf.gather(normals, edgeT1); // [E,3]
    const n2 = tf.gather(normals, edgeT2);
    const dot = n1.mul(n2).sum(1);
    const mag = n1.norm('euclidean', 1).mul(n2.norm('euclidean', 1)).add(1e-8);
    const cosPhi = dot.div(mag).clipByValue(-1 + 1e-6, 1 - 1e-6);
    return tf.acos(cosPhi); // [E]
  });
}

// Functional reductions. Return a scalar energy (to be minimized).
// For "max" direction, the caller negates.
export const FUNCTIONALS = {
  flatness: (phi) => tf.tidy(() => phi.sub(Math.PI).square().mean()),

  uniform: (phi) =>
    tf.tidy(() => {
      const mean = phi.mean();
      return phi.sub(mean).square().mean();
    }),

  maxfold: (phi) => tf.tidy(() => tf.cos(phi).mean().neg()),

  smoothness: (phi) => tf.tidy(() => tf.scalar(1).sub(tf.cos(phi)).mean()),

  // Shannon entropy of a Gaussian KDE over the dihedral distribution.
  entropy: (phi) =>
    tf.tidy(() => {
      const E = phi.shape[0];
      if (E < 2) return tf.scalar(0);
      const bw = 0.15;
      const a = phi.reshape([E, 1]);
      const b = phi.reshape([1, E]);
      const diff = a.sub(b).div(bw);
      const kernel = tf.exp(diff.square().mul(-0.5)); // [E,E]
      const density = kernel.mean(1).add(1e-8); // p(phi_i)
      // entropy estimate: -mean(log density)  (higher = more diverse)
      // We want to MAXIMIZE entropy -> return its negation as an energy.
      const H = density.log().neg().mean();
      return H.neg(); // energy: minimizing this maximizes entropy
    }),
};

// Build the full energy scalar given options.
export function dihedralEnergy(coords, triTensor, edgeT1, edgeT2, opts) {
  return tf.tidy(() => {
    const phi = dihedralAngles(coords, triTensor, edgeT1, edgeT2);
    let e = FUNCTIONALS[opts.functional](phi);
    if (opts.direction === 'max') e = e.neg();
    return e.mul(opts.lambdaDih);
  });
}

// Pairwise repulsion companion term (1/d^2 softened).
export function repulsionEnergy(coords, lambdaRep) {
  return tf.tidy(() => {
    const k = coords.shape[0];
    const a = coords.reshape([k, 1, 3]);
    const b = coords.reshape([1, k, 3]);
    const d2 = a.sub(b).square().sum(2).add(1e-3); // [k,k]
    const inv = tf.scalar(1).div(d2);
    // subtract diagonal (self, inv = 1/1e-3)
    const mask = tf.ones([k, k]).sub(tf.eye(k));
    return inv
      .mul(mask)
      .sum()
      .div(k * k)
      .mul(lambdaRep);
  });
}

// Utility to read phi for HUD/coloring (returns Float32Array).
export async function readDihedrals(coords, triTensor, edgeT1, edgeT2) {
  const phi = dihedralAngles(coords, triTensor, edgeT1, edgeT2);
  const arr = await phi.data();
  phi.dispose();
  return arr;
}
