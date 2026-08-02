// "Strictly ascending" rule.
//
// Valid if each successive tile value is strictly greater than the last.

export function ascending() {
  return {
    id: 'ascending',
    description: 'Match tiles with strictly ascending values',
    validate(tiles) {
      for (let i = 1; i < tiles.length; i++) {
        if (tiles[i].value <= tiles[i - 1].value) return false;
      }
      return tiles.length >= 2;
    },
  };
}
