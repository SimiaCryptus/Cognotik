/**
 * simulation.js — one discrete timestep per invocation + playback scheduling (§7.5, §8).
 *
 * Guarantees synchronous update semantics: every cell reads only the frozen
 * previous-step buffers, and all writes land in the "next" buffers, which are
 * swapped atomically at the end of the step.
 */

import { SCHEMA, targetAt, isTargetSpatiallyUniform } from './config.js';
import {
  Grid,
  populate,
  populateMembrane,
  neighborOffsets,
  countActiveNeighbors,
  sumNeighborVoltageDelta,
  makeActivePredicate,
  createRng,
  makeGaussianSampler,
} from './grid.js';
import { pidStep } from './controller.js';
import { expressState, expressBioelectrical } from './stateExpression.js';
import { membraneStep, makeMembraneInput, makeMembraneOutput } from './membrane.js';

export class Simulation {
  constructor(config) {
    this.config = config;
    const cfg = config.all();

    this.grid = new Grid(cfg.gridWidth, cfg.gridHeight);
    this.time = 0;
    this.running = false;
    this.measuredRate = 0;
    this.stats = {
      step: 0,
      activeFraction: 0,
      meanAbsError: 0,
      energy: 0,
      meanIntegral: 0,
      firingFraction: 0,
      refractoryFraction: 0,
      meanV: 0,
    };

    this._listeners = Object.create(null);
    this._out = { p: 0, i: 0, d: 0, u: 0, error: 0 };
    this._mIn = makeMembraneInput();
    this._mOut = makeMembraneOutput();
    this._accumulator = 0;
    this._lastFrame = 0;
    this._raf = null;
    this._rateSteps = 0;
    this._rateT0 = 0;
    this._tick = this._tick.bind(this);

    this._unsubscribe = config.subscribe((changed) => this._onConfigChange(changed));
    this.reset();
  }

  // ------------------------------------------------------------- event bus
  on(event, fn) {
    const set = this._listeners[event] || (this._listeners[event] = new Set());
    set.add(fn);
    return () => set.delete(fn);
  }

  emit(event, payload) {
    const set = this._listeners[event];
    if (!set) return;
    for (const fn of [...set]) {
      try {
        fn(payload, this);
      } catch (err) {
        console.error('simulation listener failed', err);
      }
    }
  }

  // ------------------------------------------------------- config reaction
  _onConfigChange(changed) {
    const structural = changed.some((k) => SCHEMA[k] && SCHEMA[k].structural);
    if (changed.includes('stateCardinality')) {
      this.grid.clampStates(this.config.get('stateCardinality'));
    }
    this._refreshDerived();
    if (structural) this.reset();
    else this.emit('change', changed);
  }

  _refreshDerived() {
    const cfg = this.config.all();
    this.offsets = neighborOffsets(cfg.neighborhood, cfg.radius);
    this.isActive = makeActivePredicate(cfg.activePredicate, cfg.stateCardinality);
    this.maxNeighbors = this.offsets.length / 2;
  }

  // -------------------------------------------------------------- lifecycle
  reset() {
    const cfg = this.config.all();
    if (this.grid.width !== cfg.gridWidth || this.grid.height !== cfg.gridHeight) {
      this.grid.resize(cfg.gridWidth, cfg.gridHeight);
    }
    this._refreshDerived();
    this.rng = createRng(cfg.seed);
    this.gaussian = makeGaussianSampler(this.rng);
    this.grid.clearControllerState();
    if (cfg.mode === 'pid') {
      populate(this.grid, cfg, this.rng);
      this._seedControllerState();
    } else {
      populateMembrane(this.grid, cfg, this.rng);
      this._seedMembraneState();
    }
    this.time = 0;
    this._accumulator = 0;
    this._measure();
    this.emit('reset');
  }

  /** Empty the grid and its controller memory without touching the config. */
  clear() {
    const cfg = this.config.all();
    this.grid.clearStates();
    this.grid.clearControllerState();
    if (cfg.mode === 'pid') {
      this._seedControllerState();
    } else {
      this.grid.clearMembrane(cfg.vRest);
      this._seedMembraneState();
    }
    this.time = 0;
    this._measure();
    this.emit('reset');
  }

