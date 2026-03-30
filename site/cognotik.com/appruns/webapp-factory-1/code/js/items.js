/**
 * items.js - Collectible Item Classes for Super Mario Bros Clone
 *
 * Contains implementations for all collectible items:
 * - Coin: Basic collectible worth 200 points
 * - Mushroom: Makes Mario grow to Super Mario
 * - FireFlower: Gives Mario fire-throwing ability
 * - Star: Grants temporary invincibility
 * - OneUpMushroom: Grants an extra life
 *
 * Each item has physics simulation, sprite animation,
 * and a collect() method that applies effects to the player.
 */

'use strict';

// ---------------------------------------------------------------------------
// Base Item Class
// ---------------------------------------------------------------------------

class Item {
    /**
     * @param {number} x        - World x position (pixels)
     * @param {number} y        - World y position (pixels)
     * @param {number} width    - Collision width  (pixels)
     * @param {number} height   - Collision height (pixels)
     * @param {string} type     - Item type identifier string
     */
    constructor(x, y, width, height, type) {
        // World position
        this.x = x;
        this.y = y;

        // Dimensions
        this.width  = width;
        this.height = height;

        // Type tag used by the game loop for identification
        this.type = type;

        // Physics state
        this.velX = 0;
        this.velY = 0;
        this.onGround = false;
        this.gravityEnabled = true;

        // Lifecycle flags
        this.collected  = false;   // true once the player has picked it up
        this.active     = true;    // false when the item should be removed
        this.visible    = true;    // used for blinking effects

        // Animation state
        this.animFrame      = 0;
        this.animTimer      = 0;
        this.animSpeed      = 8;   // frames between sprite changes (at 60 fps)
        this.totalFrames    = 1;

        // Collect animation / effect state
        this.collectTimer   = 0;
        this.collectDuration = 30; // frames the collect animation plays

        // Score popup (rendered by the renderer, not by this class)
        this.scorePopup     = null;

        // Sprite sheet reference (set by subclass or asset loader)
        this.sprite         = null;

        // Direction the item is moving (-1 left, 1 right)
        this.direction = 1;
    }

    // -----------------------------------------------------------------------
    // Physics helpers
    // -----------------------------------------------------------------------

    /**
     * Apply gravity and integrate velocity.
     * Called once per game-loop tick before collision resolution.
     * @param {number} dt - Delta time in seconds (typically 1/60)
     */
    applyPhysics(dt) {
        if (!this.gravityEnabled) return;

        // Gravity acceleration (pixels / s²) – matches PHYSICS constant
        const gravity = typeof PHYSICS !== 'undefined'
            ? PHYSICS.GRAVITY
            : 1400;

        this.velY += gravity * dt;

        // Terminal velocity
        const maxFall = typeof PHYSICS !== 'undefined'
            ? PHYSICS.MAX_FALL_SPEED
            : 600;
        if (this.velY > maxFall) this.velY = maxFall;

        this.x += this.velX * dt;
        this.y += this.velY * dt;
    }

