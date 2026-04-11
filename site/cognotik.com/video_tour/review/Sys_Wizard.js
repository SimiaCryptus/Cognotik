#!/usr/bin/env node
// =============================================================================
// Sys_Wizard.js — Automated edit script for System Wizard demo video
// =============================================================================
// No npm dependencies required. Requires:
//   - Node.js 14+
//   - FFmpeg and FFprobe on PATH
//
// Usage:
//   node Sys_Wizard.js
// =============================================================================

const veu = require('./lib/video-edit-utils');
const path = require('path');
const os = require('os');

// ---------------------------------------------------------------------------
// 1. Define paths
// ---------------------------------------------------------------------------
const INPUT  = path.resolve(__dirname, '../source/Sys_Wizard.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Sys_Wizard.mp4');
const TEMP   = path.join(os.tmpdir(), 'sys_wizard_edit_' + Date.now());

// ---------------------------------------------------------------------------
// 2. Define segments (edit decision list)
//
// Derived from Sys_Wizard.json — each segment maps to a keep, cut, or
// compress action. Timecodes use M:SS.mmm format from the JSON source.
//
// Segment index 1: Introduction – What is System Wizard?
//   0:03.680–0:09.600  cut    (false-start intro)
//   0:09.600–0:13.859  keep   (clean intro statement)
//   0:13.859–0:20.379  cut    ('Uh.' filler + long pause)
//   0:20.379–0:26.090  cut    (false-start explanation)
//   0:26.090–0:39.909  keep   (clean explanation)
//   0:39.909–0:48.439  keep   (value proposition)
//
// Segment index 2: Setting the Goal
//   0:48.439–0:50.310  keep   (implicit — gap between segments)
//   0:50.310–0:51.689  cut    (false start)
//   0:51.950–0:56.630  keep   (clean instruction)
//   0:56.779–0:58.500  keep   ('Let's, for this demonstration' — trimmed)
//   0:58.500–1:10.639  cut    (hesitation + dead air while typing)
//   1:10.639–1:12.330  keep   (actual goal spoken)
//   1:13.379–1:14.220  keep   (confirmation)
//
// Segment index 3: Configuring Settings and Generating the Script
//   1:14.980–1:16.910  keep   (platform context)
//   1:17.029–1:19.489  keep   ('This also supports Shell, which is...')
//   1:19.489–1:20.599  cut    ('um,' filler)
//   1:20.599–1:26.879  keep   (platform details)
//   1:27.449–1:30.169  keep   (navigation narration)
//   1:31.709–1:37.650  keep   (model selection)
//   1:38.190–1:41.830  keep   (pipeline action)
//   1:41.830–1:44.970  cut    (tighten gap — ~3s pause)
//   1:44.970–1:47.050  keep   (transition statement)
//
// Segment index 4: Waiting for Script Generation
//   1:47.050–1:56.629  compress 3x → ~3s (LLM generation wait)
//
// Segment index 5: Reviewing the Generated Script
//   1:56.629–1:56.669  keep   ('And here we go.')
//   1:56.669–2:02.510  compress 3x → ~2s (script review pause)
//   2:02.510–2:08.850  keep   (explanation of options)
//
// Segment index 6: Running the Script with Auto-Fix
//   2:08.850–2:14.309  compress 3x → ~2s (execution wait)
//   2:14.309–2:18.059  keep   (auto-fix context)
//   2:19.449–2:28.419  keep   (error handling explanation)
//   2:29.039–2:32.740  keep   (self-correction — kept corrected version)
//   2:32.740–2:36.440  cut    (stumbled self-correction)
//   2:36.440–2:40.259  keep   (clean corrected statement)
//   2:40.789–2:47.839  keep   (success result)
//
// Segment index 7: UI Notes and Wrap-Up
//   2:48.770–2:54.889  keep   (UI clarification)
//   2:55.210–2:57.610  keep   (session link instruction)
//   3:00.039–3:02.660  keep   (closing)
//   3:04.440–3:07.860  keep   (final sign-off)
// ---------------------------------------------------------------------------

