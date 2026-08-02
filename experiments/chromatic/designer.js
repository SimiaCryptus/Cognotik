// Chromatic Designer UI controller (designer.md §7).
//
// Wires the spectrum viewer canvas, inspector, constraint list, and solver
// controls onto the framework-agnostic Designer document + solve driver.

import {
  createDocument,
  addNode,
  removeNode,
  addLink,
  addGroup,
  removeGroup,
  setAnchor,
  clearAnchor,
} from './src/designer/document.js';
import { compileObjective } from './src/designer/objective.js';
import { solve } from './src/designer/solve.js';
import {
  readAxis,
  composeOklab,
  sliceColorAt,
  nodeColor,
  axisToUnit,
  unitToAxis,
  depthDelta,
  AXIS_RANGE,
} from './src/designer/viewer.js';
import { oklabToOklch } from './src/colorspace/oklab.js';

const SPACE_AXES = {
  OKLch: ['lightness', 'chroma', 'hue'],
  OKLab: ['L', 'a', 'b'],
};

const doc = createDocument();

const els = {
  canvas: document.getElementById('viewer'),
  space: document.getElementById('space'),
  xaxis: document.getElementById('xaxis'),
  yaxis: document.getElementById('yaxis'),
  depthaxis: document.getElementById('depthaxis'),
  depth: document.getElementById('depth'),
  depthOut: document.getElementById('depth-out'),
  gamut: document.getElementById('gamut'),
  inspector: document.getElementById('inspector-body'),
  constraints: document.getElementById('constraint-body'),
  solve: document.getElementById('solve'),
  step: document.getElementById('step'),
  stop: document.getElementById('stop'),
  animate: document.getElementById('animate'),
  iters: document.getElementById('iters'),
  jval: document.getElementById('jval'),
  status: document.getElementById('solve-status'),
};

const ctx = els.canvas.getContext('2d');

// --- selection & interaction state --------------------------------------
const selection = { nodes: new Set(), links: new Set() };
let dragNode = null;
let dragMoved = false;
let residualCache = null;
let solving = false;
let stopRequested = false;

// --- canvas sizing ------------------------------------------------------
function resizeCanvas() {
  const wrap = els.canvas.parentElement;
  const dpr = window.devicePixelRatio || 1;
  els.canvas.width = wrap.clientWidth * dpr;
  els.canvas.height = wrap.clientHeight * dpr;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  render();
}
window.addEventListener('resize', resizeCanvas);

// --- coordinate mapping (screen <-> axis unit) --------------------------
function viewSize() {
  const wrap = els.canvas.parentElement;
  return { w: wrap.clientWidth, h: wrap.clientHeight };
}

function nodeToScreen(node) {
  const { w, h } = viewSize();
  const xu = axisToUnit(doc.viewer.planeAxes[0], readAxis(node.oklab, doc.viewer.planeAxes[0]));
  const yu = axisToUnit(doc.viewer.planeAxes[1], readAxis(node.oklab, doc.viewer.planeAxes[1]));
  return { x: xu * w, y: (1 - yu) * h };
}

function screenToAxisVals(px, py) {
  const { w, h } = viewSize();
  const xu = Math.min(1, Math.max(0, px / w));
  const yu = Math.min(1, Math.max(0, 1 - py / h));
  return {
    x: unitToAxis(doc.viewer.planeAxes[0], xu),
    y: unitToAxis(doc.viewer.planeAxes[1], yu),
  };
}

// --- rendering ----------------------------------------------------------
let backdropCache = null;
let backdropKey = '';

function backdropCacheKey() {
  const v = doc.viewer;
  return `${v.space}|${v.planeAxes.join(',')}|${v.depthAxis}|${v.depthValue.toFixed(3)}|${els.gamut.checked}`;
}

