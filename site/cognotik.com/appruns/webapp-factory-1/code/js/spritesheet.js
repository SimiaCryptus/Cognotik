// ============================================================
//  Super Mario Bros Clone — Sprite Renderer
//  Draws all game sprites procedurally using Canvas 2D shapes.
//  No external image files required.
// ============================================================

import { SCALE, TILE_SIZE, TILE } from './constants.js';
import { TILE } from './constants.js';

const S = SCALE;
const T = TILE_SIZE * SCALE;  // 48

// ── Helpers ────────────────────────────────────────────────

function px(n) { return Math.round(n * S); }

function rect(ctx, color, x, y, w, h) {
  ctx.fillStyle = color;
  ctx.fillRect(x, y, w, h);
}

// ============================================================
//  Tile drawing
// ============================================================


export function drawTileSync(ctx, tileId, sx, sy) {
  switch (tileId) {
     case TILE.QUESTION_USED: drawUsedBlock(ctx, sx, sy);   break;
     case TILE.PIPE_TOP_LEFT: drawPipeTopL(ctx, sx, sy);    break;
     case TILE.PIPE_TOP_RIGHT:drawPipeTopR(ctx, sx, sy);    break;
     case TILE.PIPE_LEFT:     drawPipeBodyL(ctx, sx, sy);   break;
     case TILE.PIPE_RIGHT:    drawPipeBodyR(ctx, sx, sy);   break;
     case TILE.COIN:          drawCoinTile(ctx, sx, sy);    break;
     case TILE.SOLID:         drawSolid(ctx, sx, sy);       break;
     case TILE.FLAGPOLE_POLE: drawFlagPole(ctx, sx, sy);    break;
     case TILE.FLAGPOLE_BASE: drawFlagBase(ctx, sx, sy);    break;
     case TILE.CLOUD_LEFT:    drawCloudL(ctx, sx, sy);      break;
     case TILE.CLOUD_MID:     drawCloudM(ctx, sx, sy);      break;
     case TILE.CLOUD_RIGHT:   drawCloudR(ctx, sx, sy);      break;
     case TILE.HILL_LEFT:     drawHillL(ctx, sx, sy);       break;
     case TILE.HILL_MID:      drawHillM(ctx, sx, sy);       break;
     case TILE.HILL_RIGHT:    drawHillR(ctx, sx, sy);       break;
     case TILE.BUSH_LEFT:     drawBushL(ctx, sx, sy);       break;
     case TILE.BUSH_MID:      drawBushM(ctx, sx, sy);       break;
     case TILE.BUSH_RIGHT:    drawBushR(ctx, sx, sy);       break;
     case TILE.GROUND:        drawGround(ctx, sx, sy);      break;
     case TILE.BRICK:         drawBrick(ctx, sx, sy);       break;
     case TILE.QUESTION:      drawQuestion(ctx, sx, sy);    break;
     case TILE.QUESTION_USED: drawUsedBlock(ctx, sx, sy);   break;
     case TILE.PIPE_TOP_LEFT: drawPipeTopL(ctx, sx, sy);    break;
     case TILE.PIPE_TOP_RIGHT:drawPipeTopR(ctx, sx, sy);    break;
     case TILE.PIPE_LEFT:     drawPipeBodyL(ctx, sx, sy);   break;
     case TILE.PIPE_RIGHT:    drawPipeBodyR(ctx, sx, sy);   break;
     case TILE.COIN:          drawCoinTile(ctx, sx, sy);    break;
     case TILE.SOLID:         drawSolid(ctx, sx, sy);       break;
     case TILE.FLAGPOLE_POLE: drawFlagPole(ctx, sx, sy);    break;
     case TILE.FLAGPOLE_BASE: drawFlagBase(ctx, sx, sy);    break;
     case TILE.CLOUD_LEFT:    drawCloudL(ctx, sx, sy);      break;
     case TILE.CLOUD_MID:     drawCloudM(ctx, sx, sy);      break;
     case TILE.CLOUD_RIGHT:   drawCloudR(ctx, sx, sy);      break;
     case TILE.HILL_LEFT:     drawHillL(ctx, sx, sy);       break;
     case TILE.HILL_MID:      drawHillM(ctx, sx, sy);       break;
     case TILE.HILL_RIGHT:    drawHillR(ctx, sx, sy);       break;
     case TILE.BUSH_LEFT:     drawBushL(ctx, sx, sy);       break;
     case TILE.BUSH_MID:      drawBushM(ctx, sx, sy);       break;
     case TILE.BUSH_RIGHT:    drawBushR(ctx, sx, sy);       break;
    default: break;  // AIR — draw nothing
  }
}

