// Equilibrium finding, normal modes, and coupling matrix.

import {energy, gradient, hessian} from './physics.js';
import {jacobiEigen} from '../math/linalg.js';

/**
 * Relax to equilibrium via gradient descent with backtracking line search.
 * Returns { theta, iterations, gradNorm }.
 */
export function relax(theta0, pairs, n, opts = {}) {
    const {maxIter = 2000, tol = 1e-9} = opts;
    const theta = Float64Array.from(theta0);
    let step = 0.1;
    let iterations = 0;
    let gradNorm = Infinity;

    for (iterations = 0; iterations < maxIter; iterations++) {
        const g = gradient(theta, pairs, n);
        gradNorm = 0;
        for (let i = 0; i < n; i++) gradNorm += g[i] * g[i];
        gradNorm = Math.sqrt(gradNorm);
        if (gradNorm < tol) break;

        const E0 = energy(theta, pairs);
        // backtracking
        let s = step;
        let accepted = false;
        for (let ls = 0; ls < 30; ls++) {
            const trial = new Float64Array(n);
            for (let i = 0; i < n; i++) trial[i] = theta[i] - s * g[i];
            const E1 = energy(trial, pairs);
            if (E1 < E0 - 1e-4 * s * gradNorm * gradNorm) {
                for (let i = 0; i < n; i++) theta[i] = trial[i];
                step = s * 1.5;
                accepted = true;
                break;
            }
            s *= 0.5;
        }
        if (!accepted) {
            step *= 0.5;
            if (step < 1e-14) break;
        }
    }
    // wrap into [-π, π]
    for (let i = 0; i < n; i++) theta[i] = Math.atan2(Math.sin(theta[i]), Math.cos(theta[i]));
    return {theta, iterations, gradNorm};
}

/**
 * Normal-mode analysis at equilibrium.
 * Generalized eigenproblem H v = ω² M v with M = I·Identity, so
 * ω² = eig(H)/I.
 * Returns { H, C, omega2, omega, modes, values }.
 */
export function analyze(theta, pairs, n, params) {
    const {I} = params;
    const H = hessian(theta, pairs, n);
    const {values, vectors} = jacobiEigen(H);

    const omega2 = new Float64Array(n);
    const omega = new Float64Array(n);
    for (let i = 0; i < n; i++) {
        omega2[i] = values[i] / I;
        omega[i] = omega2[i] >= 0 ? Math.sqrt(omega2[i]) : -Math.sqrt(-omega2[i]); // sign flags instability
    }

    // Normalized coupling matrix C_ij = H_ij / sqrt(H_ii H_jj)
    const C = new Array(n);
    for (let i = 0; i < n; i++) {
        C[i] = new Float64Array(n);
        for (let j = 0; j < n; j++) {
            const denom = Math.sqrt(Math.abs(H[i][i] * H[j][j]));
            C[i][j] = denom > 1e-30 ? H[i][j] / denom : 0;
        }
    }

    return {H, C, values, omega2, omega, modes: vectors};
}
