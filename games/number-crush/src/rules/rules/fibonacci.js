// "Fibonacci-like sequence" rule.
//
// Valid if each value (from the third onward) equals the sum of the two
// preceding values. Requires at least 3 tiles to be meaningful.

export function fibonacci() {
  return {
    id: 'fibonacci',
    description: 'Match tiles where each value is the sum of the previous two',
    validate(tiles) {
      if (tiles.length < 3) return false;
      for (let i = 2; i < tiles.length; i++) {
        if (tiles[i].value !== tiles[i - 1].value + tiles[i - 2].value) return false;
      }
      return true;
    },
  };
}
