// =============================================================================
// SUPER MARIO BROS CLONE - Game Constants
// =============================================================================

// -----------------------------------------------------------------------------
// Canvas & Display
// -----------------------------------------------------------------------------
const CANVAS_WIDTH = 800;
const CANVAS_HEIGHT = 600;
const CANVAS_W = CANVAS_WIDTH;
const CANVAS_H = CANVAS_HEIGHT;
const SCALE = 3;
export const CANVAS_W = CANVAS_WIDTH;
export const CANVAS_H = CANVAS_HEIGHT;
export const FRAME_RATE = 60;
export const DELTA_TIME = 1 / FRAME_RATE;

// -----------------------------------------------------------------------------
// World & Tile Configuration
// -----------------------------------------------------------------------------
export const TILE_SIZE = 32;
export const SCALE = 1;              // render scale factor (1 = no scaling, tiles are TILE_SIZE px)
export const TILES_PER_ROW = Math.ceil(CANVAS_WIDTH / TILE_SIZE);
export const TILES_PER_COL = Math.ceil(CANVAS_HEIGHT / TILE_SIZE);
export const WORLD_GRAVITY = 1800;   // pixels per second squared
export const TERMINAL_VELOCITY = 900; // max fall speed in pixels/second
export const GRAVITY = WORLD_GRAVITY / (FRAME_RATE * FRAME_RATE); // per-frame gravity
export const MAX_FALL_SPEED = TERMINAL_VELOCITY / FRAME_RATE;     // per-frame terminal velocity

// -----------------------------------------------------------------------------
// Level Layout
// -----------------------------------------------------------------------------
export const LEVEL_TILE_ROWS = 15;
export const LEVEL_TILE_COLS = 212;
export const LEVEL_WIDTH = LEVEL_TILE_COLS * TILE_SIZE;
export const LEVEL_HEIGHT = LEVEL_TILE_ROWS * TILE_SIZE;
export const HUD_HEIGHT = 48;
export const GROUND_LEVEL_ROW = 13;
export const LEVEL_TIME = 400;       // default level time limit (seconds)
export const CLEAR_DELAY = 180;      // frames before advancing after level clear

// -----------------------------------------------------------------------------
// Camera
// -----------------------------------------------------------------------------
export const CAMERA_LOOKAHEAD = 200;
export const CAMERA_LEFT_BOUND = 200;

// -----------------------------------------------------------------------------
// Player Physics
// -----------------------------------------------------------------------------
export const PLAYER_WALK_SPEED = 180;
export const PLAYER_RUN_SPEED = 300;
export const PLAYER_ACCELERATION = 600;
export const PLAYER_DECELERATION = 800;
export const PLAYER_AIR_ACCELERATION = 400;
export const PLAYER_JUMP_FORCE = -620;
export const PLAYER_JUMP_HOLD_FORCE = -200;
export const PLAYER_JUMP_HOLD_TIME = 0.25;
export const PLAYER_SMALL_WIDTH = 20;
export const PLAYER_SMALL_HEIGHT = 28;
export const PLAYER_BIG_WIDTH = 20;
export const PLAYER_BIG_HEIGHT = 56;
export const PLAYER_INVINCIBLE_DURATION = 2.0;
export const PLAYER_BLINK_INTERVAL = 0.1;
export const PLAYER_DEATH_JUMP_FORCE = -700;
// Aliases used by entities/player.js
export const PLAYER_W = PLAYER_SMALL_WIDTH;
export const PLAYER_H_SMALL = PLAYER_SMALL_HEIGHT;
export const PLAYER_H_BIG = PLAYER_BIG_HEIGHT;
export const WALK_SPEED = PLAYER_WALK_SPEED / FRAME_RATE;
export const RUN_SPEED = PLAYER_RUN_SPEED / FRAME_RATE;
export const ACCEL = PLAYER_ACCELERATION / (FRAME_RATE * FRAME_RATE);
export const DECEL = PLAYER_DECELERATION / (FRAME_RATE * FRAME_RATE);
export const JUMP_FORCE = PLAYER_JUMP_FORCE / FRAME_RATE;
export const JUMP_HOLD_FORCE = PLAYER_JUMP_HOLD_FORCE / (FRAME_RATE * FRAME_RATE);
export const JUMP_HOLD_FRAMES = Math.round(PLAYER_JUMP_HOLD_TIME * FRAME_RATE);
export const INVINCIBLE_TIME = Math.round(PLAYER_INVINCIBLE_DURATION * FRAME_RATE);
export const STAR_TIME = 600;        // frames of star power
export const DEATH_DELAY = 180;      // frames before respawn after death

