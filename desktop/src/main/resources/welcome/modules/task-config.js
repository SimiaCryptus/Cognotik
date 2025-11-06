// Task configuration module
class TaskConfigManager {
    constructor(dependencies = {}) {
        this.appState = dependencies.appState;
        this.document = dependencies.document;
        this.httpService = dependencies.httpService;
        this.notificationService = dependencies.notificationService;
        this.modelManager = dependencies.modelManager;
        this.availableModels = dependencies.getAvailableModels ? dependencies.getAvailableModels() : {};
    }

    // Task type definitions with their specific configuration options
    getTaskTypes() {
        return [
            {
                id: 'Analysis',
                name: 'Analysis Task',
                description: 'Analyze code and provide detailed explanations',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'FileModification',
                name: 'File Modification Task',
                description: 'Create or modify files with AI assistance',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'FileSearch',
                name: 'File Search Task',
                description: 'Search and analyze files in the project',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'WriteHtml',
                name: 'Write HTML Task',
                description: 'Generate HTML content',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'GeneratePresentation',
                name: 'Generate Presentation Task',
                description: 'Create presentation slides',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'VectorSearch',
                name: 'Vector Search Task',
                description: 'Perform vector-based searches for similar items',
                category: 'Knowledge Management',
                configFields: []
            },
            {
                id: 'KnowledgeIndexing',
                name: 'Knowledge Indexing Task',
                description: 'Index and search knowledge bases',
                category: 'Knowledge Management',
                configFields: []
            },
            {
                id: 'RunShellCommand',
                name: 'Run Shell Command Task',
                description: 'Execute shell commands and process output',
                category: 'Execution & Automation',
                configFields: []
            },
            {
                id: 'RunCode',
                name: 'Run Code Task',
                description: 'Execute code snippets and return results',
                category: 'Execution & Automation',
                configFields: [
                    {
                        id: 'codeRuntime',
                        label: 'Code Runtime',
                        type: 'select',
                        options: ['KotlinRuntime', 'GroovyRuntime', 'BashRuntime', 'PowerShellRuntime', 'PythonRuntime', 'NodeJsRuntime'],
                        default: 'KotlinRuntime',
                        tooltip: 'Select the runtime environment for code execution'
                    }
                ]
            },
            {
                id: 'CommandSession',
                name: 'Command Session Task',
                description: 'Execute a series of commands in a session',
                category: 'Execution & Automation',
                configFields: []
            },
            {
                id: 'SeleniumSession',
                name: 'Selenium Session Task',
                description: 'Automate web browser interactions',
                category: 'Execution & Automation',
                configFields: []
            },
            {
                id: 'SelfHealing',
                name: 'Self-Healing Task',
                description: 'Automatically fix issues based on AI suggestions',
                category: 'Execution & Automation',
                configFields: [
                    {
                        id: 'commandAutoFixCommands',
                        label: 'Auto-fix Commands',
                        type: 'textarea',
                        placeholder: 'Enter one command per line',
                        tooltip: 'List of commands that can be used for auto-fixing'
                    }
                ]
            },
            {
                id: 'GitHubSearch',
                name: 'GitHub Search Task',
                description: 'Search GitHub repositories and issues',
                category: 'Online & Search',
                configFields: []
            },
            {
                id: 'CrawlerAgent',
                name: 'Web Crawler Task',
                description: 'Crawl and extract information from websites',
                category: 'Online & Search',
                configFields: [
                    {
                        id: 'seed_method',
                        label: 'Seed Method',
                        type: 'select',
                        options: ['GoogleProxy', 'GoogleSearch', 'DirectUrls', 'SearchIO_Google_Search', 'SearchIO_Google_Scholar', 'SearchIO_Google_Patents', 'SearchIO_Google_Maps'],
                        default: 'GoogleProxy',
                        tooltip: 'Method to seed the crawler'
                    },
                    {
                        id: 'fetch_method',
                        label: 'Fetch Method',
                        type: 'select',
                        options: ['HttpClient', 'Selenium'],
                        default: 'HttpClient',
                        tooltip: 'Method used to fetch content from URLs'
                    },
                    {
                        id: 'processing_strategy',
                        label: 'Processing Method',
                        type: 'select',
                        options: ['DefaultSummarizer', 'FactChecking', 'JobMatching', 'SchemaExtraction', 'DataTableAccumulation'],
                        default: 'DefaultSummarizer',
                        tooltip: 'Strategy for processing fetched content'
                    },
                    {
                        id: 'max_pages_per_task',
                        label: 'Max Pages Per Task',
                        type: 'number',
                        min: 1,
                        max: 100,
                        default: 30,
                        tooltip: 'Maximum number of pages to process (1-100)'
                    },
                    {
                        id: 'concurrent_page_processing',
                        label: 'Concurrent Processing',
                        type: 'number',
                        min: 1,
                        max: 10,
                        default: 3,
                        tooltip: 'Number of pages to process concurrently (1-10)'
                    },
                    {
                        id: 'max_final_output_size',
                        label: 'Max Final Output Size',
                        type: 'number',
                        min: 1000,
                        max: 100000,
                        default: 10000,
                        tooltip: 'Maximum characters in final summary (1000-100000)'
                    },
                    {
                        id: 'min_content_length',
                        label: 'Min Content Length',
                        type: 'number',
                        min: 10,
                        max: 10000,
                        default: 100,
                        tooltip: 'Minimum content length to process (10-10000)'
                    },
                    {
                        id: 'follow_links',
                        label: 'Follow Links',
                        type: 'checkbox',
                        default: true,
                        tooltip: 'Automatically follow links found in analyzed pages'
                    },
                    {
                        id: 'allow_revisit_pages',
                        label: 'Allow Revisit Pages',
                        type: 'checkbox',
                        default: false,
                        tooltip: 'Allow crawling the same page multiple times'
                    },
                    {
                        id: 'create_final_summary',
                        label: 'Create Final Summary',
                        type: 'checkbox',
                        default: true,
                        tooltip: 'Generate a comprehensive summary of all results'
                    },
                    {
                        id: 'respect_robots_txt',
                        label: 'Respect robots.txt',
                        type: 'checkbox',
                        default: true,
                        tooltip: 'Obey the rules set in robots.txt files'
                    },
                    {
                        id: 'allowed_domains',
                        label: 'Allowed Domains',
                        type: 'text',
                        placeholder: 'Enter one domain per line (e.g., example.com)',
                        tooltip: 'List of domains that the crawler is allowed to visit'
                    }
                ]
            },
            {
                id: 'MCPTool',
                name: 'MCP Tool Task',
                description: 'Execute Model Context Protocol tools',
                category: 'Integration',
                configFields: [
                    {
                        id: 'default_server',
                        label: 'Default Server',
                        type: 'text',
                        placeholder: 'Server name',
                        tooltip: 'Default MCP server name to use if not specified'
                    },
                    {
                        id: 'default_timeout',
                        label: 'Default Timeout (seconds)',
                        type: 'number',
                        min: 1,
                        max: 300,
                        default: 30,
                        tooltip: 'Default timeout in seconds for tool execution (1-300)'
                    },
                    {
                        id: 'auto_retry',
                        label: 'Auto Retry on Failure',
                        type: 'checkbox',
                        default: false,
                        tooltip: 'Automatically retry failed tool executions'
                    },
                    {
                        id: 'max_retries',
                        label: 'Max Retries',
                        type: 'number',
                        min: 1,
                        max: 10,
                        default: 3,
                        tooltip: 'Maximum number of retry attempts (1-10)'
                    }
                ]
            },
            {
                id: 'ChainOfThought',
                name: 'Chain of Thought',
                description: 'Break down complex problems into steps',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'MetaCognitiveReflection',
                name: 'Meta-Cognitive Reflection',
                description: 'Reflect on thinking process and strategies',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'MultiPerspectiveAnalysis',
                name: 'Multi-Perspective Analysis',
                description: 'Analyze from multiple viewpoints',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'SocraticDialogue',
                name: 'Socratic Dialogue',
                description: 'Use questioning to explore concepts',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'AnalogicalReasoning',
                name: 'Analogical Reasoning',
                description: 'Draw parallels between different domains',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'CounterfactualAnalysis',
                name: 'Counterfactual Analysis',
                description: 'Explore alternative scenarios',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'AbstractionLadder',
                name: 'Abstraction Ladder',
                description: 'Move between concrete and abstract levels',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'ConstraintSatisfaction',
                name: 'Constraint Satisfaction',
                description: 'Find solutions within constraints',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'CausalInference',
                name: 'Causal Inference',
                description: 'Identify cause-and-effect relationships',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'DecompositionSynthesis',
                name: 'Decomposition & Synthesis',
                description: 'Break down and recombine elements',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'Brainstorming',
                name: 'Brainstorming',
                description: 'Generate creative ideas and solutions',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'FiniteStateMachine',
                name: 'Finite State Machine',
                description: 'Model and analyze state-based systems',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'GameTheory',
                name: 'Game Theory',
                description: 'Analyze strategic interactions and decisions',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'AbductiveReasoning',
                name: 'Abductive Reasoning',
                description: 'Infer the best explanation for observations',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'AdversarialReasoning',
                name: 'Adversarial Reasoning',
                description: 'Anticipate and counter opposing strategies',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'ConstraintRelaxation',
                name: 'Constraint Relaxation',
                description: 'Systematically relax constraints to find solutions',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'DialecticalReasoning',
                name: 'Dialectical Reasoning',
                description: 'Resolve contradictions through thesis-antithesis-synthesis',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'LateralThinking',
                name: 'Lateral Thinking',
                description: 'Approach problems from unconventional angles',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'ProbabilisticReasoning',
                name: 'Probabilistic Reasoning',
                description: 'Reason under uncertainty with probabilities',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'SystemsThinking',
                name: 'Systems Thinking',
                description: 'Analyze complex systems and their interactions',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'TemporalReasoning',
                name: 'Temporal Reasoning',
                description: 'Reason about time-dependent relationships',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'NarrativeReasoning',
                name: 'Narrative Reasoning',
                description: 'Understand and analyze narrative structures',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'NarrativeGeneration',
                name: 'Narrative Generation',
                description: 'Generate coherent narrative content',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'GeneticOptimization',
                name: 'Genetic Optimization',
                description: 'Optimize solutions using evolutionary algorithms',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'EthicalReasoning',
                name: 'Ethical Reasoning',
                description: 'Evaluate moral implications and make ethical decisions',
                category: 'Reasoning',
                configFields: []
            },
            {
                id: 'ArticleGeneration',
                name: 'Article Generation',
                description: 'Generate well-structured articles and blog posts',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'PersuasiveEssay',
                name: 'Persuasive Essay',
                description: 'Write compelling persuasive essays and arguments',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'BusinessProposal',
                name: 'Business Proposal',
                description: 'Create professional business proposals',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'EmailCampaign',
                name: 'Email Campaign',
                description: 'Generate email marketing campaigns',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'InteractiveStory',
                name: 'Interactive Story',
                description: 'Create interactive narrative experiences',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'JournalismReasoning',
                name: 'Journalism Reasoning',
                description: 'Apply journalistic analysis and reporting techniques',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'TechnicalExplanation',
                name: 'Technical Explanation',
                description: 'Generate clear technical documentation and explanations',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'TutorialGeneration',
                name: 'Tutorial Generation',
                description: 'Create step-by-step tutorials and guides',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'ReportGeneration',
                name: 'Report Generation',
                description: 'Generate comprehensive reports and summaries',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'Scriptwriting',
                name: 'Scriptwriting',
                description: 'Write scripts for videos, plays, and performances',
                category: 'Writing',
                configFields: []
            },
            {
                id: 'GenerateImage',
                name: 'Generate Image',
                description: 'Generate images using AI image generation',
                category: 'File Operations',
                configFields: []
            },
            {
                id: 'CommandSession',
                name: 'Command Session Task',
                description: 'Execute a series of commands in a session',
                category: 'Execution & Automation',
                configFields: []
            },
            {
                id: 'SubPlanning',
                name: 'Sub-Planning',
                description: 'Create and execute sub-plans within a larger plan',
                category: 'Planning & Orchestration',
                configFields: [
                    {
                        id: 'purpose',
                        label: 'Purpose',
                        type: 'textarea',
                        placeholder: 'Describe the specific purpose or use case for this sub-planning configuration',
                        tooltip: 'Supplemental description of the purpose of this configuration',
                        rows: 3
                    },
                    {
                        id: 'cognitiveMode',
                        label: 'Cognitive Mode',
                        type: 'select',
                        options: ['Waterfall', 'Adaptive', 'Parallel', 'Iterative', 'Exploratory'],
                        default: 'Waterfall',
                        tooltip: 'Cognitive strategy to use for sub-planning'
                    },
                    {
                        id: 'taskSettings',
                        label: 'Sub-Task Configurations',
                        type: 'subtasks',
                        tooltip: 'Configure which task types are available within sub-plans'
                    }
                ]
            }
        ];
    }

