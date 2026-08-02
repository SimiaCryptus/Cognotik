// caControls.js — play/pause/step + CA parameters, drives the simulation

import { el, clear } from '../util/dom.js';
import { makeRule } from './caRule.js';
import { CaEngine } from './caEngine.js';
import { createCaView } from './caView.js';
import { makeRng, hashSeed } from './rng.js';
import { activity, hammingDistance, vocabEntropy, meanProbability } from './caMetrics.js';

// getModel() -> BidirectionalModel | null
// getConfig() -> app config (for combine/backoff/palette/etc)
// getTokenizer() -> tokenizer to seed the tape from text
export function createCaControls(container, { getModel, getConfig, getTokenizer }) {
  let engine = null;
  let tape = [];
  let original = [];
  let rng = Math.random;
  let running = false;
  let timer = null;
  let generation = 0;

  container.appendChild(el('h2', { text: 'Cellular Automaton' }));

  // --- seed input ---
  const seedText = el('textarea', {
    rows: 3,
    placeholder: 'Initial tape text (seed)…',
  });

  // --- parameter controls ---
  const policySel = sel('Policy', [
    ['sync', 'Synchronous'],
    ['async', 'Asynchronous'],
    ['subset', 'Stochastic subset'],
  ]);
  const boundarySel = sel('Boundary', [
    ['periodic', 'Periodic'],
    ['fixed', 'Fixed'],
    ['reflective', 'Reflective'],
  ]);
  const detChk = el('input', { type: 'checkbox' });
  const tempInput = el('input', {
    type: 'number',
    value: '0.2',
    min: '0',
    step: '0.05',
  });
  const rhoInput = el('input', {
    type: 'number',
    value: '1',
    min: '0',
    max: '1',
    step: '0.05',
  });
  const radiusInput = el('input', { type: 'number', value: '', min: '1', step: '1' });
  const seedNumInput = el('input', { type: 'text', value: 'ca-1', placeholder: 'PRNG seed' });
  const speedInput = el('input', {
    type: 'number',
    value: '120',
    min: '0',
    step: '10',
    title: 'Delay between generations in ms (0 = as fast as possible)',
  });

  const paramGrid = el('div', { class: 'config-grid' }, [
    el('label', { text: 'Policy' }),
    policySel,
    el('label', { text: 'Boundary' }),
    boundarySel,
    el('label', { text: 'Deterministic' }),
    detChk,
    el('label', { text: 'Temperature' }),
    tempInput,
    el('label', { text: 'Subset fraction ρ' }),
    rhoInput,
    el('label', { text: 'Radius (blank=order)' }),
    radiusInput,
    el('label', { text: 'PRNG seed' }),
    seedNumInput,
    el('label', { text: 'Speed (ms/gen)' }),
    speedInput,
  ]);

  // --- action buttons ---
  const seedBtn = el('button', { type: 'button', text: 'Seed / Reset' });
  const stepBtn = el('button', { type: 'button', text: 'Step' });
  const playBtn = el('button', { type: 'button', text: 'Play' });
  const noiseBtn = el('button', { type: 'button', text: 'Seed w/ noise' });
  const actions = el('div', { class: 'actions' }, [seedBtn, stepBtn, playBtn, noiseBtn]);

  const status = el('div', { class: 'ca-status' });
  const metricsEl = el('div', { class: 'ca-metrics' });

  container.appendChild(el('label', { text: 'Seed text' }));
  container.appendChild(seedText);
  container.appendChild(paramGrid);
  container.appendChild(actions);
  container.appendChild(status);
  container.appendChild(metricsEl);

  // --- spacetime view ---
  const viewContainer = el('div', { class: 'ca-view' });
  container.appendChild(viewContainer);
  const view = createCaView(viewContainer, {
    config: getConfig(),
    onCellClick: (t, i) => {
      status.textContent = `gen ${t}, pos ${i}`;
    },
  });

  function sel(label, options) {
    const s = el('select', {});
    for (const [v, l] of options) s.appendChild(el('option', { value: v, text: l }));
    return s;
  }

  function opts() {
    const cfg = getConfig();
    const radius = radiusInput.value ? parseInt(radiusInput.value, 10) : undefined;
    return {
      radius,
      combine: cfg.combine,
      temperature: parseFloat(tempInput.value) || 0.2,
      deterministic: detChk.checked,
      smoothingK: cfg.smoothingK,
      backoff: cfg.backoff,
      floorProb: cfg.floorProb,
      limit: 48,
    };
  }

  function buildEngine() {
    const model = getModel();
    if (!model) return null;
    rng = makeRng(hashSeed(seedNumInput.value || 'ca'));
    const rule = makeRule(model, { ...opts(), rng });
    return new CaEngine(rule, {
      policy: policySel.value,
      boundary: boundarySel.value,
      rho: parseFloat(rhoInput.value) || 0.3,
      rng,
    });
  }

  function tokenizeSeed(text) {
    const tok = getTokenizer();
    const cfg = getConfig();
    const t = cfg.lowercase ? text.toLowerCase() : text;
    return tok.tokenize(t);
  }

  function computePerCellP() {
    const model = getModel();
    const cfg = getConfig();
    const radius = radiusInput.value ? parseInt(radiusInput.value, 10) : model.order;
    const per = new Array(tape.length);
    const probOpts = {
      smoothingK: cfg.smoothingK,
      backoff: cfg.backoff,
      floorProb: cfg.floorProb,
    };
    const boundary = boundarySel.value;
    for (let i = 0; i < tape.length; i++) {
      const left = neigh(tape, i - radius, i, boundary);
      const right = neigh(tape, i + 1, i + 1 + radius, boundary).reverse();
      const pF = model.forward.prob(left, tape[i], probOpts);
      const pB = model.backward.prob(right, tape[i], probOpts);
      per[i] = combineP(pF, pB, cfg.combine);
    }
    return per;
  }

  function pushRow(changed) {
    view.appendRow({
      tape: tape.slice(),
      changed: changed ? changed.slice() : null,
      perCellP: computePerCellP(),
    });
  }

  function updateMetrics(changed) {
    const model = getModel();
    const cfg = getConfig();
    const a = changed ? activity(changed) : 0;
    const dist = hammingDistance(original, tape);
    const ent = vocabEntropy(tape);
    const { meanP, perplexity } = meanProbability(model, tape, {
      combine: cfg.combine,
      smoothingK: cfg.smoothingK,
      backoff: cfg.backoff,
      floorProb: cfg.floorProb,
      radius: radiusInput.value ? parseInt(radiusInput.value, 10) : model.order,
      boundary: boundarySel.value,
    });
    clear(metricsEl);
    metricsEl.appendChild(metricRow('Generation', String(generation)));
    metricsEl.appendChild(metricRow('Activity', (a * 100).toFixed(1) + '%'));
    metricsEl.appendChild(metricRow('Dist. from orig', (dist * 100).toFixed(1) + '%'));
    metricsEl.appendChild(metricRow('Vocab entropy', ent.toFixed(3) + ' bits'));
    metricsEl.appendChild(metricRow('Mean prob', (meanP * 100).toFixed(3) + '%'));
    metricsEl.appendChild(metricRow('Perplexity', perplexity.toPrecision(4)));
  }

  function metricRow(label, value) {
    return el('div', { class: 'stats-row' }, [
      el('span', { class: 'stats-label', text: label }),
      el('span', { class: 'stats-value', text: value }),
    ]);
  }

  function doSeed(fromNoise) {
    const model = getModel();
    if (!model) {
      status.textContent = 'Build the model first.';
      return;
    }
    stop();
    engine = buildEngine();
    if (fromNoise) {
      const vocab = model.forward.tokenTable.filter((t) => t != null);
      const len = tokenizeSeed(seedText.value || '').length || 40;
      tape = new Array(len);
      for (let i = 0; i < len; i++) {
        tape[i] = vocab[Math.floor(rng() * vocab.length)] || '';
      }
    } else {
      tape = tokenizeSeed(seedText.value || '');
    }
    if (!tape.length) {
      status.textContent = 'Seed produced an empty tape.';
      return;
    }
    original = tape.slice();
    generation = 0;
    view.clear();
    pushRow(null);
    updateMetrics(null);
    status.textContent = `Seeded ${tape.length} tokens.`;
  }

  function stepOnce() {
    if (!engine) engine = buildEngine();
    if (!engine || !tape.length) {
      status.textContent = 'Seed the tape first.';
      return false;
    }
    const { tape: next, changed } = engine.step(tape);
    tape = next;
    generation++;
    pushRow(changed);
    updateMetrics(changed);
    const changedCount = changed.filter(Boolean).length;
    if (changedCount === 0) {
      status.textContent = `Fixed point reached at gen ${generation}.`;
      return false;
    }
    return true;
  }

  function play() {
    if (running) return;
    // rebuild engine to pick up latest params
    engine = buildEngine();
    running = true;
    playBtn.textContent = 'Pause';
    const tick = () => {
      if (!running) return;
      const cont = stepOnce();
      if (!cont) {
        stop();
        return;
      }
      const delay = Math.max(0, parseInt(speedInput.value, 10) || 0);
      timer = setTimeout(tick, delay);
    };
    tick();
  }

  function stop() {
    running = false;
    playBtn.textContent = 'Play';
    if (timer) clearTimeout(timer);
    timer = null;
  }

  seedBtn.addEventListener('click', () => doSeed(false));
  noiseBtn.addEventListener('click', () => doSeed(true));
  stepBtn.addEventListener('click', () => {
    engine = engine || buildEngine();
    stepOnce();
  });
  playBtn.addEventListener('click', () => (running ? stop() : play()));

  // Rebuild engine when structural params change mid-run.
  for (const ctrl of [policySel, boundarySel, detChk, tempInput, rhoInput, radiusInput]) {
    ctrl.addEventListener('change', () => {
      if (!running) engine = null; // lazy rebuild on next step
    });
  }

  return {
    setSeedText(t) {
      seedText.value = t;
    },
  };
}

// --- boundary-aware neighborhood helpers (shared shape with caMetrics) ---
function neigh(arr, from, to, boundary) {
  const n = arr.length;
  const out = [];
  for (let j = from; j < to; j++) {
    if (j >= 0 && j < n) out.push(arr[j]);
    else if (boundary === 'periodic') out.push(arr[((j % n) + n) % n]);
    else if (boundary === 'reflective') {
      let idx = j;
      if (idx < 0) idx = -idx - 1;
      if (idx >= n) idx = 2 * n - idx - 1;
      idx = Math.max(0, Math.min(n - 1, idx));
      out.push(arr[idx]);
    }
  }
  return out;
}

function combineP(pF, pB, strategy) {
  switch (strategy) {
    case 'forward':
      return pF;
    case 'backward':
      return pB;
    case 'min':
      return Math.min(pF, pB);
    case 'max':
      return Math.max(pF, pB);
    case 'geometric-mean':
      return Math.sqrt(pF * pB);
    case 'average':
    default:
      return (pF + pB) / 2;
  }
}
