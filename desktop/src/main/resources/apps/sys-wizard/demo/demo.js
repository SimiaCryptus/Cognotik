// =============================================================================
// System Wizard — Automated Demo (Playwright)
// =============================================================================
// Records a narrated walkthrough of the System Wizard app.
//
// Usage:
//   npx playwright test demo.js
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
     setNarrationPath,
     diagnosticSnapshot
} = require('./util');

// ---------------------------------------------------------------------------
// Environment configuration — UPDATE THESE FOR YOUR SETUP
// ---------------------------------------------------------------------------
const HUB_URL = 'http://localhost:12891/';
const CREDENTIALS_PATH = path.join(__dirname, 'credentials.json');
const NARRATION_PATH = path.join(__dirname, 'narration.json');
const SPLASH_PATH = path.join(__dirname, 'splash.html');
const VIDEO_DIR = './demo-videos';
const APP_ID = 'app-sys-wizard';

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
// Model configuration — Make sure to set the models in the App UI
// ---------------------------------------------------------------------------
const SMART_MODEL = 'gemini-3-flash-preview';
const FAST_MODEL = 'gemini-3-flash-preview';

// ---------------------------------------------------------------------------
// Pipeline step toggles — set to false to skip sections
// ---------------------------------------------------------------------------
const ENABLE_SETTINGS = true;
const ENABLE_EXAMPLE_PROMPTS = false;
const ENABLE_MANUAL_MODE = false;
const ENABLE_INDIVIDUAL_STEPS = false;
const ENABLE_FULL_PIPELINE = true;
const ENABLE_USAGE_TAB = true;
const ENABLE_PLATFORM_TOGGLE = true;

// ---------------------------------------------------------------------------
// Sample goal text
// ---------------------------------------------------------------------------
const SAMPLE_GOAL = `List all running processes sorted by memory usage (descending), show the top 20, and write a summary report to /tmp/sys-wizard-report.txt that includes:
- Current date and time
- Hostname and OS version
- Top 20 processes by memory with PID, name, and memory percentage
- Total memory usage and available memory
- Disk usage summary for all mounted filesystems`;

// ---------------------------------------------------------------------------
// Helper: wait for a badge to show "done"
// ---------------------------------------------------------------------------
async function waitForBadgeDone(page, badgeId, label, timeout = 300000) {
    console.log(`⏳ Waiting for ${label} badge (#${badgeId}) to show "done"...`);
    await page.waitForSelector(`#${badgeId}.done, span.step-badge.done#${badgeId}`, { timeout });
    console.log(`✅ ${label} badge is done.`);
    await page.waitForTimeout(SHORT_PAUSE);
}

// ---------------------------------------------------------------------------
// Helper: wait for batch log to indicate completion
// ---------------------------------------------------------------------------
async function waitForPipelineCompletion(page, timeout = 600000) {
    console.log('⏳ Waiting for pipeline to complete...');
    // Wait for both badges to show done
    await page.waitForSelector('#badge-codegen.done, span.step-badge.done#badge-codegen', { timeout });
    console.log('  ✅ Code generation done.');
    await page.waitForSelector('#badge-run.done, span.step-badge.done#badge-run', { timeout });
    console.log('  ✅ Run & fix done.');
    await page.waitForTimeout(SHORT_PAUSE);
}

