// "Geometric sequence" rule.
//
// Valid if selected tile values form a geometric sequence: each
// successive value equals the previous multiplied by a constant ratio.
// Zero values are disallowed since they break the ratio.

export function geometric() {
  return {
    id: 'geometric',
    description: 'Match tiles forming a geometric sequence',
    validate(tiles) {
      if (tiles.length < 2) return false;
      for (const t of tiles) {
        if (t.value === 0) return false;
      }
      // Ratio expressed as a fraction (num/den) to avoid float error.
      const num = tiles[1].value;
      const den = tiles[0].value;
      for (let i = 2; i < tiles.length; i++) {
        // tiles[i] / tiles[i-1] === num / den  <=>  tiles[i]*den === tiles[i-1]*num
        if (tiles[i].value * den !== tiles[i - 1].value * num) return false;
      }
      return true;
    },
  };
}