    /**
     * Resolve collision with a solid tile.
     * @param {object} tile - { x, y, width, height }
     */
    resolveCollision(tile) {
        const overlapX = (this.x + this.width / 2) - (tile.x + tile.width / 2);
        const overlapY = (this.y + this.height / 2) - (tile.y + tile.height / 2);
        const halfW    = (this.width  + tile.width)  / 2;
        const halfH    = (this.height + tile.height) / 2;
        const depthX   = halfW - Math.abs(overlapX);
        const depthY   = halfH - Math.abs(overlapY);

        if (depthX <= 0 || depthY <= 0) return; // no overlap

        if (depthX < depthY) {
            // Horizontal collision → reverse direction
            if (overlapX < 0) {
                this.x -= depthX;
            } else {
                this.x += depthX;
            }
            this.velX = -this.velX;
            this.direction = -this.direction;
        } else {
            // Vertical collision
            if (overlapY < 0) {
                // Landed on top of tile
                this.y -= depthY;
                this.velY = 0;
                this.onGround = true;
            } else {
                // Hit ceiling
                this.y += depthY;
                this.velY = 0;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Animation helpers
    // -----------------------------------------------------------------------

    /**
     * Advance the animation frame counter.
     * Subclasses may override for custom animation logic.
     */
    updateAnimation() {
        this.animTimer++;
        if (this.animTimer >= this.animSpeed) {
            this.animTimer = 0;
            this.animFrame = (this.animFrame + 1) % this.totalFrames;
        }
    }

    // -----------------------------------------------------------------------
    // Core update – called every game-loop tick
    // -----------------------------------------------------------------------

    /**
     * @param {number}   dt    - Delta time in seconds
     * @param {object[]} tiles - Array of solid tile objects for collision
     */
    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        this.onGround = false;
        this.applyPhysics(dt);

        if (tiles) {
            for (const tile of tiles) {
                this.resolveCollision(tile);
            }
        }

        this.updateAnimation();
    }

    /**
     * Advance the post-collect animation (score float, fade, etc.).
     * @param {number} dt - Delta time in seconds
     */
    _updateCollectAnimation(dt) {
        this.collectTimer++;
        if (this.collectTimer >= this.collectDuration) {
            this.active = false;
        }
    }

    // -----------------------------------------------------------------------
    // Collect interface – MUST be overridden by subclasses
    // -----------------------------------------------------------------------

    /**
     * Called when the player touches this item.
     * @param {object} player - The player entity
     */
    collect(player) {
        if (this.collected) return;
        this.collected = true;
        this.velX = 0;
        this.velY = 0;
        this.gravityEnabled = false;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    /**
     * Draw the item onto the canvas context.
     * Subclasses override this for custom visuals.
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} cameraX - Horizontal camera offset
     * @param {number} cameraY - Vertical camera offset
     */
    draw(ctx, cameraX, cameraY) {
        if (!this.active || !this.visible) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            this._drawFallback(ctx, screenX, screenY);
        }
    }

    /**
     * Draw sprite from a sprite sheet.
     * Expects this.spriteFrames to be an array of { sx, sy, sw, sh } objects.
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} screenX
     * @param {number} screenY
     */
    _drawSprite(ctx, screenX, screenY) {
        if (!this.spriteFrames || this.spriteFrames.length === 0) return;
        const frame = this.spriteFrames[this.animFrame] || this.spriteFrames[0];
        ctx.drawImage(
            this.sprite,
            frame.sx, frame.sy, frame.sw, frame.sh,
            screenX,  screenY,  this.width, this.height
        );
    }

    /**
     * Fallback colored rectangle when no sprite is loaded.
     * @param {CanvasRenderingContext2D} ctx
     * @param {number} screenX
     * @param {number} screenY
     */
    _drawFallback(ctx, screenX, screenY) {
        ctx.fillStyle = this.fallbackColor || '#FF00FF';
        ctx.fillRect(screenX, screenY, this.width, this.height);
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Axis-aligned bounding-box overlap test.
     * @param {object} other - Any object with { x, y, width, height }
     * @returns {boolean}
     */
    overlaps(other) {
        return (
            this.x < other.x + other.width  &&
            this.x + this.width  > other.x  &&
            this.y < other.y + other.height &&
            this.y + this.height > other.y
        );
    }

    /**
     * Return a plain-object bounding box.
     * @returns {{ x, y, width, height }}
     */
    getBounds() {
        return { x: this.x, y: this.y, width: this.width, height: this.height };
    }
}

// ---------------------------------------------------------------------------
// Coin
// ---------------------------------------------------------------------------

/**
 * Coin – the most common collectible.
 *
 * Behaviour:
 *  - Spins in place (animated).
 *  - When collected: awards SCORE.COIN points and increments the coin counter.
 *  - If the coin counter reaches 100 the player gains an extra life and the
 *    counter resets to 0.
 *  - Block-spawn variant: pops upward out of a Question Block then falls back.
 */
class Coin extends Item {
    /**
     * @param {number}  x           - World x position
     * @param {number}  y           - World y position
     * @param {boolean} [fromBlock] - true when spawned from a hit block
     */
    constructor(x, y, fromBlock = false) {
        const size = typeof TILE_SIZE !== 'undefined' ? TILE_SIZE : 32;
        super(x, y, size * 0.75, size * 0.75, 'coin');

        this.fallbackColor  = '#FFD700';   // gold
        this.totalFrames    = 4;
        this.animSpeed      = 6;

        // Sprite sheet frames (populated when assets are loaded)
        this.spriteFrames = [
            { sx: 0,   sy: 0, sw: 16, sh: 16 },
            { sx: 16,  sy: 0, sw: 16, sh: 16 },
            { sx: 32,  sy: 0, sw: 16, sh: 16 },
            { sx: 48,  sy: 0, sw: 16, sh: 16 },
        ];

        // Block-spawn pop animation
        this.fromBlock   = fromBlock;
        this.popComplete = false;

        if (fromBlock) {
            // Coins from blocks are not affected by gravity during the pop
            this.gravityEnabled = false;
            this.velY = -300;          // initial upward velocity (px/s)
            this._popPhase = 'up';     // 'up' → 'down' → 'done'
            this._popOriginY = y;
        }

        // Score value
        this.scoreValue = typeof SCORE !== 'undefined' ? SCORE.COIN : 200;
    }

    // -----------------------------------------------------------------------

    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        if (this.fromBlock && !this.popComplete) {
            this._updatePop(dt);
        } else {
            // Normal coin just animates in place (no physics)
            this.gravityEnabled = false;
            this.updateAnimation();
        }
    }

    /**
     * Animate the block-spawn pop arc.
     * @param {number} dt
     */
    _updatePop(dt) {
        const popGravity = 900; // px/s² (lighter than normal gravity)

        this.velY += popGravity * dt;
        this.y    += this.velY  * dt;

        if (this._popPhase === 'up' && this.velY >= 0) {
            this._popPhase = 'down';
        }

        if (this._popPhase === 'down' && this.y >= this._popOriginY) {
            this.y          = this._popOriginY;
            this.popComplete = true;
            // Immediately collect (block coins disappear after the arc)
            this.active = false;
        }

        this.updateAnimation();
    }

    // -----------------------------------------------------------------------

    collect(player) {
        if (this.collected) return;
        super.collect(player);

        // Award points
        if (typeof player.addScore === 'function') {
            player.addScore(this.scoreValue);
        } else if (player.score !== undefined) {
            player.score += this.scoreValue;
        }

        // Increment coin counter
        if (player.coins !== undefined) {
            player.coins++;
            if (player.coins >= 100) {
                player.coins = 0;
                // Award extra life
                if (player.lives !== undefined) player.lives++;
                if (typeof player.onExtraLife === 'function') player.onExtraLife();
            }
        }

        // Play sound
        if (typeof AudioManager !== 'undefined' && AudioManager.play) {
            AudioManager.play('coin');
        }

        // Score popup
        this.scorePopup = { value: this.scoreValue, x: this.x, y: this.y };

        // Coins collected from blocks are already removed; others fade quickly
        this.collectDuration = 20;
    }

    // -----------------------------------------------------------------------

    draw(ctx, cameraX, cameraY) {
        if (!this.active) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            // Fallback: animated spinning ellipse
            const progress = this.animFrame / this.totalFrames; // 0..1
            const scaleX   = Math.abs(Math.cos(progress * Math.PI * 2));
            const cx       = screenX + this.width  / 2;
            const cy       = screenY + this.height / 2;
            const rx       = (this.width  / 2) * scaleX;
            const ry       = this.height / 2;

            ctx.save();
            ctx.fillStyle = '#FFD700';
            ctx.strokeStyle = '#B8860B';
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.ellipse(cx, cy, Math.max(rx, 2), ry, 0, 0, Math.PI * 2);
            ctx.fill();
            ctx.stroke();
            ctx.restore();
        }
    }
}

// ---------------------------------------------------------------------------
// Mushroom
// ---------------------------------------------------------------------------

/**
 * Mushroom – power-up that makes Small Mario grow into Super Mario.
 *
 * Behaviour:
 *  - Emerges from a Question Block (slides upward then falls).
 *  - Walks horizontally, bouncing off walls.
 *  - When collected by Small Mario  → player grows (SUPER state).
 *  - When collected by Super Mario+ → awards SCORE.MUSHROOM points only.
 */
class Mushroom extends Item {
    /**
     * @param {number} x - World x position
     * @param {number} y - World y position
     */
    constructor(x, y) {
        const size = typeof TILE_SIZE !== 'undefined' ? TILE_SIZE : 32;
        super(x, y, size, size, 'mushroom');

        this.fallbackColor = '#FF0000';   // red cap
        this.totalFrames   = 2;
        this.animSpeed     = 12;

        this.spriteFrames = [
            { sx: 0,  sy: 0, sw: 16, sh: 16 },
            { sx: 16, sy: 0, sw: 16, sh: 16 },
        ];

        // Walk speed (px/s)
        this.walkSpeed = typeof PHYSICS !== 'undefined'
            ? PHYSICS.ENEMY_WALK_SPEED
            : 80;
        this.velX = this.walkSpeed;

        // Emerge animation (slides up from block)
        this._emerging    = true;
        this._emergeY     = y;           // target y after emerging
        this._startY      = y + size;    // start one tile below
        this.y            = this._startY;
        this.gravityEnabled = false;

        this.scoreValue = typeof SCORE !== 'undefined' ? SCORE.MUSHROOM : 1000;
    }

