// Views: Home (camera select + preview) and Profiling wizard.
import { el, clear, statTile, progressBar, setProgress } from './components.js';
import { DeviceManager } from '../camera/deviceManager.js';
import { FrameCapture } from '../camera/capture.js';
import { Accumulator } from '../analysis/accumulator.js';
import { detect, DEFAULT_THRESHOLDS } from '../analysis/defectDetector.js';
import { buildProfile } from '../profile/profileModel.js';
import { drawDefectOverlay } from '../util/imageData.js';

// Shared app-level state passed in from main.js.
export class AppState {
  constructor(setStatus) {
    this.setStatus = setStatus;
    this.deviceManager = new DeviceManager();
    this.lastProfile = null;
  }
}

// ---- Home view ----------------------------------------------------------

export function renderHome(root, state) {
  clear(root);

  if (!DeviceManager.supported()) {
    root.appendChild(
      el('div', { class: 'card' }, [
        el('h2', { text: 'Camera not available' }),
        el('p', {
          class: 'error',
          text: 'getUserMedia is not supported in this browser/context. Use HTTPS or localhost.',
        }),
      ])
    );
    return;
  }

  const video = el('video', { autoplay: '', playsinline: '', muted: '' });
  video.muted = true;
  const previewWrap = el('div', { class: 'preview-wrap' }, [video]);

  const select = el('select');
  const refreshBtn = el('button', { class: 'btn secondary', text: 'Refresh' });
  const startBtn = el('button', { class: 'btn', text: 'Start camera' });
  const infoEl = el('div', { class: 'hint' });

  async function populateDevices() {
    try {
      const devices = await state.deviceManager.enumerate();
      clear(select);
      if (!devices.length) {
        select.appendChild(el('option', { value: '', text: 'Default camera' }));
      }
      devices.forEach((d, i) => {
        select.appendChild(
          el('option', {
            value: d.deviceId,
            text: d.label || `Camera ${i + 1}`,
          })
        );
      });
    } catch (e) {
      state.setStatus('Enumerate failed: ' + e.message, true);
    }
  }

  async function startCamera() {
    try {
      startBtn.disabled = true;
      state.setStatus('Starting camera…');
      const stream = await state.deviceManager.start(select.value || undefined);
      video.srcObject = stream;
      await video.play().catch(() => {});
      // Labels become available after permission; refresh the list.
      await populateDevices();
      const s = state.deviceManager.getActiveSettings();
      infoEl.textContent = s ? `Active: ${s.label || 'camera'} @ ${s.width}×${s.height}` : '';
      state.setStatus('Camera running.');
    } catch (e) {
      state.setStatus('Camera error: ' + e.message, true);
    } finally {
      startBtn.disabled = false;
    }
  }

  // If a stream is already running (returning to home), reattach it.
  if (state.deviceManager.stream) {
    video.srcObject = state.deviceManager.stream;
    video.play().catch(() => {});
    const s = state.deviceManager.getActiveSettings();
    if (s) infoEl.textContent = `Active: ${s.label || 'camera'} @ ${s.width}×${s.height}`;
  }

  startBtn.addEventListener('click', startCamera);
  refreshBtn.addEventListener('click', populateDevices);

  const card = el('div', { class: 'card' }, [
    el('h2', { text: 'Camera' }),
    el('div', { class: 'row' }, [
      el('div', { class: 'field' }, [el('label', { text: 'Device' }), select]),
      refreshBtn,
      startBtn,
    ]),
    infoEl,
    previewWrap,
  ]);

  const next = el('div', { class: 'card' }, [
    el('h2', { text: 'Next steps' }),
    el('p', {
      class: 'hint',
      text: 'Start the camera, then go to Profiling to capture dark, flat, and mixed frames and detect sensor defects.',
    }),
  ]);

  root.appendChild(card);
  root.appendChild(next);

  populateDevices();
}

// ---- Profiling view -----------------------------------------------------

const STAGES = [
  { mode: 'dark', label: 'Cover the lens (dark frames)', frames: 20 },
  { mode: 'flat', label: 'Point at a bright, even surface (flat frames)', frames: 20 },
  { mode: 'mixed', label: 'Point at a varied scene & move slightly (mixed frames)', frames: 20 },
];

