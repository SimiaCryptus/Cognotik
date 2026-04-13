// @ts-check
let defineConfig;
try {
     defineConfig = require('@playwright/test').defineConfig;
} catch {
     // Fallback: export plain object if @playwright/test is not resolvable
     defineConfig = (config) => config;
}

module.exports = defineConfig({
    testMatch: 'demo.js',
    timeout: 1800000,      // 30 minutes — generous for AI generation waits
    use: {
        headless: false,
        launchOptions: {
            slowMo: 80,
            args: ['--start-maximized'],
        },
        viewport: null,    // allow --start-maximized to control the window size
        // Video recording is handled by ffmpeg in demo.js (screen + audio capture).
        // Playwright's built-in video only captures visuals without sound.
        video: 'off',
    },
     outputDir: './demo-results',
});