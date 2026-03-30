/**
 * enemies.js
 * Enemy entity classes: Goomba, KoopaTroopa, Shell, Fireball
 *
 * Each class extends the base Entity (defined in entities.js) and implements
 * the update() and draw() methods expected by the game loop.
 */

'use strict';

/* ─────────────────────────────────────────────
   Helper – shared enemy AI utilities
   ───────────────────────────────────────────── */

/**
 * Returns true when the tile directly in front of `entity` (in its movement
 * direction) is empty, meaning the enemy should turn around at an edge.
 *
 * @param {object} entity  - any enemy with x, y, width, height, velX
 * @param {object} tileMap - the current TileMap instance
 * @returns {boolean}
 */
function isAtEdge(entity, tileMap) {
  const tileSize = TILE_SIZE;
  // Sample one tile below the leading bottom corner
  const leadingX =
    entity.velX < 0
      ? entity.x                        // moving left → check left edge
      : entity.x + entity.width - 1;   // moving right → check right edge

  const belowY = entity.y + entity.height + 1;

  const col = Math.floor(leadingX / tileSize);
  const row = Math.floor(belowY / tileSize);

  const tile = tileMap.getTile(col, row);
  return !tile || tile === TILES.EMPTY;
}

/**
 * Returns true when the tile directly ahead of `entity` is solid (wall check).
 *
 * @param {object} entity
 * @param {object} tileMap
 * @returns {boolean}
 */
function isWallAhead(entity, tileMap) {
  const tileSize = TILE_SIZE;
  const midY = entity.y + entity.height / 2;

  const leadingX =
    entity.velX < 0
      ? entity.x - 1
      : entity.x + entity.width + 1;

  const col = Math.floor(leadingX / tileSize);
  const row = Math.floor(midY / tileSize);

  const tile = tileMap.getTile(col, row);
  return tile && tile !== TILES.EMPTY && tile !== TILES.COIN;
}

/* ─────────────────────────────────────────────
   Goomba
   ───────────────────────────────────────────── */

class Goomba {
  /**
   * @param {number} x        - spawn x in pixels
   * @param {number} y        - spawn y in pixels
   * @param {object} tileMap  - TileMap reference for collision queries
   */
  constructor(x, y, tileMap) {
    this.type = ENTITY_TYPES.GOOMBA;

    // Position & size
    this.x = x;
    this.y = y;
    this.width  = TILE_SIZE;
    this.height = TILE_SIZE;

    // Physics
    this.velX = ENEMY_SPEED * -1; // start moving left
    this.velY = 0;
    this.onGround = false;

    // State
    this.alive    = true;
    this.stomped  = false;       // flattened but not yet removed
    this.stompTimer = 0;         // frames to show squished sprite before removal
    this.active   = false;       // activated when player is nearby

    // References
    this.tileMap = tileMap;

    // Animation
    this.animFrame   = 0;
    this.animTimer   = 0;
    this.animSpeed   = 12; // frames per sprite swap
  }

  /* ── Activation ─────────────────────────── */

  /** Called by the game loop when the player enters activation range. */
  activate() {
    this.active = true;
  }

  /* ── Stomp (player lands on top) ────────── */

  stomp() {
    if (this.stomped || !this.alive) return;
    this.stomped  = true;
    this.velX     = 0;
    this.velY     = 0;
    this.stompTimer = GOOMBA_STOMP_FRAMES; // e.g. 30
  }

  /* ── Update ─────────────────────────────── */

  update(dt, tileMap, entities) {
    if (!this.alive) return;

    // Count-down squish animation then die
    if (this.stomped) {
      this.stompTimer--;
      if (this.stompTimer <= 0) this.alive = false;
      return;
    }

    if (!this.active) return;

    // ── Gravity ──────────────────────────────
    this.velY += GRAVITY;
    if (this.velY > MAX_FALL_SPEED) this.velY = MAX_FALL_SPEED;

    // ── Horizontal movement ───────────────────
    // Turn at walls
    if (isWallAhead(this, tileMap)) {
      this.velX *= -1;
    }
    // Turn at ledge edges
    if (this.onGround && isAtEdge(this, tileMap)) {
      this.velX *= -1;
    }

    // ── Apply physics (delegated to Physics module) ───
    Physics.moveEntity(this, tileMap);

    // ── Animation ────────────────────────────
    this.animTimer++;
    if (this.animTimer >= this.animSpeed) {
      this.animTimer = 0;
      this.animFrame = (this.animFrame + 1) % 2;
    }
  }

