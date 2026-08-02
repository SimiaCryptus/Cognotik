// Section builders. Each returns { id, title, prose(HTML), build(ctx) }.
// ctx provides { container, canvas, global, landscape helpers }.
import { LANDSCAPES } from './landscape.js';
import { Scene2D } from './scene2d.js';
import { Scene3D } from './scene3d.js';
import { Plot1D } from './plot1d.js';
import {
  pathPolyline,
  pathPoint,
  pathTangent,
  armijoSearch,
  originDirectional,
} from './qqn-path.js';
import { inverseHessianDir, gdStep, makeAdam, makeLbfgs } from './optimizers.js';
import { buildSpline } from './spline.js';
import { neg, dot, norm, add, scale } from './mathlib.js';
import { advance, reset } from './state.js';

const C = {
  gradient: '#0072B2',
  oracle: '#E69F00',
  path: '#009E73',
  accepted: '#CC79A7',
  bracket: '#999',
  iterate: '#D55E00',
};

function legend(items) {
  return `<div class="legend">${items
    .map(([c, t]) => `<span><span class="swatch" style="background:${c}"></span>${t}</span>`)
    .join('')}</div>`;
}
// Remove any stale per-section control panels from a viz wrapper before a
// section (re)mounts. Sections are unmounted/remounted as they scroll in and
// out of view; without this, appended `.panel-controls` accumulate.
function clearPanelControls(container) {
  container.querySelectorAll('.viz .panel-controls').forEach((el) => el.remove());
}

// Helper to run a full QQN trajectory on a landscape.
function runQQN(land, start, n = 40) {
  let st = { landscapeKey: null, iterateHistory: [start.slice()], probeHistory: [] };
  const pts = [start.slice()];
  const paths = [];
  let x = start.slice();
  for (let i = 0; i < n; i++) {
    const { g, dir } = inverseHessianDir(land, x);
    const gradDir = neg(g),
      oracleDir = dir;
    const res = armijoSearch(land, x, g, gradDir, oracleDir, {});
    const d = pathPoint(gradDir, oracleDir, res.accepted);
    paths.push({ x: x.slice(), gradDir, oracleDir, t: res.accepted });
    x = [x[0] + d[0], x[1] + d[1]];
    pts.push(x.slice());
    if (norm(g) < 1e-4) break;
  }
  return { pts, paths };
}

