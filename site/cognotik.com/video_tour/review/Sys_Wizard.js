#!/usr/bin/env node
// =============================================================================
// Sys_Wizard.js — Automated video edit script for System Wizard demo
// =============================================================================
// No npm dependencies required.
// Requires: ffmpeg and ffprobe on PATH
//
// Usage:
//   node Sys_Wizard.js
// =============================================================================

const veu = require('./lib/video-edit-utils');
const path = require('path');

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------
const INPUT  = path.resolve(__dirname, '../source/Sys_Wizard.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Sys_Wizard.mp4');
const TEMP   = path.join(require('os').tmpdir(), 'sys_wizard_edit_' + Date.now());

// ---------------------------------------------------------------------------
// Segment Definitions (Edit Decision List)
//
// Derived from the chronological editing plan in Sys_Wizard.md
// ---------------------------------------------------------------------------
const segments = [
  // --- Segment 1: Opening false starts — all cut ---
  { id: 'seg01_false_start_1',   start: '00:00:03.680', end: '00:00:09.839', action: 'cut',  label: 'False start: "The SIS Wizard, the SIS Wizard app is."' },
  { id: 'seg02_false_start_2',   start: '00:00:09.839', end: '00:00:15.609', action: 'cut',  label: 'False start: mispronunciation + filler "Uh"' },
  { id: 'seg03_dead_air',        start: '00:00:15.609', end: '00:00:20.379', action: 'cut',  label: 'Dead air / silence gap' },

  // --- Segment 2: Describing the problem ---
  { id: 'seg04_stumble_cut',     start: '00:00:20.379', end: '00:00:23.329', action: 'cut',  label: 'Stumbled phrasing: "Sometimes you need to do something..."' },
  { id: 'seg05_problem_desc',    start: '00:00:23.329', end: '00:00:41.209', action: 'keep', label: 'Describing the problem — clean restart' },

  // --- Segment 3: Introducing the AI solution ---
  { id: 'seg06_ai_intro',        start: '00:00:41.950', end: '00:00:47.179', action: 'keep', label: 'AI value proposition: "with AI and Cognotic..."' },
  { id: 'seg07_false_start_trim',start: '00:00:47.179', end: '00:00:52.149', action: 'cut',  label: 'False start: "You start with the, you start..."' },
  { id: 'seg08_goal_tab',        start: '00:00:52.149', end: '00:00:58.109', action: 'keep', label: 'Clean instruction: "start at the goal tab..."' },

  // --- Segment 4: Typing the goal (screen demo) ---
  { id: 'seg09_typing_stumble',  start: '00:00:58.109', end: '00:01:06.308', action: 'cut',  label: 'Stumbled while typing: "Let\'s, for this demonstration, do..."' },
  {
    id: 'seg10_typing_wait',     start: '00:01:06.308', end: '00:01:10.638', action: 'compress',
    targetDuration: 2, overlay: 'Typing...', muteAudio: true,
    label: 'Typing/thinking silence — compress to ~2s'
  },
  { id: 'seg11_goal_saved',      start: '00:01:10.638', end: '00:01:15.430', action: 'keep', label: '"Let\'s see your running processes. We saved the goal."' },

  // --- Segment 5: Settings & configuration ---
  { id: 'seg12_settings',        start: '00:01:15.430', end: '00:01:45.250', action: 'keep', label: 'Settings walkthrough: Windows, PowerShell, model selection' },

  // --- Segment 6: Script generation (processing wait) ---
  {
    id: 'seg13_llm_wait',        start: '00:01:45.250', end: '00:01:56.638', action: 'compress',
    targetDuration: 3.5, overlay: 'Generating script...', muteAudio: true,
    label: 'LLM processing wait — compress to ~3.5s'
  },
  { id: 'seg14_result_arrives',  start: '00:01:56.638', end: '00:02:05.588', action: 'keep', label: '"And here we go." + explanation of options' },

  // --- Segment 7: Run and fix feature ---
  { id: 'seg15_run_fix_intro',   start: '00:02:05.588', end: '00:02:08.849', action: 'keep', label: '"click run and fix to test the script"' },
  {
    id: 'seg16_ui_click_wait',   start: '00:02:08.849', end: '00:02:14.308', action: 'compress',
    targetDuration: 2, overlay: 'Running script...', muteAudio: true,
    label: 'UI click/wait — compress to ~2s'
  },
  { id: 'seg17_autofix_session', start: '00:02:14.308', end: '00:02:19.819', action: 'keep', label: '"Now, in this run and autofix session..."' },

  // --- Segment 8: Error handling explanation ---
  { id: 'seg18_error_handling',  start: '00:02:19.819', end: '00:02:29.679', action: 'keep', label: 'Error handling: "If an error occurs... try to code up a fix."' },
  { id: 'seg19_repetitive_cut',  start: '00:02:29.679', end: '00:02:36.639', action: 'cut',  label: 'Repetitive/stumbled rephrasing of error handling' },
  { id: 'seg20_retry_success',   start: '00:02:36.639', end: '00:02:46.199', action: 'keep', label: '"retry the execution... first execution was successful..."' },

  // --- Segment 9: Viewing results ---
  { id: 'seg21_view_results',    start: '00:02:46.199', end: '00:03:00.479', action: 'keep', label: 'Viewing results and UX notes' },

  // --- Segment 10: Closing remarks ---
  { id: 'seg22_repetitive_close',start: '00:03:00.479', end: '00:03:04.919', action: 'cut',  label: 'Repetitive: "And there you go. That is the system wizard."' },
  { id: 'seg23_closing',         start: '00:03:04.919', end: '00:03:07.860', action: 'keep', label: '"That is the system wizard application. I hope you find it useful."' },
];

// ---------------------------------------------------------------------------
// Run the edit pipeline
// ---------------------------------------------------------------------------
veu.runEditPipeline({
  inputPath:     INPUT,
  outputPath:    OUTPUT,
  tempDir:       TEMP,

  // Intro billboard
  introTitle:    'System Wizard',
  introSubtitle: 'AI-Powered Shell Scripting',
  introDuration: 3,

  // Outro billboard
  outroLines: [
    'Thanks for watching!',
    'Cognotic — AI-Powered Development Tools',
  ],
  outroDuration: 4,

  // Edit decision list
  segments,

  // Post-processing
  normalizeAudio: true,
  concatMethod:   'demuxer',
});

console.log('\n✅ Edit complete:', OUTPUT);