# Video Tour Scripts

A collection of bash scripts for processing, transcribing, and editing video tour files using FFmpeg, AWS Transcribe,
and VAD-based audio analysis.

## Prerequisites

Run the dependency installer before using any other scripts:

```bash
./scripts/install_deps.sh
```

This installs:

- **ffmpeg** — audio/video extraction and processing
- **AWS CLI v2** — interaction with AWS Transcribe and S3
- **jq** — JSON parsing for transcript results
- **Python VAD dependencies** — Silero VAD and/or WebRTC VAD for silence detection

You will also need valid AWS credentials configured via `aws configure` with the following IAM permissions:

- `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
- `transcribe:StartTranscriptionJob`, `transcribe:GetTranscriptionJob`, `transcribe:DeleteTranscriptionJob`

## Scripts

### `install_deps.sh`

Installs all required system and Python dependencies. Supports Linux (apt) and macOS (brew).

```bash
./scripts/install_deps.sh
```

### `install_vad_deps.sh`

Installs Python dependencies for VAD-based silence segmentation. Can install for a specific backend or all.

```bash
./scripts/install_vad_deps.sh [silero|webrtc|all]
```

- **silero** — Installs PyTorch and torchaudio (deep learning, high accuracy)
- **webrtc** — Installs webrtcvad (lightweight, fast)
- **all** — Installs both (default)

### `install_video_deps.sh`

Installs Python dependencies for video-based scene segmentation and annotation using OpenCV.

```bash
./scripts/install_video_deps.sh [minimal|full|all]
```

- **minimal** — OpenCV headless + numpy (scene detection only)
- **full** — OpenCV full + scikit-image + matplotlib + Pillow (with annotation/visualization)
- **all** — Everything including PySceneDetect (default)

### `extract_transcripts.sh`

End-to-end pipeline that extracts audio from video files, uploads to S3, runs AWS Transcribe, and downloads the
resulting transcripts.

```bash
TRANSCRIBE_S3_BUCKET=my-bucket ./scripts/extract_transcripts.sh [subdir]
```

**Arguments:**

- `subdir` — Subdirectory name containing video files (default: `source`)

**Environment Variables:**
| Variable | Default | Description |
|---|---|---|
| `TRANSCRIBE_S3_BUCKET` | *(required)* | S3 bucket for temporary audio upload |
| `TRANSCRIBE_S3_PREFIX` | `video-tour-transcription` | S3 key prefix |
| `AWS_REGION` | `us-east-1` | AWS region |
| `TRANSCRIBE_LANGUAGE_CODE` | `en-US` | Language code for transcription |
| `TRANSCRIBE_POLL_INTERVAL` | `15` | Seconds between job status polls |
| `TRANSCRIBE_CLEANUP_S3` | `true` | Delete S3 audio files after transcription |

**Pipeline Steps:**

1. Extract audio (MP3, 16kHz mono) from each video via ffmpeg
2. Upload audio files to S3
3. Start AWS Transcribe jobs
4. Poll for completion and download results
5. Generate `.txt` (plain text) and `.srt` (subtitles) from transcription JSON
6. Optionally clean up S3 audio files

**Output:** `<subdir>/transcripts/<name>.json`, `<name>.txt`, `<name>.srt`

### `segment_audio.sh`

Performs Voice Activity Detection (VAD) on video files to identify speech, silence, and noise regions.

```bash
./scripts/segment_audio.sh [subdir]
```

**Arguments:**

- `subdir` — Subdirectory name containing `.mp4` files (default: `source`)

**Environment Variables:**
| Variable | Default | Description |
|---|---|---|
| `VAD_BACKEND` | `silero` | VAD engine: `silero` or `webrtc` |
| `VAD_AGGRESSIVENESS` | `2` | WebRTC aggressiveness level (0–3) |
| `SILENCE_THRESHOLD` | `0.5` | Silero speech probability threshold |
| `MIN_SILENCE_MS` | `500` | Minimum silence duration (ms) to report |
| `MIN_SPEECH_MS` | `250` | Minimum speech duration (ms) to report |
| `NOISE_FLOOR_DB` | `-45` | RMS dB threshold: below = silence, above = noise |
| `EDIT_DIR` | `<subdir>` | Override input directory |
| `SEGMENT_DIR` | `<subdir>/segments` | Override output directory |

**Output per video:**

- `<name>.segments.json` — Machine-readable segment list with statistics
- `<name>.segments.txt` — Human-readable summary report
- `<name>.segments.srt` — SRT-style markers for use in video editors

### `segment_video.sh`

Performs video-based scene segmentation and annotation using FFmpeg and OpenCV. Detects scene boundaries, classifies
transition types, extracts thumbnails, and computes per-scene visual statistics.

```bash
./scripts/segment_video.sh [subdir]
```

**Arguments:**

- `subdir` — Subdirectory name containing `.mp4` files (default: `source`)

**Detection Methods:**

- **content** — Histogram correlation + frame differencing (default, balanced accuracy)
- **threshold** — Simple mean absolute difference threshold (fast)
- **adaptive** — Two-pass adaptive threshold using local statistics (best for variable content)

**Environment Variables:**
| Variable | Default | Description |
|---|---|---|
| `DETECTION_METHOD` | `content` | Detection method: `content`, `threshold`, or `adaptive` |
| `CONTENT_THRESHOLD` | `0.3` | Content detection sensitivity (0.0–1.0) |
| `DIFF_THRESHOLD` | `30` | Frame difference threshold (0–255) |
| `MIN_SCENE_SEC` | `2.0` | Minimum scene duration (seconds) |
| `MAX_SCENE_SEC` | `300` | Maximum scene duration before forced split (seconds) |
| `ANALYSIS_FPS` | `2` | FPS for analysis (lower = faster) |
| `THUMBNAIL_WIDTH` | `640` | Thumbnail width in pixels |
| `GENERATE_ANNOTATED` | `false` | Generate annotated video with scene overlays |
| `ANNOTATION_STYLE` | `full` | Overlay style: `minimal` or `full` |
| `EDIT_DIR` | `<subdir>` | Override input directory |
| `SCENE_DIR` | `<subdir>/scenes` | Override output directory |

**Output per video:**

- `<name>.scenes.json` — Machine-readable scene list with metadata and statistics
- `<name>.scenes.txt` — Human-readable scene summary report
- `<name>.scenes.srt` — SRT-style scene markers for use in video editors
- `<name>.thumbnails/` — Representative thumbnail image for each scene
- `<name>.annotated.mp4` — (optional) Video with scene boundary overlays

**Per-scene metadata includes:**

- Scene boundaries (start/end time and frame number)
- Transition type classification (cut, fade_in, fade_out, dissolve, gradual)
- Brightness mean and standard deviation
- Color statistics (HSV)
- Edge density
- Motion estimation (optical flow)

**Examples:**

```bash
# Basic scene detection with defaults
./scripts/segment_video.sh source

