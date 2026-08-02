import { strict as assert } from 'node:assert';
import test from 'node:test';
import { project } from '../src/project/projector.js';
import { exportCSS } from '../src/export/css.js';
import { exportJSON } from '../src/export/json.js';
import { exportTerminal } from '../src/export/terminal.js';
import { rgbToOklab } from '../src/colorspace/oklab.js';

const gray = {
  id: 'gray',
  role: 'neutral',
  ...rgbToOklab({ r: 0.5, g: 0.5, b: 0.5 }),
};
const red = { id: 'red', role: 'accent', ...rgbToOklab({ r: 1, g: 0, b: 0 }) };

test('exportCSS emits custom properties with the given prefix', () => {
  const css = exportCSS(project([gray, red], 'HSL'), { prefix: '--color-' });
  assert.match(css, /:root \{/);
  assert.match(css, /--color-gray:/);
  assert.match(css, /--color-red:/);
  assert.match(css, /hsl\(/);
});

test('exportCSS RGB space emits hex colors', () => {
  const css = exportCSS(project([gray], 'RGB'));
  assert.match(css, /--color-gray: #[0-9a-f]{6};/);
});

test('exportJSON produces parseable design tokens', () => {
  const json = exportJSON(project([gray, red], 'OKLch'));
  const doc = JSON.parse(json);
  assert.equal(doc.space, 'OKLch');
  assert.equal(doc.tokens.gray.role, 'neutral');
  assert.match(doc.tokens.red.hex, /^#[0-9a-f]{6}$/);
});

test('exportTerminal renders one swatch per color', () => {
  const out = exportTerminal(project([gray, red], 'RGB'), { label: 'demo' });
  assert.match(out, /demo/);
  // two ANSI background sequences
  const matches = out.match(/\x1b\[48;2;/g) ?? [];
  assert.equal(matches.length, 2);
});
