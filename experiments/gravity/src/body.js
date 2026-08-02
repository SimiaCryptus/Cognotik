import { clone, vec } from './vector.js';
import { StateHistory } from './history.js';

let _id = 0;

export class Body {
  constructor({ position, velocity, mass, color = '#5ad1ff', radius = 8 }) {
    this.id = _id++;
    this.position = clone(position);
    this.velocity = clone(velocity);
    this.mass = mass;
    this.color = color;
    this.radius = radius;
    this.history = new StateHistory();
    this.acceleration = vec(0, 0);
  }

  record(t) {
    this.history.record(t, this.position, this.velocity);
  }

  reset(position, velocity) {
    this.position = clone(position);
    this.velocity = clone(velocity);
    this.acceleration = vec(0, 0);
    this.history.clear();
  }

  momentum() {
    return { x: this.mass * this.velocity.x, y: this.mass * this.velocity.y };
  }

  kineticEnergy() {
    const v2 = this.velocity.x ** 2 + this.velocity.y ** 2;
    return 0.5 * this.mass * v2;
  }
}
