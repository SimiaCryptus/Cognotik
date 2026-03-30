/**
 * entities.js
 * Base Entity class and Player (Mario) class implementation
 * Handles state machine, movement, jumping, running, crouching,
 * fireballs, damage, death animation, and power-up collection
 */

// ============================================================
// BASE ENTITY CLASS
// ============================================================

class Entity {
    constructor(x, y, width, height) {
        // Position
        this.x = x;
        this.y = y;

        // Dimensions
        this.width = width;
        this.height = height;

        // Velocity
        this.vx = 0;
        this.vy = 0;

        // Physics flags
        this.onGround = false;
        this.onCeiling = false;
        this.onWallLeft = false;
        this.onWallRight = false;

        // State
        this.alive = true;
        this.active = true;
        this.visible = true;

        // Facing direction: 1 = right, -1 = left
        this.facing = 1;

        // Animation
        this.animFrame = 0;
        this.animTimer = 0;
        this.animSpeed = 0.1; // frames per ms

        // Sprite / drawing
        this.spriteX = 0;
        this.spriteY = 0;

        // Collision layer flags
        this.collidesWithTiles = true;
        this.collidesWithEntities = true;
        this.isSolid = false;

        // Generic timer
        this.invincibleTimer = 0;

        // Reference to game (set externally)
        this.game = null;
    }

    // ── Bounding box helpers ──────────────────────────────────

    get left()   { return this.x; }
    get right()  { return this.x + this.width; }
    get top()    { return this.y; }
    get bottom() { return this.y + this.height; }

    get centerX() { return this.x + this.width  / 2; }
    get centerY() { return this.y + this.height / 2; }

    // ── AABB overlap test ─────────────────────────────────────

    overlaps(other) {
        return (
            this.left   < other.right  &&
            this.right  > other.left   &&
            this.top    < other.bottom &&
            this.bottom > other.top
        );
    }

    // ── Lifecycle ─────────────────────────────────────────────

    update(dt) {
        if (!this.active) return;
        if (this.invincibleTimer > 0) this.invincibleTimer -= dt;
    }

    draw(ctx, camera) {
        if (!this.visible) return;
        // Subclasses override; base draws a magenta debug rect
        if (typeof DEBUG !== 'undefined' && DEBUG) {
            ctx.fillStyle = 'magenta';
            ctx.fillRect(
                this.x - camera.x,
                this.y - camera.y,
                this.width,
                this.height
            );
        }
    }

    // ── Damage / death ────────────────────────────────────────

    takeDamage(amount = 1) {
        // Subclasses override
    }

    die() {
        this.alive  = false;
        this.active = false;
    }

    // ── Utility ───────────────────────────────────────────────

    /**
     * Clamp a value between min and max.
     */
    static clamp(val, min, max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * Linear interpolation.
     */
    static lerp(a, b, t) {
        return a + (b - a) * t;
    }
}


// ============================================================
// PLAYER STATES
// ============================================================

const PlayerState = Object.freeze({
    IDLE:       'idle',
    WALK:       'walk',
    RUN:        'run',
    JUMP:       'jump',
    FALL:       'fall',
    CROUCH:     'crouch',
    SKID:       'skid',
    SHOOT:      'shoot',
    HURT:       'hurt',
    DEAD:       'dead',
    GROW:       'grow',       // power-up transition
    SHRINK:     'shrink',     // damage transition
    WIN:        'win',        // reached the flag
    CLIMB:      'climb',
});

// ============================================================
// POWER-UP LEVELS
// ============================================================

const PowerLevel = Object.freeze({
    SMALL:  0,
    BIG:    1,
    FIRE:   2,
    STAR:   3,   // temporary invincibility
});

// ============================================================
// PLAYER CLASS
// ============================================================

class Player extends Entity {

