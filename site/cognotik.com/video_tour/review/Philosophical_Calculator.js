#!/usr/bin/env node

// Philosophical Calculator – Video Edit Script
// Generated from editing plan in Philosophical_Calculator.md
//
// Input:  source/Philosophical_Calculator.mp4
// Output: edit/Philosophical_Calculator.mp4

const {execSync} = require("child_process");
const fs = require("fs");
const path = require("path");

const INPUT = path.resolve(__dirname, "../source/Philosophical_Calculator.mp4");
const OUTPUT_DIR = path.resolve(__dirname, "../edit");
const OUTPUT = path.join(OUTPUT_DIR, "Philosophical_Calculator.mp4");
const TEMP_DIR = path.join(OUTPUT_DIR, "temp_philosophical_calculator");

if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, {recursive: true});
if (!fs.existsSync(TEMP_DIR)) fs.mkdirSync(TEMP_DIR, {recursive: true});

function ts(timeStr) {
    // Convert HH:MM:SS or HH:MM:SS.mmm to seconds
    const parts = timeStr.split(":");
    return (
        parseFloat(parts[0]) * 3600 +
        parseFloat(parts[1]) * 60 +
        parseFloat(parts[2])
    );
}

function run(cmd) {
    console.log(`\n>>> ${cmd}\n`);
    execSync(cmd, {stdio: "inherit"});
}

// ---------------------------------------------------------------------------
// Strategy: Build segments as individual clips, then concatenate.
//
// Segments (in order):
//   0. Intro billboard (generated) – 6s with fade-in/out
//   1. [00:00:06 – 00:00:21] Keep (opening introduction)
//   2. [00:00:21 – 00:00:24] Time-compress 3× + mute (typing pause)
//   3. [00:00:24 – 00:00:43] Keep (file supply & prompt setup)
//   4. [00:00:43 – 00:00:54] Keep (model selection)
//   5. [00:00:54 – 00:01:03] Keep (pipeline execution – draft article)
//   6. [00:01:03 – 00:01:12] Keep (session monitoring – trimmed before cut)
//   7. CUT [00:01:12 – 00:01:16] (mis-spoken dialogue)
//   8. [00:01:16 – 00:01:17] Keep (bridge: "We will view that...")
//   9. [00:01:17 – 00:01:22] Time-compress 3× + mute (processing wait)
//  10. [00:01:22 – 00:01:33] Keep (draft article complete & preview)
//  11. [00:01:33 – 00:01:43] Keep but edit out stutter (lenses intro)
//      -> Keep [00:01:33 – 00:01:35] then cut to [00:01:38 – 00:01:43]
//  12. [00:01:43 – 00:01:58] Keep (lens options overview)
//  13. [00:01:58 – 00:02:12] Keep (running perspective analysis)
//  14. [00:02:12 – 00:02:23] Keep (real-time monitoring explanation)
//  15. CUT [00:02:23 – 00:02:41] (abandoned thought / false starts)
//  16. [00:02:41 – 00:02:54] Keep (processing explanation)
//  17. [00:02:54 – 00:03:12] Keep (usage monitoring)
//  18. [00:03:12 – 00:03:21] Time-compress 4× + mute (processing wait)
//  19. [00:03:21 – 00:03:43] Keep (completion & lens explanation)
//  20. [00:03:43 – 00:03:53] Keep (parallel execution note)
//  21. [00:03:53 – 00:04:10] Keep (update article explanation)
//  22. [00:04:10 – 00:04:20] Keep (checking lens progress)
//  23. [00:04:20 – 00:04:29] Trim redundancy – keep partial
//      -> We need "Synthesis is" then skip to "the last step in the multi-perspective analysis."
//      -> Approximate: keep [00:04:20 – 00:04:22], cut to [00:04:25 – 00:04:29]
//  24. [00:04:29 – 00:04:53] Time-compress 8× + mute (long processing wait)
//  25. [00:04:53 – 00:05:10] Keep (results display)
//  26. [00:05:10 – 00:05:34] Keep (perspective analysis walkthrough)
//  27. [00:05:34 – 00:05:50] Keep (synthesis & recommendations)
//  28. [00:05:50 – 00:06:11] Keep (lenses summary & transition)
//  29. CUT [00:06:11 – 00:06:27] (display issue aside)
//  30. [00:06:27 – 00:06:45] Keep (image generation – real-time monitoring)
//  31. [00:06:45 – 00:06:59] Time-compress ~7× + mute (image gen wait)
//  32. [00:06:59 – 00:07:05] Time-compress 4× + mute (continued gen wait)
//  33. [00:07:05 – 00:07:18] Keep (image integration explanation)
//  34. [00:07:18 – 00:08:08] Time-compress 10× + mute (long doc integration wait)
//  35. CUT [00:08:08 – 00:08:12] (dead air / "OK" filler)
//  36. [00:08:12 – 00:08:37] Keep (final result reveal + closing)
//  37. Outro billboard (generated) – 5s with fade
// ---------------------------------------------------------------------------

