// Simple perceptual colormaps. Each function takes t in [0,1] and returns
// [r, g, b] in 0..255.

function clamp01(t) {
  return t < 0 ? 0 : t > 1 ? 1 : t;
}

function lerp(a, b, t) {
  return a + (b - a) * t;
}

function sampleStops(stops, t) {
  t = clamp01(t);
  const n = stops.length - 1;
  const idx = t * n;
  const i0 = Math.floor(idx);
  const i1 = Math.min(i0 + 1, n);
  const f = idx - i0;
  const a = stops[i0],
    b = stops[i1];
  return [
    Math.round(lerp(a[0], b[0], f)),
    Math.round(lerp(a[1], b[1], f)),
    Math.round(lerp(a[2], b[2], f)),
  ];
}

const VIRIDIS = [
  [68, 1, 84],
  [72, 40, 120],
  [62, 74, 137],
  [49, 104, 142],
  [38, 130, 142],
  [31, 158, 137],
  [53, 183, 121],
  [109, 205, 89],
  [180, 222, 44],
  [253, 231, 37],
];

const MAGMA = [
  [0, 0, 4],
  [28, 16, 68],
  [79, 18, 123],
  [129, 37, 129],
  [181, 54, 122],
  [229, 80, 100],
  [251, 135, 97],
  [254, 194, 135],
  [252, 253, 191],
];

const TWILIGHT = [
  [226, 217, 226],
  [196, 175, 208],
  [149, 137, 191],
  [97, 104, 165],
  [60, 78, 122],
  [40, 52, 79],
  [60, 78, 122],
  [97, 104, 165],
  [149, 137, 191],
  [196, 175, 208],
  [226, 217, 226],
];
const FIRE = [
  [0, 0, 0],
  [60, 0, 0],
  [120, 10, 0],
  [200, 40, 0],
  [240, 120, 0],
  [255, 200, 40],
  [255, 255, 180],
  [255, 255, 255],
];
const ICE = [
  [0, 0, 20],
  [0, 20, 60],
  [0, 60, 120],
  [20, 120, 200],
  [80, 180, 240],
  [160, 220, 250],
  [220, 245, 255],
  [255, 255, 255],
];
const PLASMA = [
  [13, 8, 135],
  [75, 3, 161],
  [125, 3, 168],
  [168, 34, 150],
  [203, 70, 121],
  [229, 107, 93],
  [248, 148, 65],
  [253, 195, 40],
  [240, 249, 33],
];
// HSV to RGB; h,s,v in [0,1], returns 0..255 triple.
function hsv2rgb(h, s, v) {
  h = (h - Math.floor(h)) * 6;
  const i = Math.floor(h);
  const f = h - i;
  const p = v * (1 - s);
  const q = v * (1 - s * f);
  const t = v * (1 - s * (1 - f));
  let r, g, b;
  switch (i % 6) {
    case 0:
      r = v;
      g = t;
      b = p;
      break;
    case 1:
      r = q;
      g = v;
      b = p;
      break;
    case 2:
      r = p;
      g = v;
      b = t;
      break;
    case 3:
      r = p;
      g = q;
      b = v;
      break;
    case 4:
      r = t;
      g = p;
      b = v;
      break;
    default:
      r = v;
      g = p;
      b = q;
      break;
  }
  return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

export const colormaps = {
  viridis: (t) => sampleStops(VIRIDIS, t),
  magma: (t) => sampleStops(MAGMA, t),
  fire: (t) => sampleStops(FIRE, t),
  ice: (t) => sampleStops(ICE, t),
  plasma: (t) => sampleStops(PLASMA, t),
  twilight: (t) => sampleStops(TWILIGHT, t),
  grayscale: (t) => {
    const v = Math.round(clamp01(t) * 255);
    return [v, v, v];
  },
  // Cycling / disco maps take an optional phase argument.
  rainbow: (t, phase = 0) => hsv2rgb(clamp01(t) + phase, 0.9, 1.0),
  disco: (t, phase = 0) =>
    hsv2rgb(clamp01(t) * 3 + phase, 1.0, 0.5 + 0.5 * Math.abs(Math.sin((t + phase) * Math.PI * 4))),
  neon: (t, phase = 0) => hsv2rgb(clamp01(t) * 0.8 + 0.5 + phase, 1.0, clamp01(0.3 + 0.7 * t)),
};
// 2D colormaps: map (u, v) in [0,1]^2 -> [r,g,b]. phase shifts hue.
export const colormaps2d = {
  // u drives hue, v drives brightness.
  snap: (u, v, phase = 0) => hsv2rgb(u + phase, 0.85, clamp01(0.15 + 0.85 * v)),
  rational_irrational: (u, v, phase = 0) =>
    hsv2rgb(0.6 * u + 0.15 + phase, clamp01(0.4 + 0.6 * v), clamp01(0.25 + 0.75 * v)),
  xy_phase: (u, v, phase = 0) => hsv2rgb(u + phase, 0.7, clamp01(0.2 + 0.8 * v)),
};
