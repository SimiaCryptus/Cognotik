// A single tile element mapping to an AST node (or a structural token
// such as a parenthesis). Tiles are keyed by a stable key so the stage
// can reconcile + animate between states.

import { el } from '../util/dom.js';

const ROUND_NUMBERS = new Set([5, 10, 15, 20, 25, 30, 50, 100]);

// Build a number tile.
export function numberTile(node, { selected, onSelect } = {}) {
  const round = ROUND_NUMBERS.has(node.value);
  const t = el(
    'div',
    {
      class: `tile tile-num${round ? ' tile-round' : ''}${selected ? ' selected' : ''}`,
      dataset: { id: node.id, kind: 'num', key: `num:${node.id}` },
      role: 'button',
      tabindex: '0',
    },
    String(node.value),
  );
  wireSelect(t, node.id, onSelect);
  return t;
}

// Build an operator tile. `pair` is 'add' for +/- and 'mul' for */÷.
export function operatorTile(node, { selected, onSelect, rawGlyph } = {}) {
  const isMul = node.op === '*' || node.op === '/' || node.op === '×' || node.op === '÷';
  const pair = isMul ? 'mul' : 'add';
  const glyph = rawGlyph || { '+': '+', '-': '−', '*': '×', '/': '÷' }[node.op] || node.op;
  const t = el(
    'div',
    {
      class: `tile tile-op tile-op-${pair}${selected ? ' selected' : ''}${
        rawGlyph ? ' tile-op-sign' : ''
      }`,
      dataset: { id: node.id, kind: 'op', key: `op:${node.id}` },
      role: rawGlyph ? undefined : 'button',
      tabindex: rawGlyph ? undefined : '0',
    },
    glyph,
  );
  wireSelect(t, node.id, onSelect);
  return t;
}

// Build a parenthesis tile. `groupId` links it to its Group node so a
// tap selects the group for ungrouping.
export function parenTile(groupId, which, { selected, onSelect } = {}) {
  const t = el(
    'div',
    {
      class: `tile tile-paren${selected ? ' selected' : ''}`,
      dataset: { id: groupId, kind: 'group', key: `paren:${which}:${groupId}` },
      role: 'button',
      tabindex: '0',
    },
    which === 'open' ? '(' : ')',
  );
  wireSelect(t, groupId, onSelect);
  return t;
}

function wireSelect(t, id, onSelect) {
  if (!onSelect) return;
  t.addEventListener('click', (e) => {
    e.stopPropagation();
    onSelect(id, t, e);
  });
  t.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelect(id, t, e);
    }
  });
}
