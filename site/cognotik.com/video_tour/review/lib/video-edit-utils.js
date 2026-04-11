#!/usr/bin/env node

/**
 * video-edit-utils.js
 *
 * Shared utility library for ffmpeg-based video editing scripts.
 * No external dependencies — uses only Node.js built-ins.
 *
 * Usage:
 *   const veu = require('./lib/video-edit-utils');
 */

const {execSync} = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");

// ---------------------------------------------------------------------------
// Shell / Command Execution
// ---------------------------------------------------------------------------

/**
 * Run a shell command, logging it to stdout first.
 * Throws on non-zero exit code.
 *
 * @param {string} cmd - The command to execute
 * @param {object} [opts] - Options passed to execSync (default: stdio inherit)
 */
function run(cmd, opts = {}) {
    console.log(`> ${cmd}`);
    execSync(cmd, {stdio: "inherit", ...opts});
}

/**
 * Run a shell command and return its stdout as a trimmed string.
 *
 * @param {string} cmd
 * @returns {string}
 */
function runCapture(cmd) {
    console.log(`> ${cmd}`);
    return execSync(cmd, {encoding: "utf-8"}).trim();
}

// ---------------------------------------------------------------------------
// Filesystem Helpers
// ---------------------------------------------------------------------------

/**
 * Ensure one or more directories exist (recursive mkdir).
 *
 * @param {...string} dirs - Directory paths to create
 */
function ensureDirs(...dirs) {
    for (const dir of dirs) {
        fs.mkdirSync(dir, {recursive: true});
    }
}

/**
 * Remove a directory and all its contents. Logs a warning on failure.
 *
 * @param {string} dirPath
 */
function cleanupDir(dirPath) {
    console.log(`Cleaning up temporary files in ${dirPath}...`);
    try {
        if (fs.existsSync(dirPath)) {
             if (typeof fs.rmSync === "function") {
                 fs.rmSync(dirPath, {recursive: true, force: true});
             } else {
                 // Fallback for Node.js < 14.14
                 fs.rmdirSync(dirPath, {recursive: true});
             }
        }
    } catch (e) {
        console.warn(`Warning: Could not remove temp dir ${dirPath}: ${e.message}`);
    }
}

// ---------------------------------------------------------------------------
// Temp Directory
// ---------------------------------------------------------------------------
/**
 * Create a proper temporary directory under the OS temp folder.
 * Returns the path to the newly created directory.
 *
 * @param {string} [prefix='veu-'] - Prefix for the temp directory name
 * @returns {string} Path to the created temporary directory
 */
function createTempDir(prefix = "veu-") {
    return fs.mkdtempSync(path.join(os.tmpdir(), prefix));
}

// ---------------------------------------------------------------------------
// Timestamp / Timecode Helpers
// ---------------------------------------------------------------------------

/**
 * Convert a timecode string to seconds.
 * Supports "HH:MM:SS", "HH:MM:SS.mmm", "MM:SS", "MM:SS.mmm", or raw number.
 *
 * @param {string|number} tc - Timecode string or numeric seconds
 * @returns {number} Time in seconds
 */
function tcToSeconds(tc) {
    if (typeof tc === "number") return tc;
    const parts = tc.split(":");
    if (parts.length === 3) {
        return (
            parseFloat(parts[0]) * 3600 +
            parseFloat(parts[1]) * 60 +
            parseFloat(parts[2])
        );
    }
    if (parts.length === 2) {
        return parseFloat(parts[0]) * 60 + parseFloat(parts[1]);
    }
    return parseFloat(tc);
}

/**
 * Convert seconds to HH:MM:SS.mmm timecode string.
 *
 * @param {number} sec
 * @returns {string}
 */
