// =============================================================================
// Comic Serial Generator — Automated Demo (Playwright)
// =============================================================================
// This script launches the app hub, opens the Comic Serial app, and walks
// through the full workflow: writing an idea, generating comics, browsing
// the series, checking usage, and configuring models.
//
// Usage:
//   npx playwright test demo.js
//   — or —
//   node demo.js  (if you wire up the launcher below)
// =============================================================================

const { test } = require('@playwright/test');
const fs = require('fs');
const path = require('path');
const {
     startRecording,
     narrate,
     highlight,
     openSessionLinkAndWaitForCompletion,
} = require('./util');


// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const HUB_URL = 'http://localhost:12891/';
const CREDENTIALS_PATH = path.join(__dirname, 'credentials.json');
const SLOW_MO = 80;           // ms between actions — makes the demo watchable
const TYPING_DELAY = 35;      // ms per keystroke for a natural typing feel
const SHORT_PAUSE = 1500;     // pause so the viewer can read
const MEDIUM_PAUSE = 3000;
const LONG_PAUSE = 5000;
const VERY_LONG_PAUSE = 15000; // waiting for AI generation
const VIDEO_DIR = './demo-videos'; // directory where recorded videos are saved


// ---------------------------------------------------------------------------
// Demo story idea
// ---------------------------------------------------------------------------
const DEMO_IDEA = `A lone astronaut named Kira discovers an ancient alien library buried beneath the ice of Europa. Each crystalline book she opens projects a holographic chapter of Earth's forgotten history — civilizations that rose and fell before humanity's recorded past.

But there's a cost: every chapter she reads slowly overwrites one of her own memories. She begins forgetting her childhood, her training, even the faces of the crew waiting in orbit above.

As she pushes deeper into the library, she realizes the books are not just records — they are warnings. Something is coming back to Earth, and the library was left here so that someone, someday, would know how to stop it.

The question is: will Kira remember enough of herself to deliver the warning?`;
// ---------------------------------------------------------------------------
// Model configuration
// ---------------------------------------------------------------------------
const SMART_MODEL = 'gemini-3-flash-preview';
const FAST_MODEL = 'gemini-3-flash-preview';
const IMAGE_MODEL = 'gemini-3.1-flash-image-preview';