function renderBackdrop() {
  const { w, h } = viewSize();
  const key = backdropCacheKey() + `|${w}x${h}`;
  if (backdropCache && backdropKey === key) {
    ctx.putImageData(backdropCache, 0, 0);
    return;
  }
  const cols = Math.max(1, Math.floor(w / 3));
  const rows = Math.max(1, Math.floor(h / 3));
  const img = ctx.createImageData(w, h);
  const data = img.data;
  for (let py = 0; py < h; py++) {
    const yu = 1 - py / h;
    const yVal = unitToAxis(doc.viewer.planeAxes[1], yu);
    for (let px = 0; px < w; px++) {
      const xu = px / w;
      const xVal = unitToAxis(doc.viewer.planeAxes[0], xu);
      const { rgb, inGamut } = sliceColorAt(doc.viewer, xVal, yVal);
      let r = rgb.r,
        g = rgb.g,
        b = rgb.b;
      if (!inGamut && els.gamut.checked) {
        // hatched/desaturated marking for out-of-gamut regions
        const dim = 0.35;
        const gray = 0.12;
        r = r * dim + gray;
        g = g * dim + gray;
        b = b * dim + gray;
      }
      const o = (py * w + px) * 4;
      data[o] = Math.round(Math.min(1, Math.max(0, r)) * 255);
      data[o + 1] = Math.round(Math.min(1, Math.max(0, g)) * 255);
      data[o + 2] = Math.round(Math.min(1, Math.max(0, b)) * 255);
      data[o + 3] = 255;
    }
  }
  backdropCache = img;
  backdropKey = key;
  ctx.putImageData(img, 0, 0);
}

function render() {
  const { w, h } = viewSize();
  ctx.clearRect(0, 0, w, h);
  renderBackdrop();

  // links
  for (const link of doc.links) {
    const a = doc.nodes.find((n) => n.id === link.a);
    const b = doc.nodes.find((n) => n.id === link.b);
    if (!a || !b) continue;
    const pa = nodeToScreen(a);
    const pb = nodeToScreen(b);
    const inWorstGroup =
      residualCache &&
      residualCache.worstGroup &&
      doc.groups.some((g) => g.id === residualCache.worstGroup && g.linkIds.includes(link.id));
    ctx.beginPath();
    ctx.moveTo(pa.x, pa.y);
    ctx.lineTo(pb.x, pb.y);
    ctx.lineWidth = selection.links.has(link.id) ? 4 : 2;
    ctx.strokeStyle = inWorstGroup
      ? '#ff8b8b'
      : selection.links.has(link.id)
        ? '#7cc4ff'
        : 'rgba(255,255,255,0.55)';
    ctx.stroke();
  }

  // nodes
  for (const node of doc.nodes) {
    const p = nodeToScreen(node);
    const { rgb } = nodeColor(node.oklab);
    const hex = `rgb(${Math.round(rgb.r * 255)},${Math.round(rgb.g * 255)},${Math.round(rgb.b * 255)})`;
    const dz = depthDelta(doc.viewer, node.oklab);
    const radius = 11;

    // depth cue ring: dashed if off-plane
    ctx.beginPath();
    ctx.arc(p.x, p.y, radius + 3, 0, Math.PI * 2);
    ctx.lineWidth = 2;
    ctx.setLineDash(Math.abs(dz) > 0.02 ? [3, 3] : []);
    const isWorst = residualCache && residualCache.worstNode === node.id;
    ctx.strokeStyle = isWorst
      ? '#ff8b8b'
      : selection.nodes.has(node.id)
        ? '#7cc4ff'
        : 'rgba(0,0,0,0.6)';
    ctx.stroke();
    ctx.setLineDash([]);

    ctx.beginPath();
    ctx.arc(p.x, p.y, radius, 0, Math.PI * 2);
    ctx.fillStyle = hex;
    ctx.fill();
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = 'rgba(0,0,0,0.5)';
    ctx.stroke();

    ctx.fillStyle = '#fff';
    ctx.font = '11px system-ui';
    ctx.textAlign = 'center';
    ctx.strokeStyle = 'rgba(0,0,0,0.7)';
    ctx.lineWidth = 3;
    ctx.strokeText(node.name, p.x, p.y - radius - 6);
    ctx.fillText(node.name, p.x, p.y - radius - 6);
  }
}

// --- hit testing --------------------------------------------------------
function nodeAt(px, py) {
  for (let i = doc.nodes.length - 1; i >= 0; i--) {
    const p = nodeToScreen(doc.nodes[i]);
    if (Math.hypot(p.x - px, p.y - py) <= 13) return doc.nodes[i];
  }
  return null;
}

