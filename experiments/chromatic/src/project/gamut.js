// sRGB gamut detection, hard clipping, and soft (chroma-reducing) compression.
import { oklabToRgb, oklabToOklch, oklchToOklab } from '../colorspace/oklab.js';

const GAMUT_EPS = 1e-4;

export function isInGamut({ r, g, b }, eps = GAMUT_EPS) {
  return r >= -eps && r <= 1 + eps && g >= -eps && g <= 1 + eps && b >= -eps && b <= 1 + eps;
}

// How far (Euclidean, in linear-ish sRGB channel space) a color sits
// outside the [0,1] cube. Zero when in gamut. Used by the gamut penalty.
export function outOfGamutDistance({ r, g, b }) {
  const excess = (c) => {
    if (c < 0) return -c;
    if (c > 1) return c - 1;
    return 0;
  };
  const dr = excess(r);
  const dg = excess(g);
  const db = excess(b);
  return Math.hypot(dr, dg, db);
}

export function clipRgb({ r, g, b }) {
  return {
    r: Math.min(1, Math.max(0, r)),
    g: Math.min(1, Math.max(0, g)),
    b: Math.min(1, Math.max(0, b)),
  };
}

// Soft compression: hold L and H fixed in OKLch, binary-search the largest
// chroma that keeps the color inside sRGB. Preserves ordering of hue and
// lightness better than naive per-channel clipping.
export function softCompressOklab(oklab, { steps = 20 } = {}) {
  const rgb = oklabToRgb(oklab);
  if (isInGamut(rgb)) {
    return { oklab, rgb, clipped: false, chromaScale: 1 };
  }

  const { L, C, H } = oklabToOklch(oklab);
  if (C < 1e-9) {
    // Achromatic but out of gamut => only L clipping helps; fall back to clip.
    const clipped = clipRgb(rgb);
    return { oklab, rgb: clipped, clipped: true, chromaScale: 1 };
  }

  let lo = 0;
  let hi = 1;
  let best = 0;
  for (let i = 0; i < steps; i++) {
    const mid = (lo + hi) / 2;
    const candidate = oklchToOklab({ L, C: C * mid, H });
    if (isInGamut(oklabToRgb(candidate))) {
      best = mid;
      lo = mid;
    } else {
      hi = mid;
    }
  }

  const resultOklab = oklchToOklab({ L, C: C * best, H });
  const resultRgb = clipRgb(oklabToRgb(resultOklab));
  return {
    oklab: resultOklab,
    rgb: resultRgb,
    clipped: true,
    chromaScale: best,
  };
}
