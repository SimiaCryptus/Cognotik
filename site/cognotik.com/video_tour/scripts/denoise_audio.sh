#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# denoise_audio.sh
#
# Reduces background hum, noise, and other unwanted sounds from audio tracks
# in video files using FFmpeg's audio filters.
#
# Applies a multi-stage noise reduction pipeline:
#   1. High-pass filter to remove low-frequency rumble/hum
#   2. afftdn (FFT-based denoiser) to reduce broadband background noise
#   3. Optional notch filters for known hum frequencies (50Hz/60Hz mains hum)
#   4. Dynamic noise gate to suppress low-level noise between speech
#
# Videos are edited in-place (original is replaced with denoised version).
#
# Usage:
#   ./scripts/denoise_audio.sh [subdir]
#
# Arguments:
#   subdir  — Subdirectory name containing .mp4 files (default: edit)
#
# Environment variables:
#   HIGHPASS_FREQ         - High-pass filter cutoff frequency in Hz (default: 80)
#   AFFTDN_NOISE_FLOOR   - afftdn noise floor in dB (default: -40)
#   AFFTDN_NOISE_TYPE     - afftdn noise type: w=white, v=vinyl, s=shellac,
#                           p=custom profile (default: w)
#   AFFTDN_REDUCTION      - Noise reduction amount in dB (default: 12)
#   HUM_FILTER            - Mains hum removal: "50" (EU), "60" (US), "both",
#                           or "none" (default: none)
#   HUM_HARMONICS         - Number of hum harmonics to remove (default: 4)
#   GATE_THRESHOLD        - Noise gate threshold in dB (default: -35)
#   GATE_ATTACK           - Noise gate attack time in ms (default: 25)
#   GATE_RELEASE          - Noise gate release time in ms (default: 150)
#   GATE_RANGE            - Noise gate range/reduction in dB (default: 20)
#   ENABLE_HIGHPASS       - Enable high-pass filter: true/false (default: true)
#   ENABLE_AFFTDN         - Enable FFT denoiser: true/false (default: true)
#   ENABLE_GATE           - Enable noise gate: true/false (default: true)
#   EDIT_DIR              - Override input directory (default: <subdir>)
# =============================================================================

SUBDIR_NAME=${1:-"edit"}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EDIT_DIR="${EDIT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}}"

# High-pass filter settings
HIGHPASS_FREQ="${HIGHPASS_FREQ:-80}"
ENABLE_HIGHPASS="${ENABLE_HIGHPASS:-true}"

# FFT-based denoiser settings
AFFTDN_NOISE_FLOOR="${AFFTDN_NOISE_FLOOR:--40}"
AFFTDN_NOISE_TYPE="${AFFTDN_NOISE_TYPE:-w}"
AFFTDN_REDUCTION="${AFFTDN_REDUCTION:-12}"
ENABLE_AFFTDN="${ENABLE_AFFTDN:-true}"

# Hum removal settings
HUM_FILTER="${HUM_FILTER:-none}"
HUM_HARMONICS="${HUM_HARMONICS:-4}"

# Noise gate settings
GATE_THRESHOLD="${GATE_THRESHOLD:--35}"
GATE_ATTACK="${GATE_ATTACK:-25}"
GATE_RELEASE="${GATE_RELEASE:-150}"
GATE_RANGE="${GATE_RANGE:-20}"
ENABLE_GATE="${ENABLE_GATE:-true}"

# =============================================================================
# Validation
# =============================================================================
if ! command -v ffmpeg &>/dev/null; then
  echo "ERROR: 'ffmpeg' is not installed. Run ./scripts/install_deps.sh first."
  exit 1
fi

if [[ ! -d "$EDIT_DIR" ]]; then
  echo "ERROR: Edit directory not found: ${EDIT_DIR}"
  exit 1
fi

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

# =============================================================================
# Build audio filter chain
# =============================================================================
build_filter_chain() {
  local filters=()

  # Stage 1: High-pass filter to remove low-frequency rumble
  if [[ "$ENABLE_HIGHPASS" == "true" ]]; then
    filters+=("highpass=f=${HIGHPASS_FREQ}:poles=2")
  fi

  # Stage 2: Notch filters for mains hum removal (50Hz and/or 60Hz + harmonics)
  if [[ "$HUM_FILTER" != "none" ]]; then
    local hum_freqs=()
    if [[ "$HUM_FILTER" == "50" || "$HUM_FILTER" == "both" ]]; then
      for ((h = 1; h <= HUM_HARMONICS; h++)); do
        hum_freqs+=($((50 * h)))
      done
    fi
    if [[ "$HUM_FILTER" == "60" || "$HUM_FILTER" == "both" ]]; then
      for ((h = 1; h <= HUM_HARMONICS; h++)); do
        hum_freqs+=($((60 * h)))
      done
    fi
    for freq in "${hum_freqs[@]}"; do
      # Narrow notch filter (high Q) centered on hum frequency
      filters+=("equalizer=f=${freq}:t=q:w=10:g=-30")
    done
  fi

  # Stage 3: FFT-based noise reduction
  if [[ "$ENABLE_AFFTDN" == "true" ]]; then
    filters+=("afftdn=nf=${AFFTDN_NOISE_FLOOR}:nt=${AFFTDN_NOISE_TYPE}:nr=${AFFTDN_REDUCTION}:om=o")
  fi

  # Stage 4: Noise gate to suppress low-level noise between speech
  if [[ "$ENABLE_GATE" == "true" ]]; then
    # Convert ms to seconds for ffmpeg
    local attack_s
    attack_s=$(echo "scale=4; ${GATE_ATTACK} / 1000" | bc)
    local release_s
    release_s=$(echo "scale=4; ${GATE_RELEASE} / 1000" | bc)
    # Convert threshold from dB to linear amplitude (agate expects linear values)
    local threshold_lin
    threshold_lin=$(echo "scale=6; e(${GATE_THRESHOLD} / 20 * l(10))" | bc -l)
    # Convert range from dB to linear ratio (0-1 scale, where 0=full mute, 1=no gating)
    local range_lin
    range_lin=$(echo "scale=6; e(-1 * ${GATE_RANGE} / 20 * l(10))" | bc -l)
    filters+=("agate=threshold=${threshold_lin}:attack=${attack_s}:release=${release_s}:range=${range_lin}")
  fi

  # Join filters with commas
  local IFS=','
  echo "${filters[*]}"
}