    constructor(x, y, game) {
        // Small Mario dimensions by default
        super(x, y, TILE_SIZE, TILE_SIZE);

        this.game = game;

        // ── Power / size ──────────────────────────────────────
        this.powerLevel = PowerLevel.SMALL;

        // ── State machine ─────────────────────────────────────
        this.state     = PlayerState.IDLE;
        this.prevState = PlayerState.IDLE;

        // ── Movement parameters ───────────────────────────────
        this.walkAccel      = PLAYER_WALK_ACCEL;
        this.runAccel       = PLAYER_RUN_ACCEL;
        this.decel          = PLAYER_DECEL;
        this.skidDecel      = PLAYER_SKID_DECEL;
        this.maxWalkSpeed   = PLAYER_MAX_WALK_SPEED;
        this.maxRunSpeed    = PLAYER_MAX_RUN_SPEED;
        this.airAccel       = PLAYER_AIR_ACCEL;
        this.airDecel       = PLAYER_AIR_DECEL;

        // ── Jump parameters ───────────────────────────────────
        this.jumpForce      = PLAYER_JUMP_FORCE;
        this.jumpHoldForce  = PLAYER_JUMP_HOLD_FORCE;  // extra upward force while holding jump
        this.jumpHoldTime   = PLAYER_JUMP_HOLD_TIME;   // max ms jump can be held
        this.jumpHoldTimer  = 0;
        this.isJumping      = false;   // true while jump button held and timer active

        // ── Timers ────────────────────────────────────────────
        this.hurtTimer          = 0;
        this.hurtDuration       = 2000;   // ms of invincibility after hurt
        this.deathTimer         = 0;
        this.deathDuration      = 3000;   // ms before respawn
        this.transitionTimer    = 0;
        this.transitionDuration = 800;    // ms for grow/shrink animation
        this.starTimer          = 0;
        this.starDuration       = 10000;  // ms of star power
        this.shootCooldown      = 0;
        this.shootCooldownMax   = 400;    // ms between fireballs
        this.skidTimer          = 0;
        this.skidDuration       = 200;    // ms of skid state

        // ── Death animation ───────────────────────────────────
        this.deathBounceVy      = -PLAYER_DEATH_BOUNCE;
        this.isDying            = false;

        // ── Flags ─────────────────────────────────────────────
        this.isCrouching        = false;
        this.isRunning          = false;
        this.isSkidding         = false;
        this.isOnGround         = false;   // alias kept for clarity
        this.wasOnGround        = false;

        // ── Score / stats ─────────────────────────────────────
        this.score  = 0;
        this.coins  = 0;
        this.lives  = 3;

        // ── Sprite animation data ─────────────────────────────
        this._initAnimations();

        // ── Fireballs pool ────────────────────────────────────
        this.fireballs = [];
        this.maxFireballs = 2;

        // ── Coyote time & jump buffer ─────────────────────────
        this.coyoteTime     = 100;   // ms
        this.coyoteTimer    = 0;
        this.jumpBufferTime = 100;   // ms
        this.jumpBufferTimer = 0;

        // ── Checkpoint ───────────────────────────────────────
        this.spawnX = x;
        this.spawnY = y;
    }

    // ─────────────────────────────────────────────────────────
    // ANIMATION SETUP
    // ─────────────────────────────────────────────────────────

    _initAnimations() {
        /**
         * Each animation entry:
         *   frames : array of [spriteSheetCol, spriteSheetRow]
         *   speed  : seconds per frame
         *   loop   : boolean
         */
        this.animations = {
            // ── Small Mario ───────────────────────────────────
            small_idle:   { frames: [[0, 0]],                       speed: 0,    loop: true  },
            small_walk:   { frames: [[1, 0], [2, 0], [3, 0]],       speed: 0.12, loop: true  },
            small_run:    { frames: [[1, 0], [2, 0], [3, 0]],       speed: 0.07, loop: true  },
            small_jump:   { frames: [[5, 0]],                       speed: 0,    loop: false },
            small_fall:   { frames: [[5, 0]],                       speed: 0,    loop: false },
            small_skid:   { frames: [[4, 0]],                       speed: 0,    loop: false },
            small_dead:   { frames: [[6, 0]],                       speed: 0,    loop: false },

            // ── Big Mario ─────────────────────────────────────
            big_idle:     { frames: [[0, 1]],                       speed: 0,    loop: true  },
            big_walk:     { frames: [[1, 1], [2, 1], [3, 1]],       speed: 0.12, loop: true  },
            big_run:      { frames: [[1, 1], [2, 1], [3, 1]],       speed: 0.07, loop: true  },
            big_jump:     { frames: [[5, 1]],                       speed: 0,    loop: false },
            big_fall:     { frames: [[5, 1]],                       speed: 0,    loop: false },
            big_skid:     { frames: [[4, 1]],                       speed: 0,    loop: false },
            big_crouch:   { frames: [[7, 1]],                       speed: 0,    loop: false },
            big_grow:     { frames: [[0, 0], [0, 1], [0, 0], [0, 1]], speed: 0.1, loop: false },
            big_shrink:   { frames: [[0, 1], [0, 0], [0, 1], [0, 0]], speed: 0.1, loop: false },

            // ── Fire Mario ────────────────────────────────────
            fire_idle:    { frames: [[0, 2]],                       speed: 0,    loop: true  },
            fire_walk:    { frames: [[1, 2], [2, 2], [3, 2]],       speed: 0.12, loop: true  },
            fire_run:     { frames: [[1, 2], [2, 2], [3, 2]],       speed: 0.07, loop: true  },
            fire_jump:    { frames: [[5, 2]],                       speed: 0,    loop: false },
            fire_fall:    { frames: [[5, 2]],                       speed: 0,    loop: false },
            fire_skid:    { frames: [[4, 2]],                       speed: 0,    loop: false },
            fire_crouch:  { frames: [[7, 2]],                       speed: 0,    loop: false },
            fire_shoot:   { frames: [[8, 2]],                       speed: 0,    loop: false },
        };

        this.currentAnim     = 'small_idle';
        this.animFrameIndex  = 0;
        this.animElapsed     = 0;
    }

