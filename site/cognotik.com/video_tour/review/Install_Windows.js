#!/usr/bin/env node
// Video Edit Script: Install_Windows
// Generated from Install_Windows.json edit instructions
//
// Requirements:
//   - Node.js 14+
//   - FFmpeg and FFprobe installed and available on PATH
//   - No npm dependencies required
//
// Usage:
//   node Install_Windows.js

const veu = require('./lib/video-edit-utils');
const path = require('path');
const os = require('os');

// 1. Define paths
const INPUT = path.resolve(__dirname, '../source/Install_Windows.mp4');
const OUTPUT = path.resolve(__dirname, '../edit/Install_Windows.mp4');
const TEMP = path.join(os.tmpdir(), 'Install_Windows_' + Date.now());

// 2. Define segments (edit decision list)
const segments = [
    // === Segment 1: Introduction ===
    // Cut: Multiple false starts (0:13.300 - 0:25.280)
    {
        id: 'seg01_intro_false_start_1',
        start: '00:00:13.300',
        end: '00:00:25.280',
        action: 'cut',
        label: 'False starts: multiple aborted introductions',
    },
    // Cut: Continuation of false start (0:25.280 - 0:28.100)
    {
        id: 'seg01_intro_false_start_2',
        start: '00:00:25.280',
        end: '00:00:28.100',
        action: 'cut',
        label: 'Continuation of false start — partial sentence fragment',
    },
    // Keep: Clean introduction (0:28.100 - 0:35.700)
    {
        id: 'seg01_intro_clean',
        start: '00:00:28.100',
        end: '00:00:35.700',
        action: 'keep',
        label: 'Clean introduction: Welcome to this demo video...',
    },

    // === Segment 2: Downloading the Installer ===
    // Keep: Download options explanation (0:37.300 - 0:47.850)
    {
        id: 'seg02_download_options',
        start: '00:00:37.300',
        end: '00:00:47.850',
        action: 'keep',
        label: 'Explains download options: cognotic.com or GitHub releases',
    },
    // Keep: Clicks download (0:48.470 - 0:50.650)
    {
        id: 'seg02_click_download',
        start: '00:00:48.470',
        end: '00:00:50.650',
        action: 'keep',
        label: 'Clicks download button',
    },
    // Keep: Saves file (0:52.720 - 0:53.900)
    {
        id: 'seg02_save_file',
        start: '00:00:52.720',
        end: '00:00:53.900',
        action: 'keep',
        label: 'Saves the file',
    },

    // === Segment 3: Download Wait & Browser Warning ===
    // Compress: Download wait (0:53.900 - 0:59.600)
    {
        id: 'seg03_download_wait',
        start: '00:00:53.900',
        end: '00:00:59.600',
        action: 'compress',
        targetDuration: 2,
        overlay: 'Downloading...',
        muteAudio: true,
        label: 'Download wait time compressed',
    },
    // Keep: Browser security warning (0:59.600 - 1:05.510)
    {
        id: 'seg03_browser_warning',
        start: '00:00:59.600',
        end: '00:01:05.510',
        action: 'keep',
        label: 'Browser security warning about uncommonly downloaded files',
    },
    // Keep: Keeping the file (1:07.690 - 1:10.430)
    {
        id: 'seg03_keep_file',
        start: '00:01:07.690',
        end: '00:01:10.430',
        action: 'keep',
        label: 'Continues explanation of keeping the file',
    },

    // === Segment 4: Running the Installer ===
    // Cut: False start (1:12.900 - 1:13.930)
    {
        id: 'seg04_false_start',
        start: '00:01:12.900',
        end: '00:01:13.930',
        action: 'cut',
        label: 'False start: Install it like any',
    },
    // Keep: Clean installation instruction (1:14.700 - 1:17.110)
    {
        id: 'seg04_install_instruction',
        start: '00:01:14.700',
        end: '00:01:17.110',
        action: 'keep',
        label: 'Install it like any desktop application',
    },
    // Compress: Installation wait (1:17.110 - 1:29.000)
    {
        id: 'seg04_install_wait',
        start: '00:01:17.110',
        end: '00:01:29.000',
        action: 'compress',
        targetDuration: 4,
        overlay: 'Installing...',
        muteAudio: true,
        label: 'Installation progress compressed',
    },
    // Keep: Installation complete (1:29.160 - 1:29.200)
    {
        id: 'seg04_install_complete',
        start: '00:01:29.160',
        end: '00:01:30.500',
        action: 'keep',
        label: 'And there we go — installation complete',
    },

    // === Segment 5: Launching & Configuring the Application ===
    // Keep: Navigate to Start menu (1:35.680 - 1:42.980)
    {
        id: 'seg05_start_menu',
        start: '00:01:35.680',
        end: '00:01:42.980',
        action: 'keep',
        label: 'Navigate to Cognotik in Start menu and run it',
    },
    // Keep: Server-based architecture (1:43.849 - 1:50.779)
    {
        id: 'seg05_server_arch',
        start: '00:01:43.849',
        end: '00:01:50.779',
        action: 'keep',
        label: 'Explains server-based architecture',
    },
    // Keep: Taskbar icon and browser (1:51.000 - 1:58.754)
    {
        id: 'seg05_taskbar_browser',
        start: '00:01:51.000',
        end: '00:01:58.754',
        action: 'keep',
        label: 'Taskbar icon and opening browser page',
    },

    // === Segment 6: Creating a Local Login ===
    // Keep: Login system explanation (1:59.254 - 2:11.414)
    {
        id: 'seg06_login_explain',
        start: '00:01:59.254',
        end: '00:02:11.414',
        action: 'keep',
        label: 'Local login system and registration process',
    },
    // Keep: Username and password entry (2:11.854 - 2:17.500)
    {
        id: 'seg06_credentials',
        start: '00:02:11.854',
        end: '00:02:17.500',
        action: 'keep',
        label: 'Entering username and password for registration',
    },
    // Cut: Misspoken dialogue (2:18.690 - 2:21.660)
    {
        id: 'seg06_misspoken',
        start: '00:02:18.690',
        end: '00:02:21.660',
        action: 'cut',
        label: 'Misspoken: Weak passwords in my for',
    },
    // Keep: Password policy explanation (2:23.100 - 2:31.399)
    {
        id: 'seg06_password_policy',
        start: '00:02:23.100',
        end: '00:02:31.399',
        action: 'keep',
        label: 'Password policy explanation',
    },

    // === Segment 7: Confirmation Dialogue & Security Note ===
    // Keep: Confirmation dialogue (2:32.750 - 2:37.250)
    {
        id: 'seg07_confirm_dialog',
        start: '00:02:32.750',
        end: '00:02:37.250',
        action: 'keep',
        label: 'Confirmation dialogue behavior',
    },
    // Keep: Alt-Tab tip (2:38.360 - 2:41.000)
    {
        id: 'seg07_alt_tab',
        start: '00:02:38.360',
        end: '00:02:41.000',
        action: 'keep',
        label: 'Find dialogue with Alt-Tab',
    },
    // Keep: Security explanation start (2:41.399 - 2:44.080)
    {
        id: 'seg07_security_start',
        start: '00:02:41.399',
        end: '00:02:44.080',
        action: 'keep',
        label: 'Prevent people over the internet',
    },
    // Cut: Filler word (2:44.440 - 2:45.029)
    {
        id: 'seg07_filler_uh',
        start: '00:02:44.440',
        end: '00:02:45.029',
        action: 'cut',
        label: 'Filler word: uh',
    },
    // Keep: Security explanation end (2:45.240 - 2:49.860)
    {
        id: 'seg07_security_end',
        start: '00:02:45.240',
        end: '00:02:49.860',
        action: 'keep',
        label: 'Potentially creating and using your service',
    },
    // Keep: Confirm yes (2:51.000 - 2:52.100)
    {
        id: 'seg07_confirm_yes',
        start: '00:02:51.000',
        end: '00:02:52.100',
        action: 'keep',
        label: 'You say yes',
    },

    // === Segment 8: Accessing the Web UI ===
    // Compress: Page loading (2:52.100 - 3:01.960)
    {
        id: 'seg08_page_load',
        start: '00:02:52.100',
        end: '00:03:01.960',
        action: 'compress',
        targetDuration: 3,
        overlay: 'Loading...',
        muteAudio: true,
        label: 'Page loading wait compressed',
    },
    // Keep: Web UI access (3:01.960 - 3:05.210)
    {
        id: 'seg08_web_ui',
        start: '00:03:01.960',
        end: '00:03:05.210',
        action: 'keep',
        label: 'And now we have access to the web UI',
    },

    // === Segment 9: Adding an API Key ===
    // Compress: Navigate to settings (3:05.210 - 3:15.800)
    {
        id: 'seg09_nav_settings',
        start: '00:03:05.210',
        end: '00:03:15.800',
        action: 'compress',
        targetDuration: 3,
        overlay: 'Navigating to Settings...',
        muteAudio: true,
        label: 'Navigation to settings compressed',
    },
    // Keep: Settings explanation (3:15.800 - 3:19.820)
    {
        id: 'seg09_settings_explain',
        start: '00:03:15.800',
        end: '00:03:19.820',
        action: 'keep',
        label: 'Go to settings to add API key',
    },
    // Keep: Add provider (3:21.119 - 3:22.259)
    {
        id: 'seg09_add_provider',
        start: '00:03:21.119',
        end: '00:03:22.259',
        action: 'keep',
        label: 'We add provider',
    },
    // Keep: Provider recommendation (3:23.050 - 3:28.710)
    {
        id: 'seg09_provider_rec',
        start: '00:03:23.050',
        end: '00:03:28.710',
        action: 'keep',
        label: 'Supports different providers, recommends Anthropic',
    },
    // Cut: Misspoken word (3:28.710 - 3:29.929)
    {
        id: 'seg09_misspoken_angioma',
        start: '00:03:28.710',
        end: '00:03:29.929',
        action: 'cut',
        label: 'Misspoken word: angioma',
    },
    // Compress: Entering API key (3:29.929 - 3:39.869)
    {
        id: 'seg09_enter_key',
        start: '00:03:29.929',
        end: '00:03:39.869',
        action: 'compress',
        targetDuration: 3,
        overlay: 'Entering API Key...',
        muteAudio: true,
        label: 'API key entry compressed',
    },
    // Cut: Speaker says "Cut this part" (3:39.869 - 3:40.889)
    {
        id: 'seg09_cut_instruction',
        start: '00:03:39.869',
        end: '00:03:40.889',
        action: 'cut',
        label: 'Speaker edit instruction: Cut this part',
    },
    // Keep: Enter API key narration (3:41.990 - 3:43.410)
    {
        id: 'seg09_enter_key_narration',
        start: '00:03:41.990',
        end: '00:03:43.410',
        action: 'keep',
        label: 'Enter an API key',
    },
    // Keep: Save settings (3:46.029 - 3:47.649)
    {
        id: 'seg09_save_settings',
        start: '00:03:46.029',
        end: '00:03:47.649',
        action: 'keep',
        label: 'Save settings',
    },
    // Keep: Password manager aside (3:49.070 - 3:51.889)
    {
        id: 'seg09_password_manager',
        start: '00:03:49.070',
        end: '00:03:51.889',
        action: 'keep',
        label: 'Don\'t save that in the password manager',
    },

    // === Segment 10: Testing the Configuration ===
    // Keep: Begin testing (3:52.509 - 3:55.050)
    {
        id: 'seg10_begin_test',
        start: '00:03:52.509',
        end: '00:03:55.050',
        action: 'keep',
        label: 'Now we can test with basic chat',
    },
    // Keep: Page refresh needed (3:56.910 - 3:59.350)
    {
        id: 'seg10_refresh',
        start: '00:03:56.910',
        end: '00:03:59.350',
        action: 'keep',
        label: 'Need to refresh the page first',
    },
    // Keep: Models discovered (3:59.789 - 4:05.460)
    {
        id: 'seg10_models_discovered',
        start: '00:03:59.789',
        end: '00:04:05.460',
        action: 'keep',
        label: 'Models were discovered',
    },
    // Keep: Full test (4:05.789 - 4:08.190)
    {
        id: 'seg10_full_test',
        start: '00:04:05.789',
        end: '00:04:08.190',
        action: 'keep',
        label: 'Full test: Hi',
    },
    // Compress: Wait for AI response (4:08.580 - 4:12.250)
    {
        id: 'seg10_ai_response_wait',
        start: '00:04:08.580',
        end: '00:04:12.250',
        action: 'compress',
        targetDuration: 1.5,
        overlay: 'Waiting for response...',
        muteAudio: true,
        label: 'AI response wait compressed',
    },
    // Keep: Closing statement (4:12.250 - 4:18.890)
    {
        id: 'seg10_closing',
        start: '00:04:12.250',
        end: '00:04:18.890',
        action: 'keep',
        label: 'Cognotik fully installed, configured and tested',
    },
];

// 3. Run pipeline
try {
    veu.runEditPipeline({
        inputPath: INPUT,
        outputPath: OUTPUT,
        tempDir: TEMP,

        // Intro billboard
        introTitle: 'Installing Cognotik Desktop on Windows',
        introSubtitle: 'Download, Install, Configure & Test',
        introDuration: 4,

        // Outro billboard
        outroLines: [
            'Thanks for Watching!',
            'Cognotik Desktop is ready to use',
            'Visit cognotic.com for more information',
            'Check out GitHub for releases and documentation',
        ],
        outroDuration: 5,

        // Transition settings
        transitionDuration: 0.5,

        // Segments
        segments: segments,

        // Audio normalization
        normalizeAudio: true,

        // Concat method
        concatMethod: 'demuxer',
    });

    console.log('\n✅ Edit complete! Output saved to:', OUTPUT);
} catch (err) {
    console.error('\n❌ Edit failed:', err.message);
    process.exit(1);
} finally {
    veu.cleanupDir(TEMP);
}