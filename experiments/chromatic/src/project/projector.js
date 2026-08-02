// Projects a Palette (defined in OKLab) into a target working colorspace.
//
// Supported targets: "OKLab", "OKLch", "Lab", "Lch", "HSL", "HSV", "RGB".
//
// Each projected PaletteColor keeps its `id`/`role`, carries `coords` in the
// target space, and records gamut-handling metadata so downstream distortion
// analysis and export can consume it uniformly.

import { oklabToRgb, oklabToOklch } from '../colorspace/oklab.js';
import { rgbToLab, labToLch } from '../colorspace/lab.js';
import { rgbToHsl } from '../colorspace/hsl.js';
import { rgbToHsv } from '../colorspace/hsv.js';
import { isInGamut, clipRgb, softCompressOklab, outOfGamutDistance } from './gamut.js';

export const SUPPORTED_SPACES = ['OKLab', 'OKLch', 'Lab', 'Lch', 'HSL', 'HSV', 'RGB'];

// Normalize a space string to its canonical form (case-insensitive).
function canonicalSpace(space) {
  const found = SUPPORTED_SPACES.find((s) => s.toLowerCase() === String(space).toLowerCase());
  if (!found) {
    throw new Error(
      `Unsupported projection space: ${space}. ` + `Expected one of ${SUPPORTED_SPACES.join(', ')}.`
    );
  }
  return found;
}

// Convert one OKLab point into the requested target space, returning both the
// target-space coordinates and the gamut metadata produced along the way.
function projectPoint(oklab, space, { gamut }) {
  // Resolve sRGB / gamut once; every non-OK space routes through sRGB.
  let rgb;
  let clipped = false;
  let gamutDistance = 0;
  let workingOklab = oklab;

  const rawRgb = oklabToRgb(oklab);
  gamutDistance = outOfGamutDistance(rawRgb);
  const inGamut = isInGamut(rawRgb);

  if (space === 'OKLab' || space === 'OKLch') {
    // OK spaces are gamut-independent for their coordinate values; we still
    // report gamut status for diagnostics.
    rgb = inGamut ? rawRgb : clipRgb(rawRgb);
    clipped = !inGamut;
  } else if (inGamut || gamut === 'none') {
    rgb = clipRgb(rawRgb);
    clipped = !inGamut;
  } else if (gamut === 'clip') {
    rgb = clipRgb(rawRgb);
    clipped = true;
  } else {
    // default: "soft" compression in OKLch (chroma reduction)
    const soft = softCompressOklab(oklab);
    workingOklab = soft.oklab;
    rgb = soft.rgb;
    clipped = soft.clipped;
  }

  let coords;
  switch (space) {
    case 'OKLab':
      coords = { ...workingOklab };
      break;
    case 'OKLch':
      coords = oklabToOklch(workingOklab);
      break;
    case 'RGB':
      coords = { ...rgb };
      break;
    case 'Lab':
      coords = rgbToLab(rgb);
      break;
    case 'Lch':
      coords = labToLch(rgbToLab(rgb));
      break;
    case 'HSL':
      coords = rgbToHsl(rgb);
      break;
    case 'HSV':
      coords = rgbToHsv(rgb);
      break;
    default:
      throw new Error(`Unhandled space: ${space}`);
  }

  return { coords, rgb, clipped, gamutDistance };
}

// A projected palette color mirrors the source's identity but lives in a
// target space. It exposes canonical lightness/chroma/hue accessors so that
// ordering/distortion machinery is space-agnostic.
function makeProjectedColor(source, space, projected) {
  const { coords, rgb, clipped, gamutDistance } = projected;
  return {
    id: source.id,
    role: source.role,
    space,
    coords,
    rgb,
    clipped,
    gamutDistance,
    lightness: lightnessOf(space, coords),
    chroma: chromaOf(space, coords),
    hue: hueOf(space, coords),
  };
}

// --- canonical property extractors per space ---

function lightnessOf(space, c) {
  switch (space) {
    case 'OKLab':
    case 'OKLch':
      return c.L;
    case 'Lab':
    case 'Lch':
      return c.L / 100; // normalize CIE L* (0..100) to 0..1
    case 'HSL':
      return c.l;
    case 'HSV':
      return c.v;
    case 'RGB':
      // relative luminance-ish proxy for ordering purposes
      return 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
    default:
      return undefined;
  }
}

function chromaOf(space, c) {
  switch (space) {
    case 'OKLab':
    case 'Lab':
      return Math.hypot(c.a, c.b);
    case 'OKLch':
    case 'Lch':
      return c.C;
    case 'HSL':
      return c.s;
    case 'HSV':
      return c.s;
    case 'RGB': {
      const max = Math.max(c.r, c.g, c.b);
      const min = Math.min(c.r, c.g, c.b);
      return max - min;
    }
    default:
      return undefined;
  }
}

function hueOf(space, c) {
  switch (space) {
    case 'OKLab':
    case 'Lab': {
      let h = (Math.atan2(c.b, c.a) * 180) / Math.PI;
      if (h < 0) h += 360;
      return h;
    }
    case 'OKLch':
    case 'Lch':
      return c.H;
    case 'HSL':
    case 'HSV':
      return c.h;
    case 'RGB':
      return undefined; // RGB has no intrinsic hue coordinate
    default:
      return undefined;
  }
}

// Accepts either a Palette-like object ({ colors: [...] }) or a bare array of
// PaletteColor-like points ({ id, role, L, a, b }).
function extractColors(palette) {
  if (Array.isArray(palette)) return palette;
  if (palette && Array.isArray(palette.colors)) return palette.colors;
  throw new Error('project: expected a Palette or an array of PaletteColors');
}

/**
 * Project a palette from OKLab into a target space.
 *
 * @param {object|Array} palette   Palette or array of OKLab PaletteColors.
 * @param {string} space           Target space (see SUPPORTED_SPACES).
 * @param {object} [options]
 * @param {"soft"|"clip"|"none"} [options.gamut="soft"]  Gamut strategy.
 * @returns {ProjectedPalette}
 */
export function project(palette, space, options = {}) {
  const target = canonicalSpace(space);
  const gamut = options.gamut ?? 'soft';
  const colors = extractColors(palette);

  const projectedColors = colors.map((source) => {
    const oklab = toOklab(source);
    const projected = projectPoint(oklab, target, { gamut });
    return makeProjectedColor(source, target, projected);
  });

  const clippedCount = projectedColors.filter((c) => c.clipped).length;

  return {
    space: target,
    gamut,
    colors: projectedColors,
    // Convenience aggregate for DistortionReport / diagnostics.
    gamutClippedFraction: projectedColors.length === 0 ? 0 : clippedCount / projectedColors.length,
  };
}

// Normalize a source color into a plain OKLab {L,a,b} object.
function toOklab(source) {
  if (source == null) throw new Error('project: null color');
  if ('L' in source && 'a' in source && 'b' in source) {
    return { L: source.L, a: source.a, b: source.b };
  }
  if ('coords' in source && source.space) {
    // Already a projected/typed color; only OKLab is directly convertible here.
    if (source.space === 'OKLab') return { ...source.coords };
  }
  throw new Error(
    'project: color must be an OKLab point ({ L, a, b }); ' +
      'convert other spaces to OKLab before projecting.'
  );
}
