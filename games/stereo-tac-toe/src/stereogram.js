// Single Image Random Dot Stereogram renderer.
// Uses a center-out constraint-propagation pass so a localized depth
// change only ripples toward the nearest edge (smaller delta volume,
// steadier fused image).

import { CONFIG, effectivePatternWidth } from './config.js';

// Small deterministic PRNG (mulberry32) so a seed reproduces a ribbon.
function makeRng(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// A cheap value-noise / plasma field, seeded & deterministic.
function hash2(ix, iy, seed) {
  let h = (ix * 374761393 + iy * 668265263 + seed * 2246822519) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

function smooth(t) {
  return t * t * (3 - 2 * t);
}

function valueNoise(x, y, scale, seed) {
  const gx = x / scale;
  const gy = y / scale;
  const x0 = Math.floor(gx);
  const y0 = Math.floor(gy);
  const fx = smooth(gx - x0);
  const fy = smooth(gy - y0);
  const v00 = hash2(x0, y0, seed);
  const v10 = hash2(x0 + 1, y0, seed);
  const v01 = hash2(x0, y0 + 1, seed);
  const v11 = hash2(x0 + 1, y0 + 1, seed);
  const a = v00 + (v10 - v00) * fx;
  const b = v01 + (v11 - v01) * fx;
  return a + (b - a) * fy;
}

// Precompute a stable random color pattern (noise ribbon) so the
// background stays steady frame-to-frame; only depth shifts ripple.
// Honors CONFIG noise + texture parameters.
function buildPattern(patternWidth, height) {
  const pattern = new Uint8Array(patternWidth * height * 3);
  const seed = CONFIG.NOISE_SEED || 1;
  const rng = makeRng(seed);
  const contrast = CONFIG.NOISE_CONTRAST;
  const mode = CONFIG.NOISE_MODE;
  const density = CONFIG.NOISE_DENSITY;

  const texture = CONFIG.NOISE_TEXTURE || 'none';
  const texScale = Math.max(1, CONFIG.NOISE_TEXTURE_SCALE || 16);
  const texStrength = Math.max(0, Math.min(1, CONFIG.NOISE_TEXTURE_STRENGTH ?? 0));

  // Scale a raw 0..1 value around mid-gray by contrast.
  const scale = (v) => {
    const c = 0.5 + (v - 0.5) * contrast;
    return Math.max(0, Math.min(255, (c * 256) | 0));
  };

  // Texture field at (x, y) -> 0..1, or null when texture is 'none'.
  const texAt = (x, y) => {
    switch (texture) {
      case 'stripes':
        return 0.5 + 0.5 * Math.sin((x / texScale) * Math.PI * 2);
      case 'checker': {
        const cx = Math.floor(x / texScale);
        const cy = Math.floor(y / texScale);
        return (cx + cy) & 1 ? 1 : 0;
      }
      case 'dots': {
        const cx = (x % texScale) - texScale / 2;
        const cy = (y % texScale) - texScale / 2;
        const r = Math.sqrt(cx * cx + cy * cy) / (texScale / 2);
        return r < 0.5 ? 1 : 0;
      }
      case 'plasma': {
        const n =
          0.5 * valueNoise(x, y, texScale, seed) + 0.5 * valueNoise(x, y, texScale * 2, seed + 101);
        return Math.max(0, Math.min(1, n));
      }
      default:
        return null;
    }
  };

  // Blend a base 0..1 sample with the texture field.
  const blend = (base, x, y) => {
    const t = texAt(x, y);
    if (t === null) return base;
    return base * (1 - texStrength) + t * texStrength;
  };

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < patternWidth; x++) {
      const i = (y * patternWidth + x) * 3;
      if (mode === 'bw') {
        let v = rng() < density ? 1 : 0;
        v = blend(v, x, y);
        const on = v >= 0.5 ? 255 : 0;
        pattern[i] = on;
        pattern[i + 1] = on;
        pattern[i + 2] = on;
      } else if (mode === 'grayscale') {
        const g = scale(blend(rng(), x, y));
        pattern[i] = g;
        pattern[i + 1] = g;
        pattern[i + 2] = g;
      } else {
        // rgb
        pattern[i] = scale(blend(rng(), x, y));
        pattern[i + 1] = scale(blend(rng(), x, y));
        pattern[i + 2] = scale(blend(rng(), x, y));
      }
    }
  }
  return pattern;
}

