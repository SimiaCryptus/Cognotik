// Entry point: wires UI controls to the field generator and renderer.

import { computeField } from './field.js';
import { renderField } from './render.js';
import { computeFFT2D, renderFFT3D } from './fft.js';
import { topAutocorrVectors } from './autocorr.js';
import { wireRationalControls } from './rational.js';
import { FieldAudio } from './audio.js';

const canvas = document.getElementById('field');
const statsEl = document.getElementById('stats');
const mainEl = document.querySelector('main');
// Render dimensions in pixels. The grid-size control sets the smaller
// dimension; the larger one is derived from the canvas display aspect so
// the field fills all available space.
const renderDims = { width: 256, height: 256 };

const controls = {
  D: document.getElementById('D'),
  mode: document.getElementById('mode'),
  K: document.getElementById('K'),
  alpha: document.getElementById('alpha'),
  eps: document.getElementById('eps'),
  size: document.getElementById('size'),
  cmap: document.getElementById('cmap'),
  cmap2d: document.getElementById('cmap2d'),
  cycle: document.getElementById('cycle'),
  seed: document.getElementById('seed'),
  zoomStep: document.getElementById('zoomStep'),
  upsample: document.getElementById('upsample'),
};

const outputs = {
  K: document.getElementById('kOut'),
  alpha: document.getElementById('alphaOut'),
  eps: document.getElementById('epsOut'),
  size: document.getElementById('sizeOut'),
  seed: document.getElementById('seedOut'),
  cycle: document.getElementById('cycleOut'),
  offset: document.getElementById('offsetOut'),
  zoomStep: document.getElementById('zoomStepOut'),
};
// Viewport state for pan & zoom (rational lattice coordinates).
const view = {
  panX: 0,
  panY: 0,
  zoom: 1, // lattice units per pixel
};
// Reference resolution that defines the viewport extent. The zoom is
// expressed in lattice-units-per-reference-pixel so that changing the grid
// size only alters resolution, never the visible lattice region. The
// effective per-field-pixel zoom is derived from the current grid size.
const REF_SIZE = 512;
function effectiveZoom(size) {
  return (view.zoom * REF_SIZE) / size;
}
// The active zoom granularity is a rational p/q controlled by the
// numerator/denominator steppers (the slider only seeds an approximation).
const zoomNumEl = document.getElementById('zoomNum');
const zoomDenEl = document.getElementById('zoomDen');
function zoomGranularity() {
  const num = parseFloat(zoomNumEl.value) || 1;
  const den = parseFloat(zoomDenEl.value) || 1;
  const v = num / den;
  // Accept any finite ratio strictly greater than 1. Do not pin to a
  // fixed fallback range; the user may enter arbitrary num/den.
  return isFinite(v) && v > 1 ? v : 1.0001;
}

// Integer offset state (paged through with buttons).
const offset = { x: 0, y: 0 };

