// State management module
class AppState {
    constructor(dependencies = {}) {
        this.localStorage = dependencies.localStorage || window.localStorage;
        this.sessionId = dependencies.sessionId || null;
        this.apiSettings = {apiKeys: {}, apiBase: {}, localTools: []};
        this.taskSettings = this.initializeTaskSettings();
        this.cognitiveMode = this.localStorage.getItem('cognitiveMode') || 'single-task';
    }

    initializeTaskSettings() {
        return {
            defaultModel: this.localStorage.getItem('defaultModel') || 'GPT4o',
            parsingModel: this.localStorage.getItem('parsingModel') || 'GPT4oMini',
            imageModel: this.localStorage.getItem('imageModel') || '',
            workingDir: this.localStorage.getItem('workingDir') || '.',
            autoFix: this.localStorage.getItem('autoFix') === 'true',
            temperature: parseFloat(this.localStorage.getItem('temperature')) || 0.2,
            maxTaskHistoryChars: parseInt(this.localStorage.getItem('maxTaskHistoryChars')) || 20000,
            maxTasksPerIteration: parseInt(this.localStorage.getItem('maxTasksPerIteration')) || 3,
            maxIterations: parseInt(this.localStorage.getItem('maxIterations')) || 100,
            graphFile: this.localStorage.getItem('graphFile') || '',
            taskSettings: this.loadTaskSettings(),
        };
    }

    loadTaskSettings() {
        const saved = this.localStorage.getItem('taskSettings');
        if (saved) {
            try {
                const parsed = JSON.parse(saved);
                // Ensure it's an object
                return typeof parsed === 'object' && parsed !== null ? parsed : {};
            } catch (e) {
                console.error('Error parsing taskSettings:', e);
                return {};
            }
        }
        return {};
    }

    updateTaskSetting(key, value) {
        if (!key || value === undefined) {
            console.warn('[updateTaskSetting] Invalid key or value:', key, value);
            return;
        }
        this.taskSettings[key] = value;
        try {
            this.localStorage.setItem(key, String(value));
        } catch (error) {
            console.error('[updateTaskSetting] Error saving to localStorage:', error);
        }
    }

    updateCognitiveMode(mode) {
        if (!mode) {
            console.warn('[updateCognitiveMode] Invalid mode:', mode);
            return;
        }
        this.cognitiveMode = mode;
        try {
            this.localStorage.setItem('cognitiveMode', mode);
        } catch (error) {
            console.error('[updateCognitiveMode] Error saving to localStorage:', error);
        }
    }

    saveTaskSettings(taskSettingsObj) {
        if (typeof taskSettingsObj !== 'object' || taskSettingsObj === null) {
            console.warn('[saveTaskSettings] Invalid taskSettings:', taskSettingsObj);
            return;
        }
        this.taskSettings.taskSettings = taskSettingsObj;
        try {
            this.localStorage.setItem('taskSettings', JSON.stringify(taskSettingsObj));
        } catch (error) {
            console.error('[saveTaskSettings] Error saving to localStorage:', error);
        }
    }

    generateTimestampedDirectory() {
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const seconds = String(now.getSeconds()).padStart(2, '0');
        return `sessions/${year}${month}${day}${hours}${minutes}${seconds}`;
    }

    generateCognotikWorkingDir() {
        const now = new Date();
        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');
        const hours = String(now.getHours()).padStart(2, '0');
        const minutes = String(now.getMinutes()).padStart(2, '0');
        const seconds = String(now.getSeconds()).padStart(2, '0');
        const timestamp = `${year}${month}${day}-${hours}${minutes}${seconds}`;
        const platform = navigator.platform.toLowerCase();
        let baseDir;
        if (platform.includes('win')) {
            baseDir = '~\\Documents\\Cognotik';
        } else if (platform.includes('mac')) {
            baseDir = '~/Documents/Cognotik';
        } else {
            baseDir = '~/Cognotik';
        }
        return `${baseDir}/session-${timestamp}`;
    }
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {AppState};
}