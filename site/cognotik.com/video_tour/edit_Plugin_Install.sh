#!/usr/bin/env bash
# Edit script for Plugin_Install.mp4
# Generated from transcripts/Plugin_Install.srt
#
# Edits:
#   1. Trim ~6s of silence/dead air at the start (start at 00:00:05.5)
#   2. Remove long pause during file selection (00:00:24.5 to 00:00:25.5)
#   3. Remove pause after license acceptance (00:00:38.0 to 00:00:39.0)
#   4. Remove long pause before closing statement (00:01:13.0 to 00:01:19.0)
#   5. Trim end after final word at ~00:01:27.5
#
# Strategy: Use ffmpeg filter_complex to concatenate segments with pauses removed

set -euo pipefail

INPUT="source/Plugin_Install.mp4"
OUTPUT="edit/Plugin_Install.mp4"

mkdir -p edit

# Define segments to keep (trimming dead air and long pauses):
#   Segment 0: 00:00:05.5 to 00:00:24.5  (intro through "Selecting the plug-in jar")
#   Segment 1: 00:00:25.5 to 00:00:38.0  ("You may want to check auto load..." through "accept the license agreement, which is OK")
#   Segment 2: 00:00:39.0 to 00:01:13.0  ("and this plugin also requires..." through "other demo videos.")
#   Segment 3: 00:01:19.0 to 00:01:27.5  ("you can see, plug-in installation is simple, and adds working applications.")

ffmpeg -y -i "$INPUT" -filter_complex \
"[0:v]split=4[v0][v1][v2][v3]; \
 [0:a]asplit=4[a0][a1][a2][a3]; \
 [v0]trim=start=5.5:end=24.5,setpts=PTS-STARTPTS[tv0]; \
 [a0]atrim=start=5.5:end=24.5,asetpts=PTS-STARTPTS[ta0]; \
 [v1]trim=start=25.5:end=38.0,setpts=PTS-STARTPTS[tv1]; \
 [a1]atrim=start=25.5:end=38.0,asetpts=PTS-STARTPTS[ta1]; \
 [v2]trim=start=39.0:end=73.0,setpts=PTS-STARTPTS[tv2]; \
 [a2]atrim=start=39.0:end=73.0,asetpts=PTS-STARTPTS[ta2]; \
 [v3]trim=start=79.0:end=87.5,setpts=PTS-STARTPTS[tv3]; \
 [a3]atrim=start=79.0:end=87.5,asetpts=PTS-STARTPTS[ta3]; \
 [tv0][ta0][tv1][ta1][tv2][ta2][tv3][ta3]concat=n=4:v=1:a=1[outv][outa]" \
-map "[outv]" -map "[outa]" \
-c:v libx264 -preset medium -crf 18 \
-c:a aac -b:a 192k \
"$OUTPUT"

echo "Edit complete: $OUTPUT"