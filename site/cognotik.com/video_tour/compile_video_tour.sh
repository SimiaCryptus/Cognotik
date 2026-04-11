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
EDIT_DIR="edit"
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

# Font configuration — find a usable font on the system
FONT_FILE=""
for candidate in \
     "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" \
     "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" \
     "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf" \
     "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans-Bold.ttf" \
     "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf" \
     "/System/Library/Fonts/Helvetica.ttc" \
     "C\\:/Windows/Fonts/arial.ttf"; do
     if [[ -f "$candidate" ]]; then
         FONT_FILE="$candidate"
         break
     fi
done


# Intro/outro durations
INTRO_DURATION=5
OUTRO_DURATION=5

# ---------------------------------------------------------------------------
# Ordered section list — controls the sequence of the compiled tour.
# Derived from the markdown transcripts; progresses from installation
# through features and applications, ending with the most visual demo.
# ---------------------------------------------------------------------------
SECTION_ORDER=(
     "Install_Windows"
     "Plugin_Install"
     "Filesystem"
     "Sys_Wizard"
     "WebApp_Factory"
     "Philosophical_Calculator"
     "Comic_Generator"
)

# ---------------------------------------------------------------------------
# Section titles and subtitles — human-friendly names derived from transcripts
# ---------------------------------------------------------------------------
declare -A SECTION_TITLES
SECTION_TITLES=(
     ["Install_Windows"]="Installing Cognotic Desktop"
     ["Plugin_Install"]="Installing a Plugin"
     ["Filesystem"]="Filesystem Access"
     ["Sys_Wizard"]="The System Wizard"
     ["WebApp_Factory"]="Web App Factory"
     ["Philosophical_Calculator"]="The Philosophical Calculator"
     ["Comic_Generator"]="Comic Generator"
)

