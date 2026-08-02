// main.js — wires UI, optimizer loop, triangulation, energy, rendering.

import * as tf from 'https://esm.sh/@tensorflow/tfjs@4.20.0';
import { MANIFOLDS } from './manifolds.js';
import { delaunay2D } from './triangulation.js';
import { dihedralEnergy, repulsionEnergy, readDihedrals } from './dihedral.js';
import { Renderer } from './renderer.js';

const $ = (id) => document.getElementById(id);

const state = {
  running: false,
  step: 0,
  k: 40,
  manifoldKey: 'plane',
  points: null, // tf.variable [k, paramDim]
  optimizer: null,
  tri: [],
  edges: [],
  triTensor: null,
  edgeT1: null,
  edgeT2: null,
  lastEnergy: 0,
  renderer: null,
};

// ---- UI bindings --------------------------------------------------------
function bindSlider(id, valId, transform, onChange) {
  const el = $(id),
    lbl = $(valId);
  const update = () => {
    const v = transform(+el.value);
    if (lbl) lbl.textContent = typeof v === 'number' ? formatNum(v) : v;
    if (onChange) onChange(v);
  };
  el.addEventListener('input', update);
  // Populate label immediately, but defer onChange until after module init
  // to avoid touching not-yet-initialized const bindings (temporal dead zone).
  const v = transform(+el.value);
  if (lbl) lbl.textContent = typeof v === 'number' ? formatNum(v) : v;
  return () => transform(+el.value);
}

function formatNum(v) {
  if (v >= 100) return v.toFixed(0);
  if (v >= 1) return v.toFixed(1);
  return v.toFixed(3);
}

const getLR = bindSlider(
  'lr',
  'lrVal',
  (v) => v / 1000,
  () => rebuildOptimizer()
);
const getLambdaDih = bindSlider('lambdaDih', 'lambdaDihVal', (v) => v / 10);
const getLambdaRep = bindSlider('lambdaRep', 'lambdaRepVal', (v) => v / 100);
const getRetri = bindSlider('retri', 'retriVal', (v) => v | 0);
const getClip = bindSlider('clip', 'clipVal', (v) => v / 10);
bindSlider(
  'numPoints',
  'numPointsVal',
  (v) => v | 0,
  (v) => {
    state.k = v;
    resetPoints();
  }
);

$('manifold').addEventListener('change', (e) => {
  state.manifoldKey = e.target.value;
  resetPoints();
});
$('functional').addEventListener('change', () => {});
$('direction').addEventListener('change', () => {});

$('run').addEventListener('click', () => {
  state.running = true;
  loop();
});
$('pause').addEventListener('click', () => {
  state.running = false;
});
$('reset').addEventListener('click', () => {
  resetPoints();
});

// ---- optimizer ----------------------------------------------------------
function rebuildOptimizer() {
  if (state.optimizer) state.optimizer = null;
  state.optimizer = tf.train.adam(getLR());
}

// ---- point lifecycle ----------------------------------------------------
function resetPoints() {
  state.step = 0;
  const M = MANIFOLDS[state.manifoldKey];
  const seed = M.seed(state.k);
  if (state.points) state.points.dispose();
  state.points = tf.variable(tf.tensor2d(seed, [state.k, M.paramDim]), true);
  rebuildOptimizer();
  retriangulate();
}

// Project world coords to a 2D plane for Delaunay connectivity.
function projectFor2D(coords3, M) {
  const k = coords3.length / 3;
  const pts = new Array(k);
  if (M.is2D) {
    for (let i = 0; i < k; i++) pts[i] = [coords3[i * 3], coords3[i * 3 + 1]];
  } else if (state.manifoldKey === 'sphere') {
    // stereographic-ish: use (x,y)/(1.5 - z)
    for (let i = 0; i < k; i++) {
      const z = coords3[i * 3 + 2];
      const denom = 1.6 - z;
      pts[i] = [coords3[i * 3] / denom, coords3[i * 3 + 1] / denom];
    }
  } else {
    // use the stored parameters (u,v) directly for torus/saddle
    for (let i = 0; i < k; i++) pts[i] = [coords3[i * 3], coords3[i * 3 + 1]];
  }
  return pts;
}

