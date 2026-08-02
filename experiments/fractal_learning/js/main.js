// Main lab entry point — wires UI, model, optimizer, and view together.

import { IFSModel } from './ifs-model.js';
import { enumerate } from './enumeration.js';
import { makeLossFn } from './loss.js';
import { OptimizerAdam } from './optimizer-adam.js';
import { OptimizerLbfgs } from './optimizer-lbfgs.js';
import { OptimizerQQN } from './optimizer-qqn.js';
import { PRESETS, presetCircle } from './presets.js';
import { View } from './view.js';
import { TransformsPanel } from './ui-transforms.js';
import { commutativeOrbitSize } from './orbit-commutative.js';

const $ = (id) => document.getElementById(id);

// ---------- TF backend ----------
async function initTF() {
  try {
    await tf.setBackend('webgl');
    await tf.ready();
  } catch {
    await tf.setBackend('cpu');
    await tf.ready();
  }
  $('backendInfo').textContent = `tf.js backend: ${tf.getBackend()}`;
}

// ---------- App state ----------
const state = {
  K: 2,
  N: 7,
  enumeration: 'commutative',
  // `targetSource` is the user-provided target (drawn or preset). `target` is
  // a resampled version of length == orbit size, which is what the loss and
  // renderer actually consume. Keeping the source lets us re-resample when N
  // or K changes the orbit size.
  targetSource: presetCircle(150),
  target: presetCircle(150),
  model: null,
  optimizer: null,
  optimizerName: 'adam',
  words: null,
  wordsKey: '',
  iter: 0,
  lastLoss: null,
  lossHistory: [],
  running: false,
  lossFn: null,
  lossHparamSig: '',
};

const view = new View($('view'), $('lossCanvas'));
let panel = null;
// ---------- target resampling ----------
// Resample `src` to exactly `n` points. Deterministic given (src, n):
//   - n == 0       -> []
//   - n <= len     -> evenly spaced subsample
//   - n  > len     -> repeat with even spacing (wrap-around)
// This preserves shape/order without random jitter.
function resampleTo(src, n) {
  if (n <= 0 || src.length === 0) return [];
  const out = new Array(n);
  for (let i = 0; i < n; i++) {
    const idx = Math.floor((i * src.length) / n) % src.length;
    const p = src[idx];
    out[i] = [p[0], p[1]];
  }
  return out;
}
function rebuildResampledTarget() {
  const want = state.orbitSize || 0;
  state.target = resampleTo(state.targetSource, want);
  invalidateLossFn();
}

// ---------- words cache ----------
function getWords() {
  const key = `${state.K}|${state.N}|${state.enumeration}`;
  if (key !== state.wordsKey) {
    // Fast path: commutative enumeration uses the binary-power DP
    // (algo.md). We don't need the explicit word list at all; we mark
    // state.words = null and just track the orbit size for the HUD.
    if (state.enumeration === 'commutative') {
      state.words = null;
      state.orbitSize = commutativeOrbitSize(state.K, state.N);
    } else {
      state.words = enumerate(state.K, state.N, state.enumeration);
      state.orbitSize = state.words.length;
    }
    state.wordsKey = key;
    // Orbit size changed -> resample target to match, which also invalidates
    // the loss closure (since Q changes).
    rebuildResampledTarget();
  }
  return state.words;
}

function readHparams() {
  return {
    alpha: parseFloat($('alpha').value) || 0,
    beta: parseFloat($('beta').value) || 0,
    lamA: parseFloat($('lamA').value) || 0,
    lamb: parseFloat($('lamb').value) || 0,
    lamC: parseFloat($('lamC').value) || 0,
    eps: parseFloat($('eps').value) || 0,
    N: state.N,
  };
}

function invalidateLossFn() {
  if (state.lossFn) state.lossFn.dispose?.();
  state.lossFn = null;
}

function ensureLossFn() {
  const hp = readHparams();
  const sig = `${state.target.length}|${state.wordsKey}|${state.N}|${JSON.stringify(hp)}`;
  if (state.lossFn && sig === state.lossHparamSig) return state.lossFn;
  invalidateLossFn();
  const words = getWords();
  state.lossFn = makeLossFn(state.model, state.target, words, hp);
  state.lossHparamSig = sig;
  return state.lossFn;
}

