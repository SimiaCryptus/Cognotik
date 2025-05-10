function generateSessionId() {
    console.log('[generateSessionId] Called');
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');

    const randomChars = Math.random().toString(36).substring(2, 6);
    const sessionId = `U-${year}${month}${day}-${randomChars}`;
    console.log('[generateSessionId] Generated sessionId:', sessionId);
    return sessionId;
}

let sessionId = generateSessionId();
console.log('[Global] Initial sessionId:', sessionId);
let apiSettings = {};
let taskSettings = {
    defaultModel: localStorage.getItem('defaultModel') || 'GPT4o',
    parsingModel: localStorage.getItem('parsingModel') || 'GPT4oMini',
    workingDir: localStorage.getItem('workingDir') || generateTimestampedDirectory(),
    autoFix: localStorage.getItem('autoFix') === 'true',
    maxTaskHistoryChars: 20000,
    maxTasksPerIteration: 3,
    maxIterations: 100,
    graphFile: '',
    taskSettings: {},
};
let cognitiveMode = localStorage.getItem('cognitiveMode') || 'single-task';
console.log('[Global] Initial taskSettings:', JSON.parse(JSON.stringify(taskSettings))); // Deep copy for logging
console.log('[Global] Initial cognitiveMode:', cognitiveMode);

const taskTypes = [
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
        id: 'RunShellCommandTask',
        name: 'Run Shell Command Task',
        description: 'Execute shell commands safely',
        tooltip: 'Executes shell commands in a controlled environment.',
    },
    {
        id: 'RunCodeTask',
        name: 'Run Code Task',
        description: 'Execute code snippets with AI assistance',
        tooltip: 'Executes code snippets with AI assistance while maintaining code quality.',
    },
    {
        id: 'CommandAutoFixTask',
        name: 'Command Auto Fix Task',
        description: 'Run a command and automatically fix any issues that arise',
        tooltip: 'Executes a command and automatically fixes any issues that arise.',
    },
    {
        id: 'FileSearchTask',
        name: 'File Search Task',
        description: 'Search project files using patterns with contextual results',
        tooltip: 'Performs pattern-based searches across project files with context.',
    },
    {
        id: 'CrawlerAgentTask',
        name: 'Web Search Task',
        description: 'Search Google, fetch top results, and analyze content',
        tooltip: 'Searches Google for specified queries and analyzes the top results.',
    },
    {
        id: 'GitHubSearchTask',
        name: 'GitHub Search Task',
        description: 'Search GitHub repositories, code, issues and users',
        tooltip: 'Performs comprehensive searches across GitHub\'s content.',
    },
    /*
        {
          id: 'TaskPlanningTask',
          name: 'Task Planning Task',
          description: 'Break down and coordinate complex development tasks with dependency management',
          tooltip: 'Orchestrates complex development tasks by breaking them down into manageable subtasks.',
        },
        {
          id: 'ForeachTask',
          name: 'Foreach Task',
          description: 'Execute subtasks for each item in a list',
          tooltip: 'Executes a set of subtasks for each item in a given list.',
        },
        {
          id: 'KnowledgeIndexingTask',
          name: 'Knowledge Indexing Task',
          description: 'Index content for semantic search capabilities',
          tooltip: 'Indexes documents and code for semantic search capabilities.',
        },
        {
          id: 'EmbeddingSearchTask',
          name: 'Embedding Search Task',
          description: 'Perform semantic search using AI embeddings',
          tooltip: 'Performs semantic search using AI embeddings across indexed content.',
        },
        {
          id: 'SeleniumSessionTask',
          name: 'Selenium Session Task',
          description: 'Automate browser interactions with Selenium',
          tooltip: 'Automates browser interactions using Selenium WebDriver.',
        },
        {
          id: 'CommandSessionTask',
          name: 'Command Session Task',
          description: 'Manage interactive command-line sessions',
          tooltip: 'Manages interactive command-line sessions with state persistence.',
        },
        {
          id: 'SoftwareGraphPlanningTask',
          name: 'Software Graph Planning Task',
          description: 'Generate and execute task plans based on software graph structure',
          tooltip: 'Creates task plans using software graph context.',
        },
        {
          id: 'SoftwareGraphModificationTask',
          name: 'Software Graph Modification Task',
          description: 'Modify an existing software graph representation',
          tooltip: 'Loads, modifies and saves software graph representations.',
        },
        {
          id: 'SoftwareGraphGenerationTask',
          name: 'Software Graph Generation Task',
          description: 'Generate a SoftwareGraph representation of the codebase',
          tooltip: 'Generates a comprehensive SoftwareGraph representation of the codebase.',
        },
        {
          id: 'DataTableCompilationTask',
          name: 'Data Table Compilation Task',
          description: 'Compile structured data tables from multiple files',
          tooltip: 'Extracts and compiles structured data from multiple files into a unified table.',
        },
    */
];

const apiProviders = [
    {id: 'OpenAI', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1'},
    {id: 'Anthropic', name: 'Anthropic', baseUrl: 'https://api.anthropic.com/v1'},
    {id: 'Google', name: 'Google', baseUrl: 'https://generativelanguage.googleapis.com'},
    {id: 'Groq', name: 'Groq', baseUrl: 'https://api.groq.com/openai/v1'},
    {id: 'Mistral', name: 'Mistral', baseUrl: 'https://api.mistral.ai/v1'},
    {id: 'AWS', name: 'AWS', baseUrl: 'https://api.openai.aws'},
    {id: 'DeepSeek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com'},
    {id: 'Github', name: 'GitHub', baseUrl: 'https://api.github.com'},
    {id: 'GoogleSearch', name: 'Google Search', baseUrl: ''},
];

function generateTimestampedDirectory() {
    console.log('[generateTimestampedDirectory] Called');
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const dir = `sessions/${year}${month}${day}${hours}${minutes}${seconds}`;
    console.log('[generateTimestampedDirectory] Generated directory:', dir);
    return dir;
}

const availableModels = {
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
        {id: 'GPT45', name: 'GPT-4.5', description: 'Advanced preview model'},
    ],
    Anthropic: [
        {id: 'Claude35Sonnet', name: 'Claude 3.5 Sonnet', description: 'Anthropic\'s advanced model'},
        {id: 'Claude37Sonnet', name: 'Claude 3.7 Sonnet', description: 'Anthropic\'s latest model'},
        {id: 'Claude35Haiku', name: 'Claude 3.5 Haiku', description: 'Smaller, faster Claude model'},
        {id: 'Claude3Opus', name: 'Claude 3 Opus', description: 'Anthropic\'s most capable model'},
        {id: 'Claude3Sonnet', name: 'Claude 3 Sonnet', description: 'Balanced Claude model'},
        {id: 'Claude3Haiku', name: 'Claude 3 Haiku', description: 'Fast, efficient Claude model'},
    ],
    Groq: [
        {id: 'Llama33_70bVersatile', name: 'Llama 3.3 70B Versatile', description: 'Fast Llama 3.3 inference'},
        {id: 'Llama33_70bSpecDec', name: 'Llama 3.3 70B SpecDec', description: 'Specialized Llama 3.3 model'},
        {id: 'Llama31_8bInstant', name: 'Llama 3.1 8B Instant', description: 'Fast, small Llama model'},
        {id: 'Gemma2_9b', name: 'Gemma 2 9B', description: 'Google\'s Gemma model on Groq'},
        {id: 'MistralSaba24b', name: 'Mistral Saba 24B', description: 'Mistral\'s Saba model'},
        {id: 'Qwen25_32b', name: 'Qwen 2.5 32B', description: 'Qwen model on Groq'},
    ],
    Mistral: [
        {id: 'Mistral7B', name: 'Mistral 7B', description: 'Mistral\'s base model'},
        {id: 'MistralSmall', name: 'Mistral Small', description: 'Mistral\'s small model'},
        {id: 'MistralMedium', name: 'Mistral Medium', description: 'Mistral\'s medium model'},
        {id: 'MistralLarge', name: 'Mistral Large', description: 'Mistral\'s large model'},
        {id: 'Mixtral8x7B', name: 'Mixtral 8x7B', description: 'Mistral\'s Mixtral model'},
        {id: 'Mixtral8x22B', name: 'Mixtral 8x22B', description: 'Mistral\'s larger Mixtral model'},
        {id: 'Codestral', name: 'Codestral', description: 'Mistral\'s code-focused model'},
    ],
    DeepSeek: [
        {id: 'DeepSeekChat', name: 'DeepSeek Chat', description: 'DeepSeek\'s general chat model'},
        {id: 'DeepSeekCoder', name: 'DeepSeek Coder', description: 'DeepSeek\'s code-focused model'},
        {id: 'DeepSeekReasoner', name: 'DeepSeek Reasoner', description: 'DeepSeek\'s reasoning model'},
    ],
    AWS: [
        {id: 'AWSLLaMA31_405bChat', name: 'Llama 3.1 405B', description: 'Largest Llama model on AWS'},
        {id: 'AWSLLaMA31_70bChat', name: 'Llama 3.1 70B', description: 'Large Llama model on AWS'},
        {id: 'Claude35SonnetAWS', name: 'Claude 3.5 Sonnet (AWS)', description: 'Claude on AWS'},
        {id: 'Claude37SonnetAWS', name: 'Claude 3.7 Sonnet (AWS)', description: 'Latest Claude on AWS'},
        {id: 'MistralLarge2407', name: 'Mistral Large 2407', description: 'Latest Mistral Large on AWS'},
    ],
};