function retriangulate() {
  const M = MANIFOLDS[state.manifoldKey];
  tf.tidy(() => {
    const coords = M.embed(state.points);
    const flat = coords.dataSync();
    let pts2d;
    if (M.is2D || state.manifoldKey === 'sphere') {
      pts2d = projectFor2D(flat, M);
    } else {
      // torus/saddle: triangulate in parameter space
      const params = state.points.dataSync();
      const k = state.k;
      pts2d = new Array(k);
      for (let i = 0; i < k; i++) pts2d[i] = [params[i * M.paramDim], params[i * M.paramDim + 1]];
    }
    const { triangles, edges } = delaunay2D(pts2d);
    state.tri = triangles;
    state.edges = edges;
  });

  // build tensors
  if (state.triTensor) state.triTensor.dispose();
  if (state.edgeT1) state.edgeT1.dispose();
  if (state.edgeT2) state.edgeT2.dispose();

  if (state.tri.length && state.edges.length) {
    const triFlat = [];
    for (const t of state.tri) triFlat.push(t[0], t[1], t[2]);
    state.triTensor = tf.tensor2d(triFlat, [state.tri.length, 3], 'int32');
    state.edgeT1 = tf.tensor1d(
      state.edges.map((e) => e.t1),
      'int32'
    );
    state.edgeT2 = tf.tensor1d(
      state.edges.map((e) => e.t2),
      'int32'
    );
  } else {
    state.triTensor = null;
    state.edgeT1 = null;
    state.edgeT2 = null;
  }
}

// ---- one optimization step ---------------------------------------------
function trainStep() {
  const M = MANIFOLDS[state.manifoldKey];
  if (!state.triTensor) return;

  const opts = {
    functional: $('functional').value,
    direction: $('direction').value,
    lambdaDih: getLambdaDih(),
  };
  const lambdaRep = getLambdaRep();
  const clipNorm = getClip();

  let energyVal = 0;

  const lossFn = () => {
    const coords = M.embed(state.points);
    let e = dihedralEnergy(coords, state.triTensor, state.edgeT1, state.edgeT2, opts);
    if (lambdaRep > 0) e = e.add(repulsionEnergy(coords, lambdaRep));
    return e;
  };

  // gradient + clipping
  const { value, grads } = tf.variableGrads(lossFn, [state.points]);
  energyVal = value.dataSync()[0];
  value.dispose();

  // clip by global norm
  tf.tidy(() => {
    const g = grads[state.points.name];
    const gnorm = g.norm().dataSync()[0];
    let scale = 1;
    if (gnorm > clipNorm && gnorm > 0) scale = clipNorm / gnorm;
    const clipped = g.mul(scale);
    state.optimizer.applyGradients({ [state.points.name]: clipped });
  });
  for (const k in grads) grads[k].dispose();

  state.lastEnergy = energyVal;
  state.step++;
}

// ---- render + HUD -------------------------------------------------------
async function renderFrame() {
  const M = MANIFOLDS[state.manifoldKey];
  const coords = M.embed(state.points);
  const flat = await coords.data();

  let phiArr = null;
  if (state.triTensor) {
    phiArr = await readDihedrals(coords, state.triTensor, state.edgeT1, state.edgeT2);
  }
  coords.dispose();

  const opts = {
    is2D: M.is2D,
    showMesh: $('showMesh').checked,
    showPoints: $('showPoints').checked,
    colorDih: $('colorDih').checked,
  };
  state.renderer.draw(flat, state.tri, state.edges, phiArr, opts);

  // HUD stats
  let phiStats = '';
  if (phiArr && phiArr.length) {
    let mn = Infinity,
      mx = -Infinity,
      sum = 0;
    for (const p of phiArr) {
      mn = Math.min(mn, p);
      mx = Math.max(mx, p);
      sum += p;
    }
    const mean = sum / phiArr.length;
    phiStats =
      `φ mean ${((mean * 180) / Math.PI).toFixed(1)}°  ` +
      `[${((mn * 180) / Math.PI).toFixed(1)}°, ${((mx * 180) / Math.PI).toFixed(1)}°]`;
  }
  $('hud').textContent =
    `step ${state.step}\n` +
    `energy ${state.lastEnergy.toExponential(3)}\n` +
    `edges ${state.edges.length}  tris ${state.tri.length}\n` +
    phiStats +
    `\ntensors ${tf.memory().numTensors}`;
}

// ---- main loop ----------------------------------------------------------
async function loop() {
  if (!state.running) {
    await renderFrame();
    // keep rendering while the user is interacting with a 3D view
    if (state.renderer && state.renderer._drag) {
      requestAnimationFrame(loop);
    }
    return;
  }
  // several optimization steps per frame
  for (let i = 0; i < 3; i++) {
    if (state.step % getRetri() === 0 && state.step > 0) retriangulate();
    trainStep();
  }
  await renderFrame();
  requestAnimationFrame(loop);
}

// ---- boot ---------------------------------------------------------------
async function boot() {
  await tf.ready();
  state.renderer = new Renderer($('view'));
  // re-render on 3D camera interaction while paused
  const kick = () => {
    if (!state.running) renderFrame();
  };
  $('view').addEventListener('mousedown', kick);
  window.addEventListener('mousemove', () => {
    if (!state.running && state.renderer._drag) renderFrame();
  });
  $('view').addEventListener('wheel', kick, { passive: false });
  resetPoints();
  await renderFrame();
}

boot();
