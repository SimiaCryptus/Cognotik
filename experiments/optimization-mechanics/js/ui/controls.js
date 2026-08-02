// Build & bind DOM controls to app-state.
import { BASE_FIELDS } from '../objective.js';

export function bindControls(state, actions) {
  const $ = (id) => document.getElementById(id);

  $('viz-mode').addEventListener('change', (e) => {
    state.set('vizMode', e.target.value);
    state.markDirty('loss');
  });
  $('color-scheme').addEventListener('change', (e) => {
    state.set('colorScheme', e.target.value);
    state.markDirty('loss');
  });
  const vs = $('value-scaling');
  if (vs)
    vs.addEventListener('change', (e) => {
      state.set('valueScaling', e.target.value);
      state.markDirty('loss');
    });
  $('autofollow').addEventListener('change', (e) => {
    state.set('autofollow', e.target.checked);
  });
  $('show-grad').addEventListener('change', (e) => {
    state.set('showGrad', e.target.checked);
    state.markDirty('path');
  });
  $('speed').addEventListener('input', (e) => {
    state.set('speed', parseInt(e.target.value));
  });

  $('btn-step').addEventListener('click', () => actions.step());
  $('btn-play').addEventListener('click', () => actions.togglePlay());
  $('btn-reset').addEventListener('click', () => actions.reset());
  // config clipboard (copy/paste JSON)
  const copyBtn = $('btn-copy-config');
  if (copyBtn) copyBtn.addEventListener('click', () => copyConfig(state));
  const pasteBtn = $('btn-paste-config');
  if (pasteBtn) pasteBtn.addEventListener('click', () => pasteConfig(state, actions));

  // side panel tabs
  state._activeTab = state._activeTab || 'base';
  document.querySelectorAll('.panel-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document
        .querySelectorAll('.panel-tab')
        .forEach((t) => t.classList.toggle('active', t === tab));
      state._activeTab = tab.dataset.tab;
      buildPanel(state, actions);
    });
  });

  buildPanel(state, actions);
}

// -- Panel builder --------------------------------------------------------

function buildPanel(state, actions) {
  const body = document.getElementById('panel-body');
  body.innerHTML = '';
  const tab = state._activeTab || 'base';
  if (tab === 'base') buildBaseTab(body, state, actions);
  else if (tab === 'noise') buildNoiseTab(body, state, actions);
  else if (tab === 'lattice') buildLatticeTab(body, state, actions);
  else if (tab === 'optimizer') buildOptimizerTab(body, state, actions);
}

function buildBaseTab(body, state, actions) {
  const c = state.config;
  // Global scaling controls
  const gsec = section(body, 'Global Scale');
  slider(gsec, state, actions, 'global.sx', 'X Scale (field)', 0.1, 5, 0.05, ['loss', 'path']);
  slider(gsec, state, actions, 'global.sy', 'Y Scale (field)', 0.1, 5, 0.05, ['loss', 'path']);
  slider(gsec, state, actions, 'global.sz', 'Z Amplitude', 0.1, 5, 0.05, ['loss', 'path']);

  const sec = section(body, 'Base Field');

  // Base selector duplicated here for convenience
  const sel = document.createElement('div');
  sel.className = 'field';
  sel.innerHTML = `
            <div class="field-label"><span>Type</span></div>
        `;
  const dd = document.createElement('select');
  dd.innerHTML = Object.entries(BASE_FIELDS)
    .map(([key, def]) => `<option value="${key}">${def.label}</option>`)
    .join('');
  dd.value = c.base;
  dd.addEventListener('change', (e) => {
    state.set('base', e.target.value);
    state.markDirty('loss', 'overlay');
    buildPanel(state, actions);
  });
  sel.appendChild(dd);
  sec.appendChild(sel);

  if (c.base === 'bowl') {
    slider(sec, state, actions, 'bowl.kx', 'Curvature X (kx)', 0, 0.2, 0.001, ['loss', 'overlay']);
    slider(sec, state, actions, 'bowl.ky', 'Curvature Y (ky)', 0, 0.2, 0.001, ['loss', 'overlay']);
    slider(sec, state, actions, 'bowl.cx', 'Center X (cx)', -50, 50, 0.5, ['loss', 'overlay']);
    slider(sec, state, actions, 'bowl.cy', 'Center Y (cy)', -50, 50, 0.5, ['loss', 'overlay']);
  } else if (c.base === 'linear') {
    slider(sec, state, actions, 'linear.a', 'Gradient A (x)', -5, 5, 0.05, ['loss', 'overlay']);
    slider(sec, state, actions, 'linear.b', 'Gradient B (y)', -5, 5, 0.05, ['loss', 'overlay']);
  } else {
    // closed-form analytic landscape: expose amplitude scale
    slider(sec, state, actions, 'analytic.scale', 'Amplitude Scale', 0.01, 5, 0.01, [
      'loss',
      'overlay',
    ]);
  }

  const startSec = section(body, 'Start Point');
  slider(startSec, state, actions, 'start.x', 'Start X', -50, 50, 0.5, [], () => actions.reset());
  slider(startSec, state, actions, 'start.y', 'Start Y', -50, 50, 0.5, [], () => actions.reset());
}

