#!/usr/bin/env bash
# Edit script for Sys_Wizard video
# Removes false starts, long pauses, and stuttered sections
#
# Segments to KEEP:
#   1. 00:00:09.839 - 00:01:06.308  (Clean intro through demo setup, skipping initial false start and long pause)
#   2. 00:01:10.638 - 00:01:45.250  (Goal saving through pipeline generation start)
#   3. 00:01:56.638 - 00:03:07.860  (Results and conclusion, skipping generation wait)
#
# Segments REMOVED:
#   - 00:00:00.000 - 00:00:09.839  (False start: "The SIS Wizard, the SIS Wizard app is.")
#   - 00:00:15.609 - 00:00:20.379  (Long ~5s pause)
#   - 00:01:06.308 - 00:01:10.638  (Filler: "That's. Right" + pause)
#   - 00:01:45.250 - 00:01:56.638  (Waiting for generation ~11s dead air)

set -euo pipefail

INPUT="source/Sys_Wizard.mp4"
OUTPUT_DIR="edit"
OUTPUT="$OUTPUT_DIR/Sys_Wizard.mp4"

mkdir -p "$OUTPUT_DIR"

ffmpeg -y -i "$INPUT" \
  -filter_complex "\
    [0:v]split=3[v1][v2][v3]; \
    [0:a]asplit=3[a1][a2][a3]; \
    [v1]trim=start=9.839:end=66.308,setpts=PTS-STARTPTS[v1t]; \
    [a1]atrim=start=9.839:end=66.308,asetpts=PTS-STARTPTS[a1t]; \
    [v2]trim=start=70.638:end=105.250,setpts=PTS-STARTPTS[v2t]; \
    [a2]atrim=start=70.638:end=105.250,asetpts=PTS-STARTPTS[a2t]; \
    [v3]trim=start=116.638:end=187.860,setpts=PTS-STARTPTS[v3t]; \
    [a3]atrim=start=116.638:end=187.860,asetpts=PTS-STARTPTS[a3t]; \
    [v1t][a1t][v2t][a2t][v3t][a3t]concat=n=3:v=1:a=1[outv][outa] \
  " \
  -map "[outv]" -map "[outa]" \
  -c:v libx264 -preset medium -crf 23 \
  -c:a aac -b:a 128k \
  "$OUTPUT"

echo "Edit complete: $OUTPUT"