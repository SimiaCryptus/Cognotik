#!/usr/bin/env bash
#
# edit_Filesystem.sh
# Auto-generated edit script for Filesystem.mp4
#
# Edits performed:
#   1. Remove "If you," false start at ~01:11.159-01:12.0
#   2. Remove filler "um" at ~01:14.799-01:15.2
#   3. Remove filler "Um," at ~01:58.168-01:58.6
#   4. Remove filler "uh," at ~01:47.150-01:47.6
#   5. Remove filler "um," at ~02:06.930-02:07.3
#   6. Remove filler "Um," at ~02:12.268-02:12.7
#
# Strategy: Use ffmpeg's select/aselect filters to skip over filler segments,
# then concatenate the remaining pieces.
#
# The filler segments are short (~0.3-0.5s) and are cut precisely based on
# SRT timestamps.

set -euo pipefail

INPUT="Filesystem.mp4"
OUTPUT="edit_Filesystem.mp4"
TRANSCRIPT_OUT="transcripts/Filesystem_edit.txt"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input file '$INPUT' not found." >&2
    exit 1
fi

mkdir -p transcripts

# Define segments to KEEP (gaps between these are the filler/false-start cuts)
# Format: start:end (in seconds)
#
# Analyzing the SRT for filler words and their approximate timestamps:
#
# Cut 1: "If you," false start at subtitle 16
#   "also view them as text files. If you, this is"
#   Subtitle 16: 01:11.159 -> 01:14.439
#   "If you," occupies roughly 01:11.159 to 01:12.200
#   Keep up to end of "text files." (~01:11.159) then skip to "this is" (~01:12.200)
#   Actually the "text files" ends around the boundary of sub 15/16.
#   Sub 15: 01:01.250 -> 01:06.150 "as HTML files that does a dynamic rendering. You can"
#   Sub 16: 01:06.150 -> 01:11.159 "also view them as text files. If you, this is"
#   "text files." ends ~01:09.5, "If you," is ~01:09.5-01:10.5
#   We'll cut 01:09.600 to 01:10.600
#
# Cut 2: "um" filler at subtitle 17
#   "handy if you want to view the markdown source, um"
#   Sub 17: 01:11.159 -> 01:14.439 (wait, after cut 1 adjustment)
#   Actually sub 17: 01:14.799 -> 01:17.918
#   "source, um" - the "um" is at the end, roughly 01:17.400-01:17.918
#   We'll cut 01:17.400 to 01:17.918
#
# Cut 3: "uh," filler at subtitle 24
#   "you're not aware, uh, Git is a version control system"
#   Sub 24: 01:43.849 -> 01:47.150
#   "uh," is roughly 01:44.800-01:45.400
#   We'll cut 01:44.800 to 01:45.400
#
# Cut 4: "Um," filler at subtitle 27
#   "changes and if needed rollback. Um, accessing"
#   Sub 27: 01:51.930 -> 01:58.168
#   "rollback." ends ~01:55.5, "Um," is ~01:55.500-01:56.200
#   We'll cut 01:55.500 to 01:56.200
#
# Cut 5: "um," filler at subtitle 29
#   "interface gives you a powerful, um, backdoor"
#   Sub 29: 02:01.930 -> 02:06.930
#   "um," is roughly 02:04.500-02:05.200
#   We'll cut 02:04.500 to 02:05.200
#
# Cut 6: "Um," filler at subtitle 30
#   "into the system functionality. Um, and provides"
#   Sub 30: 02:06.930 -> 02:12.268
#   "Um," is roughly 02:09.000-02:09.700
#   We'll cut 02:09.000 to 02:09.700

# Define the cut points (start_seconds end_seconds) - segments to REMOVE
CUTS=(
    "69.600 70.600"   # "If you," false start
    "77.400 77.918"   # "um" filler
    "104.800 105.400" # "uh," filler
    "115.500 116.200" # "Um," filler
    "124.500 125.200" # "um," filler
    "129.000 129.700" # "Um," filler
)

