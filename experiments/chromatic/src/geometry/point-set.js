// PaletteColor and Palette primitives.
//
// A PaletteColor is a point in OKLab space ({ L, a, b }) with an optional
// semantic `role` and stable `id`. A Palette is an ordered collection of
// PaletteColors plus an optional adjacency graph and topology descriptor.

import { oklchToOklab, oklabToOklch } from '../colorspace/oklab.js';

let _autoId = 0;

function nextId() {
  return `c${_autoId++}`;
}

// Normalize an arbitrary point spec into an OKLab { L, a, b } object,
// carrying id/role through if present.
export function toPaletteColor(spec, space = 'OKLab') {
  if (spec == null) throw new Error('toPaletteColor: null spec');

  const id = spec.id ?? nextId();
  const role = spec.role;

  let oklab;
  const src = spec.coords ?? spec;
  const srcSpace = spec.space ?? space;

  if (srcSpace === 'OKLch' || ('C' in src && 'H' in src && 'L' in src)) {
    oklab = oklchToOklab({ L: src.L, C: src.C, H: src.H });
  } else if ('L' in src && 'a' in src && 'b' in src) {
    oklab = { L: src.L, a: src.a, b: src.b };
  } else {
    throw new Error('toPaletteColor: expected an OKLab ({L,a,b}) or OKLch ({L,C,H}) point');
  }

  return { id, role, L: oklab.L, a: oklab.a, b: oklab.b };
}

export class Palette {
  /**
   * @param {PaletteColor[]} colors   OKLab points.
   * @param {object} [meta]
   * @param {object} [meta.topology]  Topology descriptor.
   * @param {number[][]} [meta.adjacency]  Adjacency list (index -> neighbors).
   */
  constructor(colors = [], meta = {}) {
    this.colors = colors;
    this.topology = meta.topology ?? null;
    this.adjacency = meta.adjacency ?? null;
    this.constraints = meta.constraints ?? [];
  }

  get size() {
    return this.colors.length;
  }

  at(index) {
    return this.colors[index];
  }

  byId(id) {
    return this.colors.find((c) => c.id === id);
  }

  // Return colors in OKLch coordinates (does not mutate).
  toOklch() {
    return this.colors.map((c) => ({
      id: c.id,
      role: c.role,
      ...oklabToOklch({ L: c.L, a: c.a, b: c.b }),
    }));
  }

  clone() {
    const p = new Palette(
      this.colors.map((c) => ({ ...c })),
      {
        topology: this.topology,
        adjacency: this.adjacency ? this.adjacency.map((n) => [...n]) : null,
        constraints: [...this.constraints],
      }
    );
    return p;
  }

  /**
   * Build a Palette from an array of points in the given space.
   * @param {object[]} points   Array of point specs.
   * @param {object} [options]
   * @param {"OKLab"|"OKLch"} [options.space="OKLab"]
   */
  static fromPoints(points, options = {}) {
    const space = options.space ?? 'OKLab';
    const colors = points.map((p) => toPaletteColor(p, space));
    return new Palette(colors, {
      topology: options.topology,
      adjacency: options.adjacency,
      constraints: options.constraints,
    });
  }
}
