import { latticeDirections } from '../grid/directions.js';

/**
 * Count filled neighbours around (x, y) across all directions of the
 * given lattice.
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {number} x
 * @param {number} y
 * @param {'square'|'hex'|'triangular'} [lattice]
 */
export function adjacencyScore(grid, x, y, lattice = 'square') {
  let score = 0;
  for (const d of latticeDirections(lattice, y, { includeBackwards: true })) {
    const nx = x + d.dx;
    const ny = y + d.dy;
    if (grid.inBounds(nx, ny) && grid.get(nx, ny)) score++;
  }
  return score;
}

/**
 * Simple priority structure backed by an array. Not a heap, but the
 * grids are small enough that re-scanning is fine and keeps the code
 * clear. Returns the empty cell with the highest adjacency score,
 * breaking ties randomly.
 * @param {import('../grid/Grid.js').Grid} grid
 * @param {() => number} [rng]
 * @param {'square'|'hex'|'triangular'} [lattice]
 */
export function pickNextCell(grid, rng = Math.random, lattice = 'square') {
  let best = null;
  let bestScore = -1;
  const ties = [];
  for (const { x, y } of grid.emptyCells()) {
    const s = adjacencyScore(grid, x, y, lattice);
    if (s > bestScore) {
      bestScore = s;
      ties.length = 0;
      ties.push({ x, y });
      best = { x, y };
    } else if (s === bestScore) {
      ties.push({ x, y });
    }
  }
  if (ties.length === 0) return null;
  return ties[Math.floor(rng() * ties.length)];
}
