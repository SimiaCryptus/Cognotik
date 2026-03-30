/**
 * item.js
 * Collectible items and power-ups: coins, mushrooms, flowers, stars, 1-ups.
 */

class Item {
  constructor(type, x, y) {
    this.type   = type;
    this.x      = x;
    this.y      = y;
    this.width  = TILE_SIZE - 2;
    this.height = TILE_SIZE - 2;
    this.vx     = 0;
    this.vy     = 0;
    this.active = true;
    this.collected = false;

    // Spawn animation (items emerge from blocks)
    this.emerging    = true;
    this.emergeY     = y;          // target Y
    this.emergeStart = y + TILE_SIZE; // start Y (inside block)
    this.y           = y + TILE_SIZE;
    this.emergeSpeed = 60;         // px/s

    // Coin-specific
    this.coinAnimFrame = 0;
    this.coinAnimTimer = 0;

    // Floating coin (from question block) - arc animation
    this.isFloatingCoin = (type === C.ENTITY.COIN);
    if (this.isFloatingCoin) {
      this.vy = -300;
      this.emerging = false;
      this.y = y;
    }

    // Moving items (mushroom, star)
    if (type === C.ENTITY.MUSHROOM || type === C.ENTITY.ONEUP) {
      this.vx = 60;
    }
    if (type === C.ENTITY.STAR) {
      this.vx = 80;
      this.vy = -200;
    }

    this.onGround = false;
    this.lifetime = 0;
  }

  update(dt, tiles, levelWidth) {
    if (!this.active) return;
    this.lifetime += dt;

    // Emerge animation
    if (this.emerging) {
      this.y -= this.emergeSpeed * dt;
      if (this.y <= this.emergeY) {
        this.y = this.emergeY;
        this.emerging = false;
      }
      return;
    }

    // Floating coin arc
    if (this.isFloatingCoin) {
      this.vy += C.GRAVITY * dt;
      this.y  += this.vy * dt;
      if (this.y > this.emergeStart + TILE_SIZE) {
        this.active = false;
      }
      return;
    }

    // Physics for moving items
    if (this.type === C.ENTITY.MUSHROOM || this.type === C.ENTITY.ONEUP ||
        this.type === C.ENTITY.STAR     || this.type === C.ENTITY.FIRE_FLOWER) {

      this.vy += C.GRAVITY * dt;
      if (this.vy > C.TERMINAL_VELOCITY) this.vy = C.TERMINAL_VELOCITY;

      // Move X
      this.x += this.vx * dt;
      this._resolveX(tiles, levelWidth);

      // Move Y
      this.y += this.vy * dt;
      this.onGround = false;
      this._resolveY(tiles);

      // Star bounces
      if (this.type === C.ENTITY.STAR && this.onGround) {
        this.vy = -250;
      }
    }

    // Coin spin animation
    if (this.type === C.ENTITY.COIN) {
      this.coinAnimTimer += dt;
      if (this.coinAnimTimer > 0.1) {
        this.coinAnimTimer = 0;
        this.coinAnimFrame = (this.coinAnimFrame + 1) % 4;
      }
    }
  }

