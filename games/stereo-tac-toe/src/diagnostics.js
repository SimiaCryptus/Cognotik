// Renders the raw depth map as grayscale for inspection.

export class Diagnostics {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.imageData = new ImageData(width, height);
  }
  resize(width, height) {
    this.width = width;
    this.height = height;
    this.imageData = new ImageData(width, height);
  }

  drawDepth(ctx, depthBuffer) {
    const data = this.imageData.data;
    const n = this.width * this.height;
    for (let i = 0; i < n; i++) {
      const g = Math.max(0, Math.min(255, (depthBuffer[i] * 255) | 0));
      const idx = i * 4;
      data[idx] = g;
      data[idx + 1] = g;
      data[idx + 2] = g;
      data[idx + 3] = 255;
    }
    ctx.putImageData(this.imageData, 0, 0);
  }
}
