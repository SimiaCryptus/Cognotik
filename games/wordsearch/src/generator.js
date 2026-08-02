// High-level orchestration: build a finished puzzle from inputs.

import { Grid } from './grid/Grid.js';
import { placeWords } from './grid/placement.js';
import { fillGrid } from './fill/filler.js';
import { MarkovModel } from './markov/MarkovModel.js';
import { selectWords } from './grid/wordlist.js';

/**
 * @param {object} opts
 * @param {string} opts.referenceText
 * @param {string[]} opts.words
 * @param {number} [opts.width]
 * @param {number} [opts.height]
 * @param {number} [opts.order]
 * @param {string} [opts.combiner]
 * @param {string} [opts.sampling]
 * @param {() => number} [opts.rng]
 * @param {MarkovModel} [opts.model] reuse a trained model
 */
export function generatePuzzle(opts) {
  const {
    referenceText = '',
    words = [],
    width = 15,
    height = 15,
    order = 3,
    combiner = 'product',
    sampling = 'weighted',
    rng = Math.random,
    lattice = 'square',
    includeBackwards = true,
    wordCount = 0,
    avoidWords = true,
    model: providedModel,
    reverseModel: providedReverseModel,
  } = opts;
  // Sample down (potentially large) word lists to the configured count.
  const selectedWords = selectWords(words, wordCount, rng);

  const model = providedModel || new MarkovModel(order).train(referenceText, order);
  // Reverse model only matters when backward-oriented vectors participate.
  const reverseModel = includeBackwards
    ? providedReverseModel || new MarkovModel(order).train(referenceText, order, { reverse: true })
    : null;

  const grid = new Grid(width, height);
  const placement = placeWords(grid, selectedWords, rng, { lattice, includeBackwards });
  fillGrid(grid, model, {
    combiner,
    sampling,
    rng,
    lattice,
    includeBackwards,
    reverseModel,
    words: selectedWords,
    avoidWords,
  });
  grid.lattice = lattice;

  return { grid, placement, model, reverseModel };
}
/**
 * Build the puzzle scaffold (grid + placed words + trained model) but
 * do NOT fill the empty cells. Used by the step-through / watch mode.
 * @param {object} opts same shape as generatePuzzle
 */
export function preparePuzzle(opts) {
  const {
    referenceText = '',
    words = [],
    width = 15,
    height = 15,
    order = 3,
    rng = Math.random,
    lattice = 'square',
    includeBackwards = true,
    wordCount = 0,
    model: providedModel,
    reverseModel: providedReverseModel,
  } = opts;
  // Sample down (potentially large) word lists to the configured count.
  const selectedWords = selectWords(words, wordCount, rng);
  const model = providedModel || new MarkovModel(order).train(referenceText, order);
  const reverseModel = includeBackwards
    ? providedReverseModel || new MarkovModel(order).train(referenceText, order, { reverse: true })
    : null;
  const grid = new Grid(width, height);
  const placement = placeWords(grid, selectedWords, rng, { lattice, includeBackwards });
  return { grid, placement, model, reverseModel, selectedWords };
}
