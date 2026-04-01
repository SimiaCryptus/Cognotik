// ============================================================
//  Super Mario Bros Clone — Tilemap
//  Renders the tile grid and provides collision queries.
// ============================================================

import { TILE_SIZE, SCALE, SOLID_TILES, TILE } from './constants.js';
import { drawTileSync } from './spritesheet.js';

const T = TILE_SIZE * SCALE;

export class Tilemap {
  /**
   * @param {number[][]} grid   2-D array [row][col] of tile IDs
   */
  constructor(grid) {
    this.grid = grid;
    this.rows = grid.length;
    this.cols = grid[0]?.length ?? 0;
    this.widthPx  = this.cols * T;
    this.heightPx = this.rows * T;
  }

  // ── Tile access ────────────────────────────────────────────

  getTile(col, row) {
    if (row < 0 || row >= this.rows || col < 0 || col >= this.cols) {
      return TILE.SOLID;   // treat out-of-bounds as solid
    }
    return this.grid[row][col];
  }

  setTile(col, row, id) {
    if (row >= 0 && row < this.rows && col >= 0 && col < this.cols) {
      this.grid[row][col] = id;
    }
  }

  isSolid(col, row) {
    return SOLID_TILES.has(this.getTile(col, row));
  }

  // ── World ↔ tile coordinate helpers ───────────────────────

  worldToTile(worldPx) { return Math.floor(worldPx / T); }
  tileToWorld(tile)    { return tile * T; }

  // ── Collision helpers ──────────────────────────────────────

  /**
   * Returns all solid tiles overlapping the given world-space AABB.
   * @returns {Array<{col,row,x,y,w,h}>}
   */
  getSolidTilesInRect(wx, wy, ww, wh) {
    const c0 = Math.max(0, this.worldToTile(wx));
    const c1 = Math.min(this.cols - 1, this.worldToTile(wx + ww - 1));
    const r0 = Math.max(0, this.worldToTile(wy));
    const r1 = Math.min(this.rows - 1, this.worldToTile(wy + wh - 1));

    const result = [];
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        if (this.isSolid(c, r)) {
         result.push({ col: c, row: r, x: c * T, y: r * T, w: T, h: T, width: T, height: T });
        }
      }
    }
    return result;
  }

  /**
   * Returns the tile ID at a world-space point.
   */
  getTileAtWorld(wx, wy) {
    return this.getTile(this.worldToTile(wx), this.worldToTile(wy));
  }

  // ── Rendering ──────────────────────────────────────────────

  /**
   * Draw only the tiles visible within the camera viewport.
   * @param {CanvasRenderingContext2D} ctx
   * @param {Camera} camera
   */
  render(ctx, camera) {
    const c0 = Math.max(0, Math.floor(camera.x / T));
    const c1 = Math.min(this.cols - 1, Math.ceil((camera.x + camera.width) / T));
    const r0 = Math.max(0, Math.floor(camera.y / T));
    const r1 = Math.min(this.rows - 1, Math.ceil((camera.y + camera.height) / T));

    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        const id = this.grid[r][c];
        if (id === TILE.AIR) continue;
        const sx = c * T - camera.x;
        const sy = r * T - camera.y;
        drawTileSync(ctx, id, sx, sy);
      }
    }
  }
}