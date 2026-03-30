/**
 * renderer.js
 * Main rendering pipeline. Orchestrates draw order (back to front).
 * Stateless — receives all data it needs each frame.
 */

class Renderer {
  /**
   * @param {HTMLCanvasElement} canvas
   */
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx    = canvas.getContext('2d');
    this.ctx.imageSmoothingEnabled = false;
  }

  /**
   * Clear the canvas.
   */
  clear() {
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
  }

  /**
   * Main draw call — renders a complete frame.
   *
   * Draw order (back → front):
   *   1. Background (sky / underground)
   *   2. Decorative background elements (clouds, hills)
   *   3. Tiles (ground, pipes, blocks)
   *   4. Items (coins, mushrooms, etc.)
   *   5. Particles
   *   6. Enemies
   *   7. Player
   *   8. HUD (drawn by UI class separately)
   *
   * @param {Game}  game
   * @param {Level} level
   * @param {Player} player
   */
  drawGame(game, level, player) {
    const ctx   = this.ctx;
    const scale = C.SCALE;
    const camX  = level.cameraX;

    // 1-5: Level draws its own background, tiles, items, particles
    level.draw(ctx, scale);

    // 6: Enemies
    this._drawEnemies(ctx, level, camX, scale);

    // 7: Player
    player.draw(ctx, camX, scale);

    // Score popups
    this._drawScorePopups(ctx, game, camX, scale);
  }

  _drawEnemies(ctx, level, camX, scale) {
    // Cull to viewport
    const viewWidth = C.CANVAS_WIDTH / scale;
    for (const enemy of level.enemies) {
      if (!enemy.active) continue;
      // Viewport cull
      if (enemy.x + enemy.width  < camX - TILE_SIZE) continue;
      if (enemy.x                > camX + viewWidth + TILE_SIZE) continue;
      enemy.draw(ctx, camX, scale);
    }

    // Piranha plants
    if (level.piranhas) {
      for (const p of level.piranhas) {
        if (!p.active) continue;
        if (p.x + p.width  < camX - TILE_SIZE) continue;
        if (p.x             > camX + viewWidth + TILE_SIZE) continue;
        p.draw(ctx, camX, scale);
      }
    }
  }

  _drawScorePopups(ctx, game, camX, scale) {
    if (!game.scorePopups) return;
    ctx.font = `bold ${8 * scale}px monospace`;
    ctx.textAlign = 'center';
    for (const popup of game.scorePopups) {
      if (!popup.active) continue;
      const alpha = 1 - popup.age / popup.lifetime;
      ctx.globalAlpha = alpha;
      ctx.fillStyle = '#fff';
      const sx = (popup.x - camX) * scale;
      const sy = popup.y * scale;
      ctx.fillText(popup.text, sx, sy);
    }
    ctx.globalAlpha = 1;
    ctx.textAlign = 'left';
  }

  /**
   * Draw the title screen.
   */
  drawTitle(ctx) {
    const W = C.CANVAS_WIDTH;
    const H = C.CANVAS_HEIGHT;

    // Background
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, H);

    // Title
    ctx.fillStyle = '#e40000';
    ctx.font = `bold ${20 * C.SCALE / 3}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText('SUPER MARIO BROS', W / 2, H * 0.28);

    ctx.fillStyle = '#fff';
    ctx.font = `${8 * C.SCALE / 3}px monospace`;
    ctx.fillText('© 1985 NINTENDO', W / 2, H * 0.38);

    // Blinking start text
    if (Math.floor(Date.now() / 500) % 2 === 0) {
      ctx.fillStyle = '#fff';
      ctx.font = `bold ${9 * C.SCALE / 3}px monospace`;
      ctx.fillText('PRESS ANY KEY TO START', W / 2, H * 0.58);
    }

    // Controls
    ctx.fillStyle = '#aaa';
    ctx.font = `${6 * C.SCALE / 3}px monospace`;
    ctx.fillText('ARROWS / WASD: MOVE    SPACE: JUMP', W / 2, H * 0.72);
    ctx.fillText('SHIFT: RUN    Z/X: FIRE    P: PAUSE', W / 2, H * 0.80);

    ctx.textAlign = 'left';
  }

  /**
   * Draw the game over screen.
   */
  drawGameOver(ctx) {
    const W = C.CANVAS_WIDTH;
    const H = C.CANVAS_HEIGHT;

    ctx.fillStyle = 'rgba(0,0,0,0.75)';
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = '#e40000';
    ctx.font = `bold ${18 * C.SCALE / 3}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText('GAME OVER', W / 2, H * 0.45);

    if (Math.floor(Date.now() / 600) % 2 === 0) {
      ctx.fillStyle = '#fff';
      ctx.font = `${8 * C.SCALE / 3}px monospace`;
      ctx.fillText('PRESS ANY KEY', W / 2, H * 0.62);
    }
    ctx.textAlign = 'left';
  }

  /**
   * Draw the level complete screen.
   */
  drawLevelComplete(ctx) {
    const W = C.CANVAS_WIDTH;
    const H = C.CANVAS_HEIGHT;

    ctx.fillStyle = 'rgba(0,0,0,0.5)';
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = '#f8f800';
    ctx.font = `bold ${14 * C.SCALE / 3}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText('COURSE CLEAR!', W / 2, H * 0.4);

    ctx.fillStyle = '#fff';
    ctx.font = `${8 * C.SCALE / 3}px monospace`;
    ctx.fillText('YOU GOT A CARD!', W / 2, H * 0.55);
    ctx.textAlign = 'left';
  }

  /**
   * Draw the win screen.
   */
  drawWin(ctx) {
    const W = C.CANVAS_WIDTH;
    const H = C.CANVAS_HEIGHT;

    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = '#f8f800';
    ctx.font = `bold ${16 * C.SCALE / 3}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText('CONGRATULATIONS!', W / 2, H * 0.35);

    ctx.fillStyle = '#fff';
    ctx.font = `${8 * C.SCALE / 3}px monospace`;
    ctx.fillText('YOU SAVED THE PRINCESS!', W / 2, H * 0.50);

    if (Math.floor(Date.now() / 600) % 2 === 0) {
      ctx.font = `${7 * C.SCALE / 3}px monospace`;
      ctx.fillText('PRESS ANY KEY TO PLAY AGAIN', W / 2, H * 0.68);
    }
    ctx.textAlign = 'left';
  }

  /**
   * Draw the pause overlay.
   */
  drawPause(ctx) {
    const W = C.CANVAS_WIDTH;
    const H = C.CANVAS_HEIGHT;

    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillRect(0, 0, W, H);

    ctx.fillStyle = '#fff';
    ctx.font = `bold ${16 * C.SCALE / 3}px monospace`;
    ctx.textAlign = 'center';
    ctx.fillText('PAUSED', W / 2, H / 2);

    ctx.font = `${7 * C.SCALE / 3}px monospace`;
    ctx.fillText('PRESS P TO RESUME', W / 2, H * 0.62);
    ctx.textAlign = 'left';
  }
}