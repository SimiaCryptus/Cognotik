// World<->screen transforms, panning/zoom, DPR handling.

export class Camera {
  constructor() {
    this.cx = 0;
    this.cy = 0; // world center
    this.scale = 10; // pixels per world unit
    this.width = 800;
    this.height = 600;
    this.dpr = window.devicePixelRatio || 1;
    // margins for stats bars (screen px)
    this.marginLeft = 60;
    this.marginBottom = 60;
  }

  resize(w, h) {
    this.width = w;
    this.height = h;
  }

  // grid drawing area in screen coords
  gridRect() {
    return {
      x: this.marginLeft,
      y: 0,
      w: this.width - this.marginLeft,
      h: this.height - this.marginBottom,
    };
  }

  worldToScreen(wx, wy) {
    const r = this.gridRect();
    const sx = r.x + r.w / 2 + (wx - this.cx) * this.scale;
    const sy = r.y + r.h / 2 - (wy - this.cy) * this.scale;
    return [sx, sy];
  }

  screenToWorld(sx, sy) {
    const r = this.gridRect();
    const wx = this.cx + (sx - r.x - r.w / 2) / this.scale;
    const wy = this.cy - (sy - r.y - r.h / 2) / this.scale;
    return [wx, wy];
  }

  // visible world bounds within grid area
  worldBounds() {
    const r = this.gridRect();
    const [x0, y1] = this.screenToWorld(r.x, r.y);
    const [x1, y0] = this.screenToWorld(r.x + r.w, r.y + r.h);
    return { x0, x1, y0, y1 };
  }

  centerOn(wx, wy) {
    this.cx = wx;
    this.cy = wy;
  }

  zoomBy(factor) {
    this.scale = Math.max(0.5, Math.min(200, this.scale * factor));
  }
}

export function setupCanvasDPR(canvas, camera) {
  const dpr = camera.dpr;
  canvas.width = camera.width * dpr;
  canvas.height = camera.height * dpr;
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  return ctx;
}