const segments = [];
let segIdx = 0;

function segFile(label) {
    const f = path.join(TEMP_DIR, `seg_${String(segIdx++).padStart(3, "0")}_${label}.mp4`);
    return f;
}

// ---- Segment definitions ----

// Each segment: { type, start, end, speed?, label }
// type: "keep" | "compress" | "billboard_intro" | "billboard_outro" | "keep_trimmed"

const plan = [
    {type: "billboard_intro", label: "intro", duration: 6},

    {type: "keep", start: "00:00:06", end: "00:00:21", label: "opening"},
    {type: "compress", start: "00:00:21", end: "00:00:24", speed: 3, label: "typing_pause"},
    {type: "keep", start: "00:00:24", end: "00:00:43", label: "file_supply"},
    {type: "keep", start: "00:00:43", end: "00:00:54", label: "model_select"},
    {type: "keep", start: "00:00:54", end: "00:01:03", label: "pipeline_exec"},
    {type: "keep", start: "00:01:03", end: "00:01:12", label: "session_monitor"},
    // CUT 00:01:12 – 00:01:16
    {type: "keep", start: "00:01:16", end: "00:01:17", label: "bridge_view"},
    {type: "compress", start: "00:01:17", end: "00:01:22", speed: 3, label: "proc_wait_1"},
    {type: "keep", start: "00:01:22", end: "00:01:33", label: "draft_complete"},

    // Stutter edit: keep 00:01:33–00:01:35, cut first "The philosophical,", resume at ~00:01:38
    {type: "keep", start: "00:01:33", end: "00:01:35.5", label: "lenses_intro_a"},
    {type: "keep", start: "00:01:38", end: "00:01:43", label: "lenses_intro_b"},

    {type: "keep", start: "00:01:43", end: "00:01:58", label: "lens_options"},
    {type: "keep", start: "00:01:58", end: "00:02:12", label: "run_perspective"},
    {type: "keep", start: "00:02:12", end: "00:02:23", label: "realtime_monitor"},
    // CUT 00:02:23 – 00:02:41
    {type: "keep", start: "00:02:41", end: "00:02:54", label: "proc_explain"},
    {type: "keep", start: "00:02:54", end: "00:03:12", label: "usage_monitor"},
    {type: "compress", start: "00:03:12", end: "00:03:21", speed: 4, label: "proc_wait_2"},
    {type: "keep", start: "00:03:21", end: "00:03:43", label: "completion_lens"},
    {type: "keep", start: "00:03:43", end: "00:03:53", label: "parallel_exec"},
    {type: "keep", start: "00:03:53", end: "00:04:10", label: "update_article"},
    {type: "keep", start: "00:04:10", end: "00:04:20", label: "check_progress"},

    // Trim redundancy: "Synthesis is" [00:04:20–00:04:22] + "the last step..." [00:04:25–00:04:29]
    {type: "keep", start: "00:04:20", end: "00:04:22", label: "synthesis_a"},
    {type: "keep", start: "00:04:25", end: "00:04:29", label: "synthesis_b"},

    {type: "compress", start: "00:04:29", end: "00:04:53", speed: 8, label: "proc_wait_3"},
    {type: "keep", start: "00:04:53", end: "00:05:10", label: "results_display"},
    {type: "keep", start: "00:05:10", end: "00:05:34", label: "perspective_walk"},
    {type: "keep", start: "00:05:34", end: "00:05:50", label: "synthesis_recs"},
    {type: "keep", start: "00:05:50", end: "00:06:11", label: "lenses_summary"},
    // CUT 00:06:11 – 00:06:27
    {type: "keep", start: "00:06:27", end: "00:06:45", label: "image_gen_rt"},
    {type: "compress", start: "00:06:45", end: "00:06:59", speed: 7, label: "image_gen_wait"},
    {type: "compress", start: "00:06:59", end: "00:07:05", speed: 4, label: "image_gen_wait2"},
    {type: "keep", start: "00:07:05", end: "00:07:18", label: "image_integration"},
    {type: "compress", start: "00:07:18", end: "00:08:08", speed: 10, label: "doc_integration"},
    // CUT 00:08:08 – 00:08:12
    {type: "keep", start: "00:08:12", end: "00:08:37", label: "final_reveal"},

    {type: "billboard_outro", label: "outro", duration: 5},
];