  /* ── Draw ───────────────────────────────── */

  draw(ctx, camera) {
    if (!this.alive) return;

    const sx = Math.floor(this.x - camera.x);
    const sy = Math.floor(this.y - camera.y);

    if (this.stomped) {
      // Draw squished goomba
      this._drawSquished(ctx, sx, sy);
      return;
    }

    // Try sprite sheet first, fall back to coloured rectangle
    if (window.AssetManager && AssetManager.getSprite) {
      const key = `goomba_walk_${this.animFrame}`;
      const sprite = AssetManager.getSprite(key);
      if (sprite) {
        ctx.drawImage(sprite, sx, sy, this.width, this.height);
        return;
      }
    }

    this._drawFallback(ctx, sx, sy);
  }

  /* ── Private draw helpers ────────────────── */

  _drawFallback(ctx, sx, sy) {
    const w = this.width;
    const h = this.height;

    // Body
    ctx.fillStyle = '#8B4513';
    ctx.fillRect(sx, sy + h * 0.3, w, h * 0.7);

    // Head
    ctx.fillStyle = '#A0522D';
    ctx.beginPath();
    ctx.ellipse(sx + w / 2, sy + h * 0.35, w * 0.45, h * 0.35, 0, 0, Math.PI * 2);
    ctx.fill();

    // Eyes
    ctx.fillStyle = '#fff';
    ctx.fillRect(sx + w * 0.15, sy + h * 0.15, w * 0.25, h * 0.2);
    ctx.fillRect(sx + w * 0.60, sy + h * 0.15, w * 0.25, h * 0.2);

    // Pupils (angry – angled inward)
    ctx.fillStyle = '#000';
    ctx.fillRect(sx + w * 0.30, sy + h * 0.18, w * 0.10, h * 0.12);
    ctx.fillRect(sx + w * 0.62, sy + h * 0.18, w * 0.10, h * 0.12);

    // Feet (walk animation)
    ctx.fillStyle = '#5C3317';
    const footOffset = this.animFrame === 0 ? 0 : w * 0.1;
    ctx.fillRect(sx + footOffset,           sy + h * 0.85, w * 0.3, h * 0.15);
    ctx.fillRect(sx + w * 0.55 - footOffset, sy + h * 0.85, w * 0.3, h * 0.15);
  }

  _drawSquished(ctx, sx, sy) {
    const w = this.width;
    const h = this.height * 0.4; // squished height
    const yOff = this.height - h;

    ctx.fillStyle = '#8B4513';
    ctx.fillRect(sx, sy + yOff, w, h);

    // X eyes
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 2;
    const drawX = (ex, ey, size) => {
      ctx.beginPath();
      ctx.moveTo(ex - size, ey - size); ctx.lineTo(ex + size, ey + size);
      ctx.moveTo(ex + size, ey - size); ctx.lineTo(ex - size, ey + size);
      ctx.stroke();
    };
    drawX(sx + w * 0.28, sy + yOff + h * 0.4, 4);
    drawX(sx + w * 0.72, sy + yOff + h * 0.4, 4);
  }

  /* ── Bounding box (for collision) ────────── */

  getBounds() {
    return { x: this.x, y: this.y, width: this.width, height: this.height };
  }
}

/* ─────────────────────────────────────────────
   Shell  (shared between KoopaTroopa states)
   ───────────────────────────────────────────── */

