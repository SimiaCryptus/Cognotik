#!/usr/bin/env bash
#
# edit_Philosophical_Calculator.sh
# Auto-generated edit script for Philosophical_Calculator.mp4
#
# Edits:
#   1. Remove garbled speech "Right? AI, Knox" + dead air (00:00:17.0 - 00:00:24.679)
#   2. Remove stuttered repetition "the philosophical," (00:01:35.5 - 00:01:37.5)
#   3. Remove false start "change" + dead air (00:02:21.0 - 00:02:26.849)
#   4. Remove "Um," filler before "Agents" (00:02:35.520 - 00:02:37.439)
#   5. Remove "Oh, the brains, the, uh," stumble (00:03:12.788 - 00:03:14.5)
#   6. Remove long dead air / waiting period (00:04:33.0 - 00:04:53.879)
#   7. Remove "um," filler (00:06:11.699 - 00:06:12.5)
#   8. Remove long waiting/dead air (00:06:35.0 - 00:06:59.459)
#   9. Remove long waiting/dead air while images generate (00:07:22.0 - 00:08:12.259)
#
# Strategy: Use ffmpeg filter_complex to split the video into segments (keeping
# the good parts) and concatenate them together.

set -euo pipefail

INPUT="Philosophical_Calculator.mp4"
OUTPUT="edit_Philosophical_Calculator.mp4"
TRANSCRIPT_OUT="transcripts/Philosophical_Calculator_edit.txt"

if [ ! -f "$INPUT" ]; then
    echo "ERROR: Input file '$INPUT' not found."
    exit 1
fi

echo "=== Editing $INPUT ==="
echo "Output video: $OUTPUT"
echo "Output transcript: $TRANSCRIPT_OUT"

# Define the segments to KEEP (start, end) in seconds
# These are the inverse of the cuts listed above.
#
# Full duration is ~8:37 (517s)
#
# Segment 1: 00:00:00.000 - 00:00:17.000  (before garbled "Right? AI, Knox")
# Segment 2: 00:00:24.679 - 00:01:35.500  (after dead air, before stutter)
# Segment 3: 00:01:37.500 - 00:02:21.000  (after stutter fix, before "change")
# Segment 4: 00:02:26.849 - 00:02:35.520  (after "change" dead air, before "Um,")
# Segment 5: 00:02:37.439 - 00:03:12.788  (after "Um," removal, before stumble)
# Segment 6: 00:03:14.500 - 00:04:33.000  (after stumble fix, before long dead air)
# Segment 7: 00:04:53.879 - 00:06:11.699  (after dead air, before "um,")
# Segment 8: 00:06:12.500 - 00:06:35.000  (after "um," removal, before long wait)
# Segment 9: 00:06:59.459 - 00:07:22.000  (after wait, before long image gen wait)
# Segment 10: 00:08:12.259 - end          (after long wait to end)

NUM_SEGMENTS=10

# Build the ffmpeg filter_complex for segment extraction and concatenation
ffmpeg -y -i "$INPUT" -filter_complex "
[0:v]split=${NUM_SEGMENTS}[v0][v1][v2][v3][v4][v5][v6][v7][v8][v9];
[0:a]asplit=${NUM_SEGMENTS}[a0][a1][a2][a3][a4][a5][a6][a7][a8][a9];

[v0]trim=start=0:end=17.000,setpts=PTS-STARTPTS[sv0];
[a0]atrim=start=0:end=17.000,asetpts=PTS-STARTPTS[sa0];

[v1]trim=start=24.679:end=95.500,setpts=PTS-STARTPTS[sv1];
[a1]atrim=start=24.679:end=95.500,asetpts=PTS-STARTPTS[sa1];

[v2]trim=start=97.500:end=141.000,setpts=PTS-STARTPTS[sv2];
[a2]atrim=start=97.500:end=141.000,asetpts=PTS-STARTPTS[sa2];

[v3]trim=start=146.849:end=155.520,setpts=PTS-STARTPTS[sv3];
[a3]atrim=start=146.849:end=155.520,asetpts=PTS-STARTPTS[sa3];

[v4]trim=start=157.439:end=192.788,setpts=PTS-STARTPTS[sv4];
[a4]atrim=start=157.439:end=192.788,asetpts=PTS-STARTPTS[sa4];

[v5]trim=start=194.500:end=273.000,setpts=PTS-STARTPTS[sv5];
[a5]atrim=start=194.500:end=273.000,asetpts=PTS-STARTPTS[sa5];

