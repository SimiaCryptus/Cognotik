#!/usr/bin/env node
// Comic_Generator.js — Automated video edit script for Comic Book Generator Demo
// Requirements: Node.js 14+, ffmpeg and ffprobe on PATH
// No npm packages needed.
//
// Usage:
//   node Comic_Generator.js

const veu = require('./lib/video-edit-utils');
const path = require('path');
const os = require('os');

// --- Paths ---
const INPUT  = path.resolve(__dirname, '../source/Comic_Generator.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Comic_Generator.mp4');
const TEMP   = path.join(os.tmpdir(), 'comic_generator_edit_' + Date.now());

// --- Segment Definitions (Edit Decision List) ---
// Timecodes from the JSON use "M:SS.mmm" format; converted to "HH:MM:SS.mmm" for clarity.
//
// Strategy for "tighten": extract the segment but trim it to a short duration (~0.2-0.5s)
// by only keeping a small portion (the tail end, to preserve visual continuity).
// Strategy for "edit" with internal pause tightening: extract sub-portions and let them
// concatenate naturally, skipping the pauses.
// Strategy for "cut": omit entirely.

const segments = [
    // =====================================================================
    // SEGMENT 1: Introduction to the Comic Serial App (0:03.640 – 0:17.000)
    // =====================================================================
    {
        id: 'seg01_intro_speech',
        start: '00:00:03.640',
        end: '00:00:11.580',
        action: 'keep',
        label: 'Narrator introduces the comic serial app',
    },
    // Tighten 0:11.580–0:13.800 (~2.2s pause → ~0.5s)
    {
        id: 'seg01_tighten_pause',
        start: '00:00:11.580',
        end: '00:00:12.080',
        action: 'keep',
        label: 'Tightened pause (0.5s kept from 2.2s gap)',
    },
    {
        id: 'seg01_prompt_setup',
        start: '00:00:13.800',
        end: '00:00:17.000',
        action: 'keep',
        label: 'Narrator sets up prompt entry',
    },

    // =====================================================================
    // SEGMENT 2: Entering the Prompt (0:17.000 – 0:38.900)
    // =====================================================================
    // Tighten 0:17.000–0:19.900 (~2.9s pause → ~0.5s)
    {
        id: 'seg02_tighten_pre_typing',
        start: '00:00:19.400',
        end: '00:00:19.900',
        action: 'keep',
        label: 'Tightened pause before typing (0.5s)',
    },
    {
        id: 'seg02_typing_prompt',
        start: '00:00:19.900',
        end: '00:00:25.100',
        action: 'keep',
        label: 'Narrator types prompt: Comic About Food parakeets',
    },
    // CUT 0:25.100–0:33.200 (filler: "Stop. Interrupt. So.")
    // Tighten 0:33.200–0:37.700 (~4.5s pause → ~0.3s)
    {
        id: 'seg02_tighten_post_filler',
        start: '00:00:37.400',
        end: '00:00:37.700',
        action: 'keep',
        label: 'Tightened pause after cut filler (0.3s)',
    },
    {
        id: 'seg02_saved_idea',
        start: '00:00:37.700',
        end: '00:00:38.900',
        action: 'keep',
        label: 'Narrator: We saved this idea',
    },

    // =====================================================================
    // SEGMENT 3: Model Selection (0:38.900 – 1:09.400)
    // =====================================================================
    // Tighten 0:38.900–0:40.490 (~1.6s → ~0.3s)
    {
        id: 'seg03_tighten_pre_model',
        start: '00:00:40.190',
        end: '00:00:40.490',
        action: 'keep',
        label: 'Tightened pause before model selection (0.3s)',
    },
    {
        id: 'seg03_models_selected',
        start: '00:00:40.490',
        end: '00:00:42.470',
        action: 'keep',
        label: 'Narrator: Make sure we have models selected',
    },
    // CUT 0:42.470–0:48.800 (stumbled: "These are the deep. Must be")
    {
        id: 'seg03_first_on_list',
        start: '00:00:48.800',
        end: '00:00:52.380',
        action: 'keep',
        label: 'Narrator: First on the list for whatever reason. We will select.',
    },
    // Tighten 0:52.380–0:54.370 (~2s → ~0.5s)
    {
        id: 'seg03_tighten_dropdown',
        start: '00:00:53.870',
        end: '00:00:54.370',
        action: 'keep',
        label: 'Tightened pause while navigating dropdown (0.5s)',
    },
    {
        id: 'seg03_select_models',
        start: '00:00:54.370',
        end: '00:01:06.320',
        action: 'keep',
        label: 'Narrator selects Flash 3 and Gemini 1 Flash Image Preview',
    },
    // Tighten 1:06.320–1:07.980 (~1.7s → ~0.3s)
    {
        id: 'seg03_tighten_pre_save',
        start: '00:01:07.680',
        end: '00:01:07.980',
        action: 'keep',
        label: 'Tightened pause before saving (0.3s)',
    },
    {
        id: 'seg03_save_settings',
        start: '00:01:07.980',
        end: '00:01:09.400',
        action: 'keep',
        label: 'Narrator: Save our model settings',
    },

    // =====================================================================
    // SEGMENT 4: Explaining the Comic Serial Concept (1:09.400 – 1:43.000)
    // =====================================================================
    // Tighten 1:09.400–1:10.990 (~1.6s → ~0.3s)
    {
        id: 'seg04_tighten_pre_explain',
        start: '00:01:10.690',
        end: '00:01:10.990',
        action: 'keep',
        label: 'Tightened pause before explanation (0.3s)',
    },
    {
        id: 'seg04_serial_concept',
        start: '00:01:10.990',
        end: '00:01:29.000',
        action: 'keep',
        label: 'Narrator explains comic serial concept: first comic, sequels',
    },
    // Tighten 1:29.000–1:30.130 (~1.1s → ~0.3s)
    {
        id: 'seg04_tighten_mid',
        start: '00:01:29.830',
        end: '00:01:30.130',
        action: 'keep',
        label: 'Tightened pause between concept and HTML explanation (0.3s)',
    },
    {
        id: 'seg04_html_rendering',
        start: '00:01:30.130',
        end: '00:01:39.150',
        action: 'keep',
        label: 'Narrator explains HTML rendering step',
    },
    // Tighten 1:39.150–1:41.500 (~2.35s mid-sentence pause → ~0.3s)
    {
        id: 'seg04_tighten_mid_sentence',
        start: '00:01:41.200',
        end: '00:01:41.500',
        action: 'keep',
        label: 'Tightened mid-sentence pause (0.3s)',
    },
    {
        id: 'seg04_html_framing',
        start: '00:01:41.500',
        end: '00:01:43.000',
        action: 'keep',
        label: 'Narrator: Nice HTML framing',
    },

    // =====================================================================
    // SEGMENT 5: Starting Comic Generation (1:43.000 – 1:48.000)
    // =====================================================================
    // Tighten 1:43.000–1:44.010 (~1s → ~0.2s)
    {
        id: 'seg05_tighten_pre_monitor',
        start: '00:01:43.810',
        end: '00:01:44.010',
        action: 'keep',
        label: 'Tightened pause (0.2s)',
    },
    {
        id: 'seg05_monitor_generation',
        start: '00:01:44.010',
        end: '00:01:47.960',
        action: 'keep',
        label: 'Narrator: We will monitor the generation of the comic book itself',
    },

    // =====================================================================
    // SEGMENT 6: Script Generation — Processing Wait (1:48.000 – 2:28.660)
    // =====================================================================
    {
        id: 'seg06_script_generation',
        start: '00:01:48.000',
        end: '00:02:28.660',
        action: 'compress',
        targetDuration: 5,
        overlay: 'Generating script...',
        muteAudio: true,
        label: 'Time-compressed script generation (~40s → ~5s)',
    },

    // =====================================================================
    // SEGMENT 7: Narrating Script and Character Generation (2:28.660 – 3:03.640)
    // =====================================================================
    {
        id: 'seg07_sketch_of_script',
        start: '00:02:28.660',
        end: '00:02:32.500',
        action: 'keep',
        label: 'Narrator: We have a sketch of a script',
    },
    // Tighten 2:32.500–2:34.870 (~2.4s → ~0.5s)
    {
        id: 'seg07_tighten_pre_chargen',
        start: '00:02:34.370',
        end: '00:02:34.870',
        action: 'keep',
        label: 'Tightened pause before character generation (0.5s)',
    },
    {
        id: 'seg07_char_generation',
        start: '00:02:34.870',
        end: '00:02:37.370',
        action: 'keep',
        label: 'Narrator: And then it goes into character generation',
    },
    // Tighten 2:37.370–2:39.950 (~2.6s → ~0.5s)
    {
        id: 'seg07_tighten_pre_ref',
        start: '00:02:39.450',
        end: '00:02:39.950',
        action: 'keep',
        label: 'Tightened pause before reference images explanation (0.5s)',
    },
    {
        id: 'seg07_char_ref_images',
        start: '00:02:39.950',
        end: '00:02:46.400',
        action: 'keep',
        label: 'Narrator: First step is to generate character reference images',
    },
    // Tighten 2:46.400–2:47.490 (~1.1s → ~0.3s)
    {
        id: 'seg07_tighten_mid1',
        start: '00:02:47.190',
        end: '00:02:47.490',
        action: 'keep',
        label: 'Tightened pause (0.3s)',
    },
    {
        id: 'seg07_used_for_rendering',
        start: '00:02:47.490',
        end: '00:02:51.840',
        action: 'keep',
        label: 'Narrator: These are then used when rendering the comics',
    },
    // Tighten 2:51.840–2:54.080 (~2.2s → ~0.3s)
    {
        id: 'seg07_tighten_mid2',
        start: '00:02:53.780',
        end: '00:02:54.080',
        action: 'keep',
        label: 'Tightened pause before achieve (0.3s)',
    },
    // Edit: "In order to achieve... Artistic... Consistency." — tighten internal pauses
    // 2:54.080 – ~2:56.5 "In order to achieve"
    // ~2:56.5 – ~2:59.0 (long pause)
    // ~2:59.0 – ~3:00.5 "Artistic"
    // ~3:00.5 – ~3:02.0 (long pause)
    // ~3:02.0 – ~3:03.640 "Consistency"
    // We extract the speech portions and skip the pauses
    {
        id: 'seg07_achieve_part1',
        start: '00:02:54.080',
        end: '00:02:56.500',
        action: 'keep',
        label: 'Narrator: In order to achieve',
    },
    {
        id: 'seg07_achieve_part2',
        start: '00:02:58.800',
        end: '00:03:00.500',
        action: 'keep',
        label: 'Narrator: Artistic',
    },
    {
        id: 'seg07_achieve_part3',
        start: '00:03:01.800',
        end: '00:03:03.640',
        action: 'keep',
        label: 'Narrator: Consistency',
    },

    // =====================================================================
    // SEGMENT 8: Character Image Generation — Processing Wait (3:03.640 – 3:59.440)
    // =====================================================================
    {
        id: 'seg08_char_img_generation',
        start: '00:03:03.640',
        end: '00:03:59.440',
        action: 'compress',
        targetDuration: 6,
        overlay: 'Generating character references...',
        muteAudio: true,
        label: 'Time-compressed character reference generation (~56s → ~6s)',
    },

    // =====================================================================
    // SEGMENT 9: Page Generation Begins (3:59.440 – 4:07.000)
    // =====================================================================
    {
        id: 'seg09_page_gen_begins',
        start: '00:03:59.440',
        end: '00:04:03.540',
        action: 'keep',
        label: 'Narrator: And now we are on to the actual page generation',
    },
    // Tighten 4:03.540–4:04.480 (~0.9s → ~0.3s)
    {
        id: 'seg09_tighten_brief',
        start: '00:04:04.180',
        end: '00:04:04.480',
        action: 'keep',
        label: 'Tightened brief pause (0.3s)',
    },
    {
        id: 'seg09_realtime_monitor',
        start: '00:04:04.480',
        end: '00:04:07.000',
        action: 'keep',
        label: 'Narrator: Again, you can monitor this in real time',
    },

    // =====================================================================
    // SEGMENT 10: Page Generation — Long Processing Wait (4:07.000 – 6:38.940)
    // =====================================================================
    {
        id: 'seg10_page_generation',
        start: '00:04:07.000',
        end: '00:06:38.940',
        action: 'compress',
        targetDuration: 11,
        overlay: 'Generating comic pages...',
        muteAudio: true,
        label: 'Time-compressed page generation (~152s → ~11s)',
    },

    // =====================================================================
    // SEGMENT 11: Generation Complete (6:38.940 – 6:54.000)
    // =====================================================================
    {
        id: 'seg11_fourth_wall_joke',
        start: '00:06:38.940',
        end: '00:06:43.280',
        action: 'keep',
        label: 'Narrator: And it ends with a nice little fourth wall joke',
    },
    // Tighten 6:43.280–6:44.560 (~1.3s → ~0.3s)
    {
        id: 'seg11_tighten_pre_great',
        start: '00:06:44.260',
        end: '00:06:44.560',
        action: 'keep',
        label: 'Tightened pause before Great (0.3s)',
    },
    {
        id: 'seg11_great',
        start: '00:06:44.560',
        end: '00:06:45.000',
        action: 'keep',
        label: 'Narrator: Great',
    },
    // Tighten 6:45.000–6:46.290 (~1.3s → ~0.3s)
    {
        id: 'seg11_tighten_pre_render',
        start: '00:06:45.990',
        end: '00:06:46.290',
        action: 'keep',
        label: 'Tightened pause before render instruction (0.3s)',
    },
    // Edit: "So, now that that generation has completed and my parares agree" →
    // Keep "So, now that generation has completed" (6:46.290–6:49.000), cut rest
    {
        id: 'seg11_gen_completed',
        start: '00:06:46.290',
        end: '00:06:49.000',
        action: 'keep',
        label: 'Narrator: So, now that generation has completed (cleaned)',
    },
    // CUT "and my parares agree" (6:49.000–6:51.070)
    {
        id: 'seg11_time_to_render',
        start: '00:06:51.070',
        end: '00:06:54.000',
        action: 'keep',
        label: 'Narrator: It is time to render the comic book',
    },

    // =====================================================================
    // SEGMENT 12: HTML Rendering (6:54.000 – 7:16.500)
    // =====================================================================
    // Tighten 6:54.000–6:56.700 (~2.7s → ~0.5s)
    {
        id: 'seg12_tighten_pre_render_click',
        start: '00:06:56.200',
        end: '00:06:56.700',
        action: 'keep',
        label: 'Tightened pause while clicking render (0.5s)',
    },
    {
        id: 'seg12_renders_html',
        start: '00:06:56.700',
        end: '00:06:59.810',
        action: 'keep',
        label: 'Narrator: This renders the HTML structure that will',
    },
    // CUT garbled "How is the comic since the," (6:59.810–7:04.580)
    // Keep "Basic comic book framing is somewhat basic" (7:04.580–7:11.410)
    // but tighten internal pause around "is. Somewhat"
    {
        id: 'seg12_basic_framing_p1',
        start: '00:07:04.580',
        end: '00:07:07.500',
        action: 'keep',
        label: 'Narrator: Basic comic book framing is',
    },
    {
        id: 'seg12_basic_framing_p2',
        start: '00:07:09.000',
        end: '00:07:11.410',
        action: 'keep',
        label: 'Narrator: somewhat basic',
    },
    // Tighten 7:11.410–7:15.110 (~3.7s → ~0.3s)
    {
        id: 'seg12_tighten_pre_moment',
        start: '00:07:14.810',
        end: '00:07:15.110',
        action: 'keep',
        label: 'Tightened pause before this should only take a moment (0.3s)',
    },
    {
        id: 'seg12_take_a_moment',
        start: '00:07:15.110',
        end: '00:07:16.500',
        action: 'keep',
        label: 'Narrator: This should only take a moment',
    },

    // =====================================================================
    // SEGMENT 13: Render Processing Wait (7:16.500 – 7:26.560)
    // =====================================================================
    {
        id: 'seg13_render_wait',
        start: '00:07:16.500',
        end: '00:07:26.560',
        action: 'compress',
        targetDuration: 3,
        overlay: 'Rendering HTML...',
        muteAudio: true,
        label: 'Time-compressed HTML render wait (~10s → ~3s)',
    },

    // =====================================================================
    // SEGMENT 14: Previewing the Final Comic Book (7:26.560 – 7:44.440)
    // =====================================================================
    {
        id: 'seg14_here_we_go',
        start: '00:07:26.560',
        end: '00:07:27.160',
        action: 'keep',
        label: 'Narrator: Here we go',
    },
    // Tighten 7:27.160–7:28.320 (~1.2s → ~0.3s)
    {
        id: 'seg14_tighten_pre_open',
        start: '00:07:28.020',
        end: '00:07:28.320',
        action: 'keep',
        label: 'Tightened pause before opening tab (0.3s)',
    },
    {
        id: 'seg14_open_preview',
        start: '00:07:28.320',
        end: '00:07:34.220',
        action: 'keep',
        label: 'Narrator: Let us open this in a new tab and preview it',
    },
    // CUT filler "No." (7:34.220–7:36.270)
    // Tighten 7:36.270–7:37.050 (~0.8s → ~0.2s)
    {
        id: 'seg14_tighten_post_cut',
        start: '00:07:36.850',
        end: '00:07:37.050',
        action: 'keep',
        label: 'Tightened transition after cut (0.2s)',
    },
    {
        id: 'seg14_attractive_presentation',
        start: '00:07:37.050',
        end: '00:07:44.440',
        action: 'keep',
        label: 'Narrator: A much more attractive presentation with textual dialogue',
    },

    // =====================================================================
    // SEGMENT 15: Closing Statement (7:44.440 – 7:52.840)
    // =====================================================================
    // Tighten 7:44.440–7:48.730 (~4.3s → ~0.5s)
    {
        id: 'seg15_tighten_pre_closing',
        start: '00:07:48.230',
        end: '00:07:48.730',
        action: 'keep',
        label: 'Tightened pause before closing statement (0.5s)',
    },
    {
        id: 'seg15_closing',
        start: '00:07:48.730',
        end: '00:07:52.840',
        action: 'keep',
        label: 'Narrator: And that is the Comic book generator. I hope you enjoy it.',
    },
];

// --- Run Pipeline ---
veu.runEditPipeline({
    inputPath: INPUT,
    outputPath: OUTPUT,
    tempDir: TEMP,

    // Intro billboard
    introTitle: 'Comic Book Generator',
    introSubtitle: 'AI-Powered Comic Serial Creation',
    introDuration: 4,

    // Outro billboard
    outroLines: [
        'Thanks for Watching!',
        'Comic Book Generator — AI-Powered Comic Serial Creation',
        'Like & Subscribe for more AI demos',
        'Try it yourself — link in description',
    ],
    outroDuration: 5,

    // Transition duration for fades between segments
    transitionDuration: 0.5,

    // Segments
    segments: segments,

    // Audio normalization as final pass
    normalizeAudio: true,

    // Use demuxer concat (all segments will have consistent encoding)
    concatMethod: 'demuxer',
});

console.log('\n✅ Edit complete: ' + OUTPUT);