// Renderer draws the hex board, tiles, and the current selection path
// with AAA-quality visuals: gradients, glow, particles, and shimmer.
//
// Pointy-top hexagons. Keeps rendering purely a function of model state
// plus a small amount of transient visual state (particles, flashes).

import { coordKey } from '../model/coords.js';

// Vibrant per-value palette for a punchy arcade look.
const VALUE_PALETTE = [
  { base: '#38bdf8', glow: '#7dd3fc' }, // 0
  { base: '#34d399', glow: '#6ee7b7' }, // 1
  { base: '#a3e635', glow: '#d9f99d' }, // 2
  { base: '#facc15', glow: '#fde68a' }, // 3
  { base: '#fb923c', glow: '#fdba74' }, // 4
  { base: '#f87171', glow: '#fca5a5' }, // 5
  { base: '#f472b6', glow: '#f9a8d4' }, // 6
  { base: '#c084fc', glow: '#d8b4fe' }, // 7
  { base: '#818cf8', glow: '#a5b4fc' }, // 8
  { base: '#22d3ee', glow: '#67e8f9' }, // 9
];

export class Renderer {
  constructor(canvas, board) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.board = board;
    this.size = board.grid.size;

    // Transient visual systems.
    this.particles = [];
    this.floaters = []; // floating score/combo text
    this.ripples = [];
    this._shake = { mag: 0, until: 0 };
    this._time = 0;
    this._pulse = new Map(); // tileId -> spawn time for pop-in

