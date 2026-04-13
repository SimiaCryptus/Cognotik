// =============================================================================
// Omega — AI-Powered DocOps App Generator — Automated Demo (Playwright)
// =============================================================================
// Records a narrated walkthrough of the Omega app generation workflow.
//
// Usage:
//   npx playwright test demo.js
//   npx playwright test demo.js --headed    # Watch in browser
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
const APP_ID = 'app-omega';

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
const IMAGE_MODEL = 'gemini-3.1-flash-image-preview';

// ---------------------------------------------------------------------------
// Pipeline step toggles — set to false to skip individual steps
// ---------------------------------------------------------------------------
const ENABLE_REQUIREMENTS = true;
const ENABLE_REVIEW = true;
const ENABLE_REFINE = true;
const ENABLE_PIPELINE = true;
const ENABLE_UI = true;
const ENABLE_ITERATE = false;       // Skip iteration in demo (just show the tab)
const ENABLE_GIT = true;
const ENABLE_USAGE = true;
const ENABLE_OPEN_APP = true;

// Use batch mode (Run All) instead of individual steps
const USE_BATCH_GENERATION = false;

// ---------------------------------------------------------------------------
// Sample app idea for the demo
// ---------------------------------------------------------------------------
const SAMPLE_IDEA = `I want an app that takes a research topic and produces a comprehensive literature review package.

Purpose: Generate structured research materials from a topic description
User Input: A markdown file describing the research topic, key questions, and scope constraints

Desired Output:
  - Literature review outline with major themes and sub-topics
  - Key findings summary organized by theme
  - Research gaps analysis identifying areas needing further investigation
  - Annotated bibliography template with categorized sources
  - A structured outline for a research paper

Pipeline Steps:
  1. Analyze the topic and extract key themes, questions, and scope
  2. Brainstorm potential research angles and sub-topics
  3. Research existing literature online using CrawlerAgent
  4. Synthesize findings into a structured literature review outline
  5. Identify research gaps and opportunities
  6. Generate the annotated bibliography template
  7. Produce the final research paper outline

Special Requirements:
  - Use CrawlerAgent for web research in step 3
  - The literature review should follow academic conventions
  - Include a "methodology notes" section suggesting research approaches
  - The UI should have separate tabs for each output document`;

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
// Helper: click a nav tab and wait for section to be visible
// ---------------------------------------------------------------------------
async function navigateToTab(page, sectionId, narrationKey, pauseAfter = MEDIUM_PAUSE) {
    const navSelector = `[data-section="${sectionId}"]`;
    await highlight(page, navSelector);
    await page.click(navSelector);
    await page.waitForTimeout(SHORT_PAUSE);
    if (narrationKey && NARRATION[narrationKey]) {
        await say(page, NARRATION[narrationKey], pauseAfter);
    }
}

// ---------------------------------------------------------------------------
// Helper: click a Run button and wait for completion
// ---------------------------------------------------------------------------
async function runStepAndWait(page, context, runButtonSelector, badgeId, label, timeout = 300000) {
    await highlight(page, runButtonSelector);
    await page.click(runButtonSelector);
    await page.waitForTimeout(SHORT_PAUSE);

    // Try to open session link if one appears
    await openSessionLinkAndWaitForCompletion(page, context, label, timeout).catch(() => {
        console.log(`ℹ️  No session link for ${label}, waiting for badge instead.`);
    });

    await waitForBadgeDone(page, badgeId, label, timeout);
}

