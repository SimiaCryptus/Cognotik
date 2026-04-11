#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# segment_video.sh
#
# Performs video-based scene segmentation and annotation using FFmpeg and OpenCV.
#
# Detects scene boundaries using multiple strategies:
#   - content   : Histogram + structural similarity based content change detection
#   - threshold  : Simple threshold on frame difference (fast)
#   - adaptive   : Adaptive threshold with local statistics (balanced)
#
# For each detected scene, generates:
#   - A representative thumbnail frame
#   - Motion/activity metrics
#   - Brightness and color statistics
#   - Scene transition type classification (cut, fade, dissolve)
#
# Outputs per video:
#   - <name>.scenes.json       : Machine-readable scene list with metadata
#   - <name>.scenes.txt        : Human-readable scene summary
#   - <name>.scenes.srt        : SRT-style scene markers for editors
#   - <name>.thumbnails/       : Thumbnail images for each scene
#   - <name>.annotated.mp4     : (optional) Video with scene boundary overlays
#
# Usage:
#   ./scripts/segment_video.sh [subdir]
#
# Environment variables:
#   DETECTION_METHOD      - "content", "threshold", or "adaptive" (default: content)
#   CONTENT_THRESHOLD     - Content detection sensitivity 0.0-1.0 (default: 0.3)
#   DIFF_THRESHOLD        - Frame difference threshold 0-255 (default: 30)
#   MIN_SCENE_SEC         - Minimum scene duration in seconds (default: 2.0)
#   MAX_SCENE_SEC         - Maximum scene duration before forced split (default: 300)
#   ANALYSIS_FPS          - FPS for analysis (lower = faster) (default: 2)
#   THUMBNAIL_WIDTH       - Thumbnail width in pixels (default: 640)
#   GENERATE_ANNOTATED    - Generate annotated video: true/false (default: false)
#   ANNOTATION_STYLE      - Overlay style: "minimal", "full" (default: full)
#   EDIT_DIR              - Override input directory
#   SCENE_DIR             - Override output directory
# =============================================================================

SUBDIR_NAME="${1:-source}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EDIT_DIR="${EDIT_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}}"
SCENE_DIR="${SCENE_DIR:-${SCRIPT_DIR}/${SUBDIR_NAME}/scenes}"
FRAMES_TMP_DIR="${SCENE_DIR}/.frames_tmp"

# Detection configuration
DETECTION_METHOD="${DETECTION_METHOD:-content}"
CONTENT_THRESHOLD="${CONTENT_THRESHOLD:-0.3}"
DIFF_THRESHOLD="${DIFF_THRESHOLD:-30}"
MIN_SCENE_SEC="${MIN_SCENE_SEC:-2.0}"
MAX_SCENE_SEC="${MAX_SCENE_SEC:-300}"
ANALYSIS_FPS="${ANALYSIS_FPS:-2}"
THUMBNAIL_WIDTH="${THUMBNAIL_WIDTH:-640}"
GENERATE_ANNOTATED="${GENERATE_ANNOTATED:-false}"
ANNOTATION_STYLE="${ANNOTATION_STYLE:-full}"

# =============================================================================
# Validation
# =============================================================================
if ! command -v ffmpeg &>/dev/null; then
  echo "ERROR: 'ffmpeg' is not installed. Run ./scripts/install_deps.sh first."
  exit 1
fi

if ! command -v ffprobe &>/dev/null; then
  echo "ERROR: 'ffprobe' is not installed. It is typically bundled with ffmpeg."
  exit 1
fi

if ! command -v python3 &>/dev/null; then
  echo "ERROR: 'python3' is not installed."
  exit 1
fi

if [[ ! -d "$EDIT_DIR" ]]; then
  echo "ERROR: Input directory not found: ${EDIT_DIR}"
  exit 1
fi

# Check Python dependencies
echo "Checking Python dependencies for video segmentation..."
python3 -c "
import importlib, sys
required = ['numpy', 'cv2']
missing = []
for mod in required:
    try:
        importlib.import_module(mod)
    except ImportError:
        missing.append(mod)

if missing:
    pkg_names = {'cv2': 'opencv-python-headless'}
    pkgs = [pkg_names.get(m, m) for m in missing]
    print(f'ERROR: Missing Python packages: {\" \".join(missing)}', file=sys.stderr)
    print(f'Install with: pip install {\" \".join(pkgs)}', file=sys.stderr)
    print(f'Or run: ./scripts/install_video_deps.sh', file=sys.stderr)
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

mkdir -p "$SCENE_DIR" "$FRAMES_TMP_DIR"

