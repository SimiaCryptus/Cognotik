// Bootstrap, wiring, animation loop.
import { AppState } from './app-state.js';
import { Objective } from './objective.js';
import { Camera, setupCanvasDPR } from './camera.js';
import { Stats } from './stats.js';
import { createOptimizer } from './optimizers/optimizer.js';
import { LossRenderer } from './renderer/loss-renderer.js';
import { OverlayRenderer } from './renderer/overlay-renderer.js';
import { PathRenderer } from './renderer/path-renderer.js';
import { StatsRenderer } from './renderer/stats-renderer.js';
import { bindControls, updateReadout } from './ui/controls.js';

const state = new AppState();
const camera = new Camera();
let objective = new Objective(state.config);
let optimizer;
const stats = new Stats(80);

let pathHistory = [];
let lastResult = null;
let playing = false;
let playAccumulator = 0;
let lastTime = 0;

// canvas contexts
const canvases = {
  loss: document.getElementById('loss-layer'),
  overlay: document.getElementById('overlay-layer'),
  path: document.getElementById('path-layer'),
  stats: document.getElementById('stats-layer'),
  ui: document.getElementById('ui-layer'),
};
let ctxs = {};
let lossR, overlayR, pathR, statsR;

function initRenderers() {
  ctxs.loss = setupCanvasDPR(canvases.loss, camera);
  ctxs.overlay = setupCanvasDPR(canvases.overlay, camera);
  ctxs.path = setupCanvasDPR(canvases.path, camera);
  ctxs.stats = setupCanvasDPR(canvases.stats, camera);
  ctxs.ui = setupCanvasDPR(canvases.ui, camera);
  lossR = new LossRenderer(ctxs.loss, camera);
  overlayR = new OverlayRenderer(ctxs.overlay, camera);
  pathR = new PathRenderer(ctxs.path, camera);
  statsR = new StatsRenderer(ctxs.stats, camera);
}

function resize() {
  const stage = document.getElementById('stage');
  camera.resize(stage.clientWidth, stage.clientHeight);
  initRenderers();
  state.markAllDirty();
}

function rebuildOptimizer() {
  const name = state.config.optimizer;
  const extra = (state.config.optParams && state.config.optParams[name]) || {};
  console.debug('[main] rebuilding optimizer', name, extra);
  optimizer = createOptimizer(name, {
    lr: state.config.lr,
    ...extra,
  });
  reset();
}

function reset() {
  const s = state.config.start;
  optimizer.reset(s.x, s.y);
  stats.reset();
  pathHistory = [[s.x, s.y]];
  lastResult = null;
  camera.centerOn(s.x, s.y);
  state.markAllDirty();
  updateReadout(`(${s.x.toFixed(2)}, ${s.y.toFixed(2)})`);
}

function doStep() {
  if (!optimizer) {
    console.warn('[main] doStep called before optimizer initialized');
    return;
  }
  let res;
  try {
    res = optimizer.step(objective);
  } catch (err) {
    console.error('[main] optimizer.step threw; pausing playback', err);
    playing = false;
    return;
  }
  if (!res || !res.to || !Number.isFinite(res.to[0]) || !Number.isFinite(res.to[1])) {
    console.warn('[main] optimizer produced invalid step; pausing', res);
    playing = false;
    return;
  }
  lastResult = res;
  stats.update(res.grad);
  pathHistory.push(res.to.slice());
  if (pathHistory.length > 2000) pathHistory.shift();

  if (state.config.autofollow) {
    camera.centerOn(res.to[0], res.to[1]);
    state.markDirty('loss', 'overlay');
  }
  state.markDirty('path', 'stats', 'ui');

  const loss = objective.value(res.to[0], res.to[1]);
  updateReadout(
    `pos(${res.to[0].toFixed(2)}, ${res.to[1].toFixed(2)}) ` +
      `L=${loss.toFixed(3)} |g|=${Math.hypot(res.grad[0], res.grad[1]).toFixed(3)}` +
      (res.chosenT !== undefined ? ` t=${res.chosenT.toFixed(3)}` : '')
  );
}

function togglePlay() {
  playing = !playing;
  document.getElementById('btn-play').textContent = playing ? 'Pause' : 'Play';
  document.getElementById('btn-play').classList.toggle('active', playing);
}

function loop(time) {
  const dt = (time - lastTime) / 1000;
  lastTime = time;

  if (playing) {
    playAccumulator += dt * state.config.speed;
    while (playAccumulator >= 1) {
      doStep();
      playAccumulator -= 1;
    }
  }

  // dirty-flag based redraws
  if (state.dirty.loss) {
    objective.config = state.config;
    lossR.render(objective, state.config);
    state.clean('loss');
  }
  if (state.dirty.overlay) {
    overlayR.render(state.config);
    state.clean('overlay');
  }
  if (state.dirty.path) {
    pathR.render(pathHistory, lastResult, state.config, objective);
    state.clean('path');
  }
  if (state.dirty.stats) {
    statsR.render(stats);
    state.clean('stats');
  }
  if (state.dirty.ui) {
    state.clean('ui');
  }

  requestAnimationFrame(loop);
}

// mouse interaction: pan + zoom + set start
let dragging = false,
  lastMouse = null;

function setupInteraction() {
  const top = canvases.ui;
  top.addEventListener('mousedown', (e) => {
    dragging = true;
    lastMouse = [e.offsetX, e.offsetY];
  });
  window.addEventListener('mouseup', () => (dragging = false));
  window.addEventListener('mousemove', (e) => {
    if (!dragging) return;
    const rect = canvases.ui.getBoundingClientRect();
    const mx = e.clientX - rect.left,
      my = e.clientY - rect.top;
    const dx = mx - lastMouse[0],
      dy = my - lastMouse[1];
    camera.cx -= dx / camera.scale;
    camera.cy += dy / camera.scale;
    lastMouse = [mx, my];
    state.set('autofollow', false);
    document.getElementById('autofollow').checked = false;
    state.markDirty('loss', 'overlay', 'path', 'stats');
  });
  top.addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      camera.zoomBy(e.deltaY < 0 ? 1.1 : 0.9);
      state.markDirty('loss', 'overlay', 'path', 'stats');
    },
    { passive: false }
  );
  top.addEventListener('dblclick', (e) => {
    const rect = canvases.ui.getBoundingClientRect();
    const [wx, wy] = camera.screenToWorld(e.clientX - rect.left, e.clientY - rect.top);
    state.config.start = { x: wx, y: wy };
    reset();
  });
}

// init
window.addEventListener('resize', resize);
bindControls(state, {
  step: doStep,
  togglePlay,
  reset,
  rebuildOptimizer,
});
resize();
rebuildOptimizer();
setupInteraction();
requestAnimationFrame((t) => {
  lastTime = t;
  loop(t);
});
