# Laser-Damage Camera Correction PWA

## Concept

A browser-based **Progressive Web App** (HTML + modular ES6, no build step
required) that lets a user **profile a camera** to detect sensor defects,
then **capture photos that are automatically corrected** based on the
stored profile.

The primary focus is **laser-damaged cameras**: sensors that have been
partially destroyed by exposure to laser light, producing dead, stuck, or
hot pixels. The app detects these defective pixels across a sequence of
photos and then **infills (interpolates)** them to reconstruct a clean
image.

## Goals

- Run entirely client-side — no server, no upload, privacy-preserving.
- Installable as a PWA (offline-capable via service worker).
- Work on mobile and desktop using the `getUserMedia` / `ImageCapture` APIs.
- Produce a **reusable camera profile** that can be saved, exported, and
  re-imported.
- Apply correction in near-real-time to a live preview and to captured
  stills.

## Non-Goals (initial version)

- RAW sensor decoding (start with what the browser camera API exposes).
- Lens distortion / vignetting correction (possible future extension).
- Color calibration / white balance profiling (future extension).
- Cloud sync of profiles.

---

## Core Workflows

### 1. Profiling a Camera

The user characterizes a specific camera to build a **defect map**.

Steps:

1. Select / grant access to a camera device.
2. Capture a **sequence** of frames (the more frames, the more reliable the
   statistics). Ideally captured under varied conditions:
   - **Dark frames** (lens covered) to find hot/stuck-bright pixels.
   - **Flat/bright frames** (uniform illumination) to find dead/stuck-dark
     pixels.
   - **Mixed scene frames** to confirm pixels that never respond to scene
     changes.
3. Analyze the sequence to classify each pixel:
   - **Dead** — consistently near-black regardless of illumination.
   - **Stuck** — consistently a fixed value (white, colored, or gray).
   - **Hot** — abnormally bright in dark frames; temperature/exposure
     dependent.
   - **Noisy** — variance far outside neighborhood norms.
4. Build a **defect map** (per-pixel mask + classification + confidence).
5. Save the profile to local storage / IndexedDB and allow export.

### 2. Capturing & Correcting

1. User selects an existing profile (matched to the active camera).
2. Live preview shows corrected output (optional toggle: raw vs corrected).
3. On capture, the full-resolution frame is processed:
   - Apply defect mask.
   - Infill defective pixels using interpolation.
4. Corrected image is shown, can be saved/downloaded/shared.

---

## Defect Detection Algorithms

### Statistical accumulation across a sequence

For each pixel, accumulate over N frames:

- mean, min, max, variance per channel.

Detection heuristics:

- **Dead pixel:** max value across all frames stays below a low threshold
  even when neighbors brighten.
- **Stuck pixel:** variance ≈ 0 across frames that should differ (low
  temporal variance while the surrounding region changes).
- **Hot pixel:** value consistently exceeds local neighborhood mean by a
  large margin, especially in dark frames.
- **Neighborhood deviation:** pixel value deviates strongly from the median
  of its surrounding pixels across many frames.

### Confidence scoring

Each flagged pixel gets a confidence score based on how many frames and how
strongly it triggered detection. A threshold determines inclusion in the
final mask, reducing false positives.

---

## Infilling / Correction Algorithms

Options, in increasing complexity:

1. **Nearest-neighbor copy** — fast, low quality.
2. **Bilinear / weighted average** of valid neighbors — good default.
3. **Median of valid neighbors** — robust to clustered defects.
4. **Directional / edge-aware interpolation** — preserves edges, avoids
   smearing across boundaries.
5. **Bayer-aware infill** — if/when raw mosaic data is available, interpolate
   only from same-color-channel neighbors.

Clustered defects (adjacent dead pixels, e.g. a laser burn region) need
multi-pass infilling or larger neighborhood search since immediate neighbors
may also be invalid.

---

## Architecture (modular ES6)