  /** Seed e_(t-1) from the initial neighbourhood so the first D_t term is 0. */
  _seedControllerState() {
    const cfg = this.config.all();
    const g = this.grid;
    const uniform = isTargetSpatiallyUniform(cfg) ? targetAt(cfg, 0, 0, 0) : null;
    const gaussian = cfg.perturbInit === 'normal' ? makeGaussianSampler(this.rng) : null;
    for (let y = 0; y < g.height; y++) {
      const row = y * g.width;
      for (let x = 0; x < g.width; x++) {
        const idx = row + x;
        const n = countActiveNeighbors(
          g,
          g.states,
          x,
          y,
          this.offsets,
          cfg.boundary,
          this.isActive
        );
        const T = uniform !== null ? uniform : targetAt(cfg, x, y, 0);
        const e = T - n;
        let prevError = e;
        let integral = 0;
        if (gaussian) {
          prevError += gaussian() * cfg.perturbSigma;
          integral += gaussian() * cfg.perturbSigma;
        }
        g.prevError[idx] = prevError;
        g.error[idx] = e;
        g.integral[idx] = integral;
        g.u[idx] = 0;
      }
    }
  }
  /**
   * Derive the initial display state from (V, gate), and — in pid-homeostat
   * mode — seed e_(t-1) so the first derivative term is zero.
   */
  _seedMembraneState() {
    const cfg = this.config.all();
    const g = this.grid;
    const homeostat = cfg.mode === 'pid-homeostat';
    for (let i = 0; i < g.size; i++) {
      g.states[i] = expressBioelectrical(g.V[i], g.gate[i]);
      g.nextStates[i] = g.states[i];
      if (homeostat) {
        const e = cfg.vTarget - g.V[i];
        g.prevError[i] = e;
        g.nextPrevError[i] = e;
        g.error[i] = e;
        g.integral[i] = 0;
        g.nextIntegral[i] = 0;
        g.u[i] = 0;
      }
    }
  }

  /** Live intervention / painting (§7.7). Controller memory is preserved. */
  paintCell(x, y, value) {
    this.grid.setState(x, y, value);
    this.emit('paint', { x, y, value });
  }
  // ------------------------------------------- membrane-domain interventions
  /** Write into the stimulus field (bioelectrical.md §7 "paint stimulus"). */
  paintStimulus(x, y, amount) {
    const g = this.grid;
    if (!g.inBounds(x, y)) return;
    g.stimulus[g.index(x, y)] = amount;
    this.emit('paint', { x, y, value: amount });
  }
  /** Directly set a cell's membrane potential (manual excitation). */
  paintVoltage(x, y, V) {
    const cfg = this.config.all();
    const g = this.grid;
    if (!g.inBounds(x, y)) return;
    const idx = g.index(x, y);
    g.V[idx] = V < cfg.vMin ? cfg.vMin : V > cfg.vMax ? cfg.vMax : V;
    this.emit('paint', { x, y, value: g.V[idx] });
  }
  /** Pin / unpin a cell's potential — pacemakers, boundaries, conduction block. */
  paintClamp(x, y, on, V) {
    const cfg = this.config.all();
    const g = this.grid;
    if (!g.inBounds(x, y)) return;
    const idx = g.index(x, y);
    if (on) {
      const v = V < cfg.vMin ? cfg.vMin : V > cfg.vMax ? cfg.vMax : V;
      g.clamped[idx] = 1;
      g.clampV[idx] = v;
      g.V[idx] = v;
    } else {
      g.clamped[idx] = 0;
      g.clampV[idx] = cfg.vRest;
    }
    this.emit('paint', { x, y, value: g.clamped[idx] });
  }

  // -------------------------------------------------------------- one step
  step() {
    const cfg = this.config.all();
    if (cfg.mode === 'pid') this._stepPid(cfg);
    else this._stepMembrane(cfg);
    this.time++;
    this._measure();
    this._trackRate();
    this.emit('step');
  }
  _stepPid(cfg) {
    const g = this.grid;

    const st = g.states,
      pe = g.prevError,
      ig = g.integral;
    const nst = g.nextStates,
      npe = g.nextPrevError,
      nig = g.nextIntegral;
    const uBuf = g.u,
      eBuf = g.error;

    const offsets = this.offsets;
    const isActive = this.isActive;
    const boundary = cfg.boundary;
    const expression = cfg.expression;
    const out = this._out;
    const rng = this.rng;
    const t = this.time;
    const uniformTarget = isTargetSpatiallyUniform(cfg) ? targetAt(cfg, 0, 0, t) : null;

    for (let y = 0; y < g.height; y++) {
      const row = y * g.width;
      for (let x = 0; x < g.width; x++) {
        const idx = row + x;

        // (a) neighbour count from the frozen snapshot
        const n = countActiveNeighbors(g, st, x, y, offsets, boundary, isActive);

        // (b) error + PID terms
        const T = uniformTarget !== null ? uniformTarget : targetAt(cfg, x, y, t);
        const e = T - n;
        pidStep(pe[idx], ig[idx], e, cfg, out);

        // (c) discretise u_t into the next expressed state
        nst[idx] = expressState(expression, out.u, out.p, out.i, out.d, cfg, rng);

        // carry forward controller memory + diagnostics
        npe[idx] = e;
        nig[idx] = out.i;
        uBuf[idx] = out.u;
        eBuf[idx] = e;
      }
    }

    // (d) atomic buffer swap
    g.commit();
  }
  /**
   * Bioelectrical membrane step (bioelectrical.md §4). Neighbour voltages are
   * read from the frozen snapshot only; no cell observes another cell's gate.
   */
  _stepMembrane(cfg) {
    const g = this.grid;
    const V = g.V,
      gate = g.gate,
      ot = g.openTicks,
      rt = g.restTicks;
    const nV = g.nextV,
      nGate = g.nextGate,
      nOt = g.nextOpenTicks,
      nRt = g.nextRestTicks;
    const offsets = this.offsets;
    const boundary = cfg.boundary;
    const input = this._mIn;
    const out = this._mOut;
    const pidOut = this._out;
    const homeostat = cfg.mode === 'pid-homeostat';
    const noisy = cfg.noiseAmplitude > 0;
    const gaussian = this.gaussian;
    const pulse = cfg.stimulusMode === 'pulse';
    for (let y = 0; y < g.height; y++) {
      const row = y * g.width;
      for (let x = 0; x < g.width; x++) {
        const idx = row + x;
        // 1. SENSE
        input.V = V[idx];
        input.gate = gate[idx];
        input.openTicks = ot[idx];
        input.restTicks = rt[idx];
        input.neighborSum = sumNeighborVoltageDelta(g, V, x, y, offsets, boundary);
        input.stimulus = g.stimulus[idx];
        input.noise = noisy ? gaussian() * cfg.noiseAmplitude : 0;
        input.clamp = g.clamped[idx] ? g.clampV[idx] : null;
        // optional homeostat: u_t replaces the fixed leak term (§6.3)
        if (homeostat) {
          const e = cfg.vTarget - input.V;
          pidStep(g.prevError[idx], g.integral[idx], e, cfg, pidOut);
          input.leak = pidOut.u;
          g.nextPrevError[idx] = e;
          g.nextIntegral[idx] = pidOut.i;
          g.error[idx] = e;
          g.u[idx] = pidOut.u;
        } else {
          input.leak = null;
        }
        // 2. INTEGRATE + 3. ADVANCE GATE
        membraneStep(input, cfg, out);
        nV[idx] = out.V;
        nGate[idx] = out.gate;
        nOt[idx] = out.openTicks;
        nRt[idx] = out.restTicks;
        // 4. EXPRESS
        g.nextStates[idx] = expressBioelectrical(out.V, out.gate);
        // painted stimulus is consumed unless explicitly held
        if (pulse && input.stimulus !== 0) g.stimulus[idx] = 0;
      }
    }
    g.commit();
  }

