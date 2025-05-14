/**
 * Cognotik AI Task Type Tester
 * Main entry point for programmatic usage
 */

// Core components
import { TestRunner } from './core/TestRunner';
import { SessionConfigurator } from './core/SessionConfigurator';
import { BrowserAutomation } from './core/BrowserAutomation';
import { ResultCollector } from './core/ResultCollector';
import { ReportGenerator } from './core/ReportGenerator';
import { ErrorHandler } from './core/ErrorHandler';
// Node.js path module
import path from 'path';


// Task type definitions
import * as taskTypes from './taskTypes';

// Utility functions
import * as utils from './utils';

// Type definitions
import {
  TaskTypeTest,
  TestSessionConfig,
  TestReport,
  PromptResult,
  ValidationResult,
  BrowserSession,
  TestResults,
  ReportOptions
} from './types';

// Main class for programmatic usage
class CognotikTaskTester {
  private testRunner: TestRunner;
  private reportGenerator: ReportGenerator;

  constructor(options: {
    baseUrl?: string;
    apiConfigPath?: string;
    reportDir?: string;
    headless?: boolean;
    verbose?: boolean;
  } = {}) {
    
    const errorHandler = new ErrorHandler();
    const sessionConfigurator = new SessionConfigurator();
    const browserAutomation = new BrowserAutomation({
      baseUrl: options.baseUrl ?? 'http://localhost:3000',
      screenshotDir: path.join(options.reportDir ?? './test-reports', 'screenshots'),
      headless: options.headless !== false
    });
    // Create a placeholder ResultCollector - actual instances will be created per test
    const resultCollector = new ResultCollector('placeholder', 'placeholder', options.reportDir ?? './test-reports');
    
    this.reportGenerator = new ReportGenerator();
    
    this.testRunner = new TestRunner({
      baseUrl: options.baseUrl ?? 'http://localhost:3000',
      workingDir: options.reportDir ?? './test-reports',
      sessionConfigurator: sessionConfigurator,
      browserAutomation: browserAutomation,
      resultCollector: resultCollector,
      reportGenerator: this.reportGenerator,
      errorHandler: errorHandler,
      maxRetries: 3,
    });
  }

  /**
   * Run a test for a specific task type
   * @param taskId The ID of the task type to test
   * @returns A promise that resolves to the test report
   */
  async runTaskTest(taskId: string): Promise<TestReport> {
    return this.testRunner.runTaskTest(taskId);
  }

  /**
   * Run tests for all available task types
   * @returns A promise that resolves to an array of test reports
   */
  async runAllTaskTests(): Promise<TestReport[]> {
    return this.testRunner.runAllTaskTests();
  }

  /**
   * Generate reports in the specified formats
   * @param report The test report to generate reports for
   * @param options Report generation options
   */
  async generateReports(report: TestReport, options: {
    formats: Array<'html' | 'json' | 'pdf' | 'console'>;
    outputDir?: string;
  } & Partial<ReportOptions>): Promise<void> {
    await this.reportGenerator.generateReports(report, {
      ...options,
      outputDir: options.outputDir || './reports'
    });
  }

  /**
   * Generate a summary report for multiple test reports
   * @param reports The test reports to summarize
   * @returns A summary report
   */
  generateSummaryReport(reports: TestReport[]): any {
    return this.reportGenerator.generateSummaryReport(reports);
  }
  /**
   * Save a summary report to a file
   * @param summary The summary report to save
   * @param outputPath The path to save the report to
   * @returns A promise that resolves when the report is saved
   */
  async saveSummaryReport(summary: any, outputPath: string): Promise<void> {
    return this.reportGenerator.saveSummaryReport(summary, outputPath);
  }


  /**
   * Get all available task types
   * @returns An array of task type definitions
   */
  getAvailableTaskTypes(): TaskTypeTest[] {
    return Object.values(taskTypes) as TaskTypeTest[];
  }

  /**
   * Get a specific task type by ID
   * @param taskId The ID of the task type
   * @returns The task type definition or undefined if not found
   */
  getTaskType(taskId: string): TaskTypeTest | undefined {
    return (Object.values(taskTypes) as TaskTypeTest[]).find(task => task.taskId === taskId);
  }
  /**
   * Get the output directory for reports
   * @returns The output directory path
   */
  getReportOutputDir(): string {
    return './reports'; // Return the default or configured output directory
  }
}

// Export the main class
export { CognotikTaskTester };

// Export core components for advanced usage
export {
  TestRunner,
  SessionConfigurator,
  BrowserAutomation,
  ResultCollector,
  ReportGenerator,
  ErrorHandler
};

// Export task types
export { taskTypes };

// Export utility functions
export { utils };

// Export type definitions
export type {
  TaskTypeTest,
  TestSessionConfig,
  TestReport,
  PromptResult,
  ValidationResult,
  BrowserSession,
  TestResults,
  ReportOptions
};

// Export example usage function
export const runExample = async (taskId: string = 'code_generation'): Promise<void> => {
  const tester = new CognotikTaskTester();
  
  console.log(`Running test for task type: ${taskId}`);
  const report = await tester.runTaskTest(taskId);
  
  console.log(`Test result: ${report.overallResult}`);
  
  await tester.generateReports(report, {
    formats: ['html', 'json', 'console'],
  });
  
  console.log(`Test reports generated in: ${tester.getReportOutputDir()}`);
};