class Shell {
  /**
   * @param {number} x
   * @param {number} y
   * @param {string} color  - 'green' | 'red'
   * @param {object} tileMap
   */
  constructor(x, y, color, tileMap) {
    this.type = ENTITY_TYPES.SHELL;

    this.x = x;
    this.y = y;
    this.width  = TILE_SIZE;
    this.height = TILE_SIZE;

    this.color   = color;
    this.tileMap = tileMap;

    this.velX = 0;
    this.velY = 0;
    this.onGround = false;

    this.alive    = true;
    this.sliding  = false;
    this.slideDir = 1; // +1 right, -1 left

    // Spin animation
    this.spinFrame = 0;
    this.spinTimer = 0;
    this.spinSpeed = 4;

    // Kicked by player – brief invincibility so player isn't immediately hurt
    this.kickCooldown = 0;
  }

  /** Player kicks the shell in direction `dir` (+1 or -1). */
  kick(dir) {
    this.sliding  = true;
    this.slideDir = dir;
    this.velX     = SHELL_SPEED * dir;
    this.kickCooldown = SHELL_KICK_COOLDOWN_FRAMES;
  }

  /** Stop the shell (player stands on it). */
  stop() {
    this.sliding = false;
    this.velX    = 0;
  }

  update(dt, tileMap, entities) {
    if (!this.alive) return;

    if (this.kickCooldown > 0) this.kickCooldown--;

    // Gravity
    this.velY += GRAVITY;
    if (this.velY > MAX_FALL_SPEED) this.velY = MAX_FALL_SPEED;

    if (this.sliding) {
      // Wall bounce
      if (isWallAhead(this, tileMap)) {
        this.velX *= -1;
        this.slideDir *= -1;
      }

      // Kill enemies it touches
      for (const entity of entities) {
        if (!entity.alive) continue;
        if (entity === this) continue;
        if (entity.type === ENTITY_TYPES.PLAYER) continue;
        if (entity.type === ENTITY_TYPES.SHELL) continue;
        if (entity.type === ENTITY_TYPES.FIREBALL) continue;

        if (rectsOverlap(this, entity)) {
          entity.alive = false; // instant kill
          // Optionally spawn score particle here
        }
      }
    }

    Physics.moveEntity(this, tileMap);

    // Spin animation
    if (this.sliding) {
      this.spinTimer++;
      if (this.spinTimer >= this.spinSpeed) {
        this.spinTimer = 0;
        this.spinFrame = (this.spinFrame + 1) % 4;
      }
    }
  }

  draw(ctx, camera) {
    if (!this.alive) return;

    const sx = Math.floor(this.x - camera.x);
    const sy = Math.floor(this.y - camera.y);

    if (window.AssetManager && AssetManager.getSprite) {
      const key = this.sliding
        ? `koopa_shell_spin_${this.spinFrame}`
        : `koopa_shell_${this.color}`;
      const sprite = AssetManager.getSprite(key);
      if (sprite) {
        ctx.drawImage(sprite, sx, sy, this.width, this.height);
        return;
      }
    }

    this._drawFallback(ctx, sx, sy);
  }

  _drawFallback(ctx, sx, sy) {
    const w = this.width;
    const h = this.height;
    const baseColor = this.color === 'red' ? '#c0392b' : '#27ae60';

    // Shell body
    ctx.fillStyle = baseColor;
    ctx.beginPath();
    ctx.ellipse(sx + w / 2, sy + h * 0.55, w * 0.45, h * 0.4, 0, 0, Math.PI * 2);
    ctx.fill();

    // Shell pattern
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1.5;
    // Horizontal stripe
    ctx.beginPath();
    ctx.moveTo(sx + w * 0.1, sy + h * 0.55);
    ctx.lineTo(sx + w * 0.9, sy + h * 0.55);
    ctx.stroke();
    // Vertical stripe
    ctx.beginPath();
    ctx.moveTo(sx + w * 0.5, sy + h * 0.15);
    ctx.lineTo(sx + w * 0.5, sy + h * 0.95);
    ctx.stroke();

    // Spin indicator
    if (this.sliding) {
      ctx.strokeStyle = 'rgba(255,255,255,0.6)';
      ctx.lineWidth = 2;
      const angle = (this.spinFrame / 4) * Math.PI * 2;
      ctx.beginPath();
      ctx.arc(sx + w / 2, sy + h * 0.55, w * 0.3, angle, angle + Math.PI);
      ctx.stroke();
    }
  }

  getBounds() {
    return { x: this.x, y: this.y, width: this.width, height: this.height };
  }
}

