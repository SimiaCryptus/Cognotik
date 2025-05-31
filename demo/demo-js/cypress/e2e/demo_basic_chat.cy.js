describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    it('should provide a complete demonstration of Cognotik features', () => {

        cy.narrate('basic_chat_intro');
        cy.get('#open-basic-chat').click();
        cy.get('#submit-basic-chat-settings').click()
        
        cy.narrate('parallel_expansion_intro');
        cy.get('#chat-input').type('What is machine learning?');
        cy.narrate('parallel_expansion_example');
        cy.get('[data-testid="send-button"]').click();
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').scrollIntoView();
        cy.get('.spinner-border',{timeout:12000}).should('not.exist');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")

        cy.narrate('sequential_expansion_intro');
        cy.get('#chat-input').clear().type('Can you give me specific examples?');
        cy.narrate('sequential_expansion_example');
        cy.get('[data-testid="send-button"]').click();
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').scrollIntoView();
        cy.get('.spinner-border',{timeout:12000}).should('not.exist');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")

        cy.narrate('mermaid_support_intro');
        cy.get('#chat-input').clear().type('Create a mermaid diagram showing the machine learning workflow');
        cy.get('[data-testid="send-button"]').click();
        cy.narrate('mermaid_diagram_example');
        cy.get('.spinner-border').should('exist');
        cy.get('.spinner-border').scrollIntoView();
        cy.get('.spinner-border',{timeout:12000}).should('not.exist');
        cy.get('[data-testid="message-list"]').scrollTo("bottom")

        cy.get('#session-menu-button').click()
        cy.wait(200);
        cy.get('#usage-menu-button').click();
        cy.narrate('usage_reporting');
        cy.wait(200);
        cy.get('[data-testid="modal-overlay"]').click({ x: 0, y: 0 }); // Close usage modal

        cy.narrate('themes_layout_intro');
        cy.get('#theme-menu-button').click();
        cy.get('#theme-option-pony').click()
        cy.get('#layout-menu-button').click();
        cy.get('#layout-option-compact').click();
        cy.narrate('themes_customization');

        cy.narrate('demo_conclusion');
    });
});