# Super Mario Bros Clone — Game Design Document

**Project Codename:** Super Mario JS  
**Version:** 1.0  
**Date:** 2024  
**Document Type:** Game Design Document (GDD)

---

## Table of Contents

1. [Game Overview](#1-game-overview)
2. [Core Design Pillars](#2-core-design-pillars)
3. [Player Character](#3-player-character)
4. [Controls & Input](#4-controls--input)
5. [Core Mechanics](#5-core-mechanics)
6. [Power-Up System](#6-power-up-system)
7. [Enemy Roster](#7-enemy-roster)
8. [Level Design Philosophy](#8-level-design-philosophy)
9. [World & Level Structure](#9-world--level-structure)
10. [Scoring System](#10-scoring-system)
11. [HUD & UI Design](#11-hud--ui-design)
12. [Camera System](#12-camera-system)
13. [Physics Model](#13-physics-model)
14. [Audio Design](#14-audio-design)
15. [Visual Design](#15-visual-design)
16. [Game States & Flow](#16-game-states--flow)
17. [Win & Lose Conditions](#17-win--lose-conditions)
18. [Accessibility Considerations](#18-accessibility-considerations)

---

## 1. Game Overview

### 1.1 Concept Statement

Super Mario JS is a faithful 2D side-scrolling platformer inspired by the original Super Mario Bros (NES, 1985). The player controls Mario, a plumber navigating the Mushroom Kingdom, jumping across platforms, defeating enemies, collecting power-ups, and ultimately rescuing Princess Peach from the villainous Bowser.

### 1.2 Genre

- **Primary:** 2D Side-Scrolling Platformer
- **Secondary:** Action / Arcade

### 1.3 Target Audience

- Fans of classic NES-era platformers
- Casual gamers seeking a nostalgic experience
- Players aged 8 and up
- Browser-based gaming enthusiasts

### 1.4 Platform

- Web browser (desktop-first, mobile-friendly)
- Rendered via HTML5 Canvas
- No installation required

### 1.5 Unique Selling Points

- Faithful recreation of classic Mario physics and feel
- Runs entirely in the browser with zero dependencies
- Pixel-art aesthetic rendered via Canvas 2D API
- Keyboard and touch controls supported
- Responsive design for various screen sizes

---

## 2. Core Design Pillars

### Pillar 1: "Feel First"
Every mechanic must feel satisfying. Mario's jump arc, the squish of an enemy, the pop of a coin — tactile feedback through animation and sound is paramount. If it doesn't feel good, it doesn't ship.

### Pillar 2: "Readable at a Glance"
The player should instantly understand what is dangerous, what is collectible, and what is interactive. Color, shape, and animation language must be consistent and intuitive.

### Pillar 3: "Escalating Challenge"
Each world introduces a new mechanic or enemy type. Difficulty ramps gradually so new players are not overwhelmed while veteran players are not bored.

### Pillar 4: "Reward Exploration"
Hidden blocks, secret coin caches, and warp zones reward players who experiment. The world should feel alive with secrets.

### Pillar 5: "Instant Restart"
Death should never feel punishing in terms of time lost. Respawn is near-instant. The game respects the player's time.

---

## 3. Player Character

### 3.1 Mario — Base State (Small Mario)

| Attribute        | Value / Description                          |
|------------------|----------------------------------------------|
| Sprite Size      | 16×16 pixels (scaled 3×)                     |
| Walk Speed       | 150 px/s                                     |
| Run Speed        | 250 px/s                                     |
| Jump Height      | ~3.5 tile heights                            |
| Jump Duration    | Variable (hold for higher jump)              |
| Lives            | 3 (starting)                                 |
| Hit Response     | Shrinks if Super Mario; dies if Small Mario  |

### 3.2 Super Mario State

| Attribute        | Value / Description                          |
|------------------|----------------------------------------------|
| Sprite Size      | 16×32 pixels (scaled 3×)                     |
| Walk Speed       | 150 px/s (same)                              |
| Run Speed        | 250 px/s (same)                              |
| Jump Height      | ~4 tile heights                              |
| Hit Response     | Reverts to Small Mario                       |
| Special Ability  | Can break Brick Blocks from below            |

### 3.3 Fire Mario State

| Attribute        | Value / Description                          |
|------------------|----------------------------------------------|
| Sprite Size      | 16×32 pixels (scaled 3×), white palette      |
| Special Ability  | Can throw Fireballs (max 2 on screen)        |
| Hit Response     | Reverts to Super Mario                       |
| Fireball Speed   | 300 px/s horizontal, bounces on ground       |

### 3.4 Character Animations

| State            | Frames | Trigger                                      |
|------------------|--------|----------------------------------------------|
| Idle             | 1      | No input                                     |
| Walk             | 3      | Horizontal movement at walk speed            |
| Run              | 3      | Horizontal movement at run speed (faster)    |
| Jump             | 1      | Airborne                                     |
| Skid             | 1      | Direction change while moving                |
| Crouch           | 1      | Down key pressed (Super/Fire Mario only)     |
| Death            | 1      | Collision with enemy / hazard                |
| Climb            | 2      | On vine/flagpole                             |
| Fire Throw       | 1      | Fire button pressed (Fire Mario)             |

---

## 4. Controls & Input

### 4.1 Keyboard Controls

| Action           | Primary Key      | Alternate Key    |
|------------------|------------------|------------------|
| Move Left        | Arrow Left       | A                |
| Move Right       | Arrow Right      | D                |
| Jump             | Space            | Arrow Up / W     |
| Run / Fire       | Shift            | Z / X            |
| Pause            | Escape           | P                |
| Crouch           | Arrow Down       | S                |

### 4.2 Touch Controls (Mobile)

An on-screen D-pad and action buttons overlay will be rendered on touch devices:
- **Left / Right** directional buttons
- **Jump** button (large, right side)
- **Run/Fire** button (right side, below jump)
- **Pause** button (top center)

### 4.3 Input Nuances

- **Variable Jump Height:** Holding the jump key extends the upward velocity for up to 300ms, enabling short hops and full jumps.
- **Run Modifier:** Holding Run increases max speed and affects jump distance (longer arc).
- **Coyote Time:** 80ms grace period after walking off a ledge where the player can still jump.
- **Jump Buffering:** Jump input registered up to 100ms before landing will trigger a jump on contact.

---

## 5. Core Mechanics

### 5.1 Movement

**Horizontal Movement:**
- Acceleration-based movement (not instant velocity)
- Ground friction decelerates Mario when no input is given
- Ice surfaces have reduced friction (future world feature)
- Running builds speed over ~0.3 seconds

**Vertical Movement (Jumping):**
- Jump applies an initial upward impulse
- Gravity constantly pulls Mario down
- Holding jump reduces gravity for the first 300ms (floatier arc)
- Releasing jump early cuts vertical velocity (short hop)
- Maximum fall speed is capped (terminal velocity)

### 5.2 Stomping Enemies

- Jumping on top of most enemies defeats them
- Mario must land on the enemy's top hitbox (not sides or bottom)
- Stomping grants a small upward bounce (allows chain stomps)
- Chain stomping multiple enemies in one jump multiplies score
- Some enemies (Spiny, Piranha Plant) cannot be stomped

### 5.3 Block Interaction

**Question Mark Blocks (?):**
- Hit from below to reveal contents
- Contents: Coin, Super Mushroom, Fire Flower, Star, 1-Up Mushroom
- Becomes a depleted grey block after use

**Brick Blocks:**
- Small Mario: Hits head, block shakes, nothing happens
- Super/Fire Mario: Breaks the block, may reveal coins or items
- Breaking blocks grants 50 points

**Coin Blocks:**
- Contain multiple coins, activated by repeated hits
- Timer-based: coins stop after ~5 seconds or 10 coins

**Invisible Blocks:**
- Hidden until hit from below
- Contain coins or power-ups
- Become visible after first hit

**Ground / Platform Blocks:**
- Solid, impassable from all sides
- One-way platforms: passable from below, solid from above (future)

### 5.4 Pipes

- Decorative pipes block movement
- Certain pipes are entrances to underground bonus areas
- Piranha Plants emerge from pipes on a timer
- Warp pipes allow level skipping (hidden mechanic)

### 5.5 Flagpole

- Located at the end of each level
- Mario grabs the pole and slides down
- Score bonus based on height of grab:
  - Top of pole: 5000 points
  - 75% height: 2000 points
  - 50% height: 1000 points
  - 25% height: 500 points
  - Bottom: 100 points
- Touching the flag triggers level-end sequence
- Remaining time is converted to points (100 pts per second)

### 5.6 Coins

- Collected on contact
- 100 coins = 1 extra life
- Coin counter displayed in HUD
- Animated spin cycle (4 frames)

### 5.7 Death & Respawn

- Mario plays death animation (spin upward, fall off screen)
- Life count decrements
- If lives > 0: respawn at level start (or last checkpoint)
- If lives = 0: Game Over screen
- Invincibility frames: 2 seconds after respawn

---

## 6. Power-Up System

### 6.1 Super Mushroom

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Appearance       | Red mushroom with white spots                |
| Spawn            | From ? Block                                 |
| Behavior         | Slides in direction Mario is facing, falls off edges |
| Effect           | Small Mario → Super Mario                    |
| Points           | 1000                                         |

### 6.2 Fire Flower

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Appearance       | Orange/red flower                            |
| Spawn            | From ? Block (only if already Super Mario)   |
| Behavior         | Stationary, bobs up and down                 |
| Effect           | Super Mario → Fire Mario                     |
| Points           | 1000                                         |

### 6.3 Super Star (Starman)

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Appearance       | Flashing yellow star                         |
| Spawn            | From ? Block, bounces erratically            |
| Behavior         | Bounces around, follows slight gravity       |
| Effect           | Temporary invincibility (10 seconds)         |
| Visual Feedback  | Mario flashes rainbow colors                 |
| Audio            | Invincibility music plays                    |
| Enemy Interaction| Kills all enemies on contact (500→1000→2000→4000 chain) |
| Points           | 1000                                         |

### 6.4 1-Up Mushroom

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Appearance       | Green mushroom with white spots              |
| Spawn            | Hidden blocks, specific ? Blocks             |
| Behavior         | Same as Super Mushroom (slides, falls)       |
| Effect           | +1 Life                                      |
| Points           | 0 (life gained instead)                      |

### 6.5 Power-Up Hierarchy

```
Small Mario → [Super Mushroom] → Super Mario → [Fire Flower] → Fire Mario
                                                     ↓ (hit)
                                               Super Mario
                                                     ↓ (hit)
                                               Small Mario
                                                     ↓ (hit)
                                                   DEATH
```

---

## 7. Enemy Roster

### 7.1 Goomba

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Sprite Size      | 16×16 px                                     |
| Movement         | Walks horizontally, reverses at walls/edges  |
| Speed            | 60 px/s                                      |
| Stomp Result     | Flattened sprite, disappears after 0.5s      |
| Fireball Result  | Knocked back, disappears                     |
| Shell Result     | N/A                                          |
| Points           | 100 (stomp), 100 (fireball)                  |
| Special          | None                                         |

### 7.2 Koopa Troopa (Green)

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Sprite Size      | 16×24 px (walking), 16×16 px (shell)         |
| Movement         | Walks horizontally, reverses at walls        |
| Speed            | 60 px/s                                      |
| Stomp Result     | Retreats into shell                          |
| Shell Behavior   | Stationary until kicked; slides at 300 px/s  |
| Shell Kick       | Kick shell to send it sliding, kills enemies |
| Fireball Result  | Knocked back, disappears                     |
| Points           | 100 (stomp), 400 (shell kill), 100 (fireball)|
| Special          | Shell can kill Mario if it slides back       |

### 7.3 Koopa Troopa (Red)

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Behavior         | Same as Green Koopa but does NOT walk off edges |
| Speed            | 60 px/s                                      |
| Points           | 200 (stomp)                                  |

### 7.4 Piranha Plant

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Sprite Size      | 16×24 px                                     |
| Movement         | Emerges from pipe, retracts on timer         |
| Cycle            | 2s out, 2s in, pauses if Mario is near pipe  |
| Stomp Result     | Cannot be stomped                            |
| Fireball Result  | Defeated                                     |
| Points           | 200                                          |
| Special          | Will not emerge if Mario stands on/near pipe |

### 7.5 Koopa Shell (Projectile State)

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Speed            | 300 px/s                                     |
| Collision        | Kills enemies, bounces off walls             |
| Mario Collision  | Kills Mario (unless Star-powered)            |
| Stop Method      | Mario stomps sliding shell to stop it        |

### 7.6 Bowser (Boss)

| Property         | Value                                        |
|------------------|----------------------------------------------|
| Sprite Size      | 32×32 px                                     |
| Movement         | Walks toward Mario                           |
| Attack           | Throws hammers in arc patterns               |
| Fire Breath      | Shoots fireballs horizontally                |
| Defeat Method 1  | Hit axe at end of bridge (drops into lava)   |
| Defeat Method 2  | 5 fireballs (or Star contact)                |
| Points           | 5000                                         |
| Special          | Bridge collapses when axe is hit             |

---

## 8. Level Design Philosophy

### 8.1 The "3-Step Introduction" Rule

Every new mechanic follows a three-step introduction:
1. **Safe Introduction:** Show the mechanic in a zero-risk environment (e.g., a lone Goomba on flat ground)
2. **Guided Challenge:** Use the mechanic with mild risk (e.g., Goomba near a pit)
3. **Mastery Test:** Combine with other mechanics (e.g., multiple Goombas, moving platforms, pits)

### 8.2 Horizontal Pacing

Levels are designed with a rhythm of tension and relief:
- **Open sections** (breathing room, coins, easy enemies)
- **Challenge sections** (tight jumps, enemy clusters, hazards)
- **Reward sections** (power-up blocks, coin caches)
- Pattern repeats 2–3 times per level, escalating each cycle

### 8.3 Vertical Design

- Levels have multiple vertical paths (high road / low road)
- High paths: More dangerous but more rewarding (coins, power-ups)
- Low paths: Safer but fewer rewards
- Underground sections: Darker palette, different enemy mix, bonus coins

### 8.4 Landmark Placement

Every ~20 tiles, a visual landmark (tall pipe, unique block formation, enemy cluster) helps players orient themselves and feel progress.

### 8.5 Checkpoint Philosophy

- Midpoint flags placed at roughly 50% of level length
- Respawning at checkpoint preserves power-up state
- No checkpoint in World 1 (tutorial world) — teaches restart mentality

### 8.6 Secret Design

- 1 hidden block cache per level minimum
- 1 warp zone per world (accessible via specific pipe)
- Secrets hinted at by visual anomalies (floating coins pointing to invisible block)

---

## 9. World & Level Structure

### 9.1 World Map

The game features **4 Worlds** with **3 Levels each** plus a **Castle level**:

```
World 1: Grasslands (Tutorial)
  1-1: Overworld (introduces Goombas, blocks, coins, mushroom)
  1-2: Underground (introduces pipes, Koopas, underground aesthetic)
  1-3: Overworld Bridge (introduces moving platforms, gaps)
  1-C: Castle (introduces Bowser, lava, hammer hazards)

World 2: Desert
  2-1: Overworld (introduces Piranha Plants, longer gaps)
  2-2: Underground (faster enemies, more complex layouts)
  2-3: Overworld (introduces multiple enemy types together)
  2-C: Castle (harder Bowser pattern, more hammers)

World 3: Ice/Snow
  3-1: Overworld (slippery surfaces, Koopa shells as hazards)
  3-2: Underground (tight corridors, enemy density)
  3-3: Overworld (vertical challenge, high platforms)
  3-C: Castle (Bowser + Piranha Plants)

World 4: Sky/Clouds
  4-1: Sky level (cloud platforms, wide gaps)
  4-2: Underground (final underground, maximum density)
  4-3: Overworld (gauntlet, all enemy types)
  4-C: Final Castle (True Bowser, hardest pattern)
```

### 9.2 Level Dimensions

| World | Level Width    | Height (screens) |
|-------|----------------|------------------|
| 1-1   | 3392 px        | 1                |
| 1-2   | 3392 px        | 1 (underground)  |
| 1-3   | 2816 px        | 1                |
| 1-C   | 2240 px        | 1                |
| ...   | Scales up      | Up to 2          |

### 9.3 Tile System

- Base tile size: **16×16 pixels** (rendered at 3× = 48×48 px)
- Level data stored as 2D arrays of tile IDs
- Tile types: Empty, Ground, Brick, Question, Pipe Top, Pipe Body, Castle Brick, Cloud, etc.
- Collision derived from tile type flags

---

## 10. Scoring System

### 10.1 Point Values

| Action                           | Points        |
|----------------------------------|---------------|
| Collect Coin                     | 200           |
| Hit ? Block (coin)               | 200           |
| Break Brick Block                | 50            |
| Stomp Goomba                     | 100           |
| Stomp Koopa (into shell)         | 100           |
| Kick Shell (kills enemy)         | 400           |
| Fireball kills enemy             | 100           |
| Collect Super Mushroom           | 1000          |
| Collect Fire Flower              | 1000          |
| Collect Star                     | 1000          |
| Star kill (1st enemy)            | 100           |
| Star kill (2nd enemy)            | 200           |
| Star kill (3rd enemy)            | 400           |
| Star kill (4th+ enemy)           | 500           |
| Chain stomp (2nd stomp)          | 200           |
| Chain stomp (3rd stomp)          | 400           |
| Chain stomp (4th stomp)          | 800           |
| Chain stomp (5th stomp)          | 1000          |
| Chain stomp (6th+ stomp)         | 1-Up          |
| Flagpole (top)                   | 5000          |
| Flagpole (75%)                   | 2000          |
| Flagpole (50%)                   | 1000          |
| Flagpole (25%)                   | 500           |
| Flagpole (bottom)                | 100           |
| Time bonus (per second remaining)| 50            |
| Defeat Bowser                    | 5000          |
| Complete World                   | 1000 × World# |

### 10.2 Extra Lives

| Condition                        | Result        |
|----------------------------------|---------------|
| Collect 100 coins                | +1 Life       |
| Find 1-Up Mushroom               | +1 Life       |
| Chain stomp 6+ enemies           | +1 Life       |
| Score reaches 20,000             | +1 Life       |
| Score reaches 50,000             | +1 Life       |
| Score reaches 100,000            | +1 Life       |

### 10.3 High Score

- Top 5 high scores stored in localStorage
- Initials entry screen (3 characters) on new high score
- High score table accessible from main menu

---

## 11. HUD & UI Design

### 11.1 In-Game HUD

```
┌─────────────────────────────────────────────────────────┐
│  MARIO          WORLD          TIME                      │
│  000000          1-1           300                       │
│  ♥♥♥   ×03    ●●●●●●●●●●                               │
│  LIVES  COINS   (coin counter)                           │
└─────────────────────────────────────────────────────────┘
```

**HUD Elements:**
- **Score:** 6-digit score, top-left
- **Coin Count:** Coin icon + 2-digit count
- **World:** Current world-level (e.g., "1-1")
- **Time:** Countdown timer (300 seconds default)
- **Lives:** Mario head icon × count
- **Power-Up State:** Small indicator of current power-up

### 11.2 Screens

**Title Screen:**
- Game logo (pixel art style)
- "PRESS START" blinking text
- Background: World 1-1 static scene
- Options: Start Game, High Scores, Controls

**World Clear Screen:**
- "WORLD X-X CLEAR!" text
- Score tally animation
- Time bonus calculation shown
- Transition to next world

**Game Over Screen:**
- "GAME OVER" text (large, centered)
- Final score display
- High score indicator if applicable
- Options: Try Again, Main Menu

**Pause Screen:**
- Semi-transparent overlay
- "PAUSED" text
- Resume / Quit options

**World Introduction Card:**
- "WORLD X-X" displayed before level starts
- Mario sprite shown
- Lives count shown
- 3-second display before gameplay begins

---

## 12. Camera System

### 12.1 Horizontal Scrolling

- Camera follows Mario horizontally with a **right-biased dead zone**
- Mario can move left freely within the left 40% of the screen
- Camera begins scrolling when Mario crosses the 40% mark from left
- Camera never scrolls left (classic Mario behavior — no backtracking)
- Camera stops at level end boundary

### 12.2 Vertical Camera

- Camera is fixed vertically for standard levels
- For tall levels (2 screens): camera follows Mario vertically with smooth lerp
- Underground levels: fixed camera, no vertical scroll

### 12.3 Camera Smoothing

- Horizontal: Instant follow (no lag) for responsive feel
- Vertical (where applicable): Lerp factor 0.1 per frame for smooth tracking

---

## 13. Physics Model

### 13.1 Constants

| Constant             | Value          | Notes                              |
|----------------------|----------------|------------------------------------|
| Gravity              | 1800 px/s²     | Applied every frame                |
| Max Fall Speed       | 600 px/s       | Terminal velocity                  |
| Walk Acceleration    | 600 px/s²      | Ground acceleration                |
| Run Acceleration     | 900 px/s²      | With run button held               |
| Walk Max Speed       | 150 px/s       | Without run button                 |
| Run Max Speed        | 250 px/s       | With run button held               |
| Ground Friction      | 800 px/s²      | Deceleration when no input         |
| Air Friction         | 200 px/s²      | Reduced control in air             |
| Jump Velocity        | -600 px/s      | Initial upward velocity            |
| Jump Hold Gravity    | 600 px/s²      | Reduced gravity while holding jump |
| Coyote Time          | 80 ms          | Grace period after ledge           |
| Jump Buffer          | 100 ms         | Pre-land jump registration         |

### 13.2 Collision Detection

- **AABB (Axis-Aligned Bounding Box)** collision for all entities
- Tile collision resolved by checking 4 corners of entity bounding box
- Collision response: separate entity from tile, zero velocity in collision axis
- Enemy-enemy collision: reverse direction
- Enemy-Mario collision: damage check (top = stomp, sides/bottom = damage)

### 13.3 Hitbox Definitions

| Entity           | Hitbox (relative to sprite)                  |
|------------------|----------------------------------------------|
| Small Mario      | 12×14 px, centered                           |
| Super Mario      | 12×28 px, centered                           |
| Goomba           | 14×12 px, centered                           |
| Koopa (walking)  | 12×20 px, centered                           |
| Koopa (shell)    | 14×14 px, centered                           |
| Coin             | 8×8 px, centered                             |
| Fireball         | 6×6 px, centered                             |
| Mushroom         | 14×14 px, centered                           |

---

## 14. Audio Design

### 14.1 Music Tracks

| Track                  | Usage                                        |
|------------------------|----------------------------------------------|
| Overworld Theme        | Standard above-ground levels                 |
| Underground Theme      | Underground/cave levels                      |
| Castle Theme           | Castle levels                                |
| Starman Theme          | Active Star power-up (overrides current)     |
| Hurry Up Theme         | When timer drops below 100 seconds           |
| Level Clear Fanfare    | Short jingle on flagpole touch               |
| World Clear Theme      | World completion screen                      |
| Game Over Theme        | Game over screen                             |
| Title Theme            | Main menu                                    |
| Boss Theme             | Bowser fight                                 |

### 14.2 Sound Effects

| Sound Effect           | Trigger                                      |
|------------------------|----------------------------------------------|
| Jump                   | Mario jumps                                  |
| Coin Collect           | Coin collected                               |
| Power-Up Appear        | Item spawns from block                       |
| Power-Up Collect       | Power-up touched                             |
| Mushroom Grow          | Small → Super transformation                 |
| Fireball Shoot         | Fire Mario shoots                            |
| Fireball Hit           | Fireball hits enemy/wall                     |
| Enemy Stomp            | Enemy stomped                                |
| Enemy Kick             | Shell kicked                                 |
| Block Hit              | ? Block or Brick hit                         |
| Brick Break            | Brick block destroyed                        |
| Flagpole               | Mario grabs flagpole                         |
| 1-Up                   | Extra life gained                            |
| Mario Hurt             | Mario takes damage                           |
| Mario Die              | Mario death                                  |
| Bowser Roar            | Bowser appears                               |
| Stage Clear            | Level complete                               |
| Game Over              | All lives lost                               |
| Pause                  | Game paused                                  |
| Warning (timer)        | Timer hits 100                               |

### 14.3 Audio Implementation

- Web Audio API for sound synthesis and playback
- Chiptune-style audio generated procedurally (no external files required)
- Music loops seamlessly
- Sound effects use short AudioBuffer clips
- Master volume control in options menu
- Music and SFX volume independently adjustable

---

## 15. Visual Design

### 15.1 Art Style

- **Pixel art** aesthetic inspired by NES Super Mario Bros
- Base resolution: **256×240 pixels** (NES native), scaled up 3× to 768×720
- Canvas rendered at native resolution, CSS scaled to fit viewport
- Crisp pixel rendering (no anti-aliasing: `image-rendering: pixelated`)

### 15.2 Color Palette

**World 1 (Grasslands):**
- Sky: `#5C94FC` (NES blue)
- Ground: `#C84B0C` (brick brown) / `#E49B3C` (tan)
- Grass top: `#00A800` (NES green)
- Pipes: `#00A800` / `#006800`
- Clouds: `#FCFCFC`
- Mario: `#FC0000` (hat/shirt), `#FCB800` (skin), `#0000FC` (overalls)

**World 2 (Underground):**
- Background: `#000000`
- Blocks: `#A85400`
- Accent: `#FCB800`

**World 3 (Castle):**
- Background: `#000000`
- Bricks: `#848484`
- Lava: `#FC0000` / `#FCB800` (animated)

### 15.3 Sprite Rendering

All sprites are drawn programmatically using Canvas 2D API:
- Each sprite defined as a pixel grid array
- Color palette applied at render time
- Scaling handled by canvas transform
- Sprite flipping via canvas scale(-1, 1) for left-facing

### 15.4 Tile Rendering

- Tiles drawn from a virtual tileset
- Each tile type has a unique draw function
- Animated tiles (coins, lava) cycle through frames at 8 FPS
- Background elements (clouds, hills, bushes) drawn as decorative layer

### 15.5 Particle Effects

| Effect               | Trigger                                      |
|----------------------|----------------------------------------------|
| Brick Debris         | Brick block broken (4 flying chunks)         |
| Coin Sparkle         | Coin collected (star burst)                  |
| Enemy Defeat         | Enemy killed by fireball (puff)              |
| Score Popup          | Points earned (floating number)              |
| Dust Puff            | Mario lands from high jump                   |
| Star Sparkle         | Star power active (trailing sparkles)        |

---

## 16. Game States & Flow

```
                    ┌─────────────┐
                    │  BOOT/LOAD  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ TITLE SCREEN│◄──────────────┐
                    └──────┬──────┘               │
                           │ Start                │
                    ┌──────▼──────┐               │
                    │ WORLD INTRO │               │
                    └──────┬──────┘               │
                           │                      │
                    ┌──────▼──────┐               │
              ┌────►│  GAMEPLAY   │               │
              │     └──────┬──────┘               │
              │            │                      │
              │     ┌──────▼──────┐               │
              │     │   PAUSED    │               │
              │     └──────┬──────┘               │
              │            │ Resume               │
              │            │                      │
              │     ┌──────▼──────┐               │
              │     │ LEVEL CLEAR │               │
              │     └──────┬──────┘               │
              │            │                      │
              │     ┌──────▼──────┐               │
              │     │ WORLD CLEAR │               │
              │     └──────┬──────┘               │
              │            │                      │
              │     ┌──────▼──────┐               │
              └─────┤ NEXT LEVEL  │               │
                    └──────┬──────┘               │
                           │ (lives = 0)          │
                    ┌──────▼──────┐               │
                    │  GAME OVER  ├───────────────┘
                    └─────────────┘
```

### 16.1 State Descriptions

**BOOT/LOAD:** Initialize canvas, load/generate audio assets, set up input handlers. Transitions automatically to Title Screen.

**TITLE SCREEN:** Display logo, menu options. Accept input to start game or view high scores.

**WORLD INTRO:** Display "WORLD X-X" card with lives count. Auto-transitions after 3 seconds.

**GAMEPLAY:** Main game loop. Handles all physics, rendering, input, and game logic.

**PAUSED:** Freeze all game logic. Overlay displayed. Resume or quit options.

**LEVEL CLEAR:** Flagpole animation, score tally, time bonus calculation. Auto-transitions.

**WORLD CLEAR:** Fireworks display, world complete fanfare. Transitions to next World Intro.

**GAME OVER:** Display score, high score check, options to retry or return to title.

---

## 17. Win & Lose Conditions

### 17.1 Level Win

- Mario touches the flagpole at the end of the level
- OR Mario reaches the axe at the end of a castle level

### 17.2 World Win

- Complete all levels in a world (including castle)

### 17.3 Game Win

- Complete World 4-C (defeat final Bowser)
- Credits sequence plays
- Final score displayed
- Option to play again (harder mode: enemies faster, timer shorter)

### 17.4 Lose Conditions

**Mario Dies:**
- Falls into a pit (below screen bottom)
- Touches an enemy (without stomping)
- Touches Bowser's fire or hammers
- Timer reaches 0 (instant death)

**Game Over:**
- Lives reach 0 after a death
- Triggers Game Over screen

### 17.5 Timer

- Each level starts with 300 seconds (400 for longer levels)
- Timer counts down in real-time
- At 100 seconds: "Hurry Up!" warning, music tempo increases
- At 0 seconds: Mario dies instantly

---

## 18. Accessibility Considerations

### 18.1 Visual

- High contrast mode option (increases outline thickness on sprites)
- Colorblind-friendly palette option (replaces red/green distinctions)
- Adjustable game scale (1×, 2×, 3× — default 3×)

### 18.2 Controls

- Fully remappable keyboard controls
- Touch controls with adjustable button size
- Gamepad support (Web Gamepad API) where available

### 18.3 Difficulty

- **Easy Mode:** Mario starts as Super Mario, timer is 400s, enemies move slower
- **Normal Mode:** Classic experience
- **Hard Mode:** Timer is 200s, enemies move faster, no checkpoints

### 18.4 Audio

- Separate music/SFX volume sliders
- Visual cues accompany all audio cues (screen flash on damage, etc.)
- Mute all option

---

## Appendix A: Glossary

| Term             | Definition                                               |
|------------------|----------------------------------------------------------|
| Tile             | 16×16 pixel grid unit used for level construction        |
| AABB             | Axis-Aligned Bounding Box — rectangle collision shape    |
| Coyote Time      | Grace period allowing jump after walking off ledge       |
| Jump Buffer      | Pre-registered jump input before landing                 |
| Chain Stomp      | Consecutive enemy stomps without touching ground         |
| Dead Zone        | Camera region where Mario moves without camera scrolling |
| Warp Zone        | Hidden pipe leading to a later world                     |
| 1-Up             | Extra life                                               |
| HUD              | Heads-Up Display — on-screen game information            |

## Appendix B: Revision History

| Version | Date       | Author       | Changes                          |
|---------|------------|--------------|----------------------------------|
| 0.1     | 2024-01    | Design Team  | Initial draft                    |
| 0.5     | 2024-01    | Design Team  | Added physics constants, enemies |
| 1.0     | 2024-01    | Design Team  | Complete first version           |