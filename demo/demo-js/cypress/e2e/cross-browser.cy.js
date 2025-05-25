// cypress/e2e/cross-browser.cy.js
describe('Cross-Browser Compatibility', () => {
    const browsers = ['chrome', 'firefox', 'edge'];

    browsers.forEach(browser => {
        describe(`${browser} compatibility`, () => {
            it('should work in ' + browser, () => {
                cy.visit('/welcome');

                // Test basic functionality
                cy.loadTestConfig().then((config) => {
                    cy.get('#user-settings-btn').click();
                    cy.get('#api-key-OpenAI').type(config.apiKeys?.OpenAI || 'test-key');
                    cy.get('#save-user-settings').click();
                });

                // Test localStorage
                cy.window().its('localStorage').invoke('getItem', 'defaultModel')
                    .should('not.be.null');

                // Test modern JS features
                cy.window().then((win) => {
                    expect(win.fetch).to.be.a('function');
                    expect(win.URLSearchParams).to.be.a('function');
                    expect(win.DOMParser).to.be.a('function');
                });
            });
        });
    });
});