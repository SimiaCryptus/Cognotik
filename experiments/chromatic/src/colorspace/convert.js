// Unified graph-based color conversion router.
//
// convert(color, from, to) finds a path through the conversion graph and
// applies each edge in turn. Edges are registered as pairwise functions so
// that importing a single pairwise conversion (e.g. oklab.rgbToOklab) does
// NOT pull in this router or unrelated colorspace modules — the tree-shaking
// contract from spec §5/§9.
import { srgbToLinear, linearToSrgb } from './srgb.js';
import { rgbToHsl, hslToRgb } from './hsl.js';
import { rgbToHsv, hsvToRgb } from './hsv.js';
import { rgbToLab, labToRgb, labToLch, lchToLab } from './lab.js';
import { rgbToOklab, oklabToRgb, oklabToOklch, oklchToOklab } from './oklab.js';

// Canonical space names.
export const SPACES = ['RGB', 'LinearRGB', 'HSL', 'HSV', 'Lab', 'Lch', 'OKLab', 'OKLch'];

function canonical(space) {
  const found = SPACES.find((s) => s.toLowerCase() === String(space).toLowerCase());
  if (!found) {
    throw new Error(`Unknown colorspace: ${space}. Expected one of ${SPACES.join(', ')}.`);
  }
  return found;
}

// Directed adjacency list of pairwise conversions. "RGB" is the hub.
const EDGES = {
  RGB: {
    LinearRGB: srgbToLinear,
    HSL: rgbToHsl,
    HSV: rgbToHsv,
    Lab: rgbToLab,
    OKLab: rgbToOklab,
  },
  LinearRGB: {
    RGB: linearToSrgb,
  },
  HSL: {
    RGB: hslToRgb,
  },
  HSV: {
    RGB: hsvToRgb,
  },
  Lab: {
    RGB: labToRgb,
    Lch: labToLch,
  },
  Lch: {
    Lab: lchToLab,
  },
  OKLab: {
    RGB: oklabToRgb,
    OKLch: oklabToOklch,
  },
  OKLch: {
    OKLab: oklchToOklab,
  },
};

// Breadth-first search for a conversion path from `from` to `to`.
function findPath(from, to) {
  if (from === to) return [];
  const visited = new Set([from]);
  const queue = [[from, []]];
  while (queue.length) {
    const [node, path] = queue.shift();
    const neighbors = EDGES[node] || {};
    for (const next of Object.keys(neighbors)) {
      if (visited.has(next)) continue;
      const edge = neighbors[next];
      const nextPath = [...path, edge];
      if (next === to) return nextPath;
      visited.add(next);
      queue.push([next, nextPath]);
    }
  }
  throw new Error(`No conversion path from ${from} to ${to}.`);
}

/**
 * Convert a color object between spaces.
 *
 * @param {object} color  Color coordinates in `from` space.
 * @param {string} from   Source space (see SPACES).
 * @param {string} to     Target space (see SPACES).
 * @returns {object}      Color coordinates in `to` space.
 */
export function convert(color, from, to) {
  const src = canonical(from);
  const dst = canonical(to);
  const path = findPath(src, dst);
  return path.reduce((c, fn) => fn(c), color);
}
