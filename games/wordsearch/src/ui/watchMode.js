// Watch mode: visualise the fill buildout, step or auto-play.

import { preparePuzzle } from '../generator.js';
import { fillGridSteps } from '../fill/filler.js';
import { renderGrid, cellAt } from './render.js';

let stepGen = null;
let stepGrid = null;
let timer = null;
let stepCount = 0;
let table = null;
let stepLattice = 'square';

function clearTimer() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

function renderState(root, lastStep) {
  const container = root.querySelector('#grid');
  renderGrid(container, stepGrid, { debug: true, lattice: stepLattice });
  table = container.querySelector('table');
  if (lastStep && table) {
    const td = cellAt(table, lastStep.x, lastStep.y);
    if (td) {
      td.classList.add('just-filled');
      // brief ripple so the eye is drawn to the newest cell
      td.style.zIndex = '3';
    }
  }
  const status = root.querySelector('#watch-status');
  if (status) {
    const ctxInfo =
      lastStep && lastStep.contexts.length
        ? ` — contexts: ${lastStep.contexts.map((c) => `${c.dir}:${c.ctx}`).join(', ')}`
        : '';
    status.textContent = lastStep
      ? `Step ${stepCount}: placed "${lastStep.ch.toUpperCase()}" at (${lastStep.x}, ${lastStep.y})${ctxInfo}`
      : `Ready — ${stepCount} steps.`;
  }
}

function doStep(root) {
  if (!stepGen) return false;
  const res = stepGen.next();
  if (res.done) {
    clearTimer();
    const status = root.querySelector('#watch-status');
    if (status) status.textContent = `Done — filled in ${stepCount} steps.`;
    return false;
  }
  stepCount += 1;
  renderState(root, res.value);
  return true;
}

export function initWatch(root, cfg) {
  clearTimer();
  stepCount = 0;
  const { grid, model, reverseModel, selectedWords } = preparePuzzle(cfg);
  stepGrid = grid;
  stepLattice = cfg.lattice || 'square';
  stepGen = fillGridSteps(grid, model, {
    ...cfg,
    reverseModel,
    words: selectedWords,
  });
  renderState(root, null);
}

export function watchStep(root) {
  clearTimer();
  doStep(root);
}

export function watchPlay(root, speedMs = 120) {
  clearTimer();
  timer = setInterval(() => {
    if (!doStep(root)) clearTimer();
  }, speedMs);
}

export function watchPause() {
  clearTimer();
}

export function watchFinish(root) {
  clearTimer();
  let safety = stepGrid ? stepGrid.width * stepGrid.height + 1 : 0;
  while (safety-- > 0 && doStep(root)) {
    /* run to completion */
  }
}
