// cypress/e2e/performance.cy.js
describe('Performance Tests', () => {
    it('should load welcome page within acceptable time', () => {
        const start = performance.now();

        cy.visit('/welcome');
        cy.get('#cognitive-mode').should('be.visible');

        cy.then(() => {
            const loadTime = performance.now() - start;
            expect(loadTime).to.be.lessThan(3000); // 3 seconds max
        });
    });

    it('should handle large number of API providers efficiently', () => {
        cy.visit('/welcome');

        // Measure time to populate API settings
        cy.window().then((win) => {
            const start = performance.now();
            win.initializeApiSettings();
            const duration = performance.now() - start;
            expect(duration).to.be.lessThan(100); // 100ms max
        });
    });

    it('should handle model population efficiently', () => {
        cy.visit('/welcome');

        cy.window().then((win) => {
            const mockApiSettings = {
                apiKeys: Object.fromEntries(
                    win.apiProviders.map(p => [p.id, 'test-key'])
                )
            };

            const start = performance.now();
            win.populateModelSelections(mockApiSettings, win.taskSettings);
            const duration = performance.now() - start;
            expect(duration).to.be.lessThan(200); // 200ms max
        });
    });
});