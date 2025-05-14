/**
 * Type definitions for the Cognotik AI Task Tester
 */
/**
 * Task Type Test Configuration
 * Defines the configuration for testing a specific task type
 */
export interface TaskTypeTest {
    taskId: string;
    displayName: string;
    description: string;
    requiredApiProviders: string[];
    testPrompts: string[];
    expectedOutputPatterns: RegExp[];
    setupInstructions?: string;
    timeoutSeconds: number;
    successCriteria: string[];
}
/**
 * Test Session Configuration
 * Defines the configuration for a test session
 */
export interface TestSessionConfig {
    sessionId: string;
    cognitiveMode: 'single-task';
    defaultModel: string;
    parsingModel: string;
    workingDir: string;
    temperature: number;
    autoFix: boolean;
    taskSettings: {
        [taskId: string]: {
            enabled: boolean;
            task_type: string;
        };
    };
}
/**
 * Browser Session
 * Represents a browser session for automation
 */
export interface BrowserSession {
    browser: any;
    page: any;
    context: any;
    sessionId: string;
    workingDir: string;
    close: () => Promise<void>;
}
/**
 * Prompt Result
 * Result of executing a single prompt
 */
export interface PromptResult {
    prompt: string;
    response: string;
    responseTime: number;
    matchedPatterns: string[];
    matchedPattern?: string;
    screenshots: string[];
    result: 'PASS' | 'FAIL';
    error?: string;
}
/**
 * Validation Result
 * Result of validating task output against expected patterns
 */
export interface ValidationResult {
    isValid: boolean;
    matchedPatterns: string[];
    unmatchedPatterns: string[];
    details: string;
}
/**
 * Test Results
 * Complete results of a test run
 */
export interface TestResults {
    taskId: string;
    displayName: string;
    timestamp: string;
    duration: number;
    sessionId: string;
    workingDir: string;
    overallResult: 'PASS' | 'FAIL' | 'ERROR';
    promptResults: PromptResult[];
    successCriteriaMet: string[];
    successCriteriaFailed: string[];
    artifacts: string[];
    logs: string[];
    errorDetails?: string;
}
/**
 * Test Report
 * Structured report of test results
 */
export interface TestReport {
    taskId: string;
    displayName: string;
    timestamp: string;
    duration: number;
    sessionId: string;
    workingDir: string;
    overallResult: 'PASS' | 'FAIL' | 'ERROR';
    promptResults: Array<{
        prompt: string;
        response: string;
        responseTime: number;
        matchedPatterns: string[];
        screenshots: string[];
        result: 'PASS' | 'FAIL';
    }>;
    successCriteriaMet: string[];
    successCriteriaFailed: string[];
    artifacts: string[];
    logs: string[];
    errorDetails?: string;
}
/**
 * Report Format
 * Available formats for test reports
 */
export type ReportFormat = 'html' | 'json' | 'pdf' | 'console';
/**
 * Report Options
 * Options for generating reports
 */
export interface ReportOptions {
    formats: ReportFormat[];
    outputDir: string;
    includeScreenshots?: boolean;
    includeLogs?: boolean;
    sanitizeResponses?: boolean;
}
/**
 * Error Category
 * Categories of errors that can occur during testing
 */
export type ErrorCategory = 'CONFIGURATION_ERROR' | 'LAUNCH_ERROR' | 'EXECUTION_ERROR' | 'VALIDATION_ERROR' | 'SYSTEM_ERROR';
/**
 * Test Error
 * Structured error information
 */
export interface TestError {
    category: ErrorCategory;
    message: string;
    details?: string;
    stack?: string;
    recoverable: boolean;
    screenshot?: string;
}
/**
 * CLI Options
 * Command line interface options
 */
export interface CliOptions {
    taskId?: string;
    all?: boolean;
    reportDir: string;
    headless: boolean;
    apiConfig?: string;
    timeout?: number;
    verbose: boolean;
}
/**
 * Browser Type
 * Supported browser types for testing
 */
export type BrowserType = 'chromium' | 'firefox' | 'webkit';
/**
 * Error Recovery Strategy
 * Strategies for recovering from errors during testing
 */
export type ErrorRecoveryStrategy = 'RETRY' | 'ABORT' | 'CONTINUE_WITH_WARNING';
/**
 * Report
 * Generic report interface for summary reports
 */
export interface Report {
    title: string;
    timestamp: string;
    summary: {
        total: number;
        passed: number;
        failed: number;
        error: number;
    };
    taskReports: TestReport[];
    duration: number;
}