function linkAt(px, py) {
  for (const link of doc.links) {
    const a = doc.nodes.find((n) => n.id === link.a);
    const b = doc.nodes.find((n) => n.id === link.b);
    if (!a || !b) continue;
    const pa = nodeToScreen(a);
    const pb = nodeToScreen(b);
    const d = distToSegment(px, py, pa, pb);
    if (d <= 6) return link;
  }
  return null;
}

function distToSegment(px, py, a, b) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy || 1;
  let t = ((px - a.x) * dx + (py - a.y) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  const cx = a.x + t * dx;
  const cy = a.y + t * dy;
  return Math.hypot(px - cx, py - cy);
}

// --- interaction handlers -----------------------------------------------
function canvasPos(evt) {
  const rect = els.canvas.getBoundingClientRect();
  return { x: evt.clientX - rect.left, y: evt.clientY - rect.top };
}

els.canvas.addEventListener('pointerdown', (evt) => {
  const { x, y } = canvasPos(evt);
  const node = nodeAt(x, y);
  const link = node ? null : linkAt(x, y);

  if (node) {
    if (evt.shiftKey) {
      if (selection.nodes.has(node.id)) selection.nodes.delete(node.id);
      else selection.nodes.add(node.id);
    } else {
      selection.nodes.clear();
      selection.links.clear();
      selection.nodes.add(node.id);
      dragNode = node;
      dragMoved = false;
      els.canvas.setPointerCapture(evt.pointerId);
    }
  } else if (link) {
    if (!evt.shiftKey) {
      selection.nodes.clear();
      selection.links.clear();
    }
    if (selection.links.has(link.id)) selection.links.delete(link.id);
    else selection.links.add(link.id);
  } else {
    // empty space -> place a node (unless clearing a selection with shift)
    if (!evt.shiftKey) {
      const { x: xVal, y: yVal } = screenToAxisVals(x, y);
      const oklab = composeOklab(doc.viewer, xVal, yVal, doc.viewer.depthValue);
      const node = addNode(doc, { oklab });
      selection.nodes.clear();
      selection.links.clear();
      selection.nodes.add(node.id);
    } else {
      selection.nodes.clear();
      selection.links.clear();
    }
  }
  refresh();
});

// Update any anchors on a node so their targets track the node's current
// position. Called after a manual move so "fixed" coordinates stay consistent.
function syncAnchorsToNode(node) {
  if (!node.anchors || node.anchors.length === 0) return;
  for (const a of node.anchors) {
    const cur = readAxis(node.oklab, a.dimension);
    setAnchor(doc, node.id, { dimension: a.dimension, target: cur });
  }
}

els.canvas.addEventListener('pointermove', (evt) => {
  if (!dragNode) return;
  const { x, y } = canvasPos(evt);
  const { x: xVal, y: yVal } = screenToAxisVals(x, y);
  const depthVal = readAxis(dragNode.oklab, doc.viewer.depthAxis);
  dragNode.oklab = composeOklab(doc.viewer, xVal, yVal, depthVal);
  dragMoved = true;
  syncAnchorsToNode(dragNode);
  invalidateResiduals();
  render();
});

els.canvas.addEventListener('pointerup', (evt) => {
  if (dragNode) {
    els.canvas.releasePointerCapture?.(evt.pointerId);
    dragNode = null;
    refresh();
  }
});

// scroll over a node adjusts its depth
els.canvas.addEventListener(
  'wheel',
  (evt) => {
    const { x, y } = canvasPos(evt);
    const node = nodeAt(x, y);
    if (!node) return;
    evt.preventDefault();
    const [lo, hi] = AXIS_RANGE[doc.viewer.depthAxis];
    const range = hi - lo;
    const cur = readAxis(node.oklab, doc.viewer.depthAxis);
    const next = Math.min(hi, Math.max(lo, cur - Math.sign(evt.deltaY) * range * 0.02));
    const xVal = readAxis(node.oklab, doc.viewer.planeAxes[0]);
    const yVal = readAxis(node.oklab, doc.viewer.planeAxes[1]);
    node.oklab = composeOklab(doc.viewer, xVal, yVal, next);
    syncAnchorsToNode(node);
    invalidateResiduals();
    refresh();
  },
  { passive: false }
);

