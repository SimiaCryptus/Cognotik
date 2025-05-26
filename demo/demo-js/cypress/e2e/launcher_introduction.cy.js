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
        cy.narrate('api_providers_overview');
        cy.get('#api-keys-container').should('be.visible');
        
        // Local Tools tab
        cy.narrate('local_tools_explanation');
        cy.get('[data-tab="local-tools"]').click();
        cy.wait(1000);
        cy.get('#local-tools-container').should('be.visible');
        
        // Close user settings
        cy.get('#close-user-settings-modal').click();
        
        // Step 1: Cognitive Modes
        cy.narrate('step1_cognitive_modes');
        
        // Chat Mode (Single Task)
        cy.narrate('chat_mode_explanation');
        cy.get('#single-task-mode').should('be.checked');
        cy.get('label[for="single-task-mode"]').should('contain', 'Chat');
        
        // Autonomous Mode
        cy.narrate('autonomous_mode_explanation');
        cy.get('#auto-plan-mode').check();
        cy.get('#auto-plan-settings').should('be.visible');
        cy.narrate('autonomous_settings_explanation');
        
        // Plan Ahead Mode
        cy.narrate('plan_ahead_mode_explanation');
        cy.get('#plan-ahead-mode').check();
        cy.get('#auto-plan-settings').should('not.be.visible');
        
        // Goal Oriented Mode
        cy.narrate('goal_oriented_mode_explanation');
        cy.get('#goal-oriented-mode').check();
        
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
        cy.get('#temperature').invoke('val', 0.5).trigger('input');
        cy.get('#temperature-value').should('contain', '0.5');
        
        // Auto Fix
        cy.narrate('auto_fix_explanation');
        cy.get('#auto-fix').check();
        
        cy.get('#next-to-task-selection').click();
        
        // Step 3: Task Types
        cy.narrate('step3_task_types');
        
        // Insight Task
        cy.narrate('insight_task_explanation');
        cy.get('#task-InsightTask').check();
        
        // File Modification Task
        cy.narrate('file_modification_task_explanation');
        cy.get('#task-FileModificationTask').check();
        
        // Shell Command Task
        cy.narrate('shell_command_task_explanation');
        cy.get('#task-RunShellCommandTask').check();
        
        // Code Execution Task
        cy.narrate('code_execution_task_explanation');
        cy.get('#task-RunCodeTask').check();
        
        // Auto Fix Task
        cy.narrate('auto_fix_task_explanation');
        cy.get('#task-CommandAutoFixTask').check();
        
        // File Search Task
        cy.narrate('file_search_task_explanation');
        cy.get('#task-FileSearchTask').check();
        
        // Web Search Task
        cy.narrate('web_search_task_explanation');
        cy.get('#task-CrawlerAgentTask').check();
        
        // GitHub Search Task
        cy.narrate('github_search_task_explanation');
        cy.get('#task-GitHubSearchTask').check();
        
        cy.get('#next-to-launch').click();
        
        // Step 4: Launch
        cy.narrate('step4_launch');
        
        // Review summaries
        cy.narrate('review_configuration');
        cy.get('#cognitive-mode-summary').should('be.visible');
        cy.get('#task-settings-summary').should('be.visible');
        cy.get('#api-settings-summary').should('be.visible');
        
        // Launch button
        cy.narrate('launch_button_explanation');
        cy.get('#launch-session').should('be.visible');
        
        // Basic Chat alternative
        cy.narrate('basic_chat_alternative');
        cy.get('#open-basic-chat').should('be.visible');
        
        // Conclusion
        cy.narrate('introduction_conclusion');
    });
});