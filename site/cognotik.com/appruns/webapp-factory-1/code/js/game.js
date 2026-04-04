// ============================================================
//  Super Mario Bros Clone — Game State Manager
// ============================================================

import {
} from './constants.js';
import {
   CANVAS_WIDTH as CANVAS_W, CANVAS_HEIGHT as CANVAS_H,
   TILE_SIZE, SCALE,
   CANVAS_WIDTH, CANVAS_HEIGHT,
   STATE, ENTITY_TYPE, TILE,
   SCORE, LEVEL_TIME, CLEAR_DELAY, COLOR,
   PLAYER_W, PLAYER_H_SMALL,
} from './constants.js';
import { Camera }   from './camera.js';
import { Tilemap }  from './tilemap.js';
import { LEVELS } from './level.js';
import { ACTION }   from './input.js';
import { Player, POWER } from './entities/player.js';
import { Goomba }   from './entities/goomba.js';
import { Koopa }    from './entities/koopa.js';
import {
  Mushroom, FireFlower, Star, CoinPop, ScorePop, BrickParticle,
} from './entities/powerup.js';
import * as Sprites from './spritesheet.js';


const T = TILE_SIZE * SCALE;

export class Game {
  constructor(canvas, input, audio) {
    this.canvas  = canvas;
    this.ctx     = canvas.getContext('2d');
    this.input   = input;
    this.audio   = audio;

   // Expose sprite module for dynamic entity rendering (fireballs etc.)
   this._spriteModule = Sprites;

    this.state   = STATE.TITLE;
    this.levelIndex = 0;

    this.score   = 0;
    this.lives   = 3;
    this.coins   = 0;
    this.hiScore = parseInt(localStorage.getItem('marioHiScore') || '0', 10);

    this.timer      = LEVEL_TIME;
    this.timerTick  = 0;

    this.entities   = [];
    this.effects    = [];   // score pops, particles

    this.flagReached   = false;
    this.flagY         = 0;
    this.flagSlideY    = 0;
    this.clearTimer    = 0;

    this.titleFrame    = 0;
    this.gameOverTimer = 0;

    this._loadLevel(0);
  }

  // ── Level loading ──────────────────────────────────────────

  _loadLevel(index) {
    const levelDef = LEVELS[index % LEVELS.length]();
    this.levelDef  = levelDef;
    this.tilemap   = new Tilemap(levelDef.grid);
    this.camera    = new Camera(this.tilemap.widthPx, this.tilemap.heightPx);

    // Spawn player
    const px = levelDef.startCol * T;
    const py = (levelDef.startRow - 1) * T;
    this.player = new Player(px, py, this);
    this.player.lives = this.lives;
    this.player.score = this.score;
    this.player.coins = this.coins;

    // Spawn enemies
    this.entities = [this.player];
    for (const def of levelDef.entities) {
      this._spawnEntity(def);
    }

    this.effects = [];
    this.timer   = levelDef.timeLimit || LEVEL_TIME;
    this.timerTick = 0;
    this.flagReached = false;
    this.clearTimer  = 0;
    this.state = STATE.PLAYING;
  }

  _spawnEntity(def) {
    const x = def.col * T;
    const y = def.row * T;
    switch (def.type) {
      case ENTITY_TYPE.GOOMBA: this.entities.push(new Goomba(x, y)); break;
      case ENTITY_TYPE.KOOPA:  this.entities.push(new Koopa(x, y));  break;
    }
  }

  // ── Public helpers ─────────────────────────────────────────

  spawnScorePop(x, y, value, label = null) {
    this.effects.push(new ScorePop(x, y, value, label));
  }

  spawnPowerUp(col, row, type) {
    const x = col * T + (T - 14 * SCALE) / 2;
    const y = row * T;
    switch (type) {
      case ENTITY_TYPE.MUSHROOM:
        this.entities.push(new Mushroom(x, y, false)); break;
      case ENTITY_TYPE.FLOWER:
        this.entities.push(new FireFlower(x, y)); break;
      case ENTITY_TYPE.STAR:
        this.entities.push(new Star(x, y)); break;
      case ENTITY_TYPE.ONEUP:
        this.entities.push(new Mushroom(x, y, true)); break;
      case ENTITY_TYPE.COIN_POP:
        this.entities.push(new CoinPop(x + T*0.25, y));
        this.player.addCoin();
        break;
    }
  }

