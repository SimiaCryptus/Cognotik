/**
 * hud.js - Heads-Up Display for Super Mario Bros Clone
 *
 * Renders the game overlay including:
 *  - Score counter with animated score popups
 *  - Coin count with coin icon
 *  - World / level indicator
 *  - Lives remaining with Mario icon
 *  - Time countdown with low-time warning
 *  - Pause / Game-Over overlays
 */

import { CANVAS_WIDTH, CANVAS_HEIGHT, TILE_SIZE } from './constants.js';

// ─── Layout constants ────────────────────────────────────────────────────────

const HUD_HEIGHT      = 48;          // pixels reserved at the top of the canvas
const HUD_BG_COLOR    = '#000000';
const HUD_TEXT_COLOR  = '#FFFFFF';
const HUD_WARN_COLOR  = '#FF4444';   // flashing red when time < TIME_WARN_THRESHOLD
const HUD_COIN_COLOR  = '#FFD700';
const HUD_FONT        = 'bold 14px "Press Start 2P", monospace';
const HUD_FONT_SMALL  = '10px "Press Start 2P", monospace';

const TIME_WARN_THRESHOLD = 100;     // seconds remaining before warning
const SCORE_POPUP_DURATION = 90;     // frames a score popup lives
const SCORE_POPUP_RISE     = 0.4;    // pixels per frame the popup rises

// Column x-positions (as fractions of canvas width)
const COL = {
    MARIO  : 0.04,   // "MARIO" label + score
    COIN   : 0.38,   // coin icon + count
    WORLD  : 0.55,   // "WORLD" label + id
    TIME   : 0.82,   // "TIME" label + countdown
};

// ─── ScorePopup helper ───────────────────────────────────────────────────────

class ScorePopup {
    /**
     * @param {number} value   - point value to display
     * @param {number} worldX  - world-space x (camera will offset this)
     * @param {number} worldY  - world-space y
     */
    constructor(value, worldX, worldY) {
        this.value    = value;
        this.worldX   = worldX;
        this.worldY   = worldY;
        this.timer    = SCORE_POPUP_DURATION;
        this.offsetY  = 0;
        this.alpha    = 1;
        this.text     = value >= 1000 ? `${value}` : `${value}`;
    }

    update() {
        this.timer--;
        this.offsetY  -= SCORE_POPUP_RISE;
        // Fade out in the last third of the lifetime
        if (this.timer < SCORE_POPUP_DURATION / 3) {
            this.alpha = this.timer / (SCORE_POPUP_DURATION / 3);
        }
    }

    get isDead() { return this.timer <= 0; }
}

// ─── HUD class ───────────────────────────────────────────────────────────────