# Build the segment list for ffmpeg concat demuxer approach
# We'll use the trim filter to extract segments between cuts

TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# Calculate keep segments from the cuts
# Start from 0, and for each cut, keep [prev_end, cut_start], then set prev_end = cut_end
SEGMENT_FILE="$TEMP_DIR/segments.txt"
PREV_END="0"
SEG_IDX=0

for cut in "${CUTS[@]}"; do
    CUT_START=$(echo "$cut" | awk '{print $1}')
    CUT_END=$(echo "$cut" | awk '{print $2}')

    if [ "$(echo "$PREV_END < $CUT_START" | bc -l)" -eq 1 ]; then
        SEG_FILE="$TEMP_DIR/seg_${SEG_IDX}.mp4"
        echo "Extracting segment $SEG_IDX: ${PREV_END}s to ${CUT_START}s"
        ffmpeg -hide_banner -loglevel warning \
            -i "$INPUT" \
            -ss "$PREV_END" -to "$CUT_START" \
            -c:v libx264 -preset fast -crf 18 \
            -c:a aac -b:a 192k \
            -avoid_negative_ts make_zero \
            -y "$SEG_FILE"
        echo "file '$SEG_FILE'" >> "$SEGMENT_FILE"
        SEG_IDX=$((SEG_IDX + 1))
    fi

    PREV_END="$CUT_END"
done

# Get total duration of input
TOTAL_DURATION=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$INPUT")

# Add final segment from last cut end to end of video
if [ "$(echo "$PREV_END < $TOTAL_DURATION" | bc -l)" -eq 1 ]; then
    SEG_FILE="$TEMP_DIR/seg_${SEG_IDX}.mp4"
    echo "Extracting segment $SEG_IDX: ${PREV_END}s to end (${TOTAL_DURATION}s)"
    ffmpeg -hide_banner -loglevel warning \
        -i "$INPUT" \
        -ss "$PREV_END" -to "$TOTAL_DURATION" \
        -c:v libx264 -preset fast -crf 18 \
        -c:a aac -b:a 192k \
        -avoid_negative_ts make_zero \
        -y "$SEG_FILE"
    echo "file '$SEG_FILE'" >> "$SEGMENT_FILE"
fi

# Concatenate all segments
echo ""
echo "Concatenating $(( SEG_IDX + 1 )) segments into $OUTPUT..."
ffmpeg -hide_banner -loglevel warning \
    -f concat -safe 0 \
    -i "$SEGMENT_FILE" \
    -c copy \
    -y "$OUTPUT"

echo "Edit complete: $OUTPUT"

# Write out the edited transcript
cat > "$TRANSCRIPT_OUT" << 'TRANSCRIPT'
All sessions have a file system backing them that can be accessed. If you go to the URL that is here's a previous run, for example, that we generated a comic book from. If we open in a new tab that same URL but we remove the web page part so that we just access the root directory of that session. We can access the file system itself for that session. Now this comes with a number of interesting features. First of all, we can download the entire directory as a zip file. We can look at any markdown files, we can look at any HTML files and directly view them, of course, and we can also view markdown files. As either markdown, which this will cause download, or you can view the markdown files as HTML files that does a dynamic rendering. You can also view them as text files. This is handy if you want to view the markdown source, in a way that won't trigger a download for your browser. And you can also view markdown files as PDFs and that also does a dynamic rendering of the markdown file into PDF format. Additionally, the file system has built-in Git support, so you can view the current status, you can commit the directory. If you're not aware, Git is a version control system for file systems so that it's basically like a time machine so that you can track all of the file changes and if needed rollback. Accessing the root file system for any given session via this interface gives you a powerful backdoor into the system functionality. And provides a good level of hackability and transparency for any Cognotic applications. Finally, I'd like to point out that the physical location of the file system is shown here. So if you want, you can mount this with a development environment or you can open it in the file system explorer or whatever you want to do. I hope that functionality is useful to you.
TRANSCRIPT

echo "Edited transcript written to: $TRANSCRIPT_OUT"