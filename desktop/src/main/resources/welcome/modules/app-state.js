// Application state management
class AppState {
    constructor(dependencies = {}) {
        this.localStorage = dependencies.localStorage || (typeof window !== 'undefined' ? window.localStorage : null);
        this.sessionId = dependencies.sessionId || this.generateSessionId();
        this.cognitiveMode = 'chat';
        this.taskSettings = this.loadTaskSettings();
        this.apiSettings = {apiKeys: {}, apiBase: {}, localTools: []};
    }

    generateSessionId() {
        return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }

    loadTaskSettings() {
        if (!this.localStorage) return this.getDefaultTaskSettings();
        
        try {
            const saved = this.localStorage.getItem('taskSettings');
            if (saved) {
                const parsed = JSON.parse(saved);
                // Ensure taskConfigs exists
                if (!parsed.taskConfigs) {
                    parsed.taskConfigs = {};
                }
                return parsed;
            }
        } catch (e) {
            console.error('[AppState] Error loading task settings:', e);
        }
        return this.getDefaultTaskSettings();
    }

    getDefaultTaskSettings() {
        return {
            defaultModel: 'GPT4o',
            parsingModel: 'GPT4oMini',
            workingDir: '',
            temperature: 0.3,
            autoFix: false,
            graphFile: '',
            maxTaskHistoryChars: 20000,
            maxTasksPerIteration: 3,
            maxIterations: 100,
            taskSettings: {},
            taskConfigs: {} // Store task-specific configurations
        };
    }

    saveTaskSettings(settings = null) {
        if (!this.localStorage) return;
        
        const toSave = settings || this.taskSettings;
        // Ensure taskConfigs exists
        if (!toSave.taskConfigs) {
            toSave.taskConfigs = {};
        }
        this.localStorage.setItem('taskSettings', JSON.stringify(toSave));
        if (settings) {
            this.taskSettings = settings;
        }
    }

    updateTaskSetting(key, value) {
        this.taskSettings[key] = value;
        this.saveTaskSettings();
    }

    updateCognitiveMode(mode) {
        this.cognitiveMode = mode;
    }

    // Task configuration management
    addTaskConfig(taskType, config) {
        if (!this.taskSettings.taskConfigs) {
            this.taskSettings.taskConfigs = {};
        }
        if (!this.taskSettings.taskConfigs[taskType]) {
            this.taskSettings.taskConfigs[taskType] = [];
        }
        
        // Check if config with same name exists
        const existingIndex = this.taskSettings.taskConfigs[taskType].findIndex(
            c => c.name === config.name
        );
        
        if (existingIndex >= 0) {
            // Update existing config
            this.taskSettings.taskConfigs[taskType][existingIndex] = config;
        } else {
            // Add new config
            this.taskSettings.taskConfigs[taskType].push(config);
        }
        
        this.saveTaskSettings();
    }

    removeTaskConfig(taskType, configName) {
        if (!this.taskSettings.taskConfigs || !this.taskSettings.taskConfigs[taskType]) {
            return;
        }
        
        this.taskSettings.taskConfigs[taskType] = this.taskSettings.taskConfigs[taskType].filter(
            c => c.name !== configName
        );
        
        if (this.taskSettings.taskConfigs[taskType].length === 0) {
            delete this.taskSettings.taskConfigs[taskType];
        }
        
        this.saveTaskSettings();
    }

    getTaskConfigs(taskType) {
        if (!this.taskSettings.taskConfigs || !this.taskSettings.taskConfigs[taskType]) {
            return [];
        }
        return this.taskSettings.taskConfigs[taskType];
    }

    getTaskConfig(taskType, configName) {
        const configs = this.getTaskConfigs(taskType);
        return configs.find(c => c.name === configName);
    }

    hasTaskConfigs(taskType) {
        const configs = this.getTaskConfigs(taskType);
        return configs.length > 0;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {AppState};
}