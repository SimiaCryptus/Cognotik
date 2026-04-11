#!/usr/bin/env bash
# Edit script for Comic_Generator
# Removes long pauses during generation wait times and trims start/end
# Input: source/Comic_Generator.mp4
# Output: edit/Comic_Generator.mp4

set -euo pipefail

INPUT="source/Comic_Generator.mp4"
OUTPUT="edit/Comic_Generator.mp4"
TMPDIR=$(mktemp -d)

mkdir -p edit

# Segment plan:
# 1. 00:00:03.640 - 00:00:28.318  Intro, app description, entering prompt (before "Stop Interrupt")
# 2. 00:00:37.969 - 00:01:50.000  Model selection, explanation of comic serial concept, start monitoring
# 3. 00:02:28.860 - 00:02:58.000  Script sketch done, character generation explanation
# 4. 00:03:59.439 - 00:04:08.000  "Now we're on to actual page generation" + brief monitor note
# 5. 00:06:39.139 - 00:07:52.838  Final results: fourth wall joke, rendering, preview, outro

# Segment 1: Intro and prompt entry (before the "Stop Interrupt" false start)
ffmpeg -y -hide_banner -loglevel warning \
  -i "$INPUT" \
  -ss 00:00:03.640 -to 00:00:28.318 \
  -c:v libx264 -preset fast -crf 18 \
  -c:a aac -b:a 192k \
  -avoid_negative_ts make_zero \
  "${TMPDIR}/seg1.mp4"

# Segment 2: Model selection and comic serial explanation
ffmpeg -y -hide_banner -loglevel warning \
  -i "$INPUT" \
  -ss 00:00:37.969 -to 00:01:50.000 \
  -c:v libx264 -preset fast -crf 18 \
  -c:a aac -b:a 192k \
  -avoid_negative_ts make_zero \
  "${TMPDIR}/seg2.mp4"

# Segment 3: Script and character generation (trimming the long wait)
ffmpeg -y -hide_banner -loglevel warning \
  -i "$INPUT" \
  -ss 00:02:28.860 -to 00:02:58.000 \
  -c:v libx264 -preset fast -crf 18 \
  -c:a aac -b:a 192k \
  -avoid_negative_ts make_zero \
  "${TMPDIR}/seg3.mp4"

# Segment 4: Page generation begins (brief clip before long wait)
ffmpeg -y -hide_banner -loglevel warning \
  -i "$INPUT" \
  -ss 00:03:59.439 -to 00:04:08.000 \
  -c:v libx264 -preset fast -crf 18 \
  -c:a aac -b:a 192k \
  -avoid_negative_ts make_zero \
  "${TMPDIR}/seg4.mp4"

# Segment 5: Results, rendering, preview, and outro
ffmpeg -y -hide_banner -loglevel warning \
  -i "$INPUT" \
  -ss 00:06:39.139 -to 00:07:52.838 \
  -c:v libx264 -preset fast -crf 18 \
  -c:a aac -b:a 192k \
  -avoid_negative_ts make_zero \
  "${TMPDIR}/seg5.mp4"

# Create concat list
cat > "${TMPDIR}/concat.txt" <<EOF
file 'seg1.mp4'
file 'seg2.mp4'
file 'seg3.mp4'
file 'seg4.mp4'
file 'seg5.mp4'
EOF

# Concatenate all segments
ffmpeg -y -hide_banner -loglevel warning \
  -f concat -safe 0 \
  -i "${TMPDIR}/concat.txt" \
  -c copy \
  "$OUTPUT"

# Clean up
rm -rf "$TMPDIR"

echo "Edit complete: $OUTPUT"