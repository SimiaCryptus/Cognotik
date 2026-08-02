import { lerp, clone } from './vector.js';

// Ring buffer of { t, position, velocity } samples with interpolation.
export class StateHistory {
  constructor(capacity = 4096) {
    this.capacity = capacity;
    this.buffer = new Array(capacity);
    this.head = 0; // index of next write
    this.count = 0;
  }

  clear() {
    this.head = 0;
    this.count = 0;
  }

  record(t, position, velocity) {
    this.buffer[this.head] = {
      t,
      position: clone(position),
      velocity: clone(velocity),
    };
    this.head = (this.head + 1) % this.capacity;
    if (this.count < this.capacity) this.count++;
  }

  // Logical index 0 == oldest sample.
  _at(i) {
    const start = (this.head - this.count + this.capacity) % this.capacity;
    return this.buffer[(start + i) % this.capacity];
  }

  oldest() {
    return this.count ? this._at(0) : null;
  }

  newest() {
    return this.count ? this._at(this.count - 1) : null;
  }

  // Binary search for the latest sample with time <= t.
  _findIndex(t) {
    let lo = 0;
    let hi = this.count - 1;
    let result = 0;
    while (lo <= hi) {
      const mid = (lo + hi) >> 1;
      if (this._at(mid).t <= t) {
        result = mid;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return result;
  }

  // Interpolate position (and velocity) at past time t.
  // Defaults to linear; uses cubic Hermite when useHermite is true.
  interpolate(t, useHermite = false) {
    if (this.count === 0) return null;
    if (this.count === 1) return clone(this._at(0).position);

    const oldest = this._at(0);
    const newest = this._at(this.count - 1);
    if (t <= oldest.t) return clone(oldest.position);
    if (t >= newest.t) return clone(newest.position);

    const i = this._findIndex(t);
    const a = this._at(i);
    const b = this._at(Math.min(i + 1, this.count - 1));
    const dt = b.t - a.t;
    if (dt === 0) return clone(a.position);
    const u = (t - a.t) / dt;

    if (!useHermite) return lerp(a.position, b.position, u);

    // Cubic Hermite using stored velocities as tangents.
    const u2 = u * u;
    const u3 = u2 * u;
    const h00 = 2 * u3 - 3 * u2 + 1;
    const h10 = u3 - 2 * u2 + u;
    const h01 = -2 * u3 + 3 * u2;
    const h11 = u3 - u2;
    return {
      x:
        h00 * a.position.x + h10 * dt * a.velocity.x + h01 * b.position.x + h11 * dt * b.velocity.x,
      y:
        h00 * a.position.y + h10 * dt * a.velocity.y + h01 * b.position.y + h11 * dt * b.velocity.y,
    };
  }
  // Interpolate velocity at past time t. Defaults to linear; uses the
  // analytic derivative of the cubic Hermite spline when useHermite is true.
  interpolateVelocity(t, useHermite = false) {
    if (this.count === 0) return null;
    if (this.count === 1) return clone(this._at(0).velocity);
    const oldest = this._at(0);
    const newest = this._at(this.count - 1);
    if (t <= oldest.t) return clone(oldest.velocity);
    if (t >= newest.t) return clone(newest.velocity);
    const i = this._findIndex(t);
    const a = this._at(i);
    const b = this._at(Math.min(i + 1, this.count - 1));
    const dt = b.t - a.t;
    if (dt === 0) return clone(a.velocity);
    const u = (t - a.t) / dt;
    if (!useHermite) return lerp(a.velocity, b.velocity, u);
    // Derivative of cubic Hermite w.r.t. t (= d/du / dt).
    const u2 = u * u;
    const dh00 = 6 * u2 - 6 * u;
    const dh10 = 3 * u2 - 4 * u + 1;
    const dh01 = -6 * u2 + 6 * u;
    const dh11 = 3 * u2 - 2 * u;
    return {
      x:
        (dh00 * a.position.x +
          dh10 * dt * a.velocity.x +
          dh01 * b.position.x +
          dh11 * dt * b.velocity.x) /
        dt,
      y:
        (dh00 * a.position.y +
          dh10 * dt * a.velocity.y +
          dh01 * b.position.y +
          dh11 * dt * b.velocity.y) /
        dt,
    };
  }
  // Combined position + velocity interpolation in a single search to avoid
  // redundant binary searches on hot paths (retarded-time solving).
  interpolateState(t, useHermite = false) {
    if (this.count === 0) return null;
    if (this.count === 1) {
      const s = this._at(0);
      return { position: clone(s.position), velocity: clone(s.velocity) };
    }
    const oldest = this._at(0);
    const newest = this._at(this.count - 1);
    if (t <= oldest.t)
      return { position: clone(oldest.position), velocity: clone(oldest.velocity) };
    if (t >= newest.t)
      return { position: clone(newest.position), velocity: clone(newest.velocity) };
    const i = this._findIndex(t);
    const a = this._at(i);
    const b = this._at(Math.min(i + 1, this.count - 1));
    const dt = b.t - a.t;
    if (dt === 0) return { position: clone(a.position), velocity: clone(a.velocity) };
    const u = (t - a.t) / dt;
    if (!useHermite) {
      return {
        position: lerp(a.position, b.position, u),
        velocity: lerp(a.velocity, b.velocity, u),
      };
    }
    const u2 = u * u;
    const u3 = u2 * u;
    const h00 = 2 * u3 - 3 * u2 + 1;
    const h10 = u3 - 2 * u2 + u;
    const h01 = -2 * u3 + 3 * u2;
    const h11 = u3 - u2;
    const dh00 = 6 * u2 - 6 * u;
    const dh10 = 3 * u2 - 4 * u + 1;
    const dh01 = -6 * u2 + 6 * u;
    const dh11 = 3 * u2 - 2 * u;
    return {
      position: {
        x:
          h00 * a.position.x +
          h10 * dt * a.velocity.x +
          h01 * b.position.x +
          h11 * dt * b.velocity.x,
        y:
          h00 * a.position.y +
          h10 * dt * a.velocity.y +
          h01 * b.position.y +
          h11 * dt * b.velocity.y,
      },
      velocity: {
        x:
          (dh00 * a.position.x +
            dh10 * dt * a.velocity.x +
            dh01 * b.position.x +
            dh11 * dt * b.velocity.x) /
          dt,
        y:
          (dh00 * a.position.y +
            dh10 * dt * a.velocity.y +
            dh01 * b.position.y +
            dh11 * dt * b.velocity.y) /
          dt,
      },
    };
  }
}