// -----------------------------------------------------------------------------
// Enemy Physics & Behaviour
// -----------------------------------------------------------------------------
export const GOOMBA_WALK_SPEED = 60;
export const KOOPA_WALK_SPEED = 50;
export const KOOPA_SHELL_SPEED = 300;
export const ENEMY_GRAVITY_MULTIPLIER = 1.0;
export const ENEMY_STOMP_BOUNCE = -350;

// -----------------------------------------------------------------------------
// Projectiles
// -----------------------------------------------------------------------------
export const FIREBALL_SPEED_X = 320;
export const FIREBALL_BOUNCE_SPEED_Y = -380;
export const FIREBALL_GRAVITY = 1200;
export const FIREBALL_MAX_BOUNCES = 4;
export const FIREBALL_WIDTH = 10;
export const FIREBALL_HEIGHT = 10;

// -----------------------------------------------------------------------------
// Blocks & Items
// -----------------------------------------------------------------------------
export const BLOCK_BUMP_HEIGHT = 8;
export const BLOCK_BUMP_DURATION = 0.15;
export const COIN_COLLECT_SCORE = 200;
export const COIN_ANIMATION_FRAMES = 4;
export const COIN_ANIMATION_SPEED = 0.1;
export const POWERUP_RISE_SPEED = 60;
export const MUSHROOM_WALK_SPEED = 80;
export const STAR_BOUNCE_SPEED_Y = -500;
export const STAR_WALK_SPEED = 120;
export const STAR_DURATION = 10.0;

// -----------------------------------------------------------------------------
// Scoring
// -----------------------------------------------------------------------------
export const SCORE = Object.freeze({
    COIN:              200,
    GOOMBA_STOMP:      100,
    GOOMBA_SHELL:      100,
    KOOPA_STOMP:       100,
    KOOPA_SHELL_HIT:   200,
    MULTI_STOMP_BASE:  100,   // doubles each consecutive stomp
    BRICK_BREAK:        50,
    MUSHROOM:         1000,
    FIREFLOWER:       1000,
    STAR:             1000,
    ONE_UP:              0,   // no score, just a life
    FLAGPOLE_BASE:      500,
    FLAGPOLE_MAX:      5000,
    TIME_BONUS:          50,  // per remaining second
     // Aliases used by entity files
     GOOMBA:            100,
     SHELL_HIT:         200,
     FLOWER:           1000,
});

// -----------------------------------------------------------------------------
// Lives & Coins
// -----------------------------------------------------------------------------
export const STARTING_LIVES = 3;
export const EXTRA_LIFE_COIN_COUNT = 100;
export const TIME_LIMIT = 400;

// -----------------------------------------------------------------------------
// Animation Frame Counts
// -----------------------------------------------------------------------------
export const ANIM = Object.freeze({
    PLAYER_IDLE_FRAMES:   1,
    PLAYER_WALK_FRAMES:   3,
    PLAYER_RUN_FRAMES:    3,
    PLAYER_JUMP_FRAMES:   1,
    PLAYER_SKID_FRAMES:   1,
    PLAYER_CROUCH_FRAMES: 1,
    PLAYER_DEAD_FRAMES:   1,
    GOOMBA_WALK_FRAMES:   2,
    GOOMBA_SQUISH_FRAMES: 1,
    KOOPA_WALK_FRAMES:    2,
    KOOPA_SHELL_FRAMES:   1,
    COIN_SPIN_FRAMES:     4,
    BRICK_BREAK_FRAMES:   4,
    EXPLOSION_FRAMES:     4,
    FLAG_FRAMES:          1,
});

