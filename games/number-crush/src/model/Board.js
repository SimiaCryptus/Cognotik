// Board holds the tiles keyed by coordinate, over a HexGrid.

import { HexGrid } from './HexGrid.js';
import { Tile } from './Tile.js';
import { keyOf, coordKey } from './coords.js';

export class Board {
  constructor({ cols, rows, size, valueRange = [1, 9], rng = Math.random, suppressZero = false }) {
    this.grid = new HexGrid({ cols, rows, size });
    this.valueRange = valueRange;
    this.rng = rng;
    this.suppressZero = suppressZero;
    this.tiles = new Map(); // key -> Tile
    this._fill();
  }

  _randomValue() {
    const [min, max] = this.valueRange;
    let v = min + Math.floor(this.rng() * (max - min + 1));
    if (this.suppressZero && v === 0) {
      // Re-roll until nonzero (guard against range being only {0}).
      if (!(min === 0 && max === 0)) {
        while (v === 0) {
          v = min + Math.floor(this.rng() * (max - min + 1));
        }
      }
    }
    return v;
  }

  _fill() {
    for (const cell of this.grid.allCells()) {
      const tile = new Tile(this._randomValue(), cell);
      this.tiles.set(keyOf(cell.q, cell.r), tile);
    }
  }

  getTile(coord) {
    return this.tiles.get(coordKey(coord)) || null;
  }

  // In-bounds neighbor coordinates for a tile.
  neighborCoords(coord) {
    return this.grid.neighbors(coord);
  }

  forEachTile(fn) {
    for (const tile of this.tiles.values()) fn(tile);
  }
}
