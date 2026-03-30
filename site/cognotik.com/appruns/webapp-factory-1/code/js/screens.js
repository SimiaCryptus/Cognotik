/**
 * screens.js
 * Screen renderers for each game state in Super Mario Bros Clone
 * Handles: TitleScreen, PauseScreen, GameOverScreen, LevelCompleteScreen, WorldIntroScreen
 */

'use strict';

// ─── Title Screen ────────────────────────────────────────────────────────────

class TitleScreen {
    /**
     * @param {CanvasRenderingContext2D} ctx
     * @param {AssetManager} assets
     */
    constructor(ctx, assets) {
        this.ctx = ctx;
        this.assets = assets;

        // Animation state
        this.timer          = 0;
        this.blinkTimer     = 0;
        this.blinkVisible   = true;
        this.cloudOffset    = 0;
        this.marioWalkX     = -TILE_SIZE * 2;
        this.marioFrame     = 0;
        this.marioFrameTimer = 0;
        this.logoScale      = 0;
        this.logoScaleDir   = 1;
        this.logoY          = CANVAS_HEIGHT * 0.18;
        this.copyrightAlpha = 0;
        this.starParticles  = [];
        this.introPhase     = 0; // 0=logo-in, 1=idle
        this.introTimer     = 0;

        // Decorative clouds
        this.clouds = [
            { x: 60,  y: 60,  scale: 1.2 },
            { x: 220, y: 40,  scale: 0.9 },
            { x: 380, y: 70,  scale: 1.0 },
            { x: 520, y: 50,  scale: 1.3 },
        ];

        // Decorative hills
        this.hills = [
            { x: 0,   y: CANVAS_HEIGHT - 80, r: 70 },
            { x: 160, y: CANVAS_HEIGHT - 60, r: 50 },
            { x: 340, y: CANVAS_HEIGHT - 90, r: 80 },
            { x: 500, y: CANVAS_HEIGHT - 70, r: 60 },
        ];

        this._spawnStars(20);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    _spawnStars(n) {
        for (let i = 0; i < n; i++) {
            this.starParticles.push(this._newStar());
        }
    }

    _newStar(x) {
        return {
            x:     x !== undefined ? x : Math.random() * CANVAS_HEIGHT,
            y:     Math.random() * CANVAS_HEIGHT * 0.55,
            size:  1 + Math.random() * 2,
            alpha: 0.4 + Math.random() * 0.6,
            speed: 0.2 + Math.random() * 0.4,
            twinkleSpeed: 0.03 + Math.random() * 0.05,
            twinkleOffset: Math.random() * Math.PI * 2,
        };
    }

    // ── Public API ───────────────────────────────────────────────────────────

    update(dt) {
        this.timer      += dt;
        this.blinkTimer += dt;
        this.cloudOffset = (this.cloudOffset + 20 * dt) % (CANVAS_WIDTH + 120);

        // Blink "PRESS START" every 0.55 s
        if (this.blinkTimer >= 0.55) {
            this.blinkTimer  = 0;
            this.blinkVisible = !this.blinkVisible;
        }

        // Logo scale pulse
        this.logoScale += this.logoScaleDir * dt * 0.4;
        if (this.logoScale >  0.06) this.logoScaleDir = -1;
        if (this.logoScale < -0.06) this.logoScaleDir =  1;

        // Intro phase
        if (this.introPhase === 0) {
            this.introTimer += dt;
            if (this.introTimer >= 1.2) this.introPhase = 1;
        }

        // Copyright fade-in
        if (this.copyrightAlpha < 1) {
            this.copyrightAlpha = Math.min(1, this.copyrightAlpha + dt * 0.8);
        }

        // Walking Mario on title
        this.marioWalkX += 60 * dt;
        if (this.marioWalkX > CANVAS_WIDTH + TILE_SIZE * 2) {
            this.marioWalkX = -TILE_SIZE * 2;
        }
        this.marioFrameTimer += dt;
        if (this.marioFrameTimer >= 0.12) {
            this.marioFrameTimer = 0;
            this.marioFrame = (this.marioFrame + 1) % 3;
        }

        // Twinkle stars
        for (const s of this.starParticles) {
            s.x -= s.speed * dt * 30;
            if (s.x < -4) {
                Object.assign(s, this._newStar(CANVAS_WIDTH + 4));
            }
        }
    }

    draw() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;
        const H = CANVAS_HEIGHT;

        // ── Sky gradient ──────────────────────────────────────────────────
        const sky = ctx.createLinearGradient(0, 0, 0, H);
        sky.addColorStop(0,   '#1a0533');
        sky.addColorStop(0.5, '#3b1a6e');
        sky.addColorStop(1,   '#5c3a9e');
        ctx.fillStyle = sky;
        ctx.fillRect(0, 0, W, H);

        // ── Stars ─────────────────────────────────────────────────────────
        for (const s of this.starParticles) {
            const twinkle = 0.5 + 0.5 * Math.sin(this.timer * s.twinkleSpeed * 60 + s.twinkleOffset);
            ctx.globalAlpha = s.alpha * twinkle;
            ctx.fillStyle = '#ffffff';
            ctx.beginPath();
            ctx.arc(s.x, s.y, s.size, 0, Math.PI * 2);
            ctx.fill();
        }
        ctx.globalAlpha = 1;

        // ── Ground strip ──────────────────────────────────────────────────
        ctx.fillStyle = COLORS.GROUND;
        ctx.fillRect(0, H - 32, W, 32);
        ctx.fillStyle = COLORS.GROUND_TOP;
        ctx.fillRect(0, H - 32, W, 4);

        // ── Hills ─────────────────────────────────────────────────────────
        for (const h of this.hills) {
            ctx.fillStyle = '#2d8a2d';
            ctx.beginPath();
            ctx.arc(h.x, h.y, h.r, Math.PI, 0);
            ctx.fill();
            ctx.fillStyle = '#3aaa3a';
            ctx.beginPath();
            ctx.arc(h.x, h.y - h.r * 0.3, h.r * 0.45, Math.PI, 0);
            ctx.fill();
        }

        // ── Clouds ────────────────────────────────────────────────────────
        for (const c of this.clouds) {
            const cx = (c.x + this.cloudOffset * 0.3) % (W + 120) - 60;
            this._drawCloud(cx, c.y, c.scale);
        }

        // ── Logo ──────────────────────────────────────────────────────────
        const logoProgress = this.introPhase === 0
            ? Math.min(1, this.introTimer / 1.2)
            : 1;
        const logoBaseScale = 1 + this.logoScale;
        const logoY = this.logoY - (1 - logoProgress) * 80;

        ctx.save();
        ctx.globalAlpha = logoProgress;
        ctx.translate(W / 2, logoY);
        ctx.scale(logoBaseScale, logoBaseScale);
        this._drawLogo(0, 0);
        ctx.restore();

        // ── Walking Mario ─────────────────────────────────────────────────
        this._drawWalkingMario(this.marioWalkX, H - 32 - TILE_SIZE);

        // ── "PRESS START" prompt ──────────────────────────────────────────
        if (this.introPhase === 1 && this.blinkVisible) {
            ctx.font      = `bold ${FONT_SIZE_LARGE}px "${FONT_FAMILY}"`;
            ctx.textAlign = 'center';
            this._drawTextWithShadow('PRESS  START', W / 2, H * 0.72, '#ffffff', '#000000');
        }

        // ── Copyright ─────────────────────────────────────────────────────
        ctx.globalAlpha = this.copyrightAlpha;
        ctx.font        = `${FONT_SIZE_SMALL}px "${FONT_FAMILY}"`;
        ctx.textAlign   = 'center';
        ctx.fillStyle   = '#cccccc';
        ctx.fillText('© 2024  SUPER MARIO CLONE', W / 2, H - 10);
        ctx.globalAlpha = 1;

        // ── High-score strip ──────────────────────────────────────────────
        this._drawScoreStrip();
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    _drawCloud(x, y, scale) {
        const { ctx } = this;
        ctx.save();
        ctx.translate(x, y);
        ctx.scale(scale, scale);
        ctx.fillStyle = 'rgba(255,255,255,0.85)';
        ctx.beginPath();
        ctx.arc(0,   0,  18, Math.PI, 0);
        ctx.arc(22,  -8, 14, Math.PI, 0);
        ctx.arc(-18, -4, 12, Math.PI, 0);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
    }

    _drawLogo(cx, cy) {
        const { ctx } = this;

        // Shadow block
        ctx.fillStyle = '#5a1a00';
        ctx.fillRect(cx - 122, cy - 28, 244, 56);

        // Main red block
        const grad = ctx.createLinearGradient(cx - 120, cy - 26, cx - 120, cy + 26);
        grad.addColorStop(0, '#ff6a00');
        grad.addColorStop(0.5, '#e63000');
        grad.addColorStop(1, '#a01800');
        ctx.fillStyle = grad;
        ctx.fillRect(cx - 120, cy - 26, 240, 52);

        // White border
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth   = 3;
        ctx.strokeRect(cx - 120, cy - 26, 240, 52);

        // Title text
        ctx.font      = `bold 32px "${FONT_FAMILY}"`;
        ctx.textAlign = 'center';
        ctx.fillStyle = '#ffe000';
        ctx.fillText('SUPER MARIO', cx, cy - 2);
        ctx.fillStyle = '#ffffff';
        ctx.font      = `bold 18px "${FONT_FAMILY}"`;
        ctx.fillText('B R O S   C L O N E', cx, cy + 20);
    }

    _drawWalkingMario(x, y) {
        const { ctx } = this;
        const S = TILE_SIZE;

        // Simple pixel-art Mario silhouette (3-frame walk cycle)
        const frames = [
            // frame 0 – stand
            () => {
                ctx.fillStyle = COLORS.MARIO_HAT;
                ctx.fillRect(x + 4, y,     S - 8, 5);
                ctx.fillRect(x + 2, y + 5, S - 4, 5);
                ctx.fillStyle = COLORS.MARIO_SKIN;
                ctx.fillRect(x + 4, y + 10, S - 8, 6);
                ctx.fillStyle = COLORS.MARIO_SHIRT;
                ctx.fillRect(x + 2, y + 16, S - 4, 8);
                ctx.fillStyle = COLORS.MARIO_PANTS;
                ctx.fillRect(x + 2, y + 24, 6, 8);
                ctx.fillRect(x + S - 8, y + 24, 6, 8);
            },
            // frame 1 – walk A
            () => {
                ctx.fillStyle = COLORS.MARIO_HAT;
                ctx.fillRect(x + 4, y,     S - 8, 5);
                ctx.fillRect(x + 2, y + 5, S - 4, 5);
                ctx.fillStyle = COLORS.MARIO_SKIN;
                ctx.fillRect(x + 4, y + 10, S - 8, 6);
                ctx.fillStyle = COLORS.MARIO_SHIRT;
                ctx.fillRect(x + 2, y + 16, S - 4, 8);
                ctx.fillStyle = COLORS.MARIO_PANTS;
                ctx.fillRect(x + 2, y + 24, 6, 8);
                ctx.fillRect(x + S - 6, y + 26, 6, 6);
            },
            // frame 2 – walk B
            () => {
                ctx.fillStyle = COLORS.MARIO_HAT;
                ctx.fillRect(x + 4, y,     S - 8, 5);
                ctx.fillRect(x + 2, y + 5, S - 4, 5);
                ctx.fillStyle = COLORS.MARIO_SKIN;
                ctx.fillRect(x + 4, y + 10, S - 8, 6);
                ctx.fillStyle = COLORS.MARIO_SHIRT;
                ctx.fillRect(x + 2, y + 16, S - 4, 8);
                ctx.fillStyle = COLORS.MARIO_PANTS;
                ctx.fillRect(x + 4, y + 26, 6, 6);
                ctx.fillRect(x + S - 8, y + 24, 6, 8);
            },
        ];
        frames[this.marioFrame]();
    }

    _drawTextWithShadow(text, x, y, color, shadow) {
        const { ctx } = this;
        ctx.fillStyle = shadow;
        ctx.fillText(text, x + 2, y + 2);
        ctx.fillStyle = color;
        ctx.fillText(text, x, y);
    }

    _drawScoreStrip() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;

        ctx.fillStyle = 'rgba(0,0,0,0.45)';
        ctx.fillRect(0, 8, W, 28);

        ctx.font      = `${FONT_SIZE_SMALL}px "${FONT_FAMILY}"`;
        ctx.textAlign = 'left';
        ctx.fillStyle = '#ffffff';
        ctx.fillText('MARIO', 12, 28);
        ctx.fillText('000000', 12, 40);

        ctx.textAlign = 'center';
        ctx.fillText('TOP', W / 2, 28);
        ctx.fillText('000000', W / 2, 40);

        ctx.textAlign = 'right';
        ctx.fillText('WORLD', W - 12, 28);
        ctx.fillText('1-1', W - 12, 40);
    }
}


