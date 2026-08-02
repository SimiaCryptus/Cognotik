// "Product equals target" rule.
//
// A selection of tiles is valid if the values multiply to exactly `target`.

export function productEquals(target) {
  return {
    id: 'productEquals',
    description: `Match tiles whose product is ${target}`,
    target,
    validate(tiles) {
      const product = tiles.reduce((acc, t) => acc * t.value, 1);
      return product === target;
    },
  };
}
