# Super Mario Bros Clone

A faithful browser-based clone of the classic Super Mario Bros game, built entirely with vanilla JavaScript, HTML5 Canvas, and CSS. No frameworks, no dependencies — just pure web technology.

---

## 🎮 Live Demo

Open `index.html` in any modern browser to play instantly. No build step required.

---

## ✨ Features

- **Smooth side-scrolling** world with parallax-style background layers
- **Mario physics** — acceleration, friction, gravity, variable-height jumping
- **Power-up system** — Small Mario → Super Mario (Mushroom) → Fire Mario (Fire Flower)
- **Enemy AI** — Goombas (walk and stomp), Koopa Troopas (shell mechanics)
- **Interactive blocks** — Question blocks, Brick blocks (breakable when Super), Hidden blocks
- **Collectibles** — Coins, Mushrooms, Fire Flowers, Stars (temporary invincibility)
- **Pipes** — Decorative and enterable (warp zones)
- **Flagpole** — End-of-level sequence with score bonus based on height
- **HUD** — Score, coin count, world indicator, time remaining, lives
- **Sound effects** — Web Audio API synthesized sounds (jump, coin, power-up, death, etc.)
- **Background music** — Procedurally generated chiptune via Web Audio API
- **Multiple worlds** — World 1-1, 1-2, 1-3, 1-4 (castle) included
- **Game states** — Title screen, gameplay, pause, game over, level complete, win screen
- **Particle effects** — Coin sparkles, brick debris, enemy stomp puffs
- **Responsive canvas** — Scales to fit the browser window while maintaining aspect ratio
- **Keyboard and touch controls** — Full mobile support with on-screen D-pad

---

## 🕹️ Controls

### Keyboard

| Action         | Key(s)                        |
|----------------|-------------------------------|
| Move Left      | `←` Arrow / `A`               |
| Move Right     | `→` Arrow / `D`               |
| Jump           | `Space` / `↑` Arrow / `W`    |
| Run / Fireball | `Shift` / `Z` / `X`           |
| Pause          | `P` / `Escape`                |
| Start / Select | `Enter`                       |

> **Tip:** Hold the run button while moving to build speed. Release the jump button early for a shorter hop.

### Touch / Mobile

On-screen buttons appear automatically on touch devices:

| Button | Action        |
|--------|---------------|
| ◀      | Move Left     |
| ▶      | Move Right    |
| 🅱      | Run / Fireball|
| 🅰      | Jump          |
| START  | Pause / Start |

---

## 🚀 How to Run Locally

### Option 1 — Open directly (simplest)

```bash
# Clone or download the repository
git clone https://github.com/yourname/super-mario-clone.git
cd super-mario-clone

# Open in your default browser
open index.html          # macOS
xdg-open index.html      # Linux
start index.html         # Windows
```

> Some browsers restrict certain APIs when opening files directly via `file://`. If you encounter issues, use Option 2.

### Option 2 — Local HTTP server (recommended)

**Using Python (built-in):**
```bash
# Python 3
python3 -m http.server 8080

# Python 2
python -m SimpleHTTPServer 8080
```
Then visit `http://localhost:8080` in your browser.

**Using Node.js:**
```bash
npx serve .
# or
npx http-server . -p 8080
```