  // ── Player death callback ──────────────────────────────────

  onPlayerDied() {
    this.lives  = this.player.lives;
    this.score  = this.player.score;
    this.coins  = this.player.coins;

    if (this.lives <= 0) {
      this.state = STATE.GAME_OVER;
      this.gameOverTimer = 180;
      this.audio.gameOver();
      this._saveHiScore();
    } else {
      // Respawn
      this._loadLevel(this.levelIndex);
    }
  }

  _saveHiScore() {
    if (this.score > this.hiScore) {
      this.hiScore = this.score;
      localStorage.setItem('marioHiScore', String(this.hiScore));
    }
  }

  // ── Main update ────────────────────────────────────────────

  update(dt) {
    switch (this.state) {
      case STATE.TITLE:       this._updateTitle(dt);     break;
      case STATE.PLAYING:     this._updatePlaying(dt);   break;
      case STATE.PAUSED:      this._updatePaused(dt);    break;
      case STATE.LEVEL_CLEAR: this._updateClear(dt);     break;
      case STATE.GAME_OVER:   this._updateGameOver(dt);  break;
    }
  }

  _updateTitle(dt) {
    this.titleFrame++;
   if (this.input.isPressed(ACTION.JUMP) || this.input.isPressed(ACTION.PAUSE)) {
      this.audio.resume();
      this.audio.startMusic();
      this.state = STATE.PLAYING;
    }
  }

  _updatePaused(dt) {
   if (this.input.isPressed(ACTION.PAUSE)) {
      this.state = STATE.PLAYING;
    }
  }

  _updateGameOver(dt) {
    this.gameOverTimer -= dt;
    if (this.gameOverTimer <= 0 &&
       (this.input.isPressed(ACTION.JUMP) || this.input.isPressed(ACTION.PAUSE))) {
      // Reset game
      this.score = 0; this.lives = 3; this.coins = 0;
      this.levelIndex = 0;
      this._loadLevel(0);
      this.audio.startMusic();
    }
  }

  _updatePlaying(dt) {
    // Pause
   if (this.input.isPressed(ACTION.PAUSE)) {
      this.state = STATE.PAUSED;
      return;
    }

    // Timer
    this.timerTick += dt;
    if (this.timerTick >= 60) {
      this.timerTick -= 60;
      this.timer = Math.max(0, this.timer - 1);
      if (this.timer === 0) this.player.takeDamage();
    }

    // Update entities
    for (const e of this.entities) {
      if (e.alive) e.update(dt, this.tilemap, this.entities, this);
    }

    // Update effects
    for (const ef of this.effects) ef.update(dt);

    // Prune dead
    this.entities = this.entities.filter(e => e.alive);
    this.effects  = this.effects.filter(e => e.alive);

    // Camera follows player
    this.camera.follow(this.player);

    // Sync score/lives/coins from player
    this.score = this.player.score;
    this.lives = this.player.lives;
    this.coins = this.player.coins;

    // Check block interactions (player bumping blocks from below)
    this._checkBlockBumps();

    // Check flag pole
    this._checkFlagPole();
  }

  _updateClear(dt) {
    this.clearTimer -= dt;

    // Slide flag down
    if (this.flagSlideY < this.flagEndY) {
      this.flagSlideY = Math.min(this.flagSlideY + 3 * SCALE, this.flagEndY);
    }

    if (this.clearTimer <= 0) {
      this._saveHiScore();
      this.levelIndex++;
      this._loadLevel(this.levelIndex);
      this.audio.startMusic();
    }
  }

  // ── Block bump detection ───────────────────────────────────

  _checkBlockBumps() {
    const p = this.player;
    if (p.vy >= 0 || p.dying) return;  // only when moving up

    // Check tiles just above player's head
    const c0 = this.tilemap.worldToTile(p.left  + 2);
    const c1 = this.tilemap.worldToTile(p.right  - 2);
    const row = this.tilemap.worldToTile(p.top - 2);

    for (let c = c0; c <= c1; c++) {
      const tileId = this.tilemap.getTile(c, row);
      if (tileId === TILE.QUESTION) {
        this._hitQuestionBlock(c, row);
      } else if (tileId === TILE.BRICK) {
        this._hitBrick(c, row);
      }
    }
  }

