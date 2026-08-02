export class GD {
  constructor(params = {}) {
    this.name = 'GD';
    this.params = { lr: params.lr ?? 0.05 };
    this.x = 0;
    this.y = 0;
  }

  reset(x0, y0) {
    this.x = x0;
    this.y = y0;
  }

  step(obj) {
    const from = [this.x, this.y];
    const grad = obj.grad(this.x, this.y);
    const lr = this.params.lr;
    this.x -= lr * grad[0];
    this.y -= lr * grad[1];
    return { from, to: [this.x, this.y], grad };
  }

  getState() {
    return { pos: [this.x, this.y] };
  }
}