function populateModelSelections() {
    console.log('[populateModelSelections] Called');
    const modelSelect = document.getElementById('model-selection');
    const parsingModelSelect = document.getElementById('parsing-model');
    if (!modelSelect || !parsingModelSelect) {
        console.warn('[populateModelSelections] modelSelect or parsingModelSelect element not found.');
        return;
    }
    console.log('[populateModelSelections] Current taskSettings.defaultModel:', taskSettings.defaultModel, 'taskSettings.parsingModel:', taskSettings.parsingModel);

    const savedDefaultModel = taskSettings.defaultModel;
    const savedParsingModel = taskSettings.parsingModel;

    modelSelect.innerHTML = '';
    parsingModelSelect.innerHTML = '';

    const addedModels = new Set();

    if (apiSettings && apiSettings.apiKeys) {
        for (const [provider, key] of Object.entries(apiSettings.apiKeys)) {
            console.log(`[populateModelSelections] Checking provider: ${provider}, key exists: ${!!key}`);
            if (key && availableModels[provider]) {

                availableModels[provider].forEach(model => {
                    if (!addedModels.has(model.id)) {
                        console.log(`[populateModelSelections] Adding model ${model.id} from provider ${provider}`);

                        const option = document.createElement('option');
                        option.value = model.id;
                        option.textContent = `${model.name} (${provider})`;
                        option.title = model.description;
                        modelSelect.appendChild(option);

                        const parsingOption = document.createElement('option');
                        parsingOption.value = model.id;
                        parsingOption.textContent = `${model.name} (${provider})`;
                        parsingOption.title = model.description;
                        parsingModelSelect.appendChild(parsingOption);
                        addedModels.add(model.id);
                    }
                });
            }
        }
    }

    if (modelSelect.options.length === 0) {
        console.log('[populateModelSelections] No models available from API keys, adding default OpenAI options.');
        const defaultOption = document.createElement('option');
        defaultOption.value = 'GPT4o';
        defaultOption.textContent = 'GPT-4o (OpenAI) - Configure API key';
        modelSelect.appendChild(defaultOption);
        const defaultParsingOption = document.createElement('option');
        defaultParsingOption.value = 'GPT4oMini';
        defaultParsingOption.textContent = 'GPT-4o Mini (OpenAI) - Configure API key';
        parsingModelSelect.appendChild(defaultParsingOption);
    }

    if (savedDefaultModel && Array.from(modelSelect.options).some(opt => opt.value === savedDefaultModel)) {
        modelSelect.value = savedDefaultModel;
        console.log('[populateModelSelections] Restored savedDefaultModel:', savedDefaultModel);
    } else if (modelSelect.options.length > 0) {

        modelSelect.selectedIndex = 0;

        taskSettings.defaultModel = modelSelect.value;
        console.log('[populateModelSelections] Set defaultModel to first option:', modelSelect.value);
    } else {
        console.log('[populateModelSelections] No options in modelSelect, defaultModel remains:', taskSettings.defaultModel);
    }

    if (savedParsingModel && Array.from(parsingModelSelect.options).some(opt => opt.value === savedParsingModel)) {
        parsingModelSelect.value = savedParsingModel;
        console.log('[populateModelSelections] Restored savedParsingModel:', savedParsingModel);
    } else if (parsingModelSelect.options.length > 0) {

        parsingModelSelect.selectedIndex = 0;

        taskSettings.parsingModel = parsingModelSelect.value;
        console.log('[populateModelSelections] Set parsingModel to first option:', parsingModelSelect.value);
    } else {
        console.log('[populateModelSelections] No options in parsingModelSelect, parsingModel remains:', taskSettings.parsingModel);
    }
    console.log('[populateModelSelections] Finished. Final modelSelect.value:', modelSelect.value, 'parsingModelSelect.value:', parsingModelSelect.value);
}

