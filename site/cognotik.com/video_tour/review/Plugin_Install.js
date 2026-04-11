#!/usr/bin/env node
/**
 * Plugin_Install.js - Video edit script for Plugin Install tutorial
 *
 * Generates and executes ffmpeg commands to edit source/Plugin_Install.mp4
 * according to the editing plan in Plugin_Install.md.
 *
 * Output: edit/Plugin_Install.mp4
 *
 * Requirements: ffmpeg must be installed and available on PATH.
 */

const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const SOURCE = path.join(__dirname, "..", "source", "Plugin_Install.mp4");
const OUTPUT_DIR = path.join(__dirname, "..", "edit");
const OUTPUT = path.join(OUTPUT_DIR, "Plugin_Install.mp4");
const TEMP_DIR = path.join(__dirname, "..", "edit", "temp_plugin_install");

// Ensure output and temp directories exist
fs.mkdirSync(OUTPUT_DIR, { recursive: true });
fs.mkdirSync(TEMP_DIR, { recursive: true });

function run(cmd) {
  console.log(`> ${cmd}`);
  execSync(cmd, { stdio: "inherit" });
}

function ts(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const sInt = Math.floor(s);
  const ms = Math.round((s - sInt) * 1000);
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(sInt).padStart(2, "0")}.${String(ms).padStart(3, "0")}`;
}

// ---------------------------------------------------------------------------
// Segment definitions
// Each segment: { start, end, speed, muteAudio }
//   speed: 1 = normal, >1 = fast (time-compressed)
//   muteAudio: true = silence audio during this segment
// ---------------------------------------------------------------------------
const segments = [
  // Seg 0: Opening narration (after head trim) — 00:05.5 to 00:18
  { start: 5.5, end: 18, speed: 1, muteAudio: false },
  // Seg 1: File selection narration — 00:18 to 00:24
  { start: 18, end: 24, speed: 1, muteAudio: false },
  // Seg 2: File picker silence (time-compress 2s gap to ~1s) — 00:24 to 00:26
  { start: 24, end: 26, speed: 2, muteAudio: true },
  // Seg 3: Auto load & upload — 00:26 to 00:34
  { start: 26, end: 34, speed: 1, muteAudio: false },
  // Seg 4: License agreement — 00:34 to 00:39
  { start: 34, end: 39, speed: 1, muteAudio: false },
  // Seg 5: License processing silence (time-compress 5s to ~2s) — 00:39 to 00:44
  { start: 39, end: 44, speed: 2.5, muteAudio: true },
  // Seg 6: Patreon narration — 00:44 to 00:51
  { start: 44, end: 51, speed: 1, muteAudio: false },
  // Seg 7: Patreon page load silence (time-compress 5s to ~2s) — 00:51 to 00:56
  { start: 51, end: 56, speed: 2.5, muteAudio: true },
  // Seg 8: Patreon narration continues — 00:56 to 01:02
  { start: 56, end: 62, speed: 1, muteAudio: false },
  // Seg 9: Close & refresh — 01:02 to 01:11
  { start: 62, end: 71, speed: 1, muteAudio: false },
  // Seg 10: Closing remark narration — 01:11 to 01:13
  { start: 71, end: 73, speed: 1, muteAudio: false },
  // Seg 11: Long pause (time-compress 6s to ~1.5s) — 01:13 to 01:19
  { start: 73, end: 79, speed: 4, muteAudio: true },
  // Seg 12: Closing statement — 01:19 to 01:26
  { start: 79, end: 86, speed: 1, muteAudio: false },
];

// ---------------------------------------------------------------------------
// Step 1: Create intro billboard (3 seconds, black background with white text)
// ---------------------------------------------------------------------------
const INTRO_FILE = path.join(TEMP_DIR, "intro.mp4");
run(
  `ffmpeg -y -f lavfi -i "color=c=black:s=1920x1080:d=3,format=yuv420p" ` +
    `-f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" ` +
    `-vf "drawtext=text='Cognotic – Plugin Installation':fontcolor=white:fontsize=56:x=(w-text_w)/2:y=(h-text_h)/2-30,` +
    `drawtext=text='Extensible Desktop Application':fontcolor=0xCCCCCC:fontsize=28:x=(w-text_w)/2:y=(h-text_h)/2+40" ` +
    `-t 3 -c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k -shortest "${INTRO_FILE}"`
);

// ---------------------------------------------------------------------------
// Step 2: Create outro billboard (4 seconds)
// ---------------------------------------------------------------------------
const OUTRO_FILE = path.join(TEMP_DIR, "outro.mp4");
run(
  `ffmpeg -y -f lavfi -i "color=c=black:s=1920x1080:d=4,format=yuv420p" ` +
    `-f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" ` +
    `-vf "drawtext=text='Thanks for watching!':fontcolor=white:fontsize=56:x=(w-text_w)/2:y=(h-text_h)/2-50,` +
    `drawtext=text='Visit cognotic.dev | Support on Patreon':fontcolor=0xCCCCCC:fontsize=28:x=(w-text_w)/2:y=(h-text_h)/2+20,` +
    `drawtext=text='More demo videos coming soon':fontcolor=0x999999:fontsize=24:x=(w-text_w)/2:y=(h-text_h)/2+65" ` +
    `-t 4 -c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k -shortest "${OUTRO_FILE}"`
);

