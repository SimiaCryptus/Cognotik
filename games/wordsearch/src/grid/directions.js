// Direction vectors for multiple lattice types and context-reading helpers.

// --- Square (8-direction) lattice ---
export const DIRECTIONS = {
  E: { dx: 1, dy: 0 },
  W: { dx: -1, dy: 0 },
  S: { dx: 0, dy: 1 },
  N: { dx: 0, dy: -1 },
  SE: { dx: 1, dy: 1 },
  NW: { dx: -1, dy: -1 },
  SW: { dx: -1, dy: 1 },
  NE: { dx: 1, dy: -1 },
};

export const DIRECTION_LIST = Object.keys(DIRECTIONS).map((name) => ({
  name,
  ...DIRECTIONS[name],
}));

/**
 * "Forward" directions are those whose primary motion is rightward, or
 * downward when purely vertical. The remaining directions are their
 * exact opposites ("backwards"). This lets callers disable
 * backwards-oriented placements/reads.
 * @param {{dx:number,dy:number}} d
 * @returns {boolean}
 */
export function isForward(d) {
  return d.dx > 0 || (d.dx === 0 && d.dy > 0);
}

// ---------------------------------------------------------------------------
// Lattice definitions.
//
// Each lattice supplies its own direction list. For hex/triangular grids we
// use an axial-style offset scheme keyed on row parity. Because the offsets
// depend on the row, callers must use `latticeDirections(lattice, y)` to get
// the concrete (dx, dy) vectors for a given row.
// ---------------------------------------------------------------------------

// Pointy-top hexagonal lattice using "odd-r" offset coordinates.
// 6 neighbour directions; their dx depends on whether the row is odd.
const HEX_DIRS = {
  E: { name: 'E', forward: true },
  W: { name: 'W', forward: false },
  NE: { name: 'NE', forward: true },
  NW: { name: 'NW', forward: false },
  SE: { name: 'SE', forward: true },
  SW: { name: 'SW', forward: false },
};

function hexVectors(y) {
  const odd = (y & 1) === 1;
  // odd-r: odd rows are shifted right by half a cell.
  const dxUpLeft = odd ? 0 : -1;
  const dxUpRight = odd ? 1 : 0;
  const dxDownLeft = odd ? 0 : -1;
  const dxDownRight = odd ? 1 : 0;
  return {
    E: { dx: 1, dy: 0 },
    W: { dx: -1, dy: 0 },
    NE: { dx: dxUpRight, dy: -1 },
    NW: { dx: dxUpLeft, dy: -1 },
    SE: { dx: dxDownRight, dy: 1 },
    SW: { dx: dxDownLeft, dy: 1 },
  };
}

// Triangular lattice: every cell is treated as having the full 6 hex
// neighbours PLUS the two pure-vertical neighbours, giving denser
// connectivity that better approximates a triangular tiling's reading lines.
function triVectors(y) {
  const hv = hexVectors(y);
  return {
    ...hv,
    N: { dx: 0, dy: -1 },
    S: { dx: 0, dy: 1 },
  };
}

const TRI_META = {
  ...HEX_DIRS,
  N: { name: 'N', forward: false },
  S: { name: 'S', forward: true },
};

/**
 * Return the concrete directions for a lattice at a given row.
 * @param {'square'|'hex'|'triangular'} lattice
 * @param {number} y row index (needed for hex/tri parity)
 * @param {object} [opts]
 * @param {boolean} [opts.includeBackwards=true]
 * @returns {Array<{name:string,dx:number,dy:number,forward:boolean}>}
 */
export function latticeDirections(lattice = 'square', y = 0, opts = {}) {
  const { includeBackwards = true } = opts;
  let list;
  if (lattice === 'hex') {
    const v = hexVectors(y);
    list = Object.keys(HEX_DIRS).map((name) => ({
      name,
      dx: v[name].dx,
      dy: v[name].dy,
      forward: HEX_DIRS[name].forward,
    }));
  } else if (lattice === 'triangular') {
    const v = triVectors(y);
    list = Object.keys(TRI_META).map((name) => ({
      name,
      dx: v[name].dx,
      dy: v[name].dy,
      forward: TRI_META[name].forward,
    }));
  } else {
    // square
    list = DIRECTION_LIST.map((d) => ({ ...d, forward: isForward(d) }));
  }
  if (!includeBackwards) list = list.filter((d) => d.forward);
  return list;
}