// =============================================================================
// Main demo flow
// =============================================================================
test('Comic Serial Generator — Automated Demo', async ({ browser }) => {
     // Increase the test timeout to 30 minutes for long AI generation waits
     test.setTimeout(1800000);
      // Generate a timestamped filename for the recording
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').replace('T', '_').slice(0, 19);
      const videoOutputPath = path.resolve(VIDEO_DIR, `comic-serial-demo_${timestamp}.mp4`);


     const context = await browser.newContext({
          viewport: null,  // use the full browser window (works with --start-maximized)
     });

     const page = await context.newPage();
      // Start ffmpeg screen + audio recording
      let recorder = null;

     try {
         recorder = startRecording(videoOutputPath);
         // Give ffmpeg a moment to start capturing before we begin
         await page.waitForTimeout(2000);
         // Verify ffmpeg is running
         if (!recorder.isRunning()) {
             console.warn('⚠️  ffmpeg failed to start. Demo will continue without video recording.');
             console.warn('   Install ffmpeg and ensure system audio loopback is available.');
             recorder = null;
         }

        // Prime the Web Speech API so voices are loaded before first narration
        await page.goto('about:blank');
        await page.evaluate(() => {
            return new Promise((resolve) => {
                if (!window.speechSynthesis) { resolve(); return; }
                const voices = window.speechSynthesis.getVoices();
                if (voices.length > 0) { resolve(); return; }
                window.speechSynthesis.addEventListener('voiceschanged', () => resolve(), { once: true });
                // Safety timeout in case voiceschanged never fires
                setTimeout(resolve, 3000);
            });
        });

        // -----------------------------------------------------------------
        // Step 0 — Open the App Hub
        // -----------------------------------------------------------------
        await narrate(page,
            'Welcome to the Comic Serial Generator demo! Let\'s start by opening the App Hub.',
            SHORT_PAUSE);

        await page.goto(HUB_URL, { waitUntil: 'networkidle' });
        // -----------------------------------------------------------------
        // Step 0.5 — Handle Login Page
        // -----------------------------------------------------------------
        const loginForm = page.locator('.login-container');
        if (await loginForm.isVisible({ timeout: 5000 }).catch(() => false)) {
            await narrate(page,
                'We\'ve been presented with a login page. Let\'s enter our credentials to proceed.',
                SHORT_PAUSE);
            // Load credentials from external JSON file
            let credentials;
            try {
                const credentialsRaw = fs.readFileSync(CREDENTIALS_PATH, 'utf-8');
                credentials = JSON.parse(credentialsRaw);
            } catch (e) {
                throw new Error(
                    `Failed to load credentials from ${CREDENTIALS_PATH}. ` +
                    `Please create this file with {"username": "...", "password": "..."}\n` +
                    e.message
                );
            }
            // Fill in username
            await highlight(page, '#username');
            const usernameInput = page.locator('#username');
            await usernameInput.click();
            await usernameInput.type(credentials.username, { delay: TYPING_DELAY });
            // Fill in password
            await highlight(page, '#password');
            const passwordInput = page.locator('#password');
            await passwordInput.click();
            await passwordInput.type(credentials.password, { delay: TYPING_DELAY });
            await narrate(page,
                'Credentials entered. Let\'s log in!',
                SHORT_PAUSE);
            // Click the login button
            await highlight(page, 'button[type="submit"]');
            await page.click('button[type="submit"]');
            // Wait for navigation after login
            await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
            await page.waitForTimeout(SHORT_PAUSE);
            // Check if login was successful (no longer on login page)
            const stillOnLogin = await page.locator('.login-container').isVisible().catch(() => false);
            if (stillOnLogin) {
                const errorEl = page.locator('.error');
                if (await errorEl.isVisible().catch(() => false)) {
                    const errorText = await errorEl.textContent();
                    throw new Error(`Login failed: ${errorText}`);
                }
                throw new Error('Login failed: still on login page after submission.');
            }
            await narrate(page,
                'Successfully logged in! Now we can see the App Hub.',
                SHORT_PAUSE);
        }

        await narrate(page,
            'This is the Cognotik App Hub. We can see several apps available. Let\'s launch the Comic Serial Generator.',
            MEDIUM_PAUSE);

        // -----------------------------------------------------------------
        // Step 1 — Launch the Comic Serial app
        // -----------------------------------------------------------------
        await highlight(page, '#app-comic-serial');
        await page.click('#app-comic-serial');

        // Wait for the Comic Serial app to load
        await page.waitForSelector('.app-header', { timeout: 30000 });
        await narrate(page,
            'The Comic Serial Generator is now open. It has five tabs: Idea, Pipeline, Series, Usage, and Models.',
            MEDIUM_PAUSE);

        // -----------------------------------------------------------------
         // Step 2 — Configure Models first
         // -----------------------------------------------------------------
         await narrate(page,
             'Before we start creating comics, let\'s configure the AI models. We\'ll go to the Models tab.',
             SHORT_PAUSE);

         await highlight(page, '[data-section="section-settings"]');
         await page.click('[data-section="section-settings"]');
         await page.waitForSelector('#section-settings.active', { timeout: 5000 });

         await narrate(page,
             'The Models tab lets us choose three AI models: Smart, Fast, and Image. Let\'s set them up for our demo.',
             MEDIUM_PAUSE);

         // Wait for model dropdowns to be populated
         await page.waitForFunction(() => {
             const sel = document.querySelector('#comic-smart-model');
             return sel && sel.options.length > 1;
         }, { timeout: 15000 }).catch(() => {
             console.log('  (Model dropdowns may not have loaded yet)');
         });

         // Select Smart Model
         await highlight(page, '#comic-smart-model');
         const smartSelect = page.locator('#comic-smart-model');
         try {
             await smartSelect.selectOption({ value: SMART_MODEL });
         } catch {
             // If exact value not found, try to find a matching option
             await page.evaluate((model) => {
                 const sel = document.querySelector('#comic-smart-model');
                 if (!sel) return;
                 for (let i = 0; i < sel.options.length; i++) {
                     if (sel.options[i].value.includes(model) || sel.options[i].text.includes(model)) {
                         sel.selectedIndex = i;
                         sel.dispatchEvent(new Event('change'));
                         break;
                     }
                 }
             }, SMART_MODEL);
         }
         await narrate(page,
             `Smart Model set to ${SMART_MODEL} — this handles creative writing and story generation.`,
             SHORT_PAUSE);

         // Select Fast Model
         await highlight(page, '#comic-fast-model');
         const fastSelect = page.locator('#comic-fast-model');
         try {
             await fastSelect.selectOption({ value: FAST_MODEL });
         } catch {
             await page.evaluate((model) => {
                 const sel = document.querySelector('#comic-fast-model');
                 if (!sel) return;
                 for (let i = 0; i < sel.options.length; i++) {
                     if (sel.options[i].value.includes(model) || sel.options[i].text.includes(model)) {
                         sel.selectedIndex = i;
                         sel.dispatchEvent(new Event('change'));
                         break;
                     }
                 }
             }, FAST_MODEL);
         }
         await narrate(page,
             `Fast Model set to ${FAST_MODEL} — this handles parsing and formatting tasks.`,
             SHORT_PAUSE);

         // Select Image Model
         await highlight(page, '#comic-image-model');
         const imageSelect = page.locator('#comic-image-model');
         try {
             await imageSelect.selectOption({ value: IMAGE_MODEL });
         } catch {
             await page.evaluate((model) => {
                 const sel = document.querySelector('#comic-image-model');
                 if (!sel) return;
                 for (let i = 0; i < sel.options.length; i++) {
                     if (sel.options[i].value.includes(model) || sel.options[i].text.includes(model)) {
                         sel.selectedIndex = i;
                         sel.dispatchEvent(new Event('change'));
                         break;
                     }
                 }
             }, IMAGE_MODEL);
         }
         await narrate(page,
             `Image Model set to ${IMAGE_MODEL} — this generates the comic panel artwork.`,
             SHORT_PAUSE);

         // Save model settings
         await highlight(page, '#save-model-settings');
         await page.click('#save-model-settings');
         await page.waitForTimeout(1000);

         await narrate(page,
             'Model settings saved! Now let\'s check the configuration summary below.',
             SHORT_PAUSE);

         // Scroll to show model summary
         await page.evaluate(() => {
             const el = document.querySelector('#model-summary');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
         });
         await page.waitForTimeout(MEDIUM_PAUSE);

         await narrate(page,
             'All three models are configured and active. Now let\'s write our story idea!',
             SHORT_PAUSE);

         // -----------------------------------------------------------------
         // Step 3 — Write the story idea
        // -----------------------------------------------------------------
         await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
         await page.waitForTimeout(800);
         await highlight(page, '[data-section="section-input"]');
         await page.click('[data-section="section-input"]');
         await page.waitForSelector('#section-input.active', { timeout: 5000 });

        await narrate(page,
            'We start on the Idea tab. This is where we enter the story concept that will drive our entire comic series.',
            SHORT_PAUSE);

        // Clear any existing content and type our idea
        const ideaEditor = page.locator('#idea-editor');
        await highlight(page, '#idea-editor');
        await ideaEditor.click();
        await ideaEditor.fill('');
        await ideaEditor.type(DEMO_IDEA, { delay: TYPING_DELAY });

        await narrate(page,
            'We\'ve written a sci-fi concept about an astronaut who discovers an alien library on Europa. Each book she reads erases one of her own memories. Great premise for a serial!',
            MEDIUM_PAUSE);

        // Save the idea
        await highlight(page, '#save-idea');
        await page.click('#save-idea');
        await narrate(page,
            'Idea saved. Now let\'s move to the Pipeline tab to start generating comics.',
            SHORT_PAUSE);

        // -----------------------------------------------------------------
         // Step 4 — Navigate to Pipeline
        // -----------------------------------------------------------------
        await highlight(page, '[data-section="section-pipeline"]');
        await page.click('[data-section="section-pipeline"]');
        await page.waitForSelector('#section-pipeline.active', { timeout: 5000 });

        await narrate(page,
            'The Pipeline tab shows the generation workflow. At the top is a visual diagram: Idea → Comic #1 → Comic #2 → and so on → HTML Book.',
            MEDIUM_PAUSE);

        // Scroll to show the pipeline diagram
        await page.evaluate(() => {
             var el = document.querySelector('#pipeline-diagram');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(SHORT_PAUSE);

        // -----------------------------------------------------------------
         // Step 5 — Generate the first comic
        // -----------------------------------------------------------------
        await narrate(page,
            'Step 1 is to generate the first comic episode. This establishes characters, setting, art style, and the narrative arc. Let\'s kick it off!',
            SHORT_PAUSE);

        await page.evaluate(() => {
             var el = document.querySelector('.btn-run[data-output="comic_1.md"]');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(800);

        await highlight(page, '.btn-run[data-output="comic_1.md"]');
        await page.click('.btn-run[data-output="comic_1.md"]');

        await narrate(page,
            'The AI is now generating Comic #1. This involves writing the script, creating panel descriptions, and generating artwork. This may take a minute or two...',
            SHORT_PAUSE);

         // Open the session monitor link in a new tab and wait for completion
         await openSessionLinkAndWaitForCompletion(page, page, 'Comic #1', 180000);

         // Check if generation completed
         const comic1Done = await page.evaluate(() => {
             const badge = document.querySelector('#badge-comic-1');
             return badge && (badge.textContent.trim() === 'done' || badge.classList.contains('done'));
         });

         if (comic1Done) {
             await narrate(page,
                 'Comic #1 has been generated! Let\'s take a look at it.',
                 SHORT_PAUSE);
         } else {
             // Wait a bit more for the badge to update
             try {
                 await page.waitForFunction(() => {
                     const badge = document.querySelector('#badge-comic-1');
                     return badge && (badge.textContent.trim() === 'done' || badge.classList.contains('done'));
                 }, { timeout: 30000 });
                 await narrate(page,
                     'Comic #1 has been generated! Let\'s take a look at it.',
                     SHORT_PAUSE);
             } catch {
                 await narrate(page,
                     'The generation is still running. In a live demo this would complete in 1-2 minutes. Let\'s continue exploring the interface while we wait.',
                     SHORT_PAUSE);
             }
         }

        // Try to view the first comic
        const viewBtn1 = page.locator('.btn-view[data-file="comic_1.md"]');
        if (await viewBtn1.isVisible()) {
            await highlight(page, '.btn-view[data-file="comic_1.md"]');
            await viewBtn1.click();
            await page.waitForTimeout(MEDIUM_PAUSE);

            await narrate(page,
                'Here\'s the rendered first episode. You can see the comic panels with artwork and captions, establishing Kira\'s discovery of the alien library.',
                LONG_PAUSE);

            // Scroll through the viewer content
            await page.evaluate(() => {
                const viewer = document.querySelector('#viewer-comic-1');
                if (viewer && viewer.scrollHeight > viewer.clientHeight) {
                    viewer.scrollTo({ top: viewer.scrollHeight / 2, behavior: 'smooth' });
                }
            });
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        // -----------------------------------------------------------------
         // Step 6 — Generate a sequel episode
        // -----------------------------------------------------------------
        await narrate(page,
            'Now let\'s generate a sequel. Each new episode builds on the previous one while staying true to the original idea.',
            SHORT_PAUSE);

        await page.evaluate(() => {
             var el = document.querySelector('#generate-sequel');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(800);

        // Refresh the episode count first
        const refreshCountBtn = page.locator('#refresh-count');
        if (await refreshCountBtn.isVisible()) {
            await refreshCountBtn.click();
            await page.waitForTimeout(SHORT_PAUSE);
        }

        await narrate(page,
            'The episode counter shows how many episodes exist and what the next one will be numbered. Let\'s generate the next episode!',
            SHORT_PAUSE);

        await highlight(page, '#generate-sequel');
        await page.click('#generate-sequel');

        await narrate(page,
            'Sequel generation is underway. The AI reads the previous episode and the original idea to maintain continuity.',
            SHORT_PAUSE);

         // Open the session monitor link in a new tab and wait for completion
         await openSessionLinkAndWaitForCompletion(page, page, 'the sequel episode', 180000);

         // Check if sequel completed
         const sequelDone = await page.evaluate(() => {
             const badge = document.querySelector('#badge-sequel');
             return badge && (badge.textContent.trim() === 'done' || badge.classList.contains('done'));
         });

         if (sequelDone) {
             await narrate(page,
                 'The sequel has been generated successfully!',
                 SHORT_PAUSE);
         } else {
             try {
                 await page.waitForFunction(() => {
                     const badge = document.querySelector('#badge-sequel');
                     return badge && (badge.textContent.trim() === 'done' || badge.classList.contains('done'));
                 }, { timeout: 30000 });
                 await narrate(page,
                     'The sequel has been generated successfully!',
                     SHORT_PAUSE);
             } catch {
                 await narrate(page,
                     'Sequel generation is still in progress. Let\'s continue the tour.',
                     SHORT_PAUSE);
             }
        }

        // -----------------------------------------------------------------
         // Step 7 — Show batch generation
        // -----------------------------------------------------------------
        await narrate(page,
            'For creating a longer series, we can use Batch Generation. This lets us generate multiple episodes in one go.',
            SHORT_PAUSE);

        await page.evaluate(() => {
             var el = document.querySelector('#batch-count');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(800);

        await highlight(page, '#batch-count');
        const batchInput = page.locator('#batch-count');
        await batchInput.fill('3');

        await narrate(page,
            'We\'ve set the batch count to 3. Clicking "Generate Series" would create 3 more episodes sequentially. We\'ll skip running it for now to save time.',
            MEDIUM_PAUSE);

        await highlight(page, '#run-batch');
        // Not clicking — just showing it
        await page.waitForTimeout(SHORT_PAUSE);

        // -----------------------------------------------------------------
         // Step 8 — Show HTML Book compilation
        // -----------------------------------------------------------------
        await narrate(page,
            'Once we have several episodes, we can compile them into a single HTML comicbook. Let\'s scroll down to that option.',
            SHORT_PAUSE);

        await page.evaluate(() => {
             var el = document.querySelector('#generate-htmlbook');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(800);

        await highlight(page, '#generate-htmlbook');
        await narrate(page,
            'The "Generate HTML Book" button compiles all episodes into a polished, self-contained HTML page with inline images and styled panels. You can also open it in a new tab for full-screen reading.',
            MEDIUM_PAUSE);

        // -----------------------------------------------------------------
         // Step 9 — Navigate to Series tab
        // -----------------------------------------------------------------
        await narrate(page,
            'Let\'s check out the Series tab to browse our generated episodes.',
            SHORT_PAUSE);

        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
        await page.waitForTimeout(800);

        await highlight(page, '[data-section="section-series"]');
        await page.click('[data-section="section-series"]');
        await page.waitForSelector('#section-series.active', { timeout: 5000 });

        // Refresh the series list
        const refreshSeriesBtn = page.locator('#refresh-series');
        if (await refreshSeriesBtn.isVisible()) {
            await refreshSeriesBtn.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
        }

        await narrate(page,
            'The Series tab shows all generated episodes in an expandable accordion view. Each episode can be expanded to read its full content. You can also generate new episodes or compile the book directly from here.',
            LONG_PAUSE);

        // Try to expand the first episode if it exists
        const firstEpisode = page.locator('.series-container .card').first();
        if (await firstEpisode.isVisible()) {
            await firstEpisode.click();
            await page.waitForTimeout(MEDIUM_PAUSE);
            await narrate(page,
                'Here\'s the first episode expanded. We can read through the comic panels and see how the story unfolds.',
                MEDIUM_PAUSE);
        }

        // -----------------------------------------------------------------
         // Step 10 — Navigate to Usage tab
        // -----------------------------------------------------------------
        await narrate(page,
            'Now let\'s check the Usage tab to see how much the generation cost.',
            SHORT_PAUSE);

        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
        await page.waitForTimeout(800);

        await highlight(page, '[data-section="section-usage"]');
        await page.click('[data-section="section-usage"]');
        await page.waitForSelector('#section-usage.active', { timeout: 5000 });

        // Refresh usage data
        await highlight(page, '#refresh-usage');
        await page.click('#refresh-usage');
        await page.waitForTimeout(MEDIUM_PAUSE);

        await narrate(page,
            'The Usage tab tracks token consumption and estimated costs. We can see summary cards for prompt tokens, completion tokens, total tokens, and estimated cost.',
            MEDIUM_PAUSE);

        // Scroll to show the usage summary
        await page.evaluate(() => {
             var el = document.querySelector('#usage-summary');
             if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        await page.waitForTimeout(MEDIUM_PAUSE);

        await narrate(page,
            'Below the summary, there\'s a breakdown by model showing exactly which AI models were used and how many tokens each consumed.',
            SHORT_PAUSE);

        // Show JSON view toggle
        await highlight(page, '#toggle-usage-format');
        await page.click('#toggle-usage-format');
        await page.waitForTimeout(SHORT_PAUSE);

        await narrate(page,
            'We can also toggle to a raw JSON view for detailed inspection of the usage data.',
            SHORT_PAUSE);

        // Toggle back
        await page.click('#toggle-usage-format');
        await page.waitForTimeout(SHORT_PAUSE);

        // -----------------------------------------------------------------
         // Step 11 — Navigate to Models/Settings tab (revisit)
        // -----------------------------------------------------------------
        await narrate(page,
             'Finally, let\'s revisit the Models tab to confirm our configuration.',
            SHORT_PAUSE);

        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
        await page.waitForTimeout(800);

        await highlight(page, '[data-section="section-settings"]');
        await page.click('[data-section="section-settings"]');
        await page.waitForSelector('#section-settings.active', { timeout: 5000 });

        await narrate(page,
             'The Models tab shows our configured models. We set these up at the beginning: Smart and Fast models use ' + SMART_MODEL + ', and the Image model uses ' + IMAGE_MODEL + '.',
            MEDIUM_PAUSE);

        // Highlight each model selector
        await highlight(page, '#comic-smart-model');
        await narrate(page,
            'The Smart Model handles the heavy creative lifting — writing dialogue, developing plot, and maintaining character consistency across episodes.',
            SHORT_PAUSE);

        await highlight(page, '#comic-fast-model');
        await narrate(page,
            'The Fast Model is used for lighter tasks like parsing markdown and formatting output.',
            SHORT_PAUSE);

        await highlight(page, '#comic-image-model');
        await narrate(page,
            'The Image Model generates the actual comic panel artwork. Different models produce different art styles.',
            SHORT_PAUSE);

        await narrate(page,
            'You can save your model preferences or reset to defaults at any time.',
            SHORT_PAUSE);

        await highlight(page, '#save-model-settings');
        await page.waitForTimeout(SHORT_PAUSE);

        // -----------------------------------------------------------------
         // Step 12 — Return to Idea tab for wrap-up
        // -----------------------------------------------------------------
        await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
        await page.waitForTimeout(800);

        await highlight(page, '[data-section="section-input"]');
        await page.click('[data-section="section-input"]');
        await page.waitForSelector('#section-input.active', { timeout: 5000 });

        await narrate(page,
            'And that\'s the Comic Serial Generator! To recap what we covered:',
            SHORT_PAUSE);

        await narrate(page,
            'One — Write your story idea in the Idea tab.',
            SHORT_PAUSE);

        await narrate(page,
            'Two — Use the Pipeline tab to generate individual episodes, sequels, or batch-generate an entire series.',
            SHORT_PAUSE);

        await narrate(page,
            'Three — Browse your episodes in the Series tab and compile them into an HTML comicbook.',
            SHORT_PAUSE);

        await narrate(page,
            'Four — Monitor costs in the Usage tab and configure AI models in the Models tab.',
            SHORT_PAUSE);

        await narrate(page,
            'Thanks for watching! The Comic Serial Generator makes it easy to turn any idea into a rich, ongoing comic series — one episode at a time. Happy creating!',
            LONG_PAUSE);

    } catch (error) {
        console.error('\n❌ Demo encountered an error:', error.message);
        console.error(error.stack);
    } finally {
        // Keep the browser open for a moment so the viewer can see the final state


         try {
             await page.waitForTimeout(LONG_PAUSE);
         } catch {
             // Page may already be closed if the test timed out — ignore
         }

          // Stop the ffmpeg recording before closing the browser
          if (recorder) {
              try {
                  await recorder.stop();
              } catch (e) {
                  console.error('⚠️  Error stopping recorder:', e.message);
              }
          }

         // Close the context to finalize the video recording
         try {
             await context.close();
         } catch {
             // Context may already be closed
         }

          if (recorder && fs.existsSync(videoOutputPath)) {
              const stats = fs.statSync(videoOutputPath);
              const sizeMB = (stats.size / (1024 * 1024)).toFixed(1);
              console.log(`\n🎬 Demo video with audio saved to: ${videoOutputPath} (${sizeMB} MB)`);
          } else if (recorder) {
              console.log(`\n⚠️  Video file was not created. Check ffmpeg output above for errors.`);
         }

         console.log('\n✅ Demo complete.');
    }
});