function loadSettingsFromServer() {
    console.log('[loadSettingsFromServer] Called');
    fetch('/userSettings/')
        .then(response => {
            console.log('[loadSettingsFromServer] Received response:', response);
            return response.text();
        })
        .then(html => {
            const parser = new DOMParser();
            const doc = parser.parseFromString(html, 'text/html');
            const textarea = doc.querySelector('textarea[name="settings"]');
            if (textarea) {
                try {
                    const settings = JSON.parse(textarea.textContent);
                    apiSettings = settings;
                    console.log('[loadSettingsFromServer] Parsed settings:', JSON.parse(JSON.stringify(apiSettings)));

                    if (apiSettings.apiKeys) {
                        for (const [provider, key] of Object.entries(apiSettings.apiKeys)) {
                            const input = document.getElementById(`api-key-${provider}`);
                            console.log(`[loadSettingsFromServer] Processing API key for provider: ${provider}, key exists: ${!!key}`);
                            if (input && key) {
                                input.value = '********';
                            }
                        }

                        if (settings.apiBase) {
                            for (const [provider, baseUrl] of Object.entries(settings.apiBase)) {
                                const baseInput = document.getElementById(`api-base-${provider}`);
                                console.log(`[loadSettingsFromServer] Processing API base for provider: ${provider}, baseUrl: ${baseUrl}`);
                                if (baseInput && baseUrl) {
                                    baseInput.value = baseUrl;
                                }
                            }
                        }
                    }
                    // Populate local tools
                    if (settings.localTools && Array.isArray(settings.localTools)) {
                        const toolsList = document.getElementById('local-tools-list');
                        console.log('[loadSettingsFromServer] Populating local tools:', settings.localTools);
                        toolsList.innerHTML = '';
                        settings.localTools.forEach(toolPath => {
                            const toolItem = document.createElement('div');
                            console.log('[loadSettingsFromServer] Adding local tool:', toolPath);
                            toolItem.className = 'tool-item';
                            toolItem.dataset.path = toolPath;
                            const toolText = document.createElement('span');
                            toolText.textContent = toolPath;
                            const removeBtn = document.createElement('button');
                            removeBtn.className = 'remove-tool';
                            removeBtn.textContent = '×';
                            removeBtn.addEventListener('click', function () {
                                toolItem.remove();
                            });
                            toolItem.appendChild(toolText);
                            toolItem.appendChild(removeBtn);
                            toolsList.appendChild(toolItem);
                        });
                    }

                    populateModelSelections();
                } catch (e) {
                    console.error('[loadSettingsFromServer] Error parsing API settings:', e);
                }
            }
        })
        .catch(error => {
            console.error('[loadSettingsFromServer] Error loading API settings:', error);
        });
    // Removed reference to basicChatBtn here (was causing error)
}

function populateWorkingDirFromHash() {
    console.log('[populateWorkingDirFromHash] Called');
    if (window.location.hash) {
        console.log('[populateWorkingDirFromHash] Found hash:', window.location.hash);

        let workingDir = decodeURIComponent(window.location.hash.substring(1));
        console.log('[populateWorkingDirFromHash] Decoded workingDir from hash:', workingDir);

        const workingDirInput = document.getElementById('working-dir');
        if (workingDirInput) {
            workingDirInput.value = workingDir;

            taskSettings.workingDir = workingDir;
            console.log('[populateWorkingDirFromHash] Set workingDirInput value and taskSettings.workingDir to:', workingDir);
        } else {
            console.warn('[populateWorkingDirFromHash] working-dir input element not found.');
        }
    }
}