```
/experiments/camera-tool/
  index.html
  manifest.webmanifest
  sw.js                      # service worker (offline/PWA)
  /src/
    main.js                  # app entry, wiring/UI orchestration
    /camera/
      deviceManager.js       # enumerate/select cameras, getUserMedia
      capture.js             # frame & still capture (ImageCapture)
    /profile/
      profileStore.js        # IndexedDB persistence, import/export
      profileModel.js        # profile schema & versioning
    /analysis/
      accumulator.js         # per-pixel stat accumulation over frames
      defectDetector.js      # classification heuristics & confidence
      defectMap.js           # mask data structure & serialization
    /correction/
      infill.js              # interpolation strategies
      pipeline.js            # apply mask + infill to a frame
    /ui/
      components.js          # reusable UI widgets
      views.js               # profiling view, capture view, settings
    /util/
      imageData.js           # canvas/ImageData helpers
      math.js                # stats helpers
  /styles/
    app.css
```

### Key APIs / techniques

- `navigator.mediaDevices.getUserMedia` + `enumerateDevices`
- `ImageCapture` for high-res stills where supported
- `<canvas>` + `getImageData` / `putImageData` for pixel access
- **OffscreenCanvas + Web Workers** to keep analysis off the main thread
- **WebGL / WebGPU** (future) for GPU-accelerated per-pixel correction
- **IndexedDB** for profile persistence
- **Service Worker + manifest** for installable offline PWA

---

## Data Model

### Camera Profile (conceptual)

```json
{
  "version": 1,
  "id": "uuid",
  "name": "Phone rear cam (laser test)",
  "createdAt": "ISO-8601",
  "device": {
    "label": "camera label",
    "deviceId": "optional/unstable",
    "resolution": { "width": 1920, "height": 1080 }
  },
  "defects": {
    "encoding": "rle | sparse-coords | bitmask",
    "count": 1234,
    "pixels": [{ "x": 100, "y": 200, "type": "dead", "confidence": 0.98 }]
  },
  "analysis": {
    "frameCount": 60,
    "thresholds": { "deadMax": 12, "varianceMin": 4, "hotDelta": 60 }
  }
}
```

Notes:

- `deviceId` is unstable across sessions/permissions, so profiles should
  also match on resolution + label heuristics and let the user confirm.
- Defect storage should be compact (RLE or sparse coordinates) since a
  laser burn can damage thousands of contiguous pixels.

---

## UI / UX Sketch

- **Home / Camera select:** list cameras, pick one, see live preview.
- **Profiling wizard:**
  - Step prompts ("Cover the lens", "Point at a bright even surface",
    "Capturing dark frames... 12/30").
  - Progress bar; live count of detected defects.
  - Visualization overlay highlighting detected defective pixels.
- **Profile manager:** list saved profiles, rename, delete, export/import.
- **Capture view:**
  - Toggle: raw vs corrected.
  - Capture button; gallery of corrected results.
  - Side-by-side / swipe before-after comparison.
- **Settings:** detection thresholds, infill strategy, sequence length.

---

## Performance Considerations

- Full-frame per-pixel work is heavy; offload to a **Web Worker** using
  transferable `ImageData`/`ArrayBuffer`s.
- For live preview, process at reduced resolution or only update the
  defect overlay periodically.
- Consider **WebGL/WebGPU** shaders for real-time full-res correction in a
  later phase.
- Accumulator can stream frames (online stats) to avoid storing all frames
  in memory.

---

## Roadmap / Phases

**Phase 0 — Skeleton**

- PWA shell, manifest, service worker, camera enumeration & live preview.

**Phase 1 — Profiling MVP**

- Frame sequence capture, per-pixel accumulator, basic dead/stuck/hot
  detection, defect-map visualization overlay.

**Phase 2 — Persistence**

- IndexedDB profile store, save/load, export/import JSON, profile manager UI.

**Phase 3 — Correction**

- Apply mask + neighbor-average infill on captured stills; before/after view.

**Phase 4 — Quality & Performance**

- Move analysis/correction to Web Workers; edge-aware & clustered-defect
  infill; threshold tuning UI.

**Phase 5 — Stretch**

- WebGL/WebGPU real-time corrected preview; RAW/Bayer-aware path; lens
  vignetting/distortion correction; shareable profiles.

---

## Open Questions

- How reliable is `getImageData` precision across browsers (8-bit only)?
  Does this limit dark-frame hot-pixel detection?
- Can we access higher bit-depth frames anywhere in-browser today?
- How to robustly re-match a saved profile to the same physical camera?
- Best compact encoding for large contiguous laser-burn defect regions?
- Minimum frame count / capture conditions for trustworthy detection?