function buildNoiseTab(body, state, actions) {
  const c = state.config;
  const sec = section(body, 'High-Frequency Noise');
  toggle(sec, state, actions, 'noiseOn', 'Enable Noise', ['loss', 'path']);

  if (c.noiseOn) {
    slider(sec, state, actions, 'noise.amp', 'Amplitude', 0, 20, 0.1, ['loss', 'path']);
    slider(sec, state, actions, 'noise.fx', 'Frequency X', 0.01, 2, 0.01, ['loss', 'path']);
    slider(sec, state, actions, 'noise.fy', 'Frequency Y', 0.01, 2, 0.01, ['loss', 'path']);
    seedField(sec, state, actions, 'noise.seed', 'Seed', ['loss', 'path']);
  }
}

function buildLatticeTab(body, state, actions) {
  const c = state.config;
  const sec = section(body, 'Confounding Lattice');
  toggle(sec, state, actions, 'latticeOn', 'Enable Lattice', ['loss', 'overlay', 'path']);

  if (c.latticeOn) {
    slider(sec, state, actions, 'lattice.L', 'Cell Size (L)', 1, 30, 0.5, [
      'loss',
      'overlay',
      'path',
    ]);
    slider(sec, state, actions, 'lattice.f', 'Fill Fraction (f)', 0.05, 0.95, 0.01, [
      'loss',
      'overlay',
      'path',
    ]);
    slider(sec, state, actions, 'lattice.offset', 'Region Offset Amp', 0, 3, 0.01, [
      'loss',
      'overlay',
      'path',
    ]);
    slider(sec, state, actions, 'lattice.cgx', 'Confound Grad X', -5, 5, 0.05, [
      'loss',
      'overlay',
      'path',
    ]);
    slider(sec, state, actions, 'lattice.cgy', 'Confound Grad Y', -5, 5, 0.05, [
      'loss',
      'overlay',
      'path',
    ]);
    seedField(sec, state, actions, 'lattice.seed', 'Seed', ['loss', 'overlay', 'path']);
  }
}
function buildOptimizerTab(body, state, actions) {
  const c = state.config;
  const sec = section(body, 'Optimizer');
  // optimizer selector (moved from top bar to the right panel)
  const sel = document.createElement('div');
  sel.className = 'field';
  sel.innerHTML = `<div class="field-label"><span>Algorithm</span></div>`;
  const dd = document.createElement('select');
  dd.innerHTML = `
             <option value="gd">Gradient Descent</option>
             <option value="adam">Adam</option>
             <option value="lbfgs">L-BFGS</option>
             <option value="qqn">QQN</option>
         `;
  dd.value = c.optimizer;
  dd.addEventListener('change', (e) => {
    state.set('optimizer', e.target.value);
    actions.rebuildOptimizer();
    buildPanel(state, actions);
  });
  sel.appendChild(dd);
  sec.appendChild(sel);
  // shared learning rate
  slider(sec, state, actions, 'lr', 'Learning Rate', 0.001, 2, 0.001, [], () =>
    actions.rebuildOptimizer()
  );
  // per-optimizer params
  const name = c.optimizer;
  const psec = section(body, 'Parameters');
  if (name === 'adam') {
    slider(psec, state, actions, 'optParams.adam.b1', 'Beta 1 (β₁)', 0, 0.999, 0.001, [], () =>
      actions.rebuildOptimizer()
    );
    slider(psec, state, actions, 'optParams.adam.b2', 'Beta 2 (β₂)', 0.9, 0.99999, 0.0001, [], () =>
      actions.rebuildOptimizer()
    );
    slider(psec, state, actions, 'optParams.adam.eps', 'Epsilon (ε)', 1e-8, 1e-3, 1e-8, [], () =>
      actions.rebuildOptimizer()
    );
  } else if (name === 'lbfgs') {
    slider(psec, state, actions, 'optParams.lbfgs.m', 'History (m)', 1, 20, 1, [], () =>
      actions.rebuildOptimizer()
    );
  } else if (name === 'qqn') {
    slider(psec, state, actions, 'optParams.qqn.m', 'History (m)', 1, 20, 1, [], () =>
      actions.rebuildOptimizer()
    );
    dropdown(
      psec,
      state,
      actions,
      'optParams.qqn.oracle',
      'Oracle',
      [
        ['lbfgs', 'L-BFGS'],
        ['momentum', 'Momentum'],
        ['gradient', 'Gradient (degenerate)'],
      ],
      () => actions.rebuildOptimizer()
    );
    if (state.config.optParams.qqn.oracle === 'momentum') {
      slider(
        psec,
        state,
        actions,
        'optParams.qqn.momentumBeta',
        'Momentum β',
        0,
        0.999,
        0.001,
        [],
        () => actions.rebuildOptimizer()
      );
    }
    dropdown(
      psec,
      state,
      actions,
      'optParams.qqn.lineSearch',
      'Line Search',
      [
        ['golden', 'Golden Section'],
        ['backtracking', 'Backtracking'],
      ],
      () => actions.rebuildOptimizer()
    );
    slider(psec, state, actions, 'optParams.qqn.maxLineSearch', 'Max LS Iters', 1, 100, 1, [], () =>
      actions.rebuildOptimizer()
    );
  } else {
    const note = document.createElement('div');
    note.className = 'field';
    note.innerHTML = `<div class="field-label"><span>No extra parameters</span></div>`;
    psec.appendChild(note);
  }
}

