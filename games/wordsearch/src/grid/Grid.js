// Lattice data structure.

export class Grid {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    /** @type {(string|null)[]} */
    this.cells = new Array(width * height).fill(null);
    /** @type {boolean[]} locked cells (target word letters) */
    this.locked = new Array(width * height).fill(false);
  }

  _idx(x, y) {
    return y * this.width + x;
  }

  inBounds(x, y) {
    return x >= 0 && y >= 0 && x < this.width && y < this.height;
  }

  get(x, y) {
    if (!this.inBounds(x, y)) return null;
    return this.cells[this._idx(x, y)];
  }

  set(x, y, ch) {
    if (!this.inBounds(x, y)) return false;
    this.cells[this._idx(x, y)] = ch;
    return true;
  }

  isLocked(x, y) {
    if (!this.inBounds(x, y)) return false;
    return this.locked[this._idx(x, y)];
  }

  lock(x, y) {
    if (this.inBounds(x, y)) this.locked[this._idx(x, y)] = true;
  }

  /** Iterate all empty (unset) cell coordinates. */
  *emptyCells() {
    for (let y = 0; y < this.height; y++) {
      for (let x = 0; x < this.width; x++) {
        if (!this.cells[this._idx(x, y)]) yield { x, y };
      }
    }
  }

  toStringGrid() {
    const rows = [];
    for (let y = 0; y < this.height; y++) {
      let row = '';
      for (let x = 0; x < this.width; x++) {
        row += this.get(x, y) || '.';
      }
      rows.push(row);
    }
    return rows.join('\n');
  }
}
