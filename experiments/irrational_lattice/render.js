// Render a Float32Array scalar field onto a canvas using a colormap.

import { colormaps, colormaps2d } from './colormap.js';

export function renderField(canvas, fieldResult, opts) {
  const { data, min, max, chan2, min2, max2 } = fieldResult;
  const { width, height, cmap } = opts;
  const phase = opts.colorPhase || 0;
  const cmap2d = opts.cmap2d || 'none';
  const colorFn = colormaps[cmap] || colormaps.viridis;

  // Resize canvas to match field resolution for pixel-accurate rendering.
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  const ctx = canvas.getContext('2d');
  const img = ctx.createImageData(width, height);
  const range = max - min || 1;

  const use2d = cmap2d && cmap2d !== 'none' && chan2 && colormaps2d[cmap2d];

  if (use2d) {
    const color2dFn = colormaps2d[cmap2d];
    const range2 = max2 - min2 || 1;
    for (let i = 0; i < data.length; i++) {
      const v = (data[i] - min) / range;
      const u = (chan2[i] - min2) / range2;
      const [r, g, b] = color2dFn(u, v, phase);
      const o = i * 4;
      img.data[o] = r;
      img.data[o + 1] = g;
      img.data[o + 2] = b;
      img.data[o + 3] = 255;
    }
  } else {
    for (let i = 0; i < data.length; i++) {
      const t = (data[i] - min) / range;
      const [r, g, b] = colorFn(t, phase);
      const o = i * 4;
      img.data[o] = r;
      img.data[o + 1] = g;
      img.data[o + 2] = b;
      img.data[o + 3] = 255;
    }
  }

  ctx.putImageData(img, 0, 0);
}
