// "Sum equals target" rule.
//
// A selection of tiles is valid if the values add up exactly to `target`.

export function sumEquals(target) {
  return {
    id: 'sumEquals',
    description: `Match tiles that add up to ${target}`,
    target,
    // tiles: array of Tile objects in selection order.
    validate(tiles) {
      const sum = tiles.reduce((acc, t) => acc + t.value, 0);
      return sum === target;
    },
  };
}