// ─── Pause Screen ─────────────────────────────────────────────────────────────

class PauseScreen {
    /**
     * @param {CanvasRenderingContext2D} ctx
     */
    constructor(ctx) {
        this.ctx        = ctx;
        this.timer      = 0;
        this.blinkTimer = 0;
        this.blinkOn    = true;
        this.overlayAlpha = 0;
    }

    update(dt) {
        this.timer      += dt;
        this.blinkTimer += dt;
        if (this.blinkTimer >= 0.6) {
            this.blinkTimer = 0;
            this.blinkOn    = !this.blinkOn;
        }
        // Fade-in overlay
        if (this.overlayAlpha < 0.55) {
            this.overlayAlpha = Math.min(0.55, this.overlayAlpha + dt * 3);
        }
    }

    draw() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;
        const H = CANVAS_HEIGHT;

        // Semi-transparent overlay
        ctx.fillStyle = `rgba(0,0,0,${this.overlayAlpha})`;
        ctx.fillRect(0, 0, W, H);

        // Panel
        const pw = 220, ph = 100;
        const px = (W - pw) / 2;
        const py = (H - ph) / 2;

        ctx.fillStyle = 'rgba(0,0,0,0.85)';
        this._roundRect(px, py, pw, ph, 10);
        ctx.fill();

