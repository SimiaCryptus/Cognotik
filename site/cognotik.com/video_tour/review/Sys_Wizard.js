#!/usr/bin/env node

// Edit script for Sys_Wizard video
// Generated from review/Sys_Wizard.md editing plan
// Uses ffmpeg to perform cuts, time-compression, and intro/outro cards

const { execSync } = require("child_process");
const fs = require("fs");
const path = require("path");

const INPUT = path.join(__dirname, "..", "source", "Sys_Wizard.mp4");
const OUTPUT_DIR = path.join(__dirname, "..", "edit");
const OUTPUT = path.join(OUTPUT_DIR, "Sys_Wizard.mp4");

// Ensure output directory exists
if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

// --- Configuration ---
const INTRO_DURATION = 3; // seconds
const OUTRO_DURATION = 4; // seconds
const FADE_IN = 0.5;
const FADE_OUT = 1.0;
const CROSSFADE_DURATION = 0.3;
const RESOLUTION = "1920:1080"; // assumed; adjust if source differs
const FPS = 30;

// --- Segment definitions ---
// Each segment: { start, end, type, speed }
// type: "keep" | "compress" | "intro" | "outro"
// speed: playback speed multiplier for compress segments
const segments = [
  // Segment 2 — Clean restart of the thought
  { start: 23.329, end: 27.969, type: "keep" },
  // Segment 2 — Explanatory content
  { start: 27.969, end: 41.209, type: "keep" },
  // Segment 3 — Core value proposition
  { start: 41.95, end: 47.179, type: "keep" },
  // Segment 3 — Trimmed: skip false start "You start with the, you start"
  // Keep from ~49.5 (approx "you start at the goal tab") to 52.149
  { start: 49.5, end: 52.149, type: "keep" },
  // Segment 3 — Clean instruction
  { start: 52.149, end: 58.109, type: "keep" },
  // Segment 4 — Skip false starts 58.109-66.308, then compress typing
  { start: 66.308, end: 70.638, type: "compress", speed: 3 },
  // Segment 4 — "Let's see your running processes. We saved the goal."
  { start: 70.638, end: 75.43, type: "keep" },
  // Segment 5 — Settings & Configuration (full block)
  { start: 75.43, end: 88.989, type: "keep" },
  { start: 88.989, end: 98.909, type: "keep" },
  { start: 98.909, end: 105.25, type: "keep" },
  // Segment 6 — Compress LLM processing wait
  { start: 105.25, end: 116.638, type: "compress", speed: 4 },
  // Segment 6 — "And here we go."
  { start: 116.638, end: 122.709, type: "keep" },
  // Segment 6 — "We can either use this directly..."
  { start: 122.709, end: 125.588, type: "keep" },
  // Segment 7 — "or we can click run and fix..."
  { start: 125.588, end: 128.849, type: "keep" },
  // Segment 7 — Compress UI click wait
  { start: 128.849, end: 134.308, type: "compress", speed: 3 },
  // Segment 7 — "Now, in this run and autofix session..."
  { start: 134.308, end: 139.819, type: "keep" },
  // Segment 8 — Error handling explanation (keep)
  { start: 139.819, end: 145.0, type: "keep" },
  { start: 145.0, end: 149.679, type: "keep" },
  // Segment 8 — Skip repetitive 149.679-156.639, keep from "You would be able to retry..."
  { start: 153.0, end: 166.199, type: "keep" },
  // Segment 9 — Viewing results
  { start: 166.199, end: 169.969, type: "keep" },
  { start: 169.969, end: 180.479, type: "keep" },
  // Segment 10 — Skip repetitive "And there you go. That is the system wizard."
  // Keep clean closing
  { start: 184.919, end: 187.86, type: "keep" },
];

// --- Build ffmpeg filter complex ---
// Strategy:
// 1. Generate intro card as a video source
// 2. Trim each segment from input, apply speed changes where needed
// 3. Generate outro card as a video source
// 4. Concatenate all with crossfades

