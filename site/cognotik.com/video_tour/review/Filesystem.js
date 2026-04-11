#!/usr/bin/env node
// Video edit script for: Filesystem.mp4
// Session File System Access - Browsing, Viewing & Version Control for Cognotik Sessions
//
// Requirements:
//   - Node.js 14+
//   - ffmpeg and ffprobe on PATH
//
// Usage:
//   node Filesystem.js

const veu = require('./lib/video-edit-utils');
const path = require('path');
const os = require('os');

// ── Paths ────────────────────────────────────────────────────────────────────
const INPUT  = path.resolve(__dirname, '../source/Filesystem.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Filesystem.mp4');
const TEMP   = path.join(os.tmpdir(), 'filesystem_edit_' + Date.now());

// ── Segment Definitions (Edit Decision List) ─────────────────────────────────
//
// Derived from Filesystem.json edit instructions.
// Actions: keep, cut, compress, tighten (= keep with trimmed pauses)
//
// "tighten" segments are kept but represent shortened pauses — they are
// included as very short "keep" segments. Where the JSON says "tighten"
// a gap of e.g. 0.28s, we keep a minimal portion (~0.15s) or skip if
// negligible. For clarity, tighten segments with duration < 0.2s are cut
// entirely; those >= 0.2s are compressed to ~half their duration.

