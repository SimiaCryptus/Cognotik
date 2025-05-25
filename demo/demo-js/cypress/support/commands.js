// cypress/support/commands.js
// Custom command to load test config
Cypress.Commands.add('loadTestConfig', () => {
    return cy.readFile('/home/andrew/code/Cognotik/demo/test_config.json');
});
// Custom command to get API key from test config
Cypress.Commands.add('getApiKey', (provider) => {
    return cy.loadTestConfig().then((config) => {
        return config.apiKeys?.[provider] || '';
    });
});


// Custom command to clear all application data
Cypress.Commands.add('clearAppData', () => {
    cy.clearLocalStorage();
    cy.clearCookies();
    cy.window().then((win) => {
        win.sessionStorage.clear();
    });
});

// Custom command to setup API keys
Cypress.Commands.add('setupApiKeys', (providers = ['OpenAI']) => {
    cy.loadTestConfig().then((config) => {
        cy.get('#user-settings-btn').click();
        providers.forEach(provider => {
            const apiKey = config.apiKeys?.[provider] || `test-${provider.toLowerCase()}-key`;
            cy.get(`#api-key-${provider}`).clear().type(apiKey);
        });
        cy.get('#save-user-settings').click();
        cy.get('.modal').should('not.be.visible');
    });
});

// Legacy version for backwards compatibility
Cypress.Commands.add('setupApiKeysLegacy', (providers = ['OpenAI']) => {
    cy.get('#user-settings-btn').click();
    providers.forEach(provider => {
        cy.get(`#api-key-${provider}`).clear().type(`test-${provider.toLowerCase()}-key`);
    });
    cy.get('#save-user-settings').click();
    cy.get('.modal').should('not.be.visible');
});

// Custom command to configure basic task settings
Cypress.Commands.add('configureTaskSettings', (settings = {}) => {
    const defaults = {
        model: 'GPT4o',
        parsingModel: 'GPT4oMini',
        workingDir: './test-project',
        temperature: 0.3,
        autoFix: true
    };
    const config = { ...defaults, ...settings };

    if (config.model) {
        cy.get('#model-selection').select(config.model);
    }
    if (config.parsingModel) {
        cy.get('#parsing-model').select(config.parsingModel);
    }
    if (config.workingDir) {
        cy.get('#working-dir').clear().type(config.workingDir);
    }
    if (config.temperature !== undefined) {
        cy.get('#temperature').invoke('val', config.temperature).trigger('input');
    }
    if (config.autoFix) {
        cy.get('#auto-fix').check();
    }
});

// Custom command to select tasks
Cypress.Commands.add('selectTasks', (tasks = ['InsightTask']) => {
    tasks.forEach(task => {
        cy.get(`#task-${task}`).check();
    });
});

// Custom command to navigate through wizard steps
Cypress.Commands.add('navigateToStep', (step) => {
    const steps = {
        'task-settings': '#next-to-task-settings',
        'task-selection': '#next-to-task-selection',
        'launch': '#next-to-launch'
    };

    if (steps[step]) {
        cy.get(steps[step]).click();
    }
});

// Custom command to mock API responses
Cypress.Commands.add('mockApiResponses', () => {
    cy.intercept('POST', '/taskChat/settings', { statusCode: 200 }).as('saveSettings');
    cy.intercept('POST', '/chat/settings', { statusCode: 200 }).as('saveChatSettings');
    cy.intercept('GET', '/api/models', {
        fixture: 'models.json'
    }).as('getModels');
});

// Custom command to wait for page load
Cypress.Commands.add('waitForPageLoad', () => {
    cy.get('body').should('be.visible');
    cy.window().should('have.property', 'document');
    cy.document().should('have.property', 'readyState', 'complete');
});

// Custom command to check accessibility
Cypress.Commands.add('checkA11y', () => {
    // Basic accessibility checks
    cy.get('[role]').should('exist');
    cy.get('button').should('have.attr', 'type');
    cy.get('input').should('have.attr', 'id');
    cy.get('label').should('have.attr', 'for');
});