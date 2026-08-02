// Game orchestrates the model, rules, input, scoring and rendering.
//
// For Milestones 0-3 this covers:
//  - static board render
//  - selection & input
//  - rule evaluation (sum equals) with clear-on-success feedback
//
// Gravity/refill (Milestone 4) is not yet wired; cleared tiles are simply
// removed and re-filled in place with fresh values so play can continue.

import { Board } from '../model/Board.js';
import { Tile } from '../model/Tile.js';
import { RuleEngine } from '../rules/RuleEngine.js';
import { sumEquals } from '../rules/rules/sumEquals.js';
import { Selection } from '../input/Selection.js';
import { InputController } from '../input/InputController.js';
import { ScoreManager } from './ScoreManager.js';
import { Renderer } from '../render/Renderer.js';
import { keyOf, coordKey } from '../model/coords.js';

export class Game {
  constructor(canvas, config = {}) {
    console.log('[Game] Initializing with config:', config);
    this.canvas = canvas;

    const {
      cols = 8,
      rows = 9,
      size = 28,
      valueRange = [1, 9],
      rule = sumEquals(10),
      minSelection = 2,
      maxSelection = 4,
      straightLine = false,
      suppressZero = false,
    } = config;

    this.suppressZero = suppressZero;
    this.board = new Board({ cols, rows, size, valueRange, suppressZero });

    // Size the canvas to comfortably fit the configured grid.
    const bounds = this.board.grid.pixelBounds ? this.board.grid.pixelBounds() : null;
    if (bounds) {
      canvas.width = Math.ceil(bounds.width);
      canvas.height = Math.ceil(bounds.height);
    }
    this.board.grid.layout(canvas.width, canvas.height);

    this.rules = new RuleEngine(rule);
    this.selection = new Selection({
      minLength: minSelection,
      maxLength: maxSelection,
      straightLine,
    });
    this.score = new ScoreManager();
    this.renderer = new Renderer(canvas, this.board);

    this.input = new InputController(canvas, this.board, {
      onStart: (coord) => this._onStart(coord),
      onMove: (coord) => this._onMove(coord),
      onEnd: () => this._onEnd(),
    });

    // DOM hooks (optional).
    this.scoreEl = document.getElementById('score');
    this.ruleDescEl = document.getElementById('rule-desc');
    this.indicatorEl = document.getElementById('indicator');
    this.comboEl = document.getElementById('combo');

    if (this.ruleDescEl) this.ruleDescEl.textContent = this.rules.description;

    this._flashUntil = 0;
    this._flashCoords = [];
    // Animation state for gravity/refill.
    this._animating = false;
    this._anim = null; // { start, duration, moves: [{tile, from:{x,y}, to:{x,y}}] }
    this._rafId = 0;
    // Combo tracking + continuous render loop.
    this._combo = 0;
    this._comboUntil = 0;
    this._lastFrame = performance.now();
    this._loop = this._loop.bind(this);
    this._running = true;
    requestAnimationFrame(this._loop);

    this._draw();
  }
  // Continuous render loop keeps particles/glow alive every frame.
  _loop(now) {
    if (!this._running) return;
    const dt = Math.min(48, now - this._lastFrame);
    this._lastFrame = now;
    // Expire stale combos in the HUD.
    if (this._combo > 0 && now >= this._comboUntil) {
      this._combo = 0;
      if (this.comboEl) this.comboEl.textContent = '×1';
    }
    if (!this._animating) {
      this.renderer.render(this.selection.coords, null, dt);
    }
    requestAnimationFrame(this._loop);
  }

  _selectionTiles() {
    return this.selection.coords.map((c) => this.board.getTile(c)).filter(Boolean);
  }

  _onStart(coord) {
    if (this._animating) return;
    console.log('[Game] Selection start at coord:', coord);
    this.selection.clear();
    this.selection.add(coord);
    this._updateIndicator();
    this._draw();
  }

  _onMove(coord) {
    if (this._animating) return;
    // Allow drag-back to remove the last tile.
    if (this.selection.tryBacktrack(coord)) {
      console.log('[Game] Backtracked to coord:', coord);
      this._updateIndicator();
      this._draw();
      return;
    }
    if (this.selection.add(coord)) {
      console.log('[Game] Added coord to selection:', coord, '- length:', this.selection.length);
      this._updateIndicator();
      this._draw();
    }
  }

