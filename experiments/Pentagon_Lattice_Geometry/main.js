// main.js
// Top-level wiring: build lattice, install canvas interactions,
// hook up keyboard walking and side-panel updates.

import {
  buildNgonLattice,
  buildSierpinski,
  buildPinwheel,
  fieldInfoForN,
  POLY_PRESETS,
} from './ngon.js';
import { LatticeView } from './render.js';
import { renderTileInfo, appendWalkStep, clearWalk } from './ui.js';
import { initDocs } from './ui.js';
import { CA } from './ca.js';

const canvas = document.getElementById('lattice');
const view = new LatticeView(canvas);

const $ = (id) => document.getElementById(id);
// ── Inject pinwheel option into the polygon-type select and add options UI ──
(function injectPinwheelUI() {
  const polyType = document.getElementById('polyType');
  if (polyType && !polyType.querySelector('option[value="pinwheel"]')) {
    const opt = document.createElement('option');
    opt.value = 'pinwheel';
    opt.textContent = 'Pinwheel (Rectangle + Corner Triangle)';
    // Insert before sierpinski if present, else append.
    const sierp = polyType.querySelector('option[value="sierpinski"]');
    if (sierp) polyType.insertBefore(opt, sierp);
    else polyType.appendChild(opt);
  }
  // Add pinwheel options panel next to the sierpinski depth control.
  const sierpLabel = document.getElementById('sierpinskiDepthLabel');
  if (sierpLabel && !document.getElementById('pinwheelOptionsLabel')) {
    const label = document.createElement('label');
    label.id = 'pinwheelOptionsLabel';
    label.style.display = 'none';
    label.innerHTML =
      `<span style="font-size:12px;color:var(--muted)">Pinwheel options</span>` +
      `<div style="display:flex;gap:6px;margin-top:4px;align-items:center;font-size:12px">` +
      `<span>a</span><input type="number" id="pinwheelA" value="2" step="0.5" min="0.5" max="6" style="width:55px">` +
      `<span>b</span><input type="number" id="pinwheelB" value="1" step="0.5" min="0.5" max="6" style="width:55px">` +
      `<span>c</span><input type="number" id="pinwheelC" value="1" step="0.5" min="0.5" max="6" style="width:55px">` +
      `</div>` +
      `<label style="display:flex;align-items:center;gap:6px;font-size:12px;margin-top:4px">` +
      `<input type="checkbox" id="pinwheelHypSheets">` +
      `Hypotenuse → sheet transitions (visualisation)` +
      `</label>`;
    sierpLabel.parentNode.insertBefore(label, sierpLabel.nextSibling);
  }
})();

const els = {
  radius: $('radius'),
  group: $('group'),
  rebuild: $('rebuild'),
  reset: $('reset-view'),
  tileInfo: $('tile-info'),
  walk: $('walk-history'),
  clearHist: $('clear-history'),
  // polygon type
  polyType: $('polyType'),
  customNLabel: $('customNLabel'),
  customN: $('customN'),
  sierpinskiDepthLabel: $('sierpinskiDepthLabel'),
  sierpinskiDepth: $('sierpinskiDepth'),
  pinwheelOptionsLabel: $('pinwheelOptionsLabel'),
  pinwheelHypSheets: $('pinwheelHypSheets'),
  pinwheelA: $('pinwheelA'),
  pinwheelB: $('pinwheelB'),
  pinwheelC: $('pinwheelC'),
  fieldInfo: $('fieldInfo'),
  // display
  colorMode: $('colorMode'),
  palette: $('palette'),
  alphaSel: $('alphaSel'),
  alphaOther: $('alphaOther'),
  sat: $('sat'),
  light: $('light'),
  border: $('border'),
  fillTiles: $('fillTiles'),
  strokeTiles: $('strokeTiles'),
  onlySelSheet: $('onlySelSheet'),
  originGuide: $('originGuide'),
  bgGradient: $('bgGradient'),
  // labels
  tileLabels: $('tileLabels'),
  edgelabels: $('edgelabels'),
  depthLabels: $('depthLabels'),
  indexLabels: $('indexLabels'),
  labelsAllSheets: $('labelsAllSheets'),
  labelSize: $('labelSize'),
  // selection
  showSelGlow: $('showSelGlow'),
  showNeighborLinks: $('showNeighborLinks'),
  glow: $('glow'),
  // CA
  caOverlay: $('caOverlay'),
  caFamily: $('caFamily'),
  caLifeRule: $('caLifeRule'),
  caLifeRuleLabel: $('caLifeRuleLabel'),
  caLifePreset: $('caLifePreset'),
  caLifePresetLabel: $('caLifePresetLabel'),
  caNumStates: $('caNumStates'),
  caThreshold: $('caThreshold'),
  caThresholdLabel: $('caThresholdLabel'),
  caThresholdVal: $('caThresholdVal'),
  caPlay: $('caPlay'),
  caStep: $('caStep'),
  caReset: $('caReset'),
  caSeedPoint: $('caSeedPoint'),
  caSeedRand: $('caSeedRand'),
  caClear: $('caClear'),
  caSeedShape: $('caSeedShape'),
  caSeedShapeApply: $('caSeedShapeApply'),
  caDensity: $('caDensity'),
  caDensityVal: $('caDensityVal'),
  caSpeed: $('caSpeed'),
  caSpeedVal: $('caSpeedVal'),
  caPaintMode: $('caPaintMode'),
  caGen: $('caGen'),
  caPop: $('caPop'),
  caBySheet: $('caBySheet'),
  // value displays
  alphaSelVal: $('alphaSelVal'),
  alphaOtherVal: $('alphaOtherVal'),
  satVal: $('satVal'),
  lightVal: $('lightVal'),
  borderVal: $('borderVal'),
  labelSizeVal: $('labelSizeVal'),
  glowVal: $('glowVal'),
};

