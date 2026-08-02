// Entry point: bootstrap the game onto the canvas from a config menu.

import { Game } from './game/Game.js';
import { makeRule, RULE_REGISTRY } from './rules/rules/index.js';

const canvas = document.getElementById('board');
const form = document.getElementById('config-form');
const menu = document.getElementById('config-menu');
const gameArea = document.getElementById('game-area');
const restartBtn = document.getElementById('restart-btn');
const ruleSelect = document.getElementById('cfg-rule');
const targetField = document.getElementById('cfg-target-field');
const targetInput = document.getElementById('cfg-target');

let game = null;

// Populate rule options from the registry.
for (const [id, meta] of Object.entries(RULE_REGISTRY)) {
  const opt = document.createElement('option');
  opt.value = id;
  opt.textContent = meta.label;
  ruleSelect.appendChild(opt);
}

// Show/hide the target input based on whether the rule uses it.
function syncTargetVisibility() {
  const meta = RULE_REGISTRY[ruleSelect.value];
  if (meta && meta.usesTarget) {
    targetField.style.display = '';
    if (meta.defaultTarget != null && targetInput.value === '') {
      targetInput.value = String(meta.defaultTarget);
    }
  } else {
    targetField.style.display = 'none';
  }
}
ruleSelect.addEventListener('change', () => {
  const meta = RULE_REGISTRY[ruleSelect.value];
  if (meta && meta.usesTarget && meta.defaultTarget != null) {
    targetInput.value = String(meta.defaultTarget);
  }
  syncTargetVisibility();
});
syncTargetVisibility();

function readConfig() {
  const num = (id, fallback) => {
    const v = Number(document.getElementById(id).value);
    return Number.isFinite(v) ? v : fallback;
  };

  const cols = Math.max(2, Math.min(16, num('cfg-cols', 8)));
  const rows = Math.max(2, Math.min(16, num('cfg-rows', 9)));
  const size = Math.max(12, Math.min(48, num('cfg-size', 28)));
  // Allow negative tiles: min value ranges -9..1, max value 1..9.
  const valMin = Math.max(-9, Math.min(1, num('cfg-val-min', 1)));
  const valMax = Math.max(1, Math.min(9, Math.max(valMin, num('cfg-val-max', 9))));
  const minSelection = Math.max(1, num('cfg-min-sel', 2));
  const maxSelection = Math.max(minSelection, num('cfg-max-sel', 4));
  const ruleId = ruleSelect.value;
  // Target number ranges 2..20.
  const target = Math.max(
    2,
    Math.min(20, num('cfg-target', RULE_REGISTRY[ruleId]?.defaultTarget ?? 10))
  );
  const straightLine = document.getElementById('cfg-straight-line')?.checked ?? false;
  const suppressZero = document.getElementById('cfg-suppress-zero')?.checked ?? false;

  return {
    cols,
    rows,
    size,
    valueRange: [valMin, valMax],
    rule: makeRule(ruleId, target),
    minSelection,
    maxSelection,
    straightLine,
    suppressZero,
  };
}

function startGame() {
  if (game) game.destroy();
  const config = readConfig();
  game = new Game(canvas, config);
  // Expose for debugging in the console.
  window.__numberCrush = game;
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  menu.style.display = 'none';
  gameArea.style.display = '';
  startGame();
});

restartBtn?.addEventListener('click', () => {
  gameArea.style.display = 'none';
  menu.style.display = '';
  if (game) {
    game.destroy();
    game = null;
  }
});
