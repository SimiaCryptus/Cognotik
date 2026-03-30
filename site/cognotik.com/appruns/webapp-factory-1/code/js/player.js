/**
 * player.js
 * Mario player entity — state machine, movement, animation, rendering.
 */

class Player {
  constructor(x, y) {
    this.x = x;
    this.y = y;
    this.vx = 0;
    this.vy = 0;
    this.type = C.ENTITY.PLAYER;

    // Size (logical pixels)
    this.width  = C.PLAYER_WIDTH;
    this.height = C.PLAYER_HEIGHT_SMALL;

    // Power state
    this.powerState = 'small'; // small | big | fire
    this.invincible = false;
    this.invincibleTimer = 0;
    this.starPower  = false;
    this.starTimer  = 0;
    this.blinkTimer = 0;

    // Movement state
    this.onGround   = false;
    this.facingRight = true;
    this.jumping    = false;
    this.jumpHoldTimer = 0;
    this.running    = false;
    this.crouching  = false;

    // Animation
    this.animState  = 'idle'; // idle | walk | run | jump | fall | crouch | dead
    this.animFrame  = 0;
    this.animTimer  = 0;
    this.walkCycle  = 0;

    // Death
    this.dead       = false;
    this.deadTimer  = 0;
    this.deadBounce = false;

    // Fireballs
    this.fireballs  = [];
    this.fireTimer  = 0;

    // Flagpole
    this.onFlagpole = false;
    this.flagpoleY  = 0;
    this.flagpoleX  = 0;
    this.flagpoleTimer = 0;
  }

  get isBig() { return this.powerState !== 'small'; }

  get centerX() { return this.x + this.width / 2; }
  get centerY() { return this.y + this.height / 2; }
  get bottom()  { return this.y + this.height; }
  get right()   { return this.x + this.width; }

  /** Grow to big Mario */
  powerUp(type) {
    if (type === C.ENTITY.MUSHROOM || type === C.ENTITY.ONEUP) {
      if (this.powerState === 'small') {
        this.powerState = 'big';
        this.height = C.PLAYER_HEIGHT_BIG;
        this.y -= (C.PLAYER_HEIGHT_BIG - C.PLAYER_HEIGHT_SMALL);
      }
    } else if (type === C.ENTITY.FIRE_FLOWER) {
      if (this.powerState !== 'fire') {
        if (this.powerState === 'small') {
          this.height = C.PLAYER_HEIGHT_BIG;
          this.y -= (C.PLAYER_HEIGHT_BIG - C.PLAYER_HEIGHT_SMALL);
        }
        this.powerState = 'fire';
      }
    } else if (type === C.ENTITY.STAR) {
      this.starPower = true;
      this.starTimer = C.STAR_DURATION;
    }
  }

  /** Take damage */
  takeDamage() {
    if (this.invincible || this.starPower) return false;
    if (this.powerState !== 'small') {
      this.powerState = 'small';
      this.height = C.PLAYER_HEIGHT_SMALL;
      this.y += (C.PLAYER_HEIGHT_BIG - C.PLAYER_HEIGHT_SMALL);
      this.invincible = true;
      this.invincibleTimer = C.INVINCIBLE_DURATION;
      return false; // didn't die
    }
    return true; // died
  }

  /** Trigger death sequence */
  die() {
    if (this.dead) return;
    this.dead = true;
    this.deadTimer = 0;
    this.deadBounce = false;
    this.vx = 0;
    this.vy = 0;
    this.animState = 'dead';
  }

  update(dt, input, tiles, levelWidth, audio, game) {
    if (this.dead) {
      this._updateDead(dt);
      return;
    }

    if (this.onFlagpole) {
      this._updateFlagpole(dt, game);
      return;
    }

    // Timers
    if (this.invincible) {
      this.invincibleTimer -= dt;
      this.blinkTimer += dt;
      if (this.invincibleTimer <= 0) {
        this.invincible = false;
        this.invincibleTimer = 0;
      }
    }
    if (this.starPower) {
      this.starTimer -= dt;
      if (this.starTimer <= 0) {
        this.starPower = false;
        this.starTimer = 0;
      }
    }
    if (this.fireTimer > 0) this.fireTimer -= dt;

    // Input
    this._handleInput(dt, input, audio);

    // Physics
    this._applyPhysics(dt);

    // Collision
    this._resolveCollision(tiles, levelWidth);

    // Update fireballs
    this.fireballs = this.fireballs.filter(f => f.active);
    this.fireballs.forEach(f => f.update(dt, tiles));

    // Animation
    this._updateAnimation(dt);
  }