/* ─────────────────────────────────────────────
   KoopaTroopa
   ───────────────────────────────────────────── */

class KoopaTroopa {
  /**
   * @param {number} x
   * @param {number} y
   * @param {string} color   - 'green' | 'red'
   * @param {object} tileMap
   */
  constructor(x, y, color, tileMap) {
    this.type = ENTITY_TYPES.KOOPA;

    this.x = x;
    this.y = y;
    this.width  = TILE_SIZE;
    this.height = TILE_SIZE * 1.5; // Koopa is taller than a tile

    this.color   = color || 'green';
    this.tileMap = tileMap;

    this.velX = ENEMY_SPEED * -1;
    this.velY = 0;
    this.onGround = false;

    this.alive    = true;
    this.active   = false;

    // Shell state
    this.inShell  = false;
    this.shell    = null; // Shell instance created on stomp

    // Animation
    this.animFrame = 0;
    this.animTimer = 0;
    this.animSpeed = 10;

    // Red koopas don't walk off edges
    this.staysOnPlatform = (color === 'red');
  }

  activate() {
    this.active = true;
  }

  /* ── Stomp ───────────────────────────────── */

  /**
   * Player stomps the Koopa.
   * First stomp → retreat into shell.
   * If already in shell and shell is stationary → kick it.
   * Returns the Shell instance if one was created/kicked.
   *
   * @param {number} playerCenterX - used to determine kick direction
   * @returns {Shell|null}
   */
  stomp(playerCenterX) {
    if (!this.alive) return null;

    if (!this.inShell) {
      // Retreat into shell
      this.inShell = true;
      this.velX    = 0;
      this.velY    = 0;
      // Adjust y so shell sits on same ground line
      this.y      += this.height - TILE_SIZE;
      this.height  = TILE_SIZE;

      // Create the shell entity (inactive, sitting still)
      this.shell = new Shell(this.x, this.y, this.color, this.tileMap);
      this.shell.sliding = false;
      return null;
    }

    // Already in shell
    if (this.shell && !this.shell.sliding) {
      // Kick it
      const dir = playerCenterX < this.x + this.width / 2 ? 1 : -1;
      this.shell.kick(dir);
      // Detach shell from koopa – koopa is now "gone"
      this.alive = false;
      return this.shell;
    }

    // Shell is already sliding – stop it
    if (this.shell && this.shell.sliding) {
      this.shell.stop();
    }
    return null;
  }

  /* ── Update ─────────────────────────────── */

  update(dt, tileMap, entities) {
    if (!this.alive) return;

    // If in shell, delegate to shell entity
    if (this.inShell && this.shell) {
      this.shell.update(dt, tileMap, entities);
      // Sync position
      this.x = this.shell.x;
      this.y = this.shell.y;
      return;
    }

    if (!this.active) return;

    // Gravity
    this.velY += GRAVITY;
    if (this.velY > MAX_FALL_SPEED) this.velY = MAX_FALL_SPEED;

    // Turn at walls
    if (isWallAhead(this, tileMap)) {
      this.velX *= -1;
    }

    // Red koopas turn at edges; green koopas walk off
    if (this.staysOnPlatform && this.onGround && isAtEdge(this, tileMap)) {
      this.velX *= -1;
    }

    Physics.moveEntity(this, tileMap);

    // Animation
    this.animTimer++;
    if (this.animTimer >= this.animSpeed) {
      this.animTimer = 0;
      this.animFrame = (this.animFrame + 1) % 2;
    }
  }

  /* ── Draw ───────────────────────────────── */

  draw(ctx, camera) {
    if (!this.alive) return;

    // If in shell, draw the shell
    if (this.inShell && this.shell) {
      this.shell.draw(ctx, camera);
      return;
    }

    const sx = Math.floor(this.x - camera.x);
    const sy = Math.floor(this.y - camera.y);

    if (window.AssetManager && AssetManager.getSprite) {
      const dir   = this.velX < 0 ? 'left' : 'right';
      const key   = `koopa_${this.color}_walk_${dir}_${this.animFrame}`;
      const sprite = AssetManager.getSprite(key);
      if (sprite) {
        ctx.drawImage(sprite, sx, sy, this.width, this.height);
        return;
      }
    }

    this._drawFallback(ctx, sx, sy);
  }