        ctx.strokeStyle = '#ffe000';
        ctx.lineWidth   = 3;
        this._roundRect(px, py, pw, ph, 10);
        ctx.stroke();

        // "PAUSED" text
        ctx.font      = `bold ${FONT_SIZE_XLARGE}px "${FONT_FAMILY}"`;
        ctx.textAlign = 'center';
        ctx.fillStyle = '#ffe000';
        ctx.fillText('PAUSED', W / 2, py + 48);

        // Blink hint
        if (this.blinkOn) {
            ctx.font      = `${FONT_SIZE_SMALL}px "${FONT_FAMILY}"`;
            ctx.fillStyle = '#ffffff';
            ctx.fillText('PRESS  P  TO  RESUME', W / 2, py + 80);
        }
    }

    _roundRect(x, y, w, h, r) {
        const { ctx } = this;
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h - r);
        ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        ctx.lineTo(x + r, y + h);
        ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }
}


// ─── Game Over Screen ─────────────────────────────────────────────────────────

class GameOverScreen {
    /**
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} score  – final score to display
     */
    constructor(ctx, score = 0) {
        this.ctx   = ctx;
        this.score = score;

        this.timer        = 0;
        this.textAlpha    = 0;
        this.scoreAlpha   = 0;
        this.promptAlpha  = 0;
        this.blinkTimer   = 0;
        this.blinkOn      = true;
        this.shakeX       = 0;
        this.shakeTimer   = 0;
        this.particles    = [];

        // Spawn explosion particles
        for (let i = 0; i < 40; i++) {
            const angle = (i / 40) * Math.PI * 2;
            const speed = 40 + Math.random() * 120;
            this.particles.push({
                x:     CANVAS_WIDTH  / 2,
                y:     CANVAS_HEIGHT / 2,
                vx:    Math.cos(angle) * speed,
                vy:    Math.sin(angle) * speed,
                size:  3 + Math.random() * 5,
                color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)],
                life:  1,
                decay: 0.4 + Math.random() * 0.6,
            });
        }
    }

    update(dt) {
        this.timer += dt;

        // Screen shake on entry
        if (this.shakeTimer > 0) {
            this.shakeTimer -= dt;
            this.shakeX = (Math.random() - 0.5) * 8;
        } else {
            this.shakeX = 0;
        }

        // Staggered fade-ins
        if (this.timer > 0.3)  this.textAlpha   = Math.min(1, this.textAlpha   + dt * 2.5);
        if (this.timer > 1.0)  this.scoreAlpha  = Math.min(1, this.scoreAlpha  + dt * 2.0);
        if (this.timer > 2.0)  this.promptAlpha = Math.min(1, this.promptAlpha + dt * 1.5);

        // Blink prompt
        this.blinkTimer += dt;
        if (this.blinkTimer >= 0.55) {
            this.blinkTimer = 0;
            this.blinkOn    = !this.blinkOn;
        }

        // Update particles
        for (const p of this.particles) {
            p.x    += p.vx * dt;
            p.y    += p.vy * dt;
            p.vy   += 200 * dt; // gravity
            p.life -= p.decay * dt;
        }
        // Remove dead particles
        for (let i = this.particles.length - 1; i >= 0; i--) {
            if (this.particles[i].life <= 0) this.particles.splice(i, 1);
        }
    }

    draw() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;
        const H = CANVAS_HEIGHT;

        ctx.save();
        ctx.translate(this.shakeX, 0);

        // Dark background
        ctx.fillStyle = 'rgba(0,0,0,0.82)';
        ctx.fillRect(-4, 0, W + 8, H);

        // Particles
        for (const p of this.particles) {
            ctx.globalAlpha = Math.max(0, p.life);
            ctx.fillStyle   = p.color;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
            ctx.fill();
        }
        ctx.globalAlpha = 1;

        // "GAME OVER" text
        ctx.globalAlpha = this.textAlpha;
        ctx.font        = `bold 52px "${FONT_FAMILY}"`;
        ctx.textAlign   = 'center';

        // Outline
        ctx.strokeStyle = '#000000';
        ctx.lineWidth   = 6;
        ctx.strokeText('GAME  OVER', W / 2, H / 2 - 30);

        // Fill with gradient
        const tg = ctx.createLinearGradient(0, H / 2 - 80, 0, H / 2 - 10);
        tg.addColorStop(0, '#ff4444');
        tg.addColorStop(1, '#aa0000');
        ctx.fillStyle = tg;
        ctx.fillText('GAME  OVER', W / 2, H / 2 - 30);

        // Score
        ctx.globalAlpha = this.scoreAlpha;
        ctx.font        = `bold ${FONT_SIZE_LARGE}px "${FONT_FAMILY}"`;
        ctx.fillStyle   = '#ffffff';
        ctx.fillText(`SCORE:  ${String(this.score).padStart(6, '0')}`, W / 2, H / 2 + 20);

        // Prompt
        if (this.blinkOn) {
            ctx.globalAlpha = this.promptAlpha;
            ctx.font        = `${FONT_SIZE_MEDIUM}px "${FONT_FAMILY}"`;
            ctx.fillStyle   = '#ffe000';
            ctx.fillText('PRESS  ENTER  TO  CONTINUE', W / 2, H / 2 + 70);
        }

        ctx.globalAlpha = 1;
        ctx.restore();
    }
}


