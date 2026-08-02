// Minimal WebGL-free "3D" via isometric projection on a 2D canvas.
// (A lightweight, dependency-free surface renderer; degrades gracefully.)
export class Scene3D {
  constructor(canvas, domain) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.domain = domain;
    this.yaw = 0.7;
    this.pitch = 0.9;
    this.zoom = 1;
    this.resize();
    this._attach();
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || 400,
      h = rect.height || 300;
    this.canvas.width = w * dpr;
    this.canvas.height = h * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = w;
    this.h = h;
  }

  _attach() {
    let dragging = false,
      lx = 0,
      ly = 0;
    this.canvas.addEventListener('pointerdown', (e) => {
      dragging = true;
      lx = e.clientX;
      ly = e.clientY;
    });
    window.addEventListener('pointermove', (e) => {
      if (!dragging) return;
      this.yaw += (e.clientX - lx) * 0.01;
      this.pitch = Math.max(0.2, Math.min(1.4, this.pitch + (e.clientY - ly) * 0.01));
      lx = e.clientX;
      ly = e.clientY;
      if (this._redraw) this._redraw();
    });
    window.addEventListener('pointerup', () => {
      dragging = false;
    });
    this.canvas.addEventListener(
      'wheel',
      (e) => {
        e.preventDefault();
        this.zoom *= e.deltaY > 0 ? 0.95 : 1.05;
        if (this._redraw) this._redraw();
      },
      { passive: false }
    );
  }

  _project(x, y, z) {
    // rotate around z (yaw) then tilt (pitch), orthographic
    const cx = Math.cos(this.yaw),
      sx = Math.sin(this.yaw);
    const rx = x * cx - y * sx;
    const ry = x * sx + y * cx;
    const sp = Math.sin(this.pitch);
    const px = rx;
    const py = ry * Math.cos(this.pitch) - z * sp;
    const scale = 70 * this.zoom;
    return [this.w / 2 + px * scale, this.h / 2 + py * scale - 20];
  }

  render(land, { highlight = null } = {}) {
    this._redraw = () => this.render(land, { highlight });
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.w, this.h);
    const [xm, xM, ym, yM] = this.domain;
    const n = 26;
    // normalize z
    let zmin = Infinity,
      zmax = -Infinity;
    const zs = [];
    for (let j = 0; j <= n; j++) {
      const row = [];
      for (let i = 0; i <= n; i++) {
        const x = xm + (i / n) * (xM - xm);
        const y = ym + (j / n) * (yM - ym);
        const v = land.f(x, y);
        row.push(v);
        zmin = Math.min(zmin, v);
        zmax = Math.max(zmax, v);
      }
      zs.push(row);
    }
    const norm = (v) => Math.log(v - zmin + 1) / (Math.log(zmax - zmin + 1) + 1e-9);
    const wx = (i) => (i / n - 0.5) * 2;
    const wy = (j) => (j / n - 0.5) * 2;
    // draw wireframe back-to-front
    for (let j = 0; j < n; j++) {
      for (let i = 0; i < n; i++) {
        const quad = [
          [i, j],
          [i + 1, j],
          [i + 1, j + 1],
          [i, j + 1],
        ];
        ctx.beginPath();
        quad.forEach(([ii, jj], k) => {
          const [sx, sy] = this._project(wx(ii), wy(jj), -norm(zs[jj][ii]) * 1.2);
          if (k === 0) ctx.moveTo(sx, sy);
          else ctx.lineTo(sx, sy);
        });
        ctx.closePath();
        const t = norm(zs[j][i]);
        ctx.fillStyle = `rgba(${245 - t * 90},${248 - t * 120},${255 - t * 40},0.85)`;
        ctx.fill();
        ctx.strokeStyle = 'rgba(80,80,120,0.15)';
        ctx.stroke();
      }
    }
    if (highlight) {
      const [hx, hy] = highlight;
      const wxn = (hx - (xm + xM) / 2) / ((xM - xm) / 2);
      const wyn = (hy - (ym + yM) / 2) / ((yM - ym) / 2);
      const v = land.f(hx, hy);
      const [sx, sy] = this._project(wxn, wyn, -norm(v) * 1.2);
      ctx.fillStyle = '#D55E00';
      ctx.beginPath();
      ctx.arc(sx, sy, 6, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}
