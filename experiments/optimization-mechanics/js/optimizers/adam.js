export class Adam {
  constructor(params = {}) {
    this.name = 'ADAM';
    this.params = {
      lr: params.lr ?? 0.5,
      b1: params.b1 ?? 0.9,
      b2: params.b2 ?? 0.999,
      eps: params.eps ?? 1e-8,
    };
    this.reset(0, 0);
  }

  reset(x0, y0) {
    this.x = x0;
    this.y = y0;
    this.m = [0, 0];
    this.v = [0, 0];
    this.t = 0;
  }

  step(obj) {
    const from = [this.x, this.y];
    const grad = obj.grad(this.x, this.y);
    const { lr, b1, b2, eps } = this.params;
    this.t++;
    for (let i = 0; i < 2; i++) {
      this.m[i] = b1 * this.m[i] + (1 - b1) * grad[i];
      this.v[i] = b2 * this.v[i] + (1 - b2) * grad[i] ** 2;
    }
    const mh = [this.m[0] / (1 - b1 ** this.t), this.m[1] / (1 - b1 ** this.t)];
    const vh = [this.v[0] / (1 - b2 ** this.t), this.v[1] / (1 - b2 ** this.t)];
    this.x -= (lr * mh[0]) / (Math.sqrt(vh[0]) + eps);
    this.y -= (lr * mh[1]) / (Math.sqrt(vh[1]) + eps);
    return { from, to: [this.x, this.y], grad, moments: { m: this.m.slice(), v: this.v.slice() } };
  }

  getState() {
    return { pos: [this.x, this.y], m: this.m, v: this.v };
  }
}
