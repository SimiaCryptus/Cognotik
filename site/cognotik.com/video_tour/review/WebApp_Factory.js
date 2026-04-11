#!/usr/bin/env node

// Edit script for WebApp_Factory demo video
// Requirements: ffmpeg, Node.js
// Install dependencies: npm install
// Usage: node WebApp_Factory.js
//
// Requires ffmpeg to be installed and available on PATH
// e.g., brew install ffmpeg (macOS) or apt install ffmpeg (Linux)

const {execSync, exec} = require('child_process');
const fs = require('fs');
const path = require('path');

const INPUT = path.join(__dirname, '..', 'source', 'WebApp_Factory.mp4');
const OUTPUT_DIR = path.join(__dirname, '..', 'edit');
const OUTPUT = path.join(OUTPUT_DIR, 'WebApp_Factory.mp4');
const TEMP_DIR = path.join(__dirname, '..', 'edit', '_temp_webapp_factory');

// Ensure output and temp directories exist
if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, {recursive: true});
if (!fs.existsSync(TEMP_DIR)) fs.mkdirSync(TEMP_DIR, {recursive: true});

function run(cmd) {
    console.log(`> ${cmd}`);
    execSync(cmd, {stdio: 'inherit'});
}

function ts(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = (seconds % 60).toFixed(3);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(6, '0')}`;
}

// ---------------------------------------------------------------------------
// Segment definitions
// Each segment: { type, start, end, speed?, mute?, label? }
//   type: 'keep' | 'compress' | 'cut' | 'intro_card' | 'outro_card'
//   speed: playback speed multiplier for compress segments
//   mute: whether to mute audio
// ---------------------------------------------------------------------------

const segments = [
    // Intro card (generated, 2s)
    {type: 'intro_card', duration: 2.5, label: 'Intro billboard'},

    // Segment 1: Introduction & Setup
    {type: 'keep', start: 4, end: 16, label: 'Intro - narrator introduces'},
    // CUT hesitation 16-18
    {type: 'keep', start: 18, end: 29, label: 'Graphing calculator save idea'},
    {type: 'keep', start: 29, end: 50, label: 'Model selection explanation'},
    {type: 'keep', start: 50, end: 55, label: 'Navigate to pipeline'},

    // Segment 2: Project Plan Generation
    {type: 'keep', start: 55, end: 66, label: 'Pipeline step explanation'},
    {type: 'compress', start: 66, end: 78, speed: 3, mute: true, label: 'Processing wait (compressed)'},
    {type: 'keep', start: 78, end: 82, label: 'Project plan with 5 tasks'},

    // Segment 3: Task Execution & Waiting
    {type: 'keep', start: 82, end: 96, label: 'Graph will update explanation'},
    {type: 'compress', start: 96, end: 125, speed: 4, mute: true, label: 'Processing wait (compressed)'},

    // Segment 4: Viewing Task Results
    {type: 'keep', start: 125, end: 129, label: 'Nodes not displaying correctly'},
    {type: 'compress', start: 129, end: 138, speed: 3, mute: true, label: 'Navigating UI (compressed)'},
    {type: 'keep', start: 138, end: 147, label: 'Foundational docs, core logic'},
    {type: 'compress', start: 147, end: 173, speed: 4, mute: true, label: 'Waiting for core logic (compressed)'},

    // Segment 5: Implementation Complete
    {type: 'keep', start: 173, end: 178, label: 'Web app UI will update'},
    {type: 'compress', start: 178, end: 194, speed: 4, mute: true, label: 'Waiting for completion (compressed)'},
    {type: 'keep', start: 194, end: 197, label: 'Implementation done'},

    // Segment 6: Reviewing the Project
    {type: 'keep', start: 197, end: 206, label: 'Viewing README, download zip'},
    // Dialogue cleanup: cut the stumble at 3:26-3:30 and keep the corrected version
    // "it's a version if you don't know what Git is," -> cut, keep from "it is a version control system..."
    {type: 'keep', start: 206, end: 208, label: 'Git support intro'},
    // Skip the stumble portion (~3:28 to ~3:31)
    {type: 'keep', start: 211, end: 223, label: 'Version control explanation (cleaned)'},
    {type: 'keep', start: 223, end: 233, label: 'Error but saved to Git'},

    // Segment 7: Launching & Testing the App
    {type: 'keep', start: 233, end: 242, label: 'Launch the app'},
    {type: 'keep', start: 242, end: 250, label: 'Plotting sin(x)'},
    {type: 'keep', start: 250, end: 270, label: 'Testing expressions'},
    {type: 'keep', start: 270, end: 276, label: 'It works but too bright'},

    // Segment 8: Updating the App
    {type: 'keep', start: 276, end: 286, label: 'Ask for changes'},
    {type: 'keep', start: 286, end: 296, label: 'Typing dark theme'},
    // Tighten pause around 4:56-4:59
    {type: 'keep', start: 297, end: 305, label: 'Run the update'},
    {type: 'keep', start: 305, end: 313, label: 'Update done'},

    // Segment 9: Verifying the Update
    {type: 'keep', start: 313, end: 327, label: 'Refresh and verify theme'},

    // Segment 10: Usage & Cost
    {type: 'keep', start: 327, end: 337, label: 'Usage cost 11 cents'},
    {type: 'keep', start: 337, end: 339, label: 'Hope you find this useful'},

    // Outro card (generated, 3.5s)
    {type: 'outro_card', duration: 3.5, label: 'Outro billboard'},
];

// ---------------------------------------------------------------------------
// Step 1: Probe input video for resolution and framerate
// ---------------------------------------------------------------------------
console.log('Probing input video...');
const probeCmd = `ffprobe -v error -select_streams v:0 -show_entries stream=width,height,r_frame_rate -of csv=p=0 "${INPUT}"`;
const probeResult = execSync(probeCmd).toString().trim();
const [widthStr, heightStr, fpsStr] = probeResult.split(',');
const width = parseInt(widthStr);
const height = parseInt(heightStr);
const [fpsNum, fpsDen] = fpsStr.split('/');
const fps = Math.round(parseInt(fpsNum) / parseInt(fpsDen));
console.log(`Input: ${width}x${height} @ ${fps}fps`);

// ---------------------------------------------------------------------------
// Step 2: Generate intro and outro card images using ffmpeg
// ---------------------------------------------------------------------------
const introCard = path.join(TEMP_DIR, 'intro_card.png');
const outroCard = path.join(TEMP_DIR, 'outro_card.png');

// Intro card: dark background with title and subtitle
run(`ffmpeg -y -f lavfi -i "color=c=0x1a1a2e:s=${width}x${height}:d=1" -vframes 1 -vf "drawtext=text='Web App Factory':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-50:font=Arial,drawtext=text='Build Apps with AI':fontsize=36:fontcolor=0xaaaaaa:x=(w-text_w)/2:y=(h-text_h)/2+30:font=Arial,drawtext=text='Graphing Calculator Demo':fontsize=28:fontcolor=0x888888:x=(w-text_w)/2:y=(h-text_h)/2+80:font=Arial" "${introCard}"`);

// Outro card: dark background with thanks
run(`ffmpeg -y -f lavfi -i "color=c=0x1a1a2e:s=${width}x${height}:d=1" -vframes 1 -vf "drawtext=text='Thanks for Watching!':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=(h-text_h)/2-40:font=Arial,drawtext=text='Built with Web App Factory':fontsize=32:fontcolor=0xaaaaaa:x=(w-text_w)/2:y=(h-text_h)/2+40:font=Arial" "${outroCard}"`);

// ---------------------------------------------------------------------------
// Step 3: Generate each segment as a separate clip
// ---------------------------------------------------------------------------
const clipFiles = [];
let clipIndex = 0;

for (const seg of segments) {
    const clipPath = path.join(TEMP_DIR, `clip_${String(clipIndex).padStart(3, '0')}.mp4`);
    clipFiles.push(clipPath);
    console.log(`\nProcessing clip ${clipIndex}: ${seg.label || seg.type}`);

    if (seg.type === 'intro_card') {
        // Generate intro card video with fade-in and fade-out
        const dur = seg.duration;
        run(`ffmpeg -y -loop 1 -i "${introCard}" -f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" -t ${dur} -c:v libx264 -tune stillimage -pix_fmt yuv420p -r ${fps} -c:a aac -b:a 128k -vf "fade=t=in:st=0:d=0.5,fade=t=out:st=${dur - 0.5}:d=0.5" -shortest "${clipPath}"`);
    } else if (seg.type === 'outro_card') {
        // Generate outro card video with fade-in and fade-out to black
        const dur = seg.duration;
        run(`ffmpeg -y -loop 1 -i "${outroCard}" -f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" -t ${dur} -c:v libx264 -tune stillimage -pix_fmt yuv420p -r ${fps} -c:a aac -b:a 128k -vf "fade=t=in:st=0:d=0.5,fade=t=out:st=${dur - 0.5}:d=0.5" -shortest "${clipPath}"`);
    } else if (seg.type === 'keep') {
        const duration = seg.end - seg.start;
        run(`ffmpeg -y -ss ${ts(seg.start)} -i "${INPUT}" -t ${duration.toFixed(3)} -c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k -pix_fmt yuv420p -r ${fps} "${clipPath}"`);
    } else if (seg.type === 'compress') {
        const duration = seg.end - seg.start;
        const speed = seg.speed || 4;
        // For video: setpts=PTS/speed; for audio: mute or atempo
        const audioFilter = seg.mute
            ? `-af "volume=0"`
            : `-af "atempo=${Math.min(speed, 2)}${speed > 2 ? `,atempo=${speed / 2}` : ''}"`;
        // setpts divides by speed to compress
        run(`ffmpeg -y -ss ${ts(seg.start)} -i "${INPUT}" -t ${duration.toFixed(3)} -vf "setpts=PTS/${speed}" ${audioFilter} -c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k -pix_fmt yuv420p -r ${fps} "${clipPath}"`);
    }

    clipIndex++;
}

