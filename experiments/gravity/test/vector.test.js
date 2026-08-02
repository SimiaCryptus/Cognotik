import assert from 'assert';
import { add, sub, scale, dot, len, normalize, lerp, dist } from '../src/vector.js';

describe('vector', () => {
  it('adds and subtracts', () => {
    assert.deepStrictEqual(add({ x: 1, y: 2 }, { x: 3, y: 4 }), { x: 4, y: 6 });
    assert.deepStrictEqual(sub({ x: 5, y: 5 }, { x: 1, y: 2 }), { x: 4, y: 3 });
  });

  it('scales and dots', () => {
    assert.deepStrictEqual(scale({ x: 2, y: 3 }, 2), { x: 4, y: 6 });
    assert.strictEqual(dot({ x: 1, y: 0 }, { x: 0, y: 1 }), 0);
    assert.strictEqual(dot({ x: 2, y: 3 }, { x: 4, y: 5 }), 23);
  });

  it('computes length and distance', () => {
    assert.strictEqual(len({ x: 3, y: 4 }), 5);
    assert.strictEqual(dist({ x: 0, y: 0 }, { x: 3, y: 4 }), 5);
  });

  it('normalizes (incl. zero vector)', () => {
    const n = normalize({ x: 0, y: 5 });
    assert.ok(Math.abs(n.y - 1) < 1e-12);
    assert.deepStrictEqual(normalize({ x: 0, y: 0 }), { x: 0, y: 0 });
  });

  it('lerps', () => {
    assert.deepStrictEqual(lerp({ x: 0, y: 0 }, { x: 10, y: 20 }, 0.5), { x: 5, y: 10 });
  });
});