FILTER_CHAIN="$(build_filter_chain)"

if [[ -z "$FILTER_CHAIN" ]]; then
  echo "WARNING: All denoise stages are disabled. Nothing to do."
  exit 0
fi

echo "=== Audio Denoise Processing ==="
echo "  Edit Dir:           ${EDIT_DIR}"
echo "  Videos to process:  ${#VIDEO_FILES[@]}"
echo ""
echo "  Filter Pipeline:"
if [[ "$ENABLE_HIGHPASS" == "true" ]]; then
  echo "    [1] High-pass filter:  ${HIGHPASS_FREQ} Hz"
fi
if [[ "$HUM_FILTER" != "none" ]]; then
  echo "    [2] Hum removal:       ${HUM_FILTER} Hz (${HUM_HARMONICS} harmonics)"
fi
if [[ "$ENABLE_AFFTDN" == "true" ]]; then
  echo "    [3] FFT denoiser:      floor=${AFFTDN_NOISE_FLOOR}dB, reduction=${AFFTDN_REDUCTION}dB, type=${AFFTDN_NOISE_TYPE}"
fi
if [[ "$ENABLE_GATE" == "true" ]]; then
  echo "    [4] Noise gate:        threshold=${GATE_THRESHOLD}dB, attack=${GATE_ATTACK}ms, release=${GATE_RELEASE}ms, range=${GATE_RANGE}dB"
fi
echo ""
echo "  Full filter chain: ${FILTER_CHAIN}"
echo ""

# =============================================================================
# Process each video
# =============================================================================
SUCCESS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

for video_path in "${VIDEO_FILES[@]}"; do
  video_file="$(basename "$video_path")"
  base_name="${video_file%.*}"

  echo "--- Processing: ${video_file} ---"

  # Check if the video has an audio stream
  audio_streams=$(ffprobe -v error -select_streams a -show_entries stream=index \
    -of csv=p=0 "$video_path" 2>/dev/null | wc -l)

  if [[ "$audio_streams" -eq 0 ]]; then
    echo "  [SKIP] No audio stream found."
    ((SKIP_COUNT++)) || true
    echo ""
    continue
  fi

  tmp_output="${EDIT_DIR}/${base_name}.denoised.mp4"

  echo "  [DENOISE] Applying filter chain..."
  if ffmpeg -hide_banner -nostdin -i "$video_path" \
    -map 0:v -map 0:a \
    -c:v copy \
    -af "$FILTER_CHAIN" \
    -c:a aac -b:a 192k \
    -y "$tmp_output" \
    -loglevel warning -stats 2>&1; then

    # Verify output file is valid
    output_duration=$(ffprobe -v error -show_entries format=duration \
      -of default=noprint_wrappers=1:nokey=1 "$tmp_output" 2>/dev/null || echo "0")
    input_duration=$(ffprobe -v error -show_entries format=duration \
      -of default=noprint_wrappers=1:nokey=1 "$video_path" 2>/dev/null || echo "0")

    # Check that output duration is within 1 second of input
    duration_diff=$(echo "$output_duration - $input_duration" | bc 2>/dev/null || echo "999")
    abs_diff=$(echo "${duration_diff#-}")

    if [[ -f "$tmp_output" ]] && (( $(echo "$abs_diff < 1.0" | bc -l 2>/dev/null || echo 0) )); then
      mv "$tmp_output" "$video_path"
      echo "  [DONE] ${video_file} denoised successfully."
      ((SUCCESS_COUNT++)) || true
    else
      echo "  [ERROR] Output validation failed for ${video_file}. Duration mismatch: ${duration_diff}s"
      rm -f "$tmp_output"
      ((FAIL_COUNT++)) || true
    fi
  else
    echo "  [ERROR] FFmpeg denoise failed for ${video_file}."
    rm -f "$tmp_output"
    ((FAIL_COUNT++)) || true
  fi

  echo ""
done

# =============================================================================
# Summary
# =============================================================================
echo "=== Denoise Complete ==="
echo "  Succeeded: ${SUCCESS_COUNT}"
echo "  Skipped:   ${SKIP_COUNT}"
echo "  Failed:    ${FAIL_COUNT}"
echo ""

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "WARNING: ${FAIL_COUNT} file(s) failed to denoise."
  exit 1
fi

echo "Done."