// Color cycling phase, advanced by an animation loop when cycle > 0.
let colorPhase = 0;
// --- URL hash persistence (for sharable links) ---
// Serialize all control + view state into the location hash so the exact
// view can be restored or shared by copying the URL.
let restoringFromHash = false;
function stateToHash() {
  if (restoringFromHash) return;
  const params = new URLSearchParams();
  for (const key of Object.keys(controls)) {
    params.set(key, controls[key].value);
  }
  params.set('cycleSpeed', controls.cycle.value);
  params.set('panX', view.panX.toFixed(3));
  params.set('panY', view.panY.toFixed(3));
  params.set('zoom', view.zoom.toFixed(5));
  params.set('offsetX', offset.x);
  params.set('offsetY', offset.y);
  // Replace (not push) so we don't spam browser history during drags.
  history.replaceState(null, '', '#' + params.toString());
}
function hashToState() {
  const hash = window.location.hash.replace(/^#/, '');
  if (!hash) return false;
  const params = new URLSearchParams(hash);
  restoringFromHash = true;
  for (const key of Object.keys(controls)) {
    if (params.has(key)) controls[key].value = params.get(key);
  }
  if (params.has('panX')) view.panX = parseFloat(params.get('panX'));
  if (params.has('panY')) view.panY = parseFloat(params.get('panY'));
  if (params.has('zoom')) view.zoom = parseFloat(params.get('zoom'));
  if (params.has('offsetX')) offset.x = parseInt(params.get('offsetX'), 10);
  if (params.has('offsetY')) offset.y = parseInt(params.get('offsetY'), 10);
  restoringFromHash = false;
  return true;
}

function readOpts() {
  const size = parseInt(controls.size.value, 10);
  const upsample = parseInt(controls.upsample.value, 10) || 1;
  return {
    D: parseInt(controls.D.value, 10),
    mode: controls.mode.value,
    K: parseInt(controls.K.value, 10),
    alphaScale: parseFloat(controls.alpha.value),
    epsilon: parseFloat(controls.eps.value),
    size,
    width: renderDims.width,
    height: renderDims.height,
    cmap: controls.cmap.value,
    cmap2d: controls.cmap2d.value,
    seed: parseInt(controls.seed.value, 10),
    panX: view.panX,
    panY: view.panY,
    zoom: effectiveZoom(Math.min(renderDims.width, renderDims.height)),
    upsample,
    offsetX: offset.x,
    offsetY: offset.y,
    colorPhase,
  };
}

function updateOutputs(o) {
  outputs.K.textContent = o.K;
  outputs.alpha.textContent = o.alphaScale.toFixed(3);
  outputs.eps.textContent = o.epsilon.toFixed(2);
  outputs.size.textContent = o.size;
  outputs.seed.textContent = o.seed;
  outputs.cycle.textContent = parseFloat(controls.cycle.value).toFixed(2);
  outputs.offset.textContent = `${offset.x}, ${offset.y}`;
  const upsampleOut = document.getElementById('upsampleOut');
  if (upsampleOut) upsampleOut.textContent = o.upsample;
}

function updateStats(o, result, elapsed) {
  statsEl.innerHTML = `
        <div><span>D</span><span>${o.D}</span></div>
        <div><span>&radic;D</span><span>${Math.sqrt(o.D).toFixed(6)}</span></div>
        <div><span>min</span><span>${result.min.toFixed(4)}</span></div>
        <div><span>max</span><span>${result.max.toFixed(4)}</span></div>
        <div><span>irr RMS</span><span>${result.irrRMS.toFixed(4)}</span></div>
        <div><span>sites</span><span>${o.width * o.height}</span></div>
       <div><span>zoom</span><span>${o.zoom.toFixed(3)}</span></div>
       <div><span>pan</span><span>${o.panX.toFixed(1)}, ${o.panY.toFixed(1)}</span></div>
       <div><span>offset</span><span>${offset.x}, ${offset.y}</span></div>
        <div><span>compute</span><span>${elapsed.toFixed(1)} ms</span></div>
      `;
}

// Cache the last computed result so color cycling can re-render cheaply
// without recomputing the (expensive) field.
let lastResult = null;
let lastOpts = null;

let pending = null;
function regenerate() {
  const opts = readOpts();
  updateOutputs(opts);
  // Persist the current state to the URL hash for sharing.
  stateToHash();

  // Defer to next frame to keep slider drag responsive.
  if (pending) cancelAnimationFrame(pending);
  pending = requestAnimationFrame(() => {
    const t0 = performance.now();
    const result = computeField(opts);
    renderField(canvas, result, opts);
    const t1 = performance.now();
    updateStats(opts, result, t1 - t0);
    lastResult = result;
    lastOpts = opts;
    pending = null;
    updateFFT();
    drawAcVectors();
    // Keep the audio engine's field in sync with the latest computation.
    if (fieldAudio.playing) fieldAudio.updateField(lastResult, lastOpts);
  });
}

// Re-render only (no recompute) using the cached field. Used for color
// cycling, which only changes the colormap phase.
function rerenderColor() {
  if (!lastResult) return;
  lastOpts = {
    ...lastOpts,
    colorPhase,
    cmap: controls.cmap.value,
    cmap2d: controls.cmap2d.value,
  };
  renderField(canvas, lastResult, lastOpts);
}

// Wire up listeners.
for (const key of Object.keys(controls)) {
  // `size` and `zoomStep` are managed by the rational view controls, which
  // update the hidden inputs and emit their own change events.
  if (key === 'size' || key === 'zoomStep') continue;
  controls[key].addEventListener('input', regenerate);
  controls[key].addEventListener('change', regenerate);
}
document.getElementById('regen').addEventListener('click', regenerate);
// Wire the rational view controls (integer grid size + rational zoom
// granularity p/q seeded from the slider via continued fractions).
const rationalControls = wireRationalControls({
  maxDen: 100,
  onChange: () => {
    // Keep the legacy zoomStep output (if present) in sync, then redraw.
    if (outputs.zoomStep) {
      outputs.zoomStep.textContent = zoomGranularity().toFixed(3);
    }
    // Grid size may have changed; recompute render dimensions first.
    fitCanvas();
    regenerate();
  },
});
document.getElementById('resetView').addEventListener('click', () => {
  view.panX = 0;
  view.panY = 0;
  view.zoom = 1;
  regenerate();
});

// --- Export to PNG ---
document.getElementById('exportPng').addEventListener('click', () => {
  const url = canvas.toDataURL('image/png');
  const a = document.createElement('a');
  const o = lastOpts || readOpts();
  a.href = url;
  a.download = `irrational_lattice_D${o.D}_K${o.K}_seed${o.seed}.png`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
});

// --- Integer offset paging ---
function bumpOffset(dx, dy) {
  offset.x += dx;
  offset.y += dy;
  regenerate();
}
document.getElementById('offsetXMinus').addEventListener('click', () => bumpOffset(-1, 0));
document.getElementById('offsetXPlus').addEventListener('click', () => bumpOffset(1, 0));
document.getElementById('offsetYMinus').addEventListener('click', () => bumpOffset(0, -1));
document.getElementById('offsetYPlus').addEventListener('click', () => bumpOffset(0, 1));

// --- Parameter sweep buttons ---
// Each sweep slowly animates a range slider back and forth.
const sweeping = {}; // target -> { dir }
function stepSweeps() {
  let any = false;
  for (const target of Object.keys(sweeping)) {
    if (!sweeping[target]) continue;
    any = true;
    // Zoom sweep is special: zoom lives in `view`, not in a range control.
    // Use the zoom granularity slider as a per-frame rate multiplier so the
    // sweep speed respects the same control as manual wheel zooming.
    if (target === 'zoom') {
      const zoomStep = zoomGranularity();
      // Per-frame multiplier derived from the granularity (gentler than a
      // full wheel notch so the animation stays smooth).
      const rate = 1 + (zoomStep - 1) * 0.25;
      const zoomMin = 0.001;
      const zoomMax = 1000;
      // dir === 1 => zoom in (decrease lattice-units-per-pixel),
      // dir === -1 => zoom out. Unlike other sweeps, zoom does not bounce;
      // it holds the chosen direction and just clamps at the limits.
      view.zoom *= sweeping[target].dir > 0 ? 1 / rate : rate;
      if (view.zoom <= zoomMin) view.zoom = zoomMin;
      if (view.zoom >= zoomMax) view.zoom = zoomMax;
      continue;
    }
    const input = controls[target];
    const min = parseFloat(input.min);
    const max = parseFloat(input.max);
    const step = parseFloat(input.step) || (max - min) / 200;
    let val = parseFloat(input.value);
    const dir = sweeping[target].dir;
    // Advance by ~ (range / 200) per frame for a smooth sweep.
    const delta = Math.max(step, (max - min) / 400) * dir;
    val += delta;
    if (val >= max) {
      val = max;
      sweeping[target].dir = -1;
    }
    if (val <= min) {
      val = min;
      sweeping[target].dir = 1;
    }
    input.value = val;
  }
  if (any) regenerate();
}
document.querySelectorAll('.sweep-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    const target = btn.dataset.target;
    // Zoom has two buttons (in/out) sharing the "zoom" target. Each button
    // selects a fixed direction; clicking the active one stops the sweep.
    if (target === 'zoom') {
      const dir = btn.dataset.dir === 'out' ? -1 : 1;
      const alreadyActive = sweeping[target] && sweeping[target].dir === dir;
      // Clear any active zoom button state first.
      document
        .querySelectorAll('.sweep-btn[data-target="zoom"]')
        .forEach((b) => b.classList.remove('active'));
      if (alreadyActive) {
        delete sweeping[target];
      } else {
        sweeping[target] = { dir };
        btn.classList.add('active');
      }
      return;
    }
    if (sweeping[target]) {
      delete sweeping[target];
      btn.classList.remove('active');
    } else {
      sweeping[target] = { dir: 1 };
      btn.classList.add('active');
    }
  });
});

