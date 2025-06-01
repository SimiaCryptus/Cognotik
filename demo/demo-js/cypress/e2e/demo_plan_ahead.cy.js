describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    const waitScrollAndGet = (selectorFn, timeout = 120000, waitTime = 2000) => {
        selectorFn().should('exist', {timeout}); // Ensure the element does not exist initially
        cy.wait(waitTime);
        selectorFn().scrollIntoView({timeout})
        cy.wait(waitTime);
        return selectorFn()
    };

    it('should provide a complete demonstration of Cognotik features', () => {

        let stdTimeout = 120000;
        let longTimeout = 900000;

        // (6:30 - 8:30) Plan Ahead Mode - Creating a Web Application
        cy.narrate('plan_ahead_mode_intro');

        // Select Plan Ahead Mode
        cy.get('#plan-ahead-mode').check();
        cy.get('#next-to-task-settings').click();

        // (6:45) Specify tech and tools
        cy.narrate('specify_tech_tools');
        cy.get('#working-dir').clear().type('/tmp/demo-webapp');
        cy.get('#next-to-task-selection').click();

        // Enable relevant tasks for web development
        cy.get('#task-FileModificationTask').check();
        cy.get('#task-RunShellCommandTask').check();
        cy.get('#task-RunCodeTask').check();
        cy.get('#next-to-launch').click();

        cy.get('#launch-session').click();
        cy.wait(2000);

        // (7:15) Specify feature details
        cy.narrate('specify_feature_details');
        cy.get('#chat-input').type('Create a React TypeScript web application using npm with the following features: user authentication, dashboard with charts, and responsive design. Use modern React hooks and TypeScript best practices.');
        cy.get('[data-testid="send-button"]').click();
        cy.wait(6000);


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(0)
                    .find('> [data-tab="0"]')
                    .find('.cmd-button.href-link', {timeout: stdTimeout}).eq(0),
            stdTimeout).click();


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1),
            stdTimeout).scrollIntoView();
        cy.wait(1000);
        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1)
                    .find('> .tabs > [data-for-tab="0"]'),
            stdTimeout).click();
        cy.wait(1000);
        cy.get('[data-testid="message-list"]').eq(0)
            .scrollTo('bottom')
        cy.wait(5000);


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1)
                    .find('> .tabs > [data-for-tab="1"]'),
            stdTimeout).click();


        // (8:45 - 9:00) Conclusion
        cy.narrate('demo_conclusion');
    });
});