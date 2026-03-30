// ============================================================
//  Super Mario Bros Clone — Player (Mario)
// ============================================================

import { Entity } from './entity.js';
import {
   SCALE, TILE_SIZE,
   PLAYER_W, PLAYER_H_SMALL, PLAYER_H_BIG,
   WALK_SPEED, RUN_SPEED, ACCEL, DECEL,
   JUMP_FORCE, JUMP_HOLD_FORCE, JUMP_HOLD_FRAMES,
   GRAVITY, MAX_FALL_SPEED,
   INVINCIBLE_TIME, STAR_TIME, DEATH_DELAY,
   ENTITY_TYPE, SCORE,
} from '../constants.js';
import { ACTION } from '../input.js';
import { drawMario } from '../spritesheet.js';
import { drawFireball } from '../spritesheet.js';

const T = TILE_SIZE * SCALE;

// Player power states
export const POWER = Object.freeze({
  SMALL: 'SMALL',
  BIG:   'BIG',
  FIRE:  'FIRE',
});

export class Player extends Entity {
  constructor(x, y, game) {
    super(x, y, PLAYER_W, PLAYER_H_SMALL, ENTITY_TYPE.PLAYER);
    this.game = game;

    this.power        = POWER.SMALL;
    this.lives        = 3;
    this.score        = 0;
    this.coins        = 0;

    this.dead         = false;
    this.dying        = false;
    this.dyingTimer   = 0;

    this.invincible   = false;
    this.invTimer     = 0;

    this.starPower    = false;
    this.starTimer    = 0;

    this.jumpHeld     = false;
    this.jumpFrames   = 0;
    this.canJump      = true;

    this.growing      = false;
    this.growTimer    = 0;

    this.fireballCooldown = 0;

    // Animation
    this.walkFrame    = 0;
    this.walkTimer    = 0;
    this.WALK_ANIM_SPEED = 6;  // frames per anim step
  }

  // ── Accessors ──────────────────────────────────────────────

  get isBig()  { return this.power !== POWER.SMALL; }
  get isFire() { return this.power === POWER.FIRE; }

  // ── Power-up / damage ──────────────────────────────────────

  grow(newPower) {
    if (newPower === POWER.FIRE && this.power === POWER.SMALL) {
      // Must be big first — treat as mushroom
      this._setBig();
      return;
    }
    if (newPower === POWER.BIG && this.power === POWER.SMALL) {
      this._setBig();
    } else if (newPower === POWER.FIRE) {
      this.power = POWER.FIRE;
    }
    this.game.audio.powerUp();
    this.addScore(SCORE.MUSHROOM);
  }

  _setBig() {
    this.power = POWER.BIG;
    const oldBottom = this.bottom;
    this.height = PLAYER_H_BIG;
    this.y = oldBottom - this.height;
  }

  takeDamage() {
    if (this.invincible || this.starPower || this.dying) return;

    if (this.isBig) {
      // Shrink
      this.power = POWER.SMALL;
      const oldBottom = this.bottom;
      this.height = PLAYER_H_SMALL;
      this.y = oldBottom - this.height;
      this._startInvincible();
      this.game.audio.powerUp();  // shrink sound
    } else {
      this._die();
    }
  }

  _startInvincible() {
    this.invincible = true;
    this.invTimer   = INVINCIBLE_TIME;
  }

  activateStar() {
    this.starPower = true;
    this.starTimer = STAR_TIME;
    this.invincible = true;
    this.invTimer   = STAR_TIME;
  }

  addScore(pts) {
    this.score += pts;
  }

  addCoin() {
    this.coins++;
    this.addScore(SCORE.COIN);
    if (this.coins >= 100) {
      this.coins -= 100;
      this.lives++;
      this.game.audio.oneUp();
    } else {
      this.game.audio.coin();
    }
  }

  _die() {
    if (this.dying) return;
    this.dying    = true;
    this.dead     = true;
    this.dyingTimer = DEATH_DELAY;
    this.vy       = JUMP_FORCE * 0.8;
    this.vx       = 0;
    this.lives--;
    this.game.audio.death();
    this.game.audio.stopMusic();
  }

  // ── Update ─────────────────────────────────────────────────

  update(dt, tilemap, entities, game) {
    if (this.dying) {
      this._updateDying(dt, tilemap);
      return;
    }

    this._updateTimers(dt);
    this._handleInput(game.input, game);
    this.applyPhysics(tilemap, dt);
    this._updateAnimation(dt);
    this._checkFallDeath();
    this._checkEntityCollisions(entities, game);
    this._checkCoinTiles(tilemap, game);
  }

  _updateDying(dt, tilemap) {
    this.vy = Math.min(this.vy + GRAVITY * dt, MAX_FALL_SPEED);
    this.y += this.vy * dt;
    this.dyingTimer -= dt;
    if (this.dyingTimer <= 0) {
      this.game.onPlayerDied();
    }
  }

