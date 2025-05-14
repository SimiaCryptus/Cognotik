"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getAllTaskTypes = exports.getTaskTypeById = exports.taskTypes = exports.translationTask = exports.contentRewritingTask = exports.imageGenerationTask = exports.documentSummarizationTask = exports.dataAnalysisTask = exports.codeGenerationTask = void 0;
/**
 * Predefined task type configurations for testing various capabilities
 * of the Cognotik AI platform.
 */
/**
 * Code Generation Task
 * Tests the ability to generate code based on user requirements
 */
exports.codeGenerationTask = {
    taskId: "code_generation",
    displayName: "Code Generation",
    description: "Generates code based on user requirements",
    requiredApiProviders: ["openai"],
    testPrompts: [
        "Create a simple Python function that calculates the Fibonacci sequence",
        "Write a React component that displays a countdown timer"
    ],
    expectedOutputPatterns: [
        /def\s+fibonacci/i,
        /function\s+(\w+Component|Countdown)/i
    ],
    timeoutSeconds: 120,
    successCriteria: [
        "Response contains syntactically valid code",
        "Code executes without errors",
        "Code performs the requested function"
    ]
};
/**
 * Data Analysis Task
 * Tests the ability to analyze data and provide insights
 */
exports.dataAnalysisTask = {
    taskId: "data_analysis",
    displayName: "Data Analysis",
    description: "Analyzes data and provides insights",
    requiredApiProviders: ["openai"],
    testPrompts: [
        "Analyze this sample sales data: [CSV data]",
        "What trends can you identify in this time series data: [JSON data]"
    ],
    expectedOutputPatterns: [
        /trend|pattern|correlation|analysis/i,
        /increase|decrease|growth|decline/i
    ],
    setupInstructions: "Place sample data files in the test/fixtures directory",
    timeoutSeconds: 180,
    successCriteria: [
        "Response identifies at least one meaningful insight",
        "Response includes numerical analysis",
        "Response references specific data points"
    ]
};
/**
 * Document Summarization Task
 * Tests the ability to summarize long documents
 */
exports.documentSummarizationTask = {
    taskId: "document_summarization",
    displayName: "Document Summarization",
    description: "Summarizes long documents into concise overviews",
    requiredApiProviders: ["openai"],
    testPrompts: [
        "Summarize this research paper on climate change: [Paper content]",
        "Create a brief summary of this legal document: [Legal document]"
    ],
    expectedOutputPatterns: [
        /summary|overview|key points/i,
        /main|important|significant|findings/i
    ],
    setupInstructions: "Place sample documents in the test/fixtures directory",
    timeoutSeconds: 150,
    successCriteria: [
        "Summary is significantly shorter than original document",
        "Summary captures key information from the original",
        "Summary maintains factual accuracy"
    ]
};
/**
 * Image Generation Task
 * Tests the ability to generate images based on text descriptions
 */
exports.imageGenerationTask = {
    taskId: "image_generation",
    displayName: "Image Generation",
    description: "Generates images based on text descriptions",
    requiredApiProviders: ["openai", "dalle"],
    testPrompts: [
        "Generate an image of a futuristic city with flying cars",
        "Create an image of a peaceful mountain landscape at sunset"
    ],
    expectedOutputPatterns: [
        /image|generated|created/i,
        /data:image\/|\.png|\.jpg/i
    ],
    timeoutSeconds: 240,
    successCriteria: [
        "Response contains valid image data or URL",
        "Generated image visually corresponds to the prompt",
        "Image has acceptable quality and resolution"
    ]
};
/**
 * Content Rewriting Task
 * Tests the ability to rewrite content in different styles
 */
exports.contentRewritingTask = {
    taskId: "content_rewriting",
    displayName: "Content Rewriting",
    description: "Rewrites content in different styles or tones",
    requiredApiProviders: ["openai"],
    testPrompts: [
        "Rewrite this technical explanation for a non-technical audience: [Technical content]",
        "Rewrite this formal email in a more friendly tone: [Formal email]"
    ],
    expectedOutputPatterns: [
        /rewritten|version|alternative/i,
        /simpler|friendlier|more casual|less technical/i
    ],
    timeoutSeconds: 120,
    successCriteria: [
        "Rewritten content preserves the original meaning",
        "Style matches the requested tone",
        "Content is grammatically correct and coherent"
    ]
};
/**
 * Translation Task
 * Tests the ability to translate content between languages
 */
exports.translationTask = {
    taskId: "translation",
    displayName: "Translation",
    description: "Translates content between different languages",
    requiredApiProviders: ["openai"],
    testPrompts: [
        "Translate this English text to Spanish: [English text]",
        "Translate this Spanish text to French: [Spanish text]"
    ],
    expectedOutputPatterns: [
        /translated|translation/i,
        /español|français|deutsche/i
    ],
    timeoutSeconds: 120,
    successCriteria: [
        "Translation preserves the original meaning",
        "Translation uses correct grammar in target language",
        "Translation maintains appropriate tone and style"
    ]
};
/**
 * Map of all available task types for easy lookup
 */
exports.taskTypes = {
    code_generation: exports.codeGenerationTask,
    data_analysis: exports.dataAnalysisTask,
    document_summarization: exports.documentSummarizationTask,
    image_generation: exports.imageGenerationTask,
    content_rewriting: exports.contentRewritingTask,
    translation: exports.translationTask
};
/**
 * Get a task type configuration by ID
 * @param taskId The ID of the task type to retrieve
 * @returns The task type configuration or undefined if not found
 */
function getTaskTypeById(taskId) {
    return exports.taskTypes[taskId];
}
exports.getTaskTypeById = getTaskTypeById;
/**
 * Get all available task types
 * @returns Array of all task type configurations
 */
function getAllTaskTypes() {
    return Object.values(exports.taskTypes);
}
exports.getAllTaskTypes = getAllTaskTypes;
//# sourceMappingURL=index.js.map