function buildCommand() {
  const filterParts = [];
  const concatInputs = [];
  let streamIdx = 0;

  // --- Intro card ---
  const introLabel = `intro_v`;
  const introALabel = `intro_a`;
  filterParts.push(
    `color=c=black:s=${RESOLUTION}:d=${INTRO_DURATION}:r=${FPS}[intro_bg]`,
    `[intro_bg]drawtext=text='System Wizard':fontcolor=white:fontsize=64:x=(w-text_w)/2:y=(h-text_h)/2-60:font=Arial,` +
      `drawtext=text='AI-Powered Shell Scripting':fontcolor=0xCCCCCC:fontsize=36:x=(w-text_w)/2:y=(h-text_h)/2+20:font=Arial,` +
      `drawtext=text='Cognotic':fontcolor=0x888888:fontsize=28:x=(w-text_w)/2:y=(h-text_h)/2+80:font=Arial,` +
      `fade=t=in:st=0:d=${FADE_IN}[${introLabel}]`,
    `anullsrc=r=44100:cl=stereo:d=${INTRO_DURATION}[${introALabel}]`
  );
  concatInputs.push({ v: introLabel, a: introALabel });

  // --- Content segments ---
  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i];
    const vLabel = `seg${i}_v`;
    const aLabel = `seg${i}_a`;

    if (seg.type === "keep") {
      const duration = seg.end - seg.start;
      filterParts.push(
        `[0:v]trim=start=${seg.start}:end=${seg.end},setpts=PTS-STARTPTS,scale=${RESOLUTION}:force_original_aspect_ratio=decrease,pad=${RESOLUTION}:-1:-1:color=black[${vLabel}]`,
        `[0:a]atrim=start=${seg.start}:end=${seg.end},asetpts=PTS-STARTPTS[${aLabel}]`
      );
      concatInputs.push({ v: vLabel, a: aLabel });
    } else if (seg.type === "compress") {
      const speed = seg.speed || 3;
      // Speed up video and mute audio (replace with silence)
      const duration = (seg.end - seg.start) / speed;
      filterParts.push(
        `[0:v]trim=start=${seg.start}:end=${seg.end},setpts=(PTS-STARTPTS)/${speed},scale=${RESOLUTION}:force_original_aspect_ratio=decrease,pad=${RESOLUTION}:-1:-1:color=black[${vLabel}]`,
        `anullsrc=r=44100:cl=stereo:d=${duration}[${aLabel}]`
      );
      concatInputs.push({ v: vLabel, a: aLabel });
    }
  }

  // --- Outro card ---
  const outroLabel = `outro_v`;
  const outroALabel = `outro_a`;
  filterParts.push(
    `color=c=black:s=${RESOLUTION}:d=${OUTRO_DURATION}:r=${FPS}[outro_bg]`,
    `[outro_bg]drawtext=text='Thanks for watching!':fontcolor=white:fontsize=56:x=(w-text_w)/2:y=(h-text_h)/2-40:font=Arial,` +
      `drawtext=text='Cognotic':fontcolor=0x888888:fontsize=32:x=(w-text_w)/2:y=(h-text_h)/2+40:font=Arial,` +
      `fade=t=out:st=${OUTRO_DURATION - FADE_OUT}:d=${FADE_OUT}[${outroLabel}]`,
    `anullsrc=r=44100:cl=stereo:d=${OUTRO_DURATION}[${outroALabel}]`
  );
  concatInputs.push({ v: outroLabel, a: outroALabel });

  // --- Concatenation ---
  const concatStreamList = concatInputs
    .map((s) => `[${s.v}][${s.a}]`)
    .join("");
  const concatCount = concatInputs.length;
  filterParts.push(
    `${concatStreamList}concat=n=${concatCount}:v=1:a=1[outv_raw][outa_raw]`
  );

  // --- Audio normalization and crossfade smoothing ---
  // Apply a limiter and normalize audio levels
  filterParts.push(
    `[outa_raw]dynaudnorm=p=0.9:s=5[outa]`,
    `[outv_raw]copy[outv]`
  );

  const filterComplex = filterParts.join(";\n");

  const cmd = [
    "ffmpeg",
    "-y",
    `-i "${INPUT}"`,
    `-filter_complex "${filterComplex}"`,
    `-map "[outv]"`,
    `-map "[outa]"`,
    `-c:v libx264 -preset medium -crf 20`,
    `-c:a aac -b:a 192k`,
    `-movflags +faststart`,
    `"${OUTPUT}"`,
  ].join(" \\\n  ");

  return cmd;
}

// --- Main ---
function main() {
  if (!fs.existsSync(INPUT)) {
    console.error(`Error: Input file not found: ${INPUT}`);
    console.error(`Expected source video at: ${INPUT}`);
    process.exit(1);
  }

  const cmd = buildCommand();

  // Write the command to a shell script for inspection
  const scriptPath = path.join(OUTPUT_DIR, "Sys_Wizard_edit.sh");
  fs.writeFileSync(scriptPath, `#!/bin/bash\n\n${cmd}\n`, { mode: 0o755 });
  console.log(`Edit script written to: ${scriptPath}`);
  console.log(`\nExecuting ffmpeg...\n`);
  console.log(cmd);
  console.log("");

  try {
    execSync(cmd, { stdio: "inherit", shell: true });
    console.log(`\nOutput saved to: ${OUTPUT}`);
  } catch (err) {
    console.error(`\nffmpeg failed with exit code ${err.status}`);
    process.exit(err.status || 1);
  }
}

main();