export class HUD {
    /**
     * @param {object} gameState - live reference to the shared game-state object.
     *   Expected shape:
     *   {
     *     score      : number,
     *     coins      : number,
     *     lives      : number,
     *     world      : number,   // e.g. 1
     *     level      : number,   // e.g. 1
     *     time       : number,   // seconds remaining (integer)
     *     paused     : boolean,
     *     gameOver   : boolean,
     *     playerWon  : boolean,
     *   }
     */
    constructor(gameState) {
        this.state       = gameState;
        this.popups      = [];          // active ScorePopup instances
        this._warnFlash  = 0;           // frame counter for time-warning flash
        this._prevScore  = 0;           // detect score changes for animation
        this._scoreAnim  = 0;           // displayed score (animates toward real score)
        this._coinAnim   = false;       // brief coin-icon spin flag
        this._coinTimer  = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Spawn a floating score popup at a world-space position.
     * Called by the game engine whenever points are awarded.
     *
     * @param {number} points
     * @param {number} worldX
     * @param {number} worldY
     */
    addScorePopup(points, worldX, worldY) {
        this.popups.push(new ScorePopup(points, worldX, worldY));
    }

    /**
     * Trigger the coin-collect animation (brief icon spin).
     */
    triggerCoinAnim() {
        this._coinAnim  = true;
        this._coinTimer = 20;
    }

    /**
     * Update internal animation state.  Call once per game frame.
     */
    update() {
        // Animate displayed score rolling up toward real score
        const diff = this.state.score - this._scoreAnim;
        if (diff > 0) {
            this._scoreAnim += Math.max(1, Math.floor(diff * 0.12));
            if (this._scoreAnim > this.state.score) {
                this._scoreAnim = this.state.score;
            }
        }

        // Time-warning flash counter
        if (this.state.time <= TIME_WARN_THRESHOLD) {
            this._warnFlash = (this._warnFlash + 1) % 60;
        } else {
            this._warnFlash = 0;
        }

        // Coin icon animation
        if (this._coinTimer > 0) {
            this._coinTimer--;
            if (this._coinTimer === 0) this._coinAnim = false;
        }

        // Update score popups
        for (const p of this.popups) p.update();
        this.popups = this.popups.filter(p => !p.isDead);
    }

    /**
     * Draw the entire HUD.
     *
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} cameraX - current camera x offset (for popup positioning)
     */
    draw(ctx, cameraX = 0) {
        const W = ctx.canvas.width;
        const H = ctx.canvas.height;

        ctx.save();

        // ── Background bar ────────────────────────────────────────────────────
        ctx.fillStyle = HUD_BG_COLOR;
        ctx.fillRect(0, 0, W, HUD_HEIGHT);

        // Thin separator line
        ctx.fillStyle = '#333333';
        ctx.fillRect(0, HUD_HEIGHT - 2, W, 2);

        // ── Text setup ────────────────────────────────────────────────────────
        ctx.textBaseline = 'top';
        ctx.textAlign    = 'left';

        // ── MARIO / Score column ──────────────────────────────────────────────
        const marioX = Math.floor(W * COL.MARIO);
        this._drawLabel(ctx, 'MARIO', marioX, 6);
        this._drawValue(ctx, this._formatScore(this._scoreAnim), marioX, 24);

        // ── Coin column ───────────────────────────────────────────────────────
        const coinX = Math.floor(W * COL.COIN);
        this._drawCoinIcon(ctx, coinX, 14);
        this._drawValue(ctx, `×${this._pad(this.state.coins, 2)}`, coinX + 18, 24);

        // ── WORLD column ──────────────────────────────────────────────────────
        const worldX = Math.floor(W * COL.WORLD);
        this._drawLabel(ctx, 'WORLD', worldX, 6);
        const worldStr = `${this.state.world}-${this.state.level}`;
        // Centre the world string under the label
        ctx.font      = HUD_FONT;
        const ww      = ctx.measureText(worldStr).width;
        const lw      = ctx.measureText('WORLD').width;
        this._drawValue(ctx, worldStr, worldX + Math.floor((lw - ww) / 2), 24);

        // ── Lives ─────────────────────────────────────────────────────────────
        // Drawn just to the left of the WORLD column
        const livesX = worldX - 80;
        this._drawMarioIcon(ctx, livesX, 10);
        this._drawValue(ctx, `×${this._pad(this.state.lives, 2)}`, livesX + 20, 24);

        // ── TIME column ───────────────────────────────────────────────────────
        const timeX    = Math.floor(W * COL.TIME);
        const isWarn   = this.state.time <= TIME_WARN_THRESHOLD;
        const flashOn  = isWarn && this._warnFlash < 30;   // 30/60 duty cycle

        this._drawLabel(ctx, 'TIME', timeX, 6, isWarn && flashOn ? HUD_WARN_COLOR : HUD_TEXT_COLOR);
        this._drawValue(
            ctx,
            this._pad(Math.max(0, Math.ceil(this.state.time)), 3),
            timeX,
            24,
            isWarn && flashOn ? HUD_WARN_COLOR : HUD_TEXT_COLOR
        );

        // ── Score popups (world-space, offset by camera) ──────────────────────
        this._drawPopups(ctx, cameraX);

        // ── Overlays ──────────────────────────────────────────────────────────
        if (this.state.paused)    this._drawPauseOverlay(ctx, W, H);
        if (this.state.gameOver)  this._drawGameOverOverlay(ctx, W, H);
        if (this.state.playerWon) this._drawWinOverlay(ctx, W, H);

        ctx.restore();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    _drawLabel(ctx, text, x, y, color = '#AAAAAA') {
        ctx.font      = HUD_FONT_SMALL;
        ctx.fillStyle = color;
        ctx.fillText(text, x, y);
    }

    _drawValue(ctx, text, x, y, color = HUD_TEXT_COLOR) {
        ctx.font      = HUD_FONT;
        ctx.fillStyle = color;
        ctx.fillText(text, x, y);
    }

    /** Draw a simple pixel-art style coin icon */
    _drawCoinIcon(ctx, x, y) {
        const spin = this._coinAnim;
        const scaleX = spin ? Math.abs(Math.cos((20 - this._coinTimer) * 0.3)) : 1;

        ctx.save();
        ctx.translate(x + 6, y + 6);
        ctx.scale(scaleX, 1);

        // Outer circle
        ctx.fillStyle = HUD_COIN_COLOR;
        ctx.beginPath();
        ctx.arc(0, 0, 6, 0, Math.PI * 2);
        ctx.fill();

        // Inner highlight
        ctx.fillStyle = '#FFF8DC';
        ctx.beginPath();
        ctx.arc(-1, -1, 3, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();
    }

    /** Draw a tiny Mario silhouette (hat + body) */
    _drawMarioIcon(ctx, x, y) {
        ctx.save();
        ctx.translate(x, y);

        // Hat
        ctx.fillStyle = '#E52222';
        ctx.fillRect(2, 0, 10, 4);
        ctx.fillRect(0, 4, 14, 4);

        // Face
        ctx.fillStyle = '#FFCC99';
        ctx.fillRect(2, 8, 10, 6);

        // Eyes
        ctx.fillStyle = '#000000';
        ctx.fillRect(4, 10, 2, 2);
        ctx.fillRect(8, 10, 2, 2);

        // Overalls
        ctx.fillStyle = '#2244CC';
        ctx.fillRect(0, 14, 14, 6);

        ctx.restore();
    }

    /** Draw all active score popups, offset by the camera */
    _drawPopups(ctx, cameraX) {
        ctx.textAlign    = 'center';
        ctx.textBaseline = 'middle';
        ctx.font         = HUD_FONT;

        for (const p of this.popups) {
            const screenX = p.worldX - cameraX;
            const screenY = p.worldY + p.offsetY + HUD_HEIGHT;

            // Only draw if on screen
            if (screenX < -40 || screenX > ctx.canvas.width + 40) continue;

            ctx.globalAlpha = Math.max(0, Math.min(1, p.alpha));
            ctx.fillStyle   = HUD_TEXT_COLOR;

            // Thin shadow for readability over background
            ctx.fillStyle = '#000000';
            ctx.fillText(p.text, screenX + 1, screenY + 1);
            ctx.fillStyle = HUD_TEXT_COLOR;
            ctx.fillText(p.text, screenX, screenY);
        }

        ctx.globalAlpha  = 1;
        ctx.textAlign    = 'left';
        ctx.textBaseline = 'top';
    }

    /** Semi-transparent PAUSED overlay */
    _drawPauseOverlay(ctx, W, H) {
        this._drawDimOverlay(ctx, W, H, 'rgba(0,0,0,0.55)');
        ctx.font      = 'bold 28px "Press Start 2P", monospace';
        ctx.fillStyle = HUD_TEXT_COLOR;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('PAUSED', W / 2, H / 2);
        ctx.font      = HUD_FONT_SMALL;
        ctx.fillStyle = '#AAAAAA';
        ctx.fillText('Press P to resume', W / 2, H / 2 + 36);
    }

    /** GAME OVER overlay */
    _drawGameOverOverlay(ctx, W, H) {
        this._drawDimOverlay(ctx, W, H, 'rgba(0,0,0,0.70)');
        ctx.font      = 'bold 32px "Press Start 2P", monospace';
        ctx.fillStyle = HUD_WARN_COLOR;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('GAME OVER', W / 2, H / 2 - 20);
        ctx.font      = HUD_FONT;
        ctx.fillStyle = HUD_TEXT_COLOR;
        ctx.fillText(`SCORE  ${this._formatScore(this.state.score)}`, W / 2, H / 2 + 24);
        ctx.font      = HUD_FONT_SMALL;
        ctx.fillStyle = '#AAAAAA';
        ctx.fillText('Press ENTER to restart', W / 2, H / 2 + 56);
    }

    /** Level-clear / win overlay */
    _drawWinOverlay(ctx, W, H) {
        this._drawDimOverlay(ctx, W, H, 'rgba(0,0,0,0.60)');
        ctx.font      = 'bold 28px "Press Start 2P", monospace';
        ctx.fillStyle = '#FFD700';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('YOU WIN!', W / 2, H / 2 - 20);
        ctx.font      = HUD_FONT;
        ctx.fillStyle = HUD_TEXT_COLOR;
        ctx.fillText(`SCORE  ${this._formatScore(this.state.score)}`, W / 2, H / 2 + 24);
        ctx.font      = HUD_FONT_SMALL;
        ctx.fillStyle = '#AAAAAA';
        ctx.fillText('Press ENTER to continue', W / 2, H / 2 + 56);
    }

    _drawDimOverlay(ctx, W, H, color) {
        ctx.fillStyle = color;
        ctx.fillRect(0, 0, W, H);
    }

    // ── Formatting utilities ──────────────────────────────────────────────────

    /** Zero-pad a number to `digits` characters */
    _pad(n, digits) {
        return String(Math.max(0, Math.floor(n))).padStart(digits, '0');
    }

    /** Format score as 6-digit zero-padded string */
    _formatScore(n) {
        return this._pad(n, 6);
    }
}