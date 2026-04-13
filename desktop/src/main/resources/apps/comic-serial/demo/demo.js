// =============================================================================
// Automated Demo — Comic Serial Generator (Playwright)
// =============================================================================
// Usage:
//   npx playwright test demo.js
// =============================================================================

const { test } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const {
    startRecording,
    highlight,
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
const APP_ID = 'app-comic-serial';

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
// Mad Libs-style random story idea generator
// ---------------------------------------------------------------------------
function pickRandom(arr) {
     return arr[Math.floor(Math.random() * arr.length)];
}

function generateMadLibIdea() {
     const protagonists = [
         'A lone astronaut', 'A retired detective', 'A teenage street musician',
         'A deep-sea diver', 'An exiled princess', 'A rogue archaeologist',
         'A sentient robot janitor', 'A blind cartographer', 'A time-displaced samurai',
         'A disgraced chef', 'An orphaned witch', 'A cynical librarian',
         'A shape-shifting spy', 'A ghost-hunting veterinarian', 'A rebel clockmaker'
     ];
     const actions = [
         'discovers', 'accidentally activates', 'is drawn into',
         'stumbles upon', 'inherits', 'is cursed by',
         'receives a mysterious map leading to', 'dreams every night about',
         'is the only one who can see', 'is blackmailed into stealing'
     ];
     const objects = [
         'an ancient alien library', 'a sentient crystal that speaks in riddles',
         'a portal hidden inside a painting', 'a clockwork dragon buried under a city',
         'a radio that broadcasts from the future', 'a garden where memories grow as flowers',
         'a mirror that shows alternate lives', 'a book that rewrites itself each midnight',
         'a train that travels between dimensions', 'a music box that controls the weather'
     ];
     const locations = [
         'on Europa', 'beneath the streets of Tokyo', 'in a crumbling space station',
         'deep in the Amazon rainforest', 'inside a volcano on Iceland',
         'at the bottom of the Mariana Trench', 'in a floating city above the clouds',
         'on a desert planet with twin suns', 'in a Victorian London that never was',
         'aboard a ghost ship in the Arctic'
     ];
     const complications = [
         'but using it slowly erases their own memories',
         'but each use ages them by a year',
         'but a shadowy organization is hunting them for it',
         'but it attracts dangerous creatures from another realm',
         'but it only works when they tell the truth',
         'but a rival from their past wants it too',
         'but it is slowly merging their world with another',
         'but every answer it gives demands a painful sacrifice',
         'but time loops reset their progress each week',
         'but the object has a will of its own and a hidden agenda'
     ];
     const stakes = [
         'If they fail, reality itself will unravel.',
         'The fate of an entire civilization hangs in the balance.',
         'They must succeed before the next eclipse — or lose everything.',
         'What they learn will force them to question who they really are.',
         'The truth they uncover could either save the world or destroy it.',
         'They realize they are not the hero of this story — they are the key.',
         'As the pieces come together, they discover the real enemy is time itself.',
         'In the end, they must choose between saving themselves and saving everyone else.'
     ];

     const protagonist = pickRandom(protagonists);
     const action = pickRandom(actions);
     const object = pickRandom(objects);
     const location = pickRandom(locations);
     const complication = pickRandom(complications);
     const stake = pickRandom(stakes);

     return `${protagonist} ${action} ${object} ${location}. ${protagonist.replace(/^A /i, 'The ')} is fascinated — ${complication}. ${stake}`;
}

const DEMO_IDEA = generateMadLibIdea();
console.log(`🎲 Generated Mad Lib idea: ${DEMO_IDEA}`);

// ---------------------------------------------------------------------------
// Model configuration
// ---------------------------------------------------------------------------
const SMART_MODEL = 'gemini-3-flash-preview (Gemini)';
const FAST_MODEL = 'gemini-3-flash-preview (Gemini)';
const IMAGE_MODEL = 'gemini-3.1-flash-image-preview (Gemini)';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Click a navigation tab by its data-section attribute */
async function clickTab(page, sectionId) {
    await page.click(`.nav-link[data-section="${sectionId}"]`);
    await page.waitForTimeout(SHORT_PAUSE);
}

/** Wait for a badge to show "done" */
async function waitForBadgeDone(page, badgeId, label, timeout = 600000) {
    console.log(`⏳ Waiting for ${label} badge (#${badgeId}) to show "done"...`);
    await page.waitForSelector(`#${badgeId}.done`, { timeout });
    console.log(`✅ ${label} badge is done.`);
    await page.waitForTimeout(SHORT_PAUSE);
}

/** Wait for batch log to indicate completion */
async function waitForBatchCompletion(page, timeout = 600000) {
    console.log('⏳ Waiting for batch generation to complete...');
    await page.waitForFunction(
        () => {
            const log = document.getElementById('batch-log');
            if (!log) return false;
            const text = log.textContent || '';
            return text.includes('complete') || text.includes('Complete') || text.includes('Done') || text.includes('done');
        },
        { timeout }
    );
    console.log('✅ Batch generation complete.');
    await page.waitForTimeout(SHORT_PAUSE);
}

/** Scroll an element into view and highlight it */
async function scrollAndHighlight(page, selector) {
    await page.evaluate((sel) => {
        const el = document.querySelector(sel);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, selector);
    await page.waitForTimeout(500);
    await highlight(page, selector);
}

// =============================================================================
// Main demo flow
// =============================================================================
test('Comic Serial Generator Demo', async ({ browser }) => {
    test.setTimeout(1800000); // 30 minutes for AI generation waits

    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
    const videoOutputPath = path.resolve(VIDEO_DIR, `comic_serial_demo_${timestamp}.mp4`);
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

        // Prime Web Speech API voices
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
        // Step 0 — Intro & Open the App Hub
        // =================================================================
        await say(page, NARRATION['INTRO'], SHORT_PAUSE);
        await page.waitForTimeout(MEDIUM_PAUSE);
        await page.goto(HUB_URL, { waitUntil: 'networkidle' });
        await diagnosticSnapshot(page, '00_hub_loaded');

        // Handle Login if needed
        await handleLogin(page, CREDENTIALS_PATH, NARRATION, {
            typingDelay: TYPING_DELAY,
            pauseAfter: SHORT_PAUSE
        });
        await diagnosticSnapshot(page, '00_after_login');


        await say(page, NARRATION['HUB_OVERVIEW'], MEDIUM_PAUSE);

        // =================================================================
        // Step 1 — Launch the Comic Serial Generator
        // =================================================================
        await highlight(page, `#${APP_ID}`);
        await page.click(`#${APP_ID}`);
        await page.waitForTimeout(MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '01_app_opened');


        await say(page, NARRATION['APP_OPENED'], MEDIUM_PAUSE);

        // =================================================================
        // Step 2 — Configure Model Settings
        // =================================================================
        await clickTab(page, 'section-settings');
        await say(page, NARRATION['CONFIGURE_MODELS'], SHORT_PAUSE);

        // Wait for model dropdowns to be populated
        await page.waitForFunction(
            () => {
                const sel = document.getElementById('comic-smart-model');
                return sel && sel.options.length > 1;
            },
            { timeout: 30000 }
        );
        await page.waitForTimeout(SHORT_PAUSE);

        // Select Smart Model
        await scrollAndHighlight(page, '#comic-smart-model');
        await page.selectOption('#comic-smart-model', { label: SMART_MODEL });
        await page.waitForTimeout(500);

        // Select Fast Model
        await scrollAndHighlight(page, '#comic-fast-model');
        await page.selectOption('#comic-fast-model', { label: FAST_MODEL });
        await page.waitForTimeout(500);

        // Select Image Model
        await scrollAndHighlight(page, '#comic-image-model');
        await page.selectOption('#comic-image-model', { label: IMAGE_MODEL });
        await page.waitForTimeout(500);

        // Save model settings
        await scrollAndHighlight(page, '#save-model-settings');
        await page.click('#save-model-settings');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '02_models_configured');


        await say(page, NARRATION['MODELS_SAVED'], SHORT_PAUSE);

        // =================================================================
        // Step 3 — Enter the Story Idea
        // =================================================================
        await say(page, NARRATION['IDEA_TAB'], SHORT_PAUSE);

        // Should already be on the Idea tab (it's the default active tab)
        await clickTab(page, 'section-input');
        await highlight(page, '#idea-editor');
        await page.waitForTimeout(SHORT_PAUSE);

        // Type the story idea
        await page.click('#idea-editor');
        await page.fill('#idea-editor', ''); // Clear any existing content
        await page.type('#idea-editor', DEMO_IDEA, { delay: TYPING_DELAY });
        await page.waitForTimeout(SHORT_PAUSE);
        await diagnosticSnapshot(page, '03_idea_typed');


        await say(page, NARRATION['IDEA_TYPED'], SHORT_PAUSE);

        // Save the idea
        await highlight(page, '#save-idea');
        await page.click('#save-idea');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '03_idea_saved');


        await say(page, NARRATION['IDEA_SAVED'], SHORT_PAUSE);

        // =================================================================
        // Step 4 — Pipeline: Generate First Comic
        // =================================================================
        await clickTab(page, 'section-pipeline');
        await say(page, NARRATION['PIPELINE_TAB'], MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '04_pipeline_tab');


        // Scroll to and highlight the pipeline diagram
        await scrollAndHighlight(page, '#pipeline-diagram');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Generate Comic #1
        await say(page, NARRATION['GENERATE_COMIC_1'], SHORT_PAUSE);
        const generateComic1Btn = page.locator('.btn-run[data-output="comic_1.md"]');
        await scrollAndHighlight(page, '.btn-run[data-output="comic_1.md"]');
        await generateComic1Btn.click();
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Open the session link in a new tab to watch progress
        try {
            const sessionLink = page.locator('a[href*="session"]').first();
            if (await sessionLink.isVisible({ timeout: 5000 })) {
                const href = await sessionLink.getAttribute('href');
                const sessionPage = await context.newPage();
                await sessionPage.goto(href, { waitUntil: 'networkidle' });
                await sessionPage.waitForTimeout(MEDIUM_PAUSE);

                // Wait for the badge on the original page to turn done
                await waitForBadgeDone(page, 'badge-comic-1', 'Comic #1');

                // Close the session tab
                await sessionPage.waitForTimeout(SHORT_PAUSE);
                await sessionPage.close();
            } else {
                await waitForBadgeDone(page, 'badge-comic-1', 'Comic #1');
            }
        } catch (e) {
            console.log('No session link found, waiting for badge directly...');
            await waitForBadgeDone(page, 'badge-comic-1', 'Comic #1');
        }
        await diagnosticSnapshot(page, '04_comic_1_done');


        await say(page, NARRATION['COMIC_1_DONE'], SHORT_PAUSE);

        // View Comic #1
        const viewComic1Btn = page.locator('.btn-view[data-file="comic_1.md"]');
        await scrollAndHighlight(page, '.btn-view[data-file="comic_1.md"]');
        await viewComic1Btn.click();
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['VIEW_COMIC_1'], LONG_PAUSE);
        await diagnosticSnapshot(page, '04_comic_1_viewed');


        // Scroll through the viewer to show content
        await page.evaluate(() => {
            const viewer = document.getElementById('viewer-comic-1');
            if (viewer) viewer.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
        await page.waitForTimeout(MEDIUM_PAUSE);

        // =================================================================
        // Step 5 — Pipeline: Compile HTML Book
        // =================================================================
        await say(page, NARRATION['HTMLBOOK_INTRO'], SHORT_PAUSE);

        await scrollAndHighlight(page, '#generate-htmlbook');
        await page.click('#generate-htmlbook');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // Wait for HTML book badge
        try {
            const sessionLink = page.locator('a[href*="session"]').first();
            if (await sessionLink.isVisible({ timeout: 5000 })) {
                const href = await sessionLink.getAttribute('href');
                const sessionPage = await context.newPage();
                await sessionPage.goto(href, { waitUntil: 'networkidle' });
                await sessionPage.waitForTimeout(MEDIUM_PAUSE);

                // Wait for the badge on the original page to turn done
                await waitForBadgeDone(page, 'badge-htmlbook', 'HTML Book');

                // Close the session tab
                await sessionPage.waitForTimeout(SHORT_PAUSE);
                await sessionPage.close();
            } else {
                await waitForBadgeDone(page, 'badge-htmlbook', 'HTML Book');
            }
        } catch (e) {
            console.log('No session link found for HTML book, waiting for badge...');
            await waitForBadgeDone(page, 'badge-htmlbook', 'HTML Book');
        }
        await diagnosticSnapshot(page, '05_htmlbook_done');


        await say(page, NARRATION['HTMLBOOK_DONE'], MEDIUM_PAUSE);
        // Open the HTML book in a new tab and scroll through it
        await scrollAndHighlight(page, '#open-htmlbook-tab');
        // Start listening for a new page event BEFORE clicking
        const newPagePromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
        await page.click('#open-htmlbook-tab');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // The click may or may not open a new tab — handle both cases
        let bookPage = await newPagePromise;
        let bookPageOpenedByUs = false;

        if (!bookPage) {
            // No new tab was opened (popup blocked or same-window navigation).
            // Open the comicbook URL manually in a new page.
            console.log('ℹ️  No new tab detected — opening comicbook URL directly.');
            const comicbookUrl = await page.evaluate(() => {
                // Try to find the iframe src for the comicbook
                const iframe = document.querySelector('#viewer-htmlbook iframe.comic-iframe');
                if (iframe && iframe.src) {
                    // Strip any cache-busting query param and return the base URL
                    return iframe.src.split('?')[0];
                }
                // Fallback: construct from the current page URL
                const base = window.location.href.replace(/\/[^/]*$/, '/');
                return base + 'comicbook.html';
            });
            bookPage = await context.newPage();
            bookPageOpenedByUs = true;
            await bookPage.goto(comicbookUrl, { waitUntil: 'networkidle' });
        }

        await bookPage.waitForLoadState('networkidle');
        await bookPage.waitForTimeout(MEDIUM_PAUSE);
        // Scroll through the HTML book slowly
        await say(page, NARRATION['HTMLBOOK_SCROLL'] || 'Let\'s scroll through the finished comic book.', SHORT_PAUSE);
        await bookPage.evaluate(async () => {
            const scrollStep = 400;
            const delay = 800;
            const maxScroll = document.body.scrollHeight;
            for (let y = 0; y < maxScroll; y += scrollStep) {
                window.scrollTo({ top: y, behavior: 'smooth' });
                await new Promise(r => setTimeout(r, delay));
            }
        });
        await bookPage.waitForTimeout(LONG_PAUSE);
        await diagnosticSnapshot(bookPage, '05_htmlbook_scrolled');
        // Close the book tab and return to the main page
        await bookPage.close();
        await page.waitForTimeout(SHORT_PAUSE);


        // =================================================================
        // Step 6 — Series Tab
        // =================================================================
        await clickTab(page, 'section-series');
        await say(page, NARRATION['SERIES_TAB'], SHORT_PAUSE);

        // Refresh the series list
        await page.click('#refresh-series');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '06_series_tab');


        await say(page, NARRATION['SERIES_OVERVIEW'], MEDIUM_PAUSE);

        // Expand the first episode card if there are accordion items
        const firstEpisodeCard = page.locator('#series-container .card').first();
        if (await firstEpisodeCard.isVisible({ timeout: 3000 })) {
            await firstEpisodeCard.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        // Scroll through the series container
        await page.evaluate(() => {
            const container = document.getElementById('series-container');
            if (container) container.scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
        await page.waitForTimeout(MEDIUM_PAUSE);

        // =================================================================
        // Step 7 — Usage Tab
        // =================================================================
        await clickTab(page, 'section-usage');
        await say(page, NARRATION['USAGE_TAB'], SHORT_PAUSE);

        await page.click('#refresh-usage');
        await page.waitForTimeout(MEDIUM_PAUSE);
        await diagnosticSnapshot(page, '07_usage_tab');


        // Highlight usage summary
        await scrollAndHighlight(page, '.usage-summary');
        await page.waitForTimeout(MEDIUM_PAUSE);

        // =================================================================
        // Step 8 — Settings Tab (review)
        // =================================================================
        await clickTab(page, 'section-settings');
        await say(page, NARRATION['SETTINGS_TAB'], SHORT_PAUSE);
        await diagnosticSnapshot(page, '08_settings_review');


        await scrollAndHighlight(page, '.model-settings-grid');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await say(page, NARRATION['SETTINGS_OVERVIEW'], MEDIUM_PAUSE);

        // =================================================================
        // Outro
        // =================================================================
        await say(page, NARRATION['OUTRO'], LONG_PAUSE);
        await diagnosticSnapshot(page, '09_outro');
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