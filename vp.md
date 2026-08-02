# Vocal Parkour

A browser-based vocal-gesture rhythm game. Make mouth sounds (hiss, airy,
pop, hum — or record your own) into your microphone; a real-time audio
engine classifies each sound and a rhythm rail scores your timing,
precision, and consistency.
No sound ever leaves your device: the microphone signal is analyzed
entirely client-side, and calibration/training data is persisted locally in
IndexedDB.

## Features

- **Real-time gesture detection.** A small DSP pipeline (FFT-based
  spectral features + a lightweight autocorrelation pitch tracker) turns
  raw mic audio into a per-frame feature vector, running either as a
  compiled WebAssembly engine (`agility-rt.wasm`) inside an
  `AudioWorkletProcessor`, or a JS fallback when WASM isn't available.
- **Trainable classifier.** An in-browser RBF-SVM (one-vs-rest) learns to
  tell your sounds apart from silence and from each other. Training data is
  captured through guided calibration panels (press-and-hold for sustained
  sounds like hums/hisses, a rhythmic "Pop Panel" for plosives) and stored
  per-label in IndexedDB.
- **Quick Calibrate.** A short guided flow (mic gain → pop calibration →
  sustained-sound calibration) gets a new player playable in under a
  minute.
- **Custom sound library.** Beyond the four built-in presets (hiss, airy,
  pop, hum), you can define your own labeled sounds (icon, label,
  percussive vs. sustained, pitch-invariant vs. pitch-discriminating),
  and export/import your whole library as JSON.
- **Configurable rhythm game.** Choose which sounds are active, run
  length, speed, hit/perfect windows, scoring, and how often/how long
  targets require sustained holds vs. quick taps.
- **Diagnostics / Settings panel.** Live feature visualizer (radar +
  scrolling history + pitch readout), per-sound practice/calibration,
  raw training-data inspection/export (TSV), and fine-grained DSP tuning
  (gate thresholds, gain, frequency bands, pitch tracker settings) with
  an auto-calibration assistant.
- **PWA-ready.** Installable, with a service worker precaching the app
  shell and caching the WASM engine and catalog assets.

## Getting started

```bash
npm install
npm run dev      # start the dev server
npm run build    # production build
```

Open the app, grant microphone access, and either run **Quick Calibrate**
for guided setup or go straight to **Settings** to train sounds and tune
DSP parameters, then **Play**.

## Architecture overview

```
src/
audio/        mic acquisition, AudioContext graph wiring, worklet host
engine/       DSP feature extraction, clustering/classification, ABI
types shared with the AudioWorkletProcessor, WASM loader
calibration/  guided calibration helpers (impulse localization, etc.)
game/         scoring, rhythm rail, courses, the main game loop,
diagnostics session driving the Settings panel
render/       canvas2d renderer, sound feedback (Sonifier), haptics
app/          router, shell (top-level screens), reactive Signal store
ui/           custom elements (ac-*) for HUD, settings, panels
storage/      IndexedDB-backed persistence (model, training set,
DSP config, phoneme library, baseline profile)
theme/        naming/iconography for different presentation themes
a11y/         accessibility preferences (haptics, reduced motion, captions)
pwa/          service worker registration + Workbox SW source
```

### Signal pipeline

1. The microphone feeds a `MediaStreamAudioSourceNode` into an
   `AudioWorkletNode` running the `agility-engine` processor.
2. Each audio frame is windowed and FFT'd to produce a `FeatureVector`
   (spectral centroid, noise/voicing/tilt ratios, energy, transient
   onset, flatness, rolloff, plus an NSDF-based pitch estimate).
3. Feature vectors are either matched against hand-authored `ClusterDef`
   centers (`seed-clusters.ts`) or, once trained, classified by the
   `NeuralClassifier` (an RBF-SVM ensemble) — see `engine/gate.ts` for the
   energy/transient gating and rejection-confidence logic that keeps
   silence and noise from being misclassified.
4. Classified events stream to the `GameLoop` (during play) or
   `Diagnostics` (during calibration/settings), which drive the
   `RhythmRail`/`Scorer` or the live Settings UI, respectively.

### Feature-space consistency

Because feature vectors are only meaningful relative to the DSP
parameters that produced them, the app fingerprints the active DSP
config (`dspFingerprint()` in `engine/dsp.ts`) and uses it to detect
drift between a persisted training corpus/model and the live
configuration — automatically discarding stale data rather than silently
degrading detection quality.

## Key concepts

- **Cluster / label** — an identifiable sound (e.g. `hiss`, `pop`, or a
  custom id). Presets ship with baked-in naming/icon themes
  (`theme/naming.ts`, `theme/icons.ts`); custom sounds carry their own
  label/icon plus `percussive` and `pitchInvariant` flags.
- **Percussive vs. sustained** — percussive sounds (like pops) are
  scored on onset timing; sustained sounds (hums, hisses) require holding
  the sound for a duration and are scored via `RhythmRail.updateSustain`.
- **Calibration** — capturing labeled feature vectors for a sound, either
  via a timed/held capture window or a guided rhythmic panel, which are
  used to (re)train the classifier.
- **DSP config** — the tunable parameters controlling feature
  extraction (gates, gains, frequency bands, pitch tracker settings).
  Persisted via `storage/dsp-config.ts` and adjustable live from
  Settings.

## Development notes

- The WASM engine (`agility-rt.wasm` + its Emscripten glue) is optional;
  if it fails to compile/load, the app transparently falls back to a
  pure-JS DSP implementation with equivalent behavior.
- Extensive `console.info`/`console.debug`/`console.warn` logging is used
  throughout the engine, classifier, and game loop to make audio/ML
  issues (saturation, feature-space drift, empty training data, etc.)
  diagnosable directly from the browser console.
- State is managed with a minimal reactive `Signal<T>` (see
  `app/state/signal.ts`) rather than a framework; UI is built from plain
  Custom Elements (`ui/ac-*.ts`).

## SEO & sharing

- `index.html` ships full metadata: canonical URL, description/keywords,
  Open Graph + Twitter Card tags, Apple web-app meta, and JSON-LD
  (`VideoGame`) structured data.
- `public/robots.txt` allows crawling of the app shell while excluding
  build internals (`/src/`, `/lib/`) and points to the sitemap.
- `public/sitemap.xml` lists the single indexable SPA entry point (the app
  is hash-routed, so all in-app screens share one crawlable URL) plus the
  social preview image.
- Image generation specs live alongside the assets they produce:
  - `public/images/social-preview.op.md` → `social-preview.png`
    (1200×630 Open Graph card key art).
  - `public/images/icon.op.md` → `icon.png` (512×512 maskable app
    icon / favicon source).
    > Note: update the `https://vocalparkour.app/` origin in `index.html`,
    > `robots.txt`, and `sitemap.xml` to match the production deployment domain.
