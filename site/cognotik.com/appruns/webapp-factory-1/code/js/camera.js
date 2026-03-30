// ============================================================
//  Super Mario Bros Clone — Camera
//  Follows the player horizontally, clamped to level bounds.
// ============================================================

import { CANVAS_WIDTH, CANVAS_HEIGHT } from './constants.js';

export class Camera {
  /**
   * @param {number} levelWidthPx  Total level width in world pixels
   * @param {number} levelHeightPx Total level height in world pixels
   */
  constructor(levelWidthPx, levelHeightPx) {
    this.x = 0;
    this.y = 0;
     this.width  = CANVAS_WIDTH;
     this.height = CANVAS_HEIGHT;
    this.levelW = levelWidthPx;
    this.levelH = levelHeightPx;
  }

  /**
   * Smoothly follow a target world-space rectangle.
   * @param {{ x:number, y:number, width:number, height:number }} target
   */
  follow(target) {
    // Centre camera on target horizontally
    const targetX = target.x + target.width  / 2 - this.width  / 2;
    const targetY = target.y + target.height / 2 - this.height / 2;

    // Horizontal: never scroll left of start, never past level end
    this.x = Math.max(0, Math.min(targetX, this.levelW - this.width));

    // Vertical: keep fixed (classic Mario doesn't scroll vertically much)
    this.y = Math.max(0, Math.min(targetY, this.levelH - this.height));
  }

  /**
   * Convert world X to screen X.
   * @param {number} worldX
   */
  toScreenX(worldX) { return worldX - this.x; }

  /**
   * Convert world Y to screen Y.
   * @param {number} worldY
   */
  toScreenY(worldY) { return worldY - this.y; }

  /**
   * Returns true if a world-space rect is visible on screen.
   * @param {number} wx  World X
   * @param {number} wy  World Y
   * @param {number} w   Width
   * @param {number} h   Height
   */
  isVisible(wx, wy, w, h) {
    return (
      wx + w > this.x &&
      wx     < this.x + this.width &&
      wy + h > this.y &&
      wy     < this.y + this.height
    );
  }
}
const SCALE = 3;