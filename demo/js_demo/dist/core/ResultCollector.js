"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ResultCollector = void 0;
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
class ResultCollector {
    constructor(taskId, sessionId, workingDir) {
        this.promptResults = [];
        this.successCriteriaMet = [];
        this.successCriteriaFailed = [];
        this.artifacts = [];
        this.logs = [];
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.workingDir = workingDir;
        this.startTime = Date.now();
        // Ensure the screenshots and artifacts directories exist
        const screenshotsDir = path_1.default.join(this.workingDir, 'screenshots');
        const artifactsDir = path_1.default.join(this.workingDir, 'artifacts');
        if (!fs_1.default.existsSync(screenshotsDir)) {
            fs_1.default.mkdirSync(screenshotsDir, { recursive: true });
        }
        if (!fs_1.default.existsSync(artifactsDir)) {
            fs_1.default.mkdirSync(artifactsDir, { recursive: true });
        }
        this.log('Result collector initialized');
    }
    /**
     * Captures a screenshot of the current page state
     */
    async captureScreenshot(page, name) {
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const filename = `${this.taskId}_${name}_${timestamp}.png`;
        const filepath = path_1.default.join(this.workingDir, 'screenshots', filename);
        await page.screenshot({ path: filepath, fullPage: true });
        this.log(`Screenshot captured: ${filename}`);
        return filepath;
    }
    /**
     * Records the result of a prompt execution
     */
    recordPromptResult(prompt, response, responseTime, validationResult, screenshots) {
        const result = {
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
    recordSuccessCriteria(criteria, met) {
        if (met) {
            this.successCriteriaMet.push(criteria);
            this.log(`Success criteria met: ${criteria}`);
        }
        else {
            this.successCriteriaFailed.push(criteria);
            this.log(`Success criteria failed: ${criteria}`);
        }
    }
    /**
     * Saves an artifact file to the working directory
     */
    saveArtifact(content, filename, type) {
        const artifactPath = path_1.default.join(this.workingDir, 'artifacts', filename);
        fs_1.default.writeFileSync(artifactPath, content);
        this.artifacts.push(artifactPath);
        this.log(`Saved ${type} artifact: ${filename}`);
        return artifactPath;
    }
    /**
     * Collects files from the working directory that match a pattern
     */
    collectArtifactsFromWorkingDir(pattern) {
        const files = fs_1.default.readdirSync(this.workingDir);
        const matchedFiles = files
            .filter(file => pattern.test(file))
            .map(file => path_1.default.join(this.workingDir, file));
        this.artifacts.push(...matchedFiles);
        this.log(`Collected ${matchedFiles.length} artifacts matching pattern ${pattern}`);
        return matchedFiles;
    }
    /**
     * Records an error that occurred during test execution
     */
    recordError(error) {
        this.errorDetails = typeof error === 'string' ? error : `${error.name}: ${error.message}\n${error.stack}`;
        this.log(`Error recorded: ${this.errorDetails.split('\n')[0]}`, 'ERROR');
    }
    /**
     * Adds a log entry
     */
    log(message, level = 'INFO') {
        const timestamp = new Date().toISOString();
        const logEntry = `[${timestamp}] [${level}] ${message}`;
        this.logs.push(logEntry);
        // Also log to console for real-time feedback
        if (level === 'ERROR') {
            console.error(logEntry);
        }
        else if (level === 'WARNING') {
            console.warn(logEntry);
        }
        else {
            console.log(logEntry);
        }
    }
    /**
     * Generates the final test report
     */
    generateReport(taskConfig, displayName) {
        const duration = Date.now() - this.startTime;
        // Determine overall result
        let overallResult;
        if (this.errorDetails) {
            overallResult = 'ERROR';
        }
        else if (this.successCriteriaFailed.length > 0 || this.promptResults.some(r => r.result === 'FAIL')) {
            overallResult = 'FAIL';
        }
        else {
            overallResult = 'PASS';
        }
        // Save logs to file
        const logFilePath = path_1.default.join(this.workingDir, 'test_execution.log');
        fs_1.default.writeFileSync(logFilePath, this.logs.join('\n'));
        this.log(`Test completed with result: ${overallResult}`);
        this.log(`Test duration: ${duration}ms`);
        // Generate the report
        const report = {
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
    async captureFinalState(page) {
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
                this.saveArtifact(JSON.stringify(consoleMessages, null, 2), 'console_logs.json', 'Console Logs');
            }
            this.log('Final application state captured');
        }
        catch (error) {
            this.log(`Error capturing final state: ${error}`, 'ERROR');
        }
    }
    /**
     * Collects performance metrics from the page
     */
    async collectPerformanceMetrics(page) {
        try {
            // Collect performance metrics using the Performance API
            const metrics = await page.evaluate(() => {
                const perfEntries = performance.getEntriesByType('navigation');
                if (perfEntries.length > 0) {
                    const navEntry = perfEntries[0];
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
                this.saveArtifact(JSON.stringify(metrics, null, 2), 'performance_metrics.json', 'Performance Metrics');
                this.log('Performance metrics collected');
            }
        }
        catch (error) {
            this.log(`Error collecting performance metrics: ${error}`, 'WARNING');
        }
    }
    /**
     * Cleans up temporary files and resources
     */
    cleanup() {
        // Implement cleanup logic if needed
        this.log('Result collector cleanup completed');
    }
}
exports.ResultCollector = ResultCollector;
//# sourceMappingURL=ResultCollector.js.map