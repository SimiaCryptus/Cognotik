// A single tile: a value, a stable id, optional special attribute,
// and its coordinate on the board.

let _idCounter = 0;

export function nextTileId() {
  _idCounter += 1;
  return `t${_idCounter}`;
}

export class Tile {
  constructor(value, coord, special = null) {
    this.id = nextTileId();
    this.value = value;
    this.coord = { q: coord.q, r: coord.r };
    this.special = special; // 'wildcard' | 'multiplier' | 'bomb' | 'stone' | null
  }
}
