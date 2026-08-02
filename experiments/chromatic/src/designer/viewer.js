// Viewer math for the Designer spectrum slice (designer.md §4).
//
// Maps between a node's OKLab coordinate and the 2D slice plane, resolves a
// slice-plane color for the backdrop, and provides accurate node colors.

import { oklabToOklch, oklchToOklab, oklabToRgb } from '../colorspace/oklab.js';
import { isInGamut, clipRgb } from '../project/gamut.js';

const DEG2RAD = Math.PI / 180;

// Axis ranges for OKLch/OKLab dimensions, used to map plane axes to [0,1].
export const AXIS_RANGE = {
  lightness: [0, 1],
  L: [0, 1],
  chroma: [0, 0.37],
  hue: [0, 360],
  a: [-0.4, 0.4],
  b: [-0.4, 0.4],
};

function toOklch(oklab) {
  return oklabToOklch(oklab);
}

// Read a working-space dimension from an OKLab coordinate.
export function readAxis(oklab, axis) {
  switch (axis) {
    case 'lightness':
    case 'L':
      return oklab.L;
    case 'a':
      return oklab.a;
    case 'b':
      return oklab.b;
    case 'chroma':
      return toOklch(oklab).C;
    case 'hue':
      return toOklch(oklab).H;
    default:
      throw new Error(`readAxis: unknown axis ${axis}`);
  }
}

// Construct an OKLab coordinate from working-space (x-axis, y-axis, depth).
// `space` chooses whether we build via OKLch or OKLab.
export function composeOklab({ planeAxes, depthAxis }, xVal, yVal, depthVal) {
  const dims = {};
  dims[planeAxes[0]] = xVal;
  dims[planeAxes[1]] = yVal;
  dims[depthAxis] = depthVal;

  // If any dimension is an OKLch coordinate, build via OKLch.
  const usesOklch = ['lightness', 'chroma', 'hue'].some((d) => d in dims);
  if (usesOklch) {
    return oklchToOklab({
      L: dims.lightness ?? dims.L ?? 0.6,
      C: dims.chroma ?? 0.1,
      H: dims.hue ?? 0,
    });
  }
  return {
    L: dims.L ?? 0.6,
    a: dims.a ?? 0,
    b: dims.b ?? 0,
  };
}

// The backdrop color at a given plane position (uses the slice's depth value).
export function sliceColorAt(viewer, xVal, yVal) {
  const oklab = composeOklab(viewer, xVal, yVal, viewer.depthValue);
  const rgb = oklabToRgb(oklab);
  return { rgb: clipRgb(rgb), inGamut: isInGamut(rgb), oklab };
}

// The accurate color of a node (its true 3D coordinate) — designer.md §4.2.
export function nodeColor(oklab) {
  const rgb = oklabToRgb(oklab);
  return { rgb: clipRgb(rgb), inGamut: isInGamut(rgb) };
}

// Normalize an axis value into [0,1] for screen mapping.
export function axisToUnit(axis, value) {
  const [lo, hi] = AXIS_RANGE[axis];
  return (value - lo) / (hi - lo);
}

export function unitToAxis(axis, unit) {
  const [lo, hi] = AXIS_RANGE[axis];
  return lo + unit * (hi - lo);
}

// Depth cue: how far a node's depth is from the current slice (0 = on-plane).
export function depthDelta(viewer, oklab) {
  return readAxis(oklab, viewer.depthAxis) - viewer.depthValue;
}
