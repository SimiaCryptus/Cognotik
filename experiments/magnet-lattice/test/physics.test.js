import {describe, it, assert, assertClose, assertRelClose} from './harness.js';
import {
    buildPairs,
    energy,
    gradient,
    hessian,
    gradientFD,
    hessianFD,
} from '../src/model/physics.js';

const params = {k: 1.0, m: 1.0};

function randTheta(n) {
    const t = new Float64Array(n);
    for (let i = 0; i < n; i++) t[i] = (Math.random() - 0.5) * 2 * Math.PI;
    return t;
}

describe('physics: analytic gradient vs finite-difference', () => {
    it('matches for random 4-magnet config', () => {
        const pos = [
            [0, 0],
            [48, 0],
            [0, 48],
            [48, 48],
        ];
        const pairs = buildPairs(pos, params);
        const n = pos.length;
        for (let trial = 0; trial < 5; trial++) {
            const theta = randTheta(n);
            const g = gradient(theta, pairs, n);
            const gfd = gradientFD(theta, pairs, n);
            for (let i = 0; i < n; i++) assertRelClose(g[i], gfd[i], 1e-5, `grad[${i}]`);
        }
    });
});

describe('physics: analytic Hessian vs finite-difference', () => {
    it('matches for random 3-magnet config', () => {
        const pos = [
            [0, 0],
            [48, 0],
            [24, 40],
        ];
        const pairs = buildPairs(pos, params);
        const n = pos.length;
        const theta = randTheta(n);
        const H = hessian(theta, pairs, n);
        const Hfd = hessianFD(theta, pairs, n);
        for (let i = 0; i < n; i++)
            for (let j = 0; j < n; j++) assertClose(H[i][j], Hfd[i][j], 1e-4, `H[${i}][${j}]`);
    });

    it('is symmetric', () => {
        const pos = [
            [0, 0],
            [48, 0],
            [24, 40],
        ];
        const pairs = buildPairs(pos, params);
        const n = pos.length;
        const H = hessian(randTheta(n), pairs, n);
        for (let i = 0; i < n; i++)
            for (let j = 0; j < n; j++) assertClose(H[i][j], H[j][i], 1e-12, 'symmetry');
    });
});

describe('physics: 2-magnet ground state', () => {
    it('head-to-tail alignment along the axis is a minimum', () => {
        // two magnets on the x-axis; ground state is both pointing along x (θ=0)
        const pos = [
            [0, 0],
            [48, 0],
        ];
        const pairs = buildPairs(pos, params);
        const n = 2;
        // aligned along axis
        const aligned = new Float64Array([0, 0]);
        const gradAligned = gradient(aligned, pairs, n);
        assertClose(gradAligned[0], 0, 1e-12, 'grad0 at aligned');
        assertClose(gradAligned[1], 0, 1e-12, 'grad1 at aligned');
        const Ualigned = energy(aligned, pairs);
        // perpendicular config should have higher energy
        const perp = new Float64Array([Math.PI / 2, Math.PI / 2]);
        const Uperp = energy(perp, pairs);
        assert(Ualigned < Uperp, `aligned ${Ualigned} should be < perp ${Uperp}`);
    });
});
