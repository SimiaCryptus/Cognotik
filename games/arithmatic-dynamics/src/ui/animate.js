// Animation helpers. Respect prefers-reduced-motion by no-oping.

import { prefersReducedMotion } from '../util/dom.js';

export function pop(elm) {
  if (!elm || prefersReducedMotion()) return Promise.resolve();
  return elm
    .animate(
      [
        { transform: 'scale(1)', opacity: 1 },
        { transform: 'scale(1.3)', opacity: 0.7 },
        { transform: 'scale(0)', opacity: 0 },
      ],
      { duration: 260, easing: 'ease-in' },
    )
    .finished.catch(() => {});
}

export function mergeGlow(elm) {
  if (!elm || prefersReducedMotion()) return Promise.resolve();
  return elm
    .animate(
      [
        { transform: 'scale(0.6)', opacity: 0.4 },
        { transform: 'scale(1.15)', opacity: 1 },
        { transform: 'scale(1)', opacity: 1 },
      ],
      { duration: 240, easing: 'ease-out' },
    )
    .finished.catch(() => {});
}

export function celebrate(elm) {
  if (!elm || prefersReducedMotion()) return Promise.resolve();
  return elm
    .animate([{ transform: 'scale(1)' }, { transform: 'scale(1.25)' }, { transform: 'scale(1)' }], {
      duration: 500,
      easing: 'ease-in-out',
      iterations: 2,
    })
    .finished.catch(() => {});
}