// -----------------------------------------------------------------------------
// Animation Speeds (seconds per frame)
// -----------------------------------------------------------------------------
export const ANIM_SPEED = Object.freeze({
    PLAYER_WALK:   0.1,
    PLAYER_RUN:    0.07,
    GOOMBA_WALK:   0.2,
    KOOPA_WALK:    0.2,
    COIN_SPIN:     0.1,
    BRICK_BREAK:   0.05,
    EXPLOSION:     0.06,
});

// -----------------------------------------------------------------------------
// Tile / Block Type IDs
// -----------------------------------------------------------------------------
export const TILE = Object.freeze({
    EMPTY:          0,
     AIR:            0,   // alias
    GROUND:         1,
    BRICK:          2,
    QUESTION:       3,
    QUESTION_USED:  4,
     USED_BLOCK:     4,   // alias
    PIPE_TOP_LEFT:  5,
     PIPE_TOP_L:     5,   // alias
    PIPE_TOP_RIGHT: 6,
     PIPE_TOP_R:     6,   // alias
    PIPE_LEFT:      7,
     PIPE_BODY_L:    7,   // alias
    PIPE_RIGHT:     8,
     PIPE_BODY_R:    8,   // alias
    SOLID:          9,   // indestructible solid (castle walls, etc.)
    COIN_BLOCK:    10,   // invisible block containing coin
     COIN:          10,   // alias (coin tile in world)
    CLOUD_LEFT:    11,
     CLOUD_L:       11,   // alias
    CLOUD_MID:     12,
     CLOUD_M:       12,   // alias
    CLOUD_RIGHT:   13,
     CLOUD_R:       13,   // alias
    HILL_LEFT:     14,
     HILL_L:        14,   // alias
    HILL_MID:      15,
     HILL_M:        15,   // alias
    HILL_RIGHT:    16,
     HILL_R:        16,   // alias
    BUSH_LEFT:     17,
     BUSH_L:        17,   // alias
    BUSH_MID:      18,
     BUSH_M:        18,   // alias
    BUSH_RIGHT:    19,
     BUSH_R:        19,   // alias
    FLAGPOLE_BASE: 20,
     FLAG_BASE:     20,   // alias
    FLAGPOLE_POLE: 21,
     FLAG_POLE:     21,   // alias
    CASTLE:        22,
    LAVA:          23,
    PLATFORM:      24,   // moving / floating platform
     COIN_WORLD:    25,   // collectible coin placed in world (distinct from COIN_BLOCK)
});

// -----------------------------------------------------------------------------
// Entity Type IDs
// -----------------------------------------------------------------------------
export const ENTITY_TYPE = Object.freeze({
    PLAYER:       'player',
    GOOMBA:       'goomba',
    KOOPA:        'koopa',
    KOOPA_SHELL:  'koopa_shell',
    PIRANHA:      'piranha',
    MUSHROOM:     'mushroom',
     SHELL:        'shell',
    FIRE_FLOWER:  'fire_flower',
     FLOWER:       'fire_flower',  // alias
    STAR:         'star',
    ONE_UP:       'one_up',
     ONEUP:        'one_up',       // alias
    FIREBALL:     'fireball',
    COIN_POPUP:   'coin_popup',
     COIN_POP:     'coin_popup',   // alias
    SCORE_POPUP:  'score_popup',
    BRICK_DEBRIS: 'brick_debris',
    FLAG:         'flag',
});

// -----------------------------------------------------------------------------
// Player States
// -----------------------------------------------------------------------------
export const PLAYER_STATE = Object.freeze({
    IDLE:     'idle',
    WALKING:  'walking',
    RUNNING:  'running',
    JUMPING:  'jumping',
    FALLING:  'falling',
    SKIDDING: 'skidding',
    CROUCHING:'crouching',
    DEAD:     'dead',
    CLIMBING: 'climbing',
    VICTORY:  'victory',
});

