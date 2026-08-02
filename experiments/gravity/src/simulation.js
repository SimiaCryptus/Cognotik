import { add, scale, sub, len } from './vector.js';
import { computeAcceleration } from './physics.js';

export class Simulation {
  constructor(bodies, params = {}) {
    this.bodies = bodies;
    this.params = {
      G: 1.0,
      c: 30.0,
      alpha: 0.0,
      dt: 0.01,
      epsilon: 2.0,
      hermite: true,
      ...params,
    };
    this.t = 0;
    this.running = false;
    this.integrator = 'rk4'; // 'verlet' | 'rk4'
    this._initPrecession();
    this._seedHistory();
  }
  _initPrecession() {
    // Perihelion detection state for the precession meter.
    this._prevR = null;
    this._prevDR = null; // sign of dr/dt on the previous step
    this._lastPeriAngle = null; // angle of separation at last perihelion
    this.precessionPerOrbit = 0; // radians of precession per radial period
    this.totalPrecession = 0; // accumulated precession (radians)
    this._periCount = 0;
  }
  // Call after a step to update the perihelion-based precession meter.
  _updatePrecession() {
    const [a, b] = this.bodies;
    const dx = a.position.x - b.position.x;
    const dy = a.position.y - b.position.y;
    const r = Math.sqrt(dx * dx + dy * dy);
    if (this._prevR !== null) {
      const dr = r - this._prevR;
      if (this._prevDR !== null && this._prevDR < 0 && dr >= 0) {
        // Transition from approaching to receding => perihelion passed.
        const angle = Math.atan2(dy, dx);
        if (this._lastPeriAngle !== null) {
          // Smallest signed angular difference between successive perihelia.
          let d = angle - this._lastPeriAngle;
          while (d > Math.PI) d -= 2 * Math.PI;
          while (d < -Math.PI) d += 2 * Math.PI;
          this.precessionPerOrbit = d;
          this.totalPrecession += d;
          this._periCount++;
        }
        this._lastPeriAngle = angle;
      }
      this._prevDR = dr;
    }
    this._prevR = r;
  }

  _seedHistory() {
    for (const b of this.bodies) {
      b.history.clear();
      b.record(this.t);
    }
  }

  setParams(patch) {
    Object.assign(this.params, patch);
  }

  reset(bodies) {
    if (bodies) this.bodies = bodies;
    this.t = 0;
    this._initPrecession();
    this._seedHistory();
  }

  _accelerations(t) {
    const [a, b] = this.bodies;
    return [computeAcceleration(a, b, t, this.params), computeAcceleration(b, a, t, this.params)];
  }

  stepVerlet() {
    const dt = this.params.dt;
    const acc0 = this._accelerations(this.t);
    this.bodies.forEach((body, i) => {
      body.position = add(
        add(body.position, scale(body.velocity, dt)),
        scale(acc0[i], 0.5 * dt * dt)
      );
    });
    const acc1 = this._accelerations(this.t + dt);
    this.bodies.forEach((body, i) => {
      body.velocity = add(body.velocity, scale(add(acc0[i], acc1[i]), 0.5 * dt));
      body.acceleration = acc1[i];
    });
    this.t += dt;
  }

  // RK4 for the coupled retarded/relativistic system.
  stepRK4() {
    const dt = this.params.dt;
    const bodies = this.bodies;
    const state = bodies.map((b) => ({
      p: { ...b.position },
      v: { ...b.velocity },
    }));

    const deriv = (s, t) => {
      // temporarily apply state to bodies for force evaluation
      bodies.forEach((b, i) => {
        b.position = s[i].p;
        b.velocity = s[i].v;
      });
      const acc = this._accelerations(t);
      return s.map((si, i) => ({ dp: si.v, dv: acc[i] }));
    };

    const apply = (base, k, h) =>
      base.map((s, i) => ({
        p: { x: s.p.x + k[i].dp.x * h, y: s.p.y + k[i].dp.y * h },
        v: { x: s.v.x + k[i].dv.x * h, y: s.v.y + k[i].dv.y * h },
      }));

    const k1 = deriv(state, this.t);
    const k2 = deriv(apply(state, k1, dt / 2), this.t + dt / 2);
    const k3 = deriv(apply(state, k2, dt / 2), this.t + dt / 2);
    const k4 = deriv(apply(state, k3, dt), this.t + dt);

    bodies.forEach((b, i) => {
      b.position = {
        x: state[i].p.x + (dt / 6) * (k1[i].dp.x + 2 * k2[i].dp.x + 2 * k3[i].dp.x + k4[i].dp.x),
        y: state[i].p.y + (dt / 6) * (k1[i].dp.y + 2 * k2[i].dp.y + 2 * k3[i].dp.y + k4[i].dp.y),
      };
      b.velocity = {
        x: state[i].v.x + (dt / 6) * (k1[i].dv.x + 2 * k2[i].dv.x + 2 * k3[i].dv.x + k4[i].dv.x),
        y: state[i].v.y + (dt / 6) * (k1[i].dv.y + 2 * k2[i].dv.y + 2 * k3[i].dv.y + k4[i].dv.y),
      };
      b.acceleration = k1[i].dv;
    });
    this.t += dt;
  }

  step() {
    if (this.integrator === 'verlet') this.stepVerlet();
    else this.stepRK4();
    for (const b of this.bodies) b.record(this.t);
    this._updatePrecession();
  }

  // --- diagnostics ---

  totalEnergy() {
    const [a, b] = this.bodies;
    const ke = a.kineticEnergy() + b.kineticEnergy();
    const r = len(sub(a.position, b.position));
    const soft = Math.sqrt(r * r + this.params.epsilon ** 2);
    const pe = (-this.params.G * a.mass * b.mass) / soft;
    return ke + pe;
  }

  totalMomentum() {
    const [a, b] = this.bodies;
    return add(a.momentum(), b.momentum());
  }

  angularMomentum() {
    return this.bodies.reduce((sum, body) => {
      const L = body.mass * (body.position.x * body.velocity.y - body.position.y * body.velocity.x);
      return sum + L;
    }, 0);
  }
}