  _drawFallback(ctx, sx, sy) {
    const w = this.width;
    const h = this.height;
    const baseColor = this.color === 'red' ? '#c0392b' : '#27ae60';
    const shellColor = this.color === 'red' ? '#e74c3c' : '#2ecc71';

    // Shell / body
    ctx.fillStyle = shellColor;
    ctx.beginPath();
    ctx.ellipse(sx + w / 2, sy + h * 0.6, w * 0.45, h * 0.35, 0, 0, Math.PI * 2);
    ctx.fill();

    // Shell outline
    ctx.strokeStyle = baseColor;
    ctx.lineWidth = 2;
    ctx.stroke();

    // Head
    ctx.fillStyle = '#f1c40f';
    ctx.beginPath();
    ctx.ellipse(sx + w / 2, sy + h * 0.22, w * 0.3, h * 0.2, 0, 0, Math.PI * 2);
    ctx.fill();

    // Eye
    ctx.fillStyle = '#000';
    const eyeX = this.velX < 0 ? sx + w * 0.3 : sx + w * 0.6;
    ctx.beginPath();
    ctx.arc(eyeX, sy + h * 0.18, 3, 0, Math.PI * 2);
    ctx.fill();

    // Legs
    ctx.fillStyle = '#f1c40f';
    const legOff = this.animFrame === 0 ? 0 : 4;
    ctx.fillRect(sx + w * 0.15, sy + h * 0.85 - legOff, w * 0.25, h * 0.15);
    ctx.fillRect(sx + w * 0.60, sy + h * 0.85 + legOff, w * 0.25, h * 0.15);
  }

  getBounds() {
    return { x: this.x, y: this.y, width: this.width, height: this.height };
  }
}

/* ─────────────────────────────────────────────
   Fireball  (player projectile)
   ───────────────────────────────────────────── */

class Fireball {
  /**
   * @param {number} x      - spawn x
   * @param {number} y      - spawn y
   * @param {number} dir    - +1 (right) or -1 (left)
   * @param {object} tileMap
   */
  constructor(x, y, dir, tileMap) {
    this.type = ENTITY_TYPES.FIREBALL;

    this.width  = FIREBALL_SIZE;
    this.height = FIREBALL_SIZE;
    this.x = x - this.width / 2;
    this.y = y - this.height / 2;

    this.dir     = dir;
    this.tileMap = tileMap;

    this.velX = FIREBALL_SPEED * dir;
    this.velY = FIREBALL_INITIAL_VEL_Y; // slight downward arc

    this.alive = true;

    // Animation
    this.animFrame = 0;
    this.animTimer = 0;
    this.animSpeed = 4;

    // Explosion state
    this.exploding    = false;
    this.explodeTimer = 0;
    this.explodeDuration = FIREBALL_EXPLODE_FRAMES;
  }

  /* ── Update ─────────────────────────────── */