// keyboard: delete selection
window.addEventListener('keydown', (evt) => {
  if (evt.key === 'Delete' || evt.key === 'Backspace') {
    if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
    for (const id of selection.nodes) removeNode(doc, id);
    for (const id of selection.links) {
      doc.links = doc.links.filter((l) => l.id !== id);
    }
    selection.nodes.clear();
    selection.links.clear();
    invalidateResiduals();
    refresh();
  }
});

// --- slice controls -----------------------------------------------------
function populateAxisSelectors() {
  const axes = SPACE_AXES[doc.viewer.space];
  for (const sel of [els.xaxis, els.yaxis, els.depthaxis]) {
    sel.innerHTML = '';
    for (const axis of axes) {
      const opt = document.createElement('option');
      opt.value = axis;
      opt.textContent = axis;
      sel.appendChild(opt);
    }
  }
  els.xaxis.value = doc.viewer.planeAxes[0];
  els.yaxis.value = doc.viewer.planeAxes[1];
  els.depthaxis.value = doc.viewer.depthAxis;
  syncDepthSlider();
}

function syncDepthSlider() {
  const [lo, hi] = AXIS_RANGE[doc.viewer.depthAxis];
  els.depth.min = lo;
  els.depth.max = hi;
  els.depth.step = (hi - lo) / 100;
  if (doc.viewer.depthValue < lo || doc.viewer.depthValue > hi) {
    doc.viewer.depthValue = (lo + hi) / 2;
  }
  els.depth.value = doc.viewer.depthValue;
  els.depthOut.textContent = doc.viewer.depthValue.toFixed(2);
}

els.space.addEventListener('change', () => {
  doc.viewer.space = els.space.value;
  const axes = SPACE_AXES[doc.viewer.space];
  doc.viewer.planeAxes = [axes[2] ?? axes[0], axes[1]];
  doc.viewer.depthAxis = axes[0];
  populateAxisSelectors();
  backdropCache = null;
  refresh();
});

for (const [sel, apply] of [
  [els.xaxis, (v) => (doc.viewer.planeAxes[0] = v)],
  [els.yaxis, (v) => (doc.viewer.planeAxes[1] = v)],
  [els.depthaxis, (v) => (doc.viewer.depthAxis = v)],
]) {
  sel.addEventListener('change', () => {
    apply(sel.value);
    if (sel === els.depthaxis) syncDepthSlider();
    backdropCache = null;
    invalidateResiduals();
    refresh();
  });
}

els.depth.addEventListener('input', () => {
  doc.viewer.depthValue = Number(els.depth.value);
  els.depthOut.textContent = doc.viewer.depthValue.toFixed(2);
  backdropCache = null;
  render();
});

els.gamut.addEventListener('change', () => {
  backdropCache = null;
  render();
});

// --- inspector & constraint panels --------------------------------------
function refresh() {
  render();
  renderInspector();
  renderConstraints();
  updateJ();
}

function invalidateResiduals() {
  residualCache = null;
}

function updateJ() {
  try {
    const { objective, residuals, varMap } = compileObjective(doc);
    const vec = varMap.pack();
    const r = residuals(vec);
    residualCache = r;
    els.jval.textContent = r.total.toFixed(5);
  } catch (e) {
    els.jval.textContent = '—';
  }
}

// --- link geometry helpers ---------------------------------------------
function linkLength(link) {
  const a = doc.nodes.find((n) => n.id === link.a);
  const b = doc.nodes.find((n) => n.id === link.b);
  if (!a || !b) return null;
  const dims = SPACE_AXES[doc.viewer.space];
  let sum = 0;
  for (const dim of dims) {
    const d = readAxis(b.oklab, dim) - readAxis(a.oklab, dim);
    sum += d * d;
  }
  return Math.sqrt(sum);
}