function secondsToTc(sec) {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = (sec % 60).toFixed(3);
    return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${s.padStart(6, "0")}`;
}

// ---------------------------------------------------------------------------
// Video Probing
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} VideoInfo
 * @property {number} width
 * @property {number} height
 * @property {number} fps - Frames per second (rounded integer)
 * @property {number} fpsExact - Frames per second (float)
 * @property {number} sampleRate - Audio sample rate (default 44100)
 * @property {number} channels - Audio channel count (default 2)
 * @property {string} channelLayout - "stereo" or "mono"
 */

/**
 * Probe a video file for resolution, frame rate, and audio properties.
 *
 * @param {string} inputPath - Path to the video file
 * @returns {VideoInfo}
 */
function probeVideo(inputPath) {
    // Video stream
    const videoProbe = runCapture(
        `ffprobe -v error -select_streams v:0 ` +
        `-show_entries stream=width,height,r_frame_rate -of csv=p=0 "${inputPath}"`
    );
    const videoParts = videoProbe.split(",");
    const width = parseInt(videoParts[0], 10);
    const height = parseInt(videoParts[1], 10);
    const [fpsNum, fpsDen] = videoParts[2].split("/").map(Number);
    const fpsExact = fpsNum / fpsDen;
    const fps = Math.round(fpsExact);

    // Audio stream
    let sampleRate = 44100;
    let channels = 2;
    try {
        const audioProbe = runCapture(
            `ffprobe -v error -select_streams a:0 ` +
            `-show_entries stream=sample_rate,channels -of csv=p=0 "${inputPath}"`
        );
        const audioParts = audioProbe.split(",");
        if (audioParts[0]) sampleRate = parseInt(audioParts[0], 10);
        if (audioParts[1]) channels = parseInt(audioParts[1], 10);
    } catch (e) {
        console.log("Could not probe audio, using defaults (44100 Hz, stereo)");
    }

    const channelLayout = channels === 1 ? "mono" : "stereo";

    return {width, height, fps, fpsExact, sampleRate, channels, channelLayout};
}

// ---------------------------------------------------------------------------
// FFmpeg Filter Helpers
// ---------------------------------------------------------------------------

/**
 * Build an atempo filter chain for a given speed factor.
 * The atempo filter only accepts values between 0.5 and 100.0.
 * For values > 2.0, multiple atempo filters are chained.
 *
 * @param {number} speed - Desired playback speed multiplier (e.g., 4.0 = 4× faster)
 * @returns {string} Comma-separated atempo filter chain (e.g., "atempo=2.0,atempo=2.0")
 */
function buildAtempoChain(speed) {
    const filters = [];
    let remaining = speed;
    while (remaining > 2.0) {
        filters.push("atempo=2.0");
        remaining /= 2.0;
    }
    if (remaining < 0.5) remaining = 0.5;
    filters.push(`atempo=${remaining.toFixed(4)}`);
    return filters.join(",");
}

/**
 * Escape text for use in ffmpeg drawtext filter.
 * Handles single quotes and colons.
 *
 * @param {string} text
 * @returns {string}
 */
function escapeDrawtext(text) {
    return text
        .replace(/\\/g, "\\\\")
        .replace(/'/g, "'\\''")
        .replace(/:/g, "\\:");
}

/**
 * Build a drawtext filter string for centered text.
 *
 * @param {string} text - The text to display
 * @param {object} [opts] - Options
 * @param {number} [opts.fontsize=48] - Font size
 * @param {string} [opts.fontcolor='white'] - Font color
 * @param {string} [opts.font='Arial'] - Font family
 * @param {number} [opts.yOffset=0] - Vertical offset from center (positive = down)
 * @param {number} [opts.borderw=0] - Border width
 * @param {string} [opts.bordercolor='black'] - Border color
 * @returns {string} drawtext filter string
 */
function drawTextCentered(text, opts = {}) {
    const {
        fontsize = 48,
        fontcolor = "white",
        font = "Arial",
        yOffset = 0,
        borderw = 0,
        bordercolor = "black",
    } = opts;
    const escaped = escapeDrawtext(text);
    const yExpr = yOffset === 0 ? "(h-text_h)/2" : `(h-text_h)/2+${yOffset}`;
    let filter = `drawtext=text='${escaped}':fontcolor=${fontcolor}:fontsize=${fontsize}:x=(w-text_w)/2:y=${yExpr}:font=${font}`;
    if (borderw > 0) {
        filter += `:borderw=${borderw}:bordercolor=${bordercolor}`;
    }
    return filter;
}

// ---------------------------------------------------------------------------
// Billboard / Title Card Generation
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} BillboardLine
 * @property {string} text
 * @property {number} [fontsize]
 * @property {string} [fontcolor]
 * @property {number} [yOffset] - Vertical offset from center
 */

/**
 * @typedef {Object} BillboardOptions
 * @property {number} duration - Duration in seconds
 * @property {number} width - Video width
 * @property {number} height - Video height
 * @property {number} [fps=30] - Frame rate
 * @property {number} [sampleRate=44100] - Audio sample rate
 * @property {string} [channelLayout='stereo'] - Audio channel layout
 * @property {number} [fadeIn=0.5] - Fade-in duration in seconds
 * @property {number} [fadeOut=0.5] - Fade-out duration in seconds
 * @property {string} [bgColor='black'] - Background color
 * @property {BillboardLine[]} lines - Text lines to render
 */

/**
 * Generate a billboard/title card video with text on a solid background.
 * Includes silent audio track for seamless concatenation.
 *
 * @param {string} outputPath - Output file path
 * @param {BillboardOptions} opts
 */
function generateBillboard(outputPath, opts) {
    const {
        duration,
        width,
        height,
        fps = 30,
        sampleRate = 44100,
        channelLayout = "stereo",
        fadeIn = 0.5,
        fadeOut = 0.5,
        bgColor = "black",
        lines = [],
    } = opts;

    // Build drawtext filter chain for all lines
    const textFilters = lines.map((line) => {
        const fontSize = line.fontsize || Math.round(height / 15);
        const fontColor = line.fontcolor || "white";
        const yOff = line.yOffset || 0;
        return drawTextCentered(line.text, {
            fontsize: fontSize,
            fontcolor: fontColor,
            yOffset: yOff,
        });
    });

    // Build video filter: text overlays first, then fade in/out on top
    // Order matters: drawtext first so the text is part of the faded image
    const vfParts = [];
    vfParts.push(...textFilters);
    if (fadeIn > 0) vfParts.push(`fade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) vfParts.push(`fade=t=out:st=${(duration - fadeOut).toFixed(3)}:d=${fadeOut}`);

    const vf = vfParts.length > 0 ? `-vf "${vfParts.join(",")}"` : "";

    // Build audio filter: fade in/out on silence
    const afParts = [];
    if (fadeIn > 0) afParts.push(`afade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) afParts.push(`afade=t=out:st=${(duration - fadeOut).toFixed(3)}:d=${fadeOut}`);
    const af = afParts.length > 0 ? `-af "${afParts.join(",")}"` : "";

    run(
        `ffmpeg -y -f lavfi -i color=c=${bgColor}:s=${width}x${height}:d=${duration}:r=${fps} ` +
        `-f lavfi -i anullsrc=r=${sampleRate}:cl=${channelLayout} ` +
        `${vf} ${af} ` +
        `-t ${duration} -c:v libx264 -preset fast -pix_fmt yuv420p -c:a aac -b:a 128k -shortest ` +
        `"${outputPath}"`
    );
}

/**
 * Convenience: generate a standard intro billboard.
 *
 * @param {string} outputPath
 * @param {string} title - Main title text
 * @param {string} [subtitle] - Optional subtitle
 * @param {object} videoInfo - From probeVideo()
 * @param {number} [duration=3]
 * @param {object} [fadeOpts] - Override fade durations
 * @param {number} [fadeOpts.fadeIn=0.5]
 * @param {number} [fadeOpts.fadeOut=0.5]
 */
function generateIntroBillboard(outputPath, title, subtitle, videoInfo, duration = 3, fadeOpts = {}) {
    const lines = [
        {
            text: title,
            fontsize: Math.round(videoInfo.height / 12),
            fontcolor: "white",
            yOffset: subtitle ? -Math.round(videoInfo.height / 25) : 0,
        },
    ];
    if (subtitle) {
        lines.push({
            text: subtitle,
            fontsize: Math.round(videoInfo.height / 20),
            fontcolor: "0xCCCCCC",
            yOffset: Math.round(videoInfo.height / 15),
        });
    }
    generateBillboard(outputPath, {
        duration,
        width: videoInfo.width,
        height: videoInfo.height,
        fps: videoInfo.fps,
        sampleRate: videoInfo.sampleRate,
        channelLayout: videoInfo.channelLayout,
        fadeIn: fadeOpts.fadeIn !== undefined ? fadeOpts.fadeIn : 0.5,
        fadeOut: fadeOpts.fadeOut !== undefined ? fadeOpts.fadeOut : 0.5,
        lines,
    });
}

/**
 * Convenience: generate a standard outro billboard.
 *
 * @param {string} outputPath
 * @param {string[]} textLines - Array of text lines (first is largest)
 * @param {object} videoInfo - From probeVideo()
 * @param {number} [duration=4]
 * @param {object} [fadeOpts] - Override fade durations
 * @param {number} [fadeOpts.fadeIn=0.5]
 * @param {number} [fadeOpts.fadeOut=0.5]
 */
function generateOutroBillboard(outputPath, textLines, videoInfo, duration = 4, fadeOpts = {}) {
    const lineSpacing = Math.round(videoInfo.height / 12);
    const totalHeight = (textLines.length - 1) * lineSpacing;
    const startY = -Math.round(totalHeight / 2);

    const lines = textLines.map((text, i) => ({
        text,
        fontsize: Math.round(videoInfo.height / (12 + i * 4)),
        fontcolor: i === 0 ? "white" : i === 1 ? "0xAAAAAA" : "0x888888",
        yOffset: startY + i * lineSpacing,
    }));

    generateBillboard(outputPath, {
        duration,
        width: videoInfo.width,
        height: videoInfo.height,
        fps: videoInfo.fps,
        sampleRate: videoInfo.sampleRate,
        channelLayout: videoInfo.channelLayout,
        fadeIn: fadeOpts.fadeIn !== undefined ? fadeOpts.fadeIn : 0.5,
        fadeOut: fadeOpts.fadeOut !== undefined ? fadeOpts.fadeOut : 0.5,
        lines,
    });
}

// ---------------------------------------------------------------------------
// Segment Processing
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} Segment
 * @property {string} id - Unique segment identifier
 * @property {string} start - Start timecode (HH:MM:SS.mmm)
 * @property {string} end - End timecode (HH:MM:SS.mmm)
 * @property {string} action - 'keep' | 'cut' | 'compress'
 * @property {string} [label] - Human-readable description
 * @property {number} [targetDuration] - For 'compress': desired output duration in seconds
 * @property {number} [speed] - For 'compress': alternative to targetDuration — speed multiplier
 * @property {string} [overlay] - For 'compress': overlay text to display
 * @property {boolean} [muteAudio] - For 'compress': replace audio with silence (default true)
 * @property {number} [fadeIn] - Fade-in duration in seconds (overrides default)
 * @property {number} [fadeOut] - Fade-out duration in seconds (overrides default)
 */

/**
 * Extract a "keep" segment from the source video.
 *
 * @param {string} inputPath - Source video path
 * @param {string} outputPath - Output segment path
 * @param {string} start - Start timecode
 * @param {string} end - End timecode
 * @param {object} [opts]
 * @param {number} [opts.fps] - Target frame rate
 * @param {number} [opts.sampleRate=44100]
 * @param {number} [opts.channels=2]
 * @param {number} [opts.crf=18]
 * @param {number} [opts.fadeIn=0] - Fade-in duration in seconds (0 = no fade)
 * @param {number} [opts.fadeOut=0] - Fade-out duration in seconds (0 = no fade)
 */
function extractSegment(inputPath, outputPath, start, end, opts = {}) {
    const {fps, sampleRate = 44100, channels = 2, crf = 18, fadeIn = 0, fadeOut = 0} = opts;

    const segDuration = tcToSeconds(end) - tcToSeconds(start);

    const vfParts = [];
    if (fps) vfParts.push(`fps=${fps}`);
    vfParts.push("format=yuv420p");
    if (fadeIn > 0) vfParts.push(`fade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) vfParts.push(`fade=t=out:st=${(segDuration - fadeOut).toFixed(3)}:d=${fadeOut}`);
    const vf = vfParts.length > 0 ? `-vf "${vfParts.join(",")}"` : "";
    const afParts = [];
    if (fadeIn > 0) afParts.push(`afade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) afParts.push(`afade=t=out:st=${(segDuration - fadeOut).toFixed(3)}:d=${fadeOut}`);
    const af = afParts.length > 0 ? `-af "${afParts.join(",")}"` : "";


    run(
        `ffmpeg -y -i "${inputPath}" -ss ${start} -to ${end} ` +
        `${vf} ${af} ` +
        `-c:v libx264 -preset fast -crf ${crf} -pix_fmt yuv420p ` +
        `-c:a aac -ar ${sampleRate} -ac ${channels} -b:a 128k ` +
        `"${outputPath}"`
    );
}

/**
 * Extract and time-compress a segment, replacing audio with silence.
 *
 * @param {string} inputPath - Source video path
 * @param {string} outputPath - Output segment path
 * @param {string} start - Start timecode
 * @param {string} end - End timecode
 * @param {object} opts
 * @param {number} [opts.speed] - Speed multiplier (e.g., 4 = 4× faster). Provide speed OR targetDuration.
 * @param {number} [opts.targetDuration] - Desired output duration in seconds. Overrides speed.
 * @param {string} [opts.overlay] - Text to overlay during compressed segment
 * @param {boolean} [opts.muteAudio=true] - Replace audio with silence
 * @param {number} [opts.fps] - Target frame rate
 * @param {number} [opts.sampleRate=44100]
 * @param {string} [opts.channelLayout='stereo']
 * @param {number} [opts.crf=18]
 */
function extractCompressedSegment(inputPath, outputPath, start, end, opts = {}) {
    const {
        overlay,
        muteAudio = true,
        fps,
        sampleRate = 44100,
        channelLayout = "stereo",
        crf = 18,
    } = opts;

    const startSec = tcToSeconds(start);
    const endSec = tcToSeconds(end);
    const originalDuration = endSec - startSec;

    // Determine speed factor
    let speed = opts.speed;
    let targetDuration = opts.targetDuration;
    if (targetDuration) {
        speed = originalDuration / targetDuration;
    } else if (speed) {
        targetDuration = originalDuration / speed;
    } else {
        throw new Error("extractCompressedSegment requires either speed or targetDuration");
    }

    const setptsVal = (1.0 / speed).toFixed(6);

    // Build video filter
    const vfParts = [`setpts=${setptsVal}*PTS`];
    if (fps) vfParts.push(`fps=${fps}`);
    vfParts.push("format=yuv420p");
    if (overlay) {
        vfParts.push(
            drawTextCentered(overlay, {
                fontsize: 28,
                fontcolor: "white",
                borderw: 2,
                bordercolor: "black",
                yOffset: 0, // We'll use a custom y for bottom positioning
            }).replace(/y=\(h-text_h\)\/2\+0/, "y=h-60")
        );
    }

    // Determine fade durations (passed through from processSegments or caller)
    const fadeIn = opts.fadeIn || 0;
    const fadeOut = opts.fadeOut || 0;

    if (fadeIn > 0) vfParts.push(`fade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) {
        vfParts.push(`fade=t=out:st=${(targetDuration - fadeOut).toFixed(3)}:d=${fadeOut}`);
    }

    // Build audio filter for fades on the silent/sped-up audio
    const afParts = [];
    if (fadeIn > 0) afParts.push(`afade=t=in:st=0:d=${fadeIn}`);
    if (fadeOut > 0) {
        afParts.push(`afade=t=out:st=${(targetDuration - fadeOut).toFixed(3)}:d=${fadeOut}`);
    }

    if (muteAudio) {
        // Replace audio with silence
        run(
            `ffmpeg -y -i "${inputPath}" -ss ${start} -to ${end} ` +
            `-f lavfi -i anullsrc=r=${sampleRate}:cl=${channelLayout} ` +
            `-vf "${vfParts.join(",")}" ` +
            `-map 0:v -map 1:a ` +
            `-t ${targetDuration.toFixed(3)} ` +
            `-c:v libx264 -preset fast -crf ${crf} -pix_fmt yuv420p ` +
            `-c:a aac -b:a 128k -shortest ` +
            `"${outputPath}"`
        );
    } else {
        // Keep audio but speed it up
        const atempoChain = buildAtempoChain(speed);
        const fullAf = afParts.length > 0
            ? `${atempoChain},${afParts.join(",")}`
            : atempoChain;
        run(
            `ffmpeg -y -i "${inputPath}" -ss ${start} -to ${end} ` +
            `-vf "${vfParts.join(",")}" ` +
            `-af "${fullAf}" ` +
            `-c:v libx264 -preset fast -crf ${crf} -pix_fmt yuv420p ` +
            `-c:a aac -ar ${sampleRate} -b:a 128k ` +
            `"${outputPath}"`
        );
    }
}

