import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { promisify } from 'util';
/**
 * Simple logger for internal use
 */
export const logger = {
  info: (message: string) => console.log(`[INFO] ${message}`),
  debug: (message: string) => console.log(`[DEBUG] ${message}`),
  warn: (message: string) => console.warn(`[WARN] ${message}`),
  error: (message: string) => console.error(`[ERROR] ${message}`)
};


/**
 * Generates a unique session ID for test runs
 */
export function generateSessionId(): string {
  return `test-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;
}

/**
 * Creates a working directory for test artifacts
 * @param basePath Base directory path
 * @param sessionId Session identifier
 */
export async function createWorkingDirectory(basePath: string, sessionId: string): Promise<string> {
  const workingDir = path.join(basePath, sessionId);
  
  // Create directory if it doesn't exist
  if (!fs.existsSync(workingDir)) {
    await promisify(fs.mkdir)(workingDir, { recursive: true });
  }
  
  return workingDir;
}

/**
 * Saves data to a file in the working directory
 * @param workingDir Working directory path
 * @param filename Name of the file
 * @param data Data to save
 */
export async function saveToFile(workingDir: string, filename: string, data: string | Buffer): Promise<string> {
  const filePath = path.join(workingDir, filename);
  await promisify(fs.writeFile)(filePath, data);
  return filePath;
}

/**
 * Reads data from a file
 * @param filePath Path to the file
 */
export async function readFromFile(filePath: string): Promise<string> {
  return promisify(fs.readFile)(filePath, 'utf8');
}

/**
 * Sanitizes a string for safe use in filenames
 * @param input Input string
 */
export function sanitizeFilename(input: string): string {
  return input.replace(/[^a-z0-9]/gi, '_').toLowerCase();
}

/**
 * Formats a timestamp in a human-readable format
 * @param date Date object
 */
export function formatTimestamp(date: Date = new Date()): string {
  return date.toISOString().replace(/[:.]/g, '-');
}

/**
 * Checks if required environment variables are set
 * @param requiredVars Array of required environment variable names
 */
export function checkRequiredEnvVars(requiredVars: string[]): boolean {
  const missing = requiredVars.filter(varName => !process.env[varName]);
  
  if (missing.length > 0) {
    console.error(`Missing required environment variables: ${missing.join(', ')}`);
    return false;
  }
  
  return true;
}

/**
 * Masks sensitive information in a string
 * @param input Input string
 * @param patterns Patterns to mask
 */
export function maskSensitiveInfo(input: string, patterns: RegExp[]): string {
  let result = input;
  
  patterns.forEach(pattern => {
    result = result.replace(pattern, '***REDACTED***');
  });
  
  return result;
}

/**
 * Calculates execution time between two points
 * @param startTime Start time in milliseconds
 */
export function calculateExecutionTime(startTime: number): number {
  return Date.now() - startTime;
}

/**
 * Delays execution for specified milliseconds
 * @param ms Milliseconds to delay
 */
export function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}