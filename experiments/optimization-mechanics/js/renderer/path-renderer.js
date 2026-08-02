// Path history (fading), gradient arrows, QQN mechanics.

export class PathRenderer {
  constructor(ctx, camera) {
    this.ctx = ctx;
    this.camera = camera;
  }

  render(pathHistory, lastResult, config, objective) {
    const ctx = this.ctx;
    const cam = this.camera;
    ctx.clearRect(0, 0, cam.width, cam.height);
    const r = cam.gridRect();
    ctx.save();
    ctx.beginPath();
    ctx.rect(r.x, r.y, r.w, r.h);
    ctx.clip();

    // path line (fading)
    const n = pathHistory.length;
    ctx.lineWidth = 2;
    for (let i = 1; i < n; i++) {
      const alpha = 0.15 + 0.85 * (i / n);
      const [x0, y0] = cam.worldToScreen(pathHistory[i - 1][0], pathHistory[i - 1][1]);
      const [x1, y1] = cam.worldToScreen(pathHistory[i][0], pathHistory[i][1]);
      ctx.strokeStyle = `rgba(120,200,255,${alpha})`;
      ctx.beginPath();
      ctx.moveTo(x0, y0);
      ctx.lineTo(x1, y1);
      ctx.stroke();
    }

    // history points (faded dots)
    for (let i = 0; i < n; i++) {
      const alpha = 0.1 + 0.5 * (i / n);
      const [sx, sy] = cam.worldToScreen(pathHistory[i][0], pathHistory[i][1]);
      ctx.fillStyle = `rgba(180,220,255,${alpha})`;
      ctx.beginPath();
      ctx.arc(sx, sy, 2, 0, Math.PI * 2);
      ctx.fill();
    }

    // gradient arrows along path
    if (config.showGrad) {
      const stepEvery = Math.max(1, Math.floor(n / 40));
      for (let i = 0; i < n; i += stepEvery) {
        const p = pathHistory[i];
        const g = objective.grad(p[0], p[1]);
        this.drawArrow(p, [-g[0], -g[1]], 'rgba(255,120,120,0.5)', 0.5);
      }
    }

    // QQN mechanics from lastResult
    if (lastResult) {
      const res = lastResult;
      // quadratic path
      if (res.path) {
        ctx.strokeStyle = 'rgba(255,220,80,0.7)';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        res.path.forEach((p, i) => {
          const [sx, sy] = cam.worldToScreen(p[0], p[1]);
          if (i === 0) ctx.moveTo(sx, sy);
          else ctx.lineTo(sx, sy);
        });
        ctx.stroke();
      }
      // probes
      if (res.probes) {
        res.probes.forEach((pr) => {
          const [sx, sy] = cam.worldToScreen(pr.x, pr.y);
          ctx.fillStyle = 'rgba(255,150,50,0.5)';
          ctx.beginPath();
          ctx.arc(sx, sy, 2.5, 0, Math.PI * 2);
          ctx.fill();
        });
      }
      // oracle point
      if (res.oracle) {
        const [sx, sy] = cam.worldToScreen(res.oracle[0], res.oracle[1]);
        ctx.strokeStyle = 'rgba(180,120,255,0.9)';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(sx, sy, 6, 0, Math.PI * 2);
        ctx.stroke();
        ctx.beginPath();
        ctx.moveTo(sx - 8, sy);
        ctx.lineTo(sx + 8, sy);
        ctx.moveTo(sx, sy - 8);
        ctx.lineTo(sx, sy + 8);
        ctx.stroke();
      }
      // current gradient vector
      if (res.grad && config.showGrad) {
        this.drawArrow(res.to, [-res.grad[0], -res.grad[1]], 'rgba(255,80,80,0.95)', 1.0);
      }
    }

    // current point
    if (n > 0) {
      const [sx, sy] = cam.worldToScreen(pathHistory[n - 1][0], pathHistory[n - 1][1]);
      ctx.fillStyle = '#fff';
      ctx.strokeStyle = '#3a6ea5';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(sx, sy, 5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
    }
    ctx.restore();
  }

  drawArrow(world, dir, color, scaleFactor) {
    const ctx = this.ctx;
    const cam = this.camera;
    const [sx, sy] = cam.worldToScreen(world[0], world[1]);
    // scale direction to screen; clamp length
    let dx = dir[0] * cam.scale * scaleFactor;
    let dy = -dir[1] * cam.scale * scaleFactor;
    const len = Math.hypot(dx, dy);
    const maxLen = 40;
    if (len > maxLen) {
      dx *= maxLen / len;
      dy *= maxLen / len;
    }
    if (len < 0.5) return;
    const ex = sx + dx,
      ey = sy + dy;
    ctx.strokeStyle = color;
    ctx.fillStyle = color;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(sx, sy);
    ctx.lineTo(ex, ey);
    ctx.stroke();
    // arrowhead
    const ang = Math.atan2(ey - sy, ex - sx);
    const ah = 5;
    ctx.beginPath();
    ctx.moveTo(ex, ey);
    ctx.lineTo(ex - ah * Math.cos(ang - 0.4), ey - ah * Math.sin(ang - 0.4));
    ctx.lineTo(ex - ah * Math.cos(ang + 0.4), ey - ah * Math.sin(ang + 0.4));
    ctx.closePath();
    ctx.fill();
  }
}
