/**
 * main.js — application entry point (§6.1).
 * Wires config -> simulation -> renderer -> UI and owns the redraw scheduler.
 * Note the separation of loops: simulation.js schedules *steps*, main.js
 * schedules *frames*, so tick rate and refresh rate stay independent (§8).
 */

import { Config, fromHashString } from './config.js';
import { Simulation } from './simulation.js';
import { Renderer } from './renderer.js';
import { UI } from './ui.js';
import { PRESETS } from './presets.js';

const canvas = document.getElementById('grid-canvas');
const panelRoot = document.getElementById('panel');
const toolbarRoot = document.getElementById('playback');
const statusRoot = document.getElementById('status');
const legendRoot = document.getElementById('legend');

// Configuration may be seeded from a share link: index.html#cfg=<json>
const config = new Config(fromHashString(location.hash));
const simulation = new Simulation(config);
const renderer = new Renderer(canvas, config);
const ui = new UI({
  config,
  simulation,
  renderer,
  canvas,
  panelRoot,
  toolbarRoot,
  statusRoot,
  legendRoot,
  presets: PRESETS,
});

// ---------------------------------------------------------------- redraw loop
let dirty = true;
const markDirty = () => {
  dirty = true;
};

simulation.on('step', markDirty);
simulation.on('reset', markDirty);
simulation.on('paint', markDirty);
simulation.on('change', markDirty);
config.subscribe(markDirty);

function frame() {
  if (dirty) {
    renderer.draw(simulation);
    ui.updateStatus();
    dirty = false;
  }
  requestAnimationFrame(frame);
}

requestAnimationFrame(frame);

// Status/rate readout should keep ticking even between redraws.
setInterval(() => ui.updateStatus(), 500);

window.addEventListener('hashchange', () => {
  const patch = fromHashString(location.hash);
  if (Object.keys(patch).length) {
    config.loadJSON(patch);
    simulation.reset();
  }
});

// Handy for console experimentation.
window.PIDCA = { config, simulation, renderer, ui };
