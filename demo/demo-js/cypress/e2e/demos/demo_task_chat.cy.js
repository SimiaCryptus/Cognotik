describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.log('DEMO_FLOW: Starting demo test - clearing state and enabling audio');
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    const waitScrollAndGet = (selectorFn, timeout = 120000, waitTime = 2000) => {
        cy.log('DEMO_FLOW: Waiting for element to exist and scrolling into view');
        selectorFn().should('exist', {timeout}); // Ensure the element does not exist initially
        cy.wait(waitTime);
        selectorFn().scrollIntoView({timeout})
        cy.wait(waitTime);
        return selectorFn()
    };

    it('should provide a complete demonstration of Cognotik features', () => {

        let stdTimeout = 120000;
        let webCrawlTimeout = 900000;

        cy.log('DEMO_FLOW: Starting chat mode demonstration (4:00 - 6:30)');
        cy.narrate('chat_mode_superpowers');

        cy.log('DEMO_FLOW: Explaining cognitive modes and selecting single-task mode (4:15)');
        cy.narrate('cognitive_modes_overview');
        cy.get('#single-task-mode').check();
        cy.log('DEMO_FLOW: Single-task mode selected, proceeding to task settings');
        cy.get('#next-to-task-settings').click();
        cy.wait(2000);

        cy.log('DEMO_FLOW: Explaining working directory and model selection');
        cy.narrate('ai_workspace_configuration');
        cy.narrate('workspace_configuration');
        cy.narrate('working_directory_concept');
        cy.get('#working-dir').should('exist');
        cy.get('#model-selection').should('exist');
        cy.log('DEMO_FLOW: Selecting Claude 3.7 Sonnet as main model');
        cy.narrate('ai_model_selection');
        cy.get('#model-selection').select("Claude 3.7 Sonnet (Anthropic)");
        cy.log('DEMO_FLOW: Selecting Claude 3.5 Haiku as parsing model');
        cy.get('#parsing-model').select("Claude 3.5 Haiku (Anthropic)");
        cy.log('DEMO_FLOW: Generating working directory');
        cy.narrate('generate_directory_demo');
        cy.get('#generate-working-dir').click();
        cy.log('DEMO_FLOW: Proceeding to task selection');
        cy.get('#next-to-task-selection').click();
        cy.wait(2000);

        cy.log('DEMO_FLOW: Introducing task types and selecting research tasks (4:45)');
        cy.narrate('task_types_research_intro');
        cy.narrate('file_modification_capabilities');
        cy.log('DEMO_FLOW: Selecting FileModificationTask for report generation');
        cy.get('#task-FileModificationTask').check();
        cy.narrate('web_search_capabilities');
        cy.log('DEMO_FLOW: Selecting CrawlerAgentTask for web research');
        cy.get('#task-CrawlerAgentTask').check();
        cy.log('DEMO_FLOW: Proceeding to launch configuration');
        cy.narrate('review_configuration');
        cy.get('#next-to-launch').click();
        cy.wait(2000);

        cy.log('DEMO_FLOW: Launching research session (5:15)');
        cy.narrate('launch_research_session');
        cy.log('DEMO_FLOW: Clicking launch session button');
        cy.get('#launch-session').click();
        cy.wait(2000);
        cy.log('DEMO_FLOW: Entering research query about sustainable energy');

        cy.get('#chat-input').clear();
        cy.get('#chat-input').type('Research the latest trends in sustainable energy technology and provide a comprehensive analysis');
        cy.narrate('enter_research_query');
        cy.log('DEMO_FLOW: Sending research query to start task execution');
        cy.wait(500);
        cy.get('[data-testid="send-button"]').click();
        cy.log('DEMO_FLOW: Verifying spinner appears indicating task processing');
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').scrollIntoView();

        cy.log('DEMO_FLOW: Waiting for first processing tab to become available');
        cy.narrate('auto_fix_debugging_capabilities');
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(1)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="0"]',
                    {timeout: stdTimeout}), stdTimeout).click();
        cy.log('DEMO_FLOW: First processing tab clicked');

        cy.log('DEMO_FLOW: Waiting for second processing tab (detailed processing view)');
        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(1)
                    .find('> .message-body > .tabs-container > [data-tab="0"]')
                    .find('> div > .tabs-container > .tabs > [data-for-tab="1"]', {timeout: stdTimeout})
            , stdTimeout).click();
        cy.log('DEMO_FLOW: Second processing tab clicked - showing detailed task execution');

        for (let index = 0; index < 5; index++) {
            if (index === 0) {
                cy.narrate('insight_analysis_capabilities');
            }
            waitScrollAndGet(() =>
                    cy.get('[data-testid="message-list"]').eq(0)
                        .find('> .response').eq(1)
                        .find('> .message-body > .tabs-container > [data-tab="0"]')
                        .find('> div > .tabs-container > [data-tab="1"]')
                        .find('> div > .tabs-container > .tabs > [data-for-tab="' + index + '"]', {timeout: stdTimeout})
                , stdTimeout).click();
            cy.log(`DEMO_FLOW: Page ${index} opened`);
            cy.wait(1000);
            cy.get('[data-testid="message-list"]').eq(0).scrollTo('bottom', {ensureScrollable: false})
            cy.wait(1000);
        }

        cy.log('DEMO_FLOW: Waiting for output tab (task completion) - this may take several minutes for web crawling');
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(1)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="2"]',
                    {timeout: webCrawlTimeout}), webCrawlTimeout).click();
        cy.log('DEMO_FLOW: Output tab clicked - research task completed successfully');

        cy.log('DEMO_FLOW: Requesting HTML slide presentation generation (6:00)');
        cy.get('#chat-input').clear()
        cy.log('DEMO_FLOW: Typing request for HTML slide generation');
        cy.narrate('request_html_report');
        cy.get('#chat-input').type('Generate a self-contained HTML file containing a slide presentation from this research');
        cy.log('DEMO_FLOW: Sending HTML generation request');
        cy.get('[data-testid="send-button"]').click();
        cy.log('DEMO_FLOW: Waiting for HTML generation task - first tab (overview)');
        cy.narrate('file_modification_capabilities');

        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="0"]',
                    {timeout: stdTimeout}), stdTimeout).click();
        cy.log('DEMO_FLOW: Clicking processing tab for HTML generation task');
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="1"]',
                    {timeout: stdTimeout}), stdTimeout).click();
        cy.log('DEMO_FLOW: Waiting for save file button and clicking to save HTML file');
        cy.narrate('accept_html_file_generation');
        waitScrollAndGet(() =>
                cy.get('.cmd-button.href-link', {timeout: webCrawlTimeout}).eq(0)
            , webCrawlTimeout).click();
        cy.log('DEMO_FLOW: HTML file save initiated');
        cy.wait(2000);
        cy.log('DEMO_FLOW: Accepting task output to complete file generation');
        waitScrollAndGet(() =>
                cy.get('.cmd-button.href-link', {timeout: stdTimeout}).last(),
            stdTimeout).click();
        cy.log('DEMO_FLOW: Task output accepted');

        cy.log('DEMO_FLOW: Clicking output tab to view completed the final result');
        waitScrollAndGet(() =>
            cy.get('[data-testid="message-list"]').eq(0)
                .find('> .response').eq(2)
                .find('> .message-body > .tabs-container > [data-tab="0"]')
                .find('> div > .tabs-container > .tabs > [data-for-tab="2"]',
                    {timeout: stdTimeout}), stdTimeout).click();
        cy.log('DEMO_FLOW: Output tab displayed');
        cy.wait(500)
        cy.log('DEMO_FLOW: Scrolling to bottom to show final results');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_FLOW: Opening generated file');
        cy.narrate('demo_conclusion');
        waitScrollAndGet(() =>
                cy.get('[data-testid="message-list"]').eq(0)
                    .find('> .response').eq(2)
                    .find('> .message-body > .tabs-container > [data-tab="0"]')
                    .find('> div > .tabs-container > [data-tab="2"]', {timeout: stdTimeout})
                    .find('a').eq(0)
            , stdTimeout).click();

        cy.log('DEMO_FLOW: Narrating HTML slides generation completion');
        cy.narrate('html_slides_generated');
    });
});