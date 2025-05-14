/**
 * Cognotik AI Task Type Tester
 * Main entry point for programmatic usage
 */
import { TestRunner } from './core/TestRunner';
import { SessionConfigurator } from './core/SessionConfigurator';
import { BrowserAutomation } from './core/BrowserAutomation';
import { ResultCollector } from './core/ResultCollector';
import { ReportGenerator } from './core/ReportGenerator';
import { ErrorHandler } from './core/ErrorHandler';
import * as taskTypes from './taskTypes';
import * as utils from './utils';
import { TaskTypeTest, TestSessionConfig, TestReport, PromptResult, ValidationResult, BrowserSession, TestResults, ReportOptions } from './types';
declare class CognotikTaskTester {
    private testRunner;
    private reportGenerator;
    constructor(options?: {
        baseUrl?: string;
        apiConfigPath?: string;
        reportDir?: string;
        headless?: boolean;
        verbose?: boolean;
    });
    /**
     * Run a test for a specific task type
     * @param taskId The ID of the task type to test
     * @returns A promise that resolves to the test report
     */
    runTaskTest(taskId: string): Promise<TestReport>;
    /**
     * Run tests for all available task types
     * @returns A promise that resolves to an array of test reports
     */
    runAllTaskTests(): Promise<TestReport[]>;
    /**
     * Generate reports in the specified formats
     * @param report The test report to generate reports for
     * @param options Report generation options
     */
    generateReports(report: TestReport, options: {
        formats: Array<'html' | 'json' | 'pdf' | 'console'>;
        outputDir?: string;
    } & Partial<ReportOptions>): Promise<void>;
    /**
     * Generate a summary report for multiple test reports
     * @param reports The test reports to summarize
     * @returns A summary report
     */
    generateSummaryReport(reports: TestReport[]): any;
    /**
     * Save a summary report to a file
     * @param summary The summary report to save
     * @param outputPath The path to save the report to
     * @returns A promise that resolves when the report is saved
     */
    saveSummaryReport(summary: any, outputPath: string): Promise<void>;
    /**
     * Get all available task types
     * @returns An array of task type definitions
     */
    getAvailableTaskTypes(): TaskTypeTest[];
    /**
     * Get a specific task type by ID
     * @param taskId The ID of the task type
     * @returns The task type definition or undefined if not found
     */
    getTaskType(taskId: string): TaskTypeTest | undefined;
    /**
     * Get the output directory for reports
     * @returns The output directory path
     */
    getReportOutputDir(): string;
}
export { CognotikTaskTester };
export { TestRunner, SessionConfigurator, BrowserAutomation, ResultCollector, ReportGenerator, ErrorHandler };
export { taskTypes };
export { utils };
export type { TaskTypeTest, TestSessionConfig, TestReport, PromptResult, ValidationResult, BrowserSession, TestResults, ReportOptions };
export declare const runExample: (taskId?: string) => Promise<void>;