    // ─────────────────────────────────────────────────────────
    // ANIMATION UPDATE
    // ─────────────────────────────────────────────────────────

    _updateAnimation(dt) {
        const prefix = this._animPrefix();
        let animKey  = this._resolveAnimKey(prefix);

        // Switch animation if changed
        if (animKey !== this.currentAnim) {
            this.currentAnim    = animKey;
            this.animFrameIndex = 0;
            this.animElapsed    = 0;
        }

        const anim = this.animations[this.currentAnim];
        if (!anim) return;

        if (anim.frames.length > 1 && anim.speed > 0) {
            this.animElapsed += dt / 1000;
            if (this.animElapsed >= anim.speed) {
                this.animElapsed = 0;
                if (anim.loop) {
                    this.animFrameIndex = (this.animFrameIndex + 1) % anim.frames.length;
                } else {
                    this.animFrameIndex = Math.min(
                        this.animFrameIndex + 1,
                        anim.frames.length - 1
                    );
                }
            }
        }

        const frame = anim.frames[this.animFrameIndex];
        this.spriteX = frame[0];
        this.spriteY = frame[1];
    }

    _animPrefix() {
        if (this.powerLevel === PowerLevel.FIRE) return 'fire';
        if (this.powerLevel >= PowerLevel.BIG)  return 'big';
        return 'small';
    }

    _resolveAnimKey(prefix) {
        switch (this.state) {
            case PlayerState.IDLE:   return `${prefix}_idle`;
            case PlayerState.WALK:   return `${prefix}_walk`;
            case PlayerState.RUN:    return `${prefix}_run`;
            case PlayerState.JUMP:   return `${prefix}_jump`;
            case PlayerState.FALL:   return `${prefix}_fall`;
            case PlayerState.SKID:   return `${prefix}_skid`;
            case PlayerState.CROUCH: return `${prefix}_crouch`;
            case PlayerState.SHOOT:  return `${prefix}_shoot`;
            case PlayerState.HURT:   return `${prefix}_idle`;   // flicker
            case PlayerState.DEAD:   return 'small_dead';
            case PlayerState.GROW:   return `big_grow`;
            case PlayerState.SHRINK: return `big_shrink`;
            default:                 return `${prefix}_idle`;
        }
    }

    // ─────────────────────────────────────────────────────────
    // SIZE HELPERS
    // ─────────────────────────────────────────────────────────

    _applySize() {
        const isBig = this.powerLevel >= PowerLevel.BIG;
        const newH  = isBig ? TILE_SIZE * 2 : TILE_SIZE;
        const newW  = TILE_SIZE;

        // Keep bottom-aligned when growing
        if (newH !== this.height) {
            this.y      += this.height - newH;
            this.height  = newH;
        }
        this.width = newW;
    }

    // ─────────────────────────────────────────────────────────
    // MAIN UPDATE
    // ─────────────────────────────────────────────────────────

