// ============================================================
//  Super Mario Bros Clone — Goomba Enemy
// ============================================================

import { Entity } from './entity.js';
import { ENTITY_TYPE, SCALE, TILE_SIZE } from '../constants.js';
import { drawGoomba } from '../spritesheet.js';

const T  = TILE_SIZE * SCALE;
const GOOMBA_SPEED = 1.2 * SCALE;
const GOOMBA_W     = 14 * SCALE;
const GOOMBA_H     = 14 * SCALE;

export class Goomba extends Entity {
  constructor(x, y) {
    super(x, y, GOOMBA_W, GOOMBA_H, ENTITY_TYPE.GOOMBA);
    this.vx       = -GOOMBA_SPEED;
    this.squished = false;
    this.squishTimer = 0;
    this.walkTimer   = 0;
    this.walkFrame   = 0;
  }

  squish() {
    if (this.squished) return;
    this.squished    = true;
    this.squishTimer = 30;
    this.vx          = 0;
    this.vy          = 0;
  }

  update(dt, tilemap, entities, game) {
    if (!this.alive) return;

    if (this.squished) {
      this.squishTimer -= dt;
      if (this.squishTimer <= 0) this.alive = false;
      return;
    }

    // Only update if near camera
    if (!game.camera.isVisible(this.x - T, this.y - T, this.width + T*2, this.height + T*2)) {
      return;
    }

    this.applyPhysics(tilemap, dt);

    // Turn around at walls (vx was zeroed by collision)
    if (this.vx === 0) {
      this.vx = this.facingRight ? -GOOMBA_SPEED : GOOMBA_SPEED;
    }

    // Turn around at ledge edges
    if (this.onGround) {
      const edgeCol = this.facingRight
        ? tilemap.worldToTile(this.right + 2)
        : tilemap.worldToTile(this.left  - 2);
      const belowRow = tilemap.worldToTile(this.bottom + 2);
      if (!tilemap.isSolid(edgeCol, belowRow)) {
        this.vx = -this.vx;
      }
    }

    this.facingRight = this.vx > 0;

    // Animation
    this.walkTimer++;
    if (this.walkTimer >= 10) {
      this.walkTimer = 0;
      this.walkFrame = (this.walkFrame + 1) % 2;
    }

    // Fall off screen
    if (this.y > tilemap.heightPx + T * 2) this.alive = false;
  }

  render(ctx, camera) {
    if (!camera.isVisible(this.x, this.y, this.width, this.height)) return;
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawGoomba(ctx, sx, sy, this.width, this.height, this.walkFrame, this.squished);
  }
}