  _resolveX(tiles, levelWidth) {
    if (this.x < 0) { this.x = 0; this.vx *= -1; return; }
    if (this.x + this.width > levelWidth) { this.x = levelWidth - this.width; this.vx *= -1; return; }

    const left  = Math.floor(this.x / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 1) / TILE_SIZE);
    const top   = Math.floor(this.y / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 1) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (tile && tile.isSolid) {
          if (this.vx > 0) { this.x = tile.x - this.width; this.vx *= -1; }
          else              { this.x = tile.x + tile.width; this.vx *= -1; }
          return;
        }
      }
    }
  }

  _resolveY(tiles) {
    const left  = Math.floor(this.x / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 1) / TILE_SIZE);
    const top   = Math.floor(this.y / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 1) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (tile && tile.isSolid) {
          if (this.vy > 0) {
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

  draw(ctx, camX, scale) {
    if (!this.active) return;
    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;

    switch (this.type) {
      case C.ENTITY.COIN:        this._drawCoin(ctx, sx, sy, sw, sh);       break;
      case C.ENTITY.MUSHROOM:    this._drawMushroom(ctx, sx, sy, sw, sh, '#e40000'); break;
      case C.ENTITY.ONEUP:       this._drawMushroom(ctx, sx, sy, sw, sh, '#00a800'); break;
      case C.ENTITY.FIRE_FLOWER: this._drawFireFlower(ctx, sx, sy, sw, sh); break;
      case C.ENTITY.STAR:        this._drawStar(ctx, sx, sy, sw, sh);       break;
    }
  }

  _drawCoin(ctx, x, y, w, h) {
    // Spinning coin: width varies by frame
    const frames = [1, 0.6, 0.2, 0.6];
    const scaleX = frames[this.coinAnimFrame];
    const cx = x + w / 2;
    ctx.fillStyle = C.COLOR.COIN_YELLOW;
    ctx.fillRect(cx - (w * scaleX) / 2, y, w * scaleX, h);
    ctx.fillStyle = '#ffd700';
    ctx.fillRect(cx - (w * scaleX) / 2 + 1, y + 1, (w * scaleX) * 0.4, h * 0.3);
  }

  _drawMushroom(ctx, x, y, w, h, capColor) {
    // Stem
    ctx.fillStyle = C.COLOR.MARIO_SKIN;
    ctx.fillRect(x + w * 0.2, y + h * 0.5, w * 0.6, h * 0.5);
    // Cap
    ctx.fillStyle = capColor;
    ctx.beginPath();
    ctx.arc(x + w / 2, y + h * 0.45, w * 0.52, Math.PI, 0);
    ctx.fill();
    // Spots
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(x + w * 0.3, y + h * 0.3, w * 0.1, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.arc(x + w * 0.65, y + h * 0.25, w * 0.08, 0, Math.PI * 2);
    ctx.fill();
  }

  _drawFireFlower(ctx, x, y, w, h) {
    // Stem
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(x + w * 0.45, y + h * 0.4, w * 0.1, h * 0.6);
    // Petals
    const t = Date.now() / 300;
    const colors = ['#e40000', '#f87800', '#f8f800', '#e40000'];
    for (let i = 0; i < 4; i++) {
      const angle = (i / 4) * Math.PI * 2 + t;
      const px = x + w / 2 + Math.cos(angle) * w * 0.3;
      const py = y + h * 0.3 + Math.sin(angle) * h * 0.25;
      ctx.fillStyle = colors[i];
      ctx.beginPath();
      ctx.arc(px, py, w * 0.18, 0, Math.PI * 2);
      ctx.fill();
    }
    // Center
    ctx.fillStyle = '#f8f800';
    ctx.beginPath();
    ctx.arc(x + w / 2, y + h * 0.3, w * 0.15, 0, Math.PI * 2);
    ctx.fill();
  }

  _drawStar(ctx, x, y, w, h) {
    const t = Date.now() / 200;
    ctx.fillStyle = `hsl(${(t * 60) % 360}, 100%, 60%)`;
    const cx = x + w / 2;
    const cy = y + h / 2;
    const r1 = w * 0.48;
    const r2 = w * 0.22;
    ctx.beginPath();
    for (let i = 0; i < 10; i++) {
      const angle = (i / 10) * Math.PI * 2 - Math.PI / 2;
      const r = i % 2 === 0 ? r1 : r2;
      if (i === 0) ctx.moveTo(cx + Math.cos(angle) * r, cy + Math.sin(angle) * r);
      else         ctx.lineTo(cx + Math.cos(angle) * r, cy + Math.sin(angle) * r);
    }
    ctx.closePath();
    ctx.fill();
  }
}

// ── Particle ──────────────────────────────────────────────────────────────────

class Particle {
  constructor(x, y, vx, vy, color, size, lifetime) {
    this.x = x; this.y = y;
    this.vx = vx; this.vy = vy;
    this.color = color;
    this.size = size;
    this.lifetime = lifetime;
    this.age = 0;
    this.active = true;
  }

  update(dt) {
    this.age += dt;
    if (this.age >= this.lifetime) { this.active = false; return; }
    this.vy += C.GRAVITY * 0.5 * dt;
    this.x  += this.vx * dt;
    this.y  += this.vy * dt;
  }

  draw(ctx, camX, scale) {
    if (!this.active) return;
    const alpha = 1 - this.age / this.lifetime;
    ctx.globalAlpha = alpha;
    ctx.fillStyle = this.color;
    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const ss = this.size * scale;
    ctx.fillRect(sx - ss / 2, sy - ss / 2, ss, ss);
    ctx.globalAlpha = 1;
  }
}

// ── Fireball ──────────────────────────────────────────────────────────────────

class Fireball {
  constructor(x, y, direction) {
    this.x = x; this.y = y;
    this.width  = 8;
    this.height = 8;
    this.vx = direction * C.FIREBALL_SPEED;
    this.vy = -100;
    this.active = true;
    this.onGround = false;
    this.bounces  = 0;
    this.type = C.ENTITY.FIREBALL;
  }

  update(dt, tiles) {
    if (!this.active) return;
    this.vy += C.FIREBALL_GRAVITY * dt;
    if (this.vy > C.TERMINAL_VELOCITY) this.vy = C.TERMINAL_VELOCITY;

    this.x += this.vx * dt;
    this.y += this.vy * dt;

    this._resolveCollision(tiles);

    if (this.bounces > 4) this.active = false;
  }

  _resolveCollision(tiles) {
    const left  = Math.floor(this.x / TILE_SIZE);
    const right = Math.floor((this.x + this.width - 1) / TILE_SIZE);
    const top   = Math.floor(this.y / TILE_SIZE);
    const bot   = Math.floor((this.y + this.height - 1) / TILE_SIZE);

    for (let row = top; row <= bot; row++) {
      for (let col = left; col <= right; col++) {
        const tile = tiles[row] && tiles[row][col];
        if (tile && tile.isSolid) {
          // Check if hitting from above (floor)
          const prevBot = this.y + this.height - this.vy * 0.016;
          if (this.vy > 0 && prevBot <= tile.y + 1) {
            this.y = tile.y - this.height;
            this.vy = -Math.abs(this.vy) * 0.6;
            this.bounces++;
          } else {
            this.active = false;
          }
          return;
        }
      }
    }
  }

  draw(ctx, camX, scale) {
    if (!this.active) return;
    const t  = Date.now() / 100;
    const sx = (this.x - camX) * scale;
    const sy = this.y * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;
    ctx.fillStyle = t % 2 < 1 ? '#f87800' : '#f8f800';
    ctx.beginPath();
    ctx.arc(sx + sw / 2, sy + sh / 2, sw / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(sx + sw * 0.3, sy + sh * 0.3, sw * 0.2, 0, Math.PI * 2);
    ctx.fill();
  }
}