    update(dt) {
        if (!this.active) return;

        super.update(dt);

        // Decrement cooldowns
        if (this.shootCooldown  > 0) this.shootCooldown  -= dt;
        if (this.starTimer      > 0) this.starTimer      -= dt;
        if (this.hurtTimer      > 0) this.hurtTimer      -= dt;

        // ── Special states ────────────────────────────────────
        if (this.state === PlayerState.DEAD) {
            this._updateDeath(dt);
            return;
        }

        if (this.state === PlayerState.GROW || this.state === PlayerState.SHRINK) {
            this._updateTransition(dt);
            return;
        }

        if (this.state === PlayerState.WIN) {
            this._updateWin(dt);
            return;
        }

        // ── Input ─────────────────────────────────────────────
        const input = this.game.input;

        // ── Coyote time ───────────────────────────────────────
        this.wasOnGround = this.isOnGround;
        this.isOnGround  = this.onGround;

        if (this.wasOnGround && !this.isOnGround) {
            this.coyoteTimer = this.coyoteTime;
        } else if (this.coyoteTimer > 0) {
            this.coyoteTimer -= dt;
        }

        // ── Jump buffer ───────────────────────────────────────
        if (input.isJustPressed('jump')) {
            this.jumpBufferTimer = this.jumpBufferTime;
        } else if (this.jumpBufferTimer > 0) {
            this.jumpBufferTimer -= dt;
        }

        // ── Horizontal movement ───────────────────────────────
        this._updateHorizontal(dt, input);

        // ── Vertical / jump ───────────────────────────────────
        this._updateVertical(dt, input);

        // ── Crouch ────────────────────────────────────────────
        this._updateCrouch(input);

        // ── Shoot ─────────────────────────────────────────────
        if (this.powerLevel === PowerLevel.FIRE) {
            this._updateShoot(input);
        }

        // ── Fireballs ─────────────────────────────────────────
        this._updateFireballs(dt);

        // ── State machine ─────────────────────────────────────
        this._updateStateMachine();

        // ── Animation ─────────────────────────────────────────
        this._updateAnimation(dt);

        // ── Hurt flicker ──────────────────────────────────────
        if (this.state === PlayerState.HURT) {
            // Flicker every 100 ms
            this.visible = Math.floor(this.hurtTimer / 100) % 2 === 0;
            if (this.hurtTimer <= 0) {
                this.visible = true;
                this.state   = PlayerState.IDLE;
            }
        }

        // ── Star flicker ──────────────────────────────────────
        if (this.starTimer > 0) {
            this.visible = Math.floor(this.starTimer / 80) % 2 === 0;
        }

        // ── Out of bounds (fell into pit) ─────────────────────
        if (this.y > this.game.level.height + TILE_SIZE * 4) {
            this._triggerDeath();
        }
    }

    // ─────────────────────────────────────────────────────────
    // HORIZONTAL MOVEMENT
    // ─────────────────────────────────────────────────────────

    _updateHorizontal(dt, input) {
        const dtSec     = dt / 1000;
        const left      = input.isHeld('left');
        const right     = input.isHeld('right');
        const running   = input.isHeld('run');
        const crouching = this.isCrouching;

        this.isRunning = running && !crouching;

        const maxSpeed  = this.isRunning ? this.maxRunSpeed : this.maxWalkSpeed;
        const accel     = this.isOnGround
            ? (this.isRunning ? this.runAccel : this.walkAccel)
            : this.airAccel;

        // Determine if skidding (moving opposite to input)
        const movingRight = this.vx > 0;
        const movingLeft  = this.vx < 0;
        this.isSkidding   = (left && movingRight) || (right && movingLeft);

        if (crouching && this.isOnGround) {
            // Decelerate to stop while crouching
            this._applyDecel(dtSec, this.decel * 2);
        } else if (left && !right) {
            this.facing = -1;
            if (this.isSkidding && this.isOnGround) {
                this.vx += this.skidDecel * dtSec;   // skid decel (positive = toward zero)
            } else {
                this.vx -= accel * dtSec;
            }
            this.vx = Math.max(this.vx, -maxSpeed);
        } else if (right && !left) {
            this.facing = 1;
            if (this.isSkidding && this.isOnGround) {
                this.vx -= this.skidDecel * dtSec;
            } else {
                this.vx += accel * dtSec;
            }
            this.vx = Math.min(this.vx, maxSpeed);
        } else {
            // No horizontal input – decelerate
            this._applyDecel(dtSec, this.isOnGround ? this.decel : this.airDecel);
        }
    }

    _applyDecel(dtSec, decelRate) {
        if (this.vx > 0) {
            this.vx = Math.max(0, this.vx - decelRate * dtSec);
        } else if (this.vx < 0) {
            this.vx = Math.min(0, this.vx + decelRate * dtSec);
        }
    }