/**
 * Process an array of segment definitions and return an array of output file paths.
 * Segments with action 'cut' are skipped.
 *
 * @param {Segment[]} segments - Array of segment definitions
 * @param {string} inputPath - Source video path
 * @param {string} tempDir - Directory for temporary segment files
 * @param {object} [opts] - Default options passed to extract functions
 * @param {number} [opts.fps]
 * @param {number} [opts.sampleRate=44100]
 * @param {number} [opts.channels=2]
 * @param {string} [opts.channelLayout='stereo']
 * @param {number} [opts.transitionDuration=0] - Default cross-fade duration between segments (seconds)
 * @returns {string[]} Array of output file paths (only for non-cut segments)
 */
function processSegments(segments, inputPath, tempDir, opts = {}) {
    const outputFiles = [];
    let idx = 0;
    // Filter to only actionable (non-cut) segments to determine neighbors for transitions
    const actionable = segments.filter(s => s.action !== "cut");
    const transitionDur = opts.transitionDuration || 0;
    let actionableIdx = 0;


    for (const seg of segments) {
        if (seg.action === "cut") {
            console.log(`\n--- CUT: ${seg.label || seg.id} (${seg.start} - ${seg.end}) ---`);
            continue;
        }

        const id = seg.id || `seg_${String(idx).padStart(3, "0")}`;
        const outFile = path.join(tempDir, `${id}.mp4`);
        // Determine fade durations for this segment.
        // Only the first actionable segment gets a fadeIn (from the intro billboard),
        // and only the last actionable segment gets a fadeOut (into the outro billboard).
        // Interior segments should NOT fade to black — that creates visible black gaps
        // since we use simple concatenation, not crossfade.
        const isFirst = actionableIdx === 0;
        const isLast = actionableIdx === actionable.length - 1;
        const segFadeIn = seg.fadeIn !== undefined ? seg.fadeIn : (isFirst ? transitionDur : 0);
        const segFadeOut = seg.fadeOut !== undefined ? seg.fadeOut : (isLast ? transitionDur : 0);


        if (seg.action === "keep") {
            console.log(`\n--- KEEP: ${seg.label || id} (${seg.start} - ${seg.end}) ---`);
            extractSegment(inputPath, outFile, seg.start, seg.end, {
                fps: opts.fps,
                sampleRate: opts.sampleRate || 44100,
                channels: opts.channels || 2,
                fadeIn: segFadeIn,
                fadeOut: segFadeOut,
            });
        } else if (seg.action === "compress") {
            const target = seg.targetDuration
                ? `${seg.targetDuration}s`
                : `${seg.speed}×`;
            console.log(`\n--- COMPRESS: ${seg.label || id} (${seg.start} - ${seg.end}) -> ${target} ---`);
            extractCompressedSegment(inputPath, outFile, seg.start, seg.end, {
                speed: seg.speed,
                targetDuration: seg.targetDuration,
                overlay: seg.overlay,
                muteAudio: seg.muteAudio !== false,
                fps: opts.fps,
                sampleRate: opts.sampleRate || 44100,
                channelLayout: opts.channelLayout || "stereo",
                fadeIn: segFadeIn,
                fadeOut: segFadeOut,
            });
        }

        outputFiles.push(outFile);
        idx++;
        actionableIdx++;
    }

    return outputFiles;
}

