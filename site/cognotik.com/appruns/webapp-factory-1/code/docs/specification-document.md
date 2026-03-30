# Super Mario Bros Clone - Technical Specification Document

## 1. Technology Stack
- **Runtime**: Browser (Chrome, Firefox, Safari, Edge)
- **Rendering**: HTML5 Canvas 2D API
- **Language**: Vanilla JavaScript (ES6+)
- **Styling**: CSS3
- **Audio**: Web Audio API
- **No external dependencies**

## 2. Project Structure
```
code/
├── index.html              # Entry point
├── css/
│   └── style.css           # Game styles
├── js/
│   ├── main.js             # Entry point, game loop
│   ├── constants.js        # Game constants
│   ├── game.js             # Game state manager
│   ├── renderer.js         # Canvas rendering
│   ├── input.js            # Input handler
│   ├── physics.js          # Physics engine
│   ├── level.js            # Level data & management
│   ├── player.js           # Mario entity
│   ├── enemy.js            # Enemy entities
│   ├── tile.js             # Tile/block entities
│   ├── item.js             # Collectible items
│   ├── audio.js            # Sound system
│   └── ui.js               # HUD rendering
└── docs/
    ├── game-design-document.md
    └── specification-document.md
```

## 3. Core Architecture

### 3.1 Game Loop
```
requestAnimationFrame loop:
  deltaTime = currentTime - lastTime
  input.update()
  game.update(deltaTime)
    → physics.update(entities, deltaTime)
    → collision detection & resolution
    → entity state machines
    → camera update
  renderer.draw(game.state)
  ui.draw(game.state)
```

### 3.2 Entity Component Model
All game entities share a base structure:
```javascript
{
  x, y,           // world position (pixels)
  width, height,  // bounding box
  vx, vy,         // velocity
  active,         // in-game flag
  type            // entity type string
}
```

### 3.3 Coordinate System
- World coordinates: pixels, origin top-left
- Tile size: 16×16 pixels (scaled ×3 = 48×48 on screen)
- Camera: tracks player X, clamps to level bounds
- Canvas: 768×576 (16 tiles wide × 12 tiles tall at 3× scale)

## 4. Physics Specification

### 4.1 Constants
| Constant | Value | Unit |
|----------|-------|------|
| GRAVITY | 1800 | px/s² |
| TERMINAL_VELOCITY | 600 | px/s |
| WALK_SPEED | 150 | px/s |
| RUN_SPEED | 250 | px/s |
| JUMP_FORCE | -520 | px/s |
| JUMP_HOLD_FORCE | -200 | px/s² (while held, max 0.3s) |

### 4.2 Collision Detection
- AABB (Axis-Aligned Bounding Box)
- Swept collision for fast-moving objects
- Tile collision: query tiles in entity bounding box region
- Entity-entity: broad phase grid, narrow phase AABB

### 4.3 Collision Resolution
- Separate X and Y axes
- Determine penetration depth per axis
- Push entity out of collision on minimum penetration axis
- Set velocity to 0 on collision axis

## 5. Level Format

### 5.1 Tile Map
Levels defined as 2D arrays of tile codes:
```
0  = empty
1  = ground
2  = brick
3  = question block
4  = empty block (used block)
5  = pipe top-left
6  = pipe top-right
7  = pipe body-left
8  = pipe body-right
9  = platform (pass-through)
10 = coin (in level)
11 = castle brick
12 = flag pole base
13 = cloud (decorative)
14 = hill (decorative)
```

### 5.2 Entity Spawn Data
Separate array of entity spawn descriptors:
```javascript
{ type: 'goomba', tileX: 10, tileY: 11 }
{ type: 'koopa',  tileX: 20, tileY: 11 }
{ type: 'coin',   tileX: 15, tileY: 8  }
```

### 5.3 Camera
- Follows player X with right-side threshold (40% of screen width)
- Never scrolls left (one-way scroll)
- Clamps to level width

## 6. Rendering Pipeline

### 6.1 Draw Order (back to front)
1. Sky background gradient
2. Decorative background elements (clouds, hills)
3. Tiles (ground, pipes, blocks)
4. Items (coins, mushrooms)
5. Enemies
6. Player
7. Particles/effects
8. HUD overlay

### 6.2 Sprite System
Sprites drawn programmatically using Canvas 2D shapes and paths.
Each entity has a `draw(ctx, cameraX, scale)` method.

### 6.3 Animation
- Frame-based animation using timestamp
- Player: idle, walk (2 frames), jump, crouch
- Enemies: walk (2 frames), squished, shell
- Coins: spin (4 frames)

## 7. State Machine

### 7.1 Game States
```
LOADING → TITLE → PLAYING → PAUSED
                           ↓
                        LEVEL_COMPLETE → PLAYING (next level)
                           ↓
                        GAME_OVER → TITLE
                           ↓
                        WIN → TITLE
```

### 7.2 Player States
```
IDLE ↔ WALKING ↔ RUNNING
  ↓
JUMPING → FALLING → IDLE
  ↓
DYING → DEAD
  ↓
INVINCIBLE (after damage, 2s)
```

## 8. Audio Specification

### 8.1 Web Audio API Synthesis
All audio generated procedurally:
- Square wave oscillators for melody
- Triangle wave for bass
- Noise buffer for effects

### 8.2 Sound Effects
| Effect | Waveform | Frequency |
|--------|----------|-----------|
| Jump | Square, sweep up | 200→600 Hz |
| Coin | Square, two-tone | 988, 1319 Hz |
| Stomp | Noise burst | - |
| Power-up | Square, arpeggio | C major scale |
| Death | Square, sweep down | 600→100 Hz |

## 9. Performance Targets
- 60 FPS on modern hardware
- Canvas cleared and redrawn each frame
- Tile culling: only draw tiles in camera viewport + 1 tile margin
- Entity culling: only update/draw entities within 2 screens of camera

## 10. Browser Compatibility
- Chrome 80+
- Firefox 75+
- Safari 13+
- Edge 80+
- Requires: Canvas 2D, Web Audio API, ES6