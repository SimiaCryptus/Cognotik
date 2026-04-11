#!/usr/bin/env bash
#
# edit_WebApp_Factory.sh
# Generated edit script for WebApp_Factory.mp4
#
# Edits:
#   1. Remove long idle wait while project builds (~00:01:18 to ~00:02:05)
#   2. Remove "nodes not displaying correctly" bug acknowledgment (~00:02:05 to ~00:02:17)
#   3. Remove long idle wait during core logic implementation (~00:02:27 to ~00:02:52)
#   4. Remove error stumble / "not sure what that error was" section (~00:03:42 to ~00:04:02)
#
# The approach: use ffmpeg's concat filter to stitch together the segments we KEEP.
#

set -euo pipefail

INPUT="WebApp_Factory.mp4"
OUTPUT="edit_WebApp_Factory.mp4"
TRANSCRIPT_OUT="transcripts/WebApp_Factory_edit.txt"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input file '$INPUT' not found." >&2
    exit 1
fi

mkdir -p transcripts

# Define the segments to KEEP (start, end) in seconds
# Original timeline analysis:
#
# KEEP 1: 00:00:00.000 - 00:01:18.588  (intro through "dependencies")
# CUT  1: 00:01:18.588 - 00:02:05.180  (long wait for project plan to execute)
# CUT  2: 00:02:05.180 - 00:02:17.860  ("nodes not displaying correctly" + transition)
# KEEP 2: 00:02:17.860 - 00:02:26.939  (creates foundational documents, describes tasks)
# CUT  3: 00:02:26.939 - 00:02:52.960  (long wait during core logic implementation)
# KEEP 3: 00:02:52.960 - 00:03:42.849  (completion, readme, download, git explanation)
# CUT  4: 00:03:42.849 - 00:04:02.788  (error stumble, "not sure what that error was")
# KEEP 4: 00:04:02.788 - END           (launch app, demo, update, dark theme, conclusion)

# We use the complex filtergraph with trim + concat to avoid re-encoding issues
# and ensure frame-accurate cuts.

ffmpeg -y -i "$INPUT" -filter_complex "
  [0:v]split=4[v1][v2][v3][v4];
  [0:a]asplit=4[a1][a2][a3][a4];

  [v1]trim=start=0:end=78.588,setpts=PTS-STARTPTS[v1t];
  [a1]atrim=start=0:end=78.588,asetpts=PTS-STARTPTS[a1t];

  [v2]trim=start=137.860:end=146.939,setpts=PTS-STARTPTS[v2t];
  [a2]atrim=start=137.860:end=146.939,asetpts=PTS-STARTPTS[a2t];

  [v3]trim=start=172.960:end=222.849,setpts=PTS-STARTPTS[v3t];
  [a3]atrim=start=172.960:end=222.849,asetpts=PTS-STARTPTS[a3t];

  [v4]trim=start=242.788,setpts=PTS-STARTPTS[v4t];
  [a4]atrim=start=242.788,asetpts=PTS-STARTPTS[a4t];

  [v1t][a1t][v2t][a2t][v3t][a3t][v4t][a4t]concat=n=4:v=1:a=1[outv][outa]
" -map "[outv]" -map "[outa]" \
  -c:v libx264 -preset medium -crf 18 \
  -c:a aac -b:a 192k \
  -movflags +faststart \
  "$OUTPUT"

echo "Edit complete: $OUTPUT"

# Write the edited transcript
cat > "$TRANSCRIPT_OUT" << 'TRANSCRIPT'
To generate a web application, you can use the web app factory. Simply putting in some details of what you want to build here for this demo we will implement a graphing calculator. Save your idea. Make sure that you have some models selected. We're going to be using Gemini 3 Flash preview for this demo. You do have to have an image model selected even if you aren't using it. It doesn't have to be actually an image model. You just have to select something. And then we go to pipeline and build web app. This is the only step in this pipeline, but this step is quite involved. It starts by generating a project plan of tasks and then executes them appropriately, keeping in mind all of their dependencies.

So here it creates the foundational documents for the projects, influences HTML structure, visual style. Now it's implementing the core logic.

And once it is complete, the web app UI will update. The implementation stuff is done and we can see the read me in the project route. At this point we can download the zip if you want. Also notable is that this application has Git support integrated into it. If you know what Git is, it's a version control system. If you don't know what Git is, it is a version control system that essentially allows you to version control files.

Now that the implementation is done, we can also launch the app. And here we see we are plotting sin of x. What happens if we say sin of x plus 10 of x? Here we go. That looks about right. Or what about divide instead of times? What about times? Oh, power, that looks interesting. Anyway, it seems to work, but you know what? It's too bright.

Let's ask for some changes. Update. We can update our web app using the updater, you know, we can say implement a dark theme. Save our notes and run the update. This will update our project with our requested updates. This can also be useful if you see bugs or want a new feature or really any change. The update is done. Let's go look at our graphing calculator, refresh it, and oh. We've got a theme button and it works. So, that's how to use the web app factory to generate your own applications.

We can also click usage and we can see that this entire project demo cost 11 cents so far. I hope you find this useful.
TRANSCRIPT

echo "Edited transcript written to: $TRANSCRIPT_OUT"