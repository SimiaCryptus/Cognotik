// Export a projected palette as a design-token JSON structure.

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

/**
 * @param {object} projected  Output of project().
 * @param {object} [options]
 * @param {boolean} [options.pretty=true]  Pretty-print the JSON string.
 * @returns {string}  JSON text.
 */
export function exportJSON(projected, options = {}) {
  const pretty = options.pretty ?? true;
  const tokens = {};
  for (const c of projected.colors) {
    tokens[c.id] = {
      role: c.role ?? null,
      space: c.space,
      coords: c.coords,
      hex: toHex(c.rgb ?? convert(c.coords, c.space, 'RGB')),
      clipped: !!c.clipped,
    };
  }
  const doc = { space: projected.space, tokens };
  return JSON.stringify(doc, null, pretty ? 2 : 0);
}