// ── Ground ─────────────────────────────────────────────────
function drawGround(ctx, x, y) {
  rect(ctx, '#c84c0c', x, y, T, T);
  rect(ctx, '#e8a000', x, y, T, px(2));
  // Grid lines
  ctx.strokeStyle = '#8c3400';
  ctx.lineWidth = px(0.5);
  ctx.strokeRect(x + 0.5, y + 0.5, T - 1, T - 1);
}

// ── Brick ──────────────────────────────────────────────────
function drawBrick(ctx, x, y) {
  rect(ctx, '#c84c0c', x, y, T, T);
  ctx.fillStyle = '#8c3400';
  // Horizontal mortar lines
  ctx.fillRect(x, y + px(5),  T, px(2));
  ctx.fillRect(x, y + px(11), T, px(2));
  // Vertical mortar — offset rows
  ctx.fillRect(x + px(8),  y,        px(2), px(5));
  ctx.fillRect(x,           y + px(7), px(4), px(4));
  ctx.fillRect(x + px(12), y + px(7), px(4), px(4));
  ctx.fillRect(x + px(4),  y + px(13), px(2), px(3));
}

// ── Question Block ─────────────────────────────────────────
function drawQuestion(ctx, x, y, used = false) {
  const bg = used ? '#a87000' : '#e8a000';
  rect(ctx, bg, x, y, T, T);
  // Border
  rect(ctx, '#fcfcfc', x,      y,      T,    px(2));
  rect(ctx, '#fcfcfc', x,      y+T-px(2), T, px(2));
  rect(ctx, '#fcfcfc', x,      y,      px(2), T);
  rect(ctx, '#fcfcfc', x+T-px(2), y,  px(2), T);
  rect(ctx, '#c87000', x+px(2), y+T-px(4), T-px(4), px(2));
  rect(ctx, '#c87000', x+T-px(4), y+px(2), px(2), T-px(4));
  if (!used) {
    // Draw '?'
    ctx.fillStyle = '#fcfcfc';
    ctx.font = `bold ${px(10)}px monospace`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('?', x + T/2, y + T/2 + px(1));
  }
}

// ── Used Block ─────────────────────────────────────────────
function drawUsedBlock(ctx, x, y) {
  drawQuestion(ctx, x, y, true);
}

// ── Solid Block ────────────────────────────────────────────
function drawSolid(ctx, x, y) {
  rect(ctx, '#a87000', x, y, T, T);
  rect(ctx, '#c8a000', x, y, T, px(2));
  rect(ctx, '#c8a000', x, y, px(2), T);
}

// ── Pipe ───────────────────────────────────────────────────
function drawPipeTopL(ctx, x, y) {
  rect(ctx, '#00a800', x, y, T, T);
  rect(ctx, '#00d800', x, y, px(3), T);
  rect(ctx, '#006800', x + T - px(3), y, px(3), T);
  rect(ctx, '#00d800', x, y, T, px(3));
}

function drawPipeTopR(ctx, x, y) {
  rect(ctx, '#00a800', x, y, T, T);
  rect(ctx, '#00d800', x, y, px(3), T);
  rect(ctx, '#006800', x + T - px(3), y, px(3), T);
  rect(ctx, '#00d800', x, y, T, px(3));
}

function drawPipeBodyL(ctx, x, y) {
  rect(ctx, '#00a800', x, y, T, T);
  rect(ctx, '#00d800', x + px(2), y, px(3), T);
  rect(ctx, '#006800', x + T - px(3), y, px(3), T);
}

function drawPipeBodyR(ctx, x, y) {
  rect(ctx, '#00a800', x, y, T, T);
  rect(ctx, '#00d800', x + px(2), y, px(3), T);
  rect(ctx, '#006800', x + T - px(3), y, px(3), T);
}

