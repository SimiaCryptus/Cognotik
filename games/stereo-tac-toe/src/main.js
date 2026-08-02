// Bootstrap: wire modules together and own the render loop.

import { CONFIG } from './config.js';
import { Game } from './game.js';
import { Input } from './input.js';
import { createDepthMap, renderDepthMap } from './depthMap.js';
import { Stereogram } from './stereogram.js';
import { Diagnostics } from './diagnostics.js';
import { Controls } from './controls.js';

const canvas = document.getElementById('game');
canvas.width = CONFIG.WIDTH;
canvas.height = CONFIG.HEIGHT;
const ctx = canvas.getContext('2d');

const statusEl = document.getElementById('status');
const diagnosticsToggle = document.getElementById('diagnostics');
const resetBtn = document.getElementById('reset');
const panelEl = document.getElementById('panel');

const game = new Game();
let depthBuffer = createDepthMap(CONFIG.WIDTH, CONFIG.HEIGHT);
const stereogram = new Stereogram(CONFIG.WIDTH, CONFIG.HEIGHT);
const diagnostics = new Diagnostics(CONFIG.WIDTH, CONFIG.HEIGHT);

let dirty = true;

function markDirty() {
  dirty = true;
}

// Reallocate everything that depends on canvas resolution.
function applyResolution(w, h) {
  w = Math.max(160, Math.round(w));
  h = Math.max(120, Math.round(h));
  CONFIG.WIDTH = w;
  CONFIG.HEIGHT = h;
  canvas.width = w;
  canvas.height = h;
  depthBuffer = createDepthMap(w, h);
  stereogram.resize(w, h);
  diagnostics.resize(w, h);
  markDirty();
}

const input = new Input(canvas, game, markDirty);

// Control panel: sliders/selects for stereogram, noise, depth & drawing.
const controls = new Controls(panelEl, {
  onRedraw: markDirty,
  onPatternChange: () => {
    stereogram.rebuildPattern();
    markDirty();
  },
  onResize: applyResolution,
  getSize: () => ({ w: CONFIG.WIDTH, h: CONFIG.HEIGHT }),
  canvas,
});

// Redraw only when state changed (dirty flag) to keep the fused image steady.
function frame() {
  if (dirty) {
    renderDepthMap(depthBuffer, game);

    if (diagnosticsToggle.checked) {
      diagnostics.drawDepth(ctx, depthBuffer);
    } else {
      stereogram.renderStereogram(ctx, depthBuffer);
    }

    statusEl.textContent = game.statusText();
    dirty = false;
  }
  requestAnimationFrame(frame);
}

// --- Controls ---

resetBtn.addEventListener('click', () => {
  game.reset();
  markDirty();
});

diagnosticsToggle.addEventListener('change', markDirty);

document.addEventListener('keydown', (e) => {
  if (e.key === 'd' || e.key === 'D') {
    diagnosticsToggle.checked = !diagnosticsToggle.checked;
    markDirty();
  }
});

requestAnimationFrame(frame);