  update(dt, tileMap, entities) {
    if (!this.alive) return;

    // Explosion countdown
    if (this.exploding) {
      this.explodeTimer++;
      if (this.explodeTimer >= this.explodeDuration) this.alive = false;
      return;
    }

    // Gravity (bounce physics)
    this.velY += FIREBALL_GRAVITY;
    if (this.velY > FIREBALL_MAX_FALL) this.velY = FIREBALL_MAX_FALL;

    // Store previous position for collision resolution
    const prevX = this.x;
    const prevY = this.y;

    this.x += this.velX;
    this.y += this.velY;

    // ── Tile collision ────────────────────────
    const tileSize = TILE_SIZE;

    // Horizontal wall check → explode
    const checkX = this.dir > 0 ? this.x + this.width : this.x;
    const midRow  = Math.floor((this.y + this.height / 2) / tileSize);
    const wallCol = Math.floor(checkX / tileSize);
    const wallTile = tileMap.getTile(wallCol, midRow);

    if (wallTile && wallTile !== TILES.EMPTY && wallTile !== TILES.COIN) {
      this._explode();
      return;
    }

    // Vertical floor check → bounce
    const botRow  = Math.floor((this.y + this.height) / tileSize);
    const midCol  = Math.floor((this.x + this.width / 2) / tileSize);
    const floorTile = tileMap.getTile(midCol, botRow);

    if (floorTile && floorTile !== TILES.EMPTY && floorTile !== TILES.COIN) {
      // Snap to top of tile and bounce
      this.y    = botRow * tileSize - this.height;
      this.velY = FIREBALL_BOUNCE_VEL_Y; // negative = upward
    }

    // Ceiling check → explode
    const topRow   = Math.floor(this.y / tileSize);
    const ceilTile = tileMap.getTile(midCol, topRow);
    if (ceilTile && ceilTile !== TILES.EMPTY && ceilTile !== TILES.COIN) {
      this._explode();
      return;
    }

    // ── Enemy collision ───────────────────────
    for (const entity of entities) {
      if (!entity.alive) continue;
      if (entity.type === ENTITY_TYPES.PLAYER)   continue;
      if (entity.type === ENTITY_TYPES.FIREBALL) continue;
      if (entity.type === ENTITY_TYPES.COIN)     continue;
      if (entity.type === ENTITY_TYPES.MUSHROOM) continue;

      if (rectsOverlap(this, entity)) {
        entity.alive = false; // kill enemy
        this._explode();
        return;
      }
    }

    // ── Off-screen check ─────────────────────
    const mapWidth = tileMap.cols * TILE_SIZE;
    if (this.x < 0 || this.x > mapWidth) {
      this.alive = false;
    }

    // ── Animation ────────────────────────────
    this.animTimer++;
    if (this.animTimer >= this.animSpeed) {
      this.animTimer = 0;
      this.animFrame = (this.animFrame + 1) % 4;
    }
  }

  _explode() {
    this.exploding    = true;
    this.explodeTimer = 0;
    this.velX = 0;
    this.velY = 0;
  }

  /* ── Draw ───────────────────────────────── */

  draw(ctx, camera) {
    if (!this.alive) return;

    const sx = Math.floor(this.x - camera.x);
    const sy = Math.floor(this.y - camera.y);

    if (this.exploding) {
      this._drawExplosion(ctx, sx, sy);
      return;
    }

    if (window.AssetManager && AssetManager.getSprite) {
      const key = `fireball_${this.animFrame}`;
      const sprite = AssetManager.getSprite(key);
      if (sprite) {
        ctx.drawImage(sprite, sx, sy, this.width, this.height);
        return;
      }
    }

    this._drawFallback(ctx, sx, sy);
  }

  _drawFallback(ctx, sx, sy) {
    const cx = sx + this.width / 2;
    const cy = sy + this.height / 2;
    const r  = this.width / 2;

    // Outer glow
    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    grad.addColorStop(0,   '#fff');
    grad.addColorStop(0.4, '#ff0');
    grad.addColorStop(1,   '#f80');

    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fillStyle = grad;
    ctx.fill();

    // Spin lines
    ctx.strokeStyle = 'rgba(255,100,0,0.7)';
    ctx.lineWidth = 1;
    const angle = (this.animFrame / 4) * Math.PI * 2;
    for (let i = 0; i < 4; i++) {
      const a = angle + (i * Math.PI) / 2;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
      ctx.stroke();
    }
  }

  _drawExplosion(ctx, sx, sy) {
    const progress = this.explodeTimer / this.explodeDuration;
    const r = (this.width / 2) * (1 + progress * 2);
    const cx = sx + this.width / 2;
    const cy = sy + this.height / 2;
    const alpha = 1 - progress;

    const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
    grad.addColorStop(0,   `rgba(255,255,255,${alpha})`);
    grad.addColorStop(0.5, `rgba(255,200,0,${alpha})`);
    grad.addColorStop(1,   `rgba(255,50,0,0)`);

    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fillStyle = grad;
    ctx.fill();
  }

  getBounds() {
    return { x: this.x, y: this.y, width: this.width, height: this.height };
  }
}

/* ─────────────────────────────────────────────
   Utility – AABB overlap test
   ───────────────────────────────────────────── */

