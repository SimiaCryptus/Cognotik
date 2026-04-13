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

const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------
const SHORT_PAUSE = 1500;     // pause so the viewer can read
const MEDIUM_PAUSE = 3000;
// ---------------------------------------------------------------------------
// WSL detection
// ---------------------------------------------------------------------------
const RUNNING_IN_WSL = isWSL();
if (RUNNING_IN_WSL) {
     console.log('🐧 WSL environment detected — will use ffmpeg.exe with Windows capture devices (gdigrab/dshow).');
}

/**
  * Detect if we are running inside Windows Subsystem for Linux (WSL).
  * In WSL the screen, audio, and browser are all on the Windows side,
  * so we need to use Windows capture methods (gdigrab/dshow) via ffmpeg.exe.
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
     // If running in WSL, use Windows capture methods since the display is on Windows
     if (platform === 'linux' && !RUNNING_IN_WSL) {
         // Use x11grab for video + PulseAudio monitor for system audio loopback
         // The pulse monitor source captures whatever is playing through speakers
         let pulseMonitor = 'default.monitor';
         try {
             // Try to detect the correct PulseAudio monitor source
             const sources = execSync('pactl list short sources 2>/dev/null', { encoding: 'utf-8' });
             const monitorLine = sources.split('\n').find(l => l.includes('.monitor'));
             if (monitorLine) {
                 pulseMonitor = monitorLine.split('\t')[1] || 'default.monitor';
             }
         } catch {
             // Fall back to default
         }
         // Detect screen resolution
         let screenSize = '1920x1080';
         try {
             const xdpyinfo = execSync('xdpyinfo 2>/dev/null | grep dimensions', { encoding: 'utf-8' });
             const match = xdpyinfo.match(/(\d+x\d+)/);
             if (match) screenSize = match[1];
         } catch {}
         return [
             '-y',
             // Video input: X11 screen capture
             '-f', 'x11grab',
             '-framerate', '30',
             '-video_size', screenSize,
             '-i', process.env.DISPLAY || ':0',
             // Audio input: PulseAudio system audio loopback
             '-f', 'pulse',
             '-i', pulseMonitor,
             // Encoding settings
             '-c:v', 'libx264',
             '-preset', 'ultrafast',
             '-crf', '23',
             '-pix_fmt', 'yuv420p',
             '-c:a', 'aac',
             '-b:a', '128k',
             '-ac', '2',
             outputPath
         ];
     } else if (RUNNING_IN_WSL) {
         // WSL: The browser and display are on the Windows side.
         // Use ffmpeg.exe (Windows binary) with gdigrab + dshow to capture
         // the Windows desktop screen and audio.
         // Convert the Linux path to a Windows path for ffmpeg.exe
         let winOutputPath = outputPath;
         try {
             winOutputPath = execSync(`wslpath -w "${outputPath}"`, { encoding: 'utf-8' }).trim();
         } catch {
             // If wslpath fails, try a manual conversion from /mnt/X/... to X:\...
             const mntMatch = outputPath.match(/^\/mnt\/([a-zA-Z])\/(.*)/);
             if (mntMatch) {
                 winOutputPath = `${mntMatch[1].toUpperCase()}:\\${mntMatch[2].replace(/\//g, '\\')}`;
             }
         }
         // Try to detect a loopback audio device on the Windows side
         let audioDevice = 'Stereo Mix';
         try {
             const devices = execSync(
                 'ffmpeg.exe -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                 { encoding: 'utf-8', shell: true }
             );
             const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear)[^"]*)"/i);
             if (loopbackMatch) {
                 audioDevice = loopbackMatch[1];
             }
         } catch {}
         return [
             '-y',
             // Video: GDI grab (full Windows desktop)
             '-f', 'gdigrab',
             '-framerate', '30',
             '-i', 'desktop',
             // Audio: DirectShow loopback
             '-f', 'dshow',
             '-i', `audio=${audioDevice}`,
             '-c:v', 'libx264',
             '-preset', 'ultrafast',
             '-crf', '23',
             '-pix_fmt', 'yuv420p',
             '-c:a', 'aac',
             '-b:a', '128k',
             '-ac', '2',
             winOutputPath
         ];
     } else if (platform === 'darwin') {
         // macOS: use avfoundation
         // Screen input index is typically "1" (or "Capture screen 0")
         // Audio: we need BlackHole or similar loopback, or use ":0" for default mic
         // For system audio capture on macOS, users need BlackHole/Soundflower installed
         // and configured as a multi-output device. We try to find it automatically.
         let audioDevice = '0'; // default input device
         try {
             const devices = execSync(
                 'ffmpeg -f avfoundation -list_devices true -i "" 2>&1 || true',
                 { encoding: 'utf-8' }
             );
             // Look for BlackHole or Soundflower (common loopback drivers)
             const lines = devices.split('\n');
             let inAudioSection = false;
             for (let i = 0; i < lines.length; i++) {
                 if (lines[i].includes('audio devices')) inAudioSection = true;
                 if (inAudioSection) {
                     const bhMatch = lines[i].match(/\[(\d+)\].*(?:BlackHole|Soundflower|Loopback)/i);
                     if (bhMatch) {
                         audioDevice = bhMatch[1];
                         break;
                     }
                 }
             }
         } catch {}
         return [
             '-y',
             '-f', 'avfoundation',
             '-framerate', '30',
             '-capture_cursor', '1',
             '-i', `1:${audioDevice}`,
             '-c:v', 'libx264',
             '-preset', 'ultrafast',
             '-crf', '23',
             '-pix_fmt', 'yuv420p',
             '-c:a', 'aac',
             '-b:a', '128k',
             '-ac', '2',
             outputPath
         ];
     } else if (platform === 'win32') {
         // Windows: use gdigrab for video + dshow for audio loopback
         // "Stereo Mix" is the common loopback device name on Windows
         // Users may need to enable it in Sound settings
         let audioDevice = 'Stereo Mix';
         try {
             const devices = execSync(
                 'ffmpeg -f dshow -list_devices true -i dummy 2>&1 || echo ""',
                 { encoding: 'utf-8', shell: true }
             );
             // Try to find a loopback/stereo mix device
             const loopbackMatch = devices.match(/"((?:Stereo Mix|CABLE Output|Loopback|What U Hear)[^"]*)"/i);
             if (loopbackMatch) {
                 audioDevice = loopbackMatch[1];
             }
         } catch {}
         return [
             '-y',
             // Video: GDI grab (full desktop)
             '-f', 'gdigrab',
             '-framerate', '30',
             '-i', 'desktop',
             // Audio: DirectShow loopback
             '-f', 'dshow',
             '-i', `audio=${audioDevice}`,
             '-c:v', 'libx264',
             '-preset', 'ultrafast',
             '-crf', '23',
             '-pix_fmt', 'yuv420p',
             '-c:a', 'aac',
             '-b:a', '128k',
             '-ac', '2',
             outputPath
         ];
     }
     // Fallback: just try x11grab without audio
     console.warn(`⚠️  Unsupported platform (${platform}, WSL=${RUNNING_IN_WSL}) for audio capture. Recording video only.`);
     return [
         '-y',
         '-f', 'x11grab',
         '-framerate', '30',
         '-video_size', '1920x1080',
         '-i', process.env.DISPLAY || ':0',
         '-c:v', 'libx264',
         '-preset', 'ultrafast',
         '-crf', '23',
         '-pix_fmt', 'yuv420p',
         '-an',
         outputPath
     ];
}
/**
  * Start ffmpeg recording. Returns an object with a stop() method.
  */