// ---------------------------------------------------------------------------
// Concatenation
// ---------------------------------------------------------------------------

/**
 * Build keep-segments from a time range by subtracting cut ranges.
 * Useful when you define what to remove rather than what to keep.
 *
 * @param {number} start - Start time in seconds
 * @param {number} end - End time in seconds
 * @param {{from: number, to: number}[]} cuts - Array of cut ranges in seconds
 * @returns {{from: number, to: number}[]} Array of keep ranges
 */
function buildKeepSegments(start, end, cuts) {
    const sorted = [...cuts].sort((a, b) => a.from - b.from);
    const segments = [];
    let cursor = start;

    for (const cut of sorted) {
        if (cut.from > cursor) {
            segments.push({from: cursor, to: cut.from});
        }
        cursor = Math.max(cursor, cut.to);
    }
    if (cursor < end) {
        segments.push({from: cursor, to: end});
    }
    return segments;
}

/**
 * Concatenate video files using the concat demuxer.
 *
 * @param {string[]} inputFiles - Array of file paths to concatenate
 * @param {string} outputPath - Output file path
 * @param {string} tempDir - Directory for the concat list file
 * @param {object} [opts]
 * @param {boolean} [opts.reencode=true] - Re-encode during concat (safer but slower)
 * @param {number} [opts.crf=18]
 * @param {number} [opts.sampleRate=44100]
 * @param {number} [opts.channels=2]
 * @param {boolean} [opts.faststart=true] - Add faststart flag for web playback
 */