// ---------- optimizer ----------
function buildOptimizer() {
  if (state.optimizer) state.optimizer.dispose?.();
  const lr = parseFloat($('lr').value) || 0.02;
  const name = $('optim').value;
  state.optimizerName = name;
  if (name === 'adam') state.optimizer = new OptimizerAdam(lr);
  else if (name === 'lbfgs') {
    state.optimizer = new OptimizerLbfgs();
    state.optimizer.setLearningRate(lr);
  } else if (name === 'qqn') {
    state.optimizer = new OptimizerQQN();
    state.optimizer.setLearningRate(lr);
  }
}

// ---------- forward/render only ----------
function forwardRender() {
  const words = getWords();
  const orbitT =
    words === null ? state.model.computeCommutativeOrbit(state.N) : state.model.computeOrbit(words);
  const orbit = orbitT.arraySync();
  orbitT.dispose();
  return orbit;
}

// ---------- training step ----------
function trainStep() {
  const lossFn = ensureLossFn();
  const active = state.model.activeVariables();
  const v = state.optimizer.step(lossFn, active);
  state.lastLoss = v;
  state.iter++;
  state.lossHistory.push(v);
  if (state.lossHistory.length > 2000) state.lossHistory.shift();
}

// ---------- draw ----------
function draw() {
  const orbit = forwardRender();
  view.drawGrid();
  const dpr = window.devicePixelRatio || 1;
  view.drawPoints(state.target, '#3fb950', 2.0 * dpr);
  view.drawPoints(orbit, '#58a6ff', 2.6 * dpr);
  view.drawFixedPoints(state.model.readTransforms());
  view.drawLossCurve(state.lossHistory);
  updateHUD(orbit.length);
}

function updateHUD(orbitN) {
  const words = getWords();
  const orbSize = words === null ? state.orbitSize : words.length;
  $('targCount').textContent = state.target.length;
  $('orbitCount').textContent = orbitN ?? orbSize;
  $('iterVal').textContent = state.iter;
  $('lossVal').textContent = state.lastLoss == null ? '—' : state.lastLoss.toExponential(3);
  $('activeCount').textContent = state.model.activeCount();
  $('totalCount').textContent = state.model.K;
  $('orbitSize').textContent = `orbit size: ${orbSize} words${words === null ? ' (DP)' : ''}`;
}

// ---------- UI wiring ----------
function rebuildModel(initFn = 'small') {
  const newK = Math.max(1, Math.min(6, parseInt($('K').value) || 2));
  state.K = newK;
  state.N = Math.max(1, Math.min(12, parseInt($('N').value) || 5));
  state.enumeration = $('enum').value;
  const seed = parseInt($('seed').value) || 1;

  if (state.model) state.model.dispose();
  state.model = new IFSModel(newK, seed);

  if (initFn === 'small') state.model.initSmall(seed);
  else if (initFn === 'rot') state.model.initRotations(seed);
  else if (initFn === 'shrink') state.model.initContractions();
  else if (initFn === 'sierp') {
    state.model.initSierpinski3();
    state.K = state.model.K;
    $('K').value = state.K;
  } else if (initFn === 'barnsley') {
    state.model.initBarnsley();
    state.K = state.model.K;
    $('K').value = state.K;
  }

  state.iter = 0;
  state.lossHistory = [];
  state.lastLoss = null;
  state.wordsKey = '';
  invalidateLossFn();
  buildOptimizer();

  panel = new TransformsPanel($('transforms'), state.model, {
    onChange: () => {
      draw();
      panel.render();
    },
  });
  panel.render();
  draw();
}

function afterParamChange() {
  // K change requires rebuild; otherwise just refresh.
  invalidateLossFn();
  panel?.render();
  draw();
}

