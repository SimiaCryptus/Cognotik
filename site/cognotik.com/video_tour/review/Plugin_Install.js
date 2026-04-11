#!/usr/bin/env node
// Edit script for Plugin_Install
// Requirements: Node.js 14+, ffmpeg and ffprobe on PATH
// No npm dependencies required

const path = require('path');
const os = require('os');
const veu = require('./lib/video-edit-utils');

// --- Paths ---
const INPUT  = path.resolve(__dirname, '../source/Plugin_Install.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Plugin_Install.mp4');
const TEMP   = path.join(os.tmpdir(), 'plugin_install_' + Date.now());

// --- Segment Definitions (Edit Decision List) ---
// Derived from Plugin_Install.json edit instructions
//
// Timecodes in the JSON use "M:SS.mmm" format; converted to "HH:MM:SS.mmm" below.
//
// Actions mapped:
//   "keep"                    → action: 'keep'
//   "trim" (dead air)         → action: 'cut'
//   "tighten"                 → action: 'compress' with compressedDuration
//   "time-compress-and-mute"  → action: 'compress' with targetDuration, muteAudio, overlay
//   "edit" + "tighten"        → split into keep + compress sub-segments

const segments = [
    // === Intro dead air (trimmed — replaced by intro billboard) ===
    {
        id: 'seg_intro_deadair',
        start: '00:00:00.000',
        end: '00:00:06.000',
        action: 'cut',
        label: 'Dead air before speech begins (replaced by intro billboard)',
    },

    // === Segment 1: Introduction & Navigate to Plugins ===
    {
        id: 'seg01_trim_deadair',
        start: '00:00:06.000',
        end: '00:00:06.309',
        action: 'cut',
        label: 'Trim slight dead air before first word',
    },
    {
        id: 'seg01_intro_narration',
        start: '00:00:06.309',
        end: '00:00:15.289',
        action: 'keep',
        label: 'Opening narration: introduces extensible desktop app and Plugins page',
    },

    // === Segment 2: Upload Plugin & Select File ===
    {
        id: 'seg02_tighten_nav',
        start: '00:00:15.500',
        end: '00:00:17.700',
        action: 'compress',
        targetDuration: 1,
        muteAudio: true,
        label: 'Tighten pause between navigating to plugins and clicking Upload',
    },
    {
        id: 'seg02_upload_select',
        start: '00:00:17.709',
        end: '00:00:23.879',
        action: 'keep',
        label: 'Clicks Upload Plugin, Choose File, selects the JAR',
    },
    {
        id: 'seg02_tighten_post_select',
        start: '00:00:23.879',
        end: '00:00:25.500',
        action: 'compress',
        targetDuration: 0.8,
        muteAudio: true,
        label: 'Tighten pause after selecting JAR file',
    },

    // === Segment 3: Check Auto-Load & Upload ===
    {
        id: 'seg03_autoload',
        start: '00:00:26.139',
        end: '00:00:29.840',
        action: 'keep',
        label: 'Explains checking auto-load and clicking upload plugin',
    },
    {
        id: 'seg03_compress_upload',
        start: '00:00:29.840',
        end: '00:00:31.500',
        action: 'compress',
        targetDuration: 1,
        speed: 2,
        muteAudio: true,
        overlay: 'Uploading...',
        label: 'Compress upload processing pause',
    },

    // === Segment 4: License Agreement & Patreon Verification ===
    {
        id: 'seg04_license_accept',
        start: '00:00:32.099',
        end: '00:00:37.360',
        action: 'keep',
        label: 'Plugin starts, asks to accept license agreement',
    },
    {
        id: 'seg04_compress_license_pause',
        start: '00:00:37.360',
        end: '00:00:39.459',
        action: 'compress',
        targetDuration: 1,
        speed: 2,
        muteAudio: true,
        label: 'Compress pause between license acceptance and next dialog',
    },
    {
        id: 'seg04_and_this',
        start: '00:00:39.459',
        end: '00:00:39.939',
        action: 'keep',
        label: 'Narrator says "and this" (before hesitation)',
    },
    {
        id: 'seg04_tighten_hesitation',
        start: '00:00:39.939',
        end: '00:00:40.860',
        action: 'compress',
        targetDuration: 0.3,
        muteAudio: true,
        label: 'Tighten hesitation gap between "this" and "plugin"',
    },
    {
        id: 'seg04_patreon_req',
        start: '00:00:40.860',
        end: '00:00:45.840',
        action: 'keep',
        label: 'Explains Patreon requirement',
    },
    {
        id: 'seg04_compress_patreon_ui',
        start: '00:00:45.840',
        end: '00:00:47.590',
        action: 'compress',
        targetDuration: 0.8,
        speed: 2,
        muteAudio: true,
        label: 'Compress pause during Patreon verification UI interaction',
    },
    {
        id: 'seg04_verify_status',
        start: '00:00:47.590',
        end: '00:00:50.040',
        action: 'keep',
        label: 'Completes Patreon verification explanation',
    },
    {
        id: 'seg04_tighten_pause',
        start: '00:00:50.040',
        end: '00:00:51.810',
        action: 'compress',
        targetDuration: 0.5,
        muteAudio: true,
        label: 'Tighten pause between sentences',
    },
    {
        id: 'seg04_plugin_for',
        start: '00:00:51.810',
        end: '00:00:53.090',
        action: 'keep',
        label: 'Begins explaining who the plugin is for',
    },
    {
        id: 'seg04_compress_thinking',
        start: '00:00:53.090',
        end: '00:00:55.930',
        action: 'compress',
        targetDuration: 1,
        speed: 3,
        muteAudio: true,
        label: 'Compress long mid-sentence pause ("for... supporters")',
    },
    {
        id: 'seg04_supporters',
        start: '00:00:55.930',
        end: '00:00:57.909',
        action: 'keep',
        label: 'Completes Patreon supporters statement',
    },

    // === Segment 5: Allow, Close & Refresh Home Page ===
    {
        id: 'seg05_compress_transition',
        start: '00:00:57.909',
        end: '00:01:00.610',
        action: 'compress',
        targetDuration: 1,
        speed: 3,
        muteAudio: true,
        label: 'Compress pause between Patreon statement and next action',
    },
    {
        id: 'seg05_once_you_click',
        start: '00:01:00.610',
        end: '00:01:01.619',
        action: 'keep',
        label: 'Narrator says "Once you click"',
    },
    {
        id: 'seg05_tighten_click_allow',
        start: '00:01:01.619',
        end: '00:01:02.409',
        action: 'compress',
        targetDuration: 0.3,
        muteAudio: true,
        label: 'Tighten hesitation between "click" and "allow"',
    },
    {
        id: 'seg05_allow_close',
        start: '00:01:02.409',
        end: '00:01:05.349',
        action: 'keep',
        label: 'Explains clicking allow and closing the dialog',
    },
    {
        id: 'seg05_compress_navigate',
        start: '00:01:05.349',
        end: '00:01:08.190',
        action: 'compress',
        targetDuration: 1,
        speed: 3,
        muteAudio: true,
        label: 'Compress pause while navigating to refresh',
    },
    {
        id: 'seg05_refresh_home',
        start: '00:01:08.190',
        end: '00:01:14.790',
        action: 'keep',
        label: 'Refreshes home page and shows new options',
    },

    // === Segment 6: Closing Summary ===
    {
        id: 'seg06_compress_dark_transition',
        start: '00:01:14.790',
        end: '00:01:18.900',
        action: 'compress',
        targetDuration: 1.5,
        speed: 3,
        muteAudio: true,
        label: 'Compress dark scene transition before closing statement',
    },
    {
        id: 'seg06_closing',
        start: '00:01:18.900',
        end: '00:01:26.519',
        action: 'keep',
        label: 'Closing summary: plugin installation is simple and adds working applications',
    },

    // === Trailing silence (trimmed — replaced by outro billboard) ===
    {
        id: 'seg_outro_trim',
        start: '00:01:26.519',
        end: '00:01:27.783',
        action: 'cut',
        label: 'Trim trailing dark/silence after last word',
    },
];

// --- Run Pipeline ---
veu.runEditPipeline({
    inputPath: INPUT,
    outputPath: OUTPUT,
    tempDir: TEMP,

    // Intro billboard
    introTitle: 'Cognotik Desktop',
    introSubtitle: 'Plugin Installation',
    introDuration: 3,

    // Outro billboard
    outroLines: [
        'Thanks for Watching!',
        'Cognotik Desktop – Plugin Installation',
        'Support us on Patreon for exclusive plugins',
        'More demo videos coming soon',
    ],
    outroDuration: 4,

    // Transition duration (crossfade between segments)
    transitionDuration: 0.5,

    // Segments
    segments,

    // Audio normalization as final pass
    normalizeAudio: true,

    // Use concat demuxer (all segments will have consistent encoding)
    concatMethod: 'demuxer',
});