    // ─────────────────────────────────────────────────────────
    // VERTICAL / JUMP
    // ─────────────────────────────────────────────────────────

    _updateVertical(dt, input) {
        const dtSec = dt / 1000;

        const canJump = (this.isOnGround || this.coyoteTimer > 0) && !this.isCrouching;
        const wantsJump = this.jumpBufferTimer > 0;

        // Initiate jump
        if (canJump && wantsJump) {
            this.vy              = -this.jumpForce;
            this.isJumping       = true;
            this.jumpHoldTimer   = this.jumpHoldTime;
            this.coyoteTimer     = 0;
            this.jumpBufferTimer = 0;
            this.onGround        = false;
            this.isOnGround      = false;

            if (this.game.audio) this.game.audio.play('jump');
        }

        // Variable jump height – extra upward force while holding
        if (this.isJumping && input.isHeld('jump') && this.jumpHoldTimer > 0) {
            this.vy            -= this.jumpHoldForce * dtSec;
            this.jumpHoldTimer -= dt;
        } else if (!input.isHeld('jump')) {
            this.isJumping     = false;
            this.jumpHoldTimer = 0;
        }

        // Gravity is applied by the physics system (physics.js)
        // but we cap fall speed here as a safety
        if (this.vy > MAX_FALL_SPEED) {
            this.vy = MAX_FALL_SPEED;
        }
    }

    // ─────────────────────────────────────────────────────────
    // CROUCH
    // ─────────────────────────────────────────────────────────

    _updateCrouch(input) {
        if (this.powerLevel < PowerLevel.BIG) {
            this.isCrouching = false;
            return;
        }

        const wantsCrouch = input.isHeld('down') && this.isOnGround;

        if (wantsCrouch && !this.isCrouching) {
            this.isCrouching = true;
            // Shrink hitbox: half height, keep bottom aligned
            this.y      += this.height / 2;
            this.height  = TILE_SIZE;
        } else if (!wantsCrouch && this.isCrouching) {
            // Only stand up if there's room above
            const headRoom = this._checkHeadRoom();
            if (headRoom) {
                this.isCrouching = false;
                this.y      -= TILE_SIZE;
                this.height  = TILE_SIZE * 2;
            }
        }
    }

    _checkHeadRoom() {
        // Ask the tilemap if the two tiles above the player are clear
        if (!this.game || !this.game.level) return true;
        const tm = this.game.level.tilemap;
        const tileAboveLeft  = tm.getTileAt(this.x + 2,              this.y - TILE_SIZE - 1);
        const tileAboveRight = tm.getTileAt(this.x + this.width - 2, this.y - TILE_SIZE - 1);
        return !tm.isSolid(tileAboveLeft) && !tm.isSolid(tileAboveRight);
    }

    // ─────────────────────────────────────────────────────────
    // SHOOT FIREBALL
    // ─────────────────────────────────────────────────────────

    _updateShoot(input) {
        if (input.isJustPressed('fire') && this.shootCooldown <= 0) {
            const activeFireballs = this.fireballs.filter(f => f.active).length;
            if (activeFireballs < this.maxFireballs) {
                this._spawnFireball();
                this.shootCooldown = this.shootCooldownMax;
                // Brief shoot state
                this.prevState = this.state;
                this.state     = PlayerState.SHOOT;
                setTimeout(() => {
                    if (this.state === PlayerState.SHOOT) {
                        this.state = this.prevState;
                    }
                }, 150);
            }
        }
    }

    _spawnFireball() {
        const fbX = this.facing === 1
            ? this.x + this.width
            : this.x - FIREBALL_SIZE;
        const fbY = this.y + this.height / 2 - FIREBALL_SIZE / 2;

        const fb = new Fireball(fbX, fbY, this.facing, this.game);
        this.fireballs.push(fb);

        if (this.game.audio) this.game.audio.play('fireball');
    }

