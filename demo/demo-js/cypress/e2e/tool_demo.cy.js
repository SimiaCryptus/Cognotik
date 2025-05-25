// cypress/e2e/complete-workflow.cy.js
describe('Cognotik App Launcher', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
    });

    it('should launch the chat-task app', () => {

        // Step 2: Choose Cognitive Mode
        cy.get('#single-task-mode').check();
        cy.get('#next-to-task-settings').click();

        // Step 3: Configure Task Settings
        cy.get('#model-selection').select('Claude 3.5 Haiku (Anthropic)');
        cy.get('#parsing-model').select('Claude 3.5 Haiku (Anthropic)');
        cy.get('#generate-working-dir').click()
        cy.get('#temperature').invoke('val', 0.2).trigger('input');
        cy.get('#temperature-value').should('contain', '0.2');
        cy.get('#auto-fix').check();
        cy.get('#next-to-task-selection').click();

        // Step 4: Select Tasks
        cy.get('#task-FileModificationTask').check();
        cy.get('#next-to-launch').click();

        // Step 5: Review and Launch
        cy.get('#cognitive-mode-summary').should('contain', 'Chat');
        cy.get('#api-settings-summary').should('contain', 'Anthropic: Configured');
        cy.get('#launch-session').click();
        cy.url().should('include', '/taskChat/#');

        // Step 6: Enter a request
        cy.get('[data-testid="chat-input"]').type("Create a new file called test.txt and write a short story in it");
        cy.get('[data-testid="send-button"]').click();
        cy.get('[data-testid="collapse-input"]').click();

        // Wait for the response to be generated
        cy.get('.tab-button').contains('Output', { timeout: 300000 }).wait(1000).click();
        cy.get('[data-tab="2"] .response-message > p').should('contain', 'test.txt Updated');
        cy.get('p > a').click()
    });

});