let lattice = null;
let currentTileIdx = 0;
let ca = null;
let caPlaying = false;
let caStepsPerSec = 8;
let caLastStepTime = 0;
let caRAF = null;

// ── Polygon type helpers ──────────────────────────────────────────────────────

function getPolyConfig() {
  const type = els.polyType.value;
  if (type === 'sierpinski') {
    return { mode: 'sierpinski', depth: parseInt(els.sierpinskiDepth.value, 10) || 4 };
  }
  if (type === 'pinwheel') {
    const a = parseFloat(els.pinwheelA && els.pinwheelA.value) || 2;
    const b = parseFloat(els.pinwheelB && els.pinwheelB.value) || 1;
    const c = parseFloat(els.pinwheelC && els.pinwheelC.value) || 1;
    return {
      mode: 'pinwheel',
      a,
      b,
      c,
      hypotenuseSheets: els.pinwheelHypSheets ? els.pinwheelHypSheets.checked : false,
    };
  }
  if (type === 'custom') {
    return { mode: 'ngon', n: Math.max(3, Math.min(24, parseInt(els.customN.value, 10) || 7)) };
  }
  const preset = POLY_PRESETS[type];
  if (preset) return { mode: 'ngon', n: preset.n };
  return { mode: 'ngon', n: 5 };
}

