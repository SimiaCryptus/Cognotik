// Canvas viewport + drawing utilities.

export class View {
  constructor(canvas, lossCanvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.lossCanvas = lossCanvas;
    this.lctx = lossCanvas.getContext('2d');
    this.world = { cx: 0, cy: 0, scale: 200 };
    this.resize();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = this.canvas.clientWidth * dpr;
    this.canvas.height = this.canvas.clientHeight * dpr;
    this.ctx.setTransform(1, 0, 0, 1, 0, 0);
    this.lossCanvas.width = this.lossCanvas.clientWidth * dpr;
    this.lossCanvas.height = this.lossCanvas.clientHeight * dpr;
  }

  w2s(x, y) {
    return [
      this.canvas.width / 2 + (x - this.world.cx) * this.world.scale,
      this.canvas.height / 2 - (y - this.world.cy) * this.world.scale,
    ];
  }
  s2w(sx, sy) {
    return [
      (sx - this.canvas.width / 2) / this.world.scale + this.world.cx,
      -(sy - this.canvas.height / 2) / this.world.scale + this.world.cy,
    ];
  }

  drawGrid() {
    const ctx = this.ctx,
      c = this.canvas;
    ctx.fillStyle = '#0a0d12';
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.strokeStyle = '#161b22';
    ctx.lineWidth = 1;
    const step = 0.25;
    const [x0, y0] = this.s2w(0, c.height);
    const [x1, y1] = this.s2w(c.width, 0);
    ctx.beginPath();
    for (let x = Math.floor(x0 / step) * step; x <= x1; x += step) {
      const [sx] = this.w2s(x, 0);
      ctx.moveTo(sx, 0);
      ctx.lineTo(sx, c.height);
    }
    for (let y = Math.floor(y0 / step) * step; y <= y1; y += step) {
      const [, sy] = this.w2s(0, y);
      ctx.moveTo(0, sy);
      ctx.lineTo(c.width, sy);
    }
    ctx.stroke();
    ctx.strokeStyle = '#30363d';
    ctx.beginPath();
    const [, sy0] = this.w2s(0, 0);
    const [sx0] = this.w2s(0, 0);
    ctx.moveTo(0, sy0);
    ctx.lineTo(c.width, sy0);
    ctx.moveTo(sx0, 0);
    ctx.lineTo(sx0, c.height);
    ctx.stroke();
  }