export function renderProfiling(root, state) {
  clear(root);

  if (!state.deviceManager.stream) {
    root.appendChild(
      el('div', { class: 'card' }, [
        el('h2', { text: 'No camera running' }),
        el('p', { class: 'hint', text: 'Go to Home and start a camera first.' }),
      ])
    );
    return;
  }

  const video = el('video', { autoplay: '', playsinline: '', muted: '' });
  video.muted = true;
  video.srcObject = state.deviceManager.stream;
  video.play().catch(() => {});

  const overlay = el('canvas', { class: 'overlay-canvas' });
  const previewWrap = el('div', { class: 'preview-wrap' }, [video, overlay]);

  const stepsList = el('ul', { class: 'steps' });
  STAGES.forEach((s, i) => stepsList.appendChild(el('li', { text: `${i + 1}. ${s.label}` })));

  const bar = progressBar(0);
  const stagePrompt = el('p', { class: 'hint', text: 'Ready to capture.' });

  const startBtn = el('button', { class: 'btn', text: 'Start capture sequence' });
  const detectBtn = el('button', { class: 'btn secondary', text: 'Re-detect' });
  detectBtn.disabled = true;
  const saveBtn = el('button', { class: 'btn secondary', text: 'Save profile (memory)' });
  saveBtn.disabled = true;

  const statsRow = el('div', { class: 'stat-grid' });

  // Threshold controls.
  const thr = Object.assign({}, DEFAULT_THRESHOLDS);
  function thrInput(key, label) {
    const input = el('input', { type: 'number', value: String(thr[key]) });
    input.style.width = '90px';
    input.addEventListener('change', () => {
      const v = parseFloat(input.value);
      if (!Number.isNaN(v)) thr[key] = v;
    });
    return el('div', { class: 'field' }, [el('label', { text: label }), input]);
  }
  const thresholdRow = el('div', { class: 'row' }, [
    thrInput('deadMax', 'deadMax'),
    thrInput('varianceMin', 'varianceMin'),
    thrInput('hotDelta', 'hotDelta'),
    thrInput('neighborDev', 'neighborDev'),
    thrInput('confidenceMin', 'confidenceMin'),
  ]);

  let accumulator = null;
  let lastDefectMap = null;

  function updateOverlay() {
    if (!lastDefectMap) return;
    const dispW = video.clientWidth || video.videoWidth;
    const dispH = video.clientHeight || video.videoHeight;
    drawDefectOverlay(
      overlay,
      lastDefectMap.pixels,
      lastDefectMap.width,
      lastDefectMap.height,
      dispW,
      dispH
    );
  }

  function renderStats(map) {
    clear(statsRow);
    if (!map) return;
    const by = map.countByType();
    statsRow.appendChild(statTile(map.count, 'Total defects'));
    statsRow.appendChild(statTile(by.dead, 'Dead'));
    statsRow.appendChild(statTile(by.stuck, 'Stuck'));
    statsRow.appendChild(statTile(by.hot, 'Hot'));
    statsRow.appendChild(statTile(by.noisy, 'Noisy'));
  }

  function runDetection() {
    if (!accumulator) return;
    state.setStatus('Detecting defects…');
    lastDefectMap = detect(accumulator, thr);
    renderStats(lastDefectMap);
    updateOverlay();
    detectBtn.disabled = false;
    saveBtn.disabled = false;
    state.setStatus(`Detection complete: ${lastDefectMap.count} defect(s).`);
  }

  async function captureSequence() {
    startBtn.disabled = true;
    detectBtn.disabled = true;
    saveBtn.disabled = true;
    const cap = new FrameCapture(video);
    await cap.ready();
    const first = cap.grab();
    if (!first) {
      state.setStatus('Could not read a frame.', true);
      startBtn.disabled = false;
      return;
    }
    accumulator = new Accumulator(first.width, first.height);

    const totalFrames = STAGES.reduce((a, s) => a + s.frames, 0);
    let done = 0;

    for (let si = 0; si < STAGES.length; si++) {
      const stage = STAGES[si];
      // Update step styling.
      Array.from(stepsList.children).forEach((li, i) => {
        li.classList.toggle('active', i === si);
        li.classList.toggle('done', i < si);
      });
      stagePrompt.textContent = stage.label + ' — get into position…';
      // Brief pause so the user can reposition.
      await new Promise((r) => setTimeout(r, 1200));
      stagePrompt.textContent = stage.label + ' — capturing…';

      for (let f = 0; f < stage.frames; f++) {
        await cap.nextFrame();
        const img = cap.grab();
        if (img) {
          try {
            accumulator.addFrame(img, stage.mode);
          } catch (e) {
            state.setStatus('Capture aborted: ' + e.message, true);
            startBtn.disabled = false;
            return;
          }
        }
        done++;
        setProgress(bar, (done / totalFrames) * 100);
      }
    }

    Array.from(stepsList.children).forEach((li) => {
      li.classList.remove('active');
      li.classList.add('done');
    });
    stagePrompt.textContent = `Captured ${accumulator.count} frames.`;
    state.setStatus('Capture complete. Detecting…');
    runDetection();
    startBtn.disabled = false;
  }

  startBtn.addEventListener('click', captureSequence);
  detectBtn.addEventListener('click', runDetection);
  saveBtn.addEventListener('click', () => {
    if (!lastDefectMap) return;
    const settings = state.deviceManager.getActiveSettings();
    state.lastProfile = buildProfile({
      name: settings && settings.label ? settings.label + ' profile' : 'Camera profile',
      deviceSettings: settings,
      defectMap: lastDefectMap,
      frameCount: accumulator ? accumulator.count : 0,
      thresholds: thr,
    });
    state.setStatus(
      `Profile created in memory (${lastDefectMap.count} defects). Persistence comes in Phase 2.`
    );
  });

  window.addEventListener('resize', updateOverlay);
  video.addEventListener('loadedmetadata', updateOverlay);

  const captureCard = el('div', { class: 'card' }, [
    el('h2', { text: 'Profiling wizard' }),
    stepsList,
    stagePrompt,
    bar,
    el('div', { class: 'row' }, [startBtn, detectBtn, saveBtn]),
  ]);

  const previewCard = el('div', { class: 'card' }, [
    el('h2', { text: 'Preview & defect overlay' }),
    previewWrap,
    el('p', { class: 'hint', text: 'Red=dead, yellow=stuck, magenta=hot, blue=noisy.' }),
  ]);

  const statsCard = el('div', { class: 'card' }, [
    el('h2', { text: 'Detected defects' }),
    statsRow,
  ]);

  const thrCard = el('div', { class: 'card' }, [
    el('h2', { text: 'Detection thresholds' }),
    thresholdRow,
    el('p', { class: 'hint', text: 'Adjust then press Re-detect (no recapture needed).' }),
  ]);

  root.appendChild(captureCard);
  root.appendChild(previewCard);
  root.appendChild(statsCard);
  root.appendChild(thrCard);
}
