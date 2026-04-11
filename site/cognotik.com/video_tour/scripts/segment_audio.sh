#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# segment_audio.sh
#
# Performs Voice Activity Detection (VAD) on audio extracted from video files
# to segment into speech, silence, and noise regions.
#
# Supports two VAD backends:
#   - silero   : Silero VAD (deep learning, high accuracy) [default]
#   - webrtc   : WebRTC VAD (lightweight, fast)
#
# Outputs per video:
#   - <name>.segments.json   : Machine-readable segment list
#   - <name>.segments.txt    : Human-readable segment summary
#   - <name>.segments.srt    : SRT-style markers for use in editors
#
# Usage:
#   ./scripts/segment_audio.sh
#
# Environment variables:
#   VAD_BACKEND           - "silero" or "webrtc" (default: silero)
#   VAD_AGGRESSIVENESS    - WebRTC aggressiveness 0-3 (default: 2)
#   SILENCE_THRESHOLD     - Silero speech probability threshold (default: 0.5)
#   MIN_SILENCE_MS        - Minimum silence duration in ms to report (default: 500)
#   MIN_SPEECH_MS         - Minimum speech duration in ms to report (default: 250)
#   NOISE_FLOOR_DB        - RMS dB below which is considered silence vs noise
#                           (default: -45)
#   EDIT_DIR              - Override input directory (default: ../edit)
#   SEGMENT_DIR           - Override output directory (default: ../edit/segments)
# =============================================================================

