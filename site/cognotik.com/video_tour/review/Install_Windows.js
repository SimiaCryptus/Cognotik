#!/usr/bin/env node

// Edit script for "Install Windows – Cognotic Desktop Installation Guide"
// Dependencies: ffmpeg must be installed and available on PATH
// No additional npm packages required (uses only Node.js built-ins)
//
// Usage: node Install_Windows.js
// Input:  source/Install_Windows.mp4
// Output: edit/Install_Windows.mp4

const {execSync} = require('child_process');
const fs = require('fs');
const path = require('path');

const INPUT = path.join(__dirname, '..', 'source', 'Install_Windows.mp4');
const OUTPUT_DIR = path.join(__dirname, '..', 'edit');
const OUTPUT = path.join(OUTPUT_DIR, 'Install_Windows.mp4');
const TEMP_DIR = path.join(__dirname, '..', 'edit', '_temp_install_windows');

// Ensure output and temp directories exist
fs.mkdirSync(OUTPUT_DIR, {recursive: true});
fs.mkdirSync(TEMP_DIR, {recursive: true});

// --- Configuration ---

// Intro billboard settings
const INTRO_DURATION = 3; // seconds
const INTRO_TITLE = "How to Install Cognotic Desktop on Windows";

// Outro billboard settings
const OUTRO_DURATION = 4; // seconds
const OUTRO_LINE1 = "Thanks for watching!";
const OUTRO_LINE2 = "Cognotic Desktop - cognotic.com";
const OUTRO_LINE3 = "Check out our other tutorials";

// Segment definitions from the editing plan
// Each segment is: { start, end, action, speedFactor?, label? }
// action: 'keep' | 'cut' | 'compress'
// For 'compress': speedFactor is the playback speed multiplier, audio is muted
// For 'compress': targetDuration is the desired output duration in seconds
const segments = [
    // Segment 1: Opening
    {start: '00:00:00.000', end: '00:00:13.339', action: 'cut', label: 'Dead air at start'},
    {start: '00:00:13.339', end: '00:00:22.949', action: 'cut', label: 'False start 1'},
    {start: '00:00:22.949', end: '00:00:28.429', action: 'cut', label: 'False start 2'},
    {start: '00:00:28.429', end: '00:00:37.859', action: 'keep', label: 'Clean intro greeting'},

    // Segment 2: Downloading
    {start: '00:00:37.859', end: '00:00:48.668', action: 'keep', label: 'Download options narration'},
    {start: '00:00:48.668', end: '00:00:53.899', action: 'keep', label: 'Click download'},
    {start: '00:00:53.899', end: '00:00:59.649', action: 'compress', targetDuration: 2, label: 'File download wait'},

    // Segment 3: SmartScreen
    {start: '00:00:59.649', end: '00:01:09.489', action: 'keep', label: 'SmartScreen guidance'},

    // Segment 4: Installation
    {start: '00:01:09.489', end: '00:01:15.088', action: 'cut', label: 'False start - install'},
    {start: '00:01:15.088', end: '00:01:18.000', action: 'keep', label: 'Install it like any desktop app'},
    {start: '00:01:18.000', end: '00:01:29.168', action: 'compress', targetDuration: 3, label: 'Installer running'},
    {start: '00:01:29.168', end: '00:01:35.879', action: 'keep', label: 'Installation complete'},

    // Segment 5: Launching & Configuring
    {start: '00:01:35.918', end: '00:01:57.040', action: 'keep', label: 'Configure application narration'},
    {start: '00:01:57.040', end: '00:02:00.415', action: 'keep', label: 'Browser page opens'},

    // Segment 6: Registration / Login
    {start: '00:02:00.415', end: '00:02:12.574', action: 'keep', label: 'Login explanation'},
    {start: '00:02:12.574', end: '00:02:17.500', action: 'keep', label: 'Registration demo'},
    {start: '00:02:17.500', end: '00:02:18.689', action: 'compress', targetDuration: 1, label: 'Typing silence'},
    {start: '00:02:18.689', end: '00:02:23.860', action: 'cut', label: 'Mis-spoken: weak passwords in my for'},
    {start: '00:02:23.860', end: '00:02:32.949', action: 'keep', label: 'Weak passwords explanation'},
    {start: '00:02:32.949', end: '00:02:49.000', action: 'keep', label: 'Confirmation dialogue context'},
    {start: '00:02:49.000', end: '00:02:52.000', action: 'keep', label: 'You say yes'},
    {start: '00:02:52.000', end: '00:03:02.520', action: 'compress', targetDuration: 2, label: 'Processing/loading'},

    // Segment 7: Web UI Access
    {start: '00:03:02.520', end: '00:03:06.000', action: 'keep', label: 'Web UI access line'},
    {start: '00:03:06.000', end: '00:03:16.080', action: 'compress', targetDuration: 2, label: 'Page loading'},

    // Segment 8: Adding API Key
    {start: '00:03:16.199', end: '00:03:28.330', action: 'keep', label: 'API key settings narration'},
    {start: '00:03:28.330', end: '00:03:40.069', action: 'cut', label: 'Mis-spoken: angioma + cut instruction'},
    {start: '00:03:40.069', end: '00:03:46.270', action: 'keep', label: 'Enter API key, save'},
    {start: '00:03:46.270', end: '00:03:52.508', action: 'keep', label: 'Password manager aside'},

    // Segment 9: Testing
    {start: '00:03:52.508', end: '00:04:00.508', action: 'keep', label: 'Test with basic chat'},
    {start: '00:04:00.508', end: '00:04:07.270', action: 'keep', label: 'Models discovered'},
    {start: '00:04:07.270', end: '00:04:12.409', action: 'keep', label: 'Send test message'},

    // Segment 10: Closing
    {start: '00:04:12.409', end: '00:04:18.889', action: 'keep', label: 'Closing statement'},
];

