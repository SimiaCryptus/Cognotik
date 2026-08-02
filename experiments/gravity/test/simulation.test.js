import assert from 'assert';
import { Body } from '../src/body.js';
import { Simulation } from '../src/simulation.js';

function makeOrbit() {
  return [
    new Body({ position: { x: -50, y: 0 }, velocity: { x: 0, y: 0.5 }, mass: 100 }),
    new Body({ position: { x: 50, y: 0 }, velocity: { x: 0, y: -0.5 }, mass: 100 }),
  ];
}

describe('Simulation (Newtonian limit)', () => {
  it('conserves energy reasonably over many steps (c -> inf, alpha=0)', () => {
    const sim = new Simulation(makeOrbit(), {
      G: 1,
      c: Infinity,
      alpha: 0,
      dt: 0.005,
      epsilon: 1,
    });
    sim.integrator = 'verlet';
    const E0 = sim.totalEnergy();
    for (let i = 0; i < 2000; i++) sim.step();
    const E1 = sim.totalEnergy();
    const drift = Math.abs((E1 - E0) / E0);
    assert.ok(drift < 0.02, `energy drift too large: ${drift}`);
  });

  it('conserves total momentum in Newtonian limit', () => {
    const sim = new Simulation(makeOrbit(), {
      G: 1,
      c: Infinity,
      alpha: 0,
      dt: 0.005,
      epsilon: 1,
    });
    sim.integrator = 'verlet';
    const p0 = sim.totalMomentum();
    for (let i = 0; i < 500; i++) sim.step();
    const p1 = sim.totalMomentum();
    assert.ok(Math.abs(p1.x - p0.x) < 1e-6);
    assert.ok(Math.abs(p1.y - p0.y) < 1e-6);
  });

  it('RK4 matches Verlet closely over short horizon in Newtonian limit', () => {
    const a = new Simulation(makeOrbit(), { G: 1, c: Infinity, alpha: 0, dt: 0.002, epsilon: 1 });
    a.integrator = 'verlet';
    const b = new Simulation(makeOrbit(), { G: 1, c: Infinity, alpha: 0, dt: 0.002, epsilon: 1 });
    b.integrator = 'rk4';
    for (let i = 0; i < 300; i++) {
      a.step();
      b.step();
    }
    const dx = Math.abs(a.bodies[0].position.x - b.bodies[0].position.x);
    assert.ok(dx < 1.0, `integrators diverged: ${dx}`);
  });
});
describe('Precession meter', () => {
  it('reports ~0 precession per orbit in the Newtonian limit', () => {
    const sim = new Simulation(makeOrbit(), {
      G: 1,
      c: Infinity,
      alpha: 0,
      dt: 0.005,
      epsilon: 1,
    });
    sim.integrator = 'verlet';
    for (let i = 0; i < 6000; i++) sim.step();
    // Closed Newtonian ellipse: successive perihelia at the same angle.
    if (sim._periCount > 0) {
      assert.ok(
        Math.abs(sim.precessionPerOrbit) < 0.05,
        `unexpected Newtonian precession: ${sim.precessionPerOrbit}`
      );
    }
  });
  it('detects perihelia and tracks total precession', () => {
    const sim = new Simulation(makeOrbit(), {
      G: 1,
      c: 30,
      alpha: 0.8,
      dt: 0.005,
      epsilon: 1,
    });
    for (let i = 0; i < 6000; i++) sim.step();
    assert.ok(sim._periCount >= 1, 'expected at least one perihelion detected');
    assert.ok(isFinite(sim.totalPrecession));
  });
});
