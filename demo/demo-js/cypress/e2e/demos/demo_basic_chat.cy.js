describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.log('DEMO_FLOW: Starting demo setup');
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
        cy.log('DEMO_FLOW: Demo setup completed - page loaded, storage cleared, audio enabled');
    });

    it('should provide a complete demonstration of Cognotik features', () => {
        cy.log('DEMO_FLOW: Beginning comprehensive Cognotik feature demonstration');
        cy.log('DEMO_SECTION: Basic Chat Setup - Opening chat interface');

        cy.wait(500);
        cy.log('DEMO_ACTION: Clicking open basic chat button');
        cy.get('#open-basic-chat').click();
        cy.narrate('basic_chat_welcome');
        cy.log('DEMO_ACTION: Submitting basic chat settings');
        cy.get('#submit-basic-chat-settings').click()
        cy.log('DEMO_FLOW: Basic chat interface initialized and ready');

        cy.log('DEMO_SECTION: Parallel Expansion Syntax - Demonstrating concurrent thought processing');
        cy.get('.expandable-header').should('exist');
        cy.wait(1000);
        cy.get('.expandable-header').click()
        cy.narrate('expansion_syntax_intro');

        cy.log('DEMO_ACTION: Entering initial machine learning question');
        cy.get('#chat-input').type('Explore the implications of {Mars colonization|asteroid mining|space tourism} for human civilization', {parseSpecialCharSequences: false});
        cy.wait(500);
        cy.log('DEMO_ACTION: Sending first message to trigger parallel expansion');
        cy.get('[data-testid="send-button"]').click();

        cy.log('DEMO_WAIT: Monitoring spinner appearance for processing indication');
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').last().scrollIntoView();
        cy.narrate('parallel_expansion_example');
        cy.log('DEMO_WAIT: Waiting for parallel expansion processing to complete');
        cy.get('.spinner-border', {timeout: 900000}).should('not.exist');
        cy.log('DEMO_ACTION: Scrolling to view complete parallel expansion response');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_FLOW: Parallel expansion demonstration completed successfully');
        cy.log('DEMO_SECTION: Sequential Expansion Demo - Demonstrating follow-up question handling');

        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_ACTION: Entering follow-up question using sequential expansion syntax');
        cy.get('#chat-input').clear()
        cy.get('#chat-input').type('Consider the timeline for <2030s|2040s|2050s> development');
        cy.narrate('sequential_expansion_intro');
        cy.wait(500);
        cy.log('DEMO_ACTION: Sending follow-up message to trigger sequential expansion');
        cy.get('[data-testid="send-button"]').click();
        cy.log('DEMO_WAIT: Monitoring spinner for sequential processing indication');

        cy.get('.spinner-border').should('exist');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.narrate('sequential_expansion_example');
        cy.log('DEMO_WAIT: Waiting for sequential expansion processing to complete');
        cy.get('.spinner-border', {timeout: 600000}).should('not.exist', {timeout: 600000});
        cy.log('DEMO_ACTION: Scrolling to view complete sequential expansion response');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_FLOW: Sequential expansion demonstration completed successfully');
        cy.log('DEMO_SECTION: Mermaid Diagram Support - Demonstrating visual diagram generation');

        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_ACTION: Requesting mermaid diagram creation');
        cy.get('#chat-input').clear()
        cy.get('#chat-input').type('Illustrate with mermaid diagrams');
        cy.narrate('mermaid_support_intro');
        cy.log('DEMO_ACTION: Sending diagram request to demonstrate mermaid support');
        cy.wait(500);
        cy.get('[data-testid="send-button"]').click();
        cy.log('DEMO_WAIT: Monitoring spinner for diagram generation processing');

        cy.get('.spinner-border').should('exist');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.narrate('mermaid_diagram_example');
        cy.log('DEMO_WAIT: Waiting for mermaid diagram generation to complete');
        cy.get('.spinner-border',{timeout:12000}).should('not.exist');
        cy.log('DEMO_ACTION: Scrolling to view generated mermaid diagram');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")
        cy.log('DEMO_FLOW: Mermaid diagram demonstration completed successfully');
        cy.log('DEMO_SECTION: Usage Reporting - Demonstrating analytics and usage tracking');
        cy.log('DEMO_ACTION: Opening session menu to access usage reporting');

        cy.get('#session-menu-button').click()
        cy.wait(200);
        cy.log('DEMO_ACTION: Clicking usage menu to display usage statistics');
        cy.get('#usage-menu-button').click();
        cy.narrate('usage_transparency');
        cy.log('DEMO_FLOW: Usage reporting modal displayed with session statistics');
        cy.wait(200);
        cy.log('DEMO_ACTION: Closing usage modal by clicking overlay');
       cy.get('[data-testid="modal-overlay"]').click({ x: 0, y: 0 });
        cy.log('DEMO_FLOW: Usage reporting demonstration completed');
        cy.log('DEMO_SECTION: Themes and Layout Customization - Demonstrating UI personalization');

        cy.log('DEMO_ACTION: Opening theme menu for customization demo');
        cy.get('#theme-menu-button').click();
        cy.narrate('themes_layout_intro');
        cy.log('DEMO_ACTION: Selecting theme to demonstrate theme switching');
        cy.get('#theme-option-synthwave').click()
        cy.log('DEMO_ACTION: Opening layout menu for layout customization');
        cy.get('#layout-menu-button').click();
        cy.log('DEMO_ACTION: Selecting compact layout to demonstrate layout options');
        cy.narrate('themes_customization');
        cy.get('#layout-option-compact').click();
        cy.log('DEMO_FLOW: Theme and layout customization demonstration completed');
        cy.log('DEMO_SECTION: Demo Conclusion - Wrapping up comprehensive demonstration');

        cy.narrate('demo_conclusion');
        cy.log('DEMO_FLOW: Comprehensive Cognotik feature demonstration completed successfully');
        cy.log('DEMO_SUMMARY: Demonstrated features - Basic Chat, Parallel Expansion, Sequential Expansion, Mermaid Diagrams, Usage Reporting, Theme/Layout Customization');
    });
});