function concatenateVideos(inputFiles, outputPath, tempDir, opts = {}) {
    const {
        reencode = true,
        crf = 18,
        sampleRate = 44100,
        channels = 2,
        faststart = true,
    } = opts;

    const concatListPath = path.join(tempDir, "concat_list.txt");
    const concatContent = inputFiles
        .map((f) => `file '${f.replace(/\\/g, "/")}'`)
        .join("\n");
    fs.writeFileSync(concatListPath, concatContent, "utf-8");
    console.log("Concat list:\n" + concatContent);

    const movflags = faststart ? "-movflags +faststart" : "";

    if (reencode) {
        run(
            `ffmpeg -y -f concat -safe 0 -i "${concatListPath}" ` +
            `-c:v libx264 -preset fast -crf ${crf} -pix_fmt yuv420p ` +
            `-c:a aac -ar ${sampleRate} -ac ${channels} -b:a 128k ` +
            `${movflags} "${outputPath}"`
        );
    } else {
        run(
            `ffmpeg -y -f concat -safe 0 -i "${concatListPath}" ` +
            `-c copy ${movflags} "${outputPath}"`
        );
    }
}

/**
 * Concatenate using the MPEG-TS intermediate format (protocol-level concat).
 * Useful when the concat demuxer has issues with format mismatches.
 *
 * @param {string[]} inputFiles - Array of file paths to concatenate
 * @param {string} outputPath - Output file path
 * @param {string} tempDir - Directory for temporary .ts files
 * @param {object} [opts]
 * @param {number} [opts.fps=30]
 * @param {number} [opts.width=1920]
 * @param {number} [opts.height=1080]
 * @param {number} [opts.sampleRate=44100]
 * @param {number} [opts.channels=2]
 */
