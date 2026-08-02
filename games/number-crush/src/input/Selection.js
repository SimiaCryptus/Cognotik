// Selection manages the in-progress line of tiles.
//
// Rules enforced:
//  - Contiguous: each new coord must be a hex-neighbor of the previous.
//  - Non-repeating: a tile can only appear once.
//  - Bounded length: between minLength and maxLength.

import { areNeighbors, coordKey } from '../model/coords.js';

export class Selection {
  constructor({ minLength = 2, maxLength = 4, straightLine = false } = {}) {
    this.minLength = minLength;
    this.maxLength = maxLength;
    this.straightLine = straightLine;
    this.coords = []; // array of {q, r}
    this._set = new Set();
  }

  get length() {
    return this.coords.length;
  }

  isEmpty() {
    return this.coords.length === 0;
  }

  contains(coord) {
    return this._set.has(coordKey(coord));
  }

  last() {
    return this.coords[this.coords.length - 1] || null;
  }

  canAdd(coord) {
    if (this.coords.length >= this.maxLength) return false;
    if (this.contains(coord)) return false;
    if (this.coords.length === 0) return true;
    if (!areNeighbors(this.last(), coord)) return false;
    if (this.straightLine && this.coords.length >= 2) {
      const dir = this._lineDirection(coord);
      if (!dir) return false;
    }
    return true;
  }
  // When enforcing straight lines, every step must follow the same axial
  // direction established by the first two tiles. Returns the step
  // direction for `coord` if it is legal, otherwise null.
  _lineDirection(coord) {
    const last = this.last();
    const step = { q: coord.q - last.q, r: coord.r - last.r };
    // First step defines the line's direction.
    if (this.coords.length === 1) return step;
    const base = {
      q: this.coords[1].q - this.coords[0].q,
      r: this.coords[1].r - this.coords[0].r,
    };
    if (step.q === base.q && step.r === base.r) return step;
    return null;
  }

  // Attempt to add a coordinate. Returns true if added.
  add(coord) {
    if (!this.canAdd(coord)) return false;
    this.coords.push({ q: coord.q, r: coord.r });
    this._set.add(coordKey(coord));
    return true;
  }

  // Support backtracking: if the coord is the second-to-last tile,
  // remove the last one (natural drag-back behavior).
  tryBacktrack(coord) {
    if (this.coords.length < 2) return false;
    const prev = this.coords[this.coords.length - 2];
    if (prev.q === coord.q && prev.r === coord.r) {
      const removed = this.coords.pop();
      this._set.delete(coordKey(removed));
      return true;
    }
    return false;
  }

  hasValidLength() {
    return this.coords.length >= this.minLength && this.coords.length <= this.maxLength;
  }

  clear() {
    this.coords = [];
    this._set.clear();
  }
}