  _onEnd() {
    if (this._animating) return;
    const tiles = this._selectionTiles();
    const longEnough = this.selection.hasValidLength();
    const result = longEnough ? this.rules.evaluate(tiles) : { valid: false };
    console.log(
      '[Game] Selection end - coords:',
      this.selection.coords,
      'longEnough:',
      longEnough,
      'valid:',
      result.valid
    );

    if (result.valid) {
      this._resolveClear(this.selection.coords.slice());
    } else if (!this.selection.isEmpty()) {
      this._rejectFeedback();
    }

    this.selection.clear();
    this._updateIndicator();
    this._draw();
  }

  _resolveClear(coords) {
    // Combo builds when clears chain quickly.
    const now = performance.now();
    if (now < this._comboUntil) this._combo += 1;
    else this._combo = 1;
    this._comboUntil = now + 2600;
    const multiplier = this._combo;

    const base = this.score.scoreClear(coords.length);
    const gained = base * multiplier;
    this.score.score += gained - base;
    console.log(
      '[Game] Clear resolved - cleared',
      coords.length,
      'tiles, gained',
      gained,
      'points, total score:',
      this.score.score
    );
    if (this.scoreEl) this.scoreEl.textContent = String(this.score.score);
    if (this.scoreEl) {
      this.scoreEl.classList.remove('bump');
      // Force reflow to restart the animation.
      void this.scoreEl.offsetWidth;
      this.scoreEl.classList.add('bump');
    }
    if (this.comboEl) this.comboEl.textContent = `×${multiplier}`;
    this._showIndicator(
      multiplier > 1 ? `+${gained}  ×${multiplier} COMBO!` : `+${gained}`,
      'valid'
    );

    // Juicy feedback: particles, ripples, floating text, screen shake.
    const grid = this.board.grid;
    let cx = 0;
    let cy = 0;
    for (const coord of coords) {
      const p = grid.toPixel(coord);
      cx += p.x;
      cy += p.y;
      const tile = this.board.getTile(coord);
      const pal = this.renderer.paletteFor(tile ? tile.value : 0);
      this.renderer.spawnBurst(p.x, p.y, pal.base, 16, 1 + multiplier * 0.25);
      this.renderer.spawnBurst(p.x, p.y, '#ffffff', 6, 0.8);
      this.renderer.spawnRipple(p.x, p.y, pal.glow);
    }
    cx /= coords.length;
    cy /= coords.length;
    this.renderer.spawnFloater(cx, cy - 6, `+${gained}`, '#ffe066', true);
    if (multiplier > 1) {
      this.renderer.spawnFloater(
        cx,
        cy - this.board.grid.size * 1.4,
        `${multiplier}x COMBO`,
        '#ff6ad5',
        true
      );
    }
    this.renderer.addShake(4 + Math.min(14, coords.length * 2 + multiplier * 2), 260);
    // Milestone 4: remove cleared tiles, let tiles above fall, then refill
    // the vacated top cells with fresh tiles — all animated.
    this._applyGravityAndRefill(coords);
  }
  // Remove cleared coords, collapse each column downward, and spawn new
  // tiles at the top. Builds an animation describing every tile's motion.
  _applyGravityAndRefill(clearedCoords) {
    const grid = this.board.grid;
    // Remove cleared tiles from the model.
    for (const coord of clearedCoords) {
      this.board.tiles.delete(coordKey(coord));
    }
    const moves = []; // { tile, from:{x,y}, to:{x,y} }
    const columns = grid.columns();
    for (const cells of columns.values()) {
      // cells sorted top-to-bottom (ascending row). Collect survivors and
      // drop them into the lowest slots; empties at the top get new tiles.
      const survivors = cells.map((cell) => this.board.getTile(cell)).filter(Boolean);
      const emptyCount = cells.length - survivors.length;

      cells.forEach((cell, i) => {
        const key = coordKey(cell);
        const to = grid.toPixel(cell);

        if (i < emptyCount) {
          // Fresh tile spawned above the column, falling into place.
          const tile = new Tile(this._randomValue(), cell);
          const rise = grid.size * Math.sqrt(3) * (emptyCount - i);
          moves.push({
            tile,
            from: { x: to.x, y: to.y - rise },
            to,
          });
          this.renderer.markPop(tile.id);
          this.board.tiles.set(key, tile);
        } else {
          // Surviving tile shifts down (or stays put).
          const tile = survivors[i - emptyCount];
          const from = grid.toPixel(tile.coord);
          if (from.x !== to.x || from.y !== to.y) {
            moves.push({ tile, from, to });
          }
          tile.coord = { q: cell.q, r: cell.r };
          this.board.tiles.set(key, tile);
        }
      });
    }
    if (moves.length === 0) {
      this._draw();
      return;
    }
    this._startAnimation(moves);
  }
  _startAnimation(moves) {
    this._cancelAnimation();
    this._animating = true;
    this._anim = {
      start: performance.now(),
      duration: 260,
      moves,
    };
    const step = (now) => {
      const a = this._anim;
      if (!a) return;
      const raw = Math.min(1, (now - a.start) / a.duration);
      // Bouncy ease-out (slight overshoot) for a satisfying landing.
      const t =
        raw < 1
          ? 1 -
            Math.pow(2, -10 * raw) * Math.cos((raw * 10 - 0.75) * ((2 * Math.PI) / 3)) * 0.35 -
            Math.pow(1 - raw, 3) * 0.65 +
            Math.pow(1 - raw, 3) * 0.65
          : 1;
      const ease = 1 - Math.pow(1 - raw, 3);
      const offsets = new Map();
      for (const m of a.moves) {
        offsets.set(m.tile.id, {
          x: m.from.x + (m.to.x - m.from.x) * ease,
          y: m.from.y + (m.to.y - m.from.y) * ease,
        });
      }
      const dt = Math.min(48, now - this._lastFrame);
      this._lastFrame = now;
      this.renderer.render(this.selection.coords, offsets, dt);
      if (raw < 1) {
        this._rafId = requestAnimationFrame(step);
      } else {
        this._animating = false;
        this._anim = null;
        this._rafId = 0;
        // Little landing sparkle burst.
        for (const m of a.moves) {
          this.renderer.spawnBurst(m.to.x, m.to.y, '#ffffff', 3, 0.5);
        }
        this._draw();
      }
    };
    this._rafId = requestAnimationFrame(step);
  }
  _cancelAnimation() {
    if (this._rafId) cancelAnimationFrame(this._rafId);
    this._rafId = 0;
    this._anim = null;
    this._animating = false;
  }

