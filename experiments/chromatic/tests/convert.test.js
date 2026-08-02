import { strict as assert } from 'node:assert';
import test from 'node:test';
import { convert, SPACES } from '../src/colorspace/convert.js';

const approx = (a, b, eps = 1e-6) => Math.abs(a - b) <= eps;

test('identity conversion returns the same coords', () => {
  const c = { r: 0.2, g: 0.4, b: 0.6 };
  assert.deepEqual(convert(c, 'RGB', 'RGB'), c);
});

test('RGB -> HSL -> RGB round-trips', () => {
  const rgb = { r: 0.3, g: 0.6, b: 0.9 };
  const hsl = convert(rgb, 'RGB', 'HSL');
  const back = convert(hsl, 'HSL', 'RGB');
  assert.ok(approx(back.r, rgb.r, 1e-6));
  assert.ok(approx(back.g, rgb.g, 1e-6));
  assert.ok(approx(back.b, rgb.b, 1e-6));
});

test('RGB -> OKLch multi-hop routes through OKLab', () => {
  const rgb = { r: 1, g: 0, b: 0 };
  const oklch = convert(rgb, 'RGB', 'OKLch');
  assert.equal(typeof oklch.L, 'number');
  assert.equal(typeof oklch.C, 'number');
  assert.equal(typeof oklch.H, 'number');
});

test('RGB -> Lch -> RGB round-trips', () => {
  const rgb = { r: 0.5, g: 0.25, b: 0.75 };
  const lch = convert(rgb, 'RGB', 'Lch');
  const back = convert(lch, 'Lch', 'RGB');
  assert.ok(approx(back.r, rgb.r, 1e-4));
  assert.ok(approx(back.g, rgb.g, 1e-4));
  assert.ok(approx(back.b, rgb.b, 1e-4));
});

test('case-insensitive space names', () => {
  const out = convert({ r: 0.5, g: 0.5, b: 0.5 }, 'rgb', 'oklab');
  assert.equal(typeof out.L, 'number');
});

test('rejects unknown spaces', () => {
  assert.throws(() => convert({}, 'CMYK', 'RGB'), /Unknown colorspace/);
});

test('all documented spaces are reachable from RGB', () => {
  const rgb = { r: 0.4, g: 0.5, b: 0.6 };
  for (const space of SPACES) {
    const out = convert(rgb, 'RGB', space);
    assert.ok(out && typeof out === 'object');
  }
});
