// =============================================================================
// Webapp Builder — Automated Demo (Playwright)
// =============================================================================
// Records a narrated walkthrough of the Webapp Builder application,
// demonstrating the full workflow from idea input through pipeline execution,
// live preview, update/refinement, results inspection, Git version control,
// and usage tracking.
//
// Usage:
//   npx playwright test demo.js
//   npx playwright test demo.js --headed   # watch in real-time
// =============================================================================

const { test } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const {
    startRecording,
    highlight,
    openSessionLinkAndWaitForCompletion,
    say,
    handleLogin,
    setNarrationPath
} = require('./util');

// ---------------------------------------------------------------------------
// Environment configuration — UPDATE THESE FOR YOUR SETUP
// ---------------------------------------------------------------------------
const HUB_URL = 'http://localhost:12891/';
const CREDENTIALS_PATH = path.join(__dirname, 'credentials.json');
const NARRATION_PATH = path.join(__dirname, 'narration.json');
const SPLASH_PATH = path.join(__dirname, 'splash.html');
const VIDEO_DIR = './demo-videos';
const APP_ID = 'app-webapp-factory';

// ---------------------------------------------------------------------------
// Timing constants (milliseconds) — adjust for pacing
// ---------------------------------------------------------------------------
const TYPING_DELAY = 35;
const SHORT_PAUSE = 1500;
const MEDIUM_PAUSE = 3000;
const LONG_PAUSE = 5000;

// ---------------------------------------------------------------------------
// Load narration data
// ---------------------------------------------------------------------------
const NARRATION = JSON.parse(fs.readFileSync(NARRATION_PATH, 'utf-8'));
setNarrationPath(NARRATION_PATH);

// ---------------------------------------------------------------------------
// Pipeline step toggles — set to false to skip sections
// ---------------------------------------------------------------------------
const ENABLE_PIPELINE = true;
const ENABLE_UPDATE = true;
const ENABLE_RESULTS_PREVIEW = true;
const ENABLE_RESULTS_FILES = true;
const ENABLE_RESULTS_README = true;
const ENABLE_RESULTS_DOWNLOAD = true;
const ENABLE_GIT = true;
const ENABLE_USAGE = true;

// ---------------------------------------------------------------------------
// Sample data
// ---------------------------------------------------------------------------
const SAMPLE_IDEA = `# Pomodoro Timer Pro

A clean, modern Pomodoro timer web application for focused productivity.

## Target Users
- Students studying for exams
- Remote workers managing focus sessions
- Anyone practicing time-boxed work

## Key Features
- 25-minute work timer with 5-minute break timer
- Long break (15 min) after every 4 pomodoros
- Visual circular progress indicator
- Session counter showing completed pomodoros
- Start, pause, and reset controls
- Audio notification when timer completes
- Task label input — name what you're working on
- Session history log showing completed pomodoros with timestamps

## Design
- Minimalist, centered layout
- Dark mode by default with a toggle for light mode
- Large, readable timer display (monospace font)
- Smooth CSS animations for the progress ring
- Responsive — works on mobile and desktop
- Accent color: tomato red (#e74c3c) for work, green (#2ecc71) for breaks

## Tech
- Single HTML file with embedded CSS and JS
- No external dependencies
- LocalStorage for persisting session history`;

const SAMPLE_UPDATE_NOTES = `# Update Notes

## Bug Fixes
- Timer should not reset when switching between work and break modes

## New Features
- Add a "skip break" button to jump straight to the next work session
- Show total focus time for the day at the bottom of the page

## Design Changes
- Make the progress ring thicker (8px stroke)
- Add a subtle pulse animation when the timer is running`;

// ---------------------------------------------------------------------------
// Helper: wait for a badge to show "done"
// ---------------------------------------------------------------------------
async function waitForBadgeDone(page, badgeId, label, timeout = 300000) {
    console.log(`⏳ Waiting for ${label} badge (#${badgeId}) to show "done"...`);
    await page.waitForSelector(`#${badgeId}.done, span.step-badge.done#${badgeId}`, { timeout });
    console.log(`✅ ${label} badge is done.`);
    await page.waitForTimeout(SHORT_PAUSE);
}

