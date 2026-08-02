// Time integration for the coupled-dipole Lagrangian.
//
// State: { theta: Float64Array, thetaDot: Float64Array }
// Equations of motion: I θ_ddot = τ(θ) - γ θ_dot
//
// Two integrators:
//   - 'verlet': Störmer–Verlet / velocity-Verlet (symplectic when γ=0).
//   - 'variational': implicit midpoint variational integrator with Newton
//     solve using the analytic Hessian (energy-conserving, γ=0).

import {torque, gradient, hessian} from './physics.js';
import {luSolve} from '../math/linalg.js';

/**
 * Velocity-Verlet step. Symplectic for the conservative system (γ=0).
 * With damping we use an exponential-ish semi-implicit correction.
 */
export function verletStep(state, pairs, params, h) {
    const {I, gamma} = params;
    const n = state.theta.length;
    const th = state.theta,
        thd = state.thetaDot;

    // a_k = (τ - γ θdot) / I
    const tau0 = torque(th, pairs, n);
    const a0 = new Float64Array(n);
    for (let i = 0; i < n; i++) a0[i] = (tau0[i] - gamma * thd[i]) / I;

    // half-step position
    const thNew = new Float64Array(n);
    for (let i = 0; i < n; i++) thNew[i] = th[i] + thd[i] * h + 0.5 * a0[i] * h * h;

    // new acceleration (uses old velocity for damping estimate, then corrects)
    const tau1 = torque(thNew, pairs, n);
    const thdNew = new Float64Array(n);
    // solve v_{k+1} = v_k + h/2 (a0 + a1) with a1 depending on v_{k+1} (damping)
    // a1 = (τ1 - γ v1)/I  =>  v1 (1 + hγ/(2I)) = v_k + h/2 a0 + h/(2I) τ1
    const denom = 1 + (h * gamma) / (2 * I);
    for (let i = 0; i < n; i++) {
        thdNew[i] = (thd[i] + 0.5 * h * a0[i] + (h / (2 * I)) * tau1[i]) / denom;
    }

    return {theta: thNew, thetaDot: thdNew};
}

/**
 * Implicit midpoint variational integrator (conservative, γ ignored here).
 * Discrete Lagrangian via midpoint quadrature:
 *   L_d(q_k, q_{k+1}) = h [ (I/2) ((q_{k+1}-q_k)/h)^2 - U((q_k+q_{k+1})/2) ]
 *
 * Using the position–momentum form:
 *   p_k       = -D1 L_d = I (q_{k+1}-q_k)/h + (h/2) ∇U(q_mid)
 *   p_{k+1}   =  D2 L_d = I (q_{k+1}-q_k)/h - (h/2) ∇U(q_mid)
 *
 * Given (q_k, p_k) solve for q_{k+1} via Newton, then update p_{k+1}.
 * We carry p = I θdot alongside the state.
 */
export function variationalStep(state, pairs, params, h) {
    const {I} = params;
    const n = state.theta.length;
    const q = state.theta;
    // momentum from velocity
    const p = new Float64Array(n);
    for (let i = 0; i < n; i++) p[i] = I * state.thetaDot[i];

    // Solve F(q1) = I (q1 - q)/h + (h/2) ∇U((q+q1)/2) - p = 0
    const q1 = Float64Array.from(q);
    for (let i = 0; i < n; i++) q1[i] += state.thetaDot[i] * h; // initial guess

    const maxIter = 30;
    for (let iter = 0; iter < maxIter; iter++) {
        const qmid = new Float64Array(n);
        for (let i = 0; i < n; i++) qmid[i] = 0.5 * (q[i] + q1[i]);
        const gU = gradient(qmid, pairs, n);
        const F = new Float64Array(n);
        for (let i = 0; i < n; i++) F[i] = (I * (q1[i] - q[i])) / h + 0.5 * h * gU[i] - p[i];

        let norm = 0;
        for (let i = 0; i < n; i++) norm += F[i] * F[i];
        if (Math.sqrt(norm) < 1e-12) break;

        // Jacobian dF/dq1 = (I/h) Identity + (h/4) H(qmid)
        const H = hessian(qmid, pairs, n);
        const J = new Array(n);
        for (let i = 0; i < n; i++) {
            J[i] = new Float64Array(n);
            for (let j = 0; j < n; j++) J[i][j] = 0.25 * h * H[i][j];
            J[i][i] += I / h;
        }
        const negF = new Float64Array(n);
        for (let i = 0; i < n; i++) negF[i] = -F[i];
        const dq = luSolve(J, negF);
        for (let i = 0; i < n; i++) q1[i] += dq[i];
    }

    // p_{k+1} = I (q1 - q)/h - (h/2) ∇U(qmid)
    const qmid = new Float64Array(n);
    for (let i = 0; i < n; i++) qmid[i] = 0.5 * (q[i] + q1[i]);
    const gU = gradient(qmid, pairs, n);
    const thetaDot = new Float64Array(n);
    for (let i = 0; i < n; i++) {
        const p1 = (I * (q1[i] - q[i])) / h - 0.5 * h * gU[i];
        thetaDot[i] = p1 / I;
    }
    return {theta: q1, thetaDot};
}

/** Dispatch by name. */
export function step(state, pairs, params, h, method = 'verlet') {
    if (method === 'variational') return variationalStep(state, pairs, params, h);
    return verletStep(state, pairs, params, h);
}
