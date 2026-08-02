// Bootstrap: mode switching, UI wiring, animation loop.

import {Lattice} from './model/lattice.js';
import {buildPairs, energy, kinetic, angularMomentum, torque} from './model/physics.js';
import {step} from './model/integrator.js';
import {relax, analyze} from './model/analysis.js';
import {exportJSON, importJSON} from './io/serialize.js';
import {SceneRenderer} from './ui/canvas.js';
import {renderHeatmap, renderSpectrum} from './ui/heatmap.js';

const $ = (id) => document.getElementById(id);

const params = {k: 1.0, I: 1.0, gamma: 0.0, m: 1.0};
const lattice = new Lattice(48, true);

const renderer = new SceneRenderer($('scene'));

let mode = 'draw';
let running = false;
let simTime = 0;
let E0 = null;
let stepCount = 0;
let state = null; // { theta, thetaDot } during simulation
let savedAngles = null; // to restore on reset

// mode-animation state
let modeAnim = null; // { modes, values, omega, base, idx, phase }

// ---- Mode switching ----
document.querySelectorAll('.mode-tabs button').forEach((btn) => {
    btn.addEventListener('click', () => setMode(btn.dataset.mode));
});

function setMode(m) {
    mode = m;
    document
        .querySelectorAll('.mode-tabs button')
        .forEach((b) => b.classList.toggle('active', b.dataset.mode === m));
    $('panel-draw').style.display = m === 'draw' ? '' : 'none';
    $('panel-sim').style.display = m === 'sim' ? '' : 'none';
    $('panel-analysis').style.display = m === 'analysis' ? '' : 'none';

    if (m === 'sim') {
        initSim();
    } else {
        running = false;
        modeAnim = null;
    }
    draw();
}

function initSim() {
    state = {theta: lattice.angles(), thetaDot: new Float64Array(lattice.count)};
    savedAngles = lattice.angles();
    simTime = 0;
    E0 = null;
    stepCount = 0;
    // ---- diagnostics on sim start ----
    if (lattice.count === 0) {
        console.warn('[sim] initSim: no magnets placed — nothing to simulate.');
        return;
    }
    const pairs = buildPairs(lattice.positions(), params);
    const tau = torque(state.theta, pairs, lattice.count);
    let maxTau = 0;
    for (let i = 0; i < tau.length; i++) maxTau = Math.max(maxTau, Math.abs(tau[i]));
    const U0 = energy(state.theta, pairs);
    console.log('[sim] initSim:', {
        magnets: lattice.count,
        pairs: pairs.length,
        params: {...params, h: +$('h').value},
        theta: Array.from(state.theta),
        U0,
        maxTorque: maxTau,
    });
    if (maxTau < 1e-9) {
        console.warn(
            '[sim] All torques ≈ 0 and velocities are zero: the system is at a ' +
                'stationary equilibrium, so nothing will move. Perturb the angles ' +
                '(Draw mode: shift-drag a magnet) or change k/positions.',
        );
    }
}

// ---- Draw-mode interaction ----
let dragging = null; // { idx, cx, cy }

$('scene').addEventListener('mousedown', (e) => {
    if (mode !== 'draw') return;
    const rect = $('scene').getBoundingClientRect();
    const [wx, wy] = renderer.screenToWorld(e.clientX - rect.left, e.clientY - rect.top);
    const cell = lattice.worldToCell(wx, wy);
    const existing = lattice.atCell(cell);
    if (existing && e.shiftKey) {
        // start drag to set angle
        const [cwx, cwy] = lattice.cellToWorld(existing.cell);
        dragging = {mg: existing, cx: cwx, cy: cwy};
    } else {
        lattice.toggle(cell);
        updateCount();
        draw();
    }
});

$('scene').addEventListener('mousemove', (e) => {
    if (!dragging) return;
    const rect = $('scene').getBoundingClientRect();
    const [wx, wy] = renderer.screenToWorld(e.clientX - rect.left, e.clientY - rect.top);
    dragging.mg.theta = Math.atan2(wy - dragging.cy, wx - dragging.cx);
    draw();
});

window.addEventListener('mouseup', () => {
    dragging = null;
});

$('clear').addEventListener('click', () => {
    lattice.clear();
    updateCount();
    draw();
});

function updateCount() {
    $('count').textContent = lattice.count;
}