// -----------------------------------------------------------------------------
// Player Power-Up States
// -----------------------------------------------------------------------------
export const POWER_STATE = Object.freeze({
    SMALL:  'small',
    BIG:    'big',
    FIRE:   'fire',
    STAR:   'star',
});

// -----------------------------------------------------------------------------
// Game States (top-level FSM)
// -----------------------------------------------------------------------------
export const GAME_STATE = Object.freeze({
    BOOT:         'boot',        // initial asset loading
    TITLE:        'title',       // title screen
    WORLD_MAP:    'world_map',   // world/level select map
    LEVEL_INTRO:  'level_intro', // "WORLD X-X" splash
    PLAYING:      'playing',     // active gameplay
    PAUSED:       'paused',      // pause menu
    LEVEL_CLEAR:  'level_clear', // flagpole / end sequence
    GAME_OVER:    'game_over',   // game over screen
    VICTORY:      'victory',     // final castle / credits
});
// Alias used by game.js
export const STATE = GAME_STATE;

// -----------------------------------------------------------------------------
// Direction Constants
// -----------------------------------------------------------------------------
export const DIR = Object.freeze({
    LEFT:  -1,
    RIGHT:  1,
    NONE:   0,
    UP:    -1,
    DOWN:   1,
});

// -----------------------------------------------------------------------------
// Collision Sides
// -----------------------------------------------------------------------------
export const SIDE = Object.freeze({
    TOP:    'top',
    BOTTOM: 'bottom',
    LEFT:   'left',
    RIGHT:  'right',
    NONE:   'none',
});

// -----------------------------------------------------------------------------
// Color Palette  (CSS color strings used by the canvas renderer)
// -----------------------------------------------------------------------------
export const COLOR = Object.freeze({
    // Sky & Background
    SKY:              '#5C94FC',
    SKY_UNDERGROUND:  '#000000',
    SKY_CASTLE:       '#000000',
    HORIZON:          '#5C94FC',

    // Ground & Terrain
    GROUND_TOP:       '#E8A000',
    GROUND_FILL:      '#C84C0C',
    BRICK_FACE:       '#C84C0C',
    BRICK_MORTAR:     '#8C2800',
    QUESTION_FACE:    '#F8B800',
    QUESTION_SYMBOL:  '#FFFFFF',
    QUESTION_USED:    '#8C6914',
    PIPE_GREEN:       '#00A800',
    PIPE_DARK:        '#006400',
    SOLID_BLOCK:      '#8C6914',

    // Player
    MARIO_HAT:        '#CC0000',
    MARIO_SKIN:       '#FFA060',
    MARIO_SHIRT:      '#CC0000',
    MARIO_OVERALLS:   '#0000CC',
    MARIO_SHOES:      '#8C4800',

    // Enemies
    GOOMBA_BODY:      '#C84C0C',
    GOOMBA_FEET:      '#8C2800',
    GOOMBA_EYES:      '#000000',
    KOOPA_SHELL:      '#00A800',
    KOOPA_SKIN:       '#FFA060',

    // Items
    MUSHROOM_CAP:     '#CC0000',
    MUSHROOM_SPOT:    '#FFFFFF',
    MUSHROOM_STEM:    '#FFA060',
    FIRE_FLOWER:      '#FF6000',
    STAR_COLOR:       '#F8F800',
    COIN_COLOR:       '#F8B800',
    COIN_SHINE:       '#FFFFFF',
    FIREBALL_COLOR:   '#FF6000',
    FIREBALL_CORE:    '#FFFFFF',

    // HUD
    HUD_BG:           '#000000',
    HUD_TEXT:         '#FFFFFF',
    HUD_ACCENT:       '#F8B800',

    // UI
    UI_BG:            '#000000',
    UI_TEXT:          '#FFFFFF',
    UI_HIGHLIGHT:     '#F8B800',
    UI_SHADOW:        '#000000',

    // Effects
    SCORE_POPUP:      '#FFFFFF',
    COIN_POPUP:       '#F8B800',
    BRICK_DEBRIS:     '#C84C0C',
    EXPLOSION_OUTER:  '#FF6000',
    EXPLOSION_INNER:  '#F8F800',

    // Decorations
    CLOUD:            '#FFFFFF',
    CLOUD_SHADOW:     '#CCCCCC',
    BUSH:             '#00A800',
    HILL:             '#00A800',
    HILL_SPOT:        '#00C800',
    FLAG:             '#CC0000',
    FLAGPOLE:         '#888888',
    CASTLE_WALL:      '#8C6914',
    CASTLE_WINDOW:    '#000000',
    LAVA:             '#FF4000',
    LAVA_GLOW:        '#FF8000',
});

