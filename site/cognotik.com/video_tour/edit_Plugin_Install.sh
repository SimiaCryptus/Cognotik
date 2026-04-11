#!/usr/bin/env bash
# edit_Plugin_Install.sh
# Auto-generated edit script for Plugin_Install.mp4
# Removes dead air, long pauses, and trims unnecessary gaps.
#
# Edits:
#   1. Trim leading silence (0s - 6.0s)
#   2. Trim pause between file selection and "You may want..." (23.879s - 26.0s)
#   3. Trim pause after "which is OK" (37.5s - 39.3s)
#   4. Trim long pause before closing statement (1:11.5 - 1:19.0)
#
# Strategy: Use ffmpeg filter_complex to select segments and concatenate them.

set -euo pipefail

INPUT="Plugin_Install.mp4"
OUTPUT="edit_Plugin_Install.mp4"
TRANSCRIPT_OUT="transcripts/Plugin_Install_edit.txt"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input file '$INPUT' not found." >&2
    exit 1
fi

mkdir -p transcripts

# Define the segments to KEEP (start, end) in seconds
# Segment 1: From first speech to end of "Selecting the plug-in jar"
#   Start at 6.0s (trim leading silence), end at 23.879s
# Segment 2: From "You may want to check..." to "which is OK"
#   Start at 25.8s, end at 37.5s
# Segment 3: From "and this plugin also requires..." to "other demo videos."
#   Start at 39.3s, end at 71.5s (1:11.5)
# Segment 4: From "As you can see..." to end
#   Start at 79.0s (1:19.0), end at end of file

echo "=== Editing $INPUT ==="
echo "Segments to keep:"
echo "  [1] 00:00:06.000 -> 00:00:23.879  (intro through file selection)"
echo "  [2] 00:00:25.800 -> 00:00:37.500  (auto load through license OK)"
echo "  [3] 00:00:39.300 -> 00:01:11.500  (Patreon verification through demo videos)"
echo "  [4] 00:01:19.000 -> end           (closing statement)"
echo ""

ffmpeg -y -i "$INPUT" -filter_complex "
[0:v]split=4[v1][v2][v3][v4];
[0:a]asplit=4[a1][a2][a3][a4];

[v1]trim=start=6.0:end=23.879,setpts=PTS-STARTPTS[v1out];
[a1]atrim=start=6.0:end=23.879,asetpts=PTS-STARTPTS[a1out];

[v2]trim=start=25.8:end=37.5,setpts=PTS-STARTPTS[v2out];
[a2]atrim=start=25.8:end=37.5,asetpts=PTS-STARTPTS[a2out];

[v3]trim=start=39.3:end=71.5,setpts=PTS-STARTPTS[v3out];
[a3]atrim=start=39.3:end=71.5,asetpts=PTS-STARTPTS[a3out];

[v4]trim=start=79.0,setpts=PTS-STARTPTS[v4out];
[a4]atrim=start=79.0,asetpts=PTS-STARTPTS[a4out];

[v1out][a1out][v2out][a2out][v3out][a3out][v4out][a4out]concat=n=4:v=1:a=1[outv][outa]
" -map "[outv]" -map "[outa]" -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k "$OUTPUT"

echo ""
echo "=== Edit complete: $OUTPUT ==="

# Write the edited transcript
cat > "$TRANSCRIPT_OUT" << 'EOF'
The cognotic desktop application is extensible. You can install a plug-in by clicking Plugins from the main page, clicking Upload Plugin, Choose File. Selecting the plug-in jar.

You may want to check auto load and then upload plugin. Upon starting this plugin asks you to accept the license agreement, which is OK.

And this plugin also requires you to be on Patreon and verify your subscription status. This plug-in is for supporters of my Patreon. Once you click allow on that, you can close that and then refresh the home page and you see some extra options.

As you can see, plug-in installation is simple, and adds working applications.
EOF

echo "Edited transcript written to: $TRANSCRIPT_OUT"