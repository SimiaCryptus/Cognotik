describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    // Utility function to wait for element, scroll into view, and return it
    const waitScrollAndGet = (selectorFn, timeout = 120000, waitTime = 2000) => {
        selectorFn().should('exist', {timeout}); // Ensure the element does not exist initially
        cy.wait(waitTime);
        selectorFn().scrollIntoView({timeout})
        cy.wait(waitTime);
        return selectorFn()
    };


    it('should provide a complete demonstration of Cognotik features', () => {

        // (4:00 - 6:30) Chat Mode - Basic Task Execution
        cy.narrate('chat_mode_intro');

        // (4:15) Explain cognitive modes
        cy.narrate('cognitive_modes_explanation');
        cy.get('#single-task-mode').check();
        cy.get('#next-to-task-settings').click();
        cy.wait(2000);

        // Point out working directory, model
        cy.narrate('working_directory_model_explanation');
        cy.get('#working-dir').should('exist');
        cy.get('#model-selection').should('exist');
        cy.get('#model-selection').select("Claude 3.7 Sonnet (Anthropic)");
        cy.get('#parsing-model').select("Claude 3.5 Haiku (Anthropic)");
        cy.get('#generate-working-dir').click();
        cy.get('#next-to-task-selection').click();
        cy.wait(2000);

        // (4:45) Brief intro to task types
        cy.narrate('task_types_research_intro');
        //cy.get('#task-InsightTask').check();
        cy.get('#task-FileModificationTask').check();
        cy.get('#task-CrawlerAgentTask').check();
        cy.get('#next-to-launch').click();
        cy.wait(2000);

        // (5:15) Launch session, enter query
        cy.narrate('launch_research_session');
        cy.get('#launch-session').click();
        cy.wait(2000);

        cy.narrate('enter_research_query');
        cy.get('#chat-input').clear();
        cy.get('#chat-input').type('Research the latest trends in sustainable energy technology and provide a comprehensive analysis');
        cy.get('[data-testid="send-button"]').click();
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').scrollIntoView();

        let stdTimeout = 120000;
        let webCrawlTimeout = 900000;

        // Wait for and click the second tab (Processing)
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(1)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="0"]', {timeout: stdTimeout}), stdTimeout).click();

        // Wait for and click the second tab (Processing)
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(1)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="1"]', {timeout: stdTimeout}), stdTimeout).click();

        // Wait for and click the third tab (Output - task complete)
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(1)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="2"]', {timeout: webCrawlTimeout}), webCrawlTimeout).click();

        // (6:00) Request report as HTML slides
        cy.narrate('request_html_report');
        cy.get('#chat-input').clear()
        cy.get('#chat-input').type('Generate a self-contained HTML file containing a slide presentation from this research');
        cy.get('[data-testid="send-button"]').click();

        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="0"]', {timeout: stdTimeout}), stdTimeout).click();
        // Wait for and click the second tab for the HTML generation task
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="1"]', {timeout: stdTimeout}), stdTimeout).click();
        // Wait for 'save file' button and click it
        waitScrollAndGet(() =>
                cy.get('.cmd-button.href-link', {timeout: stdTimeout}).eq(0),
            stdTimeout).click();
        cy.wait(2000);
        // Then accept the task output
        waitScrollAndGet(() =>
                cy.get('.cmd-button.href-link', {timeout: stdTimeout}).last(),
            stdTimeout).click();

        // Wait for and click the output tab for the HTML generation task
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="2"]', {timeout: stdTimeout}), stdTimeout).click();

        cy.wait(500)
        cy.get('[data-testid="message-list"]').scrollTo("bottom")

        cy.narrate('html_slides_generated');

        cy.narrate('demo_conclusion');
    });
});