// -----------------------------------------------------------------------------
// Z-Index / Draw Layer Order
// -----------------------------------------------------------------------------
export const LAYER = Object.freeze({
    BACKGROUND:   0,
    DECORATION:   1,
    TILES:        2,
    ITEMS:        3,
    ENEMIES:      4,
    PLAYER:       5,
    PROJECTILES:  6,
    PARTICLES:    7,
    HUD:          8,
    UI_OVERLAY:   9,
});

// -----------------------------------------------------------------------------
// Sound Effect Keys  (mapped to AudioManager)
// -----------------------------------------------------------------------------
export const SFX = Object.freeze({
    JUMP:          'jump',
    COIN:          'coin',
    POWERUP:       'powerup',
    POWERUP_SPAWN: 'powerup_spawn',
    BREAK_BLOCK:   'break_block',
    BUMP:          'bump',
    STOMP:         'stomp',
    KICK:          'kick',
    FIREBALL:      'fireball',
    PIPE:          'pipe',
    FLAGPOLE:      'flagpole',
    STAGE_CLEAR:   'stage_clear',
    WORLD_CLEAR:   'world_clear',
    GAME_OVER:     'game_over',
    DEATH:         'death',
    ONE_UP:        'one_up',
    PAUSE:         'pause',
    LOW_TIME:      'low_time',
});

// -----------------------------------------------------------------------------
// Music Track Keys
// -----------------------------------------------------------------------------
export const MUSIC = Object.freeze({
    OVERWORLD:    'overworld',
    UNDERGROUND:  'underground',
    CASTLE:       'castle',
    STARMAN:      'starman',
    HURRY:        'hurry',
    TITLE:        'title',
    GAME_OVER:    'game_over_music',
    VICTORY:      'victory',
});

// -----------------------------------------------------------------------------
// Input Key Bindings  (KeyboardEvent.code values)
// -----------------------------------------------------------------------------
export const KEY = Object.freeze({
    LEFT:    ['ArrowLeft',  'KeyA'],
    RIGHT:   ['ArrowRight', 'KeyD'],
    UP:      ['ArrowUp',    'KeyW'],
    DOWN:    ['ArrowDown',  'KeyS'],
    JUMP:    ['Space', 'ArrowUp', 'KeyW'],
    RUN:     ['ShiftLeft', 'ShiftRight', 'KeyZ', 'KeyX'],
    FIRE:    ['ShiftLeft', 'ShiftRight', 'KeyZ', 'KeyX'],
    PAUSE:   ['Escape', 'KeyP'],
    START:   ['Enter', 'Space'],
    SELECT:  ['ShiftLeft', 'ShiftRight'],
});

// -----------------------------------------------------------------------------
// Particle Configuration
// -----------------------------------------------------------------------------
export const PARTICLE = Object.freeze({
    BRICK_DEBRIS_COUNT:  4,
    BRICK_DEBRIS_SPEED:  200,
    BRICK_DEBRIS_LIFE:   0.8,
    COIN_POPUP_SPEED:   -300,
    COIN_POPUP_LIFE:     0.6,
    SCORE_POPUP_SPEED:  -80,
    SCORE_POPUP_LIFE:    0.9,
    EXPLOSION_COUNT:     8,
    EXPLOSION_SPEED:     150,
    EXPLOSION_LIFE:      0.5,
});