// --- Animation loop: color cycling + parameter sweeps ---
let lastT = performance.now();
function animate(now) {
  const dt = (now - lastT) / 1000;
  lastT = now;

  const cycleSpeed = parseFloat(controls.cycle.value);
  const anySweep = Object.values(sweeping).some(Boolean);

  if (anySweep) {
    // Sweeps recompute the field; color phase advances along with it.
    if (cycleSpeed > 0) colorPhase = (colorPhase + cycleSpeed * dt) % 1;
    stepSweeps();
  } else if (cycleSpeed > 0) {
    // Only the colormap phase changes: cheap re-render without recompute.
    colorPhase = (colorPhase + cycleSpeed * dt) % 1;
    rerenderColor();
  }
  // Autocorrelation random walk playback (independent of color cycling).
  if (acPlaying) {
    const stepsPerSec = parseFloat(acSpeed.value) || 1;
    acAccum += dt;
    const interval = 1 / stepsPerSec;
    // Take at most one step per frame to keep things responsive.
    if (acAccum >= interval) {
      acAccum -= interval;
      acWalkStep();
    }
  }
  requestAnimationFrame(animate);
}
requestAnimationFrame(animate);

// --- Fit canvas display size to available space ---
function fitCanvas() {
  const wrap = canvas.parentElement;
  // Available width inside the wrap (minus its padding).
  const style = getComputedStyle(wrap);
  const padX = parseFloat(style.paddingLeft) + parseFloat(style.paddingRight);
  const avail = wrap.clientWidth - padX;
  // Fill the available rectangle. Cap the height by the viewport.
  const padY = parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
  const dispW = Math.max(64, avail);
  const dispH = Math.max(64, window.innerHeight * 0.9 - padY);
  canvas.style.width = dispW + 'px';
  canvas.style.height = dispH + 'px';

  // The grid-size control sets the smaller render dimension; the larger
  // dimension is scaled by the display aspect ratio so pixels stay square.
  const gridSize = parseInt(controls.size.value, 10) || 256;
  const aspect = dispW / dispH;
  if (aspect >= 1) {
    renderDims.height = gridSize;
    renderDims.width = Math.max(1, Math.round(gridSize * aspect));
  } else {
    renderDims.width = gridSize;
    renderDims.height = Math.max(1, Math.round(gridSize / aspect));
  }
}
window.addEventListener('resize', () => {
  fitCanvas();
  regenerate();
});

