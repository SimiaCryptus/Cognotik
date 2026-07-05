// Validation service module
class ValidationService {
    constructor(dependencies = {}) {
        this.appState = dependencies.appState;
        this.notificationService = dependencies.notificationService;
    }

    validateConfiguration() {
        console.log('[validateConfiguration] Called');

        if (!this.validateApiKeys()) {
            return false;
        }

        if (!this.validateEnabledTasks()) {
            return false;
        }

        console.log('[validateConfiguration] Configuration is valid.');
        return true;
    }

    validateApiKeys() {
        let hasApiKey = false;
        if (this.appState.apiSettings.apiKeys) {
            console.log('[validateConfiguration] Checking API keys:', Object.keys(this.appState.apiSettings.apiKeys));
            for (const key of Object.values(this.appState.apiSettings.apiKeys)) {
                if (key) {
                    hasApiKey = true;
                    break;
                }
            }
        }

        if (!hasApiKey) {
            console.warn('[validateConfiguration] No API key configured.');
            this.notificationService.showNotification('Please configure at least one API key before launching', 'error');
            return false;
        }

        console.log('[validateConfiguration] API key check passed.');
        return true;
    }

    validateEnabledTasks() {
        let hasEnabledTask = false;
        const taskSettings = this.appState.taskSettings;
        if (typeof taskSettings === 'object' && taskSettings !== null) {
            console.log('[validateConfiguration] Checking enabled tasks:', taskSettings);
            for (const [taskId, settings] of Object.entries(taskSettings)) {
                console.log(`[validateConfiguration] Task ${taskId}`);
                if (settings) {
                    hasEnabledTask = true;
                    break;
                }
            }
        }

        if (!hasEnabledTask) {
            console.warn('[validateConfiguration] No task enabled.');
            this.notificationService.showNotification('Please enable at least one task before launching', 'error');
            return false;
        }

        console.log('[validateConfiguration] Enabled task check passed.');
        return true;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {ValidationService};
}