// renderer.js
// Draws points + triangulation onto a 2D canvas.
// 3D coords are projected with a simple rotating orthographic camera.

export class Renderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.W = canvas.width;
    this.H = canvas.height;
    this.is2D = true;
    // 3D camera state (orbit controls)
    this.yaw = 0.6; // rotation around Y (azimuth)
    this.pitch = 0.5; // tilt around X (elevation)
    this.zoom = 1.0; // zoom factor
    this._drag = null; // {x,y,yaw,pitch}
    this._bindControls();
  }
  _bindControls() {
    const c = this.canvas;
    c.addEventListener('mousedown', (e) => {
      this._drag = { x: e.clientX, y: e.clientY, yaw: this.yaw, pitch: this.pitch };
    });
    window.addEventListener('mousemove', (e) => {
      if (!this._drag || this.is2D) return;
      const dx = e.clientX - this._drag.x;
      const dy = e.clientY - this._drag.y;
      this.yaw = this._drag.yaw + dx * 0.01;
      let p = this._drag.pitch + dy * 0.01;
      const lim = Math.PI / 2 - 0.01;
      this.pitch = Math.max(-lim, Math.min(lim, p));
    });
    window.addEventListener('mouseup', () => {
      this._drag = null;
    });
    c.addEventListener(
      'wheel',
      (e) => {
        if (this.is2D) return;
        e.preventDefault();
        const factor = Math.exp(-e.deltaY * 0.001);
        this.zoom = Math.max(0.2, Math.min(5, this.zoom * factor));
      },
      { passive: false }
    );
  }

  project(coords3, is2D) {
    // coords3: Float32Array flat [k*3]; returns array of [sx,sy,depth]
    const k = coords3.length / 3;
    const out = new Array(k);
    // yaw (azimuth) around Y, pitch (elevation) tilt around X
    const s = this.is2D ? 0 : Math.sin(this.yaw);
    const c = this.is2D ? 1 : Math.cos(this.yaw);
    const tilt = this.is2D ? 0 : this.pitch;
    const ct = Math.cos(tilt),
      st = Math.sin(tilt);
    let minx = Infinity,
      maxx = -Infinity,
      miny = Infinity,
      maxy = -Infinity;
    const proj = new Array(k);
    for (let i = 0; i < k; i++) {
      let x = coords3[i * 3],
        y = coords3[i * 3 + 1],
        z = coords3[i * 3 + 2];
      // rotate around Y then tilt around X
      let rx = c * x + s * z;
      let rz = -s * x + c * z;
      let ry = ct * y - st * rz;
      let depth = st * y + ct * rz;
      proj[i] = [rx, ry, depth];
      if (rx < minx) minx = rx;
      if (rx > maxx) maxx = rx;
      if (ry < miny) miny = ry;
      if (ry > maxy) maxy = ry;
    }
    const spanX = maxx - minx || 1,
      spanY = maxy - miny || 1;
    const span = Math.max(spanX, spanY);
    const pad = 40;
    const zoom = this.is2D ? 1 : this.zoom;
    const scale = ((Math.min(this.W, this.H) - 2 * pad) / span) * zoom;
    const cx = (minx + maxx) / 2,
      cy = (miny + maxy) / 2;
    for (let i = 0; i < k; i++) {
      const [rx, ry, depth] = proj[i];
      out[i] = [this.W / 2 + (rx - cx) * scale, this.H / 2 - (ry - cy) * scale, depth];
    }
    return out;
  }

  dihColor(phi) {
    // phi in [0,pi]; map to hue (flat=blue, folded=red)
    const t = phi / Math.PI; // 0..1
    const hue = 220 - 220 * (1 - t); // 0 -> red, 1 -> blue
    return `hsl(${hue}, 70%, 55%)`;
  }

  draw(coords3, tri, edges, phiArr, opts) {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, this.W, this.H);
    this.is2D = opts.is2D;

    const P = this.project(coords3, opts.is2D);

    // mesh triangles
    if (opts.showMesh) {
      ctx.lineWidth = 1;
      ctx.strokeStyle = 'rgba(120,150,180,0.25)';
      for (const [a, b, c] of tri) {
        if (!P[a] || !P[b] || !P[c]) continue;
        ctx.beginPath();
        ctx.moveTo(P[a][0], P[a][1]);
        ctx.lineTo(P[b][0], P[b][1]);
        ctx.lineTo(P[c][0], P[c][1]);
        ctx.closePath();
        ctx.stroke();
      }
    }

    // interior edges colored by dihedral angle
    if (opts.colorDih && phiArr) {
      ctx.lineWidth = 2;
      edges.forEach((e, i) => {
        if (!P[e.a] || !P[e.b]) return;
        ctx.strokeStyle = this.dihColor(phiArr[i]);
        ctx.beginPath();
        ctx.moveTo(P[e.a][0], P[e.a][1]);
        ctx.lineTo(P[e.b][0], P[e.b][1]);
        ctx.stroke();
      });
    }

    // points
    if (opts.showPoints) {
      for (let i = 0; i < P.length; i++) {
        const [x, y] = P[i];
        ctx.fillStyle = '#4fd1c5';
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }
}
