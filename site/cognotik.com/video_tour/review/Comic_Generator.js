#!/usr/bin/env node

const {execSync} = require('child_process');
const fs = require('fs');
const path = require('path');

const SOURCE = 'source/Comic_Generator.mp4';
const OUTPUT = 'edit/Comic_Generator.mp4';
const TEMP_DIR = 'edit/temp_comic_generator';

// Ensure output and temp directories exist
fs.mkdirSync('edit', {recursive: true});
fs.mkdirSync(TEMP_DIR, {recursive: true});

function run(cmd) {
    console.log(`> ${cmd}`);
    execSync(cmd, {stdio: 'inherit'});
}

// --- Billboard / Title Card Generation ---
// Create intro title card (3 seconds, 1920x1080, black bg with white text)
run(`ffmpeg -y -f lavfi -i color=c=black:s=1920x1080:d=3:r=30 \
  -f lavfi -i anullsrc=r=44100:cl=stereo \
  -vf "drawtext=text='Comic Serial Generator Demo':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-40, \
       drawtext=text='AI-Powered Comic Book Creation':fontsize=32:fontcolor=0xCCCCCC:x=(w-text_w)/2:y=(h-text_h)/2+40" \
  -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \
  ${TEMP_DIR}/intro_card.mp4`);

// Create outro card (4 seconds)
run(`ffmpeg -y -f lavfi -i color=c=black:s=1920x1080:d=4:r=30 \
  -f lavfi -i anullsrc=r=44100:cl=stereo \
  -vf "drawtext=text='Thanks for watching!':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-40, \
       drawtext=text='Comic Serial Generator':fontsize=32:fontcolor=0xCCCCCC:x=(w-text_w)/2:y=(h-text_h)/2+40" \
  -t 4 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \
  ${TEMP_DIR}/outro_card.mp4`);

// --- Segment Extraction ---
// We define segments in order, each with: start, end, type ('keep', 'compress', 'cut')
// For 'compress' segments, we also define target duration and overlay text.

const segments = [
    // Scene 1: Introduction to the App
    {id: 'seg01', start: '00:00:03.640', end: '00:00:20.280', type: 'keep'},

    // Scene 2: Entering the Prompt (keep first part, cut 28.318-37.969)
    {id: 'seg02', start: '00:00:21.100', end: '00:00:28.318', type: 'keep'},
    // 28.318 - 37.969 is CUT (false start)

    // Scene 3: Configuring Models
    {id: 'seg03', start: '00:00:37.969', end: '00:00:43.209', type: 'keep'},
    // Trim hesitation: skip "These are the deep" (~43.209 to ~45.0), keep from ~45.0
    {id: 'seg04', start: '00:00:45.000', end: '00:01:11.469', type: 'keep'},

    // Scene 4: Explaining Comic Serial Concept
    {id: 'seg05', start: '00:01:11.469', end: '00:01:47.579', type: 'keep'},

    // Scene 5: Generation Begins — Waiting for Script (time-compress ~41s to ~10s)
    {
        id: 'seg06',
        start: '00:01:47.579',
        end: '00:02:28.860',
        type: 'compress',
        targetDuration: 10,
        overlay: 'Generating script...'
    },

    // Scene 6: Script Complete, Character Generation
    {id: 'seg07', start: '00:02:28.860', end: '00:02:54.819', type: 'keep'},

    // Scene 7: Character Generation Processing (time-compress ~65s to ~12s)
    {
        id: 'seg08',
        start: '00:02:54.819',
        end: '00:03:59.439',
        type: 'compress',
        targetDuration: 12,
        overlay: 'Generating character reference images...'
    },

    // Scene 8: Page Generation Begins
    {id: 'seg09', start: '00:03:59.439', end: '00:04:04.808', type: 'keep'},

    // Scene 9: Page Generation Processing (time-compress ~2:34 to ~20s)
    {
        id: 'seg10',
        start: '00:04:04.808',
        end: '00:06:39.139',
        type: 'compress',
        targetDuration: 20,
        overlay: 'Generating comic pages...'
    },

    // Scene 10: Generation Complete
    {id: 'seg11', start: '00:06:39.139', end: '00:06:56.699', type: 'keep'},

    // Scene 11: HTML Rendering (keep explanation, cut redundant part)
    {id: 'seg12', start: '00:06:56.699', end: '00:07:07.019', type: 'keep'},
    // 7:07.019 - 7:15.329 is CUT (redundant "somewhat basic")

    // Time-compress HTML render wait
    {
        id: 'seg13',
        start: '00:07:15.329',
        end: '00:07:26.798',
        type: 'compress',
        targetDuration: 4,
        overlay: 'Rendering HTML...'
    },

    // Scene 12: Previewing the Result
    // Trim "No—" false start at ~7:37 by splitting
    {id: 'seg14', start: '00:07:26.798', end: '00:07:36.500', type: 'keep'},
    // Skip ~0.5s false start
    {id: 'seg15', start: '00:07:37.000', end: '00:07:52.838', type: 'keep'},
];