const segments = [
  // ── Intro: 0:00.000 – 0:04.000 is trimmed (replaced by intro billboard) ──

  // ── Segment 1: Introduction – Accessing the Session File System ───────────
  {
    id: 'seg1_01_opening',
    start: '00:00:04.030',
    end:   '00:00:09.550',
    action: 'keep',
    label: 'Opening explanation: all sessions have a file system',
  },
  // 0:09.550 – 0:09.829 = 0.279s pause → tighten (cut entirely, < 0.3s)
  {
    id: 'seg1_02_pause',
    start: '00:00:09.550',
    end:   '00:00:09.829',
    action: 'cut',
    label: 'Tighten: small pause before URL explanation',
  },
  {
    id: 'seg1_03_url_nav',
    start: '00:00:09.829',
    end:   '00:00:16.430',
    action: 'keep',
    label: 'Navigating to URL, references comic book run',
  },
  // 0:16.430 – 0:16.790 = 0.36s pause → tighten (cut)
  {
    id: 'seg1_04_pause',
    start: '00:00:16.430',
    end:   '00:00:16.790',
    action: 'cut',
    label: 'Tighten: brief pause before next sentence',
  },
  {
    id: 'seg1_05_root_dir',
    start: '00:00:16.790',
    end:   '00:00:25.909',
    action: 'keep',
    label: 'Demonstrates opening root directory by modifying URL',
  },
  // 0:25.909 – 0:26.579 = 0.67s pause → tighten to ~0.3s
  {
    id: 'seg1_06_pause',
    start: '00:00:25.909',
    end:   '00:00:26.579',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: short pause between sentences to ~0.3s',
  },
  {
    id: 'seg1_07_access_fs',
    start: '00:00:26.579',
    end:   '00:00:31.030',
    action: 'keep',
    label: 'Confirms file system access for the session',
  },

  // ── Segment 2: File System Features – Download, Browse & View Files ───────
  // 0:31.030 – 0:31.590 = 0.56s pause → tighten (cut)
  {
    id: 'seg2_01_pause',
    start: '00:00:31.030',
    end:   '00:00:31.590',
    action: 'cut',
    label: 'Tighten: brief pause before features intro',
  },
  {
    id: 'seg2_02_features_intro',
    start: '00:00:31.590',
    end:   '00:00:37.849',
    action: 'keep',
    label: 'Introduces features: download entire directory as zip',
  },
  // 0:37.849 – 0:38.709 = 0.86s pause → tighten to ~0.3s
  {
    id: 'seg2_03_pause',
    start: '00:00:37.849',
    end:   '00:00:38.709',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: pause between zip mention and markdown files',
  },
  {
    id: 'seg2_04_markdown',
    start: '00:00:38.709',
    end:   '00:00:42.610',
    action: 'keep',
    label: 'Can look at markdown files',
  },
  // 0:42.610 – 0:43.470 = 0.86s pause → tighten to ~0.3s
  {
    id: 'seg2_05_pause',
    start: '00:00:42.610',
    end:   '00:00:43.470',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: pause while navigating UI',
  },
  {
    id: 'seg2_06_html',
    start: '00:00:43.470',
    end:   '00:00:48.630',
    action: 'keep',
    label: 'Can look at HTML files and directly view them',
  },
  {
    id: 'seg2_07_view_md',
    start: '00:00:48.630',
    end:   '00:00:52.630',
    action: 'keep',
    label: 'Can also view markdown files',
  },

  // ── Segment 3: Markdown Viewing Options – HTML, Text & PDF Rendering ──────
  // 0:52.630 – 0:53.610 = 0.98s pause → tighten to ~0.3s
  {
    id: 'seg3_01_pause',
    start: '00:00:52.630',
    end:   '00:00:53.610',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: pause before markdown options',
  },
  {
    id: 'seg3_02_md_download',
    start: '00:00:53.610',
    end:   '00:00:58.470',
    action: 'keep',
    label: 'Explains markdown download option',
  },
  // 0:58.470 – 0:59.049 = 0.579s pause → tighten (cut)
  {
    id: 'seg3_03_pause',
    start: '00:00:58.470',
    end:   '00:00:59.049',
    action: 'cut',
    label: 'Tighten: brief pause',
  },
  {
    id: 'seg3_04_html_render',
    start: '00:00:59.049',
    end:   '00:01:05.069',
    action: 'keep',
    label: 'View markdown as HTML with dynamic rendering',
  },
  {
    id: 'seg3_05_text_view',
    start: '00:01:05.069',
    end:   '00:01:09.290',
    action: 'keep',
    label: 'View as text files',
  },
  // 1:09.290 – 1:10.319 = false start "If you," → cut
  {
    id: 'seg3_06_false_start',
    start: '00:01:09.290',
    end:   '00:01:10.319',
    action: 'cut',
    label: 'Cut: false start "If you,"',
  },
  {
    id: 'seg3_07_handy',
    start: '00:01:10.319',
    end:   '00:01:13.800',
    action: 'keep',
    label: 'This is handy if you want to view the markdown source',
  },
  // 1:13.800 – 1:14.800 = filler "um" → cut
  {
    id: 'seg3_08_filler_um',
    start: '00:01:13.800',
    end:   '00:01:14.800',
    action: 'cut',
    label: 'Cut: filler word "um"',
  },
  {
    id: 'seg3_09_no_download',
    start: '00:01:14.800',
    end:   '00:01:18.660',
    action: 'keep',
    label: 'Text view avoids triggering download',
  },
  {
    id: 'seg3_10_pdf',
    start: '00:01:18.660',
    end:   '00:01:22.949',
    action: 'keep',
    label: 'Markdown files as PDFs',
  },
  {
    id: 'seg3_11_pdf_render',
    start: '00:01:22.949',
    end:   '00:01:28.480',
    action: 'keep',
    label: 'Dynamic rendering of markdown to PDF format',
  },

  // ── Segment 4: Built-in Git Version Control ───────────────────────────────
  // 1:28.480 – 1:29.610 = 1.13s pause → tighten to ~0.3s
  {
    id: 'seg4_01_pause',
    start: '00:01:28.480',
    end:   '00:01:29.610',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: pause before Git section',
  },
  {
    id: 'seg4_02_git_intro',
    start: '00:01:29.610',
    end:   '00:01:35.470',
    action: 'keep',
    label: 'Introduces built-in Git support',
  },
  // 1:35.470 – 1:37.190 = 1.72s pause/navigation → tighten/compress to ~0.5s
  {
    id: 'seg4_03_nav_pause',
    start: '00:01:35.470',
    end:   '00:01:37.190',
    action: 'compress',
    targetDuration: 0.5,
    muteAudio: true,
    label: 'Tighten: pause while navigating to Git UI, compress to ~0.5s',
  },
  {
    id: 'seg4_04_git_status',
    start: '00:01:37.190',
    end:   '00:01:40.010',
    action: 'keep',
    label: 'View current status, commit the directory',
  },
  // 1:40.010 – 1:41.230 = 1.22s pause → tighten to ~0.3s
  {
    id: 'seg4_05_pause',
    start: '00:01:40.010',
    end:   '00:01:41.230',
    action: 'compress',
    targetDuration: 0.3,
    muteAudio: true,
    label: 'Tighten: brief pause',
  },
  {
    id: 'seg4_06_if_not_aware',
    start: '00:01:41.230',
    end:   '00:01:43.639',
    action: 'keep',
    label: 'If you\'re not aware,',
  },
  // 1:43.639 – 1:45.190 = filler "uh" → cut
  {
    id: 'seg4_07_filler_uh',
    start: '00:01:43.639',
    end:   '00:01:45.190',
    action: 'cut',
    label: 'Cut: filler "uh" before Git explanation',
  },
  {
    id: 'seg4_08_git_explain',
    start: '00:01:45.190',
    end:   '00:01:54.209',
    action: 'keep',
    label: 'Git explanation: version control, time machine, track changes, rollback',
  },

  // ── Segment 5: Summary – Power & Hackability ─────────────────────────────
  // 1:54.209 – 1:55.129 = filler "Um," → cut
  {
    id: 'seg5_01_filler_um',
    start: '00:01:54.209',
    end:   '00:01:55.129',
    action: 'cut',
    label: 'Cut: filler "Um" at start of summary',
  },
  // 1:55.129 – 1:57.370 = thinking pause → time-compress-and-mute to ~0.8s
  {
    id: 'seg5_02_thinking_pause',
    start: '00:01:55.129',
    end:   '00:01:57.370',
    action: 'compress',
    targetDuration: 0.8,
    muteAudio: true,
    label: 'Time-compress-and-mute: thinking pause from ~2.2s to ~0.8s',
  },
  {
    id: 'seg5_03_root_fs',
    start: '00:01:57.370',
    end:   '00:02:02.910',
    action: 'keep',
    label: 'Accessing the root file system for any given session via this interface',
  },
  {
    id: 'seg5_04_powerful',
    start: '00:02:02.910',
    end:   '00:02:05.190',
    action: 'keep',
    label: 'Gives you a powerful,',
  },
  // 2:05.190 – 2:06.110 = filler "um" → cut
  {
    id: 'seg5_05_filler_um',
    start: '00:02:05.190',
    end:   '00:02:06.110',
    action: 'cut',
    label: 'Cut: filler "um" in "powerful, um, backdoor"',
  },
  {
    id: 'seg5_06_backdoor',
    start: '00:02:06.110',
    end:   '00:02:09.009',
    action: 'keep',
    label: 'Backdoor into the system functionality',
  },
  // 2:09.009 – 2:10.548 = filler "Um," → cut
  {
    id: 'seg5_07_filler_um',
    start: '00:02:09.009',
    end:   '00:02:10.548',
    action: 'cut',
    label: 'Cut: filler "Um," between sentences',
  },
  {
    id: 'seg5_08_hackability',
    start: '00:02:10.548',
    end:   '00:02:16.449',
    action: 'keep',
    label: 'Provides hackability and transparency for any,',
  },
  // 2:16.449 – 2:17.850 = pause → time-compress-and-mute to ~0.5s
  {
    id: 'seg5_09_word_search_pause',
    start: '00:02:16.449',
    end:   '00:02:17.850',
    action: 'compress',
    targetDuration: 0.5,
    muteAudio: true,
    label: 'Time-compress-and-mute: pause before "Cognotik" to ~0.5s',
  },
  {
    id: 'seg5_10_cognotik_apps',
    start: '00:02:17.850',
    end:   '00:02:19.869',
    action: 'keep',
    label: 'Cognotik applications',
  },

  // ── Segment 6: Physical File System Location & Closing ────────────────────
  // 2:19.869 – 2:20.419 = 0.55s pause → tighten (cut)
  {
    id: 'seg6_01_pause',
    start: '00:02:19.869',
    end:   '00:02:20.419',
    action: 'cut',
    label: 'Tighten: brief pause before "Finally"',
  },
  {
    id: 'seg6_02_physical_loc',
    start: '00:02:20.419',
    end:   '00:02:28.410',
    action: 'keep',
    label: 'Points out physical location of the file system',
  },
  // 2:28.410 – 2:28.970 = filler "Uh," → cut
  {
    id: 'seg6_03_filler_uh',
    start: '00:02:28.410',
    end:   '00:02:28.970',
    action: 'cut',
    label: 'Cut: filler "Uh," before next sentence',
  },
  {
    id: 'seg6_04_mount_dev',
    start: '00:02:28.970',
    end:   '00:02:39.830',
    action: 'keep',
    label: 'Mount with dev environment, open in file system explorer',
  },
  // 2:39.830 – 2:41.169 = 1.339s pause → tighten to ~0.5s
  {
    id: 'seg6_05_pause',
    start: '00:02:39.830',
    end:   '00:02:41.169',
    action: 'compress',
    targetDuration: 0.5,
    muteAudio: true,
    label: 'Tighten: pause before closing statement to ~0.5s',
  },
  {
    id: 'seg6_06_closing',
    start: '00:02:41.169',
    end:   '00:02:45.750',
    action: 'keep',
    label: 'Closing: hope that functionality is useful',
  },

  // ── Outro: 2:45.750 – end is trimmed (replaced by outro billboard) ────────
];

// ── Run Pipeline ─────────────────────────────────────────────────────────────
veu.runEditPipeline({
  inputPath:  INPUT,
  outputPath: OUTPUT,
  tempDir:    TEMP,

  // Intro billboard
  introTitle:    'Session File System Access',
  introSubtitle: 'Browsing, Viewing & Version Control for Cognotik Sessions',
  introDuration: 4,

  // Outro billboard
  outroLines: [
    'Thanks for Watching!',
    'Cognotik – Session File System Access',
    'Explore more at cognotic.dev',
    'Like & Subscribe for more tutorials',
  ],
  outroDuration: 5,

  // Transition settings
  transitionDuration: 0.5,

  // Edit decision list
  segments,

  // Final audio normalization
  normalizeAudio: true,

  // Use demuxer concat (all segments will have consistent encoding)
  concatMethod: 'demuxer',
});