// Flagship v0.1 demonstration (spec §10): the same conceptual palette laid
// out in OKLab and projected to HSL, side by side with a naive HSL-rotated
// equivalent, with concrete DistortionReport metrics.
//
// Imports the library directly from source as native ES modules — no build
// step required. Serve this directory over HTTP so relative imports resolve.

import { Palette } from './src/geometry/point-set.js';
import * as group from './src/geometry/group.js';
import { convert } from './src/colorspace/convert.js';
import { project } from './src/project/projector.js';
import { distortionReport } from './src/solver/distortion.js';

const els = {
  count: document.getElementById('count'),
  light: document.getElementById('light'),
  chroma: document.getElementById('chroma'),
  hue0: document.getElementById('hue0'),
  countOut: document.getElementById('count-out'),
  lightOut: document.getElementById('light-out'),
  chromaOut: document.getElementById('chroma-out'),
  hue0Out: document.getElementById('hue0-out'),
  oklabSwatches: document.getElementById('oklab-swatches'),
  oklabMetrics: document.getElementById('oklab-metrics'),
  hslSwatches: document.getElementById('hsl-swatches'),
  hslMetrics: document.getElementById('hsl-metrics'),
  error: document.getElementById('error'),
};

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

// Build the perceptually-uniform palette: a cyclic orbit in OKLch, evenly
// spaced in OKLch hue, then treated as a Palette for projection.
function buildOklabPalette({ count, L, C, H0 }) {
  const base = { L, C, H: H0 };
  const orbit = group.cyclic({ order: count }).apply(base);
  return Palette.fromPoints(orbit, { space: 'OKLch' });
}

// Build the naive palette: even steps in HSL hue at fixed S/L, converted to
// OKLch so it can share the same Palette/projection machinery.
function buildNaiveHslPalette({ count, L, C }) {
  // Pick an HSL saturation/lightness roughly comparable to the OKLch target
  // so the comparison is about *spacing*, not overall brightness.
  const s = clamp01(C / 0.25);
  const l = clamp01(L);
  const points = [];
  for (let i = 0; i < count; i++) {
    const h = (360 / count) * i;
    const oklch = convert({ h, s, l }, 'HSL', 'OKLch');
    points.push(oklch);
  }
  return Palette.fromPoints(points, { space: 'OKLch' });
}

function renderSwatches(container, projected) {
  container.innerHTML = '';
  for (const c of projected.colors) {
    const rgb = c.rgb ?? convert(c.coords, c.space, 'RGB');
    const div = document.createElement('div');
    div.className = 'swatch';
    div.style.background = toHex(rgb);
    div.title = `${c.id}${c.clipped ? ' (gamut-clipped)' : ''}`;
    div.textContent = c.clipped ? '⚠' : '';
    container.appendChild(div);
  }
}

function metric(key, value, opts = {}) {
  const div = document.createElement('div');
  div.className = 'metric';
  if (opts.good) div.classList.add('good');
  if (opts.bad) div.classList.add('bad');
  div.innerHTML = `<div class="k">${key}</div><div class="v">${value}</div>`;
  return div;
}

function fmt(x, digits = 3) {
  return typeof x === 'number' ? x.toFixed(digits) : String(x);
}

function renderMetrics(container, palette, projected) {
  container.innerHTML = '';
  // Compare the projected (HSL) layout back against the OKLab reference.
  const report = distortionReport(palette, projected, 'HSL');
  const s = report.summary ? report.summary() : report;

  const ordering = s.orderingViolations ?? 0;
  const hueDist = s.avgHueDistortion ?? s.hueDistortion ?? 0;
  const clipped = s.gamutClipped ?? 0;

  container.appendChild(
    metric('Ordering violations', ordering, {
      good: ordering === 0,
      bad: ordering > 0,
    })
  );
  container.appendChild(
    metric('Avg hue distortion', fmt(hueDist), {
      good: hueDist <= 0.08,
      bad: hueDist > 0.15,
    })
  );
  container.appendChild(
    metric('Gamut clipped', clipped, {
      good: clipped === 0,
      bad: clipped > 0,
    })
  );
}

function update() {
  const count = Number(els.count.value);
  const L = Number(els.light.value);
  const C = Number(els.chroma.value);
  const H0 = Number(els.hue0.value);

  els.countOut.textContent = String(count);
  els.lightOut.textContent = L.toFixed(2);
  els.chromaOut.textContent = C.toFixed(2);
  els.hue0Out.textContent = String(H0);

  els.error.hidden = true;
  els.error.textContent = '';

  try {
    const oklab = buildOklabPalette({ count, L, C, H0 });
    const naive = buildNaiveHslPalette({ count, L, C });

    const oklabHsl = project(oklab, 'HSL');
    const naiveHsl = project(naive, 'HSL');

    renderSwatches(els.oklabSwatches, oklabHsl);
    renderSwatches(els.hslSwatches, naiveHsl);

    renderMetrics(els.oklabMetrics, oklab, oklabHsl);
    renderMetrics(els.hslMetrics, naive, naiveHsl);
  } catch (err) {
    els.error.hidden = false;
    els.error.textContent = `Demo error: ${err && err.stack ? err.stack : err}`;
    // eslint-disable-next-line no-console
    console.error(err);
  }
}

for (const id of ['count', 'light', 'chroma', 'hue0']) {
  els[id].addEventListener('input', update);
}

update();
