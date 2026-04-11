# Video Edit Utilities Library

A zero-dependency Node.js utility library for scripted video editing with FFmpeg.

## Overview

`video-edit-utils.js` extracts common patterns from video editing scripts into reusable functions.
It provides everything needed to build automated video editing pipelines: probing source files, generating title cards,
extracting/compressing segments, concatenating clips, and normalizing audio.

**Requirements:**

- Node.js 14+
- FFmpeg and FFprobe installed and available on `PATH`

**No npm dependencies** — uses only Node.js built-in modules (`child_process`, `fs`, `path`).

## Installation

Copy `video-edit-utils.js` into your project's `lib/` directory:

```
review/
├── lib/
│   └── video-edit-utils.js
├── Comic_Generator.js
├── Filesystem.js
├── Install_Windows.js
└── Philosophical_Calculator.js
```

## Quick Start

### Minimal Example

```js
const veu = require('./lib/video-edit-utils');

veu.runEditPipeline({
    inputPath: 'source/MyVideo.mp4',
    outputPath: 'edit/MyVideo.mp4',
    tempDir: 'edit/temp_myvideo',
    introTitle: 'My Demo Video',
    introSubtitle: 'A Quick Walkthrough',
    introDuration: 3,
    outroLines: ['Thanks for watching!', 'Visit example.com'],
    outroDuration: 4,
     transitionDuration: 0.5,
    segments: [
        {id: 'seg01', start: '00:00:05', end: '00:00:30', action: 'keep', label: 'Introduction'},
        {id: 'seg02', start: '00:00:30', end: '00:00:45', action: 'cut', label: 'False start'},
        {id: 'seg03', start: '00:00:45', end: '00:01:30', action: 'keep', label: 'Main content'},
        {
            id: 'seg04', start: '00:01:30', end: '00:02:30', action: 'compress',
            targetDuration: 10, overlay: 'Processing...', label: 'Wait time'
        },
        {id: 'seg05', start: '00:02:30', end: '00:03:00', action: 'keep', label: 'Results'},
    ],
    normalizeAudio: true,
});
```

This single call will:

1. Probe the source video for resolution, fps, and audio properties
2. Generate an intro billboard with title and subtitle
3. Extract and process each segment (keeping, cutting, or compressing)
4. Generate an outro billboard
5. Concatenate everything into the final output
6. Apply audio normalization
7. Clean up temporary files

### Granular Usage

For more control, use individual functions:

```js
const veu = require('./lib/video-edit-utils');
const path = require('path');

const INPUT = 'source/MyVideo.mp4';
const OUTPUT = 'edit/MyVideo.mp4';
const TEMP = 'edit/temp';

veu.ensureDirs('edit', TEMP);

// Probe source
const info = veu.probeVideo(INPUT);
console.log(`${info.width}x${info.height} @ ${info.fps}fps`);

// Generate billboards
veu.generateIntroBillboard(path.join(TEMP, 'intro.mp4'), 'My Title', 'Subtitle', info, 3);

// Extract a segment
veu.extractSegment(INPUT, path.join(TEMP, 'seg1.mp4'), '00:00:10', '00:00:45', {
    fps: info.fps,
    sampleRate: info.sampleRate,
});

// Time-compress a segment
veu.extractCompressedSegment(INPUT, path.join(TEMP, 'seg2.mp4'), '00:01:00', '00:02:00', {
    targetDuration: 10,
    overlay: 'Generating...',
    fps: info.fps,
});

// Concatenate
veu.concatenateVideos(
    [path.join(TEMP, 'intro.mp4'), path.join(TEMP, 'seg1.mp4'), path.join(TEMP, 'seg2.mp4')],
    OUTPUT,
    TEMP
);

// Cleanup
veu.cleanupDir(TEMP);
```

## API Reference

### Shell Execution

#### `run(cmd, opts?)`

Execute a shell command with logging. Inherits stdio by default.

#### `runCapture(cmd)`

Execute a shell command and return stdout as a trimmed string.

### Filesystem

#### `ensureDirs(...dirs)`

Create one or more directories recursively.

#### `cleanupDir(dirPath)`

Remove a directory and all contents. Logs a warning on failure instead of throwing.

### Timecodes

#### `tcToSeconds(tc)` → `number`

Convert timecode string to seconds. Supports:

- `"HH:MM:SS.mmm"` → `3661.5`
- `"MM:SS.mmm"` → `61.5`
- `"SS.mmm"` → `1.5`
- Raw numbers pass through unchanged

#### `secondsToTc(sec)` → `string`

Convert seconds to `"HH:MM:SS.mmm"` format.

### Video Probing

#### `probeVideo(inputPath)` → `VideoInfo`

Returns an object with:

| Property        | Type   | Description                       |
|-----------------|--------|-----------------------------------|
| `width`         | number | Video width in pixels             |
| `height`        | number | Video height in pixels            |
| `fps`           | number | Frame rate (rounded integer)      |
| `fpsExact`      | number | Frame rate (precise float)        |
| `sampleRate`    | number | Audio sample rate (default 44100) |
| `channels`      | number | Audio channel count (default 2)   |
| `channelLayout` | string | `"stereo"` or `"mono"`            |