function updatePolyTypeUI() {
  const type = els.polyType.value;
  els.customNLabel.style.display = type === 'custom' ? '' : 'none';
  els.sierpinskiDepthLabel.style.display = type === 'sierpinski' ? '' : 'none';
  if (els.pinwheelOptionsLabel) {
    els.pinwheelOptionsLabel.style.display = type === 'pinwheel' ? '' : 'none';
  }

  // Show/hide BFS radius (not meaningful for Sierpiński; meaningful for pinwheel).
  const radiusLabel = els.radius.closest('label');
  if (radiusLabel) radiusLabel.style.display = type === 'sierpinski' ? 'none' : '';

  // Update field info box.
  if (type === 'sierpinski') {
    els.fieldInfo.innerHTML =
      `<b>Sierpiński Triangle</b><br>` +
      `Field: ℚ(√3) &nbsp;|&nbsp; Γ: ℤ₆<br>` +
      `IFS contraction ratio: ½<br>` +
      `Fractal dim: log3/log2 ≈ 1.585`;
  } else if (type === 'pinwheel') {
    const cfg = getPolyConfig();
    els.fieldInfo.innerHTML =
      `<b>Pinwheel Tile</b> (rectangle + free hypotenuse triangle)<br>` +
      `Rectangle: ${cfg.a} × ${cfg.b} &nbsp;|&nbsp; Triangle legs: ${cfg.a} × ${cfg.c}<br>` +
      `Hypotenuse: √(${cfg.a}² + ${cfg.c}²) = ${Math.hypot(cfg.a, cfg.c).toFixed(3)}<br>` +
      `Active edges: 4 of 5 (3 rectangle sides + vertical triangle leg)<br>` +
      `Inactive: hypotenuse (free edge, edge 4 in CCW order)<br>` +
      `Replication: ×4 by 90° rotations → windmill motif (ℤ₄)<br>` +
      `Irregular but symmetric; algebraically compact<br>` +
      `Field: ℚ (when a, b, c ∈ ℚ) &nbsp;|&nbsp; Target lattice: ℤ²<br>` +
      (cfg.hypotenuseSheets
        ? `Hypotenuses carry sheet-shift (visualisation only)`
        : `Hypotenuses are pure boundary (no sheet shift)`) +
      `<br>d<sub>eff</sub> = 2 exactly`;
  } else {
    const n = getPolyConfig().n;
    const info = fieldInfoForN(n);
    const isOdd = n % 2 === 1;
    const fiber = isOdd ? 'Z₂ (orientation cover, 2 sheets)' : 'trivial (single sheet)';
    els.fieldInfo.innerHTML =
      `<b>${n}-gon</b><br>` +
      `Base field: ${info.field}<br>` +
      `Fiber / structure group: ${fiber}<br>` +
      `<span style="color:var(--muted);font-size:11px">` +
      `Adjacent tiles flip orientation; vertex-loop holonomy is trivial ` +
      `(even-length cycle). ${info.result}</span>`;
  }

  // Update subtitle.
  const sub = $('subtitle-text');
  if (sub) {
    const cfg = getPolyConfig();
    if (cfg.mode === 'sierpinski') {
      sub.innerHTML =
        `Sierpiński Triangle IFS. Click a tile to inspect. ` +
        `Press <kbd>space</kbd> to play/pause CA, <kbd>n</kbd> to step.`;
    } else if (cfg.mode === 'pinwheel') {
      sub.innerHTML =
        `Pinwheel tile: rectangle (a×b) + right triangle (legs a, c) with free hypotenuse, ` +
        `replicated by 90° rotations (×4) about the inner corner to form a windmill (ℤ₄). ` +
        `Click a tile to inspect. Press <kbd>1</kbd>–<kbd>5</kbd> to walk edges ` +
        `(the hypotenuse edge is free / inactive). ` +
        `Press <kbd>space</kbd> to play/pause CA, <kbd>n</kbd> to step.`;
    } else {
      const edgeKeys =
        cfg.n <= 9 ? `<kbd>1</kbd>–<kbd>${cfg.n}</kbd>` : `<kbd>1</kbd>–<kbd>9</kbd>`;
      sub.innerHTML =
        `Regular ${cfg.n}-gon lattice (Z₂ orientation cover; ` +
        `each edge flips orientation, vertex-loop holonomy trivial). ` +
        `Click a tile to inspect. Press ${edgeKeys} to walk. ` +
        `Press <kbd>space</kbd> to play/pause CA, <kbd>n</kbd> to step.`;
    }
  }
}

function groupOrderFromSel() {
  // sheet_fix.md (NORMATIVE): the cover is Z₂ (orientation only). The old
  // Z₅ / Z₁₀ options were based on the corrected error and no longer exist.
  // buildNgonLattice further forces groupOrder to 2 (odd n) or 1 (even n),
  // so this value is only an upper bound / display hint.
  return 2;
}

function rebuild() {
  const cfg = getPolyConfig();

  if (cfg.mode === 'sierpinski') {
    lattice = buildSierpinski(cfg.depth);
  } else if (cfg.mode === 'pinwheel') {
    const radius = Math.max(0, Math.min(8, parseInt(els.radius.value, 10) || 3));
    lattice = buildPinwheel({
      radius,
      a: cfg.a,
      b: cfg.b,
      c: cfg.c,
      hypotenuseSheets: cfg.hypotenuseSheets,
    });
  } else {
    const radius = Math.max(0, Math.min(8, parseInt(els.radius.value, 10) || 3));
    const groupOrder = groupOrderFromSel();
    lattice = buildNgonLattice({ n: cfg.n, radius, groupOrder });
  }

  view.setLattice(lattice);
  currentTileIdx = 0;
  view.select(currentTileIdx);
  renderTileInfo(els.tileInfo, lattice.tiles[currentTileIdx], lattice);
  clearWalk(els.walk);
  appendWalkStep(els.walk, lattice.tiles[currentTileIdx], null, 'origin');
  initCA();
  updatePolyTypeUI();
}

