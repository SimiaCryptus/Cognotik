// Per-pixel streaming statistics over a sequence of frames.
// Works on luminance to keep memory reasonable (4 Float32 arrays).
import { luma } from '../util/imageData.js';

export class Accumulator {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    const n = width * height;
    this.count = 0;
    this.sum = new Float64Array(n); // sum of luma
    this.sumSq = new Float64Array(n); // sum of luma^2
    this.min = new Float32Array(n).fill(255);
    this.max = new Float32Array(n).fill(0);
    // Track value during "dark" frames separately for hot-pixel detection.
    this.darkMax = new Float32Array(n).fill(0);
    this.darkCount = 0;
  }

  reset() {
    const n = this.width * this.height;
    this.count = 0;
    this.darkCount = 0;
    this.sum.fill(0);
    this.sumSq.fill(0);
    this.min.fill(255);
    this.max.fill(0);
    this.darkMax.fill(0);
  }

  // Add one frame. `mode` is 'dark' | 'flat' | 'mixed'.
  addFrame(imageData, mode) {
    const { width, height } = this;
    if (imageData.width !== width || imageData.height !== height) {
      throw new Error('Frame dimensions changed mid-sequence.');
    }
    const data = imageData.data;
    const isDark = mode === 'dark';
    for (let i = 0, p = 0; i < data.length; i += 4, p++) {
      const l = luma(data[i], data[i + 1], data[i + 2]);
      this.sum[p] += l;
      this.sumSq[p] += l * l;
      if (l < this.min[p]) this.min[p] = l;
      if (l > this.max[p]) this.max[p] = l;
      if (isDark && l > this.darkMax[p]) this.darkMax[p] = l;
    }
    this.count++;
    if (isDark) this.darkCount++;
  }

  meanAt(p) {
    return this.count ? this.sum[p] / this.count : 0;
  }
}