document.addEventListener('DOMContentLoaded', function () {

    setupWizard();
    console.log('[DOMContentLoaded] setupWizard called.');

    initializeApiSettings();
    console.log('[DOMContentLoaded] initializeApiSettings called.');

    initializeTaskToggles();
    console.log('[DOMContentLoaded] initializeTaskToggles called.');

    setupEventListeners();
    console.log('[DOMContentLoaded] setupEventListeners called.');

    loadSettingsFromServer();
    console.log('[DOMContentLoaded] loadSettingsFromServer called.');

    populateWorkingDirFromHash();
    console.log('[DOMContentLoaded] populateWorkingDirFromHash called.');

    populateModelSelections();
    console.log('[DOMContentLoaded] populateModelSelections called.');

    loadSavedSettings(); // Now defined
    console.log('[DOMContentLoaded] loadSavedSettings called.');

    if (!taskSettings.taskSettings || Object.keys(taskSettings.taskSettings).length === 0) {
        saveTaskSelection();
    }

    // --- Basic Chat Modal Setup ---
    const basicChatBtn = document.getElementById('open-basic-chat');
    const basicChatModal = document.getElementById('basic-chat-settings-modal');
    const closeBasicChatModal = document.getElementById('close-basic-chat-modal');
    const cancelBasicChatSettings = document.getElementById('cancel-basic-chat-settings');
    const basicChatForm = document.getElementById('basic-chat-settings-form');
    const tempSlider = document.getElementById('basic-chat-temperature');
    const tempValue = document.getElementById('basic-chat-temperature-value');

    basicChatBtn.addEventListener('click', function () {
        console.log('[DOMContentLoaded] basicChatBtn clicked.');
        // Populate model selectors with available models (same as main pipeline)
        populateBasicChatModelSelections();
        // Prefill using main pipeline's preferences (shared keys), fallback to legacy basicChat* keys, then default
        const model = localStorage.getItem('defaultModel') || localStorage.getItem('basicChatModel') || 'GPT4o';
        console.log(`[DOMContentLoaded] Basic Chat Modal: model determined as ${model} (defaultModel: ${localStorage.getItem('defaultModel')}, basicChatModel: ${localStorage.getItem('basicChatModel')})`);
        const parsingModel = localStorage.getItem('parsingModel') || localStorage.getItem('basicChatParsingModel') || 'GPT4oMini';
        const temperature = localStorage.getItem('temperature') || localStorage.getItem('basicChatTemperature') || '0.3';
        const budget = localStorage.getItem('budget') || localStorage.getItem('basicChatBudget') || '2.0';
        document.getElementById('basic-chat-model').value = model;
        document.getElementById('basic-chat-parsing-model').value = parsingModel;
        document.getElementById('basic-chat-temperature').value = temperature;
        document.getElementById('basic-chat-temperature-value').textContent = temperature;
        document.getElementById('basic-chat-budget').value = budget;
        console.log('[DOMContentLoaded] Basic Chat Modal prefilled with: model:', model, 'parsingModel:', parsingModel, 'temperature:', temperature, 'budget:', budget);
        basicChatModal.style.display = "block";
    });

    // Populate the Basic Chat model selectors with available models based on API keys
    function populateBasicChatModelSelections() {
        console.log('[populateBasicChatModelSelections] Called');
        const modelSelect = document.getElementById('basic-chat-model');
        const parsingModelSelect = document.getElementById('basic-chat-parsing-model');
        if (!modelSelect || !parsingModelSelect) {
            console.warn('[populateBasicChatModelSelections] basic-chat-model or basic-chat-parsing-model element not found.');
            return;
        }

        // Save current values to try to preserve selection
        const prevModel = modelSelect.value;
        const prevParsingModel = parsingModelSelect.value;
        console.log('[populateBasicChatModelSelections] Previous selections - model:', prevModel, 'parsingModel:', prevParsingModel);

        modelSelect.innerHTML = '';
        parsingModelSelect.innerHTML = '';
        const addedModels = new Set();
        if (apiSettings && apiSettings.apiKeys) {
            for (const [provider, key] of Object.entries(apiSettings.apiKeys)) {
                console.log(`[populateBasicChatModelSelections] Checking provider: ${provider}, key exists: ${!!key}`);
                if (key && availableModels[provider]) {
                    availableModels[provider].forEach(model => {
                        if (!addedModels.has(model.id)) {
                            console.log(`[populateBasicChatModelSelections] Adding model ${model.id} from provider ${provider}`);
                            const option = document.createElement('option');
                            option.value = model.id;
                            option.textContent = `${model.name} (${provider})`;
                            option.title = model.description;
                            modelSelect.appendChild(option);

                            const parsingOption = document.createElement('option');
                            parsingOption.value = model.id;
                            parsingOption.textContent = `${model.name} (${provider})`;
                            parsingOption.title = model.description;
                            parsingModelSelect.appendChild(parsingOption);
                            addedModels.add(model.id);
                        }
                    });
                }
            }
        }
        // If no models available, show default
        if (modelSelect.options.length === 0) {
            console.log('[populateBasicChatModelSelections] No models available from API keys, adding default OpenAI options for basic chat.');
            const defaultOption = document.createElement('option');
            defaultOption.value = 'GPT4o';
            defaultOption.textContent = 'GPT-4o (OpenAI) - Configure API key';
            modelSelect.appendChild(defaultOption);
            const defaultParsingOption = document.createElement('option');
            defaultParsingOption.value = 'GPT4oMini';
            defaultParsingOption.textContent = 'GPT-4o Mini (OpenAI) - Configure API key';
            parsingModelSelect.appendChild(defaultParsingOption);
        }
        // Try to restore previous selection
        if (prevModel && Array.from(modelSelect.options).some(opt => opt.value === prevModel)) {
            modelSelect.value = prevModel;
            console.log('[populateBasicChatModelSelections] Restored previous model selection:', prevModel);
        }
        if (prevParsingModel && Array.from(parsingModelSelect.options).some(opt => opt.value === prevParsingModel)) {
            parsingModelSelect.value = prevParsingModel;
            console.log('[populateBasicChatModelSelections] Restored previous parsing model selection:', prevParsingModel);
        }
        console.log('[populateBasicChatModelSelections] Finished. Final basic-chat-model.value:', modelSelect.value, 'basic-chat-parsing-model.value:', parsingModelSelect.value);
    }

    // Update temperature value display
    tempSlider.addEventListener('input', function () {
        console.log('[DOMContentLoaded] Basic Chat tempSlider input event. New value:', this.value);
        tempValue.textContent = this.value;
    });

    closeBasicChatModal.onclick = function () {
        console.log('[DOMContentLoaded] closeBasicChatModal clicked.');
        basicChatModal.style.display = "none";
    };
    cancelBasicChatSettings.onclick = function () {
        console.log('[DOMContentLoaded] cancelBasicChatSettings clicked.');
        basicChatModal.style.display = "none";
    };
    window.addEventListener('click', function (event) {
        if (event.target === basicChatModal) {
            console.log('[DOMContentLoaded] Window click event, target is basicChatModal. Closing modal.');
            basicChatModal.style.display = "none";
        }
    });
    // Save to localStorage for convenience
    basicChatForm.addEventListener('submit', function (e) {
        console.log('[DOMContentLoaded] basicChatForm submitted.');
        e.preventDefault();
        // Gather settings
        const model = document.getElementById('basic-chat-model').value;
        const parsingModel = document.getElementById('basic-chat-parsing-model').value;
        const temperature = parseFloat(document.getElementById('basic-chat-temperature').value);
        const budget = parseFloat(document.getElementById('basic-chat-budget').value);
        console.log('[DOMContentLoaded] Basic Chat Form Save - model:', model, 'parsingModel:', parsingModel, 'temperature:', temperature, 'budget:', budget);

        // Save to localStorage for convenience AND sync with main pipeline preferences
        // Always use main pipeline keys for shared params
        localStorage.setItem('defaultModel', model);
        localStorage.setItem('parsingModel', parsingModel);
        localStorage.setItem('temperature', temperature);
        localStorage.setItem('budget', budget);
        // Also keep legacy basicChat* keys for backward compatibility if needed
        localStorage.setItem('basicChatModel', model);
        localStorage.setItem('basicChatParsingModel', parsingModel);
        localStorage.setItem('basicChatTemperature', temperature);
        localStorage.setItem('basicChatBudget', budget);
        console.log('[DOMContentLoaded] Saved basic chat settings to localStorage.');
        // Generate session id
        const chatSessionId = generateSessionId();
        console.log('[DOMContentLoaded] Generated chatSessionId for basic chat:', chatSessionId);
        // Post settings to chat app endpoint
        fetch(`/chat/settings`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: new URLSearchParams({
                sessionId: chatSessionId,
                action: 'save',
                settings: JSON.stringify({
                    model: model,
                    parsingModel: parsingModel,
                    temperature: temperature,
                    budget: budget
                })
            })
        })
            .then(response => {
                console.log('[DOMContentLoaded] Basic Chat Save - fetch response:', response);
                if (response.ok) {
                    basicChatModal.style.display = "none";
                    console.log('[DOMContentLoaded] Basic Chat settings saved successfully. Redirecting to /chat/#', chatSessionId);
                    window.location.href = `/chat/#${chatSessionId}`;
                } else {
                    console.error('[DOMContentLoaded] Failed to save chat settings. Status:', response.status);
                    showNotification('Failed to save chat settings.', 'error');
                }
            })
            .catch(error => {
                console.error('[DOMContentLoaded] Error saving chat settings:', error);
                showNotification('Error saving chat settings: ' + error.message, 'error');
            });
    });
});


function setupWizard() {
    console.log('[setupWizard] Called');

    const modal = document.getElementById('user-settings-modal');
    const btn = document.getElementById('user-settings-btn');
    const closeBtn = document.getElementById('close-user-settings-modal');

    btn.onclick = function () {
        console.log('[setupWizard] user-settings-btn clicked, displaying modal.');
        modal.style.display = "block";
    }

    closeBtn.onclick = function () {
        console.log('[setupWizard] close-user-settings-modal clicked, hiding modal.');
        modal.style.display = "none";
    }

    window.onclick = function (event) {
        if (event.target == modal) {
            console.log('[setupWizard] Window click event, target is user-settings-modal. Hiding modal.');
            modal.style.display = "none";
        }
    }
    // Tab functionality
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', () => {
            const tabId = button.dataset.tab;
            console.log(`[setupWizard] Tab button clicked: ${tabId}`);
            // Remove active class from all tabs
            document.querySelectorAll('.tab-button').forEach(btn => {
                btn.classList.remove('active');
            });
            document.querySelectorAll('.tab-content').forEach(content => {
                content.classList.remove('active');
            });
            // Add active class to clicked tab
            button.classList.add('active');
            document.getElementById(`${tabId}-tab`).classList.add('active');
            console.log(`[setupWizard] Activated tab content: ${tabId}-tab`);
        });
    });
    document.getElementById('back-to-cognitive-mode').addEventListener('click', () => {
        console.log('[setupWizard] back-to-cognitive-mode clicked.');
        navigateToStep('cognitive-mode');
    });


    document.getElementById('back-to-task-settings').addEventListener('click', () => {
        console.log('[setupWizard] back-to-task-settings clicked.');
        navigateToStep('task-settings');
    });
    document.getElementById('next-to-task-selection').addEventListener('click', () => {
        console.log('[setupWizard] next-to-task-selection clicked.');
        navigateToStep('task-selection');
    });


    document.getElementById('next-to-task-settings').addEventListener('click', () => {
        console.log('[setupWizard] next-to-task-settings clicked.');
        navigateToStep('task-settings');
    });


    document.getElementById('next-to-launch').addEventListener('click', () => {
        console.log('[setupWizard] next-to-launch clicked.');
        updateLaunchSummaries();
        navigateToStep('launch');
    });

    document.getElementById('back-to-task-selection').addEventListener('click', () => {
        console.log('[setupWizard] back-to-task-selection clicked.');
        navigateToStep('task-selection');
    });
}

