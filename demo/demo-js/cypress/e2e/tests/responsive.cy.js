// cypress/e2e/responsive.cy.js
describe('Responsive Design Tests', () => {
    const viewports = [
        {name: 'mobile', width: 375, height: 667},
        {name: 'tablet', width: 768, height: 1024},
        {name: 'desktop', width: 1280, height: 720},
        {name: 'large-desktop', width: 1920, height: 1080}
    ];

    viewports.forEach(viewport => {
        describe(`${viewport.name} viewport`, () => {
            beforeEach(() => {
                cy.viewport(viewport.width, viewport.height);
                cy.visit('/welcome');
            });

            it('should display properly on ' + viewport.name, () => {
                // Check that main elements are visible
                cy.get('#cognitive-mode').should('be.visible');
                cy.get('#user-settings-btn').should('be.visible');

                // Check that content doesn't overflow
                cy.get('body').then(($body) => {
                    expect($body[0].scrollWidth).to.be.at.most(viewport.width + 20); // Allow small margin
                });
            });

            it('should have working navigation on ' + viewport.name, () => {
                cy.get('#user-settings-btn').click();
                cy.get('#user-settings-modal').should('be.visible');

                // Modal should fit in viewport
                cy.get('#user-settings-modal').then(($modal) => {
                    const modalRect = $modal[0].getBoundingClientRect();
                    expect(modalRect.width).to.be.at.most(viewport.width);
                    expect(modalRect.height).to.be.at.most(viewport.height);
                });
            });
        });
    });
});