import assert from 'assert';
import { combine } from '../src/fill/combiners.js';

function d(obj) {
  return new Map(Object.entries(obj));
}

describe('combiners', () => {
  it('product normalises to 1', () => {
    const out = combine([d({ a: 0.5, b: 0.5 }), d({ a: 0.8, b: 0.2 })], 'product');
    let t = 0;
    for (const v of out.values()) t += v;
    assert.ok(Math.abs(t - 1) < 1e-9);
    assert.ok(out.get('a') > out.get('b'));
  });

  it('vote picks per-direction argmax', () => {
    const out = combine(
      [d({ a: 0.9, b: 0.1 }), d({ a: 0.6, b: 0.4 }), d({ b: 0.7, a: 0.3 })],
      'vote'
    );
    assert.ok(out.get('a') > out.get('b'));
  });

  it('empty input gives empty map', () => {
    assert.equal(combine([], 'sum').size, 0);
  });
});