  _hitQuestionBlock(col, row) {
    this.tilemap.setTile(col, row, TILE.USED_BLOCK);
    this.audio.blockBump();

    // Determine content
    const contents = this.levelDef.questionContents || {};
    const type = contents[col] || ENTITY_TYPE.COIN_POP;
    this.spawnPowerUp(col, row, type);
  }

  _hitBrick(col, row) {
    if (this.player.isBig) {
      // Break brick
      this.tilemap.setTile(col, row, TILE.AIR);
      this.audio.brickBreak();
      this.player.addScore(SCORE.BRICK_BREAK);
      // Spawn particles
      const bx = col * T + T/2;
      const by = row * T + T/2;
      const S2 = SCALE;
      this.effects.push(new BrickParticle(bx, by, -3*S2, -5*S2));
      this.effects.push(new BrickParticle(bx, by,  3*S2, -5*S2));
      this.effects.push(new BrickParticle(bx, by, -2*S2, -3*S2));
      this.effects.push(new BrickParticle(bx, by,  2*S2, -3*S2));
    } else {
      // Bump
      this.audio.blockBump();
    }
  }

  // ── Flag pole ──────────────────────────────────────────────

  _checkFlagPole() {
    if (this.flagReached) return;
    const p = this.player;

    // Find flag pole column
    const poleCol = 197;  // hardcoded for level 1-1
    const poleX   = poleCol * T;

    if (p.right >= poleX && p.left <= poleX + T * 0.5) {
      this.flagReached = true;
      this.state = STATE.LEVEL_CLEAR;
      this.clearTimer = CLEAR_DELAY;
      this.audio.flagpole();
      this.audio.stopMusic();

      // Score based on height
      const heightScore = Math.max(100, Math.floor((this.tilemap.heightPx - p.y) / T) * 500);
      this.player.addScore(Math.min(5000, heightScore));
      this.score = this.player.score;

      // Flag slide
      this.flagSlideY  = 3 * T;
      this.flagEndY    = 10 * T;
    }
  }

  // ── Render ─────────────────────────────────────────────────

  render() {
    const ctx = this.ctx;
    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);

