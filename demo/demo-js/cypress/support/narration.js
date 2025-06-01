// Narration support for Cypress tests
class NarrationManager {
    constructor() {
        this.narrations = {};
        this.audioEnabled = Cypress.env('ENABLE_NARRATION') || false;
       this.narrationsLoaded = false;
        this.audioContext = null;
        this.audioElements = [];
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
    initializeAudioContext() {
        if (!this.audioContext) {
            return cy.window().then((win) => {
                this.audioContext = new win.AudioContext();
                return this.audioContext;
            });
        }
        return cy.wrap(this.audioContext);
    }


    playNarration(key, options = {}) {
        if (!this.audioEnabled) {
            return cy.wrap(null);
        }


        return this.loadNarrations().then(() => {
            const narration = this.narrations[key];
            if (!narration) {
                cy.log(`Narration not found for key: ${key}`);
                return cy.wrap(null);
            }

            // Log the narration text
            cy.log(`🎙️ Narration: ${narration.text}`);

            // If audio file exists, play it
            if (narration.audio) {
                return cy.window().then((win) => {
                    // Create audio element in the page context so it gets captured
                    const audio = win.document.createElement('audio');
                    audio.src = `/audio/${narration.audio}`;
                    audio.preload = 'auto';
                    audio.volume = 1.0;
                    
                    // Add to DOM temporarily (hidden)
                    audio.style.display = 'none';
                    win.document.body.appendChild(audio);
                    this.audioElements.push(audio);

                    // Set up cleanup function
                    const cleanup = () => {
                        if (audio.parentNode) {
                            audio.parentNode.removeChild(audio);
                        }
                        const index = this.audioElements.indexOf(audio);
                        if (index > -1) {
                            this.audioElements.splice(index, 1);
                        }
                    };

                    // Play the audio and handle completion
                    audio.play().catch((error) => {
                        cy.log(`Failed to play audio for ${key}: ${error.message}`);
                        cleanup();
                    });

                    // Use cy.wrap with a promise that resolves when audio ends
                    return cy.wrap(new Promise((resolve) => {
                        audio.onended = () => {
                            cleanup();
                            resolve();
                        };

                        audio.onerror = () => {
                            cleanup();
                            resolve();
                        };

                        // Fallback timeout (reduced from 60s to 30s)
                        setTimeout(() => {
                            cleanup();
                            resolve();
                        }, 30000);
                    }), {timeout: 35000}); // Set explicit timeout for cy.wrap
                });
            } else if (options.readingDelay !== false) {
                const readingTime = Math.max(2000, narration.text.length * 5); // ~50ms per character
                return cy.wait(readingTime);
            }
            return cy.wrap(null);
        });
    }
    cleanup() {
        // Clean up any remaining audio elements
        this.audioElements.forEach(audio => {
            if (audio.parentNode) {
                audio.parentNode.removeChild(audio);
            }
        });
        this.audioElements = [];
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
    return narrationManager.playNarration(key, options);
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