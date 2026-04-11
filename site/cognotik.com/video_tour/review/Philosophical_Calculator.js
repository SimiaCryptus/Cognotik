#!/usr/bin/env node
// Edit script for Philosophical Calculator demo video
// Requirements: Node.js 14+, ffmpeg and ffprobe on PATH
// No npm dependencies required
//
// Usage: node Philosophical_Calculator.js

const veu = require('./lib/video-edit-utils');
const path = require('path');
const os = require('os');

// --- Paths ---
const VIDEO_NAME = 'Philosophical_Calculator';
const INPUT = path.resolve(__dirname, '../source', VIDEO_NAME + '.mp4');
const OUTPUT = path.resolve(__dirname, '../edit', VIDEO_NAME + '.mp4');
const TEMP = path.join(os.tmpdir(), VIDEO_NAME + '_' + Date.now());

// --- Segment Definitions (Edit Decision List) ---
// Derived from Philosophical_Calculator.json
//
// Action mapping:
//   "keep"                    → action: 'keep'
//   "cut"                     → action: 'cut'
//   "tighten"                 → action: 'cut'  (remove dead air / pauses)
//   "edit" (remove filler)    → action: 'cut'  (remove false starts, stumbles, tangents)
//   "time-compress-and-mute"  → action: 'compress'

