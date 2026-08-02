// affine.js
// Precomputed reference of affine transformation sets in algebraic form,
// one entry per polygon type. Each "move" is an edge-crossing generator
// normalized to a canonical compass order (North, then clockwise).
//
// The algebraic representation reuses field.js for n=5 (pentagon) where
// exact Q(sqrt5, S) arithmetic is available. For other n we store the
// float rotation angle and a symbolic label; the algebraic layer can be
// expanded later per polygon.
//
// Each affine generator g maps a tile frame (centroid c, orient o, sigma s)
// to a neighbor frame. We expose:
//   - rotationSteps: how many edge rotations relative to canonical North
//   - sheetDelta:    canonical sheet shift for THIS compass direction
//
// The key normalization rule: a "move" is indexed by its compass slot,
// not by the raw edge index. Edge index depends on orientation; compass
// slot does not. We compute compass slot = (edge - orient) mod n with the
// North edge at slot 0, then increasing clockwise.

import { K } from './field.js';

// Canonical compass labels for small n (purely informational).
const COMPASS = {
  3: ['N', 'SE', 'SW'],
  4: ['N', 'E', 'S', 'W'],
  5: ['N', 'NE', 'SE', 'SW', 'NW'],
  6: ['N', 'NE', 'SE', 'S', 'SW', 'NW'],
};

// Normalize a raw edge index to a compass slot given the tile orientation.
// North-then-clockwise: slot 0 is the edge that, in the tile's own frame,
// points most nearly "up" (+y). Because orient rotates the vertex frame by
// orient*(2pi/n), the canonical North edge of an oriented tile is edge
// index `orient` (mod n) for the +y-up convention used in ngon.js.
export function edgeToCompass(edge, orient, n) {
  return (((edge - orient) % n) + n) % n;
}

// Inverse: compass slot back to raw edge index for a given orientation.
export function compassToEdge(slot, orient, n) {
  return (((slot + orient) % n) + n) % n;
}

// Canonical sheet delta for a compass slot. This is the holonomy generator
// assignment: it depends ONLY on the absolute compass direction, so that
// walking the same world-direction always accrues the same sheet shift.
// Default: slot index itself (mod groupOrder). Override per-n as needed.
export function compassSheetDelta(slot, groupOrder) {
  return ((slot % groupOrder) + groupOrder) % groupOrder;
}

// Algebraic generator table. For pentagon we store exact field constants;
// others are extensible placeholders.
export const AFFINE_TABLE = {
  5: {
    field: 'Q(sqrt5, S)',
    // exact circumradius and rotation handled in geometry.js; here we just
    // tag that an exact representation exists.
    exact: true,
    compass: COMPASS[5],
  },
};

export function affineInfo(n) {
  return (
    AFFINE_TABLE[n] || {
      field: `Q(cos 2pi/${n})`,
      exact: n === 3 || n === 4 || n === 6,
      compass: COMPASS[n] || null,
    }
  );
}