# Fast threshold-based detection
DETECTION_METHOD=threshold DIFF_THRESHOLD=40 ./scripts/segment_video.sh source

# Adaptive detection with annotated output video
DETECTION_METHOD=adaptive GENERATE_ANNOTATED=true ./scripts/segment_video.sh source

# High-sensitivity content detection with smaller thumbnails
CONTENT_THRESHOLD=0.15 THUMBNAIL_WIDTH=320 ./scripts/segment_video.sh edit
```

### `normalize_audio.sh`

Normalizes audio loudness across all `.mp4` files in a directory using two-pass EBU R128 loudness normalization. Videos
are edited in-place.

```bash
./scripts/normalize_audio.sh [subdir]
```

**Arguments:**

- `subdir` — Subdirectory name containing `.mp4` files (default: `edit`)

**Environment Variables:**

| Variable | Default | Description |
|---|---|---|
| `TARGET_I` | `-16` | Target integrated loudness (LUFS) |
| `TARGET_TP` | `-1.5` | Target true peak (dBTP) |
| `TARGET_LRA` | `11` | Target loudness range (LU) |
| `EDIT_DIR` | `<subdir>` | Override input directory |

Files already within 0.5 LUFS of the target are automatically skipped.

### `denoise_audio.sh`

Reduces background hum, noise, and other unwanted sounds from audio tracks in video files using a multi-stage FFmpeg
audio filter pipeline. Videos are edited in-place.

```bash
./scripts/denoise_audio.sh [subdir]
```

**Arguments:**

- `subdir` — Subdirectory name containing `.mp4` files (default: `edit`)
  **Filter Pipeline Stages:**

1. **High-pass filter** — Removes low-frequency rumble below cutoff frequency
2. **Hum removal** — Notch filters targeting 50Hz and/or 60Hz mains hum plus harmonics
3. **FFT denoiser** (`afftdn`) — Broadband noise reduction using spectral analysis
4. **Noise gate** — Suppresses low-level noise during non-speech segments
   **Environment Variables:**

| Variable             | Default    | Description                                               |
|----------------------|------------|-----------------------------------------------------------|
| `HIGHPASS_FREQ`      | `80`       | High-pass filter cutoff frequency (Hz)                    |
| `ENABLE_HIGHPASS`    | `true`     | Enable/disable high-pass filter                           |
| `AFFTDN_NOISE_FLOOR` | `-40`      | FFT denoiser noise floor (dB)                             |
| `AFFTDN_NOISE_TYPE`  | `w`        | Noise type: `w`=white, `v`=vinyl, `s`=shellac, `p`=custom |
| `AFFTDN_REDUCTION`   | `12`       | Noise reduction amount (dB)                               |
| `ENABLE_AFFTDN`      | `true`     | Enable/disable FFT denoiser                               |
| `HUM_FILTER`         | `none`     | Hum removal: `50` (EU), `60` (US), `both`, or `none`      |
| `HUM_HARMONICS`      | `4`        | Number of hum harmonics to remove                         |
| `GATE_THRESHOLD`     | `-35`      | Noise gate threshold (dB)                                 |
| `GATE_ATTACK`        | `25`       | Noise gate attack time (ms)                               |
| `GATE_RELEASE`       | `150`      | Noise gate release time (ms)                              |
| `GATE_RANGE`         | `20`       | Noise gate range/reduction (dB)                           |
| `ENABLE_GATE`        | `true`     | Enable/disable noise gate                                 |
| `EDIT_DIR`           | `<subdir>` | Override input directory                                  |

Each stage can be independently enabled or disabled. For example, to only apply hum removal for 60Hz mains:

```bash
ENABLE_HIGHPASS=false ENABLE_AFFTDN=false ENABLE_GATE=false HUM_FILTER=60 ./scripts/denoise_audio.sh edit
```

### `runall_edits.sh`

Convenience script that runs all `edit_*.sh` scripts found in the project root (sorted alphabetically), then runs
`denoise_audio.sh` to reduce background noise, and finally runs `normalize_audio.sh` to level audio across all edited
videos.

```bash
./scripts/runall_edits.sh
```

## Directory Structure

```
video_tour/
├── source/                    # Original video files
│   ├── audio/                 # Extracted audio (MP3)
│   ├── transcripts/           # Transcription output (JSON, TXT, SRT)
│   └── segments/              # VAD segmentation output (JSON, TXT, SRT)
├── edit/                      # Edited video files
├── edit_*.sh                  # Per-video edit scripts
└── scripts/
    ├── README.md              # This file
    ├── install_deps.sh        # Dependency installer
    ├── install_vad_deps.sh    # VAD Python dependency installer
     ├── install_video_deps.sh  # Video/OpenCV Python dependency installer
    ├── extract_transcripts.sh # Transcription pipeline
    ├── segment_audio.sh       # VAD silence segmentation
     ├── segment_video.sh       # Video scene segmentation & annotation
     ├── denoise_audio.sh       # Audio noise reduction
    ├── normalize_audio.sh     # EBU R128 audio normalization
    └── runall_edits.sh        # Run all edits + normalize
```

## Typical Workflow

```bash
# 1. Install dependencies
./scripts/install_deps.sh

# 2. Configure AWS credentials
aws configure

# 3. Analyze source videos for speech/silence regions (audio)
./scripts/segment_audio.sh source

# 4. Analyze source videos for scene boundaries (video)
./scripts/segment_video.sh source

# 5. Transcribe source videos
TRANSCRIBE_S3_BUCKET=my-bucket ./scripts/extract_transcripts.sh source

# 6. Create edit scripts based on analysis, then run all edits
./scripts/runall_edits.sh
```