function concatenateViaMpegTs(inputFiles, outputPath, tempDir, opts = {}) {
    const {
        fps = 30,
        width = 1920,
        height = 1080,
        sampleRate = 44100,
        channels = 2,
    } = opts;

    const tsFiles = [];
    inputFiles.forEach((f, i) => {
        const tsFile = path.join(tempDir, `norm_${String(i).padStart(3, "0")}.ts`);
        run(
            `ffmpeg -y -i "${f}" ` +
            `-c:v libx264 -pix_fmt yuv420p -r ${fps} ` +
            `-c:a aac -ar ${sampleRate} -ac ${channels} -b:a 128k ` +
            `-vf "scale=${width}:${height}:force_original_aspect_ratio=decrease,pad=${width}:${height}:(ow-iw)/2:(oh-ih)/2" ` +
            `-bsf:v h264_mp4toannexb -f mpegts "${tsFile}"`
        );
        tsFiles.push(tsFile);
    });

    const concatInput = tsFiles.join("|");
    run(
        `ffmpeg -y -i "concat:${concatInput}" ` +
        `-c:v libx264 -pix_fmt yuv420p ` +
        `-c:a aac -ar ${sampleRate} -ac ${channels} ` +
        `-movflags +faststart "${outputPath}"`
    );
}

