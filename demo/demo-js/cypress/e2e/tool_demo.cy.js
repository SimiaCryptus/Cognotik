describe('Cognotik App Launcher', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });


    it('should launch the chat-task app', () => {

        cy.narrate('demo_start');
        
        let model = 'Claude 3.5 Haiku (Anthropic)';
        let command = "Create a new file called test.txt and write a short story in it";
        let taskType = 'FileModificationTask';


        cy.narrate('select_single_task');
        cy.get('#single-task-mode').check();
        cy.get('#next-to-task-settings').click();
        cy.narrate('configure_model');
        cy.get('#model-selection').select(model);
        cy.get('#parsing-model').select(model);
        cy.get('#generate-working-dir').click()
        cy.narrate('set_temperature');
        cy.get('#temperature').invoke('val', 0.2).trigger('input');
        cy.get('#temperature-value').should('contain', '0.2');
        cy.narrate('enable_autofix');
        cy.get('#auto-fix').check();
        cy.get('#next-to-task-selection').click();
        cy.narrate('select_task_type');
        cy.get('#task-' + taskType).check();
        cy.get('#next-to-launch').click();
        cy.narrate('launch_session');
        cy.get('#launch-session').click();
        cy.url().should('include', '/taskChat/#');
        cy.narrate('enter_command');
        cy.get('[data-testid="chat-input"]').type(command);
        cy.get('[data-testid="send-button"]').click();
        cy.get('[data-testid="collapse-input"]').click();
        cy.narrate('view_plan');
        cy.get('.tab-button').contains('Plan', {timeout: 300000}).wait(1000).click();
        cy.narrate('execute_task');
        cy.get('.tab-button').contains('Run', {timeout: 300000}).wait(1000).click();
        cy.narrate('view_output');
        cy.get('.tab-button').contains('Output', {timeout: 300000}).wait(1000).click();



        cy.get('[data-tab="2"] .response-message > p').should('contain', 'test.txt Updated');
        cy.get('p > a').click()
        cy.narrate('demo_complete');
    });
});