// ─── Level Complete Screen ────────────────────────────────────────────────────

class LevelCompleteScreen {
    /**
     * @param {CanvasRenderingContext2D} ctx
     * @param {object} stats  – { score, coins, time, world, level }
     */
    constructor(ctx, stats = {}) {
        this.ctx   = ctx;
        this.stats = Object.assign({ score: 0, coins: 0, time: 0, world: 1, level: 1 }, stats);

        this.timer       = 0;
        this.phase       = 0;   // 0=banner-in, 1=tally, 2=done
        this.bannerY     = -80;
        this.bannerTargY = CANVAS_HEIGHT * 0.28;
        this.tallyTimer  = 0;
        this.tallyStep   = 0;   // which row is being counted
        this.tallyValues = [0, 0, 0]; // [score, coins, time-bonus]
        this.finalScore  = 0;
        this.blinkTimer  = 0;
        this.blinkOn     = true;
        this.fireworks   = [];
        this.fwTimer     = 0;

        // Compute targets
        this._scoreTarget     = this.stats.score;
        this._coinsTarget     = this.stats.coins;
        this._timeBonusTarget = Math.max(0, this.stats.time) * 50; // 50 pts per second left
        this.finalScore       = this._scoreTarget + this._timeBonusTarget;
    }

    update(dt) {
        this.timer += dt;

        // Phase 0 – slide banner in
        if (this.phase === 0) {
            this.bannerY += (this.bannerTargY - this.bannerY) * Math.min(1, dt * 8);
            if (Math.abs(this.bannerY - this.bannerTargY) < 1) {
                this.bannerY = this.bannerTargY;
                this.phase   = 1;
            }
        }

        // Phase 1 – tally rows
        if (this.phase === 1) {
            this.tallyTimer += dt;
            const STEP_DUR = 1.2; // seconds per row
            const step = Math.floor(this.tallyTimer / STEP_DUR);

            if (step === 0) {
                const t = Math.min(1, this.tallyTimer / STEP_DUR);
                this.tallyValues[0] = Math.floor(t * this._scoreTarget);
            } else if (step === 1) {
                this.tallyValues[0] = this._scoreTarget;
                const t = Math.min(1, (this.tallyTimer - STEP_DUR) / STEP_DUR);
                this.tallyValues[1] = Math.floor(t * this._coinsTarget);
            } else if (step === 2) {
                this.tallyValues[1] = this._coinsTarget;
                const t = Math.min(1, (this.tallyTimer - STEP_DUR * 2) / STEP_DUR);
                this.tallyValues[2] = Math.floor(t * this._timeBonusTarget);
            } else {
                this.tallyValues[2] = this._timeBonusTarget;
                this.phase = 2;
            }
        }

        // Phase 2 – fireworks + blink
        if (this.phase === 2) {
            this.blinkTimer += dt;
            if (this.blinkTimer >= 0.55) {
                this.blinkTimer = 0;
                this.blinkOn    = !this.blinkOn;
            }

            this.fwTimer += dt;
            if (this.fwTimer >= 0.4) {
                this.fwTimer = 0;
                this._spawnFirework();
            }
        }

        // Update fireworks
        for (const fw of this.fireworks) {
            fw.timer += dt;
            for (const p of fw.particles) {
                p.x  += p.vx * dt;
                p.y  += p.vy * dt;
                p.vy += 80 * dt;
                p.life -= dt * 1.2;
            }
        }
        for (let i = this.fireworks.length - 1; i >= 0; i--) {
            if (this.fireworks[i].timer > 2) this.fireworks.splice(i, 1);
        }
    }

