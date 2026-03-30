/**
 * enemy.js
 * Enemy entity classes: Goomba, Koopa, Piranha Plant.
 */

class Enemy {
  constructor(type, x, y) {
    this.type   = type;
    this.x      = x;
    this.y      = y;
    this.width  = TILE_SIZE - 2;
    this.height = TILE_SIZE - 2;
    this.vx     = -C.GOOMBA_SPEED;
    this.vy     = 0;
    this.active = true;
    this.onGround = false;
    this.direction = -1; // -1 = left, 1 = right

    // State
    this.state = 'walk'; // walk | squished | shell | shell_moving | dead
    this.stateTimer = 0;
    this.animFrame  = 0;
    this.animTimer  = 0;

    // Koopa-specific
    if (type === C.ENTITY.KOOPA) {
      this.height = TILE_SIZE * 1.5 - 2;
      this.y -= TILE_SIZE * 0.5;
      this.vx = -C.KOOPA_SPEED;
    }
  }

  update(dt, tiles, levelWidth, entities) {
    if (!this.active) return;

    this.animTimer += dt;
    if (this.animTimer > 0.25) {
      this.animTimer = 0;
      this.animFrame = (this.animFrame + 1) % 2;
    }

    if (this.state === 'squished') {
      this.stateTimer += dt;
      if (this.stateTimer > 0.5) this.active = false;
      return;
    }

    if (this.state === 'dead') {
      this.vy += C.GRAVITY * dt;
      this.y  += this.vy * dt;
      this.x  += this.vx * dt;
      if (this.y > 1000) this.active = false;
      return;
    }

    // Shell sliding
    if (this.state === 'shell_moving') {
      this.stateTimer += dt;
      this.vx = this.direction * C.SHELL_SPEED;
    }

    // Shell idle (waiting to be kicked)
    if (this.state === 'shell') {
      this.stateTimer += dt;
      if (this.stateTimer > 5) {
        // Wake up
        this.state = 'walk';
        this.vx = -C.KOOPA_SPEED;
      }
      this.vx = 0;
    }

    // Apply gravity
    this.vy += C.GRAVITY * dt;
    if (this.vy > C.TERMINAL_VELOCITY) this.vy = C.TERMINAL_VELOCITY;

    // Move X
    this.x += this.vx * dt;
    this._resolveX(tiles, levelWidth);

    // Move Y
    this.y += this.vy * dt;
    this.onGround = false;
    this._resolveY(tiles);

    // Turn around at edges (only walking enemies)
    if (this.state === 'walk' && this.onGround) {
      this._checkEdge(tiles);
    }
  }