function initCA() {
  const numStates = Math.max(2, Math.min(16, parseInt(els.caNumStates.value, 10) || 2));
  ca = new CA(lattice, {
    numStates,
    family: els.caFamily.value,
    cyclicThreshold: parseInt(els.caThreshold.value, 10) || 1,
  });
  ca.setLifeRule(els.caLifeRule.value);
  ca.seedPoint(0, 1);
  view.setCA(ca);
  updateCAStats();
}

function updateCAStats() {
  if (!ca) return;
  els.caGen.textContent = String(ca.generation);
  els.caPop.textContent = String(ca.population());
  const by = ca.populationBySheet();
  if (by.size === 0) {
    els.caBySheet.textContent = '—';
  } else {
    const parts = [...by.entries()].sort((a, b) => a[0] - b[0]).map(([s, n]) => `s${s}:${n}`);
    els.caBySheet.textContent = parts.join('  ');
  }
}

function caTick(now) {
  if (!caPlaying) {
    caRAF = null;
    return;
  }
  const interval = 1000 / caStepsPerSec;
  if (now - caLastStepTime >= interval) {
    ca.step();
    caLastStepTime = now;
    view.draw();
    updateCAStats();
  }
  caRAF = requestAnimationFrame(caTick);
}

function caPlayPause(force) {
  if (typeof force === 'boolean') caPlaying = force;
  else caPlaying = !caPlaying;
  els.caPlay.textContent = caPlaying ? '⏸ Pause' : '▶ Play';
  if (caPlaying) {
    caLastStepTime = performance.now();
    caRAF = requestAnimationFrame(caTick);
  } else if (caRAF !== null) {
    cancelAnimationFrame(caRAF);
    caRAF = null;
  }
}

function updateFamilyVisibility() {
  const fam = els.caFamily.value;
  els.caLifeRuleLabel.style.display = fam === 'life' ? '' : 'none';
  els.caLifePresetLabel.style.display = fam === 'life' ? '' : 'none';
  els.caThresholdLabel.style.display = fam === 'cyclic' ? '' : 'none';
}

// ── Event wiring ─────────────────────────────────────────────────────────────

els.rebuild.addEventListener('click', rebuild);
els.reset.addEventListener('click', () => {
  view.fit();
  view.draw();
});

els.polyType.addEventListener('change', () => {
  updatePolyTypeUI();
  // Auto-rebuild when type changes.
  rebuild();
});
els.customN.addEventListener('change', () => {
  if (els.polyType.value === 'custom') {
    updatePolyTypeUI();
    rebuild();
  }
});
els.sierpinskiDepth.addEventListener('change', () => {
  if (els.polyType.value === 'sierpinski') rebuild();
});
if (els.pinwheelHypSheets) {
  els.pinwheelHypSheets.addEventListener('change', () => {
    if (els.polyType.value === 'pinwheel') rebuild();
  });
}
// Pinwheel a, b, c dimension changes trigger rebuild.
['pinwheelA', 'pinwheelB', 'pinwheelC'].forEach((id) => {
  const el = document.getElementById(id);
  if (el)
    el.addEventListener('change', () => {
      if (els.polyType.value === 'pinwheel') {
        // Refresh refs (in case they were added after initial $()).
        els.pinwheelA = document.getElementById('pinwheelA');
        els.pinwheelB = document.getElementById('pinwheelB');
        els.pinwheelC = document.getElementById('pinwheelC');
        rebuild();
      }
    });
});

// ---- Display option wiring ----
function bindCheckbox(el, name) {
  if (!el) return;
  view.setOption(name, el.checked);
  el.addEventListener('change', (e) => view.setOption(name, e.target.checked));
}
function bindSelect(el, name) {
  if (!el) return;
  view.setOption(name, el.value);
  el.addEventListener('change', (e) => view.setOption(name, e.target.value));
}
function bindRange(el, name, display, transform = (x) => x, fmt = (x) => x) {
  if (!el) return;
  const apply = () => {
    const v = transform(parseFloat(el.value));
    view.setOption(name, v);
    if (display) display.textContent = fmt(v);
  };
  apply();
  el.addEventListener('input', apply);
}