function initializeApiSettings() {
    console.log('[initializeApiSettings] Called');
    const container = document.getElementById('api-keys-container');

    apiProviders.forEach((provider) => {
        const keyGroup = document.createElement('div');
        keyGroup.className = 'api-key-group';
        const label = document.createElement('label');
        label.textContent = provider.name + ':';
        label.setAttribute('for', `api-key-${provider.id}`);
        const input = document.createElement('input');
        input.type = 'password';
        input.id = `api-key-${provider.id}`;
        console.log(`[initializeApiSettings] Creating input for provider: ${provider.name} (ID: ${provider.id})`);

        if (provider.id === 'GoogleSearch') {
            console.log('[initializeApiSettings] Special handling for GoogleSearch provider.');
            input.placeholder = `Enter Google API key for search`;

            const searchEngineGroup = document.createElement('div');
            searchEngineGroup.className = 'api-key-group';
            const searchEngineLabel = document.createElement('label');
            searchEngineLabel.textContent = 'Search Engine ID:';
            searchEngineLabel.setAttribute('for', `api-base-${provider.id}`);
            const searchEngineInput = document.createElement('input');
            searchEngineInput.type = 'text';
            searchEngineInput.id = `api-base-${provider.id}`;
            searchEngineInput.placeholder = 'Enter Google Search Engine ID';
            searchEngineInput.setAttribute('aria-label', 'Google Search Engine ID');
            searchEngineGroup.appendChild(searchEngineLabel);
            searchEngineGroup.appendChild(searchEngineInput);
            container.appendChild(searchEngineGroup);
        } else {
            input.placeholder = `Enter ${provider.name} API key`;
        }

        input.setAttribute('aria-label', `${provider.name} API key`);
        keyGroup.appendChild(label);
        keyGroup.appendChild(input);
        container.appendChild(keyGroup);
    });
}

function initializeTaskToggles() {
    console.log('[initializeTaskToggles] Called');
    const container = document.getElementById('task-toggles');
    container.innerHTML = '';


    const temperatureSlider = document.getElementById('temperature');
    const temperatureValue = document.getElementById('temperature-value');
    console.log('[initializeTaskToggles] Setting up temperature slider listener.');
    temperatureSlider.addEventListener('input', function () {
        temperatureValue.textContent = this.value;

        taskSettings.temperature = parseFloat(this.value);
        localStorage.setItem('temperature', this.value);
        console.log('[initializeTaskToggles] Temperature changed to:', this.value, 'Updated taskSettings.temperature and localStorage.');
    });

    taskTypes.forEach((task) => {
        const taskToggle = document.createElement('div');
        taskToggle.className = 'task-toggle';
        const toggleContent = document.createElement('div');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `task-${task.id}`;
        checkbox.value = task.id;
        checkbox.setAttribute('aria-label', `Enable ${task.name}`);
        checkbox.checked = false;
        console.log(`[initializeTaskToggles] Creating toggle for task: ${task.name} (ID: ${task.id})`);
        const label = document.createElement('label');
        label.textContent = task.name;
        label.setAttribute('for', `task-${task.id}`);
        const tooltip = document.createElement('span');
        tooltip.className = 'tooltip';
        tooltip.textContent = '?';
        tooltip.setAttribute('aria-hidden', 'true');
        const tooltipText = document.createElement('span');
        tooltipText.className = 'tooltiptext';
        tooltipText.innerHTML = task.tooltip;
        tooltip.appendChild(tooltipText);
        toggleContent.appendChild(checkbox);
        toggleContent.appendChild(label);
        toggleContent.appendChild(tooltip);

        const description = document.createElement('span');
        description.className = 'sr-only';
        description.id = `desc-${task.id}`;
        description.textContent = task.tooltip;
        description.style.position = 'absolute';
        description.style.width = '1px';
        description.style.height = '1px';
        description.style.padding = '0';
        description.style.margin = '-1px';
        description.style.overflow = 'hidden';
        description.style.clip = 'rect(0, 0, 0, 0)';
        description.style.whiteSpace = 'nowrap';
        description.style.border = '0';
        checkbox.setAttribute('aria-describedby', `desc-${task.id}`);
        toggleContent.appendChild(description);
        taskToggle.appendChild(toggleContent);
        container.appendChild(taskToggle);
    });
}

function generateCognotikWorkingDir() {
    console.log('[generateCognotikWorkingDir] Called');
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const timestamp = `${year}${month}${day}-${hours}${minutes}${seconds}`;
    const platform = navigator.platform.toLowerCase();
    let baseDir;
    console.log(`[generateCognotikWorkingDir] Detected platform: ${platform}`);
    if (platform.includes('win')) {
        baseDir = '~\\Documents\\Cognotik';
    } else if (platform.includes('mac')) {
        baseDir = '~/Documents/Cognotik';
    } else {
        baseDir = '~/Cognotik';
    }
    const dir = `${baseDir}/session-${timestamp}`;
    console.log(`[generateCognotikWorkingDir] Generated Cognotik working directory: ${dir}`);
    return dir;
}