    switch (this.state) {
      case STATE.TITLE:       this._renderTitle(ctx);    break;
      case STATE.GAME_OVER:   this._renderGameOver(ctx); break;
      default:
        this._renderWorld(ctx);
        this._renderHUD(ctx);
        if (this.state === STATE.PAUSED)      this._renderPause(ctx);
        if (this.state === STATE.LEVEL_CLEAR) this._renderClear(ctx);
        break;
    }
  }

  _renderWorld(ctx) {
    // Sky background
    ctx.fillStyle = COLOR.SKY;
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // Tilemap
    this.tilemap.render(ctx, this.camera);

    // Flag (sliding)
    if (this.flagReached) {
      const poleX = 197 * T - this.camera.x;
      Sprites.drawFlag(ctx, poleX, this.flagSlideY - this.camera.y, T * 0.8, T * 0.8);
    }

    // Entities (back to front)
    for (const e of this.entities) {
      if (e.alive) e.render(ctx, this.camera);
    }

    // Effects
    for (const ef of this.effects) ef.render(ctx, this.camera);
  }

  _renderHUD(ctx) {
    const HUD_H = 36;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, CANVAS_W, HUD_H);

    ctx.fillStyle = '#fff';
     ctx.font = `bold 12px monospace`;
    ctx.textBaseline = 'top';

    // MARIO
    ctx.textAlign = 'left';
    ctx.fillText('MARIO', 16, 6);
    ctx.fillText(String(this.score).padStart(6, '0'), 16, 20);

    // COINS
    ctx.textAlign = 'center';
    ctx.fillStyle = '#fcd800';
    ctx.fillText(`★ ×${String(this.coins).padStart(2,'0')}`, CANVAS_W/2, 6);

    // WORLD
    ctx.fillStyle = '#fff';
    ctx.fillText(this.levelDef?.name || 'WORLD 1-1', CANVAS_W/2, 20);

    // TIME
    ctx.textAlign = 'right';
    ctx.fillText('TIME', CANVAS_W - 16, 6);
    ctx.fillStyle = this.timer < 100 ? '#e40058' : '#fff';
    ctx.fillText(String(Math.ceil(this.timer)).padStart(3, '0'), CANVAS_W - 16, 20);

    // HI-SCORE
    ctx.fillStyle = '#fff';
    ctx.textAlign = 'center';
    ctx.fillText(`HI ${String(Math.max(this.hiScore, this.score)).padStart(6,'0')}`, CANVAS_W/2 + 120, 6);

    // Lives
    ctx.textAlign = 'left';
    ctx.fillText(`♥ ×${this.lives}`, 16 + 120, 6);
  }

  _renderTitle(ctx) {
    ctx.fillStyle = COLOR.SKY;
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    // Draw some ground
    ctx.fillStyle = '#c84c0c';
    ctx.fillRect(0, CANVAS_H - T, CANVAS_W, T);

    // Title box
    ctx.fillStyle = 'rgba(0,0,0,0.7)';
    ctx.fillRect(CANVAS_W/2 - 280, CANVAS_H/2 - 120, 560, 240);

    ctx.fillStyle = '#e40058';
     ctx.font = `bold 24px monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('SUPER MARIO BROS', CANVAS_W/2, CANVAS_H/2 - 60);

    ctx.fillStyle = '#fff';
     ctx.font = `bold 12px monospace`;
    ctx.fillText('CLONE', CANVAS_W/2, CANVAS_H/2 - 20);

    // Blink "press start"
    if (Math.floor(this.titleFrame / 30) % 2 === 0) {
      ctx.fillStyle = '#fcd800';
       ctx.font = `bold 11px monospace`;
      ctx.fillText('PRESS SPACE TO START', CANVAS_W/2, CANVAS_H/2 + 40);
    }

    ctx.fillStyle = '#aaa';
     ctx.font = `9px monospace`;
    ctx.fillText('← → MOVE   SPACE JUMP   SHIFT RUN', CANVAS_W/2, CANVAS_H/2 + 80);

    // Draw Mario on title
    Sprites.drawMario(ctx, CANVAS_W/2 - 24, CANVAS_H/2 + 100, PLAYER_W, PLAYER_H_SMALL, {
      big: false, facingRight: true, frame: 0,
    });
  }

  _renderPause(ctx) {
    ctx.fillStyle = 'rgba(0,0,0,0.5)';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    ctx.fillStyle = '#fff';
     ctx.font = `bold 18px monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('PAUSED', CANVAS_W/2, CANVAS_H/2);
  }

  _renderClear(ctx) {
    ctx.fillStyle = 'rgba(0,0,0,0.4)';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);
    ctx.fillStyle = '#fcd800';
     ctx.font = `bold 16px monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('COURSE CLEAR!', CANVAS_W/2, CANVAS_H/2 - 30);
    ctx.fillStyle = '#fff';
     ctx.font = `bold 12px monospace`;
    ctx.fillText(`SCORE: ${this.score}`, CANVAS_W/2, CANVAS_H/2 + 20);
  }

  _renderGameOver(ctx) {
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, CANVAS_W, CANVAS_H);

    ctx.fillStyle = '#e40058';
     ctx.font = `bold 24px monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('GAME OVER', CANVAS_W/2, CANVAS_H/2 - 40);

    ctx.fillStyle = '#fff';
     ctx.font = `bold 12px monospace`;
    ctx.fillText(`SCORE: ${this.score}`, CANVAS_W/2, CANVAS_H/2 + 10);
    ctx.fillText(`HI-SCORE: ${this.hiScore}`, CANVAS_W/2, CANVAS_H/2 + 40);

    if (this.gameOverTimer <= 0 && Math.floor(Date.now() / 500) % 2 === 0) {
      ctx.fillStyle = '#fcd800';
       ctx.font = `bold 11px monospace`;
      ctx.fillText('PRESS SPACE TO RETRY', CANVAS_W/2, CANVAS_H/2 + 90);
    }
  }
}