  _updateTimers(dt) {
    if (this.invincible) {
      this.invTimer -= dt;
      if (this.invTimer <= 0) {
        this.invincible = false;
        this.starPower  = false;
      }
    }
    if (this.starPower) {
      this.starTimer -= dt;
    }
    if (this.fireballCooldown > 0) this.fireballCooldown -= dt;
  }

  _handleInput(input, game) {
    const left  = input.isDown(ACTION.LEFT);
    const right = input.isDown(ACTION.RIGHT);
    const jump  = input.isDown(ACTION.JUMP);
    const run   = input.isDown(ACTION.RUN);

    const maxSpeed = run ? RUN_SPEED : WALK_SPEED;

    // Horizontal movement
    if (left && !right) {
      this.vx = Math.max(this.vx - ACCEL, -maxSpeed);
      this.facingRight = false;
    } else if (right && !left) {
      this.vx = Math.min(this.vx + ACCEL, maxSpeed);
      this.facingRight = true;
    } else {
      // Decelerate
      if (this.vx > 0) this.vx = Math.max(0, this.vx - DECEL);
      if (this.vx < 0) this.vx = Math.min(0, this.vx + DECEL);
    }

    // Jump
    if (jump && this.canJump && this.onGround) {
      this.vy        = JUMP_FORCE;
      this.jumpHeld  = true;
      this.jumpFrames = 0;
      this.canJump   = false;
      game.audio.jump();
    }

    if (jump && this.jumpHeld && this.jumpFrames < JUMP_HOLD_FRAMES) {
      this.vy += JUMP_HOLD_FORCE;
      this.jumpFrames++;
    }

    if (!jump) {
      this.jumpHeld = false;
      if (this.onGround) this.canJump = true;
    }

    if (this.onGround) this.canJump = true;

    // Fire
    if (run && input.isPressed(ACTION.RUN) && this.isFire && this.fireballCooldown <= 0) {
      this._shootFireball(game);
    }
  }

  _shootFireball(game) {
    this.fireballCooldown = 20;
    const fb = {
      type: ENTITY_TYPE.FIREBALL,
      x: this.facingRight ? this.right : this.left - T * 0.5,
      y: this.centerY - T * 0.25,
      width:  T * 0.5,
      height: T * 0.5,
      vx: (this.facingRight ? 1 : -1) * WALK_SPEED * 1.5,
      vy: 0,
      alive: true,
      bounces: 0,
      animFrame: 0,
      animTimer: 0,
      update(dt, tilemap, entities, g) {
        this.vy = Math.min(this.vy + GRAVITY * dt * 0.5, MAX_FALL_SPEED * 0.5);
        this.x += this.vx * dt;
        this.y += this.vy * dt;
        // Bounce off ground
        const tiles = tilemap.getSolidTilesInRect(this.x, this.y, this.width, this.height);
        for (const tile of tiles) {
          const dy = this.centerY - (tile.y + tile.h/2);
          if (dy < 0 && this.vy > 0) {
            this.y = tile.y - this.height;
            this.vy = JUMP_FORCE * 0.4;
            this.bounces++;
          } else {
            this.alive = false;
          }
        }
        if (this.bounces > 5) this.alive = false;
        // Off screen
        if (this.x < g.camera.x - T || this.x > g.camera.x + g.camera.width + T) {
          this.alive = false;
        }
        // Hit enemies
        for (const e of entities) {
          if ((e.type === ENTITY_TYPE.GOOMBA || e.type === ENTITY_TYPE.KOOPA) && e.alive) {
            if (this.x < e.right && this.right > e.x && this.y < e.bottom && this.bottom > e.y) {
              e.alive = false;
              g.spawnScorePop(e.centerX, e.y, SCORE.GOOMBA);
              g.player.addScore(SCORE.GOOMBA);
              this.alive = false;
            }
          }
        }
        this.animTimer++;
        if (this.animTimer > 4) { this.animFrame++; this.animTimer = 0; }
      },
      render(ctx, camera) {
        // Import drawFireball inline to avoid circular dependency
        const sx = this.x - camera.x;
        const sy = this.y - camera.y;
         // Draw simple fireball fallback
         ctx.fillStyle = this.animFrame % 2 === 0 ? '#fcfcfc' : '#fcd800';
         ctx.beginPath();
         ctx.arc(sx + this.width/2, sy + this.height/2, this.width/2, 0, Math.PI*2);
         ctx.fill();
         ctx.fillStyle = '#e40058';
         ctx.beginPath();
         ctx.arc(sx + this.width/2, sy + this.height/2, this.width/4, 0, Math.PI*2);
         ctx.fill();
      },
      get centerX() { return this.x + this.width/2; },
      get centerY() { return this.y + this.height/2; },
      get left()    { return this.x; },
      get right()   { return this.x + this.width; },
      get bottom()  { return this.y + this.height; },
    };
    game.entities.push(fb);
  }

