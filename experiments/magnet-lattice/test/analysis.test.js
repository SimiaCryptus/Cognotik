import {describe, it, assert, assertClose, assertRelClose} from './harness.js';
import {buildPairs, gradient} from '../src/model/physics.js';
import {relax, analyze} from '../src/model/analysis.js';
import {jacobiEigen, luSolve} from '../src/math/linalg.js';

const params = {k: 1.0, m: 1.0, I: 1.0};

describe('linalg: luSolve', () => {
    it('solves a 3x3 system', () => {
        const A = [
            new Float64Array([2, 1, 1]),
            new Float64Array([1, 3, 2]),
            new Float64Array([1, 0, 0]),
        ];
        const b = new Float64Array([4, 5, 6]);
        const x = luSolve(A, b);
        // verify A x = b
        for (let i = 0; i < 3; i++) {
            let s = 0;
            for (let j = 0; j < 3; j++) s += A[i][j] * x[j];
            assertClose(s, b[i], 1e-10, `row ${i}`);
        }
    });
});

describe('linalg: jacobiEigen', () => {
    it('diagonal matrix eigenvalues', () => {
        const A = [
            new Float64Array([3, 0, 0]),
            new Float64Array([0, 1, 0]),
            new Float64Array([0, 0, 2]),
        ];
        const {values} = jacobiEigen(A);
        assertClose(values[0], 1, 1e-10);
        assertClose(values[1], 2, 1e-10);
        assertClose(values[2], 3, 1e-10);
    });

    it('known 2x2 symmetric matrix', () => {
        // [[2,1],[1,2]] -> eigenvalues 1 and 3
        const A = [new Float64Array([2, 1]), new Float64Array([1, 2])];
        const {values, vectors} = jacobiEigen(A);
        assertClose(values[0], 1, 1e-10);
        assertClose(values[1], 3, 1e-10);
        // eigenvector check A v = λ v
        for (let k = 0; k < 2; k++) {
            const v = vectors[k];
            for (let i = 0; i < 2; i++) {
                let s = 0;
                for (let j = 0; j < 2; j++) s += A[i][j] * v[j];
                assertClose(s, values[k] * v[i], 1e-9, `Av=λv k=${k} i=${i}`);
            }
        }
    });
});

describe('analysis: 2-magnet equilibrium & modes', () => {
    it('relax finds aligned ground state on x-axis', () => {
        const pos = [
            [0, 0],
            [48, 0],
        ];
        const pairs = buildPairs(pos, params);
        const {theta, gradNorm} = relax(new Float64Array([0.5, -0.3]), pairs, 2);
        assert(gradNorm < 1e-7, `gradNorm ${gradNorm}`);
        // both should align along ±x (θ ≈ 0 or π, same axis)
        const g = gradient(theta, pairs, 2);
        assertClose(g[0], 0, 1e-6);
        assertClose(g[1], 0, 1e-6);
    });

    it('normal modes: symmetric & antisymmetric', () => {
        const pos = [
            [0, 0],
            [48, 0],
        ];
        const pairs = buildPairs(pos, params);
        const {theta} = relax(new Float64Array([0.1, -0.1]), pairs, 2);
        const res = analyze(theta, pairs, 2, params);
        // at a stable equilibrium both eigenvalues should be >= 0
        assert(res.values[0] > -1e-6, `mode0 ω² ${res.values[0]}`);
        assert(res.values[1] > -1e-6, `mode1 ω² ${res.values[1]}`);
        // one mode is the uniform rotation-like / one is relative
        assert(res.omega.length === 2);
    });
});
