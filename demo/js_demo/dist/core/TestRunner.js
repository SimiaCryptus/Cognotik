"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || function (mod) {
    if (mod && mod.__esModule) return mod;
    var result = {};
    if (mod != null) for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
    __setModuleDefault(result, mod);
    return result;
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.TestRunner = void 0;
const SessionConfigurator_1 = require("./SessionConfigurator");
const BrowserAutomation_1 = require("./BrowserAutomation");
const ResultCollector_1 = require("./ResultCollector");
const ReportGenerator_1 = require("./ReportGenerator");
const ErrorHandler_1 = require("./ErrorHandler");
const utils_1 = require("../utils");
const path_1 = __importDefault(require("path"));
const fs_1 = __importDefault(require("fs"));
class TestRunner {
    constructor(options) {
        this.baseUrl = options.baseUrl;
        this.workingDir = options.workingDir;
        this.maxRetries = options.maxRetries || 3;
        this.sessionConfigurator = options.sessionConfigurator || new SessionConfigurator_1.SessionConfigurator();
        this.browserAutomation = options.browserAutomation || new BrowserAutomation_1.BrowserAutomation({
            baseUrl: this.baseUrl,
            screenshotDir: path_1.default.join(this.workingDir, 'screenshots')
        });
        this.resultCollector = options.resultCollector || new ResultCollector_1.ResultCollector("default", // Will be overridden in runTaskTest
        (0, utils_1.generateSessionId)(), this.workingDir);
        this.reportGenerator = options.reportGenerator || new ReportGenerator_1.ReportGenerator();
        this.errorHandler = options.errorHandler || new ErrorHandler_1.ErrorHandler();
        // Ensure working directory exists
        if (!fs_1.default.existsSync(this.workingDir)) {
            fs_1.default.mkdirSync(this.workingDir, { recursive: true });
        }
    }
    /**
     * Run a test for a specific task type
     * @param taskId The ID of the task type to test
     * @param taskConfig The task type configuration
     * @returns A promise that resolves to the test report
     */
    async runTaskTest(taskId, taskConfig) {
        const startTime = Date.now();
        let browserSession = null;
        let testReport;
        try {
            // Load task configuration if not provided
            if (!taskConfig) {
                taskConfig = await this.loadTaskConfig(taskId);
            }
            // Verify required API providers are configured
            this.verifyApiProviders(taskConfig.requiredApiProviders);
            // Generate session ID and create test-specific working directory
            const sessionId = (0, utils_1.generateSessionId)();
            const testWorkingDir = path_1.default.join(this.workingDir, `test-${taskId}-${sessionId}`);
            fs_1.default.mkdirSync(testWorkingDir, { recursive: true });
            // Configure session with only the target task type enabled
            const sessionConfig = this.sessionConfigurator.configureTestSession(taskConfig, sessionId, testWorkingDir);
            // Launch browser and navigate to the application
            browserSession = await this.browserAutomation.launch(sessionConfig);
            // Execute the test prompts
            const promptResults = await this.executePromptSequence(browserSession, taskConfig.testPrompts, taskConfig.expectedOutputPatterns, taskConfig.timeoutSeconds);
            // Collect results and evaluate success criteria
            // Create a new ResultCollector with the current task ID
            this.resultCollector = new ResultCollector_1.ResultCollector(taskConfig.taskId, sessionId, testWorkingDir);
            // Record prompt results
            for (const result of promptResults) {
                const validation = {
                    isValid: result.matchedPatterns.length > 0,
                    matchedPatterns: result.matchedPatterns,
                    unmatchedPatterns: [],
                    details: ''
                };
                this.resultCollector.recordPromptResult(result.prompt, result.response, result.responseTime, validation, result.screenshots);
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
                this.resultCollector.saveArtifact(fs_1.default.readFileSync(artifact), path_1.default.basename(artifact), 'Test Artifact');
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
        }
        catch (error) {
            // Handle errors and generate error report
            const errorSessionId = browserSession?.sessionId || (0, utils_1.generateSessionId)();
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
        }
        finally {
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
    async runAllTaskTests() {
        const taskConfigs = await this.loadAllTaskConfigs();
        const reports = [];
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
    async generateReports(report, options) {
        // Simple implementation that writes JSON report to file
        const outputDir = options.outputDir;
        // Ensure output directory exists
        if (!fs_1.default.existsSync(outputDir)) {
            fs_1.default.mkdirSync(outputDir, { recursive: true });
        }
        // Generate reports in requested formats
        for (const format of options.formats) {
            if (format === 'json') {
                const jsonPath = path_1.default.join(outputDir, `${report.taskId}_${report.sessionId}.json`);
                fs_1.default.writeFileSync(jsonPath, JSON.stringify(report, null, 2));
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
    generateSummaryReport(reports) {
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
    async loadTaskConfig(taskId) {
        try {
            // Import task configurations dynamically
            const { getTaskTypeById } = await Promise.resolve().then(() => __importStar(require('../taskTypes')));
            const taskConfig = getTaskTypeById(taskId);
            if (!taskConfig) {
                throw new Error(`Task type configuration not found for ID: ${taskId}`);
            }
            return taskConfig;
        }
        catch (error) {
            throw new Error(`Failed to load task configuration for ${taskId}: ${error["message"]}`);
        }
    }
    /**
     * Load all available task configurations
     * @returns An array of task configurations
     * @private
     */
    async loadAllTaskConfigs() {
        try {
            const taskTypes = await Promise.resolve().then(() => __importStar(require('../taskTypes')));
            return Object.values(taskTypes);
        }
        catch (error) {
            throw new Error(`Failed to load task configurations: ${error.message}`);
        }
    }
    /**
     * Verify that required API providers are configured
     * @param requiredProviders Array of required API provider names
     * @private
     */
    verifyApiProviders(requiredProviders) {
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
    async executePromptSequence(browserSession, prompts, expectedPatterns, timeoutSeconds) {
        const results = [];
        for (let i = 0; i < prompts.length; i++) {
            const prompt = prompts[i];
            const expectedPattern = expectedPatterns[i] || null;
            let retryCount = 0;
            let success = false;
            let result = null;
            while (!success && retryCount < this.maxRetries) {
                try {
                    // Execute the prompt and wait for response
                    // Use the executePromptSequence method from BrowserAutomation
                    const promptResults = await this.browserAutomation.executePromptSequence(browserSession, [prompt], expectedPattern ? [expectedPattern] : []);
                    // Get the first result
                    result = promptResults[0];
                    // Validate the response if an expected pattern is provided
                    if (expectedPattern) {
                        const validation = this.validateTaskOutput(result.response, [expectedPattern]);
                        result.matchedPatterns = validation.matchedPatterns;
                        success = validation.isValid;
                    }
                    else {
                        success = true;
                    }
                }
                catch (error) {
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
    validateTaskOutput(output, patterns) {
        const matchedPatterns = [];
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
    async collectArtifacts(workingDir) {
        try {
            const files = fs_1.default.readdirSync(workingDir);
            return files.map(file => path_1.default.join(workingDir, file));
        }
        catch (error) {
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
    createTestReport(options) {
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
exports.TestRunner = TestRunner;
//# sourceMappingURL=TestRunner.js.map