// =============================================================================
// Main demo flow
// =============================================================================
test('Omega App Generator Demo', async ({ browser }) => {
    test.setTimeout(1800000); // 30 minutes for AI generation waits

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const videoOutputPath = path.resolve(VIDEO_DIR, `omega_demo_${timestamp}.mp4`);
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
        // Step 0 — Splash screen & TTS voice priming
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
        // Step 2 — Launch Omega
        // =================================================================
        await highlight(page, `#${APP_ID}`);
        await page.click(`#${APP_ID}`);
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['APP_OPENED'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '02-app-opened');

        // =================================================================
        // Step 3 — Idea Tab: Describe the app idea
        // =================================================================
        await navigateToTab(page, 'section-idea', 'IDEA_TAB');

        await say(page, NARRATION['IDEA_TYPING'], SHORT_PAUSE);

        // Clear existing content and type the sample idea
        await highlight(page, '#idea-editor');
        await page.click('#idea-editor');
        await page.evaluate(() => {
            document.getElementById('idea-editor').value = '';
        });
        await page.type('#idea-editor', SAMPLE_IDEA, { delay: TYPING_DELAY / 3 }); // Faster typing for long text
        await page.waitForTimeout(SHORT_PAUSE);

        // Save the idea
        await highlight(page, '#save-idea');
        await page.click('#save-idea');
        await page.waitForTimeout(SHORT_PAUSE);

        await say(page, NARRATION['IDEA_SAVED'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '03-idea-saved');

        // =================================================================
        // Step 4 — Models Tab: Configure AI models
        // =================================================================
        await navigateToTab(page, 'section-settings', 'MODELS_TAB');

        // Refresh models list
        await highlight(page, '#refresh-models');
        await page.click('#refresh-models');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Select smart model
        const smartSelect = page.locator('#model-smart');
        await highlight(page, '#model-smart');
        try {
            await smartSelect.selectOption({ label: new RegExp(SMART_MODEL, 'i') });
        } catch {
            // Try selecting by value if label doesn't match
            const options = await smartSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(SMART_MODEL.toLowerCase()));
            if (match) await smartSelect.selectOption({ label: match });
        }
        await page.waitForTimeout(SHORT_PAUSE);

        // Select fast model
        const fastSelect = page.locator('#model-fast');
        await highlight(page, '#model-fast');
        try {
            await fastSelect.selectOption({ label: new RegExp(FAST_MODEL, 'i') });
        } catch {
            const options = await fastSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(FAST_MODEL.toLowerCase()));
            if (match) await fastSelect.selectOption({ label: match });
        }
        await page.waitForTimeout(SHORT_PAUSE);

        // Select image model
        const imageSelect = page.locator('#model-image');
        await highlight(page, '#model-image');
        try {
            await imageSelect.selectOption({ label: new RegExp(IMAGE_MODEL, 'i') });
        } catch {
            const options = await imageSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(IMAGE_MODEL.toLowerCase()));
            if (match) await imageSelect.selectOption({ label: match });
        }
        await page.waitForTimeout(SHORT_PAUSE);

        // Save model settings
        await highlight(page, '#save-model-settings');
        await page.click('#save-model-settings');
        await page.waitForTimeout(SHORT_PAUSE);

        await say(page, NARRATION['MODELS_CONFIGURED'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '04-models-configured');

        // =================================================================
        // Step 5 — Generate Tab: Run the generation pipeline
        // =================================================================
        await navigateToTab(page, 'section-pipeline', 'GENERATE_TAB');

        if (USE_BATCH_GENERATION) {
            // ----- Batch mode: Run All -----
            await say(page, NARRATION['RUN_ALL_INTRO'], SHORT_PAUSE);

            await highlight(page, '#run-all-generate');
            await page.click('#run-all-generate');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['RUN_ALL_STARTED'], SHORT_PAUSE);

            // Wait for all badges to complete
            await waitForBadgeDone(page, 'badge-requirements', 'Requirements', 300000);
            await waitForBadgeDone(page, 'badge-review', 'Review', 300000);
            await waitForBadgeDone(page, 'badge-refine', 'Refine', 300000);
            await waitForBadgeDone(page, 'badge-pipeline', 'Pipeline', 300000);
            await waitForBadgeDone(page, 'badge-ui', 'UI', 300000);

            await say(page, NARRATION['RUN_ALL_DONE'], LONG_PAUSE);
             await diagnosticSnapshot(page, '05-run-all-done');

        } else {
            // ----- Individual step mode -----

            // --- Step 5a: Generate Requirements ---
            if (ENABLE_REQUIREMENTS) {
                await say(page, NARRATION['REQUIREMENTS_STARTED'], SHORT_PAUSE);

                // Find the Run button for requirements (Step 1)
                const reqRunBtn = page.locator('button.btn-run[data-badge="badge-requirements"]');
                await runStepAndWait(page, context, 'button.btn-run[data-badge="badge-requirements"]', 'badge-requirements', 'Requirements', 300000);

                await say(page, NARRATION['REQUIREMENTS_DONE'], MEDIUM_PAUSE);
                 await diagnosticSnapshot(page, '05a-requirements-done');

                // View the result
                const reqViewBtn = page.locator('button.btn-view[data-viewer="viewer-requirements"]');
                await highlight(page, 'button.btn-view[data-viewer="viewer-requirements"]');
                await reqViewBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            // --- Step 5b: Review Requirements ---
            if (ENABLE_REVIEW) {
                await say(page, NARRATION['REVIEW_STARTED'], SHORT_PAUSE);

                await runStepAndWait(page, context, 'button.btn-run[data-badge="badge-review"]', 'badge-review', 'Review', 300000);

                await say(page, NARRATION['REVIEW_DONE'], MEDIUM_PAUSE);
                 await diagnosticSnapshot(page, '05b-review-done');

                // View the result
                const reviewViewBtn = page.locator('button.btn-view[data-viewer="viewer-review"]');
                await highlight(page, 'button.btn-view[data-viewer="viewer-review"]');
                await reviewViewBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            // --- Step 5c: Refine Requirements ---
            if (ENABLE_REFINE) {
                await say(page, NARRATION['REFINE_STARTED'], SHORT_PAUSE);

                await runStepAndWait(page, context, 'button.btn-run[data-badge="badge-refine"]', 'badge-refine', 'Refine', 300000);

                await say(page, NARRATION['REFINE_DONE'], MEDIUM_PAUSE);
                 await diagnosticSnapshot(page, '05c-refine-done');

                // View the refined requirements
                const refineViewBtn = page.locator('button.btn-view[data-viewer="viewer-refine"]');
                await highlight(page, 'button.btn-view[data-viewer="viewer-refine"]');
                await refineViewBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            // --- Step 5d: Generate Pipeline Ops ---
            if (ENABLE_PIPELINE) {
                await say(page, NARRATION['PIPELINE_STARTED'], SHORT_PAUSE);

                await runStepAndWait(page, context, 'button.btn-run[data-badge="badge-pipeline"]', 'badge-pipeline', 'Pipeline Ops', 300000);

                await say(page, NARRATION['PIPELINE_DONE'], MEDIUM_PAUSE);
                 await diagnosticSnapshot(page, '05d-pipeline-done');

                // View the README
                const pipelineViewBtn = page.locator('button.btn-view[data-viewer="viewer-pipeline"]');
                await highlight(page, 'button.btn-view[data-viewer="viewer-pipeline"]');
                await pipelineViewBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }

            // --- Step 5e: Generate UI ---
            if (ENABLE_UI) {
                await say(page, NARRATION['UI_STARTED'], SHORT_PAUSE);

                await runStepAndWait(page, context, 'button.btn-run[data-badge="badge-ui"]', 'badge-ui', 'UI Generation', 300000);

                await say(page, NARRATION['UI_DONE'], LONG_PAUSE);
                 await diagnosticSnapshot(page, '05e-ui-done');

                // View the UI source
                const uiViewBtn = page.locator('button.btn-view[data-viewer="viewer-ui"]');
                await highlight(page, 'button.btn-view[data-viewer="viewer-ui"]');
                await uiViewBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
            }
        }

        // =================================================================
        // Step 6 — Results Tab: Browse generated files
        // =================================================================
        await navigateToTab(page, 'section-results', 'RESULTS_TAB');

        // Refresh file list
        await highlight(page, '#refresh-file-tree');
        await page.click('#refresh-file-tree');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // --- README sub-tab ---
        await highlight(page, '[data-tab="tab-readme"]');
        await page.click('[data-tab="tab-readme"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-readme');
        await page.click('#refresh-readme');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_README'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06a-results-readme');

        // --- Requirements sub-tab ---
        await highlight(page, '[data-tab="tab-requirements"]');
        await page.click('[data-tab="tab-requirements"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-requirements');
        await page.click('#refresh-requirements');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_REQUIREMENTS'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06b-results-requirements');

        // --- Review sub-tab ---
        await highlight(page, '[data-tab="tab-review"]');
        await page.click('[data-tab="tab-review"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-review');
        await page.click('#refresh-review');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_REVIEW'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06c-results-review');

        // --- Pipeline Ops sub-tab ---
        await highlight(page, '[data-tab="tab-ops"]');
        await page.click('[data-tab="tab-ops"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-ops-list');
        await page.click('#refresh-ops-list');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_OPS'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06d-results-ops');

        // --- UI Source sub-tab ---
        await highlight(page, '[data-tab="tab-ui-preview"]');
        await page.click('[data-tab="tab-ui-preview"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-ui-source');
        await page.click('#refresh-ui-source');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_UI'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06e-results-ui');

        // --- All Files sub-tab ---
        await highlight(page, '[data-tab="tab-files"]');
        await page.click('[data-tab="tab-files"]');
        await page.waitForTimeout(SHORT_PAUSE);
        await highlight(page, '#refresh-all-files');
        await page.click('#refresh-all-files');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await say(page, NARRATION['RESULTS_FILES'], MEDIUM_PAUSE);
         await diagnosticSnapshot(page, '06f-results-files');

        // =================================================================
        // Step 7 — Open Generated App (if available)
        // =================================================================
        if (ENABLE_OPEN_APP) {
            // Check if the Open App button is visible
            const openAppBtn = page.locator('#open-app-results');
            const isVisible = await openAppBtn.isVisible().catch(() => false);
            if (isVisible) {
                await say(page, NARRATION['OPEN_APP'], SHORT_PAUSE);
                await highlight(page, '#open-app-results');
                await page.click('#open-app-results');
                await page.waitForTimeout(LONG_PAUSE);
                await say(page, NARRATION['OPEN_APP_DONE'], LONG_PAUSE);
                 await diagnosticSnapshot(page, '07-open-app');
            }
        }

        // =================================================================
        // Step 8 — Iterate Tab: Show the refinement workflow
        // =================================================================
        await navigateToTab(page, 'section-iterate', 'ITERATE_TAB');

        if (ENABLE_ITERATE) {
            // Pipeline notes
            await say(page, NARRATION['ITERATE_PIPELINE_NOTES'], SHORT_PAUSE);
            await highlight(page, '#pipeline-notes-editor');
            await page.click('#pipeline-notes-editor');
            await page.type('#pipeline-notes-editor', '- Add a citation formatting step after the bibliography generation\n- The web research step should focus on academic sources (Google Scholar, arXiv)', { delay: TYPING_DELAY });
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '#save-pipeline-notes');
            await page.click('#save-pipeline-notes');
            await page.waitForTimeout(SHORT_PAUSE);

            // UI notes
            await say(page, NARRATION['ITERATE_UI_NOTES'], SHORT_PAUSE);
            await highlight(page, '#ui-notes-editor');
            await page.click('#ui-notes-editor');
            await page.type('#ui-notes-editor', '- Add a progress indicator showing which research phase is active\n- Include a word count display for each generated document', { delay: TYPING_DELAY });
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '#save-ui-notes');
            await page.click('#save-ui-notes');
            await page.waitForTimeout(SHORT_PAUSE);
             await diagnosticSnapshot(page, '08-iterate-notes-saved');
        } else {
            await say(page, NARRATION['ITERATE_PIPELINE_NOTES'], SHORT_PAUSE);
            await page.waitForTimeout(SHORT_PAUSE);
            await say(page, NARRATION['ITERATE_UI_NOTES'], SHORT_PAUSE);
            await page.waitForTimeout(SHORT_PAUSE);
            await say(page, NARRATION['ITERATE_SKIPPED'], MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '08-iterate-skipped');
        }

        // =================================================================
        // Step 9 — Git Tab: Version control
        // =================================================================
        await navigateToTab(page, 'section-git', 'GIT_TAB');

        if (ENABLE_GIT) {
            // Initialize repository
            await say(page, NARRATION['GIT_INIT'], SHORT_PAUSE);
            await highlight(page, '#git-init');
            await page.click('#git-init');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Refresh status
            await highlight(page, '#git-refresh-status');
            await page.click('#git-refresh-status');
            await page.waitForTimeout(MEDIUM_PAUSE);

            // Commit
            await say(page, NARRATION['GIT_COMMIT'], SHORT_PAUSE);
            await highlight(page, '#git-commit-message');
            await page.click('#git-commit-message');
            await page.type('#git-commit-message', 'Initial generation: Research Assistant app', { delay: TYPING_DELAY });
            await page.waitForTimeout(SHORT_PAUSE);

            await highlight(page, '#git-commit');
            await page.click('#git-commit');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['GIT_COMMITTED'], MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '09a-git-committed');

            // Show commit log
            await highlight(page, '#git-refresh-log');
            await page.click('#git-refresh-log');
            await page.waitForTimeout(MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '09b-git-log');
        } else {
            await say(page, NARRATION['GIT_SKIPPED'], MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '09-git-skipped');
        }

        // =================================================================
        // Step 10 — Usage Tab: Show token usage
        // =================================================================
        await navigateToTab(page, 'section-usage', 'USAGE_TAB');

        if (ENABLE_USAGE) {
            await highlight(page, '#usage-refresh');
            await page.click('#usage-refresh');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['USAGE_OVERVIEW'], LONG_PAUSE);
             await diagnosticSnapshot(page, '10-usage-overview');
        } else {
            await say(page, NARRATION['USAGE_SKIPPED'], MEDIUM_PAUSE);
             await diagnosticSnapshot(page, '10-usage-skipped');
        }

        // =================================================================
        // Outro
        // =================================================================
        await say(page, NARRATION['OUTRO'], LONG_PAUSE);
         await diagnosticSnapshot(page, '11-outro');
        await page.waitForTimeout(LONG_PAUSE);

        console.log('\n✅ Omega demo complete.');

    } catch (error) {
        console.error('❌ Demo failed with error:', error);
         await diagnosticSnapshot(page, 'error-state').catch(() => {});
        throw error;
    } finally {
        // Stop recording if active
        if (recorder && recorder.isRunning()) {
            await recorder.stop();
        }
        await context.close();
    }
});