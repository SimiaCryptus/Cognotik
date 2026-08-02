// Multiplicative (×/÷) level pack — gated behind the additive pack.

import { defineLevel, Stars } from '../level.js';

export const multiplicativeLevels = [
  defineLevel({
    id: 'mul-01-warmup',
    start: '6 * 5',
    allowedVerbs: ['combine'],
    allowedOps: ['*', '/'],
    stars: [Stars.solve()],
    hint: 'Combine the two numbers.',
  }),
  defineLevel({
    id: 'mul-02-cancel',
    start: '(6 * 5) / 5',
    allowedVerbs: ['cancel', 'combine', 'ungroup'],
    allowedOps: ['*', '/'],
    stars: [Stars.solve(), Stars.fewMoves(1)],
    hint: 'Times 5 then divide by 5 — they undo each other.',
  }),
];