function setupEventListeners() {
    console.log('[setupEventListeners] Called');
    // Launch Session Button
    const launchButton = document.getElementById('launch-session');
    if (launchButton) {
        launchButton.addEventListener('click', function () {
            console.log('[launch-session] Clicked.');
            // 1. Ensure all settings are captured/updated in global variables
            taskSettings.workingDir = document.getElementById('working-dir').value;
            // autoFix, models, temperature, cognitiveMode, auto-plan settings are updated by their respective event listeners.
            // graphFile is also updated by its event listener if/when that mode is active.
            saveTaskSelection(); // This updates taskSettings.taskSettings and localStorage
            console.log('[launch-session] Current cognitiveMode:', cognitiveMode);
            console.log('[launch-session] Current taskSettings:', JSON.parse(JSON.stringify(taskSettings)));
            console.log('[launch-session] Current apiSettings (relevant parts might be on server):', Object.keys(apiSettings.apiKeys || {}));
            // 2. Validate configuration
            if (!validateConfiguration()) {
                console.log('[launch-session] Validation failed.');
                return;
            }
            console.log('[launch-session] Validation passed.');
            // 3. Prepare payload
            const payload = {
                sessionId: sessionId,
                cognitiveMode: cognitiveMode,
                taskSettings: taskSettings,
                // apiSettings are primarily managed server-side.
                // If specific client-side API choices (e.g. base URLs if overridable per session) are needed:
                // clientApiPreferences: { apiBase: apiSettings.apiBase, localTools: apiSettings.localTools }
            };
            // console.log('[launch-session] Payload to send (formerly):', JSON.parse(JSON.stringify(payload)));

            // 3. Determine target path based on cognitiveMode
            let targetPath;
            switch (cognitiveMode) {
                case 'single-task':
                    targetPath = '/taskChat'; // Maps to TaskChatMode in UnifiedPlanApp
                    break;
                case 'auto-plan':
                    targetPath = '/autoPlan';
                    break;
                case 'plan-ahead':
                    targetPath = '/planAhead';
                    break;
                case 'goal-oriented':
                    targetPath = '/goalOriented';
                    break;
                // case 'graph': // Example if graph mode is fully re-enabled
                //     targetPath = '/graphApp'; // Adjust if needed, maps to a specific graph app
                //     break;
                default:
                    console.error('[launch-session] Unknown cognitive mode:', cognitiveMode);
                    showNotification(`Error: Unknown cognitive mode selected: ${cognitiveMode}. Please select a valid mode.`, 'error');
                    navigateToStep('cognitive-mode'); // Navigate back to mode selection
                    return;
            }

            // 4. Redirect to the target application URL with sessionId in hash
            // Settings are expected to be picked up by the target app from localStorage
            const targetUrl = `${targetPath}/#${sessionId}`;
            console.log('[launch-session] Redirecting to:', targetUrl);
            document.getElementById('loading').style.display = 'block';

            // Simulate a slight delay for loading text to be visible, then redirect
            // The target application will be responsible for loading its settings from localStorage
            // using the shared keys and potentially the sessionId from the hash if needed.
            setTimeout(() => {
                window.location.href = targetUrl;
                // No need to hide loading indicator, as the page will navigate away.
                // If navigation fails, the loading indicator might remain.
                // Consider adding a timeout to hide it if navigation doesn't occur.
            }, 100); // Small delay for UI update

        });
        console.log('[setupEventListeners] Attached launch-session click listener.');
    }

    console.log('[setupEventListeners] Called');
    document.getElementById('generate-working-dir').addEventListener('click', function () {
        console.log('[setupEventListeners] generate-working-dir button clicked.');
        const newDir = generateCognotikWorkingDir();
        document.getElementById('working-dir').value = newDir;
        taskSettings.workingDir = newDir;
        localStorage.setItem('workingDir', newDir);
        console.log('[setupEventListeners] New working directory generated and set:', newDir);
        showNotification('New working directory generated!');
    });

    document.getElementById('save-user-settings').addEventListener('click', saveUserSettings);
    console.log('[setupEventListeners] Attached saveUserSettings to save-user-settings click.');
    document.getElementById('reset-user-settings').addEventListener('click', resetUserSettings);
    console.log('[setupEventListeners] Attached resetUserSettings to reset-user-settings click.');
    document.getElementById('add-local-tool').addEventListener('click', addLocalTool);
    console.log('[setupEventListeners] Attached addLocalTool to add-local-tool click.');

    apiProviders.forEach(provider => {
        const keyInput = document.getElementById(`api-key-${provider.id}`);
        if (keyInput) {
            console.log(`[setupEventListeners] Adding change listener for API key input: api-key-${provider.id}`);
            keyInput.addEventListener('change', () => {
                console.log(`[setupEventListeners] API key changed for provider: ${provider.id}. Calling populateModelSelections.`);
                populateModelSelections();
            });
        }
    });

    const modelSelect = document.getElementById('model-selection');
    if (modelSelect) {
        console.log('[setupEventListeners] Adding change listener for model-selection.');
        modelSelect.addEventListener('change', function () {
            taskSettings.defaultModel = this.value;
            localStorage.setItem('defaultModel', this.value);
            console.log('[setupEventListeners] defaultModel changed to:', this.value, 'Updated taskSettings and localStorage.');
        });
    }
    const parsingModelSelect = document.getElementById('parsing-model');
    if (parsingModelSelect) {
        console.log('[setupEventListeners] Adding change listener for parsing-model.');
        parsingModelSelect.addEventListener('change', function () {
            taskSettings.parsingModel = this.value;
            localStorage.setItem('parsingModel', this.value);
            console.log('[setupEventListeners] parsingModel changed to:', this.value, 'Updated taskSettings and localStorage.');
        });
    }
    const autoFixCheckbox = document.getElementById('auto-fix');
    if (autoFixCheckbox) {
        console.log('[setupEventListeners] Adding change listener for auto-fix.');
        autoFixCheckbox.addEventListener('change', function () {
            taskSettings.autoFix = this.checked;
            localStorage.setItem('autoFix', this.checked);
            console.log('[setupEventListeners] autoFix changed to:', this.checked, 'Updated taskSettings and localStorage.');
        });
    }
    ['max-task-history', 'max-tasks-per-iteration', 'max-iterations'].forEach(id => {
        const input = document.getElementById(id);
        if (input) {
            console.log(`[setupEventListeners] Adding change listener for ${id}.`);
            input.addEventListener('input', function () { // 'input' for responsiveness
                const value = parseInt(this.value, 10);
                let keyInTaskSettings;
                let localStorageKey;
                if (id === 'max-task-history') {
                    keyInTaskSettings = 'maxTaskHistoryChars';
                    localStorageKey = 'maxTaskHistoryChars';
                } else if (id === 'max-tasks-per-iteration') {
                    keyInTaskSettings = 'maxTasksPerIteration';
                    localStorageKey = 'maxTasksPerIteration';
                } else if (id === 'max-iterations') {
                    keyInTaskSettings = 'maxIterations';
                    localStorageKey = 'maxIterations';
                }
                if (keyInTaskSettings && !isNaN(value)) {
                    taskSettings[keyInTaskSettings] = value;
                    if (localStorageKey) {
                        localStorage.setItem(localStorageKey, String(value));
                    }
                    console.log(`[setupEventListeners] ${keyInTaskSettings} changed to:`, value, 'Updated taskSettings and localStorage.');
                }
            });
        }
    });
    const graphFileInput = document.getElementById('graph-file');
    if (graphFileInput) {
        console.log('[setupEventListeners] Adding change listener for graph-file.');
        graphFileInput.addEventListener('change', function () {
            taskSettings.graphFile = this.value;
            localStorage.setItem('graphFile', this.value);
            console.log('[setupEventListeners] graphFile changed to:', this.value, 'Updated taskSettings and localStorage.');
        });
    }
    const workingDirInput = document.getElementById('working-dir');
    if (workingDirInput) {
        console.log('[setupEventListeners] Adding change listener for working-dir.');
        // Using 'change' instead of 'input' for file paths is usually better
        workingDirInput.addEventListener('change', function () {
            taskSettings.workingDir = this.value;
            localStorage.setItem('workingDir', this.value);
            console.log('[setupEventListeners] workingDir changed to:', this.value, 'Updated taskSettings and localStorage.');
        });
    }


    document.querySelectorAll('input[name="cognitive-mode"]').forEach(radio => {
        console.log(`[setupEventListeners] Adding change listener for cognitive-mode radio: ${radio.value}`);
        radio.addEventListener('change', function () {
            console.log(`[setupEventListeners] Cognitive mode changed to: ${this.value}`);
            document.getElementById('graph-settings').style.display = 'none';
            document.getElementById('auto-plan-settings').style.display = 'none';
            if (this.value === 'graph') {
                console.log('[setupEventListeners] Displaying graph-settings.');
                document.getElementById('graph-settings').style.display = 'block';
            } else if (this.value === 'auto-plan') {
                document.getElementById('auto-plan-settings').style.display = 'block';
            }
            cognitiveMode = this.value; // Update global cognitiveMode variable
            localStorage.setItem('cognitiveMode', this.value);
        });
    });
}

