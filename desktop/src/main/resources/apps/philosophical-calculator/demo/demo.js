// =============================================================================
// Philosophical Calculator — Automated Demo (Playwright)
// =============================================================================
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

const HUB_URL = 'http://localhost:12891/';
const CREDENTIALS_PATH = path.join(__dirname, 'credentials.json');
const NARRATION_PATH = path.join(__dirname, 'narration.json');
const SPLASH_PATH = path.join(__dirname, 'splash.html');
const TYPING_DELAY = 35;
const SHORT_PAUSE = 1500;
const MEDIUM_PAUSE = 3000;
const LONG_PAUSE = 5000;
const VIDEO_DIR = './demo-videos';
const APP_ID = 'app-philosophical-calculator';
const NARRATION = JSON.parse(fs.readFileSync(NARRATION_PATH, 'utf-8'));
// Tell util.js where narration.json lives so audio paths resolve correctly
setNarrationPath(NARRATION_PATH);

// ---------------------------------------------------------------------------
// Model configuration
// ---------------------------------------------------------------------------
const SMART_MODEL = 'gemini-3-flash-preview';
const FAST_MODEL = 'gemini-3-flash-preview';
const IMAGE_MODEL = 'gemini-3.1-flash-image-preview';
// ---------------------------------------------------------------------------
// Pipeline step toggles — set to false to skip a section
// ---------------------------------------------------------------------------
const ENABLE_SUMMARIZE = false;
const ENABLE_DRAFT = true;
const ENABLE_LENS_DIALECTICAL = false;
const ENABLE_LENS_PERSPECTIVES = true;
const ENABLE_LENS_SOCRATIC = false;
const ENABLE_LENS_BRAINSTORM = false;
const ENABLE_UPDATE_ARTICLE = true;
const ENABLE_ILLUSTRATE = false;

// ---------------------------------------------------------------------------
// Sample notes to paste into the input
// ---------------------------------------------------------------------------
const SAMPLE_NOTES = `# The Paradox of Automation and Human Purpose

## Key observations
- As AI automates more cognitive tasks, humans face an identity crisis — much of our self-worth is tied to productive work
- Historical parallel: the Luddites weren't anti-technology, they were pro-dignity
- Keynes predicted 15-hour work weeks by 2030, but we invented new forms of "busy work" instead
- The Protestant work ethic deeply shapes Western attitudes — idleness is seen as moral failure
- Universal Basic Income experiments (Finland, Stockton CA) show people don't stop working — they pursue meaning differently
- Creative work, caregiving, community building — these are undervalued economically but essential for human flourishing
- The "bullshit jobs" phenomenon (David Graeber): many existing jobs already lack purpose
- Children don't need external motivation to learn and create — curiosity is innate
- Ancient Greek concept of "schole" (leisure) was the basis of scholarship — leisure as the foundation of culture
- Risk: a two-tier society where AI-owners accumulate wealth while displaced workers lose agency
- Opportunity: redefine "contribution" beyond market value — parenting, art, mentorship, civic engagement
- Buddhist economics (E.F. Schumacher): work should serve human development, not just GDP
- The attention economy already commodifies human consciousness — AI could either deepen or liberate this
- Post-scarcity thought experiments: Star Trek's Federation vs. Wall-E's Axiom — same technology, opposite outcomes
- Key question: Can we build institutions that distribute AI's productivity gains equitably?`;

const SAMPLE_INSTRUCTIONS = `Write a 2000-word thought-provoking essay exploring the tension between automation and human purpose.

Tone: Accessible but intellectually rigorous. Blend philosophy, economics, and cultural criticism.

Structure:
1. Opening hook — a vivid scenario or paradox
2. Historical context — how we got here
3. The psychological dimension — why work matters beyond money
4. Two possible futures — utopian vs dystopian
5. A practical path forward — policy and cultural shifts
6. Closing reflection — reframe the question

Audience: Educated general readers, newsletter subscribers, long-form blog readers.`;