// Extract and process each segment
const partFiles = [];

// Add intro card
partFiles.push(`${TEMP_DIR}/intro_card_faded.mp4`);
run(`ffmpeg -y -i ${TEMP_DIR}/intro_card.mp4 \
  -vf "fade=t=in:st=0:d=0.5,fade=t=out:st=2.5:d=0.5" \
  -af "afade=t=in:st=0:d=0.5,afade=t=out:st=2.5:d=0.5" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac \
  ${TEMP_DIR}/intro_card_faded.mp4`);

segments.forEach((seg) => {
    const outFile = `${TEMP_DIR}/${seg.id}.mp4`;

    if (seg.type === 'keep') {
        // Extract segment at original speed with crossfade-friendly encoding
        run(`ffmpeg -y -i ${SOURCE} \
      -ss ${seg.start} -to ${seg.end} \
      -c:v libx264 -pix_fmt yuv420p -c:a aac -ar 44100 -ac 2 \
      ${outFile}`);
    } else if (seg.type === 'compress') {
        // Calculate speed factor
        const startSec = timestampToSeconds(seg.start);
        const endSec = timestampToSeconds(seg.end);
        const originalDuration = endSec - startSec;
        const speedFactor = originalDuration / seg.targetDuration;

        // For setpts, we divide by speed factor; for atempo, we chain if > 2.0
        const setptsVal = (1.0 / speedFactor).toFixed(6);
        const atempoFilters = buildAtempoChain(speedFactor);

        // Time-compress with muted audio (silent) and overlay text
        run(`ffmpeg -y -i ${SOURCE} \
      -ss ${seg.start} -to ${seg.end} \
      -f lavfi -i anullsrc=r=44100:cl=stereo \
      -vf "setpts=${setptsVal}*PTS,drawtext=text='${seg.overlay}':fontsize=28:fontcolor=white:borderw=2:bordercolor=black:x=(w-text_w)/2:y=h-60" \
      -map 0:v -map 1:a \
      -t ${seg.targetDuration} \
      -c:v libx264 -pix_fmt yuv420p -c:a aac -ar 44100 -ac 2 -shortest \
      ${outFile}`);
    }

    partFiles.push(outFile);
});

// Add outro card
partFiles.push(`${TEMP_DIR}/outro_card_faded.mp4`);
run(`ffmpeg -y -i ${TEMP_DIR}/outro_card.mp4 \
  -vf "fade=t=in:st=0:d=0.5,fade=t=out:st=3.5:d=0.5" \
  -af "afade=t=in:st=0:d=0.5,afade=t=out:st=3.5:d=0.5" \
  -c:v libx264 -pix_fmt yuv420p -c:a aac \
  ${TEMP_DIR}/outro_card_faded.mp4`);

// --- Normalize all segments to consistent format for concatenation ---
const normalizedFiles = [];
partFiles.forEach((f, i) => {
    const normFile = `${TEMP_DIR}/norm_${String(i).padStart(3, '0')}.ts`;
    run(`ffmpeg -y -i ${f} \
    -c:v libx264 -pix_fmt yuv420p -r 30 \
    -c:a aac -ar 44100 -ac 2 -b:a 128k \
    -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2" \
    -bsf:v h264_mp4toannexb \
    -f mpegts \
    ${normFile}`);
    normalizedFiles.push(normFile);
});

// --- Concatenate all segments ---
const concatInput = normalizedFiles.join('|');
run(`ffmpeg -y -i "concat:${concatInput}" \
  -c:v libx264 -pix_fmt yuv420p \
  -c:a aac -ar 44100 -ac 2 \
  -movflags +faststart \
  ${OUTPUT}`);

// --- Cleanup temp files ---
console.log('Cleaning up temporary files...');
try {
     if (fs.existsSync(TEMP_DIR)) {
         fs.rmdirSync(TEMP_DIR, {recursive: true});
     }
} catch (e) {
     console.warn(`Warning: Could not remove temp dir ${TEMP_DIR}: ${e.message}`);
}

console.log(`\nDone! Output saved to: ${OUTPUT}`);

// --- Helper Functions ---

function timestampToSeconds(ts) {
    // Supports HH:MM:SS.mmm format
    const parts = ts.split(':');
    const hours = parseFloat(parts[0]);
    const minutes = parseFloat(parts[1]);
    const seconds = parseFloat(parts[2]);
    return hours * 3600 + minutes * 60 + seconds;
}

function buildAtempoChain(speed) {
    // atempo filter only accepts values between 0.5 and 100.0
    // For values > 2.0, we chain multiple atempo filters
    const filters = [];
    let remaining = speed;
    while (remaining > 2.0) {
        filters.push('atempo=2.0');
        remaining /= 2.0;
    }
    if (remaining < 0.5) remaining = 0.5;
    filters.push(`atempo=${remaining.toFixed(4)}`);
    return filters.join(',');
}