    // -----------------------------------------------------------------------

    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        if (this._emerging) {
            this._updateEmerge(dt);
            return;
        }

        // Normal physics + walk
        this.onGround = false;
        this.gravityEnabled = true;
        this.applyPhysics(dt);

        if (tiles) {
            for (const tile of tiles) {
                this.resolveCollision(tile);
            }
        }

        // Maintain walk speed
        this.velX = this.direction * this.walkSpeed;

        this.updateAnimation();
    }

    /**
     * Slide the mushroom upward out of the block.
     * @param {number} dt
     */
    _updateEmerge(dt) {
        const emergeSpeed = 60; // px/s
        this.y -= emergeSpeed * dt;
        if (this.y <= this._emergeY) {
            this.y         = this._emergeY;
            this._emerging = false;
            this.gravityEnabled = true;
        }
    }

    // -----------------------------------------------------------------------

    collect(player) {
        if (this.collected) return;
        super.collect(player);

        const playerState = typeof PLAYER_STATE !== 'undefined'
            ? PLAYER_STATE
            : { SMALL: 0, SUPER: 1, FIRE: 2, STAR: 3 };

        if (player.state === playerState.SMALL || player.state === 0) {
            // Grow the player
            if (typeof player.grow === 'function') {
                player.grow();
            } else {
                player.state = playerState.SUPER !== undefined
                    ? playerState.SUPER
                    : 1;
            }
        }

        // Always award points
        if (typeof player.addScore === 'function') {
            player.addScore(this.scoreValue);
        } else if (player.score !== undefined) {
            player.score += this.scoreValue;
        }

        if (typeof AudioManager !== 'undefined' && AudioManager.play) {
            AudioManager.play('powerup');
        }

        this.scorePopup = { value: this.scoreValue, x: this.x, y: this.y };
    }