function renderInspector() {
  const body = els.inspector;
  const selNodes = [...selection.nodes];
  const selLinks = [...selection.links];

  if (selNodes.length === 1 && selLinks.length === 0) {
    const node = doc.nodes.find((n) => n.id === selNodes[0]);
    if (!node) {
      body.textContent = 'Nothing selected.';
      return;
    }
    const oklch = oklabToOklch(node.oklab);
    const { rgb } = nodeColor(node.oklab);
    const chip = `<span class="swatch-chip" style="background:rgb(${Math.round(rgb.r * 255)},${Math.round(rgb.g * 255)},${Math.round(rgb.b * 255)})"></span>`;
    const dims = SPACE_AXES[doc.viewer.space];
    body.innerHTML = `
      <div class="inspector-row">${chip}<b>${escapeHtml(node.name)}</b></div>
      <div class="inspector-row"><span class="lbl">OKLab</span> L=${node.oklab.L.toFixed(3)} a=${node.oklab.a.toFixed(3)} b=${node.oklab.b.toFixed(3)}</div>
      <div class="inspector-row"><span class="lbl">OKLch</span> L=${oklch.L.toFixed(3)} C=${oklch.C.toFixed(3)} H=${oklch.H.toFixed(1)}°</div>
      <div class="inspector-row"><span class="lbl">Anchors</span></div>
      <div id="anchor-editor"></div>
    `;
    const editor = body.querySelector('#anchor-editor');
    for (const dim of dims) {
      editor.appendChild(anchorLine(node, dim));
    }
    return;
  }

  if (selLinks.length >= 1 && selNodes.length === 0) {
    const links = selLinks.map((id) => doc.links.find((l) => l.id === id)).filter(Boolean);
    body.innerHTML = `<div class="inspector-row">${selLinks.length} link${selLinks.length > 1 ? 's' : ''} selected.</div>`;

    // length display (single edge) or list (multiple)
    if (links.length === 1) {
      const len = linkLength(links[0]);
      const lenRow = document.createElement('div');
      lenRow.className = 'inspector-row';
      lenRow.innerHTML = `<span class="lbl">length</span> ${len != null ? len.toFixed(4) : '—'}`;
      body.appendChild(lenRow);
    } else {
      const lenRow = document.createElement('div');
      lenRow.className = 'inspector-row';
      lenRow.innerHTML = `<span class="lbl">lengths</span> ${links
        .map((l) => {
          const v = linkLength(l);
          return v != null ? v.toFixed(3) : '—';
        })
        .join(', ')}`;
      body.appendChild(lenRow);
    }

    // min/max length bounds (applied to all selected links)
    const boundsRow = document.createElement('div');
    boundsRow.className = 'anchor-line';
    const firstMin = links.length ? (links[0].minLength ?? '') : '';
    const firstMax = links.length ? (links[0].maxLength ?? '') : '';

    const minLbl = document.createElement('span');
    minLbl.textContent = 'min';
    minLbl.className = 'tiny';
    const minInp = document.createElement('input');
    minInp.type = 'number';
    minInp.step = '0.01';
    minInp.min = '0';
    minInp.value = firstMin;
    minInp.style.width = '64px';

    const maxLbl = document.createElement('span');
    maxLbl.textContent = 'max';
    maxLbl.className = 'tiny';
    const maxInp = document.createElement('input');
    maxInp.type = 'number';
    maxInp.step = '0.01';
    maxInp.min = '0';
    maxInp.value = firstMax;
    maxInp.style.width = '64px';

    minInp.addEventListener('change', () => {
      const v = minInp.value === '' ? null : Number(minInp.value);
      for (const l of links) l.minLength = v;
      invalidateResiduals();
      refresh();
    });
    maxInp.addEventListener('change', () => {
      const v = maxInp.value === '' ? null : Number(maxInp.value);
      for (const l of links) l.maxLength = v;
      invalidateResiduals();
      refresh();
    });
    boundsRow.append(minLbl, minInp, maxLbl, maxInp);
    body.appendChild(boundsRow);

    // axis-lock: constrain edge to a single color axis
    const axisRow = document.createElement('div');
    axisRow.className = 'anchor-line';
    const axisLbl = document.createElement('span');
    axisLbl.textContent = 'lock axis';
    axisLbl.className = 'tiny';
    const axisSel = document.createElement('select');
    const noneOpt = document.createElement('option');
    noneOpt.value = '';
    noneOpt.textContent = '(none)';
    axisSel.appendChild(noneOpt);
    for (const dim of SPACE_AXES[doc.viewer.space]) {
      const opt = document.createElement('option');
      opt.value = dim;
      opt.textContent = dim;
      axisSel.appendChild(opt);
    }
    // show shared value if all links agree
    const shared = links.every((l) => l.lockAxis === links[0].lockAxis)
      ? (links[0].lockAxis ?? '')
      : '';
    axisSel.value = shared;
    axisSel.addEventListener('change', () => {
      const v = axisSel.value || null;
      for (const l of links) l.lockAxis = v;
      invalidateResiduals();
      refresh();
    });
    axisRow.append(axisLbl, axisSel);
    body.appendChild(axisRow);

    // group constraints (require >= 2 links)
    if (selLinks.length >= 2) {
      const row = document.createElement('div');
      row.className = 'btn-row';
      row.appendChild(makeBtn('Same length', () => makeGroup('length')));
      row.appendChild(makeBtn('Same angle', () => makeGroup('angle')));
      row.appendChild(
        makeBtn('Same length & angle', () => {
          makeGroup('length');
          makeGroup('angle');
        })
      );
      body.appendChild(row);
    }
    return;
  }

  if (selNodes.length >= 2 && selLinks.length === 0) {
    body.innerHTML = `<div class="inspector-row">${selNodes.length} nodes selected.</div>`;
    const row = document.createElement('div');
    row.className = 'btn-row';
    row.appendChild(
      makeBtn('Link chain', () => {
        for (let i = 0; i < selNodes.length - 1; i++) addLink(doc, selNodes[i], selNodes[i + 1]);
        invalidateResiduals();
        refresh();
      })
    );
    if (selNodes.length === 2) {
      row.appendChild(
        makeBtn('Link', () => {
          addLink(doc, selNodes[0], selNodes[1]);
          invalidateResiduals();
          refresh();
        })
      );
    }
    body.appendChild(row);

    // batch anchor to lightness
    const batch = document.createElement('div');
    batch.className = 'anchor-line';
    const dim = doc.viewer.space === 'OKLch' ? 'lightness' : 'L';
    const inp = document.createElement('input');
    inp.type = 'number';
    inp.step = '0.01';
    inp.value = '0.6';
    const btn = makeBtn(`Anchor all ${dim}`, () => {
      const v = Number(inp.value);
      for (const id of selNodes)
        setAnchor(doc, id, { dimension: dim, target: v, weight: 1, hard: false });
      invalidateResiduals();
      refresh();
    });
    batch.append(btn, inp);
    body.appendChild(batch);
    return;
  }

  body.textContent = 'Nothing selected.';
}

