import { TestReport } from '../types';
export type ReportFormat = 'html' | 'json' | 'pdf' | 'console';
export interface ReportOptions {
    formats: ReportFormat[];
    outputDir: string;
    includeScreenshots?: boolean;
    includeLogs?: boolean;
    title?: string;
}
export declare class ReportGenerator {
    private defaultOptions;
    /**
     * Generates reports in the specified formats
     * @param report The test report data
     * @param options Report generation options
     * @returns Object with paths to generated reports
     */
    generateReports(report: TestReport, options?: Partial<ReportOptions>): Promise<Record<ReportFormat, string>>;
    /**
     * Generates a summary report for multiple test reports
     * @param reports Array of test reports
     * @param options Report generation options
     * @returns Path to the generated summary report
     */
    generateSummaryReport(reports: TestReport[], options?: Partial<ReportOptions>): Promise<string>;
    /**
     * Generates an HTML report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated HTML report
     */
    private generateHtmlReport;
    /**
     * Generates a JSON report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated JSON report
     */
    private generateJsonReport;
    /**
     * Generates a PDF report
     * @param report The test report data
     * @param options Report generation options
     * @returns Path to the generated PDF report
     */
    private generatePdfReport;
    /**
     * Generates a console report and prints it
     * @param report The test report data
     * @returns A placeholder string (console output is printed directly)
     */
    private generateConsoleReport;
    /**
     * Generates HTML for a summary report of multiple tests
     * @param summary Summary data
     * @param reports Individual test reports
     * @returns HTML content for the summary
     */
    private generateSummaryHtml;
    /**
     * Saves a summary report to a file
     * @param summary The summary report data
     * @param outputPath The path where the report should be saved
     * @returns A promise that resolves when the report is saved
     */
    saveSummaryReport(summary: any, outputPath: string): Promise<void>;
    /**
     * Escapes HTML special characters to prevent XSS
     * @param text Text to escape
     * @returns Escaped HTML
     */
    private escapeHtml;
    /**
     * Gets base64 representation of an image for embedding in HTML
     * @param imagePath Path to the image file
     * @returns Base64 encoded image data
     */
    private getImageBase64;
}