    // -----------------------------------------------------------------------

    draw(ctx, cameraX, cameraY) {
        if (!this.active) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            this._drawMushroomFallback(ctx, screenX, screenY);
        }
    }

    _drawMushroomFallback(ctx, sx, sy) {
        const w = this.width;
        const h = this.height;

        ctx.save();

        // Stem
        ctx.fillStyle = '#FFDEAD';
        ctx.fillRect(sx + w * 0.25, sy + h * 0.5, w * 0.5, h * 0.5);

        // Cap
        ctx.fillStyle = '#CC0000';
        ctx.beginPath();
        ctx.arc(sx + w / 2, sy + h * 0.45, w * 0.5, Math.PI, 0);
        ctx.closePath();
        ctx.fill();

        // White spots
        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(sx + w * 0.3, sy + h * 0.35, w * 0.1, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.arc(sx + w * 0.65, sy + h * 0.3, w * 0.08, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();
    }
}

// ---------------------------------------------------------------------------
// FireFlower
// ---------------------------------------------------------------------------

/**
 * FireFlower – power-up that gives Mario the ability to throw fireballs.
 *
 * Behaviour:
 *  - Emerges from a Question Block (same as Mushroom).
 *  - Stays in place (no horizontal movement), bobs up and down.
 *  - When collected by Small Mario  → player grows AND gets fire power.
 *  - When collected by Super Mario  → player gets fire power.
 *  - When collected by Fire Mario   → awards points only.
 */
class FireFlower extends Item {
    /**
     * @param {number} x - World x position
     * @param {number} y - World y position
     */
    constructor(x, y) {
        const size = typeof TILE_SIZE !== 'undefined' ? TILE_SIZE : 32;
        super(x, y, size, size, 'fireFlower');

        this.fallbackColor  = '#FF4500';   // orange-red
        this.totalFrames    = 4;
        this.animSpeed      = 8;

        // Four-frame colour cycle (red → orange → yellow → orange)
        this.spriteFrames = [
            { sx: 0,  sy: 0, sw: 16, sh: 16 },
            { sx: 16, sy: 0, sw: 16, sh: 16 },
            { sx: 32, sy: 0, sw: 16, sh: 16 },
            { sx: 48, sy: 0, sw: 16, sh: 16 },
        ];

        // Flower does not walk
        this.velX = 0;
        this.gravityEnabled = false;

        // Emerge animation
        this._emerging = true;
        this._emergeY  = y;
        this._startY   = y + size;
        this.y         = this._startY;

        // Bob animation
        this._bobOffset  = 0;
        this._bobDir     = -1;   // -1 up, 1 down
        this._bobSpeed   = 30;   // px/s
        this._bobRange   = 4;    // px

        this.scoreValue = typeof SCORE !== 'undefined' ? SCORE.FIRE_FLOWER : 1000;
    }

    // -----------------------------------------------------------------------

    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        if (this._emerging) {
            this._updateEmerge(dt);
            return;
        }

        // Bob in place
        this._bobOffset += this._bobDir * this._bobSpeed * dt;
        if (Math.abs(this._bobOffset) >= this._bobRange) {
            this._bobDir = -this._bobDir;
        }

        this.updateAnimation();
    }

    _updateEmerge(dt) {
        const emergeSpeed = 60;
        this.y -= emergeSpeed * dt;
        if (this.y <= this._emergeY) {
            this.y         = this._emergeY;
            this._emerging = false;
        }
    }

    // -----------------------------------------------------------------------

    collect(player) {
        if (this.collected) return;
        super.collect(player);

        const playerState = typeof PLAYER_STATE !== 'undefined'
            ? PLAYER_STATE
            : { SMALL: 0, SUPER: 1, FIRE: 2, STAR: 3 };

        if (player.state === playerState.SMALL || player.state === 0) {
            // Small Mario: grow first, then fire
            if (typeof player.grow === 'function') player.grow();
        }

        // Grant fire power
        if (typeof player.getFirePower === 'function') {
            player.getFirePower();
        } else {
            player.state = playerState.FIRE !== undefined ? playerState.FIRE : 2;
        }

        if (typeof player.addScore === 'function') {
            player.addScore(this.scoreValue);
        } else if (player.score !== undefined) {
            player.score += this.scoreValue;
        }

        if (typeof AudioManager !== 'undefined' && AudioManager.play) {
            AudioManager.play('powerup');
        }

        this.scorePopup = { value: this.scoreValue, x: this.x, y: this.y };
    }

    // -----------------------------------------------------------------------

    draw(ctx, cameraX, cameraY) {
        if (!this.active) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY + this._bobOffset);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            this._drawFlowerFallback(ctx, screenX, screenY);
        }
    }

    _drawFlowerFallback(ctx, sx, sy) {
        const w = this.width;
        const h = this.height;

        // Colour cycle based on animation frame
        const colours = ['#FF0000', '#FF7700', '#FFFF00', '#FF7700'];
        const petalColor = colours[this.animFrame % colours.length];

        ctx.save();

        // Stem
        ctx.strokeStyle = '#228B22';
        ctx.lineWidth   = 3;
        ctx.beginPath();
        ctx.moveTo(sx + w / 2, sy + h);
        ctx.lineTo(sx + w / 2, sy + h * 0.55);
        ctx.stroke();

        // Petals (4 petals around centre)
        ctx.fillStyle = petalColor;
        const cx = sx + w / 2;
        const cy = sy + h * 0.4;
        const pr = w * 0.18;
        const offsets = [
            [0, -pr * 1.4],
            [pr * 1.4, 0],
            [0,  pr * 1.4],
            [-pr * 1.4, 0],
        ];
        for (const [ox, oy] of offsets) {
            ctx.beginPath();
            ctx.arc(cx + ox, cy + oy, pr, 0, Math.PI * 2);
            ctx.fill();
        }

        // Centre
        ctx.fillStyle = '#FFFF00';
        ctx.beginPath();
        ctx.arc(cx, cy, pr * 0.8, 0, Math.PI * 2);
        ctx.fill();

        ctx.restore();
    }
}

