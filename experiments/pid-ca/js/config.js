/**
 * config.js — single source of truth for the PID-CA configuration surface (§5, §7.1).
 *
 * Responsibilities:
 *  - declare the parameter schema (type, range, default, grouping, visibility)
 *  - validate / coerce arbitrary input into a well-formed configuration
 *  - serialise to and from plain JSON-compatible objects (presets, share links)
 *  - notify subscribers when parameters change
 */

export const BOUNDARIES = ['toroidal', 'fixed', 'reflective'];
export const NEIGHBORHOODS = ['moore', 'vonNeumann'];
export const EXPRESSIONS = ['threshold', 'quantize', 'semantic', 'probabilistic'];
export const ACTIVE_PREDICATES = ['gt0', 'ge2', 'eqMax'];
export const INITIAL_CONDITIONS = ['random', 'center', 'singleCell', 'stripes', 'empty'];
export const TARGET_MODES = ['constant', 'gradientX', 'radial', 'oscillating'];
export const OVERLAYS = ['none', 'u', 'integral', 'error', 'voltage'];

// ---- bioelectrical membrane domain (bioelectrical.md §5, §6.3) ------------
export const MODES = ['pid', 'membrane-only', 'pid-homeostat'];
export const POLARITIES = ['depolarizing', 'hyperpolarizing'];
export const CLOSE_MODES = ['hysteresis', 'timed'];
export const MEMBRANE_INITS = [
  'uniform-rest',
  'noisy-rest',
  'seeded-spike',
  'pacemaker',
  'painted',
];
export const STIMULUS_MODES = ['pulse', 'hold'];

export const GROUPS = [
  'Model',
  'Grid & topology',
  'Target',
  'PID gains',
  'State expression',
  'Membrane (bioelectrical)',
  'Initialisation',
  'Painting',
  'Playback',
  'Display',
];

const isExpr =
  (...names) =>
  (cfg) =>
    names.includes(cfg.expression);
/** Visibility helpers for the two coexisting domains. */
const isPid = (cfg) => cfg.mode === 'pid';
const isMembrane = (cfg) => cfg.mode !== 'pid';
/** Maximum neighbour count for the configured neighbourhood (diffusion stability). */
export function maxNeighborCount(cfg) {
  const r = Math.max(1, cfg.radius | 0);
  return cfg.neighborhood === 'vonNeumann' ? 2 * r * (r + 1) : (2 * r + 1) * (2 * r + 1) - 1;
}

