// Grid lines + lattice cell outlines.

export class OverlayRenderer {
  constructor(ctx, camera) {
    this.ctx = ctx;
    this.camera = camera;
  }

  render(config) {
    const ctx = this.ctx;
    const cam = this.camera;
    ctx.clearRect(0, 0, cam.width, cam.height);
    const r = cam.gridRect();
    ctx.save();
    ctx.beginPath();
    ctx.rect(r.x, r.y, r.w, r.h);
    ctx.clip();

    const b = cam.worldBounds();

    // grid lines at integer-ish spacing
    const spacing = niceSpacing(cam.scale);
    ctx.strokeStyle = 'rgba(255,255,255,0.06)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let x = Math.ceil(b.x0 / spacing) * spacing; x <= b.x1; x += spacing) {
      const [sx] = cam.worldToScreen(x, 0);
      ctx.moveTo(sx, r.y);
      ctx.lineTo(sx, r.y + r.h);
    }
    for (let y = Math.ceil(b.y0 / spacing) * spacing; y <= b.y1; y += spacing) {
      const [, sy] = cam.worldToScreen(0, y);
      ctx.moveTo(r.x, sy);
      ctx.lineTo(r.x + r.w, sy);
    }
    ctx.stroke();

    // axes
    ctx.strokeStyle = 'rgba(255,255,255,0.25)';
    ctx.beginPath();
    const [ox, oy] = cam.worldToScreen(0, 0);
    ctx.moveTo(ox, r.y);
    ctx.lineTo(ox, r.y + r.h);
    ctx.moveTo(r.x, oy);
    ctx.lineTo(r.x + r.w, oy);
    ctx.stroke();

    // lattice cell outlines
    if (config.latticeOn) {
      const L = config.lattice.L;
      ctx.strokeStyle = 'rgba(255,180,60,0.2)';
      ctx.beginPath();
      for (let x = Math.floor(b.x0 / L) * L; x <= b.x1; x += L) {
        const [sx] = cam.worldToScreen(x, 0);
        ctx.moveTo(sx, r.y);
        ctx.lineTo(sx, r.y + r.h);
      }
      for (let y = Math.floor(b.y0 / L) * L; y <= b.y1; y += L) {
        const [, sy] = cam.worldToScreen(0, y);
        ctx.moveTo(r.x, sy);
        ctx.lineTo(r.x + r.w, sy);
      }
      ctx.stroke();
    }
    ctx.restore();
  }
}

function niceSpacing(scale) {
  // aim for ~50px grid spacing
  const target = 50 / scale;
  const pow = Math.pow(10, Math.floor(Math.log10(target)));
  const candidates = [1, 2, 5, 10].map((c) => c * pow);
  return candidates.reduce((best, c) =>
    Math.abs(c - target) < Math.abs(best - target) ? c : best
  );
}