// ---------------------------------------------------------------------------
// Star  (Super Star / Starman)
// ---------------------------------------------------------------------------

/**
 * Star – grants temporary invincibility.
 *
 * Behaviour:
 *  - Bounces around the level (gravity + horizontal movement).
 *  - Flashes / sparkles while active.
 *  - When collected: player enters STAR state for STAR_DURATION frames.
 *    During this time the player is invincible and kills enemies on contact.
 */
class Star extends Item {
    /**
     * @param {number} x - World x position
     * @param {number} y - World y position
     */
    constructor(x, y) {
        const size = typeof TILE_SIZE !== 'undefined' ? TILE_SIZE : 32;
        super(x, y, size, size, 'star');

        this.fallbackColor = '#FFD700';
        this.totalFrames   = 4;
        this.animSpeed     = 4;   // fast sparkle

        this.spriteFrames = [
            { sx: 0,  sy: 0, sw: 16, sh: 16 },
            { sx: 16, sy: 0, sw: 16, sh: 16 },
            { sx: 32, sy: 0, sw: 16, sh: 16 },
            { sx: 48, sy: 0, sw: 16, sh: 16 },
        ];

        // Bounce physics
        this.velX = 120;                // px/s horizontal
        this.velY = -300;              // initial upward bounce
        this._bounceVelY = -300;       // velocity applied on each ground bounce

        // Emerge animation
        this._emerging = true;
        this._emergeY  = y;
        this._startY   = y + size;
        this.y         = this._startY;
        this.gravityEnabled = false;

        // Visual sparkle
        this._sparkleTimer = 0;
        this._sparkleOn    = true;

        this.scoreValue = typeof SCORE !== 'undefined' ? SCORE.STAR : 1000;

        // How long the star power lasts (frames at 60 fps)
        this.starDuration = typeof STAR_DURATION !== 'undefined'
            ? STAR_DURATION
            : 600;
    }