/**
 * Convert timecode string (HH:MM:SS.mmm) to seconds
 */
function tcToSeconds(tc) {
    const parts = tc.split(':');
    const hours = parseFloat(parts[0]);
    const minutes = parseFloat(parts[1]);
    const seconds = parseFloat(parts[2]);
    return hours * 3600 + minutes * 60 + seconds;
}

/**
 * Run a command, logging it first
 */
function run(cmd) {
    console.log(`> ${cmd}`);
    execSync(cmd, {stdio: 'inherit'});
}

/**
 * Generate intro billboard video with title text on black background
 */
function generateIntro(outputPath, duration, width, height, fps) {
    // Create a black background with centered white title text
    // Word-wrap the title by using drawtext with a constrained width
    const escapedTitle = INTRO_TITLE.replace(/'/g, "'\\''").replace(/:/g, '\\:');
    const filter = [
        `color=c=black:s=${width}x${height}:d=${duration}:r=${fps}`,
        `drawtext=text='${escapedTitle}':fontcolor=white:fontsize=48:x=(w-text_w)/2:y=(h-text_h)/2:font=Arial`,
        `format=yuv420p`
    ].join(',');

    run(`ffmpeg -y -f lavfi -i "${filter}" -c:v libx264 -preset fast -tune stillimage -t ${duration} "${outputPath}"`);

    // Also generate silent audio track and mux
    const videoOnly = outputPath.replace('.mp4', '_v.mp4');
    fs.renameSync(outputPath, videoOnly);
    run(`ffmpeg -y -i "${videoOnly}" -f lavfi -i anullsrc=r=44100:cl=stereo -c:v copy -c:a aac -shortest "${outputPath}"`);
    fs.unlinkSync(videoOnly);
}

/**
 * Generate outro billboard video
 */
function generateOutro(outputPath, duration, width, height, fps) {
    const line1 = OUTRO_LINE1.replace(/'/g, "'\\''").replace(/:/g, '\\:');
    const line2 = OUTRO_LINE2.replace(/'/g, "'\\''").replace(/:/g, '\\:');
    const line3 = OUTRO_LINE3.replace(/'/g, "'\\''").replace(/:/g, '\\:');

    const filter = [
        `color=c=black:s=${width}x${height}:d=${duration}:r=${fps}`,
        `drawtext=text='${line1}':fontcolor=white:fontsize=48:x=(w-text_w)/2:y=(h/2)-80:font=Arial`,
        `drawtext=text='${line2}':fontcolor=#AAAAAA:fontsize=32:x=(w-text_w)/2:y=(h/2):font=Arial`,
        `drawtext=text='${line3}':fontcolor=#888888:fontsize=28:x=(w-text_w)/2:y=(h/2)+60:font=Arial`,
        `format=yuv420p`
    ].join(',');

    run(`ffmpeg -y -f lavfi -i "${filter}" -c:v libx264 -preset fast -tune stillimage -t ${duration} "${outputPath}"`);

    const videoOnly = outputPath.replace('.mp4', '_v.mp4');
    fs.renameSync(outputPath, videoOnly);
    run(`ffmpeg -y -i "${videoOnly}" -f lavfi -i anullsrc=r=44100:cl=stereo -c:v copy -c:a aac -shortest "${outputPath}"`);
    fs.unlinkSync(videoOnly);
}

/**
 * Probe video for resolution and fps
 */
function probeVideo(inputPath) {
    const info = execSync(
        `ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate -of json "${inputPath}"`,
        {encoding: 'utf-8'}
    );
    const parsed = JSON.parse(info);
    const stream = parsed.streams[0];
    const [num, den] = stream.r_frame_rate.split('/');
    const fps = Math.round(parseInt(num) / parseInt(den));
    return {width: stream.width, height: stream.height, fps};
}

// --- Main ---

console.log('=== Install_Windows Video Edit Script ===');
console.log(`Input:  ${INPUT}`);
console.log(`Output: ${OUTPUT}`);

if (!fs.existsSync(INPUT)) {
    console.error(`ERROR: Input file not found: ${INPUT}`);
    process.exit(1);
}

// Probe source video
const {width, height, fps} = probeVideo(INPUT);
console.log(`Source: ${width}x${height} @ ${fps}fps`);

// Step 1: Generate intro and outro billboards
const introPath = path.join(TEMP_DIR, 'intro.mp4');
const outroPath = path.join(TEMP_DIR, 'outro.mp4');

console.log('\n--- Generating intro billboard ---');
generateIntro(introPath, INTRO_DURATION, width, height, fps);

console.log('\n--- Generating outro billboard ---');
generateOutro(outroPath, OUTRO_DURATION, width, height, fps);

// Step 2: Extract and process each segment
const partFiles = [];
let partIndex = 0;

// Add intro as first part
partFiles.push(introPath);

for (const seg of segments) {
    if (seg.action === 'cut') {
        console.log(`\n--- CUTTING: ${seg.label} (${seg.start} - ${seg.end}) ---`);
        continue;
    }

    const startSec = tcToSeconds(seg.start);
    const endSec = tcToSeconds(seg.end);
    const duration = endSec - startSec;
    const partFile = path.join(TEMP_DIR, `part_${String(partIndex).padStart(3, '0')}.mp4`);

    if (seg.action === 'keep') {
        console.log(`\n--- KEEPING: ${seg.label} (${seg.start} - ${seg.end}) ---`);
        run(
            `ffmpeg -y -i "${INPUT}" -ss ${seg.start} -to ${seg.end} -c:v libx264 -preset fast -c:a aac -ar 44100 -ac 2 "${partFile}"`
        );
    } else if (seg.action === 'compress') {
        console.log(`\n--- COMPRESSING: ${seg.label} (${seg.start} - ${seg.end}) -> ${seg.targetDuration}s ---`);
        const speedFactor = duration / seg.targetDuration;

        // Extract the segment first, then speed it up with muted audio
        const rawPart = path.join(TEMP_DIR, `part_${String(partIndex).padStart(3, '0')}_raw.mp4`);
        run(
            `ffmpeg -y -i "${INPUT}" -ss ${seg.start} -to ${seg.end} -c:v libx264 -preset fast -c:a aac -ar 44100 -ac 2 "${rawPart}"`
        );

        // Speed up video and replace audio with silence
        // setpts=PTS/speedFactor for video speedup
        const setPts = (1 / speedFactor).toFixed(6);
        run(
            `ffmpeg -y -i "${rawPart}" -f lavfi -i anullsrc=r=44100:cl=stereo ` +
            `-filter_complex "[0:v]setpts=${setPts}*PTS[v]" ` +
            `-map "[v]" -map 1:a -c:v libx264 -preset fast -c:a aac -shortest "${partFile}"`
        );
        fs.unlinkSync(rawPart);
    }

    partFiles.push(partFile);
    partIndex++;
}

// Add outro as last part
partFiles.push(outroPath);

// Step 3: Create concat file and concatenate all parts
const concatListPath = path.join(TEMP_DIR, 'concat_list.txt');
const concatContent = partFiles.map(f => `file '${f.replace(/\\/g, '/')}'`).join('\n');
fs.writeFileSync(concatListPath, concatContent, 'utf-8');

console.log('\n--- Concatenating all parts ---');
console.log(`Parts: ${partFiles.length}`);

// First pass: re-encode all parts to ensure consistent format for concat
// We'll use the concat demuxer with re-encoding to handle any format mismatches
run(
    `ffmpeg -y -f concat -safe 0 -i "${concatListPath}" ` +
    `-c:v libx264 -preset fast -crf 18 -c:a aac -ar 44100 -ac 2 -b:a 128k ` +
    `-movflags +faststart "${OUTPUT}"`
);

console.log('\n--- Cleaning up temp files ---');

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