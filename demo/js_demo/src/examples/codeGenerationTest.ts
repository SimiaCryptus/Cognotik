import { CognotikTaskTester } from '../index';
import { TestReport } from '../types';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Example script demonstrating how to run a test for the code_generation task type
 * 
 * This example:
 * 1. Creates a new tester instance
 * 2. Runs a test for the code_generation task
 * 3. Generates reports in HTML and JSON formats
 * 4. Logs the results to the console
 */
async function runCodeGenerationTest() {
  try {
    // Create a new tester instance
    const tester = new CognotikTaskTester({
      apiConfigPath: path.resolve(__dirname, '../../config/api-config.json'),
      reportDir: path.resolve(__dirname, '../../test-reports'),
      verbose: true
    });

    console.log('Starting code generation task test...');
    
    // Run the test for the code_generation task
    const report: TestReport = await tester.runTaskTest('code_generation');
    
    // Log the test results
    console.log(`\nTest completed with result: ${report.overallResult}`);
    console.log(`Test duration: ${report.duration}ms`);
    console.log(`Session ID: ${report.sessionId}`);
    
    // Log success criteria results
    console.log('\nSuccess criteria met:');
    report.successCriteriaMet.forEach(criteria => console.log(`✅ ${criteria}`));
    
    if (report.successCriteriaFailed.length > 0) {
      console.log('\nSuccess criteria failed:');
      report.successCriteriaFailed.forEach(criteria => console.log(`❌ ${criteria}`));
    }
    
    // Log prompt results summary
    console.log('\nPrompt results:');
    report.promptResults.forEach((result, index) => {
      console.log(`\nPrompt ${index + 1}: ${result.prompt.substring(0, 50)}...`);
      console.log(`Result: ${result.result}`);
      console.log(`Response time: ${result.responseTime}ms`);
      console.log(`Matched patterns: ${result.matchedPatterns.length}`);
    });
    
    // Generate and save reports
    const reportOutputDir = path.resolve(__dirname, '../../test-reports', report.taskId);
    
    // Ensure the directory exists
    if (!fs.existsSync(reportOutputDir)) {
      fs.mkdirSync(reportOutputDir, { recursive: true });
    }
    
    await tester.generateReports(report, {
      formats: ['html', 'json'],
      outputDir: reportOutputDir
    });
    
    console.log(`\nReports generated in: ${reportOutputDir}`);
    console.log(`HTML Report: ${path.join(reportOutputDir, `${report.taskId}_${report.timestamp}.html`)}`);
    console.log(`JSON Report: ${path.join(reportOutputDir, `${report.taskId}_${report.timestamp}.json`)}`);
    
    // Example of how to run all task tests
    if (process.env.RUN_ALL_TESTS === 'true') {
      console.log('\nRunning all available task tests...');
      const allReports = await tester.runAllTaskTests();
      const summary = tester.generateSummaryReport(allReports);
      
      console.log(`\nAll tests completed. Overall success rate: ${summary.successRate}%`);
      console.log(`Passed: ${summary.passed}, Failed: ${summary.failed}, Errors: ${summary.errors}`);
      
      // Save the summary report
      const summaryPath = path.join(reportOutputDir, `summary_${new Date().toISOString().split('T')[0]}.html`);
      await tester.saveSummaryReport(summary, summaryPath);
      console.log(`Summary report saved to: ${summaryPath}`);
    }
    
    return report;
  } catch (error) {
    console.error('Error running code generation test:', error);
    throw error;
  }
}

// Run the example if this file is executed directly
if (require.main === module) {
  runCodeGenerationTest()
    .then(() => {
      console.log('Example completed successfully');
      process.exit(0);
    })
    .catch(error => {
      console.error('Example failed:', error);
      process.exit(1);
    });
}

// Export for use in other examples or tests
export { runCodeGenerationTest };