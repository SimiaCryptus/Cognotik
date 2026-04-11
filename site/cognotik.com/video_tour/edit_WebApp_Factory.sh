#!/usr/bin/env bash
# Edit script for WebApp_Factory.mp4
# Trims start/end silence and removes long pauses (build wait times)

set -euo pipefail

INPUT="source/WebApp_Factory.mp4"
OUTPUT="edit/WebApp_Factory.mp4"

mkdir -p edit

# We will use ffmpeg's concat demuxer with trimmed segments.
# Segments chosen to remove dead air / waiting periods:
#
# Segment 1: 00:00:03.5 - 00:01:06.0   Intro, setting up the project, plan generation
# Segment 2: 00:01:06.0 - 00:01:22.3   Brief transition showing plan (keep short wait context)
#   -> Actually the wait from ~1:06 to ~2:05 is mostly dead air. We keep 1:06-1:22 (plan shown)
#      then skip to 2:05 where content resumes.
# Segment 3: 00:02:05.0 - 00:02:27.0   Nodes display, viewing tasks, foundational docs
# Segment 4: 00:02:27.0 - 00:02:53.0   "Now it's implementing the core logic" - long wait
#   -> Keep 2:27-2:30 for context, skip to 2:53
# Segment 5: 00:02:53.0 - 00:03:14.0   "once complete, web app UI will update" - wait
#   -> Keep 2:53-2:58 for context, skip to 3:14
# Segment 6: 00:03:14.0 - 00:03:42.8   Implementation done, readme, download, git
# Segment 7: 00:03:42.8 - 00:03:53.0   Confused about error - awkward, skip most
#   -> Keep 3:53-3:57 "It did save the files to git"
# Segment 8: 00:03:53.0 - 00:05:39.5   Launch app, demo, update, dark theme, conclusion
#
# Refined segments:
#   A: 00:00:03.5  to 00:01:22.5  (intro through project plan shown)
#   B: 00:02:05.0  to 00:02:30.0  (tasks displayed, foundational docs, starts core logic)
#   C: 00:02:52.5  to 00:02:58.0  (once complete, UI will update)
#   D: 00:03:14.0  to 00:03:42.0  (implementation done, readme, download, git explanation)
#   E: 00:03:53.0  to 00:05:39.5  (files saved, launch app, demo, update, conclusion)

SEGMENTS_FILE=$(mktemp /tmp/segments_XXXXXX.txt)

# Create individual trimmed segments
ffmpeg -y -i "$INPUT" -ss 00:00:03.500 -to 00:01:22.500 -c:v libx264 -c:a aac -avoid_negative_ts make_zero "/tmp/webapp_seg_a.mp4"
ffmpeg -y -i "$INPUT" -ss 00:02:05.000 -to 00:02:30.000 -c:v libx264 -c:a aac -avoid_negative_ts make_zero "/tmp/webapp_seg_b.mp4"
ffmpeg -y -i "$INPUT" -ss 00:02:52.500 -to 00:02:58.000 -c:v libx264 -c:a aac -avoid_negative_ts make_zero "/tmp/webapp_seg_c.mp4"
ffmpeg -y -i "$INPUT" -ss 00:03:14.000 -to 00:03:42.000 -c:v libx264 -c:a aac -avoid_negative_ts make_zero "/tmp/webapp_seg_d.mp4"
ffmpeg -y -i "$INPUT" -ss 00:03:53.000 -to 00:05:39.500 -c:v libx264 -c:a aac -avoid_negative_ts make_zero "/tmp/webapp_seg_e.mp4"

# Create concat list
cat > "$SEGMENTS_FILE" <<EOF
file '/tmp/webapp_seg_a.mp4'
file '/tmp/webapp_seg_b.mp4'
file '/tmp/webapp_seg_c.mp4'
file '/tmp/webapp_seg_d.mp4'
file '/tmp/webapp_seg_e.mp4'
EOF

# Concatenate all segments
ffmpeg -y -f concat -safe 0 -i "$SEGMENTS_FILE" -c:v libx264 -c:a aac -movflags +faststart "$OUTPUT"

# Clean up temp files
rm -f /tmp/webapp_seg_a.mp4 /tmp/webapp_seg_b.mp4 /tmp/webapp_seg_c.mp4 /tmp/webapp_seg_d.mp4 /tmp/webapp_seg_e.mp4
rm -f "$SEGMENTS_FILE"

echo "Edit complete: $OUTPUT"

# Also generate the edited transcript markdown
cat > "WebApp_Factory.md" << 'MARKDOWN'
# Web App Factory: Building a Graphing Calculator

## Overview

A walkthrough of using the Web App Factory to generate a fully functional web application — in this case, a graphing calculator — with just a brief description and a single pipeline step.

---

## Setting Up the Project
*(0:00)*

To generate a web application, you can use the Web App Factory. Simply put in some details of what you want to build. For this demo, we will implement a graphing calculator.

Save your idea and make sure that you have some models selected. We're going to be using Gemini 3 Flash Preview for this demo. You do have to have an image model selected even if you aren't using it — it doesn't have to be actually an image model, you just have to select something.

## Running the Pipeline
*(0:46)*

Then we go to Pipeline and select "Build Web App." This is the only step in this pipeline, but this step is quite involved. It starts by generating a project plan of tasks and then executes them appropriately, keeping in mind all of their dependencies.

Here we see the project plan with five different tasks. It's currently executing the first task. As it proceeds through the plan, the graph will update, and we just have to wait until the project is implemented.

## Viewing the Build Process
*(2:05)*

We can view the tasks themselves. Here it creates the foundational documents for the project — the HTML structure, visual style — and now it's implementing the core logic.

Once it is complete, the web app UI will update.

## Implementation Complete
*(3:14)*

The implementation is done and we can see the README in the project root. At this point we can download the zip if we want.

Also notable is that this application has Git support integrated into it. Git is a version control system that allows you to track and manage changes to your files. The files were saved to Git automatically.

## Launching and Testing the App
*(3:53)*

Now that the implementation is done, we can also launch the app. Here we see we are plotting `sin(x)`. What happens if we say `sin(x) + 10*x`? Here we go — that looks about right.

Or what about divide instead of times? What about times? Oh, power — that looks interesting.

Anyway, it seems to work, but you know what? It's too bright.

## Updating the App
*(4:36)*

Let's ask for some changes. We can update our web app using the updater. We can say: "Implement a dark theme."

Save our notes and run the update. This will update our project with our requested changes. This can also be useful if you see bugs or want a new feature or really any change.

The update is done. Let's go look at our graphing calculator, refresh it, and — we've got a theme button and it works!

## Conclusion
*(5:23)*

So that's how to use the Web App Factory to generate your own applications. We can also click "Usage" and see that this entire project demo cost 11 cents so far.

I hope you find this useful.
MARKDOWN

echo "Markdown transcript written: WebApp_Factory.md"