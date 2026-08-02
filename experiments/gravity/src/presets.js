import { Body } from './body.js';

// All positions in world units centered around origin (0,0).
export const presets = {
  binary: {
    label: 'Stable Binary',
    params: { G: 1, c: 1000, alpha: 0, dt: 0.01, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: -80, y: 0 },
        velocity: { x: 0, y: 1.6 },
        mass: 100,
        color: '#5ad1ff',
        radius: 10,
      }),
      new Body({
        position: { x: 80, y: 0 },
        velocity: { x: 0, y: -1.6 },
        mass: 100,
        color: '#ff7b5a',
        radius: 10,
      }),
    ],
  },

  precessing: {
    label: 'Precessing Orbit',
    params: { G: 1, c: 24, alpha: 0.6, dt: 0.008, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.3 },
        mass: 400,
        color: '#ffd45a',
        radius: 16,
      }),
      new Body({
        position: { x: 130, y: 0 },
        velocity: { x: 0, y: 1.9 },
        mass: 20,
        color: '#5ad1ff',
        radius: 7,
      }),
    ],
  },

  chaotic: {
    label: 'Chaotic Fly-by',
    params: { G: 1, c: 18, alpha: 0.4, dt: 0.006, epsilon: 1.5 },
    bodies: () => [
      new Body({
        position: { x: -120, y: -30 },
        velocity: { x: 1.2, y: 0.4 },
        mass: 160,
        color: '#5aff9d',
        radius: 11,
      }),
      new Body({
        position: { x: 120, y: 40 },
        velocity: { x: -1.1, y: 0.6 },
        mass: 120,
        color: '#ff5ad1',
        radius: 9,
      }),
    ],
  },

  // --- circular two-body orbit about a common barycenter ---
  circular: {
    label: 'Circular Barycentric',
    params: { G: 1, c: 1000, alpha: 0, dt: 0.01, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: -100, y: 0 },
        velocity: { x: 0, y: 1.118 },
        mass: 150,
        color: '#5ad1ff',
        radius: 12,
      }),
      new Body({
        position: { x: 100, y: 0 },
        velocity: { x: 0, y: -1.118 },
        mass: 150,
        color: '#ff7b5a',
        radius: 12,
      }),
    ],
  },

  // --- highly eccentric orbit: dramatic perihelion sweeps ---
  eccentric: {
    label: 'Eccentric Comet',
    params: { G: 1, c: 60, alpha: 0.3, dt: 0.005, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.08 },
        mass: 800,
        color: '#ffd45a',
        radius: 20,
      }),
      new Body({
        position: { x: 200, y: 0 },
        velocity: { x: 0, y: 1.7 },
        mass: 15,
        color: '#9d5aff',
        radius: 6,
      }),
    ],
  },

  // --- strong relativity: rapid rosette precession ---
  rosette: {
    label: 'Rosette (Strong GR)',
    params: { G: 1, c: 12, alpha: 1.0, dt: 0.004, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.2 },
        mass: 600,
        color: '#ff5a5a',
        radius: 18,
      }),
      new Body({
        position: { x: 90, y: 0 },
        velocity: { x: 0, y: 2.4 },
        mass: 18,
        color: '#5ad1ff',
        radius: 7,
      }),
    ],
  },

  // --- tight relativistic inspiral-flavored fast orbit ---
  inspiral: {
    label: 'Fast Inner Orbit',
    params: { G: 1, c: 10, alpha: 0.9, dt: 0.003, epsilon: 1.5 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.45 },
        mass: 500,
        color: '#ffae5a',
        radius: 16,
      }),
      new Body({
        position: { x: 55, y: 0 },
        velocity: { x: 0, y: 3.0 },
        mass: 40,
        color: '#5affe1',
        radius: 8,
      }),
    ],
  },

  // --- nearly equal masses on a slow wide ellipse ---
  slowWaltz: {
    label: 'Slow Waltz',
    params: { G: 1, c: 400, alpha: 0.1, dt: 0.012, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: -140, y: 0 },
        velocity: { x: 0.1, y: 0.85 },
        mass: 120,
        color: '#a05aff',
        radius: 12,
      }),
      new Body({
        position: { x: 140, y: 0 },
        velocity: { x: -0.1, y: -0.95 },
        mass: 110,
        color: '#5affb0',
        radius: 11,
      }),
    ],
  },

  // --- grazing hyperbolic encounter, no capture ---
  flyby: {
    label: 'Hyperbolic Flyby',
    params: { G: 1, c: 80, alpha: 0.2, dt: 0.006, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: -260, y: -60 },
        velocity: { x: 2.6, y: 0 },
        mass: 500,
        color: '#ffd45a',
        radius: 18,
      }),
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: 0 },
        mass: 30,
        color: '#5ad1ff',
        radius: 8,
      }),
    ],
  },

  // --- light, fast satellite around a heavy primary ---
  planetMoon: {
    label: 'Heavy Primary',
    params: { G: 1, c: 200, alpha: 0.05, dt: 0.008, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.05 },
        mass: 900,
        color: '#ff8a5a',
        radius: 22,
      }),
      new Body({
        position: { x: 110, y: 0 },
        velocity: { x: 0, y: 2.9 },
        mass: 8,
        color: '#bcd6ff',
        radius: 5,
      }),
    ],
  },

  // --- pure retardation showcase: low c, no alpha corrections ---
  retardation: {
    label: 'Pure Retardation',
    params: { G: 1, c: 8, alpha: 0, dt: 0.004, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: -70, y: 0 },
        velocity: { x: 0, y: 1.4 },
        mass: 200,
        color: '#5ad1ff',
        radius: 12,
      }),
      new Body({
        position: { x: 70, y: 0 },
        velocity: { x: 0, y: -1.4 },
        mass: 200,
        color: '#ff7b5a',
        radius: 12,
      }),
    ],
  },

  // --- astronomically-motivated: Mercury around the Sun (qualitative) ---
  // Heavy primary, tiny fast inner planet on an eccentric orbit with enough
  // relativity to exhibit visible perihelion precession (Mercury's hallmark).
  mercurySun: {
    label: '☉ Mercury–Sun',
    params: { G: 1, c: 40, alpha: 0.5, dt: 0.004, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.02 },
        mass: 1000,
        color: '#ffd23f',
        radius: 22,
      }),
      new Body({
        position: { x: 120, y: 0 },
        velocity: { x: 0, y: 2.55 },
        mass: 3,
        color: '#c9b8a8',
        radius: 5,
      }),
    ],
  },

  // --- collapsing black-hole pair: tight, fast, strongly relativistic ---
  // Two heavy compact objects spiraling in a deep, fast inner orbit. Not GR,
  // but the low c + high alpha give a dramatic rosette "inspiral" flavor.
  blackHolePair: {
    label: '⬤ Collapsing BH Pair',
    params: { G: 1, c: 9, alpha: 1.0, dt: 0.0025, epsilon: 1.2 },
    bodies: () => [
      new Body({
        position: { x: -42, y: 0 },
        velocity: { x: 0, y: 2.0 },
        mass: 400,
        color: '#9d7bff',
        radius: 13,
      }),
      new Body({
        position: { x: 42, y: 0 },
        velocity: { x: 0, y: -2.0 },
        mass: 400,
        color: '#ff6bd0',
        radius: 13,
      }),
    ],
  },

  // --- Earth–Moon style: light fast satellite, mild relativity ---
  earthMoon: {
    label: '🌍 Earth–Moon',
    params: { G: 1, c: 300, alpha: 0.02, dt: 0.008, epsilon: 2 },
    bodies: () => [
      new Body({
        position: { x: 0, y: 0 },
        velocity: { x: 0, y: -0.04 },
        mass: 800,
        color: '#5aa9ff',
        radius: 18,
      }),
      new Body({
        position: { x: 130, y: 0 },
        velocity: { x: 0, y: 2.45 },
        mass: 10,
        color: '#d8dde6',
        radius: 6,
      }),
    ],
  },

  // --- pulsar binary: two neutron-star-like masses, eccentric & relativistic ---
  pulsarBinary: {
    label: '✦ Pulsar Binary',
    params: { G: 1, c: 22, alpha: 0.8, dt: 0.003, epsilon: 1.5 },
    bodies: () => [
      new Body({
        position: { x: -90, y: 0 },
        velocity: { x: 0, y: 0.9 },
        mass: 300,
        color: '#5affe1',
        radius: 10,
      }),
      new Body({
        position: { x: 110, y: 0 },
        velocity: { x: 0.05, y: -1.4 },
        mass: 200,
        color: '#fff35a',
        radius: 9,
      }),
    ],
  },
};

export const presetKeys = Object.keys(presets);
