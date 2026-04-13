// =============================================================================
// Philosophical Calculator — Demo Utilities
// =============================================================================
// Shared helpers for screen recording, narration, highlighting, and session
// monitoring used by the Playwright demo script.
// =============================================================================

const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');
// ---------------------------------------------------------------------------
// Narration / credentials paths (set by consumer via setNarrationPath)
// ---------------------------------------------------------------------------
let NARRATION_PATH = '';
/**
  * Allow the demo script to tell util where narration.json lives so that
  * audio-file paths inside `say()` can be resolved relative to it.
  */
function setNarrationPath(p) {
     NARRATION_PATH = p;
}

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const SHORT_PAUSE = 1500;
const MEDIUM_PAUSE = 3000;
const TYPING_DELAY = 35;

// ---------------------------------------------------------------------------
// WSL detection
// ---------------------------------------------------------------------------
const RUNNING_IN_WSL = isWSL();
if (RUNNING_IN_WSL) {
    console.log('🐧 WSL environment detected — will use ffmpeg.exe with Windows capture devices (gdigrab/dshow).');
}


/**
  * Handle login if a login form is visible on the current page.
  *
  * @param {import('@playwright/test').Page} page
  * @param {string} credentialsPath - Path to credentials.json
  * @param {object} narration - The full NARRATION object (needs LOGIN_PROMPT & LOGIN_SUCCESS keys)
  * @param {object} [options]
  * @param {number} [options.typingDelay] - ms between keystrokes (default 35)
  * @param {number} [options.pauseAfter] - ms to wait after successful login (default SHORT_PAUSE)
  */
async function handleLogin(page, credentialsPath, narration, options = {}) {
     const typingDelay = options.typingDelay ?? TYPING_DELAY;
     const pauseAfter = options.pauseAfter ?? SHORT_PAUSE;
     const loginForm = page.locator('.login-container');
     if (!(await loginForm.isVisible({ timeout: 5000 }).catch(() => false))) {
         return false; // No login form — nothing to do
     }
     if (narration['LOGIN_PROMPT']) {
         await say(page, narration['LOGIN_PROMPT'], SHORT_PAUSE);
     }
     let credentials;
     try {
         const raw = fs.readFileSync(credentialsPath, 'utf-8');
         credentials = JSON.parse(raw);
     } catch (e) {
         throw new Error(`Failed to load credentials from ${credentialsPath}: ${e.message}`);
     }
     await highlight(page, '#username');
     await page.locator('#username').click();
     await page.locator('#username').type(credentials.username, { delay: typingDelay });
     await highlight(page, '#password');
     await page.locator('#password').click();
     await page.locator('#password').type(credentials.password, { delay: typingDelay });
     await highlight(page, 'button[type="submit"]');
     await page.click('button[type="submit"]');
     await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
     await page.waitForTimeout(pauseAfter);
     if (narration['LOGIN_SUCCESS']) {
         await say(page, narration['LOGIN_SUCCESS'], SHORT_PAUSE);
     }
     return true;
}
/**
 * Speak a narration entry by key. If the entry has a pre-rendered audio file,
 * it will be played; otherwise falls back to Web Speech API via `narrate()`.
 *
 * @param {import('@playwright/test').Page} page
 * @param {string} key  - Key into NARRATION (e.g. "INTRO")
 * @param {number} [pauseAfter] - Extra ms to wait after speech finishes
 */
