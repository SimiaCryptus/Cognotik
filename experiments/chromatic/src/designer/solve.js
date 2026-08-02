// Designer solve driver (designer.md §6.3): gamut-aware, interruptible,
// interactive optimization over a Designer document.

import { compileObjective, applyVector } from './objective.js';
import { minimize, anneal } from '../solver/optimize.js';
import { softCompressOklab } from '../project/gamut.js';

// Push every node back inside sRGB (soft compression) after a solve step,
// mirroring designer.md §6.3 ("Every solve is gamut-aware").
function gamutClampDocument(doc) {
  for (const node of doc.nodes) {
    const soft = softCompressOklab(node.oklab);
    node.oklab = { L: soft.oklab.L, a: soft.oklab.a, b: soft.oklab.b };
  }
}

/**
 * Solve a Designer document in place.
 *
 * @param {object} doc
 * @param {object} [options]
 * @param {"gradient"|"anneal"} [options.method="gradient"]
 * @param {number} [options.maxIterations]
 * @param {number} [options.tolerance]
 * @param {boolean} [options.gamut=true]  Soft-compress nodes into sRGB.
 * @param {function} [options.onIteration]  Called with { iteration, value }.
 * @returns {{ value, iterations, residuals }}
 */
export function solve(doc, options = {}) {
  const method = options.method ?? 'gradient';
  const gamut = options.gamut ?? true;
  const { objective, residuals, varMap } = compileObjective(doc);
  const initial = varMap.pack();

  if (initial.length === 0) {
    // Nothing to optimize (all coords hard-anchored).
    return { value: objective([]), iterations: 0, residuals: residuals([]) };
  }

  const runner = method === 'anneal' ? anneal : minimize;
  const result = runner(objective, initial, {
    maxIterations: options.maxIterations ?? (method === 'anneal' ? 2000 : 200),
    tolerance: options.tolerance ?? 1e-6,
    onIteration: options.onIteration
      ? ({ iteration, value, vector }) => options.onIteration({ iteration, value, vector })
      : undefined,
  });

  applyVector(doc, varMap, result.vector);
  if (gamut) gamutClampDocument(doc);

  return {
    value: result.value,
    iterations: result.iterations,
    residuals: residuals(varMap.pack()),
  };
}