declare -A SECTION_SUBTITLES
SECTION_SUBTITLES=(
     ["Install_Windows"]="Getting Started on Windows"
     ["Plugin_Install"]="Extending Cognotic with Plugins"
     ["Filesystem"]="Session File System & Git Integration"
     ["Sys_Wizard"]="AI-Powered Shell Script Generation"
     ["WebApp_Factory"]="Generate Full Web Applications"
     ["Philosophical_Calculator"]="Multi-Perspective Analysis & Illustration"
     ["Comic_Generator"]="AI-Powered Comic Book Creation"
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

[[ -d "$EDIT_DIR" ]] || err "Edit directory '$EDIT_DIR' not found."



# Discover video files from the edit directory, respecting SECTION_ORDER
# Videos listed in SECTION_ORDER are placed first (in that order), followed
# by any remaining videos found in the edit directory (alphabetically).
VALID_VIDEOS=()
SEEN_BASENAMES=()

for section in "${SECTION_ORDER[@]}"; do
     candidate="$EDIT_DIR/${section}.mp4"
     if [[ -f "$candidate" ]]; then
         VALID_VIDEOS+=("$candidate")
         SEEN_BASENAMES+=("${section}.mp4")
     else
         warn "Ordered section video not found: $candidate — skipping"
     fi
done

# Append any videos not already covered by SECTION_ORDER
for f in "$EDIT_DIR"/*.mp4; do
     [[ -e "$f" ]] || continue
     fname="$(basename "$f")"
     already_seen=false
     for seen in "${SEEN_BASENAMES[@]}"; do
         if [[ "$seen" == "$fname" ]]; then
             already_seen=true
             break
         fi
     done
     if [[ "$already_seen" == false ]]; then
         VALID_VIDEOS+=("$f")
         warn "Video '${fname}' not in SECTION_ORDER — appending at end"
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
     local font_opt=""
     if [[ -n "$FONT_FILE" ]]; then
         font_opt=":fontfile='${FONT_FILE}'"
     fi

    # Main title — centered
    drawtext_filter+="drawtext=text='${main_text}'"
     drawtext_filter+="${font_opt}"
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
         drawtext_filter+="${font_opt}"
        drawtext_filter+=":fontsize=${SUBTITLE_FONT_SIZE}"
        drawtext_filter+=":fontcolor=${ACCENT_COLOR}"
        drawtext_filter+=":x=(w-text_w)/2"
        drawtext_filter+=":y=(h/2)+30"
        drawtext_filter+=":alpha='if(lt(t,0.8),t/0.8,1)'"
    fi

     # Add a subtle horizontal accent line
     local box_x=$(( TARGET_W / 4 ))
     local box_y=$(( TARGET_H / 2 - 2 ))
     local box_w=$(( TARGET_W / 2 ))
     drawtext_filter+=",drawbox=x=${box_x}:y=${box_y}:w=${box_w}:h=2:color=${ACCENT_COLOR}@0.5:t=fill"

    ffmpeg -y -f lavfi \
        -i "color=c=${BG_COLOR}:s=${TARGET_W}x${TARGET_H}:d=${dur}:r=${TARGET_FPS}" \
        -f lavfi -i "anullsrc=r=44100:cl=stereo" \
        -vf "${drawtext_filter}" \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -c:a aac -b:a 128k \
         -t "$dur" -shortest "$out"

     if [[ ! -f "$out" ]]; then
         err "Failed to generate title card: $out"
     fi
     log "Generated title card: $(basename "$out")"
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
         "$output"

     if [[ ! -f "$output" ]]; then
         err "Failed to normalize: $(basename "$input")"
     fi
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
     fout_start=$(python3 -c "print(${total_dur} - ${fout_dur})" 2>/dev/null \
         || echo "$total_dur - $fout_dur" | bc -l 2>/dev/null \
         || echo "$total_dur")

    ffmpeg -y -i "$input" \
        -vf "fade=t=in:st=0:d=${fin_dur},fade=t=out:st=${fout_start}:d=${fout_dur}" \
        -af "afade=t=in:st=0:d=${fin_dur},afade=t=out:st=${fout_start}:d=${fout_dur}" \
        -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p \
        -c:a aac -b:a 128k \
         "$output"

     if [[ ! -f "$output" ]]; then
         err "Failed to apply fades: $(basename "$input")"
     fi
     log "Applied fades: $(basename "$output")"
}

# Log font status
if [[ -n "$FONT_FILE" ]]; then
     log "Using font: $FONT_FILE"
else
     warn "No font file found — drawtext may fail. Install fonts (e.g., fonts-dejavu-core)."
fi

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
     basename_noext="$(basename "${video}" .mp4)"

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
generate_title_card "$OUTRO_FILE" "$OUTRO_DURATION" "Thank You" "Explore more at cognotic.com"
apply_fades "$OUTRO_FILE" "$TEMP_DIR/${padded_idx}_outro_faded.mp4" "$FADE_DURATION" "$FADE_DURATION"
echo "file '${padded_idx}_outro_faded.mp4'" >> "$CONCAT_LIST"

# ---------------------------------------------------------------------------
# Concatenate all segments
# ---------------------------------------------------------------------------
log "Concatenating $((SEGMENT_INDEX + 1)) segments (intro + ${#VALID_VIDEOS[@]} sections + outro) into $OUTPUT_FILE..."

ffmpeg -y -f concat -safe 0 \
    -i "$CONCAT_LIST" \
    -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p \
    -c:a aac -b:a 192k \
    -movflags +faststart \
     "$OUTPUT_FILE"

if [[ -f "$OUTPUT_FILE" ]]; then
    FINAL_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
    FINAL_DUR=$(ffprobe -v error -show_entries format=duration \
        -of csv=p=0 "$OUTPUT_FILE" 2>/dev/null | head -1)
    log "=========================================="
    log "Video tour compiled successfully!"
    log "  Output:   $OUTPUT_FILE"
    log "  Size:     $FINAL_SIZE"
    log "  Duration: ${FINAL_DUR}s"
     log "  Sections: ${#VALID_VIDEOS[@]}"
    log "=========================================="
else
    err "Failed to create $OUTPUT_FILE"
fi