// =============================================================================
// Main demo flow
// =============================================================================
test('Webapp Builder Demo', async ({ browser }) => {
    test.setTimeout(1800000); // 30 minutes for AI generation waits

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const videoOutputPath = path.resolve(VIDEO_DIR, `webapp_builder_demo_${timestamp}.mp4`);
    const context = await browser.newContext({ viewport: null });
    const page = await context.newPage();
    let recorder = null;

    try {
        // =================================================================
        // Start recording
        // =================================================================
        recorder = startRecording(videoOutputPath);
        await page.waitForTimeout(2000);
        if (!recorder.isRunning()) {
            console.warn('⚠️  ffmpeg failed to start. Continuing without video recording.');
            recorder = null;
        }

        // =================================================================
        // Splash screen — prime TTS voices
        // =================================================================
        await page.goto(`file://${SPLASH_PATH}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1500);
        await page.evaluate(() => {
            return new Promise((resolve) => {
                if (!window.speechSynthesis) { resolve(); return; }
                const voices = window.speechSynthesis.getVoices();
                if (voices.length > 0) { resolve(); return; }
                window.speechSynthesis.addEventListener('voiceschanged', () => resolve(), { once: true });
                setTimeout(resolve, 3000);
            });
        });

        // =================================================================
        // Step 0 — Introduction
        // =================================================================
        await say(page, NARRATION['INTRO'], SHORT_PAUSE);
        await page.waitForTimeout(MEDIUM_PAUSE);

        // =================================================================
        // Step 1 — Navigate to App Hub
        // =================================================================
        await page.goto(HUB_URL, { waitUntil: 'networkidle' });

        // Handle login if needed
        await handleLogin(page, CREDENTIALS_PATH, NARRATION, {
            typingDelay: TYPING_DELAY,
            pauseAfter: SHORT_PAUSE
        });

        await say(page, NARRATION['HUB_OVERVIEW'], MEDIUM_PAUSE);

        // =================================================================
        // Step 2 — Launch Webapp Builder
        // =================================================================
        await highlight(page, `#${APP_ID}`);
        await page.click(`#${APP_ID}`);
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['APP_OPENED'], MEDIUM_PAUSE);

        // =================================================================
        // Step 3 — Idea Tab: Describe the webapp
        // =================================================================
        // The Idea tab should be active by default
        await highlight(page, '[data-section="section-input"]');
        await page.click('[data-section="section-input"]');
        await page.waitForTimeout(SHORT_PAUSE);

        await say(page, NARRATION['IDEA_TAB'], MEDIUM_PAUSE);

        // Clear and type the idea
        await highlight(page, '#idea-editor');
        await page.click('#idea-editor');
        await page.fill('#idea-editor', '');
        await page.waitForTimeout(500);

        await say(page, NARRATION['IDEA_TYPING'], SHORT_PAUSE);
        await page.type('#idea-editor', SAMPLE_IDEA, { delay: TYPING_DELAY });
        await page.waitForTimeout(SHORT_PAUSE);

        // Save the idea
        await highlight(page, '#save-idea');
        await page.click('#save-idea');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['IDEA_SAVED'], SHORT_PAUSE);

        // =================================================================
        // Step 4 — Model Settings
        // =================================================================
        await say(page, NARRATION['MODEL_SETTINGS'], MEDIUM_PAUSE);

        await highlight(page, '#model-smart');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#model-fast');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#model-image');
        await page.waitForTimeout(SHORT_PAUSE);

        await say(page, NARRATION['MODEL_SETTINGS_NOTE'], SHORT_PAUSE);

        // =================================================================
        // Step 5 — Pipeline Tab: Run the build
        // =================================================================
        if (ENABLE_PIPELINE) {
            await highlight(page, '[data-section="section-pipeline"]');
            await page.click('[data-section="section-pipeline"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['PIPELINE_TAB'], MEDIUM_PAUSE);

            // Show the pipeline diagram
            await highlight(page, '#pipeline-diagram');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['PIPELINE_OVERVIEW'], MEDIUM_PAUSE);

            // Run the full pipeline
            await highlight(page, '#run-all');
            await say(page, NARRATION['PIPELINE_STARTED'], SHORT_PAUSE);
            await page.click('#run-all');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Wait for the render step to complete
            await say(page, NARRATION['PIPELINE_RUNNING'], SHORT_PAUSE);

            // Monitor via session link if available
            await openSessionLinkAndWaitForCompletion(page, page, 'webapp build', 300000).catch(() => {
                console.log('ℹ️  Session link monitoring not available, falling back to badge polling.');
            });

            await waitForBadgeDone(page, 'badge-render', 'Render Project', 300000);

            await say(page, NARRATION['PIPELINE_DONE'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['PIPELINE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 6 — Results Tab: Explore the output
        // =================================================================
        await highlight(page, '[data-section="section-results"]');
        await page.click('[data-section="section-results"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['RESULTS_TAB'], MEDIUM_PAUSE);

        // --- Live Preview ---
        if (ENABLE_RESULTS_PREVIEW) {
            await highlight(page, '[data-tab="tab-preview"]');
            await page.click('[data-tab="tab-preview"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_PREVIEW'], MEDIUM_PAUSE);

            // Refresh the preview
            await highlight(page, '#btn-preview-refresh');
            await page.click('#btn-preview-refresh');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_PREVIEW_LOADED'], LONG_PAUSE);

            // Show the console panel
            await highlight(page, '#console-panel');
            await page.waitForTimeout(SHORT_PAUSE);
        } else {
            await say(page, NARRATION['RESULTS_PREVIEW_SKIPPED'], SHORT_PAUSE);
        }

        // --- Project Files ---
        if (ENABLE_RESULTS_FILES) {
            await highlight(page, '[data-tab="tab-files"]');
            await page.click('[data-tab="tab-files"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_FILES'], MEDIUM_PAUSE);

            // Refresh file list
            await highlight(page, '#btn-refresh-files-results');
            await page.click('#btn-refresh-files-results');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_FILES_LOADED'], MEDIUM_PAUSE);
        } else {
            await say(page, NARRATION['RESULTS_FILES_SKIPPED'], SHORT_PAUSE);
        }

        // --- README ---
        if (ENABLE_RESULTS_README) {
            await highlight(page, '[data-tab="tab-readme"]');
            await page.click('[data-tab="tab-readme"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_README'], MEDIUM_PAUSE);
        } else {
            await say(page, NARRATION['RESULTS_README_SKIPPED'], SHORT_PAUSE);
        }

        // --- Download ---
        if (ENABLE_RESULTS_DOWNLOAD) {
            await highlight(page, '[data-tab="tab-download"]');
            await page.click('[data-tab="tab-download"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['RESULTS_DOWNLOAD'], MEDIUM_PAUSE);

            await highlight(page, '#btn-zip-whole-project');
            await page.waitForTimeout(SHORT_PAUSE);
            await highlight(page, '#btn-zip-code-only');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['RESULTS_DOWNLOAD_OPTIONS'], SHORT_PAUSE);
        } else {
            await say(page, NARRATION['RESULTS_DOWNLOAD_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 7 — Update Tab: Refine the webapp
        // =================================================================
        if (ENABLE_UPDATE) {
            await highlight(page, '[data-section="section-update"]');
            await page.click('[data-section="section-update"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['UPDATE_TAB'], MEDIUM_PAUSE);

            // Type update notes
            await highlight(page, '#notes-editor');
            await page.click('#notes-editor');
            await page.fill('#notes-editor', '');
            await page.waitForTimeout(500);

            await say(page, NARRATION['UPDATE_TYPING'], SHORT_PAUSE);
            await page.type('#notes-editor', SAMPLE_UPDATE_NOTES, { delay: TYPING_DELAY });
            await page.waitForTimeout(SHORT_PAUSE);

            // Save notes
            await highlight(page, '#save-notes');
            await page.click('#save-notes');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['UPDATE_SAVED'], SHORT_PAUSE);

            // Run the update
            await highlight(page, '#run-update');
            await say(page, NARRATION['UPDATE_STARTED'], SHORT_PAUSE);
            await page.click('#run-update');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['UPDATE_RUNNING'], SHORT_PAUSE);

            // Wait for update to complete
            await openSessionLinkAndWaitForCompletion(page, page, 'webapp update', 300000).catch(() => {
                console.log('ℹ️  Session link monitoring not available, falling back to badge polling.');
            });

            await waitForBadgeDone(page, 'badge-update', 'Update Project', 300000);

            await say(page, NARRATION['UPDATE_DONE'], LONG_PAUSE);

            // Show the updated preview
            await highlight(page, '[data-section="section-results"]');
            await page.click('[data-section="section-results"]');
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '[data-tab="tab-preview"]');
            await page.click('[data-tab="tab-preview"]');
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '#btn-preview-refresh');
            await page.click('#btn-preview-refresh');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['UPDATE_PREVIEW'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['UPDATE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 8 — Git Tab: Version control
        // =================================================================
        if (ENABLE_GIT) {
            await highlight(page, '[data-section="section-git"]');
            await page.click('[data-section="section-git"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_TAB'], MEDIUM_PAUSE);

            // Initialize repository
            await highlight(page, '#btn-git-init');
            await page.click('#btn-git-init');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_INIT'], SHORT_PAUSE);

            // Refresh status
            await highlight(page, '#btn-git-status');
            await page.click('#btn-git-status');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_STATUS'], SHORT_PAUSE);

            // Commit changes
            await highlight(page, '#git-commit-message');
            await page.fill('#git-commit-message', '');
            await page.type('#git-commit-message', 'Initial build: Pomodoro Timer Pro', { delay: TYPING_DELAY });
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '#btn-git-commit');
            await page.click('#btn-git-commit');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_COMMITTED'], SHORT_PAUSE);

            // Show commit log
            await highlight(page, '#btn-git-log');
            await page.click('#btn-git-log');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_LOG'], MEDIUM_PAUSE);

            // Quick snapshot action
            await highlight(page, '#quick-snapshot');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['GIT_QUICK_ACTIONS'], SHORT_PAUSE);
        } else {
            await say(page, NARRATION['GIT_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 9 — Usage Tab: Review costs
        // =================================================================
        if (ENABLE_USAGE) {
            await highlight(page, '[data-section="section-usage"]');
            await page.click('[data-section="section-usage"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['USAGE_TAB'], MEDIUM_PAUSE);

            // Refresh usage
            await highlight(page, '#btn-refresh-usage');
            await page.click('#btn-refresh-usage');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['USAGE_SUMMARY'], MEDIUM_PAUSE);

            // Show per-task usage
            await highlight(page, '#btn-refresh-task-usage');
            await page.click('#btn-refresh-task-usage');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['USAGE_TASKS'], SHORT_PAUSE);
        } else {
            await say(page, NARRATION['USAGE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 10 — Launch the app
        // =================================================================
        await say(page, NARRATION['LAUNCH_INTRO'], SHORT_PAUSE);

        await highlight(page, '#nav-launch-app');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['LAUNCH_READY'], MEDIUM_PAUSE);

        // =================================================================
        // Outro
        // =================================================================
        await say(page, NARRATION['OUTRO'], LONG_PAUSE);
        await page.waitForTimeout(LONG_PAUSE);

        console.log('\n✅ Demo complete.');
    } catch (error) {
        console.error('❌ Demo failed with error:', error);
        throw error;
    } finally {
        if (recorder && recorder.isRunning()) {
            await recorder.stop();
        }
        await context.close();
    }
});