  _measure() {
    if (this.config.get('mode') !== 'pid') {
      this._measureMembrane();
      return;
    }
    const g = this.grid;
    const isActive = this.isActive;
    let active = 0,
      sumAbs = 0,
      sumSq = 0,
      sumI = 0;
    for (let i = 0; i < g.size; i++) {
      if (isActive(g.states[i])) active++;
      const e = g.error[i];
      sumAbs += Math.abs(e);
      sumSq += e * e;
      sumI += g.integral[i];
    }
    this.stats = {
      step: this.time,
      activeFraction: active / g.size,
      meanAbsError: sumAbs / g.size,
      energy: sumSq,
      meanIntegral: sumI / g.size,
      firingFraction: 0,
      refractoryFraction: 0,
      meanV: 0,
    };
  }
  _measureMembrane() {
    const g = this.grid;
    let open = 0,
      refractory = 0,
      sumV = 0,
      sumI = 0;
    for (let i = 0; i < g.size; i++) {
      const gate = g.gate[i];
      if (gate === 1) open++;
      else if (gate === 2) refractory++;
      sumV += g.V[i];
      sumI += g.integral[i];
    }
    this.stats = {
      step: this.time,
      activeFraction: open / g.size,
      firingFraction: open / g.size,
      refractoryFraction: refractory / g.size,
      meanV: sumV / g.size,
      meanAbsError: 0,
      energy: 0,
      meanIntegral: sumI / g.size,
    };
  }

  _trackRate() {
    const now = performance.now();
    if (this._rateT0 === 0) {
      this._rateT0 = now;
      this._rateSteps = 0;
    }
    this._rateSteps++;
    const dt = now - this._rateT0;
    if (dt >= 500) {
      this.measuredRate = (this._rateSteps * 1000) / dt;
      this._rateSteps = 0;
      this._rateT0 = now;
    }
  }

  // --------------------------------------------------------------- playback
  play() {
    if (this.running) return;
    this.running = true;
    this._lastFrame = performance.now();
    this._accumulator = 0;
    this._rateT0 = 0;
    this._raf = requestAnimationFrame(this._tick);
    this.emit('running', true);
  }

  pause() {
    if (!this.running) return;
    this.running = false;
    if (this._raf !== null) cancelAnimationFrame(this._raf);
    this._raf = null;
    this.measuredRate = 0;
    this.emit('running', false);
  }

  toggle() {
    if (this.running) this.pause();
    else this.play();
  }

  _tick(now) {
    if (!this.running) return;
    const cfg = this.config.all();
    const dt = Math.min(0.25, (now - this._lastFrame) / 1000);
    this._lastFrame = now;
    this._accumulator += dt * cfg.stepsPerSecond;

    let steps = Math.floor(this._accumulator);
    if (steps > cfg.maxStepsPerFrame) {
      steps = cfg.maxStepsPerFrame;
      this._accumulator = 0;
    } else {
      this._accumulator -= steps;
    }
    for (let k = 0; k < steps; k++) this.step();

    this._raf = requestAnimationFrame(this._tick);
  }

  dispose() {
    this.pause();
    if (this._unsubscribe) this._unsubscribe();
    this._listeners = Object.create(null);
  }
}