async function say(page, entry, pauseAfter = SHORT_PAUSE) {
    if (entry.audio) {
        // Play pre-rendered audio file and wait for it to finish
        const audioPath = path.resolve(path.dirname(NARRATION_PATH), entry.audio);
        if (fs.existsSync(audioPath)) {
            const audioData = fs.readFileSync(audioPath).toString('base64');
            const ext = path.extname(entry.audio).replace('.', '');
            const mime = ext === 'mp3' ? 'audio/mpeg'
                : ext === 'ogg' ? 'audio/ogg'
                    : `audio/${ext}`;
            await page.evaluate(({dataUri}) => {
                return new Promise((resolve) => {
                    const audio = new Audio(dataUri);
                    audio.addEventListener('ended', resolve);
                    audio.addEventListener('error', resolve);
                    audio.play().catch(resolve);
                });
            }, {dataUri: `data:${mime};base64,${audioData}`});
            if (pauseAfter > 0) await page.waitForTimeout(pauseAfter);
            return;
        } else {
            console.warn(`⚠️  Audio file "${entry.audio}" not found for key "${key}" — falling back to TTS.`);
        }
    }
    // Fallback: Web Speech API via the existing narrate() helper
    await narrate(page, entry.text, pauseAfter);
}


/**
 * Detect if we are running inside Windows Subsystem for Linux (WSL).
 */
function isWSL() {
    if (process.platform !== 'linux') return false;
    try {
        const release = fs.readFileSync('/proc/version', 'utf-8').toLowerCase();
        return release.includes('microsoft') || release.includes('wsl');
    } catch {
        return false;
    }
}

// ---------------------------------------------------------------------------
// FFmpeg screen + audio recording helpers
// ---------------------------------------------------------------------------

/**
 * Detect the platform and return the appropriate ffmpeg args for
 * screen capture WITH system audio (loopback).
 */
