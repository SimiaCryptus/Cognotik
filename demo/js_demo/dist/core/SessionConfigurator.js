"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SessionConfigurator = void 0;
class SessionConfigurator {
    /**
     * Configure a test session for a specific task type
     * @param taskType The task type to test
     * @param sessionId The session ID
     * @param workingDir The working directory for the test
     * @returns The test session configuration
     */
    configureTestSession(taskType, sessionId, workingDir) {
        // Create a session configuration with only the target task type enabled
        const config = {
            sessionId,
            cognitiveMode: 'single-task',
            defaultModel: 'gpt-4',
            parsingModel: 'gpt-3.5-turbo',
            workingDir,
            temperature: 0.2,
            autoFix: true,
            taskSettings: {}
        };
        // Enable only the target task type
        config.taskSettings[taskType.taskId] = {
            enabled: true,
            task_type: taskType.taskId
        };
        return config;
    }
}
exports.SessionConfigurator = SessionConfigurator;
//# sourceMappingURL=SessionConfigurator.js.map