  _handleInput(dt, input, audio) {
    this.running = input.run;

    // Horizontal movement
    const speed = this.running ? C.RUN_SPEED : C.WALK_SPEED;
    if (input.left) {
      this.vx -= C.ACCELERATION * dt;
      if (this.vx < -speed) this.vx = -speed;
      this.facingRight = false;
    } else if (input.right) {
      this.vx += C.ACCELERATION * dt;
      if (this.vx > speed) this.vx = speed;
      this.facingRight = true;
    } else {
      // Friction
      const friction = this.onGround ? C.FRICTION_GROUND : C.FRICTION_AIR;
      this.vx *= Math.pow(friction, dt * 60);
      if (Math.abs(this.vx) < 2) this.vx = 0;
    }

    // Jump
    if (input.jumpPressed && this.onGround) {
      this.vy = C.JUMP_FORCE;
      this.jumping = true;
      this.jumpHoldTimer = 0;
      this.onGround = false;
      audio.playJump();
    }

    // Variable jump height (hold to jump higher)
    if (input.jumpHeld && this.jumping && !this.onGround) {
      this.jumpHoldTimer += dt;
      if (this.jumpHoldTimer < C.JUMP_HOLD_MAX_TIME) {
        this.vy += C.JUMP_HOLD_GRAVITY * dt;
      }
    }
    if (!input.jumpHeld) {
      this.jumping = false;
    }

    // Fire
    if (input.fire && this.powerState === 'fire' && this.fireTimer <= 0) {
      const dir = this.facingRight ? 1 : -1;
      const fb  = new Fireball(
        this.x + (dir > 0 ? this.width : 0),
        this.y + this.height * 0.3,
        dir
      );
      this.fireballs.push(fb);
      this.fireTimer = 0.35;
      audio.playFireball();
    }
  }

  _applyPhysics(dt) {
    // Gravity
    this.vy += C.GRAVITY * dt;
    if (this.vy > C.TERMINAL_VELOCITY) this.vy = C.TERMINAL_VELOCITY;
  }

  _resolveCollision(tiles, levelWidth) {
    // Move X
    this.x += this.vx * dt;
    this._resolveX(tiles, levelWidth);

    // Move Y
    this.y += this.vy * dt;
    this.onGround = false;
    this._resolveY(tiles);
  }

  // dt is captured via closure from update() — pass it explicitly
  _resolveCollision(tiles, levelWidth) {
    // This method is called from update() where dt is in scope
    // We need dt here — it's passed via the update call chain
    // Workaround: store dt on instance temporarily
    this.x += this.vx * this._dt;
    this._resolveX(tiles, levelWidth);
    this.y += this.vy * this._dt;
    this.onGround = false;
    this._resolveY(tiles);
  }