function getFFmpegArgs(outputPath) {
    const platform = process.platform;

    if (platform === 'linux' && !RUNNING_IN_WSL) {
        let pulseMonitor = 'default.monitor';
        try {
            const sources = execSync('pactl list short sources 2>/dev/null', { encoding: 'utf-8' });
            const monitorLine = sources.split('\n').find(l => l.includes('.monitor'));
            if (monitorLine) {
                pulseMonitor = monitorLine.split('\t')[1] || 'default.monitor';
            }
        } catch {}

        let screenSize = '1920x1080';
        try {
            const xdpyinfo = execSync('xdpyinfo 2>/dev/null | grep dimensions', { encoding: 'utf-8' });
            const match = xdpyinfo.match(/(\d+x\d+)/);
            if (match) screenSize = match[1];
        } catch {}

        return [
            '-y',
            '-f', 'x11grab', '-framerate', '30', '-video_size', screenSize,
            '-i', process.env.DISPLAY || ':0',
            '-f', 'pulse', '-i', pulseMonitor,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    } else if (RUNNING_IN_WSL) {
        let winOutputPath = outputPath;
        try {
            winOutputPath = execSync(`wslpath -w "${outputPath}"`, { encoding: 'utf-8' }).trim();
        } catch {
            const mntMatch = outputPath.match(/^\/mnt\/([a-zA-Z])\/(.*)/);
            if (mntMatch) {
                winOutputPath = `${mntMatch[1].toUpperCase()}:\\${mntMatch[2].replace(/\//g, '\\')}`;
            }
        }

        let audioDevice = null;
        try {
            const devices = execSync(
                'ffmpeg.exe -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                { encoding: 'utf-8', shell: true }
            );
             const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear|Internal AUX|Virtual Audio)[^"]*)"/i);
            if (loopbackMatch) audioDevice = loopbackMatch[1];
            if (!audioDevice) {
                 // Modern ffmpeg (8.x+) uses per-line "(audio)" / "(video)" markers
                 // instead of section headers like "DirectShow audio devices".
                const lines = devices.split('\n');
                for (const line of lines) {
                     if (line.includes('Alternative name')) continue;
                     // Match lines like: [in#0 @ ...] "Device Name" (audio)
                     const audioLineMatch = line.match(/"([^"]+)"\s*\(audio\)/i);
                     if (audioLineMatch) {
                         audioDevice = audioLineMatch[1];
                         break;
                     }
                 }
                 // Fallback: try legacy section-header format
                 if (!audioDevice) {
                     let inAudioSection = false;
                     for (const line of lines) {
                         if (line.includes('DirectShow audio devices')) inAudioSection = true;
                         else if (line.includes('DirectShow video devices')) inAudioSection = false;
                         if (inAudioSection) {
                             const nameMatch = line.match(/"([^"]+)"/);
                             if (nameMatch && !line.includes('Alternative name')) {
                                 audioDevice = nameMatch[1];
                                 break;
                             }
                         }
                    }
                }
            }
         } catch (e) {
             console.warn(`⚠️  Failed to enumerate dshow devices: ${e.message}`);
         }
        if (!audioDevice) {
             console.warn(
                 '⚠️  No audio capture device found (WSL/Windows). ' +
                 'Enable "Stereo Mix" in Windows Sound settings, or install virtual audio cable (e.g. VB-CABLE). ' +
                 'Recording will proceed WITHOUT audio.'
            );
             return [
                 '-y',
                 '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
                 '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
                 '-an',
                 winOutputPath
             ];
        }
        console.log(`   Audio device: "${audioDevice}"`);

        return [
            '-y',
            '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
            '-f', 'dshow', '-i', `audio=${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            winOutputPath
        ];
    } else if (platform === 'darwin') {
        let audioDevice = '0';
        try {
            const devices = execSync(
                'ffmpeg -f avfoundation -list_devices true -i "" 2>&1 || true',
                { encoding: 'utf-8' }
            );
            const lines = devices.split('\n');
            let inAudioSection = false;
            for (const line of lines) {
                if (line.includes('audio devices')) inAudioSection = true;
                if (inAudioSection) {
                    const bhMatch = line.match(/\[(\d+)\].*(?:BlackHole|Soundflower|Loopback)/i);
                    if (bhMatch) { audioDevice = bhMatch[1]; break; }
                }
            }
        } catch {}

        return [
            '-y',
            '-f', 'avfoundation', '-framerate', '30', '-capture_cursor', '1',
            '-i', `1:${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    } else if (platform === 'win32') {
        let audioDevice = null;
        try {
            const devices = execSync(
                'ffmpeg -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                { encoding: 'utf-8', shell: true }
            );
             const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear|Internal AUX|Virtual Audio)[^"]*)"/i);
            if (loopbackMatch) audioDevice = loopbackMatch[1];
            if (!audioDevice) {
                 // Modern ffmpeg (8.x+) uses per-line "(audio)" / "(video)" markers
                const lines = devices.split('\n');
                for (const line of lines) {
                     if (line.includes('Alternative name')) continue;
                     const audioLineMatch = line.match(/"([^"]+)"\s*\(audio\)/i);
                     if (audioLineMatch) {
                         audioDevice = audioLineMatch[1];
                         break;
                     }
                 }
                 // Fallback: try legacy section-header format
                 if (!audioDevice) {
                     let inAudioSection = false;
                     for (const line of lines) {
                         if (line.includes('DirectShow audio devices')) inAudioSection = true;
                         else if (line.includes('DirectShow video devices')) inAudioSection = false;
                         if (inAudioSection) {
                             const nameMatch = line.match(/"([^"]+)"/);
                             if (nameMatch && !line.includes('Alternative name')) {
                                 audioDevice = nameMatch[1];
                                 break;
                             }
                         }
                    }
                }
            }
         } catch (e) {
             console.warn(`⚠️  Failed to enumerate dshow devices: ${e.message}`);
         }
        if (!audioDevice) {
             console.warn(
                 '⚠️  No audio capture device found on Windows. ' +
                 'Enable "Stereo Mix" in Windows Sound settings, or install virtual audio cable (e.g. VB-CABLE). ' +
                 'Recording will proceed WITHOUT audio.'
            );
             return [
                 '-y',
                 '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
                 '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
                 '-an',
                 outputPath
             ];
        }
        console.log(`   Audio device: "${audioDevice}"`);

        return [
            '-y',
            '-f', 'gdigrab', '-framerate', '30', '-i', 'desktop',
            '-f', 'dshow', '-i', `audio=${audioDevice}`,
            '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
            '-c:a', 'aac', '-b:a', '128k', '-ac', '2',
            outputPath
        ];
    }

    console.warn(`⚠️  Unsupported platform (${platform}). Recording video only.`);
    return [
        '-y',
        '-f', 'x11grab', '-framerate', '30', '-video_size', '1920x1080',
        '-i', process.env.DISPLAY || ':0',
        '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
        '-an',
        outputPath
    ];
}

