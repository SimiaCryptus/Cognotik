// Bootstrap: wires the core session, UI layers, and gestures together.

import { GameSession } from '../game/session.js';
import { evaluateStars } from '../game/stars.js';
import { allLevels, findLevel } from '../game/levels/index.js';
import { generateRandom } from '../game/generator.js';
import { legalVerbs, combineNeedsInput } from '../core/legality.js';
import { findNode, isNum, isSum, isProduct, isGroup, membersOf } from '../core/expression.js';
import { serialize, parse } from '../core/serialize.js';
import { el, clear } from '../util/dom.js';
import { Stage } from './stage.js';
import { History } from './history.js';
import { RadialMenu } from './radial-menu.js';
import { SplitChooser } from './split-chooser.js';
import { GestureRecognizer } from './gestures.js';
import { celebrate } from './animate.js';
import { SettingsMenu, DEFAULT_SETTINGS } from './settings.js';

export function boot(mount) {
  console.log('[app] booting Arithmetic Dynamics');
  const state = { level: null, session: null, settings: { ...DEFAULT_SETTINGS } };

  // Layout scaffold.
  const stageEl = el('div', { class: 'stage', id: 'stage' });
  const menuEl = el('div', { class: 'radial-layer', id: 'radial' });
  const chooserEl = el('div', { class: 'chooser-layer', id: 'chooser' });
  const historyEl = el('div', { class: 'history', id: 'history' });
  const toolbarEl = el('div', { class: 'toolbar', id: 'toolbar' });
  const levelBarEl = el('div', { class: 'level-bar', id: 'levelbar' });
  const settingsEl = el('div', { class: 'settings', id: 'settings-menu' });
  const statusEl = el('div', { class: 'status', id: 'status' });

  const stageWrap = el('div', { class: 'stage-wrap' }, [stageEl, menuEl, chooserEl]);

  clear(mount);
  mount.append(
    levelBarEl,
    el('div', { class: 'main' }, [
      el('div', { class: 'play-col' }, [statusEl, stageWrap, toolbarEl]),
      historyEl,
    ]),
  );

  const stage = new Stage(stageEl, { onSelect: handleSelect });
  const history = new History(historyEl, { onRewind: handleRewind });
  const radial = new RadialMenu(menuEl, { onVerb: applyVerb });
  const chooser = new SplitChooser(chooserEl, { onChoose: onSplitChoose });
  const settings = new SettingsMenu(settingsEl, {
    settings: state.settings,
    onChange: (s) => {
      state.settings = s;
    },
  });

  // selection is an array of 0..2 ids.
  let selection = [];
  let splitTargetId = null;
  let splitMode = 'split';
  // Clicking/tapping empty gamespace clears the current selection.
  stageEl.addEventListener('pointerdown', (e) => {
    if (e.target.closest && e.target.closest('.tile')) return;
    if (selection.length) clearSelection();
  });

  new GestureRecognizer(stageEl, {
    onTap: (id, elm, kind) => handleSelect(id, elm),
    onLongPress: (id, elm) => openMenu([id], elm),
    onDragCombine: (fromId, toId) => tryPair(fromId, toId, 'combine'),
    onDragSwap: (fromId, toId) => tryPair(fromId, toId, 'swap'),
  });

  buildLevelBar();
  buildToolbar();
  loadLevel(allLevels[0].id);

  function buildLevelBar() {
    clear(levelBarEl);
    levelBarEl.appendChild(el('span', { class: 'brand' }, 'Arithmetic Dynamics'));
    const sel = el('select', {
      class: 'level-select',
      onChange: (e) => loadLevel(e.target.value),
    });
    for (const lvl of allLevels) {
      sel.appendChild(el('option', { value: lvl.id }, lvl.id));
    }
    state._levelSelect = sel;
    const randBtn = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: () => loadGenerated(),
      },
      '🎲 Random',
    );
    const customInput = el('input', {
      type: 'text',
      class: 'level-custom-input',
      placeholder: 'e.g. 4 + 19 * 2',
    });
    const loadCustom = () => {
      const text = customInput.value.trim();
      if (!text) return;
      loadFromText(text);
    };
    customInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') loadCustom();
    });
    const customBtn = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: loadCustom,
      },
      '⌨ Load',
    );
    levelBarEl.append(sel, randBtn, customInput, customBtn, settingsEl);
  }

  function buildToolbar() {
    clear(toolbarEl);
    const undoBtn = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: () => {
          state.session.undo();
          syncHistoryToSession();
        },
      },
      '↶ Undo',
    );
    const redoBtn = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: () => {
          state.session.redo();
          syncHistoryToSession();
        },
      },
      '↷ Redo',
    );
    const resetBtn = el(
      'button',
      {
        class: 'tool-btn',
        type: 'button',
        onClick: () => {
          state.session.reset();
          history.reset();
        },
      },
      '⟲ Reset',
    );
    toolbarEl.append(undoBtn, redoBtn, resetBtn);
  }

  function bindSession() {
    state.session.on('changed', () => {
      stage.setSelection(selection.length ? selection : null);
      stage.render(state.session.expr);
      updateStatus();
    });
    state.session.on('solved', () => onSolved());
  }

  function loadLevel(id) {
    const level = findLevel(id);
    if (!level) {
      console.warn(`[app] loadLevel: no level found for id "${id}"`);
      return;
    }
    console.log(`[app] loadLevel`, { id });
    startLevel(level, id);
  }

  function loadGenerated() {
    const s = state.settings || {};
    const level = generateRandom({
      allowMultiply: s.allowMultiply !== undefined ? s.allowMultiply : true,
      minTerm: s.minTerm,
      maxTerm: s.maxTerm,
      ops: s.ops,
    });
    startLevel(level, null);
  }
  // Build a level from a free-text expression the player typed in.
  function loadFromText(text) {
    try {
      parse(text); // validate before committing
    } catch (err) {
      flashError(`Could not parse "${text}": ${err.message}`);
      return;
    }
    const level = {
      id: null,
      start: text,
      allowedVerbs: [
        'split',
        'factorize',
        'swap',
        'group',
        'ungroup',
        'combine',
        'cancel',
        'distribute',
        'extract',
      ],
      allowedOps: ['+', '-', '*', '/'],
      stars: [{ id: 'solve', label: 'Solve it', test: (s) => s.isSolved() }],
      hint: null,
    };
    startLevel(level, null);
  }

  function startLevel(level, id) {
    console.log(`[app] startLevel`, { id, start: level.start, allowedVerbs: level.allowedVerbs });
    state.level = level;
    state.session = new GameSession(level);
    selection = [];
    radial.hide();
    chooser.hide();
    history.reset();
    if (id && state._levelSelect) state._levelSelect.value = id;
    bindSession();
    stage.setSelection(null);
    stage.render(state.session.expr);
    updateStatus();
  }

  function updateStatus() {
    clear(statusEl);
    const s = state.session;
    const hintText = state.level.hint ? ` · Hint: ${state.level.hint}` : '';
    statusEl.appendChild(el('span', {}, `Moves: ${s.moveCount}${hintText}`));
  }
  // Resolve the difficulty passed to legality checks. For 'custom' we pass a
  // parameter object { threshold, factors } that difficultyAllows understands;
  // named difficulties are passed through as strings.
  function resolveDifficulty() {
    const s = state.settings || {};
    if (s.difficulty === 'custom') {
      return {
        threshold: s.customThreshold !== null ? s.customThreshold : 10,
        factors: s.customFactors && s.customFactors.length ? s.customFactors : [2, 5],
      };
    }
    return s.difficulty;
  }

  // Tap: build up a selection of up to two adjacent siblings.
  function handleSelect(id, elm) {
    if (selection.includes(id)) {
      selection = selection.filter((x) => x !== id);
    } else {
      // Extend the selection when the new id is a sibling of the current
      // selection (members of the same commutative container). This allows
      // collecting a common factor across 2+ terms of a sum. Otherwise the
      // selection resets to just the new id.
      if (selection.length >= 1 && areAdjacentSiblings(selection[0], id)) {
        selection = [...selection, id];
      } else {
        selection = [id];
      }
    }
    stage.setSelection(selection.length ? selection : null);
    stage.render(state.session.expr);
    if (!selection.length) {
      radial.hide();
      return;
    }
    openMenu(selection, findTileEl(selection[selection.length - 1]));
  }

  function areAdjacentSiblings(aId, bId) {
    const fa = findNode(state.session.expr, aId);
    const fb = findNode(state.session.expr, bId);
    if (!fa || !fb || !fa.parent || fa.parent !== fb.parent) return false;
    if (!(isSum(fa.parent) || isProduct(fa.parent))) return false;
    // Members of the same commutative container may be paired even when
    // they are not physically adjacent.
    return true;
  }

  function findTileEl(id) {
    return stageEl.querySelector(`.tile[data-id="${id}"]`);
  }
  function clearSelection() {
    selection = [];
    stage.setSelection(null);
    stage.render(state.session.expr);
    radial.hide();
  }

  function openMenu(ids, elm) {
    const verbs = legalVerbs(state.session.expr, ids, state.session.allowedVerbs, {
      difficulty: resolveDifficulty(),
    });
    if (verbs.length === 0) {
      radial.hide();
      return;
    }
    const rect = elm ? elm.getBoundingClientRect() : null;
    radial.show(verbs, rect);
  }

  function applyVerb(verb) {
    if (!selection.length) return;

    if (verb === 'split') {
      const id = selection[0];
      const found = findNode(state.session.expr, id);
      if (found && isNum(found.node)) openSplit(id, found.node.value, 'split');
      return;
    }
    if (verb === 'factorize') {
      const id = selection[0];
      const found = findNode(state.session.expr, id);
      if (found && isNum(found.node)) doFactorize(id, found.node.value);
      return;
    }
    if (verb === 'ungroup') {
      commit('ungroup', selection[0]);
      return;
    }
    if (verb === 'group') {
      commit('group', selection.slice());
      return;
    }
    if (verb === 'distribute' && selection.length === 2) {
      commitDistribute(selection[0], selection[1]);
      return;
    }
    if (verb === 'extract' && selection.length >= 2) {
      commit('extract', selection.slice());
      return;
    }
    if (verb === 'combine' && selection.length === 2) {
      tryCombine(selection[0], selection[1]);
      return;
    }
    // combine / swap / cancel need two ids.
    if (selection.length === 2) {
      commit(verb, selection[0], selection[1]);
    }
  }

  function openSplit(id, value, mode = 'split') {
    splitTargetId = id;
    splitMode = mode;
    chooser.show(value, mode);
  }
  // Completely factorize a number into its prime factorization and apply
  // it directly (no chooser popup). If the number is prime or |value| < 2,
  // there is nothing to do.
  function doFactorize(id, value) {
    const into = primeFactorString(value);
    if (!into) {
      flashError(`${value} cannot be factorized further`);
      selection = [];
      return;
    }
    const before = serialize(state.session.expr);
    try {
      state.session.apply('factorize', id, { into });
      history.push(before, 'factorize');
      maybeAutoUngroupFactor();
    } catch (err) {
      flashError(err.message);
    }
    selection = [];
  }
  // Build a "p * q * r" string of the prime factorization of |value|.
  // Returns null when there is no non-trivial factorization (prime or
  // magnitude < 2). The sign is carried by the split machinery via the
  // target's neg flag, so we factor the magnitude only.
  function primeFactorString(value) {
    let v = Math.abs(value);
    if (v < 4) return null;
    const factors = [];
    for (let f = 2; f * f <= v; f++) {
      while (v % f === 0) {
        factors.push(f);
        v /= f;
      }
    }
    if (v > 1) factors.push(v);
    if (factors.length < 2) return null;
    return factors.join(' * ');
  }
  // After a factorize, the replacement product is wrapped in a group. If
  // it sits directly inside a product it can be spliced inline.
  function maybeAutoUngroupFactor() {
    autoUngroupPass();
  }

  function onSplitChoose(into) {
    if (!splitTargetId) return;
    const before = serialize(state.session.expr);
    const verb = splitMode === 'factor' ? 'factorize' : 'split';
    try {
      state.session.apply(verb, splitTargetId, { into });
      history.push(before, verb);
      maybeAutoUngroup();
    } catch (err) {
      flashError(err.message);
    }
    splitTargetId = null;
    selection = [];
  }
  // After a split/factorize, the replacement is wrapped in a group to
  // preserve unambiguity. If that group carries no neg/recip flag it is
  // safe to splice inline immediately, so do so automatically.
  function maybeAutoUngroup() {
    const found = findNode(state.session.expr, splitTargetId);
    // The split target id no longer exists; instead locate the new group
    // by scanning for a plain (non-inverse) group whose parent is a
    // matching container. Simpler: attempt ungroup on every plain group
    // that is a direct member of a sum/product and collapses cleanly.
    void found;
    autoUngroupPass();
  }
  // Repeatedly splice away any plain, splicable, or trivial (<= 1 member)
  // groups until none remain. Each successful ungroup is recorded so the
  // history and undo stack stay consistent.
  function autoUngroupPass() {
    let guard = 0;
    while (guard++ < 64) {
      const gid = findPlainSplicableGroup(state.session.expr);
      if (!gid) break;
      try {
        const before = serialize(state.session.expr);
        state.session.apply('ungroup', gid);
        history.push(before, 'ungroup');
      } catch {
        // Leave the group in place if ungroup is illegal, and stop to
        // avoid an infinite loop over an un-splicable group.
        break;
      }
    }
  }
  // Find a group that is a direct member of a matching container
  // (sum-in-sum or product-in-product) and carries no neg/recip flag.
  function findPlainSplicableGroup(root, parent = null) {
    if (!root) return null;
    if (isGroup(root)) {
      const inline =
        parent &&
        !root.neg &&
        !root.recip &&
        ((isSum(parent) && isSum(root.child)) || (isProduct(parent) && isProduct(root.child)));
      // A group of one-or-fewer elements is always safe to unwrap: a
      // single atom child, or a container child with <= 1 member.
      const child = root.child;
      const childMembers = isSum(child) || isProduct(child) ? membersOf(child).length : null;
      // Trivial groups (single atom child, or container with <= 1 member)
      // can always be unwrapped — including at the top level (no parent).
      const trivial = !root.neg && !root.recip && (childMembers === null || childMembers <= 1);
      if (inline || trivial) return root.id;
      return findPlainSplicableGroup(root.child, root);
    }
    if (isSum(root) || isProduct(root)) {
      for (const m of membersOf(root)) {
        const hit = findPlainSplicableGroup(m, root);
        if (hit) return hit;
      }
    }
    return null;
  }

  function commit(verb, ...args) {
    const before = serialize(state.session.expr);
    try {
      state.session.apply(verb, ...args);
      history.push(before, verb);
      selection = [];
      stage.setSelection(null);
      radial.hide();
      autoUngroupPass();
    } catch (err) {
      console.warn(`[app] commit "${verb}" failed`, err);
      flashError(err.message);
    }
  }
  // Distribute a factor across a grouped sum. Determines which selected id
  // is the group and which is the plain factor, applies `distribute`, then
  // runs an auto-ungroup pass to tidy the result.
  function commitDistribute(idA, idB) {
    const fa = findNode(state.session.expr, idA);
    const fb = findNode(state.session.expr, idB);
    if (!fa || !fb) return;
    let factorId;
    let groupId;
    if (isGroup(fa.node) && !isGroup(fb.node)) {
      groupId = idA;
      factorId = idB;
    } else if (isGroup(fb.node) && !isGroup(fa.node)) {
      groupId = idB;
      factorId = idA;
    } else {
      flashError('Distribute needs one factor and one group');
      return;
    }
    const before = serialize(state.session.expr);
    try {
      state.session.apply('distribute', factorId, groupId);
      history.push(before, 'distribute');
      selection = [];
      stage.setSelection(null);
      radial.hide();
      autoUngroupPass();
    } catch (err) {
      console.warn('[app] distribute failed', err);
      flashError(err.message);
    }
  }
  // Combine two numbers. If the difficulty gate does not permit an
  // automatic fold, prompt the player to enter the answer; otherwise
  // combine immediately.
  function tryCombine(aId, bId) {
    const fa = findNode(state.session.expr, aId);
    const fb = findNode(state.session.expr, bId);
    if (!fa || !fb || !isNum(fa.node) || !isNum(fb.node)) return;
    if (!fa.parent || fa.parent !== fb.parent) return;
    const container = fa.parent;
    if (!(isSum(container) || isProduct(container))) return;
    const needsInput = combineNeedsInput(container, fa.node, fb.node, resolveDifficulty());
    if (!needsInput) {
      commit('combine', aId, bId);
      return;
    }
    const opGlyph = isSum(container) ? '+' : '×';
    chooser.askAnswer(`${serialize(fa.node)} ${opGlyph} ${serialize(fb.node)} = ?`, (answer) => {
      const before = serialize(state.session.expr);
      try {
        state.session.apply('combine', aId, bId, Number(answer));
        history.push(before, 'combine');
        selection = [];
        stage.setSelection(null);
        radial.hide();
      } catch (err) {
        flashError(err.message);
      }
    });
  }

  // Drag-driven pairing: attempt the given verb on two dragged atoms if
  // they are adjacent siblings; combine falls back to cancel when apt.
  function tryPair(fromId, toId, preferred) {
    if (!areAdjacentSiblings(fromId, toId)) return;
    const verbs = legalVerbs(state.session.expr, [fromId, toId], state.session.allowedVerbs, {
      difficulty: resolveDifficulty(),
    });
    let verb = preferred;
    if (preferred === 'combine' && !verbs.includes('combine') && verbs.includes('cancel')) {
      verb = 'cancel';
    }
    if (!verbs.includes(verb)) return;
    if (verb === 'combine') {
      tryCombine(fromId, toId);
    } else {
      commit(verb, fromId, toId);
    }
  }

  function syncHistoryToSession() {
    history.truncate(state.session.moveCount);
    selection = [];
    stage.setSelection(null);
  }

  function handleRewind(index) {
    const target = index;
    while (state.session.moveCount > target && state.session.canUndo()) {
      state.session.undo();
    }
    history.truncate(state.session.moveCount);
  }

  function onSolved() {
    radial.hide();
    const { earned } = evaluateStars(state.session, state.level);
    const banner = el('div', { class: 'solved-banner' }, [
      el('div', { class: 'solved-title' }, `Solved! = ${serialize(state.session.expr)}`),
      el(
        'div',
        { class: 'solved-stars' },
        state.level.stars.map((star) =>
          el(
            'span',
            {
              class: `star ${earned.includes(star.id) ? 'earned' : ''}`,
              title: star.label || star.id,
            },
            earned.includes(star.id) ? '★' : '☆',
          ),
        ),
      ),
    ]);
    statusEl.appendChild(banner);
    celebrate(stageEl.querySelector('.tile'));
  }

  function flashError(msg) {
    console.error(`[app] error:`, msg);
    const note = el('div', { class: 'error-note' }, msg);
    statusEl.appendChild(note);
    setTimeout(() => note.remove(), 1800);
  }
}
