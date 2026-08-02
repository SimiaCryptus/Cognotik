// Shared state schema + pure-ish advance reducer for deterministic replay.
import { LANDSCAPES } from './landscape.js';
import { qqnStep } from './qqn-path.js';
import { inverseHessianDir } from './optimizers.js';

export function makeInitialState({ landscapeKey = 'rosenbrock', start = null, config = {} } = {}) {
  const land = LANDSCAPES[landscapeKey];
  const s = start || land.start;
  return {
    landscapeKey,
    start: s.slice(),
    config: {
      eta: 0.01,
      seed: 1,
      lineSearch: 'armijo',
      spline: false,
      oracle: 'lbfgs',
      region: 'none',
      ...config,
    },
    iterateHistory: [s.slice()],
    probeHistory: [],
    tick: 0,
  };
}

// Pure QQN advance: given state -> next state (adds one iterate).
export function advance(state) {
  const land = LANDSCAPES[state.landscapeKey];
  const x = state.iterateHistory[state.iterateHistory.length - 1];
  const { next, probes, t, gradDir, oracleDir } = qqnStep(land, x, inverseHessianDir, {});
  return {
    ...state,
    iterateHistory: [...state.iterateHistory, next],
    probeHistory: [...state.probeHistory, { probes, t, gradDir, oracleDir, x }],
    tick: state.tick + 1,
  };
}

export function reset(state, start) {
  const s = start || state.start;
  return {
    ...state,
    start: s.slice(),
    iterateHistory: [s.slice()],
    probeHistory: [],
    tick: 0,
  };
}
