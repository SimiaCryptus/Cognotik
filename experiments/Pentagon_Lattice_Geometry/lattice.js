// lattice.js
// Thin wrapper: delegates to ngon.js for the actual construction.
// Kept for backward-compatibility with main.js imports.

export { buildNgonLattice as makeLattice, buildSierpinski } from './ngon.js';
