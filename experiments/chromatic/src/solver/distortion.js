// DistortionReport computation (spec §4.3, §7.4).
//
// Given a source Palette (OKLab) and a projected palette (target space), it
// quantifies, per invariant family, how much relational structure survived
// the projection.

import { deltaEOK } from '../colorspace/distance.js';
import { knn, edgeEditDistance } from '../geometry/graph.js';

// --- helpers ---

// Pairwise distance matrix over a list of items using a distance function.
function distanceMatrix(items, distFn) {
  const n = items.length;
  const flat = [];
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      flat.push(distFn(items[i], items[j]));
    }
  }
  return flat;
}

// Euclidean distance in a projected color's canonical (L/C/H) space,
// handling hue wraparound.
function projectedDistance(a, b) {
  const dL = (a.lightness ?? 0) - (b.lightness ?? 0);
  const dC = (a.chroma ?? 0) - (b.chroma ?? 0);
  let dH = 0;
  if (a.hue != null && b.hue != null) {
    dH = Math.abs(a.hue - b.hue);
    if (dH > 180) dH = 360 - dH;
    // scale hue degrees down to be comparable to L/C magnitudes
    dH /= 360;
  }
  return Math.sqrt(dL * dL + dC * dC + dH * dH);
}

// Pearson correlation between two equal-length arrays.
function correlation(xs, ys) {
  const n = xs.length;
  if (n === 0) return 1;
  const mean = (a) => a.reduce((s, v) => s + v, 0) / a.length;
  const mx = mean(xs);
  const my = mean(ys);
  let num = 0;
  let dx2 = 0;
  let dy2 = 0;
  for (let i = 0; i < n; i++) {
    const dx = xs[i] - mx;
    const dy = ys[i] - my;
    num += dx * dy;
    dx2 += dx * dx;
    dy2 += dy * dy;
  }
  const denom = Math.sqrt(dx2 * dy2);
  return denom === 0 ? 1 : num / denom;
}

// Rank order of a numeric accessor across items; returns index -> rank.
function rankOrder(values) {
  const idx = [...values.keys()].sort((a, b) => values[a] - values[b]);
  const rank = new Array(values.length);
  idx.forEach((originalIndex, r) => {
    rank[originalIndex] = r;
  });
  return rank;
}

// Count pairs whose relative order flips between two value arrays.
function orderingViolations(sourceVals, targetVals) {
  const n = sourceVals.length;
  let violations = 0;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const sOrder = Math.sign(sourceVals[i] - sourceVals[j]);
      const tOrder = Math.sign(targetVals[i] - targetVals[j]);
      if (sOrder !== 0 && tOrder !== 0 && sOrder !== tOrder) violations++;
    }
  }
  return violations;
}

// Circular hue difference in degrees.
function hueDiff(a, b) {
  if (a == null || b == null) return 0;
  let d = Math.abs(a - b);
  if (d > 180) d = 360 - d;
  return d;
}

/**
 * Compute a distortion report comparing an OKLab source palette against its
 * projection into a target space.
 *
 * @param {object} sourcePalette   { colors: [{ id, L, a, b }] } (OKLab).
 * @param {object} projected       Output of project(): { space, colors: [...] }.
 * @param {object} [options]
 * @param {number} [options.k=2]   k for the adjacency-graph comparison.
 * @returns {DistortionReport}
 */
export function distortionReport(sourcePalette, projected, options = {}) {
  const k = options.k ?? 2;
  const srcColors = sourcePalette.colors ?? sourcePalette;
  const tgtColors = projected.colors;
  const n = srcColors.length;

  // --- metric distortion: correlate distance matrices ---
  const srcDist = distanceMatrix(srcColors, (a, b) =>
    deltaEOK({ L: a.L, a: a.a, b: a.b }, { L: b.L, a: b.a, b: b.b })
  );
  const tgtDist = distanceMatrix(tgtColors, projectedDistance);
  const distanceCorrelation = correlation(srcDist, tgtDist);

  // Average absolute ratio deviation from a best-fit scale.
  const scale = srcDist.reduce((s, v) => s + v, 0) / (tgtDist.reduce((s, v) => s + v, 0) || 1);
  let distanceRatioError = 0;
  for (let i = 0; i < srcDist.length; i++) {
    const predicted = tgtDist[i] * scale;
    distanceRatioError += Math.abs(predicted - srcDist[i]);
  }
  distanceRatioError /= srcDist.length || 1;

  // --- ordering distortion ---
  const srcL = srcColors.map((c) => c.L);
  const tgtL = tgtColors.map((c) => c.lightness);
  const srcC = srcColors.map((c) => Math.hypot(c.a, c.b));
  const tgtC = tgtColors.map((c) => c.chroma);
  const srcH = srcColors.map((c) => {
    let h = (Math.atan2(c.b, c.a) * 180) / Math.PI;
    if (h < 0) h += 360;
    return h;
  });
  const tgtH = tgtColors.map((c) => c.hue);

  const orderingLightness = orderingViolations(srcL, tgtL);
  const orderingChroma = orderingViolations(srcC, tgtC);
  const orderingViolationsTotal = orderingLightness + orderingChroma;

  // --- hue distortion ---
  let hueDistortions = [];
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const srcDelta = hueDiff(srcH[i], srcH[j]);
      const tgtDelta = hueDiff(tgtH[i], tgtH[j]);
      // normalize to fraction of a full turn
      hueDistortions.push(Math.abs(srcDelta - tgtDelta) / 360);
    }
  }
  const avgHueDistortion =
    hueDistortions.length === 0
      ? 0
      : hueDistortions.reduce((s, v) => s + v, 0) / hueDistortions.length;

  // --- adjacency-graph edit distance ---
  const srcOklab = srcColors.map((c) => ({ L: c.L, a: c.a, b: c.b }));
  const tgtVec = tgtColors.map((c) => ({
    L: c.lightness ?? 0,
    a: c.chroma ?? 0,
    // encode hue as a coordinate so knn is meaningful in target space
    b: c.hue != null ? c.hue / 360 : 0,
  }));
  const srcAdj = knn(srcOklab, k);
  const tgtAdj = knn(tgtVec, k);
  const adjacencyEditDistance = edgeEditDistance(srcAdj, tgtAdj);

  // --- gamut clipping incidence ---
  const clippedCount = tgtColors.filter((c) => c.clipped).length;
  const gamutClipped = projected.gamutClippedFraction ?? (n === 0 ? 0 : clippedCount / n);

  const report = {
    space: projected.space,
    orderingViolations: orderingViolationsTotal,
    orderingLightness,
    orderingChroma,
    avgHueDistortion,
    distanceCorrelation,
    distanceRatioError,
    adjacencyEditDistance,
    gamutClipped,
    summary() {
      return {
        orderingViolations: this.orderingViolations,
        avgHueDistortion: Number(this.avgHueDistortion.toFixed(4)),
        distanceCorrelation: Number(this.distanceCorrelation.toFixed(4)),
        adjacencyEditDistance: this.adjacencyEditDistance,
        gamutClipped: Number(this.gamutClipped.toFixed(4)),
      };
    },
  };
  return report;
}
