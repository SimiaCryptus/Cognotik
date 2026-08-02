// Additive (+/-) level pack.

import { defineLevel, Stars } from '../level.js';

export const additiveLevels = [
  defineLevel({
    id: 'add-01-warmup',
    start: '25 - 2',
    allowedVerbs: ['combine'],
    allowedOps: ['+', '-'],
    stars: [Stars.solve()],
    hint: 'Two small numbers side by side? Combine them.',
  }),
  defineLevel({
    id: 'add-02-swap',
    start: '3 + 20',
    allowedVerbs: ['swap', 'combine'],
    allowedOps: ['+', '-'],
    stars: [Stars.solve(), Stars.fewMoves(2)],
    hint: 'Order does not matter for +. Rearrange, then combine.',
  }),
  defineLevel({
    id: 'add-03-plus-19',
    start: '4 + 19',
    allowedVerbs: ['split', 'swap', 'group', 'ungroup', 'combine', 'cancel'],
    allowedOps: ['+', '-'],
    stars: [Stars.solve(), Stars.fewMoves(4), Stars.onlyVerbs(['split', 'combine', 'ungroup'])],
    hint: 'Try rounding 19 up to 20.',
  }),
  defineLevel({
    id: 'add-04-cancel',
    start: '(7 + 3) - 3',
    allowedVerbs: ['cancel', 'combine', 'ungroup'],
    allowedOps: ['+', '-'],
    stars: [Stars.solve(), Stars.fewMoves(1)],
    hint: 'Adding 3 then taking 3 away? They cancel out.',
  }),
];
