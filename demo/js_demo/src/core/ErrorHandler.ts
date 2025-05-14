import { BrowserContext, Page } from 'playwright';
import { ErrorCategory, ErrorRecoveryStrategy } from '../types/index';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Handles errors that occur during test execution with appropriate recovery strategies
 */
export class ErrorHandler {
  /**
   * Logs an error message
   * @param message The error message to log
   */
  log(message: string): void {
    console.error(`[ERROR] ${message}`);
  }
}

export default ErrorHandler;