// "Arithmetic sequence" rule.
//
// Valid if selected tile values form an arithmetic sequence: each
// successive value differs from the previous by a constant step.
// Requires at least 2 tiles (with 2 tiles any pair trivially qualifies,
// so we require a nonzero common difference for longer runs to be
// meaningful — but 2 tiles are always allowed).

export function arithmetic() {
  return {
    id: 'arithmetic',
    description: 'Match tiles forming an arithmetic sequence',
    validate(tiles) {
      if (tiles.length < 2) return false;
      const step = tiles[1].value - tiles[0].value;
      for (let i = 2; i < tiles.length; i++) {
        if (tiles[i].value - tiles[i - 1].value !== step) return false;
      }
      return true;
    },
  };
}
