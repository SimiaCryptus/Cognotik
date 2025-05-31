describe('Cognotik - Comprehensive Demo Walkthrough', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    it('should provide a complete demonstration of Cognotik features', () => {

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

        // (7:45) Prompt to build/validate but do not start
        cy.narrate('build_validate_prompt');
        cy.get('#chat-input').clear().type('Please create a detailed plan for this application but do not execute it yet. Show me the project structure and key components.');
        cy.get('[data-testid="send-button"]').click();
        cy.wait(4000);

        // (8:15) View built dist/build result
        cy.narrate('view_build_result');
        cy.get('#chat-input').clear().type('Show me what the final build output structure would look like in the dist folder');
        cy.get('[data-testid="send-button"]').click();
        cy.wait(3000);
        cy.narrate('build_structure_explanation');

        // (8:45 - 9:00) Conclusion
        cy.narrate('demo_conclusion');
    });
});