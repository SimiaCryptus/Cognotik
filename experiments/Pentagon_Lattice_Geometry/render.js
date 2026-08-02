// render.js
// Canvas drawing for the lattice, with view transform (pan/zoom),
// selection highlight, and edge labels.
// Generalised to support any n-gon and the Sierpiński IFS tree.

export class LatticeView {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.scale = 80;
    this.offset = { x: 0, y: 0 };
    this.selectedIdx = null;
    this.lattice = null;
    this.ca = null;
    this.options = {
      colorMode: 'sheet',
      palette: 'hsl',
      saturation: 60,
      lightness: 56,
      fillTiles: true,
      strokeTiles: true,
      borderWidth: 1.3,
      alphaSelected: 0.92,
      alphaOther: 0.28,
      onlySelSheet: false,
      originGuide: true,
      bgGradient: true,
      tileLabels: true,
      edgeLabels: true,
      depthLabels: false,
      indexLabels: false,
      labelsAllSheets: false,
      labelSize: 10,
      showSelGlow: true,
      showNeighborLinks: true,
      glowStrength: 14,
      caOverlay: true,
      caDeadAlphaSel: 0.2,
      caDeadAlphaOther: 0.06,
      caLiveBoost: 1.0,
    };
    this._dpr = 1;
    this._w = 900;
    this._h = 700;
    this._fitDPI();
    if (typeof ResizeObserver !== 'undefined') {
      this._ro = new ResizeObserver(() => this._fitDPI());
      this._ro.observe(this.canvas);
    } else {
      window.addEventListener('resize', () => this._fitDPI());
    }
  }

  _fitDPI() {
    const dpr = window.devicePixelRatio || 1;
    const w = this.canvas.clientWidth;
    const h = this.canvas.clientHeight;
    if (w === 0 || h === 0) return;
    this._dpr = dpr;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this._w = w;
    this._h = h;
    this.draw();
  }

  setLattice(l) {
    this.lattice = l;
    this.selectedIdx = 0;
    this._fitDPI();
    this.fit();
    this.draw();
  }

  setCA(ca) {
    this.ca = ca;
    this.draw();
  }

  setOption(name, val) {
    this.options[name] = val;
    this.draw();
  }

  fit() {
    if (!this.lattice) return;
    let xmin = +Infinity,
      xmax = -Infinity;
    let ymin = +Infinity,
      ymax = -Infinity;
    for (const t of this.lattice.tiles) {
      for (const [vx, vy] of t.vertsF) {
        if (vx < xmin) xmin = vx;
        if (vx > xmax) xmax = vx;
        if (vy < ymin) ymin = vy;
        if (vy > ymax) ymax = vy;
      }
    }
    const cx = (xmin + xmax) / 2;
    const cy = (ymin + ymax) / 2;
    const wRange = xmax - xmin;
    const hRange = ymax - ymin;
    const margin = 40;
    const sx = (this._w - margin * 2) / Math.max(wRange, 0.5);
    const sy = (this._h - margin * 2) / Math.max(hRange, 0.5);
    this.scale = Math.min(sx, sy);
    this.offset.x = this._w / 2 - cx * this.scale;
    this.offset.y = this._h / 2 + cy * this.scale;
  }

  worldToScreen(wx, wy) {
    return [wx * this.scale + this.offset.x, -wy * this.scale + this.offset.y];
  }

  screenToWorld(sx, sy) {
    return [(sx - this.offset.x) / this.scale, -(sy - this.offset.y) / this.scale];
  }

  eventToCanvas(ev) {
    const rect = this.canvas.getBoundingClientRect();
    const scaleX = rect.width / this.canvas.clientWidth || 1;
    const scaleY = rect.height / this.canvas.clientHeight || 1;
    return [(ev.clientX - rect.left) / scaleX, (ev.clientY - rect.top) / scaleY];
  }

  pickTile(sx, sy) {
    if (!this.lattice) return null;
    const [wx, wy] = this.screenToWorld(sx, sy);
    const sel = this.selectedIdx !== null ? this.lattice.tiles[this.selectedIdx] : null;
    const selSheet = sel ? sel.sheet : 0;
    let fallback = null;
    for (const t of this.lattice.tiles) {
      if (pointInPoly(wx, wy, t.vertsF)) {
        if (t.sheet === selSheet) return t.index;
        if (fallback === null) fallback = t.index;
      }
    }
    return fallback;
  }

  select(idx) {
    this.selectedIdx = idx;
    this.draw();
  }

  pan(dx, dy) {
    this.offset.x += dx;
    this.offset.y += dy;
    this.draw();
  }

  zoom(factor, cx, cy) {
    const [wx, wy] = this.screenToWorld(cx, cy);
    this.scale *= factor;
    const [nx, ny] = this.worldToScreen(wx, wy);
    this.offset.x += cx - nx;
    this.offset.y += cy - ny;
    this.draw();
  }

  draw() {
    const ctx = this.ctx;
    if (this.options.bgGradient) {
      const g = ctx.createRadialGradient(
        this._w / 2,
        this._h / 2,
        0,
        this._w / 2,
        this._h / 2,
        Math.max(this._w, this._h) * 0.7
      );
      g.addColorStop(0, '#11141c');
      g.addColorStop(1, '#07090d');
      ctx.fillStyle = g;
    } else {
      ctx.fillStyle = '#0b0d12';
    }
    ctx.fillRect(0, 0, this._w, this._h);
    if (!this.lattice) return;

    if (this.options.originGuide) this._drawOriginGuide();

    const sel = this.selectedIdx !== null ? this.lattice.tiles[this.selectedIdx] : null;
    const selSheet = sel ? sel.sheet : 0;

    // For Sierpiński, draw back-to-front by depth (largest first).
    if (this.lattice.isSierpinski) {
      const sorted = [...this.lattice.tiles].sort((a, b) => a.depth - b.depth);
      for (const t of sorted) {
        this._drawTile(t, this.options.alphaSelected, true);
      }
      if (sel && this.options.showSelGlow) this._drawSelection(sel);
      return;
    }

    // Regular n-gon: group by sheet.
    const bySheet = new Map();
    for (const t of this.lattice.tiles) {
      if (!bySheet.has(t.sheet)) bySheet.set(t.sheet, []);
      bySheet.get(t.sheet).push(t);
    }
    const sheets = [...bySheet.keys()].sort((a, b) => {
      const da = Math.abs(a - selSheet);
      const db = Math.abs(b - selSheet);
      return db - da;
    });
    for (const sh of sheets) {
      const isSel = sh === selSheet;
      if (this.options.onlySelSheet && !isSel) continue;
      const alpha = isSel ? this.options.alphaSelected : this.options.alphaOther;
      for (const t of bySheet.get(sh)) this._drawTile(t, alpha, isSel);
    }

    if (sel && this.options.showSelGlow) this._drawSelection(sel);
  }

  _drawOriginGuide() {
    const ctx = this.ctx;
    const [ox, oy] = this.worldToScreen(0, 0);
    ctx.save();
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, oy);
    ctx.lineTo(this._w, oy);
    ctx.moveTo(ox, 0);
    ctx.lineTo(ox, this._h);
    ctx.stroke();
    ctx.restore();
  }

  _drawTile(t, alpha, isSelSheet) {
    const ctx = this.ctx;
    const opts = this.options;
    ctx.save();

    let useAlpha = alpha;
    let isLive = false;
    if (opts.caOverlay && this.ca) {
      const v = this.ca.state[t.index] | 0;
      isLive = v !== 0;
      if (!isLive) {
        useAlpha = isSelSheet ? opts.caDeadAlphaSel : opts.caDeadAlphaOther;
      } else {
        useAlpha = isSelSheet ? Math.max(alpha, 0.95) : Math.max(alpha, 0.6);
      }
    }
    ctx.globalAlpha = useAlpha;

    ctx.beginPath();
    for (let i = 0; i < t.vertsF.length; i++) {
      const [sx, sy] = this.worldToScreen(...t.vertsF[i]);
      if (i === 0) ctx.moveTo(sx, sy);
      else ctx.lineTo(sx, sy);
    }
    ctx.closePath();

    if (opts.fillTiles) {
      ctx.fillStyle = this._tileColor(t, isSelSheet);
      ctx.fill();
    }
    if (opts.strokeTiles) {
      // If this tile has an activeEdges restriction (pinwheel), draw
      // active edges solid and inactive edges with a dashed, dim style.
      if (t.activeEdges) {
        const n = t.vertsF.length;
        for (let k = 0; k < n; k++) {
          const [sx0, sy0] = this.worldToScreen(...t.vertsF[k]);
          const [sx1, sy1] = this.worldToScreen(...t.vertsF[(k + 1) % n]);
          ctx.beginPath();
          ctx.moveTo(sx0, sy0);
          ctx.lineTo(sx1, sy1);
          if (t.activeEdges[k]) {
            ctx.strokeStyle = isSelSheet ? 'rgba(255,255,255,0.30)' : 'rgba(255,255,255,0.14)';
            ctx.lineWidth = isSelSheet
              ? opts.borderWidth * 1.3
              : Math.max(0.4, opts.borderWidth * 0.7);
            ctx.setLineDash([]);
          } else {
            ctx.strokeStyle = isSelSheet ? 'rgba(255, 90, 90, 0.55)' : 'rgba(255, 90, 90, 0.28)';
            ctx.lineWidth = isSelSheet ? opts.borderWidth : Math.max(0.4, opts.borderWidth * 0.5);
            ctx.setLineDash([4, 3]);
          }
          ctx.stroke();
        }
        ctx.setLineDash([]);
      } else {
        ctx.strokeStyle = isSelSheet ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.08)';
        ctx.lineWidth = isSelSheet ? opts.borderWidth : Math.max(0.4, opts.borderWidth * 0.6);
        ctx.stroke();
      }
    }

    const drawLabels = isSelSheet || opts.labelsAllSheets;
    if (drawLabels && !t.isSierpinski && !t.isPinwheel) {
      const [cx, cy] = this.worldToScreen(...t.centroidF);
      const sz = opts.labelSize;
      ctx.font = `600 ${sz}px ui-monospace, 'JetBrains Mono', monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillStyle = 'rgba(10,14,22,0.85)';
      const labelLines = [];
      if (opts.tileLabels) labelLines.push(`s${t.sheet}·o${t.orient}`);
      if (opts.indexLabels) labelLines.push(`#${t.index}`);
      if (opts.depthLabels) labelLines.push(`d${t.depth}`);
      const lineH = sz + 2;
      const totalH = lineH * labelLines.length;
      let y = cy - totalH / 2 + lineH / 2;
      for (const line of labelLines) {
        ctx.fillText(line, cx, y);
        y += lineH;
      }

      if (opts.edgeLabels) {
        const n = t.n || t.vertsF.length;
        const esz = Math.max(6, sz - 1);
        ctx.font = `600 ${esz}px ui-monospace, 'JetBrains Mono', monospace`;
        ctx.fillStyle = 'rgba(10,14,22,0.7)';
        for (let k = 0; k < n; k++) {
          const v0 = t.vertsF[k];
          const v1 = t.vertsF[(k + 1) % n];
          const mx = (v0[0] + v1[0]) / 2;
          const my = (v0[1] + v1[1]) / 2;
          const ix = mx * 0.72 + t.centroidF[0] * 0.28;
          const iy = my * 0.72 + t.centroidF[1] * 0.28;
          const [sx, sy] = this.worldToScreen(ix, iy);
          ctx.fillText(`${k + 1}`, sx, sy);
        }
      }
    }

    // Sierpiński: show depth label if requested.
    if (t.isSierpinski && opts.depthLabels) {
      const [cx, cy] = this.worldToScreen(...t.centroidF);
      const sz = Math.max(6, opts.labelSize - 2);
      ctx.font = `600 ${sz}px ui-monospace, 'JetBrains Mono', monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillStyle = 'rgba(10,14,22,0.85)';
      ctx.fillText(`d${t.depth}`, cx, cy);
    }

    ctx.restore();
  }

  _tileColor(t, isSelSheet) {
    const opts = this.options;

    if (opts.caOverlay && this.ca) {
      const v = this.ca.state[t.index] | 0;
      const ns = this.ca.numStates;
      if (v === 0) {
        return paletteColor(
          t.sheet,
          Math.max(this.lattice.groupOrder, 1),
          'mono',
          20,
          30,
          isSelSheet
        );
      }
      return caStateColor(v, ns, opts.palette, opts.saturation, opts.lightness, isSelSheet);
    }

    if (opts.colorMode === 'flat') {
      return isSelSheet ? '#2a3242' : '#1a2030';
    }

    let val, modulus;
    if (opts.colorMode === 'sheet') {
      val = t.sheet;
      modulus = Math.max(this.lattice.groupOrder, 1);
    } else if (opts.colorMode === 'orient') {
      val = t.sigma !== undefined ? t.sigma : t.orient % 2;
      modulus = 2;
    } else if (opts.colorMode === 'depth') {
      val = t.depth;
      modulus = Math.max((this.lattice.radius || 4) + 1, 1);
    } else {
      val = t.sheet;
      modulus = Math.max(this.lattice.groupOrder, 1);
    }
    return paletteColor(val, modulus, opts.palette, opts.saturation, opts.lightness, isSelSheet);
  }

  _drawSelection(t) {
    const ctx = this.ctx;
    const opts = this.options;
    ctx.save();

    ctx.beginPath();
    for (let i = 0; i < t.vertsF.length; i++) {
      const [sx, sy] = this.worldToScreen(...t.vertsF[i]);
      if (i === 0) ctx.moveTo(sx, sy);
      else ctx.lineTo(sx, sy);
    }
    ctx.closePath();
    ctx.shadowColor = 'rgba(255, 180, 84, 0.7)';
    ctx.shadowBlur = opts.glowStrength;
    ctx.strokeStyle = '#ffb454';
    ctx.lineWidth = 2.6;
    ctx.stroke();
    ctx.shadowBlur = 0;

    if (opts.showNeighborLinks) {
      const nCount = t.neighbors.length;
      for (let k = 0; k < nCount; k++) {
        const nIdx = t.neighbors[k];
        if (nIdx === null) continue;
        const n = this.lattice.tiles[nIdx];
        const [sx0, sy0] = this.worldToScreen(...t.centroidF);
        const [sx1, sy1] = this.worldToScreen(...n.centroidF);
        ctx.strokeStyle = 'rgba(255, 180, 84, 0.55)';
        ctx.lineWidth = 1.2;
        ctx.setLineDash([3, 3]);
        ctx.beginPath();
        ctx.moveTo(sx0, sy0);
        ctx.lineTo(sx1, sy1);
        ctx.stroke();
        ctx.setLineDash([]);
      }
    }
    ctx.restore();
  }
}

function pointInPoly(x, y, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const [xi, yi] = poly[i];
    const [xj, yj] = poly[j];
    const intersect = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi + 1e-30) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

function paletteColor(val, modulus, palette, sat, light, vivid) {
  const t = ((val % modulus) + modulus) % modulus;
  const frac = t / Math.max(modulus, 1);
  const s = vivid ? sat : Math.max(0, sat - 15);
  const l = vivid ? light : Math.max(0, light - 8);
  if (palette === 'warm') {
    const h = 0 + frac * 80;
    return `hsl(${h}, ${s}%, ${l}%)`;
  }
  if (palette === 'cool') {
    const h = 180 + frac * 100;
    return `hsl(${h}, ${s}%, ${l}%)`;
  }
  if (palette === 'mono') {
    const ll = 20 + frac * 60;
    return `hsl(220, 10%, ${vivid ? ll : ll * 0.8}%)`;
  }
  const h = frac * 360;
  return `hsl(${h}, ${s}%, ${l}%)`;
}

function caStateColor(val, numStates, palette, sat, light, vivid) {
  const s = vivid ? Math.min(95, sat + 15) : sat;
  const l = vivid ? Math.min(75, light + 6) : light;
  if (numStates <= 2) {
    return `hsl(38, ${s}%, ${l}%)`;
  }
  const frac = (val - 1) / Math.max(numStates - 1, 1);
  if (palette === 'warm') {
    const h = 10 + frac * 70;
    return `hsl(${h}, ${s}%, ${l}%)`;
  }
  if (palette === 'cool') {
    const h = 170 + frac * 120;
    return `hsl(${h}, ${s}%, ${l}%)`;
  }
  if (palette === 'mono') {
    const ll = 35 + frac * 50;
    return `hsl(40, 30%, ${ll}%)`;
  }
  const h = frac * 320;
  return `hsl(${h}, ${s}%, ${l}%)`;
}
