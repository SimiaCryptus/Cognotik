// cypress/support/page-objects/WelcomePage.js
class WelcomePage {
    // Selectors
    get userSettingsBtn() { return cy.get('#user-settings-btn'); }
    get cognitiveMode() { return cy.get('#cognitive-mode'); }
    get autoPlanMode() { return cy.get('input[name="cognitive-mode"][value="auto-plan"]'); }
    get basicChatBtn() { return cy.get('#open-basic-chat'); }
    get nextToTaskSettings() { return cy.get('#next-to-task-settings'); }
    get nextToTaskSelection() { return cy.get('#next-to-task-selection'); }
    get nextToLaunch() { return cy.get('#next-to-launch'); }
    get launchSession() { return cy.get('#launch-session'); }

    // Auto-plan settings
    get maxTaskHistory() { return cy.get('#max-task-history'); }
    get maxTasksPerIteration() { return cy.get('#max-tasks-per-iteration'); }

    // Task settings
    get modelSelection() { return cy.get('#model-selection'); }
    get parsingModel() { return cy.get('#parsing-model'); }
    get workingDir() { return cy.get('#working-dir'); }
    get temperature() { return cy.get('#temperature'); }
    get temperatureValue() { return cy.get('#temperature-value'); }
    get autoFix() { return cy.get('#auto-fix'); }

    // Methods
    visit() {
        cy.visit('/welcome');
        return this;
    }

    openUserSettings() {
        this.userSettingsBtn.click();
        return this;
    }

    selectCognitiveMode(mode) {
        cy.get(`input[name="cognitive-mode"][value="${mode}"]`).check();
        return this;
    }

    configureAutoPlanSettings(maxHistory = 15000, maxTasks = 5) {
        this.maxTaskHistory.clear().type(maxHistory.toString());
        this.maxTasksPerIteration.clear().type(maxTasks.toString());
        return this;
    }

    configureTaskSettings(settings = {}) {
        if (settings.model) {
            this.modelSelection.select(settings.model);
        }
        if (settings.parsingModel) {
            this.parsingModel.select(settings.parsingModel);
        }
        if (settings.workingDir) {
            this.workingDir.clear().type(settings.workingDir);
        }
        if (settings.temperature !== undefined) {
            this.temperature.invoke('val', settings.temperature).trigger('input');
        }
        if (settings.autoFix) {
            this.autoFix.check();
        }
        return this;
    }

    selectTask(taskName) {
        cy.get(`#task-${taskName}`).check();
        return this;
    }

    selectTasks(tasks) {
        tasks.forEach(task => this.selectTask(task));
        return this;
    }

    navigateToTaskSettings() {
        this.nextToTaskSettings.click();
        return this;
    }

    navigateToTaskSelection() {
        this.nextToTaskSelection.click();
        return this;
    }

    navigateToLaunch() {
        this.nextToLaunch.click();
        return this;
    }

    launch() {
        this.launchSession.click();
        return this;
    }

    openBasicChat() {
        this.basicChatBtn.click();
        return this;
    }
}

export default WelcomePage;