SUBDIR_NAME=${1:-"source"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EDIT_DIR="${EDIT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}}"
SEGMENT_DIR="${SEGMENT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}/segments}"
AUDIO_TMP_DIR="${SEGMENT_DIR}/.audio_tmp"

# VAD configuration
VAD_BACKEND="${VAD_BACKEND:-silero}"
VAD_AGGRESSIVENESS="${VAD_AGGRESSIVENESS:-2}"
SILENCE_THRESHOLD="${SILENCE_THRESHOLD:-0.5}"
MIN_SILENCE_MS="${MIN_SILENCE_MS:-500}"
MIN_SPEECH_MS="${MIN_SPEECH_MS:-250}"
NOISE_FLOOR_DB="${NOISE_FLOOR_DB:--45}"

# =============================================================================
# Validation
# =============================================================================
if ! command -v ffmpeg &>/dev/null; then
  echo "ERROR: 'ffmpeg' is not installed. Run ./scripts/install_deps.sh first."
  exit 1
fi

if ! command -v python3 &>/dev/null; then
  echo "ERROR: 'python3' is not installed."
  exit 1
fi

if [[ ! -d "$EDIT_DIR" ]]; then
  echo "ERROR: Edit directory not found: ${EDIT_DIR}"
  exit 1
fi

# Check Python dependencies based on backend
echo "Checking Python dependencies for VAD backend: ${VAD_BACKEND}..."
python3 -c "
import importlib, sys
required = ['numpy']
backend = '${VAD_BACKEND}'
if backend == 'silero':
    required += ['torch', 'torchaudio']
elif backend == 'webrtc':
    required += ['webrtcvad']
else:
    print(f'ERROR: Unknown VAD backend: {backend}. Use \"silero\" or \"webrtc\".', file=sys.stderr)
    sys.exit(1)

missing = []
for mod in required:
    try:
        importlib.import_module(mod)
    except ImportError:
        missing.append(mod)

if missing:
    print(f'ERROR: Missing Python packages: {\" \".join(missing)}', file=sys.stderr)
    print(f'Install with: pip install {\" \".join(missing)}', file=sys.stderr)
    sys.exit(1)

print('  All dependencies satisfied.')
" || exit 1

# =============================================================================
# Collect video files
# =============================================================================
declare -a VIDEO_FILES=()
for video_path in "${EDIT_DIR}"/*.mp4; do
  [[ -f "$video_path" ]] || continue
  VIDEO_FILES+=("$video_path")
done

if [[ ${#VIDEO_FILES[@]} -eq 0 ]]; then
  echo "ERROR: No .mp4 files found in ${EDIT_DIR}"
  exit 1
fi

mkdir -p "$SEGMENT_DIR" "$AUDIO_TMP_DIR"

echo "=== Silence / Speech Segmentation (VAD) ==="
echo "  Backend:            ${VAD_BACKEND}"
echo "  Edit Dir:           ${EDIT_DIR}"
echo "  Segment Dir:        ${SEGMENT_DIR}"
echo "  Min Silence:        ${MIN_SILENCE_MS} ms"
echo "  Min Speech:         ${MIN_SPEECH_MS} ms"
echo "  Noise Floor:        ${NOISE_FLOOR_DB} dB"
if [[ "$VAD_BACKEND" == "silero" ]]; then
  echo "  Speech Threshold:   ${SILENCE_THRESHOLD}"
elif [[ "$VAD_BACKEND" == "webrtc" ]]; then
  echo "  Aggressiveness:     ${VAD_AGGRESSIVENESS}"
fi
echo "  Videos to process:  ${#VIDEO_FILES[@]}"
echo ""

# =============================================================================
# Process each video
# =============================================================================
SUCCESS_COUNT=0
FAIL_COUNT=0

for video_path in "${VIDEO_FILES[@]}"; do
  video_file="$(basename "$video_path")"
  base_name="${video_file%.*}"

  echo "--- Processing: ${video_file} ---"

  json_out="${SEGMENT_DIR}/${base_name}.segments.json"
  txt_out="${SEGMENT_DIR}/${base_name}.segments.txt"
  srt_out="${SEGMENT_DIR}/${base_name}.segments.srt"

  # Step 1: Extract mono 16kHz WAV for VAD processing
  wav_file="${AUDIO_TMP_DIR}/${base_name}.wav"
  if [[ ! -f "$wav_file" ]]; then
    echo "  [EXTRACT] Extracting audio -> ${wav_file}"
    ffmpeg -hide_banner -nostdin -i "$video_path" \
      -vn -acodec pcm_s16le -ar 16000 -ac 1 \
      -y "$wav_file" \
      -loglevel warning -stats 2>&1
  else
    echo "  [SKIP] Audio already extracted: ${wav_file}"
  fi

  # Step 2: Run VAD segmentation via Python
  echo "  [VAD] Running ${VAD_BACKEND} segmentation..."

  python3 << 'PYTHON_SCRIPT' - "$wav_file" "$json_out" "$txt_out" "$srt_out" "$VAD_BACKEND" "$SILENCE_THRESHOLD" "$VAD_AGGRESSIVENESS" "$MIN_SILENCE_MS" "$MIN_SPEECH_MS" "$NOISE_FLOOR_DB"
import sys
import json
import struct
import wave
import numpy as np
from pathlib import Path

# ---------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------
wav_path        = sys.argv[1]
json_out        = sys.argv[2]
txt_out         = sys.argv[3]
srt_out         = sys.argv[4]
vad_backend     = sys.argv[5]
speech_thresh   = float(sys.argv[6])
aggressiveness  = int(sys.argv[7])
min_silence_ms  = int(sys.argv[8])
min_speech_ms   = int(sys.argv[9])
noise_floor_db  = float(sys.argv[10])

SAMPLE_RATE = 16000

# ---------------------------------------------------------------------------
# Read WAV file
# ---------------------------------------------------------------------------
def read_wav(path):
    with wave.open(path, 'rb') as wf:
        assert wf.getnchannels() == 1, "Expected mono audio"
        assert wf.getsampwidth() == 2, "Expected 16-bit audio"
        assert wf.getframerate() == SAMPLE_RATE, f"Expected {SAMPLE_RATE}Hz"
        n_frames = wf.getnframes()
        raw = wf.readframes(n_frames)
    samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
    return samples

# ---------------------------------------------------------------------------
# Compute RMS energy in dB for a chunk of samples
# ---------------------------------------------------------------------------
def rms_db(samples):
    if len(samples) == 0:
        return -100.0
    rms = np.sqrt(np.mean(samples.astype(np.float64) ** 2))
    if rms < 1e-10:
        return -100.0
    return 20.0 * np.log10(rms / 32768.0)

# ---------------------------------------------------------------------------
# Silero VAD
# ---------------------------------------------------------------------------
def run_silero_vad(samples):
    import torch
    # Load Silero VAD model
    model, utils = torch.hub.load(
        repo_or_dir='snakers4/silero-vad',
        model='silero_vad',
        force_reload=False,
        onnx=False,
        trust_repo=True
    )
    (get_speech_timestamps, _, read_audio, _, _) = utils

    # Convert numpy to torch tensor
    audio_tensor = torch.from_numpy(samples / 32768.0).float()

    # Get speech timestamps
    speech_timestamps = get_speech_timestamps(
        audio_tensor,
        model,
        threshold=speech_thresh,
        sampling_rate=SAMPLE_RATE,
        min_silence_duration_ms=min_silence_ms,
        min_speech_duration_ms=min_speech_ms,
        return_seconds=False
    )

    # Convert sample indices to frame-level boolean mask
    # Each entry: {'start': sample_idx, 'end': sample_idx}
    speech_regions = []
    for ts in speech_timestamps:
        speech_regions.append((ts['start'] / SAMPLE_RATE, ts['end'] / SAMPLE_RATE))

    return speech_regions

# ---------------------------------------------------------------------------
# WebRTC VAD
# ---------------------------------------------------------------------------
def run_webrtc_vad(samples):
    import webrtcvad
    vad = webrtcvad.Vad(aggressiveness)

    # WebRTC VAD requires frames of 10, 20, or 30 ms
    frame_duration_ms = 30
    frame_size = int(SAMPLE_RATE * frame_duration_ms / 1000)  # 480 samples
    raw_bytes = (samples.astype(np.int16)).tobytes()

    speech_frames = []
    n_frames = len(samples) // frame_size

    for i in range(n_frames):
        offset = i * frame_size * 2  # 2 bytes per sample
        frame = raw_bytes[offset:offset + frame_size * 2]
        if len(frame) < frame_size * 2:
            break
        is_speech = vad.is_speech(frame, SAMPLE_RATE)
        speech_frames.append((i * frame_duration_ms / 1000.0, is_speech))

    # Merge consecutive speech frames into regions
    speech_regions = []
    in_speech = False
    start_t = 0.0

    for t, is_speech in speech_frames:
        if is_speech and not in_speech:
            start_t = t
            in_speech = True
        elif not is_speech and in_speech:
            end_t = t
            duration_ms = (end_t - start_t) * 1000
            if duration_ms >= min_speech_ms:
                speech_regions.append((start_t, end_t))
            in_speech = False

    # Handle trailing speech
    if in_speech:
        end_t = len(samples) / SAMPLE_RATE
        duration_ms = (end_t - start_t) * 1000
        if duration_ms >= min_speech_ms:
            speech_regions.append((start_t, end_t))

    return speech_regions

# ---------------------------------------------------------------------------
# Classify gaps between speech as silence or noise
# ---------------------------------------------------------------------------
def classify_segments(samples, speech_regions, total_duration):
    segments = []
    prev_end = 0.0

    for (start, end) in speech_regions:
        # Gap before this speech region
        if start > prev_end:
            gap_start = prev_end
            gap_end = start
            gap_duration_ms = (gap_end - gap_start) * 1000

            if gap_duration_ms >= min_silence_ms:
                # Classify gap as silence or noise based on RMS energy
                s_start = int(gap_start * SAMPLE_RATE)
                s_end = int(gap_end * SAMPLE_RATE)
                chunk = samples[s_start:s_end]
                db = rms_db(chunk)

                if db <= noise_floor_db:
                    seg_type = "silence"
                else:
                    seg_type = "noise"

                segments.append({
                    "type": seg_type,
                    "start": round(gap_start, 3),
                    "end": round(gap_end, 3),
                    "duration": round(gap_end - gap_start, 3),
                    "rms_db": round(db, 1)
                })

        # Speech region
        s_start = int(start * SAMPLE_RATE)
        s_end = int(end * SAMPLE_RATE)
        chunk = samples[s_start:s_end]
        db = rms_db(chunk)

        segments.append({
            "type": "speech",
            "start": round(start, 3),
            "end": round(end, 3),
            "duration": round(end - start, 3),
            "rms_db": round(db, 1)
        })

        prev_end = end

    # Trailing gap after last speech
    if prev_end < total_duration:
        gap_start = prev_end
        gap_end = total_duration
        gap_duration_ms = (gap_end - gap_start) * 1000

        if gap_duration_ms >= min_silence_ms:
            s_start = int(gap_start * SAMPLE_RATE)
            s_end = int(gap_end * SAMPLE_RATE)
            chunk = samples[s_start:s_end]
            db = rms_db(chunk)

            if db <= noise_floor_db:
                seg_type = "silence"
            else:
                seg_type = "noise"

            segments.append({
                "type": seg_type,
                "start": round(gap_start, 3),
                "end": round(gap_end, 3),
                "duration": round(gap_end - gap_start, 3),
                "rms_db": round(db, 1)
            })

    return segments

# ---------------------------------------------------------------------------
# Format timestamp for SRT
# ---------------------------------------------------------------------------
def format_srt_ts(seconds):
    s = float(seconds)
    h = int(s // 3600)
    m = int((s % 3600) // 60)
    sec = int(s % 60)
    ms = int((s - int(s)) * 1000)
    return f"{h:02d}:{m:02d}:{sec:02d},{ms:03d}"

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
print(f"  [READ] {wav_path}")
samples = read_wav(wav_path)
total_duration = len(samples) / SAMPLE_RATE
print(f"  [INFO] Duration: {total_duration:.1f}s ({len(samples)} samples)")

# Run VAD
print(f"  [VAD] Running {vad_backend} backend...")
if vad_backend == "silero":
    speech_regions = run_silero_vad(samples)
elif vad_backend == "webrtc":
    speech_regions = run_webrtc_vad(samples)
else:
    print(f"ERROR: Unknown backend: {vad_backend}", file=sys.stderr)
    sys.exit(1)

print(f"  [VAD] Found {len(speech_regions)} speech region(s)")

# Classify all segments
segments = classify_segments(samples, speech_regions, total_duration)

# Compute summary statistics
speech_segs = [s for s in segments if s["type"] == "speech"]
silence_segs = [s for s in segments if s["type"] == "silence"]
noise_segs = [s for s in segments if s["type"] == "noise"]

speech_total = sum(s["duration"] for s in speech_segs)
silence_total = sum(s["duration"] for s in silence_segs)
noise_total = sum(s["duration"] for s in noise_segs)

summary = {
    "file": Path(wav_path).stem,
    "duration": round(total_duration, 3),
    "vad_backend": vad_backend,
    "settings": {
        "min_silence_ms": min_silence_ms,
        "min_speech_ms": min_speech_ms,
        "noise_floor_db": noise_floor_db,
        "speech_threshold": speech_thresh if vad_backend == "silero" else None,
        "aggressiveness": aggressiveness if vad_backend == "webrtc" else None,
    },
    "statistics": {
        "speech_segments": len(speech_segs),
        "silence_segments": len(silence_segs),
        "noise_segments": len(noise_segs),
        "speech_duration": round(speech_total, 3),
        "silence_duration": round(silence_total, 3),
        "noise_duration": round(noise_total, 3),
        "speech_pct": round(100 * speech_total / total_duration, 1) if total_duration > 0 else 0,
        "silence_pct": round(100 * silence_total / total_duration, 1) if total_duration > 0 else 0,
        "noise_pct": round(100 * noise_total / total_duration, 1) if total_duration > 0 else 0,
    },
    "segments": segments
}

# Write JSON output
with open(json_out, 'w') as f:
    json.dump(summary, f, indent=2)
print(f"  [WRITE] {json_out}")

# Write human-readable text summary
with open(txt_out, 'w') as f:
    f.write(f"Silence Segmentation Report: {Path(wav_path).stem}\n")
    f.write(f"{'=' * 60}\n")
    f.write(f"Duration:       {total_duration:.1f}s\n")
    f.write(f"VAD Backend:    {vad_backend}\n")
    f.write(f"\n")
    f.write(f"Summary:\n")
    f.write(f"  Speech:   {speech_total:7.1f}s  ({summary['statistics']['speech_pct']:5.1f}%)  [{len(speech_segs)} segments]\n")
    f.write(f"  Silence:  {silence_total:7.1f}s  ({summary['statistics']['silence_pct']:5.1f}%)  [{len(silence_segs)} segments]\n")
    f.write(f"  Noise:    {noise_total:7.1f}s  ({summary['statistics']['noise_pct']:5.1f}%)  [{len(noise_segs)} segments]\n")
    f.write(f"\n")
    f.write(f"{'#':>4}  {'Type':<8}  {'Start':>9}  {'End':>9}  {'Duration':>9}  {'RMS dB':>7}\n")
    f.write(f"{'-' * 55}\n")
    for i, seg in enumerate(segments, 1):
        start_str = f"{seg['start']:.3f}s"
        end_str = f"{seg['end']:.3f}s"
        dur_str = f"{seg['duration']:.3f}s"
        f.write(f"{i:4d}  {seg['type']:<8}  {start_str:>9}  {end_str:>9}  {dur_str:>9}  {seg['rms_db']:>6.1f}\n")
print(f"  [WRITE] {txt_out}")

# Write SRT-style markers
with open(srt_out, 'w') as f:
    for i, seg in enumerate(segments, 1):
        label = seg["type"].upper()
        db_info = f"[{seg['rms_db']:.1f} dB]"
        f.write(f"{i}\n")
        f.write(f"{format_srt_ts(seg['start'])} --> {format_srt_ts(seg['end'])}\n")
        f.write(f"[{label}] {seg['duration']:.1f}s {db_info}\n")
        f.write(f"\n")
print(f"  [WRITE] {srt_out}")

# Print summary to stdout
print(f"  [SUMMARY] Speech: {speech_total:.1f}s ({summary['statistics']['speech_pct']}%) | "
      f"Silence: {silence_total:.1f}s ({summary['statistics']['silence_pct']}%) | "
      f"Noise: {noise_total:.1f}s ({summary['statistics']['noise_pct']}%)")

PYTHON_SCRIPT

  if [[ $? -eq 0 ]]; then
    echo "  [DONE] ${video_file} segmented successfully."
    ((SUCCESS_COUNT++)) || true
  else
    echo "  [ERROR] Segmentation failed for ${video_file}."
    ((FAIL_COUNT++)) || true
  fi

  echo ""
done

# =============================================================================
# Cleanup temporary audio files
# =============================================================================
echo "--- Cleaning up temporary audio files ---"
rm -rf "$AUDIO_TMP_DIR"
echo ""

# =============================================================================
# Summary
# =============================================================================
echo "=== Segmentation Complete ==="
echo "  Succeeded: ${SUCCESS_COUNT}"
echo "  Failed:    ${FAIL_COUNT}"
echo ""
echo "Output files in: ${SEGMENT_DIR}/"
for video_path in "${VIDEO_FILES[@]}"; do
  base_name="$(basename "${video_path%.*}")"
  json_out="${SEGMENT_DIR}/${base_name}.segments.json"
  txt_out="${SEGMENT_DIR}/${base_name}.segments.txt"
  srt_out="${SEGMENT_DIR}/${base_name}.segments.srt"
  if [[ -f "$json_out" ]]; then
    echo "  [JSON] ${json_out}"
    echo "  [TXT]  ${txt_out}"
    echo "  [SRT]  ${srt_out}"
  fi
done
echo ""

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "WARNING: ${FAIL_COUNT} file(s) failed to segment."
  exit 1
fi

echo "Done."