    // -----------------------------------------------------------------------

    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        if (this._emerging) {
            this._updateEmerge(dt);
            return;
        }

        this.onGround = false;
        this.gravityEnabled = true;
        this.applyPhysics(dt);

        if (tiles) {
            for (const tile of tiles) {
                this._resolveStarCollision(tile);
            }
        }

        // Maintain horizontal speed
        this.velX = this.direction * Math.abs(this.velX);

        // Sparkle toggle
        this._sparkleTimer++;
        if (this._sparkleTimer >= 4) {
            this._sparkleTimer = 0;
            this._sparkleOn    = !this._sparkleOn;
        }

        this.updateAnimation();
    }

    _updateEmerge(dt) {
        const emergeSpeed = 60;
        this.y -= emergeSpeed * dt;
        if (this.y <= this._emergeY) {
            this.y         = this._emergeY;
            this._emerging = false;
            this.gravityEnabled = true;
        }
    }

    /**
     * Custom collision: bounce off ground instead of stopping.
     * @param {object} tile
     */
    _resolveStarCollision(tile) {
        const overlapX = (this.x + this.width / 2) - (tile.x + tile.width / 2);
        const overlapY = (this.y + this.height / 2) - (tile.y + tile.height / 2);
        const halfW    = (this.width  + tile.width)  / 2;
        const halfH    = (this.height + tile.height) / 2;
        const depthX   = halfW - Math.abs(overlapX);
        const depthY   = halfH - Math.abs(overlapY);

        if (depthX <= 0 || depthY <= 0) return;

        if (depthX < depthY) {
            if (overlapX < 0) { this.x -= depthX; } else { this.x += depthX; }
            this.velX     = -this.velX;
            this.direction = -this.direction;
        } else {
            if (overlapY < 0) {
                // Ground bounce
                this.y    -= depthY;
                this.velY  = this._bounceVelY;   // bounce upward
                this.onGround = true;
            } else {
                this.y    += depthY;
                this.velY  = 0;
            }
        }
    }

    // -----------------------------------------------------------------------

    collect(player) {
        if (this.collected) return;
        super.collect(player);

        // Grant star power
        if (typeof player.getStar === 'function') {
            player.getStar(this.starDuration);
        } else {
            const playerState = typeof PLAYER_STATE !== 'undefined'
                ? PLAYER_STATE
                : { STAR: 3 };
            player.state        = playerState.STAR !== undefined ? playerState.STAR : 3;
            player.starTimer    = this.starDuration;
        }

        if (typeof player.addScore === 'function') {
            player.addScore(this.scoreValue);
        } else if (player.score !== undefined) {
            player.score += this.scoreValue;
        }

        if (typeof AudioManager !== 'undefined' && AudioManager.play) {
            AudioManager.play('starman');
        }

        this.scorePopup = { value: this.scoreValue, x: this.x, y: this.y };
    }

    // -----------------------------------------------------------------------

    draw(ctx, cameraX, cameraY) {
        if (!this.active) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            this._drawStarFallback(ctx, screenX, screenY);
        }
    }

    _drawStarFallback(ctx, sx, sy) {
        const w  = this.width;
        const h  = this.height;
        const cx = sx + w / 2;
        const cy = sy + h / 2;

        // Sparkle colours
        const colours = ['#FFD700', '#FFFFFF', '#FFA500', '#FFFF00'];
        const color    = colours[this.animFrame % colours.length];

        ctx.save();
        ctx.fillStyle = color;

        // 5-pointed star path
        const outerR = w * 0.45;
        const innerR = w * 0.2;
        const points = 5;
        ctx.beginPath();
        for (let i = 0; i < points * 2; i++) {
            const angle  = (i * Math.PI) / points - Math.PI / 2;
            const radius = i % 2 === 0 ? outerR : innerR;
            const px     = cx + Math.cos(angle) * radius;
            const py     = cy + Math.sin(angle) * radius;
            if (i === 0) ctx.moveTo(px, py);
            else         ctx.lineTo(px, py);
        }
        ctx.closePath();
        ctx.fill();

        // Sparkle overlay
        if (this._sparkleOn) {
            ctx.fillStyle = 'rgba(255,255,255,0.6)';
            ctx.beginPath();
            ctx.arc(cx - w * 0.1, cy - h * 0.1, w * 0.1, 0, Math.PI * 2);
            ctx.fill();
        }

        ctx.restore();
    }
}

