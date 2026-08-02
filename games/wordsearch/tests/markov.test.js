import assert from 'assert';
import { MarkovModel } from '../src/markov/MarkovModel.js';

describe('MarkovModel', () => {
  it('predicts and normalises a distribution', () => {
    const m = new MarkovModel(2).train('abab', 2);
    const dist = m.predict('ab');
    let total = 0;
    for (const v of dist.values()) total += v;
    assert.ok(Math.abs(total - 1) < 1e-9);
  });

  it('backs off when context unseen', () => {
    const m = new MarkovModel(3).train('hello world', 3);
    // 'zzz' never seen; back-off must still yield something.
    const dist = m.predict('zzz');
    assert.ok(dist.size > 0);
  });

  it('serialises round-trip', () => {
    const m = new MarkovModel(2).train('mississippi', 2);
    const json = JSON.parse(JSON.stringify(m.toJSON()));
    const m2 = MarkovModel.fromJSON(json);
    const a = [...m.predict('is').entries()].sort();
    const b = [...m2.predict('is').entries()].sort();
    assert.deepEqual(a, b);
  });
});