echo "=== Video Scene Segmentation & Annotation ==="
echo "  Detection Method:   ${DETECTION_METHOD}"
echo "  Input Dir:          ${EDIT_DIR}"
echo "  Output Dir:         ${SCENE_DIR}"
echo "  Analysis FPS:       ${ANALYSIS_FPS}"
echo "  Min Scene Duration: ${MIN_SCENE_SEC}s"
echo "  Max Scene Duration: ${MAX_SCENE_SEC}s"
echo "  Thumbnail Width:    ${THUMBNAIL_WIDTH}px"
echo "  Generate Annotated: ${GENERATE_ANNOTATED}"
if [[ "$DETECTION_METHOD" == "content" ]]; then
  echo "  Content Threshold:  ${CONTENT_THRESHOLD}"
elif [[ "$DETECTION_METHOD" == "threshold" || "$DETECTION_METHOD" == "adaptive" ]]; then
  echo "  Diff Threshold:     ${DIFF_THRESHOLD}"
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

  json_out="${SCENE_DIR}/${base_name}.scenes.json"
  txt_out="${SCENE_DIR}/${base_name}.scenes.txt"
  srt_out="${SCENE_DIR}/${base_name}.scenes.srt"
  thumb_dir="${SCENE_DIR}/${base_name}.thumbnails"
  annotated_out="${SCENE_DIR}/${base_name}.annotated.mp4"

  mkdir -p "$thumb_dir"

  # Step 1: Get video metadata via ffprobe
  echo "  [PROBE] Reading video metadata..."
  VIDEO_DURATION=$(ffprobe -v error -show_entries format=duration \
    -of default=noprint_wrappers=1:nokey=1 "$video_path" 2>/dev/null || echo "0")
  VIDEO_WIDTH=$(ffprobe -v error -select_streams v:0 -show_entries stream=width \
    -of default=noprint_wrappers=1:nokey=1 "$video_path" 2>/dev/null || echo "0")
  VIDEO_HEIGHT=$(ffprobe -v error -select_streams v:0 -show_entries stream=height \
    -of default=noprint_wrappers=1:nokey=1 "$video_path" 2>/dev/null || echo "0")
  VIDEO_FPS=$(ffprobe -v error -select_streams v:0 -show_entries stream=r_frame_rate \
    -of default=noprint_wrappers=1:nokey=1 "$video_path" 2>/dev/null || echo "30/1")

  echo "  [INFO] Duration: ${VIDEO_DURATION}s, Resolution: ${VIDEO_WIDTH}x${VIDEO_HEIGHT}, FPS: ${VIDEO_FPS}"

  # Step 2: Run scene detection and annotation via Python + OpenCV
  echo "  [DETECT] Running ${DETECTION_METHOD} scene detection..."

   python3 "$(dirname "${BASH_SOURCE[0]}")/segment_video.py" \
     "$video_path" "$json_out" "$txt_out" "$srt_out" "$thumb_dir" "$annotated_out" \
     "$DETECTION_METHOD" "$CONTENT_THRESHOLD" "$DIFF_THRESHOLD" \
     "$MIN_SCENE_SEC" "$MAX_SCENE_SEC" "$ANALYSIS_FPS" "$THUMBNAIL_WIDTH" \
     "$GENERATE_ANNOTATED" "$ANNOTATION_STYLE" "$VIDEO_DURATION"

  if [[ $? -eq 0 ]]; then
    echo "  [DONE] ${video_file} segmented successfully."
    ((SUCCESS_COUNT++)) || true
  else
    echo "  [ERROR] Scene segmentation failed for ${video_file}."
    ((FAIL_COUNT++)) || true
  fi

  echo ""
done

# =============================================================================
# Cleanup temporary frame files
# =============================================================================
echo "--- Cleaning up temporary files ---"
rm -rf "$FRAMES_TMP_DIR"
echo ""

# =============================================================================
# Summary
# =============================================================================
echo "=== Video Scene Segmentation Complete ==="
echo "  Succeeded: ${SUCCESS_COUNT}"
echo "  Failed:    ${FAIL_COUNT}"
echo ""
echo "Output files in: ${SCENE_DIR}/"
for video_path in "${VIDEO_FILES[@]}"; do
  base_name="$(basename "${video_path%.*}")"
  json_out="${SCENE_DIR}/${base_name}.scenes.json"
  txt_out="${SCENE_DIR}/${base_name}.scenes.txt"
  srt_out="${SCENE_DIR}/${base_name}.scenes.srt"
  thumb_dir="${SCENE_DIR}/${base_name}.thumbnails"
  annotated_out="${SCENE_DIR}/${base_name}.annotated.mp4"
  if [[ -f "$json_out" ]]; then
    echo "  [JSON]   ${json_out}"
    echo "  [TXT]    ${txt_out}"
    echo "  [SRT]    ${srt_out}"
    echo "  [THUMBS] ${thumb_dir}/"
    if [[ -f "$annotated_out" ]]; then
      echo "  [VIDEO]  ${annotated_out}"
    fi
  fi
done
echo ""

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo "WARNING: ${FAIL_COUNT} file(s) failed to segment."
  exit 1
fi

echo "Done."