function anchorLine(node, dim) {
  const line = document.createElement('div');
  line.className = 'anchor-line';
  const anchor = node.anchors.find((a) => a.dimension === dim);
  const enabled = !!anchor;

  const cb = document.createElement('input');
  cb.type = 'checkbox';
  cb.checked = enabled;

  const label = document.createElement('span');
  label.textContent = dim;
  label.style.minWidth = '58px';

  const num = document.createElement('input');
  num.type = 'number';
  num.step = dim === 'hue' ? '1' : '0.01';
  num.disabled = !enabled;
  num.value = anchor ? anchor.target : readAxis(node.oklab, dim).toFixed(3);

  const hardCb = document.createElement('input');
  hardCb.type = 'checkbox';
  hardCb.checked = anchor ? !!anchor.hard : false;
  hardCb.disabled = !enabled;
  const hardLbl = document.createElement('span');
  hardLbl.textContent = 'hard';
  hardLbl.className = 'tiny';

  cb.addEventListener('change', () => {
    if (cb.checked) {
      setAnchor(doc, node.id, {
        dimension: dim,
        target: Number(num.value),
        weight: 1,
        hard: hardCb.checked,
      });
    } else {
      clearAnchor(doc, node.id, dim);
    }
    invalidateResiduals();
    refresh();
  });
  num.addEventListener('change', () => {
    if (cb.checked) setAnchor(doc, node.id, { dimension: dim, target: Number(num.value) });
    invalidateResiduals();
    updateJ();
  });
  hardCb.addEventListener('change', () => {
    if (cb.checked) setAnchor(doc, node.id, { dimension: dim, hard: hardCb.checked });
    invalidateResiduals();
    updateJ();
  });

  line.append(cb, label, num, hardCb, hardLbl);
  return line;
}

