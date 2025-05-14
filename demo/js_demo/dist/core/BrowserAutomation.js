"use strict";
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
Object.defineProperty(exports, "__esModule", { value: true });
exports.BrowserAutomation = void 0;
const playwright_1 = require("playwright");
const path = __importStar(require("path"));
const fs = __importStar(require("fs"));
class BrowserAutomation {
    constructor(config) {
        this.config = config;
        this.browser = null;
        this.context = null;
        this.page = null;
        this.baseUrl = config.baseUrl || 'http://localhost:3000';
        this.screenshotDir = config.screenshotDir || path.join(process.cwd(), 'screenshots');
        // Ensure screenshot directory exists
        if (!fs.existsSync(this.screenshotDir)) {
            fs.mkdirSync(this.screenshotDir, { recursive: true });
        }
    }
    /**
     * Launch a browser session for testing
     */
    async launch(sessionConfig) {
        try {
            // Select browser based on configuration
            const browserType = this.config.browserType || 'chromium';
            switch (browserType) {
                case 'firefox':
                    this.browser = await playwright_1.firefox.launch({ headless: this.config.headless !== false });
                    break;
                case 'webkit':
                    this.browser = await playwright_1.webkit.launch({ headless: this.config.headless !== false });
                    break;
                case 'chromium':
                default:
                    this.browser = await playwright_1.chromium.launch({ headless: this.config.headless !== false });
                    break;
            }
            // Create a new browser context
            this.context = await this.browser.newContext({
                recordVideo: {
                    dir: path.join(this.screenshotDir, 'videos'),
                    size: { width: 1280, height: 720 }
                }
            });
            // Create a new page
            this.page = await this.context.newPage();
            // First, save session configuration to server
            await this.saveSessionConfig(sessionConfig);
            // Navigate to the taskChat application with the session ID
            const url = `${this.baseUrl}/taskChat/#${sessionConfig.sessionId}`;
            await this.page.goto(url);
            // Wait for application to fully load
            await this.page.waitForSelector('.chat-container', { timeout: 30000 });
            // Take initial screenshot
            await this.takeScreenshot('initial-load');
            // Verify task type is correctly displayed in UI
            const taskId = Object.keys(sessionConfig.taskSettings).find(key => sessionConfig.taskSettings[key].enabled);
            if (taskId) {
                const taskTypeElement = await this.page.locator(`[data-task-id="${taskId}"]`);
                if (!await taskTypeElement.isVisible()) {
                    throw new Error(`Task type ${taskId} is not visible in the UI`);
                }
            }
            return {
                page: this.page,
                browser: this.browser,
                context: this.context,
                sessionId: sessionConfig.sessionId,
                workingDir: sessionConfig.workingDir,
                close: async () => await this.close()
            };
        }
        catch (error) {
            // Clean up resources in case of error
            await this.close();
            throw error;
        }
    }
    /**
     * Execute a sequence of prompts in the chat interface
     */
    async executePromptSequence(browserSession, prompts, expectedPatterns) {
        const results = [];
        const { page } = browserSession;
        for (let i = 0; i < prompts.length; i++) {
            const prompt = prompts[i];
            const expectedPattern = expectedPatterns[i];
            const promptIndex = i + 1;
            try {
                // Take screenshot before entering prompt
                await this.takeScreenshot(`prompt-${promptIndex}-before`);
                // Type the prompt into the chat input
                await page.locator('.chat-input textarea').fill(prompt);
                // Click the send button
                const startTime = Date.now();
                await page.locator('.chat-input button[type="submit"]').click();
                // Wait for the response to complete (look for loading indicator to disappear)
                await page.waitForSelector('.loading-indicator', { state: 'visible', timeout: 5000 })
                    .catch(() => console.log('Loading indicator not found, continuing...'));
                await page.waitForSelector('.loading-indicator', { state: 'hidden', timeout: 60000 })
                    .catch(() => console.log('Loading indicator did not disappear, continuing...'));
                // Additional wait to ensure response is complete
                await page.waitForTimeout(1000);
                const responseTime = Date.now() - startTime;
                // Take screenshot after response
                await this.takeScreenshot(`prompt-${promptIndex}-after`);
                // Capture the complete response
                const responseElement = await page.locator(`.message-container:nth-of-type(${(i + 1) * 2}) .message-content`);
                const response = await responseElement.innerText();
                // Verify response against expected output pattern
                const matches = expectedPattern ? expectedPattern.test(response) : true;
                results.push({
                    prompt,
                    response,
                    responseTime,
                    matchedPatterns: matches ? [expectedPattern?.toString() || ''] : [],
                    matchedPattern: matches ? expectedPattern?.toString() : undefined,
                    screenshots: [
                        path.join(this.screenshotDir, `prompt-${promptIndex}-before.png`),
                        path.join(this.screenshotDir, `prompt-${promptIndex}-after.png`)
                    ],
                    result: matches ? 'PASS' : 'FAIL'
                });
            }
            catch (error) {
                results.push({
                    prompt,
                    response: '',
                    responseTime: 0,
                    matchedPatterns: [],
                    error: error instanceof Error ? error.message : String(error),
                    screenshots: [
                        path.join(this.screenshotDir, `prompt-${promptIndex}-before.png`)
                    ],
                    result: 'FAIL'
                });
            }
        }
        return results;
    }
    /**
     * Take a screenshot of the current page state
     */
    async takeScreenshot(name) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        const filename = `${name}-${Date.now()}.png`;
        const filepath = path.join(this.screenshotDir, filename);
        await this.page.screenshot({ path: filepath, fullPage: true });
        return filepath;
    }
    /**
     * Save session configuration to server
     */
    async saveSessionConfig(sessionConfig) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        // Navigate to settings page
        await this.page.goto(`${this.baseUrl}/taskChat/settings`);
        // Wait for settings page to load
        await this.page.waitForSelector('form', { timeout: 10000 });
        // Use page.evaluate to set the configuration in localStorage
        await this.page.evaluate((config) => {
            localStorage.setItem(`taskChat_settings_${config.sessionId}`, JSON.stringify(config));
        }, sessionConfig);
        // Optionally submit the form if needed
        // await this.page.locator('form button[type="submit"]').click();
    }
    /**
     * Download any generated files
     */
    async downloadGeneratedFiles(targetDir) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        // Find all download links
        const downloadLinks = await this.page.locator('.download-link').all();
        const downloadedFiles = [];
        for (const link of downloadLinks) {
            // Get the download attribute or href
            const downloadPath = await link.getAttribute('download') ||
                await link.getAttribute('href') || '';
            if (downloadPath) {
                // Setup download listener
                const [download] = await Promise.all([
                    this.page.waitForEvent('download'),
                    link.click()
                ]);
                // Save downloaded file
                const fileName = download.suggestedFilename();
                const filePath = path.join(targetDir, fileName);
                await download.saveAs(filePath);
                downloadedFiles.push(filePath);
            }
        }
        return downloadedFiles;
    }
    /**
     * Close the browser session and clean up resources
     */
    async close() {
        if (this.context) {
            await this.context.close();
            this.context = null;
        }
        if (this.browser) {
            await this.browser.close();
            this.browser = null;
        }
        this.page = null;
    }
    /**
     * Check if a specific element exists in the page
     */
    async elementExists(selector) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        const count = await this.page.locator(selector).count();
        return count > 0;
    }
    /**
     * Get the text content of an element
     */
    async getElementText(selector) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        return await this.page.locator(selector).innerText();
    }
    /**
     * Execute custom JavaScript in the page context
     */
    async evaluateInPage(fn) {
        if (!this.page) {
            throw new Error('Browser page not initialized');
        }
        return await this.page.evaluate(fn);
    }
}
exports.BrowserAutomation = BrowserAutomation;
exports.default = BrowserAutomation;
//# sourceMappingURL=BrowserAutomation.js.map