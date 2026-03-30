// ============================================================
//  Super Mario Bros Clone — Power-up Entities
//  Mushroom, Fire Flower, Star, 1-Up
// ============================================================

import { Entity } from './entity.js';
import {
  ENTITY_TYPE, SCALE, TILE_SIZE,
  WALK_SPEED, GRAVITY, MAX_FALL_SPEED,
} from '../constants.js';
import {
   drawMushroom, drawFireFlower, drawStar,
   drawOneUp, drawCoin, drawScorePop, drawParticle,
} from '../spritesheet.js';

const T = TILE_SIZE * SCALE;
const PU_W = 14 * SCALE;
const PU_H = 14 * SCALE;

// ── Base PowerUp ───────────────────────────────────────────

class PowerUp extends Entity {
  constructor(x, y, type) {
    super(x, y, PU_W, PU_H, type);
    this.emerging    = true;
    this.emergeY     = y;
    this.emergeTarget = y - T;
    this.emergeSpeed  = 1.5;
  }

  update(dt, tilemap, entities, game) {
    if (!this.alive) return;

    if (this.emerging) {
      this.y -= this.emergeSpeed * dt;
      if (this.y <= this.emergeTarget) {
        this.y = this.emergeTarget;
        this.emerging = false;
        this._onEmerged();
      }
      return;
    }

    this._updateMovement(dt, tilemap);
  }

  _onEmerged() { /* override */ }
  _updateMovement(dt, tilemap) { /* override */ }
}

// ── Mushroom ───────────────────────────────────────────────

export class Mushroom extends PowerUp {
  constructor(x, y, oneUp = false) {
    super(x, y, oneUp ? ENTITY_TYPE.ONEUP : ENTITY_TYPE.MUSHROOM);
    this.oneUp = oneUp;
    this.vx    = WALK_SPEED * 0.8;
  }

  _onEmerged() {
    this.vx = WALK_SPEED * 0.8;
  }

  _updateMovement(dt, tilemap) {
    this.vy = Math.min(this.vy + GRAVITY * dt, MAX_FALL_SPEED);
    this.x += this.vx * dt;
    this.y += this.vy * dt;

    // Tile collisions (simplified)
    const tiles = tilemap.getSolidTilesInRect(this.x, this.y, this.width, this.height);
    for (const tile of tiles) {
      const dx = this.centerX - (tile.x + tile.w/2);
      const dy = this.centerY - (tile.y + tile.h/2);
      const ox = (this.width/2 + tile.w/2) - Math.abs(dx);
      const oy = (this.height/2 + tile.h/2) - Math.abs(dy);
      if (ox > 0 && oy > 0) {
        if (ox < oy) {
          this.x += dx > 0 ? ox : -ox;
          this.vx = -this.vx;
        } else {
          this.y += dy > 0 ? oy : -oy;
          if (dy > 0) this.vy = 0;
          else        this.vy = Math.max(0, this.vy);
        }
      }
    }

    if (this.y > tilemap.heightPx + T) this.alive = false;
  }

  render(ctx, camera) {
    if (!camera.isVisible(this.x, this.y, this.width, this.height)) return;
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    if (this.oneUp) drawOneUp(ctx, sx, sy, this.width, this.height);
    else            drawMushroom(ctx, sx, sy, this.width, this.height);
  }
}

// ── Fire Flower ────────────────────────────────────────────

export class FireFlower extends PowerUp {
  constructor(x, y) {
    super(x, y, ENTITY_TYPE.FLOWER);
    this.bobTimer = 0;
    this.bobY     = y - T;
  }

  _onEmerged() {}

  _updateMovement(dt, tilemap) {
    // Flowers just bob in place
    this.bobTimer += dt;
    this.y = this.bobY + Math.sin(this.bobTimer * 0.08) * 3;
  }

  render(ctx, camera) {
    if (!camera.isVisible(this.x, this.y, this.width, this.height)) return;
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawFireFlower(ctx, sx, sy, this.width, this.height);
  }
}

// ── Star ───────────────────────────────────────────────────

