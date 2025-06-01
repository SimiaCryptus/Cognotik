describe('Cognotik Launcher - Comprehensive Introduction', () => {
    beforeEach(() => {
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
    });

    it('should provide a complete introduction to the Cognotik launcher', () => {
        
        // Introduction
        cy.narrate('intro_welcome');
        
        // Overview of the cognitive/task mode model
        cy.narrate('cognitive_model_overview');
        
        // Demonstrate User Settings
        cy.narrate('user_settings_intro');
        cy.get('#user-settings-btn').click();
        
        // API Keys tab
        cy.narrate('api_keys_explanation');
        cy.get('[data-tab="api-keys"]').click();
        cy.wait(1000);
        
        // Show different API providers
        cy.get('#api-keys-container').should('be.visible');
        cy.narrate('api_providers_overview');

        // Configure API Settings
        cy.loadTestConfig().then((config) => {
            cy.get('#api-key-Anthropic').clear()
            cy.get('#api-key-Anthropic').type(config.apiKeys?.Anthropic || 'test-anthropic-key');
            cy.get('#save-user-settings').click();
            cy.get('.modal').should('not.be.visible');
        });

        // Local Tools tab
        cy.get('[data-tab="local-tools"]').click();
        cy.get('#local-tools-container').should('be.visible');
        cy.narrate('local_tools_explanation');

        // Close user settings
        cy.get('#close-user-settings-modal').click();
        
        // Step 1: Cognitive Modes
        cy.narrate('step1_cognitive_modes');
        
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(1) > :nth-child(1)').click()
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(1) > :nth-child(1) > .tooltip').click();
        cy.narrate('chat_mode_explanation');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(2) > :nth-child(1) > .tooltip').click()
        cy.narrate('autonomous_mode_explanation');
        cy.get('#auto-plan-mode').check();
        cy.narrate('autonomous_settings_explanation');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(3) > :nth-child(1)').click()
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(3) > :nth-child(1) > .tooltip').click()
        cy.narrate('plan_ahead_mode_explanation');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(4) > :nth-child(1)').click()
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(4) > :nth-child(1) > .tooltip').click()
        cy.narrate('goal_oriented_mode_explanation');

        // Return to Chat mode for demonstration
        cy.get('#single-task-mode').check();
        cy.get('#next-to-task-settings').click();
        
        // Step 2: Task Settings
        cy.narrate('step2_task_settings');
        
        // Model Selection
        cy.narrate('model_selection_explanation');
        cy.get('#model-selection').should('be.visible');
        cy.get('#parsing-model').should('be.visible');
        
        // Working Directory
        cy.narrate('working_directory_explanation');
        cy.get('#working-dir').should('be.visible');
        cy.get('#generate-working-dir').click();
        cy.narrate('generate_directory_demo');
        
        // Temperature Setting
        cy.narrate('temperature_explanation');
        cy.get('#temperature').invoke('val', 0.2).trigger('input');
        cy.get('#temperature-value').should('contain', '0.2');
        
        // Auto Fix
        cy.narrate('auto_fix_explanation');
        cy.get('#auto-fix').check();
        
        cy.get('#next-to-task-selection').click();
        
        // Step 3: Task Types
        cy.narrate('step3_task_types');
        
        // Insight Task
        cy.get('#task-toggles > :nth-child(1) > div > .tooltip').click();
        cy.narrate('insight_task_explanation');
        //cy.get('#task-InsightTask').check();
        
        // File Modification Task
        cy.get('#task-toggles > :nth-child(2) > div > .tooltip').click();
        cy.narrate('file_modification_task_explanation');
        // cy.get('#task-FileModificationTask').check();

        // Shell Command Task
        cy.get('#task-toggles > :nth-child(3) > div > .tooltip').click();
        cy.narrate('shell_command_task_explanation');
        // cy.get('#task-RunShellCommandTask').check();

        // Code Execution Task
        cy.get('#task-toggles > :nth-child(4) > div > .tooltip').click();
        cy.narrate('code_execution_task_explanation');
        // cy.get('#task-RunCodeTask').check();

        // Auto Fix Task
        cy.get('#task-toggles > :nth-child(5) > div > .tooltip').click();
        cy.narrate('auto_fix_task_explanation');
        // cy.get('#task-CommandAutoFixTask').check();

        // File Search Task
        cy.get('#task-toggles > :nth-child(6) > div > .tooltip').click();
        cy.narrate('file_search_task_explanation')
        // cy.get('#task-FileSearchTask').check();

        // Web Search Task
        cy.get('#task-toggles > :nth-child(7) > div > .tooltip').click();
        cy.narrate('web_search_task_explanation');
        // cy.get('#task-CrawlerAgentTask').check();

        // GitHub Search Task
        cy.get('#task-toggles > :nth-child(8) > div > .tooltip').click();
        cy.narrate('github_search_task_explanation');
        //cy.get('#task-GitHubSearchTask').check();

        cy.get('#next-to-launch').click();
        cy.narrate('step4_launch');
        cy.narrate('review_configuration');
        cy.narrate('launch_button_explanation');
        cy.narrate('basic_chat_alternative');
        cy.narrate('introduction_conclusion');
    });
});