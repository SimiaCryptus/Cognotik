// Simple 1-D plotting helper for phi(t) = f(x+d(t)) slices and splines.
export class Plot1D {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.resize();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || 400,
      h = rect.height || 200;
    this.canvas.width = w * dpr;
    this.canvas.height = h * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = w;
    this.h = h;
  }

  setRange(tmin, tmax, fmin, fmax) {
    this.tmin = tmin;
    this.tmax = tmax;
    this.fmin = fmin;
    this.fmax = fmax;
    const pad = (fmax - fmin) * 0.1 || 1;
    this.fmin -= pad;
    this.fmax += pad;
  }

  toX(t) {
    return ((t - this.tmin) / (this.tmax - this.tmin)) * (this.w - 40) + 30;
  }

  toY(f) {
    return this.h - 25 - ((f - this.fmin) / (this.fmax - this.fmin)) * (this.h - 45);
  }

  clear() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.w, this.h);
    ctx.strokeStyle = '#ccc';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(30, 10);
    ctx.lineTo(30, this.h - 25);
    ctx.lineTo(this.w - 10, this.h - 25);
    ctx.stroke();
    ctx.fillStyle = '#889';
    ctx.font = '11px monospace';
    ctx.fillText('t', this.w - 18, this.h - 10);
    ctx.fillText('φ(t)', 4, 14);
  }

  curve(fn, color, samples = 120, dash = null) {
    const ctx = this.ctx;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    for (let i = 0; i <= samples; i++) {
      const t = this.tmin + (i / samples) * (this.tmax - this.tmin);
      const f = fn(t);
      if (Number.isNaN(f)) continue;
      const x = this.toX(t),
        y = this.toY(f);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
    ctx.restore();
  }

  line(t0, f0, t1, f1, color, dash = [4, 4]) {
    const ctx = this.ctx;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    ctx.moveTo(this.toX(t0), this.toY(f0));
    ctx.lineTo(this.toX(t1), this.toY(f1));
    ctx.stroke();
    ctx.restore();
  }

  dot(t, f, color, r = 4) {
    const ctx = this.ctx;
    ctx.save();
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(this.toX(t), this.toY(f), r, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  // draw a small tangent stub of slope m at (t,f)
  tangentStub(t, f, m, color, len = 0.08) {
    const t0 = t - len,
      t1 = t + len;
    this.line(t0, f - m * len, t1, f + m * len, color, null);
  }
}