    draw() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;
        const H = CANVAS_HEIGHT;

        // Background
        const bg = ctx.createLinearGradient(0, 0, 0, H);
        bg.addColorStop(0, '#000033');
        bg.addColorStop(1, '#001a66');
        ctx.fillStyle = bg;
        ctx.fillRect(0, 0, W, H);

        // Fireworks
        for (const fw of this.fireworks) {
            for (const p of fw.particles) {
                ctx.globalAlpha = Math.max(0, p.life);
                ctx.fillStyle   = p.color;
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
                ctx.fill();
            }
        }
        ctx.globalAlpha = 1;

        // Banner
        const bw = W - 40, bh = 60;
        const bx = 20;
        ctx.fillStyle = '#c80000';
        ctx.fillRect(bx, this.bannerY, bw, bh);
        ctx.strokeStyle = '#ffe000';
        ctx.lineWidth   = 3;
        ctx.strokeRect(bx, this.bannerY, bw, bh);

        ctx.font      = `bold 30px "${FONT_FAMILY}"`;
        ctx.textAlign = 'center';
        ctx.fillStyle = '#ffe000';
        ctx.fillText(
            `WORLD  ${this.stats.world}-${this.stats.level}  COMPLETE!`,
            W / 2,
            this.bannerY + 40
        );

