// Export a projected palette as CSS custom properties.
//
// Accepts the output of project() (a projected palette) and emits a
// `:root { --prefix-id: <css-color>; }` block. Color syntax is chosen based
// on the projected space; falls back to sRGB hex for maximum compatibility.

import { convert } from '../colorspace/convert.js';

function clamp01(x) {
  return Math.min(1, Math.max(0, x));
}

function toHex({ r, g, b }) {
  const h = (c) =>
    Math.round(clamp01(c) * 255)
      .toString(16)
      .padStart(2, '0');
  return `#${h(r)}${h(g)}${h(b)}`;
}

function cssColor(color) {
  const { space, coords, rgb } = color;
  switch (space) {
    case 'HSL': {
      const h = Math.round(coords.h);
      const s = Math.round(coords.s * 100);
      const l = Math.round(coords.l * 100);
      return `hsl(${h} ${s}% ${l}%)`;
    }
    case 'RGB':
      return toHex(coords);
    default:
      // Fall back to the sRGB representation captured during projection.
      return toHex(rgb ?? convert(coords, space, 'RGB'));
  }
}

/**
 * @param {object} projected     Output of project().
 * @param {object} [options]
 * @param {string} [options.prefix="--color-"]  Custom-property prefix.
 * @param {string} [options.selector=":root"]   Wrapping selector.
 * @returns {string}  CSS text.
 */
export function exportCSS(projected, options = {}) {
  const prefix = options.prefix ?? '--color-';
  const selector = options.selector ?? ':root';
  const lines = projected.colors.map((c) => {
    const name = `${prefix}${c.id}`;
    return `  ${name}: ${cssColor(c)};`;
  });
  return `${selector} {\n${lines.join('\n')}\n}\n`;
}
