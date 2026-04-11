#!/usr/bin/env bash
# Edit script for Install_Windows
# Generated from transcripts/Install_Windows.srt
#
# Edits:
#   1. Remove false start "Hello. Hello" and repeated intros (00:00:13 - 00:00:28)
#   2. Keep clean intro starting at 00:00:28.429
#   3. Remove stuttered "Install it like any install" - keep clean "it like any desktop application"
#   4. Remove "Cut this part" as speaker explicitly requests removal (00:03:40 - 00:03:46)
#   5. Remove aside "Don't save that in the password manager" (00:03:51 - 00:03:57)
#   6. Remove garbled "Weak passwords in my for." (00:02:23.860 - 00:02:27.618)

set -euo pipefail

INPUT="source/Install_Windows.mp4"
OUTPUT="edit/Install_Windows.mp4"

mkdir -p edit

# Define segments to keep (cutting out false starts, repeated takes, and explicit cut requests)
#
# Seg 1: Clean intro + download instructions + save file
#   From 00:00:28.429 ("to this demo video of how to install Cognotic desktop on Windows...")
#   To   00:01:09.489 (end of "keep the file before you open it to install")
#
# Seg 2: Clean install instruction (skip stuttered "Install it like any install")
#   From 00:01:15.088 ("it like any desktop application.")
#   To   00:02:23.860 (before garbled "Weak passwords in my for.")
#
# Seg 3: After garbled speech, password warning and registration
#   From 00:02:27.618 ("about weak passwords, but it will allow them...")
#   To   00:03:40.069 (before "Cut this part")
#
# Seg 4: After "Cut this part", the API key entry
#   From 00:03:46.270 ("enter an API key, save")
#   To   00:03:51.889 (before "Don't save that in the password manager")
#
# Seg 5: After the aside, test and conclusion
#   From 00:03:57.270 ("and now we can test with basic chat...")
#   To   end of file

ffmpeg -y -i "$INPUT" -filter_complex "
[0:v]split=5[v1][v2][v3][v4][v5];
[0:a]asplit=5[a1][a2][a3][a4][a5];

[v1]trim=start=28.429:end=69.489,setpts=PTS-STARTPTS[v1t];
[a1]atrim=start=28.429:end=69.489,asetpts=PTS-STARTPTS[a1t];

[v2]trim=start=75.088:end=143.860,setpts=PTS-STARTPTS[v2t];
[a2]atrim=start=75.088:end=143.860,asetpts=PTS-STARTPTS[a2t];

[v3]trim=start=147.618:end=220.069,setpts=PTS-STARTPTS[v3t];
[a3]atrim=start=147.618:end=220.069,asetpts=PTS-STARTPTS[a3t];

[v4]trim=start=226.270:end=231.889,setpts=PTS-STARTPTS[v4t];
[a4]atrim=start=226.270:end=231.889,asetpts=PTS-STARTPTS[a4t];

[v5]trim=start=237.270,setpts=PTS-STARTPTS[v5t];
[a5]atrim=start=237.270,asetpts=PTS-STARTPTS[a5t];

[v1t][a1t][v2t][a2t][v3t][a3t][v4t][a4t][v5t][a5t]concat=n=5:v=1:a=1[outv][outa]
" -map "[outv]" -map "[outa]" -c:v libx264 -crf 18 -preset medium -c:a aac -b:a 192k "$OUTPUT"

echo "Edit complete: $OUTPUT"