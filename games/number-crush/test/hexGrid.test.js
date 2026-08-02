import { describe, it, expect } from 'vitest';
import { HexGrid } from '../src/model/HexGrid.js';
import { areNeighbors, neighbors } from '../src/model/coords.js';

describe('coords', () => {
  it('computes 6 neighbors', () => {
    const ns = neighbors({ q: 0, r: 0 });
    expect(ns).toHaveLength(6);
  });

  it('detects adjacency', () => {
    expect(areNeighbors({ q: 0, r: 0 }, { q: 1, r: 0 })).toBe(true);
    expect(areNeighbors({ q: 0, r: 0 }, { q: 2, r: 0 })).toBe(false);
    expect(areNeighbors({ q: 0, r: 0 }, { q: 0, r: 0 })).toBe(false);
  });
});

describe('HexGrid', () => {
  it('builds the expected number of cells', () => {
    const grid = new HexGrid({ cols: 5, rows: 4, size: 20 });
    expect(grid.allCells()).toHaveLength(20);
  });

  it('reports in-bounds membership', () => {
    const grid = new HexGrid({ cols: 3, rows: 3, size: 20 });
    const cell = grid.allCells()[0];
    expect(grid.has(cell)).toBe(true);
    expect(grid.has({ q: 999, r: 999 })).toBe(false);
  });

  it('returns only in-bounds neighbors', () => {
    const grid = new HexGrid({ cols: 3, rows: 3, size: 20 });
    const corner = grid.allCells()[0];
    const ns = grid.neighbors(corner);
    expect(ns.length).toBeLessThan(6);
    ns.forEach((n) => expect(grid.has(n)).toBe(true));
  });
});