### FFmpeg Filter Helpers

#### `buildAtempoChain(speed)` → `string`

Build a chained `atempo` filter for arbitrary speed factors. FFmpeg's `atempo` only accepts 0.5–100.0, so values > 2.0
are automatically chained:

```js
buildAtempoChain(4.0)  // → "atempo=2.0,atempo=2.0"
buildAtempoChain(1.5)  // → "atempo=1.5000"
buildAtempoChain(8.0)  // → "atempo=2.0,atempo=2.0,atempo=2.0"
```

#### `escapeDrawtext(text)` → `string`

Escape text for safe use in FFmpeg `drawtext` filters (handles `\`, `'`, `:`).

#### `drawTextCentered(text, opts?)` → `string`

Generate a `drawtext` filter string for horizontally and vertically centered text.

Options:

| Option        | Default   | Description                 |
|---------------|-----------|-----------------------------|
| `fontsize`    | 48        | Font size                   |
| `fontcolor`   | `'white'` | Font color                  |
| `font`        | `'Arial'` | Font family                 |
| `yOffset`     | 0         | Vertical offset from center |
| `borderw`     | 0         | Text border width           |
| `bordercolor` | `'black'` | Text border color           |

### Billboard Generation

#### `generateBillboard(outputPath, opts)`

Generate a title card video with text on a solid background. Full control over all parameters.

Options:

| Option          | Default    | Description                                        |
|-----------------|------------|----------------------------------------------------|
| `duration`      | *required* | Duration in seconds                                |
| `width`         | *required* | Video width                                        |
| `height`        | *required* | Video height                                       |
| `fps`           | 30         | Frame rate                                         |
| `sampleRate`    | 44100      | Audio sample rate                                  |
| `channelLayout` | `'stereo'` | Audio channel layout                               |
| `fadeIn`        | 0.5        | Fade-in duration                                   |
| `fadeOut`       | 0.5        | Fade-out duration                                  |
| `bgColor`       | `'black'`  | Background color                                   |
| `lines`         | `[]`       | Array of `{text, fontsize?, fontcolor?, yOffset?}` |

#### `generateIntroBillboard(outputPath, title, subtitle?, videoInfo, duration?)`

Convenience wrapper for a standard intro card. Font sizes are automatically calculated from video height.

#### `generateOutroBillboard(outputPath, textLines, videoInfo, duration?)`

Convenience wrapper for a standard outro card. Accepts an array of strings; first line is largest, subsequent lines
progressively smaller and dimmer.

### Segment Processing

#### `extractSegment(inputPath, outputPath, start, end, opts?)`

Extract a segment at original speed.

#### `extractCompressedSegment(inputPath, outputPath, start, end, opts)`

Extract and time-compress a segment. Provide either `speed` (multiplier) or `targetDuration` (seconds).

Options:

| Option           | Default | Description                            |
|------------------|---------|----------------------------------------|
| `speed`          | —       | Speed multiplier (e.g., 4 = 4× faster) |
| `targetDuration` | —       | Desired output duration in seconds     |
| `overlay`        | —       | Text overlay during compressed segment |
| `muteAudio`      | `true`  | Replace audio with silence             |
| `fps`            | —       | Target frame rate                      |

#### `processSegments(segments, inputPath, tempDir, opts?)` → `string[]`

Process an array of segment definitions. Returns file paths for non-cut segments.

Segment definition:

```js
{
    id: 'seg01',           // Unique identifier (used for filename)
    start:'00:00:10',     // Start timecode
    end:'00:00:30',       // End timecode
    action:'keep',        // 'keep' | 'cut' | 'compress'
    label:'Introduction', // Human-readable description (for logging)
    targetDuration:10,    // For compress: desired output seconds
    speed:4,              // For compress: alternative speed multiplier
    overlay:'Loading...', // For compress: overlay text
    muteAudio:true,       // For compress: mute audio (default true)
     fadeIn:0.5,           // Override default transition fade-in (seconds)
     fadeOut:0.3,          // Override default transition fade-out (seconds)
}
```

### Concatenation

#### `buildKeepSegments(start, end, cuts)` → `{from, to}[]`

Build keep-ranges by subtracting cut-ranges from a time span. Useful when your editing plan defines what to *remove*:

```js
const keeps = buildKeepSegments(0, 120, [
    {from: 15, to: 25},  // Cut 15s–25s
    {from: 60, to: 70},  // Cut 60s–70s
]);
// → [{ from: 0, to: 15 }, { from: 25, to: 60 }, { from: 70, to: 120 }]
```

#### `concatenateVideos(inputFiles, outputPath, tempDir, opts?)`

Concatenate using FFmpeg's concat demuxer. Re-encodes by default for format consistency.

Options:

| Option      | Default | Description                             |
|-------------|---------|-----------------------------------------|
| `reencode`  | `true`  | Re-encode during concat                 |
| `crf`       | 18      | Video quality (lower = better)          |
| `faststart` | `true`  | Add moov atom at start for web playback |

#### `concatenateViaMpegTs(inputFiles, outputPath, tempDir, opts?)`

Concatenate via MPEG-TS intermediate format. More robust when segments have different formats, but slower. Normalizes
resolution and frame rate.

### Audio

#### `normalizeAudio(inputPath, outputPath, opts?)`

Apply EBU R128 loudness normalization. Video is stream-copied (no re-encode).

Options:

| Option      | Default | Description                |
|-------------|---------|----------------------------|
| `targetI`   | -16     | Integrated loudness (LUFS) |
| `targetTP`  | -1.5    | True peak (dBTP)           |
| `targetLRA` | 11      | Loudness range (LU)        |

### Pipeline

#### `runEditPipeline(config)`

Run a complete edit pipeline in one call. See [Quick Start](#quick-start) for usage.

Config:

| Option           | Default     | Description                                |
|------------------|-------------|--------------------------------------------|
| `inputPath`      | *required*  | Source video file                          |
| `outputPath`     | *required*  | Final output file                          |
| `tempDir`        | *required*  | Temporary working directory                |
| `introTitle`     | —           | Intro billboard title (omit to skip intro) |
| `introSubtitle`  | —           | Intro billboard subtitle                   |
| `introDuration`  | 3           | Intro duration in seconds                  |
| `outroLines`     | —           | Outro text lines (omit to skip outro)      |
| `outroDuration`  | 4           | Outro duration in seconds                  |
| `segments`       | *required*  | Array of segment definitions               |
| `normalizeAudio` | `false`     | Apply loudnorm after concat                |
| `concatMethod`   | `'demuxer'` | `'demuxer'` or `'mpegts'`                  |
| `transitionDuration` | `0.5`  | Default fade duration between segments (seconds). Set to 0 to disable. |

## Best Practices

### 1. Consistent Encoding Parameters

Always match encoding parameters across all segments to avoid concat issues:

- **Video:** libx264, yuv420p pixel format, same fps and resolution
- **Audio:** AAC, same sample rate (44100 Hz) and channel count (stereo)

The utility functions handle this automatically, but be aware when mixing custom FFmpeg commands.

### 2. Always Include Silent Audio on Billboards

Title cards need a silent audio track for seamless concatenation with content that has audio. The billboard generators
handle this automatically.

### 3. Use `targetDuration` Over `speed` for Compressed Segments

Specifying `targetDuration` is more intuitive and less error-prone than calculating speed factors manually:

```js
// ✅ Preferred — clear intent
{action: 'compress', start:'00:01:00', end:'00:02:30', targetDuration:10}

// ⚠️ Works but requires mental math
{action: 'compress', start:'00:01:00', end:'00:02:30', speed:9}
```

### 4. Segment ID Naming

Use descriptive IDs for segments — they become filenames in the temp directory, making debugging easier:

```js
{id: 'intro_greeting',...}    // ✅ Easy to find in temp dir
{id: 'seg_003',...}           // ⚠️ Less informative
```

### 5. Prefer Concat Demuxer Over MPEG-TS

The concat demuxer (`concatenateVideos`) is faster and produces better results when all segments have consistent
encoding. Use MPEG-TS (`concatenateViaMpegTs`) only when you have format mismatches that can't be resolved otherwise.

### 6. Audio Normalization

Apply `normalizeAudio` as a final pass after concatenation, not per-segment. This ensures consistent loudness across the
entire video:

```js
// ✅ Normalize once at the end
runEditPipeline({..., normalizeAudio: true});

// ⚠️ Don't normalize individual segments — levels will still vary
```

### 7. Timecode Precision

Use full `HH:MM:SS.mmm` timecodes for precise cuts. FFmpeg seeks to the nearest keyframe when using `-ss` before `-i`,
so slight variations are normal. The library places `-ss` after `-i` for frame-accurate seeking (slower but precise).

### 8. Temp Directory Cleanup

The `runEditPipeline` function and `cleanupDir` handle cleanup automatically. If your script might fail mid-execution,
consider wrapping in try/finally:

```js
const TEMP = 'edit/temp';
try {
    // ... editing operations ...
} finally {
    veu.cleanupDir(TEMP);
}
```

### 9. Overlay Text on Compressed Segments

Always add overlay text on time-compressed segments so viewers understand the speedup:

```js
{
    action: 'compress',
    targetDuration:10,
    overlay:'Generating images...',  // ✅ Viewer knows what's happening
    muteAudio:true,                  // Sped-up audio sounds bad
}
```

### 10. Script Structure

Follow this pattern for maintainable edit scripts:

```js
const veu = require('./lib/video-edit-utils');
const path = require('path');

// 1. Define paths
const INPUT = path.resolve(__dirname, '../source/MyVideo.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/MyVideo.mp4');
const TEMP = path.resolve(__dirname, '../edit/temp_myvideo');

// 2. Define segments (the "edit decision list")
const segments = [
    {id: 'opening', start: '...', end: '...', action: 'keep', label: '...'},
    // ...
];

// 3. Run pipeline
veu.runEditPipeline({
    inputPath: INPUT,
    outputPath: OUTPUT,
    tempDir: TEMP,
    introTitle: '...',
    outroLines: ['...'],
    segments,
});
```