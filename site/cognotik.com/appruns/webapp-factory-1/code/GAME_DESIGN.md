# Super Mario Bros Clone - Game Design Document

## Overview
A faithful recreation of the classic Super Mario Bros gameplay experience built with vanilla JavaScript, HTML5 Canvas, and CSS.

## Core Mechanics

### Player Character (Mario)
- **Movement**: Left/right walking and running
- **Jumping**: Variable height jump based on button hold duration
- **States**: Small Mario, Super Mario (with mushroom), Fire Mario (with fire flower)
- **Lives System**: 3 lives, game over on 0
- **Death**: Fall into pit or enemy contact (when not stomping)

### Physics
- Gravity constant applied every frame
- Terminal velocity cap
- Acceleration/deceleration for smooth movement
- Collision detection: AABB (Axis-Aligned Bounding Box)

### Enemies
- **Goomba**: Walks in one direction, turns on wall/edge, killed by stomp
- **Koopa Troopa**: Walks, retreats into shell on stomp, shell can be kicked
- **Piranha Plant**: Emerges from pipes periodically

### Power-ups
- **Super Mushroom**: Grows Mario to large size
- **Fire Flower**: Grants fire-throwing ability
- **Star**: Temporary invincibility
- **1-Up Mushroom**: Extra life

### World Structure
- Tile-based levels (16x16 pixel tiles)
- Scrolling camera follows Mario horizontally
- Level end: Touch the flagpole

### Scoring
- Enemy stomp: 100 points
- Coin collect: 200 points
- Power-up: 1000 points
- Flagpole: 100–5000 points based on height

## Controls
| Action | Keyboard | Mobile |
|--------|----------|--------|
| Move Left | ← / A | D-pad Left |
| Move Right | → / D | D-pad Right |
| Jump | Space / W / ↑ | Jump Button |
| Run/Fire | Shift / Z | B Button |
| Pause | Enter / P | Pause Button |

## Level Design
- World 1-1 inspired layout
- Ground tiles, brick blocks, question mark blocks
- Pipes of varying heights
- Gaps and platforms
- Underground bonus areas (future)

## Audio
- Background music (Web Audio API synthesized)
- Sound effects: jump, coin, stomp, power-up, death

## Visual Style
- Pixel art aesthetic using Canvas 2D API
- NES color palette approximation
- Sprite-based rendering with spritesheet
- Parallax background layers