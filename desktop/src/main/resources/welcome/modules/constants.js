// Constants module - Task types remain static
const TASK_TYPES = [
    {
        id: 'InsightTask',
        name: 'Insight Task',
        description: 'Analyze code and provide detailed explanations of implementation patterns',
        tooltip: 'Provides detailed answers and insights about code implementation by analyzing specified files.',
    },
    {
        id: 'FileModificationTask',
        name: 'File Modification Task',
        description: 'Create new files or modify existing code with AI-powered assistance',
        tooltip: 'Creates or modifies source files with AI assistance while maintaining code quality.',
    },
    {
        id: 'DocumentationTask',
        name: 'Documentation Task',
        description: 'Generate comprehensive documentation for code and projects',
        tooltip: 'Creates detailed documentation including API docs, README files, and code comments.',
    },
    {
        id: 'TestGenerationTask',
        name: 'Test Generation Task',
        description: 'Generate unit tests and integration tests for existing code',
        tooltip: 'Automatically creates test cases to improve code coverage and reliability.',
    },
    {
        id: 'CodeReviewTask',
        name: 'Code Review Task',
        description: 'Perform automated code reviews and suggest improvements',
        tooltip: 'Reviews code for best practices, potential bugs, and optimization opportunities.',
    }
];
// API_PROVIDERS and AVAILABLE_MODELS will be loaded dynamically from /apiProviders
let API_PROVIDERS = [];
let AVAILABLE_MODELS = {};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        TASK_TYPES,
        API_PROVIDERS,
        AVAILABLE_MODELS
    };
}