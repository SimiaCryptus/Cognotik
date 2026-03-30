# Super Mario Bros Clone - Technical Specification

## Technology Stack
- **Rendering**: HTML5 Canvas 2D API
- **Language**: Vanilla JavaScript (ES6+ classes, modules)
- **Styling**: CSS3
- **Audio**: Web Audio API
- **No external dependencies**

## File Structure
```
code/
├── index.html              # Entry point
├── README.md               # Project documentation
├── GAME_DESIGN.md          # Game design document
├── SPECIFICATION.md        # This file
├── css/
│   └── style.css           # Game styles
└── js/
    ├── main.js             # Entry point, game loop
    ├── constants.js        # Game constants
    ├── input.js            # Input handling
    ├── camera.js           # Camera/viewport
    ├── audio.js            # Web Audio sound engine
    ├── renderer.js         # Canvas rendering utilities
    ├── spritesheet.js      # Sprite definitions
    ├── tilemap.js          # Tile map loading/rendering
    ├── level.js            # Level data and parsing
    ├── entities/
    │   ├── entity.js       # Base entity class
    │   ├── player.js       # Mario player class
    │   ├── goomba.js       # Goomba enemy
    │   ├── koopa.js        # Koopa Troopa enemy
    │   └── powerup.js      # Power-up items
    ├── blocks/
    │   ├── block.js        # Base block class
    │   ├── questionBlock.js# ? block
    │   └── brickBlock.js   # Brick block
    └── game.js             # Main game state manager
```

## Architecture

### Game Loop
```
requestAnimationFrame loop:
  1. Calculate delta time
  2. Update input state
  3. Update game state (physics, AI, collisions)
  4. Render frame
  5. Schedule next frame
```

### Coordinate System
- World coordinates in pixels
- Tile size: 16×16 pixels
- Scale factor: 3× (renders at 48×48 per tile)
- Canvas: 768×576 pixels (16×12 tiles visible)

### Collision System
- Broad phase: Only check nearby tiles/entities
- Narrow phase: AABB overlap test
- Resolution: Separate along minimum penetration axis

### Entity Lifecycle
1. `spawn()` – Initialize position, state
2. `update(dt)` – Physics, AI, state machine
3. `render(ctx, camera)` – Draw to canvas
4. `destroy()` – Remove from entity list

### State Machine (Player)
```
States: IDLE, WALKING, RUNNING, JUMPING, FALLING,
        CROUCHING, DYING, INVINCIBLE, GROWING, SHRINKING
```

### Tile Types
| ID | Name | Solid | Description |
|----|------|-------|-------------|
| 0  | Air  | No    | Empty space |
| 1  | Ground | Yes | Solid ground tile |
| 2  | Brick | Yes | Breakable brick |
| 3  | Question | Yes | ? block with item |
| 4  | Pipe Top Left | Yes | Pipe entrance |
| 5  | Pipe Top Right | Yes | Pipe entrance |
| 6  | Pipe Body Left | Yes | Pipe body |
| 7  | Pipe Body Right | Yes | Pipe body |
| 8  | Coin | No | Collectible coin |
| 9  | Solid Block | Yes | Indestructible |
| 10 | Flag Pole Base | No | Level end |
| 11 | Castle | No | Decorative |

## Performance Targets
- 60 FPS on modern hardware
- Only render tiles within camera viewport
- Object pooling for particles and projectiles
- Spatial partitioning for collision (grid-based)

## Save System
- localStorage for high score persistence
- Current session: lives, score, coins, world

## Browser Support
- Chrome 80+, Firefox 75+, Safari 13+, Edge 80+
- Mobile: iOS Safari 13+, Chrome Android 80+