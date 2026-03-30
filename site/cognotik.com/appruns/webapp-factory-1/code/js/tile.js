/**
 * tile.js
 * Tile and Block entity definitions.
 * Handles rendering and interaction logic for all tile types.
 */

class Tile {
  /**
   * @param {number} tileId  - Tile type constant (C.TILE.*)
   * @param {number} col     - Tile column in level grid
   * @param {number} row     - Tile row in level grid
   */
  constructor(tileId, col, row) {
    this.id     = tileId;
    this.col    = col;
    this.row    = row;
    this.x      = col * TILE_SIZE;
    this.y      = row * TILE_SIZE;
    this.width  = TILE_SIZE;
    this.height = TILE_SIZE;
    this.active = true;

    // Block-specific state
    this.animating    = false;  // bump animation
    this.animTimer    = 0;
    this.animOffset   = 0;      // y offset during bump
    this.contents     = null;   // 'coin' | 'mushroom' | 'flower' | 'star' | 'oneup' | null
    this.contentCount = 0;      // how many items remain (for multi-coin blocks)
    this.used         = false;

    // Pipe-specific
    this.isPipeTop = (tileId === C.TILE.PIPE_TL || tileId === C.TILE.PIPE_TR);
  }

  get isSolid() {
    const id = this.id;
    return id === C.TILE.GROUND   ||
           id === C.TILE.BRICK    ||
           id === C.TILE.QUESTION ||
           id === C.TILE.USED_BLOCK ||
           id === C.TILE.PIPE_TL  ||
           id === C.TILE.PIPE_TR  ||
           id === C.TILE.PIPE_BL  ||
           id === C.TILE.PIPE_BR  ||
           id === C.TILE.CASTLE   ||
           id === C.TILE.CEILING  ||
           id === C.TILE.FLAG_BASE;
  }

  get isPassThrough() {
    return this.id === C.TILE.PLATFORM;
  }

  get isDeadly() {
    return this.id === C.TILE.LAVA;
  }

  /** Trigger bump animation (when hit from below) */
  bump() {
    if (this.animating) return;
    this.animating  = true;
    this.animTimer  = 0;
    this.animOffset = 0;
  }

  update(dt) {
    if (!this.animating) return;
    this.animTimer += dt;
    const dur = 0.2;
    const t   = this.animTimer / dur;
    if (t >= 1) {
      this.animating  = false;
      this.animOffset = 0;
      this.animTimer  = 0;
    } else {
      // Sine arc: goes up then comes back
      this.animOffset = -Math.sin(t * Math.PI) * 6;
    }
  }

  draw(ctx, camX, scale) {
    if (!this.active) return;
    const sx = (this.x - camX) * scale;
    const sy = (this.y + this.animOffset) * scale;
    const sw = this.width  * scale;
    const sh = this.height * scale;

    switch (this.id) {
      case C.TILE.GROUND:    this._drawGround(ctx, sx, sy, sw, sh); break;
      case C.TILE.BRICK:     this._drawBrick(ctx, sx, sy, sw, sh);  break;
      case C.TILE.QUESTION:  this._drawQuestion(ctx, sx, sy, sw, sh); break;
      case C.TILE.USED_BLOCK:this._drawUsed(ctx, sx, sy, sw, sh);   break;
      case C.TILE.PIPE_TL:   this._drawPipeTL(ctx, sx, sy, sw, sh); break;
      case C.TILE.PIPE_TR:   this._drawPipeTR(ctx, sx, sy, sw, sh); break;
      case C.TILE.PIPE_BL:   this._drawPipeBL(ctx, sx, sy, sw, sh); break;
      case C.TILE.PIPE_BR:   this._drawPipeBR(ctx, sx, sy, sw, sh); break;
      case C.TILE.PLATFORM:  this._drawPlatform(ctx, sx, sy, sw, sh); break;
      case C.TILE.CASTLE:    this._drawCastle(ctx, sx, sy, sw, sh); break;
      case C.TILE.CEILING:   this._drawCeiling(ctx, sx, sy, sw, sh); break;
      case C.TILE.FLAG_BASE: this._drawFlagBase(ctx, sx, sy, sw, sh); break;
      case C.TILE.FLAGPOLE:  this._drawFlagpole(ctx, sx, sy, sw, sh); break;
      case C.TILE.LAVA:      this._drawLava(ctx, sx, sy, sw, sh); break;
    }
  }

  _drawGround(ctx, x, y, w, h) {
    // Top highlight row
    ctx.fillStyle = C.COLOR.GROUND_TOP;
    ctx.fillRect(x, y, w, h * 0.25);
    // Main body
    ctx.fillStyle = C.COLOR.GROUND;
    ctx.fillRect(x, y + h * 0.25, w, h * 0.75);
    // Grid lines
    ctx.strokeStyle = C.COLOR.BRICK_DARK;
    ctx.lineWidth = 1;
    ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
  }

