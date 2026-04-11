#!/usr/bin/env bash
#
# compile_video_tour.sh — Compile individual video tour segments into a single
# cohesive video tour with transitions and section title cards.
#
# Usage:
#   ./compile_video_tour.sh
#
# Prerequisites:
#   - ffmpeg (with libx264 and aac support)
#   - Video files listed in files.txt must exist in the current directory
#
# Output:
#   - video_tour.mp4
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
FILES_LIST="files.txt"
OUTPUT_FILE="video_tour.mp4"
TEMP_DIR=".tour_build"
TITLE_DURATION=3          # seconds for each section title card
FADE_DURATION=1           # seconds for crossfade transitions
FONT_SIZE=64
TITLE_FONT_SIZE=84
SUBTITLE_FONT_SIZE=42
BG_COLOR="black"
TEXT_COLOR="white"
ACCENT_COLOR="#4FC3F7"

# Intro/outro durations
INTRO_DURATION=5
OUTRO_DURATION=5

# ---------------------------------------------------------------------------
# Section titles derived from filenames — human-friendly names
# ---------------------------------------------------------------------------
declare -A SECTION_TITLES
SECTION_TITLES=(
    ["Comic_Generator"]="Comic Generator"
    ["Filesystem"]="Filesystem Access"
    ["Install_Windows"]="Windows Installation"
    ["Philosophical_Calculator"]="Philosophical Calculator"
    ["Plugin_Install"]="Plugin Installation"
    ["Sys_Wizard"]="System Wizard"
    ["WebApp_Factory"]="Web App Factory"
)

