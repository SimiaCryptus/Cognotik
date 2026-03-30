// ============================================================
//  Super Mario Bros Clone — Entry Point & Game Loop
// ============================================================

import { Game }         from './game.js';
import { InputHandler } from './input.js';
import { AudioEngine }  from './audio.js';
import { CANVAS_WIDTH, CANVAS_HEIGHT } from './constants.js';

// ── Canvas setup ───────────────────────────────────────────

const canvas = document.getElementById('game-canvas');
canvas.width  = CANVAS_WIDTH;
canvas.height = CANVAS_HEIGHT;

// Pixel-perfect scaling
const ctx = canvas.getContext('2d');
ctx.imageSmoothingEnabled = false;

// ── Subsystem init ─────────────────────────────────────────

const input = new InputHandler();
const audio = new AudioEngine();

// Resume AudioContext on first user interaction
document.addEventListener('pointerdown', () => audio.resume(), { once: true });
document.addEventListener('keydown',     () => audio.resume(), { once: true });

// ── Game init ──────────────────────────────────────────────

const game = new Game(canvas, input, audio);

// ── Game loop ──────────────────────────────────────────────

let lastTime = 0;
const TARGET_DT = 1000 / 60;  // 60 fps target

function loop(timestamp) {
  const elapsed = timestamp - lastTime;
  lastTime = timestamp;

  // Clamp dt to avoid spiral of death on tab switch
  const dt = Math.min(elapsed / TARGET_DT, 3);

  game.update(dt);
  game.render();
  input.update();   // clear single-frame pressed/released flags

  requestAnimationFrame(loop);
}

requestAnimationFrame((ts) => {
  lastTime = ts;
  requestAnimationFrame(loop);
});

// ── Responsive canvas scaling ──────────────────────────────

function resizeCanvas() {
  const wrapper = document.getElementById('game-container');
  const maxW = window.innerWidth;
  const maxH = window.innerHeight * 0.85;
   const scale = Math.min(maxW / CANVAS_WIDTH, maxH / CANVAS_HEIGHT);
   canvas.style.width  = `${CANVAS_WIDTH  * scale}px`;
   canvas.style.height = `${CANVAS_HEIGHT * scale}px`;
}

window.addEventListener('resize', resizeCanvas);
resizeCanvas();