/** Full parameter schema. `structural: true` means a change forces reallocation + reset. */
export const SCHEMA = {
  // ------------------------------------------------------------------- model
  mode: {
    group: 'Model',
    label: 'Domain / mechanism',
    type: 'enum',
    options: MODES,
    default: 'pid',
    structural: true,
    optionLabels: {
      pid: 'PID controller (base model)',
      'membrane-only': 'Bioelectrical membrane',
      'pid-homeostat': 'Membrane + PID homeostat',
    },
    hint: 'Membrane modes replace the neighbour-count controller with the excitable-membrane rule.',
  },

  // ---------------------------------------------------------------- topology
  gridWidth: {
    group: 'Grid & topology',
    label: 'Grid width',
    type: 'int',
    min: 8,
    max: 400,
    step: 1,
    default: 120,
    structural: true,
  },
  gridHeight: {
    group: 'Grid & topology',
    label: 'Grid height',
    type: 'int',
    min: 8,
    max: 400,
    step: 1,
    default: 80,
    structural: true,
  },
  boundary: {
    group: 'Grid & topology',
    label: 'Boundary condition',
    type: 'enum',
    options: BOUNDARIES,
    default: 'toroidal',
    optionLabels: {
      toroidal: 'Toroidal (wrap)',
      fixed: 'Fixed (dead border)',
      reflective: 'Reflective',
    },
  },
  neighborhood: {
    group: 'Grid & topology',
    label: 'Neighbourhood',
    type: 'enum',
    options: NEIGHBORHOODS,
    default: 'moore',
    optionLabels: { moore: 'Moore', vonNeumann: 'von Neumann' },
  },
  radius: {
    group: 'Grid & topology',
    label: 'Neighbourhood radius',
    type: 'int',
    min: 1,
    max: 6,
    step: 1,
    default: 1,
  },
  activePredicate: {
    group: 'Grid & topology',
    label: '"Active" predicate',
    type: 'enum',
    options: ACTIVE_PREDICATES,
    default: 'gt0',
    optionLabels: { gt0: 'state > 0', ge2: 'state >= 2', eqMax: 'state = max' },
    hint: 'Definition of "active" used when counting N_t(c).',
    visible: isPid,
  },

  // ------------------------------------------------------------------ target
  targetMode: {
    group: 'Target',
    label: 'Target mode',
    type: 'enum',
    options: TARGET_MODES,
    default: 'constant',
    optionLabels: {
      constant: 'Constant T',
      gradientX: 'Horizontal gradient',
      radial: 'Radial field',
      oscillating: 'Time-oscillating',
    },
  },
  target: {
    group: 'Target',
    label: 'Target T (active neighbours)',
    type: 'float',
    min: 0,
    max: 24,
    step: 0.05,
    default: 4,
  },
  targetAmplitude: {
    group: 'Target',
    label: 'Target amplitude',
    type: 'float',
    min: -8,
    max: 8,
    step: 0.05,
    default: 2,
    visible: (cfg) => cfg.targetMode !== 'constant',
  },
  targetPeriod: {
    group: 'Target',
    label: 'Target period (steps)',
    type: 'int',
    min: 2,
    max: 2000,
    step: 1,
    default: 160,
    visible: (cfg) => cfg.targetMode === 'oscillating',
  },

  // --------------------------------------------------------------- pid gains
  kp: {
    group: 'PID gains',
    label: 'Kp (proportional)',
    type: 'float',
    min: -4,
    max: 4,
    step: 0.01,
    default: 1,
  },
  ki: {
    group: 'PID gains',
    label: 'Ki (integral)',
    type: 'float',
    min: -1,
    max: 1,
    step: 0.002,
    default: 0.02,
  },
  kd: {
    group: 'PID gains',
    label: 'Kd (derivative)',
    type: 'float',
    min: -4,
    max: 4,
    step: 0.01,
    default: 0.4,
  },
  integralClamp: {
    group: 'PID gains',
    label: 'Clamp integral (anti-windup)',
    type: 'bool',
    default: true,
  },
  integralMin: {
    group: 'PID gains',
    label: 'Integral min',
    type: 'float',
    min: -50,
    max: 0,
    step: 0.5,
    default: -8,
    visible: (cfg) => cfg.integralClamp,
  },
  integralMax: {
    group: 'PID gains',
    label: 'Integral max',
    type: 'float',
    min: 0,
    max: 50,
    step: 0.5,
    default: 8,
    visible: (cfg) => cfg.integralClamp,
  },

  // -------------------------------------------------------- state expression
  stateCardinality: {
    group: 'State expression',
    label: 'State cardinality',
    type: 'enum',
    options: [2, 3],
    default: 2,
    optionLabels: { 2: '2-state {0,1}', 3: '3-state {0,1,2}' },
  },
  expression: {
    group: 'State expression',
    label: 'Expression function',
    type: 'enum',
    options: EXPRESSIONS,
    default: 'threshold',
    optionLabels: {
      threshold: 'Binary threshold',
      quantize: 'Ternary quantisation',
      semantic: 'Ternary semantic (dominant term)',
      probabilistic: 'Probabilistic (sigmoid)',
    },
  },
  theta: {
    group: 'State expression',
    label: 'Threshold θ',
    type: 'float',
    min: -10,
    max: 10,
    step: 0.05,
    default: 0,
    visible: isExpr('threshold', 'semantic', 'probabilistic'),
  },
  bandA: {
    group: 'State expression',
    label: 'Band edge a',
    type: 'float',
    min: -10,
    max: 10,
    step: 0.05,
    default: -0.5,
    visible: isExpr('quantize'),
  },
  bandB: {
    group: 'State expression',
    label: 'Band edge b',
    type: 'float',
    min: -10,
    max: 10,
    step: 0.05,
    default: 0.5,
    visible: isExpr('quantize'),
    hint: 'Requires a < b.',
  },
  sigmoidScale: {
    group: 'State expression',
    label: 'Sigmoid steepness',
    type: 'float',
    min: 0.1,
    max: 10,
    step: 0.1,
    default: 1,
    visible: isExpr('probabilistic'),
  },

  // -------------------------------------------------------- initial condition
  initialCondition: {
    group: 'Initialisation',
    label: 'Initial condition',
    type: 'enum',
    options: INITIAL_CONDITIONS,
    default: 'random',
    optionLabels: {
      random: 'Uniform random density',
      center: 'Central block',
      singleCell: 'Single seed cell',
      stripes: 'Vertical stripes',
      empty: 'Empty grid',
    },
    visible: isPid,
  },
  initialDensity: {
    group: 'Initialisation',
    label: 'Random density',
    type: 'float',
    min: 0,
    max: 1,
    step: 0.01,
    default: 0.35,
    visible: (cfg) =>
      isPid(cfg) && (cfg.initialCondition === 'random' || cfg.initialCondition === 'center'),
  },
  seed: {
    group: 'Initialisation',
    label: 'RNG seed',
    type: 'int',
    min: 0,
    max: 999999,
    step: 1,
    default: 1234,
    hint: 'Deterministic: same seed + config = same run.',
  },
  perturbInit: {
    group: 'Initialisation',
    label: 'Symmetry-breaking perturbation',
    type: 'enum',
    options: ['none', 'normal'],
    default: 'none',
    optionLabels: { none: 'Disabled', normal: 'Normal noise' },
    hint: 'Adds noise to initial controller memory (e_(t-1), I_0) to break symmetry.',
    visible: isPid,
  },
  perturbSigma: {
    group: 'Initialisation',
    label: 'Perturbation σ',
    type: 'float',
    min: 0,
    max: 5,
    step: 0.01,
    default: 0.1,
    visible: (cfg) => isPid(cfg) && cfg.perturbInit !== 'none',
  },
  // ------------------------------------------------- membrane (§3.4–§3.6, §5)
  vRest: {
    group: 'Membrane (bioelectrical)',
    label: 'V_rest (resting / leak target)',
    type: 'float',
    min: -100,
    max: 20,
    step: 0.5,
    default: -70,
    visible: isMembrane,
  },
  vThreshold: {
    group: 'Membrane (bioelectrical)',
    label: 'V_threshold (gate trigger)',
    type: 'float',
    min: -100,
    max: 40,
    step: 0.5,
    default: -55,
    visible: isMembrane,
  },
  vClose: {
    group: 'Membrane (bioelectrical)',
    label: 'V_close (hysteresis edge)',
    type: 'float',
    min: -120,
    max: 40,
    step: 0.5,
    default: -60,
    visible: (cfg) => isMembrane(cfg) && cfg.closeMode === 'hysteresis',
    hint: 'Depolarizing polarity requires V_release < V_close < V_threshold.',
  },
  vRelease: {
    group: 'Membrane (bioelectrical)',
    label: 'V_release (open-gate target)',
    type: 'float',
    min: -150,
    max: 40,
    step: 0.5,
    default: -90,
    visible: isMembrane,
  },
  vMin: {
    group: 'Membrane (bioelectrical)',
    label: 'V_min (clamp)',
    type: 'float',
    min: -200,
    max: 0,
    step: 1,
    default: -100,
    visible: isMembrane,
  },
  vMax: {
    group: 'Membrane (bioelectrical)',
    label: 'V_max (clamp)',
    type: 'float',
    min: -40,
    max: 120,
    step: 1,
    default: 40,
    visible: isMembrane,
  },
  kLeak: {
    group: 'Membrane (bioelectrical)',
    label: 'k_leak (relaxation to rest)',
    type: 'float',
    min: 0,
    max: 1,
    step: 0.005,
    default: 0.05,
    visible: (cfg) => cfg.mode === 'membrane-only',
    hint: 'In pid-homeostat mode this term is replaced by the PID output u_t.',
  },
  kCoupling: {
    group: 'Membrane (bioelectrical)',
    label: 'k_coupling (gap junction)',
    type: 'float',
    min: 0,
    max: 1,
    step: 0.005,
    default: 0.08,
    visible: isMembrane,
    hint: 'Wave speed. Stability requires k_coupling · maxNeighbours ≤ 1.',
  },
  kRelease: {
    group: 'Membrane (bioelectrical)',
    label: 'k_release (open-gate rate)',
    type: 'float',
    min: 0,
    max: 1,
    step: 0.01,
    default: 0.6,
    visible: isMembrane,
    hint: 'Should dominate k_leak by ~an order of magnitude or no spike forms.',
  },
  polarity: {
    group: 'Membrane (bioelectrical)',
    label: 'Comparator polarity',
    type: 'enum',
    options: POLARITIES,
    default: 'depolarizing',
    optionLabels: {
      depolarizing: 'Depolarizing (V ≥ V_threshold)',
      hyperpolarizing: 'Hyperpolarizing (V ≤ V_threshold)',
    },
    visible: isMembrane,
  },
  closeMode: {
    group: 'Membrane (bioelectrical)',
    label: 'Close condition',
    type: 'enum',
    options: CLOSE_MODES,
    default: 'hysteresis',
    optionLabels: { hysteresis: 'Hysteresis + min open time', timed: 'Timed (pulse generator)' },
    visible: isMembrane,
  },
  minOpenTicks: {
    group: 'Membrane (bioelectrical)',
    label: 'minOpenTicks (pulse width)',
    type: 'int',
    min: 0,
    max: 60,
    step: 1,
    default: 1,
    visible: isMembrane,
  },
  resetPeriod: {
    group: 'Membrane (bioelectrical)',
    label: 'resetPeriod (refractory ticks)',
    type: 'int',
    min: 0,
    max: 200,
    step: 1,
    default: 6,
    visible: isMembrane,
    hint: '0 disables refractoriness (waves back-propagate).',
  },
  vTarget: {
    group: 'Membrane (bioelectrical)',
    label: 'V_target (homeostat setpoint)',
    type: 'float',
    min: -120,
    max: 40,
    step: 0.5,
    default: -70,
    visible: (cfg) => cfg.mode === 'pid-homeostat',
    hint: 'e_t = V_target − V_t; u_t replaces the leak term (§6.3).',
  },
  noiseAmplitude: {
    group: 'Membrane (bioelectrical)',
    label: 'Noise amplitude (per-tick dV)',
    type: 'float',
    min: 0,
    max: 5,
    step: 0.01,
    default: 0,
    visible: isMembrane,
    hint: 'Non-zero breaks the bit-identical determinism guarantee (§9).',
  },
  membraneInit: {
    group: 'Membrane (bioelectrical)',
    label: 'Initial condition',
    type: 'enum',
    options: MEMBRANE_INITS,
    default: 'uniform-rest',
    optionLabels: {
      'uniform-rest': 'Uniform rest',
      'noisy-rest': 'Noisy rest',
      'seeded-spike': 'Seeded spike (centre)',
      pacemaker: 'Clamped pacemaker (centre)',
      painted: 'Painted (rest, paint to excite)',
    },
    visible: isMembrane,
    structural: true,
  },
  membraneJitter: {
    group: 'Membrane (bioelectrical)',
    label: 'Initial voltage jitter σ',
    type: 'float',
    min: 0,
    max: 40,
    step: 0.1,
    default: 3,
    visible: (cfg) => isMembrane(cfg) && cfg.membraneInit === 'noisy-rest',
  },

  // ---------------------------------------------------------------- painting
  paintTool: {
    group: 'Painting',
    label: 'Paint tool',
    type: 'enum',
    options: ['brush', 'fill'],
    default: 'brush',
    optionLabels: { brush: 'Brush', fill: 'Area fill (drag rectangle)' },
  },
  brushSize: {
    group: 'Painting',
    label: 'Brush size (cells)',
    type: 'int',
    min: 1,
    max: 25,
    step: 1,
    default: 1,
    visible: (cfg) => cfg.paintTool === 'brush',
    hint: 'Side length of the square painted around the pointer.',
  },
  fillDensity: {
    group: 'Painting',
    label: 'Fill density (%)',
    type: 'float',
    min: 0,
    max: 100,
    step: 1,
    default: 50,
    visible: (cfg) => cfg.paintTool === 'fill' && cfg.fillPattern === 'random',
  },
  fillPattern: {
    group: 'Painting',
    label: 'Fill pattern',
    type: 'enum',
    options: ['random', 'stripesH', 'stripesV', 'checker'],
    default: 'random',
    optionLabels: {
      random: 'Random (density)',
      stripesH: 'Horizontal stripes',
      stripesV: 'Vertical stripes',
      checker: 'Checkerboard',
    },
    visible: (cfg) => cfg.paintTool === 'fill',
    hint: 'Drag a rectangle on the grid, release to apply.',
  },
  membraneTool: {
    group: 'Painting',
    label: 'Membrane paint target',
    type: 'enum',
    options: ['stimulus', 'depolarize', 'clamp'],
    default: 'stimulus',
    optionLabels: {
      stimulus: 'Inject stimulus current',
      depolarize: 'Set V above threshold',
      clamp: 'Clamp cell voltage',
    },
    visible: isMembrane,
    hint: 'Shift-drag / right-drag erases (stimulus 0, V → V_rest, unclamp).',
  },
  stimulusAmplitude: {
    group: 'Painting',
    label: 'Stimulus amplitude (dV/tick)',
    type: 'float',
    min: -50,
    max: 50,
    step: 0.5,
    default: 8,
    visible: (cfg) => isMembrane(cfg) && cfg.membraneTool === 'stimulus',
  },
  stimulusMode: {
    group: 'Painting',
    label: 'Stimulus persistence',
    type: 'enum',
    options: STIMULUS_MODES,
    default: 'pulse',
    optionLabels: { pulse: 'Pulse (one tick)', hold: 'Hold (until erased)' },
    visible: (cfg) => isMembrane(cfg) && cfg.membraneTool === 'stimulus',
  },
  clampVoltage: {
    group: 'Painting',
    label: 'Clamp voltage',
    type: 'float',
    min: -120,
    max: 40,
    step: 0.5,
    default: -20,
    visible: (cfg) => isMembrane(cfg) && cfg.membraneTool !== 'stimulus',
    hint: 'Also used by the "clamped pacemaker" initial condition.',
  },

  // ---------------------------------------------------------------- playback
  stepsPerSecond: {
    group: 'Playback',
    label: 'Steps per second',
    type: 'float',
    min: 0.5,
    max: 240,
    step: 0.5,
    default: 20,
  },
  maxStepsPerFrame: {
    group: 'Playback',
    label: 'Max steps per frame',
    type: 'int',
    min: 1,
    max: 60,
    step: 1,
    default: 8,
    hint: 'Caps catch-up work when the tick rate exceeds the refresh rate.',
  },

  // ----------------------------------------------------------------- display
  cellSize: {
    group: 'Display',
    label: 'Cell size (px)',
    type: 'int',
    min: 1,
    max: 24,
    step: 1,
    default: 6,
  },
  overlay: {
    group: 'Display',
    label: 'Diagnostic overlay',
    type: 'enum',
    options: OVERLAYS,
    default: 'none',
    optionLabels: {
      none: 'None (states only)',
      u: 'Control output u_t',
      integral: 'Integral I_t (frustration)',
      error: 'Error e_t',
      voltage: 'Membrane potential V',
    },
  },
  overlayScale: {
    group: 'Display',
    label: 'Overlay scale (± range)',
    type: 'float',
    min: 0.5,
    max: 40,
    step: 0.5,
    default: 8,
    visible: (cfg) => cfg.overlay !== 'none',
  },
  showGridLines: {
    group: 'Display',
    label: 'Grid lines',
    type: 'bool',
    default: false,
  },
};