function makeGroup(kind) {
  const linkIds = [...selection.links];
  if (linkIds.length < 2) return;
  addGroup(doc, { kind, linkIds, mode: 'free', weight: 1 });
  invalidateResiduals();
  refresh();
}

function renderConstraints() {
  const body = els.constraints;
  body.innerHTML = '';
  const r = residualCache;

  const anchorCount = doc.nodes.reduce((s, n) => s + n.anchors.length, 0);
  const linkConstraintCount = doc.links.reduce(
    (s, l) =>
      s + (l.minLength != null ? 1 : 0) + (l.maxLength != null ? 1 : 0) + (l.lockAxis ? 1 : 0),
    0
  );
  if (anchorCount === 0 && doc.groups.length === 0 && linkConstraintCount === 0) {
    body.textContent = 'No constraints yet.';
    return;
  }

  // anchors
  doc.nodes.forEach((node) => {
    node.anchors.forEach((a) => {
      const line = document.createElement('div');
      line.className = 'anchor-line';
      const resid = r
        ? r.anchors.find((x) => x.node === node.id && x.dimension === a.dimension)
        : null;
      const rv = resid ? resid.error : null;
      line.innerHTML = `<span>${escapeHtml(node.name)}.${a.dimension}${a.hard ? ' <span class="tiny">(hard)</span>' : ''} = ${a.target}</span>
        <span class="residual ${rv != null ? (rv < 1e-4 ? 'good' : 'bad') : ''}">${rv != null ? rv.toExponential(1) : ''}</span>`;
      body.appendChild(line);
    });
  });

  // per-link bounds & axis locks
  doc.links.forEach((link) => {
    if (link.minLength == null && link.maxLength == null && !link.lockAxis) return;
    const a = doc.nodes.find((n) => n.id === link.a);
    const b = doc.nodes.find((n) => n.id === link.b);
    const name = `${a ? a.name : '?'}–${b ? b.name : '?'}`;
    const len = linkLength(link);

    if (link.minLength != null || link.maxLength != null) {
      const line = document.createElement('div');
      line.className = 'anchor-line';
      const lo = link.minLength;
      const hi = link.maxLength;
      const within =
        len != null && (lo == null || len >= lo - 1e-6) && (hi == null || len <= hi + 1e-6);
      const boundsTxt = `${lo != null ? lo.toFixed(3) : '−∞'} ≤ len ≤ ${hi != null ? hi.toFixed(3) : '∞'}`;
      line.innerHTML = `<span>${escapeHtml(name)} ${boundsTxt}</span>
        <span class="residual ${within ? 'good' : 'bad'}">${len != null ? len.toFixed(4) : ''}</span>`;
      body.appendChild(line);
    }

    if (link.lockAxis) {
      const line = document.createElement('div');
      line.className = 'anchor-line';
      // residual = movement off the locked axis (other dims should match)
      let off = null;
      if (a && b) {
        off = 0;
        for (const dim of SPACE_AXES[doc.viewer.space]) {
          if (dim === link.lockAxis) continue;
          const d = readAxis(b.oklab, dim) - readAxis(a.oklab, dim);
          off += d * d;
        }
        off = Math.sqrt(off);
      }
      line.innerHTML = `<span>${escapeHtml(name)} <span class="tiny">locked to</span> ${link.lockAxis}</span>
        <span class="residual ${off != null ? (off < 1e-3 ? 'good' : 'bad') : ''}">${off != null ? off.toExponential(1) : ''}</span>`;
      body.appendChild(line);
    }
  });

  // groups
  doc.groups.forEach((g) => {
    const line = document.createElement('div');
    line.className = 'group-line';
    const gr = r ? r.groups.find((x) => x.group === g.id) : null;
    const rv = gr ? gr.error : null;
    const unit = g.kind === 'angle' ? '°' : '';
    const membersTxt = gr
      ? gr.members.map((m) => m.toFixed(g.kind === 'angle' ? 0 : 3) + unit).join(', ')
      : '';
    const label = document.createElement('span');
    label.innerHTML = `<b>${g.kind}</b> group (${g.linkIds.length}) <span class="tiny">${g.mode}</span>
      <span class="residual ${rv != null ? (rv < 1e-4 ? 'good' : 'bad') : ''}"> ${rv != null ? rv.toExponential(1) : ''}</span>
      <div class="tiny">${membersTxt}</div>`;
    line.appendChild(label);

    const controls = document.createElement('div');
    controls.className = 'btn-row';
    // fixed/free toggle
    const modeBtn = makeBtn(g.mode === 'fixed' ? 'make free' : 'make fixed', () => {
      if (g.mode === 'fixed') {
        g.mode = 'free';
        g.target = null;
      } else {
        g.mode = 'fixed';
        // seed fixed target from current mean
        if (gr && gr.members.length) {
          const mean = gr.members.reduce((s, v) => s + v, 0) / gr.members.length;
          g.target = mean; // degrees for angle, distance for length
        } else {
          g.target = g.kind === 'angle' ? 0 : 0.1;
        }
      }
      invalidateResiduals();
      refresh();
    });
    controls.appendChild(modeBtn);
    if (g.mode === 'fixed') {
      const num = document.createElement('input');
      num.type = 'number';
      num.step = g.kind === 'angle' ? '1' : '0.01';
      num.value = g.target ?? 0;
      num.style.width = '70px';
      num.addEventListener('change', () => {
        g.target = Number(num.value);
        invalidateResiduals();
        refresh();
      });
      controls.appendChild(num);
    }
    controls.appendChild(
      makeBtn('✕', () => {
        removeGroup(doc, g.id);
        invalidateResiduals();
        refresh();
      })
    );
    line.appendChild(controls);
    body.appendChild(line);
  });
}

