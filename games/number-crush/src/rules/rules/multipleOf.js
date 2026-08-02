// "Sum is a multiple of target" rule.
//
// Valid if the sum of tile values is a non-zero multiple of `target`.

export function multipleOf(target) {
  return {
    id: 'multipleOf',
    description: `Match tiles whose sum is a multiple of ${target}`,
    target,
    validate(tiles) {
      const sum = tiles.reduce((acc, t) => acc + t.value, 0);
      return sum > 0 && sum % target === 0;
    },
  };
}
