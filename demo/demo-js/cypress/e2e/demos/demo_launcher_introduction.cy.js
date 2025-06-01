describe('Cognotik Launcher - Comprehensive Introduction', () => {
    beforeEach(() => {
        cy.log('DEMO_FLOW: Starting Cognotik Launcher Introduction Demo');
        cy.visit('/');
        cy.clearLocalStorage();
        cy.enableAudioCapture();
        cy.log('DEMO_FLOW: Initial setup complete - visited homepage, cleared storage, enabled audio');
    });

    it('should provide a complete introduction to the Cognotik launcher', () => {
        
        cy.log('DEMO_SECTION: Beginning Introduction Phase');
        cy.narrate('intro_welcome');
        cy.log('NARRATION: Delivered welcome introduction');
        
        cy.log('DEMO_SECTION: Explaining Cognitive/Task Mode Model');
        cy.narrate('cognitive_architecture_intro');
        cy.log('NARRATION: Explained cognitive model overview');
        
        cy.log('DEMO_SECTION: Demonstrating User Settings');
        cy.narrate('workspace_setup');
        cy.log('NARRATION: Introduced user settings concept');
        cy.get('#user-settings-btn').click();
        cy.log('UI_ACTION: Opened user settings modal');
        
        cy.log('DEMO_SUBSECTION: API Keys Configuration');
        cy.narrate('byok_approach_explanation');
        cy.narrate('byok_approach_explanation');
        cy.log('NARRATION: Explained API keys functionality');
        cy.get('[data-tab="api-keys"]').click();
        cy.log('UI_ACTION: Switched to API keys tab');
        cy.wait(1000);
        cy.log('DEMO_FLOW: Waited for tab transition');
        
        cy.get('#api-keys-container').should('be.visible');
        cy.log('UI_VERIFICATION: API keys container is visible');
        cy.narrate('ai_provider_ecosystem');
        cy.narrate('ai_provider_ecosystem');
        cy.log('NARRATION: Provided overview of API providers');

        cy.log('DEMO_SUBSECTION: Configuring API Settings');
        cy.loadTestConfig().then((config) => {
            cy.log('CONFIG: Loaded test configuration for API setup');
            cy.get('#api-key-Anthropic').clear()
            cy.log('UI_ACTION: Cleared existing Anthropic API key field');
            cy.get('#api-key-Anthropic').type(config.apiKeys?.Anthropic || 'test-anthropic-key');
            cy.log('UI_ACTION: Entered Anthropic API key');
            cy.wait(500);
            cy.get('#save-user-settings').click();
            cy.log('UI_ACTION: Saved user settings');
            cy.get('.modal').should('not.be.visible');
            cy.log('UI_VERIFICATION: Settings modal closed successfully');
        });
        cy.log('DEMO_SUBSECTION: Local Tools Configuration');

        cy.get('#user-settings-btn').click();
        cy.log('UI_ACTION: Reopened user settings modal');
        cy.get('[data-tab="local-tools"]').click();
        cy.log('UI_ACTION: Switched to local tools tab');
        cy.get('#local-tools-container').should('be.visible');
        cy.log('UI_VERIFICATION: Local tools container is visible');
        cy.narrate('local_tools_integration');
        cy.log('NARRATION: Explained local tools functionality');

        cy.get('#close-user-settings-modal').click();
        cy.log('UI_ACTION: Closed user settings modal');
        
        cy.log('DEMO_SECTION: Step 1 - Cognitive Modes Configuration');
        cy.narrate('step1_cognitive_modes');
        cy.log('NARRATION: Introduced cognitive modes step');
        
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(1) > :nth-child(1)').click()
        cy.log('UI_ACTION: Selected chat mode option');
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(1) > :nth-child(1) > .tooltip').click();
        cy.log('UI_ACTION: Opened chat mode tooltip');
        cy.narrate('chat_mode_capabilities');
        cy.log('NARRATION: Explained chat mode functionality');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(2) > :nth-child(1) > .tooltip').click()
        cy.log('UI_ACTION: Opened autonomous mode tooltip');
        cy.narrate('autonomous_mode_capabilities');
        cy.log('NARRATION: Explained autonomous mode functionality');
        cy.get('#auto-plan-mode').check();
        cy.log('UI_ACTION: Selected autonomous plan mode');
        cy.narrate('autonomous_control_parameters');
        cy.narrate('autonomous_control_parameters');
        cy.log('NARRATION: Explained autonomous settings');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(3) > :nth-child(1)').click()
        cy.log('UI_ACTION: Selected plan ahead mode option');
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(3) > :nth-child(1) > .tooltip').click()
        cy.log('UI_ACTION: Opened plan ahead mode tooltip');
        cy.narrate('plan_ahead_mode_capabilities');
        cy.log('NARRATION: Explained plan ahead mode functionality');

        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(4) > :nth-child(1)').click()
        cy.log('UI_ACTION: Selected goal oriented mode option');
        cy.get('#cognitive-mode > :nth-child(4) > :nth-child(4) > :nth-child(1) > .tooltip').click()
        cy.log('UI_ACTION: Opened goal oriented mode tooltip');
        cy.narrate('goal_oriented_mode_capabilities');
        cy.log('NARRATION: Explained goal oriented mode functionality');

        cy.log('DEMO_FLOW: Returning to Chat mode for demonstration');
        cy.get('#single-task-mode').check();
        cy.log('UI_ACTION: Selected single task (chat) mode');
        cy.get('#next-to-task-settings').click();
        cy.log('UI_ACTION: Proceeded to task settings step');
        
        cy.log('DEMO_SECTION: Step 2 - Task Settings Configuration');
        cy.narrate('step2_task_settings');
        cy.log('NARRATION: Introduced task settings step');

        // TODO: The models should be called smart / fast
        cy.log('DEMO_SUBSECTION: Model Selection');
        cy.narrate('ai_model_selection');
        cy.log('NARRATION: Explained model selection options');
        cy.get('#model-selection').should('be.visible');
        cy.log('UI_VERIFICATION: Model selection dropdown is visible');
        cy.get('#parsing-model').should('be.visible');
        cy.log('UI_VERIFICATION: Parsing model selection is visible');
        
        cy.log('DEMO_SUBSECTION: Working Directory Configuration');
        cy.narrate('working_directory_concept');
        cy.log('NARRATION: Explained working directory concept');
        cy.get('#working-dir').should('be.visible');
        cy.log('UI_VERIFICATION: Working directory field is visible');
        cy.get('#generate-working-dir').click();
        cy.log('UI_ACTION: Generated working directory');
        cy.narrate('generate_directory_demo');
        cy.log('NARRATION: Demonstrated directory generation');
        
        cy.log('DEMO_SUBSECTION: Temperature Setting');
        cy.narrate('temperature_control');
        cy.log('NARRATION: Explained temperature parameter');
        cy.get('#temperature').invoke('val', 0.2).trigger('input');
        cy.log('UI_ACTION: Set temperature to 0.2');
        cy.get('#temperature-value').should('contain', '0.2');
        cy.log('UI_VERIFICATION: Temperature value updated to 0.2');
        
        cy.log('DEMO_SUBSECTION: Auto Fix Configuration');
        cy.narrate('auto_fix_automation');
        cy.log('NARRATION: Explained auto fix functionality');
        cy.get('#auto-fix').check();
        cy.log('UI_ACTION: Enabled auto fix option');
        
        cy.get('#next-to-task-selection').click();
        cy.log('UI_ACTION: Proceeded to task selection step');
        
        cy.log('DEMO_SECTION: Step 3 - Task Types Selection');
        cy.narrate('step3_task_types');
        cy.log('NARRATION: Introduced task types step');
        
        cy.log('DEMO_SUBSECTION: Insight Task');
        cy.get('#task-toggles > :nth-child(1) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened insight task tooltip');
        cy.narrate('insight_analysis_capabilities');
        cy.log('NARRATION: Explained insight task functionality');
        //cy.get('#task-InsightTask').check();
        
        cy.log('DEMO_SUBSECTION: File Modification Task');
        cy.get('#task-toggles > :nth-child(2) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened file modification task tooltip');
        cy.narrate('file_modification_capabilities');
        cy.log('NARRATION: Explained file modification task functionality');
        // cy.get('#task-FileModificationTask').check();

        cy.log('DEMO_SUBSECTION: Shell Command Task');
        cy.get('#task-toggles > :nth-child(3) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened shell command task tooltip');
        cy.narrate('shell_command_capabilities');
        cy.narrate('shell_command_capabilities');
        cy.log('NARRATION: Explained shell command task functionality');
        // cy.get('#task-RunShellCommandTask').check();

        cy.log('DEMO_SUBSECTION: Code Execution Task');
        cy.get('#task-toggles > :nth-child(4) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened code execution task tooltip');
        cy.narrate('code_execution_capabilities');
        cy.narrate('code_execution_capabilities');
        cy.log('NARRATION: Explained code execution task functionality');
        // cy.get('#task-RunCodeTask').check();

        cy.log('DEMO_SUBSECTION: Auto Fix Task');
        cy.get('#task-toggles > :nth-child(5) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened auto fix task tooltip');
        cy.narrate('auto_fix_debugging_capabilities');
        cy.narrate('auto_fix_debugging_capabilities');
        cy.log('NARRATION: Explained auto fix task functionality');
        // cy.get('#task-CommandAutoFixTask').check();

        cy.log('DEMO_SUBSECTION: File Search Task');
        cy.get('#task-toggles > :nth-child(6) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened file search task tooltip');
        cy.narrate('file_search_capabilities')
        cy.narrate('file_search_capabilities')
        cy.log('NARRATION: Explained file search task functionality');
        // cy.get('#task-FileSearchTask').check();

        cy.log('DEMO_SUBSECTION: Web Search Task');
        cy.get('#task-toggles > :nth-child(7) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened web search task tooltip');
        cy.narrate('web_search_capabilities');
        cy.narrate('web_search_capabilities');
        cy.log('NARRATION: Explained web search task functionality');
        // cy.get('#task-CrawlerAgentTask').check();

        cy.log('DEMO_SUBSECTION: GitHub Search Task');
        cy.get('#task-toggles > :nth-child(8) > div > .tooltip').click();
        cy.log('UI_ACTION: Opened GitHub search task tooltip');
        cy.narrate('github_search_capabilities');
        cy.narrate('github_search_capabilities');
        cy.log('NARRATION: Explained GitHub search task functionality');
        //cy.get('#task-GitHubSearchTask').check();

        cy.get('#next-to-launch').click();
        cy.log('UI_ACTION: Proceeded to launch step');
        cy.log('DEMO_SECTION: Step 4 - Launch Configuration');
        cy.narrate('step4_launch');
        cy.log('NARRATION: Introduced launch step');
        cy.narrate('review_configuration');
        cy.log('NARRATION: Explained configuration review');
        cy.narrate('basic_chat_option');
        cy.log('NARRATION: Explained basic chat alternative');
        cy.narrate('introduction_conclusion');
        cy.log('NARRATION: Delivered conclusion');
        cy.log('DEMO_FLOW: Cognotik Launcher Introduction Demo completed successfully');
    });
});