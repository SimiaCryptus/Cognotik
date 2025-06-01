describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.log('DEMO_FLOW: Starting Plan Ahead Mode demo');
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
        cy.log('DEMO_FLOW: Initial setup complete - page loaded, storage cleared, audio enabled');
    });

    const waitScrollAndGet = (selectorFn, timeout = 120000, waitTime = 2000) => {
        cy.log('DEMO_FLOW: Waiting for element to exist and scrolling into view');
        selectorFn().should('exist', {timeout}); // Ensure the element does not exist initially
        cy.wait(waitTime);
        selectorFn().scrollIntoView({timeout})
        cy.wait(waitTime);
        cy.log('DEMO_FLOW: Element located and scrolled into view');
        return selectorFn()
    };

    it('should provide a complete demonstration of Cognotik features', () => {

        let stdTimeout = 120000;
        let longTimeout = 900000;

        cy.log('DEMO_FLOW: Phase 1 - Plan Ahead Mode Introduction (6:30-8:30)');
        cy.narrate('plan_ahead_mode_intro');

        cy.log('DEMO_FLOW: Selecting Plan Ahead Mode');
        cy.get('#plan-ahead-mode').check();
        cy.log('DEMO_FLOW: Plan Ahead Mode selected, proceeding to task settings');
        cy.get('#next-to-task-settings').click();

        cy.log('DEMO_FLOW: Phase 2 - Specifying technology and tools (6:45)');
        cy.log('DEMO_FLOW: Setting working directory to /tmp/demo-webapp');
        cy.get('#working-dir').clear()
        cy.get('#working-dir').type('/tmp/demo-webapp');
        cy.narrate('specify_tech_tools');
        cy.log('DEMO_FLOW: Working directory set, proceeding to task selection');
        cy.wait(500);
        cy.get('#next-to-task-selection').click();

        cy.log('DEMO_FLOW: Enabling web development tasks');
        cy.get('#task-FileModificationTask').check();
        cy.log('DEMO_FLOW: FileModificationTask enabled');
        cy.get('#task-RunShellCommandTask').check();
        cy.log('DEMO_FLOW: RunShellCommandTask enabled');
        cy.get('#task-RunCodeTask').check();
        cy.log('DEMO_FLOW: RunCodeTask enabled');
        cy.log('DEMO_FLOW: All required tasks enabled, proceeding to launch');
        cy.get('#next-to-launch').click();
        cy.log('DEMO_FLOW: Launching session');

        cy.get('#launch-session').click();
        cy.wait(2000);
        cy.log('DEMO_FLOW: Session launched successfully');

        cy.log('DEMO_FLOW: Phase 3 - Specifying feature details (7:15)');
        cy.log('DEMO_FLOW: Entering React TypeScript web application requirements');
        cy.get('#chat-input').type('Create a React TypeScript web application using npm with the following features: user authentication, dashboard with charts, and responsive design. Use modern React hooks and TypeScript best practices.');
        cy.narrate('specify_feature_details');
        cy.log('DEMO_FLOW: Requirements entered, sending message');
        cy.wait(500);
        cy.get('[data-testid="send-button"]').click();
        cy.wait(6000);
        cy.log('DEMO_FLOW: Message sent, waiting for AI response');
        cy.log('DEMO_FLOW: Interacting with first response tab - clicking href link');


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(0)
                    .find('> [data-tab="0"]')
                    .find('.cmd-button.href-link', {timeout: stdTimeout}).eq(0),
            stdTimeout).click();
        cy.log('DEMO_FLOW: First href link clicked successfully');
        cy.log('DEMO_FLOW: Navigating to second tabs container');


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1),
            stdTimeout).scrollIntoView();
        cy.wait(1000);
        cy.log('DEMO_FLOW: Clicking first tab in second container');
        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1)
                    .find('> .tabs > [data-for-tab="0"]'),
            stdTimeout).click();
        cy.wait(1000);
        cy.log('DEMO_FLOW: First tab clicked, scrolling to bottom of message list');
        cy.get('[data-testid="message-list"]').eq(0).scrollTo('bottom')
        cy.wait(5000);
        cy.log('DEMO_FLOW: Scrolled to bottom, waiting before next interaction');
        cy.log('DEMO_FLOW: Clicking second tab in second container');


        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container').eq(1)
                    .find('> .tabs > [data-for-tab="1"]'),
            stdTimeout).click();
        cy.log('DEMO_FLOW: Second tab clicked successfully');


        cy.log('DEMO_FLOW: Phase 4 - Demo conclusion (8:45-9:00)');
        cy.narrate('demo_conclusion');
        cy.log('DEMO_FLOW: Plan Ahead Mode demo completed successfully');
    });
});