  /**
   * Draw a cloud of points with a glow halo + crisp core, so dense fractals
   * stay legible at small radii. `r` is the core radius (px).
   */
  drawPoints(pts, color, r) {
    if (!pts.length) return;
    const ctx = this.ctx;
    const dpr = window.devicePixelRatio || 1;
    // Halo pass (low alpha, larger)
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.fillStyle = color;
    ctx.globalAlpha = 0.35;
    const haloR = Math.max(r * 1.8, 2.5 * dpr);
    for (let i = 0; i < pts.length; i++) {
      const [sx, sy] = this.w2s(pts[i][0], pts[i][1]);
      ctx.beginPath();
      ctx.arc(sx, sy, haloR, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
    // Core pass (full alpha, small)
    ctx.fillStyle = color;
    const coreR = Math.max(r, 1.2 * dpr);
    for (let i = 0; i < pts.length; i++) {
      const [sx, sy] = this.w2s(pts[i][0], pts[i][1]);
      ctx.beginPath();
      ctx.arc(sx, sy, coreR, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  /**
   * For each transform draw:
   *   - the fixed point (filled dot)
   *   - the image of the unit basis vectors anchored at the fixed point:
   *       red-ish arrow = T(e_x) - p*    (column 1 of A)
   *       green-ish arrow = T(e_y) - p*  (column 2 of A)
   *     i.e. these are the two columns of A drawn at p*. Their lengths show
   *     scaling, their angles show rotation/shear, and a parallelogram outline
   *     conveys det(A) (area, with reflected orientation if det<0).
   */
  drawFixedPoints(transforms) {
    const ctx = this.ctx;
    const dpr = window.devicePixelRatio || 1;
    for (let k = 0; k < transforms.length; k++) {
      const { A, b, frozen } = transforms[k];
      const m11 = 1 - A[0],
        m12 = -A[1],
        m21 = -A[2],
        m22 = 1 - A[3];
      const det = m11 * m22 - m12 * m21;
      if (Math.abs(det) < 1e-6) continue;
      const fx = (m22 * b[0] - m12 * b[1]) / det;
      const fy = (-m21 * b[0] + m11 * b[1]) / det;

      // Image of e_x = (A[0], A[2]); image of e_y = (A[1], A[3])
      const ex = [A[0], A[2]];
      const ey = [A[1], A[3]];

      const [sx, sy] = this.w2s(fx, fy);
      const [sxx, syx] = this.w2s(fx + ex[0], fy + ex[1]);
      const [sxy, syy] = this.w2s(fx + ey[0], fy + ey[1]);
      const [spx, spy] = this.w2s(fx + ex[0] + ey[0], fy + ex[1] + ey[1]);

      const baseColor = frozen ? '#d29922' : '#ff7b72';
      const exColor = frozen ? '#f0b429' : '#ff9d8a'; // x-basis (along A col1)
      const eyColor = frozen ? '#b8860b' : '#ff5f74'; // y-basis (along A col2)

      // Parallelogram outline (image of unit square under A, translated to p*)
      ctx.save();
      ctx.lineWidth = 1 * dpr;
      ctx.strokeStyle = baseColor;
      ctx.globalAlpha = 0.45;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.lineTo(sxx, syx);
      ctx.lineTo(spx, spy);
      ctx.lineTo(sxy, syy);
      ctx.closePath();
      ctx.stroke();
      // Light fill to convey area / sense of det sign
      ctx.fillStyle = baseColor;
      ctx.globalAlpha = 0.07;
      ctx.fill();
      ctx.restore();

      // Basis arrows
      this._drawArrow(sx, sy, sxx, syx, exColor, 1.6 * dpr);
      this._drawArrow(sx, sy, sxy, syy, eyColor, 1.6 * dpr);

      // Fixed point marker
      ctx.fillStyle = baseColor;
      ctx.strokeStyle = '#0a0d12';
      ctx.lineWidth = 1.5 * dpr;
      ctx.beginPath();
      ctx.arc(sx, sy, 5 * dpr, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();

      // Label
      ctx.fillStyle = baseColor;
      ctx.font = `${11 * dpr}px ui-monospace, monospace`;
      ctx.fillText('T' + (k + 1) + (frozen ? ' ❄' : ''), sx + 8 * dpr, sy - 8 * dpr);
    }
  }

  _drawArrow(x0, y0, x1, y1, color, width) {
    const ctx = this.ctx;
    const dx = x1 - x0,
      dy = y1 - y0;
    const len = Math.hypot(dx, dy);
    if (len < 1e-6) return;
    const ux = dx / len,
      uy = dy / len;
    const head = Math.min(8 * (window.devicePixelRatio || 1), len * 0.4);
    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = width;
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(x0, y0);
    ctx.lineTo(x1, y1);
    ctx.stroke();
    // Arrowhead
    const ax = x1 - ux * head;
    const ay = y1 - uy * head;
    const px = -uy,
      py = ux;
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(ax + px * head * 0.45, ay + py * head * 0.45);
    ctx.lineTo(ax - px * head * 0.45, ay - py * head * 0.45);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }

  drawLossCurve(history) {
    const w = this.lossCanvas.width,
      h = this.lossCanvas.height;
    const lctx = this.lctx;
    lctx.fillStyle = '#0d1117';
    lctx.fillRect(0, 0, w, h);
    if (history.length < 2) return;
    const logs = history.map((v) => Math.log10(Math.max(v, 1e-10)));
    let lo = Math.min(...logs),
      hi = Math.max(...logs);
    if (hi - lo < 1e-6) hi = lo + 1e-6;
    lctx.strokeStyle = '#58a6ff';
    lctx.lineWidth = 1.5;
    lctx.beginPath();
    for (let i = 0; i < logs.length; i++) {
      const x = (i / (logs.length - 1)) * w;
      const y = h - ((logs[i] - lo) / (hi - lo)) * (h - 4) - 2;
      if (i === 0) lctx.moveTo(x, y);
      else lctx.lineTo(x, y);
    }
    lctx.stroke();
    lctx.fillStyle = '#7d8590';
    lctx.font = '10px ui-monospace';
    lctx.fillText(`log10 loss [${lo.toFixed(2)}, ${hi.toFixed(2)}]`, 4, 12);
  }
}

// Spectral radius of a 2x2 matrix [a,b,c,d].
export function specRad(A) {
  const [a, b, c, d] = A;
  const tr = a + d,
    det = a * d - b * c;
  const disc = (tr * tr) / 4 - det;
  if (disc >= 0) {
    const s = Math.sqrt(disc);
    return Math.max(Math.abs(tr / 2 + s), Math.abs(tr / 2 - s));
  }
  return Math.sqrt(Math.max(det, 0));
}