// --- solver controls ----------------------------------------------------
async function runSolve(singleStep) {
  if (solving) return;
  solving = true;
  stopRequested = false;
  els.stop.disabled = false;
  els.solve.disabled = true;
  els.step.disabled = true;
  els.status.textContent = 'solving…';

  const animate = els.animate.checked && !singleStep;
  const maxIterations = singleStep ? 1 : Number(els.iters.value) || 200;

  // Run solve; if animating, re-render on each iteration via a chunked loop.
  if (animate) {
    // Run in small batches so the UI can paint and Stop can interrupt.
    const batch = 5;
    let done = 0;
    const total = maxIterations;
    while (done < total && !stopRequested) {
      const result = solve(doc, {
        method: 'gradient',
        maxIterations: batch,
        gamut: true,
      });
      residualCache = result.residuals;
      done += batch;
      els.jval.textContent = result.value.toFixed(5);
      render();
      renderConstraints();
      await new Promise((r) => requestAnimationFrame(r));
      if (result.iterations < batch) break; // converged
    }
  } else {
    const result = solve(doc, {
      method: 'gradient',
      maxIterations,
      gamut: true,
    });
    residualCache = result.residuals;
    els.jval.textContent = result.value.toFixed(5);
  }

  solving = false;
  els.stop.disabled = true;
  els.solve.disabled = false;
  els.step.disabled = false;
  els.status.textContent = stopRequested ? 'stopped' : 'done';
  refresh();
}

els.solve.addEventListener('click', () => runSolve(false));
els.step.addEventListener('click', () => runSolve(true));
els.stop.addEventListener('click', () => {
  stopRequested = true;
});

// --- helpers ------------------------------------------------------------
function makeBtn(label, onClick) {
  const b = document.createElement('button');
  b.textContent = label;
  b.addEventListener('click', onClick);
  return b;
}

function escapeHtml(s) {
  return String(s).replace(
    /[&<>"]/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]
  );
}

// --- boot ---------------------------------------------------------------
populateAxisSelectors();
resizeCanvas();
refresh();
