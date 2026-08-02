// Canvas 2D renderer for contour plots, paths, arrows and 1D slices.
export class Scene2D {
  constructor(canvas, domain) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.domain = domain; // [xmin,xmax,ymin,ymax]
    this.resize();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || 400;
    const h = rect.height || 400;
    this.canvas.width = w * dpr;
    this.canvas.height = h * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = w;
    this.h = h;
  }

  setDomain(domain) {
    this.domain = domain;
  }

  // world -> screen
  toScreen([x, y]) {
    const [xm, xM, ym, yM] = this.domain;
    const sx = ((x - xm) / (xM - xm)) * this.w;
    const sy = this.h - ((y - ym) / (yM - ym)) * this.h;
    return [sx, sy];
  }

  // screen -> world
  toWorld(sx, sy) {
    const [xm, xM, ym, yM] = this.domain;
    const x = xm + (sx / this.w) * (xM - xm);
    const y = ym + (1 - sy / this.h) * (yM - ym);
    return [x, y];
  }

  clear() {
    this.ctx.clearRect(0, 0, this.w, this.h);
  }

  drawContours(land, { levels = 18, seed = 1 } = {}) {
    const ctx = this.ctx;
    // Guard: skip if canvas has no meaningful size yet.
    if (!this.w || !this.h || this.w < 2 || this.h < 2) {
      console.warn('Scene2D.drawContours: canvas not sized yet', this.w, this.h);
      return;
    }
    const [xm, xM, ym, yM] = this.domain;
    const nx = 120,
      ny = 120;
    // sample field
    let fmin = Infinity,
      fmax = -Infinity;
    const grid = [];
    for (let j = 0; j <= ny; j++) {
      const row = [];
      for (let i = 0; i <= nx; i++) {
        const x = xm + (i / nx) * (xM - xm);
        const y = ym + (j / ny) * (yM - ym);
        const v = land.f(x, y);
        row.push(v);
        if (v < fmin) fmin = v;
        if (v > fmax) fmax = v;
      }
      grid.push(row);
    }
    // filled heat via imageData.
    // NOTE: putImageData ignores the ctx transform, so we must fill the
    // *backing store* pixel dimensions (w*dpr x h*dpr), not the CSS-pixel
    // dimensions. Using this.w/this.h here produced misaligned/bright rows
    // on high-DPR screens (visible on the tiny thumbnails).
    const pw = this.canvas.width; // device pixels
    const ph = this.canvas.height; // device pixels
    const img = ctx.createImageData(pw, ph);
    const logDen = Math.log(fmax - fmin + 1) + 1e-9;
    for (let py = 0; py < ph; py++) {
      // map device-pixel row -> CSS-pixel coordinate for toWorld
      const cssY = ((py + 0.5) * this.h) / ph;
      for (let px = 0; px < pw; px++) {
        const cssX = ((px + 0.5) * this.w) / pw;
        const [wx, wy] = this.toWorld(cssX, cssY);
        const v = land.f(wx, wy);
        const tnorm = Math.max(0, Math.min(1, Math.log(v - fmin + 1) / logDen));
        const r = 245 - tnorm * 90;
        const g = 248 - tnorm * 120;
        const b = 255 - tnorm * 40;
        const idx = (py * pw + px) * 4;
        img.data[idx] = r;
        img.data[idx + 1] = g;
        img.data[idx + 2] = b;
        img.data[idx + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
    // contour lines (marching squares light)
    ctx.strokeStyle = 'rgba(60,60,90,0.25)';
    ctx.lineWidth = 1;
    for (let l = 1; l <= levels; l++) {
      const level = fmin + (Math.exp((l / levels) * Math.log(fmax - fmin + 1)) - 1);
      this._marchingSquares(grid, level, nx, ny);
    }
  }

  _marchingSquares(grid, level, nx, ny) {
    const ctx = this.ctx;
    const [xm, xM, ym, yM] = this.domain;
    const gx = (i) => xm + (i / nx) * (xM - xm);
    const gy = (j) => ym + (j / ny) * (yM - ym);
    ctx.beginPath();
    for (let j = 0; j < ny; j++) {
      for (let i = 0; i < nx; i++) {
        const v0 = grid[j][i],
          v1 = grid[j][i + 1],
          v2 = grid[j + 1][i + 1],
          v3 = grid[j + 1][i];
        const seg = [];
        const interp = (a, b, va, vb) => {
          const tt = (level - va) / (vb - va);
          return [a[0] + tt * (b[0] - a[0]), a[1] + tt * (b[1] - a[1])];
        };
        const P = [
          [gx(i), gy(j)],
          [gx(i + 1), gy(j)],
          [gx(i + 1), gy(j + 1)],
          [gx(i), gy(j + 1)],
        ];
        const V = [v0, v1, v2, v3];
        for (let e = 0; e < 4; e++) {
          const a = e,
            b = (e + 1) % 4;
          if (V[a] < level !== V[b] < level) {
            seg.push(interp(P[a], P[b], V[a], V[b]));
          }
        }
        if (seg.length === 2) {
          const [s0] = [this.toScreen(seg[0])];
          const s1 = this.toScreen(seg[1]);
          ctx.moveTo(s0[0], s0[1]);
          ctx.lineTo(s1[0], s1[1]);
        }
      }
    }
    ctx.stroke();
  }

  drawPolyline(worldPts, color, width = 2, dash = null) {
    const ctx = this.ctx;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    if (dash) ctx.setLineDash(dash);
    ctx.beginPath();
    worldPts.forEach((p, i) => {
      const [x, y] = this.toScreen(p);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
    ctx.restore();
  }

  drawArrow(fromW, toW, color, width = 2) {
    const ctx = this.ctx;
    const a = this.toScreen(fromW);
    const b = this.toScreen(toW);
    ctx.save();
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = width;
    ctx.beginPath();
    ctx.moveTo(a[0], a[1]);
    ctx.lineTo(b[0], b[1]);
    ctx.stroke();
    const ang = Math.atan2(b[1] - a[1], b[0] - a[0]);
    const s = 8;
    ctx.beginPath();
    ctx.moveTo(b[0], b[1]);
    ctx.lineTo(b[0] - s * Math.cos(ang - 0.4), b[1] - s * Math.sin(ang - 0.4));
    ctx.lineTo(b[0] - s * Math.cos(ang + 0.4), b[1] - s * Math.sin(ang + 0.4));
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }

  drawDot(worldPt, color, r = 5) {
    const ctx = this.ctx;
    const [x, y] = this.toScreen(worldPt);
    ctx.save();
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  drawLabel(worldPt, text, color = '#222', dx = 8, dy = -8) {
    const ctx = this.ctx;
    const [x, y] = this.toScreen(worldPt);
    ctx.save();
    ctx.fillStyle = color;
    ctx.font = '12px ui-monospace, monospace';
    ctx.fillText(text, x + dx, y + dy);
    ctx.restore();
  }
}
