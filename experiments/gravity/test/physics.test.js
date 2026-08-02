import assert from 'assert';
import { newtonianAccel, gamma } from '../src/physics.js';

const params = { G: 1, epsilon: 0 };

describe('physics', () => {
  it('newtonian accel points toward the source', () => {
    const a = newtonianAccel({ x: 0, y: 0 }, { x: 10, y: 0 }, 100, params);
    assert.ok(a.x > 0);
    assert.ok(Math.abs(a.y) < 1e-12);
  });

  it('newtonian accel obeys inverse square', () => {
    const near = newtonianAccel({ x: 0, y: 0 }, { x: 1, y: 0 }, 1, params);
    const far = newtonianAccel({ x: 0, y: 0 }, { x: 2, y: 0 }, 1, params);
    // doubling distance -> 1/4 the magnitude
    assert.ok(Math.abs(far.x - near.x / 4) < 1e-9);
  });

  it('softening prevents blow-up at zero distance', () => {
    const a = newtonianAccel({ x: 0, y: 0 }, { x: 0, y: 0 }, 100, { G: 1, epsilon: 1 });
    assert.ok(isFinite(a.x) && isFinite(a.y));
  });

  it('gamma is 1 at rest and grows with speed', () => {
    assert.ok(Math.abs(gamma({ x: 0, y: 0 }, 10) - 1) < 1e-12);
    assert.ok(gamma({ x: 8, y: 0 }, 10) > 1.5);
  });

  it('gamma is clamped below c', () => {
    const g = gamma({ x: 1000, y: 0 }, 10);
    assert.ok(isFinite(g));
  });
});
