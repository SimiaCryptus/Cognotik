/// <reference types="node" />
/// <reference types="node" />
/**
 * Simple logger for internal use
 */
export declare const logger: {
    info: (message: string) => void;
    debug: (message: string) => void;
    warn: (message: string) => void;
    error: (message: string) => void;
};
/**
 * Generates a unique session ID for test runs
 */
export declare function generateSessionId(): string;
/**
 * Creates a working directory for test artifacts
 * @param basePath Base directory path
 * @param sessionId Session identifier
 */
export declare function createWorkingDirectory(basePath: string, sessionId: string): Promise<string>;
/**
 * Saves data to a file in the working directory
 * @param workingDir Working directory path
 * @param filename Name of the file
 * @param data Data to save
 */
export declare function saveToFile(workingDir: string, filename: string, data: string | Buffer): Promise<string>;
/**
 * Reads data from a file
 * @param filePath Path to the file
 */
export declare function readFromFile(filePath: string): Promise<string>;
/**
 * Sanitizes a string for safe use in filenames
 * @param input Input string
 */
export declare function sanitizeFilename(input: string): string;
/**
 * Formats a timestamp in a human-readable format
 * @param date Date object
 */
export declare function formatTimestamp(date?: Date): string;
/**
 * Checks if required environment variables are set
 * @param requiredVars Array of required environment variable names
 */
export declare function checkRequiredEnvVars(requiredVars: string[]): boolean;
/**
 * Masks sensitive information in a string
 * @param input Input string
 * @param patterns Patterns to mask
 */
export declare function maskSensitiveInfo(input: string, patterns: RegExp[]): string;
/**
 * Calculates execution time between two points
 * @param startTime Start time in milliseconds
 */
export declare function calculateExecutionTime(startTime: number): number;
/**
 * Delays execution for specified milliseconds
 * @param ms Milliseconds to delay
 */
export declare function delay(ms: number): Promise<void>;