// ---- Draw-mode config ----
$('pitch').addEventListener('input', (e) => {
    lattice.pitch = +e.target.value;
    $('pitch-val').textContent = e.target.value;
    draw();
});
$('snap').addEventListener('change', (e) => {
    lattice.snap = e.target.checked;
});

// ---- Simulation controls ----
function bindSlider(id, key, fmt = (v) => v.toFixed(2)) {
    $(id).addEventListener('input', (e) => {
        params[key] = +e.target.value;
        $(id + '-val').textContent = fmt(params[key]);
    });
}

bindSlider('k', 'k', (v) => v.toFixed(1));
bindSlider('I', 'I', (v) => v.toFixed(1));
bindSlider('gamma', 'gamma');
bindSlider('h', 'h', (v) => v.toFixed(3));

$('h').addEventListener('input', (e) => {
    $('h-val').textContent = (+e.target.value).toFixed(3);
});

$('play').addEventListener('click', () => {
    running = !running;
    $('play').textContent = running ? '⏸ Pause' : '▶ Play';
});
$('step').addEventListener('click', () => {
    doStep();
    draw();
    updateDiagnostics();
});
$('reset').addEventListener('click', () => {
    if (savedAngles) lattice.setAngles(savedAngles);
    initSim();
    running = false;
    $('play').textContent = '▶ Play';
    draw();
    updateDiagnostics();
});

function doStep() {
    if (!state || lattice.count === 0) return;
    const pairs = buildPairs(lattice.positions(), params);
    const method = $('integrator').value;
    const h = +$('h').value;
    const before = Float64Array.from(state.theta);
    state = step(state, pairs, params, h, method);
    lattice.setAngles(state.theta);
    simTime += h;
    stepCount++;
    // throttled diagnostics: every 100 steps report motion so we can see
    // whether the integrator is actually advancing the state.
    if (stepCount % 100 === 0) {
        let maxDTheta = 0,
            maxThetaDot = 0;
        for (let i = 0; i < state.theta.length; i++) {
            maxDTheta = Math.max(maxDTheta, Math.abs(state.theta[i] - before[i]));
            maxThetaDot = Math.max(maxThetaDot, Math.abs(state.thetaDot[i]));
        }
        const tau = torque(state.theta, pairs, state.theta.length);
        let maxTau = 0;
        for (let i = 0; i < tau.length; i++) maxTau = Math.max(maxTau, Math.abs(tau[i]));
        console.log(
            `[sim] step ${stepCount} t=${simTime.toFixed(3)} ` +
                `maxΔθ/step=${maxDTheta.toExponential(2)} ` +
                `max|θ̇|=${maxThetaDot.toExponential(2)} ` +
                `max|τ|=${maxTau.toExponential(2)} method=${method}`,
        );
    }
}

function updateDiagnostics() {
    if (!state || lattice.count === 0) return;
    const pairs = buildPairs(lattice.positions(), params);
    const U = energy(state.theta, pairs);
    const T = kinetic(state.thetaDot, params.I);
    const E = T + U;
    const L = angularMomentum(state.thetaDot, params.I);
    if (E0 === null) E0 = E;
    $('t-val').textContent = simTime.toFixed(2);
    $('E-val').textContent = E.toFixed(6);
    $('T-val').textContent = T.toFixed(6);
    $('U-val').textContent = U.toFixed(6);
    $('L-val').textContent = L.toFixed(6);
    const dE = Math.abs(E0) > 1e-12 ? (E - E0) / Math.abs(E0) : E - E0;
    $('dE-val').textContent = dE.toExponential(3);
}

// ---- Analysis controls ----
$('relax').addEventListener('click', () => {
    if (lattice.count === 0) return setStatus('No magnets to relax', true);
    const pairs = buildPairs(lattice.positions(), params);
    const {theta, iterations, gradNorm} = relax(lattice.angles(), pairs, lattice.count);
    lattice.setAngles(theta);
    savedAngles = lattice.angles();
    setStatus(`Relaxed in ${iterations} iters, |∇U|=${gradNorm.toExponential(2)}`);
    draw();
});

$('analyze').addEventListener('click', () => {
    if (lattice.count === 0) return setStatus('No magnets to analyze', true);
    const pairs = buildPairs(lattice.positions(), params);
    const res = analyze(lattice.angles(), pairs, lattice.count, params);
    renderHeatmap($('heatmap'), res.C);
    renderSpectrum($('spectrum'), res.omega);
    $('heatmap-wrap').classList.add('show');
    const n = lattice.count;
    $('mode-pick').max = String(n - 1);
    modeAnim = {
        modes: res.modes,
        values: res.values,
        omega: res.omega,
        base: lattice.angles(),
        idx: 0,
        phase: 0,
    };
    showMode(0);
    setStatus('Modes computed');
});