  _resolveX(tiles, levelWidth) {
    if (this.x < 0) { this.x = 0; this.vx *= -1; this.direction *= -1; return; }
    if (this.x + this.width > levelWidth) {
      this.x = levelWidth - this.width;
      this.vx *= -1; this.direction *= -1;
      return;
    }

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
            this.vx *= -1; this.direction = -1;
          } else {
            this.x = tile.x + tile.width;
            this.vx *= -1; this.direction = 1;
          }
          return;
        }
      }
    }
  }

  _resolveY(tiles) {
    const left  = Math.floor((this.x + 1) / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 2) / TILE_SIZE);
    const top   = Math.floor(this.y / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 1) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (tile && tile.isSolid) {
          if (this.vy >= 0) {
            this.y = tile.y - this.height;
            this.vy = 0;
            this.onGround = true;
          } else {
            this.y = tile.y + tile.height;
            this.vy = 0;
          }
          return;
        }
      }
    }
  }

  _checkEdge(tiles) {
    // Look one tile ahead and one tile below — if no ground, turn around
    const aheadX = this.vx > 0
      ? this.x + this.width + 2
      : this.x - 2;
    const belowY = this.y + this.height + 2;
    const col = Math.floor(aheadX / TILE_SIZE);
    const row = Math.floor(belowY / TILE_SIZE);
    const tile = tiles[row] && tiles[row][col];
    if (!tile || !tile.isSolid) {
      this.vx *= -1;
      this.direction *= -1;
    }
  }

  /** Called when Mario stomps this enemy */
  stomp() {
    if (this.type === C.ENTITY.GOOMBA) {
      this.state = 'squished';
      this.stateTimer = 0;
      this.vx = 0;
      this.vy = 0;
      this.height = TILE_SIZE * 0.4;
      this.y += TILE_SIZE * 0.6;
    } else if (this.type === C.ENTITY.KOOPA) {
      if (this.state === 'walk') {
        this.state = 'shell';
        this.stateTimer = 0;
        this.vx = 0;
        this.height = TILE_SIZE - 2;
        this.y += TILE_SIZE * 0.5;
      } else if (this.state === 'shell') {
        // Kick the shell
        this.state = 'shell_moving';
        this.stateTimer = 0;
        this.direction = 1;
        this.vx = C.SHELL_SPEED;
      } else if (this.state === 'shell_moving') {
        // Stop the shell
        this.state = 'shell';
        this.stateTimer = 0;
        this.vx = 0;
      }
    }
  }

  /** Called when hit by fireball or shell */
  kill() {
    this.state = 'dead';
    this.vy = -300;
    this.vx = this.direction * 60;
    this.active = true; // stays active for death animation
  }

  draw(ctx, camX, scale) {
    if (!this.active) return;
    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;

    if (this.type === C.ENTITY.GOOMBA) this._drawGoomba(ctx, sx, sy, sw, sh);
    else if (this.type === C.ENTITY.KOOPA) this._drawKoopa(ctx, sx, sy, sw, sh);
  }

  _drawGoomba(ctx, x, y, w, h) {
    if (this.state === 'squished') {
      // Flat squished goomba
      ctx.fillStyle = C.COLOR.GOOMBA_BROWN;
      ctx.fillRect(x, y + h * 0.6, w, h * 0.4);
      ctx.fillStyle = '#000';
      ctx.fillRect(x + w * 0.1, y + h * 0.65, w * 0.25, h * 0.2);
      ctx.fillRect(x + w * 0.65, y + h * 0.65, w * 0.25, h * 0.2);
      return;
    }

    // Body
    ctx.fillStyle = C.COLOR.GOOMBA_BROWN;
    ctx.fillRect(x + w * 0.05, y + h * 0.3, w * 0.9, h * 0.7);

    // Head
    ctx.fillStyle = C.COLOR.GOOMBA_BROWN;
    ctx.beginPath();
    ctx.arc(x + w / 2, y + h * 0.35, w * 0.45, 0, Math.PI * 2);
    ctx.fill();

    // Eyes
    ctx.fillStyle = '#fff';
    ctx.fillRect(x + w * 0.15, y + h * 0.2, w * 0.28, h * 0.22);
    ctx.fillRect(x + w * 0.57, y + h * 0.2, w * 0.28, h * 0.22);
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.22, y + h * 0.24, w * 0.14, h * 0.14);
    ctx.fillRect(x + w * 0.64, y + h * 0.24, w * 0.14, h * 0.14);

    // Eyebrows (angry)
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.12, y + h * 0.15, w * 0.3, h * 0.06);
    ctx.fillRect(x + w * 0.58, y + h * 0.15, w * 0.3, h * 0.06);

    // Feet (animated)
    const footOffset = this.animFrame === 0 ? 0 : h * 0.08;
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.05, y + h * 0.85 + footOffset, w * 0.35, h * 0.15);
    ctx.fillRect(x + w * 0.6,  y + h * 0.85 - footOffset, w * 0.35, h * 0.15);
  }

  _drawKoopa(ctx, x, y, w, h) {
    const isShell = this.state === 'shell' || this.state === 'shell_moving';

    if (isShell) {
      // Shell
      ctx.fillStyle = C.COLOR.KOOPA_GREEN;
      ctx.fillRect(x + w * 0.1, y + h * 0.1, w * 0.8, h * 0.8);
      ctx.fillStyle = C.COLOR.PIPE_DARK;
      ctx.fillRect(x + w * 0.1, y + h * 0.1, w * 0.8, h * 0.15);
      ctx.fillRect(x + w * 0.1, y + h * 0.75, w * 0.8, h * 0.15);
      ctx.fillRect(x + w * 0.1, y + h * 0.1, w * 0.15, h * 0.8);
      ctx.fillRect(x + w * 0.75, y + h * 0.1, w * 0.15, h * 0.8);
      // Hex pattern
      ctx.fillStyle = 'rgba(0,0,0,0.2)';
      ctx.fillRect(x + w * 0.35, y + h * 0.3, w * 0.3, h * 0.4);
      return;
    }

    // Shell (back)
    ctx.fillStyle = C.COLOR.KOOPA_GREEN;
    ctx.fillRect(x + w * 0.15, y + h * 0.15, w * 0.7, h * 0.65);

    // Head
    ctx.fillStyle = '#78c800';
    ctx.beginPath();
    ctx.arc(x + w / 2, y + h * 0.18, w * 0.35, 0, Math.PI * 2);
    ctx.fill();

    // Eyes
    ctx.fillStyle = '#fff';
    ctx.fillRect(x + w * 0.55, y + h * 0.08, w * 0.2, h * 0.14);
    ctx.fillStyle = '#000';
    ctx.fillRect(x + w * 0.6, y + h * 0.1, w * 0.1, h * 0.1);

    // Feet
    const footOffset = this.animFrame === 0 ? 0 : h * 0.06;
    ctx.fillStyle = '#f8a800';
    ctx.fillRect(x + w * 0.1, y + h * 0.78 + footOffset, w * 0.3, h * 0.22);
    ctx.fillRect(x + w * 0.6, y + h * 0.78 - footOffset, w * 0.3, h * 0.22);
  }
}

