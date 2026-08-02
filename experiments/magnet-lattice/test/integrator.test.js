import {describe, it, assert} from './harness.js';
import {buildPairs, energy, kinetic} from '../src/model/physics.js';
import {step} from '../src/model/integrator.js';

const params = {k: 1.0, m: 1.0, I: 1.0, gamma: 0.0};

function totalEnergy(state, pairs) {
    return kinetic(state.thetaDot, params.I) + energy(state.theta, pairs);
}

function runEnergyTest(method, steps, tol) {
    // 2-magnet system perturbed from equilibrium
    const pos = [
        [0, 0],
        [48, 0],
    ];
    const pairs = buildPairs(pos, params);
    let state = {theta: new Float64Array([0.3, -0.2]), thetaDot: new Float64Array([0, 0])};
    const E0 = totalEnergy(state, pairs);
    let maxDev = 0;
    const h = 0.01;
    for (let s = 0; s < steps; s++) {
        state = step(state, pairs, params, h, method);
        const E = totalEnergy(state, pairs);
        maxDev = Math.max(maxDev, Math.abs((E - E0) / E0));
    }
    return {maxDev, E0};
}

describe('integrator: symplectic energy conservation', () => {
    it('Verlet drift bounded over 20000 steps', () => {
        const {maxDev} = runEnergyTest('verlet', 20000, 1e-3);
        assert(maxDev < 1e-3, `verlet maxDev ${maxDev}`);
    });

    it('Variational drift bounded over 20000 steps', () => {
        const {maxDev} = runEnergyTest('variational', 20000, 1e-4);
        assert(maxDev < 1e-4, `variational maxDev ${maxDev}`);
    });
});

describe('integrator: damping dissipates energy', () => {
    it('energy decreases monotonically-ish with gamma>0', () => {
        const dparams = {...params, gamma: 0.5};
        const pos = [
            [0, 0],
            [48, 0],
        ];
        const pairs = buildPairs(pos, dparams);
        let state = {theta: new Float64Array([1.0, -0.5]), thetaDot: new Float64Array([0, 0])};
        const E0 = kinetic(state.thetaDot, dparams.I) + energy(state.theta, pairs);
        for (let s = 0; s < 5000; s++) state = step(state, pairs, dparams, 0.01, 'verlet');
        const Ef = kinetic(state.thetaDot, dparams.I) + energy(state.theta, pairs);
        assert(Ef < E0, `final ${Ef} should be < initial ${E0}`);
    });
});
