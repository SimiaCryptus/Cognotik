class UserSettingsModal {
    // Selectors
    get modal() {
        return cy.get('#user-settings-modal');
    }

    get saveButton() {
        return cy.get('#save-user-settings');
    }

    get cancelButton() {
        return cy.get('#cancel-user-settings');
    }

    // API Key inputs
    getApiKeyInput(provider) {
        return cy.get(`#api-key-${provider}`);
    }

    // Methods
    setApiKey(provider, key) {
        this.getApiKeyInput(provider).clear().type(key);
        return this;
    }

    setApiKeyFromConfig(provider) {
        cy.loadTestConfig().then((config) => {
            const key = config.apiKeys?.[provider] || `test-${provider.toLowerCase()}-key`;
            this.setApiKey(provider, key);
        });
        return this;
    }

    setMultipleApiKeys(keys) {
        Object.entries(keys).forEach(([provider, key]) => {
            this.setApiKey(provider, key);
        });
        return this;
    }

    setMultipleApiKeysFromConfig(providers) {
        cy.loadTestConfig().then((config) => {
            providers.forEach(provider => {
                const key = config.apiKeys?.[provider] || `test-${provider.toLowerCase()}-key`;
                this.setApiKey(provider, key);
            });
        });
        return this;
    }

    save() {
        this.saveButton.click();
        return this;
    }

    cancel() {
        this.cancelButton.click();
        return this;
    }

    shouldBeVisible() {
        this.modal.should('be.visible');
        return this;
    }

    shouldNotBeVisible() {
        this.modal.should('not.be.visible');
        return this;
    }
}

export default UserSettingsModal;