// ---------------------------------------------------------------------------
// Step 3: Extract and process each segment from the source video
// ---------------------------------------------------------------------------
const segmentFiles = [];

segments.forEach((seg, i) => {
  const outFile = path.join(TEMP_DIR, `seg_${String(i).padStart(2, "0")}.mp4`);
  segmentFiles.push(outFile);

  const duration = seg.end - seg.start;

  if (seg.speed === 1 && !seg.muteAudio) {
    // Normal speed, keep audio
    run(
      `ffmpeg -y -ss ${ts(seg.start)} -i "${SOURCE}" -t ${duration.toFixed(3)} ` +
        `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k "${outFile}"`
    );
  } else if (seg.speed !== 1) {
    // Time-compressed segment with muted audio
    // Use setpts for video speed and generate silent audio
    const ptsMultiplier = (1 / seg.speed).toFixed(4);
    run(
      `ffmpeg -y -ss ${ts(seg.start)} -i "${SOURCE}" -t ${duration.toFixed(3)} ` +
        `-f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" ` +
        `-filter_complex "[0:v]setpts=${ptsMultiplier}*PTS[v]" ` +
        `-map "[v]" -map 1:a ` +
        `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k ` +
        `-shortest "${outFile}"`
    );
  } else {
    // Normal speed but muted audio
    run(
      `ffmpeg -y -ss ${ts(seg.start)} -i "${SOURCE}" -t ${duration.toFixed(3)} ` +
        `-f lavfi -i "anullsrc=channel_layout=stereo:sample_rate=48000" ` +
        `-map 0:v -map 1:a ` +
        `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k ` +
        `-shortest "${outFile}"`
    );
  }
});

// ---------------------------------------------------------------------------
// Step 4: Build concat list (intro + segments + outro)
// ---------------------------------------------------------------------------
const concatListFile = path.join(TEMP_DIR, "concat_list.txt");
const allFiles = [INTRO_FILE, ...segmentFiles, OUTRO_FILE];
const concatContent = allFiles.map((f) => `file '${f}'`).join("\n");
fs.writeFileSync(concatListFile, concatContent);

// ---------------------------------------------------------------------------
// Step 5: Concatenate all segments into intermediate file
// ---------------------------------------------------------------------------
const CONCAT_FILE = path.join(TEMP_DIR, "concatenated.mp4");
run(
  `ffmpeg -y -f concat -safe 0 -i "${concatListFile}" ` +
    `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k "${CONCAT_FILE}"`
);

// ---------------------------------------------------------------------------
// Step 6: Apply crossfade transitions and audio normalization
//
// We apply:
//   - 0.5s fade-in from black at the start (intro→content transition)
//   - 0.5s fade-out to black at the end (content→outro transition)
//   - Audio normalization (loudnorm)
//
// Note: True crossfade between intro/content and content/outro would require
// complex xfade filter chains. Instead we use fade-in on the intro card and
// fade-out on the outro card, plus fade transitions at the boundaries.
// ---------------------------------------------------------------------------
run(
  `ffmpeg -y -i "${CONCAT_FILE}" ` +
    `-af "loudnorm=I=-16:TP=-1.5:LRA=11" ` +
    `-vf "fade=t=in:st=0:d=0.5,fade=t=out:st=0:d=0" ` +
    `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k "${OUTPUT}"`
);

// We need to know the total duration for the fade-out. Let's redo with a probe.
// Get duration of concatenated file
const probeResult = execSync(
  `ffprobe -v error -show_entries format=duration -of csv=p=0 "${CONCAT_FILE}"`
)
  .toString()
  .trim();
const totalDuration = parseFloat(probeResult);
const fadeOutStart = (totalDuration - 0.5).toFixed(3);

// Re-encode with proper fade-out timing
run(
  `ffmpeg -y -i "${CONCAT_FILE}" ` +
    `-af "loudnorm=I=-16:TP=-1.5:LRA=11,afade=t=in:st=0:d=0.5,afade=t=out:st=${fadeOutStart}:d=0.5" ` +
    `-vf "fade=t=in:st=0:d=0.5,fade=t=out:st=${fadeOutStart}:d=0.5" ` +
    `-c:v libx264 -preset fast -crf 18 -c:a aac -b:a 192k "${OUTPUT}"`
);

// ---------------------------------------------------------------------------
// Step 7: Cleanup temp files
// ---------------------------------------------------------------------------
console.log("\nCleaning up temporary files...");
fs.rmSync(TEMP_DIR, { recursive: true, force: true });

console.log(`\n✅ Done! Output saved to: ${OUTPUT}`);
console.log(
  `   Estimated duration: ~${Math.round(totalDuration)} seconds`
);