// =============================================================================
// Main demo flow
// =============================================================================
test('System Wizard Demo', async ({ browser }) => {
    test.setTimeout(1800000); // 30 minutes for AI generation waits

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const videoOutputPath = path.resolve(VIDEO_DIR, `sys_wizard_demo_${timestamp}.mp4`);
    const context = await browser.newContext({ viewport: null });
    const page = await context.newPage();
    let recorder = null;

    try {
        recorder = startRecording(videoOutputPath);
        await page.waitForTimeout(2000);
        if (!recorder.isRunning()) {
            console.warn('⚠️  ffmpeg failed to start. Continuing without video recording.');
            recorder = null;
        }

        // =================================================================
        // Step 0 — Splash Screen & Voice Priming
        // =================================================================
        await page.goto(`file://${SPLASH_PATH}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1500);
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
                window.speechSynthesis.addEventListener('voiceschanged', () => resolve(), { once: true });
                setTimeout(resolve, 3000);
            });
        });

        await say(page, NARRATION['INTRO'], SHORT_PAUSE);
        await page.waitForTimeout(MEDIUM_PAUSE);
     await diagnosticSnapshot(page, '00-splash');

        // =================================================================
        // Step 1 — Open the App Hub
        // =================================================================
        await page.goto(HUB_URL, { waitUntil: 'networkidle' });

        // Handle Login if needed
        await handleLogin(page, CREDENTIALS_PATH, NARRATION, {
            typingDelay: TYPING_DELAY,
            pauseAfter: SHORT_PAUSE
        });

        await say(page, NARRATION['HUB_OVERVIEW'], MEDIUM_PAUSE);
     await diagnosticSnapshot(page, '01-hub-overview');

        // =================================================================
        // Step 2 — Launch the System Wizard
        // =================================================================
        await highlight(page, `#${APP_ID}`);
        await page.click(`#${APP_ID}`);
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['APP_OPENED'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '02-app-opened');

        // =================================================================
        // Step 3 — Platform Toggle (optional)
        // =================================================================
        if (ENABLE_PLATFORM_TOGGLE) {
            await highlight(page, '#platform-toggle');
            await page.waitForTimeout(SHORT_PAUSE);
            await say(page, NARRATION['PLATFORM_TOGGLE'], SHORT_PAUSE);

            // Briefly toggle to Windows and back to Shell
            await highlight(page, '.platform-btn[data-platform="cmd"]');
            await page.click('.platform-btn[data-platform="cmd"]');
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '.platform-btn[data-platform="sh"]');
            await page.click('.platform-btn[data-platform="sh"]');
            await page.waitForTimeout(SHORT_PAUSE);
             await diagnosticSnapshot(page, '03-platform-toggle');
        }

        // =================================================================
        // Step 4 — Goal Tab: Define the Goal
        // =================================================================
        await say(page, NARRATION['GOAL_TAB'], MEDIUM_PAUSE);

        // Show tips
        await highlight(page, '.card-tips');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['GOAL_TIPS'], SHORT_PAUSE);

        // Example prompts (optional)
        if (ENABLE_EXAMPLE_PROMPTS) {
            await highlight(page, '#example-prompts');
            await say(page, NARRATION['EXAMPLE_PROMPTS'], SHORT_PAUSE);

            // Click an example prompt to show how it works
            await highlight(page, '.btn-example[data-example="sysinfo"]');
            await page.click('.btn-example[data-example="sysinfo"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Clear it so we can type our own
            await page.fill('#goal-editor', '');
            await page.waitForTimeout(SHORT_PAUSE);
        } else {
            await say(page, NARRATION['EXAMPLE_PROMPTS_SKIPPED'], SHORT_PAUSE);
        }

        // Type the goal
        await say(page, NARRATION['GOAL_TYPING'], SHORT_PAUSE);
        await highlight(page, '#goal-editor');
        await page.click('#goal-editor');
        await page.waitForTimeout(500);
        await page.type('#goal-editor', SAMPLE_GOAL, { delay: TYPING_DELAY });
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Save the goal
        await highlight(page, '#save-goal');
        await page.click('#save-goal');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['GOAL_SAVED'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '04-goal-saved');

        // =================================================================
        // Step 5 — Settings Tab: Configure Models (optional)
        // =================================================================
        if (ENABLE_SETTINGS) {
            await highlight(page, '[data-section="section-settings"]');
            await page.click('[data-section="section-settings"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['SETTINGS_TAB'], MEDIUM_PAUSE);

            // Wait for models to load
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['SETTINGS_MODELS'], SHORT_PAUSE);

            // Select smart model
            await highlight(page, '#setting-smart-model');
            const smartModelSelect = page.locator('#setting-smart-model');
            const smartOptions = await smartModelSelect.locator('option').allTextContents();
            const smartMatch = smartOptions.find(opt => opt.includes(SMART_MODEL));
            if (smartMatch) {
                await smartModelSelect.selectOption({ label: smartMatch });
            } else if (smartOptions.length > 1) {
                // Select the first non-placeholder option
                await smartModelSelect.selectOption({ index: 1 });
            }
            await page.waitForTimeout(SHORT_PAUSE);

            // Select fast model
            await highlight(page, '#setting-fast-model');
            const fastModelSelect = page.locator('#setting-fast-model');
            const fastOptions = await fastModelSelect.locator('option').allTextContents();
            const fastMatch = fastOptions.find(opt => opt.includes(FAST_MODEL));
            if (fastMatch) {
                await fastModelSelect.selectOption({ label: fastMatch });
            } else if (fastOptions.length > 1) {
                await fastModelSelect.selectOption({ index: 1 });
            }
            await page.waitForTimeout(SHORT_PAUSE);

            // Save settings
            await highlight(page, '#save-model-settings');
            await page.click('#save-model-settings');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['SETTINGS_SAVED'], MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '05-settings-saved');
        } else {
            await say(page, NARRATION['SETTINGS_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 6 — Pipeline Tab
        // =================================================================
        await highlight(page, '[data-section="section-pipeline"]');
        await page.click('[data-section="section-pipeline"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['PIPELINE_TAB'], MEDIUM_PAUSE);

        // Highlight the pipeline diagram
        await highlight(page, '.pipeline-diagram');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['PIPELINE_OVERVIEW'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06-pipeline-tab');

        // -----------------------------------------------------------------
        // Manual mode showcase (optional)
        // -----------------------------------------------------------------
        if (ENABLE_MANUAL_MODE) {
            await say(page, NARRATION['MANUAL_MODE_INTRO'], SHORT_PAUSE);

            await highlight(page, '#mode-manual');
            await page.click('#mode-manual');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Show the manual editor briefly
            await highlight(page, '#script-editor');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Switch back to AI generate mode
            await highlight(page, '#mode-generate');
            await page.click('#mode-generate');
            await page.waitForTimeout(SHORT_PAUSE);
        } else {
            await say(page, NARRATION['MANUAL_MODE_SKIPPED'], SHORT_PAUSE);
        }

        // -----------------------------------------------------------------
        // Individual steps (optional) or full pipeline
        // -----------------------------------------------------------------
        if (ENABLE_INDIVIDUAL_STEPS) {
            // === Step 6a: Generate Script ===
            await say(page, NARRATION['CODEGEN_INTRO'], SHORT_PAUSE);

            await highlight(page, '#btn-generate');
            await page.click('#btn-generate');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['CODEGEN_STARTED'], MEDIUM_PAUSE);

            // Wait for code generation to complete
            await waitForBadgeDone(page, 'badge-codegen', 'Code Generation', 300000);

            await say(page, NARRATION['CODEGEN_DONE'], MEDIUM_PAUSE);

            // View the generated script
            await highlight(page, '#viewer-codegen');
            await page.waitForTimeout(MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '07-codegen-done');

            // === Step 6b: Run & Fix ===
            await say(page, NARRATION['RUN_FIX_INTRO'], SHORT_PAUSE);

            await highlight(page, '#btn-run-fix');
            await page.click('#btn-run-fix');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['RUN_FIX_STARTED'], MEDIUM_PAUSE);

            // Wait for run & fix to complete
            await waitForBadgeDone(page, 'badge-run', 'Run & Fix', 300000);

            await say(page, NARRATION['RUN_FIX_DONE'], MEDIUM_PAUSE);

            // View the execution log
            await highlight(page, '#viewer-run');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // View the final script
            await highlight(page, '#btn-view-final-script');
            await page.click('#btn-view-final-script');
            await page.waitForTimeout(MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '08-run-fix-done');
        } else {
            await say(page, NARRATION['CODEGEN_SKIPPED'], SHORT_PAUSE);
            await say(page, NARRATION['RUN_FIX_SKIPPED'], SHORT_PAUSE);
        }

        // -----------------------------------------------------------------
        // Full pipeline run (optional)
        // -----------------------------------------------------------------
        if (ENABLE_FULL_PIPELINE) {
            // Scroll to the batch execution card
            await page.locator('#run-all').scrollIntoViewIfNeeded().catch(() => {});
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['FULL_PIPELINE_INTRO'], MEDIUM_PAUSE);

            await highlight(page, '#run-all');
            await page.click('#run-all');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['FULL_PIPELINE_STARTED'], MEDIUM_PAUSE);

            // Show the batch log as it updates
            await highlight(page, '#batch-log');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Wait for the full pipeline to complete
            await waitForPipelineCompletion(page, 600000);

            // Highlight the completed pipeline diagram
            await page.locator('.pipeline-diagram').scrollIntoViewIfNeeded().catch(() => {});
            await highlight(page, '.pipeline-diagram');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['FULL_PIPELINE_DONE'], LONG_PAUSE);
             await diagnosticSnapshot(page, '09-full-pipeline-done');
        } else {
            await say(page, NARRATION['FULL_PIPELINE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 7 — Results Tab
        // =================================================================
        await highlight(page, '[data-section="section-results"]');
        await page.click('[data-section="section-results"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['RESULTS_TAB'], MEDIUM_PAUSE);

        // --- Script tab ---
        // Refresh the script viewer
        const scriptRefreshBtn = page.locator('#tab-script .btn-refresh');
        if (await scriptRefreshBtn.isVisible()) {
            await scriptRefreshBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        await highlight(page, '#result-script');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['RESULTS_SCRIPT'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '10-results-script');

        // Show copy button
        await highlight(page, '#copy-script');
        await page.waitForTimeout(SHORT_PAUSE);

        // --- Execution Log tab ---
        await highlight(page, '.results-tab[data-tab="tab-log"]');
        await page.click('.results-tab[data-tab="tab-log"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Refresh the log viewer
        const logRefreshBtn = page.locator('#tab-log .btn-refresh');
        if (await logRefreshBtn.isVisible()) {
            await logRefreshBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        await highlight(page, '#result-log');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['RESULTS_LOG'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '11-results-log');

        // --- Goal tab ---
        await highlight(page, '.results-tab[data-tab="tab-goal"]');
        await page.click('.results-tab[data-tab="tab-goal"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Refresh the goal viewer
        const goalRefreshBtn = page.locator('#tab-goal .btn-refresh');
        if (await goalRefreshBtn.isVisible()) {
            await goalRefreshBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        await highlight(page, '#result-goal');
        await page.waitForTimeout(SHORT_PAUSE);
        await say(page, NARRATION['RESULTS_GOAL'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '12-results-goal');

        // =================================================================
        // Step 8 — Usage Tab (optional)
        // =================================================================
        if (ENABLE_USAGE_TAB) {
            await highlight(page, '[data-section="section-usage"]');
            await page.click('[data-section="section-usage"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Refresh usage data
            const refreshUsageBtn = page.locator('#refresh-usage');
            if (await refreshUsageBtn.isVisible()) {
                await refreshUsageBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            await highlight(page, '#usage-totals-grid');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['USAGE_TAB'], MEDIUM_PAUSE);

            // Show per-model breakdown if available
            const breakdownCard = page.locator('#usage-breakdown-card');
            if (await breakdownCard.isVisible()) {
                await highlight(page, '#usage-breakdown-card');
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            // Show child session usage if available
            const childrenCard = page.locator('#usage-children-card');
            if (await childrenCard.isVisible()) {
                await highlight(page, '#usage-children-card');
                await page.waitForTimeout(SHORT_PAUSE);
            }
             await diagnosticSnapshot(page, '13-usage-tab');
        } else {
            await say(page, NARRATION['USAGE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 9 — Outro
        // =================================================================
        await say(page, NARRATION['OUTRO'], LONG_PAUSE);
        await page.waitForTimeout(LONG_PAUSE);
         await diagnosticSnapshot(page, '14-outro');

        console.log('\n✅ System Wizard demo complete.');
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