// ── Coin (tile) ────────────────────────────────────────────
function drawCoinTile(ctx, x, y) {
  const cx = x + T/2;
  const cy = y + T/2;
  const r  = px(5);
  ctx.fillStyle = '#fcd800';
  ctx.beginPath();
  ctx.ellipse(cx, cy, r * 0.6, r, 0, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#fcfc00';
  ctx.beginPath();
  ctx.ellipse(cx - px(1), cy, r * 0.3, r * 0.7, 0, 0, Math.PI * 2);
  ctx.fill();
}

// ── Flag Pole ──────────────────────────────────────────────
function drawFlagPole(ctx, x, y) {
  rect(ctx, '#a0a0a0', x + T/2 - px(1), y, px(2), T);
}

function drawFlagBase(ctx, x, y) {
  rect(ctx, '#a0a0a0', x + T/2 - px(1), y, px(2), T);
  rect(ctx, '#a0a0a0', x + px(2), y + T - px(4), T - px(4), px(4));
}

// ── Decorative: Clouds ─────────────────────────────────────
function drawCloudL(ctx, x, y) {
  ctx.fillStyle = '#fcfcfc';
  ctx.fillRect(x, y + px(8), T, px(8));
  ctx.beginPath();
  ctx.arc(x + px(8), y + px(8), px(8), Math.PI, 0);
  ctx.fill();
}
function drawCloudM(ctx, x, y) {
  ctx.fillStyle = '#fcfcfc';
  ctx.fillRect(x, y + px(4), T, px(12));
  ctx.beginPath();
  ctx.arc(x + T/2, y + px(4), px(10), Math.PI, 0);
  ctx.fill();
}
function drawCloudR(ctx, x, y) {
  ctx.fillStyle = '#fcfcfc';
  ctx.fillRect(x, y + px(8), T, px(8));
  ctx.beginPath();
  ctx.arc(x + px(8), y + px(8), px(8), Math.PI, 0);
  ctx.fill();
}

// ── Decorative: Hills ──────────────────────────────────────
function drawHillL(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.beginPath();
  ctx.moveTo(x, y + T);
  ctx.quadraticCurveTo(x, y, x + T, y + T);
  ctx.fill();
}
function drawHillM(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.fillRect(x, y, T, T);
}
function drawHillR(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.beginPath();
  ctx.moveTo(x, y + T);
  ctx.quadraticCurveTo(x + T, y, x + T, y + T);
  ctx.fill();
}

// ── Decorative: Bushes ─────────────────────────────────────
function drawBushL(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.beginPath();
  ctx.arc(x + px(10), y + px(10), px(10), Math.PI, 0);
  ctx.fill();
  ctx.fillRect(x, y + px(10), T, px(6));
}
function drawBushM(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.beginPath();
  ctx.arc(x + T/2, y + px(6), px(12), Math.PI, 0);
  ctx.fill();
  ctx.fillRect(x, y + px(6), T, px(10));
}
function drawBushR(ctx, x, y) {
  ctx.fillStyle = '#00a800';
  ctx.beginPath();
  ctx.arc(x + px(6), y + px(10), px(10), Math.PI, 0);
  ctx.fill();
  ctx.fillRect(x, y + px(10), T, px(6));
}

// ============================================================
//  Entity Sprites
// ============================================================

/**
 * Draw Mario (small or big, facing direction, animation frame).
 */
export function drawMario(ctx, x, y, w, h, {
  big = false,
  facingRight = true,
  frame = 0,
  dead = false,
  fire = false,
  invincible = false,
  star = false,
} = {}) {
  if (invincible && Math.floor(Date.now() / 80) % 2 === 0) return;

  ctx.save();
  if (!facingRight) {
    ctx.translate(x + w, y);
    ctx.scale(-1, 1);
    x = 0; y = 0;
  }

  const hatColor  = fire ? '#fcfcfc' : '#e40058';
  const shirtColor = fire ? '#e40058' : '#e40058';
  const overallColor = fire ? '#0000e4' : '#0000e4';
  const skinColor = '#fca044';
  const hairColor = '#7c3410';

  if (dead) {
    // Dead Mario — flat sprite
    _drawMarioSmallDead(ctx, x, y, w, h, hatColor, skinColor, overallColor);
    ctx.restore();
    return;
  }

  if (big) {
    _drawMarioBig(ctx, x, y, w, h, frame, hatColor, skinColor, overallColor, hairColor);
  } else {
    _drawMarioSmall(ctx, x, y, w, h, frame, hatColor, skinColor, overallColor, hairColor);
  }

  ctx.restore();
}

function _drawMarioSmall(ctx, x, y, w, h, frame, hat, skin, overall, hair) {
  const run = frame % 3;
  // Hat
  rect(ctx, hat,     x + px(3), y,        px(10), px(4));
  rect(ctx, hat,     x + px(1), y + px(4), px(14), px(3));
  // Face
  rect(ctx, skin,    x + px(3), y + px(4), px(10), px(5));
  // Eyes
  rect(ctx, hair,    x + px(9), y + px(5), px(3),  px(2));
  // Mustache
  rect(ctx, hair,    x + px(5), y + px(8), px(8),  px(2));
  // Body
  rect(ctx, overall, x + px(2), y + px(9), px(12), px(5));
  // Legs
  if (run === 0) {
    rect(ctx, overall, x + px(2), y + px(14), px(5), px(2));
    rect(ctx, overall, x + px(9), y + px(14), px(5), px(2));
  } else if (run === 1) {
    rect(ctx, overall, x + px(1), y + px(13), px(5), px(3));
    rect(ctx, overall, x + px(10),y + px(14), px(5), px(2));
  } else {
    rect(ctx, overall, x + px(2), y + px(14), px(5), px(2));
    rect(ctx, overall, x + px(10),y + px(13), px(5), px(3));
  }
  // Shoes
  rect(ctx, hair,    x + px(1), y + px(14), px(6), px(2));
  rect(ctx, hair,    x + px(9), y + px(14), px(6), px(2));
}

function _drawMarioBig(ctx, x, y, w, h, frame, hat, skin, overall, hair) {
  const run = frame % 3;
  // Hat
  rect(ctx, hat,     x + px(3), y,         px(10), px(5));
  rect(ctx, hat,     x + px(1), y + px(5),  px(14), px(3));
  // Hair
  rect(ctx, hair,    x + px(1), y + px(5),  px(3),  px(3));
  // Face
  rect(ctx, skin,    x + px(3), y + px(5),  px(10), px(7));
  // Eyes
  rect(ctx, hair,    x + px(9), y + px(7),  px(3),  px(2));
  // Mustache
  rect(ctx, hair,    x + px(4), y + px(11), px(9),  px(2));
  // Shirt
  rect(ctx, hat,     x + px(2), y + px(12), px(12), px(4));
  // Overalls
  rect(ctx, overall, x + px(1), y + px(16), px(14), px(6));
  // Straps
  rect(ctx, overall, x + px(3), y + px(12), px(3),  px(4));
  rect(ctx, overall, x + px(10),y + px(12), px(3),  px(4));
  // Legs
  if (run === 0) {
    rect(ctx, overall, x + px(2), y + px(22), px(5), px(4));
    rect(ctx, overall, x + px(9), y + px(22), px(5), px(4));
  } else if (run === 1) {
    rect(ctx, overall, x + px(1), y + px(21), px(5), px(5));
    rect(ctx, overall, x + px(10),y + px(22), px(5), px(4));
  } else {
    rect(ctx, overall, x + px(2), y + px(22), px(5), px(4));
    rect(ctx, overall, x + px(10),y + px(21), px(5), px(5));
  }
  // Shoes
  rect(ctx, hair,    x + px(1), y + px(24), px(6), px(3));
  rect(ctx, hair,    x + px(9), y + px(24), px(6), px(3));
}

function _drawMarioSmallDead(ctx, x, y, w, h, hat, skin, overall) {
  rect(ctx, hat,     x + px(3), y,        px(10), px(4));
  rect(ctx, skin,    x + px(3), y + px(4), px(10), px(5));
  rect(ctx, overall, x + px(2), y + px(9), px(12), px(7));
}

// ── Goomba ─────────────────────────────────────────────────
export function drawGoomba(ctx, x, y, w, h, frame = 0, squished = false) {
  if (squished) {
    rect(ctx, '#a85400', x + px(1), y + h - px(6), w - px(2), px(6));
    rect(ctx, '#000',    x + px(2), y + h - px(5), px(4), px(3));
    rect(ctx, '#000',    x + w - px(6), y + h - px(5), px(4), px(3));
    return;
  }
  const walk = frame % 2;
  // Body
  rect(ctx, '#a85400', x + px(1), y + px(4), w - px(2), h - px(8));
  // Head
  rect(ctx, '#a85400', x, y, w, px(10));
  // Eyes
  rect(ctx, '#fcfcfc', x + px(2), y + px(2), px(5), px(5));
  rect(ctx, '#fcfcfc', x + w - px(7), y + px(2), px(5), px(5));
  rect(ctx, '#000',    x + px(3), y + px(3), px(3), px(3));
  rect(ctx, '#000',    x + w - px(6), y + px(3), px(3), px(3));
  // Angry brows
  ctx.fillStyle = '#000';
  ctx.beginPath();
  ctx.moveTo(x + px(1), y + px(2));
  ctx.lineTo(x + px(7), y + px(4));
  ctx.lineWidth = px(1.5);
  ctx.strokeStyle = '#000';
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(x + w - px(1), y + px(2));
  ctx.lineTo(x + w - px(7), y + px(4));
  ctx.stroke();
  // Feet
  if (walk === 0) {
    rect(ctx, '#7c3410', x,          y + h - px(4), px(7), px(4));
    rect(ctx, '#7c3410', x + w - px(7), y + h - px(4), px(7), px(4));
  } else {
    rect(ctx, '#7c3410', x + px(2),  y + h - px(4), px(7), px(4));
    rect(ctx, '#7c3410', x + w - px(9), y + h - px(4), px(7), px(4));
  }
}

// ── Koopa ──────────────────────────────────────────────────
export function drawKoopa(ctx, x, y, w, h, frame = 0, inShell = false) {
  if (inShell) {
    // Shell
    rect(ctx, '#00a800', x + px(1), y + px(2), w - px(2), h - px(4));
    rect(ctx, '#fcfcfc', x + px(3), y + px(4), w - px(6), h - px(8));
    // Shell pattern
    ctx.strokeStyle = '#006800';
    ctx.lineWidth = px(1);
    ctx.beginPath();
    ctx.moveTo(x + T/2, y + px(4));
    ctx.lineTo(x + T/2, y + h - px(4));
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x + px(3), y + h/2);
    ctx.lineTo(x + w - px(3), y + h/2);
    ctx.stroke();
    return;
  }
  const walk = frame % 2;
  // Shell on back
  rect(ctx, '#00a800', x + px(2), y + px(8), w - px(4), h - px(12));
  // Head
  rect(ctx, '#00c800', x + px(3), y, w - px(6), px(10));
  // Eyes
  rect(ctx, '#fcfcfc', x + px(4), y + px(2), px(4), px(4));
  rect(ctx, '#000',    x + px(5), y + px(3), px(2), px(2));
  // Neck
  rect(ctx, '#00c800', x + px(4), y + px(8), w - px(8), px(4));
  // Feet
  if (walk === 0) {
    rect(ctx, '#c8a000', x + px(1), y + h - px(5), px(6), px(5));
    rect(ctx, '#c8a000', x + w - px(7), y + h - px(5), px(6), px(5));
  } else {
    rect(ctx, '#c8a000', x + px(3), y + h - px(5), px(6), px(5));
    rect(ctx, '#c8a000', x + w - px(9), y + h - px(5), px(6), px(5));
  }
}

