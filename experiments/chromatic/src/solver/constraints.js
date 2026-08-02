// Constraint primitives for the Designer (and, later, the DSL compiler).
//
// A constraint is a plain object with a `kind`, references into the point-set,
// and enough parameters to (a) contribute an error term to the objective and
// (b) report a residual for the error readout (designer.md §6.4).
//
// The point-set the solver operates on is an array of OKLab points
// ({ L, a, b }) indexed positionally. The Designer maps its node ids to those
// indices before solving.

const TAU = Math.PI * 2;

// Wrap an angle difference into [-PI, PI].
export function wrapAngle(d) {
  while (d > Math.PI) d -= TAU;
  while (d < -Math.PI) d += TAU;
  return d;
}

// --- coordinate helpers -------------------------------------------------

// Read a named working-space dimension out of an OKLab point.
// Supported dimensions: OKLab { L, a, b } and OKLch { lightness, chroma, hue }.
export function readDimension(oklab, dimension) {
  switch (dimension) {
    case 'L':
    case 'lightness':
      return oklab.L;
    case 'a':
      return oklab.a;
    case 'b':
      return oklab.b;
    case 'chroma':
      return Math.hypot(oklab.a, oklab.b);
    case 'hue': {
      let h = Math.atan2(oklab.b, oklab.a);
      if (h < 0) h += TAU;
      return h; // radians
    }
    default:
      throw new Error(`readDimension: unknown dimension ${dimension}`);
  }
}

// Euclidean length of a link between two OKLab points, measured in the
// plane spanned by the two chosen axes, or in full 3D.
// `axes` is an array of dimension names (2 for a plane, or omitted for 3D).
export function linkLength(pa, pb, axes) {
  if (!axes) {
    const dL = pa.L - pb.L;
    const da = pa.a - pb.a;
    const db = pa.b - pb.b;
    return Math.sqrt(dL * dL + da * da + db * db);
  }
  let sum = 0;
  for (const axis of axes) {
    const d = readDimension(pa, axis) - readDimension(pb, axis);
    sum += d * d;
  }
  return Math.sqrt(sum);
}

// Angle of a link in the 2D viewing plane, measured from node a to node b.
// `axes` is [xAxis, yAxis]; returns radians in (-PI, PI].
export function linkAngle(pa, pb, axes) {
  const [xAxis, yAxis] = axes;
  const dx = readDimension(pb, xAxis) - readDimension(pa, xAxis);
  const dy = readDimension(pb, yAxis) - readDimension(pa, yAxis);
  return Math.atan2(dy, dx);
}

// --- constraint error terms --------------------------------------------

// Soft anchor: w * (value - target)^2 on a single node's dimension.
export function anchorError(points, constraint) {
  const p = points[constraint.node];
  const value = readDimension(p, constraint.dimension);
  const w = constraint.weight ?? 1;
  const diff = value - constraint.target;
  return w * diff * diff;
}
// Length bounds: hinge penalties keeping a link's length within
// [minLength, maxLength]. Either bound may be null (unbounded on that side).
// Error is w * (violation)^2, summed over whichever bounds are active.
// `axes` matches linkLength: 2 axes for a plane, omitted for full 3D.
export function lengthBoundError(points, constraint, axes) {
  const len = linkLength(points[constraint.a], points[constraint.b], axes);
  const w = constraint.weight ?? 1;
  let sum = 0;
  if (constraint.maxLength != null) {
    const over = Math.max(0, len - constraint.maxLength);
    sum += over * over;
  }
  if (constraint.minLength != null) {
    const under = Math.max(0, constraint.minLength - len);
    sum += under * under;
  }
  return w * sum;
}
// Axis lock: a link should lie along a single axis, so every *other* axis
// delta between its endpoints should be zero. Error is w * sum of squared
// off-axis deltas. `spaceAxes` is the full list of working-space dimensions.
export function axisLockError(points, constraint, spaceAxes) {
  const pa = points[constraint.a];
  const pb = points[constraint.b];
  const w = constraint.weight ?? 1;
  let sum = 0;
  for (const dim of spaceAxes) {
    if (dim === constraint.lockAxis) continue;
    const d = readDimension(pb, dim) - readDimension(pa, dim);
    sum += d * d;
  }
  return w * sum;
}

// Length group: members should share a common length. When the group target
// is `fixed`, error is sum (len_i - target)^2; when `free`, error is the
// variance of member lengths (each measured against the mean).
export function lengthGroupError(points, group, axes) {
  const lens = group.links.map(([i, j]) => linkLength(points[i], points[j], axes));
  if (lens.length === 0) return 0;
  const w = group.weight ?? 1;
  let target;
  if (group.mode === 'fixed') {
    target = group.target;
  } else {
    target = lens.reduce((s, v) => s + v, 0) / lens.length;
  }
  let sum = 0;
  for (const len of lens) {
    const d = len - target;
    sum += d * d;
  }
  return w * sum;
}

// Angle group: members should share a common angle (circular). Free groups
// use the circular mean; fixed groups use the pinned target (radians).
export function angleGroupError(points, group, axes) {
  const angles = group.links.map(([i, j]) => linkAngle(points[i], points[j], axes));
  if (angles.length === 0) return 0;
  const w = group.weight ?? 1;
  let target;
  if (group.mode === 'fixed') {
    target = group.target;
  } else {
    // circular mean
    let sx = 0;
    let sy = 0;
    for (const a of angles) {
      sx += Math.cos(a);
      sy += Math.sin(a);
    }
    target = Math.atan2(sy, sx);
  }
  let sum = 0;
  for (const a of angles) {
    const d = wrapAngle(a - target);
    sum += d * d;
  }
  return w * sum;
}