// -----------------------------------------------------------------------------
// Level Intro / Transition Timing
// -----------------------------------------------------------------------------
export const TRANSITION = Object.freeze({
    INTRO_DISPLAY_TIME:  2.5,   // seconds "WORLD X-X" is shown
    LEVEL_CLEAR_DELAY:   1.0,   // seconds before score tally starts
    SCORE_TALLY_SPEED:   50,    // score points added per frame during tally
    GAME_OVER_DELAY:     3.0,   // seconds before game over screen appears
    DEATH_FREEZE_TIME:   0.5,   // seconds game freezes on player death
    DEATH_FALL_TIME:     2.0,   // seconds player falls off screen after death
    PIPE_ENTER_TIME:     1.2,   // seconds to enter/exit pipe
    FLAG_SLIDE_SPEED:    120,   // pixels/second Mario slides down flagpole
    CASTLE_WALK_TIME:    3.0,   // seconds Mario walks into castle
});

// -----------------------------------------------------------------------------
// Debug Flags  (set to true during development)
// -----------------------------------------------------------------------------
const DEBUG = Object.freeze({
    SHOW_HITBOXES:    false,
    SHOW_TILE_GRID:   false,
    SHOW_FPS:         false,
    SHOW_ENTITY_INFO: false,
    GOD_MODE:         false,
    SKIP_INTRO:       false,
});
// -----------------------------------------------------------------------------
// Aliases & Derived Constants  (used by ES module imports)
// -----------------------------------------------------------------------------
const CANVAS_W = CANVAS_WIDTH;
const CANVAS_H = CANVAS_HEIGHT;
const SCALE    = 3;
// Player dimensions (in world pixels = tile pixels × SCALE)
const PLAYER_W       = PLAYER_SMALL_WIDTH  * SCALE;
const PLAYER_H_SMALL = PLAYER_SMALL_HEIGHT * SCALE;
const PLAYER_H_BIG   = PLAYER_BIG_HEIGHT   * SCALE;
// Movement (per-frame at 60fps, converted from px/s)
const WALK_SPEED  = PLAYER_WALK_SPEED  / 60;
const RUN_SPEED   = PLAYER_RUN_SPEED   / 60;
const ACCEL       = PLAYER_ACCELERATION / 60;
const DECEL       = PLAYER_DECELERATION / 60;
// Jump (per-frame)
const JUMP_FORCE       = Math.abs(PLAYER_JUMP_FORCE)      / 60;
const JUMP_HOLD_FORCE  = Math.abs(PLAYER_JUMP_HOLD_FORCE) / 60;
const JUMP_HOLD_FRAMES = Math.round(PLAYER_JUMP_HOLD_TIME * 60);
// Physics (per-frame)
const GRAVITY       = WORLD_GRAVITY      / (60 * 60);
const MAX_FALL_SPEED = TERMINAL_VELOCITY / 60;
// Timers (in frames at 60fps)
const INVINCIBLE_TIME = Math.round(PLAYER_INVINCIBLE_DURATION * 60);
const STAR_TIME       = Math.round(STAR_DURATION * 60);
const DEATH_DELAY     = Math.round(TRANSITION.DEATH_FALL_TIME * 60);
// Game state aliases
const STATE = Object.freeze({
     TITLE:       GAME_STATE.TITLE,
     PLAYING:     GAME_STATE.PLAYING,
     PAUSED:      GAME_STATE.PAUSED,
     LEVEL_CLEAR: GAME_STATE.LEVEL_CLEAR,
     GAME_OVER:   GAME_STATE.GAME_OVER,
     VICTORY:     GAME_STATE.VICTORY,
});
// Level timing
const LEVEL_TIME  = TIME_LIMIT;
const CLEAR_DELAY = Math.round(TRANSITION.LEVEL_CLEAR_DELAY * 60);
// Solid tile set (used by Tilemap.isSolid)
const SOLID_TILES = new Set([
     TILE.GROUND,
     TILE.BRICK,
     TILE.QUESTION,
     TILE.QUESTION_USED,
     TILE.PIPE_TOP_LEFT,
     TILE.PIPE_TOP_RIGHT,
     TILE.PIPE_LEFT,
     TILE.PIPE_RIGHT,
     TILE.SOLID,
     TILE.COIN_BLOCK,
     TILE.FLAGPOLE_BASE,
     TILE.FLAGPOLE_POLE,
     TILE.CASTLE,
]);
// Entity type aliases used by ES modules
// (re-export ENTITY_TYPE members as ENTITY_TYPE.FLOWER, ENTITY_TYPE.ONEUP, etc.)
// Extend ENTITY_TYPE with additional aliases needed by powerup.js
Object.assign(ENTITY_TYPE, {
     FLOWER:   'fire_flower',
     ONEUP:    'one_up',
     COIN_POP: 'coin_popup',
});

