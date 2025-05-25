// cypress/e2e/complete-workflow.cy.js
describe('Complete Cognotik Workflow', () => {
    beforeEach(() => {
        cy.visit('/welcome');
        cy.clearLocalStorage();
    });

    it('should complete full configuration and launch workflow', () => {
        // Step 1: Configure API Settings
        cy.get('#user-settings-btn').click();
        cy.get('#api-key-OpenAI').type('test-openai-key');
        cy.get('#api-key-Anthropic').type('test-anthropic-key');
        cy.get('#save-user-settings').click();
        cy.get('.modal').should('not.be.visible');

        // Step 2: Choose Cognitive Mode
        cy.get('input[name="cognitive-mode"][value="auto-plan"]').check();
        cy.get('#auto-plan-settings').should('be.visible');
        cy.get('#max-task-history').clear().type('15000');
        cy.get('#max-tasks-per-iteration').clear().type('5');
        cy.get('#next-to-task-settings').click();

        // Step 3: Configure Task Settings
        cy.get('#model-selection').should('contain.option', 'GPT-4o');
        cy.get('#model-selection').select('GPT4o');
        cy.get('#parsing-model').select('GPT4oMini');
        cy.get('#working-dir').clear().type('./test-project');
        cy.get('#temperature').invoke('val', 0.5).trigger('input');
        cy.get('#temperature-value').should('contain', '0.5');
        cy.get('#auto-fix').check();
        cy.get('#next-to-task-selection').click();

        // Step 4: Select Tasks
        cy.get('#task-InsightTask').check();
        cy.get('#task-FileModificationTask').check();
        cy.get('#task-RunShellCommandTask').check();
        cy.get('#next-to-launch').click();

        // Step 5: Review and Launch
        cy.get('#cognitive-mode-summary').should('contain', 'Autonomous');
        cy.get('#task-settings-summary').should('contain', 'GPT-4o');
        cy.get('#task-settings-summary').should('contain', './test-project');
        cy.get('#api-settings-summary').should('contain', 'OpenAI: Configured');

        // Mock the launch request
        cy.intercept('POST', '/taskChat/settings', { statusCode: 200 }).as('saveSettings');

        cy.get('#launch-session').click();
        cy.wait('@saveSettings');

        // Should redirect to auto-plan interface
        cy.url().should('include', '/autoPlan/#');
    });

    it('should handle basic chat workflow', () => {
        // Configure API key first
        cy.get('#user-settings-btn').click();
        cy.get('#api-key-OpenAI').type('test-openai-key');
        cy.get('#save-user-settings').click();
        cy.get('.modal').should('not.be.visible');

        // Open basic chat
        cy.get('#open-basic-chat').click();
        cy.get('#basic-chat-settings-modal').should('be.visible');

        // Configure basic chat settings
        cy.get('#basic-chat-model').select('GPT4o');
        cy.get('#basic-chat-parsing-model').select('GPT4oMini');
        cy.get('#basic-chat-temperature').invoke('val', 0.7).trigger('input');
        cy.get('#basic-chat-temperature-value').should('contain', '0.7');
        cy.get('#basic-chat-budget').clear().type('5.0');

        // Mock the chat settings save
        cy.intercept('POST', '/chat/settings', { statusCode: 200 }).as('saveChatSettings');

        cy.get('#submit-basic-chat-settings').click();
        cy.wait('@saveChatSettings');

        // Should redirect to chat interface
        cy.url().should('include', '/chat/#');
    });
});