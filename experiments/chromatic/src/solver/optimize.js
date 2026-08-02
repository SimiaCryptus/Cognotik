// Small, dependency-free optimizers used by the Designer solve driver.
//
// Both optimizers minimize a scalar `objective(vec)` over a flat numeric
// vector and return { vector, value, iterations } so callers (solve.js) can
// apply the result back into the document.

// Numerical gradient via central differences.
function numericalGradient(objective, vec, eps = 1e-4) {
  const grad = new Array(vec.length).fill(0);
  for (let i = 0; i < vec.length; i++) {
    const orig = vec[i];
    vec[i] = orig + eps;
    const fPlus = objective(vec);
    vec[i] = orig - eps;
    const fMinus = objective(vec);
    vec[i] = orig;
    grad[i] = (fPlus - fMinus) / (2 * eps);
  }
  return grad;
}

function vecNorm(v) {
  let s = 0;
  for (const x of v) s += x * x;
  return Math.sqrt(s);
}

/**
 * Gradient-descent minimizer with a simple backtracking line search.
 *
 * @param {(vec:number[])=>number} objective
 * @param {number[]} initial
 * @param {object} [options]
 * @param {number} [options.maxIterations=200]
 * @param {number} [options.tolerance=1e-6]
 * @param {function} [options.onIteration]
 * @returns {{ vector:number[], value:number, iterations:number }}
 */
export function minimize(objective, initial, options = {}) {
  const maxIterations = options.maxIterations ?? 200;
  const tolerance = options.tolerance ?? 1e-6;
  const onIteration = options.onIteration;

  let vec = initial.slice();
  let value = objective(vec);
  let iterations = 0;

  if (vec.length === 0) {
    return { vector: vec, value, iterations: 0 };
  }

  let step = 0.1;

  for (let iter = 0; iter < maxIterations; iter++) {
    iterations = iter + 1;

    const grad = numericalGradient(objective, vec);
    const gnorm = vecNorm(grad);
    if (gnorm === 0 || !Number.isFinite(gnorm)) {
      break;
    }

    // Backtracking line search along the negative gradient.
    let bestVec = vec;
    let bestValue = value;
    let localStep = step;
    let improved = false;
    for (let ls = 0; ls < 12; ls++) {
      const trial = vec.map((x, i) => x - localStep * grad[i]);
      const trialValue = objective(trial);
      if (Number.isFinite(trialValue) && trialValue < bestValue) {
        bestVec = trial;
        bestValue = trialValue;
        improved = true;
        break;
      }
      localStep *= 0.5;
    }

    if (!improved) {
      // Could not make progress; shrink the base step and stop if tiny.
      step *= 0.5;
      if (step < 1e-8) break;
      continue;
    }

    // Grow the step a little for the next iteration to accelerate descent.
    step = localStep * 1.5;

    const delta = value - bestValue;
    vec = bestVec;
    value = bestValue;

    if (onIteration) onIteration({ iteration: iterations, value, vector: vec });

    if (delta < tolerance) break;
  }

  return { vector: vec, value, iterations };
}

/**
 * Simulated-annealing minimizer. Useful for escaping local minima on the
 * non-convex constraint landscape.
 *
 * @param {(vec:number[])=>number} objective
 * @param {number[]} initial
 * @param {object} [options]
 * @param {number} [options.maxIterations=2000]
 * @param {number} [options.tolerance=1e-6]
 * @param {number} [options.temperature=1]
 * @param {number} [options.cooling=0.995]
 * @param {number} [options.stepSize=0.05]
 * @param {function} [options.onIteration]
 * @returns {{ vector:number[], value:number, iterations:number }}
 */
export function anneal(objective, initial, options = {}) {
  const maxIterations = options.maxIterations ?? 2000;
  const tolerance = options.tolerance ?? 1e-6;
  const onIteration = options.onIteration;
  let temperature = options.temperature ?? 1;
  const cooling = options.cooling ?? 0.995;
  const stepSize = options.stepSize ?? 0.05;

  let current = initial.slice();
  let currentValue = objective(current);
  let best = current.slice();
  let bestValue = currentValue;
  let iterations = 0;

  if (current.length === 0) {
    return { vector: current, value: currentValue, iterations: 0 };
  }

  for (let iter = 0; iter < maxIterations; iter++) {
    iterations = iter + 1;

    const scale = Math.max(temperature, 1e-3) * stepSize;
    const candidate = current.map((x) => x + (Math.random() * 2 - 1) * scale);
    const candidateValue = objective(candidate);

    const delta = candidateValue - currentValue;
    if (
      Number.isFinite(candidateValue) &&
      (delta < 0 || Math.random() < Math.exp(-delta / Math.max(temperature, 1e-6)))
    ) {
      current = candidate;
      currentValue = candidateValue;
      if (currentValue < bestValue) {
        best = current.slice();
        bestValue = currentValue;
      }
    }

    if (onIteration) onIteration({ iteration: iterations, value: bestValue, vector: best });

    temperature *= cooling;
    if (temperature < tolerance) break;
  }

  return { vector: best, value: bestValue, iterations };
}