**Using VS Code:**
Install the [Live Server](https://marketplace.visualstudio.com/items?itemName=ritwickdey.LiveServer) extension and click **Go Live**.

---

## 📁 Project Structure

```
super-mario-clone/
│
├── index.html                  # Entry point — loads the game
├── README.md                   # This file
│
├── docs/
│   ├── game-design-document.md # Full GDD with mechanics, level design, enemies
│   └── technical-specification.md # Architecture, data formats, API contracts
│
└── src/
    ├── main.js                 # Bootstrap — initialises canvas, game loop, state machine
    │
    ├── core/
    │   ├── GameLoop.js         # requestAnimationFrame loop, delta-time, FPS cap
    │   ├── StateMachine.js     # Game state management (title/play/pause/gameover/win)
    │   ├── InputManager.js     # Keyboard + touch input abstraction
    │   ├── Camera.js           # Scrolling camera with bounds clamping
    │   ├── EventBus.js         # Simple pub/sub for decoupled communication
    │   └── AssetLoader.js      # Sprite sheet + audio asset loading pipeline
    │
    ├── graphics/
    │   ├── Renderer.js         # Master draw call orchestrator
    │   ├── SpriteSheet.js      # Sprite clipping and animation frame management
    │   ├── Animator.js         # Frame-based animation controller
    │   ├── TileRenderer.js     # Efficient tile-map rendering with culling
    │   └── ParticleSystem.js   # Pooled particle emitter (coins, debris, puffs)
    │
    ├── audio/
    │   ├── AudioEngine.js      # Web Audio API context wrapper
    │   ├── SoundEffects.js     # Synthesized SFX (jump, coin, stomp, death…)
    │   └── MusicPlayer.js      # Chiptune BGM sequencer
    │
    ├── world/
    │   ├── Level.js            # Level container — tiles, entities, spawn points
    │   ├── TileMap.js          # 2-D tile grid with collision queries
    │   ├── LevelLoader.js      # Parses level data objects into live Level instances
    │   └── levels/
    │       ├── World1-1.js     # Overworld — tutorial level
    │       ├── World1-2.js     # Underground — pipes and coins
    │       ├── World1-3.js     # Treetop — moving platforms
    │       └── World1-4.js     # Castle — Bowser boss encounter
    │
    ├── entities/
    │   ├── Entity.js           # Base class — position, velocity, AABB, update/draw
    │   ├── Mario.js            # Player — state machine, power-ups, input handling
    │   ├── enemies/
    │   │   ├── Enemy.js        # Base enemy — patrol AI, stomp detection
    │   │   ├── Goomba.js       # Walks, squishes on stomp
    │   │   └── Koopa.js        # Shell mechanic, retreats and slides
    │   ├── blocks/
    │   │   ├── Block.js        # Base interactive block
    │   │   ├── QuestionBlock.js# Reveals item on bump, becomes empty
    │   │   ├── BrickBlock.js   # Breaks when Super Mario bumps; coins otherwise
    │   │   └── CoinBlock.js    # Hidden block containing a coin
    │   ├── items/
    │   │   ├── Coin.js         # Auto-collected, animates upward
    │   │   ├── Mushroom.js     # Slides along ground, powers up Mario
    │   │   ├── FireFlower.js   # Stationary, grants fire power
    │   │   └── Star.js         # Bounces, grants temporary invincibility
    │   ├── Fireball.js         # Projectile fired by Fire Mario
    │   ├── Flagpole.js         # End-of-level flag with slide animation
    │   └── Bowser.js           # Boss — walks, throws hammers, fireballs
    │
    ├── ui/
    │   ├── HUD.js              # Score, coins, world, time, lives overlay
    │   ├── TitleScreen.js      # Animated title with character showcase
    │   ├── PauseScreen.js      # Semi-transparent pause overlay
    │   ├── GameOverScreen.js   # Game over sequence
    │   ├── LevelComplete.js    # Flag slide + score tally animation
    │   └── TouchControls.js   # On-screen D-pad and buttons for mobile
    │
    └── data/
        ├── sprites.js          # Sprite coordinate definitions (no external images)
        ├── animations.js       # Animation sequence definitions
        ├── constants.js        # Physics constants, tile size, game settings
        └── palette.js          # NES colour palette used for canvas drawing
```

---

## 🛠️ Technology Choices

| Technology | Reason |
|---|---|
| **HTML5 Canvas 2D** | Hardware-accelerated pixel-perfect rendering without a framework |
| **Vanilla JavaScript (ES6+)** | Zero dependencies, maximum portability, runs in any modern browser |
| **Web Audio API** | Synthesized chiptune sounds without audio files — works offline |
| **CSS** | Canvas centering, responsive scaling, touch button styling |
| **No build tools** | Open `index.html` and play — no npm, no webpack, no transpilation |

All graphics are drawn programmatically using Canvas 2D drawing primitives and a hand-crafted NES-accurate colour palette. This means the game has **no external asset files** and works completely offline.

---

## 🎨 Art Style

Graphics are rendered using the original NES colour palette (`#5C94FC` sky blue, `#E52521` Mario red, etc.) with pixel-art style rectangles and arcs drawn via the Canvas 2D API. The visual style is faithful to the 1985 original while being entirely code-generated.

---

## 🔊 Audio

All sound effects and music are synthesized in real-time using the **Web Audio API**:

- **Oscillators** (square, triangle, sawtooth) replicate the NES 2A03 sound chip
- **Envelope generators** shape attack/decay/sustain/release
- **The iconic overworld theme** is reproduced as a note sequence played through a square-wave oscillator with vibrato

No `.mp3` or `.ogg` files are required.

---

## 📐 Physics Summary

| Parameter | Value |
|---|---|
| Tile size | 16 × 16 px (rendered at 2× = 32px) |
| Gravity | 0.5 px/frame² |
| Max fall speed | 10 px/frame |
| Walk speed | 2.5 px/frame |
| Run speed | 4.5 px/frame |
| Jump velocity | −12 px/frame |
| Variable jump | Release early → less height |
| Friction (ground) | 0.85 multiplier per frame |
| Friction (air) | 0.95 multiplier per frame |

---

## 🗺️ Levels Included

| Level | Theme | Highlights |
|---|---|---|
| World 1-1 | Overworld | Tutorial goombas, first mushroom, flagpole |
| World 1-2 | Underground | Pipe maze, coin heaven warp zone |
| World 1-3 | Treetop | Moving platforms, koopa troopas |
| World 1-4 | Castle | Bowser boss, axe bridge, lava |

---

## 🐛 Known Limitations

- Multiplayer (2-player alternating) is not implemented
- Only World 1 is included (4 levels)
- Enemy variety is limited to Goombas and Koopas (no Piranha Plants, Lakitus, etc.)
- Save/load (high score persistence) uses `localStorage` only

---

## 📜 Credits

- **Original Game:** Super Mario Bros © 1985 Nintendo Co., Ltd.  
  *This project is a fan-made educational clone and is not affiliated with or endorsed by Nintendo.*
- **Development:** Built as a learning exercise in HTML5 game development
- **Inspiration:** [Infinite Mario Bros](http://www.mojang.com/notch/mario/) by Markus Persson, [Mari0](https://stabyourself.net/mari0/) by Stabyourself

---

## 📄 License

This project is released for **educational purposes only**.  
Super Mario Bros is a trademark of Nintendo Co., Ltd.  
All original code in this repository is available under the [MIT License](LICENSE).

---

*Made with ❤️ and a lot of `requestAnimationFrame`*