$('mode-pick').addEventListener('input', (e) => showMode(+e.target.value));
$('anim-mode').addEventListener('click', () => {
    if (!modeAnim) return;
    modeAnim.animating = !modeAnim.animating;
    $('anim-mode').textContent = modeAnim.animating ? 'Stop' : 'Animate Mode';
});

function showMode(idx) {
    if (!modeAnim) return;
    modeAnim.idx = idx;
    modeAnim.phase = 0;
    $('mode-val').textContent = idx;
    const w = modeAnim.omega[idx];
    $('omega-val').textContent = w.toFixed(4);
    const unstable = modeAnim.values[idx] < -1e-9;
    $('stab-val').textContent = unstable ? '⚠ unstable' : '';
    $('stab-val').style.color = unstable ? '#e77' : '#8a8';
    draw();
}

// ---- Import / Export ----
$('export').addEventListener('click', () => {
    $('json').value = exportJSON(lattice, params);
    setStatus('Exported to text box');
});
$('import').addEventListener('click', () => {
    try {
        const doc = importJSON($('json').value);
        lattice.pitch = doc.grid.pitch;
        lattice.snap = doc.grid.snap;
        Object.assign(params, doc.params);
        lattice.clear();
        for (const mg of doc.magnets) {
            const added = lattice.add(mg.cell, mg.theta);
            added.id = mg.id;
        }
        lattice._nextId = Math.max(0, ...doc.magnets.map((m) => m.id)) + 1;
        syncUI();
        updateCount();
        setStatus(`Imported ${doc.magnets.length} magnets`);
        draw();
    } catch (e) {
        setStatus(e.message, true);
    }
});

function syncUI() {
    $('pitch').value = lattice.pitch;
    $('pitch-val').textContent = lattice.pitch;
    $('snap').checked = lattice.snap;
    $('k').value = params.k;
    $('k-val').textContent = params.k.toFixed(1);
    $('I').value = params.I;
    $('I-val').textContent = params.I.toFixed(1);
    $('gamma').value = params.gamma;
    $('gamma-val').textContent = params.gamma.toFixed(2);
}

function setStatus(msg, err = false) {
    const el = $('status');
    el.textContent = msg;
    el.className = 'status' + (err ? ' err' : '');
}

// ---- Animation loop ----
function draw() {
    let modeVec = null;
    if (mode === 'analysis' && modeAnim) {
        const v = modeAnim.modes[modeAnim.idx];
        modeVec = v;
        // apply displaced angles for preview
        const amp = 0.6 * Math.sin(modeAnim.phase);
        const disp = Float64Array.from(modeAnim.base);
        for (let i = 0; i < disp.length; i++) disp[i] += amp * v[i];
        lattice.setAngles(disp);
    }
    renderer.render(lattice, {modeVec});
}

function loop() {
    if (mode === 'sim' && running) {
        // multiple substeps for smoothness
        for (let s = 0; s < 4; s++) doStep();
        updateDiagnostics();
        draw();
    } else if (mode === 'analysis' && modeAnim && modeAnim.animating) {
        modeAnim.phase += 0.08;
        draw();
    }
    requestAnimationFrame(loop);
}

// ---- Init ----
function seed() {
    // a small 2-magnet default so the app isn't empty.
    // NOTE: (θ=0, θ=π) along the x-axis is exactly the head-to-tail
    // equilibrium — with zero initial velocity nothing would move in Sim
    // mode. Seed slightly perturbed angles so the dynamics are visible.
    lattice.add([-1, 0], 0.6);
    lattice.add([1, 0], Math.PI - 0.6);
    updateCount();
    syncUI();
    draw();
}
// Debug handle: inspect state/params/lattice from the console, e.g.
//   window.__ml.state, window.__ml.params, window.__ml.lattice
window.__ml = {
    get state() {
        return state;
    },
    get params() {
        return params;
    },
    get lattice() {
        return lattice;
    },
    get running() {
        return running;
    },
    get mode() {
        return mode;
    },
    step: () => {
        doStep();
        draw();
        updateDiagnostics();
    },
};
console.log('[magnet-lattice] booted. Debug handle available as window.__ml');

seed();
loop();
