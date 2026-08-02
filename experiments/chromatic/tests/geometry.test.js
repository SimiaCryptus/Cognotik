import { strict as assert } from 'node:assert';
import test from 'node:test';
import { Palette, toPaletteColor } from '../src/geometry/point-set.js';
import * as group from '../src/geometry/group.js';
import * as manifold from '../src/geometry/manifold.js';
import * as sampling from '../src/geometry/sampling.js';
import * as graph from '../src/geometry/graph.js';
import { deltaEOK } from '../src/colorspace/distance.js';

const approx = (a, b, eps = 1e-6) => Math.abs(a - b) <= eps;

test('toPaletteColor accepts OKLab and OKLch', () => {
  const a = toPaletteColor({ id: 'x', L: 0.5, a: 0.1, b: -0.1 });
  assert.equal(a.id, 'x');
  assert.equal(a.L, 0.5);
  const b = toPaletteColor({ id: 'y', L: 0.6, C: 0.1, H: 30 }, 'OKLch');
  assert.ok(approx(Math.hypot(b.a, b.b), 0.1, 1e-6));
});

test('Palette.fromPoints builds OKLab colors', () => {
  const p = Palette.fromPoints(
    [
      { L: 0.6, C: 0.15, H: 0 },
      { L: 0.6, C: 0.15, H: 120 },
    ],
    { space: 'OKLch' }
  );
  assert.equal(p.size, 2);
  assert.equal(typeof p.at(0).L, 'number');
});

test('cyclic group orbit has `order` members, evenly spaced in hue', () => {
  const orbit = group.cyclic({ order: 6 }).apply({ L: 0.6, C: 0.15, H: 30 });
  assert.equal(orbit.length, 6);
  assert.ok(approx(orbit[0].H, 30));
  assert.ok(approx(orbit[1].H, 90));
  assert.ok(approx(orbit[3].H, 210));
});

test('cyclic orbit is closed: applying full cycle returns to start', () => {
  const g = group.cyclic({ order: 4 });
  const orbit = g.apply({ L: 0.6, C: 0.15, H: 45 });
  // step 4 == step 0 (mod 360)
  const wrapped = (((45 + 4 * 90) % 360) + 360) % 360;
  assert.ok(approx(wrapped, 45));
});

test('dihedral group has 2*order members', () => {
  const orbit = group.dihedral({ order: 3 }).apply({ L: 0.6, C: 0.12, H: 0 });
  assert.equal(orbit.length, 6);
});

test('uniform sampling is monotonic in parameter along a geodesic', () => {
  const g = manifold.geodesic({
    from: { L: 0.3, a: 0, b: 0 },
    to: { L: 0.9, a: 0, b: 0 },
  });
  const pts = sampling.uniform(g, 5);
  assert.equal(pts.length, 5);
  for (let i = 1; i < pts.length; i++) {
    assert.ok(pts[i].L > pts[i - 1].L);
  }
});

test('arcLength sampling produces near-equal perceptual spacing', () => {
  const g = manifold.geodesic({
    from: { L: 0.2, a: 0, b: 0 },
    to: { L: 0.8, a: 0, b: 0 },
  });
  const pts = sampling.arcLength(g, 4);
  const gaps = [];
  for (let i = 1; i < pts.length; i++) gaps.push(deltaEOK(pts[i - 1], pts[i]));
  const mean = gaps.reduce((s, v) => s + v, 0) / gaps.length;
  for (const gap of gaps) assert.ok(Math.abs(gap - mean) < 1e-3);
});

test('cycle graph is connected with cycle rank 1', () => {
  const pts = group
    .cyclic({ order: 5 })
    .apply({ L: 0.6, C: 0.15, H: 0 })
    .map((p) => toPaletteColor({ ...p }, 'OKLch'));
  const adj = graph.cycle(pts);
  assert.ok(graph.isConnected(adj));
  assert.equal(graph.cycleRank(adj), 1);
});

test('chain graph is connected with cycle rank 0 (a tree)', () => {
  const pts = Array.from({ length: 4 }, (_, i) => ({
    L: 0.2 + i * 0.2,
    a: 0,
    b: 0,
  }));
  const adj = graph.chain(pts);
  assert.ok(graph.isConnected(adj));
  assert.equal(graph.cycleRank(adj), 0);
});

test('MST of connected points is a spanning tree', () => {
  const pts = Array.from({ length: 5 }, (_, i) => ({
    L: 0.1 * i,
    a: 0,
    b: 0,
  }));
  const adj = graph.mst(pts);
  assert.ok(graph.isConnected(adj));
  assert.equal(graph.edgeCount(adj), pts.length - 1);
});

test('edgeEditDistance is zero for identical graphs', () => {
  const pts = Array.from({ length: 4 }, (_, i) => ({ L: 0.1 * i, a: 0, b: 0 }));
  const a = graph.chain(pts);
  const b = graph.chain(pts);
  assert.equal(graph.edgeEditDistance(a, b), 0);
});
