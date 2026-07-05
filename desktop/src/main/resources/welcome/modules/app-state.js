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
                // Ensure taskSettings exists
                if (!parsed.taskSettings) {
                    parsed.taskSettings = {};
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
            smartModel: 'GPT4o',
            fastModel: 'GPT4oMini',
            imageModel: '',
            workingDir: '',
            temperature: 0.3,
            autoFix: false,
            graphFile: '',
            maxTaskHistoryChars: 20000,
            maxTasksPerIteration: 3,
            maxIterations: 100,
            taskSettings: {},
        };
    }

    saveTaskSettings(settings = null) {
        if (!this.localStorage) return;
        
        const toSave = settings || this.taskSettings;
        // Ensure taskSettings exists
        if (!toSave.taskSettings) {
            toSave.taskSettings = {};
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
        if (!this.taskSettings.taskSettings) {
            this.taskSettings.taskSettings = {};
        }
        
        // Use config name as key, store the config directly
        const configKey = config.name;
        
        // Store config with task_type included
        this.taskSettings.taskSettings[configKey] = {
            ...config,
            task_type: taskType
        };
        
        this.saveTaskSettings();
    }

    removeTaskConfig(taskType, configName) {
        if (!this.taskSettings.taskSettings || !this.taskSettings.taskSettings[taskType]) {
            return;
        }
        
        
        delete this.taskSettings.taskSettings[configName];
        
        this.saveTaskSettings();
    }

    getTaskConfigs(taskType) {
        if (!this.taskSettings.taskSettings || !this.taskSettings.taskSettings[taskType]) {
            return {};
        }
        
        // Filter configs by task_type
        const configs = {};
        for (const [configName, config] of Object.entries(this.taskSettings.taskSettings)) {
            if (config.task_type === taskType) {
                configs[configName] = config;
            }
        }
        return configs;
    }

    getTaskConfig(taskType, configName) {
        if (!this.taskSettings.taskSettings || !this.taskSettings.taskSettings[configName]) {
            return null;
        }
        
        const config = this.taskSettings.taskSettings[configName];
        // Verify it matches the task type
        if (config.task_type === taskType) {
            return config;
        }
        return null;
    }

    hasTaskConfigs(taskType) {
        const configs = this.getTaskConfigs(taskType);
        return Object.keys(configs).length > 0;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {AppState};
}