  _updateAnimation(dt) {
    if (!this.onGround) {
      this.walkFrame = 1;
      return;
    }
    if (Math.abs(this.vx) > 0.5) {
      this.walkTimer++;
      if (this.walkTimer >= this.WALK_ANIM_SPEED) {
        this.walkTimer = 0;
        this.walkFrame = (this.walkFrame + 1) % 3;
      }
    } else {
      this.walkFrame = 0;
    }
  }

  _checkFallDeath() {
    if (this.y > this.game.tilemap.heightPx + T * 2) {
      this._die();
    }
  }

  _checkEntityCollisions(entities, game) {
    for (const e of entities) {
      if (!e.alive || e === this) continue;
      if (!this.overlaps(e)) continue;

      if (e.type === ENTITY_TYPE.GOOMBA || e.type === ENTITY_TYPE.KOOPA) {
        this._handleEnemyCollision(e, game);
      } else if (
        e.type === ENTITY_TYPE.MUSHROOM ||
        e.type === ENTITY_TYPE.FLOWER   ||
        e.type === ENTITY_TYPE.STAR     ||
        e.type === ENTITY_TYPE.ONEUP
      ) {
        this._collectPowerUp(e, game);
      } else if (e.type === ENTITY_TYPE.COIN_POP) {
        // handled by coin entity itself
      }
    }
  }

  _handleEnemyCollision(enemy, game) {
    if (this.starPower) {
      enemy.alive = false;
      game.spawnScorePop(enemy.centerX, enemy.y, SCORE.GOOMBA);
      this.addScore(SCORE.GOOMBA);
      game.audio.stomp();
      return;
    }

    // Stomp from above
    const stompThreshold = enemy.top + enemy.height * 0.35;
    if (this.bottom <= stompThreshold + Math.abs(this.vy) + 4 && this.vy >= 0) {
      if (enemy.type === ENTITY_TYPE.KOOPA && !enemy.inShell) {
        enemy.enterShell();
      } else if (enemy.type === ENTITY_TYPE.KOOPA && enemy.inShell) {
        enemy.kickShell(this.centerX < enemy.centerX);
      } else {
        enemy.squish();
      }
      this.vy = JUMP_FORCE * 0.5;
      this.addScore(SCORE.GOOMBA);
      game.spawnScorePop(enemy.centerX, enemy.y, SCORE.GOOMBA);
      game.audio.stomp();
    } else {
      this.takeDamage();
    }
  }

  _collectPowerUp(item, game) {
    item.alive = false;
    if (item.type === ENTITY_TYPE.MUSHROOM) {
      this.grow(POWER.BIG);
      this.addScore(SCORE.MUSHROOM);
      game.spawnScorePop(item.centerX, item.y, SCORE.MUSHROOM);
    } else if (item.type === ENTITY_TYPE.FLOWER) {
      this.grow(POWER.FIRE);
      this.addScore(SCORE.FLOWER);
      game.spawnScorePop(item.centerX, item.y, SCORE.FLOWER);
    } else if (item.type === ENTITY_TYPE.STAR) {
      this.activateStar();
      this.addScore(SCORE.STAR);
      game.spawnScorePop(item.centerX, item.y, SCORE.STAR);
    } else if (item.type === ENTITY_TYPE.ONEUP) {
      this.lives++;
      game.audio.oneUp();
      game.spawnScorePop(item.centerX, item.y, 0, '1UP');
    }
  }

  _checkCoinTiles(tilemap, game) {
    // Check if Mario is overlapping a coin tile
    const c0 = tilemap.worldToTile(this.left  + 2);
    const c1 = tilemap.worldToTile(this.right  - 2);
    const r0 = tilemap.worldToTile(this.top    + 2);
    const r1 = tilemap.worldToTile(this.bottom - 2);
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        if (tilemap.getTile(c, r) === 8 /* COIN */) {
          tilemap.setTile(c, r, 0);
          this.addCoin();
        }
      }
    }
  }

  // ── Render ─────────────────────────────────────────────────

  render(ctx, camera) {
    if (this.dying && this.y > camera.y + camera.height + T) return;

    const sx = this.x - camera.x;
    const sy = this.y - camera.y;

    drawMario(ctx, sx, sy, this.width, this.height, {
      big:         this.isBig,
      facingRight: this.facingRight,
      frame:       this.walkFrame,
      dead:        this.dead && this.dying,
      fire:        this.isFire,
      invincible:  this.invincible && !this.starPower,
      star:        this.starPower,
    });
  }
}