// cypress.config.js
const { defineConfig } = require('cypress');
const path = require('path');

module.exports = defineConfig({
    e2e: {
        baseUrl: 'http://localhost:7682/',
        supportFile: 'cypress/support/e2e.js',
        specPattern: 'cypress/e2e/**/*.cy.{js,jsx,ts,tsx}',
        viewportWidth: 1280,
        viewportHeight: 720,

        // Video settings
        video: true,
        videoCompression: 1, // Good balance of quality/size
        videosFolder: 'cypress/videos', // Relative path
        videoUploadOnPasses: true, // Keep videos even for passing tests

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