export const CONFIG_KEYS = Object.keys(SCHEMA);

/** @returns {object} a fresh configuration containing every schema default. */
export function defaultConfig() {
  const cfg = {};
  for (const key of CONFIG_KEYS) cfg[key] = SCHEMA[key].default;
  return cfg;
}

function clamp(v, lo, hi) {
  if (typeof lo === 'number' && v < lo) return lo;
  if (typeof hi === 'number' && v > hi) return hi;
  return v;
}

/**
 * Coerce + validate an arbitrary object into a complete configuration.
 * Unknown keys are ignored; invalid values fall back to defaults.
 * @returns {{config: object, errors: string[]}}
 */
export function validateConfig(raw) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const config = defaultConfig();
  const errors = [];

  for (const key of CONFIG_KEYS) {
    if (!(key in source)) continue;
    const spec = SCHEMA[key];
    let value = source[key];
    if (value === undefined || value === null || value === '') continue;

    switch (spec.type) {
      case 'int': {
        const n = Math.round(Number(value));
        if (!Number.isFinite(n)) {
          errors.push(key + ': not a number');
          break;
        }
        config[key] = clamp(n, spec.min, spec.max);
        break;
      }
      case 'float': {
        const n = Number(value);
        if (!Number.isFinite(n)) {
          errors.push(key + ': not a number');
          break;
        }
        config[key] = clamp(n, spec.min, spec.max);
        break;
      }
      case 'bool':
        config[key] = value === 'false' ? false : Boolean(value);
        break;
      case 'enum': {
        const match = spec.options.find((o) => o === value || String(o) === String(value));
        if (match === undefined) {
          errors.push(key + ': "' + value + '" is not a valid option');
          break;
        }
        config[key] = match;
        break;
      }
      default:
        config[key] = value;
    }
  }

  // ---- cross-field invariants -------------------------------------------
  if (config.bandA >= config.bandB) {
    config.bandB = config.bandA + Math.max(0.05, Math.abs(config.bandA) * 0.05);
    errors.push('band edges must satisfy a < b; adjusted b');
  }
  if (config.integralMin >= config.integralMax) {
    config.integralMin = -Math.abs(config.integralMax) - 1;
    errors.push('integral clamp must satisfy min < max; adjusted min');
  }
  if (config.stateCardinality === 2 && config.expression === 'semantic') {
    errors.push('semantic mapping is intended for 3-state mode; states will be clamped to {0,1}');
  }
  // ---- membrane invariants (bioelectrical.md §5) -------------------------
  const maxN = maxNeighborCount(config);
  if (config.kCoupling * maxN > 1) {
    config.kCoupling = Math.round((1 / maxN) * 1000) / 1000;
    errors.push('k_coupling · maxNeighbours must be ≤ 1; reduced k_coupling');
  }
  if (config.polarity === 'depolarizing') {
    if (config.vClose >= config.vThreshold) {
      config.vClose = config.vThreshold - 1;
      errors.push('require V_close < V_threshold; adjusted V_close');
    }
    if (config.vRelease >= config.vClose) {
      config.vRelease = config.vClose - 1;
      errors.push('require V_release < V_close; adjusted V_release');
    }
  } else {
    if (config.vClose <= config.vThreshold) {
      config.vClose = config.vThreshold + 1;
      errors.push('require V_close > V_threshold (hyperpolarizing); adjusted V_close');
    }
    if (config.vRelease <= config.vClose) {
      config.vRelease = config.vClose + 1;
      errors.push('require V_release > V_close (hyperpolarizing); adjusted V_release');
    }
  }
  const needMin = Math.min(config.vRelease, config.vClose, config.vThreshold, config.vRest);
  const needMax = Math.max(config.vRelease, config.vClose, config.vThreshold, config.vRest);
  if (config.vMin > needMin) {
    config.vMin = needMin;
    errors.push('V_min must not exceed the configured potentials; lowered V_min');
  }
  if (config.vMax < needMax) {
    config.vMax = needMax;
    errors.push('V_max must not be below the configured potentials; raised V_max');
  }

  return { config, errors };
}

