import { test } from 'node:test';
import assert from 'node:assert/strict';
import { evaluate } from '../src/core/value.js';
import { parse } from '../src/core/serialize.js';
import { combine } from '../src/core/transformations.js';

test('evaluates addition and subtraction', () => {
  assert.equal(evaluate(parse('4 + 19')), 23);
  assert.equal(evaluate(parse('20 + 5 - 1 - 1')), 23);
});

test('respects precedence and grouping', () => {
  assert.equal(evaluate(parse('2 + 3 * 4')), 14);
  assert.equal(evaluate(parse('(2 + 3) * 4')), 20);
});

test('exact division combines; non-exact is rejected at combine time', () => {
  assert.equal(evaluate(parse('12 / 4')), 3);
  const bad = parse('7 / 2');
  assert.throws(() => combine(bad, bad.factors[0].id, bad.factors[1].id), /non-exact/i);
});
