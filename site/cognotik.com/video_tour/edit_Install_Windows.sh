#!/usr/bin/env bash
# Edit script for Install_Windows.mp4
# Generated from transcripts/Install_Windows.srt
#
# Edits:
#   1. Trim false starts/repeated intros at the beginning (00:00-00:28)
#   2. Remove stuttered "install it like any install" overlap (~01:09-01:12)
#   3. Trim long silent pause during installation (~01:15-01:29)
#   4. Remove garbled "Weak passwords in my for" (~02:23-02:27)
#   5. Trim long pause after "You say yes" (~03:02-03:16)
#   6. Remove speaker's explicit "Cut this part" (~03:40-03:46)
#   7. Remove aside "Don't save that in the password manager" (~03:51-03:57)

set -euo pipefail

INPUT="source/Install_Windows.mp4"
OUTPUT="edit/Install_Windows.mp4"
SEGMENTS_DIR="$(mktemp -d)"
CONCAT_FILE="${SEGMENTS_DIR}/concat.txt"

mkdir -p edit

# Define the segments to KEEP (start, end)
# Segment 1: Clean intro starts at ~00:28.4 through download/save
# Segment 2: Keep file instructions through install start
# Segment 3: Skip long install pause, pick up at "there we go" briefly then config
# Segment 4: Configuration and registration flow, skip garbled "weak passwords in my for"
# Segment 5: After garbled part, continue with weak password explanation
# Segment 6: Skip long pause after "You say yes", pick up at web UI
# Segment 7: Settings and API key, skip "Cut this part"
# Segment 8: After cut part, skip "Don't save that in the password manager"
# Segment 9: Final test and conclusion

SEGMENTS=(
  # Seg 1: Clean intro - "install Cognotic desktop on Windows" through saving file
  "00:00:28.429,00:01:09.489"
  # Seg 2: "Install it like any desktop application" (skip stutter at start)
  "00:01:11.500,00:01:16.000"
  # Seg 3: Skip long install wait, resume at "there we go" + config section
  "00:01:28.500,00:02:23.000"
  # Seg 4: Skip garbled "Weak passwords in my for", resume at clean explanation
  "00:02:27.618,00:03:02.520"
  # Seg 5: Skip long pause, resume at "now we have access to the web UI"
  "00:03:02.520,00:03:04.000"
  # Seg 6: "Next we need to go to settings" through API key section, skip "Cut this part"
  "00:03:16.199,00:03:40.069"
  # Seg 7: "enter an API key, save settings" - skip "Don't save that in the password manager"
  "00:03:46.270,00:03:51.889"
  # Seg 8: Final testing and conclusion
  "00:03:57.270,00:04:18.889"
)

echo "Extracting segments..."

for i in "${!SEGMENTS[@]}"; do
  IFS=',' read -r START END <<< "${SEGMENTS[$i]}"
  SEGMENT_FILE="${SEGMENTS_DIR}/seg_$(printf '%03d' "$i").mp4"

  ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss "$START" -to "$END" \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    -movflags +faststart \
    "$SEGMENT_FILE"

  echo "file '${SEGMENT_FILE}'" >> "$CONCAT_FILE"
done

echo "Concatenating segments..."

ffmpeg -y -hide_banner -loglevel warning \
  -f concat -safe 0 \
  -i "$CONCAT_FILE" \
  -c copy \
  -movflags +faststart \
  "$OUTPUT"

echo "Cleaning up temporary files..."
rm -rf "$SEGMENTS_DIR"

echo "Done! Output saved to ${OUTPUT}"