#!/usr/bin/env node

// Video Edit Script for Filesystem walkthrough
// Requirements: ffmpeg must be installed and available on PATH
// No additional npm packages required (uses child_process from Node.js stdlib)
//
// Usage: node review/Filesystem.js
// Input:  source/Filesystem.mp4
// Output: edit/Filesystem.mp4

const {execSync} = require("child_process");
const fs = require("fs");
const path = require("path");

const INPUT = path.join(__dirname, "..", "source", "Filesystem.mp4");
const OUTPUT_DIR = path.join(__dirname, "..", "edit");
const OUTPUT = path.join(OUTPUT_DIR, "Filesystem.mp4");
const TEMP_DIR = path.join(__dirname, "..", "edit", "tmp_filesystem");

// Ensure output and temp directories exist
fs.mkdirSync(OUTPUT_DIR, {recursive: true});
fs.mkdirSync(TEMP_DIR, {recursive: true});

function run(cmd) {
    console.log(`> ${cmd}`);
    execSync(cmd, {stdio: "inherit"});
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const INTRO_TEXT = "SimSapa – Session File System";
const OUTRO_TEXT = "Thanks for watching! | SimSapa";
const INTRO_DURATION = 3; // seconds
const OUTRO_DURATION = 4; // seconds
const FADE_DURATION = 0.5; // crossfade duration in seconds

// Cut list – segments to REMOVE (false starts, fillers)
// We keep everything except these ranges
const CUTS = [
    // Remove "If you," false start: 00:01:11.159 – 00:01:12.000
    {from: 71.159, to: 72.0},
    // Remove "Um," filler at ~00:02:12.268 – 00:02:12.800
    {from: 132.268, to: 132.8},
];

// Trim points
const CONTENT_START = 0.0; // We start from 0 but the first 4s become intro billboard
const SPEECH_START = 4.0; // Speech begins at 4s
const CONTENT_END = 166.0; // ~2:46 – trim trailing silence after closing line at ~2:46

// ---------------------------------------------------------------------------
// Step 1: Generate intro billboard (black bg, white text, fade in)
// ---------------------------------------------------------------------------
const INTRO_FILE = path.join(TEMP_DIR, "intro.mp4");

// Probe input to get resolution and audio sample rate
const probeCmd = `ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 "${INPUT}"`;
const probeResult = execSync(probeCmd, {encoding: "utf-8"}).trim();
const [width, height] = probeResult.split(",").map(Number);
console.log(`Source resolution: ${width}x${height}`);

const audioProbeCmd = `ffprobe -v error -select_streams a:0 -show_entries stream=sample_rate,channels -of csv=p=0 "${INPUT}"`;
let sampleRate = 44100;
let channels = 2;
try {
    const audioProbe = execSync(audioProbeCmd, {encoding: "utf-8"}).trim();
    const parts = audioProbe.split(",");
    if (parts[0]) sampleRate = parseInt(parts[0], 10);
    if (parts[1]) channels = parseInt(parts[1], 10);
} catch (e) {
    console.log("Could not probe audio, using defaults");
}

// Generate intro card: black background, centered white text, fade from black
run(
    `ffmpeg -y -f lavfi -i color=c=black:s=${width}x${height}:d=${INTRO_DURATION}:r=30 ` +
    `-f lavfi -i anullsrc=r=${sampleRate}:cl=${channels === 1 ? "mono" : "stereo"} ` +
    `-vf "drawtext=text='${INTRO_TEXT}':fontcolor=white:fontsize=${Math.round(
        height / 15
    )}:x=(w-text_w)/2:y=(h-text_h)/2:font=sans,` +
    `fade=t=in:st=0:d=${FADE_DURATION},fade=t=out:st=${
        INTRO_DURATION - FADE_DURATION
    }:d=${FADE_DURATION}" ` +
    `-t ${INTRO_DURATION} -c:v libx264 -preset fast -pix_fmt yuv420p -c:a aac -b:a 128k ` +
    `"${INTRO_FILE}"`
);

// ---------------------------------------------------------------------------
// Step 2: Generate outro billboard (black bg, white text, fade out to black)
// ---------------------------------------------------------------------------
const OUTRO_FILE = path.join(TEMP_DIR, "outro.mp4");

run(
    `ffmpeg -y -f lavfi -i color=c=black:s=${width}x${height}:d=${OUTRO_DURATION}:r=30 ` +
    `-f lavfi -i anullsrc=r=${sampleRate}:cl=${channels === 1 ? "mono" : "stereo"} ` +
    `-vf "drawtext=text='${OUTRO_TEXT}':fontcolor=white:fontsize=${Math.round(
        height / 18
    )}:x=(w-text_w)/2:y=(h-text_h)/2:font=sans,` +
    `fade=t=in:st=0:d=${FADE_DURATION},fade=t=out:st=${
        OUTRO_DURATION - FADE_DURATION
    }:d=${FADE_DURATION}" ` +
    `-t ${OUTRO_DURATION} -c:v libx264 -preset fast -pix_fmt yuv420p -c:a aac -b:a 128k ` +
    `"${OUTRO_FILE}"`
);

// ---------------------------------------------------------------------------
// Step 3: Build the keep-segments from the source video
//
// We want:
//   - Skip 0–4s (replaced by intro billboard)
//   - Keep SPEECH_START to CONTENT_END, minus the CUT ranges
// ---------------------------------------------------------------------------

// Build list of segments to keep from the source
function buildKeepSegments(start, end, cuts) {
    // Sort cuts by start time
    const sorted = [...cuts].sort((a, b) => a.from - b.from);
    const segments = [];
    let cursor = start;

    for (const cut of sorted) {
        if (cut.from > cursor) {
            segments.push({from: cursor, to: cut.from});
        }
        cursor = Math.max(cursor, cut.to);
    }
    if (cursor < end) {
        segments.push({from: cursor, to: end});
    }
    return segments;
}

const keepSegments = buildKeepSegments(SPEECH_START, CONTENT_END, CUTS);
console.log("Keep segments:", keepSegments);

// Extract each segment
const segmentFiles = [];

keepSegments.forEach((seg, i) => {
    const segFile = path.join(TEMP_DIR, `seg_${String(i).padStart(3, "0")}.mp4`);
    const duration = seg.to - seg.from;
    run(
        `ffmpeg -y -ss ${seg.from} -i "${INPUT}" -t ${duration} ` +
        `-c:v libx264 -preset fast -pix_fmt yuv420p -c:a aac -b:a 128k ` +
        `-avoid_negative_ts make_zero ` +
        `"${segFile}"`
    );
    segmentFiles.push(segFile);
});

// ---------------------------------------------------------------------------
// Step 4: Normalize audio across all segments
// ---------------------------------------------------------------------------
// We'll apply loudnorm during the final concat pass instead of per-segment
// to keep things simpler.

// ---------------------------------------------------------------------------
// Step 5: Concatenate intro + segments + outro using concat demuxer
// ---------------------------------------------------------------------------
const CONCAT_LIST = path.join(TEMP_DIR, "concat.txt");
const allFiles = [INTRO_FILE, ...segmentFiles, OUTRO_FILE];

const concatContent = allFiles.map((f) => `file '${f}'`).join("\n");
fs.writeFileSync(CONCAT_LIST, concatContent, "utf-8");
console.log("Concat list:\n" + concatContent);

// First pass: concat without audio normalization
const CONCAT_RAW = path.join(TEMP_DIR, "concat_raw.mp4");
run(
    `ffmpeg -y -f concat -safe 0 -i "${CONCAT_LIST}" ` +
    `-c:v libx264 -preset fast -pix_fmt yuv420p -c:a aac -b:a 128k ` +
    `"${CONCAT_RAW}"`
);

// ---------------------------------------------------------------------------
// Step 6: Apply audio normalization (loudnorm) on the final output
// ---------------------------------------------------------------------------
run(
    `ffmpeg -y -i "${CONCAT_RAW}" ` +
    `-c:v copy ` +
    `-af loudnorm=I=-16:TP=-1.5:LRA=11 -c:a aac -b:a 128k ` +
    `"${OUTPUT}"`
);

// ---------------------------------------------------------------------------
// Step 7: Cleanup temp files
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


console.log(`\nDone! Output: ${OUTPUT}`);
console.log(
    `Estimated duration: ~${INTRO_DURATION}s intro + ${keepSegments
        .reduce((sum, s) => sum + (s.to - s.from), 0)
        .toFixed(1)}s content + ${OUTRO_DURATION}s outro`
);