// ── Power-up Sprites ───────────────────────────────────────
export function drawMushroom(ctx, x, y, w, h) {
  // Cap
  rect(ctx, '#e40058', x, y, w, h * 0.6);
  // White dots
  rect(ctx, '#fcfcfc', x + px(2), y + px(2), px(4), px(4));
  rect(ctx, '#fcfcfc', x + w - px(6), y + px(2), px(4), px(4));
  // Stem
  rect(ctx, '#fca044', x + px(2), y + h * 0.55, w - px(4), h * 0.45);
  // Eyes
  rect(ctx, '#000', x + px(4), y + h * 0.55 + px(2), px(2), px(3));
  rect(ctx, '#000', x + w - px(6), y + h * 0.55 + px(2), px(2), px(3));
}

export function drawFireFlower(ctx, x, y, w, h) {
  // Stem
  rect(ctx, '#00a800', x + w/2 - px(2), y + h * 0.4, px(4), h * 0.6);
  // Leaves
  rect(ctx, '#00a800', x + px(2), y + h * 0.5, px(6), px(4));
  rect(ctx, '#00a800', x + w - px(8), y + h * 0.5, px(6), px(4));
  // Petals
  const cx = x + w/2;
  const cy = y + h * 0.3;
  const r  = px(7);
  ctx.fillStyle = '#e40058';
  ctx.beginPath(); ctx.arc(cx, cy - r*0.6, r*0.5, 0, Math.PI*2); ctx.fill();
  ctx.beginPath(); ctx.arc(cx + r*0.6, cy, r*0.5, 0, Math.PI*2); ctx.fill();
  ctx.beginPath(); ctx.arc(cx - r*0.6, cy, r*0.5, 0, Math.PI*2); ctx.fill();
  ctx.beginPath(); ctx.arc(cx, cy + r*0.6, r*0.5, 0, Math.PI*2); ctx.fill();
  // Centre
  ctx.fillStyle = '#fcd800';
  ctx.beginPath(); ctx.arc(cx, cy, r*0.4, 0, Math.PI*2); ctx.fill();
}

