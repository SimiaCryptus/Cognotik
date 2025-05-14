/// <reference types="node" />
/// <reference types="node" />
import { Page } from 'playwright';
import { TestReport, ValidationResult, TaskTypeTest } from '../types';
export declare class ResultCollector {
    private workingDir;
    private taskId;
    private sessionId;
    private startTime;
    private promptResults;
    private successCriteriaMet;
    private successCriteriaFailed;
    private artifacts;
    private logs;
    private errorDetails?;
    constructor(taskId: string, sessionId: string, workingDir: string);
    /**
     * Captures a screenshot of the current page state
     */
    captureScreenshot(page: Page, name: string): Promise<string>;
    /**
     * Records the result of a prompt execution
     */
    recordPromptResult(prompt: string, response: string, responseTime: number, validationResult: ValidationResult, screenshots: string[]): void;
    /**
     * Records success criteria results
     */
    recordSuccessCriteria(criteria: string, met: boolean): void;
    /**
     * Saves an artifact file to the working directory
     */
    saveArtifact(content: string | Buffer, filename: string, type: string): string;
    /**
     * Collects files from the working directory that match a pattern
     */
    collectArtifactsFromWorkingDir(pattern: RegExp): string[];
    /**
     * Records an error that occurred during test execution
     */
    recordError(error: Error | string): void;
    /**
     * Adds a log entry
     */
    log(message: string, level?: 'INFO' | 'WARNING' | 'ERROR'): void;
    /**
     * Generates the final test report
     */
    generateReport(taskConfig: TaskTypeTest, displayName: string): TestReport;
    /**
     * Captures the final application state
     */
    captureFinalState(page: Page): Promise<void>;
    /**
     * Collects performance metrics from the page
     */
    collectPerformanceMetrics(page: Page): Promise<void>;
    /**
     * Cleans up temporary files and resources
     */
    cleanup(): void;
}