/**
 * Start ffmpeg recording. Returns an object with stop() and isRunning().
 */
function startRecording(outputPath) {
    const dir = path.dirname(outputPath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }

    const args = getFFmpegArgs(outputPath);
    const ffmpegBin = RUNNING_IN_WSL ? 'ffmpeg.exe' : 'ffmpeg';
    console.log(`\n🎬 Starting screen + audio recording with ${ffmpegBin}...`);
    console.log(`   Output: ${outputPath}`);
    console.log(`   Command: ${ffmpegBin} ${args.join(' ')}`);

    const ffmpeg = spawn(ffmpegBin, args, {
        stdio: ['pipe', 'pipe', 'pipe'],
    });

    let ffmpegRunning = true;
    let stderrLog = '';

    ffmpeg.stderr.on('data', (data) => {
        stderrLog += data.toString();
    });

    ffmpeg.on('error', (err) => {
        ffmpegRunning = false;
        if (err.code === 'ENOENT') {
            console.error(`❌ ${ffmpegBin} not found! Please install ffmpeg.`);
            if (RUNNING_IN_WSL) {
                console.error('   ⚠️  WSL detected: you need ffmpeg.exe (Windows) on your PATH.');
            }
        } else {
            console.error(`❌ ${ffmpegBin} error:`, err.message);
        }
    });

    ffmpeg.on('close', (code) => {
        ffmpegRunning = false;
        if (code !== 0 && code !== 255) {
            console.error(`⚠️  ffmpeg exited with code ${code}`);
            const lines = stderrLog.trim().split('\n');
            const tail = lines.slice(-5).join('\n');
            if (tail) console.error(`   Last ffmpeg output:\n${tail}`);
        }
    });

    const startTime = Date.now();

    return {
        stop: () => {
            return new Promise((resolve) => {
                if (!ffmpegRunning) { resolve(outputPath); return; }
                console.log('\n🎬 Stopping recording...');
                try {
                    ffmpeg.stdin.write('q');
                    ffmpeg.stdin.end();
                } catch {
                    try { ffmpeg.kill('SIGINT'); } catch {}
                }
                const timeout = setTimeout(() => {
                    try { ffmpeg.kill('SIGKILL'); } catch {}
                    resolve(outputPath);
                }, 10000);
                ffmpeg.on('close', () => {
                    clearTimeout(timeout);
                    const duration = ((Date.now() - startTime) / 1000).toFixed(1);
                    console.log(`🎬 Recording saved (${duration}s): ${outputPath}`);
                    resolve(outputPath);
                });
            });
        },
        process: ffmpeg,
        isRunning: () => ffmpegRunning,
    };
}

// ---------------------------------------------------------------------------
// Narration helper — uses Web Speech API in the browser
// ---------------------------------------------------------------------------
async function narrate(page, text, pauseMs = MEDIUM_PAUSE) {
    console.log(`\n🎙️  ${text}`);
    try {
        await page.evaluate(async (utteranceText) => {
            return new Promise((resolve) => {
                if (!window.speechSynthesis) { resolve(); return; }
                window.speechSynthesis.cancel();
                const utterance = new SpeechSynthesisUtterance(utteranceText);
                utterance.rate = 1.05;
                utterance.pitch = 1.0;
                utterance.volume = 1.0;
                const voices = window.speechSynthesis.getVoices();
                if (voices.length > 0) {
                    const preferred = voices.find(v =>
                        (v.lang.startsWith('en') && v.name.includes('Google')) ||
                        (v.lang.startsWith('en') && v.name.includes('Natural')) ||
                        (v.lang.startsWith('en') && v.name.includes('Samantha'))
                    ) || voices.find(v => v.lang.startsWith('en') && !v.localService === false)
                      || voices.find(v => v.lang.startsWith('en'));
                    if (preferred) utterance.voice = preferred;
                }
                utterance.onend = () => resolve();
                utterance.onerror = () => resolve();
                const safetyTimeout = setTimeout(() => {
                    window.speechSynthesis.cancel();
                    resolve();
                }, Math.max(utteranceText.length * 100, 15000));
                utterance.onend = () => { clearTimeout(safetyTimeout); resolve(); };
                utterance.onerror = () => { clearTimeout(safetyTimeout); resolve(); };
                window.speechSynthesis.speak(utterance);
            });
        }, text);
    } catch {
        // TTS failed — fall back to pause only
    }
    await page.waitForTimeout(Math.min(pauseMs, SHORT_PAUSE));
}