// ---------------------------------------------------------------------------
// OneUpMushroom
// ---------------------------------------------------------------------------

/**
 * OneUpMushroom – grants the player one extra life.
 *
 * Behaviour:
 *  - Identical movement to the red Mushroom (emerges, walks, bounces off walls).
 *  - Distinctive green colour.
 *  - When collected: player.lives++ and a "1UP" popup is shown.
 */
class OneUpMushroom extends Item {
    /**
     * @param {number} x - World x position
     * @param {number} y - World y position
     */
    constructor(x, y) {
        const size = typeof TILE_SIZE !== 'undefined' ? TILE_SIZE : 32;
        super(x, y, size, size, 'oneUpMushroom');

        this.fallbackColor = '#00CC00';   // green cap
        this.totalFrames   = 2;
        this.animSpeed     = 12;

        this.spriteFrames = [
            { sx: 0,  sy: 0, sw: 16, sh: 16 },
            { sx: 16, sy: 0, sw: 16, sh: 16 },
        ];

        // Walk speed
        this.walkSpeed = typeof PHYSICS !== 'undefined'
            ? PHYSICS.ENEMY_WALK_SPEED
            : 80;
        this.velX = this.walkSpeed;

        // Emerge animation
        this._emerging    = true;
        this._emergeY     = y;
        this._startY      = y + size;
        this.y            = this._startY;
        this.gravityEnabled = false;

        // Score value (1UP mushrooms typically show "1UP" not a number)
        this.scoreValue = typeof SCORE !== 'undefined' ? SCORE.ONE_UP : 0;
    }

    // -----------------------------------------------------------------------