// ---------------------------------------------------------------------------
// Audio Normalization
// ---------------------------------------------------------------------------

/**
 * Apply loudnorm audio normalization to a video file.
 * Video stream is copied without re-encoding.
 *
 * @param {string} inputPath
 * @param {string} outputPath
 * @param {object} [opts]
 * @param {number} [opts.targetI=-16] - Integrated loudness target (LUFS)
 * @param {number} [opts.targetTP=-1.5] - True peak target (dBTP)
 * @param {number} [opts.targetLRA=11] - Loudness range target (LU)
 */
function normalizeAudio(inputPath, outputPath, opts = {}) {
    const {targetI = -16, targetTP = -1.5, targetLRA = 11} = opts;
    ensureDirs(path.dirname(outputPath));
    run(
        `ffmpeg -y -i "${inputPath}" ` +
        `-c:v copy ` +
        `-af loudnorm=I=${targetI}:TP=${targetTP}:LRA=${targetLRA} ` +
        `-c:a aac -b:a 128k "${outputPath}"`
    );
}

// ---------------------------------------------------------------------------
// High-Level Pipeline
// ---------------------------------------------------------------------------

/**
 * @typedef {Object} EditPipelineOptions
 * @property {string} inputPath - Source video file
 * @property {string} outputPath - Final output file
 * @property {string} [tempDir] - Temporary working directory (default: auto-created in OS temp folder)
 * @property {string} [introTitle] - Intro billboard title
 * @property {string} [introSubtitle] - Intro billboard subtitle
 * @property {number} [introDuration=3] - Intro duration in seconds
 * @property {string[]} [outroLines] - Outro billboard text lines
 * @property {number} [outroDuration=4] - Outro duration in seconds
 * @property {Segment[]} segments - Segment definitions
 * @property {boolean} [normalizeAudio=false] - Apply loudnorm after concat
 * @property {'demuxer'|'mpegts'} [concatMethod='demuxer'] - Concatenation method
 * @property {number} [transitionDuration=0.5] - Default fade duration between segments (seconds)
 */

/**
 * Run a complete edit pipeline: probe → billboards → segments → concat → cleanup.
 *
 * @param {EditPipelineOptions} config
 */
