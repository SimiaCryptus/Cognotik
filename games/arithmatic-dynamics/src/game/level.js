// Level schema + loader helpers.
//
// A level is a plain object:
//   {
//     id, start, allowedVerbs, allowedOps, stars, hint
//   }
//
// `defineLevel` fills in sensible defaults so authors only specify what
// matters for a given puzzle.

import { verbsSubsetOf } from './stars.js';

const ALL_VERBS = ['split', 'swap', 'group', 'ungroup', 'combine', 'cancel'];

export function defineLevel(spec) {
  const stars = spec.stars || [{ id: 'solve', label: 'Solve it', test: (s) => s.isSolved() }];
  return {
    id: spec.id,
    start: spec.start,
    allowedVerbs: spec.allowedVerbs || ALL_VERBS.slice(),
    allowedOps: spec.allowedOps || ['+', '-'],
    stars,
    hint: spec.hint || null,
  };
}

// Common star builders.
export const Stars = {
  solve() {
    return { id: 'solve', label: 'Solve it', test: (s) => s.isSolved() };
  },
  fewMoves(max) {
    return {
      id: 'few-moves',
      label: `Solve in ${max} moves or fewer`,
      test: (s) => s.isSolved() && s.moveCount <= max,
    };
  },
  onlyVerbs(verbs) {
    return {
      id: 'only-verbs',
      label: `Use only: ${verbs.join(', ')}`,
      test: (s) => s.isSolved() && verbsSubsetOf(s, verbs),
    };
  },
};
