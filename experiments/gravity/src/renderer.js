import { sub, normalize, scale, add, len } from './vector.js';
import { solveRetardedPosition } from './physics.js';

export class Renderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.scaleFactor = 1;
    this.offset = { x: 0, y: 0 }; // pan in screen px
    this.showVectors = true;
    this.showRetarded = true; // draw co-observed (retarded) force directions

    // --- camera framing controls ---
    // followMode: 'free' | 'a' | 'b' | 'com' | 'fit'
    this.followMode = 'fit';
    // Independent locks: when locked, that aspect of the camera is not
    // auto-updated by the framing logic.
    this.lockLocation = false; // pan/center
    this.lockScale = false; // zoom
    this.lockVelocity = false; // velocity-compensation (de-rotate drift)

    this.fitPadding = 80; // screen px margin around bodies
    this.fitLerp = 0.12; // smoothing factor for camera moves
    this.scaleLerp = 0.08; // smoothing factor for zoom changes

    // Velocity compensation: subtract the followed frame's velocity so the
    // viewport "moves with" the target smoothly instead of jittering.
    this.viewVelocity = { x: 0, y: 0 };
    this._lastCenter = null;
    this._lastTime = null;

    // --- cosmic flair: starfield ---
    this.stars = [];
    this._initStars();

    this.resize();
    window.addEventListener('resize', () => this.resize());
  }

  _initStars() {
    this.stars = [];
    const n = 220;
    for (let i = 0; i < n; i++) {
      this.stars.push({
        // parallax depth in [0.15, 1]; smaller = farther/slower
        z: 0.15 + Math.random() * 0.85,
        x: Math.random(),
        y: Math.random(),
        r: Math.random() * 1.4 + 0.2,
        tw: Math.random() * Math.PI * 2, // twinkle phase
        hue: 200 + Math.random() * 80,
      });
    }
  }

  resize() {
    const dpr = window.devicePixelRatio || 1;
    this.canvas.width = this.canvas.clientWidth * dpr;
    this.canvas.height = this.canvas.clientHeight * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.w = this.canvas.clientWidth;
    this.h = this.canvas.clientHeight;
  }

  // world -> screen
  toScreen(p) {
    return {
      x: this.w / 2 + p.x * this.scaleFactor + this.offset.x,
      y: this.h / 2 + p.y * this.scaleFactor + this.offset.y,
    };
  }

  // screen -> world
  toWorld(s) {
    return {
      x: (s.x - this.w / 2 - this.offset.x) / this.scaleFactor,
      y: (s.y - this.h / 2 - this.offset.y) / this.scaleFactor,
    };
  }

  clear() {
    const ctx = this.ctx;
    // Deep-space gradient wash with motion-blur trails.
    ctx.fillStyle = 'rgba(4, 5, 12, 0.30)';
    ctx.fillRect(0, 0, this.w, this.h);
  }

  // Reset only the location (pan/center). With a follow target this snaps
  // the camera onto the target immediately.
  resetLocation(simulation) {
    if (simulation) {
      const c = this._targetCenter(simulation);
      if (c) {
        this.offset.x = -c.x * this.scaleFactor;
        this.offset.y = -c.y * this.scaleFactor;
      } else {
        this.offset.x = 0;
        this.offset.y = 0;
      }
    } else {
      this.offset.x = 0;
      this.offset.y = 0;
    }
    this.viewVelocity = { x: 0, y: 0 };
    this._lastCenter = null;
  }

  // Reset only the scale (zoom). Re-fits to bodies if a simulation given.
  resetScale(simulation) {
    if (simulation && simulation.bodies && simulation.bodies.length) {
      this.scaleFactor = this._targetScale(simulation);
    } else {
      this.scaleFactor = 1;
    }
  }

  // Reset velocity-compensation tracking.
  resetVelocity() {
    this.viewVelocity = { x: 0, y: 0 };
    this._lastCenter = null;
  }

  // Legacy reset-all hook used by older "Reset View" control.
  resetView() {
    this.followMode = 'fit';
    this.lockLocation = false;
    this.lockScale = false;
    this.lockVelocity = false;
  }

  // Compute the world-space center the camera should track given followMode.
  _targetCenter(simulation) {
    const bodies = simulation.bodies;
    if (!bodies || bodies.length === 0) return null;
    if (this.followMode === 'a') return { ...bodies[0].position };
    if (this.followMode === 'b' && bodies[1]) return { ...bodies[1].position };
    if (this.followMode === 'com') {
      let mx = 0;
      let my = 0;
      let m = 0;
      for (const b of bodies) {
        mx += b.position.x * b.mass;
        my += b.position.y * b.mass;
        m += b.mass;
      }
      return m > 0 ? { x: mx / m, y: my / m } : null;
    }
    // 'fit' and 'free' use the geometric midpoint as the reference.
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (const b of bodies) {
      if (b.position.x < minX) minX = b.position.x;
      if (b.position.y < minY) minY = b.position.y;
      if (b.position.x > maxX) maxX = b.position.x;
      if (b.position.y > maxY) maxY = b.position.y;
    }
    return { x: (minX + maxX) / 2, y: (minY + maxY) / 2 };
  }

  // Compute the scale that fits all bodies in the viewport.
  _targetScale(simulation) {
    const bodies = simulation.bodies;
    if (!bodies || bodies.length === 0) return this.scaleFactor;
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (const b of bodies) {
      const r = b.radius;
      if (b.position.x - r < minX) minX = b.position.x - r;
      if (b.position.y - r < minY) minY = b.position.y - r;
      if (b.position.x + r > maxX) maxX = b.position.x + r;
      if (b.position.y + r > maxY) maxY = b.position.y + r;
    }
    const spanX = Math.max(maxX - minX, 1);
    const spanY = Math.max(maxY - minY, 1);
    const availW = Math.max(this.w - this.fitPadding * 2, 1);
    const availH = Math.max(this.h - this.fitPadding * 2, 1);
    return Math.min(availW / spanX, availH / spanY);
  }

  // Unified camera update. Honors followMode and the per-aspect locks.
  updateCamera(simulation) {
    const center = this._targetCenter(simulation);
    if (!center) return;

    // --- velocity estimate of the tracked center (world units / sec) ---
    const now = simulation.t;
    if (!this.lockVelocity && this._lastCenter && this._lastTime !== null) {
      const dt = now - this._lastTime;
      if (dt > 1e-9) {
        const vx = (center.x - this._lastCenter.x) / dt;
        const vy = (center.y - this._lastCenter.y) / dt;
        // smooth the velocity estimate
        this.viewVelocity.x += (vx - this.viewVelocity.x) * 0.25;
        this.viewVelocity.y += (vy - this.viewVelocity.y) * 0.25;
      }
    }
    this._lastCenter = { ...center };
    this._lastTime = now;

    // --- scale ---
    if (!this.lockScale && this.followMode === 'fit') {
      const targetScale = this._targetScale(simulation);
      this.scaleFactor += (targetScale - this.scaleFactor) * this.scaleLerp;
    }

    // --- location (pan) ---
    if (!this.lockLocation && this.followMode !== 'free') {
      const targetOffsetX = -center.x * this.scaleFactor;
      const targetOffsetY = -center.y * this.scaleFactor;
      // When velocity-locked we let drift accumulate (looser tracking);
      // otherwise we tightly center, which inherently compensates motion.
      const lerp = this.lockVelocity ? this.fitLerp * 0.4 : this.fitLerp;
      this.offset.x += (targetOffsetX - this.offset.x) * lerp;
      this.offset.y += (targetOffsetY - this.offset.y) * lerp;
    }
  }

  drawStarfield() {
    const ctx = this.ctx;
    const t = performance.now() * 0.001;
    const w = this.w;
    const h = this.h;
    const ox = this.offset.x;
    const oy = this.offset.y;
    // Parallax: stars shift opposite to camera offset, scaled by depth.
    for (const s of this.stars) {
      const px = (((s.x * w + ox * s.z * 0.15) % w) + w) % w;
      const py = (((s.y * h + oy * s.z * 0.15) % h) + h) % h;
      const tw = 0.5 + 0.5 * Math.sin(t * (0.6 + s.z) + s.tw);
      const a = 0.25 + 0.6 * tw * s.z;
      ctx.beginPath();
      ctx.fillStyle = `hsla(${s.hue}, 70%, ${70 + s.z * 20}%, ${a.toFixed(3)})`;
      ctx.arc(px, py, s.r * s.z, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  drawTrail(body) {
    const ctx = this.ctx;
    const h = body.history;
    if (h.count < 2) return;
    // Glowing gradient trail that fades toward older samples.
    ctx.lineWidth = 1.8;
    ctx.shadowBlur = 8;
    ctx.shadowColor = body.color;
    // Bucket segments into a fixed number of alpha bands so we only set
    // strokeStyle / call stroke() a handful of times instead of per-segment.
    const BANDS = 16;
    const count = h.count;
    const buckets = [];
    for (let bnd = 0; bnd < BANDS; bnd++) buckets.push(null);
    let prev = this.toScreen(h._at(0).position);
    for (let i = 1; i < count; i++) {
      const cur = this.toScreen(h._at(i).position);
      const frac = i / count;
      let band = (frac * BANDS) | 0;
      if (band >= BANDS) band = BANDS - 1;
      let path = buckets[band];
      if (!path) {
        path = new Path2D();
        buckets[band] = path;
      }
      path.moveTo(prev.x, prev.y);
      path.lineTo(cur.x, cur.y);
      prev = cur;
    }
    for (let bnd = 0; bnd < BANDS; bnd++) {
      const path = buckets[bnd];
      if (!path) continue;
      const alpha = Math.floor(((bnd + 0.5) / BANDS) * 170 + 20);
      const aa = alpha.toString(16).padStart(2, '0');
      ctx.strokeStyle = body.color + aa;
      ctx.stroke(path);
    }
    ctx.shadowBlur = 0;
  }

  drawBody(body) {
    const ctx = this.ctx;
    const p = this.toScreen(body.position);
    const r = body.radius * Math.max(0.5, this.scaleFactor);

    // Outer cosmic halo.
    const halo = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, r * 3.4);
    halo.addColorStop(0, body.color + 'aa');
    halo.addColorStop(0.4, body.color + '33');
    halo.addColorStop(1, body.color + '00');
    ctx.fillStyle = halo;
    ctx.beginPath();
    ctx.arc(p.x, p.y, r * 3.4, 0, Math.PI * 2);
    ctx.fill();

    // Core with a bright hot center.
    const grad = ctx.createRadialGradient(p.x - r * 0.3, p.y - r * 0.3, 0, p.x, p.y, r);
    grad.addColorStop(0, '#ffffff');
    grad.addColorStop(0.3, body.color);
    grad.addColorStop(1, body.color);
    ctx.fillStyle = grad;
    ctx.shadowBlur = 18;
    ctx.shadowColor = body.color;
    ctx.beginPath();
    ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;

    if (this.showVectors) this.drawVectors(body);
  }

  drawVectors(body) {
    const ctx = this.ctx;
    const origin = this.toScreen(body.position);

    const drawArrow = (vecW, color, gain) => {
      const tip = this.toScreen(add(body.position, scale(vecW, gain)));
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(origin.x, origin.y);
      ctx.lineTo(tip.x, tip.y);
      ctx.stroke();
      const dir = normalize(sub(tip, origin));
      const a = 6;
      ctx.beginPath();
      ctx.moveTo(tip.x, tip.y);
      ctx.lineTo(tip.x - dir.x * a - dir.y * a, tip.y - dir.y * a + dir.x * a);
      ctx.lineTo(tip.x - dir.x * a + dir.y * a, tip.y - dir.y * a - dir.x * a);
      ctx.closePath();
      ctx.fillStyle = color;
      ctx.fill();
    };

    drawArrow(body.velocity, '#5ad1ff', 20);
    if (body.acceleration) drawArrow(body.acceleration, '#ff7b5a', 60);
  }
  // Draw the co-observed (retarded) force directions: for each body, show
  // where it currently "sees" the other body (the retarded position) and the
  // force direction along that retarded line of sight. This visually
  // distinguishes the delayed interaction from the instantaneous geometry.
  drawRetardedVectors(simulation) {
    const ctx = this.ctx;
    const bodies = simulation.bodies;
    if (bodies.length < 2) return;
    const params = simulation.params;
    if (!isFinite(params.c)) return;
    for (let i = 0; i < bodies.length; i++) {
      const observer = bodies[i];
      const source = bodies[(i + 1) % bodies.length];
      if (source.history.count <= 1) continue;
      // Reuse the retarded position cached during the physics step when
      // available; only re-solve if missing (e.g. just after a reset).
      let retPos = observer._retardedCache;
      if (!retPos) {
        const ret = solveRetardedPosition(observer.position, source.history, simulation.t, params);
        if (!ret) continue;
        retPos = ret.position;
      }
      const ret = { position: retPos };
      const oScreen = this.toScreen(observer.position);
      const retScreen = this.toScreen(ret.position);
      const curScreen = this.toScreen(source.position);
      // Faint dashed line connecting current source position to its
      // retarded (co-observed) position.
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = '#ffd45a66';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(curScreen.x, curScreen.y);
      ctx.lineTo(retScreen.x, retScreen.y);
      ctx.stroke();
      ctx.restore();
      // Marker ring at the retarded position ("ghost" of the source).
      ctx.save();
      ctx.strokeStyle = '#ffd45a';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(retScreen.x, retScreen.y, 5, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
      // Co-observed force direction: line of sight from observer toward the
      // retarded source position (the direction the force actually acts).
      const dir = normalize(sub(retScreen, oScreen));
      const tip = {
        x: oScreen.x + dir.x * 28,
        y: oScreen.y + dir.y * 28,
      };
      ctx.save();
      ctx.strokeStyle = '#9d5aff';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(oScreen.x, oScreen.y);
      ctx.lineTo(tip.x, tip.y);
      ctx.stroke();
      const a = 6;
      ctx.beginPath();
      ctx.moveTo(tip.x, tip.y);
      ctx.lineTo(tip.x - dir.x * a - dir.y * a, tip.y - dir.y * a + dir.x * a);
      ctx.lineTo(tip.x - dir.x * a + dir.y * a, tip.y - dir.y * a - dir.x * a);
      ctx.closePath();
      ctx.fillStyle = '#9d5aff';
      ctx.fill();
      ctx.restore();
    }
  }

  render(simulation) {
    this.updateCamera(simulation);
    this.clear();
    this.drawStarfield();
    for (const b of simulation.bodies) this.drawTrail(b);
    for (const b of simulation.bodies) this.drawBody(b);
    if (this.showRetarded) this.drawRetardedVectors(simulation);
  }
}