// --- Pan & zoom interaction on the canvas ---
// The canvas is displayed scaled (CSS) relative to its pixel resolution
// (size x size). Convert client pixels to field pixels accordingly.
function clientToFieldPixel(ev) {
  const rect = canvas.getBoundingClientRect();
  const fx = ((ev.clientX - rect.left) / rect.width) * canvas.width;
  const fy = ((ev.clientY - rect.top) / rect.height) * canvas.height;
  return { fx, fy };
}
// Convert a field pixel coordinate to lattice coordinates given the view.
function pixelToLattice(fx, fy) {
  const w = canvas.width;
  const h = canvas.height;
  const z = effectiveZoom(Math.min(w, h));
  return {
    x: (fx - w / 2) * z + view.panX,
    y: (fy - h / 2) * z + view.panY,
  };
}
let dragging = false;
let dragStart = null;
let panStart = null;
canvas.addEventListener('mousedown', (ev) => {
  // First check whether we're grabbing an autocorrelation vector endpoint.
  const fp = clientToFieldPixel(ev);
  const hit = acHitTest(fp.fx, fp.fy);
  if (hit) {
    acDragging = hit;
    canvas.style.cursor = 'crosshair';
    ev.preventDefault();
    return;
  }
  dragging = true;
  dragStart = clientToFieldPixel(ev);
  panStart = { x: view.panX, y: view.panY };
  canvas.style.cursor = 'grabbing';
});
window.addEventListener('mousemove', (ev) => {
  // Editing an autocorrelation vector endpoint.
  if (acDragging) {
    const { fx, fy } = clientToFieldPixel(ev);
    const cx = canvas.width / 2;
    const cy = canvas.height / 2;
    const z = effectiveZoom(Math.min(canvas.width, canvas.height));
    const v = acVectors[acDragging.index];
    // Endpoint position in pixels relative to center, undoing the sign so
    // the stored vector always represents the +v direction.
    const dxPx = (fx - cx) * acDragging.sign;
    const dyPx = (fy - cy) * acDragging.sign;
    v.dx = dxPx * z;
    v.dy = dyPx * z;
    // Update the readout and redraw.
    acOut.textContent = acVectors
      .map((vv) => `(${vv.dx.toFixed(2)}, ${vv.dy.toFixed(2)})`)
      .join('  ');
    regenerate();
    return;
  }
  if (!dragging) return;
  const cur = clientToFieldPixel(ev);
  // Drag moves the view: dragging right pulls content right => pan left.
  // Round the pixel delta so panning snaps to whole field-pixel steps.
  // This keeps the lattice sampling aligned to the pixel grid and avoids
  // sub-pixel resampling jitter while dragging.
  const dxPx = Math.round(cur.fx - dragStart.fx);
  const dyPx = Math.round(cur.fy - dragStart.fy);
  const z = effectiveZoom(Math.min(canvas.width, canvas.height));
  view.panX = panStart.x - dxPx * z;
  view.panY = panStart.y - dyPx * z;
  regenerate();
});
// Cursor feedback: show a pointer over draggable endpoint handles.
canvas.addEventListener('mousemove', (ev) => {
  if (dragging || acDragging) return;
  const { fx, fy } = clientToFieldPixel(ev);
  canvas.style.cursor = acHitTest(fx, fy) ? 'pointer' : 'grab';
});
window.addEventListener('mouseup', () => {
  if (acDragging) {
    acDragging = null;
    canvas.style.cursor = 'grab';
    return;
  }
  if (!dragging) return;
  dragging = false;
  canvas.style.cursor = 'grab';
});
// Wheel to zoom, keeping the lattice point under the cursor fixed.
canvas.addEventListener(
  'wheel',
  (ev) => {
    ev.preventDefault();
    const { fx, fy } = clientToFieldPixel(ev);
    const before = pixelToLattice(fx, fy);
    // Configurable zoom granularity: each wheel notch multiplies/divides
    // the zoom by zoomStep. Values close to 1.0 give finer control.
    const zoomStep = zoomGranularity();
    const factor = ev.deltaY < 0 ? 1 / zoomStep : zoomStep;
    view.zoom *= factor;
    // Clamp zoom to a reasonable range.
    view.zoom = Math.min(Math.max(view.zoom, 0.001), 1000);
    // Recompute pan so the cursor stays over the same lattice point.
    const w = canvas.width;
    const h = canvas.height;
    const z = effectiveZoom(Math.min(w, h));
    view.panX = before.x - (fx - w / 2) * z;
    view.panY = before.y - (fy - h / 2) * z;
    regenerate();
  },
  { passive: false }
);
canvas.style.cursor = 'grab';
// --- 3D FFT floating subwindow ---
const fftWindow = document.getElementById('fftWindow');
const fftTitlebar = document.getElementById('fftTitlebar');
const fftBody = document.getElementById('fftBody');
const fftCanvas = document.getElementById('fftCanvas');
const fftCollapse = document.getElementById('fftCollapse');
const fftRefresh = document.getElementById('fftRefresh');
const fftRot = document.getElementById('fftRot');
const fftTilt = document.getElementById('fftTilt');
const fftScale = document.getElementById('fftScale');
let lastFFT = null;
function updateFFT() {
  if (fftWindow.classList.contains('collapsed')) return;
  if (!lastResult) return;
  lastFFT = computeFFT2D(lastResult.data, lastOpts.width, 64, lastOpts.height);
  drawFFT();
}
function drawFFT() {
  if (!lastFFT) return;
  renderFFT3D(fftCanvas, lastFFT, {
    rot: parseFloat(fftRot.value),
    tilt: parseFloat(fftTilt.value),
    heightScale: parseFloat(fftScale.value),
  });
}
for (const el of [fftRot, fftTilt, fftScale]) {
  el.addEventListener('input', drawFFT);
}
fftRefresh.addEventListener('click', updateFFT);
fftCollapse.addEventListener('click', () => {
  fftWindow.classList.toggle('collapsed');
  fftCollapse.textContent = fftWindow.classList.contains('collapsed') ? '▸' : '▾';
  updateFFT();
});
// Dragging the subwindow by its titlebar.
let fwDragging = false;
let fwStart = null;
let fwOrigin = null;
fftTitlebar.addEventListener('mousedown', (ev) => {
  // Ignore drags that start on a button.
  if (ev.target.closest('button')) return;
  fwDragging = true;
  const rect = fftWindow.getBoundingClientRect();
  fwStart = { x: ev.clientX, y: ev.clientY };
  fwOrigin = { x: rect.left, y: rect.top };
  // Switch from right-anchored to left/top positioning.
  fftWindow.style.left = rect.left + 'px';
  fftWindow.style.top = rect.top + 'px';
  fftWindow.style.right = 'auto';
  ev.preventDefault();
});
window.addEventListener('mousemove', (ev) => {
  if (!fwDragging) return;
  const dx = ev.clientX - fwStart.x;
  const dy = ev.clientY - fwStart.y;
  fftWindow.style.left = Math.max(0, fwOrigin.x + dx) + 'px';
  fftWindow.style.top = Math.max(0, fwOrigin.y + dy) + 'px';
});
window.addEventListener('mouseup', () => {
  fwDragging = false;
});
// --- Autocorrelation-driven random walk ---
// Compute the two strongest autocorrelation displacement vectors, then
// step the pan by a randomly-signed combination of them. This nudges the
// viewport toward self-similar features in the field.
const acCompute = document.getElementById('acCompute');
const acStep = document.getElementById('acStep');
const acPlay = document.getElementById('acPlay');
const acSpeed = document.getElementById('acSpeed');
const acSpeedOut = document.getElementById('acSpeedOut');
const acOut = document.getElementById('acOut');
const acShow = document.getElementById('acShow');
let acVectors = null;
let acPlaying = false;
let acAccum = 0; // seconds accumulated toward the next step
let acShowVectors = false;
// Endpoint editing state: which vector endpoint (if any) is being dragged.
// { index, sign } identifies the +v (sign=1) or -v (sign=-1) endpoint of
// acVectors[index].
let acDragging = null;
// Pixel radius within which a click grabs an endpoint handle.
const AC_HANDLE_RADIUS = 10;
// Return the field-pixel position of a vector endpoint given its view.
function acEndpointPixel(v, sign) {
  const cx = canvas.width / 2;
  const cy = canvas.height / 2;
  const z = effectiveZoom(Math.min(canvas.width, canvas.height));
  return {
    x: cx + sign * (v.dx / z),
    y: cy + sign * (v.dy / z),
  };
}
// Hit-test: find the endpoint handle under a field-pixel coordinate.
function acHitTest(fx, fy) {
  if (!acShowVectors || !acVectors) return null;
  for (let i = 0; i < acVectors.length; i++) {
    for (const sign of [1, -1]) {
      const p = acEndpointPixel(acVectors[i], sign);
      if (Math.hypot(fx - p.x, fy - p.y) <= AC_HANDLE_RADIUS) {
        return { index: i, sign };
      }
    }
  }
  return null;
}
// Draw the autocorrelation vectors as overlay arrows from the canvas center.
function drawAcVectors() {
  if (!acShowVectors || !acVectors || acVectors.length === 0) return;
  const ctx = canvas.getContext('2d');
  const w = canvas.width;
  const h = canvas.height;
  const size = Math.min(w, h);
  const cx = w / 2;
  const cy = h / 2;
  // Vectors are in lattice units; convert to field pixels via the zoom.
  const colors = ['#ff3b6b', '#3bff9d', '#3b9dff', '#ffd23b'];
  ctx.save();
  ctx.lineWidth = Math.max(1, size / 256);
  const z = effectiveZoom(size);
  acVectors.forEach((v, i) => {
    const px = v.dx / z;
    const py = v.dy / z;
    const color = colors[i % colors.length];
    // Draw both +v and -v directions.
    for (const s of [1, -1]) {
      const ex = cx + s * px;
      const ey = cy + s * py;
      ctx.strokeStyle = color;
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(ex, ey);
      ctx.stroke();
      // Arrowhead.
      const ang = Math.atan2(ey - cy, ex - cx);
      const head = Math.max(5, size / 80);
      ctx.beginPath();
      ctx.moveTo(ex, ey);
      ctx.lineTo(ex - head * Math.cos(ang - Math.PI / 6), ey - head * Math.sin(ang - Math.PI / 6));
      ctx.lineTo(ex - head * Math.cos(ang + Math.PI / 6), ey - head * Math.sin(ang + Math.PI / 6));
      ctx.closePath();
      ctx.fill();
      // Draggable endpoint handle (hollow circle).
      const handleR = Math.max(4, size / 160);
      ctx.beginPath();
      ctx.arc(ex, ey, handleR, 0, Math.PI * 2);
      ctx.fillStyle = '#0e0f13';
      ctx.fill();
      ctx.lineWidth = Math.max(1, size / 320);
      ctx.strokeStyle = color;
      ctx.stroke();
      ctx.lineWidth = Math.max(1, size / 256);
    }
  });
  // Center marker.
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.arc(cx, cy, Math.max(2, size / 256) * 1.5, 0, Math.PI * 2);
  ctx.fill();
  ctx.restore();
}
function computeAutocorrVectors() {
  console.group('[main] computeAutocorrVectors');
  if (!lastResult || !lastOpts) {
    console.log('[main] no cached result — computing field synchronously');
    // Compute the field synchronously so analysis has data to work with.
    // (regenerate() defers via requestAnimationFrame, so lastResult would
    // not be populated by the time we read it here.)
    const opts = readOpts();
    updateOutputs(opts);
    const result = computeField(opts);
    renderField(canvas, result, opts);
    lastResult = result;
    lastOpts = opts;
    if (!lastResult || !lastOpts) {
      console.warn('[main] field computation failed; aborting analysis');
      console.groupEnd();
      return;
    }
  }
  const size = Math.min(lastOpts.width, lastOpts.height);
  const z = effectiveZoom(size);
  console.log('[main] analysis params:', {
    width: lastOpts.width,
    height: lastOpts.height,
    size,
    effectiveZoom: z,
    dataLength: lastResult.data ? lastResult.data.length : null,
    fieldMin: lastResult.min,
    fieldMax: lastResult.max,
    mode: lastOpts.mode,
  });
  acVectors = topAutocorrVectors(lastResult.data, lastOpts.width, z, 64, lastOpts.height);
  if (!acVectors || acVectors.length === 0) {
    console.warn('[main] topAutocorrVectors returned no vectors');
    acOut.textContent = 'no vectors';
    acVectors = null;
    console.groupEnd();
    return;
  }
  acOut.textContent = acVectors.map((v) => `(${v.dx.toFixed(2)}, ${v.dy.toFixed(2)})`).join('  ');
  console.log('[main] acVectors set:', acVectors);
  drawAcVectors();
  console.groupEnd();
}
function acWalkStep() {
  if (!acVectors || acVectors.length === 0) {
    computeAutocorrVectors();
    if (!acVectors) return;
  }
  // Random signed combination of the available vectors.
  for (const v of acVectors) {
    const sign = Math.random() < 0.5 ? -1 : 1;
    // Only take a vector ~half the time so steps vary in length.
    if (Math.random() < 0.5) {
      view.panX += sign * v.dx;
      view.panY += sign * v.dy;
    }
  }
  regenerate();
}
acCompute.addEventListener('click', computeAutocorrVectors);
acStep.addEventListener('click', acWalkStep);
acSpeed.addEventListener('input', () => {
  acSpeedOut.textContent = parseFloat(acSpeed.value).toFixed(1);
});
acPlay.addEventListener('click', () => {
  acPlaying = !acPlaying;
  acPlay.textContent = acPlaying ? '❚❚ pause' : '▶ play';
  acAccum = 0;
});
acShow.addEventListener('click', () => {
  acShowVectors = !acShowVectors;
  acShow.textContent = acShowVectors ? 'hide vectors' : 'show vectors';
  acShow.classList.toggle('active', acShowVectors);
  if (acShowVectors && !acVectors) {
    computeAutocorrVectors();
  } else {
    // Re-render to add or clear the overlay.
    regenerate();
  }
});
// --- Audio sonification ---
// Map the scalar field to sound. The "scan" mode sweeps a spectral frame
// across the field columns; the "sample" mode plays the field as a looping
// waveform. Playback requires a cached field (computed synchronously here if
// one is not yet available, since regenerate() defers via rAF).
const fieldAudio = new FieldAudio();
const audioPlay = document.getElementById('audioPlay');
const audioMode = document.getElementById('audioMode');
const audioVol = document.getElementById('audioVol');
const audioVolOut = document.getElementById('audioVolOut');
const audioRate = document.getElementById('audioRate');
const audioRateOut = document.getElementById('audioRateOut');
function ensureFieldForAudio() {
  if (!lastResult || !lastOpts) {
    const opts = readOpts();
    updateOutputs(opts);
    const result = computeField(opts);
    renderField(canvas, result, opts);
    lastResult = result;
    lastOpts = opts;
  }
  return lastResult && lastOpts;
}
audioPlay.addEventListener('click', () => {
  // A user gesture is required to start the AudioContext.
  fieldAudio.setVolume(parseFloat(audioVol.value));
  fieldAudio.setScanRate(parseFloat(audioRate.value));
  if (fieldAudio.playing) {
    fieldAudio.stop();
    audioPlay.textContent = '▶ play audio';
    audioPlay.classList.remove('active');
    return;
  }
  if (!ensureFieldForAudio()) return;
  fieldAudio.start(lastResult, lastOpts);
  audioPlay.textContent = '❚❚ stop audio';
  audioPlay.classList.add('active');
});
audioMode.addEventListener('change', () => {
  fieldAudio.setMode(audioMode.value);
});
audioVol.addEventListener('input', () => {
  const v = parseFloat(audioVol.value);
  audioVolOut.textContent = v.toFixed(2);
  fieldAudio.setVolume(v);
});
audioRate.addEventListener('input', () => {
  const r = parseFloat(audioRate.value);
  audioRateOut.textContent = String(r);
  fieldAudio.setScanRate(r);
});

// Initial render.
fitCanvas();
hashToState();
// Reflect any hash-restored size into the rational stepper display.
if (rationalControls && rationalControls.refresh) rationalControls.refresh();
fitCanvas();
regenerate();
// --- Sidebar collapse toggle ---
const sidebarToggle = document.getElementById('sidebarToggle');
sidebarToggle.addEventListener('click', () => {
  mainEl.classList.toggle('sidebar-collapsed');
  sidebarToggle.classList.toggle('active', mainEl.classList.contains('sidebar-collapsed'));
  // The canvas wrap changes width when the sidebar collapses; refit.
  fitCanvas();
  regenerate();
});

// Respond to external hash changes (shared link pasted, back/forward nav).
window.addEventListener('hashchange', () => {
  if (restoringFromHash) return;
  if (hashToState()) regenerate();
});
