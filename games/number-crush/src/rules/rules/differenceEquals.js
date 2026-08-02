// "Difference equals target" rule.
//
// Valid only for exactly two tiles whose absolute difference is `target`.

export function differenceEquals(target) {
  return {
    id: 'differenceEquals',
    description: `Match two tiles that differ by ${target}`,
    target,
    validate(tiles) {
      if (tiles.length !== 2) return false;
      return Math.abs(tiles[0].value - tiles[1].value) === target;
    },
  };
}
