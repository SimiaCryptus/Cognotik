// "Shared nontrivial factor" rule.
//
// Valid if all selected tile values share a common factor greater than 1
// (i.e. their greatest common divisor exceeds 1). Zero and values whose
// absolute value is 1 cannot participate in a nontrivial factor, so any
// such tile makes the selection invalid.

function gcd(a, b) {
  a = Math.abs(a);
  b = Math.abs(b);
  while (b) {
    [a, b] = [b, a % b];
  }
  return a;
}

export function sharedFactor() {
  return {
    id: 'sharedFactor',
    description: 'Match tiles that share a common factor greater than 1',
    validate(tiles) {
      if (tiles.length < 2) return false;
      let g = 0;
      for (const t of tiles) {
        const v = Math.abs(t.value);
        if (v <= 1) return false; // 0 and ±1 have no nontrivial factor
        g = gcd(g, v);
      }
      return g > 1;
    },
  };
}
