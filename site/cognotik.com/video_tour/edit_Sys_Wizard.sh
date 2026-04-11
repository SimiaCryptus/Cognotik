#!/usr/bin/env bash
# Edit script for Sys_Wizard video
# Generated from transcripts/Sys_Wizard.srt
#
# Edits:
#   1. Remove stutter/false start at beginning (0:03-0:09), keep clean statement
#   2. Remove false start "Sometimes you need to do something to your system. Normally you might, you" (0:20-0:27)
#   3. Remove filler "That's. Right" and dead air gap (0:58.109 - 1:10.638)
#   4. Remove long waiting pause for generation (1:56.638 - 2:02.709)

set -euo pipefail

INPUT="source/Sys_Wizard.mp4"
OUTPUT="edit/Sys_Wizard.mp4"
FILTER_COMPLEX_PARTS=""
SEGMENT=0

mkdir -p edit

# Define segments to KEEP (gaps between these are the cuts)
# Segment 0: Opening - keep the clean version of the intro (subtitle 2 start)
#   From 0:00.000 to 0:03.680 (before any speech, if there's intro visuals) — skip, speech starts at 3.68
#   Subtitle 1-2 stutter: 3.680 - 15.609 → replace with just the clean part
#   Clean part is in subtitle 2: "SIS Wizard app is powerful and dangerous" ~9.839-15.609
#   But subtitle 1 starts the sentence. Best to keep from ~7.5s (where "The Sys Wizard app is powerful and dangerous" begins in sub 2)
# Segment 0: 0.000 to 3.680 (any pre-speech content)
# Segment 1: 9.839 to 20.379 (clean intro statement + pause before next section)
#   Sub 2: 9.839-15.609 "SISs Wizard app is powerful and dangerous"
#   Sub 3 starts at 20.379 but has false start
# Segment 2: Skip false start. Keep from "sometimes you need to do something with your system" 
#   The restart begins at ~25.5s in subtitle 4: "you sometimes you need to do something with your system"
#   Cleaner cut: start at subtitle 5 (27.969) "your system. You need to install something..."
#   Actually sub 4 at 23.329 says "you might, you sometimes you need to do something with"
#   Sub 5 at 27.969 "your system. You need to install something or figure out"
#   Best to cut from 24.5 (where "sometimes" starts) through to end of that thought
#   Let's keep from 24.8 to capture "sometimes you need to do something with your system..."
# Segment 2: 24.800 to 58.109 (main explanation through "for this demonstration, do.")
# Segment 3: Skip filler "That's. Right" and dead air (58.109 to ~1:10.638)
# Segment 3: 70.638 to 116.638 ("Let's see your running processes..." through "generate a shell script. That will be generated momentarily.")
#   Sub 13: 70.638 (1:10.638) to sub 20 end ~105.250 (1:45.250)
#   Sub 20-21: "generate a shell script. That will be generated momentarily." ends ~116.638 (1:56.638)
# Segment 4: Skip waiting dead air. Resume at "And here we go" but that's also filler.
#   Sub 22: 122.709 (2:02.709) "We can either use this directly..."
#   Actually sub 22 is 2:02.709-2:05.588 "here we go. We" — skip this too, go to sub 23
#   Sub 23: 2:02.709 says "We" continuing to 2:05.588
#   Let's keep from 2:02.709 — "We can either use this directly..."
# Segment 4: 122.709 to end (2:02.709 to end at ~3:07.860)

# Let's refine the segments:
# Seg A: 0.000  → 3.680   (pre-speech silence/intro if any)
# Seg B: 9.839  → 15.609  (clean: "Sys Wizard app is powerful and dangerous")
# Seg C: 24.800 → 58.109  (main content: "Sometimes you need to do something with your system..." through "demonstration, do.")
# Seg D: 70.638 → 105.250 (demo walkthrough: "Let's see your running processes" through "generate a shell script.")
# Seg E: 105.250→ 108.000 ("That will be generated momentarily.")
# Seg F: 122.709→ 187.860 ("We can either use this..." to end)

# Actually, let me re-examine more carefully and keep it simpler with fewer cuts:
# 
# CUT 1: Remove 3.680-9.839 (stutter "The SIS Wizard, the SIS Wizard app is. The")
# CUT 2: Remove 15.609-24.800 (filler "Uh" + false start "Sometimes you need to do something to your system. Normally you might, you")
# CUT 3: Remove 58.109-70.638 (filler "That's. Right" + dead air)
# CUT 4: Remove 116.638-122.709 (dead air waiting for generation + "And here we go")

# Segments to keep:
# [0.000, 3.680]
# [9.839, 15.609]
# [24.800, 58.109]
# [70.638, 116.638]
# [122.709, end]

# Build filter_complex for concatenation
ffmpeg -y -i "$INPUT" \
  -filter_complex "\
    [0:v]split=5[v0][v1][v2][v3][v4]; \
    [0:a]asplit=5[a0][a1][a2][a3][a4]; \
    [v0]trim=start=0:end=3.680,setpts=PTS-STARTPTS[sv0]; \
    [a0]atrim=start=0:end=3.680,asetpts=PTS-STARTPTS[sa0]; \
    [v1]trim=start=9.839:end=15.609,setpts=PTS-STARTPTS[sv1]; \
    [a1]atrim=start=9.839:end=15.609,asetpts=PTS-STARTPTS[sa1]; \
    [v2]trim=start=24.800:end=58.109,setpts=PTS-STARTPTS[sv2]; \
    [a2]atrim=start=24.800:end=58.109,asetpts=PTS-STARTPTS[sa2]; \
    [v3]trim=start=70.638:end=116.638,setpts=PTS-STARTPTS[sv3]; \
    [a3]atrim=start=70.638:end=116.638,asetpts=PTS-STARTPTS[sa3]; \
    [v4]trim=start=122.709,setpts=PTS-STARTPTS[sv4]; \
    [a4]atrim=start=122.709,asetpts=PTS-STARTPTS[sa4]; \
    [sv0][sa0][sv1][sa1][sv2][sa2][sv3][sa3][sv4][sa4]concat=n=5:v=1:a=1[outv][outa]" \
  -map "[outv]" -map "[outa]" \
  -c:v libx264 -preset medium -crf 23 \
  -c:a aac -b:a 128k \
  "$OUTPUT"

echo "Edit complete: $OUTPUT"