// -----------------------------------------------------------------------------
// Solid tile set (used by Tilemap.isSolid)
// -----------------------------------------------------------------------------
export const SOLID_TILES = new Set([
     TILE.GROUND,
     TILE.BRICK,
     TILE.QUESTION,
     TILE.USED_BLOCK,
     TILE.PIPE_TOP_L,
     TILE.PIPE_TOP_R,
     TILE.PIPE_BODY_L,
     TILE.PIPE_BODY_R,
     TILE.SOLID,
     TILE.CASTLE,
]);


// -----------------------------------------------------------------------------
// Export (module-safe: works both as ES module and plain <script> include)
// -----------------------------------------------------------------------------
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
         CANVAS_WIDTH, CANVAS_HEIGHT, CANVAS_W, CANVAS_H, SCALE,
         FRAME_RATE, DELTA_TIME,
         PLAYER_W, PLAYER_H_SMALL, PLAYER_H_BIG,
         WALK_SPEED, RUN_SPEED, ACCEL, DECEL,
         JUMP_FORCE, JUMP_HOLD_FORCE, JUMP_HOLD_FRAMES,
         GRAVITY, MAX_FALL_SPEED,
         INVINCIBLE_TIME, STAR_TIME, DEATH_DELAY,
         STATE, LEVEL_TIME, CLEAR_DELAY,
         SOLID_TILES,
         CANVAS_W, CANVAS_H, SCALE,
         CANVAS_W, CANVAS_H, SCALE,
        TILE_SIZE, TILES_PER_ROW, TILES_PER_COL,
        WORLD_GRAVITY, TERMINAL_VELOCITY,
         GRAVITY, MAX_FALL_SPEED,
        LEVEL_TILE_ROWS, LEVEL_TILE_COLS, LEVEL_WIDTH, LEVEL_HEIGHT,
         HUD_HEIGHT, GROUND_LEVEL_ROW, LEVEL_TIME, CLEAR_DELAY,
        CAMERA_LOOKAHEAD, CAMERA_LEFT_BOUND,
        PLAYER_WALK_SPEED, PLAYER_RUN_SPEED, PLAYER_ACCELERATION,
        PLAYER_DECELERATION, PLAYER_AIR_ACCELERATION,
        PLAYER_JUMP_FORCE, PLAYER_JUMP_HOLD_FORCE, PLAYER_JUMP_HOLD_TIME,
        PLAYER_SMALL_WIDTH, PLAYER_SMALL_HEIGHT,
        PLAYER_BIG_WIDTH, PLAYER_BIG_HEIGHT,
        PLAYER_INVINCIBLE_DURATION, PLAYER_BLINK_INTERVAL,
        PLAYER_DEATH_JUMP_FORCE,
         PLAYER_W, PLAYER_H_SMALL, PLAYER_H_BIG,
         WALK_SPEED, RUN_SPEED, ACCEL, DECEL,
         JUMP_FORCE, JUMP_HOLD_FORCE, JUMP_HOLD_FRAMES,
         INVINCIBLE_TIME, STAR_TIME, DEATH_DELAY,
        GOOMBA_WALK_SPEED, KOOPA_WALK_SPEED, KOOPA_SHELL_SPEED,
        ENEMY_GRAVITY_MULTIPLIER, ENEMY_STOMP_BOUNCE,
        FIREBALL_SPEED_X, FIREBALL_BOUNCE_SPEED_Y, FIREBALL_GRAVITY,
        FIREBALL_MAX_BOUNCES, FIREBALL_WIDTH, FIREBALL_HEIGHT,
        BLOCK_BUMP_HEIGHT, BLOCK_BUMP_DURATION,
        COIN_COLLECT_SCORE, COIN_ANIMATION_FRAMES, COIN_ANIMATION_SPEED,
        POWERUP_RISE_SPEED, MUSHROOM_WALK_SPEED,
        STAR_BOUNCE_SPEED_Y, STAR_WALK_SPEED, STAR_DURATION,
        SCORE, STARTING_LIVES, EXTRA_LIFE_COIN_COUNT, TIME_LIMIT,
        ANIM, ANIM_SPEED,
        TILE, ENTITY_TYPE,
        PLAYER_STATE, POWER_STATE, GAME_STATE,
         DIR, SIDE, COLOR, LAYER, SFX, MUSIC, KEY,
        PARTICLE, TRANSITION, DEBUG,
    };
}
// ES module named exports
export {
     CANVAS_WIDTH, CANVAS_HEIGHT, CANVAS_W, CANVAS_H, SCALE,
     FRAME_RATE, DELTA_TIME,
     TILE_SIZE, TILES_PER_ROW, TILES_PER_COL,
     WORLD_GRAVITY, TERMINAL_VELOCITY,
     LEVEL_TILE_ROWS, LEVEL_TILE_COLS, LEVEL_WIDTH, LEVEL_HEIGHT,
     HUD_HEIGHT, GROUND_LEVEL_ROW,
     CAMERA_LOOKAHEAD, CAMERA_LEFT_BOUND,
     PLAYER_WALK_SPEED, PLAYER_RUN_SPEED, PLAYER_ACCELERATION,
     PLAYER_DECELERATION, PLAYER_AIR_ACCELERATION,
     PLAYER_JUMP_FORCE, PLAYER_JUMP_HOLD_FORCE, PLAYER_JUMP_HOLD_TIME,
     PLAYER_SMALL_WIDTH, PLAYER_SMALL_HEIGHT,
     PLAYER_BIG_WIDTH, PLAYER_BIG_HEIGHT,
     PLAYER_INVINCIBLE_DURATION, PLAYER_BLINK_INTERVAL,
     PLAYER_DEATH_JUMP_FORCE,
     PLAYER_W, PLAYER_H_SMALL, PLAYER_H_BIG,
     WALK_SPEED, RUN_SPEED, ACCEL, DECEL,
     JUMP_FORCE, JUMP_HOLD_FORCE, JUMP_HOLD_FRAMES,
     GRAVITY, MAX_FALL_SPEED,
     INVINCIBLE_TIME, STAR_TIME, DEATH_DELAY,
     STATE, LEVEL_TIME, CLEAR_DELAY,
     SOLID_TILES,
     GOOMBA_WALK_SPEED, KOOPA_WALK_SPEED, KOOPA_SHELL_SPEED,
     ENEMY_GRAVITY_MULTIPLIER, ENEMY_STOMP_BOUNCE,
     FIREBALL_SPEED_X, FIREBALL_BOUNCE_SPEED_Y, FIREBALL_GRAVITY,
     FIREBALL_MAX_BOUNCES, FIREBALL_WIDTH, FIREBALL_HEIGHT,
     BLOCK_BUMP_HEIGHT, BLOCK_BUMP_DURATION,
     COIN_COLLECT_SCORE, COIN_ANIMATION_FRAMES, COIN_ANIMATION_SPEED,
     POWERUP_RISE_SPEED, MUSHROOM_WALK_SPEED,
     STAR_BOUNCE_SPEED_Y, STAR_WALK_SPEED, STAR_DURATION,
     SCORE, STARTING_LIVES, EXTRA_LIFE_COIN_COUNT, TIME_LIMIT,
     ANIM, ANIM_SPEED,
     TILE, ENTITY_TYPE,
     PLAYER_STATE, POWER_STATE, GAME_STATE,
     DIR, SIDE, COLOR, LAYER, SFX, MUSIC, KEY,
     PARTICLE, TRANSITION, DEBUG,
};