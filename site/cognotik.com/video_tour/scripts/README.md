# Video Tour Scripts

A collection of bash scripts for processing, transcribing, and editing video tour files using FFmpeg, AWS Transcribe, and VAD-based audio analysis.

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

### `extract_transcripts.sh`

End-to-end pipeline that extracts audio from video files, uploads to S3, runs AWS Transcribe, and downloads the resulting transcripts.

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

### `normalize_audio.sh`

Normalizes audio loudness across all `.mp4` files in a directory using two-pass EBU R128 loudness normalization. Videos are edited in-place.

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

### `runall_edits.sh`

Convenience script that runs all `edit_*.sh` scripts found in the project root (sorted alphabetically), then runs `normalize_audio.sh` to level audio across all edited videos.

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
    ├── extract_transcripts.sh # Transcription pipeline
    ├── segment_audio.sh       # VAD silence segmentation
    ├── normalize_audio.sh     # EBU R128 audio normalization
    └── runall_edits.sh        # Run all edits + normalize
```

## Typical Workflow

```bash
# 1. Install dependencies
./scripts/install_deps.sh

# 2. Configure AWS credentials
aws configure

# 3. Analyze source videos for speech/silence regions
./scripts/segment_audio.sh source

# 4. Transcribe source videos
TRANSCRIBE_S3_BUCKET=my-bucket ./scripts/extract_transcripts.sh source

# 5. Create edit scripts based on analysis, then run all edits
./scripts/runall_edits.sh
```