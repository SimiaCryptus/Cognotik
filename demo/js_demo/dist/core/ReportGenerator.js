"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ReportGenerator = void 0;
const fs_1 = __importDefault(require("fs"));
const path_1 = __importDefault(require("path"));
const puppeteer_1 = __importDefault(require("puppeteer"));
const chalk_1 = __importDefault(require("chalk"));
class ReportGenerator {
    constructor() {
        this.defaultOptions = {
            formats: ['html', 'json'],
            outputDir: './reports',
            includeScreenshots: true,
            includeLogs: true,
            title: 'Cognotik Task Test Report'
        };
    }
    /**
     * Generates reports in the specified formats
     * @param report The test report data
     * @param options Report generation options
     * @returns Object with paths to generated reports
     */
    async generateReports(report, options) {
        const mergedOptions = { ...this.defaultOptions, ...options };
        const { formats, outputDir } = mergedOptions;
        // Ensure output directory exists
        if (!fs_1.default.existsSync(outputDir)) {
            fs_1.default.mkdirSync(outputDir, { recursive: true });
        }
        const results = {};
        // Generate each requested format
        for (const format of formats) {
            switch (format) {
                case 'html':
                    results.html = await this.generateHtmlReport(report, mergedOptions);
                    break;
                case 'json':
                    results.json = this.generateJsonReport(report, mergedOptions);
                    break;
                case 'pdf':
                    results.pdf = await this.generatePdfReport(report, mergedOptions);
                    break;
                case 'console':
                    results.console = this.generateConsoleReport(report);
                    break;
            }
        }
        return results;
    }
    /**
     * Generates a summary report for multiple test reports
     * @param reports Array of test reports
     * @param options Report generation options
     * @returns Path to the generated summary report
     */
    async generateSummaryReport(reports, options) {
        const mergedOptions = { ...this.defaultOptions, ...options };
        const { outputDir } = mergedOptions;
        // Create summary data
        const summary = {
            timestamp: new Date().toISOString(),
            totalTests: reports.length,
            passed: reports.filter(r => r.overallResult === 'PASS').length,
            failed: reports.filter(r => r.overallResult === 'FAIL').length,
            errors: reports.filter(r => r.overallResult === 'ERROR').length,
            tasks: reports.map(r => ({
                taskId: r.taskId,
                displayName: r.displayName,
                result: r.overallResult,
                duration: r.duration,
                successRate: r.successCriteriaMet.length /
                    (r.successCriteriaMet.length + r.successCriteriaFailed.length) * 100
            }))
        };
        // Generate HTML summary
        const htmlPath = path_1.default.join(outputDir, 'summary.html');
        const htmlContent = this.generateSummaryHtml(summary, reports);
        fs_1.default.writeFileSync(htmlPath, htmlContent);
        return htmlPath;
    }
    /**
     * Generates an HTML report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated HTML report
     */
    async generateHtmlReport(report, options) {
        const { outputDir, includeScreenshots, title } = options;
        const fileName = `${report.taskId}_${report.timestamp.replace(/[:.]/g, '-')}.html`;
        const filePath = path_1.default.join(outputDir, fileName);
        // Generate HTML content
        let html = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>${title} - ${report.displayName}</title>
      <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; color: #333; }
        .container { max-width: 1200px; margin: 0 auto; }
        header { background: #f4f4f4; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
        h1, h2, h3 { color: #444; }
        .result-pass { color: #2ecc71; }
        .result-fail { color: #e74c3c; }
        .result-error { color: #e67e22; }
        .prompt-section { background: #f9f9f9; padding: 15px; margin-bottom: 15px; border-radius: 5px; border-left: 5px solid #ddd; }
        .response { background: #f0f0f0; padding: 10px; border-radius: 3px; white-space: pre-wrap; font-family: monospace; }
        .criteria { display: flex; flex-wrap: wrap; }
        .criteria div { flex: 1; min-width: 300px; }
        .screenshot { max-width: 100%; height: auto; border: 1px solid #ddd; margin: 10px 0; }
        .collapsible { cursor: pointer; padding: 10px; background: #eee; border: none; text-align: left; width: 100%; }
        .content { padding: 0 18px; display: none; overflow: hidden; }
        .active { display: block; }
        table { width: 100%; border-collapse: collapse; }
        table, th, td { border: 1px solid #ddd; }
        th, td { padding: 12px; text-align: left; }
        th { background-color: #f2f2f2; }
      </style>
    </head>
    <body>
      <div class="container">
        <header>
          <h1>${report.displayName} Test Report</h1>
          <p><strong>Task ID:</strong> ${report.taskId}</p>
          <p><strong>Session ID:</strong> ${report.sessionId}</p>
          <p><strong>Timestamp:</strong> ${report.timestamp}</p>
          <p><strong>Duration:</strong> ${report.duration}ms</p>
          <p><strong>Overall Result:</strong> <span class="result-${report.overallResult.toLowerCase()}">${report.overallResult}</span></p>
        </header>
        
        <h2>Prompt Results</h2>
    `;
        // Add each prompt result
        report.promptResults.forEach((promptResult, index) => {
            html += `
        <div class="prompt-section">
          <h3>Prompt ${index + 1}: <span class="result-${promptResult.result.toLowerCase()}">${promptResult.result}</span></h3>
          
          <button class="collapsible">Show Prompt</button>
          <div class="content">
            <p>${promptResult.prompt}</p>
          </div>
          
          <button class="collapsible">Show Response (${promptResult.responseTime}ms)</button>
          <div class="content">
            <div class="response">${this.escapeHtml(promptResult.response)}</div>
          </div>
          
          <p><strong>Matched Patterns:</strong> ${promptResult.matchedPatterns.join(', ') || 'None'}</p>
          
          ${includeScreenshots && promptResult.screenshots.length > 0 ? `
            <button class="collapsible">Show Screenshots (${promptResult.screenshots.length})</button>
            <div class="content">
              ${promptResult.screenshots.map(screenshot => `
                <img class="screenshot" src="data:image/png;base64,${this.getImageBase64(screenshot)}" alt="Screenshot">
              `).join('')}
            </div>
          ` : ''}
        </div>
      `;
        });
        // Add success criteria
        html += `
        <h2>Success Criteria</h2>
        <div class="criteria">
          <div>
            <h3>Met Criteria (${report.successCriteriaMet.length})</h3>
            <ul>
              ${report.successCriteriaMet.map(criteria => `<li>${criteria}</li>`).join('')}
            </ul>
          </div>
          <div>
            <h3>Failed Criteria (${report.successCriteriaFailed.length})</h3>
            <ul>
              ${report.successCriteriaFailed.map(criteria => `<li>${criteria}</li>`).join('')}
            </ul>
          </div>
        </div>
    `;
        // Add artifacts
        if (report.artifacts.length > 0) {
            html += `
        <h2>Artifacts</h2>
        <ul>
          ${report.artifacts.map(artifact => `<li>${artifact}</li>`).join('')}
        </ul>
      `;
        }
        // Add logs
        if (options.includeLogs && report.logs.length > 0) {
            html += `
        <h2>Logs</h2>
        <button class="collapsible">Show Logs (${report.logs.length})</button>
        <div class="content">
          <pre>${report.logs.join('\n')}</pre>
        </div>
      `;
        }
        // Add error details if present
        if (report.errorDetails) {
            html += `
        <h2>Error Details</h2>
        <div class="response">${this.escapeHtml(report.errorDetails)}</div>
      `;
        }
        // Close HTML
        html += `
        <script>
          // Add collapsible functionality
          const collapsibles = document.getElementsByClassName("collapsible");
          for (let i = 0; i < collapsibles.length; i++) {
            collapsibles[i].addEventListener("click", function() {
              this.classList.toggle("active");
              const content = this.nextElementSibling;
              if (content.style.display === "block") {
                content.style.display = "none";
              } else {
                content.style.display = "block";
              }
            });
          }
        </script>
      </div>
    </body>
    </html>
    `;
        fs_1.default.writeFileSync(filePath, html);
        return filePath;
    }
    /**
     * Generates a JSON report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated JSON report
     */
    generateJsonReport(report, options) {
        const { outputDir } = options;
        const fileName = `${report.taskId}_${report.timestamp.replace(/[:.]/g, '-')}.json`;
        const filePath = path_1.default.join(outputDir, fileName);
        // Create a copy of the report to avoid modifying the original
        const reportCopy = JSON.parse(JSON.stringify(report));
        // Remove binary data like screenshots if needed
        if (!options.includeScreenshots) {
            reportCopy.promptResults.forEach((result) => {
                result.screenshots = result.screenshots.length > 0 ?
                    [`${result.screenshots.length} screenshots (excluded from JSON report)`] : [];
            });
        }
        // Remove logs if needed
        if (!options.includeLogs) {
            reportCopy.logs = reportCopy.logs.length > 0 ?
                [`${reportCopy.logs.length} log entries (excluded from JSON report)`] : [];
        }
        fs_1.default.writeFileSync(filePath, JSON.stringify(reportCopy, null, 2));
        return filePath;
    }
    /**
     * Generates a PDF report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated PDF report
     */
    async generatePdfReport(report, options) {
        // First generate HTML report
        const htmlPath = await this.generateHtmlReport(report, options);
        const pdfPath = htmlPath.replace('.html', '.pdf');
        // Use puppeteer to convert HTML to PDF
        const browser = await puppeteer_1.default.launch({ headless: 'new' });
        const page = await browser.newPage();
        await page.goto(`file://${path_1.default.resolve(htmlPath)}`, { waitUntil: 'networkidle0' });
        await page.pdf({
            path: pdfPath,
            format: 'A4',
            printBackground: true,
            margin: {
                top: '20px',
                right: '20px',
                bottom: '20px',
                left: '20px'
            }
        });
        await browser.close();
        return pdfPath;
    }
    /**
     * Generates a console report and prints it
     * @param report The test report data
     * @returns A placeholder string (console output is printed directly)
     */
    generateConsoleReport(report) {
        console.log('\n' + '='.repeat(80));
        console.log(chalk_1.default.bold(`${report.displayName} Test Report (${report.taskId})`));
        console.log('='.repeat(80));
        console.log(`Session ID: ${report.sessionId}`);
        console.log(`Timestamp: ${report.timestamp}`);
        console.log(`Duration: ${report.duration}ms`);
        // Print overall result with color
        let resultColor;
        switch (report.overallResult) {
            case 'PASS':
                resultColor = chalk_1.default.green;
                break;
            case 'FAIL':
                resultColor = chalk_1.default.red;
                break;
            case 'ERROR':
                resultColor = chalk_1.default.yellow;
                break;
            default: resultColor = chalk_1.default.white;
        }
        console.log(`Overall Result: ${resultColor(report.overallResult)}`);
        // Print prompt results summary
        console.log('\n' + chalk_1.default.bold('Prompt Results:'));
        report.promptResults.forEach((result, index) => {
            const resultColor = result.result === 'PASS' ? chalk_1.default.green : chalk_1.default.red;
            console.log(`  ${index + 1}. ${resultColor(result.result)} (${result.responseTime}ms)`);
            console.log(`     Prompt: ${result.prompt.substring(0, 60)}${result.prompt.length > 60 ? '...' : ''}`);
            console.log(`     Matched Patterns: ${result.matchedPatterns.length}`);
        });
        // Print success criteria
        console.log('\n' + chalk_1.default.bold('Success Criteria:'));
        console.log(chalk_1.default.green(`  Met: ${report.successCriteriaMet.length}`));
        report.successCriteriaMet.forEach(criteria => {
            console.log(`    ✓ ${criteria}`);
        });
        console.log(chalk_1.default.red(`  Failed: ${report.successCriteriaFailed.length}`));
        report.successCriteriaFailed.forEach(criteria => {
            console.log(`    ✗ ${criteria}`);
        });
        // Print error details if present
        if (report.errorDetails) {
            console.log('\n' + chalk_1.default.bold('Error Details:'));
            console.log(chalk_1.default.yellow(report.errorDetails));
        }
        console.log('\n' + '='.repeat(80) + '\n');
        // Return a placeholder - the actual output is printed to console
        return 'Console report printed';
    }
    /**
     * Generates HTML for a summary report of multiple tests
     * @param summary Summary data
     * @param reports Individual test reports
     * @returns HTML content for the summary
     */
    generateSummaryHtml(summary, reports) {
        const html = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Cognotik Task Tests Summary Report</title>
      <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; color: #333; }
        .container { max-width: 1200px; margin: 0 auto; }
        header { background: #f4f4f4; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
        h1, h2, h3 { color: #444; }
        .summary-stats { display: flex; justify-content: space-between; margin-bottom: 20px; }
        .stat-box { flex: 1; padding: 15px; margin: 0 10px; text-align: center; border-radius: 5px; }
        .stat-box.total { background-color: #f0f0f0; }
        .stat-box.passed { background-color: #d5f5e3; }
        .stat-box.failed { background-color: #fadbd8; }
        .stat-box.errors { background-color: #fef9e7; }
        .stat-number { font-size: 2em; font-weight: bold; margin: 10px 0; }
        table { width: 100%; border-collapse: collapse; }
        table, th, td { border: 1px solid #ddd; }
        th, td { padding: 12px; text-align: left; }
        th { background-color: #f2f2f2; }
        .result-PASS { color: #2ecc71; }
        .result-FAIL { color: #e74c3c; }
        .result-ERROR { color: #e67e22; }
        .progress-bar { height: 20px; background-color: #f0f0f0; border-radius: 10px; overflow: hidden; }
        .progress-fill { height: 100%; background-color: #2ecc71; }
      </style>
    </head>
    <body>
      <div class="container">
        <header>
          <h1>Cognotik Task Tests Summary Report</h1>
          <p><strong>Generated:</strong> ${summary.timestamp}</p>
        </header>
        
        <div class="summary-stats">
          <div class="stat-box total">
            <h3>Total Tests</h3>
            <div class="stat-number">${summary.totalTests}</div>
          </div>
          <div class="stat-box passed">
            <h3>Passed</h3>
            <div class="stat-number">${summary.passed}</div>
          </div>
          <div class="stat-box failed">
            <h3>Failed</h3>
            <div class="stat-number">${summary.failed}</div>
          </div>
          <div class="stat-box errors">
            <h3>Errors</h3>
            <div class="stat-number">${summary.errors}</div>
          </div>
        </div>
        
        <h2>Task Results</h2>
        <table>
          <thead>
            <tr>
              <th>Task ID</th>
              <th>Display Name</th>
              <th>Result</th>
              <th>Duration (ms)</th>
              <th>Success Rate</th>
            </tr>
          </thead>
          <tbody>
            ${summary.tasks.map((task) => `
              <tr>
                <td>${task.taskId}</td>
                <td>${task.displayName}</td>
                <td class="result-${task.result}">${task.result}</td>
                <td>${task.duration}</td>
                <td>
                  <div class="progress-bar">
                    <div class="progress-fill" style="width: ${task.successRate}%"></div>
                  </div>
                  ${task.successRate.toFixed(1)}%
                </td>
              </tr>
            `).join('')}
          </tbody>
        </table>
        
        <h2>Individual Reports</h2>
        <ul>
          ${reports.map(report => `
            <li>
              <a href="./${report.taskId}_${report.timestamp.replace(/[:.]/g, '-')}.html">
                ${report.displayName} (${report.taskId}) - ${report.timestamp}
              </a>
            </li>
          `).join('')}
        </ul>
      </div>
    </body>
    </html>
    `;
        return html;
    }
    /**
     * Saves a summary report to a file
     * @param summary The summary report data
     * @param outputPath The path where the report should be saved
     * @returns A promise that resolves when the report is saved
     */
    async saveSummaryReport(summary, outputPath) {
        try {
            // Ensure the directory exists
            const outputDir = path_1.default.dirname(outputPath);
            if (!fs_1.default.existsSync(outputDir)) {
                fs_1.default.mkdirSync(outputDir, { recursive: true });
            }
            // Determine the file format based on extension
            const ext = path_1.default.extname(outputPath).toLowerCase();
            if (ext === '.html') {
                // For HTML format, we need the reports array to generate links
                // Since we don't have it here, we'll create a simplified version
                const html = this.generateSummaryHtml(summary, summary.tasks || []);
                fs_1.default.writeFileSync(outputPath, html);
            }
            else if (ext === '.json') {
                // For JSON format, just stringify the summary
                fs_1.default.writeFileSync(outputPath, JSON.stringify(summary, null, 2));
            }
            else {
                // Default to JSON if format is unknown
                fs_1.default.writeFileSync(outputPath, JSON.stringify(summary, null, 2));
            }
        }
        catch (error) {
            console.error(`Error saving summary report to ${outputPath}:`, error);
            throw error;
        }
    }
    /**
     * Escapes HTML special characters to prevent XSS
     * @param text Text to escape
     * @returns Escaped HTML
     */
    escapeHtml(text) {
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
    /**
     * Gets base64 representation of an image for embedding in HTML
     * @param imagePath Path to the image file
     * @returns Base64 encoded image data
     */
    getImageBase64(imagePath) {
        try {
            // Check if the path is already a base64 string
            if (imagePath.startsWith('data:image')) {
                return imagePath.split(',')[1];
            }
            // Otherwise read from file
            if (fs_1.default.existsSync(imagePath)) {
                const imageBuffer = fs_1.default.readFileSync(imagePath);
                return imageBuffer.toString('base64');
            }
            return '';
        }
        catch (error) {
            console.error(`Error reading image: ${imagePath}`, error);
            return '';
        }
    }
}
exports.ReportGenerator = ReportGenerator;
//# sourceMappingURL=ReportGenerator.js.map