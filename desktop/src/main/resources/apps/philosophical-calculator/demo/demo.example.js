// =============================================================================
// EXAMPLE — Automated Demo (Playwright)
// =============================================================================
// This is an example configuration file for the automated demo.
//
// Usage:
//   npx playwright test demo.js
// =============================================================================

const {test} = require('@playwright/test');
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
const APP_ID = 'app-philosophical-calculator';

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
// Tell util.js where narration.json lives so audio paths resolve correctly
setNarrationPath(NARRATION_PATH);

// ---------------------------------------------------------------------------
// Model configuration — Make sure to set the models in the App UI
// ---------------------------------------------------------------------------
const SMART_MODEL = 'gemini-3-flash-preview';
const FAST_MODEL = 'gemini-3-flash-preview';
const IMAGE_MODEL = 'gemini-3.1-flash-image-preview';

// Helper: wait for a badge to show "done"
async function waitForBadgeDone(page, badgeId, label, timeout = 300000) {
    console.log(`⏳ Waiting for ${label} badge (#${badgeId}) to show "done"...`);
    await page.waitForSelector(`#${badgeId}.done, span.step-badge.done#${badgeId}`, {timeout}); // TODO: This may need to be adjusted based on your app's DOM structure and badge implementation
    console.log(`✅ ${label} badge is done.`);
    await page.waitForTimeout(SHORT_PAUSE);
}

// =============================================================================
// Main demo flow
// =============================================================================
test('Philosophical Calculator Demo', async ({browser}) => {
    test.setTimeout(1800000); // 30 minutes for AI generation waits

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const videoOutputPath = path.resolve(VIDEO_DIR, `philo_calc_demo_${timestamp}.mp4`);
    const context = await browser.newContext({viewport: null});
    const page = await context.newPage();
    let recorder = null;

    try {
        recorder = startRecording(videoOutputPath);
        await page.waitForTimeout(2000);
        if (!recorder.isRunning()) {
            console.warn('⚠️  ffmpeg failed to start. Continuing without video recording.');
            recorder = null;
        }

        // Prime Web Speech API voices
        await page.goto(`file://${SPLASH_PATH}`, {waitUntil: 'domcontentloaded'});
        await page.waitForTimeout(1500); // Let splash animations begin
        await page.evaluate(() => {
            return new Promise((resolve) => {
                if (!window.speechSynthesis) {
                    resolve();
                    return;
                }
                const voices = window.speechSynthesis.getVoices();
                if (voices.length > 0) {
                    resolve();
                    return;
                }
                window.speechSynthesis.addEventListener('voiceschanged', () => resolve(), {once: true});
                setTimeout(resolve, 3000);
            });
        });

        // =================================================================
        // Step 0 — Open the App Hub
        // =================================================================
        await say(page, NARRATION['INTRO'], SHORT_PAUSE);
        await page.waitForTimeout(MEDIUM_PAUSE); // Linger on splash before navigating away
        await page.goto(HUB_URL, {waitUntil: 'networkidle'});

        // -----------------------------------------------------------------
        // Handle Login if needed
        // -----------------------------------------------------------------
        await handleLogin(page, CREDENTIALS_PATH, NARRATION, {
            typingDelay: TYPING_DELAY,
            pauseAfter: SHORT_PAUSE
        });

        await say(page, NARRATION['HUB_OVERVIEW'], MEDIUM_PAUSE);

        // =================================================================
        // Step 1 — Launch the Philosophical Calculator
        // =================================================================
        await highlight(page, `#${APP_ID}`);
        await page.click(`#${APP_ID}`);
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['APP_OPENED'], MEDIUM_PAUSE);

        // -----------------------------------------------------------------
        // App-specific demo goes here
        // -----------------------------------------------------------------

        await say(page, NARRATION['OUTRO'], LONG_PAUSE);
        await page.waitForTimeout(LONG_PAUSE);
        console.log('\n✅ Demo complete.');
    } catch (error) {
        console.error('❌ Demo failed with error:', error);
        throw error;
    } finally {
        // Stop recording if active
        if (recorder && recorder.isRunning()) {
            await recorder.stop();
        }
        await context.close();
    }
});