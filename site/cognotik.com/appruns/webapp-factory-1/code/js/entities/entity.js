// ============================================================
//  Super Mario Bros Clone — Base Entity
// ============================================================

import { TILE_SIZE, SCALE, GRAVITY, MAX_FALL_SPEED, SOLID_TILES } from '../constants.js';


export class Entity {
  /**
   * @param {number} x      World X (pixels)
   * @param {number} y      World Y (pixels)
   * @param {number} width  Width (pixels)
   * @param {number} height Height (pixels)
   * @param {string} type   ENTITY_TYPE value
   */
  constructor(x, y, width, height, type) {
    this.x      = x;
    this.y      = y;
    this.width  = width;
    this.height = height;
    this.type   = type;

    this.vx     = 0;
    this.vy     = 0;

    this.onGround  = false;
    this.alive     = true;
    this.facingRight = true;

    this.animFrame = 0;
    this.animTimer = 0;
  }

  // ── AABB helpers ───────────────────────────────────────────

  get left()   { return this.x; }
  get right()  { return this.x + this.width; }
  get top()    { return this.y; }
  get bottom() { return this.y + this.height; }
  get centerX(){ return this.x + this.width  / 2; }
  get centerY(){ return this.y + this.height / 2; }

  /**
   * Returns true if this entity's AABB overlaps another.
   */
  overlaps(other) {
    return (
      this.left   < other.right  &&
      this.right  > other.left   &&
      this.top    < other.bottom &&
      this.bottom > other.top
    );
  }

  // ── Physics ────────────────────────────────────────────────

  /**
   * Apply gravity and move, resolving tile collisions.
   * @param {Tilemap} tilemap
   * @param {number}  dt       Delta time (1 = one frame at 60fps)
   */
  applyPhysics(tilemap, dt = 1) {
    // Gravity
    this.vy = Math.min(this.vy + GRAVITY * dt, MAX_FALL_SPEED);

    // Move X
    this.x += this.vx * dt;
    this._resolveX(tilemap);

    // Move Y
    this.y += this.vy * dt;
    this._resolveY(tilemap);
  }

  _resolveX(tilemap) {
    const tiles = tilemap.getSolidTilesInRect(this.x, this.y + 1, this.width, this.height - 2);
    for (const tile of tiles) {
      const overlapX = this._overlapX(tile);
      if (overlapX !== 0) {
        this.x += overlapX;
        this.vx = 0;
      }
    }
  }

  _resolveY(tilemap) {
    this.onGround = false;
    const tiles = tilemap.getSolidTilesInRect(this.x + 1, this.y, this.width - 2, this.height);
    for (const tile of tiles) {
      const overlapY = this._overlapY(tile);
      if (overlapY !== 0) {
        this.y += overlapY;
        if (overlapY < 0) {
          this.onGround = true;
          this.vy = 0;
        } else {
          this.vy = Math.max(0, this.vy);
        }
      }
    }
  }

  _overlapX(rect) {
    const dx = this.centerX - (rect.x + rect.w / 2);
    const overlapX = (this.width / 2 + rect.w / 2) - Math.abs(dx);
    if (overlapX <= 0) return 0;
    return dx > 0 ? overlapX : -overlapX;
  }

  _overlapY(rect) {
    const dy = this.centerY - (rect.y + rect.h / 2);
    const overlapY = (this.height / 2 + rect.h / 2) - Math.abs(dy);
    if (overlapY <= 0) return 0;
    return dy > 0 ? overlapY : -overlapY;
  }

  // ── Lifecycle ──────────────────────────────────────────────

  update(dt, tilemap, entities, game) { /* override */ }
  render(ctx, camera)                 { /* override */ }

  destroy() {
    this.alive = false;
  }
}