export function drawStar(ctx, x, y, w, h, frame = 0) {
  const cx = x + w/2;
  const cy = y + h/2;
  const r  = Math.min(w, h) / 2 - px(1);
  const colors = ['#fcd800','#fcfc00','#e8c000'];
  ctx.fillStyle = colors[frame % colors.length];
  ctx.beginPath();
  for (let i = 0; i < 5; i++) {
    const angle = (i * 4 * Math.PI / 5) - Math.PI / 2;
    const ir    = r * 0.4;
    const ia    = angle + Math.PI / 5;
    if (i === 0) ctx.moveTo(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
    else         ctx.lineTo(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
    ctx.lineTo(cx + ir * Math.cos(ia), cy + ir * Math.sin(ia));
  }
  ctx.closePath();
  ctx.fill();
}

export function drawOneUp(ctx, x, y, w, h) {
  // Green mushroom
  rect(ctx, '#00a800', x, y, w, h * 0.6);
  rect(ctx, '#fcfcfc', x + px(2), y + px(2), px(4), px(4));
  rect(ctx, '#fcfcfc', x + w - px(6), y + px(2), px(4), px(4));
  rect(ctx, '#fca044', x + px(2), y + h * 0.55, w - px(4), h * 0.45);
  // "1UP" text
  ctx.fillStyle = '#fcfcfc';
  ctx.font = `bold ${px(5)}px monospace`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('1UP', x + w/2, y + h * 0.75);
}

export function drawCoin(ctx, x, y, w, h, frame = 0) {
  const cx = x + w/2;
  const cy = y + h/2;
  const scaleX = Math.abs(Math.cos(frame * 0.15));
  ctx.fillStyle = '#fcd800';
  ctx.beginPath();
  ctx.ellipse(cx, cy, (w/2 - px(2)) * scaleX, h/2 - px(2), 0, 0, Math.PI*2);
  ctx.fill();
  if (scaleX > 0.3) {
    ctx.fillStyle = '#fcfc00';
    ctx.beginPath();
    ctx.ellipse(cx - px(1), cy, (w/4) * scaleX, h/2 - px(4), 0, 0, Math.PI*2);
    ctx.fill();
  }
}

export function drawFireball(ctx, x, y, w, h, frame = 0) {
  const cx = x + w/2;
  const cy = y + h/2;
  const r  = Math.min(w, h) / 2;
  ctx.fillStyle = frame % 2 === 0 ? '#fcfcfc' : '#fcd800';
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI*2);
  ctx.fill();
  ctx.fillStyle = '#e40058';
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.5, 0, Math.PI*2);
  ctx.fill();
}

// ── Flag ───────────────────────────────────────────────────
export function drawFlag(ctx, x, y, w, h) {
  rect(ctx, '#00a800', x, y, w, h);
  rect(ctx, '#00d800', x, y, px(2), h);
}

// ── Score Pop ──────────────────────────────────────────────
export function drawScorePop(ctx, x, y, value, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.fillStyle = '#fcfcfc';
  ctx.font = `bold ${px(6)}px monospace`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(String(value), x, y);
  ctx.restore();
}

// ── Particle ───────────────────────────────────────────────
export function drawParticle(ctx, x, y, size, color, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.fillRect(x - size/2, y - size/2, size, size);
  ctx.restore();
}