  _resolveX(tiles, levelWidth) {
    // World bounds
    if (this.x < 0) { this.x = 0; this.vx = 0; }
    if (this.x + this.width > levelWidth) { this.x = levelWidth - this.width; this.vx = 0; }

    const left  = Math.floor(this.x / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 1) / TILE_SIZE);
    const top   = Math.floor((this.y + 2) / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 2) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (tile && tile.isSolid) {
          if (this.vx > 0) {
            this.x = tile.x - this.width;
            this.vx = 0;
          } else if (this.vx < 0) {
            this.x = tile.x + tile.width;
            this.vx = 0;
          }
          return;
        }
      }
    }
  }

  _resolveY(tiles) {
    const left  = Math.floor((this.x + 2) / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 3) / TILE_SIZE);
    const top   = Math.floor(this.y / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 1) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (!tile) continue;

        if (tile.isSolid) {
          if (this.vy >= 0) {
            // Landing on top
            this.y = tile.y - this.height;
            this.vy = 0;
            this.onGround = true;
            this.jumping  = false;
          } else {
            // Hitting ceiling
            this.y = tile.y + tile.height;
            this.vy = 0;
          }
          return;
        }

        if (tile.isPassThrough && this.vy >= 0) {
          // Only land on pass-through from above
          const prevBot = (this.y - this.vy * this._dt) + this.height;
          if (prevBot <= tile.y + 2) {
            this.y = tile.y - this.height;
            this.vy = 0;
            this.onGround = true;
            this.jumping  = false;
            return;
          }
        }
      }
    }
  }

  _updateDead(dt) {
    this.deadTimer += dt;
    if (!this.deadBounce && this.deadTimer > 0.4) {
      this.deadBounce = true;
      this.vy = -400;
    }
    if (this.deadBounce) {
      this.vy += C.GRAVITY * dt;
      this.y  += this.vy * dt;
    }
  }

  _updateFlagpole(dt, game) {
    this.flagpoleTimer += dt;
    // Slide down the pole
    this.y += 80 * dt;
    if (this.y >= this.flagpoleY) {
      this.y = this.flagpoleY;
      if (this.flagpoleTimer > 2) {
        game.levelComplete();
      }
    }
  }

  _updateAnimation(dt) {
    this.animTimer += dt;

    if (!this.onGround) {
      this.animState = this.vy < 0 ? 'jump' : 'fall';
    } else if (Math.abs(this.vx) > 5) {
      this.animState = Math.abs(this.vx) > C.WALK_SPEED * 0.8 ? 'run' : 'walk';
      const frameRate = 0.08 + (1 - Math.abs(this.vx) / C.RUN_SPEED) * 0.1;
      if (this.animTimer > frameRate) {
        this.animTimer = 0;
        this.animFrame = (this.animFrame + 1) % 3;
      }
    } else {
      this.animState = 'idle';
      this.animFrame = 0;
    }
  }

  update(dt, input, tiles, levelWidth, audio, game) {
    this._dt = dt; // store for use in collision methods

    if (this.dead) {
      this._updateDead(dt);
      return;
    }

    if (this.onFlagpole) {
      this._updateFlagpole(dt, game);
      return;
    }

    // Timers
    if (this.invincible) {
      this.invincibleTimer -= dt;
      this.blinkTimer += dt;
      if (this.invincibleTimer <= 0) {
        this.invincible = false;
        this.invincibleTimer = 0;
      }
    }
    if (this.starPower) {
      this.starTimer -= dt;
      if (this.starTimer <= 0) {
        this.starPower = false;
        this.starTimer = 0;
      }
    }
    if (this.fireTimer > 0) this.fireTimer -= dt;

    this._handleInput(dt, input, audio);
    this._applyPhysics(dt);
    this._resolveCollision(tiles, levelWidth);

    this.fireballs = this.fireballs.filter(f => f.active);
    this.fireballs.forEach(f => f.update(dt, tiles));

    this._updateAnimation(dt);
  }

  draw(ctx, camX, scale) {
    if (!this.active && !this.dead) return;

    // Blink when invincible
    if (this.invincible && Math.floor(this.blinkTimer * 10) % 2 === 0) return;

    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;

    // Star rainbow effect
    if (this.starPower) {
      ctx.globalAlpha = 0.85;
    }

    if (this.dead) {
      this._drawDead(ctx, sx, sy, sw, sh);
    } else if (this.isBig) {
      this._drawBigMario(ctx, sx, sy, sw, sh);
    } else {
      this._drawSmallMario(ctx, sx, sy, sw, sh);
    }

    ctx.globalAlpha = 1;

    // Draw fireballs
    this.fireballs.forEach(f => f.draw(ctx, camX, scale));
  }

  _drawSmallMario(ctx, x, y, w, h) {
    const flip = !this.facingRight;
    ctx.save();
    if (flip) {
      ctx.translate(x + w, y);
      ctx.scale(-1, 1);
      x = 0; y = 0;
    }

    const skin  = C.COLOR.MARIO_SKIN;
    const red   = C.COLOR.MARIO_RED;
    const brown = C.COLOR.MARIO_BROWN;
    const blue  = C.COLOR.MARIO_BLUE;

    // Hat
    ctx.fillStyle = red;
    ctx.fillRect(x + w * 0.1, y, w * 0.8, h * 0.25);
    ctx.fillRect(x, y + h * 0.12, w, h * 0.12);

    // Face
    ctx.fillStyle = skin;
    ctx.fillRect(x + w * 0.15, y + h * 0.25, w * 0.7, h * 0.3);

    // Eyes
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.55, y + h * 0.3, w * 0.2, h * 0.12);

    // Mustache
    ctx.fillStyle = brown;
    ctx.fillRect(x + w * 0.3, y + h * 0.45, w * 0.55, h * 0.1);

    // Body (overalls)
    ctx.fillStyle = red;
    ctx.fillRect(x + w * 0.15, y + h * 0.55, w * 0.7, h * 0.3);
    ctx.fillStyle = blue;
    ctx.fillRect(x + w * 0.2, y + h * 0.55, w * 0.25, h * 0.3);
    ctx.fillRect(x + w * 0.55, y + h * 0.55, w * 0.25, h * 0.3);

    // Legs
    const legOff = this.animFrame === 1 ? h * 0.08 : (this.animFrame === 2 ? -h * 0.08 : 0);
    ctx.fillStyle = blue;
    ctx.fillRect(x + w * 0.15, y + h * 0.82 + legOff, w * 0.3, h * 0.18);
    ctx.fillRect(x + w * 0.55, y + h * 0.82 - legOff, w * 0.3, h * 0.18);

    // Shoes
    ctx.fillStyle = brown;
    ctx.fillRect(x + w * 0.1, y + h * 0.88 + legOff, w * 0.35, h * 0.12);
    ctx.fillRect(x + w * 0.5, y + h * 0.88 - legOff, w * 0.4, h * 0.12);

    ctx.restore();
  }

  _drawBigMario(ctx, x, y, w, h) {
    const flip = !this.facingRight;
    ctx.save();
    if (flip) {
      ctx.translate(x + w, y);
      ctx.scale(-1, 1);
      x = 0; y = 0;
    }

    const skin  = C.COLOR.MARIO_SKIN;
    const red   = this.powerState === 'fire' ? '#fff' : C.COLOR.MARIO_RED;
    const brown = C.COLOR.MARIO_BROWN;
    const blue  = this.powerState === 'fire' ? '#e40000' : C.COLOR.MARIO_BLUE;

    // Hat
    ctx.fillStyle = red;
    ctx.fillRect(x + w * 0.1, y, w * 0.8, h * 0.18);
    ctx.fillRect(x, y + h * 0.08, w, h * 0.1);

    // Face
    ctx.fillStyle = skin;
    ctx.fillRect(x + w * 0.15, y + h * 0.18, w * 0.7, h * 0.22);

    // Eyes
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.55, y + h * 0.22, w * 0.18, h * 0.09);

    // Mustache
    ctx.fillStyle = brown;
    ctx.fillRect(x + w * 0.3, y + h * 0.34, w * 0.55, h * 0.07);

    // Body
    ctx.fillStyle = red;
    ctx.fillRect(x + w * 0.1, y + h * 0.4, w * 0.8, h * 0.28);

    // Overalls
    ctx.fillStyle = blue;
    ctx.fillRect(x + w * 0.15, y + h * 0.4, w * 0.28, h * 0.28);
    ctx.fillRect(x + w * 0.57, y + h * 0.4, w * 0.28, h * 0.28);

    // Legs
    const legOff = this.animFrame === 1 ? h * 0.06 : (this.animFrame === 2 ? -h * 0.06 : 0);
    ctx.fillStyle = blue;
    ctx.fillRect(x + w * 0.1, y + h * 0.68 + legOff, w * 0.35, h * 0.2);
    ctx.fillRect(x + w * 0.55, y + h * 0.68 - legOff, w * 0.35, h * 0.2);

    // Shoes
    ctx.fillStyle = brown;
    ctx.fillRect(x + w * 0.05, y + h * 0.86 + legOff, w * 0.4, h * 0.14);
    ctx.fillRect(x + w * 0.5, y + h * 0.86 - legOff, w * 0.45, h * 0.14);

    ctx.restore();
  }

  _drawDead(ctx, x, y, w, h) {
    // Flat dead Mario sprite
    ctx.fillStyle = C.COLOR.MARIO_RED;
    ctx.fillRect(x + w * 0.1, y, w * 0.8, h * 0.3);
    ctx.fillStyle = C.COLOR.MARIO_SKIN;
    ctx.fillRect(x + w * 0.15, y + h * 0.3, w * 0.7, h * 0.25);
    ctx.fillStyle = C.COLOR.MARIO_BLUE;
    ctx.fillRect(x + w * 0.1, y + h * 0.55, w * 0.8, h * 0.45);
    // X eyes
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.25, y + h * 0.35, w * 0.15, h * 0.08);
    ctx.fillRect(x + w * 0.6, y + h * 0.35, w * 0.15, h * 0.08);
  }
}