/** Target field T(c, t) — §3.2 / §5 (global scalar, spatial field, or time-varying). */
export function targetAt(cfg, x, y, t) {
  switch (cfg.targetMode) {
    case 'gradientX': {
      const w = Math.max(1, cfg.gridWidth - 1);
      return cfg.target + cfg.targetAmplitude * ((x / w) * 2 - 1);
    }
    case 'radial': {
      const cx = (cfg.gridWidth - 1) / 2;
      const cy = (cfg.gridHeight - 1) / 2;
      const norm = Math.hypot(cx, cy) || 1;
      const r = Math.hypot(x - cx, y - cy) / norm;
      return cfg.target + cfg.targetAmplitude * (1 - 2 * r);
    }
    case 'oscillating':
      return (
        cfg.target +
        cfg.targetAmplitude * Math.sin((2 * Math.PI * t) / Math.max(2, cfg.targetPeriod))
      );
    case 'constant':
    default:
      return cfg.target;
  }
}

/** True when T does not vary across space (allows a fast path in simulation.js). */
export function isTargetSpatiallyUniform(cfg) {
  return cfg.targetMode === 'constant' || cfg.targetMode === 'oscillating';
}

/** Only the keys that differ from defaults — keeps share links / presets small. */
export function diffFromDefaults(cfg) {
  const defaults = defaultConfig();
  const out = {};
  for (const key of CONFIG_KEYS) {
    if (cfg[key] !== defaults[key]) out[key] = cfg[key];
  }
  return out;
}

