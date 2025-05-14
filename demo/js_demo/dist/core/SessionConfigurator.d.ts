import { TaskTypeTest, TestSessionConfig } from '../types';
export declare class SessionConfigurator {
    /**
     * Configure a test session for a specific task type
     * @param taskType The task type to test
     * @param sessionId The session ID
     * @param workingDir The working directory for the test
     * @returns The test session configuration
     */
    configureTestSession(taskType: TaskTypeTest, sessionId: string, workingDir: string): TestSessionConfig;
}