export function buildSections(global) {
  const S = [];

  // ---- Section 0: Hook ----
  S.push({
    id: 'hook',
    title: 'The teaser',
    prose: `<h2>0 · Most optimizers pick a direction and hope.</h2>
          <p>This one draws a little curve and <em>searches</em> it. Watch the
          green parabola flash at each step before QQN commits.</p>
          <p><strong>Click anywhere on the plot</strong> to move the start point;
          the run re-solves and re-draws.</p>`,
    build(ctx) {
      const cv = ctx.canvas;
      let land = LANDSCAPES[ctx.global.landscapeKey] || LANDSCAPES.rosenbrock;
      const sc = new Scene2D(cv, land.domain);
      let start = ctx.global.start.slice();
      let anim = 0,
        raf = null;
      const draw = () => {
        land = LANDSCAPES[ctx.global.landscapeKey] || LANDSCAPES.rosenbrock;
        sc.setDomain(land.domain);
        sc.clear();
        sc.drawContours(land);
        const { pts, paths } = runQQN(land, start, 40);
        const upto = Math.min(pts.length, Math.floor(anim));
        sc.drawPolyline(pts.slice(0, upto + 1), C.iterate, 2);
        const pi = Math.min(paths.length - 1, Math.floor(anim));
        if (paths[pi]) {
          const pl = pathPolyline(paths[pi].x, paths[pi].gradDir, paths[pi].oracleDir);
          sc.drawPolyline(pl, C.path, 2);
        }
        for (let i = 0; i <= upto && i < pts.length; i++) sc.drawDot(pts[i], C.iterate, 3);
        anim += 0.4;
        if (anim > pts.length + 4) anim = 0;
        raf = requestAnimationFrame(draw);
      };
      cv.addEventListener('pointerdown', (e) => {
        const r = cv.getBoundingClientRect();
        start = sc.toWorld(e.clientX - r.left, e.clientY - r.top);
        anim = 0;
      });
      const onLand = () => {
        land = LANDSCAPES[ctx.global.landscapeKey] || LANDSCAPES.rosenbrock;
        start = ctx.global.start.slice();
        anim = 0;
      };
      const onStart = () => {
        start = ctx.global.start.slice();
        anim = 0;
      };
      ctx.global.addEventListener('landscape', onLand);
      ctx.global.addEventListener('start', onStart);
      draw();
      return {
        teardown() {
          cancelAnimationFrame(raf);
          ctx.global.removeEventListener('landscape', onLand);
          ctx.global.removeEventListener('start', onStart);
        },
      };
    },
  });

  // ---- Section 1: Loss landscape ----
  S.push({
    id: 'landscape',
    title: 'What is a loss landscape?',
    prose: `<h2>1 · Height = loss; we want to get low.</h2>
          <p>The same function shown two ways: a 3-D surface you can orbit, and a
          2-D contour map. Hover the contour to light up the matching point on the
          surface.</p>
          <p>Use the <strong>landscape selector</strong> (top-left) to swap
          functions — this duality is used everywhere after.</p>`,
    build(ctx) {
      ctx.container.querySelector('.viz').innerHTML =
        `<canvas class="s3d" style="height:220px"></canvas>
             <canvas class="s2d" style="height:220px"></canvas>
             <div class="readout" id="ro-1">hover the contour…</div>`;
      let land = LANDSCAPES[global.landscapeKey];
      const c3 = ctx.container.querySelector('.s3d');
      const c2 = ctx.container.querySelector('.s2d');
      const s3 = new Scene3D(c3, land.domain);
      const s2 = new Scene2D(c2, land.domain);
      const ro = ctx.container.querySelector('#ro-1');
      let hover = null;
      const redraw = () => {
        land = LANDSCAPES[global.landscapeKey];
        s3.domain = land.domain;
        s2.setDomain(land.domain);
        s2.clear();
        s2.drawContours(land);
        if (hover) s2.drawDot(hover, C.iterate, 5);
        s3.render(land, { highlight: hover });
      };
      c2.addEventListener('pointermove', (e) => {
        const r = c2.getBoundingClientRect();
        hover = s2.toWorld(e.clientX - r.left, e.clientY - r.top);
        ro.textContent = `x=(${hover[0].toFixed(2)}, ${hover[1].toFixed(2)})  f=${land.f(hover[0], hover[1]).toFixed(3)}`;
        redraw();
      });
      const onLand = () => redraw();
      global.addEventListener('landscape', onLand);
      redraw();
      return {
        teardown() {
          global.removeEventListener('landscape', onLand);
        },
      };
    },
  });

  // ---- Section 2: What is an optimizer? ----
  S.push({
    id: 'optimizer',
    title: 'What is an optimizer?',
    prose: `<h2>2 · An optimizer is a loop.</h2>
          <ol><li>Look around (gradient).</li><li>Pick a direction.</li>
          <li>Pick how far (step).</li><li>Move. Repeat.</li></ol>
          <p>QQN reorganizes steps 2 &amp; 3 into a single question:
          <em>where on the curve?</em></p>
          <p>Press <strong>Step</strong> to advance the marble one iteration.</p>`,
    build(ctx) {
      const land = LANDSCAPES[global.landscapeKey];
      const sc = new Scene2D(ctx.canvas, land.domain);
      let x = global.start.slice(),
        pts = [x.slice()];
      const stage = ['look (∇f)', 'direction', 'step', 'move'];
      let phase = 0;
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        sc.drawPolyline(pts, C.iterate, 2);
        pts.forEach((p) => sc.drawDot(p, C.iterate, 3));
        const g = L.grad(x[0], x[1]);
        if (phase >= 1) sc.drawArrow(x, add(x, scale(neg(g), 0.05)), C.gradient, 2);
        sc.drawDot(x, C.iterate, 6);
        sc.drawLabel(x, stage[Math.min(phase, 3)], C.iterate);
      };
      const onT = (e) => {
        if (e.detail.action === 'reset') {
          x = global.start.slice();
          pts = [x.slice()];
          phase = 0;
        } else if (e.detail.action === 'step') {
          phase++;
          if (phase > 3) {
            const L = LANDSCAPES[global.landscapeKey];
            const g = L.grad(x[0], x[1]);
            x = add(x, scale(g, -0.01));
            pts.push(x.slice());
            phase = 0;
          }
        }
        draw();
      };
      global.addEventListener('transport', onT);
      const onL = () => {
        x = global.start.slice();
        pts = [x.slice()];
        draw();
      };
      global.addEventListener('landscape', onL);
      draw();
      return {
        teardown() {
          global.removeEventListener('transport', onT);
          global.removeEventListener('landscape', onL);
        },
      };
    },
  });

  // ---- Section 3: Gradient descent ----
  S.push({
    id: 'gd',
    title: 'Gradient descent',
    prose: `<h2>3 · x ← x − η∇f</h2>
          <p>Follow the negative gradient. Too small a step crawls; too big a step
          zig-zags and diverges — especially on the ill-conditioned valley.</p>
          <p><em>Robust but slow.</em> (See <code>background.md</code>.)</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<label>learning rate η = <span id="eta-v">0.01</span></label>
            <input type="range" id="eta" min="-4" max="0" step="0.05" value="-2" />
            <label>iterations = <span id="iter-v">60</span></label>
            <input type="range" id="iters" min="1" max="200" step="1" value="60" />
            ${legend([
              [C.gradient, '−∇f'],
              [C.iterate, 'trajectory'],
            ])}`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES.illcond.domain);
      let eta = 0.01,
        iters = 60;
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        let x = global.start.slice();
        const pts = [x.slice()];
        for (let i = 0; i < iters; i++) {
          x = gdStep(L, x, { eta, seed: global.seed });
          if (Math.abs(x[0]) > 1e4) break;
          pts.push(x.slice());
        }
        sc.drawPolyline(pts, C.iterate, 2);
        const g = L.grad(global.start[0], global.start[1]);
        sc.drawArrow(global.start, add(global.start, scale(neg(g), 0.05)), C.gradient, 2);
        pts.slice(0, Math.min(pts.length, 30)).forEach((p) => sc.drawDot(p, C.iterate, 2));
      };
      pc.querySelector('#eta').addEventListener('input', (e) => {
        eta = Math.pow(10, parseFloat(e.target.value));
        pc.querySelector('#eta-v').textContent = eta.toFixed(4);
        draw();
      });
      pc.querySelector('#iters').addEventListener('input', (e) => {
        iters = parseInt(e.target.value, 10);
        pc.querySelector('#iter-v').textContent = iters;
        draw();
      });
      const onL = () => draw();
      global.addEventListener('landscape', onL);
      global.addEventListener('start', onL);
      draw();
      return {
        teardown() {
          global.removeEventListener('landscape', onL);
          global.removeEventListener('start', onL);
        },
      };
    },
  });

  // ---- Section 4: Momentum & Adam ----
  S.push({
    id: 'adam',
    title: 'Momentum & Adam',
    prose: `<h2>4 · Momentum &amp; Adam</h2>
          <p>Momentum is a heavy ball rolling through the valley. Adam adds a
          per-axis adaptive step from second-moment estimates.</p>
          <div class="aside-box"><strong>Hold onto Adam.</strong> In QQN, Adam
          isn't a rival optimizer — it's an <em>oracle</em>, a direction supplier
          we'll plug in at Section 11.</div>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `
            <button class="btn active" data-mode="gd">GD</button>
            <button class="btn" data-mode="momentum">Momentum</button>
            <button class="btn" data-mode="adam">Adam</button>
            <label>β1 <input type="range" id="b1" min="0" max="0.99" step="0.01" value="0.9"></label>
            <label>β2 <input type="range" id="b2" min="0.9" max="0.9999" step="0.0001" value="0.999"></label>
            ${legend([
              [C.iterate, 'trajectory'],
              [C.gradient, 'velocity'],
            ])}`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES.noisy.domain);
      let mode = 'gd',
        b1 = 0.9,
        b2 = 0.999;
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L, { seed: global.seed });
        let x = global.start.slice();
        const pts = [x.slice()];
        if (mode === 'gd') {
          for (let i = 0; i < 60; i++) {
            x = gdStep(L, x, { eta: 0.02, seed: global.seed });
            pts.push(x.slice());
          }
        } else {
          const opt = makeAdam({ mode, beta1: b1, beta2: b2 });
          for (let i = 0; i < 60; i++) {
            const r = opt.step(L, x, global.seed);
            x = r.next;
            pts.push(x.slice());
          }
        }
        sc.drawPolyline(pts, C.iterate, 2);
        pts.slice(0, 40).forEach((p) => sc.drawDot(p, C.iterate, 2));
      };
      pc.querySelectorAll('[data-mode]').forEach((b) =>
        b.addEventListener('click', () => {
          pc.querySelectorAll('[data-mode]').forEach((x) => x.classList.remove('active'));
          b.classList.add('active');
          mode = b.dataset.mode;
          draw();
        })
      );
      pc.querySelector('#b1').addEventListener('input', (e) => {
        b1 = +e.target.value;
        draw();
      });
      pc.querySelector('#b2').addEventListener('input', (e) => {
        b2 = +e.target.value;
        draw();
      });
      const onL = () => draw();
      global.addEventListener('landscape', onL);
      global.addEventListener('start', onL);
      global.addEventListener('seed', onL);
      draw();
      return {
        teardown() {
          ['landscape', 'start', 'seed'].forEach((t) => global.removeEventListener(t, onL));
        },
      };
    },
  });

  // ---- Section 5: L-BFGS ----
  S.push({
    id: 'lbfgs',
    title: 'L-BFGS (quasi-Newton)',
    prose: `<h2>5 · Reshape the space so the valley looks round.</h2>
          <p>Second-order methods use curvature to take bigger, better-aimed steps
          (the long orange arrow). Near a minimum they're superlinear.</p>
          <p><strong>But they're fragile.</strong> On a saddle, <code>−H∇f</code>
          can point <em>uphill</em>. Hit "break it" to expose the ascent-direction
          failure — the seed of why QQN needs a fallback.</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<label>history size <span id="hs-v">5</span></label>
            <input type="range" id="hs" min="1" max="10" value="5">
            <button class="btn" id="breakit">break it (jump to saddle)</button>
            ${legend([
              [C.oracle, '−H∇f'],
              [C.iterate, 'trajectory'],
            ])}`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES.rosenbrock.domain);
      let hs = 5;
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        const opt = makeLbfgs({ historySize: hs });
        let x = global.start.slice();
        const pts = [x.slice()];
        for (let i = 0; i < 30; i++) {
          x = opt.step(L, x);
          pts.push(x.slice());
          if (norm(L.grad(x[0], x[1])) < 1e-4) break;
        }
        sc.drawPolyline(pts, C.iterate, 2);
        pts.forEach((p) => sc.drawDot(p, C.iterate, 3));
        // draw oracle direction at start
        const { g, dir } = inverseHessianDir(L, global.start);
        sc.drawArrow(global.start, add(global.start, scale(dir, 0.3)), C.oracle, 3);
        if (dot(g, dir) > 0) sc.drawLabel(global.start, '⚠ ascent!', '#d33', 10, 16);
      };
      pc.querySelector('#hs').addEventListener('input', (e) => {
        hs = +e.target.value;
        pc.querySelector('#hs-v').textContent = hs;
        draw();
      });
      pc.querySelector('#breakit').addEventListener('click', () => {
        global.landscapeKey = 'saddle';
        global.setStart(LANDSCAPES.saddle.start.slice());
        draw();
      });
      const onL = () => draw();
      global.addEventListener('landscape', onL);
      global.addEventListener('start', onL);
      draw();
      return {
        teardown() {
          global.removeEventListener('landscape', onL);
          global.removeEventListener('start', onL);
        },
      };
    },
  });

  // ---- Section 6: Trade-off ----
  S.push({
    id: 'tradeoff',
    title: 'The trade-off',
    prose: `<h2>6 · Robust-but-slow vs fast-but-fragile.</h2>
          <p>A synchronized race on the ill-conditioned valley. GD (blue) plods
          safely; L-BFGS (orange) races ahead — until you swap to a non-convex
          landscape and it stumbles.</p>
          <p><strong>This tension is the whole motivation for QQN.</strong></p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<button class="btn" id="race">run race</button>
            ${legend([
              [C.gradient, 'GD'],
              [C.oracle, 'L-BFGS'],
            ])}`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES.illcond.domain);
      let raf = null;
      const draw = (nGd, nLb) => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        let xg = global.start.slice();
        const g = [xg.slice()];
        for (let i = 0; i < nGd; i++) {
          xg = gdStep(L, xg, { eta: 0.02 });
          g.push(xg.slice());
        }
        const opt = makeLbfgs({ historySize: 5 });
        let xl = global.start.slice();
        const l = [xl.slice()];
        for (let i = 0; i < nLb; i++) {
          xl = opt.step(L, xl);
          l.push(xl.slice());
        }
        sc.drawPolyline(g, C.gradient, 2);
        sc.drawPolyline(l, C.oracle, 2);
        sc.drawDot(g[g.length - 1], C.gradient, 5);
        sc.drawDot(l[l.length - 1], C.oracle, 5);
      };
      pc.querySelector('#race').addEventListener('click', () => {
        cancelAnimationFrame(raf);
        let step = 0;
        const tick = () => {
          draw(step, step);
          step++;
          if (step < 40) raf = requestAnimationFrame(tick);
        };
        tick();
      });
      const onL = () => draw(0, 0);
      global.addEventListener('landscape', onL);
      global.addEventListener('start', onL);
      draw(0, 0);
      return {
        teardown() {
          cancelAnimationFrame(raf);
          global.removeEventListener('landscape', onL);
          global.removeEventListener('start', onL);
        },
      };
    },
  });

  // ---- Section 7: QQN blend ----
  S.push({
    id: 'qqn',
    title: "QQN: blend, don't choose",
    prose: `<h2>7 · The centerpiece.</h2>
          <p>From the current iterate: the gradient direction (blue), the oracle
          endpoint (orange, <strong>draggable</strong>), and the quadratic path
          <code>d(t) = t(1−t)(−∇f) + t²(−H∇f)</code> (green) connecting them.</p>
          <p>QQN refuses the binary choice. The path <em>starts</em> along the
          gradient and <em>ends</em> at the oracle.</p>
          <p><strong>Drag the orange dot</strong> to reshape d(1); <strong>scrub
          t</strong> to ride the curve.</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<label>t = <span id="t-v">0.50</span></label>
            <input type="range" id="tt" min="0" max="1" step="0.01" value="0.5">
            <div class="readout" id="ro-7"></div>
            ${legend([
              [C.gradient, '−∇f'],
              [C.oracle, '−H∇f (drag)'],
              [C.path, 'd(t)'],
            ])}`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const land = LANDSCAPES[global.landscapeKey];
      const sc = new Scene2D(ctx.canvas, land.domain);
      let t = 0.5;
      const x = global.start.slice();
      const { g } = inverseHessianDir(land, x);
      let gradDir = scale(neg(g), 0.4);
      let oracleDir = inverseHessianDir(land, x).dir;
      let dragging = false;
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        const pl = pathPolyline(x, gradDir, oracleDir);
        sc.drawPolyline(pl, C.path, 3);
        sc.drawArrow(x, add(x, gradDir), C.gradient, 2);
        const oPt = add(x, oracleDir);
        sc.drawArrow(x, oPt, C.oracle, 2);
        sc.drawDot(oPt, C.oracle, 7);
        const d = pathPoint(gradDir, oracleDir, t);
        const m = add(x, d);
        sc.drawDot(m, C.accepted, 6);
        sc.drawDot(x, C.iterate, 5);
        const fv = L.f(m[0], m[1]);
        pc.querySelector('#ro-7').textContent = `t=${t.toFixed(2)}  f(x+d(t))=${fv.toFixed(4)}`;
      };
      ctx.canvas.addEventListener('pointerdown', (e) => {
        const r = ctx.canvas.getBoundingClientRect();
        const w = sc.toWorld(e.clientX - r.left, e.clientY - r.top);
        if (norm([w[0] - (x[0] + oracleDir[0]), w[1] - (x[1] + oracleDir[1])]) < 0.3)
          dragging = true;
      });
      window.addEventListener('pointermove', (e) => {
        if (!dragging) return;
        const r = ctx.canvas.getBoundingClientRect();
        const w = sc.toWorld(e.clientX - r.left, e.clientY - r.top);
        oracleDir = [w[0] - x[0], w[1] - x[1]];
        draw();
      });
      window.addEventListener('pointerup', () => {
        dragging = false;
      });
      pc.querySelector('#tt').addEventListener('input', (e) => {
        t = +e.target.value;
        pc.querySelector('#t-v').textContent = t.toFixed(2);
        draw();
      });
      draw();
      return {
        teardown() {},
      };
    },
  });

  // ---- Section 8: three properties ----
  S.push({
    id: 'props',
    title: "The path's three properties",
    prose: `<h2>8 · Why the curve is trustworthy.</h2>
          <ul>
            <li><code>d(0)=0</code> — we start where we are.</li>
            <li><code>d'(0)=−∇f</code> — the initial tangent is steepest descent.</li>
            <li><code>d(1)=−H∇f</code> — the endpoint is the oracle.</li>
          </ul>
          <p>Because the path <em>begins</em> tangent to steepest descent, a small
          step always decreases f — <strong>the globalization guarantee</strong>.</p>
          <p>Toggle "verify tangent": −∇f overlays d'(0) exactly, regardless of the
          oracle endpoint.</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<label class="chk"><input type="checkbox" id="vt" checked> verify tangent</label>
            <div class="readout" id="ro-8"></div>`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const land = LANDSCAPES[global.landscapeKey];
      const sc = new Scene2D(ctx.canvas, land.domain);
      const x = global.start.slice();
      const { g, dir } = inverseHessianDir(land, x);
      const gradDir = scale(neg(g), 0.4);
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        const pl = pathPolyline(x, gradDir, dir);
        sc.drawPolyline(pl, C.path, 3);
        sc.drawDot(x, C.iterate, 6);
        sc.drawLabel(x, 'd(0)=0', C.iterate);
        const oPt = add(x, dir);
        sc.drawDot(oPt, C.oracle, 6);
        sc.drawLabel(oPt, 'd(1)=−H∇f', C.oracle);
        const tang = pathTangent(gradDir, dir, 0); // = gradDir
        sc.drawArrow(x, add(x, scale(tang, 0.5)), C.path, 3);
        if (pc.querySelector('#vt').checked) {
          sc.drawArrow(x, add(x, scale(neg(g), 0.2)), C.gradient, 1.5);
          sc.drawLabel(add(x, scale(neg(g), 0.2)), "d'(0)=−∇f", C.gradient, 6, -6);
        }
        const dd = originDirectional(g);
        pc.querySelector('#ro-8').textContent = `⟨∇f, d'(0)⟩ = −‖∇f‖² = ${dd.toFixed(4)}`;
      };
      pc.querySelector('#vt').addEventListener('change', draw);
      draw();
      return {
        teardown() {},
      };
    },
  });

  // ---- Section 9: Line search ----
  S.push({
    id: 'linesearch',
    title: 'Line search: walking t',
    prose: `<h2>9 · The search space is one-dimensional: the curve itself.</h2>
          <p>Below, φ(t)=f(x+d(t)). Probes appear as dots. The Armijo line
          φ(0)+c₁·t·φ'(0) must be undercut for acceptance.</p>
          <div class="callout">The line search is a <strong>first-class
          component</strong>, not a detail — the optimization quality is bounded by
          it.</div>`,
    build(ctx) {
      ctx.container.querySelector('.viz').innerHTML =
        `<canvas class="s2d" style="height:200px"></canvas>
             <canvas class="p1d" style="height:180px"></canvas>
             <div class="panel-controls">
               <button class="btn active" data-ls="backtracking">backtracking</button>
               <button class="btn" data-ls="armijo">armijo</button>
               <button class="btn" data-ls="strong_wolfe">strong_wolfe</button>
               <button class="btn" data-ls="fixed">fixed</button>
               <label>c1 <input type="range" id="c1" min="1e-4" max="0.5" step="0.001" value="0.1"></label>
               <div class="readout" id="ro-9"></div>
               ${legend([
                 [C.path, 'φ(t)'],
                 [C.bracket, 'Armijo line'],
                 [C.accepted, 'accepted'],
               ])}
               <div id="sw-warn"></div>
             </div>`;
      const land = LANDSCAPES[global.landscapeKey];
      const s2 = new Scene2D(ctx.container.querySelector('.s2d'), land.domain);
      const p1 = new Plot1D(ctx.container.querySelector('.p1d'));
      const x = global.start.slice();
      const { g, dir } = inverseHessianDir(land, x);
      const gradDir = neg(g);
      let c1 = 0.1,
        strat = 'backtracking';
      const phi = (t) => {
        const d = pathPoint(gradDir, dir, t);
        return land.f(x[0] + d[0], x[1] + d[1]);
      };
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        s2.clear();
        s2.drawContours(L);
        s2.drawPolyline(pathPolyline(x, gradDir, dir), C.path, 3);
        s2.drawDot(x, C.iterate, 5);
        // 1D
        let fmin = Infinity,
          fmax = -Infinity;
        for (let i = 0; i <= 60; i++) {
          const v = phi(i / 60);
          fmin = Math.min(fmin, v);
          fmax = Math.max(fmax, v);
        }
        p1.setRange(0, 1, fmin, fmax);
        p1.clear();
        p1.curve(phi, C.path);
        const f0 = phi(0),
          phi0p = dot(g, gradDir);
        p1.line(0, f0, 1, f0 + c1 * phi0p, C.bracket);
        // run search
        const res = armijoSearch(land, x, g, gradDir, dir, {
          c1,
          tStart: strat === 'fixed' ? 1 : 1,
          maxIter: strat === 'fixed' ? 1 : 20,
        });
        res.probes.forEach((pr) => p1.dot(pr.t, pr.f, pr.ok ? C.accepted : C.bracket, 4));
        p1.dot(res.accepted, phi(res.accepted), C.accepted, 6);
        const dacc = pathPoint(gradDir, dir, res.accepted);
        s2.drawDot([x[0] + dacc[0], x[1] + dacc[1]], C.accepted, 6);
        ctx.container.querySelector('#ro-9').textContent =
          `accepted t=${res.accepted.toFixed(3)}  φ(t)=${phi(res.accepted).toFixed(4)}`;
        ctx.container.querySelector('#sw-warn').innerHTML =
          strat === 'strong_wolfe'
            ? `<div class="warn-banner">strong_wolfe over-restricts the path step (see algorithm.md).</div>`
            : '';
      };
      ctx.container.querySelectorAll('[data-ls]').forEach((b) =>
        b.addEventListener('click', () => {
          ctx.container.querySelectorAll('[data-ls]').forEach((x) => x.classList.remove('active'));
          b.classList.add('active');
          strat = b.dataset.ls;
          draw();
        })
      );
      ctx.container.querySelector('#c1').addEventListener('input', (e) => {
        c1 = +e.target.value;
        draw();
      });
      draw();
      return {
        teardown() {},
      };
    },
  });

  // ---- Section 10: Spline search ----
  S.push({
    id: 'spline',
    title: 'Spline search',
    prose: `<h2>10 · Every probe gives value <em>and</em> slope.</h2>
          <p>A naive search throws the slope away. The cubic Hermite spline honors
          both — a richer model, fewer evaluations. Stationary points (closed-form
          roots) are marked as candidate steps.</p>
          <p><strong>Reflect tangents</strong> is the key idea: tangents opposing
          the secant slope create phantom minima; reflection flips their sign.</p>
          <div class="callout"><strong>Honesty:</strong> reflection is a heuristic.
          Safety comes from the outer <code>improves = cf &lt; bv</code> gate, not
          the reflection itself.</div>`,
    build(ctx) {
      ctx.container.querySelector('.viz').innerHTML =
        `<canvas class="p1d" style="height:260px"></canvas>
             <div class="panel-controls">
               <label class="chk"><input type="checkbox" id="reflect" checked> reflect tangents</label>
               <div class="readout" id="ro-10">drag control-point dots</div>
               ${legend([
                 [C.path, 'spline'],
                 [C.oracle, 'tangent stub'],
                 [C.accepted, 'stationary pt'],
               ])}
             </div>`;
      const p1 = new Plot1D(ctx.container.querySelector('.p1d'));
      // control points (t, f, slope m)
      let pts = [
        { t: 0.0, f: 1.0, m: -2.0 },
        { t: 0.5, f: 0.3, m: 1.5 },
        { t: 1.0, f: 0.6, m: -0.5 },
      ];
      let reflect = true,
        drag = null;
      const draw = () => {
        const fs = pts.map((p) => p.f);
        p1.setRange(0, 1, Math.min(...fs), Math.max(...fs));
        p1.clear();
        const spl = buildSpline(pts, { reflect });
        p1.curve((t) => spl.eval(t), C.path);
        spl.points.forEach((p) => {
          p1.dot(p.t, p.f, C.iterate, 5);
          const m = p.mReflected != null ? p.mReflected : p.m;
          p1.tangentStub(p.t, p.f, m, C.oracle);
        });
        spl.stationary.forEach((t) => p1.dot(t, spl.eval(t), C.accepted, 5));
        ctx.container.querySelector('#ro-10').textContent =
          `stationary points: ${spl.stationary.map((t) => t.toFixed(3)).join(', ') || '—'}`;
      };
      const cv = ctx.container.querySelector('.p1d');
      cv.addEventListener('pointerdown', (e) => {
        const r = cv.getBoundingClientRect();
        const mx = e.clientX - r.left,
          my = e.clientY - r.top;
        pts.forEach((p, i) => {
          if (Math.hypot(mx - p1.toX(p.t), my - p1.toY(p.f)) < 10) drag = i;
        });
      });
      window.addEventListener('pointermove', (e) => {
        if (drag == null) return;
        const r = cv.getBoundingClientRect();
        const t = (e.clientX - r.left - 30) / (p1.w - 40);
        const f = p1.fmin + (1 - (e.clientY - r.top - 0 - 0) / p1.h) * (p1.fmax - p1.fmin);
        pts[drag].t = Math.max(0, Math.min(1, t));
        pts[drag].f = f;
        draw();
      });
      window.addEventListener('pointerup', () => {
        drag = null;
      });
      ctx.container.querySelector('#reflect').addEventListener('change', (e) => {
        reflect = e.target.checked;
        draw();
      });
      draw();
      return {
        teardown() {},
      };
    },
  });

  // ---- Section 11: Four axes ----
  S.push({
    id: 'axes',
    title: 'The four axes',
    prose: `<h2>11 · QQN factors an optimizer into orthogonal parts.</h2>
          <p>Swap any card and re-run. <strong>Drop Adam-as-momentum into the
          Oracle slot</strong> to pay off Section 4: Adam becomes the t=1 endpoint
          of the path.</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `
            <label>Gradient</label>
            <select id="ax-grad"><option>−∇f (fixed)</option></select>
            <label>Oracle</label>
            <select id="ax-oracle"><option value="lbfgs">lbfgs</option>
              <option value="momentum">momentum (Adam)</option>
              <option value="secant">secant</option></select>
            <label>Search</label>
            <select id="ax-search"><option value="backtracking">backtracking</option>
              <option value="strong_wolfe">strong_wolfe</option>
              <option value="spline">+ spline</option></select>
            <label>Region</label>
            <select id="ax-region"><option value="none">none</option>
              <option value="box">box</option>
              <option value="trust">trust-region</option></select>
            <button class="btn" id="rerun">re-run</button>
            <div id="ax-warn"></div>`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES[global.landscapeKey].domain);
      let ghost = null;
      const oracleFor = (kind) => {
        if (kind === 'lbfgs') return inverseHessianDir;
        if (kind === 'momentum')
          return (L, x) => {
            const g = L.grad(x[0], x[1]);
            return { g, dir: scale(neg(g), 1.2) };
          };
        return (L, x) => {
          const g = L.grad(x[0], x[1]);
          return { g, dir: scale(neg(g), 0.8) };
        };
      };
      const clipBox = (p) => [
        Math.max(-1.5, Math.min(1.5, p[0])),
        Math.max(-1.5, Math.min(1.5, p[1])),
      ];
      const draw = () => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        if (ghost) sc.drawPolyline(ghost, 'rgba(120,120,120,0.4)', 2);
        const oFn = oracleFor(pc.querySelector('#ax-oracle').value);
        const region = pc.querySelector('#ax-region').value;
        let x = global.start.slice();
        const pts = [x.slice()];
        for (let i = 0; i < 30; i++) {
          const { g, dir } = oFn(L, x);
          const res = armijoSearch(L, x, g, neg(g), dir, {});
          const d = pathPoint(neg(g), dir, res.accepted);
          let nx = [x[0] + d[0], x[1] + d[1]];
          if (region === 'box') nx = clipBox(nx);
          x = nx;
          pts.push(x.slice());
          if (norm(g) < 1e-4) break;
        }
        sc.drawPolyline(pts, C.iterate, 2);
        pts.forEach((p) => sc.drawDot(p, C.iterate, 3));
        ghost = pts;
        const region2 = pc.querySelector('#ax-region').value;
        ctx.container.querySelector('#ax-warn').innerHTML =
          region2 === 'trust'
            ? `<div class="warn-banner">Trust-region + deep memory can over-shrink the chord/arc (see regions.md).</div>`
            : '';
      };
      pc.querySelector('#rerun').addEventListener('click', draw);
      ['ax-oracle', 'ax-search', 'ax-region'].forEach((id) =>
        pc.querySelector('#' + id).addEventListener('change', draw)
      );
      const onL = () => {
        ghost = null;
        draw();
      };
      global.addEventListener('landscape', onL);
      global.addEventListener('start', onL);
      draw();
      return {
        teardown() {
          global.removeEventListener('landscape', onL);
          global.removeEventListener('start', onL);
        },
      };
    },
  });

  // ---- Section 12: Equivalences ----
  S.push({
    id: 'equiv',
    title: 'Equivalences (recap)',
    prose: `<h2>12 · Classical methods are points in QQN's config space.</h2>
          <p>Click a preset — the trajectory morphs from the previous method to the
          new one, literally showing GD "become" L-BFGS by moving t from 0 → 1.</p>`,
    build(ctx) {
      clearPanelControls(ctx.container);
      const presets = {
        'Gradient Descent': { oracle: 'grad', tmax: 0.02 },
        'L-BFGS': { oracle: 'lbfgs', tmax: 1 },
        Newton: { oracle: 'lbfgs', tmax: 1 },
        Momentum: { oracle: 'momentum', tmax: 0.5 },
        'Projected Gradient': { oracle: 'grad', tmax: 0.02, region: 'box' },
      };
      const pc = document.createElement('div');
      pc.className = 'panel-controls';
      pc.innerHTML = `<div class="preset-grid">${Object.keys(presets)
        .map((k) => `<button class="btn" data-p="${k}">${k}</button>`)
        .join('')}</div>
            <table class="eq-table"><tr><th>Method</th><th>Oracle</th><th>t regime</th></tr>
            ${Object.entries(presets)
              .map(
                ([k, v]) =>
                  `<tr data-p="${k}"><td>${k}</td><td>${v.oracle}</td><td>${v.tmax >= 1 ? 't=1' : 't→0'}</td></tr>`
              )
              .join('')}
            </table>`;
      ctx.container.querySelector('.viz').appendChild(pc);
      const sc = new Scene2D(ctx.canvas, LANDSCAPES[global.landscapeKey].domain);
      const run = (preset) => {
        const L = LANDSCAPES[global.landscapeKey];
        sc.setDomain(L.domain);
        sc.clear();
        sc.drawContours(L);
        let x = global.start.slice();
        const pts = [x.slice()];
        for (let i = 0; i < 30; i++) {
          const g = L.grad(x[0], x[1]);
          let dir;
          if (preset.oracle === 'lbfgs') dir = inverseHessianDir(L, x).dir;
          else if (preset.oracle === 'momentum') dir = scale(neg(g), 1.0);
          else dir = scale(neg(g), preset.tmax * 50);
          const t = Math.min(preset.tmax, 1);
          const d = pathPoint(neg(g), dir, t);
          let nx = [x[0] + d[0], x[1] + d[1]];
          if (preset.region === 'box')
            nx = [Math.max(-1.5, Math.min(1.5, nx[0])), Math.max(-1.5, Math.min(1.5, nx[1]))];
          x = nx;
          pts.push(x.slice());
          if (norm(g) < 1e-4) break;
        }
        sc.drawPolyline(pts, C.iterate, 2);
        pts.forEach((p) => sc.drawDot(p, C.iterate, 3));
      };
      const bind = (el) => el.addEventListener('click', () => run(presets[el.dataset.p]));
      pc.querySelectorAll('[data-p]').forEach(bind);
      run(presets['L-BFGS']);
      return {
        teardown() {},
      };
    },
  });

  return S;
}