function bindUI() {
  $('K').addEventListener('change', () => rebuildModel('small'));
  $('N').addEventListener('change', () => {
    state.N = Math.max(1, Math.min(12, parseInt($('N').value) || 5));
    state.wordsKey = '';
    afterParamChange();
  });
  $('enum').addEventListener('change', () => {
    state.enumeration = $('enum').value;
    state.wordsKey = '';
    afterParamChange();
  });

  $('initSmall').addEventListener('click', () => rebuildModel('small'));
  $('initRot').addEventListener('click', () => rebuildModel('rot'));
  $('initShrink').addEventListener('click', () => rebuildModel('shrink'));
  $('initSierp').addEventListener('click', () => rebuildModel('sierp'));
  $('initBarnsley').addEventListener('click', () => rebuildModel('barnsley'));
  $('reset').addEventListener('click', () => rebuildModel('small'));

  $('optim').addEventListener('change', () => buildOptimizer());
  $('lr').addEventListener('change', () => {
    const lr = parseFloat($('lr').value) || 0.02;
    if (state.optimizer?.setLearningRate) state.optimizer.setLearningRate(lr);
  });

  // hparams trigger lossFn rebuild
  ['alpha', 'beta', 'lamA', 'lamb', 'lamC', 'eps'].forEach((id) => {
    $(id).addEventListener('change', () => invalidateLossFn());
  });

  $('step').addEventListener('click', () => {
    const steps = parseInt($('steps').value) || 1;
    for (let i = 0; i < steps; i++) trainStep();
    draw();
    panel.render();
  });

  $('run').addEventListener('click', () => {
    state.running = !state.running;
    $('run').textContent = state.running ? '⏸ Pause' : '▶ Run';
    if (state.running) loop();
  });

  // Bulk freeze
  $('freezeAll').addEventListener('click', () => {
    state.model.freezeAll(true);
    panel.render();
    draw();
  });
  $('unfreezeAll').addEventListener('click', () => {
    state.model.freezeAll(false);
    panel.render();
    draw();
  });
  $('invertFreeze').addEventListener('click', () => {
    state.model.invertFreeze();
    panel.render();
    draw();
  });

  // Target
  $('targetClear').addEventListener('click', () => {
    state.targetSource = [];
    state.target = [];
    invalidateLossFn();
    draw();
  });
  $('loadPreset').addEventListener('click', () => {
    const v = $('targetPreset').value;
    if (!v) return;
    state.targetSource = PRESETS[v]();
    rebuildResampledTarget();
    draw();
  });

  // Drawing target on canvas
  let drawing = false;
  $('view').addEventListener('mousedown', (e) => {
    if (e.button === 2) return;
    drawing = true;
    addTargetAt(e);
  });
  $('view').addEventListener('mousemove', (e) => {
    if (drawing) addTargetAt(e);
  });
  window.addEventListener('mouseup', () => {
    drawing = false;
  });
  function addTargetAt(e) {
    const rect = $('view').getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const sx = (e.clientX - rect.left) * dpr;
    const sy = (e.clientY - rect.top) * dpr;
    const [wx, wy] = view.s2w(sx, sy);
    const src = state.targetSource;
    const last = src[src.length - 1];
    if (last) {
      const d = Math.hypot(wx - last[0], wy - last[1]);
      if (d < 0.015) return;
    }
    src.push([wx, wy]);
    rebuildResampledTarget();
    draw();
  }

  // Zoom / pan
  $('view').addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      const rect = $('view').getBoundingClientRect();
      const dpr = window.devicePixelRatio || 1;
      const sx = (e.clientX - rect.left) * dpr;
      const sy = (e.clientY - rect.top) * dpr;
      const [wx, wy] = view.s2w(sx, sy);
      const factor = Math.exp(-e.deltaY * 0.001);
      view.world.scale *= factor;
      const [nwx, nwy] = view.s2w(sx, sy);
      view.world.cx += wx - nwx;
      view.world.cy += wy - nwy;
      draw();
    },
    { passive: false }
  );

  let panning = false,
    panLast = null;
  $('view').addEventListener('contextmenu', (e) => e.preventDefault());
  $('view').addEventListener('mousedown', (e) => {
    if (e.button === 2) {
      panning = true;
      panLast = [e.clientX, e.clientY];
    }
  });
  window.addEventListener('mousemove', (e) => {
    if (panning) {
      const dx = e.clientX - panLast[0];
      const dy = e.clientY - panLast[1];
      const dpr = window.devicePixelRatio || 1;
      view.world.cx -= (dx * dpr) / view.world.scale;
      view.world.cy += (dy * dpr) / view.world.scale;
      panLast = [e.clientX, e.clientY];
      draw();
    }
  });
  window.addEventListener('mouseup', (e) => {
    if (e.button === 2) panning = false;
  });

  window.addEventListener('keydown', (e) => {
    if (e.code === 'Space' && e.target.tagName !== 'INPUT') {
      e.preventDefault();
      $('step').click();
    }
  });
  window.addEventListener('resize', () => {
    view.resize();
    draw();
  });
}

function loop() {
  if (!state.running) return;
  const fps = parseFloat($('fps').value) || 60;
  const minDt = 1000 / fps;
  const now = performance.now();
  if (!loop._last) loop._last = 0;
  if (now - loop._last >= minDt) {
    const steps = parseInt($('steps').value) || 1;
    for (let i = 0; i < steps; i++) trainStep();
    draw();
    panel.render();
    loop._last = now;
  }
  requestAnimationFrame(loop);
}

// ---------- boot ----------
(async () => {
  await initTF();
  bindUI();
  $('targetPreset').value = 'circle';
  rebuildModel('small');
})();