/**
 * Returns true if two axis-aligned bounding boxes overlap.
 * @param {object} a - { x, y, width, height }
 * @param {object} b - { x, y, width, height }
 */
function rectsOverlap(a, b) {
  return (
    a.x < b.x + b.width  &&
    a.x + a.width  > b.x &&
    a.y < b.y + b.height &&
    a.y + a.height > b.y
  );
}

/* ─────────────────────────────────────────────
   EnemyManager – factory & update coordinator
   ───────────────────────────────────────────── */

/**
 * Manages all enemy instances in the current level.
 * The game loop calls EnemyManager.update() and EnemyManager.draw() each frame.
 */
class EnemyManager {
  constructor() {
    /** @type {Array<Goomba|KoopaTroopa|Shell|Fireball>} */
    this.enemies = [];
  }

  /** Remove all enemies (level reset / new level). */
  clear() {
    this.enemies = [];
  }

  /**
   * Spawn an enemy from a level-data descriptor.
   * @param {{ type: string, x: number, y: number, color?: string }} descriptor
   * @param {object} tileMap
   */
  spawn(descriptor, tileMap) {
    let enemy;
    switch (descriptor.type) {
      case ENTITY_TYPES.GOOMBA:
        enemy = new Goomba(descriptor.x, descriptor.y, tileMap);
        break;
      case ENTITY_TYPES.KOOPA:
        enemy = new KoopaTroopa(
          descriptor.x,
          descriptor.y,
          descriptor.color || 'green',
          tileMap
        );
        break;
      default:
        console.warn(`EnemyManager: unknown enemy type "${descriptor.type}"`);
        return;
    }
    this.enemies.push(enemy);
  }

  /**
   * Add a fireball (called by the player when shooting).
   * @param {number} x
   * @param {number} y
   * @param {number} dir
   * @param {object} tileMap
   * @returns {Fireball}
   */
  spawnFireball(x, y, dir, tileMap) {
    const fb = new Fireball(x, y, dir, tileMap);
    this.enemies.push(fb);
    return fb;
  }

  /**
   * Activate enemies within `range` pixels of `playerX`.
   * @param {number} playerX
   * @param {number} range
   */
  activateNearPlayer(playerX, range) {
    for (const e of this.enemies) {
      if (!e.active && e.type !== ENTITY_TYPES.FIREBALL) {
        const cx = e.x + e.width / 2;
        if (Math.abs(cx - playerX) < range) {
          e.activate();
        }
      }
    }
  }

  /**
   * Update all enemies.
   * @param {number}  dt
   * @param {object}  tileMap
   * @param {object}  player   - player entity (for interaction checks)
   */
  update(dt, tileMap, player) {
    // Collect all entities for inter-enemy collision (shells killing goombas etc.)
    const allEntities = [...this.enemies];
    if (player) allEntities.push(player);

    for (const e of this.enemies) {
      e.update(dt, tileMap, allEntities);
    }

    // Promote detached shells to top-level enemies
    for (const e of this.enemies) {
      if (e instanceof KoopaTroopa && e.inShell && e.shell && !this.enemies.includes(e.shell)) {
        // Shell was kicked and detached – add it
        if (!e.alive) {
          this.enemies.push(e.shell);
        }
      }
    }

    // Purge dead entities
    this.enemies = this.enemies.filter(e => e.alive);
  }

  /**
   * Draw all enemies.
   * @param {CanvasRenderingContext2D} ctx
   * @param {object} camera - { x, y }
   */
  draw(ctx, camera) {
    for (const e of this.enemies) {
      e.draw(ctx, camera);
    }
  }

  /**
   * Returns all living enemies (excluding fireballs) for player collision.
   * @returns {Array}
   */
  getLivingEnemies() {
    return this.enemies.filter(
      e => e.alive && e.type !== ENTITY_TYPES.FIREBALL
    );
  }

  /**
   * Returns all living fireballs for enemy collision.
   * @returns {Array}
   */
  getFireballs() {
    return this.enemies.filter(
      e => e.alive && e.type === ENTITY_TYPES.FIREBALL
    );
  }
}

// Expose a singleton
const enemyManager = new EnemyManager();