[v6]trim=start=293.879:end=371.699,setpts=PTS-STARTPTS[sv6];
[a6]atrim=start=293.879:end=371.699,asetpts=PTS-STARTPTS[sa6];

[v7]trim=start=372.500:end=395.000,setpts=PTS-STARTPTS[sv7];
[a7]atrim=start=372.500:end=395.000,asetpts=PTS-STARTPTS[sa7];

[v8]trim=start=419.459:end=442.000,setpts=PTS-STARTPTS[sv8];
[a8]atrim=start=419.459:end=442.000,asetpts=PTS-STARTPTS[sa8];

[v9]trim=start=492.259,setpts=PTS-STARTPTS[sv9];
[a9]atrim=start=492.259,asetpts=PTS-STARTPTS[sa9];

[sv0][sa0][sv1][sa1][sv2][sa2][sv3][sa3][sv4][sa4][sv5][sa5][sv6][sa6][sv7][sa7][sv8][sa8][sv9][sa9]concat=n=${NUM_SEGMENTS}:v=1:a=1[outv][outa]
" -map "[outv]" -map "[outa]" -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k -movflags +faststart "$OUTPUT"

echo "=== Video edit complete: $OUTPUT ==="

# Write the edited transcript
mkdir -p transcripts
cat > "$TRANSCRIPT_OUT" << 'TRANSCRIPT'
One of my favorite apps is the Philosophical calculator. Like a normal calculator, the first step is to give it some input. Here, we're going to say,

We could also supply files. But in this case, we're going to go with a simple prompt. Now, the first step in using the philosophical calculator, of course, is to make sure we have some models selected. We're going with haiku and Gemini flash image.

And then we start at the pipeline. Since our input is fairly small, we're going to skip the summarize notes part and go straight to draft article. We can run that in the UI. And wait for it to complete. We could view this session. To monitor it in real time but this processing session is a one stop operation. Its details aren't particularly interesting. For session detail we will view that in depth when we execute lenses which is next. But for now,

We have the completed draft article. That is shown in a formatted preview. And now that we have the draft article we can proceed to lenses. Here's where it gets really interesting. The philosophical calculator provides a variety of operations that you can perform on your draft article. It performs a variety of different analysis like brainstorming or multi-perspective analysis or even writing a comic book about the article.

For demonstration purposes, let's go with perspective analysis. I'm going to click run, and in a second, a session link will pop up. This session link allows us to monitor the multi perspective analysis task in real time as it proceeds through a multistep reasoning.

The user interface you're seeing here is the standard user interface for real-time agents and analysis. This will take a moment because it will be going through a number of different perspectives, and then finally it will synthesize the results, basically summarize all of the perspectives. We can also in the main UI go over to usage and we can monitor the usage as it uses tokens.

So far, we have spent about 4 cents on this process. The multi-perspective dialogue is ongoing so it just use another penny.

And when it completes, the UI will update. We're only going to run this one lens to demonstrate this module, but you could run each of these if you wanted to in parallel. Depending on what you felt was appropriate. When these are completed, we can then go back to the pipeline. And we can update the article, which will take the output of all of the lens runs and try to fold them back into the article to pick up insights and new ideas. Other things like that. Perhaps correcting issues.

Looking at lenses, this is kind of almost complete. I saw a synthesis here. Synthesis is the last step in the multi perspective analysis.

And when it completes, there we go. And we can see the detailed output for a given lens run in the UI. Here we see the multi perspective analysis transcript, which will detail each perspective in turn. For example, this is the competitive player perspective. It goes through each perspective, so it's quite a long analysis, and at the end, you will find the synthesis and recommendations section. Here we go.

So, the lenses generally involve a very long set of analysis and then they conclude with a summary. Those summaries and analysis can again be folded back into the original article using the pipeline. However, for brevity's sake, we will skip that and demonstrate one last feature, the illustrate article feature, which is, I think, one of the most entertaining.

This takes your content in your article and designs a number of illustrations. It can then weave into the article for display. There's a display issue. The session link is shown in the draft article section. Hopefully they'll be fixed and released soon. And again, we can monitor this in real time. It comes up with the image ideas, and then it generates each image.

Here we go. And then after it generates all of the images, it will integrate the images into the original documents by editing the documents and adding in image links.

And now we see our article. Full of illustrations. For 62 cents, we have a fully illustrated guide to Connect Four. This of course is merely a demo. I hope you enjoy this tool and exploring your own ideas.
TRANSCRIPT

echo "=== Edited transcript written to: $TRANSCRIPT_OUT ==="