        // Tally panel
        if (this.phase >= 1) {
            const rows = [
                { label: 'SCORE',      value: this.tallyValues[0] },
                { label: 'COINS',      value: this.tallyValues[1] },
                { label: 'TIME BONUS', value: this.tallyValues[2] },
            ];
            const panelY = this.bannerTargY + 80;
            const rowH   = 36;

            ctx.fillStyle = 'rgba(0,0,0,0.6)';
            ctx.fillRect(bx, panelY, bw, rowH * rows.length + 20);

            rows.forEach((row, i) => {
                const ry = panelY + 28 + i * rowH;
                ctx.font      = `bold ${FONT_SIZE_MEDIUM}px "${FONT_FAMILY}"`;
                ctx.textAlign = 'left';
                ctx.fillStyle = '#aaddff';
                ctx.fillText(row.label, bx + 20, ry);
                ctx.textAlign = 'right';
                ctx.fillStyle = '#ffffff';
                ctx.fillText(String(row.value).padStart(7, ' '), bx + bw - 20, ry);
            });

            // Divider + total
            if (this.phase === 2) {
                const ty = panelY + rowH * rows.length + 16;
                ctx.strokeStyle = '#ffe000';
                ctx.lineWidth   = 2;
                ctx.beginPath();
                ctx.moveTo(bx + 10, ty - 8);
                ctx.lineTo(bx + bw - 10, ty - 8);
                ctx.stroke();

                ctx.font      = `bold ${FONT_SIZE_LARGE}px "${FONT_FAMILY}"`;
                ctx.textAlign = 'left';
                ctx.fillStyle = '#ffe000';
                ctx.fillText('TOTAL', bx + 20, ty + 24);
                ctx.textAlign = 'right';
                ctx.fillText(String(this.finalScore).padStart(7, ' '), bx + bw - 20, ty + 24);

                // Prompt
                if (this.blinkOn) {
                    ctx.font      = `${FONT_SIZE_SMALL}px "${FONT_FAMILY}"`;
                    ctx.textAlign = 'center';
                    ctx.fillStyle = '#ffffff';
                    ctx.fillText('PRESS  ENTER  TO  CONTINUE', W / 2, ty + 60);
                }
            }
        }
    }

    _spawnFirework() {
        const x = 60 + Math.random() * (CANVAS_WIDTH - 120);
        const y = 30 + Math.random() * (CANVAS_HEIGHT * 0.5);
        const color = PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)];
        const particles = [];
        for (let i = 0; i < 28; i++) {
            const angle = (i / 28) * Math.PI * 2;
            const speed = 50 + Math.random() * 100;
            particles.push({
                x, y,
                vx:    Math.cos(angle) * speed,
                vy:    Math.sin(angle) * speed,
                size:  2 + Math.random() * 3,
                color,
                life:  1,
            });
        }
        this.fireworks.push({ timer: 0, particles });
    }
}