bindSelect(els.colorMode, 'colorMode');
bindSelect(els.palette, 'palette');
bindRange(
  els.alphaSel,
  'alphaSelected',
  els.alphaSelVal,
  (v) => v / 100,
  (v) => v.toFixed(2)
);
bindRange(
  els.alphaOther,
  'alphaOther',
  els.alphaOtherVal,
  (v) => v / 100,
  (v) => v.toFixed(2)
);
bindRange(
  els.sat,
  'saturation',
  els.satVal,
  (v) => v,
  (v) => String(Math.round(v))
);
bindRange(
  els.light,
  'lightness',
  els.lightVal,
  (v) => v,
  (v) => String(Math.round(v))
);
bindRange(
  els.border,
  'borderWidth',
  els.borderVal,
  (v) => v / 10,
  (v) => v.toFixed(1)
);
bindCheckbox(els.fillTiles, 'fillTiles');
bindCheckbox(els.strokeTiles, 'strokeTiles');
bindCheckbox(els.onlySelSheet, 'onlySelSheet');
bindCheckbox(els.originGuide, 'originGuide');
bindCheckbox(els.bgGradient, 'bgGradient');

bindCheckbox(els.tileLabels, 'tileLabels');
bindCheckbox(els.edgelabels, 'edgeLabels');
bindCheckbox(els.depthLabels, 'depthLabels');
bindCheckbox(els.indexLabels, 'indexLabels');
bindCheckbox(els.labelsAllSheets, 'labelsAllSheets');
bindRange(
  els.labelSize,
  'labelSize',
  els.labelSizeVal,
  (v) => v,
  (v) => String(Math.round(v))
);

bindCheckbox(els.showSelGlow, 'showSelGlow');
bindCheckbox(els.showNeighborLinks, 'showNeighborLinks');
bindRange(
  els.glow,
  'glowStrength',
  els.glowVal,
  (v) => v,
  (v) => String(Math.round(v))
);

// ---- CA wiring ----
bindCheckbox(els.caOverlay, 'caOverlay');
els.caFamily.addEventListener('change', () => {
  if (ca) ca.setFamily(els.caFamily.value);
  updateFamilyVisibility();
  view.draw();
});
els.caLifeRule.addEventListener('change', () => {
  if (ca) ca.setLifeRule(els.caLifeRule.value);
  if (els.caLifePreset && els.caLifePreset.value !== els.caLifeRule.value) {
    els.caLifePreset.value = '';
  }
});
els.caLifePreset.addEventListener('change', () => {
  const v = els.caLifePreset.value;
  if (!v) return;
  els.caLifeRule.value = v;
  if (ca) ca.setLifeRule(v);
});
els.caNumStates.addEventListener('change', () => {
  const n = Math.max(2, Math.min(16, parseInt(els.caNumStates.value, 10) || 2));
  if (ca) ca.setNumStates(n);
  view.draw();
  updateCAStats();
});
els.caThreshold.addEventListener('input', () => {
  const t = parseInt(els.caThreshold.value, 10) || 1;
  els.caThresholdVal.textContent = String(t);
  if (ca) ca.setCyclicThreshold(t);
});
els.caDensity.addEventListener('input', () => {
  const d = (parseInt(els.caDensity.value, 10) || 0) / 100;
  els.caDensityVal.textContent = d.toFixed(2);
});
els.caSpeed.addEventListener('input', () => {
  caStepsPerSec = Math.max(1, parseInt(els.caSpeed.value, 10) || 1);
  els.caSpeedVal.textContent = String(caStepsPerSec);
});
els.caPlay.addEventListener('click', () => caPlayPause());
els.caStep.addEventListener('click', () => {
  if (!ca) return;
  caPlayPause(false);
  ca.step();
  ensureCAOverlayOn();
  view.draw();
  updateCAStats();
});
els.caReset.addEventListener('click', () => {
  if (!ca) return;
  caPlayPause(false);
  ca.clear();
  view.draw();
  updateCAStats();
});
els.caSeedPoint.addEventListener('click', () => {
  if (!ca) return;
  ca.seedPoint(currentTileIdx, 1);
  ensureCAOverlayOn();
  view.draw();
  updateCAStats();
});
els.caSeedRand.addEventListener('click', () => {
  if (!ca) return;
  const d = (parseInt(els.caDensity.value, 10) || 30) / 100;
  ca.randomize(d);
  ensureCAOverlayOn();
  view.draw();
  updateCAStats();
});
els.caSeedShapeApply.addEventListener('click', () => {
  if (!ca) return;
  const shape = els.caSeedShape.value;
  ca.seedShape(shape, currentTileIdx);
  ensureCAOverlayOn();
  view.draw();
  updateCAStats();
});
els.caClear.addEventListener('click', () => {
  if (!ca) return;
  ca.clear();
  view.draw();
  updateCAStats();
});

