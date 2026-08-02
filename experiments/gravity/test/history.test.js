import assert from 'assert';
import { StateHistory } from '../src/history.js';

describe('StateHistory', () => {
  it('records and reports newest/oldest', () => {
    const h = new StateHistory(8);
    h.record(0, { x: 0, y: 0 }, { x: 1, y: 0 });
    h.record(1, { x: 1, y: 0 }, { x: 1, y: 0 });
    assert.strictEqual(h.oldest().t, 0);
    assert.strictEqual(h.newest().t, 1);
  });

  it('wraps around the ring buffer', () => {
    const h = new StateHistory(4);
    for (let i = 0; i < 10; i++) h.record(i, { x: i, y: 0 }, { x: 1, y: 0 });
    assert.strictEqual(h.count, 4);
    assert.strictEqual(h.oldest().t, 6);
    assert.strictEqual(h.newest().t, 9);
  });

  it('linear interpolation matches a straight line', () => {
    const h = new StateHistory(16);
    for (let i = 0; i <= 10; i++) {
      h.record(i, { x: 2 * i, y: -i }, { x: 2, y: -1 });
    }
    const p = h.interpolate(3.5, false);
    assert.ok(Math.abs(p.x - 7) < 1e-9);
    assert.ok(Math.abs(p.y + 3.5) < 1e-9);
  });

  it('clamps to endpoints out of range', () => {
    const h = new StateHistory(16);
    h.record(0, { x: 0, y: 0 }, { x: 0, y: 0 });
    h.record(2, { x: 4, y: 0 }, { x: 0, y: 0 });
    assert.deepStrictEqual(h.interpolate(-5, false), { x: 0, y: 0 });
    assert.deepStrictEqual(h.interpolate(99, false), { x: 4, y: 0 });
  });

  it('hermite interpolation reproduces a cubic-ish curve endpoints', () => {
    const h = new StateHistory(16);
    // y = t^2, velocity dy/dt = 2t
    for (let i = 0; i <= 5; i++) {
      h.record(i, { x: i, y: i * i }, { x: 1, y: 2 * i });
    }
    const p = h.interpolate(2.5, true);
    // exact cubic Hermite of y=t^2 is exact between nodes
    assert.ok(Math.abs(p.y - 6.25) < 1e-6);
  });
  it('linear velocity interpolation matches recorded velocities', () => {
    const h = new StateHistory(16);
    for (let i = 0; i <= 5; i++) {
      h.record(i, { x: i, y: 0 }, { x: 2 * i, y: -i });
    }
    const v = h.interpolateVelocity(2.5, false);
    assert.ok(Math.abs(v.x - 5) < 1e-9);
    assert.ok(Math.abs(v.y + 2.5) < 1e-9);
  });
  it('hermite velocity interpolation matches derivative of y=t^2', () => {
    const h = new StateHistory(16);
    // y = t^2, dy/dt = 2t -> at t=2.5 velocity should be 5
    for (let i = 0; i <= 5; i++) {
      h.record(i, { x: i, y: i * i }, { x: 1, y: 2 * i });
    }
    const v = h.interpolateVelocity(2.5, true);
    assert.ok(Math.abs(v.y - 5) < 1e-6);
    assert.ok(Math.abs(v.x - 1) < 1e-6);
  });
});
