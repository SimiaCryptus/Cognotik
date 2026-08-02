// Terminal / ASCII swatch preview (spec v0.1) using 24-bit ANSI color.
//
// Renders a projected palette as a row of colored blocks with ids, plus an
// optional distortion summary line — enough to build the flagship
// side-by-side "OKLab vs naive HSL" demonstration in a terminal.

import { convert } from '../colorspace/convert.js';

function clamp255(x) {
  return Math.min(255, Math.max(0, Math.round(x * 255)));
}

function ansiBg({ r, g, b }) {
  return `\x1b[48;2;${clamp255(r)};${clamp255(g)};${clamp255(b)}m`;
}

const RESET = '\x1b[0m';

/**
 * @param {object} projected  Output of project().
 * @param {object} [options]
 * @param {number} [options.width=4]   Cells per swatch.
 * @param {string} [options.label]     Optional heading printed above the row.
 * @returns {string}  ANSI-colored preview text.
 */
export function exportTerminal(projected, options = {}) {
  const width = options.width ?? 4;
  const block = ' '.repeat(width);
  const swatches = projected.colors
    .map((c) => {
      const rgb = c.rgb ?? convert(c.coords, c.space, 'RGB');
      return `${ansiBg(rgb)}${block}${RESET}`;
    })
    .join('');
  const ids = projected.colors.map((c) => (c.id ?? '').padEnd(width).slice(0, width)).join('');
  const heading = options.label ? `${options.label}\n` : '';
  return `${heading}${swatches}\n${ids}\n`;
}
