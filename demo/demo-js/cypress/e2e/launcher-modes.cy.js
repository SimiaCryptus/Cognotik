// cypress/e2e/complete-workflow.cy.js
describe('Cognotik App Launcher', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
    });

    it('should launch the autoplanning app', () => {
        // Step 1: Configure API Settings
        cy.loadTestConfig().then((config) => {
            cy.get('#user-settings-btn').click();
            cy.get('#api-key-Anthropic').clear()
            cy.get('#api-key-Anthropic').type(config.apiKeys?.Anthropic || 'test-anthropic-key');
            cy.get('#save-user-settings').click();
            cy.get('.modal').should('not.be.visible');
        });

        // Step 2: Choose Cognitive Mode
        cy.get('input[name="cognitive-mode"][value="auto-plan"]').check();
        cy.get('#auto-plan-settings').should('be.visible');
        cy.get('#max-task-history').clear().type('15000');
        cy.get('#max-tasks-per-iteration').clear().type('1');
        cy.get('#next-to-task-settings').click();

        // Step 3: Configure Task Settings
        cy.get('#model-selection').select('Claude 3.5 Sonnet (Anthropic)');
        cy.get('#parsing-model').select('Claude 3.5 Haiku (Anthropic)');
        // cy.get('#working-dir').clear().type('./test-project');
        cy.get('#generate-working-dir').click()
        cy.get('#temperature').invoke('val', 0.2).trigger('input');
        cy.get('#temperature-value').should('contain', '0.2');
        cy.get('#auto-fix').check();
        cy.get('#next-to-task-selection').click();

        // Step 4: Select Tasks
        cy.get('#task-InsightTask').check();
        cy.get('#task-FileModificationTask').check();
        cy.get('#task-RunShellCommandTask').check();
        cy.get('#next-to-launch').click();

        // Step 5: Review and Launch
        cy.get('#cognitive-mode-summary').should('contain', 'Autonomous');
        cy.get('#task-settings-summary').should('contain', 'Sonnet');
        cy.get('#api-settings-summary').should('contain', 'Anthropic: Configured');

        // Mock the launch request
        // cy.intercept('POST', '/taskChat/settings', { statusCode: 200 }).as('saveSettings');

        cy.get('#launch-session').click();
        // cy.wait('@saveSettings');

        // Should redirect to auto-plan interface
        cy.url().should('include', '/autoPlan/#');
    });

    it('should launch chat app', () => {
        // Configure API key first
        cy.loadTestConfig().then((config) => {
            cy.get('#user-settings-btn').click();
            cy.get('#api-key-Anthropic').type(config.apiKeys?.Anthropic || 'test-anthropic-key');
            cy.get('#save-user-settings').click();
            cy.get('.modal').should('not.be.visible');
        });

        // Open basic chat
        cy.get('#open-basic-chat').click();
        cy.get('#basic-chat-settings-modal').should('be.visible');

        // Configure basic chat settings
        cy.get('#basic-chat-model').select('Claude 3.5 Sonnet (Anthropic)');
        cy.get('#basic-chat-parsing-model').select('Claude 3.5 Haiku (Anthropic)');
        cy.get('#basic-chat-temperature').invoke('val', 0.2).trigger('input');
        cy.get('#basic-chat-temperature-value').should('contain', '0.2');
        cy.get('#basic-chat-budget').clear().type('5.0');

        // Mock the chat settings save
        // cy.intercept('POST', '/chat/settings', { statusCode: 200 }).as('saveChatSettings');

        cy.get('#submit-basic-chat-settings').click();
        // cy.wait('@saveChatSettings');

        // Should redirect to chat interface
        cy.url().should('include', '/chat/#');
    });
});