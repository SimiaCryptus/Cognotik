// "All same value" rule.
//
// Valid if every selected tile shares the same value.

export function allSame() {
  return {
    id: 'allSame',
    description: 'Match tiles that all share the same value',
    validate(tiles) {
      if (tiles.length === 0) return false;
      const first = tiles[0].value;
      return tiles.every((t) => t.value === first);
    },
  };
}