function runEditPipeline(config) {
    const {
        inputPath,
        outputPath,
        introTitle,
        introSubtitle,
        introDuration = 3,
        outroLines,
        outroDuration = 4,
        segments,
        normalizeAudio: doNormalize = false,
        concatMethod = "demuxer",
        transitionDuration = 0.5,
    } = config;
    // Use provided tempDir or create a proper OS-level temp directory
    const tempDir = config.tempDir || createTempDir();


    const outputDir = path.dirname(outputPath);
    ensureDirs(outputDir, tempDir);

    // Validate input
    if (!fs.existsSync(inputPath)) {
        console.error(`ERROR: Input file not found: ${inputPath}`);
        process.exit(1);
    }

    // Probe
    console.log("=== Probing source video ===");
    const videoInfo = probeVideo(inputPath);
    console.log(`Source: ${videoInfo.width}x${videoInfo.height} @ ${videoInfo.fps}fps, ${videoInfo.sampleRate}Hz ${videoInfo.channelLayout}`);

    const partFiles = [];

    // Intro billboard
    if (introTitle) {
        console.log("\n=== Generating intro billboard ===");
        const introPath = path.join(tempDir, "billboard_intro.mp4");
        generateIntroBillboard(introPath, introTitle, introSubtitle, videoInfo, introDuration, {
            fadeIn: 0.5,
            fadeOut: Math.max(0.5, transitionDuration),
        });
        partFiles.push(introPath);
    }

    // Process segments
    console.log("\n=== Processing segments ===");
    const segmentFiles = processSegments(segments, inputPath, tempDir, {
        fps: videoInfo.fps,
        sampleRate: videoInfo.sampleRate,
        channels: videoInfo.channels,
        channelLayout: videoInfo.channelLayout,
        transitionDuration,
    });
    partFiles.push(...segmentFiles);

    // Outro billboard
    if (outroLines && outroLines.length > 0) {
        console.log("\n=== Generating outro billboard ===");
        const outroPath = path.join(tempDir, "billboard_outro.mp4");
        generateOutroBillboard(outroPath, outroLines, videoInfo, outroDuration, {
            fadeIn: Math.max(0.5, transitionDuration),
            fadeOut: 0.5,
        });
        partFiles.push(outroPath);
    }

    // Concatenate
    console.log("\n=== Concatenating ===");
    const concatOutput = doNormalize
        ? path.join(tempDir, "concat_raw.mp4")
        : outputPath;

    if (concatMethod === "mpegts") {
        concatenateViaMpegTs(partFiles, concatOutput, tempDir, {
            fps: videoInfo.fps,
            width: videoInfo.width,
            height: videoInfo.height,
            sampleRate: videoInfo.sampleRate,
            channels: videoInfo.channels,
        });
    } else {
        concatenateVideos(partFiles, concatOutput, tempDir, {
            sampleRate: videoInfo.sampleRate,
            channels: videoInfo.channels,
        });
    }

    // Audio normalization
    if (doNormalize) {
        console.log("\n=== Normalizing audio ===");
        const normalizedTemp = path.join(tempDir, "normalized_final.mp4");
        ensureDirs(path.dirname(outputPath));
        normalizeAudio(concatOutput, normalizedTemp);
        // Copy from temp to final destination (handles cross-filesystem/WSL mounts)
        console.log(`Copying result to ${outputPath}...`);
        ensureDirs(path.dirname(outputPath));
        try {
            fs.copyFileSync(normalizedTemp, outputPath);
        } catch (copyErr) {
            console.warn(`fs.copyFileSync failed (${copyErr.message}), trying buffer read/write...`);
            try {
                const buf = fs.readFileSync(normalizedTemp);
                fs.writeFileSync(outputPath, buf);
            } catch (bufErr) {
                console.warn(`Buffer copy failed (${bufErr.message}), falling back to shell cp...`);
                try {
                    run(`cp "${normalizedTemp}" "${outputPath}"`);
                } catch (cpErr) {
                    console.warn(`Shell cp failed (${cpErr.message}), falling back to ffmpeg copy...`);
                    // Write to a local temp file first, then move
                    const localTemp = path.join(path.dirname(outputPath), '_tmp_final_' + Date.now() + '.mp4');
                    try {
                        run(
                            `ffmpeg -y -i "${normalizedTemp}" -c copy -movflags +faststart "${localTemp}"`
                        );
                        fs.renameSync(localTemp, outputPath);
                    } catch (ffErr) {
                        // Last resort: pipe via dd or cat
                        console.warn(`ffmpeg copy failed (${ffErr.message}), trying cat...`);
                        run(`cat "${normalizedTemp}" > "${outputPath}"`);
                    }
                }
            }
        }
    } else {
        // If concat wrote directly to outputPath on a mount, it may also need copying
        // but concatOutput === outputPath in this branch, so nothing to do
    }

    // Cleanup
    cleanupDir(tempDir);

    console.log(`\n=== Done! Output: ${outputPath} ===`);
}

// ---------------------------------------------------------------------------
// Exports
// ---------------------------------------------------------------------------

module.exports = {
    // Shell
    run,
    runCapture,

    // Filesystem
    ensureDirs,
    cleanupDir,
    createTempDir,

    // Timecodes
    tcToSeconds,
    secondsToTc,

    // Probing
    probeVideo,

    // Filters
    buildAtempoChain,
    escapeDrawtext,
    drawTextCentered,

    // Billboards
    generateBillboard,
    generateIntroBillboard,
    generateOutroBillboard,

    // Segments
    extractSegment,
    extractCompressedSegment,
    processSegments,

    // Concatenation
    buildKeepSegments,
    concatenateVideos,
    concatenateViaMpegTs,

    // Audio
    normalizeAudio,

    // Pipeline
    runEditPipeline,
};