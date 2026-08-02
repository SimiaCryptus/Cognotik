#!/usr/bin/env node
/* ─────────────────────────────────────────────────────────────
     Generate og-image.png (1200x630) for social sharing.
     Uses the `canvas` dependency already in package.json.
     Usage: node scripts/generate-og-image.js
     ───────────────────────────────────────────────────────────── */

import { createCanvas } from 'canvas';
import { promises as fs } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');

const W = 1200;
const H = 630;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext('2d');

// Dark gradient background
const bg = ctx.createLinearGradient(0, 0, W, H);
bg.addColorStop(0, '#0a0a14');
bg.addColorStop(0.5, '#10101f');
bg.addColorStop(1, '#0a0a14');
ctx.fillStyle = bg;
ctx.fillRect(0, 0, W, H);

// Aurora-ish blobs
function blob(x, y, r, color) {
  const g = ctx.createRadialGradient(x, y, 0, x, y, r);
  g.addColorStop(0, color);
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
}
blob(220, 160, 360, 'rgba(70,120,255,0.35)');
blob(980, 500, 420, 'rgba(150,80,255,0.30)');

// Big sigma glyph
ctx.fillStyle = 'rgba(255,255,255,0.10)';
ctx.font = 'bold 520px serif';
ctx.textAlign = 'center';
ctx.textBaseline = 'middle';
ctx.fillText('∑', W - 200, H / 2 + 20);

// Title
ctx.fillStyle = '#ffffff';
ctx.textAlign = 'left';
ctx.font = 'bold 84px sans-serif';
ctx.fillText('Mathematical', 90, 250);
ctx.fillStyle = '#9db4ff';
ctx.fillText('Explorations', 90, 350);

// Subtitle
ctx.fillStyle = 'rgba(255,255,255,0.72)';
ctx.font = '32px sans-serif';
ctx.fillText('Interactive math research · runs in the browser', 92, 430);

const out = path.join(ROOT, 'og-image.png');
const buf = canvas.toBuffer('image/png');
await fs.writeFile(out, buf);
console.log(`✓ og-image.png written (${W}x${H})`);
