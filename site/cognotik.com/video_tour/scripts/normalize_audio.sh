#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# normalize_audio.sh
#
# Normalizes voice/speech audio levels in all videos under edit/ directory.
# Uses a two-pass EBU R128 loudness normalization (loudnorm filter) for
# broadcast-quality speech leveling.
#
# Videos are edited in-place (original is replaced after successful processing).
#
# Target levels (optimized for speech/voice):
#   - Integrated loudness: -16 LUFS (standard for online video)
#   - True peak: -1.5 dBTP
#   - Loudness range: 11 LU
#
# Usage:
#   ./scripts/normalize_audio.sh
#
# Environment variables:
#   TARGET_I      - Target integrated loudness in LUFS (default: -16)
#   TARGET_TP     - Target true peak in dBTP (default: -1.5)
#   TARGET_LRA    - Target loudness range in LU (default: 11)
#   EDIT_DIR      - Override edit directory path
# =============================================================================


SUBDIR_NAME=${1:-"edit"}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EDIT_DIR="${EDIT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}}"

# EBU R128 target levels — tuned for speech/voice content
TARGET_I="${TARGET_I:--16}"
TARGET_TP="${TARGET_TP:--1.5}"
TARGET_LRA="${TARGET_LRA:-11}"

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

echo "=== Audio Volume Normalization (EBU R128) ==="
echo "  Edit Dir:           ${EDIT_DIR}"
echo "  Target Loudness:    ${TARGET_I} LUFS"
echo "  Target True Peak:   ${TARGET_TP} dBTP"
echo "  Target LRA:         ${TARGET_LRA} LU"
echo "  Videos to process:  ${#VIDEO_FILES[@]}"
echo ""

SUCCESS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

for video_path in "${VIDEO_FILES[@]}"; do
  video_file="$(basename "$video_path")"
  echo "--- Processing: ${video_file} ---"

  # Temp file for output (same directory to ensure same filesystem for mv)
  tmp_output="${video_path}.normalized.tmp.mp4"

  # =========================================================================
  # Pass 1: Analyze current loudness levels
  # =========================================================================
  echo "  [PASS 1] Measuring loudness..."

  loudnorm_stats=$(ffmpeg -hide_banner -nostdin -i "$video_path" \
    -af "loudnorm=I=${TARGET_I}:TP=${TARGET_TP}:LRA=${TARGET_LRA}:print_format=json" \
    -f null /dev/null 2>&1 | tail -n 12)

  # Parse the JSON stats from loudnorm output
  measured_I=$(echo "$loudnorm_stats" | grep '"input_i"' | sed 's/.*: "//;s/".*//')
  measured_TP=$(echo "$loudnorm_stats" | grep '"input_tp"' | sed 's/.*: "//;s/".*//')
  measured_LRA=$(echo "$loudnorm_stats" | grep '"input_lra"' | sed 's/.*: "//;s/".*//')
  measured_thresh=$(echo "$loudnorm_stats" | grep '"input_thresh"' | sed 's/.*: "//;s/".*//')
  target_offset=$(echo "$loudnorm_stats" | grep '"target_offset"' | sed 's/.*: "//;s/".*//')

  if [[ -z "$measured_I" || "$measured_I" == "" ]]; then
    echo "  [ERROR] Could not measure loudness for ${video_file}. Skipping."
    echo ""
    ((FAIL_COUNT++)) || true
    continue
  fi

  echo "  [STATS] Input: ${measured_I} LUFS, TP: ${measured_TP} dBTP, LRA: ${measured_LRA} LU"

  # Check if already within acceptable range (within 0.5 LUFS of target)
  already_normalized=$(python3 -c "
i = float('${measured_I}')
t = float('${TARGET_I}')
print('yes' if abs(i - t) < 0.5 else 'no')
" 2>/dev/null || echo "no")

  if [[ "$already_normalized" == "yes" ]]; then
    echo "  [SKIP] Already at target loudness (${measured_I} LUFS ≈ ${TARGET_I} LUFS)"
    echo ""
    ((SKIP_COUNT++)) || true
    continue
  fi

  # =========================================================================
  # Pass 2: Apply loudness normalization with measured values
  # =========================================================================
  echo "  [PASS 2] Applying normalization (${measured_I} LUFS -> ${TARGET_I} LUFS)..."

  if ffmpeg -hide_banner -nostdin -i "$video_path" \
    -af "loudnorm=I=${TARGET_I}:TP=${TARGET_TP}:LRA=${TARGET_LRA}:measured_I=${measured_I}:measured_TP=${measured_TP}:measured_LRA=${measured_LRA}:measured_thresh=${measured_thresh}:offset=${target_offset}:linear=true:print_format=summary" \
    -c:v copy \
    -c:a aac -b:a 192k -ar 48000 \
    -movflags +faststart \
    -y "$tmp_output" \
    -loglevel warning -stats 2>&1; then

    # Verify the output file is valid and non-empty
    if [[ -f "$tmp_output" ]] && [[ $(stat -f%z "$tmp_output" 2>/dev/null || stat -c%s "$tmp_output" 2>/dev/null || echo 0) -gt 1000 ]]; then
      # Replace original with normalized version
      mv -f "$tmp_output" "$video_path"
      echo "  [DONE] ${video_file} normalized successfully."
      ((SUCCESS_COUNT++)) || true
    else
      echo "  [ERROR] Output file is invalid or too small. Keeping original."
      rm -f "$tmp_output"
      ((FAIL_COUNT++)) || true
    fi
  else
    echo "  [ERROR] ffmpeg failed for ${video_file}. Keeping original."
    rm -f "$tmp_output"
    ((FAIL_COUNT++)) || true
  fi

  echo ""
done

# =============================================================================
# Summary
# =============================================================================
echo "=== Normalization Complete ==="
echo "  Normalized: ${SUCCESS_COUNT}"
echo "  Skipped:    ${SKIP_COUNT} (already at target)"
echo "  Failed:     ${FAIL_COUNT}"
echo ""

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "WARNING: ${FAIL_COUNT} file(s) failed to normalize."
  exit 1
fi

echo "Done."