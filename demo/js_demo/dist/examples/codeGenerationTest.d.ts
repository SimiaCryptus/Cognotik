import { TestReport } from '../types';
/**
 * Example script demonstrating how to run a test for the code_generation task type
 *
 * This example:
 * 1. Creates a new tester instance
 * 2. Runs a test for the code_generation task
 * 3. Generates reports in HTML and JSON formats
 * 4. Logs the results to the console
 */
declare function runCodeGenerationTest(): Promise<TestReport>;
export { runCodeGenerationTest };