    this.colors = {
      text: '#ffffff',
      selectedText: '#0b1020',
    };
  }

  paletteFor(value) {
    const idx = ((value % VALUE_PALETTE.length) + VALUE_PALETTE.length) % VALUE_PALETTE.length;
    return VALUE_PALETTE[idx];
  }

  // ---- Effects API (called by Game) ----
  addShake(mag = 8, ms = 220) {
    this._shake = { mag, until: this._time + ms };
  }

  spawnBurst(x, y, color, count = 18, power = 1) {
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const sp = (1.2 + Math.random() * 3.2) * power;
      this.particles.push({
        x,
        y,
        vx: Math.cos(a) * sp,
        vy: Math.sin(a) * sp - 1,
        life: 1,
        decay: 0.012 + Math.random() * 0.02,
        size: 2 + Math.random() * 3.5,
        color,
      });
    }
  }

  spawnFloater(x, y, text, color = '#ffe066', big = false) {
    this.floaters.push({
      x,
      y,
      vy: -0.7,
      life: 1,
      decay: 0.014,
      text,
      color,
      big,
    });
  }

  spawnRipple(x, y, color) {
    this.ripples.push({ x, y, r: this.size * 0.4, life: 1, color });
  }

  markPop(tileId) {
    this._pulse.set(tileId, this._time);
  }

  hasActiveEffects() {
    return this.particles.length > 0 || this.floaters.length > 0 || this.ripples.length > 0;
  }

  // Compute the 6 corner points of a pointy-top hex centered at (cx, cy).
  _hexPath(cx, cy, size = this.size) {
    const pts = [];
    for (let i = 0; i < 6; i++) {
      const angle = (Math.PI / 180) * (60 * i - 30);
      pts.push({
        x: cx + size * Math.cos(angle),
        y: cy + size * Math.sin(angle),
      });
    }
    return pts;
  }

  _traceHex(cx, cy, size) {
    const pts = this._hexPath(cx, cy, size);
    const ctx = this.ctx;
    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < 6; i++) ctx.lineTo(pts[i].x, pts[i].y);
    ctx.closePath();
  }

  _drawBackground() {
    const ctx = this.ctx;
    const w = this.canvas.width;
    const h = this.canvas.height;
    const g = ctx.createRadialGradient(w / 2, h * 0.35, 40, w / 2, h / 2, Math.max(w, h) * 0.8);
    g.addColorStop(0, '#1b2440');
    g.addColorStop(0.5, '#131a30');
    g.addColorStop(1, '#0a0e1c');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);

    // Subtle animated vignette shimmer.
    const t = this._time * 0.0015;
    ctx.save();
    ctx.globalAlpha = 0.06;
    for (let i = 0; i < 3; i++) {
      const cx = w / 2 + Math.cos(t + i * 2.1) * w * 0.25;
      const cy = h / 2 + Math.sin(t * 0.8 + i * 1.7) * h * 0.25;
      const rg = ctx.createRadialGradient(cx, cy, 0, cx, cy, w * 0.4);
      rg.addColorStop(0, ['#59c2ff', '#c084fc', '#34d399'][i]);
      rg.addColorStop(1, 'transparent');
      ctx.fillStyle = rg;
      ctx.fillRect(0, 0, w, h);
    }
    ctx.restore();
  }

  _drawCellSocket(cx, cy) {
    const ctx = this.ctx;
    // Recessed empty-cell socket for depth.
    this._traceHex(cx, cy, this.size * 0.96);
    ctx.fillStyle = 'rgba(0,0,0,0.28)';
    ctx.fill();
    this._traceHex(cx, cy, this.size * 0.96);
    ctx.lineWidth = 1.5;
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.stroke();
  }

  _drawTile(cx, cy, value, selected, scale = 1, tileId = null) {
    const ctx = this.ctx;
    const pal = this.paletteFor(value);
    const size = this.size * scale;

    // Pop-in animation.
    let popScale = 1;
    if (tileId && this._pulse.has(tileId)) {
      const age = this._time - this._pulse.get(tileId);
      if (age < 260) {
        const p = age / 260;
        popScale = 0.5 + 0.5 * (1 - Math.pow(1 - p, 3)) + Math.sin(p * Math.PI) * 0.12;
      } else {
        this._pulse.delete(tileId);
      }
    }
    const s = size * popScale;

    ctx.save();

    // Glow halo.
    ctx.shadowColor = selected ? '#ffffff' : pal.glow;
    ctx.shadowBlur = selected ? 26 : 14;

    // Body gradient (glossy gem).
    const grad = ctx.createLinearGradient(cx, cy - s, cx, cy + s);
    if (selected) {
      grad.addColorStop(0, '#ffffff');
      grad.addColorStop(0.4, pal.glow);
      grad.addColorStop(1, pal.base);
    } else {
      grad.addColorStop(0, pal.glow);
      grad.addColorStop(0.55, pal.base);
      grad.addColorStop(1, this._darken(pal.base, 0.4));
    }

    this._traceHex(cx, cy, s * 0.92);
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.shadowBlur = 0;

    // Inner top highlight (glass reflection).
    ctx.save();
    this._traceHex(cx, cy, s * 0.92);
    ctx.clip();
    const hi = ctx.createLinearGradient(cx, cy - s, cx, cy);
    hi.addColorStop(0, 'rgba(255,255,255,0.55)');
    hi.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = hi;
    this._traceHex(cx, cy - s * 0.18, s * 0.7);
    ctx.fill();
    ctx.restore();

    // Rim.
    this._traceHex(cx, cy, s * 0.92);
    ctx.lineWidth = selected ? 3.5 : 2;
    ctx.strokeStyle = selected ? '#ffffff' : this._lighten(pal.glow, 0.2);
    ctx.stroke();

    // Value text with shadow for punch.
    ctx.font = `800 ${Math.round(this.size * 0.86)}px "Segoe UI", system-ui, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = 'rgba(0,0,0,0.35)';
    ctx.fillText(String(value), cx, cy + 2);
    ctx.fillStyle = selected ? this.colors.selectedText : '#ffffff';
    ctx.fillText(String(value), cx, cy);

    ctx.restore();
  }

  _darken(hex, amt) {
    const { r, g, b } = this._hexToRgb(hex);
    return `rgb(${Math.round(r * (1 - amt))},${Math.round(g * (1 - amt))},${Math.round(b * (1 - amt))})`;
  }
  _lighten(hex, amt) {
    const { r, g, b } = this._hexToRgb(hex);
    return `rgb(${Math.round(r + (255 - r) * amt)},${Math.round(g + (255 - g) * amt)},${Math.round(b + (255 - b) * amt)})`;
  }
  _hexToRgb(hex) {
    const h = hex.replace('#', '');
    return {
      r: parseInt(h.substring(0, 2), 16),
      g: parseInt(h.substring(2, 4), 16),
      b: parseInt(h.substring(4, 6), 16),
    };
  }

  _updateEffects(dt) {
    this._time += dt;
    for (const p of this.particles) {
      p.x += p.vx;
      p.y += p.vy;
      p.vy += 0.12; // gravity
      p.vx *= 0.98;
      p.life -= p.decay;
    }
    this.particles = this.particles.filter((p) => p.life > 0);

    for (const f of this.floaters) {
      f.y += f.vy;
      f.vy *= 0.98;
      f.life -= f.decay;
    }
    this.floaters = this.floaters.filter((f) => f.life > 0);

    for (const r of this.ripples) {
      r.r += 3.5;
      r.life -= 0.05;
    }
    this.ripples = this.ripples.filter((r) => r.life > 0);
  }

  _drawParticles() {
    const ctx = this.ctx;
    ctx.save();
    for (const p of this.particles) {
      ctx.globalAlpha = Math.max(0, p.life);
      ctx.shadowColor = p.color;
      ctx.shadowBlur = 8;
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.size * p.life, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  _drawRipples() {
    const ctx = this.ctx;
    ctx.save();
    for (const r of this.ripples) {
      ctx.globalAlpha = Math.max(0, r.life) * 0.5;
      ctx.strokeStyle = r.color;
      ctx.lineWidth = 3 * r.life;
      this._traceHex(r.x, r.y, r.r);
      ctx.stroke();
    }
    ctx.restore();
  }

  _drawFloaters() {
    const ctx = this.ctx;
    ctx.save();
    for (const f of this.floaters) {
      ctx.globalAlpha = Math.max(0, f.life);
      const scale = f.big ? 1.4 + (1 - f.life) * 0.4 : 1;
      ctx.font = `900 ${Math.round(this.size * (f.big ? 1.1 : 0.75) * scale)}px "Segoe UI", system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.lineWidth = 4;
      ctx.strokeStyle = 'rgba(0,0,0,0.6)';
      ctx.strokeText(f.text, f.x, f.y);
      ctx.shadowColor = f.color;
      ctx.shadowBlur = 12;
      ctx.fillStyle = f.color;
      ctx.fillText(f.text, f.x, f.y);
    }
    ctx.restore();
  }

  // selectionCoords: array of {q,r} in order (may be empty).
  // offsets: optional Map of tileId -> { x, y } absolute pixel center.
  render(selectionCoords = [], offsets = null, dt = 16) {
    const ctx = this.ctx;
    this._updateEffects(dt);

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    this._drawBackground();

    // Screen shake.
    if (this._time < this._shake.until) {
      const s = this._shake.mag * ((this._shake.until - this._time) / 220);
      ctx.translate((Math.random() - 0.5) * s, (Math.random() - 0.5) * s);
    }

    const selectedSet = new Set(selectionCoords.map(coordKey));

    // Draw empty sockets first for depth.
    for (const cell of this.board.grid.allCells()) {
      const { x, y } = this.board.grid.toPixel(cell);
      this._drawCellSocket(x, y);
    }

    this._drawRipples();

    // Draw selection glow-line UNDER selected tiles.
    if (!offsets && selectionCoords.length > 1) {
      const first = this.board.getTile(selectionCoords[0]);
      const pal = first ? this.paletteFor(first.value) : { glow: '#fff' };
      ctx.save();
      ctx.beginPath();
      selectionCoords.forEach((coord, i) => {
        const { x, y } = this.board.grid.toPixel(coord);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = '#ffffff';
      ctx.shadowColor = pal.glow;
      ctx.shadowBlur = 20;
      ctx.lineWidth = 8 + Math.sin(this._time * 0.02) * 1.5;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.globalAlpha = 0.85;
      ctx.stroke();
      ctx.restore();
    }

    // Draw tiles.
    this.board.forEachTile((tile) => {
      let { x, y } = this.board.grid.toPixel(tile.coord);
      if (offsets && offsets.has(tile.id)) {
        const o = offsets.get(tile.id);
        x = o.x;
        y = o.y;
      }
      const selected = selectedSet.has(coordKey(tile.coord));
      const scale = selected ? 1.08 + Math.sin(this._time * 0.015) * 0.03 : 1;
      this._drawTile(x, y, tile.value, selected, scale, tile.id);
    });

    this._drawParticles();
    this._drawFloaters();
  }
}