declare -A SECTION_SUBTITLES
SECTION_SUBTITLES=(
    ["Comic_Generator"]="AI-Powered Comic Book Creation"
    ["Filesystem"]="Session File System & Git Integration"
    ["Install_Windows"]="Getting Started on Windows"
    ["Philosophical_Calculator"]="Multi-Perspective Analysis & Illustration"
    ["Plugin_Install"]="Extending with Plugins"
    ["Sys_Wizard"]="Guided System Configuration"
    ["WebApp_Factory"]="Generate Full Web Applications"
)

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log()  { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
err()  { printf '[ERROR] %s\n' "$*" >&2; exit 1; }

cleanup() {
    if [[ -d "$TEMP_DIR" ]]; then
        log "Cleaning up temporary files..."
        rm -rf "$TEMP_DIR"
    fi
}
trap cleanup EXIT

require_cmd() {
    command -v "$1" &>/dev/null || err "'$1' is not installed. Please install it first."
}

# ---------------------------------------------------------------------------
# Preflight checks
# ---------------------------------------------------------------------------
require_cmd ffmpeg

[[ -f "$FILES_LIST" ]] || err "File list '$FILES_LIST' not found."

# Read video files, skip blanks and comments
VIDEOS=()
while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"          # strip inline comments
    line="$(echo "$line" | xargs)"  # trim whitespace
    [[ -z "$line" ]] && continue
    VIDEOS+=("$line")
done < "$FILES_LIST"

[[ ${#VIDEOS[@]} -gt 0 ]] || err "No video files found in '$FILES_LIST'."

# Verify all source videos exist
VALID_VIDEOS=()
for v in "${VIDEOS[@]}"; do
    if [[ -f "$v" ]]; then
        VALID_VIDEOS+=("$v")
    else
        warn "Video not found: $v — skipping"
    fi
done

[[ ${#VALID_VIDEOS[@]} -gt 0 ]] || err "No valid video files found. Aborting."

log "Found ${#VALID_VIDEOS[@]} video(s) to compile."

# ---------------------------------------------------------------------------
# Detect resolution from first video to normalize all segments
# ---------------------------------------------------------------------------
PROBE_WIDTH=$(ffprobe -v error -select_streams v:0 \
    -show_entries stream=width -of csv=p=0 "${VALID_VIDEOS[0]}" 2>/dev/null | head -1)
PROBE_HEIGHT=$(ffprobe -v error -select_streams v:0 \
    -show_entries stream=height -of csv=p=0 "${VALID_VIDEOS[0]}" 2>/dev/null | head -1)

# Default to 1920x1080 if probe fails
TARGET_W="${PROBE_WIDTH:-1920}"
TARGET_H="${PROBE_HEIGHT:-1080}"
TARGET_FPS=30

log "Target resolution: ${TARGET_W}x${TARGET_H} @ ${TARGET_FPS}fps"

# ---------------------------------------------------------------------------
# Create temp directory
# ---------------------------------------------------------------------------
mkdir -p "$TEMP_DIR"

# ---------------------------------------------------------------------------
# Generate a title card video (solid background + centered text)
#   generate_title_card <output_path> <duration> <main_text> [<subtitle_text>]
# ---------------------------------------------------------------------------
generate_title_card() {
    local out="$1"
    local dur="$2"
    local main_text="$3"
    local sub_text="${4:-}"

    local drawtext_filter=""

    # Main title — centered
    drawtext_filter+="drawtext=text='${main_text}'"
    drawtext_filter+=":fontsize=${FONT_SIZE}"
    drawtext_filter+=":fontcolor=${TEXT_COLOR}"
    drawtext_filter+=":x=(w-text_w)/2"
    if [[ -n "$sub_text" ]]; then
        drawtext_filter+=":y=(h-text_h)/2-40"
    else
        drawtext_filter+=":y=(h-text_h)/2"
    fi
    # Fade text in
    drawtext_filter+=":alpha='if(lt(t,0.5),t/0.5,1)'"

    # Subtitle line
    if [[ -n "$sub_text" ]]; then
        drawtext_filter+=",drawtext=text='${sub_text}'"
        drawtext_filter+=":fontsize=${SUBTITLE_FONT_SIZE}"
        drawtext_filter+=":fontcolor=${ACCENT_COLOR}"
        drawtext_filter+=":x=(w-text_w)/2"
        drawtext_filter+=":y=(h/2)+30"
        drawtext_filter+=":alpha='if(lt(t,0.8),t/0.8,1)'"
    fi

    # Add a subtle horizontal accent line
    drawtext_filter+=",drawbox=x=(w/4):y=(h/2)-2:w=(w/2):h=2:color=${ACCENT_COLOR}@0.5:t=fill"

    ffmpeg -y -f lavfi \
        -i "color=c=${BG_COLOR}:s=${TARGET_W}x${TARGET_H}:d=${dur}:r=${TARGET_FPS}" \
        -f lavfi -i "anullsrc=r=44100:cl=stereo" \
        -vf "${drawtext_filter}" \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -c:a aac -b:a 128k \
        -t "$dur" -shortest \
        "$out" 2>/dev/null

    log "Generated title card: $out"
}

# ---------------------------------------------------------------------------
# Normalize a video segment to consistent format
#   normalize_video <input> <output>
# ---------------------------------------------------------------------------
normalize_video() {
    local input="$1"
    local output="$2"

    ffmpeg -y -i "$input" \
        -vf "scale=${TARGET_W}:${TARGET_H}:force_original_aspect_ratio=decrease,pad=${TARGET_W}:${TARGET_H}:(ow-iw)/2:(oh-ih)/2:color=black,fps=${TARGET_FPS},format=yuv420p" \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -c:a aac -b:a 128k -ar 44100 -ac 2 \
        "$output" 2>/dev/null

    log "Normalized: $(basename "$input") -> $(basename "$output")"
}

# ---------------------------------------------------------------------------
# Apply fade-in / fade-out to a segment
#   apply_fades <input> <output> <fade_in_dur> <fade_out_dur>
# ---------------------------------------------------------------------------
apply_fades() {
    local input="$1"
    local output="$2"
    local fin_dur="$3"
    local fout_dur="$4"

    # Get duration of the input
    local total_dur
    total_dur=$(ffprobe -v error -show_entries format=duration \
        -of csv=p=0 "$input" 2>/dev/null | head -1)

    # Calculate fade-out start time
    local fout_start
    fout_start=$(echo "$total_dur - $fout_dur" | bc -l 2>/dev/null || echo "$total_dur")

    ffmpeg -y -i "$input" \
        -vf "fade=t=in:st=0:d=${fin_dur},fade=t=out:st=${fout_start}:d=${fout_dur}" \
        -af "afade=t=in:st=0:d=${fin_dur},afade=t=out:st=${fout_start}:d=${fout_dur}" \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -c:a aac -b:a 128k \
        "$output" 2>/dev/null

    log "Applied fades: $(basename "$output")"
}

# ---------------------------------------------------------------------------
# Build all segments
# ---------------------------------------------------------------------------
SEGMENT_INDEX=0
CONCAT_LIST="$TEMP_DIR/concat_list.txt"
> "$CONCAT_LIST"

# --- Intro title card ---
log "Generating intro title card..."
INTRO_FILE="$TEMP_DIR/000_intro.mp4"
generate_title_card "$INTRO_FILE" "$INTRO_DURATION" "Cognotic Desktop" "Video Tour"
apply_fades "$INTRO_FILE" "$TEMP_DIR/000_intro_faded.mp4" "$FADE_DURATION" "$FADE_DURATION"
echo "file '000_intro_faded.mp4'" >> "$CONCAT_LIST"
SEGMENT_INDEX=1

# --- Process each video ---
for video in "${VALID_VIDEOS[@]}"; do
    basename_noext="${video%.mp4}"

    # Determine section title
    title="${SECTION_TITLES[$basename_noext]:-$basename_noext}"
    subtitle="${SECTION_SUBTITLES[$basename_noext]:-}"

    padded_idx=$(printf "%03d" "$SEGMENT_INDEX")

    # Generate section title card
    TITLE_CARD="$TEMP_DIR/${padded_idx}_a_title.mp4"
    log "Generating section title: $title"
    generate_title_card "$TITLE_CARD" "$TITLE_DURATION" "$title" "$subtitle"
    apply_fades "$TITLE_CARD" "$TEMP_DIR/${padded_idx}_a_title_faded.mp4" "$FADE_DURATION" "$FADE_DURATION"
    echo "file '${padded_idx}_a_title_faded.mp4'" >> "$CONCAT_LIST"

    # Normalize the video segment
    NORM_VIDEO="$TEMP_DIR/${padded_idx}_b_video.mp4"
    log "Normalizing: $video"
    normalize_video "$video" "$NORM_VIDEO"
    apply_fades "$NORM_VIDEO" "$TEMP_DIR/${padded_idx}_b_video_faded.mp4" "$FADE_DURATION" "$FADE_DURATION"
    echo "file '${padded_idx}_b_video_faded.mp4'" >> "$CONCAT_LIST"

    SEGMENT_INDEX=$((SEGMENT_INDEX + 1))
done

# --- Outro title card ---
log "Generating outro title card..."
padded_idx=$(printf "%03d" "$SEGMENT_INDEX")
OUTRO_FILE="$TEMP_DIR/${padded_idx}_outro.mp4"
generate_title_card "$OUTRO_FILE" "$OUTRO_DURATION" "Thank You" "Explore more at cognotic.dev"
apply_fades "$OUTRO_FILE" "$TEMP_DIR/${padded_idx}_outro_faded.mp4" "$FADE_DURATION" "$FADE_DURATION"
echo "file '${padded_idx}_outro_faded.mp4'" >> "$CONCAT_LIST"

# ---------------------------------------------------------------------------
# Concatenate all segments
# ---------------------------------------------------------------------------
log "Concatenating ${SEGMENT_INDEX} sections into $OUTPUT_FILE..."

ffmpeg -y -f concat -safe 0 \
    -i "$CONCAT_LIST" \
    -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p \
    -c:a aac -b:a 192k \
    -movflags +faststart \
    "$OUTPUT_FILE" 2>/dev/null

if [[ -f "$OUTPUT_FILE" ]]; then
    FINAL_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
    FINAL_DUR=$(ffprobe -v error -show_entries format=duration \
        -of csv=p=0 "$OUTPUT_FILE" 2>/dev/null | head -1)
    log "=========================================="
    log "Video tour compiled successfully!"
    log "  Output:   $OUTPUT_FILE"
    log "  Size:     $FINAL_SIZE"
    log "  Duration: ${FINAL_DUR}s"
    log "=========================================="
else
    err "Failed to create $OUTPUT_FILE"
fi