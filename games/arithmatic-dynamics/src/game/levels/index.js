// Manifest of level packs.

import { additiveLevels } from './additive.js';
import { multiplicativeLevels } from './multiplicative.js';

export const packs = [
  { id: 'additive', label: 'Plus & Minus', levels: additiveLevels },
  { id: 'multiplicative', label: 'Times & Divide', levels: multiplicativeLevels },
];

export const allLevels = packs.flatMap((p) => p.levels);

export function findLevel(id) {
  return allLevels.find((l) => l.id === id) || null;
}