/**
 * Step from (x, y) by `n` cells along a lattice direction, accounting for
 * row-parity offsets in hex/triangular lattices. Returns {x, y}.
 * @param {'square'|'hex'|'triangular'} lattice
 * @param {number} x
 * @param {number} y
 * @param {string} dirName
 * @param {number} n
 */
export function step(lattice, x, y, dirName, n) {
  let cx = x;
  let cy = y;
  for (let i = 0; i < n; i++) {
    const vecs =
      lattice === 'hex' ? hexVectors(cy) : lattice === 'triangular' ? triVectors(cy) : DIRECTIONS;
    const v = vecs[dirName];
    if (!v) return null;
    cx += v.dx;
    cy += v.dy;
  }
  return { x: cx, y: cy };
}

/**
 * Read the context string of up to `order` filled characters that
 * precede (x, y) when moving toward it along direction d.
 *
 * "Preceding" means the cells behind it, i.e. stepping in the
 * OPPOSITE direction of d. For non-square lattices the step offsets
 * depend on the current row, so a lattice + dirName must be supplied.
 *
 * Reading stops at grid edges or at the first empty/unset cell.
 *
 * @param {import('./Grid.js').Grid} grid
 * @param {number} x
 * @param {number} y
 * @param {{dx:number,dy:number,name?:string}} d
 * @param {number} order
 * @param {'square'|'hex'|'triangular'} [lattice]
 * @returns {string}
 */
export function readContext(grid, x, y, d, order, lattice = 'square') {
  const chars = [];
  let cx = x;
  let cy = y;
  for (let k = 1; k <= order; k++) {
    // step one cell backwards (opposite of d) using row-aware offsets.
    const vecs =
      lattice === 'hex' ? hexVectors(cy) : lattice === 'triangular' ? triVectors(cy) : DIRECTIONS;
    const v = d.name && vecs[d.name] ? vecs[d.name] : d;
    cx = cx - v.dx;
    cy = cy - v.dy;
    if (!grid.inBounds(cx, cy)) break;
    const ch = grid.get(cx, cy);
    if (!ch) break;
    chars.push(ch);
  }
  // chars are ordered nearest-first; reverse for reading order.
  return chars.reverse().join('');
}
/**
 * Read up to `back` filled characters behind (x, y) and up to `fwd` filled
 * characters ahead of (x, y) along direction d, accounting for row-parity
 * offsets in hex/triangular lattices.
 *
 * Returns { before, after } strings in reading order. The cell (x, y)
 * itself is NOT included.
 *
 * @param {import('./Grid.js').Grid} grid
 * @param {number} x
 * @param {number} y
 * @param {{dx:number,dy:number,name?:string}} d
 * @param {number} back
 * @param {number} fwd
 * @param {'square'|'hex'|'triangular'} [lattice]
 * @returns {{before:string, after:string}}
 */
export function readLineAround(grid, x, y, d, back, fwd, lattice = 'square') {
  const before = [];
  const after = [];
  // Walk backwards (opposite of d).
  let cx = x;
  let cy = y;
  for (let k = 0; k < back; k++) {
    const vecs =
      lattice === 'hex' ? hexVectors(cy) : lattice === 'triangular' ? triVectors(cy) : DIRECTIONS;
    const v = d.name && vecs[d.name] ? vecs[d.name] : d;
    cx = cx - v.dx;
    cy = cy - v.dy;
    if (!grid.inBounds(cx, cy)) break;
    const ch = grid.get(cx, cy);
    if (!ch) break;
    before.push(ch);
  }
  // Walk forwards (along d).
  cx = x;
  cy = y;
  for (let k = 0; k < fwd; k++) {
    const vecs =
      lattice === 'hex' ? hexVectors(cy) : lattice === 'triangular' ? triVectors(cy) : DIRECTIONS;
    const v = d.name && vecs[d.name] ? vecs[d.name] : d;
    cx = cx + v.dx;
    cy = cy + v.dy;
    if (!grid.inBounds(cx, cy)) break;
    const ch = grid.get(cx, cy);
    if (!ch) break;
    after.push(ch);
  }
  return { before: before.reverse().join(''), after: after.join('') };
}
