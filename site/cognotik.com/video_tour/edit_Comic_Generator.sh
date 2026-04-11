#!/usr/bin/env bash
#
# edit_Comic_Generator.sh
# Auto-generated edit script for Comic_Generator.mp4
#
# Edits performed:
#   1. Remove false start / interruption ("About Food parakeets. Stop Interrupt. So.")
#      from ~00:00:20.000 to ~00:00:38.000
#   2. Trim long generation wait between ~00:01:50 and ~00:02:28
#   3. Trim long generation wait between ~00:03:04 and ~00:03:59
#   4. Trim long generation wait between ~00:04:05 and ~00:06:39
#   5. Trim short wait between ~00:07:15 and ~00:07:26
#
# Strategy: Use ffmpeg's select/aselect filters to keep only the desired segments,
# then concatenate them together.
#
set -euo pipefail

INPUT="Comic_Generator.mp4"
OUTPUT="edit_Comic_Generator.mp4"
TRANSCRIPT_OUT="transcripts/Comic_Generator_edit.txt"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input file '$INPUT' not found." >&2
    exit 1
fi

# Define the segments to KEEP (start, end) in seconds
# Segment 1: Opening through the prompt entry (before the false start)
#   00:00:00.000 - 00:00:20.000
# Segment 2: After interruption, resuming with "We saved this idea..."
#   00:00:37.969 - 00:01:50.000
# Segment 3: After first long wait, "We have a Sketch of a script..."
#   00:02:28.860 - 00:03:04.000
# Segment 4: After second long wait, "And now we're on to the actual page generation..."
#   00:03:59.439 - 00:04:05.000
# Segment 5: After third long wait (monitoring), "And it ends with even a Nice little..."
#   00:06:39.139 - 00:07:15.329
# Segment 6: After short wait, "Here we go..."
#   00:07:26.798 - end

# Use the concat demuxer approach: extract segments then concatenate
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

echo "Extracting segments..."

# Segment 1: Intro through prompt
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:00:00.000 -to 00:00:20.000 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg1.mp4"

# Segment 2: "We saved this idea..." through comic serial explanation
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:00:37.969 -to 00:01:50.000 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg2.mp4"

# Segment 3: "We have a Sketch of a script..." through character generation
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:02:28.860 -to 00:03:04.000 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg3.mp4"

# Segment 4: "And now we're on to the actual page generation..."
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:03:59.439 -to 00:04:05.000 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg4.mp4"

# Segment 5: "And it ends with even a Nice little fourth wall joke..." through render
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:06:39.139 -to 00:07:15.329 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg5.mp4"

# Segment 6: "Here we go..." through end
ffmpeg -y -hide_banner -loglevel warning \
    -i "$INPUT" \
    -ss 00:07:26.798 \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -avoid_negative_ts make_zero \
    "$TMPDIR/seg6.mp4"

# Create concat list
cat > "$TMPDIR/concat.txt" <<EOF
file 'seg1.mp4'
file 'seg2.mp4'
file 'seg3.mp4'
file 'seg4.mp4'
file 'seg5.mp4'
file 'seg6.mp4'
EOF

echo "Concatenating segments..."
ffmpeg -y -hide_banner -loglevel warning \
    -f concat -safe 0 \
    -i "$TMPDIR/concat.txt" \
    -c:v libx264 -preset fast -crf 18 \
    -c:a aac -b:a 192k \
    -movflags +faststart \
    "$OUTPUT"

echo "Edit complete: $OUTPUT"

# Write the edited transcript
mkdir -p transcripts
cat > "$TRANSCRIPT_OUT" <<'TRANSCRIPT'
Probably the most entertaining app is the comic serial app. It generates a series of comic books. Based on your prompts here, let's say, Come

We saved this idea. Make sure that we have models selected. These are the deep. Must be First on the list for whatever reason. We will select. Flash three, Gemini three flash preview. And for the image model, let's use Gemini one flash image preview. Save our model settings. And the reason it's called Comic serial is because you can generate a first comic book series, comic book, which includes many frames. We will, we will do that in a second, but then afterwards you can generate sequels and you can keep generating sequels as desired. The final step is then to render the comic book into an HTML format, which uses the same images. It just gives it a, Nice HTML framing. But first, we will monitor the generation of the comic book itself.

We have a Sketch of a script. And then it goes into character generation. The first step that it does when generating comics is to generate Character reference images. These are then used when it's rendering the comics themselves. In order to achieve. Artistic. Consistency

And now we're on to the actual page generation.

And it ends with even a Nice little fourth wall joke. Great. So, now that that generation has completed and my parakeets agree, It is time to render the comic book. This renders the HTML structure that will, How is the comic since the, Basic comic book framing is. Somewhat Basic.

Here we go. Let's open this in a new tab and preview it. We've got our character reference images and, No. A much more attractive presentation with the textual, Dialogue alongside also. And that is the Comic book generator. I hope you enjoy it.
TRANSCRIPT

echo "Edited transcript written to: $TRANSCRIPT_OUT"