export class Star extends PowerUp {
  constructor(x, y) {
    super(x, y, ENTITY_TYPE.STAR);
    this.vx = WALK_SPEED * 0.9;
    this.animFrame = 0;
    this.animTimer = 0;
  }

  _onEmerged() {
    this.vx = WALK_SPEED * 0.9;
    this.vy = -6 * SCALE;
  }

  _updateMovement(dt, tilemap) {
    this.vy = Math.min(this.vy + GRAVITY * dt, MAX_FALL_SPEED);
    this.x += this.vx * dt;
    this.y += this.vy * dt;

    const tiles = tilemap.getSolidTilesInRect(this.x, this.y, this.width, this.height);
    for (const tile of tiles) {
      const dx = this.centerX - (tile.x + tile.w/2);
      const dy = this.centerY - (tile.y + tile.h/2);
      const ox = (this.width/2 + tile.w/2) - Math.abs(dx);
      const oy = (this.height/2 + tile.h/2) - Math.abs(dy);
      if (ox > 0 && oy > 0) {
        if (ox < oy) {
          this.x += dx > 0 ? ox : -ox;
          this.vx = -this.vx;
        } else {
          this.y += dy > 0 ? oy : -oy;
          if (dy > 0) this.vy = -6 * SCALE;  // bounce
          else        this.vy = Math.max(0, this.vy);
        }
      }
    }

    this.animTimer++;
    if (this.animTimer >= 4) { this.animFrame++; this.animTimer = 0; }
    if (this.y > tilemap.heightPx + T) this.alive = false;
  }

  render(ctx, camera) {
    if (!camera.isVisible(this.x, this.y, this.width, this.height)) return;
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawStar(ctx, sx, sy, this.width, this.height, this.animFrame);
  }
}

// ── Coin Pop (from ? block) ────────────────────────────────

export class CoinPop extends Entity {
  constructor(x, y) {
    super(x, y, T * 0.5, T * 0.5, ENTITY_TYPE.COIN_POP);
    this.vy        = -8 * SCALE;
    this.animFrame = 0;
    this.animTimer = 0;
    this.lifetime  = 40;
  }

  update(dt, tilemap, entities, game) {
    this.vy = Math.min(this.vy + GRAVITY * dt * 0.8, MAX_FALL_SPEED);
    this.y += this.vy * dt;
    this.lifetime -= dt;
    if (this.lifetime <= 0) this.alive = false;

    this.animTimer++;
    if (this.animTimer >= 4) { this.animFrame++; this.animTimer = 0; }
  }

  render(ctx, camera) {
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawCoin(ctx, sx, sy, this.width, this.height, this.animFrame);
  }
}

// ── Score Pop ─────────────────────────────────────────────

export class ScorePop {
  constructor(x, y, value, label = null) {
    this.x       = x;
    this.y       = y;
    this.value   = value;
    this.label   = label || String(value);
    this.vy      = -1.5 * SCALE;
    this.lifetime = 50;
    this.alive   = true;
    this.type    = 'SCORE_POP';
  }

  update(dt) {
    this.y -= 1.2 * dt;
    this.lifetime -= dt;
    if (this.lifetime <= 0) this.alive = false;
  }

  render(ctx, camera) {
    const alpha = Math.min(1, this.lifetime / 20);
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawScorePop(ctx, sx, sy, this.label, alpha);
  }
}

// ── Brick Particle ─────────────────────────────────────────

export class BrickParticle {
  constructor(x, y, vx, vy) {
    this.x    = x;
    this.y    = y;
    this.vx   = vx;
    this.vy   = vy;
    this.size = 6 * SCALE;
    this.alive = true;
    this.lifetime = 40;
    this.type = 'PARTICLE';
  }

  update(dt) {
    this.vy += GRAVITY * dt * 0.5;
    this.x  += this.vx * dt;
    this.y  += this.vy * dt;
    this.lifetime -= dt;
    if (this.lifetime <= 0) this.alive = false;
  }

  render(ctx, camera) {
    const alpha = Math.min(1, this.lifetime / 20);
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawParticle(ctx, sx, sy, this.size, '#c84c0c', alpha);
  }
}