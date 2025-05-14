"use strict";
/**
 * Cognotik AI Task Type Tester
 * Main entry point for programmatic usage
 */
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
exports.runExample = exports.utils = exports.taskTypes = exports.ErrorHandler = exports.ReportGenerator = exports.ResultCollector = exports.BrowserAutomation = exports.SessionConfigurator = exports.TestRunner = exports.CognotikTaskTester = void 0;
// Core components
const TestRunner_1 = require("./core/TestRunner");
Object.defineProperty(exports, "TestRunner", { enumerable: true, get: function () { return TestRunner_1.TestRunner; } });
const SessionConfigurator_1 = require("./core/SessionConfigurator");
Object.defineProperty(exports, "SessionConfigurator", { enumerable: true, get: function () { return SessionConfigurator_1.SessionConfigurator; } });
const BrowserAutomation_1 = require("./core/BrowserAutomation");
Object.defineProperty(exports, "BrowserAutomation", { enumerable: true, get: function () { return BrowserAutomation_1.BrowserAutomation; } });
const ResultCollector_1 = require("./core/ResultCollector");
Object.defineProperty(exports, "ResultCollector", { enumerable: true, get: function () { return ResultCollector_1.ResultCollector; } });
const ReportGenerator_1 = require("./core/ReportGenerator");
Object.defineProperty(exports, "ReportGenerator", { enumerable: true, get: function () { return ReportGenerator_1.ReportGenerator; } });
const ErrorHandler_1 = require("./core/ErrorHandler");
Object.defineProperty(exports, "ErrorHandler", { enumerable: true, get: function () { return ErrorHandler_1.ErrorHandler; } });
// Node.js path module
const path_1 = __importDefault(require("path"));
// Task type definitions
const taskTypes = __importStar(require("./taskTypes"));
exports.taskTypes = taskTypes;
// Utility functions
const utils = __importStar(require("./utils"));
exports.utils = utils;
// Main class for programmatic usage
class CognotikTaskTester {
    constructor(options = {}) {
        const errorHandler = new ErrorHandler_1.ErrorHandler();
        const sessionConfigurator = new SessionConfigurator_1.SessionConfigurator();
        const browserAutomation = new BrowserAutomation_1.BrowserAutomation({
            baseUrl: options.baseUrl ?? 'http://localhost:3000',
            screenshotDir: path_1.default.join(options.reportDir ?? './test-reports', 'screenshots'),
            headless: options.headless !== false
        });
        // Create a placeholder ResultCollector - actual instances will be created per test
        const resultCollector = new ResultCollector_1.ResultCollector('placeholder', 'placeholder', options.reportDir ?? './test-reports');
        this.reportGenerator = new ReportGenerator_1.ReportGenerator();
        this.testRunner = new TestRunner_1.TestRunner({
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
    async runTaskTest(taskId) {
        return this.testRunner.runTaskTest(taskId);
    }
    /**
     * Run tests for all available task types
     * @returns A promise that resolves to an array of test reports
     */
    async runAllTaskTests() {
        return this.testRunner.runAllTaskTests();
    }
    /**
     * Generate reports in the specified formats
     * @param report The test report to generate reports for
     * @param options Report generation options
     */
    async generateReports(report, options) {
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
    generateSummaryReport(reports) {
        return this.reportGenerator.generateSummaryReport(reports);
    }
    /**
     * Save a summary report to a file
     * @param summary The summary report to save
     * @param outputPath The path to save the report to
     * @returns A promise that resolves when the report is saved
     */
    async saveSummaryReport(summary, outputPath) {
        return this.reportGenerator.saveSummaryReport(summary, outputPath);
    }
    /**
     * Get all available task types
     * @returns An array of task type definitions
     */
    getAvailableTaskTypes() {
        return Object.values(taskTypes);
    }
    /**
     * Get a specific task type by ID
     * @param taskId The ID of the task type
     * @returns The task type definition or undefined if not found
     */
    getTaskType(taskId) {
        return Object.values(taskTypes).find(task => task.taskId === taskId);
    }
    /**
     * Get the output directory for reports
     * @returns The output directory path
     */
    getReportOutputDir() {
        return './reports'; // Return the default or configured output directory
    }
}
exports.CognotikTaskTester = CognotikTaskTester;
// Export example usage function
const runExample = async (taskId = 'code_generation') => {
    const tester = new CognotikTaskTester();
    console.log(`Running test for task type: ${taskId}`);
    const report = await tester.runTaskTest(taskId);
    console.log(`Test result: ${report.overallResult}`);
    await tester.generateReports(report, {
        formats: ['html', 'json', 'console'],
    });
    console.log(`Test reports generated in: ${tester.getReportOutputDir()}`);
};
exports.runExample = runExample;
//# sourceMappingURL=index.js.map