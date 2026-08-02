import { strict as assert } from 'node:assert';
import test from 'node:test';
import { project } from '../src/project/projector.js';
import { distortionReport } from '../src/solver/distortion.js';
import { Palette } from '../src/geometry/point-set.js';
import * as group from '../src/geometry/group.js';

// A 6-color hue cycle constructed in OKLab (the flagship demo palette).
function hueCycle() {
  const orbit = group.cyclic({ order: 6 }).apply({ L: 0.6, C: 0.15, H: 0 });
  return Palette.fromPoints(
    orbit.map((p, i) => ({ id: `c${i}`, role: 'accent', ...p })),
    { space: 'OKLch' }
  );
}

test('report exposes all invariant families', () => {
  const pal = hueCycle();
  const projected = project(pal, 'HSL');
  const report = distortionReport(pal, projected);
  const s = report.summary();
  assert.equal(typeof s.orderingViolations, 'number');
  assert.equal(typeof s.avgHueDistortion, 'number');
  assert.equal(typeof s.distanceCorrelation, 'number');
  assert.equal(typeof s.adjacencyEditDistance, 'number');
  assert.equal(typeof s.gamutClipped, 'number');
});

test('projecting into OKLab yields near-zero distortion', () => {
  const pal = hueCycle();
  const projected = project(pal, 'OKLab');
  const report = distortionReport(pal, projected);
  assert.equal(report.orderingViolations, 0);
  assert.ok(report.avgHueDistortion < 1e-6);
  assert.ok(report.distanceCorrelation > 0.999);
  assert.equal(report.adjacencyEditDistance, 0);
});

test('equal-lightness cycle has no lightness ordering to violate', () => {
  const pal = hueCycle();
  const projected = project(pal, 'HSL');
  const report = distortionReport(pal, projected);
  // All L equal in source => no strict lightness order => no violations.
  assert.equal(report.orderingLightness, 0);
});

test('HSL projection distorts hue relative to OKLab', () => {
  const pal = hueCycle();
  const hsl = distortionReport(pal, project(pal, 'HSL'));
  const ok = distortionReport(pal, project(pal, 'OKLab'));
  // Naive HSL should distort hue spacing more than the identity projection.
  assert.ok(hsl.avgHueDistortion >= ok.avgHueDistortion);
});