const segments = [
  // === Segment 1: Introduction – What is System Wizard? ===
  { id: 'seg01_false_start',       start: '00:00:03.680', end: '00:00:09.600', action: 'cut',  label: 'Cut false-start intro' },
  { id: 'seg02_clean_intro',       start: '00:00:09.600', end: '00:00:13.859', action: 'keep', label: 'Clean intro: app is powerful and dangerous' },
  { id: 'seg03_uh_filler',         start: '00:00:13.859', end: '00:00:20.379', action: 'cut',  label: 'Cut "Uh." filler and long pause' },
  { id: 'seg04_false_start_2',     start: '00:00:20.379', end: '00:00:26.090', action: 'cut',  label: 'Cut false-start explanation' },
  { id: 'seg05_clean_explanation', start: '00:00:26.090', end: '00:00:39.909', action: 'keep', label: 'Clean explanation of use cases' },
  { id: 'seg06_value_prop',        start: '00:00:39.909', end: '00:00:48.439', action: 'keep', label: 'Value proposition: LLM writes shell scripts' },

  // === Segment 2: Setting the Goal ===
  { id: 'seg07_goal_intro',        start: '00:00:48.439', end: '00:00:50.310', action: 'keep', label: 'Transition to goal tab' },
  { id: 'seg08_false_start_3',     start: '00:00:50.310', end: '00:00:51.689', action: 'cut',  label: 'Cut false start: "You start with the,"' },
  { id: 'seg09_clean_instruction', start: '00:00:51.950', end: '00:00:56.630', action: 'keep', label: 'Clean instruction: goal tab usage' },
  { id: 'seg10_demo_intro',        start: '00:00:56.779', end: '00:00:58.500', action: 'keep', label: '"Let\'s, for this demonstration" (trimmed)' },
  { id: 'seg11_hesitation',        start: '00:00:58.500', end: '00:01:10.639', action: 'cut',  label: 'Cut hesitation and dead air while typing' },
  { id: 'seg12_goal_spoken',       start: '00:01:10.639', end: '00:01:12.330', action: 'keep', label: 'Goal spoken: "list running processes"' },
  { id: 'seg13_goal_saved',        start: '00:01:13.379', end: '00:01:14.220', action: 'keep', label: 'Confirmation: "We saved the goal"' },

  // === Segment 3: Configuring Settings and Generating the Script ===
  { id: 'seg14_platform',          start: '00:01:14.980', end: '00:01:16.910', action: 'keep', label: 'Platform context: Windows' },
  { id: 'seg15_shell_support',     start: '00:01:17.029', end: '00:01:19.489', action: 'keep', label: 'Shell support mention' },
  { id: 'seg16_um_filler',         start: '00:01:19.489', end: '00:01:20.599', action: 'cut',  label: 'Cut "um," filler' },
  { id: 'seg17_platform_details',  start: '00:01:20.599', end: '00:01:26.879', action: 'keep', label: 'Platform details: Bash vs PowerShell' },
  { id: 'seg18_nav_narration',     start: '00:01:27.449', end: '00:01:30.169', action: 'keep', label: 'Navigation narration' },
  { id: 'seg19_model_selection',   start: '00:01:31.709', end: '00:01:37.650', action: 'keep', label: 'Model selection: Gemini 3 Flash' },
  { id: 'seg20_pipeline_action',   start: '00:01:38.190', end: '00:01:41.830', action: 'keep', label: 'Pipeline: generate shell script' },
  { id: 'seg21_tighten_gap',       start: '00:01:41.830', end: '00:01:44.970', action: 'cut',  label: 'Tighten gap before transition statement' },
  { id: 'seg22_transition_stmt',   start: '00:01:44.970', end: '00:01:47.050', action: 'keep', label: '"Generated momentarily"' },

  // === Segment 4: Waiting for Script Generation ===
  { id: 'seg23_gen_wait',          start: '00:01:47.050', end: '00:01:56.629', action: 'compress', targetDuration: 3, overlay: '⏳ Generating shell script...', muteAudio: true, label: 'Time-compress LLM generation wait' },

  // === Segment 5: Reviewing the Generated Script ===
  { id: 'seg24_here_we_go',        start: '00:01:56.629', end: '00:01:56.669', action: 'keep', label: '"And here we go"' },
  { id: 'seg25_review_pause',      start: '00:01:56.669', end: '00:02:02.510', action: 'compress', targetDuration: 2, overlay: 'Reviewing generated script...', muteAudio: true, label: 'Time-compress script review pause' },
  { id: 'seg26_options_explain',   start: '00:02:02.510', end: '00:02:08.850', action: 'keep', label: 'Explanation of run options' },

  // === Segment 6: Running the Script with Auto-Fix ===
  { id: 'seg27_exec_wait',         start: '00:02:08.850', end: '00:02:14.309', action: 'compress', targetDuration: 2, overlay: 'Executing script...', muteAudio: true, label: 'Time-compress execution wait' },
  { id: 'seg28_autofix_context',   start: '00:02:14.309', end: '00:02:18.059', action: 'keep', label: 'Auto-fix session context' },
  { id: 'seg29_error_handling',    start: '00:02:19.449', end: '00:02:28.419', action: 'keep', label: 'Error handling explanation' },
  { id: 'seg30_self_correction',   start: '00:02:29.039', end: '00:02:32.740', action: 'keep', label: 'Self-correction (kept corrected version)' },
  { id: 'seg31_stumble_cut',       start: '00:02:32.740', end: '00:02:36.440', action: 'cut',  label: 'Cut stumbled self-correction' },
  { id: 'seg32_clean_corrected',   start: '00:02:36.440', end: '00:02:40.259', action: 'keep', label: 'Clean corrected statement' },
  { id: 'seg33_success_result',    start: '00:02:40.789', end: '00:02:47.839', action: 'keep', label: 'Success result: processes listed' },

  // === Segment 7: UI Notes and Wrap-Up ===
  { id: 'seg34_ui_clarification',  start: '00:02:48.770', end: '00:02:54.889', action: 'keep', label: 'UI clarification: command succeeded' },
  { id: 'seg35_session_link',      start: '00:02:55.210', end: '00:02:57.610', action: 'keep', label: 'Session link instruction' },
  { id: 'seg36_closing',           start: '00:03:00.039', end: '00:03:02.660', action: 'keep', label: 'Closing: "That is the system wizard"' },
  { id: 'seg37_signoff',           start: '00:03:04.440', end: '00:03:07.860', action: 'keep', label: 'Final sign-off' },
];

// ---------------------------------------------------------------------------
// 3. Run the edit pipeline
// ---------------------------------------------------------------------------
veu.runEditPipeline({
  inputPath:  INPUT,
  outputPath: OUTPUT,
  tempDir:    TEMP,

  // Intro billboard
  introTitle:    'System Wizard',
  introSubtitle: 'AI-Powered Shell Script Generation & Execution',
  introDuration: 3,

  // Outro billboard
  outroLines: [
    'System Wizard',
    'Part of the Cognotik Platform',
    'Thanks for watching!',
    'Try it yourself at cognotic.dev',
  ],
  outroDuration: 4,

  // Transition duration between segments (crossfade)
  transitionDuration: 0.5,

  // Segment definitions
  segments,

  // Audio normalization as final pass
  normalizeAudio: true,

  // Use concat demuxer (all segments will have consistent encoding)
  concatMethod: 'demuxer',
});

console.log('✅ Edit complete:', OUTPUT);