/**
 * ui.js
 * HUD (Heads-Up Display) rendering.
 * Draws score, coins, lives, world name, and timer.
 */

class UI {
  constructor() {
    this._coinAnimTimer = 0;
    this._coinAnimFrame = 0;
  }

  /**
   * Draw the complete HUD overlay.
   *
   * @param {CanvasRenderingContext2D} ctx
   * @param {Game} game
   * @param {number} dt
   */
  draw(ctx, game, dt) {
    this._coinAnimTimer += dt;
    if (this._coinAnimTimer > 0.12) {
      this._coinAnimTimer = 0;
      this._coinAnimFrame = (this._coinAnimFrame + 1) % 4;
    }

    const W = C.CANVAS_WIDTH;
    const s = C.SCALE;

    // HUD background bar
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillRect(0, 0, W, 20 * s / 3);

    const fontSize = Math.round(7 * s / 3);
    ctx.font = `bold ${fontSize}px monospace`;
    ctx.fillStyle = '#fff';

    // MARIO label + score
    const scoreStr = String(game.score).padStart(6, '0');
    this._drawLabel(ctx, 'MARIO', scoreStr, 16 * s / 3, 14 * s / 3);

    // Coin counter
    this._drawCoinIcon(ctx, 120 * s / 3, 10 * s / 3, s);
    ctx.fillStyle = '#fff';
    ctx.font = `bold ${fontSize}px monospace`;
    ctx.fillText(`×${String(game.coins).padStart(2, '0')}`, 130 * s / 3, 14 * s / 3);

    // World name
    this._drawLabel(ctx, 'WORLD', game.worldName, 180 * s / 3, 14 * s / 3);

    // Timer
    const timeStr = String(Math.ceil(game.timeRemaining)).padStart(3, '0');
    this._drawLabel(ctx, 'TIME', timeStr, 240 * s / 3, 14 * s / 3);

    // Lives (bottom-left)
    ctx.fillStyle = '#fff';
    ctx.font = `bold ${fontSize}px monospace`;
    ctx.fillText(`♥ ×${game.lives}`, 16 * s / 3, (C.CANVAS_HEIGHT - 8) * s / C.CANVAS_HEIGHT * C.CANVAS_HEIGHT / s);

    // Power state indicator (bottom-right)
    this._drawPowerState(ctx, game, s);
  }

  _drawLabel(ctx, label, value, x, y) {
    const s = C.SCALE;
    const fontSize = Math.round(7 * s / 3);
    ctx.font = `bold ${fontSize}px monospace`;
    ctx.fillStyle = '#fff';
    ctx.fillText(label, x, y - fontSize * 0.8);
    ctx.fillText(value, x, y);
  }

  _drawCoinIcon(ctx, x, y, scale) {
    const frames = [1, 0.6, 0.2, 0.6];
    const scaleX = frames[this._coinAnimFrame];
    const w = 6 * scale / 3;
    const h = 8 * scale / 3;
    ctx.fillStyle = C.COLOR.COIN_YELLOW;
    ctx.fillRect(x - (w * scaleX) / 2, y - h / 2, w * scaleX, h);
  }

  _drawPowerState(ctx, game, scale) {
    if (!game.player) return;
    const ps = game.player.powerState;
    if (ps === 'small') return;

    const x = C.CANVAS_WIDTH - 80 * scale / 3;
    const y = C.CANVAS_HEIGHT - 20 * scale / 3;
    const fontSize = Math.round(6 * scale / 3);
    ctx.font = `${fontSize}px monospace`;
    ctx.fillStyle = ps === 'fire' ? '#f87800' : '#00c800';
    ctx.fillText(ps === 'fire' ? '🔥 FIRE' : '⭐ SUPER', x, y);
  }
}