  _randomValue() {
    const [min, max] = this.board.valueRange;
    let v = min + Math.floor(Math.random() * (max - min + 1));
    if (this.suppressZero && v === 0 && !(min === 0 && max === 0)) {
      while (v === 0) {
        v = min + Math.floor(Math.random() * (max - min + 1));
      }
    }
    return v;
  }

  _rejectFeedback() {
    console.log('[Game] Selection rejected - no match');
    this._showIndicator('No match', 'invalid');
    this.renderer.addShake(6, 180);
    for (const coord of this.selection.coords) {
      const p = this.board.grid.toPixel(coord);
      this.renderer.spawnBurst(p.x, p.y, '#ff5c7c', 8, 0.7);
    }
  }

  _updateIndicator() {
    const tiles = this._selectionTiles();
    if (tiles.length === 0) {
      this._showIndicator('\u00a0', '');
      return;
    }
    const sum = tiles.reduce((a, t) => a + t.value, 0);
    this._showIndicator(`Sum: ${sum}`, '');
  }

  _showIndicator(text, cls) {
    if (!this.indicatorEl) return;
    this.indicatorEl.textContent = text;
    this.indicatorEl.className = cls || '';
  }

  _draw() {
    const now = performance.now();
    const dt = Math.min(48, now - this._lastFrame);
    this._lastFrame = now;
    this.renderer.render(this.selection.coords, null, dt);
  }

  destroy() {
    this._running = false;
    this._cancelAnimation();
    this.input.destroy();
  }
}
