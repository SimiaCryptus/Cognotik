import { Browser } from 'playwright';
import { 
  TaskTypeTest, 
  TestReport, 
  TestSessionConfig,
  PromptResult,
  ValidationResult,
  BrowserSession,
  TestResults,
  ReportOptions
} from '../types';
import { SessionConfigurator } from './SessionConfigurator';
import { BrowserAutomation } from './BrowserAutomation';
import { ResultCollector } from './ResultCollector';
import { ReportGenerator } from './ReportGenerator';
import { ErrorHandler } from './ErrorHandler';
import { generateSessionId } from '../utils';
import path from 'path';
import fs from 'fs';

export class TestRunner {
  private sessionConfigurator: SessionConfigurator;
  private browserAutomation: BrowserAutomation;
  private resultCollector: ResultCollector;
  private reportGenerator: ReportGenerator;
  private errorHandler: ErrorHandler;
  private baseUrl: string;
  private workingDir: string;
  private maxRetries: number;

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
  }) {
    this.baseUrl = options.baseUrl;
    this.workingDir = options.workingDir;
    this.maxRetries = options.maxRetries || 3;
    
    this.sessionConfigurator = options.sessionConfigurator || new SessionConfigurator();
    this.browserAutomation = options.browserAutomation || new BrowserAutomation({
      baseUrl: this.baseUrl,
      screenshotDir: path.join(this.workingDir, 'screenshots')
    });
    this.resultCollector = options.resultCollector || new ResultCollector(
      "default", // Will be overridden in runTaskTest
      generateSessionId(),
      this.workingDir
    );
    this.reportGenerator = options.reportGenerator || new ReportGenerator();
    this.errorHandler = options.errorHandler || new ErrorHandler();
    
    // Ensure working directory exists
    if (!fs.existsSync(this.workingDir)) {
      fs.mkdirSync(this.workingDir, { recursive: true });
    }
  }

  /**
   * Run a test for a specific task type
   * @param taskId The ID of the task type to test
   * @param taskConfig The task type configuration
   * @returns A promise that resolves to the test report
   */
  public async runTaskTest(taskId: string, taskConfig?: TaskTypeTest): Promise<TestReport> {
    const startTime = Date.now();
    let browserSession: BrowserSession | null = null;
    let testReport: TestReport;
    
    try {
      // Load task configuration if not provided
      if (!taskConfig) {
        taskConfig = await this.loadTaskConfig(taskId);
      }
      
      // Verify required API providers are configured
      this.verifyApiProviders(taskConfig.requiredApiProviders);
      
      // Generate session ID and create test-specific working directory
      const sessionId = generateSessionId();
      const testWorkingDir = path.join(this.workingDir, `test-${taskId}-${sessionId}`);
      fs.mkdirSync(testWorkingDir, { recursive: true });
      
      // Configure session with only the target task type enabled
      const sessionConfig = this.sessionConfigurator.configureTestSession(
        taskConfig,
        sessionId,
        testWorkingDir
      );
      
      // Launch browser and navigate to the application
      browserSession = await this.browserAutomation.launch(sessionConfig);
      
      // Execute the test prompts
      const promptResults = await this.executePromptSequence(
        browserSession,
        taskConfig.testPrompts,
        taskConfig.expectedOutputPatterns,
        taskConfig.timeoutSeconds
      );
      
      // Collect results and evaluate success criteria
      // Create a new ResultCollector with the current task ID
      this.resultCollector = new ResultCollector(
        taskConfig.taskId,
        sessionId,
        testWorkingDir
      );
      
      // Record prompt results
      for (const result of promptResults) {
        const validation = {
          isValid: result.matchedPatterns.length > 0,
          matchedPatterns: result.matchedPatterns,
          unmatchedPatterns: [],
          details: ''
        };
        this.resultCollector.recordPromptResult(
          result.prompt,
          result.response,
          result.responseTime,
          validation,
          result.screenshots
        );
      }
      
      // Evaluate success criteria
      for (const criteria of taskConfig.successCriteria) {
        // Simple evaluation - if all prompts passed, consider criteria met
        const allPromptsPassed = promptResults.every(r => r.result === 'PASS');
        this.resultCollector.recordSuccessCriteria(criteria, allPromptsPassed);
      }
      
      // Collect artifacts
      const artifacts = await this.collectArtifacts(testWorkingDir);
      for (const artifact of artifacts) {
        this.resultCollector.saveArtifact(
          fs.readFileSync(artifact),
          path.basename(artifact),
          'Test Artifact'
        );
      }
      
      // Generate the test results
      const testResults = this.resultCollector.generateReport(taskConfig, taskConfig.displayName);
      
      // Generate the test report
      testReport = this.createTestReport({
        taskId: taskConfig.taskId,
        displayName: taskConfig.displayName,
        sessionId,
        workingDir: testWorkingDir,
        startTime,
        testResults
      });
      
    } catch (error) {
      // Handle errors and generate error report
      const errorSessionId = browserSession?.sessionId || generateSessionId();
      const errorWorkingDir = browserSession?.workingDir || this.workingDir;
      
      // Create a basic error report
      testReport = {
        taskId,
        displayName: taskId,
        timestamp: new Date().toISOString(),
        duration: Date.now() - startTime,
        sessionId: errorSessionId,
        workingDir: errorWorkingDir,
        overallResult: 'ERROR',
        promptResults: [],
        successCriteriaMet: [],
        successCriteriaFailed: [],
        artifacts: [],
        logs: [`Error: ${error instanceof Error ? error.message : String(error)}`],
        errorDetails: error instanceof Error ? 
          `${error.message}\n${error.stack}` : 
          String(error)
      };
      
      // Log the error
      this.errorHandler.log(`Test error in task ${taskId}: ${error instanceof Error ? error.message : String(error)}`);
    } finally {
      // Close the browser session if it exists
      if (browserSession) {
        await browserSession.close();
      }
    }
    
    return testReport;
  }

  /**
   * Run tests for all available task types
   * @returns A promise that resolves to an array of test reports
   */
  public async runAllTaskTests(): Promise<TestReport[]> {
    const taskConfigs = await this.loadAllTaskConfigs();
    const reports: TestReport[] = [];
    
    for (const taskConfig of taskConfigs) {
      const report = await this.runTaskTest(taskConfig.taskId, taskConfig);
      reports.push(report);
    }
    
    return reports;
  }

  /**
   * Generate reports in various formats
   * @param report The test report to generate reports for
   * @param options Report generation options
   */
  public async generateReports(report: TestReport, options: {
    formats: Array<'html' | 'json' | 'pdf'>;
    outputDir: string;
  } & Partial<ReportOptions>): Promise<void> {
    // Simple implementation that writes JSON report to file
    const outputDir = options.outputDir;
    
    // Ensure output directory exists
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
    
    // Generate reports in requested formats
    for (const format of options.formats) {
      if (format === 'json') {
        const jsonPath = path.join(outputDir, `${report.taskId}_${report.sessionId}.json`);
        fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2));
      }
      // Implement other formats as needed
    }
    
    return Promise.resolve();
  }

  /**
   * Generate a summary report for multiple test reports
   * @param reports The test reports to summarize
   * @returns The summary report
   */
  public generateSummaryReport(reports: TestReport[]): any {
    // Create a basic summary report
    const passed = reports.filter(r => r.overallResult === 'PASS').length;
    const failed = reports.filter(r => r.overallResult === 'FAIL').length;
    const error = reports.filter(r => r.overallResult === 'ERROR').length;
    
    return {
      title: 'Test Summary Report',
      timestamp: new Date().toISOString(),
      summary: {
        total: reports.length,
        passed,
        failed,
        error
      },
      taskReports: reports,
      duration: reports.reduce((sum, r) => sum + r.duration, 0)
    };
  }

  /**
   * Load task configuration for a specific task type
   * @param taskId The ID of the task type
   * @returns The task configuration
   * @private
   */
  private async loadTaskConfig(taskId: string): Promise<TaskTypeTest> {
    try {
      // Import task configurations dynamically
    const { getTaskTypeById } = await import('../taskTypes');
    const taskConfig = getTaskTypeById(taskId);
      
      if (!taskConfig) {
        throw new Error(`Task type configuration not found for ID: ${taskId}`);
      }
      
      return taskConfig;
    } catch (error : any) {
      throw new Error(`Failed to load task configuration for ${taskId}: ${error["message"]}`);
    }
  }

  /**
   * Load all available task configurations
   * @returns An array of task configurations
   * @private
   */
  private async loadAllTaskConfigs(): Promise<TaskTypeTest[]> {
    try {
      const taskTypes = await import('../taskTypes');
      return Object.values(taskTypes) as TaskTypeTest[];
    } catch (error : any) {
      throw new Error(`Failed to load task configurations: ${error.message}`);
    }
  }

  /**
   * Verify that required API providers are configured
   * @param requiredProviders Array of required API provider names
   * @private
   */
  private verifyApiProviders(requiredProviders: string[]): void {
    // This would check environment variables or configuration files
    // to ensure the required API providers are properly configured
    const missingProviders = requiredProviders.filter(provider => {
      const envVar = `API_KEY_${provider.toUpperCase()}`;
      return !process.env[envVar];
    });
    
    if (missingProviders.length > 0) {
      throw new Error(`Missing API configuration for providers: ${missingProviders.join(', ')}`);
    }
  }

  /**
   * Execute a sequence of prompts in the browser session
   * @param browserSession The browser session
   * @param prompts Array of prompts to execute
   * @param expectedPatterns Array of expected output patterns
   * @param timeoutSeconds Maximum timeout for each prompt
   * @returns Array of prompt results
   * @private
   */
  private async executePromptSequence(
    browserSession: BrowserSession,
    prompts: string[],
    expectedPatterns: RegExp[],
    timeoutSeconds: number
  ): Promise<PromptResult[]> {
    const results: PromptResult[] = [];
    
    for (let i = 0; i < prompts.length; i++) {
      const prompt = prompts[i];
      const expectedPattern = expectedPatterns[i] || null;
      
      let retryCount = 0;
      let success = false;
      let result: PromptResult | null = null;
      
      while (!success && retryCount < this.maxRetries) {
        try {
          // Execute the prompt and wait for response
          // Use the executePromptSequence method from BrowserAutomation
          const promptResults = await this.browserAutomation.executePromptSequence(
            browserSession,
            [prompt],
            expectedPattern ? [expectedPattern] : []
          );
          
          // Get the first result
          result = promptResults[0];
          
          // Validate the response if an expected pattern is provided
          if (expectedPattern) {
            const validation = this.validateTaskOutput(result.response, [expectedPattern]);
            result.matchedPatterns = validation.matchedPatterns;
            success = validation.isValid;
          } else {
            success = true;
          }
          
        } catch (error) {
          retryCount++;
          if (retryCount >= this.maxRetries) {
            throw error;
          }
          // Wait before retrying
          await new Promise(resolve => setTimeout(resolve, 2000));
        }
      }
      
      if (result) {
        results.push(result);
      }
    }
    
    return results;
  }

  /**
   * Validate task output against expected patterns
   * @param output The output to validate
   * @param patterns Array of expected patterns
   * @returns Validation result
   * @private
   */
  private validateTaskOutput(output: string, patterns: RegExp[]): ValidationResult {
    const matchedPatterns: string[] = [];
    
    for (const pattern of patterns) {
      if (pattern.test(output)) {
        matchedPatterns.push(pattern.toString());
      }
    }
    
    return {
      isValid: matchedPatterns.length > 0,
      matchedPatterns,
      unmatchedPatterns: patterns
        .filter(p => !p.test(output))
        .map(p => p.toString()),
      details: matchedPatterns.length > 0 
        ? `Matched ${matchedPatterns.length} patterns` 
        : 'No patterns matched'
    };
  }

  /**
   * Collect artifacts from the test working directory
   * @param workingDir The working directory
   * @returns Array of artifact paths
   * @private
   */
  private async collectArtifacts(workingDir: string): Promise<string[]> {
    try {
      const files = fs.readdirSync(workingDir);
      return files.map(file => path.join(workingDir, file));
    } catch (error : any        ) {

      console.error(`Failed to collect artifacts: ${error.message}`);
      return [];
    }
  }

  /**
   * Create a test report from test results
   * @param options Options for creating the test report
   * @returns The test report
   * @private
   */
  private createTestReport(options: {
    taskId: string;
    displayName: string;
    sessionId: string;
    workingDir: string;
    startTime: number;
    testResults: TestResults;
  }): TestReport {
    const { taskId, displayName, sessionId, workingDir, startTime, testResults } = options;
    const duration = Date.now() - startTime;
    
    // Determine overall result based on success criteria
    const allCriteriaMet = testResults.successCriteriaFailed.length === 0;
    const overallResult = allCriteriaMet ? 'PASS' : 'FAIL';
    
    return {
      taskId,
      displayName,
      timestamp: new Date().toISOString(),
      duration,
      sessionId,
      workingDir,
      overallResult,
      promptResults: testResults.promptResults.map(pr => ({
        prompt: pr.prompt,
        response: pr.response,
        responseTime: pr.responseTime,
        matchedPatterns: pr.matchedPatterns || [],
        screenshots: pr.screenshots || [],
        result: pr.matchedPatterns && pr.matchedPatterns.length > 0 ? 'PASS' : 'FAIL'
      })),
      successCriteriaMet: testResults.successCriteriaMet,
      successCriteriaFailed: testResults.successCriteriaFailed,
      artifacts: testResults.artifacts,
      logs: testResults.logs || []
    };
  }
}