// -- Widget helpers -------------------------------------------------------

function section(parent, title) {
  const s = document.createElement('div');
  s.className = 'panel-section';
  const h = document.createElement('h3');
  h.textContent = title;
  s.appendChild(h);
  parent.appendChild(s);
  return s;
}

// read/write dotted config path e.g. "noise.amp"
function getPath(config, path) {
  const parts = path.split('.');
  let o = config;
  for (let i = 0; i < parts.length - 1; i++) o = o[parts[i]];
  return o[parts[parts.length - 1]];
}

function setPath(config, path, value) {
  const parts = path.split('.');
  let o = config;
  for (let i = 0; i < parts.length - 1; i++) o = o[parts[i]];
  o[parts[parts.length - 1]] = value;
}

function slider(parent, state, actions, path, label, min, max, step, dirty, onChange) {
  const val = getPath(state.config, path);
  const field = document.createElement('div');
  field.className = 'field';

  const lab = document.createElement('div');
  lab.className = 'field-label';
  const nameSpan = document.createElement('span');
  nameSpan.textContent = label;
  const valSpan = document.createElement('span');
  valSpan.className = 'field-value';
  valSpan.textContent = fmt(val);
  lab.appendChild(nameSpan);
  lab.appendChild(valSpan);

  const input = document.createElement('input');
  input.type = 'range';
  input.min = min;
  input.max = max;
  input.step = step;
  input.value = val;

  input.addEventListener('input', (e) => {
    const v = parseFloat(e.target.value);
    setPath(state.config, path, v);
    valSpan.textContent = fmt(v);
    state.emit({ type: 'config', key: path, value: v });
    if (dirty && dirty.length) state.markDirty(...dirty);
    if (onChange) onChange();
  });

  field.appendChild(lab);
  field.appendChild(input);
  parent.appendChild(field);
}

function toggle(parent, state, actions, key, label, dirty) {
  const val = state.config[key];
  const row = document.createElement('label');
  row.className = 'toggle-row';
  const input = document.createElement('input');
  input.type = 'checkbox';
  input.checked = val;
  const span = document.createElement('span');
  span.textContent = label;
  input.addEventListener('change', (e) => {
    state.set(key, e.target.checked);
    if (dirty && dirty.length) state.markDirty(...dirty);
    buildPanel(state, actions);
  });
  row.appendChild(input);
  row.appendChild(span);
  parent.appendChild(row);
}
// Generic dotted-path dropdown. `options` is an array of [value, label].
function dropdown(parent, state, actions, path, label, options, onChange) {
  const val = getPath(state.config, path);
  const field = document.createElement('div');
  field.className = 'field';
  const lab = document.createElement('div');
  lab.className = 'field-label';
  lab.innerHTML = `<span>${label}</span>`;
  field.appendChild(lab);
  const dd = document.createElement('select');
  dd.innerHTML = options.map(([v, l]) => `<option value="${v}">${l}</option>`).join('');
  dd.value = val;
  dd.addEventListener('change', (e) => {
    setPath(state.config, path, e.target.value);
    state.emit({ type: 'config', key: path, value: e.target.value });
    if (onChange) onChange();
    buildPanel(state, actions);
  });
  field.appendChild(dd);
  parent.appendChild(field);
}

