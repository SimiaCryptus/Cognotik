/**
 * particles.js - Particle effect classes
 *
 * BrickParticle  – debris when a brick is broken
 * ScoreParticle  – floating score text pop-up
 * CoinParticle   – coin sparkle from question block
 */

'use strict';

// ─── Base Particle ───────────────────────────────────────────────────────────

class Particle {
    constructor(x, y, vx, vy, life) {
        this.x       = x;
        this.y       = y;
        this.vx      = vx;
        this.vy      = vy;
        this.life    = life;
        this.maxLife = life;
        this.expired = false;
    }

    update(dt) {
        this.x    += this.vx * dt;
        this.y    += this.vy * dt;
        this.life -= dt;
        if (this.life <= 0) this.expired = true;
    }

    /** @param {CanvasRenderingContext2D} ctx */
    draw(ctx) { /* override */ }

    get alpha() {
        return Math.max(0, this.life / this.maxLife);
    }
}

// ─── Brick Particle ──────────────────────────────────────────────────────────

class BrickParticle extends Particle {
    constructor(x, y, vx, vy) {
        super(x, y, vx, vy, BRICK_PARTICLE_LIFE);
        this.size = 6 + Math.random() * 6;
        this.rotation = Math.random() * Math.PI * 2;
        this.rotSpeed = (Math.random() - 0.5) * 10;
    }

    update(dt) {
        super.update(dt);
        this.vy       += GRAVITY * dt;
        this.rotation += this.rotSpeed * dt;
    }

    draw(ctx) {
        ctx.save();
        ctx.globalAlpha = this.alpha;
        ctx.translate(this.x, this.y);
        ctx.rotate(this.rotation);
        ctx.fillStyle = COLORS.BRICK;
        ctx.fillRect(-this.size / 2, -this.size / 2, this.size, this.size);
        ctx.restore();
    }
}

// ─── Score Particle ──────────────────────────────────────────────────────────

class ScoreParticle extends Particle {
    constructor(x, y, points) {
        super(x, y, 0, -60, 1.2);
        this.text = points > 0 ? `+${points}` : '';
    }

    draw(ctx) {
        if (!this.text) return;
        ctx.save();
        ctx.globalAlpha = this.alpha;
        ctx.fillStyle   = COLORS.SCORE_POP;
        ctx.font        = 'bold 14px monospace';
        ctx.textAlign   = 'center';
        ctx.fillText(this.text, this.x, this.y);
        ctx.restore();
    }
}

// ─── Coin Particle ───────────────────────────────────────────────────────────

class CoinParticle extends Particle {
    constructor(x, y) {
        super(x, y, 0, -300, 0.6);
        this.gy = GRAVITY * 0.5;
    }

    update(dt) {
        super.update(dt);
        this.vy += this.gy * dt;
    }

    draw(ctx) {
        ctx.save();
        ctx.globalAlpha = this.alpha;
        ctx.fillStyle   = COLORS.COIN_YELLOW;
        ctx.beginPath();
        ctx.arc(this.x, this.y, 8, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }
}