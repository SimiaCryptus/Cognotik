# Super Mario Bros Clone — Technical Specification

**Version:** 1.0  
**Date:** 2024  
**Status:** Draft

---

## Table of Contents

1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Architecture Overview](#architecture-overview)
5. [Canvas Rendering Pipeline](#canvas-rendering-pipeline)
6. [Physics Engine Design](#physics-engine-design)
7. [Input Handling](#input-handling)
8. [State Management](#state-management)
9. [Entity Component System](#entity-component-system)
10. [Asset Management](#asset-management)
11. [Level System](#level-system)
12. [Audio System](#audio-system)
13. [Collision Detection](#collision-detection)
14. [Camera System](#camera-system)
15. [UI System](#ui-system)
16. [Performance Considerations](#performance-considerations)
17. [Module Interfaces](#module-interfaces)

---

## 1. Overview

This document defines the technical architecture and implementation details for a Super Mario Bros clone built entirely with vanilla JavaScript, HTML5 Canvas, and CSS. No external libraries or frameworks are used. The game targets modern desktop browsers and runs at a fixed logical resolution of **256×240 pixels** (the original NES resolution), scaled up to fill the browser window while maintaining aspect ratio.

---

## 2. Technology Stack

| Layer | Technology |
|---|---|
| Rendering | HTML5 Canvas 2D API |
| Logic | Vanilla ES6+ JavaScript (modules) |
| Styling | CSS3 |
| Audio | Web Audio API |
| Build | None (native ES modules via `<script type="module">`) |
| Assets | Procedurally drawn sprites (Canvas API) + optional PNG spritesheets |

### Browser Requirements
- Chrome 80+, Firefox 75+, Safari 13+, Edge 80+
- ES2020 support (optional chaining, nullish coalescing)
- Web Audio API support

---

## 3. Project Structure

```
code/
├── index.html                  # Entry point
├── README.md                   # Project documentation
├── docs/
│   ├── game-design-document.md
│   └── technical-specification.md
├── css/
│   └── style.css               # Global styles, canvas centering
├── assets/
│   ├── sprites/                # PNG spritesheets (optional)
│   ├── audio/                  # OGG/MP3 sound files (optional)
│   └── levels/                 # JSON level data files
│       ├── world-1-1.json
│       ├── world-1-2.json
│       └── world-1-3.json
└── js/
    ├── main.js                 # Entry point, bootstraps game
    ├── Game.js                 # Root game controller
    ├── constants.js            # Global constants and enums
    ├── utils.js                # Utility functions
    ├── engine/
    │   ├── GameLoop.js         # requestAnimationFrame loop
    │   ├── Renderer.js         # Canvas rendering pipeline
    │   ├── Camera.js           # Scrolling camera
    │   ├── Physics.js          # Physics engine
    │   ├── InputManager.js     # Keyboard/gamepad input
    │   ├── AudioManager.js     # Web Audio API wrapper
    │   ├── AssetManager.js     # Image/audio loading
    │   └── CollisionSystem.js  # AABB collision detection
    ├── states/
    │   ├── StateMachine.js     # Finite state machine
    │   ├── TitleState.js       # Title screen
    │   ├── GameState.js        # Main gameplay
    │   ├── PauseState.js       # Pause screen
    │   ├── GameOverState.js    # Game over screen
    │   └── WinState.js         # Level complete / win screen
    ├── entities/
    │   ├── Entity.js           # Base entity class
    │   ├── Mario.js            # Player character
    │   ├── Goomba.js           # Goomba enemy
    │   ├── KoopaTroopa.js      # Koopa Troopa enemy
    │   ├── Shell.js            # Koopa shell projectile
    │   ├── Mushroom.js         # Super Mushroom power-up
    │   ├── FireFlower.js       # Fire Flower power-up
    │   ├── Coin.js             # Collectible coin
    │   ├── Fireball.js         # Mario's fireball projectile
    │   └── Flag.js             # Goal flag pole
    ├── tiles/
    │   ├── Tile.js             # Base tile class
    │   ├── TileMap.js          # Tilemap manager
    │   ├── TileTypes.js        # Tile type definitions
    │   └── tiles/
    │       ├── BrickTile.js    # Breakable brick
    │       ├── QuestionTile.js # ? block
    │       ├── GroundTile.js   # Solid ground
    │       ├── PipeTile.js     # Warp pipe
    │       └── CoinTile.js     # Coin block
    ├── ui/
    │   ├── HUD.js              # Heads-up display (score, lives, time)
    │   ├── ScorePopup.js       # Floating score text
    │   └── Transition.js       # Screen fade transitions
    └── level/
        ├── LevelLoader.js      # Parses JSON level data
        └── LevelGenerator.js   # Procedural level helpers
```

---

## 4. Architecture Overview

### 4.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                     index.html                       │
│              <canvas id="gameCanvas">                │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                    main.js                           │
│         Initializes Game, starts GameLoop            │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                    Game.js                           │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ StateMachine│  │ AssetManager │  │AudioManager│  │
│  └──────┬──────┘  └──────────────┘  └────────────┘  │
│         │                                            │
│  ┌──────▼──────────────────────────────────────┐    │
│  │              Active State                    │    │
│  │  (TitleState / GameState / PauseState / ...) │    │
│  └──────┬──────────────────────────────────────┘    │
│         │                                            │
│  ┌──────▼──────┐  ┌──────────────┐  ┌────────────┐  │
│  │  GameLoop   │  │InputManager  │  │  Renderer  │  │
│  │  update()   │  │  pollInput() │  │  render()  │  │
│  └─────────────┘  └──────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 4.2 Game Loop Architecture

The game uses a **fixed timestep update loop** with variable rendering:

```
GameLoop.start()
  └── requestAnimationFrame(tick)
        ├── accumulate delta time
        ├── while (accumulator >= FIXED_STEP):
        │     state.update(FIXED_STEP)   // fixed physics step
        │     accumulator -= FIXED_STEP
        └── state.render(alpha)          // interpolated render
```

- **Fixed step:** `1/60` seconds (16.67ms)
- **Max frame skip:** 5 frames (prevents spiral of death)
- **Alpha interpolation:** Smooth rendering between physics steps

---

## 5. Canvas Rendering Pipeline

### 5.1 Canvas Setup

```
Physical Canvas (CSS pixels, fills window)
    └── Logical Canvas (256×240 NES pixels)
          └── Scale transform applied each frame
```

Two canvas elements are used:
1. **Main canvas** — displayed to user, CSS-scaled
2. **Off-screen buffer** — rendered at 256×240, then blitted to main canvas

### 5.2 Render Layers (back to front)

| Layer | Z-Order | Contents |
|---|---|---|
| Sky/Background | 0 | Solid color fill |
| Background Scenery | 1 | Hills, clouds, bushes (static) |
| Tilemap Back | 2 | Background tiles |
| Tilemap Front | 3 | Foreground solid tiles |
| Entities (back) | 4 | Coins, power-ups |
| Entities (mid) | 5 | Enemies |
| Player | 6 | Mario |
| Entities (front) | 7 | Fireballs, shells |
| Particles | 8 | Brick debris, coin sparkles |
| HUD | 9 | Score, lives, timer (fixed position) |
| Transitions | 10 | Fade overlays |

### 5.3 Renderer Class Interface

```javascript
class Renderer {
  constructor(canvas)
  
  // Core
  clear()
  present()                          // blit offscreen → main canvas
  
  // Transforms
  save()
  restore()
  applyCamera(camera)
  
  // Primitives
  drawRect(x, y, w, h, color)
  drawSprite(sprite, sx, sy, sw, sh, dx, dy, dw, dh)
  drawText(text, x, y, options)
  
  // Composite
  drawTile(tileType, x, y, animFrame)
  drawEntity(entity, animFrame)
  drawHUD(gameState)
  drawBackground(cameraX)
  
  // Effects
  drawParticles(particles)
  drawScorePopups(popups)
  fadeOverlay(alpha, color)
}
```

### 5.4 Sprite Sheet Layout

All sprites are drawn procedurally using Canvas 2D primitives (rectangles, arcs, paths) to avoid asset dependencies. Each entity has a `draw(ctx, x, y, state, frame)` method.

Optional PNG spritesheet support: 16×16 pixel tiles on a 256-wide sheet.

---

## 6. Physics Engine Design

### 6.1 Units

- **1 tile = 16 pixels** (logical pixels at 256×240 resolution)
- **Velocity:** pixels per second
- **Acceleration:** pixels per second²

### 6.2 Physics Constants

```javascript
const PHYSICS = {
  GRAVITY:              1400,   // px/s²
  TERMINAL_VELOCITY:    600,    // px/s (max fall speed)
  
  // Mario movement
  WALK_SPEED:           120,    // px/s
  RUN_SPEED:            200,    // px/s
  WALK_ACCELERATION:    600,    // px/s²
  RUN_ACCELERATION:     900,    // px/s²
  DECELERATION:         800,    // px/s² (ground friction)
  AIR_DECELERATION:     400,    // px/s² (air resistance)
  
  // Jumping
  JUMP_VELOCITY:       -380,    // px/s (initial jump impulse)
  JUMP_HOLD_GRAVITY:    700,    // px/s² (reduced gravity while holding jump)
  JUMP_RELEASE_GRAVITY: 2000,   // px/s² (fast fall when jump released)
  
  // Enemies
  GOOMBA_SPEED:          60,    // px/s
  KOOPA_SPEED:           80,    // px/s
  SHELL_SPEED:           240,   // px/s
  
  // Projectiles
  FIREBALL_SPEED_X:      240,   // px/s
  FIREBALL_BOUNCE_VY:   -300,   // px/s
};
```

### 6.3 Physics Update Loop

Each entity's physics is updated as follows:

```
1. Apply horizontal input acceleration
2. Apply friction/deceleration if no input
3. Apply gravity to vertical velocity
4. Clamp velocities to terminal values
5. Integrate position: pos += vel * dt
6. Resolve collisions (see §13)
7. Update grounded state
```

### 6.4 Jump Mechanics

Mario's jump uses **variable-height jumping**:

```
if (jumpPressed && grounded):
    vy = JUMP_VELOCITY
    jumping = true

if (jumping):
    if (jumpHeld):
        gravity = JUMP_HOLD_GRAVITY
    else:
        gravity = JUMP_RELEASE_GRAVITY
        jumping = false

if (vy >= 0):
    jumping = false
    gravity = GRAVITY
```

### 6.5 Entity Physics Interface

```javascript
class Entity {
  x, y          // position (top-left corner)
  vx, vy        // velocity
  width, height // hitbox dimensions
  grounded      // boolean
  
  applyGravity(dt)
  applyFriction(dt)
  integrate(dt)
}
```

---

## 7. Input Handling

### 7.1 InputManager

Maintains a map of key states with three states per key:
- `pressed` — true only on the frame the key was first pressed
- `held` — true while key is held down
- `released` — true only on the frame the key was released

```javascript
class InputManager {
  constructor()
  
  // Called each frame before update
  poll()
  
  // Query methods
  isPressed(action)   // true on first frame
  isHeld(action)      // true while held
  isReleased(action)  // true on release frame
  
  // Action bindings
  bindKey(keyCode, action)
}
```

### 7.2 Default Key Bindings

| Action | Primary Key | Secondary Key |
|---|---|---|
| MOVE_LEFT | ArrowLeft | KeyA |
| MOVE_RIGHT | ArrowRight | KeyD |
| JUMP | Space | ArrowUp / KeyW |
| RUN | ShiftLeft | KeyZ |
| FIRE | KeyX | — |
| PAUSE | Escape | KeyP |
| CONFIRM | Enter | Space |
| BACK | Escape | — |

### 7.3 Gamepad Support (Optional)

```javascript
// Gamepad API polling (no events, must poll each frame)
const gamepads = navigator.getGamepads();
// Map standard gamepad buttons to actions
// Button 0 = A (jump), Button 1 = B (run/fire)
// Axes 0 = left stick X (move)
```

### 7.4 Input Buffer

A **6-frame input buffer** is maintained for jump inputs to allow "early" jump presses to register when Mario lands.

---

## 8. State Management

### 8.1 StateMachine

```javascript
class StateMachine {
  constructor(states)       // { name: StateClass }
  
  change(stateName, data)   // transition to new state
  update(dt)                // delegate to current state
  render(renderer)          // delegate to current state
  
  get current()             // returns active state name
}
```

### 8.2 State Interface

All states implement:

```javascript
class BaseState {
  onEnter(data)             // called when state becomes active
  onExit()                  // called when state is leaving
  update(dt)                // game logic update
  render(renderer)          // draw this state
}
```

### 8.3 State Transition Diagram

```
                    ┌─────────────┐
              ┌────►│  TitleState │◄────┐
              │     └──────┬──────┘     │
              │            │ CONFIRM    │
              │     ┌──────▼──────┐     │
              │     │  GameState  │     │
              │     └──┬──────┬───┘     │
              │        │      │         │
              │   PAUSE│      │GAME     │
              │        │      │OVER     │
              │  ┌─────▼──┐  ┌▼──────┐  │
              │  │Pause   │  │GameOver│  │
              │  │State   │  │State  ├──┘
              │  └─────┬──┘  └───────┘
              │  RESUME│
              │        │
              │  ┌─────▼──┐
              └──┤WinState │
                 └─────────┘
```

### 8.4 Game State Data

The `GameState` maintains:

```javascript
{
  world: 1,
  level: 1,
  score: 0,
  lives: 3,
  coins: 0,
  time: 400,
  mario: MarioEntity,
  tilemap: TileMap,
  entities: Entity[],
  camera: Camera,
  particles: Particle[],
  scorePopups: ScorePopup[]
}
```

---

## 9. Entity Component System

### 9.1 Base Entity

```javascript
class Entity {
  constructor(x, y, width, height)
  
  // Lifecycle
  update(dt, gameState)
  render(renderer, camera)
  onCollision(other, side)    // 'top'|'bottom'|'left'|'right'
  destroy()
  
  // State
  get isAlive()
  get bounds()                // { x, y, w, h }
  
  // Physics (mixed in)
  x, y, vx, vy
  grounded
}
```

### 9.2 Entity Types and Properties

#### Mario

```javascript
class Mario extends Entity {
  // States: 'small' | 'super' | 'fire'
  powerState: string
  
  // Sub-states
  isRunning: boolean
  isJumping: boolean
  isCrouching: boolean
  isInvincible: boolean       // after taking damage
  invincibleTimer: number
  isDead: boolean
  
  // Actions
  jump()
  run(direction)
  fire()
  grow()                      // small → super
  powerUp()                   // super → fire
  takeDamage()                // fire → super → small → dead
  
  // Hitbox sizes
  // Small Mario: 12×16
  // Super Mario: 12×24 (2 tiles tall)
}
```

#### Goomba

```javascript
class Goomba extends Entity {
  // States: 'walking' | 'squished' | 'dead'
  state: string
  direction: -1 | 1
  squishTimer: number         // time before disappearing after stomp
  
  onStomped()                 // called when Mario lands on top
  onShellHit()                // killed by shell
}
```

#### KoopaTroopa

```javascript
class KoopaTroopa extends Entity {
  // States: 'walking' | 'shell' | 'dead'
  state: string
  direction: -1 | 1
  shellKickTimer: number
  
  onStomped()                 // becomes shell
  onShellHit()
}
```

#### Shell

```javascript
class Shell extends Entity {
  moving: boolean
  direction: -1 | 1
  
  kick(direction)
}
```

### 9.3 Entity Manager

```javascript
class EntityManager {
  entities: Entity[]
  
  add(entity)
  remove(entity)
  update(dt, gameState)
  render(renderer, camera)
  
  getByType(EntityClass)
  getInRange(x, y, radius)
  
  // Cleanup dead entities each frame
  purge()
}
```

---

## 10. Asset Management

### 10.1 AssetManager

```javascript
class AssetManager {
  // Loading
  async loadImage(key, url)
  async loadAudio(key, url)
  async loadJSON(key, url)
  async loadAll(manifest)     // load all assets from manifest object
  
  // Retrieval
  getImage(key)               // returns HTMLImageElement
  getAudio(key)               // returns AudioBuffer
  getJSON(key)                // returns parsed object
  
  // State
  get loadProgress()          // 0.0 – 1.0
  get isLoaded()              // boolean
}
```

### 10.2 Asset Manifest

```javascript
const ASSET_MANIFEST = {
  images: {
    // 'spritesheet': 'assets/sprites/mario-sprites.png',
    // All sprites are procedurally drawn; no images required
  },
  audio: {
    // 'jump':    'assets/audio/jump.ogg',
    // 'coin':    'assets/audio/coin.ogg',
    // All audio is synthesized via Web Audio API
  },
  levels: {
    'world-1-1': 'assets/levels/world-1-1.json',
    'world-1-2': 'assets/levels/world-1-2.json',
    'world-1-3': 'assets/levels/world-1-3.json',
  }
};
```

### 10.3 Procedural Sprite Rendering

Since no external assets are required, all sprites are drawn procedurally:

```javascript
// Example: Mario sprite drawing
function drawMarioSmall(ctx, x, y, direction, animFrame) {
  ctx.save();
  if (direction === -1) {
    ctx.scale(-1, 1);
    x = -x - 12;
  }
  // Hat
  ctx.fillStyle = '#E52521';
  ctx.fillRect(x+2, y, 8, 3);
  // Face
  ctx.fillStyle = '#FFBD88';
  ctx.fillRect(x+1, y+3, 10, 5);
  // ... etc
  ctx.restore();
}
```

---

## 11. Level System

### 11.1 Level JSON Format

```json
{
  "meta": {
    "world": 1,
    "level": 1,
    "name": "World 1-1",
    "timeLimit": 400,
    "music": "overworld",
    "background": "day"
  },
  "dimensions": {
    "widthTiles": 212,
    "heightTiles": 15
  },
  "tilemap": [
    { "x": 0, "y": 13, "w": 212, "h": 2, "type": "ground" },
    { "x": 16, "y": 10, "type": "question", "contents": "coin" },
    { "x": 20, "y": 10, "type": "question", "contents": "mushroom" },
    { "x": 22, "y": 10, "type": "brick" },
    { "x": 28, "y": 8, "w": 2, "h": 4, "type": "pipe", "height": 2 }
  ],
  "entities": [
    { "type": "goomba", "x": 176, "y": 208 },
    { "type": "goomba", "x": 224, "y": 208 },
    { "type": "koopa",  "x": 400, "y": 208 }
  ],
  "spawnPoint": { "x": 48, "y": 192 },
  "flagPole": { "x": 3296, "y": 16 },
  "pipes": [
    { "x": 448, "y": 160, "destination": null },
    { "x": 896, "y": 144, "destination": null }
  ]
}
```

### 11.2 TileMap

```javascript
class TileMap {
  constructor(levelData)
  
  // Tile access
  getTile(tileX, tileY)           // returns Tile | null
  setTile(tileX, tileY, type)
  
  // Collision queries
  getSolidTilesInRect(rect)       // returns Tile[]
  
  // Rendering
  render(renderer, camera)
  
  // Tile dimensions
  static TILE_SIZE = 16           // pixels
  
  // Coordinate conversion
  static worldToTile(worldX)      // → tileX
  static tileToWorld(tileX)       // → worldX
}
```

### 11.3 Tile Types

```javascript
const TILE_TYPES = {
  EMPTY:    0,
  GROUND:   1,   // solid, indestructible
  BRICK:    2,   // solid, breakable (Super Mario only)
  QUESTION: 3,   // solid, contains item, becomes empty block
  EMPTY_BLOCK: 4,// solid, indestructible (used-up ? block)
  PIPE_TOP_L: 5, // pipe top-left
  PIPE_TOP_R: 6, // pipe top-right
  PIPE_BODY_L: 7,// pipe body-left
  PIPE_BODY_R: 8,// pipe body-right
  COIN:     9,   // collectible (not solid)
  PLATFORM: 10,  // one-way platform (solid from top only)
  HIDDEN_BLOCK: 11, // invisible until hit from below
  CLOUD_PLATFORM: 12,
  CASTLE_BRICK: 13,
  FLAGPOLE: 14,
};
```

---

## 12. Audio System

### 12.1 AudioManager

All audio is synthesized using the Web Audio API — no audio files required.

```javascript
class AudioManager {
  constructor()
  
  // Playback
  play(soundName)
  playMusic(trackName)
  stopMusic()
  pauseMusic()
  resumeMusic()
  
  // Settings
  setMasterVolume(0.0–1.0)
  setSFXVolume(0.0–1.0)
  setMusicVolume(0.0–1.0)
  
  // Mute
  mute()
  unmute()
}
```

### 12.2 Sound Effects (Synthesized)

| Sound | Synthesis Method |
|---|---|
| Jump | Short sine sweep 200→600Hz, 0.1s |
| Coin | Sine 800Hz + 1200Hz, 0.15s |
| Stomp | Noise burst + low sine, 0.1s |
| Brick break | Noise burst, 0.2s |
| Power-up | Ascending arpeggio, 0.5s |
| Death | Descending chromatic, 1.0s |
| Flagpole | Ascending scale, 1.5s |
| Fireball | Short noise burst, 0.05s |
| 1-Up | Ascending major 6th, 0.5s |

### 12.3 Music (Synthesized)

Background music is generated using the Web Audio API with oscillators playing the iconic Mario theme melody using a square wave approximation.

---

## 13. Collision Detection

### 13.1 Broad Phase

Spatial partitioning using a simple **grid-based broad phase**:
- Grid cell size: 64×64 pixels
- Only check collisions between entities in the same or adjacent cells
- Tilemap collision uses direct tile lookup (O(1))

### 13.2 Narrow Phase — AABB

All entities use **Axis-Aligned Bounding Box (AABB)** collision:

```javascript
function aabbOverlap(a, b) {
  return a.x < b.x + b.w &&
         a.x + a.w > b.x &&
         a.y < b.y + b.h &&
         a.y + a.h > b.y;
}
```

### 13.3 Swept AABB (Tilemap)

For tilemap collision, **swept AABB** is used to prevent tunneling at high speeds:

```
1. Compute movement vector (dx, dy) for this frame
2. Sweep along X axis:
   a. Move entity by dx
   b. Check all overlapping tiles
   c. Resolve X penetration
3. Sweep along Y axis:
   a. Move entity by dy
   b. Check all overlapping tiles
   c. Resolve Y penetration
   d. Set grounded = true if resolved downward
```

### 13.4 Collision Resolution

```javascript
function resolveEntityTile(entity, tile) {
  const overlap = getOverlap(entity.bounds, tile.bounds);
  
  if (Math.abs(overlap.x) < Math.abs(overlap.y)) {
    // Horizontal resolution
    entity.x += overlap.x;
    entity.vx = 0;
  } else {
    // Vertical resolution
    entity.y += overlap.y;
    if (overlap.y < 0) {
      entity.vy = 0;
      entity.grounded = true;
    } else {
      entity.vy = Math.max(0, entity.vy); // bounce off ceiling
      tile.onHitFromBelow(entity);
    }
  }
}
```

### 13.5 Entity-Entity Collision

```javascript
// Mario vs Enemy
if (mario.vy > 0 && marioBottom < enemyCenter) {
  // Mario stomped enemy
  enemy.onStomped();
  mario.vy = STOMP_BOUNCE;
} else {
  // Enemy hit Mario
  mario.takeDamage();
}
```

---

## 14. Camera System

### 14.1 Camera Class

```javascript
class Camera {
  x, y          // top-left world position
  width, height // viewport size (256×240)
  
  // Follow target with constraints
  follow(target, levelWidth, levelHeight)
  
  // Convert world → screen coordinates
  worldToScreen(worldX, worldY)
  
  // Culling
  isVisible(entity)
  isRectVisible(x, y, w, h)
}
```

### 14.2 Camera Behavior

- **Horizontal:** Camera follows Mario with a **right-biased dead zone**
  - Dead zone: 80px from left edge, 176px from right edge
  - Camera never scrolls left (one-way scroll like original)
  - Clamped to level bounds
- **Vertical:** Fixed at ground level (no vertical scrolling in overworld)
  - Underground levels: vertical scroll enabled

### 14.3 Parallax Background

Background elements scroll at different rates:
- Clouds: `scrollX = cameraX * 0.3`
- Hills: `scrollX = cameraX * 0.5`
- Bushes: `scrollX = cameraX * 0.7`

---

## 15. UI System

### 15.1 HUD Layout (256×240)

```
┌────────────────────────────────────────────────────────────────┐
│ MARIO          WORLD          TIME                             │
│ 000000          1-1           388                              │
│ ♥♥♥                                                           │
│ ○ 00                                                           │
└────────────────────────────────────────────────────────────────┘
```

### 15.2 HUD Data

```javascript
class HUD {
  render(ctx, gameData) {
    // Score (6 digits, zero-padded)
    // World indicator (X-X)
    // Time (3 digits, counts down)
    // Lives (heart icons)
    // Coin count
    // Power-up indicator
  }
}
```

### 15.3 Score Popups

Floating score text appears when Mario earns points:

```javascript
class ScorePopup {
  x, y
  value: number     // 100, 200, 400, 800, 1000, 2000, 4000, 8000
  lifetime: number  // 1.0 seconds
  vy: number        // -60 px/s (floats upward)
}
```

### 15.4 Transitions

```javascript
class Transition {
  // Fade to black
  fadeOut(duration, callback)
  
  // Fade from black
  fadeIn(duration, callback)
  
  // Iris wipe (circular)
  irisOut(cx, cy, duration, callback)
  irisIn(cx, cy, duration, callback)
}
```

---

## 16. Performance Considerations

### 16.1 Rendering Optimizations

- **Dirty rectangle tracking:** Only redraw changed regions (optional)
- **Entity culling:** Skip rendering entities outside camera view
- **Tile culling:** Only render tiles within camera viewport + 1 tile margin
- **Sprite caching:** Pre-render static sprites to off-screen canvases
- **Avoid layout thrashing:** Never read DOM properties during render

### 16.2 Update Optimizations

- **Spatial hashing:** O(1) broad-phase collision lookup
- **Entity pooling:** Reuse particle and score popup objects
- **Sleep system:** Entities far off-screen skip physics updates

### 16.3 Memory Management

- **Object pooling** for frequently created/destroyed objects:
  - Particles (max 200 active)
  - Score popups (max 20 active)
  - Fireballs (max 2 active)
- **Level streaming:** Only keep current level in memory

### 16.4 Target Performance

| Metric | Target |
|---|---|
| Frame rate | 60 FPS |
| Update time | < 4ms per frame |
| Render time | < 8ms per frame |
| Memory usage | < 50MB |
| Load time | < 2s (no external assets) |

---

## 17. Module Interfaces

### 17.1 Game Bootstrap Sequence

```javascript
// main.js
async function bootstrap() {
  const canvas = document.getElementById('gameCanvas');
  const game = new Game(canvas);
  
  await game.init();        // load assets, setup audio context
  game.start();             // begin game loop
}

window.addEventListener('load', bootstrap);
```

### 17.2 Game Class Interface

```javascript
class Game {
  constructor(canvas)
  
  async init()
  start()
  stop()
  
  // Sub-systems (accessible to states)
  renderer: Renderer
  input: InputManager
  audio: AudioManager
  assets: AssetManager
  states: StateMachine
  
  // Shared game data
  saveData: {
    highScore: number,
    unlockedWorlds: number[]
  }
}
```

### 17.3 Inter-Module Communication

Modules communicate via:
1. **Direct method calls** — for synchronous, tightly coupled systems
2. **Event emitter** — for loose coupling (score updates, entity deaths)
3. **Shared game state object** — passed to `update()` and `render()` calls

```javascript
class EventEmitter {
  on(event, handler)
  off(event, handler)
  emit(event, data)
}
```

### 17.4 Constants Module

```javascript
// constants.js
export const TILE_SIZE = 16;
export const SCREEN_WIDTH = 256;
export const SCREEN_HEIGHT = 240;
export const FIXED_STEP = 1 / 60;
export const MAX_FRAME_SKIP = 5;

export const SCORE_VALUES = {
  COIN: 200,
  GOOMBA_STOMP: 100,
  KOOPA_STOMP: 100,
  SHELL_HIT: 200,
  BRICK_BREAK: 50,
  FLAGPOLE: 5000,
  POWER_UP: 1000,
};

export const COLORS = {
  SKY_DAY: '#5C94FC',
  SKY_UNDERGROUND: '#000000',
  GROUND: '#C84C0C',
  BRICK: '#C84C0C',
  QUESTION_BLOCK: '#E8A000',
  MARIO_RED: '#E52521',
  MARIO_SKIN: '#FFBD88',
  GOOMBA_BROWN: '#8B4513',
  PIPE_GREEN: '#00A800',
  COIN_YELLOW: '#FFD700',
  TEXT_WHITE: '#FFFFFF',
  TEXT_SHADOW: '#000000',
};
```

---

## Appendix A: Scoring System

| Event | Points |
|---|---|
| Coin collected | 200 |
| Goomba stomped | 100 |
| Koopa stomped | 100 |
| Shell hit (1st) | 200 |
| Shell hit (2nd) | 400 |
| Shell hit (3rd) | 800 |
| Shell hit (4th) | 1000 |
| Shell hit (5th) | 2000 |
| Shell hit (6th) | 4000 |
| Shell hit (7th) | 8000 |
| Brick broken | 50 |
| Power-up collected | 1000 |
| Flagpole (top) | 5000 |
| Flagpole (mid) | 2000 |
| Flagpole (low) | 1000 |
| Time bonus | remaining time × 50 |
| 1-Up mushroom | extra life |
| 100 coins | extra life |

---

## Appendix B: Animation Frames

| Entity | State | Frames | FPS |
|---|---|---|---|
| Mario Small | Walk | 3 | 12 |
| Mario Small | Run | 3 | 20 |
| Mario Small | Jump | 1 | — |
| Mario Small | Idle | 1 | — |
| Mario Small | Death | 1 | — |
| Mario Super | Walk | 3 | 12 |
| Mario Super | Run | 3 | 20 |
| Mario Super | Jump | 1 | — |
| Mario Super | Crouch | 1 | — |
| Goomba | Walk | 2 | 8 |
| Goomba | Squished | 1 | — |
| Koopa | Walk | 2 | 8 |
| Koopa | Shell | 1 | — |
| Coin | Spin | 4 | 8 |
| ? Block | Idle | 4 | 6 |
| Brick | Idle | 1 | — |
| Flag | Wave | 2 | 4 |

---

## Appendix C: Level Design Constraints

| Parameter | Value |
|---|---|
| Level width | 212 tiles (3392px) |
| Level height | 15 tiles (240px) |
| Playfield height | 13 tiles (208px) |
| HUD height | 2 tiles (32px) |
| Minimum gap (jumpable) | 3 tiles |
| Maximum gap (jumpable) | 5 tiles |
| Maximum platform height | 4 tiles |
| Pipe minimum height | 2 tiles |
| Enemy spawn distance | > 5 tiles from spawn |

---

*End of Technical Specification*