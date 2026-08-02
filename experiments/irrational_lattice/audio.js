// Audio sonification of the scalar lattice field.
//
// We "scan" the field one column at a time (left to right), treating each
// column as a short spectral frame. The field values in a column are mapped
// to amplitudes across a bank of oscillators whose frequencies are spread
// over a musical range. As the scan position advances, the timbre evolves,
// turning the spatial structure of the field into an evolving drone.
//
// A second, simpler mode ("sample") reads the field row-by-row directly into
// an AudioBuffer and plays it as a looping waveform.

export class FieldAudio {
  constructor() {
    this.ctx = null;
    this.master = null;
    this.playing = false;
    this.mode = 'scan'; // 'scan' | 'sample'

    // Scan state.
    this.osc = []; // oscillator nodes
    this.gains = []; // per-oscillator gain nodes
    this.numOsc = 48;
    this.baseFreq = 110; // A2
    this.scanPos = 0; // current column (float)
    this.scanRate = 30; // columns per second
    this.lastFrameTime = 0;
    this._raf = null;

    // Sample-mode state.
    this.bufferSource = null;
  }

  ensureContext() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      this.ctx = new AC();
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.0;
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') this.ctx.resume();
    return this.ctx;
  }

  setVolume(v) {
    this.volume = v;
    if (this.master) {
      const now = this.ctx.currentTime;
      this.master.gain.setTargetAtTime(this.playing ? v : 0, now, 0.05);
    }
  }

  setScanRate(r) {
    this.scanRate = r;
  }

  setMode(mode) {
    const wasPlaying = this.playing;
    if (wasPlaying) this.stop();
    this.mode = mode;
    if (wasPlaying) this.start(this._lastResult, this._lastOpts);
  }

  // Build the oscillator bank for scan mode.
  _buildBank() {
    this._teardownBank();
    const ctx = this.ctx;
    for (let i = 0; i < this.numOsc; i++) {
      const osc = ctx.createOscillator();
      // Spread frequencies over ~5 octaves using a mildly inharmonic ratio
      // so the drone has body without being a pure harmonic stack.
      const ratio = Math.pow(2, (i / this.numOsc) * 5);
      osc.frequency.value = this.baseFreq * ratio;
      osc.type = 'sine';
      const g = ctx.createGain();
      g.gain.value = 0;
      osc.connect(g);
      g.connect(this.master);
      osc.start();
      this.osc.push(osc);
      this.gains.push(g);
    }
  }

  _teardownBank() {
    for (const o of this.osc) {
      try {
        o.stop();
      } catch (e) {
        /* already stopped */
      }
      o.disconnect();
    }
    for (const g of this.gains) g.disconnect();
    this.osc = [];
    this.gains = [];
  }

  // Map one column of the field to oscillator gains.
  _applyColumn(result, opts, col) {
    const { data, min, max, width, height } = result;
    const range = max - min || 1;
    const n = this.numOsc;
    const now = this.ctx.currentTime;
    // Sample `numOsc` rows evenly down the chosen column.
    for (let i = 0; i < n; i++) {
      const row = Math.min(height - 1, Math.floor((i / n) * height));
      const v = (data[row * width + col] - min) / range;
      // Perceptual shaping: emphasize peaks, normalize total energy by the
      // oscillator count so the master level stays roughly constant.
      const amp = (v * v) / Math.sqrt(n);
      this.gains[i].gain.setTargetAtTime(amp, now, 0.02);
    }
  }

  // Render the field into an AudioBuffer for sample mode (row-major scan
  // of the field used directly as a waveform).
  _buildSampleBuffer(result) {
    const { data, min, max } = result;
    const range = max - min || 1;
    const ctx = this.ctx;
    const len = data.length;
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const ch = buf.getChannelData(0);
    for (let i = 0; i < len; i++) {
      // Map field value to [-1, 1].
      ch[i] = ((data[i] - min) / range) * 2 - 1;
    }
    return buf;
  }

  start(result, opts) {
    if (!result) return;
    this._lastResult = result;
    this._lastOpts = opts;
    this.ensureContext();
    this.playing = true;

    if (this.mode === 'sample') {
      if (this.bufferSource) {
        try {
          this.bufferSource.stop();
        } catch (e) {
          /* noop */
        }
      }
      const buf = this._buildSampleBuffer(result);
      const src = ctx_safe(this).createBufferSource();
      src.buffer = buf;
      src.loop = true;
      // Play back at a rate that gives an audible pitch from the scan length.
      src.playbackRate.value = 1;
      src.connect(this.master);
      src.start();
      this.bufferSource = src;
    } else {
      this._buildBank();
      this.scanPos = 0;
      this.lastFrameTime = performance.now();
      const tick = () => {
        if (!this.playing) return;
        const now = performance.now();
        const dt = (now - this.lastFrameTime) / 1000;
        this.lastFrameTime = now;
        const w = this._lastResult.width;
        this.scanPos = (this.scanPos + this.scanRate * dt) % w;
        this._applyColumn(this._lastResult, this._lastOpts, Math.floor(this.scanPos));
        this._raf = requestAnimationFrame(tick);
      };
      this._raf = requestAnimationFrame(tick);
    }

    this.setVolume(this.volume != null ? this.volume : 0.4);
  }

  // Update the field being sonified without restarting playback.
  updateField(result, opts) {
    this._lastResult = result;
    this._lastOpts = opts;
    if (this.playing && this.mode === 'sample') {
      // Rebuild the buffer for the new field.
      const wasPlaying = this.playing;
      this.start(result, opts);
      this.playing = wasPlaying;
    }
  }

  stop() {
    this.playing = false;
    if (this._raf) cancelAnimationFrame(this._raf);
    this._raf = null;
    if (this.master) {
      const now = this.ctx.currentTime;
      this.master.gain.setTargetAtTime(0, now, 0.05);
    }
    if (this.bufferSource) {
      try {
        this.bufferSource.stop(this.ctx.currentTime + 0.1);
      } catch (e) {
        /* noop */
      }
      this.bufferSource = null;
    }
    // Tear down the oscillator bank slightly after the fade.
    const bank = this.osc;
    const gains = this.gains;
    this.osc = [];
    this.gains = [];
    if (this.ctx) {
      setTimeout(() => {
        for (const o of bank) {
          try {
            o.stop();
          } catch (e) {
            /* noop */
          }
          o.disconnect();
        }
        for (const g of gains) g.disconnect();
      }, 120);
    }
  }

  toggle(result, opts) {
    if (this.playing) {
      this.stop();
      return false;
    }
    this.start(result, opts);
    return true;
  }
}

// Small helper so the sample-mode branch reads clearly.
function ctx_safe(self) {
  return self.ctx;
}
