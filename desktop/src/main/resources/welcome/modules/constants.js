// Constants module
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

const API_PROVIDERS = [
    {id: 'OpenAI', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1'},
    {id: 'Anthropic', name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1'},
    {id: 'Google', name: 'Google', baseUrl: 'https://generativelanguage.googleapis.com'},
    {id: 'Groq', name: 'Groq', baseUrl: 'https://api.groq.com/openai/v1'},
    {id: 'Mistral', name: 'Mistral', baseUrl: 'https://api.mistral.ai/v1'},
    {id: 'AWS', name: 'AWS', baseUrl: 'https://api.openai.aws'},
    {id: 'DeepSeek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com'},
    {id: 'Github', name: 'GitHub', baseUrl: 'https://api.github.com'},
    {id: 'GoogleSearch', name: 'Google Search', baseUrl: ''}
];

const AVAILABLE_MODELS = {
    OpenAI: [
        {id: 'GPT4o', name: 'GPT-4o', description: 'OpenAI\'s capable vision model'},
        {id: 'GPT4oMini', name: 'GPT-4o Mini', description: 'Smaller, faster version of GPT-4o'},
        {id: 'O1', name: 'o1', description: 'OpenAI\'s reasoning-focused model'},
        {id: 'O1Mini', name: 'o1-mini', description: 'Smaller version of o1'},
        {id: 'O1Preview', name: 'o1-preview', description: 'Preview version of o1'},
        {id: 'O3', name: 'o3', description: 'OpenAI\'s advanced reasoning model'},
        {id: 'O3Mini', name: 'o3-mini', description: 'Smaller version of o3'},
        {id: 'O4Mini', name: 'o4-mini', description: 'Latest mini reasoning model'},
        {id: 'GPT41', name: 'GPT-4.1', description: 'Latest GPT-4 series model'},
        {id: 'GPT41Mini', name: 'GPT-4.1 Mini', description: 'Smaller version of GPT-4.1'},
        {id: 'GPT41Nano', name: 'GPT-4.1 Nano', description: 'Smallest version of GPT-4.1'},
        {id: 'GPT45', name: 'GPT-4.5', description: 'Advanced preview model'}
    ],
    Anthropic: [
        {id: 'Claude35Haiku', name: 'Claude 3.5 Haiku', description: 'Smaller, faster Claude model'},
        {id: 'Claude4Sonnet', name: 'Claude 4 Sonnet', description: 'Anthropic\'s latest model'},
        {id: 'Claude41Opus', name: 'Claude 4.1 Opus', description: 'Anthropic\'s most capable model'}
    ],
    Groq: [
        {id: 'Llama33_70bVersatile', name: 'Llama 3.3 70B Versatile', description: 'Fast Llama 3.3 inference'},
        {id: 'Llama33_70bSpecDec', name: 'Llama 3.3 70B SpecDec', description: 'Specialized Llama 3.3 model'},
        {id: 'Llama31_8bInstant', name: 'Llama 3.1 8B Instant', description: 'Fast, small Llama model'},
        {id: 'Gemma2_9b', name: 'Gemma 2 9B', description: 'Google\'s Gemma model on Groq'},
        {id: 'MistralSaba24b', name: 'Mistral Saba 24B', description: 'Mistral\'s Saba model'},
        {id: 'Qwen25_32b', name: 'Qwen 2.5 32B', description: 'Qwen model on Groq'}
    ],
    Mistral: [
        {id: 'Mistral7B', name: 'Mistral 7B', description: 'Mistral\'s base model'},
        {id: 'MistralSmall', name: 'Mistral Small', description: 'Mistral\'s small model'},
        {id: 'MistralMedium', name: 'Mistral Medium', description: 'Mistral\'s medium model'},
        {id: 'MistralLarge', name: 'Mistral Large', description: 'Mistral\'s large model'},
        {id: 'Mixtral8x7B', name: 'Mixtral 8x7B', description: 'Mistral\'s Mixtral model'},
        {id: 'Mixtral8x22B', name: 'Mixtral 8x22B', description: 'Mistral\'s larger Mixtral model'},
        {id: 'Codestral', name: 'Codestral', description: 'Mistral\'s code-focused model'}
    ],
    DeepSeek: [
        {id: 'DeepSeekChat', name: 'DeepSeek Chat', description: 'DeepSeek\'s general chat model'},
        {id: 'DeepSeekCoder', name: 'DeepSeek Coder', description: 'DeepSeek\'s code-focused model'},
        {id: 'DeepSeekReasoner', name: 'DeepSeek Reasoner', description: 'DeepSeek\'s reasoning model'}
    ],
    AWS: [
        {id: 'AWSLLaMA31_405bChat', name: 'Llama 3.1 405B', description: 'Largest Llama model on AWS'},
        {id: 'AWSLLaMA31_70bChat', name: 'Llama 3.1 70B', description: 'Large Llama model on AWS'},
        {id: 'Claude35SonnetAWS', name: 'Claude 3.5 Sonnet (AWS)', description: 'Claude on AWS'},
        {id: 'Claude37SonnetAWS', name: 'Claude 3.7 Sonnet (AWS)', description: 'Latest Claude on AWS'},
        {id: 'MistralLarge2407', name: 'Mistral Large 2407', description: 'Latest Mistral Large on AWS'}
    ]
};

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        TASK_TYPES,
        API_PROVIDERS,
        AVAILABLE_MODELS
    };
}