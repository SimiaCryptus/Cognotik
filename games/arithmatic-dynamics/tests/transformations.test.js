import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parse, serialize } from '../src/core/serialize.js';
import { evaluate } from '../src/core/value.js';
import {
  split,
  swap,
  group,
  ungroup,
  combine,
  cancel,
  distribute,
  extract,
} from '../src/core/transformations.js';

test('extract collects a common factor across two products in a sum', () => {
  const ast = parse('2 * 3 + 2 * 5');
  const next = extract(ast, [ast.terms[0].id, ast.terms[1].id]);
  assert.equal(evaluate(next), evaluate(ast));
  assert.equal(serialize(next), '2 * (3 + 5)');
});

test('extract works across three products', () => {
  const ast = parse('2 * 3 + 2 * 5 + 2 * 7');
  const ids = ast.terms.map((t) => t.id);
  const next = extract(ast, ids);
  assert.equal(evaluate(next), evaluate(ast));
  assert.equal(serialize(next), '2 * (3 + 5 + 7)');
});

test('extract rejects terms with no common factor', () => {
  const ast = parse('2 * 3 + 5 * 7');
  assert.throws(() => extract(ast, [ast.terms[0].id, ast.terms[1].id]), /no common factor/);
});

test('distribute then extract round-trips value', () => {
  const ast = parse('2 * (3 + 5)');
  const groupId = ast.factors[1].id;
  const factorId = ast.factors[0].id;
  const distributed = distribute(ast, factorId, groupId);
  assert.equal(evaluate(distributed), evaluate(ast));
});
