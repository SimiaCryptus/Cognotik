import { strict as assert } from 'node:assert';
import test from 'node:test';
import { euclidean, deltaEOK, deltaE76, deltaE2000 } from '../src/colorspace/distance.js';

const approx = (a, b, eps = 1e-4) => Math.abs(a - b) <= eps;

test('euclidean of identical points is zero', () => {
  const p = { L: 0.5, a: 0.1, b: -0.2 };
  assert.equal(euclidean(p, p), 0);
});

test('deltaEOK matches manual Euclidean in OKLab', () => {
  const a = { L: 0.5, a: 0.1, b: 0.1 };
  const b = { L: 0.6, a: 0.0, b: -0.1 };
  const expected = Math.sqrt(0.1 ** 2 + 0.1 ** 2 + 0.2 ** 2);
  assert.ok(approx(deltaEOK(a, b), expected));
});

test('deltaE76 is Euclidean in Lab', () => {
  const a = { L: 50, a: 10, b: -20 };
  const b = { L: 55, a: 12, b: -18 };
  const expected = Math.sqrt(25 + 4 + 4);
  assert.ok(approx(deltaE76(a, b), expected));
});

test('deltaE2000 of identical colors is zero', () => {
  const c = { L: 40, a: 5, b: -3 };
  assert.ok(approx(deltaE2000(c, c), 0));
});

// Reference pair from Sharma et al. CIEDE2000 test data (dataset row).
test('deltaE2000 matches a known reference value', () => {
  const a = { L: 50, a: 2.6772, b: -79.7751 };
  const b = { L: 50, a: 0.0, b: -82.7485 };
  // Expected ~2.0425 from the Sharma test set.
  assert.ok(approx(deltaE2000(a, b), 2.0425, 1e-3));
});
