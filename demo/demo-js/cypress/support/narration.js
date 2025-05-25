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
                
            return cy.window().then((win) => {
                return new Promise((resolve) => {
                    // Create audio element in the page context so it gets captured
                    const audio = win.document.createElement('audio');
                    audio.src = `/audio/${narration.audio}`;
                    audio.preload = 'auto';
                    audio.volume = 1.0;
                    
                    // Add to DOM temporarily (hidden)
                    audio.style.display = 'none';
                    win.document.body.appendChild(audio);
                    this.audioElements.push(audio);
                    
                    audio.onended = () => {
                        // Clean up
                        if (audio.parentNode) {
                            audio.parentNode.removeChild(audio);
                        }
                        const index = this.audioElements.indexOf(audio);
                        if (index > -1) {
                            this.audioElements.splice(index, 1);
                        }
                        resolve();
                    };
                    
                    audio.onerror = (error) => {
                        cy.log(`Failed to play audio for ${key}: ${error.message}`);
                        resolve();
                    };
                    
                    // Play the audio
                    audio.play().catch((error) => {
                        cy.log(`Failed to play audio for ${key}: ${error.message}`);
                        resolve();
                    });
                    
                    // Fallback timeout
                    setTimeout(resolve, 10000);
                });
            });
        }

        // Add delay for reading time if no audio
        if (!narration.audio && options.readingDelay !== false) {
            const readingTime = Math.max(2000, narration.text.length * 50); // ~50ms per character
            cy.wait(readingTime);
        }
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