export class Stereogram {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.patternWidth = effectivePatternWidth();
    this.pattern = buildPattern(this.patternWidth, height);
    this.imageData = new ImageData(width, height);
    // Scratch buffers reused each frame.
    this.same = new Int32Array(width);
  }

  // Resize the render target (changes resolution, reallocates buffers).
  resize(width, height) {
    this.width = width;
    this.height = height;
    this.imageData = new ImageData(width, height);
    this.same = new Int32Array(width);
    this.rebuildPattern();
  }

  // Rebuild the noise ribbon (call when noise or pattern params change).
  rebuildPattern() {
    this.patternWidth = effectivePatternWidth();
    this.pattern = buildPattern(this.patternWidth, this.height);
  }

  // Separation (in px) between the two eye-images for a given depth z (0..1).
  _separation(z) {
    const E = CONFIG.EYE_SEPARATION_PX;
    const mu = CONFIG.MU;
    return Math.round(((1 - mu * z) * E) / (2 - mu * z));
  }

  // Link two pixels (union) so they share a color anchor.
  _link(same, a, b) {
    if (a === b) return;
    same[a] = b;
  }

  renderStereogram(ctx, depthBuffer) {
    const { width, height, same, pattern } = this;
    const patternWidth = this.patternWidth;
    const data = this.imageData.data;
    const center = width >> 1;

    for (let y = 0; y < height; y++) {
      const rowOff = y * width;

      // Each pixel initially links to itself.
      for (let x = 0; x < width; x++) same[x] = x;

      // --- Center-out constraint propagation ---
      // Walk outward from the center column in both directions so a
      // localized depth edit only ripples toward the nearer screen edge.
      for (let d = 0; d <= center; d++) {
        // Rightward half.
        const xr = center + d;
        if (xr < width) {
          const z = depthBuffer[rowOff + xr];
          const sep = this._separation(z);
          const left = xr - (sep >> 1);
          const right = left + sep;
          if (left >= 0 && right < width) {
            // Anchor to the pixel nearer the center to keep the seam
            // stable around the middle of the image.
            this._link(same, right, left);
          }
        }
        // Leftward half.
        const xl = center - d;
        if (d !== 0 && xl >= 0) {
          const z = depthBuffer[rowOff + xl];
          const sep = this._separation(z);
          const left = xl - (sep >> 1);
          const right = left + sep;
          if (left >= 0 && right < width) {
            this._link(same, left, right);
          }
        }
      }

      // Resolve link chains to a representative anchor per pixel.
      const resolve = (x) => {
        let r = x;
        while (same[r] !== r) r = same[r];
        // Path compression.
        let c = x;
        while (same[c] !== r) {
          const next = same[c];
          same[c] = r;
          c = next;
        }
        return r;
      };

      // Assign colors. Anchors sample the stable pattern; linked pixels
      // copy their anchor's color.
      for (let x = 0; x < width; x++) {
        const anchor = resolve(x);
        const idx = (rowOff + x) * 4;
        if (anchor === x) {
          const p = ((x % patternWidth) + y * patternWidth) * 3;
          data[idx] = pattern[p];
          data[idx + 1] = pattern[p + 1];
          data[idx + 2] = pattern[p + 2];
          data[idx + 3] = 255;
        } else {
          const src = (rowOff + anchor) * 4;
          data[idx] = data[src];
          data[idx + 1] = data[src + 1];
          data[idx + 2] = data[src + 2];
          data[idx + 3] = 255;
        }
      }
    }

    ctx.putImageData(this.imageData, 0, 0);
  }
}