// --- MISSING FUNCTION: saveUserSettings ---
function saveUserSettings() {
    console.log('[saveUserSettings] Called');
    // Gather API keys
    const apiKeys = {};
    const apiBase = {};
    apiProviders.forEach(provider => {
        const keyInput = document.getElementById(`api-key-${provider.id}`);
        if (keyInput && keyInput.value && keyInput.value !== '********') {
            console.log(`[saveUserSettings] Saving API key for provider: ${provider.id}`);
            apiKeys[provider.id] = keyInput.value;
        }
        // For GoogleSearch, also save the Search Engine ID as "apiBase"
        const baseInput = document.getElementById(`api-base-${provider.id}`);
        if (baseInput && baseInput.value) {
            console.log(`[saveUserSettings] Saving API base for provider: ${provider.id}, value: ${baseInput.value}`);
            apiBase[provider.id] = baseInput.value;
        }
    });
    // Gather local tools
    const localTools = [];
    const toolsList = document.getElementById('local-tools-list');
    if (toolsList) {
        toolsList.querySelectorAll('.tool-item').forEach(item => {
            if (item.dataset.path) {
                console.log(`[saveUserSettings] Adding local tool from dataset.path: ${item.dataset.path}`);
                localTools.push(item.dataset.path);
            } else if (item.textContent) {
                // fallback if dataset not set
                console.log(`[saveUserSettings] Adding local tool from textContent: ${item.textContent.trim()}`);
                localTools.push(item.textContent.trim());
            }
        });
    }
    // Compose settings object
    const settings = {
        apiKeys,
        apiBase,
        localTools
    };
    console.log('[saveUserSettings] Settings to save:', JSON.stringify(settings)); // Avoid logging actual keys if sensitive
    // Save to server
    fetch('/userSettings/', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            action: 'save',
            settings: JSON.stringify(settings)
        })
    })
        .then(response => {
            console.log('[saveUserSettings] Server response:', response);
            if (response.ok) {
                console.log('[saveUserSettings] User settings saved successfully.');
                showNotification('User settings saved!', 'success');
                // Update apiSettings in memory and refresh model selections
                console.log('[saveUserSettings] Updated apiSettings in memory.');
                apiSettings = settings;
                populateModelSelections();
            } else {
                console.error('[saveUserSettings] Failed to save user settings. Status:', response.status);
                showNotification('Failed to save user settings.', 'error');
            }
        })
        .catch(error => {
            console.error('[saveUserSettings] Error saving user settings:', error);
            showNotification('Error saving user settings: ' + error.message, 'error');
        });
}

// --- MISSING FUNCTION: resetUserSettings ---
function resetUserSettings() {
    console.log('[resetUserSettings] Called');
    // Clear all API key fields
    apiProviders.forEach(provider => {
        const keyInput = document.getElementById(`api-key-${provider.id}`);
        if (keyInput) {
            console.log(`[resetUserSettings] Clearing API key for provider: ${provider.id}`);
            keyInput.value = '';
        }
        const baseInput = document.getElementById(`api-base-${provider.id}`);
        if (baseInput) {
            console.log(`[resetUserSettings] Clearing API base for provider: ${provider.id}`);
            baseInput.value = '';
        }
    });
    // Clear local tools
    const toolsList = document.getElementById('local-tools-list');
    if (toolsList) toolsList.innerHTML = '';
    console.log('[resetUserSettings] Cleared local tools list.');
    showNotification('User settings reset. Remember to save!', 'info');
}

// --- MISSING FUNCTION: addLocalTool ---
function addLocalTool() {
    console.log('[addLocalTool] Called');
    const newToolInput = document.getElementById('new-tool-path');
    const toolsList = document.getElementById('local-tools-list');
    if (newToolInput && toolsList && newToolInput.value.trim() !== '') {
        const toolPath = newToolInput.value.trim();
        const toolItem = document.createElement('div');
        toolItem.className = 'tool-item';
        toolItem.dataset.path = toolPath;
        const toolText = document.createElement('span');
        toolText.textContent = toolPath;
        const removeBtn = document.createElement('button');
        removeBtn.className = 'remove-tool';
        removeBtn.textContent = '×';
        removeBtn.addEventListener('click', function () {
            console.log(`[addLocalTool] Remove button clicked for tool: ${toolPath}`);
            toolItem.remove();
        });
        toolItem.appendChild(toolText);
        toolItem.appendChild(removeBtn);
        toolsList.appendChild(toolItem);
        newToolInput.value = '';
        console.log(`[addLocalTool] Added new local tool: ${toolPath}`);
    } else {
        console.warn('[addLocalTool] Could not add tool. Input empty, or newToolInput/toolsList not found.');
    }
}

// --- MISSING FUNCTION: showNotification ---
function showNotification(message, type = 'info') {
    console.log(`[showNotification] Called with message: "${message}", type: "${type}"`);
    // Simple notification using alert, can be replaced with a fancier UI
    if (type === 'error') {
        console.error(`[showNotification] Error: ${message}`);
        alert('❌ ' + message);
    } else if (type === 'success') {
        console.log(`[showNotification] Success: ${message}`);
        alert('✅ ' + message);
    } else {
        console.info(`[showNotification] Info: ${message}`);
        alert(message);
    }
}

// --- MISSING FUNCTION: navigateToStep ---
function navigateToStep(stepId) {
    console.log(`[navigateToStep] Called with stepId: ${stepId}`);
    // Hide all wizard-content
    document.querySelectorAll('.wizard-content').forEach(el => el.classList.remove('active'));
    // Show the selected step
    const stepContent = document.getElementById(stepId);
    if (stepContent) {
        stepContent.classList.add('active');
        console.log(`[navigateToStep] Activated content for step: ${stepId}`);
    } else {
        console.warn(`[navigateToStep] Content element for step ${stepId} not found.`);
    }
    // Update wizard-nav
    document.querySelectorAll('.wizard-step').forEach(step => step.classList.remove('active'));
    const navStep = document.querySelector(`.wizard-step[data-step="${stepId}"]`);
    if (navStep) navStep.classList.add('active');
    console.log(`[navigateToStep] Updated wizard navigation for step: ${stepId}`);
}

function updateLaunchSummaries() {
    // Update the summary sections in the launch step
    // Cognitive Mode
    const mode = localStorage.getItem('cognitiveMode') || 'single-task';
    const modeMap = {
        'single-task': 'Chat',
        'auto-plan': 'Autonomous',
        'plan-ahead': 'Plan Ahead',
        'goal-oriented': 'Goal Oriented',
        'graph': 'Graph Mode'
    };
    document.getElementById('cognitive-mode-summary').textContent = modeMap[mode] || mode;
    console.log(`[updateLaunchSummaries] Cognitive Mode Summary: ${modeMap[mode] || mode}`);
    // Task Settings
    let summary = '';
    summary += 'Default Model: ' + (taskSettings.defaultModel || '-') + '\n';
    summary += 'Parsing Model: ' + (taskSettings.parsingModel || '-') + '\n'; // Ensure parsingModel is in taskSettings
    summary += 'Working Directory: ' + (taskSettings.workingDir || '-') + '\n';
    summary += 'Temperature: ' + (taskSettings.temperature ?? '-') + '\n'; // Use ?? to handle 0 correctly
    summary += 'Auto Fix: ' + (taskSettings.autoFix ? 'Enabled' : 'Disabled') + '\n';
    document.getElementById('task-settings-summary').textContent = summary;
    console.log(`[updateLaunchSummaries] Task Settings Summary:\n${summary}`);
    // API Settings
    let apiSummary = '';
    if (apiSettings.apiKeys) {
        for (const [provider, key] of Object.entries(apiSettings.apiKeys)) {
            if (key) {
                console.log(`[updateLaunchSummaries] API Key configured for: ${provider}`);
                apiSummary += provider + ': Configured\n';
            }
        }
    }
    document.getElementById('api-settings-summary').textContent = apiSummary || 'No API keys configured.';
    console.log(`[updateLaunchSummaries] API Settings Summary:\n${apiSummary || 'No API keys configured.'}`);
}

