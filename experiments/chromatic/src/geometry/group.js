// Cyclic / dihedral / affine group action generators.
//
// Group actions operate on OKLch points ({ L, C, H }) because rotation is a
// hue rotation about the neutral axis — a natural symmetry for palettes.
// Generators return arrays of OKLch points (the orbit of a base point).

function normalizeHue(h) {
  return ((h % 360) + 360) % 360;
}

// Cyclic group C_n: rotate the base point's hue by 360/n each step.
export function cyclic({ order }) {
  if (!Number.isInteger(order) || order < 1) {
    throw new Error('cyclic: order must be a positive integer');
  }
  return {
    order,
    type: 'cyclic',
    /**
     * @param {object} base  OKLch base point { L, C, H }.
     * @returns {object[]}    Orbit of `order` OKLch points.
     */
    apply(base) {
      const step = 360 / order;
      const out = [];
      for (let i = 0; i < order; i++) {
        out.push({
          L: base.L,
          C: base.C,
          H: normalizeHue(base.H + i * step),
        });
      }
      return out;
    },
    // The rotation transform for orbit member i (used by symmetry residual).
    transform(i) {
      const step = 360 / order;
      return (p) => ({ L: p.L, C: p.C, H: normalizeHue(p.H + i * step) });
    },
  };
}

// Dihedral group D_n: cyclic rotations plus a hue reflection about `axis`.
export function dihedral({ order, axis = 0 }) {
  if (!Number.isInteger(order) || order < 1) {
    throw new Error('dihedral: order must be a positive integer');
  }
  const rot = cyclic({ order });
  return {
    order: order * 2,
    type: 'dihedral',
    apply(base) {
      const rotated = rot.apply(base);
      const reflected = rotated.map((p) => ({
        L: p.L,
        C: p.C,
        H: normalizeHue(2 * axis - p.H),
      }));
      return [...rotated, ...reflected];
    },
  };
}

// Affine action in OKLab: apply a 3x3 matrix + translation to each of a
// set of base points. Useful for lattice/scaling transforms.
export function affine({ matrix, translate = { L: 0, a: 0, b: 0 } }) {
  const m = matrix ?? [
    [1, 0, 0],
    [0, 1, 0],
    [0, 0, 1],
  ];
  return {
    type: 'affine',
    applyPoint({ L, a, b }) {
      return {
        L: m[0][0] * L + m[0][1] * a + m[0][2] * b + translate.L,
        a: m[1][0] * L + m[1][1] * a + m[1][2] * b + translate.a,
        b: m[2][0] * L + m[2][1] * a + m[2][2] * b + translate.b,
      };
    },
    apply(points) {
      return points.map((p) => this.applyPoint(p));
    },
  };
}