function startRecording(outputPath) {
     // Ensure output directory exists
     const dir = path.dirname(outputPath);
     if (!fs.existsSync(dir)) {
         fs.mkdirSync(dir, { recursive: true });
     }
     const args = getFFmpegArgs(outputPath);
      // In WSL, we must invoke the Windows ffmpeg.exe since we need Windows
      // capture devices (gdigrab, dshow). The Linux ffmpeg cannot access them.
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
              console.error(`❌ ${ffmpegBin} not found! Please install ffmpeg to enable video+audio recording.`);
             console.error('   On Ubuntu/Debian: sudo apt install ffmpeg');
             console.error('   On macOS: brew install ffmpeg');
             console.error('   On Windows: choco install ffmpeg  or download from https://ffmpeg.org');
              if (RUNNING_IN_WSL) {
                  console.error('   ⚠️  WSL detected: you need ffmpeg.exe (Windows) on your PATH.');
                  console.error('      Install ffmpeg for Windows and ensure ffmpeg.exe is accessible from WSL.');
                  console.error('      e.g. add /mnt/c/path/to/ffmpeg/bin to your PATH');
              }
         } else {
              console.error(`❌ ${ffmpegBin} error:`, err.message);
         }
     });
     ffmpeg.on('close', (code) => {
         ffmpegRunning = false;
         if (code !== 0 && code !== 255) {
             // code 255 is normal when we send 'q' to quit
             console.error(`⚠️  ffmpeg exited with code ${code}`);
             // Print last few lines of stderr for debugging
             const lines = stderrLog.trim().split('\n');
             const tail = lines.slice(-5).join('\n');
             if (tail) console.error(`   Last ffmpeg output:\n${tail}`);
         }
     });
     // Give ffmpeg a moment to initialize
     const startTime = Date.now();
     return {
         /** Gracefully stop the recording and wait for the file to be finalized. */
         stop: () => {
             return new Promise((resolve) => {
                 if (!ffmpegRunning) {
                     resolve(outputPath);
                     return;
                 }
                 console.log('\n🎬 Stopping recording...');
                 // Send 'q' to ffmpeg's stdin for graceful shutdown
                 try {
                     ffmpeg.stdin.write('q');
                     ffmpeg.stdin.end();
                 } catch {
                     // stdin may already be closed; try SIGINT
                     try { ffmpeg.kill('SIGINT'); } catch {}
                 }
                 // Wait for ffmpeg to exit, with a safety timeout
                 const timeout = setTimeout(() => {
                     console.log('   Force-killing ffmpeg (timeout)...');
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
// Narration helper — prints to console; swap in a TTS engine if desired
// ---------------------------------------------------------------------------
async function narrate(page, text, pauseMs = MEDIUM_PAUSE) {
    console.log(`\n🎙️  ${text}`);
     // Use the browser's built-in Web Speech API for text-to-speech
     try {
         await page.evaluate(async (utteranceText) => {
             return new Promise((resolve) => {
                 if (!window.speechSynthesis) {
                     resolve();
                     return;
                 }
                 // Cancel any ongoing speech first
                 window.speechSynthesis.cancel();
                 const utterance = new SpeechSynthesisUtterance(utteranceText);
                 utterance.rate = 1.05;   // slightly faster than default for a brisk demo pace
                 utterance.pitch = 1.0;
                 utterance.volume = 1.0;
                 // Try to pick a good English voice if available
                 const voices = window.speechSynthesis.getVoices();
                 if (voices.length > 0) {
                     const preferred = voices.find(v =>
                         (v.lang.startsWith('en') && v.name.includes('Google')) ||
                         (v.lang.startsWith('en') && v.name.includes('Natural')) ||
                         (v.lang.startsWith('en') && v.name.includes('Samantha'))
                     ) || voices.find(v => v.lang.startsWith('en') && !v.localService === false)
                       || voices.find(v => v.lang.startsWith('en'));
                     if (preferred) {
                         utterance.voice = preferred;
                     }
                 }
                 utterance.onend = () => resolve();
                 utterance.onerror = () => resolve();
                 // Safety timeout — don't block forever if speech hangs
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
         // If TTS fails (e.g. headless mode, page closed), just fall back to the pause
     }
     // Always wait at least a short moment so the viewer can read on-screen changes
     await page.waitForTimeout(Math.min(pauseMs, SHORT_PAUSE));
}
// ---------------------------------------------------------------------------
// Highlight an element briefly so the viewer can see what we're about to click
// ---------------------------------------------------------------------------
async function highlight(page, selector, durationMs = 1200) {
    await page.evaluate(({ sel, dur }) => {
        const el = document.querySelector(sel);
        if (!el) return;
        const prev = el.style.outline;
        el.style.outline = '3px solid #ff6600';
        el.style.outlineOffset = '3px';
         setTimeout(function() { el.style.outline = prev; el.style.outlineOffset = ''; }, dur);
    }, { sel: selector, dur: durationMs });
    await page.waitForTimeout(durationMs);
}
// ---------------------------------------------------------------------------
// Helper: open session monitor link in new tab, return when task completes
// ---------------------------------------------------------------------------
async function openSessionLinkAndWaitForCompletion(page, mainPage, taskDescription, timeoutMs = 120000) {
     // Wait for a session monitor link to appear (the live processing link)
     let sessionTab = null;
     try {
         await page.waitForSelector('.session-monitor-link a.monitor-link', { timeout: 15000 });
         await page.waitForTimeout(1000); // Let the link stabilize
         // Get the href of the monitor link
         const monitorLink = page.locator('.session-monitor-link a.monitor-link').last();
         if (await monitorLink.isVisible()) {
             const href = await monitorLink.getAttribute('href');
             await narrate(page,
                 `A live session monitor link appeared for ${taskDescription}. Let\'s open it to watch the AI work in real-time!`,
                 SHORT_PAUSE);
             // Open in a new tab
             const context = page.context();
             sessionTab = await context.newPage();
             const fullUrl = new URL(href, page.url()).toString();
             await sessionTab.goto(fullUrl, { waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => {});
             await narrate(sessionTab,
                 `This is the live session view. We can see the AI processing ${taskDescription} in real-time — prompts, responses, and progress.`,
                 MEDIUM_PAUSE);
             // Scroll down a bit to show content
             await sessionTab.evaluate(() => {
                 window.scrollTo({ top: 300, behavior: 'smooth' });
             }).catch(() => {});
             await sessionTab.waitForTimeout(MEDIUM_PAUSE);
         }
     } catch (e) {
         // No monitor link appeared, that's okay
         console.log(`  (No session monitor link detected for ${taskDescription})`);
     }
     // Now wait for the task to complete (poll on the main page)
     try {
         await page.waitForFunction(() => {
             // Check if any session-completed-link or session-error-link appeared
             const completed = document.querySelector('.session-completed-link');
             const errored = document.querySelector('.session-error-link');
             return completed || errored;
         }, { timeout: timeoutMs });
     } catch (e) {
         console.log(`  (Task ${taskDescription} did not complete within timeout)`);
     }
     // If we opened a session tab, show final state then switch back
     if (sessionTab) {
         try {
             // Refresh the session tab to show final state
             await sessionTab.reload({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => {});
             await sessionTab.waitForTimeout(SHORT_PAUSE);
             // Scroll to bottom to show completion
             await sessionTab.evaluate(() => {
                 window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
             }).catch(() => {});
             await narrate(sessionTab,
                 `The session for ${taskDescription} has finished. Let\'s go back to the main app.`,
                 SHORT_PAUSE);
             // Close the session tab and bring focus back to main page
             await sessionTab.close();
         } catch (e) {
             // If anything goes wrong, just try to close
             await sessionTab.close().catch(() => {});
         }
         // Bring main page to front
         await page.bringToFront();
         await page.waitForTimeout(800);
     }
}