    _updateFireballs(dt) {
        for (let i = this.fireballs.length - 1; i >= 0; i--) {
            const fb = this.fireballs[i];
            fb.update(dt);
            if (!fb.active) {
                this.fireballs.splice(i, 1);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // STATE MACHINE
    // ─────────────────────────────────────────────────────────

    _updateStateMachine() {
        // Don't override special states
        if (
            this.state === PlayerState.HURT   ||
            this.state === PlayerState.DEAD   ||
            this.state === PlayerState.GROW   ||
            this.state === PlayerState.SHRINK ||
            this.state === PlayerState.WIN    ||
            this.state === PlayerState.SHOOT
        ) return;

        if (!this.isOnGround) {
            this.state = this.vy < 0 ? PlayerState.JUMP : PlayerState.FALL;
        } else if (this.isCrouching) {
            this.state = PlayerState.CROUCH;
        } else if (this.isSkidding) {
            this.state = PlayerState.SKID;
        } else if (Math.abs(this.vx) > this.maxWalkSpeed * 0.9) {
            this.state = PlayerState.RUN;
        } else if (Math.abs(this.vx) > 0.5) {
            this.state = PlayerState.WALK;
        } else {
            this.state = PlayerState.IDLE;
        }
    }

    // ─────────────────────────────────────────────────────────
    // DEATH
    // ─────────────────────────────────────────────────────────

    _triggerDeath() {
        if (this.state === PlayerState.DEAD) return;

        this.state       = PlayerState.DEAD;
        this.isDying     = true;
        this.deathTimer  = this.deathDuration;
        this.vx          = 0;
        this.vy          = this.deathBounceVy;
        this.lives      -= 1;

        // Disable tile collision so Mario falls through the floor
        this.collidesWithTiles    = false;
        this.collidesWithEntities = false;

        if (this.game.audio) {
            this.game.audio.stopMusic();
            this.game.audio.play('death');
        }
    }

    _updateDeath(dt) {
        // Simple parabolic death bounce
        this.vy         += GRAVITY * (dt / 1000);
        this.y          += this.vy * (dt / 1000);
        this.deathTimer -= dt;

        if (this.deathTimer <= 0) {
            if (this.lives > 0) {
                this.game.respawnPlayer();
            } else {
                this.game.gameOver();
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // GROW / SHRINK TRANSITION
    // ─────────────────────────────────────────────────────────

    _startGrow(newLevel) {
        this._pendingPowerLevel = newLevel;
        this.state              = PlayerState.GROW;
        this.transitionTimer    = this.transitionDuration;
        this.vx                 = 0;
        // Temporarily disable entity collision
        this.collidesWithEntities = false;
    }

    _startShrink() {
        this.state           = PlayerState.SHRINK;
        this.transitionTimer = this.transitionDuration;
        this.collidesWithEntities = false;
    }

    _updateTransition(dt) {
        this.transitionTimer -= dt;

        if (this.transitionTimer <= 0) {
            this.collidesWithEntities = true;

            if (this.state === PlayerState.GROW) {
                this.powerLevel = this._pendingPowerLevel;
                this._applySize();
            } else {
                // Shrink: drop one power level
                if (this.powerLevel === PowerLevel.FIRE || this.powerLevel === PowerLevel.BIG) {
                    this.powerLevel = PowerLevel.SMALL;
                    this._applySize();
                }
            }

            this.state       = PlayerState.IDLE;
            this.hurtTimer   = this.hurtDuration;
            this.state       = PlayerState.HURT;
        }
    }

    // ─────────────────────────────────────────────────────────
    // WIN
    // ─────────────────────────────────────────────────────────

    triggerWin() {
        this.state = PlayerState.WIN;
        this.vx    = 0;
        this.vy    = 0;
        if (this.game.audio) {
            this.game.audio.stopMusic();
            this.game.audio.play('flagpole');
        }
    }

    _updateWin(dt) {
        // Slide down flagpole, then walk to castle – handled by game.js
    }

    // ─────────────────────────────────────────────────────────
    // DAMAGE / POWER-UP COLLECTION
    // ─────────────────────────────────────────────────────────

    takeDamage() {
        // Invincible during hurt, transition, star, or death
        if (
            this.hurtTimer   > 0 ||
            this.starTimer   > 0 ||
            this.state === PlayerState.HURT   ||
            this.state === PlayerState.GROW   ||
            this.state === PlayerState.SHRINK ||
            this.state === PlayerState.DEAD
        ) return;

        if (this.game.audio) this.game.audio.play('hurt');

        if (this.powerLevel === PowerLevel.SMALL) {
            this._triggerDeath();
        } else {
            this._startShrink();
        }
    }

    collectPowerUp(type) {
        switch (type) {
            case 'mushroom':
                if (this.powerLevel === PowerLevel.SMALL) {
                    this._startGrow(PowerLevel.BIG);
                    this.addScore(SCORE_MUSHROOM);
                } else {
                    this.addScore(SCORE_MUSHROOM);
                }
                if (this.game.audio) this.game.audio.play('powerup');
                break;

            case 'fireflower':
                if (this.powerLevel < PowerLevel.FIRE) {
                    this._startGrow(PowerLevel.FIRE);
                } else {
                    this.addScore(SCORE_FIREFLOWER);
                }
                if (this.game.audio) this.game.audio.play('powerup');
                break;

            case 'star':
                this.starTimer = this.starDuration;
                this.addScore(SCORE_STAR);
                if (this.game.audio) {
                    this.game.audio.stopMusic();
                    this.game.audio.play('starmusic');
                }
                break;

            case 'coin':
                this.coins += 1;
                this.addScore(SCORE_COIN);
                if (this.coins >= 100) {
                    this.coins -= 100;
                    this.lives += 1;
                    if (this.game.audio) this.game.audio.play('1up');
                }
                if (this.game.audio) this.game.audio.play('coin');
                break;

            case '1up':
                this.lives += 1;
                this.addScore(SCORE_1UP);
                if (this.game.audio) this.game.audio.play('1up');
                break;

            default:
                break;
        }
    }

    // ─────────────────────────────────────────────────────────
    // STOMP (landing on enemy)
    // ─────────────────────────────────────────────────────────

    stomp() {
        // Bounce upward after stomping an enemy
        const bounceForce = input && this.game.input.isHeld('jump')
            ? this.jumpForce * 0.9
            : this.jumpForce * 0.6;
        this.vy          = -bounceForce;
        this.isJumping   = false;
        this.onGround    = false;
        this.isOnGround  = false;
        if (this.game.audio) this.game.audio.play('stomp');
    }

    // ─────────────────────────────────────────────────────────
    // SCORE
    // ─────────────────────────────────────────────────────────

    addScore(points) {
        this.score += points;
        if (this.game && this.game.ui) {
            this.game.ui.showFloatingScore(points, this.x, this.y);
        }
    }

    // ─────────────────────────────────────────────────────────
    // RESPAWN
    // ─────────────────────────────────────────────────────────

    respawn() {
        this.x                    = this.spawnX;
        this.y                    = this.spawnY;
        this.vx                   = 0;
        this.vy                   = 0;
        this.powerLevel           = PowerLevel.SMALL;
        this.state                = PlayerState.IDLE;
        this.alive                = true;
        this.active               = true;
        this.visible              = true;
        this.isDying              = false;
        this.isCrouching          = false;
        this.isJumping            = false;
        this.hurtTimer            = 0;
        this.starTimer            = 0;
        this.shootCooldown        = 0;
        this.collidesWithTiles    = true;
        this.collidesWithEntities = true;
        this.fireballs            = [];
        this._applySize();
    }

    // ─────────────────────────────────────────────────────────
    // DRAW
    // ─────────────────────────────────────────────────────────

    draw(ctx, camera) {
        if (!this.visible) return;

        const screenX = Math.round(this.x - camera.x);
        const screenY = Math.round(this.y - camera.y);

        const spriteSheet = this.game.assets.getImage('mario');

        if (spriteSheet) {
            ctx.save();

            // Flip horizontally when facing left
            if (this.facing === -1) {
                ctx.translate(screenX + this.width, screenY);
                ctx.scale(-1, 1);
                ctx.drawImage(
                    spriteSheet,
                    this.spriteX * SPRITE_SIZE,
                    this.spriteY * SPRITE_SIZE,
                    SPRITE_SIZE,
                    SPRITE_SIZE * (this.height / TILE_SIZE),
                    0, 0,
                    this.width,
                    this.height
                );
            } else {
                ctx.drawImage(
                    spriteSheet,
                    this.spriteX * SPRITE_SIZE,
                    this.spriteY * SPRITE_SIZE,
                    SPRITE_SIZE,
                    SPRITE_SIZE * (this.height / TILE_SIZE),
                    screenX, screenY,
                    this.width,
                    this.height
                );
            }

            ctx.restore();
        } else {
            // Fallback: draw colored rectangle
            ctx.fillStyle = this.powerLevel >= PowerLevel.BIG ? '#e52521' : '#e52521';
            ctx.fillRect(screenX, screenY, this.width, this.height);
            // Hat
            ctx.fillStyle = '#e52521';
            ctx.fillRect(screenX + 2, screenY - 4, this.width - 4, 4);
        }

        // Draw fireballs
        for (const fb of this.fireballs) {
            fb.draw(ctx, camera);
        }

        // Debug hitbox
        if (typeof DEBUG !== 'undefined' && DEBUG) {
            ctx.strokeStyle = 'lime';
            ctx.lineWidth   = 1;
            ctx.strokeRect(screenX, screenY, this.width, this.height);
        }
    }

    // ─────────────────────────────────────────────────────────
    // SERIALISE (save state)
    // ─────────────────────────────────────────────────────────

    serialize() {
        return {
            x:          this.x,
            y:          this.y,
            powerLevel: this.powerLevel,
            score:      this.score,
            coins:      this.coins,
            lives:      this.lives,
        };
    }

    deserialize(data) {
        this.x          = data.x;
        this.y          = data.y;
        this.powerLevel = data.powerLevel;
        this.score      = data.score;
        this.coins      = data.coins;
        this.lives      = data.lives;
        this._applySize();
    }
}


// ============================================================
// FIREBALL CLASS
// ============================================================

class Fireball extends Entity {

    constructor(x, y, direction, game) {
        super(x, y, FIREBALL_SIZE, FIREBALL_SIZE);
        this.game      = game;
        this.direction = direction;   // 1 or -1
        this.vx        = direction * FIREBALL_SPEED;
        this.vy        = 0;
        this.bounces   = 0;
        this.maxBounces = 4;

        this.animFrameIndex = 0;
        this.animElapsed    = 0;
        this.animSpeed      = 0.08;   // seconds per frame
        this.frames         = [[0, 3], [1, 3], [2, 3], [3, 3]];  // sprite coords
    }

    update(dt) {
        if (!this.active) return;

        const dtSec = dt / 1000;

        // Gravity
        this.vy += GRAVITY * dtSec;

        // Move
        this.x += this.vx * dtSec;
        this.y += this.vy * dtSec;

        // Bounce off ground
        if (this.onGround) {
            this.vy      = -FIREBALL_BOUNCE;
            this.onGround = false;
            this.bounces++;
            if (this.bounces >= this.maxBounces) {
                this._explode();
                return;
            }
        }

        // Hit a wall
        if (this.onWallLeft || this.onWallRight) {
            this._explode();
            return;
        }

        // Off-screen horizontally
        if (this.game && this.game.camera) {
            const cam = this.game.camera;
            if (
                this.x > cam.x + cam.width  + TILE_SIZE * 2 ||
                this.x < cam.x              - TILE_SIZE * 2
            ) {
                this.active = false;
                return;
            }
        }

        // Animate
        this.animElapsed += dtSec;
        if (this.animElapsed >= this.animSpeed) {
            this.animElapsed    = 0;
            this.animFrameIndex = (this.animFrameIndex + 1) % this.frames.length;
        }

        // Check entity collisions
        this._checkEntityCollisions();
    }

    _checkEntityCollisions() {
        if (!this.game || !this.game.entities) return;
        for (const entity of this.game.entities) {
            if (entity === this || !entity.active || !entity.alive) continue;
            if (entity instanceof Player) continue;
            if (this.overlaps(entity)) {
                entity.takeDamage(1, 'fireball');
                this._explode();
                return;
            }
        }
    }

    _explode() {
        this.active = false;
        // Spawn explosion particle via game
        if (this.game && this.game.spawnExplosion) {
            this.game.spawnExplosion(this.centerX, this.centerY);
        }
        if (this.game && this.game.audio) {
            this.game.audio.play('kick');
        }
    }

    draw(ctx, camera) {
        if (!this.active) return;

        const screenX = Math.round(this.x - camera.x);
        const screenY = Math.round(this.y - camera.y);

        const sheet = this.game.assets.getImage('mario');
        if (sheet) {
            const frame = this.frames[this.animFrameIndex];
            ctx.drawImage(
                sheet,
                frame[0] * SPRITE_SIZE,
                frame[1] * SPRITE_SIZE,
                SPRITE_SIZE,
                SPRITE_SIZE,
                screenX, screenY,
                this.width,
                this.height
            );
        } else {
            // Fallback
            ctx.fillStyle = '#ff8800';
            ctx.beginPath();
            ctx.arc(
                screenX + this.width  / 2,
                screenY + this.height / 2,
                this.width / 2,
                0, Math.PI * 2
            );
            ctx.fill();
        }
    }
}


// ============================================================
// EXPORTS (module pattern – also available as globals)
// ============================================================

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { Entity, Player, Fireball, PlayerState, PowerLevel };
}