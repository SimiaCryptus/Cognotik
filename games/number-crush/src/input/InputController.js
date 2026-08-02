// InputController translates pointer events on the canvas into
// selection actions. It is intentionally decoupled from resolution:
// it emits callbacks that the Game wires up.

import { pixelToAxial } from '../model/coords.js';

export class InputController {
  constructor(canvas, board, callbacks = {}) {
    this.canvas = canvas;
    this.board = board;
    this.callbacks = callbacks; // { onStart, onMove, onEnd }
    this._pointerDown = false;

    this._onDown = this._onDown.bind(this);
    this._onMove = this._onMove.bind(this);
    this._onUp = this._onUp.bind(this);

    canvas.addEventListener('pointerdown', this._onDown);
    canvas.addEventListener('pointermove', this._onMove);
    window.addEventListener('pointerup', this._onUp);
  }

  destroy() {
    this.canvas.removeEventListener('pointerdown', this._onDown);
    this.canvas.removeEventListener('pointermove', this._onMove);
    window.removeEventListener('pointerup', this._onUp);
  }

  _coordFromEvent(e) {
    const rect = this.canvas.getBoundingClientRect();
    // Account for CSS scaling of the canvas.
    const scaleX = this.canvas.width / rect.width;
    const scaleY = this.canvas.height / rect.height;
    const px = (e.clientX - rect.left) * scaleX - this.board.grid._offsetX;
    const py = (e.clientY - rect.top) * scaleY - this.board.grid._offsetY;
    const coord = pixelToAxial(px, py, this.board.grid.size);
    if (!this.board.grid.has(coord)) return null;
    return coord;
  }

  _onDown(e) {
    const coord = this._coordFromEvent(e);
    if (!coord) {
      console.log('[InputController] pointerdown outside grid');
      return;
    }
    this._pointerDown = true;
    this.canvas.setPointerCapture?.(e.pointerId);
    this.callbacks.onStart?.(coord);
  }

  _onMove(e) {
    if (!this._pointerDown) return;
    const coord = this._coordFromEvent(e);
    if (!coord) return;
    this.callbacks.onMove?.(coord);
  }

  _onUp() {
    if (!this._pointerDown) return;
    this._pointerDown = false;
    console.log('[InputController] pointerup - ending selection');
    this.callbacks.onEnd?.();
  }
}