// ---------------------------------------------------------------------------
// Step 1: Probe input to get resolution and framerate
// ---------------------------------------------------------------------------
console.log("=== Philosophical Calculator Video Edit Script ===");
console.log(`Input:  ${INPUT}`);
console.log(`Output: ${OUTPUT}`);

if (!fs.existsSync(INPUT)) {
    console.error(`ERROR: Input file not found: ${INPUT}`);
    process.exit(1);
}

// Get video properties
const probeCmd = `ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate -of csv=p=0 "${INPUT}"`;
const probeResult = execSync(probeCmd).toString().trim();
const [width, height, fpsRatio] = probeResult.split(",");
const [fpsNum, fpsDen] = fpsRatio.split("/");
const fps = parseFloat(fpsNum) / parseFloat(fpsDen);
console.log(`Video: ${width}x${height} @ ${fps.toFixed(2)} fps`);

// ---------------------------------------------------------------------------
// Step 2: Generate billboard images
// ---------------------------------------------------------------------------

function createBillboard(outputPath, title, subtitle, w, h) {
    // Create a black frame with centered white text using ffmpeg
    const escapedTitle = title.replace(/'/g, "'\\''");
    const escapedSubtitle = subtitle.replace(/'/g, "'\\''");
    run(
        `ffmpeg -y -f lavfi -i color=c=black:s=${w}x${h}:d=1:r=${fps.toFixed(0)} -vframes 1 ` +
        `-vf "drawtext=text='${escapedTitle}':fontcolor=white:fontsize=${Math.round(parseInt(h) / 12)}:x=(w-text_w)/2:y=(h-text_h)/2-${Math.round(parseInt(h) / 15)},` +
        `drawtext=text='${escapedSubtitle}':fontcolor=0xCCCCCC:fontsize=${Math.round(parseInt(h) / 20)}:x=(w-text_w)/2:y=(h-text_h)/2+${Math.round(parseInt(h) / 10)}" ` +
        `-frames:v 1 "${outputPath}"`
    );
}

const introBillboardImg = path.join(TEMP_DIR, "intro_billboard.png");
const outroBillboardImg = path.join(TEMP_DIR, "outro_billboard.png");

createBillboard(introBillboardImg, "The Philosophical Calculator", "A Walkthrough Demo", width, height);
createBillboard(outroBillboardImg, "The Philosophical Calculator", "Thank you for watching!", width, height);

// ---------------------------------------------------------------------------
// Step 3: Generate each segment
// ---------------------------------------------------------------------------

const segFiles = [];

for (const seg of plan) {
    const outFile = segFile(seg.label);
    segFiles.push(outFile);

    if (seg.type === "billboard_intro") {
        // 6s billboard: fade in from black 1.5s, hold, crossfade-ready ending
        // Generate video from still image with fade in and fade out
        run(
            `ffmpeg -y -loop 1 -i "${introBillboardImg}" -f lavfi -i anullsrc=r=44100:cl=stereo ` +
            `-t ${seg.duration} -vf "fps=${fps.toFixed(0)},format=yuv420p,` +
            `fade=t=in:st=0:d=1.5,fade=t=out:st=${seg.duration - 0.5}:d=0.5" ` +
            `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k -shortest "${outFile}"`
        );
    } else if (seg.type === "billboard_outro") {
        // 5s outro billboard: crossfade-ready start, fade to black at end
        run(
            `ffmpeg -y -loop 1 -i "${outroBillboardImg}" -f lavfi -i anullsrc=r=44100:cl=stereo ` +
            `-t ${seg.duration} -vf "fps=${fps.toFixed(0)},format=yuv420p,` +
            `fade=t=in:st=0:d=0.5,fade=t=out:st=${seg.duration - 1.5}:d=1.5" ` +
            `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k -shortest "${outFile}"`
        );
    } else if (seg.type === "keep") {
        const startSec = ts(seg.start);
        const endSec = ts(seg.end);
        const duration = endSec - startSec;
        // Extract segment with crossfade-friendly transitions (very brief fades at cut points)
        run(
            `ffmpeg -y -ss ${startSec} -i "${INPUT}" -t ${duration} ` +
            `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k ` +
            `-vf "fps=${fps.toFixed(0)},format=yuv420p" ` +
            `"${outFile}"`
        );
    } else if (seg.type === "compress") {
        const startSec = ts(seg.start);
        const endSec = ts(seg.end);
        const duration = endSec - startSec;
        const speed = seg.speed;
        // Time-compress with speed ramp and muted audio
        // Video: setpts=PTS/speed, Audio: muted (replace with silence)
        run(
            `ffmpeg -y -ss ${startSec} -i "${INPUT}" -t ${duration} ` +
            `-f lavfi -i anullsrc=r=44100:cl=stereo ` +
            `-vf "setpts=PTS/${speed},fps=${fps.toFixed(0)},format=yuv420p" ` +
            `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k ` +
            `-map 0:v -map 1:a -shortest "${outFile}"`
        );
    }
}

// ---------------------------------------------------------------------------
// Step 4: Build concat file and concatenate all segments
// ---------------------------------------------------------------------------

const concatListFile = path.join(TEMP_DIR, "concat_list.txt");
const concatLines = segFiles.map((f) => `file '${f}'`).join("\n");
fs.writeFileSync(concatListFile, concatLines);

console.log("\n=== Concatenating all segments ===");
run(
    `ffmpeg -y -f concat -safe 0 -i "${concatListFile}" ` +
    `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k ` +
    `-movflags +faststart "${OUTPUT}"`
);

// ---------------------------------------------------------------------------
// Step 5: Cleanup temp files
// ---------------------------------------------------------------------------


// --- Cleanup temp files ---
console.log('Cleaning up temporary files...');
try {
    if (fs.existsSync(TEMP_DIR)) {
        fs.rmdirSync(TEMP_DIR, {recursive: true});
    }
} catch (e) {
    console.warn(`Warning: Could not remove temp dir ${TEMP_DIR}: ${e.message}`);
}


console.log(`\n=== Done! Output: ${OUTPUT} ===`);
console.log("Estimated duration: ~5:15–5:30");