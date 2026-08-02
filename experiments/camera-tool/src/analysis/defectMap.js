// Defect map data structure & serialization (sparse coordinates).

export class DefectMap {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.pixels = []; // { x, y, type, confidence }
  }

  add(x, y, type, confidence) {
    this.pixels.push({ x, y, type, confidence });
  }

  get count() {
    return this.pixels.length;
  }

  countByType() {
    const out = { dead: 0, stuck: 0, hot: 0, noisy: 0 };
    for (const p of this.pixels) {
      out[p.type] = (out[p.type] || 0) + 1;
    }
    return out;
  }

  // Build a fast lookup mask (Uint8Array) for correction passes.
  toMask() {
    const mask = new Uint8Array(this.width * this.height);
    for (const p of this.pixels) {
      mask[p.y * this.width + p.x] = 1;
    }
    return mask;
  }

  toJSON() {
    return {
      encoding: 'sparse-coords',
      count: this.pixels.length,
      pixels: this.pixels,
    };
  }

  static fromJSON(width, height, json) {
    const map = new DefectMap(width, height);
    if (json && Array.isArray(json.pixels)) {
      map.pixels = json.pixels.slice();
    }
    return map;
  }
}