function validateConfiguration() {
    console.log('[validateConfiguration] Called');

    let hasApiKey = false;
    if (apiSettings.apiKeys) {
        console.log('[validateConfiguration] Checking API keys:', Object.keys(apiSettings.apiKeys));
        for (const key of Object.values(apiSettings.apiKeys)) {
            if (key) {
                hasApiKey = true;
                break;
            }
        }
    }
    if (!hasApiKey) {
        console.warn('[validateConfiguration] No API key configured.');
        showNotification('Please configure at least one API key before launching', 'error');

        document.getElementById('api-settings-btn').click();
        console.log('[validateConfiguration] Clicked api-settings-btn due to missing API key.');
        return false;
    }
    console.log('[validateConfiguration] API key check passed.');

    let hasEnabledTask = false;
    if (taskSettings.taskSettings) {
        console.log('[validateConfiguration] Checking enabled tasks:', taskSettings.taskSettings);
        for (const settings of Object.values(taskSettings.taskSettings)) {
            if (settings.enabled) {
                hasEnabledTask = true;
                break;
            }
        }
    }
    if (!hasEnabledTask) {
        console.warn('[validateConfiguration] No task enabled.');
        showNotification('Please enable at least one task before launching', 'error');

        navigateToStep('task-selection');
        console.log('[validateConfiguration] Navigated to task-selection due to no enabled tasks.');
        return false;
    }
    console.log('[validateConfiguration] Enabled task check passed. Configuration is valid.');
    return true;

}

function loadSavedSettings() {
    console.log('[loadSavedSettings] Called');
    const savedCognitiveMode = localStorage.getItem('cognitiveMode');
    if (savedCognitiveMode) {
        cognitiveMode = savedCognitiveMode;
        const radioToSelect = document.querySelector(`input[name="cognitive-mode"][value="${savedCognitiveMode}"]`);
        if (radioToSelect) {
            radioToSelect.checked = true;
            radioToSelect.dispatchEvent(new Event('change'));
            console.log('[loadSavedSettings] Restored cognitiveMode:', savedCognitiveMode);
        }
    }
    const modelSelect = document.getElementById('model-selection');
    if (modelSelect && localStorage.getItem('defaultModel')) modelSelect.value = localStorage.getItem('defaultModel');
    // taskSettings.defaultModel is already initialized from localStorage or default
    const parsingModelSelect = document.getElementById('parsing-model');
    if (parsingModelSelect && localStorage.getItem('parsingModel')) parsingModelSelect.value = localStorage.getItem('parsingModel');
    // taskSettings.parsingModel is already initialized
    const workingDirInput = document.getElementById('working-dir');
    if (workingDirInput && localStorage.getItem('workingDir')) workingDirInput.value = localStorage.getItem('workingDir');
    // taskSettings.workingDir is already initialized
    const autoFixCheckbox = document.getElementById('auto-fix');
    if (autoFixCheckbox) autoFixCheckbox.checked = localStorage.getItem('autoFix') === 'true';
    // taskSettings.autoFix is already initialized
    const temperatureSlider = document.getElementById('temperature');
    const temperatureValue = document.getElementById('temperature-value');
    const savedTemperature = localStorage.getItem('temperature');
    if (savedTemperature) {
        taskSettings.temperature = parseFloat(savedTemperature);
        if (temperatureSlider) temperatureSlider.value = savedTemperature;
        if (temperatureValue) temperatureValue.textContent = savedTemperature;
        console.log('[loadSavedSettings] Restored temperature:', savedTemperature);
    } else if (temperatureSlider) {
        taskSettings.temperature = parseFloat(temperatureSlider.value); // Ensure global taskSettings has default
        if (temperatureValue) temperatureValue.textContent = temperatureSlider.value;
    }
    // Auto-plan settings
    const autoPlanFields = {
        'maxTaskHistoryChars': 'max-task-history',
        'maxTasksPerIteration': 'max-tasks-per-iteration',
        'maxIterations': 'max-iterations'
    };
    for (const [key, id] of Object.entries(autoPlanFields)) {
        const input = document.getElementById(id);
        const savedValue = localStorage.getItem(key);
        if (savedValue) {
            taskSettings[key] = parseInt(savedValue, 10);
            if (input) input.value = savedValue;
        } else if (input) { // Ensure global taskSettings has default from input
            taskSettings[key] = parseInt(input.value, 10);
        }
    }
    const graphFileInput = document.getElementById('graph-file');
    const savedGraphFile = localStorage.getItem('graphFile');
    if (savedGraphFile) {
        taskSettings.graphFile = savedGraphFile;
        if (graphFileInput) graphFileInput.value = savedGraphFile;
    } else if (graphFileInput && graphFileInput.value) { // Ensure global taskSettings has default from input
        taskSettings.graphFile = graphFileInput.value;
    }
    const savedEnabledTasks = localStorage.getItem('enabledTasks');
    if (savedEnabledTasks) {
        try {
            taskSettings.taskSettings = JSON.parse(savedEnabledTasks);
            console.log('[loadSavedSettings] Restored enabledTasks:', taskSettings.taskSettings);
            Object.keys(taskSettings.taskSettings).forEach(taskId => {
                const checkbox = document.getElementById(`task-${taskId}`);
                if (checkbox && taskSettings.taskSettings[taskId]?.enabled) checkbox.checked = true;
            });
        } catch (e) {
            console.error('[loadSavedSettings] Error parsing enabledTasks:', e);
            taskSettings.taskSettings = {};
        }
    }
    console.log('[loadSavedSettings] Finished. Initial taskSettings after load:', JSON.parse(JSON.stringify(taskSettings)));
}

function saveTaskSelection() {
    console.log('[saveTaskSelection] Called');
    const currentEnabledTasks = {};
    document.querySelectorAll('#task-toggles .task-toggle input[type="checkbox"]').forEach(checkbox => {
        currentEnabledTasks[checkbox.value] = {enabled: checkbox.checked};
    });
    taskSettings.taskSettings = currentEnabledTasks;
    localStorage.setItem('enabledTasks', JSON.stringify(currentEnabledTasks));
    console.log('[saveTaskSelection] Saved enabledTasks to localStorage and taskSettings.taskSettings:', JSON.stringify(currentEnabledTasks));
}