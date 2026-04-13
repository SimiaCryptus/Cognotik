# Automated Demo Framework

This directory contains a Playwright-based automated demo framework for recording narrated, scripted walkthroughs of Cognotik web applications. It is designed to be copied and adapted for new app demos with minimal changes.

---

## Table of Contents

- [Quick Start](#quick-start)
- [Directory Structure](#directory-structure)
- [File Reference](#file-reference)
  - [demo.js — Main Demo Script](#demojs--main-demo-script)
  - [narration.json — Narration Text & Audio](#narrationjson--narration-text--audio)
  - [credentials.json — Login Credentials](#credentialsjson--login-credentials)
  - [splash.html — Splash Screen](#splashhtml--splash-screen)
  - [util.js — Utility Functions](#utiljs--utility-functions)
- [Configuration Reference](#configuration-reference)
  - [Constants](#constants)
  - [Pipeline Step Toggles](#pipeline-step-toggles)
- [Utility Functions](#utility-functions)
  - [startRecording(outputPath)](#startrecordingoutputpath)
  - [highlight(page, selector)](#highlightpage-selector)
  - [say(page, narrationEntry, pauseAfter)](#saypage-narrationentry-pauseafter)
  - [openSessionLinkAndWaitForCompletion(page, context, label, timeout)](#opensessionlinkandwaitforcompletionpage-context-label-timeout)
- [Splash Page](#splash-page)
- [Narration System](#narration-system)
- [Credentials Format](#credentials-format)
- [Adapting for a New App](#adapting-for-a-new-app)
- [Structure Best Practices](#structure-best-practices)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

```bash
# Install dependencies (from this directory or project root)
npm install @playwright/test

# Ensure the app server is running at the configured HUB_URL

# Create credentials.json (see format below)

# Run the demo
npx playwright test demo.js
```

Video output is saved to `./demo-videos/` with a timestamped filename.

---

## Directory Structure

```
demo/
├── demo.js              # Main Playwright test — the scripted demo flow
├── narration.json       # All narration text (and optional audio paths)
├── credentials.json     # Login credentials (gitignored, create manually)
├── splash.html          # Animated splash screen shown at demo start
├── util.js              # Shared utility functions (recording, highlighting, TTS, etc.)
├── demo-videos/         # Output directory for recorded videos (auto-created)
└── README.md            # This file
```

---

## File Reference

### `demo.js` — Main Demo Script

The main Playwright test file that orchestrates the entire demo. It is structured as a single `test(...)` block containing sequential steps that:

1. Start screen recording
2. Show the splash page (and prime TTS voices)
3. Navigate to the App Hub
4. Log in (if required)
5. Launch the target application
6. Walk through each feature/tab, filling inputs, clicking buttons, and waiting for results
7. Narrate each step using the `say()` utility
8. Highlight interactive elements using the `highlight()` utility
9. Stop recording and clean up

**Key design principle:** The demo script reads like a screenplay. Each section is clearly delimited with comment banners (`// === Step N — Description ===`) and follows a consistent pattern:

```js
// Navigate to section
await highlight(page, '[data-section="section-name"]');
await page.click('[data-section="section-name"]');
await page.waitForTimeout(MEDIUM_PAUSE);

// Narrate what's happening
await say(page, NARRATION['STEP_KEY'], MEDIUM_PAUSE);

// Interact with elements
await highlight(page, '#some-button');
await page.click('#some-button');

// Wait for results
await waitForBadgeDone(page, 'badge-id', 'Step Label', 120000);
```

### `narration.json` — Narration Text & Audio

A flat JSON object mapping narration keys to entries:

```json
{
  "STEP_KEY": {
    "text": "The text to speak via Web Speech API (TTS) or display.",
    "audio": null
  }
}
```

- **`text`** — The narration string. Used by `say()` for browser-based TTS.
- **`audio`** — Optional path to a pre-recorded audio file (`.mp3`, `.wav`). When provided, `say()` plays this file instead of using TTS. Set to `null` to use TTS.

All narration is externalized here so it can be reviewed, translated, or replaced with professional voiceover without modifying the demo script.

### `credentials.json` — Login Credentials

**This file is not checked into version control.** Create it manually:

```json
{
  "username": "your-username",
  "password": "your-password"
}
```

| Field      | Type   | Description                          |
|------------|--------|--------------------------------------|
| `username` | string | Login username for the App Hub       |
| `password` | string | Login password for the App Hub       |

The demo script checks for a login form on the hub page. If one is visible, it reads `credentials.json` and types the credentials with a realistic typing delay. If no login form appears, this file is not needed.

### `splash.html` — Splash Screen

A self-contained HTML file displayed at the very start of the demo recording. It serves two purposes:

1. **Visual intro** — An animated title card with the app name, description, and feature pills, providing a professional opening frame for the video.
2. **TTS voice priming** — The browser's Web Speech API often needs a page load to initialize available voices. Loading the splash page first ensures voices are ready before narration begins.

See [Splash Page](#splash-page) for customization details.

### `util.js` — Utility Functions

Shared helper functions used by all demo scripts. Imported at the top of `demo.js`:

```js
const {
    startRecording,
    highlight,
    openSessionLinkAndWaitForCompletion,
    say
} = require('./util');
```

See [Utility Functions](#utility-functions) for detailed API documentation.

---

## Configuration Reference

### Constants

Defined at the top of `demo.js` for easy tuning:

| Constant           | Default                        | Description                                                |
|--------------------|--------------------------------|------------------------------------------------------------|
| `HUB_URL`          | `http://localhost:12891/`      | Base URL of the Cognotik App Hub                              |
| `CREDENTIALS_PATH` | `./credentials.json`           | Path to login credentials file                             |
| `NARRATION_PATH`   | `./narration.json`             | Path to narration text/audio definitions                   |
| `SPLASH_PATH`      | `./splash.html`                | Path to the splash screen HTML                             |
| `TYPING_DELAY`     | `35`                           | Milliseconds between keystrokes (for realistic typing)     |
| `SHORT_PAUSE`      | `1500`                         | Short pause duration (ms) — after minor actions            |
| `MEDIUM_PAUSE`     | `3000`                         | Medium pause duration (ms) — after section transitions     |
| `LONG_PAUSE`       | `5000`                         | Long pause duration (ms) — after major results             |
| `VIDEO_DIR`        | `./demo-videos`                | Output directory for recorded video files                  |
| `APP_ID`           | `app-philosophical-calculator` | DOM `id` of the app card in the Hub to click               |

### Pipeline Step Toggles

Boolean flags that enable/disable individual demo sections. Set to `false` to skip a step (useful for shorter recordings or debugging):

```js
const ENABLE_SUMMARIZE          = false;
const ENABLE_DRAFT              = true;
const ENABLE_LENS_DIALECTICAL   = false;
const ENABLE_LENS_PERSPECTIVES  = true;
const ENABLE_LENS_SOCRATIC      = false;
const ENABLE_LENS_BRAINSTORM    = false;
const ENABLE_UPDATE_ARTICLE     = true;
const ENABLE_ILLUSTRATE         = false;
```

When a step is disabled, the script narrates a brief "skipping..." message (using the corresponding `*_SKIPPED` narration key) and moves on.

---

## Utility Functions

### `startRecording(outputPath)`

Starts an ffmpeg-based screen recording process that captures the desktop to an MP4 file.

```js
const recorder = startRecording('./demo-videos/output.mp4');
// ... run demo ...
await recorder.stop();
```

**Returns:** A recorder object with:
- `isRunning()` — Returns `true` if ffmpeg is actively recording.
- `stop()` — Gracefully stops the recording and finalizes the video file.

**Notes:**
- Requires `ffmpeg` to be installed and on the system PATH.
- If ffmpeg fails to start, the demo continues without recording (a warning is logged).
- The output directory is created automatically if it doesn't exist.

### `highlight(page, selector)`

Temporarily draws a visual highlight (animated border/glow) around a DOM element to draw the viewer's attention.

```js
await highlight(page, '#my-button');
```

**Parameters:**
| Param      | Type   | Description                                    |
|------------|--------|------------------------------------------------|
| `page`     | Page   | Playwright page object                         |
| `selector` | string | CSS selector of the element to highlight       |

The highlight is non-destructive (uses an overlay or outline) and fades after a short duration. Call this immediately before interacting with an element so viewers can see what's about to be clicked.

### `say(page, narrationEntry, pauseAfter)`

Speaks narration text using the browser's Web Speech API (or plays a pre-recorded audio file).

```js
await say(page, NARRATION['STEP_KEY'], MEDIUM_PAUSE);
```

**Parameters:**
| Param            | Type   | Description                                              |
|------------------|--------|----------------------------------------------------------|
| `page`           | Page   | Playwright page object (used to execute TTS in-browser)  |
| `narrationEntry` | object | An entry from `narration.json` with `text` and `audio`   |
| `pauseAfter`     | number | Milliseconds to wait after speech completes              |

**Behavior:**
- If `narrationEntry.audio` is a valid file path, it plays that audio file.
- Otherwise, it uses `window.speechSynthesis.speak()` with the `text` field.
- The function awaits completion of speech before applying `pauseAfter`.

### `openSessionLinkAndWaitForCompletion(page, context, label, timeout)`

After clicking a "Run" button that triggers an AI operation, this utility watches for a session link to appear, optionally opens it, and waits for the operation to complete.

```js
await openSessionLinkAndWaitForCompletion(page, page, 'article drafting', 180000);
```

**Parameters:**
| Param     | Type   | Description                                                  |
|-----------|--------|--------------------------------------------------------------|
| `page`    | Page   | Playwright page to monitor for session links                 |
| `context` | Page   | Browser context (for opening new tabs if needed)             |
| `label`   | string | Human-readable label for console logging                     |
| `timeout` | number | Maximum time (ms) to wait for the operation to complete      |

---

## Splash Page

The `splash.html` file is a standalone HTML page with:

- **Animated gradient background** with floating particle effects
- **Glassmorphism card** with app icon, title, subtitle, and feature pills
- **CSS-only animations** (no JavaScript dependencies)
- **Branding footer**

### Customizing for a New App

1. **Icon** — Change the emoji in `.splash-icon` (`🧮` → your app's icon)
2. **Title** — Update `.splash-title` text
3. **Subtitle** — Update `.splash-subtitle` text
4. **Feature pills** — Replace the `<span class="pill">` elements with your app's features
5. **Colors** — Adjust the gradient in `body` and accent colors (`#a78bfa`, `#818cf8`) to match your app's theme
6. **Footer** — Update the "Powered by" text if needed

The splash page is loaded via `file://` protocol, so it works offline with no server dependency.

---

## Narration System

### Design Philosophy

All spoken narration is externalized in `narration.json` to:
- Allow non-developers to review and edit narration copy
- Enable easy translation to other languages
- Support swapping TTS with pre-recorded professional voiceover
- Keep the demo script focused on actions, not prose

### Key Naming Convention

Narration keys follow a consistent pattern:

| Pattern              | Usage                                           |
|----------------------|-------------------------------------------------|
| `INTRO`              | Opening narration (played over splash)          |
| `*_TAB`              | When navigating to a tab/section                |
| `*_OVERVIEW`         | Describing what a section does                  |
| `*_STARTED`          | When an AI operation begins                     |
| `*_DONE`             | When an AI operation completes                  |
| `*_SKIPPED`          | When a step is disabled via toggle              |
| `*_RESULTS`          | When reviewing output in a results view         |
| `*_INTRO`            | Introducing a step before running it            |
| `OUTRO`              | Closing narration                               |
| `LOGIN_PROMPT`       | Before typing credentials                       |
| `LOGIN_SUCCESS`      | After successful login                          |

### Adding Pre-Recorded Audio

To replace TTS with a recorded audio file for any narration entry:

```json
{
  "INTRO": {
    "text": "Welcome to the demo...",
    "audio": "./audio/intro.mp3"
  }
}
```

The `say()` utility will prefer the audio file when present.

---

## Credentials Format

Create `credentials.json` in the demo directory:

```json
{
  "username": "demo-user",
  "password": "demo-password"
}
```

**Security notes:**
- Add `credentials.json` to `.gitignore` — never commit real credentials.
- The demo script only reads this file if a login form is detected on the hub page.
- For CI/CD or shared environments, consider injecting credentials via environment variables and modifying the script to read from `process.env`.

---

## Adapting for a New App

Follow these steps to create a demo for a different application:

### 1. Copy the demo directory

```bash
cp -r apps/philosophical-calculator/demo/ apps/your-new-app/demo/
```

### 2. Update `demo.js`

- **`APP_ID`** — Set to the DOM `id` of your app's card in the Hub.
- **`HUB_URL`** — Update if your app runs on a different port.
- **Model constants** — Update `SMART_MODEL`, `FAST_MODEL`, `IMAGE_MODEL` or remove if not applicable.
- **Pipeline toggles** — Replace with toggles relevant to your app's workflow.
- **Sample data** — Replace `SAMPLE_NOTES`, `SAMPLE_INSTRUCTIONS` with content appropriate for your app.
- **Step sections** — Rewrite the `// === Step N ===` sections to match your app's UI flow. Keep the pattern of: navigate → narrate → highlight → interact → wait → narrate result.
- **`waitForBadgeDone()`** — Update badge IDs and selectors to match your app's progress indicators (or replace with your own completion-detection logic).

### 3. Update `narration.json`

- Replace all entries with narration appropriate for your app.
- Keep the naming convention (`*_STARTED`, `*_DONE`, `*_SKIPPED`, etc.).
- Remove entries for steps that don't exist in your app; add new ones as needed.

### 4. Update `splash.html`

- Change the icon, title, subtitle, and feature pills.
- Optionally adjust the color scheme.

### 5. Create `credentials.json`

- Add credentials for your app's login (if applicable).

### 6. Test incrementally

- Use pipeline toggles to enable one step at a time.
- Run with `--headed` to watch the browser:
  ```bash
  npx playwright test demo.js --headed
  ```
- Check console output for `✅` / `❌` markers.

---

## Structure Best Practices

### Script Organization

1. **Constants at the top** — All URLs, paths, delays, model names, and toggles should be defined as constants at the top of the file for easy tuning.

2. **Clear step boundaries** — Use prominent comment banners to separate each demo step:
   ```js
   // =================================================================
   // Step N — Description of What Happens
   // =================================================================
   ```

3. **Consistent interaction pattern** — Every UI interaction should follow:
   ```
   highlight → click/type → wait → narrate
   ```

4. **Generous timeouts** — AI operations can be slow. Set `test.setTimeout()` to at least 30 minutes. Use per-step timeouts in `waitForBadgeDone()` and `openSessionLinkAndWaitForCompletion()`.

5. **Graceful degradation** — Use `.catch(() => {})` for non-critical waits (e.g., `scrollIntoViewIfNeeded`, `waitForNavigation`). The demo should continue even if a minor UI element isn't found.

6. **Toggle-gated sections** — Wrap each major feature in an `if (ENABLE_*)` block with a corresponding `*_SKIPPED` narration fallback. This allows quick iteration and shorter test runs.

### Timing Guidelines

| Context                        | Recommended Pause | Constant       |
|--------------------------------|-------------------|----------------|
| After a minor click/type       | 1.5s              | `SHORT_PAUSE`  |
| After navigating to a new tab  | 3s                | `MEDIUM_PAUSE` |
| After viewing a major result   | 5s                | `LONG_PAUSE`   |
| Typing delay per character     | 35ms              | `TYPING_DELAY` |

These values produce a natural-feeling pace for video recording. Increase them for slower narration; decrease for faster demos.

### Error Handling

The demo is wrapped in a `try/catch/finally` block:

- **`try`** — The entire demo flow.
- **`catch`** — Logs the error and re-throws (so Playwright reports it as a test failure).
- **`finally`** — Always stops the recorder and closes the browser context, ensuring no orphaned processes.

### Video Recording

- Videos are saved with ISO timestamps in the filename for easy sorting.
- The `VIDEO_DIR` is created automatically by the recorder utility.
- If ffmpeg is not available, the demo runs without recording (a warning is logged).

---

## Troubleshooting

| Problem                              | Solution                                                                                   |
|--------------------------------------|--------------------------------------------------------------------------------------------|
| `ffmpeg` not found                   | Install ffmpeg and ensure it's on your PATH                                                |
| TTS voices not loading               | The splash page primes voices; increase the initial wait if voices still aren't available   |
| Login form not detected              | Check that `HUB_URL` is correct and the server is running                                  |
| Badge never shows "done"             | Increase the timeout in `waitForBadgeDone()` or check the app's backend logs               |
| Credentials file not found           | Create `credentials.json` in the demo directory (see format above)                         |
| Elements not found by selector       | Run with `--headed` and inspect the DOM; selectors may differ between app versions         |
| Demo times out (30 min)              | Increase `test.setTimeout()` or disable slow steps via toggles                             |
| Video is black/empty                 | Ensure the screen/display is active (not locked); ffmpeg captures the desktop, not the browser |