const segments = [
    // === Segment 1: Introduction & Input ===
    {
        id: 'seg01_intro_speech',
        start: '00:00:06.329',
        end: '00:00:15.840',
        action: 'keep',
        label: 'Introduction to the Philosophical Calculator and explanation of input step',
    },
    {
        id: 'seg02_typing_compress',
        start: '00:00:15.840',
        end: '00:00:21.700',
        action: 'compress',
        targetDuration: 3,
        overlay: 'Entering prompt...',
        muteAudio: true,
        label: 'Typing/input pause — compressed',
    },
    {
        id: 'seg03_dead_air_cut',
        start: '00:00:21.700',
        end: '00:00:24.680',
        action: 'cut',
        label: 'Dead air after typing',
    },
    {
        id: 'seg04_file_upload',
        start: '00:00:24.680',
        end: '00:00:31.520',
        action: 'keep',
        label: 'Explains file upload option and choosing a simple prompt',
    },

    // === Segment 2: Model Selection & Pipeline Setup ===
    {
        id: 'seg05_tighten_pause',
        start: '00:00:31.520',
        end: '00:00:33.110',
        action: 'cut',
        label: 'Tighten: small pause between prompt section and model selection',
    },
    {
        id: 'seg06_model_selection',
        start: '00:00:33.110',
        end: '00:00:41.729',
        action: 'keep',
        label: 'Selecting models (Haiku and Gemini Flash Image)',
    },
    {
        id: 'seg07_tighten_pause2',
        start: '00:00:41.729',
        end: '00:00:43.560',
        action: 'cut',
        label: 'Tighten: brief pause before pipeline explanation',
    },
    {
        id: 'seg08_pipeline_start',
        start: '00:00:43.560',
        end: '00:00:55.319',
        action: 'keep',
        label: 'Starting the pipeline, skipping summarize, going to draft article',
    },

    // === Segment 3: Draft Article Processing ===
    {
        id: 'seg09_wait_complete',
        start: '00:00:55.319',
        end: '00:00:59.549',
        action: 'keep',
        label: 'Waiting for completion and mentioning session viewing',
    },
    {
        id: 'seg10_tighten_pause3',
        start: '00:00:59.549',
        end: '00:01:01.439',
        action: 'cut',
        label: 'Tighten: small gap before explaining session monitoring',
    },
    {
        id: 'seg11_session_explain',
        start: '00:01:01.439',
        end: '00:01:17.400',
        action: 'keep',
        label: 'Explains session monitoring, one-stop operation, lenses next',
    },
    {
        id: 'seg12_nav_compress',
        start: '00:01:17.400',
        end: '00:01:21.949',
        action: 'compress',
        targetDuration: 2,
        overlay: 'Processing...',
        muteAudio: true,
        label: 'Navigating UI to view completed draft — compressed',
    },
    {
        id: 'seg13_draft_complete',
        start: '00:01:21.949',
        end: '00:01:28.000',
        action: 'keep',
        label: 'Shows the completed draft article in formatted preview',
    },

    // === Segment 4: Introduction to Lenses ===
    {
        id: 'seg14_cut_transition',
        start: '00:01:28.000',
        end: '00:01:29.160',
        action: 'cut',
        label: 'Cut gap before lenses section',
    },
    {
        id: 'seg15_lenses_intro',
        start: '00:01:29.160',
        end: '00:01:33.879',
        action: 'keep',
        label: 'Transitioning to lenses section',
    },
    {
        id: 'seg16_cut_gap',
        start: '00:01:33.879',
        end: '00:01:34.760',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg17_false_start_cut',
        start: '00:01:34.760',
        end: '00:01:35.970',
        action: 'cut',
        label: 'Edit: remove false start "The philosophical,"',
    },
    {
        id: 'seg18_lens_operations',
        start: '00:01:35.970',
        end: '00:01:53.529',
        action: 'keep',
        label: 'Describes the variety of lens operations available',
    },
    {
        id: 'seg19_tighten_pause4',
        start: '00:01:53.529',
        end: '00:01:55.339',
        action: 'cut',
        label: 'Tighten: brief pause before demonstration choice',
    },
    {
        id: 'seg20_perspective_choice',
        start: '00:01:55.339',
        end: '00:02:04.790',
        action: 'keep',
        label: 'Choosing perspective analysis and clicking run',
    },

    // === Segment 5: Multi-Perspective Analysis Session ===
    {
        id: 'seg21_tighten_pause5',
        start: '00:02:04.790',
        end: '00:02:07.180',
        action: 'cut',
        label: 'Tighten: brief pause before session link explanation',
    },
    {
        id: 'seg22_session_link',
        start: '00:02:07.180',
        end: '00:02:18.710',
        action: 'keep',
        label: 'Explains the session link and real-time monitoring',
    },
    {
        id: 'seg23_stumble_cut',
        start: '00:02:18.710',
        end: '00:02:26.850',
        action: 'cut',
        label: 'Edit: remove stumbled "change" and dead air gap',
    },
    {
        id: 'seg24_standard_ui',
        start: '00:02:26.850',
        end: '00:02:32.410',
        action: 'keep',
        label: 'Describes the standard UI for real-time viewing',
    },
    {
        id: 'seg25_filler_cut',
        start: '00:02:32.410',
        end: '00:02:40.369',
        action: 'cut',
        label: 'Edit: remove "Um, Agents. For real-time agents and analysis." filler',
    },
    {
        id: 'seg26_analysis_moment',
        start: '00:02:40.369',
        end: '00:02:51.309',
        action: 'keep',
        label: 'Explains analysis will take a moment, going through perspectives',
    },
    {
        id: 'seg27_cut_gap2',
        start: '00:02:51.309',
        end: '00:02:53.220',
        action: 'cut',
        label: 'Cut gap before usage monitoring',
    },
    {
        id: 'seg28_usage_monitoring_pre',
        start: '00:02:53.220',
        end: '00:03:08.200',
        action: 'keep',
        label: 'Navigating to usage monitoring, checking token costs',
    },
    {
        id: 'seg29_filler_brains_cut',
        start: '00:03:08.200',
        end: '00:03:09.400',
        action: 'cut',
        label: 'Edit: remove "Oh, the brains, the, uh," filler',
    },
    {
        id: 'seg30_usage_monitoring_post',
        start: '00:03:09.400',
        end: '00:03:15.089',
        action: 'keep',
        label: 'Multi-perspective dialogue ongoing, used another penny',
    },

    // === Segment 6: Waiting for Analysis Completion ===
    {
        id: 'seg31_dead_air_cut2',
        start: '00:03:15.089',
        end: '00:03:20.990',
        action: 'cut',
        label: 'Dead air gap between usage discussion and next speech',
    },
    {
        id: 'seg32_ui_update',
        start: '00:03:20.990',
        end: '00:03:35.450',
        action: 'keep',
        label: 'Explains UI update on completion, running one lens for demo',
    },
    {
        id: 'seg33_tighten_pause6',
        start: '00:03:35.450',
        end: '00:03:37.240',
        action: 'cut',
        label: 'Tighten: small pause',
    },
    {
        id: 'seg34_pipeline_update',
        start: '00:03:37.240',
        end: '00:03:44.009',
        action: 'keep',
        label: 'Explains pipeline update step and folding lens results back',
    },
    {
        id: 'seg35_cut_gap3',
        start: '00:03:44.009',
        end: '00:03:45.289',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg36_update_article',
        start: '00:03:45.289',
        end: '00:04:06.080',
        action: 'keep',
        label: 'Describes updating article with lens output, picking up insights',
    },

    // === Segment 7: Viewing Lens Results ===
    {
        id: 'seg37_dead_air_cut3',
        start: '00:04:06.080',
        end: '00:04:10.669',
        action: 'cut',
        label: 'Dead air while navigating back to lenses view',
    },
    {
        id: 'seg38_lenses_almost_done',
        start: '00:04:10.669',
        end: '00:04:15.160',
        action: 'keep',
        label: 'Looking at lenses, noting almost complete, seeing synthesis',
    },
    {
        id: 'seg39_false_start_synthesis',
        start: '00:04:15.160',
        end: '00:04:25.279',
        action: 'cut',
        label: 'Edit: remove false start/self-correction about synthesis being last step',
    },
    {
        id: 'seg40_waiting_complete',
        start: '00:04:25.279',
        end: '00:04:30.839',
        action: 'keep',
        label: 'Waiting for completion',
    },
    {
        id: 'seg41_analysis_wait_compress',
        start: '00:04:30.839',
        end: '00:04:53.720',
        action: 'compress',
        targetDuration: 5,
        overlay: 'Analysis completing...',
        muteAudio: true,
        label: 'Long wait for analysis to finish — compressed',
    },
    {
        id: 'seg42_there_we_go',
        start: '00:04:53.720',
        end: '00:04:54.000',
        action: 'keep',
        label: 'Completion confirmation: "There we go"',
    },

    // === Segment 8: Reviewing Analysis Output ===
    {
        id: 'seg43_cut_gap4',
        start: '00:04:54.000',
        end: '00:04:55.369',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg44_detailed_output',
        start: '00:04:55.369',
        end: '00:05:02.920',
        action: 'keep',
        label: 'Viewing detailed output for the lens run in the UI',
    },
    {
        id: 'seg45_cut_gap5',
        start: '00:05:02.920',
        end: '00:05:03.559',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg46_transcript_view',
        start: '00:05:03.559',
        end: '00:05:11.690',
        action: 'keep',
        label: 'Viewing the multi-perspective analysis transcript',
    },
    {
        id: 'seg47_tighten_scroll',
        start: '00:05:11.690',
        end: '00:05:14.160',
        action: 'cut',
        label: 'Tighten: brief pause while scrolling',
    },
    {
        id: 'seg48_competitive_player',
        start: '00:05:14.160',
        end: '00:05:24.559',
        action: 'keep',
        label: 'Shows competitive player perspective, explains long analysis',
    },
    {
        id: 'seg49_tighten_scroll2',
        start: '00:05:24.559',
        end: '00:05:27.190',
        action: 'cut',
        label: 'Tighten: pause while scrolling to synthesis section',
    },
    {
        id: 'seg50_synthesis_summary',
        start: '00:05:27.190',
        end: '00:05:43.140',
        action: 'keep',
        label: 'Shows synthesis and recommendations, summarizes how lenses work',
    },

    // === Segment 9: Illustrate Article Feature ===
    {
        id: 'seg51_cut_gap6',
        start: '00:05:43.140',
        end: '00:05:43.880',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg52_illustrate_intro',
        start: '00:05:43.880',
        end: '00:06:02.670',
        action: 'keep',
        label: 'Explains folding results back, introduces illustrate article feature',
    },
    {
        id: 'seg53_cut_gap7',
        start: '00:06:02.670',
        end: '00:06:03.500',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg54_illustrate_explain',
        start: '00:06:03.500',
        end: '00:06:10.059',
        action: 'keep',
        label: 'Explains what the illustrate feature does',
    },
    {
        id: 'seg55_bug_aside_cut',
        start: '00:06:10.059',
        end: '00:06:27.000',
        action: 'cut',
        label: 'Edit: remove tangential display bug aside',
    },
    {
        id: 'seg56_monitor_realtime',
        start: '00:06:27.000',
        end: '00:06:35.640',
        action: 'keep',
        label: 'Monitoring illustration generation in real time',
    },

    // === Segment 10: Image Generation Processing ===
    {
        id: 'seg57_image_gen_compress',
        start: '00:06:35.640',
        end: '00:06:59.260',
        action: 'compress',
        targetDuration: 5,
        overlay: 'Generating illustrations...',
        muteAudio: true,
        label: 'Long wait while images are being generated — compressed',
    },
    {
        id: 'seg58_here_we_go',
        start: '00:06:59.260',
        end: '00:06:59.859',
        action: 'keep',
        label: 'Brief confirmation: "Here we go"',
    },
    {
        id: 'seg59_cut_gap8',
        start: '00:06:59.859',
        end: '00:07:01.760',
        action: 'cut',
        label: 'Cut gap',
    },
    {
        id: 'seg60_after_gen',
        start: '00:07:01.760',
        end: '00:07:08.980',
        action: 'keep',
        label: 'Explains what happens after image generation',
    },
    {
        id: 'seg61_false_start_create',
        start: '00:07:08.980',
        end: '00:07:14.269',
        action: 'cut',
        label: 'Edit: remove false start "Create," and long pause',
    },
    {
        id: 'seg62_integrate_images',
        start: '00:07:14.269',
        end: '00:07:21.130',
        action: 'keep',
        label: 'Explains image integration into the document',
    },

    // === Segment 11: Final Result & Conclusion ===
    {
        id: 'seg63_integration_compress',
        start: '00:07:21.130',
        end: '00:08:08.260',
        action: 'compress',
        targetDuration: 8,
        overlay: 'Integrating illustrations into article...',
        muteAudio: true,
        label: 'Very long wait while images are integrated — compressed',
    },
    {
        id: 'seg64_ok',
        start: '00:08:08.260',
        end: '00:08:08.269',
        action: 'keep',
        label: 'Brief "OK" acknowledgment',
    },
    {
        id: 'seg65_dead_air_cut4',
        start: '00:08:08.269',
        end: '00:08:12.260',
        action: 'cut',
        label: 'Dead air between OK and viewing the article',
    },
    {
        id: 'seg66_article_reveal',
        start: '00:08:12.260',
        end: '00:08:17.320',
        action: 'keep',
        label: 'Reveals the final illustrated article',
    },
    {
        id: 'seg67_scroll_compress',
        start: '00:08:17.320',
        end: '00:08:23.269',
        action: 'compress',
        targetDuration: 3,
        muteAudio: true,
        label: 'Scrolling through illustrated article — compressed',
    },
    {
        id: 'seg68_cost_summary',
        start: '00:08:23.269',
        end: '00:08:27.820',
        action: 'keep',
        label: 'Cost summary: 62 cents for a fully illustrated guide',
    },
    {
        id: 'seg69_tighten_pause7',
        start: '00:08:27.820',
        end: '00:08:30.690',
        action: 'cut',
        label: 'Tighten: small pause before closing remarks',
    },
    {
        id: 'seg70_closing',
        start: '00:08:30.690',
        end: '00:08:37.030',
        action: 'keep',
        label: 'Closing remarks',
    },
];

// --- Run Pipeline ---
veu.runEditPipeline({
    inputPath: INPUT,
    outputPath: OUTPUT,
    tempDir: TEMP,

    // Intro billboard
    introTitle: 'The Philosophical Calculator',
    introSubtitle: 'AI-Powered Article Generation & Analysis',
    introDuration: 4,

    // Outro billboard
    outroLines: [
        'The Philosophical Calculator',
        'Thanks for watching!',
        'Try it with your own ideas',
        'Like & Subscribe for more AI tool demos',
    ],
    outroDuration: 5,

    // Transition duration between segments
    transitionDuration: 0.3,

    // Edit decision list
    segments,

    // Audio normalization as final pass
    normalizeAudio: true,

    // Use concat demuxer (all segments will have consistent encoding)
    concatMethod: 'demuxer',
});

console.log(`\nEdit complete: ${OUTPUT}`);