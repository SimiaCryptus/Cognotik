// Stateless dipole physics: energy U, gradient (∇U = -τ), and Hessian H.
//
// Model: each magnet i has fixed position r_i and orientation θ_i.
// Dipole μ_i = m (cos θ_i, sin θ_i).
// Pairwise energy with d = r_j - r_i, r = |d|, n = d/r:
//   U_ij = (k / r^3) [ μ_i·μ_j - 3 (μ_i·n)(μ_j·n) ]
//
// We precompute per-pair geometric factors so the hot loop is cheap.

/**
 * Precompute pair data: for each pair (i<j), store indices and geometric
 * angle φ of the separation vector plus the coefficient k*m^2 / r^3.
 * This lets us write U_ij in a compact trig form (see below).
 */
export function buildPairs(positions, params) {
    const {k, m} = params;
    const n = positions.length;
    const pairs = [];
    for (let i = 0; i < n; i++) {
        for (let j = i + 1; j < n; j++) {
            const dx = positions[j][0] - positions[i][0];
            const dy = positions[j][1] - positions[i][1];
            const r2 = dx * dx + dy * dy;
            const r = Math.sqrt(r2);
            if (r === 0) continue; // coincident (shouldn't happen on grid)
            const phi = Math.atan2(dy, dx); // angle of separation vector
            const coeff = (k * m * m) / (r * r * r);
            pairs.push({i, j, phi, coeff});
        }
    }
    return pairs;
}

// Derivation of pair energy in trig form.
// With unit vectors a_i = (cos θ_i, sin θ_i), n = (cos φ, sin φ):
//   μ_i·μ_j = m^2 cos(θ_i - θ_j)
//   (μ_i·n)(μ_j·n) = m^2 cos(θ_i - φ) cos(θ_j - φ)
// So U_ij = coeff * [ cos(θ_i - θ_j) - 3 cos(θ_i - φ) cos(θ_j - φ) ]
// where coeff = k m^2 / r^3.

/** Total potential energy U(θ). */
export function energy(theta, pairs) {
    let U = 0;
    for (const p of pairs) {
        const ti = theta[p.i],
            tj = theta[p.j];
        const term = Math.cos(ti - tj) - 3 * Math.cos(ti - p.phi) * Math.cos(tj - p.phi);
        U += p.coeff * term;
    }
    return U;
}

/**
 * Gradient of U w.r.t. θ. gradient[i] = ∂U/∂θ_i = -τ_i.
 * ∂U_ij/∂θ_i = coeff * [ -sin(θ_i - θ_j) + 3 sin(θ_i - φ) cos(θ_j - φ) ]
 * ∂U_ij/∂θ_j = coeff * [  sin(θ_i - θ_j) + 3 cos(θ_i - φ) sin(θ_j - φ) ]
 */
export function gradient(theta, pairs, n) {
    const g = new Float64Array(n);
    for (const p of pairs) {
        const ti = theta[p.i],
            tj = theta[p.j];
        const dij = ti - tj;
        const ai = ti - p.phi,
            aj = tj - p.phi;
        const gi = p.coeff * (-Math.sin(dij) + 3 * Math.sin(ai) * Math.cos(aj));
        const gj = p.coeff * (Math.sin(dij) + 3 * Math.cos(ai) * Math.sin(aj));
        g[p.i] += gi;
        g[p.j] += gj;
    }
    return g;
}

/** Torque τ_i = -∂U/∂θ_i. */
export function torque(theta, pairs, n) {
    const g = gradient(theta, pairs, n);
    for (let i = 0; i < n; i++) g[i] = -g[i];
    return g;
}

/**
 * Hessian H_ij = ∂²U / ∂θ_i ∂θ_j (dense, symmetric, n×n).
 * Second derivatives of U_ij:
 *   ∂²/∂θ_i²   = coeff * [ -cos(θ_i-θ_j) + 3 cos(θ_i-φ) cos(θ_j-φ) ]
 *   ∂²/∂θ_j²   = coeff * [ -cos(θ_i-θ_j) + 3 cos(θ_i-φ) cos(θ_j-φ) ]
 *   ∂²/∂θ_i∂θ_j= coeff * [  cos(θ_i-θ_j) + 3 sin(θ_i-φ) sin(θ_j-φ) ]
 */
export function hessian(theta, pairs, n) {
    const H = new Array(n);
    for (let i = 0; i < n; i++) H[i] = new Float64Array(n);
    for (const p of pairs) {
        const ti = theta[p.i],
            tj = theta[p.j];
        const c = Math.cos(ti - tj);
        const ai = ti - p.phi,
            aj = tj - p.phi;
        const diag = p.coeff * (-c + 3 * Math.cos(ai) * Math.cos(aj));
        const cross = p.coeff * (c + 3 * Math.sin(ai) * Math.sin(aj));
        H[p.i][p.i] += diag;
        H[p.j][p.j] += diag;
        H[p.i][p.j] += cross;
        H[p.j][p.i] += cross;
    }
    return H;
}

/** Kinetic energy T = 1/2 I Σ θdot². */
export function kinetic(thetaDot, I) {
    let T = 0;
    for (let i = 0; i < thetaDot.length; i++) T += thetaDot[i] * thetaDot[i];
    return 0.5 * I * T;
}

/** Total angular momentum L = I Σ θdot. */
export function angularMomentum(thetaDot, I) {
    let L = 0;
    for (let i = 0; i < thetaDot.length; i++) L += thetaDot[i];
    return I * L;
}

/** Finite-difference gradient (for validation only). */
export function gradientFD(theta, pairs, n, eps = 1e-6) {
    const g = new Float64Array(n);
    const t = Float64Array.from(theta);
    for (let i = 0; i < n; i++) {
        t[i] = theta[i] + eps;
        const up = energy(t, pairs);
        t[i] = theta[i] - eps;
        const dn = energy(t, pairs);
        t[i] = theta[i];
        g[i] = (up - dn) / (2 * eps);
    }
    return g;
}

/** Finite-difference Hessian (for validation only). */
export function hessianFD(theta, pairs, n, eps = 1e-5) {
    const H = new Array(n);
    for (let i = 0; i < n; i++) H[i] = new Float64Array(n);
    const t = Float64Array.from(theta);
    for (let i = 0; i < n; i++) {
        t[i] = theta[i] + eps;
        const gp = gradient(t, pairs, n);
        t[i] = theta[i] - eps;
        const gm = gradient(t, pairs, n);
        t[i] = theta[i];
        for (let j = 0; j < n; j++) H[j][i] = (gp[j] - gm[j]) / (2 * eps);
    }
    return H;
}