// ---------------------------------------------------------------------------
// Highlight an element briefly
// ---------------------------------------------------------------------------
async function highlight(page, selector, durationMs = 1200) {
    await page.evaluate(({ sel, dur }) => {
        const el = document.querySelector(sel);
        if (!el) return;
        const prev = el.style.outline;
        el.style.outline = '3px solid #ff6600';
        el.style.outlineOffset = '3px';
        setTimeout(() => { el.style.outline = prev; el.style.outlineOffset = ''; }, dur);
    }, { sel: selector, dur: durationMs });
    await page.waitForTimeout(durationMs);
}

// ---------------------------------------------------------------------------
// Open session monitor link in new tab, wait for task completion
// ---------------------------------------------------------------------------
async function openSessionLinkAndWaitForCompletion(page, mainPage, taskDescription, timeoutMs = 120000) {
    let sessionTab = null;
    try {
        await page.waitForSelector('.session-monitor-link a.monitor-link', { timeout: 15000 });
        await page.waitForTimeout(1000);

        const monitorLink = page.locator('.session-monitor-link a.monitor-link').last();
        if (await monitorLink.isVisible()) {
            const href = await monitorLink.getAttribute('href');
            await narrate(page,
                `A live session monitor appeared for ${taskDescription}. Let's watch the AI work in real-time.`,
                SHORT_PAUSE);

            const context = page.context();
            sessionTab = await context.newPage();
            const fullUrl = new URL(href, page.url()).toString();
            await sessionTab.goto(fullUrl, { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});

            await narrate(sessionTab,
                `This is the live session view for ${taskDescription}. We can see prompts, responses, and progress as they happen.`,
                MEDIUM_PAUSE);

            await sessionTab.evaluate(() => {
                window.scrollTo({ top: 300, behavior: 'smooth' });
            }).catch(() => {});
            await sessionTab.waitForTimeout(MEDIUM_PAUSE);
        }
    } catch {
        console.log(`  (No session monitor link detected for ${taskDescription})`);
    }

    // Wait for task completion on the main page
    try {
        await page.waitForFunction(() => {
            const completed = document.querySelector('.session-completed-link');
            const errored = document.querySelector('.session-error-link');
            return completed || errored;
        }, { timeout: timeoutMs });
    } catch {
        console.log(`  (Task ${taskDescription} did not complete within timeout)`);
    }

    // Close session tab if opened
    if (sessionTab) {
        try {
            await sessionTab.reload({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => {});
            await sessionTab.waitForTimeout(SHORT_PAUSE);
            await sessionTab.evaluate(() => {
                window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
            }).catch(() => {});
            await narrate(sessionTab,
                `${taskDescription} is complete. Let's go back to the main app.`,
                SHORT_PAUSE);
            await sessionTab.close();
        } catch {
            await sessionTab.close().catch(() => {});
        }
        await page.bringToFront();
        await page.waitForTimeout(800);
    }
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------
module.exports = {
    startRecording,
    highlight,
    openSessionLinkAndWaitForCompletion,
     say,
     handleLogin,
     setNarrationPath
};