"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.delay = exports.calculateExecutionTime = exports.maskSensitiveInfo = exports.checkRequiredEnvVars = exports.formatTimestamp = exports.sanitizeFilename = exports.readFromFile = exports.saveToFile = exports.createWorkingDirectory = exports.generateSessionId = exports.logger = void 0;
const crypto_1 = __importDefault(require("crypto"));
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const util_1 = require("util");
/**
 * Simple logger for internal use
 */
exports.logger = {
    info: (message) => console.log(`[INFO] ${message}`),
    debug: (message) => console.log(`[DEBUG] ${message}`),
    warn: (message) => console.warn(`[WARN] ${message}`),
    error: (message) => console.error(`[ERROR] ${message}`)
};
/**
 * Generates a unique session ID for test runs
 */
function generateSessionId() {
    return `test-${Date.now()}-${crypto_1.default.randomBytes(4).toString('hex')}`;
}
exports.generateSessionId = generateSessionId;
/**
 * Creates a working directory for test artifacts
 * @param basePath Base directory path
 * @param sessionId Session identifier
 */
async function createWorkingDirectory(basePath, sessionId) {
    const workingDir = path_1.default.join(basePath, sessionId);
    // Create directory if it doesn't exist
    if (!fs_1.default.existsSync(workingDir)) {
        await (0, util_1.promisify)(fs_1.default.mkdir)(workingDir, { recursive: true });
    }
    return workingDir;
}
exports.createWorkingDirectory = createWorkingDirectory;
/**
 * Saves data to a file in the working directory
 * @param workingDir Working directory path
 * @param filename Name of the file
 * @param data Data to save
 */
async function saveToFile(workingDir, filename, data) {
    const filePath = path_1.default.join(workingDir, filename);
    await (0, util_1.promisify)(fs_1.default.writeFile)(filePath, data);
    return filePath;
}
exports.saveToFile = saveToFile;
/**
 * Reads data from a file
 * @param filePath Path to the file
 */
async function readFromFile(filePath) {
    return (0, util_1.promisify)(fs_1.default.readFile)(filePath, 'utf8');
}
exports.readFromFile = readFromFile;
/**
 * Sanitizes a string for safe use in filenames
 * @param input Input string
 */
function sanitizeFilename(input) {
    return input.replace(/[^a-z0-9]/gi, '_').toLowerCase();
}
exports.sanitizeFilename = sanitizeFilename;
/**
 * Formats a timestamp in a human-readable format
 * @param date Date object
 */
function formatTimestamp(date = new Date()) {
    return date.toISOString().replace(/[:.]/g, '-');
}
exports.formatTimestamp = formatTimestamp;
/**
 * Checks if required environment variables are set
 * @param requiredVars Array of required environment variable names
 */
function checkRequiredEnvVars(requiredVars) {
    const missing = requiredVars.filter(varName => !process.env[varName]);
    if (missing.length > 0) {
        console.error(`Missing required environment variables: ${missing.join(', ')}`);
        return false;
    }
    return true;
}
exports.checkRequiredEnvVars = checkRequiredEnvVars;
/**
 * Masks sensitive information in a string
 * @param input Input string
 * @param patterns Patterns to mask
 */
function maskSensitiveInfo(input, patterns) {
    let result = input;
    patterns.forEach(pattern => {
        result = result.replace(pattern, '***REDACTED***');
    });
    return result;
}
exports.maskSensitiveInfo = maskSensitiveInfo;
/**
 * Calculates execution time between two points
 * @param startTime Start time in milliseconds
 */
function calculateExecutionTime(startTime) {
    return Date.now() - startTime;
}
exports.calculateExecutionTime = calculateExecutionTime;
/**
 * Delays execution for specified milliseconds
 * @param ms Milliseconds to delay
 */
function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}
exports.delay = delay;
//# sourceMappingURL=index.js.map