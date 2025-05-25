// Narration support for Cypress tests
class NarrationManager {
    constructor() {
        this.narrations = {};
        this.audioEnabled = Cypress.env('ENABLE_NARRATION') || false;
       this.narrationsLoaded = false;
    }

    loadNarrations() {
       if (!this.narrationsLoaded) {
           return cy.fixture('narrations.json').then((data) => {
               this.narrations = data;
               this.narrationsLoaded = true;
               return data;
           });
       }
       return cy.wrap(this.narrations);
    }

    async playNarration(key, options = {}) {
        if (!this.audioEnabled) {
            return;
        }
       // Ensure narrations are loaded before proceeding
       if (!this.narrationsLoaded) {
           await this.loadNarrations();
       }


        const narration = this.narrations[key];
        if (!narration) {
            cy.log(`Narration not found for key: ${key}`);
            return;
        }

        // Log the narration text
        cy.log(`🎙️ Narration: ${narration.text}`);

        // If audio file exists, play it
        if (narration.audio) {
            try {
               const audio = new Audio(`/audio/${narration.audio}`);
                await audio.play();
                
                // Wait for audio to finish if specified
                if (options.waitForCompletion !== false) {
                    await new Promise(resolve => {
                        audio.onended = resolve;
                    });
                }
            } catch (error) {
                cy.log(`Failed to play audio for ${key}: ${error.message}`);
            }
        }

        // Add delay for reading time if no audio
        if (!narration.audio && options.readingDelay !== false) {
            const readingTime = Math.max(2000, narration.text.length * 50); // ~50ms per character
            cy.wait(readingTime);
        }
    }

    logNarration(key) {
       // Ensure narrations are loaded before proceeding
       if (!this.narrationsLoaded) {
           return this.loadNarrations().then(() => {
               const narration = this.narrations[key];
               if (narration) {
                   cy.log(`📖 ${narration.text}`);
               }
           });
       }

        const narration = this.narrations[key];
        if (narration) {
            cy.log(`📖 ${narration.text}`);
        }
    }
}

// Create global instance
const narrationManager = new NarrationManager();

// Add Cypress commands
Cypress.Commands.add('narrate', (key, options = {}) => {
   return narrationManager.loadNarrations().then(() => {
       return narrationManager.playNarration(key, options);
   });
});

Cypress.Commands.add('logNarration', (key) => {
   return narrationManager.loadNarrations().then(() => {
       const narration = narrationManager.narrations[key];
       if (narration) {
           cy.log(`📖 ${narration.text}`);
       }
   });
});

export default narrationManager;