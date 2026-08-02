// Public API surface for Chromatic (spec §8).
//
// The core colorspace conversions, geometry kernel, projection, distortion
// analysis, and minimal exporters are re-exported here. The DSL and numerical
// solver (roadmap v0.3) are not yet part of this surface.

export { Palette } from './geometry/point-set.js';

export * as manifold from './geometry/manifold.js';
export * as group from './geometry/group.js';
export * as graph from './geometry/graph.js';
export * as sampling from './geometry/sampling.js';

export { convert, SPACES } from './colorspace/convert.js';
export { euclidean, deltaEOK, deltaE76, deltaE2000 } from './colorspace/distance.js';

export { project, SUPPORTED_SPACES } from './project/projector.js';
export { distortionReport } from './solver/distortion.js';

export { exportCSS } from './export/css.js';
export { exportJSON } from './export/json.js';
export { exportTerminal } from './export/terminal.js';