    update(dt, tiles) {
        if (!this.active) return;

        if (this.collected) {
            this._updateCollectAnimation(dt);
            return;
        }

        if (this._emerging) {
            this._updateEmerge(dt);
            return;
        }

        this.onGround = false;
        this.gravityEnabled = true;
        this.applyPhysics(dt);

        if (tiles) {
            for (const tile of tiles) {
                this.resolveCollision(tile);
            }
        }

        this.velX = this.direction * this.walkSpeed;
        this.updateAnimation();
    }

    _updateEmerge(dt) {
        const emergeSpeed = 60;
        this.y -= emergeSpeed * dt;
        if (this.y <= this._emergeY) {
            this.y         = this._emergeY;
            this._emerging = false;
            this.gravityEnabled = true;
        }
    }

    // -----------------------------------------------------------------------

    collect(player) {
        if (this.collected) return;
        super.collect(player);

        // Grant extra life
        if (player.lives !== undefined) {
            player.lives++;
        }
        if (typeof player.onExtraLife === 'function') {
            player.onExtraLife();
        }

        // Score (usually 0 for 1UP, but configurable)
        if (this.scoreValue > 0) {
            if (typeof player.addScore === 'function') {
                player.addScore(this.scoreValue);
            } else if (player.score !== undefined) {
                player.score += this.scoreValue;
            }
        }

        if (typeof AudioManager !== 'undefined' && AudioManager.play) {
            AudioManager.play('oneUp');
        }

        // Special "1UP" popup instead of a number
        this.scorePopup = { value: '1UP', x: this.x, y: this.y };
    }

    // -----------------------------------------------------------------------

    draw(ctx, cameraX, cameraY) {
        if (!this.active) return;

        const screenX = Math.floor(this.x - cameraX);
        const screenY = Math.floor(this.y - cameraY);

        if (this.sprite) {
            this._drawSprite(ctx, screenX, screenY);
        } else {
            this._drawOneUpFallback(ctx, screenX, screenY);
        }
    }

    _drawOneUpFallback(ctx, sx, sy) {
        const w = this.width;
        const h = this.height;

        ctx.save();

        // Stem
        ctx.fillStyle = '#FFDEAD';
        ctx.fillRect(sx + w * 0.25, sy + h * 0.5, w * 0.5, h * 0.5);

        // Green cap
        ctx.fillStyle = '#00AA00';
        ctx.beginPath();
        ctx.arc(sx + w / 2, sy + h * 0.45, w * 0.5, Math.PI, 0);
        ctx.closePath();
        ctx.fill();

        // White spots
        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(sx + w * 0.3, sy + h * 0.35, w * 0.1, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.arc(sx + w * 0.65, sy + h * 0.3, w * 0.08, 0, Math.PI * 2);
        ctx.fill();

        // "1UP" text label
        ctx.fillStyle    = '#FFFFFF';
        ctx.font         = `bold ${Math.floor(w * 0.28)}px monospace`;
        ctx.textAlign    = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText('1UP', sx + w / 2, sy + h * 0.72);

        ctx.restore();
    }
}

// ---------------------------------------------------------------------------
// ItemFactory – convenience factory function
// ---------------------------------------------------------------------------

/**
 * Create an item by type string.
 *
 * @param {string} type - 'coin' | 'mushroom' | 'fireFlower' | 'star' | 'oneUpMushroom'
 * @param {number} x    - World x position
 * @param {number} y    - World y position
 * @param {object} [opts] - Extra options (e.g. { fromBlock: true })
 * @returns {Item|null}
 */
function createItem(type, x, y, opts = {}) {
    switch (type) {
        case 'coin':
            return new Coin(x, y, opts.fromBlock || false);
        case 'mushroom':
            return new Mushroom(x, y);
        case 'fireFlower':
            return new FireFlower(x, y);
        case 'star':
            return new Star(x, y);
        case 'oneUpMushroom':
            return new OneUpMushroom(x, y);
        default:
            console.warn(`[ItemFactory] Unknown item type: "${type}"`);
            return null;
    }
}

// ---------------------------------------------------------------------------
// Module export (supports both browser globals and CommonJS / ES modules)
// ---------------------------------------------------------------------------

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        Item,
        Coin,
        Mushroom,
        FireFlower,
        Star,
        OneUpMushroom,
        createItem,
    };
}