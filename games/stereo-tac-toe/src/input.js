// Maps mouse events on the canvas to grid cells and cursor pixels.

import { boardGeometry } from './config.js';

export class Input {
  constructor(canvas, game, onChange) {
    this.canvas = canvas;
    this.game = game;
    this.onChange = onChange; // called when cursor cell changes
    this.cursorPixel = null; // { x, y } in canvas coords, or null

    canvas.addEventListener('mousemove', (e) => this._onMove(e));
    canvas.addEventListener('mouseleave', () => this._onLeave());
    canvas.addEventListener('click', (e) => this._onClick(e));
  }

  // Convert a client event into canvas-space pixel coordinates,
  // accounting for CSS scaling of the canvas element.
  _toCanvas(e) {
    const rect = this.canvas.getBoundingClientRect();
    const scaleX = this.canvas.width / rect.width;
    const scaleY = this.canvas.height / rect.height;
    return {
      x: (e.clientX - rect.left) * scaleX,
      y: (e.clientY - rect.top) * scaleY,
    };
  }

  pixelToCell(x, y) {
    const { originX, originY, cellSize } = boardGeometry();
    const col = Math.floor((x - originX) / cellSize);
    const row = Math.floor((y - originY) / cellSize);
    if (col < 0 || col > 2 || row < 0 || row > 2) return null;
    return row * 3 + col;
  }

  _onMove(e) {
    const { x, y } = this._toCanvas(e);
    this.cursorPixel = { x, y };
    const cell = this.pixelToCell(x, y);
    if (cell !== this.game.cursor) {
      this.game.setCursor(cell);
      this.onChange();
    }
  }

  _onLeave() {
    this.cursorPixel = null;
    if (this.game.cursor !== null) {
      this.game.setCursor(null);
      this.onChange();
    }
  }

  _onClick(e) {
    const { x, y } = this._toCanvas(e);
    const cell = this.pixelToCell(x, y);
    if (cell !== null && this.game.move(cell)) {
      this.onChange();
    }
  }
}
