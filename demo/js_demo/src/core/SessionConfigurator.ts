import { v4 as uuidv4 } from 'uuid';
import path from 'path';
import fs from 'fs';
import { TaskTypeTest, TestSessionConfig } from '../types';
export class SessionConfigurator {
  /**
   * Configure a test session for a specific task type
   * @param taskType The task type to test
   * @param sessionId The session ID
   * @param workingDir The working directory for the test
   * @returns The test session configuration
   */
  configureTestSession(
    taskType: TaskTypeTest,
    sessionId: string,
    workingDir: string
  ): TestSessionConfig {
    // Create a session configuration with only the target task type enabled
    const config: TestSessionConfig = {
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