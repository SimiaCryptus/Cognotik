import { strict as assert } from 'node:assert';
import test from 'node:test';
import { project, SUPPORTED_SPACES } from '../src/project/projector.js';
import { isInGamut, outOfGamutDistance } from '../src/project/gamut.js';
import { rgbToOklab } from '../src/colorspace/oklab.js';

// A mid-gray built from sRGB so we know it's safely in gamut.
const gray = { id: 'gray', role: 'neutral', ...rgbToOklab({ r: 0.5, g: 0.5, b: 0.5 }) };
const red = { id: 'red', role: 'accent', ...rgbToOklab({ r: 1, g: 0, b: 0 }) };

test('supports all documented spaces', () => {
  for (const space of SUPPORTED_SPACES) {
    const out = project([gray], space);
    assert.equal(out.space, space);
    assert.equal(out.colors.length, 1);
    assert.ok(out.colors[0].coords);
  }
});

test('space matching is case-insensitive', () => {
  const out = project([gray], 'hsl');
  assert.equal(out.space, 'HSL');
});

test('rejects unknown spaces', () => {
  assert.throws(() => project([gray], 'CMYK'), /Unsupported projection space/);
});

test('preserves id and role', () => {
  const out = project([gray, red], 'HSL');
  assert.equal(out.colors[0].id, 'gray');
  assert.equal(out.colors[0].role, 'neutral');
  assert.equal(out.colors[1].id, 'red');
});

test('in-gamut colors are not flagged clipped', () => {
  const out = project([gray], 'RGB');
  assert.equal(out.colors[0].clipped, false);
  assert.equal(out.gamutClippedFraction, 0);
});

test('HSL projection of gray has ~zero saturation', () => {
  const out = project([gray], 'HSL');
  assert.ok(out.colors[0].coords.s < 1e-3);
});

test('canonical accessors are populated for HSL', () => {
  const out = project([red], 'HSL');
  const c = out.colors[0];
  assert.equal(typeof c.lightness, 'number');
  assert.equal(typeof c.chroma, 'number');
  assert.equal(typeof c.hue, 'number');
});

test('out-of-gamut color is soft-compressed by default', () => {
  // A very high-chroma OKLab point outside sRGB.
  const wild = { id: 'wild', L: 0.7, a: 0.3, b: 0.2 };
  const out = project([wild], 'RGB');
  assert.equal(out.colors[0].clipped, true);
  assert.equal(out.gamutClippedFraction, 1);
  assert.ok(isInGamut(out.colors[0].rgb));
});

test("gamut='none' passes clip through but still reports clipping", () => {
  const wild = { id: 'wild', L: 0.7, a: 0.3, b: 0.2 };
  const out = project([wild], 'RGB', { gamut: 'none' });
  assert.equal(out.colors[0].clipped, true);
  assert.ok(isInGamut(out.colors[0].rgb));
});

test('outOfGamutDistance is zero for in-gamut colors', () => {
  assert.equal(outOfGamutDistance({ r: 0.5, g: 0.5, b: 0.5 }), 0);
  assert.ok(outOfGamutDistance({ r: 1.5, g: 0, b: 0 }) > 0);
});

test('OK spaces are gamut-independent in coordinate value', () => {
  const wild = { id: 'wild', L: 0.7, a: 0.3, b: 0.2 };
  const out = project([wild], 'OKLab');
  // OKLab coords passed through unchanged even though out of sRGB gamut.
  assert.equal(out.colors[0].coords.L, 0.7);
  assert.equal(out.colors[0].coords.a, 0.3);
  assert.equal(out.colors[0].clipped, true);
});

test('accepts a Palette-like object with a colors array', () => {
  const out = project({ colors: [gray] }, 'Lab');
  assert.equal(out.colors.length, 1);
});
