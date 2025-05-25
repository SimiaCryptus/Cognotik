// cypress.config.js
const { defineConfig } = require('cypress');

module.exports = defineConfig({
    e2e: {
        baseUrl: 'http://localhost:7682/',
        supportFile: 'cypress/support/e2e.js',
        specPattern: 'cypress/e2e/**/*.cy.{js,jsx,ts,tsx}',
        // viewportWidth: 1280,
        // viewportHeight: 720,
        viewportWidth: 2560,
        viewportHeight: 1440,

        // Video settings
        // video: true,
        video: false,
        videoCompression: 15, // Lower compression for better quality
        videosFolder: 'cypress/videos', // Relative path
        videoUploadOnPasses: true, // Keep videos even for passing tests
        // Run in headless mode to avoid Cypress UI
        chromeWebSecurity: false,

        // Screenshot settings
        screenshotOnRunFailure: true,
        screenshotsFolder: 'cypress/screenshots',

        // Timeouts
        defaultCommandTimeout: 10000,
        requestTimeout: 10000,
        responseTimeout: 10000,

        // Additional settings for better recording
        trashAssetsBeforeRuns: true,

        setupNodeEvents(on, config) {
            // Set browser launch options to hide Cypress UI elements
            on('before:browser:launch', (browser = {}, launchOptions) => {
                if (browser.name === 'chrome') {
                    launchOptions.args.push('--disable-web-security');
                    launchOptions.args.push('--disable-features=VizDisplayCompositor');
                    launchOptions.args.push('--autoplay-policy=no-user-gesture-required');
                    // Enable audio capture
                    launchOptions.args.push('--use-fake-ui-for-media-stream');
                    launchOptions.args.push('--use-fake-device-for-media-stream');
                    launchOptions.args.push('--allow-running-insecure-content');
                   // Maximize and kiosk mode
                   launchOptions.args.push('--start-maximized');
                   launchOptions.args.push('--kiosk');
                   // Remove robot warning bar and other UI elements
                   launchOptions.args.push('--disable-infobars');
                   launchOptions.args.push('--disable-extensions');
                   launchOptions.args.push('--no-first-run');
                   launchOptions.args.push('--disable-default-apps');
                   launchOptions.args.push('--disable-popup-blocking');
                   launchOptions.args.push('--disable-translate');
                   launchOptions.args.push('--disable-background-timer-throttling');
                   launchOptions.args.push('--disable-renderer-backgrounding');
                   launchOptions.args.push('--disable-backgrounding-occluded-windows');
                   launchOptions.args.push('--disable-component-extensions-with-background-pages');
                }
                return launchOptions;
            });

           
            // Optional: Add video processing events
            on('after:spec', (spec, results) => {
                if (results && results.video) {
                    console.log('Video saved to:', results.video);
                }
            });

            return config;
        }
    },

    // Environment variables
    env: {
        ENABLE_NARRATION: true
    }
});