function ensureCAOverlayOn() {
  if (!els.caOverlay.checked) {
    els.caOverlay.checked = true;
    view.setOption('caOverlay', true);
  }
}

caStepsPerSec = parseInt(els.caSpeed.value, 10) || 8;
els.caSpeedVal.textContent = String(caStepsPerSec);
els.caDensityVal.textContent = ((parseInt(els.caDensity.value, 10) || 0) / 100).toFixed(2);
updateFamilyVisibility();

els.clearHist.addEventListener('click', () => {
  clearWalk(els.walk);
  if (lattice) appendWalkStep(els.walk, lattice.tiles[currentTileIdx], null, 'origin');
});

// Canvas interaction: click to select, drag to pan, wheel to zoom.
let dragging = false;
let dragMoved = false;
let lastX = 0,
  lastY = 0;
canvas.addEventListener('mousedown', (e) => {
  dragging = true;
  dragMoved = false;
  lastX = e.clientX;
  lastY = e.clientY;
});
window.addEventListener('mousemove', (e) => {
  if (!dragging) return;
  const dx = e.clientX - lastX;
  const dy = e.clientY - lastY;
  if (Math.abs(dx) + Math.abs(dy) > 2) dragMoved = true;
  lastX = e.clientX;
  lastY = e.clientY;
  view.pan(dx, dy);
});
window.addEventListener('mouseup', (e) => {
  if (!dragging) return;
  dragging = false;
  if (!dragMoved) {
    const [sx, sy] = view.eventToCanvas(e);
    const idx = view.pickTile(sx, sy);
    if (idx !== null) {
      if (els.caPaintMode.checked && ca) {
        ca.toggleCell(idx);
        ensureCAOverlayOn();
        view.draw();
        updateCAStats();
      } else {
        currentTileIdx = idx;
        view.select(idx);
        renderTileInfo(els.tileInfo, lattice.tiles[idx], lattice);
        appendWalkStep(els.walk, lattice.tiles[idx], null, 'click');
      }
    }
  }
});
canvas.addEventListener(
  'wheel',
  (e) => {
    e.preventDefault();
    const [sx, sy] = view.eventToCanvas(e);
    const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15;
    view.zoom(factor, sx, sy);
  },
  { passive: false }
);

// Keyboard walking: 1..9 steps across edges of the currently selected tile.
window.addEventListener('keydown', (e) => {
  if (!lattice) return;
  const tag = (e.target && e.target.tagName) || '';
  if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return;

  if (e.key === ' ' || e.code === 'Space') {
    e.preventDefault();
    caPlayPause();
    return;
  }
  if (e.key === 'n' || e.key === 'N') {
    if (ca) {
      caPlayPause(false);
      ca.step();
      view.draw();
      updateCAStats();
    }
    return;
  }

  // Support 1..9 for up to 9-gons.
  const k = '123456789'.indexOf(e.key);
  if (k < 0) return;
  const t = lattice.tiles[currentTileIdx];
  if (k >= t.neighbors.length) return;
  if (t.activeEdges && !t.activeEdges[k]) {
    appendWalkStep(els.walk, t, k, 'inactive edge (pinwheel)');
    return;
  }
  const nIdx = t.neighbors[k];
  if (nIdx === null) {
    appendWalkStep(els.walk, t, k, 'no neighbor in lattice');
    return;
  }
  currentTileIdx = nIdx;
  view.select(nIdx);
  renderTileInfo(els.tileInfo, lattice.tiles[nIdx], lattice);
  appendWalkStep(els.walk, lattice.tiles[nIdx], k);
});

// initial build
rebuild();
initDocs();
