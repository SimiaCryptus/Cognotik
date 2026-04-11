#!/usr/bin/env node
// Video edit script for: Web App Factory – Graphing Calculator Demo
// Generated from: WebApp_Factory.json
//
// Requirements:
//   - Node.js 14+
//   - ffmpeg and ffprobe on PATH
//   - No npm dependencies
//
// Usage:
//   node WebApp_Factory.js

const path = require('path');
const os = require('os');
const veu = require('./lib/video-edit-utils');

// ── Paths ──────────────────────────────────────────────────────────────────────
const INPUT  = path.resolve(__dirname, '../source/WebApp_Factory.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/WebApp_Factory.mp4');
const TEMP   = path.join(os.tmpdir(), 'WebApp_Factory_' + Date.now());

// ── Segment Definitions (Edit Decision List) ───────────────────────────────────
//
// The JSON defines edits at a very granular level with "tighten" actions (reduce
// pauses) and fine-grained cuts within segments. We flatten these into a linear
// sequence of keep / compress / cut segments for the pipeline.
//
// "tighten" segments are treated as cuts — the pause is removed entirely.
// "edit" with secondaryAction "cut" removes the false-start sub-range.
// Adjacent keeps are merged where practical for cleaner processing.

const segments = [
  // ── Segment 1: Introduction & Setup ──────────────────────────────────────
  // 0:04.349 – 0:09.010  keep  "To generate a web application..."
  {
    id: 'seg01_intro_opening',
    start: '00:00:04.349',
    end: '00:00:09.010',
    action: 'keep',
    label: 'Opening explanation',
  },
  // 0:09.010 – 0:10.109  tighten (cut)
  // 0:10.109 – 0:16.959  keep  "Simply putting in some details..."
  {
    id: 'seg01_enter_details',
    start: '00:00:10.109',
    end: '00:00:16.959',
    action: 'keep',
    label: 'Describes entering details',
  },
  // 0:16.959 – 0:22.719  cut (pause + mis-spoken "Graphic")
  // 0:22.719 – 0:26.670  keep  "Implements a graphing calculator. Save your idea."
  {
    id: 'seg01_save_idea',
    start: '00:00:22.719',
    end: '00:00:26.670',
    action: 'keep',
    label: 'Save your idea',
  },
  // 0:26.670 – 0:27.950  tighten (cut)
  // 0:27.950 – 0:44.009  keep  Model selection explanation
  {
    id: 'seg01_model_selection',
    start: '00:00:27.950',
    end: '00:00:44.009',
    action: 'keep',
    label: 'Model selection explanation',
  },

  // ── Segment 2: Launching the Pipeline ────────────────────────────────────
  // 0:44.009 – 0:44.650  tighten (cut)
  // 0:44.650 – 0:48.330  keep
  {
    id: 'seg02_pipeline_nav',
    start: '00:00:44.650',
    end: '00:00:48.330',
    action: 'keep',
    label: 'Navigate to pipeline',
  },
  // 0:48.330 – 0:55.409  keep
  {
    id: 'seg02_pipeline_explain',
    start: '00:00:48.330',
    end: '00:00:55.409',
    action: 'keep',
    label: 'Pipeline step explanation',
  },
  // 0:55.409 – 0:57.250  keep  "It starts by generating..."
  // 0:57.250 – 0:58.110  tighten (cut)
  // 0:58.110 – 1:09.260  keep  "Project plan of tasks..."
  // Merge the two keeps around the tighten for cleaner output:
  {
    id: 'seg02_starts_generating',
    start: '00:00:55.409',
    end: '00:00:57.250',
    action: 'keep',
    label: 'It starts by generating',
  },
  {
    id: 'seg02_project_plan',
    start: '00:00:58.110',
    end: '00:01:09.260',
    action: 'keep',
    label: 'Project plan explanation',
  },

  // ── Segment 3: Watching the Project Plan Execute ─────────────────────────
  // 1:09.260 – 1:17.620  time-compress-and-mute (3x → ~3s)
  {
    id: 'seg03_gen_plan_wait',
    start: '00:01:09.260',
    end: '00:01:17.620',
    action: 'compress',
    targetDuration: 3,
    overlay: 'Generating project plan...',
    muteAudio: true,
    label: 'Waiting for project plan (compressed)',
  },
  // 1:17.620 – 1:24.290  keep
  {
    id: 'seg03_plan_visible',
    start: '00:01:17.620',
    end: '00:01:24.290',
    action: 'keep',
    label: 'Project plan with 5 tasks',
  },
  // 1:24.290 – 1:25.870  tighten (cut)
  // 1:25.870 – 1:30.410  keep
  {
    id: 'seg03_graph_update',
    start: '00:01:25.870',
    end: '00:01:30.410',
    action: 'keep',
    label: 'Graph will update',
  },
  // 1:30.410 – 1:33.069  tighten (cut)
  // 1:33.069 – 1:35.879  keep
  {
    id: 'seg03_wait_msg',
    start: '00:01:33.069',
    end: '00:01:35.879',
    action: 'keep',
    label: 'Wait until project is implemented',
  },

  // ── Segment 4: Waiting for Implementation (Long Processing) ──────────────
  // 1:35.879 – 2:05.180  time-compress-and-mute (6x → ~5s)
  {
    id: 'seg04_build_wait',
    start: '00:01:35.879',
    end: '00:02:05.180',
    action: 'compress',
    targetDuration: 5,
    overlay: 'Building project... ⏳',
    muteAudio: true,
    label: 'Building project (compressed)',
  },

  // ── Segment 5: Reviewing the Task Graph & Tasks ──────────────────────────
  // 2:05.180 – 2:12.639  keep
  {
    id: 'seg05_nodes_display',
    start: '00:02:05.180',
    end: '00:02:12.639',
    action: 'keep',
    label: 'Nodes not displaying correctly',
  },
  // 2:12.639 – 2:17.619  time-compress-and-mute (3x → ~2s)
  {
    id: 'seg05_nav_tasks',
    start: '00:02:12.639',
    end: '00:02:17.619',
    action: 'compress',
    targetDuration: 2,
    muteAudio: true,
    label: 'Navigating to tasks (compressed)',
  },
  // 2:17.619 – 2:28.240  keep
  {
    id: 'seg05_task_details',
    start: '00:02:17.619',
    end: '00:02:28.240',
    action: 'keep',
    label: 'Foundational documents and core logic',
  },

  // ── Segment 6: Waiting for Core Logic Implementation ─────────────────────
  // 2:28.240 – 2:52.699  time-compress-and-mute (6x → ~4s)
  {
    id: 'seg06_core_logic_wait',
    start: '00:02:28.240',
    end: '00:02:52.699',
    action: 'compress',
    targetDuration: 4,
    overlay: 'Implementing core logic...',
    muteAudio: true,
    label: 'Core logic implementation (compressed)',
  },
  // 2:52.699 – 2:57.720  keep  (note: JSON end is 2:57.720 for seg6 keep portion)
  {
    id: 'seg06_ui_update_msg',
    start: '00:02:52.699',
    end: '00:02:57.720',
    action: 'keep',
    label: 'Web app UI will update',
  },

  // ── Segment 7: Waiting for UI Update ─────────────────────────────────────
  // 2:57.720 – 3:14.270  time-compress-and-mute (6x → ~3s)
  {
    id: 'seg07_ui_finalize',
    start: '00:02:57.720',
    end: '00:03:14.270',
    action: 'compress',
    targetDuration: 3,
    overlay: 'Finalizing web app...',
    muteAudio: true,
    label: 'Finalizing web app (compressed)',
  },

  // ── Segment 8: Implementation Complete – Reviewing Results ───────────────
  // 3:14.270 – 3:19.320  keep
  {
    id: 'seg08_impl_done',
    start: '00:03:14.270',
    end: '00:03:19.320',
    action: 'keep',
    label: 'Implementation done, readme visible',
  },
  // 3:19.320 – 3:20.199  tighten (cut)
  // 3:20.199 – 3:23.380  keep
  {
    id: 'seg08_download_zip',
    start: '00:03:20.199',
    end: '00:03:23.380',
    action: 'keep',
    label: 'Download zip option',
  },

  // ── Segment 9: Git Support Explanation ───────────────────────────────────
  // 3:23.380 – 3:24.940  tighten (cut)
  // 3:24.940 – 3:30.910  keep
  {
    id: 'seg09_git_intro',
    start: '00:03:24.940',
    end: '00:03:30.910',
    action: 'keep',
    label: 'Git support integrated',
  },
  // 3:30.910 – 3:38.860  edit: cut false start 3:31.279–3:33.320, keep rest
  // Before false start: 3:30.910 – 3:31.279 (very short, ~0.37s — "If you know")
  //   This is the false start itself, so we cut it too.
  // After false start: 3:33.320 – 3:38.860 keep
  //   "if you don't know what Git is, it is a version control system that essentially allows you to."
  {
    id: 'seg09_git_explain',
    start: '00:03:33.320',
    end: '00:03:38.860',
    action: 'keep',
    label: 'Git explanation (false start removed)',
  },
  // 3:38.860 – 3:42.199  cut (silence)
  // 3:42.199 – 3:43.869  keep  "Version control files."
  {
    id: 'seg09_version_control',
    start: '00:03:42.199',
    end: '00:03:43.869',
    action: 'keep',
    label: 'Version control files',
  },
  // 3:43.869 – 3:52.669  cut (silence during UI interaction)
  // 3:52.669 – 3:55.809  keep  "I'm not sure what that error was about, but."
  {
    id: 'seg09_error_mention',
    start: '00:03:52.669',
    end: '00:03:55.809',
    action: 'keep',
    label: 'Error acknowledgment',
  },
  // 3:55.809 – 3:57.009  tighten (cut)
  // 3:57.009 – 3:59.190  keep  "It did save the files to Git."
  {
    id: 'seg09_git_saved',
    start: '00:03:57.009',
    end: '00:03:59.190',
    action: 'keep',
    label: 'Files saved to Git',
  },

  // ── Segment 10: Launching & Testing the App ──────────────────────────────
  // 3:59.190 – 4:02.630  tighten (cut)
  // 4:02.630 – 4:06.009  keep
  {
    id: 'seg10_launch_app',
    start: '00:04:02.630',
    end: '00:04:06.009',
    action: 'keep',
    label: 'Launch the app',
  },
  // 4:06.009 – 4:07.470  tighten (cut)
  // 4:07.470 – 4:11.429  keep
  {
    id: 'seg10_sin_x',
    start: '00:04:07.470',
    end: '00:04:11.429',
    action: 'keep',
    label: 'Plotting sin of x',
  },
  // 4:11.429 – 4:13.730  keep
  {
    id: 'seg10_test_formula',
    start: '00:04:11.429',
    end: '00:04:13.730',
    action: 'keep',
    label: 'Testing formulas',
  },

  // ── Segment 11: Playing with the Calculator ──────────────────────────────
  // 4:13.730 – 4:15.669  tighten (cut)
  // 4:15.669 – 4:18.109  keep
  {
    id: 'seg11_looks_right',
    start: '00:04:15.669',
    end: '00:04:18.109',
    action: 'keep',
    label: 'That looks about right',
  },
  // 4:18.109 – 4:21.070  keep
  {
    id: 'seg11_divide',
    start: '00:04:18.109',
    end: '00:04:21.070',
    action: 'keep',
    label: 'Divide instead of times',
  },
  // 4:21.070 – 4:22.049  keep  "What about times?"
  {
    id: 'seg11_times',
    start: '00:04:21.070',
    end: '00:04:22.049',
    action: 'keep',
    label: 'What about times',
  },
  // 4:22.049 – 4:24.730  time-compress-and-mute (3x → ~1s)
  {
    id: 'seg11_graph_wait',
    start: '00:04:22.049',
    end: '00:04:24.730',
    action: 'compress',
    targetDuration: 1,
    muteAudio: true,
    label: 'Waiting for graph update (compressed)',
  },
  // 4:24.730 – 4:27.489  keep
  {
    id: 'seg11_power',
    start: '00:04:24.730',
    end: '00:04:27.489',
    action: 'keep',
    label: 'Power looks interesting',
  },
  // 4:27.489 – 4:28.440  tighten (cut)
  // 4:28.440 – 4:31.279  keep
  {
    id: 'seg11_seems_to_work',
    start: '00:04:28.440',
    end: '00:04:31.279',
    action: 'keep',
    label: 'Seems to work',
  },

  // ── Segment 12: Requesting Dark Theme Update ─────────────────────────────
  // 4:31.279 – 4:32.890  tighten (cut)
  // 4:32.890 – 4:33.730  keep  "It's too bright."
  {
    id: 'seg12_too_bright',
    start: '00:04:32.890',
    end: '00:04:33.730',
    action: 'keep',
    label: 'Too bright',
  },
  // 4:33.730 – 4:35.959  tighten (cut)
  // 4:35.959 – 4:39.119  keep
  {
    id: 'seg12_ask_changes',
    start: '00:04:35.959',
    end: '00:04:39.119',
    action: 'keep',
    label: 'Ask for changes',
  },
  // 4:39.119 – 4:44.510  keep
  {
    id: 'seg12_updater_explain',
    start: '00:04:39.119',
    end: '00:04:44.510',
    action: 'keep',
    label: 'Update web app using updater',
  },
  // 4:44.510 – 4:46.079  tighten (cut)
  // 4:46.079 – 4:47.450  keep  "Implement a dark..."
  {
    id: 'seg12_implement_dark',
    start: '00:04:46.079',
    end: '00:04:47.450',
    action: 'keep',
    label: 'Implement a dark',
  },
  // 4:47.450 – 4:49.250  cut (pause while typing)
  // 4:49.250 – 4:50.089  keep  "A theme."
  {
    id: 'seg12_a_theme',
    start: '00:04:49.250',
    end: '00:04:50.089',
    action: 'keep',
    label: 'A theme',
  },
  // 4:50.089 – 4:56.059  cut (silence while typing)
  // 4:56.059 – 5:02.079  keep
  {
    id: 'seg12_run_update',
    start: '00:04:56.059',
    end: '00:05:02.079',
    action: 'keep',
    label: 'Save notes and run update',
  },

  // ── Segment 13: Update Processing & Results ──────────────────────────────
  // 5:02.079 – 5:03.589  tighten (cut)
  // 5:03.589 – 5:10.290  keep
  {
    id: 'seg13_useful_updates',
    start: '00:05:03.589',
    end: '00:05:10.290',
    action: 'keep',
    label: 'Useful for bugs and features',
  },
  // 5:10.290 – 5:11.109  tighten (cut)
  // 5:11.109 – 5:12.269  keep
  {
    id: 'seg13_update_done',
    start: '00:05:11.109',
    end: '00:05:12.269',
    action: 'keep',
    label: 'Update is done',
  },
  // 5:12.269 – 5:16.470  keep
  {
    id: 'seg13_refresh',
    start: '00:05:12.269',
    end: '00:05:16.470',
    action: 'keep',
    label: 'Refresh graphing calculator',
  },
  // 5:16.470 – 5:17.470  tighten (cut — but keep reaction beat short)
  // 5:17.470 – 5:20.410  keep
  {
    id: 'seg13_theme_works',
    start: '00:05:17.470',
    end: '00:05:20.410',
    action: 'keep',
    label: 'Theme button works',
  },

  // ── Segment 14: Wrap-Up & Usage Cost ─────────────────────────────────────
  // 5:20.410 – 5:21.470  tighten (cut)
  // 5:21.470 – 5:26.690  keep
  {
    id: 'seg14_wrapup',
    start: '00:05:21.470',
    end: '00:05:26.690',
    action: 'keep',
    label: 'How to use web app factory',
  },
  // 5:26.690 – 5:27.640  tighten (cut)
  // 5:27.640 – 5:37.019  keep
  {
    id: 'seg14_usage_cost',
    start: '00:05:27.640',
    end: '00:05:37.019',
    action: 'keep',
    label: 'Usage cost 11 cents',
  },
  // 5:37.019 – 5:39.220  keep
  {
    id: 'seg14_closing',
    start: '00:05:37.019',
    end: '00:05:39.220',
    action: 'keep',
    label: 'Hope you find this useful',
  },
];

// ── Run Pipeline ───────────────────────────────────────────────────────────────
veu.runEditPipeline({
  inputPath: INPUT,
  outputPath: OUTPUT,
  tempDir: TEMP,

  // Intro billboard
  introTitle: 'Web App Factory',
  introSubtitle: 'Generate full web applications with AI',
  introDuration: 3,

  // Outro billboard
  outroLines: [
    'Thanks for Watching!',
    'Web App Factory – Build apps with AI',
    'Like & Subscribe for more demos',
  ],
  outroDuration: 4,

  // Transition duration between segments
  transitionDuration: 0.5,

  // Edit decision list
  segments,

  // Post-processing
  normalizeAudio: true,

  // Use demuxer concat (all segments will have consistent encoding)
  concatMethod: 'demuxer',
});

console.log('✅ Edit complete: ' + OUTPUT);