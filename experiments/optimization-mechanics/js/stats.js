// Running gradient statistics accumulators.

export class Stats {
  constructor(historyLen = 60) {
    this.historyLen = historyLen;
    this.reset();
  }

  reset() {
    this.cur = [0, 0];
    this.sum = [0, 0]; // Σ grad
    this.sumSq = [0, 0]; // Σ grad²
    this.n = 0;
    this.history = []; // array of [gx, gy]
  }

  update(grad) {
    this.cur = grad.slice();
    this.sum[0] += grad[0];
    this.sum[1] += grad[1];
    this.sumSq[0] += grad[0] ** 2;
    this.sumSq[1] += grad[1] ** 2;
    this.n++;
    this.history.push(grad.slice());
    if (this.history.length > this.historyLen) this.history.shift();
  }

  mean() {
    if (this.n === 0) return [0, 0];
    return [this.sum[0] / this.n, this.sum[1] / this.n];
  }

  meanSq() {
    if (this.n === 0) return [0, 0];
    return [this.sumSq[0] / this.n, this.sumSq[1] / this.n];
  }
}
