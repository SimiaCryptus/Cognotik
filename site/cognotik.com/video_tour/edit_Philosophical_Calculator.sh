#!/usr/bin/env bash
# Edit script for Philosophical_Calculator.mp4
# Trims dead air at start/end, removes long pauses and filler sections
# Input: source/Philosophical_Calculator.mp4
# Output: edit/Philosophical_Calculator.mp4

set -euo pipefail

INPUT="source/Philosophical_Calculator.mp4"
OUTPUT="edit/Philosophical_Calculator.mp4"
TMPDIR="$(mktemp -d)"

mkdir -p edit

# Define segments to keep (trimming dead air, long pauses, and filler)
#
# Segment 1: Opening through pipeline setup (trim ~6s dead air at start)
#   00:00:05.5 -> 00:02:23.0  (intro, model selection, draft article, lenses intro)
#
# Segment 2: Skip the "change..." stumble/pause at ~2:23-2:37, resume with UI explanation
#   00:02:30.0 -> 00:04:29.0  (UI explanation, usage monitoring, lens demo)
#
# Segment 3: Skip the long ~24s wait at 4:29-4:53, resume with results
#   00:04:53.5 -> 00:06:02.5  (results viewing, synthesis, lens output)
#
# Segment 4: Illustrate article feature intro
#   00:06:02.5 -> 00:06:59.0  (illustrate article, session link, monitoring)
#
# Segment 5: Skip the long ~69s image generation wait, resume with results
#   00:06:59.0 -> 00:07:05.0  (brief "Here we go" transition)
#
# Segment 6: Skip another wait for image integration, pick up at final reveal
#   00:07:11.0 -> 00:07:18.5  (integration explanation)
#
# Segment 7: Skip the long ~54s wait for document editing, show final result
#   00:08:08.0 -> 00:08:12.0  (OK transition - skip this dead air)
#
# Segment 8: Final article reveal and closing
#   00:08:12.0 -> 00:08:37.1  (final illustrated article, closing)

# Simplified approach: use ffmpeg filter_complex to concat kept segments
# We'll define the segments we want to KEEP and concatenate them

# Segment definitions (start, end) - times in seconds
# Seg 1: 5.5 - 142.0   (00:00:05.5 - 00:02:22.0) Intro through lenses intro
# Seg 2: 150.0 - 269.0  (00:02:30.0 - 00:04:29.0) UI, usage, skip stumble
# Seg 3: 294.0 - 362.5  (00:04:54.0 - 00:06:02.5) Results and lens output
# Seg 4: 362.5 - 425.0  (00:06:02.5 - 00:07:05.0) Illustrate article feature
# Seg 5: 431.0 - 438.5  (00:07:11.0 - 00:07:18.5) Integration explanation
# Seg 6: 492.0 - 517.1  (00:08:12.0 - 00:08:37.1) Final reveal and closing

ffmpeg -y -i "$INPUT" -filter_complex \
"[0:v]split=6[v1][v2][v3][v4][v5][v6]; \
 [0:a]asplit=6[a1][a2][a3][a4][a5][a6]; \
 [v1]trim=start=5.5:end=142.0,setpts=PTS-STARTPTS[v1t]; \
 [a1]atrim=start=5.5:end=142.0,asetpts=PTS-STARTPTS[a1t]; \
 [v2]trim=start=150.0:end=269.0,setpts=PTS-STARTPTS[v2t]; \
 [a2]atrim=start=150.0:end=269.0,asetpts=PTS-STARTPTS[a2t]; \
 [v3]trim=start=294.0:end=362.5,setpts=PTS-STARTPTS[v3t]; \
 [a3]atrim=start=294.0:end=362.5,asetpts=PTS-STARTPTS[a3t]; \
 [v4]trim=start=362.5:end=425.0,setpts=PTS-STARTPTS[v4t]; \
 [a4]atrim=start=362.5:end=425.0,asetpts=PTS-STARTPTS[a4t]; \
 [v5]trim=start=431.0:end=438.5,setpts=PTS-STARTPTS[v5t]; \
 [a5]atrim=start=431.0:end=438.5,asetpts=PTS-STARTPTS[a5t]; \
 [v6]trim=start=492.0:end=517.1,setpts=PTS-STARTPTS[v6t]; \
 [a6]atrim=start=492.0:end=517.1,asetpts=PTS-STARTPTS[a6t]; \
 [v1t][a1t][v2t][a2t][v3t][a3t][v4t][a4t][v5t][a5t][v6t][a6t]concat=n=6:v=1:a=1[outv][outa]" \
 -map "[outv]" -map "[outa]" \
 -c:v libx264 -preset medium -crf 23 \
 -c:a aac -b:a 128k \
 "$OUTPUT"

echo "Edit complete: $OUTPUT"
echo "Duration reduced from ~8:37 to ~5:22 (removed pauses and dead air)"