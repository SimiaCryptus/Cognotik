/**
 * membrane.js — the per-cell bioelectrical membrane mechanism
 * (bioelectrical.md §3.4–§3.6 and steps 2–3 of §4).
 *
 * Deliberately knows nothing about grid topology, rendering, or
 * discretisation. It is a pure map
 *   (V, gate, openTicks, restTicks, neighbourSum, stimulus, params)
 *     -> (V', gate', openTicks', restTicks')
 * mirroring exactly the contract controller.js holds in the PID domain,
 * which is what makes the two domains swappable.
 */

export const GATE_CLOSED = 0;
export const GATE_OPEN = 1;
export const GATE_REFRACTORY = 2;

export const GATE_NAMES = ['CLOSED', 'OPEN', 'REFRACTORY'];

/** Hard clamp on the membrane potential (§3.4). */
export function clampVoltage(v, cfg) {
  if (v < cfg.vMin) return cfg.vMin;
  if (v > cfg.vMax) return cfg.vMax;
  return v;
}

/** Voltage comparator, polarity-aware (§3.3). */
export function comparatorTrips(V, cfg) {
  return cfg.polarity === 'hyperpolarizing' ? V <= cfg.vThreshold : V >= cfg.vThreshold;
}

/** Close condition: minimum open time AND (optionally) hysteresis retreat (§3.6). */
export function closeCondition(openTicks, V, cfg) {
  if (openTicks < cfg.minOpenTicks) return false;
  if (cfg.closeMode === 'timed') return true;
  return cfg.polarity === 'hyperpolarizing' ? V >= cfg.vClose : V <= cfg.vClose;
}

/**
 * Reusable input record. `leak` may be null (use k_leak · (V_rest − V)) or a
 * number supplied by an external homeostat (the `pid-homeostat` mode, §6.3).
 * `clamp` may be null or a voltage the cell is pinned to.
 */
export function makeMembraneInput() {
  return {
    V: 0,
    gate: GATE_CLOSED,
    openTicks: 0,
    restTicks: 0,
    neighborSum: 0,
    stimulus: 0,
    noise: 0,
    leak: null,
    clamp: null,
  };
}

export function makeMembraneOutput() {
  return {
    V: 0,
    gate: GATE_CLOSED,
    openTicks: 0,
    restTicks: 0,
    leak: 0,
    coupling: 0,
    release: 0,
    dV: 0,
  };
}

/**
 * One membrane update for a single cell.
 *
 * Ordering is load-bearing (§4): the voltage integrates first, and the gate
 * is then advanced against the *new* voltage, so a cell that crosses
 * threshold this tick only begins releasing on the next one. That one-tick
 * conduction delay per cell is what sets wave speed.
 */
export function membraneStep(input, cfg, out) {
  const result = out || makeMembraneOutput();
  const V = input.V;

  // ---- 2. INTEGRATE VOLTAGE -------------------------------------------
  const leak =
    input.leak === null || input.leak === undefined ? cfg.kLeak * (cfg.vRest - V) : input.leak;
  const coupling = cfg.kCoupling * input.neighborSum;
  const release = input.gate === GATE_OPEN ? cfg.kRelease * (cfg.vRelease - V) : 0;
  const dV = leak + coupling + release + (input.stimulus || 0) + (input.noise || 0);

  let Vn =
    input.clamp === null || input.clamp === undefined
      ? clampVoltage(V + dV, cfg)
      : clampVoltage(input.clamp, cfg);

  // ---- 3. ADVANCE GATE (evaluated against V_next) ----------------------
  let gate = input.gate;
  let openTicks = input.openTicks;
  let restTicks = input.restTicks;

  if (gate === GATE_OPEN) {
    const ot = openTicks + 1;
    if (closeCondition(ot, Vn, cfg)) {
      openTicks = 0;
      if (cfg.resetPeriod > 0) {
        gate = GATE_REFRACTORY;
        restTicks = cfg.resetPeriod;
      } else {
        // resetPeriod = 0 disables refractoriness: re-arm immediately (§3.5)
        gate = GATE_CLOSED;
        restTicks = 0;
      }
    } else {
      openTicks = ot;
    }
  } else if (gate === GATE_REFRACTORY) {
    const rt = restTicks - 1;
    if (rt <= 0) {
      gate = GATE_CLOSED;
      restTicks = 0;
    } else {
      restTicks = rt;
    }
  } else if (comparatorTrips(Vn, cfg)) {
    gate = GATE_OPEN;
    openTicks = 0;
    restTicks = 0;
  }

  result.V = Vn;
  result.gate = gate;
  result.openTicks = openTicks;
  result.restTicks = restTicks;
  result.leak = leak;
  result.coupling = coupling;
  result.release = release;
  result.dV = dV;
  return result;
}

export default membraneStep;