// Helper: wait for a badge to show "done"
async function waitForBadgeDone(page, badgeId, label, timeout = 300000) {
    console.log(`⏳ Waiting for ${label} badge (#${badgeId}) to show "done"...`);
    await page.waitForSelector(`#${badgeId}.done, span.step-badge.done#${badgeId}`, {timeout});
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

        // =================================================================
        // Step 2 — Input Notes
        // =================================================================
        await highlight(page, '[data-section="section-input"]');
        await page.click('[data-section="section-input"]');
        await page.waitForTimeout(SHORT_PAUSE);

        // Paste notes into the editor
        await say(page, NARRATION['PASTE_NOTES'], MEDIUM_PAUSE);

        await highlight(page, '#notes-editor');
        const notesEditor = page.locator('#notes-editor');
        await notesEditor.click();
        await notesEditor.fill(SAMPLE_NOTES);
        await page.waitForTimeout(SHORT_PAUSE);

        // Save notes
        await say(page, NARRATION['SAVE_NOTES'], SHORT_PAUSE);
        await highlight(page, '#save-notes');
        await page.click('#save-notes');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Add instructions
        await say(page, NARRATION['ADD_INSTRUCTIONS'], MEDIUM_PAUSE);

        await highlight(page, '#instruct-editor');
        const instructEditor = page.locator('#instruct-editor');
        await instructEditor.click();
        await instructEditor.fill(SAMPLE_INSTRUCTIONS);
        await page.waitForTimeout(SHORT_PAUSE);

        await highlight(page, '#save-instruct');
        await page.click('#save-instruct');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['NOTES_SAVED'], SHORT_PAUSE);

        // =================================================================
        // Step 3 — Configure Models
        // =================================================================
        await highlight(page, '[data-section="section-settings"]');
        await page.click('[data-section="section-settings"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['MODELS_TAB'], MEDIUM_PAUSE);

        // Select smart model
        const smartSelect = page.locator('#smart-model-select');
        await page.waitForTimeout(SHORT_PAUSE);
        try {
            await smartSelect.selectOption({label: new RegExp(SMART_MODEL, 'i')});
        } catch {
            // Try by value if label doesn't match
            const options = await smartSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(SMART_MODEL.toLowerCase()));
            if (match) await smartSelect.selectOption({label: match});
        }
        await highlight(page, '#smart-model-select');
        await page.waitForTimeout(SHORT_PAUSE);

        // Select fast model
        const fastSelect = page.locator('#fast-model-select');
        try {
            await fastSelect.selectOption({label: new RegExp(FAST_MODEL, 'i')});
        } catch {
            const options = await fastSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(FAST_MODEL.toLowerCase()));
            if (match) await fastSelect.selectOption({label: match});
        }
        await highlight(page, '#fast-model-select');
        await page.waitForTimeout(SHORT_PAUSE);

        // Select image model
        const imageSelect = page.locator('#image-model-select');
        try {
            await imageSelect.selectOption({label: new RegExp(IMAGE_MODEL, 'i')});
        } catch {
            const options = await imageSelect.locator('option').allTextContents();
            const match = options.find(o => o.toLowerCase().includes(IMAGE_MODEL.toLowerCase()));
            if (match) await imageSelect.selectOption({label: match});
        }
        await highlight(page, '#image-model-select');
        await page.waitForTimeout(SHORT_PAUSE);

        // Save model settings
        await highlight(page, '#save-model-settings');
        await page.click('#save-model-settings');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['MODELS_SAVED'], SHORT_PAUSE);

        // =================================================================
        // Step 4 — Run Pipeline: Summarize
        // =================================================================
        await highlight(page, '[data-section="section-pipeline"]');
        await page.click('[data-section="section-pipeline"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['PIPELINE_OVERVIEW'], LONG_PAUSE);


        if (ENABLE_SUMMARIZE) {
            // Click the Summarize Run button
            const summarizeBtn = page.locator('.btn-run[data-output="summary.md"]').first();
            await highlight(page, '.btn-run[data-output="summary.md"]');
            await summarizeBtn.click();

            await say(page, NARRATION['SUMMARIZE_STARTED'], SHORT_PAUSE);

            await openSessionLinkAndWaitForCompletion(page, page, 'summarization', 120000);
            await waitForBadgeDone(page, 'badge-summary', 'Summarize', 120000);
            await page.waitForTimeout(MEDIUM_PAUSE);

            // View the summary
            const viewSummaryBtn = page.locator('.btn-view[data-file="summary.md"]').first();
            await highlight(page, '.btn-view[data-file="summary.md"]');
            await viewSummaryBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['SUMMARIZE_DONE'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['SUMMARIZE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 5 — Run Pipeline: Draft Article
        // =================================================================
        if (ENABLE_DRAFT) {
            const draftBtn = page.locator('.btn-run[data-output="content.md"][data-op="ops/draft_article_op.md"]');
            await highlight(page, '.btn-run[data-op="ops/draft_article_op.md"]');
            await draftBtn.click();

            await say(page, NARRATION['DRAFT_STARTED'], SHORT_PAUSE);

            await openSessionLinkAndWaitForCompletion(page, page, 'article drafting', 180000);
            await waitForBadgeDone(page, 'badge-content', 'Draft Article', 180000);
            await page.waitForTimeout(MEDIUM_PAUSE);

            // View the draft
            const viewDraftBtn = page.locator('.btn-view[data-file="content.md"]').first();
            await viewDraftBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['DRAFT_DONE'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['DRAFT_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 6 — Run Multi-Perspective Analysis (Lenses)
        // =================================================================
        const anyLensEnabled = ENABLE_LENS_DIALECTICAL || ENABLE_LENS_PERSPECTIVES || ENABLE_LENS_SOCRATIC || ENABLE_LENS_BRAINSTORM;
        if (anyLensEnabled) {
            await highlight(page, '[data-section="section-lenses"]');
            await page.click('[data-section="section-lenses"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['LENSES_OVERVIEW'], LONG_PAUSE);

            // --- Run Dialectical lens ---
            if (ENABLE_LENS_DIALECTICAL) {
                await say(page, NARRATION['DIALECTICAL_INTRO'], MEDIUM_PAUSE);

                const dialecticalBtn = page.locator('.btn-run[data-output="dialectical.md"]');
                await highlight(page, '.btn-run[data-output="dialectical.md"]');
                await dialecticalBtn.click();

                await openSessionLinkAndWaitForCompletion(page, page, 'dialectical analysis', 120000);
                await waitForBadgeDone(page, 'badge-dialectical', 'Dialectical', 120000);
                await page.waitForTimeout(SHORT_PAUSE);

                // View dialectical result
                const viewDialecticalBtn = page.locator('.btn-view[data-file="dialectical.md"]');
                await viewDialecticalBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);

                await say(page, NARRATION['DIALECTICAL_DONE'], MEDIUM_PAUSE);
                // Show dialectical result in Results tab
                await highlight(page, '[data-section="section-results"]');
                await page.click('[data-section="section-results"]');
                await page.waitForTimeout(SHORT_PAUSE);
                await highlight(page, '[data-tab="tab-dialectical"]');
                await page.click('[data-tab="tab-dialectical"]');
                await page.waitForTimeout(SHORT_PAUSE);
                const refreshDialecticalResult = page.locator('.btn-refresh[data-file="dialectical.md"]');
                await refreshDialecticalResult.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
                await say(page, NARRATION['DIALECTICAL_RESULTS'], MEDIUM_PAUSE);
                // Return to Lenses tab
                await highlight(page, '[data-section="section-lenses"]');
                await page.click('[data-section="section-lenses"]');
                await page.waitForTimeout(SHORT_PAUSE);
            }

            // --- Run Perspectives lens ---
            if (ENABLE_LENS_PERSPECTIVES) {
                const perspectivesBtn = page.locator('.btn-run[data-output="perspectives.md"]');
                await highlight(page, '.btn-run[data-output="perspectives.md"]');
                await perspectivesBtn.click();

                await say(page, NARRATION['PERSPECTIVES_STARTED'], SHORT_PAUSE);

                await openSessionLinkAndWaitForCompletion(page, page, 'perspectives analysis', 120000);
                await waitForBadgeDone(page, 'badge-perspectives', 'Perspectives', 120000);
                await page.waitForTimeout(SHORT_PAUSE);

                const viewPerspectivesBtn = page.locator('.btn-view[data-file="perspectives.md"]');
                await viewPerspectivesBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);

                await say(page, NARRATION['PERSPECTIVES_DONE'], MEDIUM_PAUSE);
                // Show perspectives result in Results tab
                await highlight(page, '[data-section="section-results"]');
                await page.click('[data-section="section-results"]');
                await page.waitForTimeout(SHORT_PAUSE);
                await highlight(page, '[data-tab="tab-perspectives"]');
                await page.click('[data-tab="tab-perspectives"]');
                await page.waitForTimeout(SHORT_PAUSE);
                const refreshPerspectivesResult = page.locator('.btn-refresh[data-file="perspectives.md"]');
                await refreshPerspectivesResult.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
                await say(page, NARRATION['PERSPECTIVES_RESULTS'], MEDIUM_PAUSE);
                // Return to Lenses tab
                await highlight(page, '[data-section="section-lenses"]');
                await page.click('[data-section="section-lenses"]');
                await page.waitForTimeout(SHORT_PAUSE);
            }

            // --- Run Socratic lens ---
            if (ENABLE_LENS_SOCRATIC) {
                const socraticBtn = page.locator('.btn-run[data-output="socratic.md"]');
                await highlight(page, '.btn-run[data-output="socratic.md"]');
                await socraticBtn.click();

                await say(page, NARRATION['SOCRATIC_STARTED'], SHORT_PAUSE);

                await openSessionLinkAndWaitForCompletion(page, page, 'Socratic dialogue', 120000);
                await waitForBadgeDone(page, 'badge-socratic', 'Socratic', 120000);
                await page.waitForTimeout(SHORT_PAUSE);

                const viewSocraticBtn = page.locator('.btn-view[data-file="socratic.md"]');
                await viewSocraticBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);

                await say(page, NARRATION['SOCRATIC_DONE'], LONG_PAUSE);
                // Show Socratic result in Results tab
                await highlight(page, '[data-section="section-results"]');
                await page.click('[data-section="section-results"]');
                await page.waitForTimeout(SHORT_PAUSE);
                await highlight(page, '[data-tab="tab-socratic"]');
                await page.click('[data-tab="tab-socratic"]');
                await page.waitForTimeout(SHORT_PAUSE);
                const refreshSocraticResult = page.locator('.btn-refresh[data-file="socratic.md"]');
                await refreshSocraticResult.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
                await say(page, NARRATION['SOCRATIC_RESULTS'], MEDIUM_PAUSE);
                // Return to Lenses tab
                await highlight(page, '[data-section="section-lenses"]');
                await page.click('[data-section="section-lenses"]');
                await page.waitForTimeout(SHORT_PAUSE);
            }

            // --- Run Brainstorm lens ---
            if (ENABLE_LENS_BRAINSTORM) {
                const brainstormBtn = page.locator('.btn-run[data-output="brainstorm.md"]');
                await highlight(page, '.btn-run[data-output="brainstorm.md"]');
                await brainstormBtn.click();

                await say(page, NARRATION['BRAINSTORM_STARTED'], SHORT_PAUSE);

                await openSessionLinkAndWaitForCompletion(page, page, 'brainstorming', 120000);
                await waitForBadgeDone(page, 'badge-brainstorm', 'Brainstorm', 120000);
                await page.waitForTimeout(SHORT_PAUSE);

                const viewBrainstormBtn = page.locator('.btn-view[data-file="brainstorm.md"]');
                await viewBrainstormBtn.click();
                await page.waitForTimeout(MEDIUM_PAUSE);

                await say(page, NARRATION['BRAINSTORM_DONE'], MEDIUM_PAUSE);
                // Show brainstorm result in Results tab
                await highlight(page, '[data-section="section-results"]');
                await page.click('[data-section="section-results"]');
                await page.waitForTimeout(SHORT_PAUSE);
                await highlight(page, '[data-tab="tab-brainstorm"]');
                await page.click('[data-tab="tab-brainstorm"]');
                await page.waitForTimeout(SHORT_PAUSE);
                const refreshBrainstormResult = page.locator('.btn-refresh[data-file="brainstorm.md"]');
                await refreshBrainstormResult.click();
                await page.waitForTimeout(MEDIUM_PAUSE);
                await say(page, NARRATION['BRAINSTORM_RESULTS'], MEDIUM_PAUSE);
            }
        } else {
            await say(page, NARRATION['LENSES_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 7 — Update Article (Synthesize Lenses)
        // =================================================================
        if (ENABLE_UPDATE_ARTICLE) {
            await highlight(page, '[data-section="section-pipeline"]');
            await page.click('[data-section="section-pipeline"]');
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['UPDATE_INTRO'], LONG_PAUSE);

            // Scroll to the Update Article step
            const updateSection = page.locator('#badge-update').locator('..');
            await updateSection.scrollIntoViewIfNeeded().catch(() => {
            });
            await page.waitForTimeout(SHORT_PAUSE);

            const updateBtn = page.locator('.btn-run[data-op="ops/update_article_op.md"]');
            await highlight(page, '.btn-run[data-op="ops/update_article_op.md"]');
            await updateBtn.click();

            await say(page, NARRATION['UPDATE_STARTED'], SHORT_PAUSE);

            await openSessionLinkAndWaitForCompletion(page, page, 'article update', 180000);
            await waitForBadgeDone(page, 'badge-update', 'Update Article', 180000);
            await page.waitForTimeout(MEDIUM_PAUSE);

            // View the updated article
            const viewUpdateBtn = page.locator('.btn-view[data-file="content.md"]').nth(1);
            await viewUpdateBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);

            await say(page, NARRATION['UPDATE_DONE'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['UPDATE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 8 — Illustrate Article
        // =================================================================
        if (ENABLE_ILLUSTRATE) {
            // Make sure we're on the pipeline tab
            await highlight(page, '[data-section="section-pipeline"]');
            await page.click('[data-section="section-pipeline"]');
            await page.waitForTimeout(SHORT_PAUSE);

            await say(page, NARRATION['ILLUSTRATE_INTRO'], MEDIUM_PAUSE);

            // Scroll to the Illustrate step
            const illustrateSection = page.locator('#badge-illustration').locator('..');
            await illustrateSection.scrollIntoViewIfNeeded().catch(() => {
            });
            await page.waitForTimeout(SHORT_PAUSE);

            const illustrateBtn = page.locator('.btn-run[data-op="ops/illustration_op.md"][data-badge="badge-illustration"]');
            await highlight(page, '.btn-run[data-badge="badge-illustration"]');
            await illustrateBtn.click();

            await say(page, NARRATION['ILLUSTRATE_STARTED'], SHORT_PAUSE);

            await openSessionLinkAndWaitForCompletion(page, page, 'illustration', 300000);
            await waitForBadgeDone(page, 'badge-illustration', 'Illustration', 300000);
            await page.waitForTimeout(MEDIUM_PAUSE);

            // View the illustrated article
            const viewIllustratedBtn = page.locator('.btn-view[data-file="content.md"]').nth(2);
            await viewIllustratedBtn.click();
            await page.waitForTimeout(LONG_PAUSE);

            await say(page, NARRATION['ILLUSTRATE_DONE'], LONG_PAUSE);
        } else {
            await say(page, NARRATION['ILLUSTRATE_SKIPPED'], SHORT_PAUSE);
        }

        // =================================================================
        // Step 9 — Check Usage
        // =================================================================
        await highlight(page, '[data-section="section-usage"]');
        await page.click('[data-section="section-usage"]');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['USAGE_TAB'], MEDIUM_PAUSE);

        await highlight(page, '#refresh-usage');
        await page.click('#refresh-usage');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['USAGE_DETAILS'], LONG_PAUSE);

        // =================================================================
        // Step 10 — Final Article Review (Pipeline Tab)
        // =================================================================
        await say(page, NARRATION['DOWNLOAD_INTRO'], SHORT_PAUSE);

        // Navigate to the Pipeline tab to review the final article
        await highlight(page, '[data-section="section-pipeline"]');
        await page.click('[data-section="section-pipeline"]');
        await page.waitForTimeout(MEDIUM_PAUSE);
        // Open the article viewer in the pipeline (use the last content.md view button)
        const viewContentBtn = page.locator('.btn-view[data-file="content.md"]').first();
        await highlight(page, '.btn-view[data-file="content.md"]');
        await viewContentBtn.click();
        await page.waitForTimeout(MEDIUM_PAUSE);
        // Zoom into the article for a full-screen review
        const zoomBtn = page.locator('.btn-zoom[data-viewer="viewer-content"]').first();
        if (await zoomBtn.isVisible({timeout: 3000}).catch(() => false)) {
            await zoomBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
            // Slowly scroll through the zoomed article
            const zoomBody = page.locator('#zoom-overlay-body');
            const scrollHeight = await zoomBody.evaluate(el => el.scrollHeight);
            const viewportHeight = await zoomBody.evaluate(el => el.clientHeight);
            const scrollSteps = Math.min(5, Math.ceil((scrollHeight - viewportHeight) / 400));
            for (let i = 1; i <= scrollSteps; i++) {
                const scrollTo = Math.min(i * 400, scrollHeight - viewportHeight);
                await zoomBody.evaluate((el, top) => {
                    el.scrollTo({top, behavior: 'smooth'});
                }, scrollTo);
                await page.waitForTimeout(MEDIUM_PAUSE);
            }
            // Close the zoom overlay
            await page.click('#zoom-close-btn');
            await page.waitForTimeout(SHORT_PAUSE);
        }

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