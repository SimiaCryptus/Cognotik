// UI management module
class UIManager {
    constructor(dependencies = {}) {
        this.document = dependencies.document || document;
        this.appState = dependencies.appState;
        this.httpService = dependencies.httpService;
        this.notificationService = dependencies.notificationService;
    }

    setupTooltips() {
        console.log('[setupTooltips] Called');

        this.document.querySelectorAll('.tooltip').forEach(tooltip => {
            tooltip.addEventListener('click', (e) => {
                e.stopPropagation();
                console.log('[setupTooltips] Tooltip clicked');

                this.document.querySelectorAll('.tooltip.active').forEach(activeTooltip => {
                    if (activeTooltip !== tooltip) {
                        activeTooltip.classList.remove('active');
                    }
                });

                tooltip.classList.toggle('active');
            });
        });

        this.document.addEventListener('click', (e) => {
            if (!e.target.closest('.tooltip')) {
                console.log('[setupTooltips] Clicked outside tooltip, closing all');
                this.document.querySelectorAll('.tooltip.active').forEach(tooltip => {
                    tooltip.classList.remove('active');
                });
            }
        });
    }

    navigateToStep(stepId) {
        console.log(`[navigateToStep] Called with stepId: ${stepId}`);

        this.document.querySelectorAll('.wizard-content').forEach(el => el.classList.remove('active'));

        const stepContent = this.document.getElementById(stepId);
        if (stepContent) {
            stepContent.classList.add('active');
            console.log(`[navigateToStep] Activated content for step: ${stepId}`);
        } else {
            console.warn(`[navigateToStep] Content element for step ${stepId} not found.`);
        }

        this.document.querySelectorAll('.wizard-step').forEach(step => step.classList.remove('active'));
        const navStep = this.document.querySelector(`.wizard-step[data-step="${stepId}"]`);
        if (navStep) navStep.classList.add('active');
        console.log(`[navigateToStep] Updated wizard navigation for step: ${stepId}`);
    }

    updateLaunchSummaries() {
        const mode = this.appState.cognitiveMode;
        const modeMap = {
            'single-task': 'Chat',
            'auto-plan': 'Autonomous',
            'plan-ahead': 'Plan Ahead',
            'goal-oriented': 'Goal Oriented',
            'graph': 'Graph Mode'
        };

        const cognitiveModeSummary = this.document.getElementById('cognitive-mode-summary');
        if (cognitiveModeSummary) {
            cognitiveModeSummary.textContent = modeMap[mode] || mode;
        }

        let summary = '';
        summary += 'Default Model: ' + (this.appState.taskSettings.defaultModel || '-') + '\n';
        summary += 'Parsing Model: ' + (this.appState.taskSettings.parsingModel || '-') + '\n';
        summary += 'Working Directory: ' + (this.appState.taskSettings.workingDir || '-') + '\n';
        summary += 'Temperature: ' + (this.appState.taskSettings.temperature ?? '-') + '\n';
        summary += 'Auto Fix: ' + (this.appState.taskSettings.autoFix ? 'Enabled' : 'Disabled') + '\n';

        const taskSettingsSummary = this.document.getElementById('task-settings-summary');
        if (taskSettingsSummary) {
            taskSettingsSummary.textContent = summary;
        }

        let apiSummary = '';
        if (this.appState.apiSettings.apiKeys) {
            for (const [provider, key] of Object.entries(this.appState.apiSettings.apiKeys)) {
                if (key) {
                    apiSummary += provider + ': Configured\n';
                }
            }
        }

        const apiSettingsSummary = this.document.getElementById('api-settings-summary');
        if (apiSettingsSummary) {
            apiSettingsSummary.textContent = apiSummary || 'No API keys configured.';
        }
    }

    showNotification(message, type = 'info') {
        console.log(`[showNotification] Called with message: "${message}", type: "${type}"`);

        if (type === 'error') {
            console.error(`[showNotification] Error: ${message}`);
            alert('❌ ' + message);
        } else if (type === 'success') {
            console.log(`[showNotification] Success: ${message}`);
            alert('✅ ' + message);
        } else {
            console.info(`[showNotification] Info: ${message}`);
            alert(message);
        }
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {UIManager};
}