  _drawBrick(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.BRICK;
    ctx.fillRect(x, y, w, h);
    // Mortar lines
    ctx.fillStyle = C.COLOR.BRICK_DARK;
    ctx.fillRect(x, y + h * 0.45, w, h * 0.1);
    ctx.fillRect(x + w * 0.5, y, w * 0.05, h * 0.45);
    ctx.fillRect(x, y + h * 0.55, w * 0.5, h * 0.45);
    ctx.fillRect(x + w * 0.5, y + h * 0.55, w * 0.05, h * 0.45);
    // Highlight
    ctx.fillStyle = 'rgba(255,255,255,0.15)';
    ctx.fillRect(x, y, w, h * 0.12);
  }

  _drawQuestion(ctx, x, y, w, h) {
    const gold = this.used ? '#888' : C.COLOR.QUESTION_GOLD;
    const dark = this.used ? '#555' : C.COLOR.QUESTION_DARK;
    ctx.fillStyle = gold;
    ctx.fillRect(x, y, w, h);
    // Border
    ctx.fillStyle = dark;
    ctx.fillRect(x, y, w, h * 0.1);
    ctx.fillRect(x, y + h * 0.9, w, h * 0.1);
    ctx.fillRect(x, y, w * 0.1, h);
    ctx.fillRect(x + w * 0.9, y, w * 0.1, h);
    // Question mark
    ctx.fillStyle = this.used ? '#333' : '#fff';
    const qx = x + w * 0.3;
    const qy = y + h * 0.2;
    const qw = w * 0.4;
    const qh = h * 0.55;
    ctx.fillRect(qx, qy, qw, qh * 0.35);
    ctx.fillRect(qx + qw * 0.6, qy, qw * 0.4, qh * 0.55);
    ctx.fillRect(qx + qw * 0.3, qy + qh * 0.35, qw * 0.4, qh * 0.2);
    ctx.fillRect(qx + qw * 0.3, qy + qh * 0.75, qw * 0.4, qh * 0.25);
  }

  _drawUsed(ctx, x, y, w, h) {
    ctx.fillStyle = '#888';
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = '#555';
    ctx.fillRect(x, y, w, h * 0.1);
    ctx.fillRect(x, y + h * 0.9, w, h * 0.1);
    ctx.fillRect(x, y, w * 0.1, h);
    ctx.fillRect(x + w * 0.9, y, w * 0.1, h);
  }

  _drawPipeTL(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = C.COLOR.PIPE_DARK;
    ctx.fillRect(x, y, w * 0.15, h);
    ctx.fillRect(x + w * 0.85, y, w * 0.15, h);
    ctx.fillStyle = 'rgba(255,255,255,0.2)';
    ctx.fillRect(x + w * 0.2, y + h * 0.1, w * 0.2, h * 0.8);
  }

  _drawPipeTR(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = C.COLOR.PIPE_DARK;
    ctx.fillRect(x, y, w * 0.15, h);
    ctx.fillRect(x + w * 0.85, y, w * 0.15, h);
    // Pipe lip on top-right
    ctx.fillStyle = C.COLOR.PIPE_DARK;
    ctx.fillRect(x, y + h * 0.85, w, h * 0.15);
  }

  _drawPipeBL(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = C.COLOR.PIPE_DARK;
    ctx.fillRect(x, y, w * 0.15, h);
    ctx.fillStyle = 'rgba(255,255,255,0.15)';
    ctx.fillRect(x + w * 0.2, y + h * 0.1, w * 0.15, h * 0.8);
  }

  _drawPipeBR(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.PIPE_GREEN;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = C.COLOR.PIPE_DARK;
    ctx.fillRect(x + w * 0.85, y, w * 0.15, h);
  }

  _drawPlatform(ctx, x, y, w, h) {
    ctx.fillStyle = '#c8a000';
    ctx.fillRect(x, y, w, h * 0.4);
    ctx.fillStyle = '#a07800';
    ctx.fillRect(x, y + h * 0.4, w, h * 0.6);
  }

  _drawCastle(ctx, x, y, w, h) {
    ctx.fillStyle = C.COLOR.CASTLE_GRAY;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = '#888';
    ctx.fillRect(x, y, w * 0.1, h);
    ctx.fillRect(x + w * 0.9, y, w * 0.1, h);
    ctx.fillRect(x, y + h * 0.9, w, h * 0.1);
  }

  _drawCeiling(ctx, x, y, w, h) {
    ctx.fillStyle = '#5a3a1a';
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = '#3a2010';
    ctx.fillRect(x, y, w, h * 0.2);
  }

  _drawFlagBase(ctx, x, y, w, h) {
    ctx.fillStyle = '#888';
    ctx.fillRect(x + w * 0.4, y, w * 0.2, h);
  }

  _drawFlagpole(ctx, x, y, w, h) {
    ctx.fillStyle = '#888';
    ctx.fillRect(x + w * 0.45, y, w * 0.1, h);
    // Flag
    ctx.fillStyle = C.COLOR.FLAG_GREEN;
    ctx.fillRect(x + w * 0.55, y + h * 0.1, w * 0.4, h * 0.35);
  }

  _drawLava(ctx, x, y, w, h) {
    const t = Date.now() / 500;
    ctx.fillStyle = C.COLOR.LAVA_RED;
    ctx.fillRect(x, y, w, h);
    ctx.fillStyle = C.COLOR.LAVA_ORANGE;
    const wave = Math.sin(t + this.col) * 0.15 + 0.15;
    ctx.fillRect(x, y, w, h * wave);
  }
}