// ── Piranha Plant ─────────────────────────────────────────────────────────────

class PiranhaPlant {
  constructor(x, y) {
    this.type   = C.ENTITY.PIRANHA;
    this.x      = x;
    this.y      = y;
    this.width  = TILE_SIZE - 4;
    this.height = TILE_SIZE * 1.5;
    this.active = true;
    this.state  = 'hidden'; // hidden | emerging | visible | retracting
    this.timer  = 1.5;      // start hidden for a bit
    this.baseY  = y;
    this.targetY = y - TILE_SIZE * 1.5;
    this.speed  = TILE_SIZE * 2; // px/s
  }

  update(dt) {
    if (!this.active) return;
    this.timer -= dt;

    if (this.state === 'hidden' && this.timer <= 0) {
      this.state = 'emerging';
      this.timer = 0;
    } else if (this.state === 'emerging') {
      this.y -= this.speed * dt;
      if (this.y <= this.targetY) {
        this.y = this.targetY;
        this.state = 'visible';
        this.timer = C.PIRANHA_CYCLE * 0.5;
      }
    } else if (this.state === 'visible' && this.timer <= 0) {
      this.state = 'retracting';
    } else if (this.state === 'retracting') {
      this.y += this.speed * dt;
      if (this.y >= this.baseY) {
        this.y = this.baseY;
        this.state = 'hidden';
        this.timer = C.PIRANHA_CYCLE * 0.5;
      }
    }
  }

  draw(ctx, camX, scale) {
    if (!this.active || this.state === 'hidden') return;
    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;

    // Stem
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(sx + sw * 0.3, sy + sh * 0.4, sw * 0.4, sh * 0.6);

    // Head
    ctx.fillStyle = '#e40000';
    ctx.beginPath();
    ctx.arc(sx + sw / 2, sy + sh * 0.3, sw * 0.5, 0, Math.PI * 2);
    ctx.fill();

    // Mouth
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(sx + sw / 2, sy + sh * 0.35, sw * 0.35, 0, Math.PI);
    ctx.fill();

    // Teeth
    ctx.fillStyle = '#fff';
    for (let i = 0; i < 3; i++) {
      ctx.fillRect(sx + sw * (0.15 + i * 0.25), sy + sh * 0.2, sw * 0.12, sh * 0.12);
    }

    // Eyes
    ctx.fillStyle = '#fff';
    ctx.fillRect(sx + sw * 0.15, sy + sh * 0.05, sw * 0.2, sh * 0.15);
    ctx.fillRect(sx + sw * 0.65, sy + sh * 0.05, sw * 0.2, sh * 0.15);
    ctx.fillStyle = '#000';
    ctx.fillRect(sx + sw * 0.2, sy + sh * 0.08, sw * 0.1, sh * 0.09);
    ctx.fillRect(sx + sw * 0.7, sy + sh * 0.08, sw * 0.1, sh * 0.09);
  }
}