    // Get task type by ID
    getTaskType(taskId) {
        return this.getTaskTypes().find(t => t.id === taskId);
    }

    // Get task categories
    getTaskCategories() {
        const categories = new Set();
        this.getTaskTypes().forEach(task => {
            categories.add(task.category);
        });
        return Array.from(categories).sort();
    }

    // Get tasks by category
    getTasksByCategory(category) {
        return this.getTaskTypes().filter(t => t.category === category);
    }

    // Create task configuration dialog
    showTaskConfigDialog(taskId, existingConfig = null) {
        const taskType = this.getTaskType(taskId);
        if (!taskType) {
            console.error('[TaskConfigManager] Task type not found:', taskId);
            return;
        }

        const modal = this.createTaskConfigModal(taskType, existingConfig);
        this.document.body.appendChild(modal);
        modal.style.display = 'block';

        return new Promise((resolve, reject) => {
            const saveBtn = modal.querySelector('.save-task-config');
            const cancelBtn = modal.querySelector('.cancel-task-config');
            const closeBtn = modal.querySelector('.close-task-config-modal');

            const cleanup = () => {
                modal.remove();
            };

            saveBtn.addEventListener('click', () => {
                const config = this.collectTaskConfig(modal, taskType);
                if (this.validateTaskConfig(config, taskType)) {
                    cleanup();
                    resolve(config);
                }
            });

            cancelBtn.addEventListener('click', () => {
                cleanup();
                reject(new Error('Cancelled'));
            });

            closeBtn.addEventListener('click', () => {
                cleanup();
                reject(new Error('Cancelled'));
            });

            // Close on outside click
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    cleanup();
                    reject(new Error('Cancelled'));
                }
            });
        });
    }

    // Create modal HTML
    createTaskConfigModal(taskType, existingConfig) {
        const modal = this.document.createElement('div');
        modal.className = 'modal';
        modal.id = 'task-config-modal';
        modal.dataset.taskType = taskType.id;

        // Default config name to task type name if not provided
        const configName = existingConfig?.name || taskType.id;
        const configModel = existingConfig?.model || '';

        let fieldsHtml = '';
        if (taskType.configFields && taskType.configFields.length > 0) {
            fieldsHtml = '<div class="form-section"><h4>Task-Specific Settings</h4>';
            taskType.configFields.forEach(field => {
                fieldsHtml += this.createFieldHtml(field, existingConfig);
            });
            fieldsHtml += '</div>';
        }

        modal.innerHTML = `
            <div class="modal-content" style="max-width: 600px;">
                <div class="modal-header">
                    <h3>Configure ${taskType.name}</h3>
                    <span class="close-task-config-modal">&times;</span>
                </div>
                <div class="modal-body">
                    <p class="task-description">${taskType.description}</p>
                    
                    <div class="form-section">
                        <div class="form-group">
                            <label for="task-config-name">Configuration Name:</label>
                            <input type="text" id="task-config-name" class="form-control" 
                                   value="${configName}" 
                                   placeholder="${taskType.id}"
                                   pattern="[a-zA-Z0-9_-]+"
                                   title="Only letters, numbers, underscores and hyphens allowed">
                            <small>Enter a unique name for this configuration (defaults to task type name)</small>
                        </div>
                        
                        <div class="form-group">
                            <label for="task-config-model">AI Model:</label>
                            <select id="task-config-model" class="form-control">
                                <option value="">Use Default Model</option>
                            </select>
                            <small>Select the AI model to use for this task type</small>
                        </div>
                    </div>
                    
                    ${fieldsHtml}
                </div>
                <div class="modal-footer">
                    <button class="button secondary cancel-task-config">Cancel</button>
                    <button class="button primary save-task-config">Save Configuration</button>
                </div>
            </div>
        `;

        // Populate model dropdown
        this.populateTaskModelDropdown(modal, configModel);
        
        // Set up sub-tasks event handlers if this is a SubPlanning task
        if (taskType.id === 'SubPlanning') {
            this.setupSubTasksHandlers(modal, existingConfig);
        }

        return modal;
    }

    // Create HTML for a configuration field
    createFieldHtml(field, existingConfig) {
        const value = existingConfig?.[field.id] ?? field.default ?? '';
        let inputHtml = '';

        switch (field.type) {
            case 'text':
                inputHtml = `<input type="text" id="task-field-${field.id}" class="form-control" 
                                   value="${value}" placeholder="${field.placeholder || ''}">`;
                break;
            case 'number':
                inputHtml = `<input type="number" id="task-field-${field.id}" class="form-control" 
                                   value="${value}" min="${field.min || ''}" max="${field.max || ''}">`;
                break;
            case 'select':
                inputHtml = `<select id="task-field-${field.id}" class="form-control">`;
                field.options.forEach(opt => {
                    const selected = opt === value ? 'selected' : '';
                    inputHtml += `<option value="${opt}" ${selected}>${opt}</option>`;
                });
                inputHtml += `</select>`;
                break;
            case 'checkbox':
                const checked = value === true || value === 'true' ? 'checked' : '';
                inputHtml = `<input type="checkbox" id="task-field-${field.id}" ${checked}>`;
                break;
            case 'textarea':
                const textValue = Array.isArray(value) ? value.join('\n') : (value || '');
                const rows = field.rows || 5;
                inputHtml = `<textarea id="task-field-${field.id}" class="form-control" rows="${rows}" 
                                      placeholder="${field.placeholder || ''}">${textValue}</textarea>`;
                break;
            case 'subtasks':
                inputHtml = this.createSubTasksField(field, value);
                break;
        }

        return `
            <div class="form-group">
                <label for="task-field-${field.id}">
                    ${field.label}
                    ${field.tooltip ? `<span class="tooltip">?<span class="tooltiptext">${field.tooltip}</span></span>` : ''}
                </label>
                ${inputHtml}
            </div>
        `;
    }

    // Create sub-tasks configuration field
    createSubTasksField(field, existingSubTasks) {
        const subTasks = existingSubTasks || {};
        let html = `
            <div class="subtasks-container" id="task-field-${field.id}">
                <div class="subtasks-info">
                    <p>Configure which task types are available within sub-plans. 
                    Each task type can have its own configuration that will be used 
                    when executing within a sub-plan context.</p>
                </div>
                <div class="subtasks-list" id="subtasks-list">
        `;

        // Add existing sub-tasks
        for (const [key, config] of Object.entries(subTasks)) {
            const taskTypeName = key.includes('_') ? key.split('_')[0] : key;
            const taskType = this.getTaskType(taskTypeName);
            if (taskType) {
                html += this.createSubTaskItem(key, taskType, config);
            }
        }

        html += `
                </div>
                <div class="subtasks-actions">
                    <button type="button" class="button secondary add-subtask">Add Sub-Task</button>
                </div>
            </div>
        `;

        return html;
    }

    // Create a sub-task list item
    createSubTaskItem(key, taskType, config) {
        const displayName = config.name || 'Default';
        return `
            <div class="subtask-item" data-key="${key}">
                <div class="subtask-info">
                    <strong>${taskType.name}</strong> - ${displayName}
                    <br>
                    <small>${taskType.description}</small>
                </div>
                <div class="subtask-actions">
                    <button type="button" class="button small edit-subtask" data-key="${key}">Edit</button>
                    <button type="button" class="button small secondary delete-subtask" data-key="${key}">Delete</button>
                </div>
            </div>
        `;
    }
    // Set up event handlers for sub-tasks management
    setupSubTasksHandlers(modal, existingConfig) {
        // Initialize sub-tasks data on modal
        modal.subTasksData = existingConfig?.taskSettings || {};
        const addBtn = modal.querySelector('.add-subtask');
        const subtasksList = modal.querySelector('#subtasks-list');
        if (addBtn) {
            addBtn.addEventListener('click', () => {
                this.showTaskTypeSelectionDialog().then(taskType => {
                    if (taskType) {
                        this.showTaskConfigDialog(taskType.id).then(config => {
                            const key = config.name ? `${taskType.id}_${config.name}` : taskType.id;
                            modal.subTasksData[key] = config;
                            // Add to list
                            const itemHtml = this.createSubTaskItem(key, taskType, config);
                            subtasksList.insertAdjacentHTML('beforeend', itemHtml);
                            // Attach event handlers to new item
                            this.attachSubTaskItemHandlers(modal, subtasksList.lastElementChild);
                        }).catch(() => {
                            // User cancelled config dialog
                        });
                    }
                }).catch(() => {
                    // User cancelled task type selection
                });
            });
        }
        // Attach handlers to existing items
        subtasksList.querySelectorAll('.subtask-item').forEach(item => {
            this.attachSubTaskItemHandlers(modal, item);
        });
    }
    // Attach event handlers to a sub-task item
    attachSubTaskItemHandlers(modal, item) {
        const key = item.dataset.key;
        const editBtn = item.querySelector('.edit-subtask');
        const deleteBtn = item.querySelector('.delete-subtask');
        if (editBtn) {
            editBtn.addEventListener('click', () => {
                const config = modal.subTasksData[key];
                const taskTypeName = key.includes('_') ? key.split('_')[0] : key;
                const taskType = this.getTaskType(taskTypeName);
                if (taskType && config) {
                    this.showTaskConfigDialog(taskType.id, config).then(updatedConfig => {
                        const newKey = updatedConfig.name ? `${taskType.id}_${updatedConfig.name}` : taskType.id;
                        // Remove old key if changed
                        if (key !== newKey) {
                            delete modal.subTasksData[key];
                        }
                        modal.subTasksData[newKey] = updatedConfig;
                        // Update item
                        const newItemHtml = this.createSubTaskItem(newKey, taskType, updatedConfig);
                        const tempDiv = this.document.createElement('div');
                        tempDiv.innerHTML = newItemHtml;
                        const newItem = tempDiv.firstElementChild;
                        item.replaceWith(newItem);
                        this.attachSubTaskItemHandlers(modal, newItem);
                    }).catch(() => {
                        // User cancelled
                    });
                }
            });
        }
        if (deleteBtn) {
            deleteBtn.addEventListener('click', () => {
                const config = modal.subTasksData[key];
                const displayName = config?.name || 'Default';
                const taskTypeName = key.includes('_') ? key.split('_')[0] : key;
                if (confirm(`Delete sub-task configuration '${displayName}' for ${taskTypeName}?`)) {
                    delete modal.subTasksData[key];
                    item.remove();
                }
            });
        }
    }
    // Show task type selection dialog
    showTaskTypeSelectionDialog() {
        return new Promise((resolve, reject) => {
            const modal = this.document.createElement('div');
            modal.className = 'modal';
            modal.id = 'task-type-selection-modal';
            const categories = this.getTaskCategories();
            let categoriesHtml = '';
            categories.forEach(category => {
                const tasks = this.getTasksByCategory(category);
                categoriesHtml += `
                    <div class="task-category">
                        <h4>${category}</h4>
                        <div class="task-list">
                `;
                tasks.forEach(task => {
                    categoriesHtml += `
                        <div class="task-type-option" data-task-id="${task.id}">
                            <strong>${task.name}</strong>
                            <p>${task.description}</p>
                        </div>
                    `;
                });
                categoriesHtml += `
                        </div>
                    </div>
                `;
            });
            modal.innerHTML = `
                <div class="modal-content" style="max-width: 700px;">
                    <div class="modal-header">
                        <h3>Select Task Type</h3>
                        <span class="close-task-type-modal">&times;</span>
                    </div>
                    <div class="modal-body" style="max-height: 500px; overflow-y: auto;">
                        ${categoriesHtml}
                    </div>
                    <div class="modal-footer">
                        <button class="button secondary cancel-task-type">Cancel</button>
                    </div>
                </div>
            `;
            this.document.body.appendChild(modal);
            modal.style.display = 'block';
            const cleanup = () => {
                modal.remove();
            };
            // Handle task selection
            modal.querySelectorAll('.task-type-option').forEach(option => {
                option.addEventListener('click', () => {
                    const taskId = option.dataset.taskId;
                    const taskType = this.getTaskType(taskId);
                    cleanup();
                    resolve(taskType);
                });
            });
            // Handle cancel
            const cancelBtn = modal.querySelector('.cancel-task-type');
            const closeBtn = modal.querySelector('.close-task-type-modal');
            cancelBtn.addEventListener('click', () => {
                cleanup();
                reject(new Error('Cancelled'));
            });
            closeBtn.addEventListener('click', () => {
                cleanup();
                reject(new Error('Cancelled'));
            });
            // Close on outside click
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    cleanup();
                    reject(new Error('Cancelled'));
                }
            });
        });
    }


    // Populate model dropdown with available models
    populateTaskModelDropdown(modal, selectedModel) {
        const modelSelect = modal.querySelector('#task-config-model');
        if (!modelSelect) return;

        const addedModels = new Set();

        if (this.appState.apiSettings && this.appState.apiSettings.apiKeys) {
            for (const [provider, key] of Object.entries(this.appState.apiSettings.apiKeys)) {
                if (key && this.availableModels[provider]) {
                    this.availableModels[provider].forEach(model => {
                        if (!addedModels.has(model.id)) {
                            const option = this.document.createElement('option');
                            option.value = model.id;
                            option.textContent = `${model.name} (${provider})`;
                            option.title = model.description;
                            if (model.id === selectedModel) {
                                option.selected = true;
                            }
                            modelSelect.appendChild(option);
                            addedModels.add(model.id);
                        }
                    });
                }
            }
        }
    }

    // Collect configuration from modal
    collectTaskConfig(modal, taskType) {
        const nameInput = modal.querySelector('#task-config-name').value.trim();
        const config = {
            task_type: taskType.id,
            // Default to task type name if empty
            name: nameInput || taskType.id,
            model: modal.querySelector('#task-config-model').value || null
        };

        // Collect task-specific fields
        if (taskType.configFields) {
            taskType.configFields.forEach(field => {
                const element = modal.querySelector(`#task-field-${field.id}`);
                if (element) {
                    if (field.type === 'checkbox') {
                        config[field.id] = element.checked;
                    } else if (field.type === 'number') {
                        const value = parseInt(element.value);
                        config[field.id] = isNaN(value) ? field.default : value;
                    } else if (field.type === 'textarea') {
                        // Split by newlines and filter empty lines
                        config[field.id] = element.value.split('\n')
                            .map(line => line.trim())
                            .filter(line => line.length > 0);
                    } else if (field.type === 'subtasks') {
                        // Get sub-tasks data from modal
                        config[field.id] = modal.subTasksData || {};
                    } else {
                        config[field.id] = element.value;
                    }
                }
            });
        }

        return config;
    }

    // Validate task configuration
    validateTaskConfig(config, taskType) {
        // Validate name
        if (!config.name) {
            this.notificationService.showNotification('Configuration name cannot be empty', 'error');
            return false;
        }

        const namePattern = /^[a-zA-Z0-9_-]+$/;
        if (!namePattern.test(config.name)) {
            this.notificationService.showNotification(
                'Configuration name can only contain letters, numbers, underscores and hyphens',
                'error'
            );
            return false;
        }

        // Validate task-specific fields
        if (taskType.configFields) {
            for (const field of taskType.configFields) {
                const value = config[field.id];

                if (field.type === 'number' && value !== undefined) {
                    if (field.min !== undefined && value < field.min) {
                        this.notificationService.showNotification(
                            `${field.label} must be at least ${field.min}`,
                            'error'
                        );
                        return false;
                    }
                    if (field.max !== undefined && value > field.max) {
                        this.notificationService.showNotification(
                            `${field.label} must be at most ${field.max}`,
                            'error'
                        );
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // Get configuration key for storage
    getConfigKey(taskType, configName) {
        return configName ? `${taskType}_${configName}` : taskType;
    }
}

 if (typeof module !== 'undefined' && module.exports) {
    module.exports = {TaskConfigManager};
}