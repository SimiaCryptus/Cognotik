// config.js — schema, defaults, persistence

const STORAGE_KEY = 'markov-analysis-config';
import { REGEX_PRESETS } from './tokenizers/regexTokenizer.js';

export const CONFIG_SCHEMA = [
  {
    key: 'tokenizerId',
    type: 'select',
    label: 'Tokenizer',
    default: 'char',
    options: [
      { value: 'char', label: 'Character' },
      { value: 'whitespace', label: 'Whitespace word' },
      { value: 'wordpunct', label: 'Word + punctuation' },
      { value: 'regex', label: 'Custom regex' },
    ],
    rebuild: true,
  },
  {
    key: 'regexPreset',
    type: 'select',
    label: 'Regex preset',
    default: REGEX_PRESETS[0].value,
    options: REGEX_PRESETS.map((p) => ({ value: p.value, label: p.label })),
    rebuild: true,
  },
  {
    key: 'regexPattern',
    type: 'text',
    label: 'Regex pattern',
    default: REGEX_PRESETS[0].value,
    rebuild: true,
  },
  {
    key: 'order',
    type: 'int',
    label: 'Model order (n)',
    default: 2,
    min: 1,
    max: 8,
    rebuild: true,
  },
  { key: 'lowercase', type: 'bool', label: 'Lowercase corpus', default: false, rebuild: true },
  {
    key: 'smoothingK',
    type: 'float',
    label: 'Smoothing k',
    default: 0,
    min: 0,
    step: 0.01,
    rebuild: true,
  },
  {
    key: 'combine',
    type: 'select',
    label: 'Combine strategy',
    default: 'average',
    options: [
      { value: 'forward', label: 'Forward' },
      { value: 'backward', label: 'Backward' },
      { value: 'average', label: 'Average' },
      { value: 'min', label: 'Min' },
      { value: 'max', label: 'Max' },
      { value: 'geometric-mean', label: 'Geometric mean' },
    ],
  },
  { key: 'backoff', type: 'bool', label: 'Backoff enabled', default: true },
  {
    key: 'floorProb',
    type: 'float',
    label: 'Floor probability',
    default: 1e-6,
    min: 0,
    step: 1e-6,
  },
  { key: 'topN', type: 'int', label: 'Top-N replacements', default: 5, min: 1, max: 20 },
  {
    key: 'colorScale',
    type: 'select',
    label: 'Color scale',
    default: 'log',
    options: [
      { value: 'linear', label: 'Linear' },
      { value: 'log', label: 'Log' },
    ],
  },
  {
    key: 'palette',
    type: 'select',
    label: 'Palette',
    default: 'heat',
    options: [
      { value: 'heat', label: 'Heat' },
      { value: 'viridis', label: 'Viridis' },
    ],
  },
];

export function defaultConfig() {
  const cfg = {};
  for (const f of CONFIG_SCHEMA) cfg[f.key] = f.default;
  return cfg;
}

export function loadConfig() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return { ...defaultConfig(), ...JSON.parse(raw) };
  } catch (e) {
    /* ignore */
  }
  return defaultConfig();
}

export function saveConfig(cfg) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cfg));
  } catch (e) {
    /* ignore */
  }
}

// returns list of keys that require a rebuild when changed
export const REBUILD_KEYS = CONFIG_SCHEMA.filter((f) => f.rebuild).map((f) => f.key);
