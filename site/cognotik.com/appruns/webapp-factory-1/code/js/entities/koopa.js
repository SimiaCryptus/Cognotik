// ============================================================
//  Super Mario Bros Clone — Koopa Troopa Enemy
// ============================================================

import { Entity } from './entity.js';
import { ENTITY_TYPE, SCALE, TILE_SIZE, SCORE } from '../constants.js';
import { drawKoopa } from '../spritesheet.js';

const T          = TILE_SIZE * SCALE;
const KOOPA_SPEED = 1.0 * SCALE;
const SHELL_SPEED = 5.0 * SCALE;
const KOOPA_W    = 14 * SCALE;
const KOOPA_H    = 20 * SCALE;
const SHELL_H    = 14 * SCALE;

export class Koopa extends Entity {
  constructor(x, y) {
    super(x, y, KOOPA_W, KOOPA_H, ENTITY_TYPE.KOOPA);
    this.vx       = -KOOPA_SPEED;
    this.inShell  = false;
    this.shellMoving = false;
    this.shellTimer  = 0;   // time before shell starts moving on its own
    this.walkTimer   = 0;
    this.walkFrame   = 0;
  }

  enterShell() {
    if (this.inShell) return;
    this.inShell  = true;
    this.shellMoving = false;
    this.vx       = 0;
    // Resize to shell
    const oldBottom = this.bottom;
    this.height   = SHELL_H;
    this.y        = oldBottom - this.height;
    this.shellTimer = 180;  // 3 seconds before auto-move
  }

  kickShell(toRight) {
    this.shellMoving = true;
    this.vx = toRight ? SHELL_SPEED : -SHELL_SPEED;
    this.shellTimer  = 0;
  }

  update(dt, tilemap, entities, game) {
    if (!this.alive) return;

    if (!game.camera.isVisible(this.x - T, this.y - T, this.width + T*2, this.height + T*2)) {
      return;
    }

    if (this.inShell) {
      this._updateShell(dt, tilemap, entities, game);
    } else {
      this._updateWalking(dt, tilemap);
    }

    if (this.y > tilemap.heightPx + T * 2) this.alive = false;
  }

  _updateWalking(dt, tilemap) {
    this.applyPhysics(tilemap, dt);

    if (this.vx === 0) {
      this.vx = this.facingRight ? -KOOPA_SPEED : KOOPA_SPEED;
    }

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

    this.walkTimer++;
    if (this.walkTimer >= 12) {
      this.walkTimer = 0;
      this.walkFrame = (this.walkFrame + 1) % 2;
    }
  }

  _updateShell(dt, tilemap, entities, game) {
    if (!this.shellMoving) {
      this.shellTimer -= dt;
      if (this.shellTimer <= 0) {
        this.kickShell(true);
      }
      return;
    }

    this.applyPhysics(tilemap, dt);

    // Bounce off walls
    if (this.vx === 0) {
      this.vx = this.facingRight ? -SHELL_SPEED : SHELL_SPEED;
    }
    this.facingRight = this.vx > 0;

    // Hit other enemies
    for (const e of entities) {
      if (e === this || !e.alive) continue;
      if (e.type === ENTITY_TYPE.GOOMBA || e.type === ENTITY_TYPE.KOOPA) {
        if (this.overlaps(e)) {
          e.alive = false;
          game.spawnScorePop(e.centerX, e.y, SCORE.SHELL_HIT);
          game.player.addScore(SCORE.SHELL_HIT);
          game.audio.stomp();
        }
      }
    }
  }

  // Needed for overlaps() in player
  overlaps(other) {
    return (
      this.left   < other.right  &&
      this.right  > other.left   &&
      this.top    < other.bottom &&
      this.bottom > other.top
    );
  }

  render(ctx, camera) {
    if (!camera.isVisible(this.x, this.y, this.width, this.height)) return;
    const sx = this.x - camera.x;
    const sy = this.y - camera.y;
    drawKoopa(ctx, sx, sy, this.width, this.height, this.walkFrame, this.inShell);
  }
}