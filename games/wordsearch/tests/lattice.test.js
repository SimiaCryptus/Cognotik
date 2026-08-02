import assert from 'assert';
import { latticeDirections, step, isForward } from '../src/grid/directions.js';
import { Grid } from '../src/grid/Grid.js';
import { placeWords } from '../src/grid/placement.js';
import { generatePuzzle } from '../src/generator.js';

describe('lattice directions', () => {
  it('square has 8 directions, 4 forward', () => {
    const all = latticeDirections('square', 0, { includeBackwards: true });
    const fwd = latticeDirections('square', 0, { includeBackwards: false });
    assert.equal(all.length, 8);
    assert.equal(fwd.length, 4);
    assert.ok(fwd.every((d) => d.forward));
  });

  it('hex has 6 directions', () => {
    const all = latticeDirections('hex', 0, { includeBackwards: true });
    assert.equal(all.length, 6);
  });

  it('triangular has 8 directions', () => {
    const all = latticeDirections('triangular', 0, { includeBackwards: true });
    assert.equal(all.length, 8);
  });

  it('hex row parity changes diagonal dx', () => {
    const even = latticeDirections('hex', 0).find((d) => d.name === 'NE');
    const odd = latticeDirections('hex', 1).find((d) => d.name === 'NE');
    assert.notEqual(even.dx, odd.dx);
  });

  it('step is row-aware for hex', () => {
    const p = step('hex', 0, 0, 'SE', 1);
    assert.ok(p);
    assert.equal(p.y, 1);
  });

  it('isForward classifies E forward and W backward', () => {
    assert.ok(isForward({ dx: 1, dy: 0 }));
    assert.ok(!isForward({ dx: -1, dy: 0 }));
  });
});

describe('lattice placement', () => {
  it('places words on hex grid and keeps them readable', () => {
    const { grid, placement } = generatePuzzle({
      referenceText: 'the quick brown fox jumps over the lazy dog',
      words: ['fox', 'dog'],
      width: 12,
      height: 12,
      order: 2,
      lattice: 'hex',
    });
    for (const rec of placement.placed) {
      let read = '';
      for (const c of rec.coords) read += grid.get(c.x, c.y);
      assert.equal(read, rec.word);
    }
  });

  it('forward-only placement uses only forward directions', () => {
    const grid = new Grid(15, 15);
    const { placed } = placeWords(grid, ['cat', 'dog', 'fox'], Math.random, {
      lattice: 'square',
      includeBackwards: false,
    });
    const fwdNames = new Set(
      latticeDirections('square', 0, { includeBackwards: false }).map((d) => d.name)
    );
    for (const rec of placed) {
      assert.ok(fwdNames.has(rec.dir), `dir ${rec.dir} should be forward`);
    }
  });
});