// ─── World Intro Screen ───────────────────────────────────────────────────────

class WorldIntroScreen {
    /**
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} world
     * @param {number} level
     * @param {number} lives
     */
    constructor(ctx, world = 1, level = 1, lives = 3) {
        this.ctx   = ctx;
        this.world = world;
        this.level = level;
        this.lives = lives;

        this.timer      = 0;
        this.alpha      = 0;
        this.scaleWorld = 0.5;
        this.scaleLives = 0.5;
        this.done       = false;

        // Duration before auto-advance
        this.DISPLAY_DURATION = 3.0;
    }

    get isComplete() {
        return this.done;
    }

    update(dt) {
        this.timer += dt;

        // Fade in
        this.alpha = Math.min(1, this.timer / 0.4);

        // Scale pop-in
        if (this.scaleWorld < 1) {
            this.scaleWorld = Math.min(1, this.scaleWorld + dt * 4);
        }
        if (this.timer > 0.5 && this.scaleLives < 1) {
            this.scaleLives = Math.min(1, this.scaleLives + dt * 4);
        }

        // Auto-advance
        if (this.timer >= this.DISPLAY_DURATION) {
            this.done = true;
        }
    }

    draw() {
        const { ctx } = this;
        const W = CANVAS_WIDTH;
        const H = CANVAS_HEIGHT;

        // Black background
        ctx.globalAlpha = this.alpha;
        ctx.fillStyle   = '#000000';
        ctx.fillRect(0, 0, W, H);
        ctx.globalAlpha = 1;

        const cx = W / 2;
        const cy = H / 2;

        // World label
        ctx.save();
        ctx.translate(cx, cy - 30);
        ctx.scale(this.scaleWorld, this.scaleWorld);
        ctx.font      = `bold 36px "${FONT_FAMILY}"`;
        ctx.textAlign = 'center';
        ctx.fillStyle = '#ffffff';
        ctx.fillText(`WORLD  ${this.world}-${this.level}`, 0, 0);
        ctx.restore();

        // Divider
        if (this.scaleWorld >= 1) {
            ctx.strokeStyle = '#888888';
            ctx.lineWidth   = 1;
            ctx.beginPath();
            ctx.moveTo(cx - 80, cy);
            ctx.lineTo(cx + 80, cy);
            ctx.stroke();
        }

        // Lives row
        ctx.save();
        ctx.translate(cx, cy + 40);
        ctx.scale(this.scaleLives, this.scaleLives);
        ctx.textAlign = 'center';

        // Mario icon (small)
        this._drawMiniMario(-50, -10);

        ctx.font      = `bold ${FONT_SIZE_LARGE}px "${FONT_FAMILY}"`;
        ctx.fillStyle = '#ffffff';
        ctx.fillText(`× ${this.lives}`, 10, 6);
        ctx.restore();
    }

    _drawMiniMario(x, y) {
        const { ctx } = this;
        const S = 16;
        ctx.fillStyle = COLORS.MARIO_HAT;
        ctx.fillRect(x + 2, y,     S - 4, 4);
        ctx.fillRect(x,     y + 4, S,     4);
        ctx.fillStyle = COLORS.MARIO_SKIN;
        ctx.fillRect(x + 2, y + 8, S - 4, 4);
        ctx.fillStyle = COLORS.MARIO_SHIRT;
        ctx.fillRect(x,     y + 12, S,    6);
        ctx.fillStyle = COLORS.MARIO_PANTS;
        ctx.fillRect(x,     y + 18, 6,    4);
        ctx.fillRect(x + S - 6, y + 18, 6, 4);
    }
}


// ─── Shared colour palette for particles ─────────────────────────────────────

const PARTICLE_COLORS = [
    '#ff4444', '#ff8800', '#ffee00',
    '#44ff44', '#44aaff', '#cc44ff',
    '#ffffff', '#ff88cc',
];