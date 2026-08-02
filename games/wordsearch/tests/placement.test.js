import assert from 'assert';
import { Grid } from '../src/grid/Grid.js';
import { placeWords } from '../src/grid/placement.js';
import { generatePuzzle } from '../src/generator.js';

describe('placement', () => {
  it('places non-conflicting words and locks them', () => {
    const grid = new Grid(15, 15);
    const { placed, failed } = placeWords(grid, ['cat', 'dog'], Math.random);
    assert.equal(failed.length, 0);
    for (const rec of placed) {
      for (const c of rec.coords) {
        assert.ok(grid.isLocked(c.x, c.y));
        assert.equal(grid.get(c.x, c.y), c.ch);
      }
    }
  });

  it('every placed word is readable after fill (property)', () => {
    const { grid, placement } = generatePuzzle({
      referenceText: 'the quick brown fox jumps over the lazy dog',
      words: ['fox', 'dog', 'lazy'],
      width: 12,
      height: 12,
      order: 2,
    });
    for (const rec of placement.placed) {
      let read = '';
      for (const c of rec.coords) read += grid.get(c.x, c.y);
      assert.equal(read, rec.word);
    }
  });
});
