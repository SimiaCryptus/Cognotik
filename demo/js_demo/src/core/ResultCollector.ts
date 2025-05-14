import fs from 'fs';
import path from 'path';
import { Browser, Page } from 'playwright';
import { 
  PromptResult, 
  TestReport, 
  ValidationResult,
  TaskTypeTest
} from '../types';
export class ResultCollector {
  private workingDir: string;
  private taskId: string;
  private sessionId: string;
  private startTime: number;
  private promptResults: PromptResult[] = [];
  private successCriteriaMet: string[] = [];
  private successCriteriaFailed: string[] = [];
  private artifacts: string[] = [];
  private logs: string[] = [];
  private errorDetails?: string;
  constructor(taskId: string, sessionId: string, workingDir: string) {
    this.taskId = taskId;
    this.sessionId = sessionId;
    this.workingDir = workingDir;
    this.startTime = Date.now();
    // Ensure the screenshots and artifacts directories exist
    const screenshotsDir = path.join(this.workingDir, 'screenshots');
    const artifactsDir = path.join(this.workingDir, 'artifacts');
    if (!fs.existsSync(screenshotsDir)) {
      fs.mkdirSync(screenshotsDir, { recursive: true });
    }
    if (!fs.existsSync(artifactsDir)) {
      fs.mkdirSync(artifactsDir, { recursive: true });
    }
    this.log('Result collector initialized');
  }
  /**
   * Captures a screenshot of the current page state
   */
  async captureScreenshot(page: Page, name: string): Promise<string> {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const filename = `${this.taskId}_${name}_${timestamp}.png`;
    const filepath = path.join(this.workingDir, 'screenshots', filename);
    await page.screenshot({ path: filepath, fullPage: true });
    this.log(`Screenshot captured: ${filename}`);
    return filepath;
  }
  /**
   * Records the result of a prompt execution
   */
  recordPromptResult(
    prompt: string, 
    response: string, 
    responseTime: number, 
    validationResult: ValidationResult,
    screenshots: string[]
  ): void {
    const result: PromptResult = {
      prompt,
      response,
      responseTime,
      matchedPatterns: validationResult.matchedPatterns,
      screenshots,
      result: validationResult.isValid ? 'PASS' : 'FAIL'
    };
    this.promptResults.push(result);
    this.log(`Recorded result for prompt: "${prompt.substring(0, 50)}..."`);
  }
  /**
   * Records success criteria results
   */
  recordSuccessCriteria(criteria: string, met: boolean): void {
    if (met) {
      this.successCriteriaMet.push(criteria);
      this.log(`Success criteria met: ${criteria}`);
    } else {
      this.successCriteriaFailed.push(criteria);
      this.log(`Success criteria failed: ${criteria}`);
    }
  }
  /**
   * Saves an artifact file to the working directory
   */
  saveArtifact(content: string | Buffer, filename: string, type: string): string {
    const artifactPath = path.join(this.workingDir, 'artifacts', filename);
    fs.writeFileSync(artifactPath, content);
    this.artifacts.push(artifactPath);
    this.log(`Saved ${type} artifact: ${filename}`);
    return artifactPath;
  }
  /**
   * Collects files from the working directory that match a pattern
   */
  collectArtifactsFromWorkingDir(pattern: RegExp): string[] {
    const files = fs.readdirSync(this.workingDir);
    const matchedFiles = files
      .filter(file => pattern.test(file))
      .map(file => path.join(this.workingDir, file));
    this.artifacts.push(...matchedFiles);
    this.log(`Collected ${matchedFiles.length} artifacts matching pattern ${pattern}`);
    return matchedFiles;
  }
  /**
   * Records an error that occurred during test execution
   */
  recordError(error: Error | string): void {
    this.errorDetails = typeof error === 'string' ? error : `${error.name}: ${error.message}\n${error.stack}`;
    this.log(`Error recorded: ${this.errorDetails.split('\n')[0]}`, 'ERROR');
  }
  /**
   * Adds a log entry
   */
  log(message: string, level: 'INFO' | 'WARNING' | 'ERROR' = 'INFO'): void {
    const timestamp = new Date().toISOString();
    const logEntry = `[${timestamp}] [${level}] ${message}`;
    this.logs.push(logEntry);
    // Also log to console for real-time feedback
    if (level === 'ERROR') {
      console.error(logEntry);
    } else if (level === 'WARNING') {
      console.warn(logEntry);
    } else {
      console.log(logEntry);
    }
  }
  /**
   * Generates the final test report
   */
  generateReport(taskConfig: TaskTypeTest, displayName: string): TestReport {
    const duration = Date.now() - this.startTime;
    // Determine overall result
    let overallResult: 'PASS' | 'FAIL' | 'ERROR';
    if (this.errorDetails) {
      overallResult = 'ERROR';
    } else if (this.successCriteriaFailed.length > 0 || this.promptResults.some(r => r.result === 'FAIL')) {
      overallResult = 'FAIL';
    } else {
      overallResult = 'PASS';
    }
    // Save logs to file
    const logFilePath = path.join(this.workingDir, 'test_execution.log');
    fs.writeFileSync(logFilePath, this.logs.join('\n'));
    this.log(`Test completed with result: ${overallResult}`);
    this.log(`Test duration: ${duration}ms`);
    // Generate the report
    const report: TestReport = {
      taskId: this.taskId,
      displayName,
      timestamp: new Date().toISOString(),
      duration,
      sessionId: this.sessionId,
      workingDir: this.workingDir,
      overallResult,
      promptResults: this.promptResults,
      successCriteriaMet: this.successCriteriaMet,
      successCriteriaFailed: this.successCriteriaFailed,
      artifacts: this.artifacts,
      logs: this.logs
    };
    if (this.errorDetails) {
      report.errorDetails = this.errorDetails;
    }
    return report;
  }
  /**
   * Captures the final application state
   */
  async captureFinalState(page: Page): Promise<void> {
    try {
      // Capture final screenshot
      const finalScreenshot = await this.captureScreenshot(page, 'final_state');
      // Capture DOM structure
      const htmlContent = await page.content();
      this.saveArtifact(htmlContent, 'final_dom.html', 'HTML');
      // Capture console logs if available
      const consoleMessages = await page.evaluate(() => {
        // This assumes you've been collecting console messages in the page
        // @ts-ignore - This is a custom property we might have added
        return window._consoleMessages || [];
      }).catch(() => []);
      if (consoleMessages.length > 0) {
        this.saveArtifact(
          JSON.stringify(consoleMessages, null, 2), 
          'console_logs.json', 
          'Console Logs'
        );
      }
      this.log('Final application state captured');
    } catch (error) {
      this.log(`Error capturing final state: ${error}`, 'ERROR');
    }
  }
  /**
   * Collects performance metrics from the page
   */
  async collectPerformanceMetrics(page: Page): Promise<void> {
    try {
      // Collect performance metrics using the Performance API
      const metrics = await page.evaluate(() => {
        const perfEntries = performance.getEntriesByType('navigation');
        if (perfEntries.length > 0) {
          const navEntry = perfEntries[0] as PerformanceNavigationTiming;
          return {
            domContentLoaded: navEntry.domContentLoadedEventEnd - navEntry.domContentLoadedEventStart,
            load: navEntry.loadEventEnd - navEntry.loadEventStart,
            domInteractive: navEntry.domInteractive,
            firstPaint: performance.getEntriesByName('first-paint')[0]?.startTime,
            firstContentfulPaint: performance.getEntriesByName('first-contentful-paint')[0]?.startTime
          };
        }
        return null;
      });
      if (metrics) {
        this.saveArtifact(
          JSON.stringify(metrics, null, 2),
          'performance_metrics.json',
          'Performance Metrics'
        );
        this.log('Performance metrics collected');
      }
    } catch (error) {
      this.log(`Error collecting performance metrics: ${error}`, 'WARNING');
    }
  }
  /**
   * Cleans up temporary files and resources
   */
  cleanup(): void {
    // Implement cleanup logic if needed
    this.log('Result collector cleanup completed');
  }
}