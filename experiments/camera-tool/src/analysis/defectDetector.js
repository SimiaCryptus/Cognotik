// Classification heuristics & confidence scoring over an Accumulator.
import { DefectMap } from './defectMap.js';
import { varianceFromSums } from '../util/math.js';

export const DEFAULT_THRESHOLDS = {
  deadMax: 12, // max luma across all frames below this => dead
  varianceMin: 4, // temporal variance below this (with scene change) => stuck
  hotDelta: 60, // dark-frame luma exceeding neighborhood by this => hot
  neighborDev: 50, // deviation from neighborhood median across frames
  confidenceMin: 0.5, // minimum confidence to include in final map
};

// Compute a robust neighborhood mean (excluding center) from the per-pixel
// mean image. Returns a Float32Array.
function neighborhoodMean(meanImg, width, height) {
  const out = new Float32Array(width * height);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      let sum = 0;
      let n = 0;
      for (let dy = -1; dy <= 1; dy++) {
        const ny = y + dy;
        if (ny < 0 || ny >= height) continue;
        for (let dx = -1; dx <= 1; dx++) {
          if (dx === 0 && dy === 0) continue;
          const nx = x + dx;
          if (nx < 0 || nx >= width) continue;
          sum += meanImg[ny * width + nx];
          n++;
        }
      }
      out[y * width + x] = n ? sum / n : 0;
    }
  }
  return out;
}

// Run detection. Returns a DefectMap.
export function detect(acc, thresholds) {
  const t = Object.assign({}, DEFAULT_THRESHOLDS, thresholds || {});
  const { width, height, count, darkCount } = acc;
  const n = width * height;
  const map = new DefectMap(width, height);

  // Precompute per-pixel mean image for neighborhood comparison.
  const meanImg = new Float32Array(n);
  for (let p = 0; p < n; p++) meanImg[p] = count ? acc.sum[p] / count : 0;
  const nbMean = neighborhoodMean(meanImg, width, height);

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const p = y * width + x;
      const max = acc.max[p];
      const min = acc.min[p];
      const mean = meanImg[p];
      const varVal = varianceFromSums(acc.sum[p], acc.sumSq[p], count);
      const nb = nbMean[p];
      const nbDev = Math.abs(mean - nb);

      let type = null;
      let confidence = 0;

      // Dead: stays dark across the entire sequence even where neighbors
      // brighten (neighborhood notably brighter than this pixel).
      if (max <= t.deadMax && nb - mean > t.neighborDev) {
        type = 'dead';
        confidence = Math.min(
          1,
          (nb - mean) / (t.neighborDev * 2) + (t.deadMax - max) / (t.deadMax * 2 + 1)
        );
      }

      // Hot: bright in dark frames, far above neighborhood.
      if (!type && darkCount > 0 && acc.darkMax[p] - nb > t.hotDelta) {
        type = 'hot';
        confidence = Math.min(1, (acc.darkMax[p] - nb) / (t.hotDelta * 2));
      }

      // Stuck: very low temporal variance while neighborhood varies, and
      // the pixel value disagrees with its neighborhood.
      if (!type && count >= 3 && varVal < t.varianceMin && nbDev > t.neighborDev) {
        type = 'stuck';
        confidence = Math.min(
          1,
          ((t.varianceMin - varVal) / (t.varianceMin + 1)) * 0.5 + nbDev / (t.neighborDev * 2)
        );
      }

      // Noisy: large temporal swing far beyond neighborhood norms.
      if (!type && count >= 3) {
        const range = max - min;
        if (range > 120 && nbDev > t.neighborDev) {
          type = 'noisy';
          confidence = Math.min(1, (range / 255) * 0.5 + nbDev / (t.neighborDev * 2));
        }
      }

      if (type && confidence >= t.confidenceMin) {
        map.add(x, y, type, +confidence.toFixed(3));
      }
    }
  }

  return map;
}
