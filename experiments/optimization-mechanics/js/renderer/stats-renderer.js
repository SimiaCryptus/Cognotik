// Left & bottom statistics bars.

const TRACKS = [
  { key: 'cur', label: 'grad', color: '#ff5050' },
  { key: 'mean', label: 'Σg', color: '#50c8ff' },
  { key: 'meanSq', label: 'Σg²', color: '#ffc850' },
];

export class StatsRenderer {
  constructor(ctx, camera) {
    this.ctx = ctx;
    this.camera = camera;
  }

  render(stats) {
    const ctx = this.ctx;
    const cam = this.camera;
    ctx.clearRect(0, 0, cam.width, cam.height);
    const r = cam.gridRect();

    // backgrounds
    ctx.fillStyle = '#202026';
    ctx.fillRect(0, 0, cam.marginLeft, r.h); // left bar
    ctx.fillRect(r.x, r.h, r.w, cam.marginBottom); // bottom bar

    const cur = stats.cur;
    const mean = stats.mean();
    const meanSq = stats.meanSq();
    const vals = { cur, mean, meanSq };

    // normalization
    const maxMag = Math.max(
      1e-6,
      Math.abs(cur[0]),
      Math.abs(cur[1]),
      Math.abs(mean[0]),
      Math.abs(mean[1]),
      Math.sqrt(meanSq[0]),
      Math.sqrt(meanSq[1])
    );

    // Bottom bar: x-components. Center line at r.w/2 baseline.
    const bx = r.x,
      bw = r.w;
    const baseY = r.h + cam.marginBottom / 2;
    ctx.strokeStyle = 'rgba(255,255,255,0.2)';
    ctx.beginPath();
    ctx.moveTo(bx, baseY);
    ctx.lineTo(bx + bw, baseY);
    ctx.stroke();

    let slot = 0;
    TRACKS.forEach((tr) => {
      const vx = tr.key === 'meanSq' ? Math.sqrt(vals[tr.key][0]) : vals[tr.key][0];
      const t = vx / maxMag; // -1..1
      const barLen = t * (bw / 2 - 10);
      const yy = r.h + 6 + slot * 16;
      ctx.fillStyle = tr.color;
      ctx.fillRect(bx + bw / 2, yy, barLen, 10);
      ctx.fillStyle = '#aaa';
      ctx.font = '9px monospace';
      ctx.fillText(tr.label, bx + 2, yy + 9);
      slot++;
    });

    // Left bar: y-components. baseline vertical center.
    const by = r.y,
      bh = r.h;
    const baseX = cam.marginLeft / 2;
    ctx.strokeStyle = 'rgba(255,255,255,0.2)';
    ctx.beginPath();
    ctx.moveTo(baseX, by);
    ctx.lineTo(baseX, by + bh);
    ctx.stroke();

    slot = 0;
    TRACKS.forEach((tr) => {
      const vy = tr.key === 'meanSq' ? Math.sqrt(vals[tr.key][1]) : vals[tr.key][1];
      const t = vy / maxMag;
      const barLen = t * (bh / 2 - 10);
      const xx = 4 + slot * 16;
      ctx.fillStyle = tr.color;
      // positive up
      ctx.fillRect(xx, by + bh / 2, 10, -barLen);
      slot++;
    });

    // history sparkline (bottom, x-component) faint
    if (stats.history.length > 1) {
      ctx.strokeStyle = 'rgba(120,200,255,0.4)';
      ctx.beginPath();
      const h = stats.history;
      const w = bw / h.length;
      h.forEach((g, i) => {
        const t = g[0] / maxMag;
        const y = baseY - t * (cam.marginBottom / 2 - 4);
        const x = bx + i * w;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    }
  }
}