export function toHashString(cfg) {
  return 'cfg=' + encodeURIComponent(JSON.stringify(diffFromDefaults(cfg)));
}

export function fromHashString(hash) {
  try {
    const raw = new URLSearchParams(String(hash || '').replace(/^#/, '')).get('cfg');
    if (!raw) return {};
    const parsed = JSON.parse(decodeURIComponent(raw));
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (err) {
    console.warn('config: could not parse URL hash', err);
    return {};
  }
}

/**
 * Observable configuration container.
 * Subscribers receive (changedKeys: string[], config: object).
 */
export class Config {
  constructor(initial = {}) {
    const { config, errors } = validateConfig({ ...defaultConfig(), ...initial });
    this._values = config;
    this.errors = errors;
    this._subscribers = new Set();
  }

  /** Read-only snapshot (do not mutate). */
  all() {
    return this._values;
  }

  get(key) {
    return this._values[key];
  }

  set(key, value) {
    if (!(key in SCHEMA)) return [];
    return this.patch({ [key]: value });
  }

  patch(partial) {
    const { config, errors } = validateConfig({ ...this._values, ...partial });
    const changed = this._diff(config);
    this.errors = errors;
    if (!changed.length) return changed;
    this._values = config;
    this._emit(changed);
    return changed;
  }

  /** Replace the whole configuration (missing keys revert to defaults). */
  loadJSON(obj) {
    const { config, errors } = validateConfig(obj);
    const changed = this._diff(config);
    this._values = config;
    this.errors = errors;
    if (changed.length) this._emit(changed);
    return errors;
  }

  toJSON() {
    return { ...this._values };
  }

  subscribe(fn) {
    this._subscribers.add(fn);
    return () => this._subscribers.delete(fn);
  }

  _diff(next) {
    return CONFIG_KEYS.filter((k) => next[k] !== this._values[k]);
  }

  _emit(changed) {
    for (const fn of [...this._subscribers]) {
      try {
        fn(changed, this._values);
      } catch (err) {
        console.error('config subscriber failed', err);
      }
    }
  }
}
