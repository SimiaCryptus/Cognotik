import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parse } from '../src/core/serialize.js';
import { legalVerbs, difficultyAllows } from '../src/core/legality.js';

test('extract offered on two products sharing a factor in a sum', () => {
  const ast = parse('2 * 3 + 2 * 5');
  const verbs = legalVerbs(ast, [ast.terms[0].id, ast.terms[1].id]);
  assert.ok(verbs.includes('extract'));
});

test('extract offered across 3+ terms sharing a factor', () => {
  const ast = parse('2 * 3 + 2 * 5 + 2 * 7');
  const ids = ast.terms.map((t) => t.id);
  assert.ok(legalVerbs(ast, ids).includes('extract'));
});

test('extract not offered when no common factor', () => {
  const ast = parse('2 * 3 + 5 * 7');
  const verbs = legalVerbs(ast, [ast.terms[0].id, ast.terms[1].id]);
  assert.ok(!verbs.includes('extract'));
});
