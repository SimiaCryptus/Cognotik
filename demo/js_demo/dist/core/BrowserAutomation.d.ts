import { BrowserSession, BrowserType, PromptResult, TestSessionConfig } from '../types/index';
export declare class BrowserAutomation {
    private readonly config;
    private browser;
    private context;
    private page;
    private screenshotDir;
    private baseUrl;
    constructor(config: {
        baseUrl: string;
        screenshotDir: string;
        browserType?: BrowserType;
        headless?: boolean;
    });
    /**
     * Launch a browser session for testing
     */
    launch(sessionConfig: TestSessionConfig): Promise<BrowserSession>;
    /**
     * Execute a sequence of prompts in the chat interface
     */
    executePromptSequence(browserSession: BrowserSession, prompts: string[], expectedPatterns: RegExp[]): Promise<PromptResult[]>;
    /**
     * Take a screenshot of the current page state
     */
    takeScreenshot(name: string): Promise<string>;
    /**
     * Save session configuration to server
     */
    private saveSessionConfig;
    /**
     * Download any generated files
     */
    downloadGeneratedFiles(targetDir: string): Promise<string[]>;
    /**
     * Close the browser session and clean up resources
     */
    close(): Promise<void>;
    /**
     * Check if a specific element exists in the page
     */
    elementExists(selector: string): Promise<boolean>;
    /**
     * Get the text content of an element
     */
    getElementText(selector: string): Promise<string>;
    /**
     * Execute custom JavaScript in the page context
     */
    evaluateInPage<T>(fn: () => T): Promise<T>;
}
export default BrowserAutomation;