function seedField(parent, state, actions, path, label, dirty) {
  const val = getPath(state.config, path);
  const field = document.createElement('div');
  field.className = 'field';

  const lab = document.createElement('div');
  lab.className = 'field-label';
  lab.innerHTML = `<span>${label}</span>`;
  field.appendChild(lab);

  const row = document.createElement('div');
  row.className = 'seed-row';

  const input = document.createElement('input');
  input.type = 'number';
  input.step = 1;
  input.value = val;
  input.addEventListener('change', (e) => {
    const v = parseInt(e.target.value) || 0;
    setPath(state.config, path, v);
    state.emit({ type: 'config', key: path, value: v });
    if (dirty && dirty.length) state.markDirty(...dirty);
  });

  const btn = document.createElement('button');
  btn.textContent = '🎲';
  btn.title = 'Randomize seed';
  btn.addEventListener('click', () => {
    const v = Math.floor(Math.random() * 1000000);
    setPath(state.config, path, v);
    input.value = v;
    state.emit({ type: 'config', key: path, value: v });
    if (dirty && dirty.length) state.markDirty(...dirty);
  });

  row.appendChild(input);
  row.appendChild(btn);
  field.appendChild(row);
  parent.appendChild(field);
}

function fmt(v) {
  if (Number.isInteger(v)) return String(v);
  const a = Math.abs(v);
  if (a >= 100) return v.toFixed(1);
  if (a >= 1) return v.toFixed(2);
  return v.toFixed(3);
}

export function updateReadout(text) {
  document.getElementById('readout').textContent = text;
}
// -- Config clipboard (copy/paste JSON) -----------------------------------
// Keys of config that represent persistable state (skip transient/internal).
function serializeConfig(state) {
  const c = state.config;
  // shallow clone; config only holds plain data so JSON round-trips safely
  return JSON.stringify(c, null, 2);
}
async function copyConfig(state) {
  const json = serializeConfig(state);
  try {
    await navigator.clipboard.writeText(json);
    updateReadout('Configuration copied to clipboard');
  } catch (err) {
    // Fallback: show a prompt the user can copy from manually.
    window.prompt('Copy configuration JSON:', json);
  }
}
// Deep-merge parsed config into existing config so missing keys keep defaults.
function mergeConfig(target, source) {
  for (const key of Object.keys(source)) {
    const sv = source[key];
    if (
      sv &&
      typeof sv === 'object' &&
      !Array.isArray(sv) &&
      target[key] &&
      typeof target[key] === 'object' &&
      !Array.isArray(target[key])
    ) {
      mergeConfig(target[key], sv);
    } else {
      target[key] = sv;
    }
  }
}
function applyConfig(state, actions, json) {
  let parsed;
  try {
    parsed = JSON.parse(json);
  } catch (err) {
    updateReadout('Paste failed: invalid JSON');
    return;
  }
  if (!parsed || typeof parsed !== 'object') {
    updateReadout('Paste failed: not a config object');
    return;
  }
  mergeConfig(state.config, parsed);
  // Sync toolbar controls that mirror config state.
  const $ = (id) => document.getElementById(id);
  if ($('viz-mode')) $('viz-mode').value = state.config.vizMode;
  if ($('color-scheme')) $('color-scheme').value = state.config.colorScheme;
  if ($('value-scaling') && state.config.valueScaling)
    $('value-scaling').value = state.config.valueScaling;
  if ($('autofollow')) $('autofollow').checked = state.config.autofollow;
  if ($('show-grad')) $('show-grad').checked = state.config.showGrad;
  if ($('speed')) $('speed').value = state.config.speed;
  state.emit({ type: 'config', key: '*', value: null });
  actions.rebuildOptimizer();
  buildPanel(state, actions);
  state.markAllDirty();
  updateReadout('Configuration applied from clipboard');
}
async function pasteConfig(state, actions) {
  let json = null;
  try {
    json = await navigator.clipboard.readText();
  } catch (err) {
    json = null;
  }
  if (!json) {
    // Fallback: ask the user to paste manually.
    json = window.prompt('Paste configuration JSON:');
    if (!json) return;
  }
  applyConfig(state, actions, json);
}
