// cypress/e2e/error-handling.cy.js
describe('Error Handling', () => {
    beforeEach(() => {
        cy.visit('/welcome');
        cy.clearLocalStorage();
    });

    it('should prevent launch without API keys', () => {
        // Try to launch without configuring API keys
        cy.get('#next-to-task-settings').click();
        cy.get('#next-to-task-selection').click();
        cy.get('#task-InsightTask').check();
        cy.get('#next-to-launch').click();
        cy.get('#launch-session').click();

        // Should show error notification
        cy.window().its('alert').should('have.been.calledWith',
            '❌ Please configure at least one API key before launching');
    });

    it('should prevent launch without enabled tasks', () => {
        // Configure API key but no tasks
        cy.loadTestConfig().then((config) => {
            cy.get('#user-settings-btn').click();
            cy.get('#api-key-OpenAI').type(config.apiKeys?.OpenAI || 'test-key');
            cy.get('#save-user-settings').click();
            cy.get('.modal').should('not.be.visible');
        });

        // Navigate to launch without selecting tasks
        cy.get('#next-to-task-settings').click();
        cy.get('#next-to-task-selection').click();
        cy.get('#next-to-launch').click();
        cy.get('#launch-session').click();

        // Should show error notification
        cy.window().its('alert').should('have.been.calledWith',
            '❌ Please enable at least one task before launching');
    });

    it('should handle server errors gracefully', () => {
        // Configure valid settings
        cy.loadTestConfig().then((config) => {
            cy.get('#user-settings-btn').click();
            cy.get('#api-key-OpenAI').type(config.apiKeys?.OpenAI || 'test-key');
            cy.get('#save-user-settings').click();
            cy.get('.modal').should('not.be.visible');
        });

        cy.get('#next-to-task-settings').click();
        cy.get('#next-to-task-selection').click();
        cy.get('#task-InsightTask').check();
        cy.get('#next-to-launch').click();

        // Mock server error
        cy.intercept('POST', '/taskChat/settings', {
            statusCode: 500,
            body: 'Internal Server Error'
        }).as('saveSettingsError');

        cy.get('#launch-session').click();
        cy.wait('@saveSettingsError');

        // Should show error notification
        cy.window().its('alert').should('have.been.calledWith',
            '❌ Error saving session settings before launch: Failed to save session settings: 500 Internal Server Error');
    });
});