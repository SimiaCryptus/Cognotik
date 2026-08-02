import { strict as assert } from 'node:assert';
import test from 'node:test';
import {
  createDocument,
  addNode,
  addLink,
  addGroup,
  setAnchor,
  serialize,
  deserialize,
} from '../src/designer/document.js';
import { compileObjective } from '../src/designer/objective.js';
import { solve } from '../src/designer/solve.js';
import { oklchToOklab, oklabToOklch } from '../src/colorspace/oklab.js';

function ok(L, C, H) {
  return oklchToOklab({ L, C, H });
}

test('document round-trips through JSON', () => {
  const doc = createDocument();
  addNode(doc, { name: 'a', oklab: ok(0.6, 0.1, 0) });
  addNode(doc, { name: 'b', oklab: ok(0.6, 0.1, 120) });
  const back = deserialize(serialize(doc));
  assert.equal(back.nodes.length, 2);
  assert.equal(back.nodes[0].name, 'a');
});

test('soft anchor pulls a coordinate toward its target', () => {
  const doc = createDocument({ viewer: { space: 'OKLab', planeAxes: ['a', 'b'], depthAxis: 'L' } });
  const n = addNode(doc, { name: 'x', oklab: { L: 0.3, a: 0, b: 0 } });
  setAnchor(doc, n.id, { dimension: 'L', target: 0.7, weight: 1, hard: false });
  const before = Math.abs(doc.nodes[0].oklab.L - 0.7);
  solve(doc, { maxIterations: 300, gamut: false });
  const after = Math.abs(doc.nodes[0].oklab.L - 0.7);
  assert.ok(after < before);
  assert.ok(after < 0.01);
});

test('hard anchor is enforced by construction (not a free variable)', () => {
  const doc = createDocument({ viewer: { space: 'OKLab', planeAxes: ['a', 'b'], depthAxis: 'L' } });
  const n = addNode(doc, { name: 'x', oklab: { L: 0.3, a: 0.1, b: 0 } });
  setAnchor(doc, n.id, { dimension: 'L', target: 0.55, hard: true });
  const { varMap } = compileObjective(doc);
  // L slot removed => only a,b remain free for the single node
  assert.equal(varMap.slots.length, 2);
  solve(doc, { maxIterations: 50, gamut: false });
  // held exactly at the hard target
  assert.ok(Math.abs(doc.nodes[0].oklab.L - 0.55) < 1e-9);
});

test('free length group equalizes link lengths', () => {
  const doc = createDocument({ viewer: { space: 'OKLab', planeAxes: ['a', 'b'], depthAxis: 'L' } });
  const c = addNode(doc, { name: 'c', oklab: { L: 0.6, a: 0, b: 0 } });
  const p1 = addNode(doc, { name: 'p1', oklab: { L: 0.6, a: 0.1, b: 0 } });
  const p2 = addNode(doc, { name: 'p2', oklab: { L: 0.6, a: 0.02, b: 0 } });
  // pin the center and outer chroma-ish positions loosely via anchors on L
  const l1 = addLink(doc, c.id, p1.id);
  const l2 = addLink(doc, c.id, p2.id);
  addGroup(doc, { kind: 'length', linkIds: [l1.id, l2.id], mode: 'free', weight: 1 });

  const { objective, varMap, residuals } = compileObjective(doc);
  const startJ = objective(varMap.pack());
  const result = solve(doc, { maxIterations: 400, gamut: false });
  assert.ok(result.value < startJ);

  // lengths should be close after solving
  const r = result.residuals ?? residuals(varMap.pack());
  const lens = r.groups[0].members;
  assert.ok(Math.abs(lens[0] - lens[1]) < 1e-2);
});

test('residual breakdown flags worst node', () => {
  const doc = createDocument({
    viewer: { space: 'OKLch', planeAxes: ['hue', 'chroma'], depthAxis: 'lightness' },
  });
  const n = addNode(doc, { name: 'x', oklab: ok(0.5, 0.05, 0) });
  setAnchor(doc, n.id, { dimension: 'lightness', target: 0.9, weight: 1, hard: false });
  const { residuals, varMap } = compileObjective(doc);
  const r = residuals(varMap.pack());
  assert.equal(r.worstNode, n.id);
  assert.ok(r.total > 0);
});

test('fixed-target length group drives links toward the pinned length', () => {
  const doc = createDocument({ viewer: { space: 'OKLab', planeAxes: ['a', 'b'], depthAxis: 'L' } });
  const c = addNode(doc, { name: 'c', oklab: { L: 0.6, a: 0, b: 0 } });
  const p = addNode(doc, { name: 'p', oklab: { L: 0.6, a: 0.05, b: 0 } });
  const l = addLink(doc, c.id, p.id);
  const g = addGroup(doc, {
    kind: 'length',
    linkIds: [l.id],
    mode: 'fixed',
    target: 0.2,
    weight: 1,
  });
  // single-member fixed group is meaningful (unlike a single-member free group)
  solve(doc, { maxIterations: 400, gamut: false });
  const { residuals, varMap } = compileObjective(doc);
  const r = residuals(varMap.pack());
  assert.ok(Math.abs(r.groups[0].members[0] - 0.2) < 1e-2);
});
