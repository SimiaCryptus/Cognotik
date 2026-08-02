// main.js — app bootstrap, wires UI + worker

import { loadConfig, saveConfig } from './config.js';
import { createCorpusPanel } from './ui/corpusPanel.js';
import { createConfigPanel } from './ui/configPanel.js';
import { renderAnalysis, renderLegend } from './ui/analysisView.js';
import { createStatsPanel } from './ui/statsPanel.js';
import { BidirectionalModel } from './model/bidirectional.js';
import { getTokenizer, applyRegexConfig } from './tokenizers/index.js';
import { analyze } from './model/scoring.js';
import { createCaControls } from './ca/caControls.js';

const config = loadConfig();

const modelStateEl = document.getElementById('modelState');
const tokenCountEl = document.getElementById('tokenCount');
const legendEl = document.getElementById('legend');
const analysisEl = document.getElementById('analysisView');
const testTextEl = document.getElementById('testText');
const analyzeBtn = document.getElementById('analyzeBtn');
const statsPanelEl = document.getElementById('statsPanel');
const statsPanel = statsPanelEl ? createStatsPanel(statsPanelEl) : null;
const caPanelEl = document.getElementById('caPanel');

let modelReady = false;
let lastResults = null;
let debounceTimer = null;
let lastAnalyzedText = '';
// Render analysis heatmap and refresh the statistics panel together.
function showResults(results) {
  renderAnalysis(analysisEl, results, config, { onReplace: replaceToken });
  if (statsPanel) statsPanel.render(results);
}

// --- Worker with main-thread fallback ---
let worker = null;
let fallbackModel = null;
let fallbackCfg = null;

function useFallback() {
  return typeof Worker === 'undefined' || worker === null;
}

try {
  worker = new Worker(new URL('./workers/modelWorker.js', import.meta.url), { type: 'module' });
  worker.onmessage = onWorkerMessage;
  worker.onerror = () => {
    worker = null;
  };
} catch (e) {
  worker = null;
}

function setState(txt) {
  modelStateEl.textContent = txt;
}

function onWorkerMessage(e) {
  const msg = e.data;
  if (msg.type === 'progress') {
    if (msg.phase === 'build') corpusPanel.setProgress(msg.value);
    setState(
      msg.phase === 'build'
        ? `building… ${Math.round(msg.value * 100)}%`
        : `analyzing… ${Math.round(msg.value * 100)}%`
    );
  } else if (msg.type === 'built') {
    modelReady = true;
    corpusPanel.setProgress(null);
    setState('ready');
    tokenCountEl.textContent = msg.summary.tokenCount;
  } else if (msg.type === 'analyzed') {
    lastResults = msg.results;
    showResults(lastResults);
    setState('ready');
  } else if (msg.type === 'error') {
    setState('error: ' + msg.error);
  }
}

// --- Build model ---
function buildModel(corpus) {
  if (!corpus.trim()) {
    setState('empty corpus');
    return;
  }
  modelReady = false;
  setState('building…');
  if (useFallback()) {
    const tok = getTokenizer(config.tokenizerId);
    applyRegexConfig(config);
    const tokens = tok.tokenize(corpus);
    fallbackModel = new BidirectionalModel(config.order);
    fallbackCfg = { ...config };
    fallbackModel.build(tokens, (p) => corpusPanel.setProgress(p));
    corpusPanel.setProgress(null);
    modelReady = true;
    tokenCountEl.textContent = tokens.length;
    setState('ready');
  } else {
    worker.postMessage({ type: 'build', corpus, config: { ...config } });
  }
}

// --- Analyze ---
function runAnalysis() {
  if (!modelReady) {
    setState('build model first');
    return;
  }
  const text = testTextEl.value;
  if (!text) return;
  lastAnalyzedText = text;
  setState('analyzing…');
  if (useFallback()) {
    const tok = getTokenizer(fallbackCfg.tokenizerId);
    applyRegexConfig(fallbackCfg);
    applyRegexConfig(fallbackCfg);
    const spans = tok.tokenizeWithSpans(config.lowercase ? text.toLowerCase() : text);
    lastResults = analyze(fallbackModel, spans, config, () => {});
    showResults(lastResults);
    setState('ready');
  } else {
    worker.postMessage({ type: 'analyze', testText: text, config: { ...config } });
  }
}
// Replace the token at result's [start,end) with newToken in the test text,
// then re-run analysis on the edited text.
function replaceToken(result, newToken) {
  const text = lastAnalyzedText || testTextEl.value;
  if (result.start == null || result.end == null) return;
  const edited = text.slice(0, result.start) + newToken + text.slice(result.end);
  testTextEl.value = edited;
  runAnalysis();
}

function debouncedReanalyze() {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    if (modelReady && testTextEl.value) runAnalysis();
  }, 300);
}

// --- UI wiring ---
const corpusPanel = createCorpusPanel(document.getElementById('corpusPanel'), {
  getConfig: () => config,
  onCorpus: (norm, tokenCount) => {
    tokenCountEl.textContent = tokenCount;
  },
  onBuild: (corpus) => buildModel(corpus),
});

createConfigPanel(document.getElementById('configPanel'), {
  config,
  onChange: (key, val, requiresRebuild) => {
    saveConfig(config);
    corpusPanel.updateStats();
    renderLegend(legendEl, config);
    if (requiresRebuild) {
      if (modelReady) {
        setState('config changed — rebuild required');
        modelReady = false;
      }
    } else {
      // re-run analysis + re-color existing results
      if (lastResults) showResults(lastResults);
      debouncedReanalyze();
    }
  },
});

analyzeBtn.addEventListener('click', runAnalysis);
renderLegend(legendEl, config);
// --- Cellular Automaton panel ---
// Uses the same built model. In worker mode we still need a model instance on
// the main thread for the CA, so build a lightweight fallback model lazily.
let caModel = null;
let caModelKey = null;
function currentCorpus() {
  return corpusPanel.getCorpus();
}
// Build (or reuse) a main-thread model for the CA layer. The worker model
// can't be queried synchronously per-cell, so the CA always uses a local one.
function getCaModel() {
  if (!modelReady) return null;
  const corpus = currentCorpus();
  const key = `${config.tokenizerId}|${config.order}|${config.lowercase}|${config.regexPattern}|${corpus.length}`;
  if (caModel && caModelKey === key) return caModel;
  if (fallbackModel && useFallback()) {
    caModel = fallbackModel;
    caModelKey = key;
    return caModel;
  }
  // Rebuild locally for CA use.
  const tok = getTokenizer(config.tokenizerId);
  applyRegexConfig(config);
  const tokens = tok.tokenize(corpus);
  if (!tokens.length) return null;
  caModel = new BidirectionalModel(config.order);
  caModel.build(tokens);
  caModelKey = key;
  return caModel;
}
if (caPanelEl) {
  const caControls = createCaControls(caPanelEl, {
    getModel: getCaModel,
    getConfig: () => config,
    getTokenizer: () => getTokenizer(config.tokenizerId),
  });
  // Seed the CA with whatever is in the test text box.
  if (testTextEl && testTextEl.value) caControls.setSeedText(testTextEl.value);
}