// ---------------------------------------------------------------------------
// Step 4: Create concat file and join all clips
// ---------------------------------------------------------------------------
const concatFile = path.join(TEMP_DIR, 'concat.txt');
const concatContent = clipFiles.map(f => `file '${f.replace(/'/g, "'\\''")}'`).join('\n');
fs.writeFileSync(concatFile, concatContent);

console.log('\nConcatenating all clips...');
run(`ffmpeg -y -f concat -safe 0 -i "${concatFile}" -c:v libx264 -preset fast -crf 18 -c:a aac -b:a 128k -pix_fmt yuv420p -movflags +faststart "${OUTPUT}"`);

// ---------------------------------------------------------------------------
// Step 5: Cleanup temp files
// ---------------------------------------------------------------------------
console.log('\nCleaning up temp files...');
for (const f of clipFiles) {
    if (fs.existsSync(f)) fs.unlinkSync(f);
}
if (fs.existsSync(introCard)) fs.unlinkSync(introCard);
if (fs.existsSync(outroCard)) fs.unlinkSync(outroCard);
if (fs.existsSync(concatFile)) fs.unlinkSync(concatFile);
if (fs.existsSync(TEMP_DIR)) fs.rmdirSync(TEMP_DIR);

console.log(`\n✅ Done! Output saved to: ${OUTPUT}`);
console.log(`Estimated runtime: ~4:55 (down from ~5:39)`);