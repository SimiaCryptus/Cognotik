import { TaskTypeTest, TestReport, ReportOptions } from '../types';
import { SessionConfigurator } from './SessionConfigurator';
import { BrowserAutomation } from './BrowserAutomation';
import { ResultCollector } from './ResultCollector';
import { ReportGenerator } from './ReportGenerator';
import { ErrorHandler } from './ErrorHandler';
export declare class TestRunner {
    private sessionConfigurator;
    private browserAutomation;
    private resultCollector;
    private reportGenerator;
    private errorHandler;
    private baseUrl;
    private workingDir;
    private maxRetries;
    constructor(options: {
        baseUrl: string;
        workingDir: string;
        maxRetries?: number;
        sessionConfigurator?: SessionConfigurator;
        browserAutomation?: BrowserAutomation;
        resultCollector?: ResultCollector;
        reportGenerator?: ReportGenerator;
        errorHandler?: ErrorHandler;
        apiConfigPath?: string;
        verbose?: boolean;
    });
    /**
     * Run a test for a specific task type
     * @param taskId The ID of the task type to test
     * @param taskConfig The task type configuration
     * @returns A promise that resolves to the test report
     */
    runTaskTest(taskId: string, taskConfig?: TaskTypeTest): Promise<TestReport>;
    /**
     * Run tests for all available task types
     * @returns A promise that resolves to an array of test reports
     */
    runAllTaskTests(): Promise<TestReport[]>;
    /**
     * Generate reports in various formats
     * @param report The test report to generate reports for
     * @param options Report generation options
     */
    generateReports(report: TestReport, options: {
        formats: Array<'html' | 'json' | 'pdf'>;
        outputDir: string;
    } & Partial<ReportOptions>): Promise<void>;
    /**
     * Generate a summary report for multiple test reports
     * @param reports The test reports to summarize
     * @returns The summary report
     */
    generateSummaryReport(reports: TestReport[]): any;
    /**
     * Load task configuration for a specific task type
     * @param taskId The ID of the task type
     * @returns The task configuration
     * @private
     */
    private loadTaskConfig;
    /**
     * Load all available task configurations
     * @returns An array of task configurations
     * @private
     */
    private loadAllTaskConfigs;
    /**
     * Verify that required API providers are configured
     * @param requiredProviders Array of required API provider names
     * @private
     */
    private verifyApiProviders;
    /**
     * Execute a sequence of prompts in the browser session
     * @param browserSession The browser session
     * @param prompts Array of prompts to execute
     * @param expectedPatterns Array of expected output patterns
     * @param timeoutSeconds Maximum timeout for each prompt
     * @returns Array of prompt results
     * @private
     */
    private executePromptSequence;
    /**
     * Validate task output against expected patterns
     * @param output The output to validate
     * @param patterns Array of expected patterns
     * @returns Validation result
     * @private
     */
    private validateTaskOutput;
    /**
     * Collect artifacts from the test working directory
     * @param workingDir The working directory
     * @returns Array of artifact paths
     * @private
     */
    private collectArtifacts;
    /**
     * Create a test report from test results
     * @param options Options for creating the test report
     * @returns The test report
     * @private
     */
    private createTestReport;
}
