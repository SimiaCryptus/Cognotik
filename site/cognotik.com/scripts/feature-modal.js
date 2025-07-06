document.addEventListener('DOMContentLoaded', function () {
    // Get the modal
    const modal = document.getElementById('feature-modal');
    if (!modal) return; // Exit if modal doesn't exist

    const modalTitle = document.getElementById('modal-title');
    const modalContent = document.getElementById('modal-content');
    const closeModal = document.querySelector('.close-modal');
    let previouslyFocusedElement = null; // To restore focus later


    // Feature details content
    const featureDetails = {
        'Intelligent Task Automation': {
            title: 'Intelligent Task Automation',
            content: `
                <p>Cognotik's intelligent agents automate complex workflows while understanding context and adapting to your needs:</p>
                <ul>
                    <li><strong>Context-Aware Processing:</strong> AI agents understand your project structure, coding patterns, and requirements to deliver relevant solutions.</li>
                    <li><strong>Specialized Task Optimization:</strong> Each agent is fine-tuned for specific tasks like code generation, refactoring, documentation, or testing.</li>
                    <li><strong>Quality Assurance:</strong> Built-in checks ensure generated code follows best practices and maintains consistency with your existing codebase.</li>
                    <li><strong>Adaptive Learning:</strong> Agents improve their understanding of your preferences and coding style over time.</li>
                    <li><strong>Workflow Integration:</strong> Seamlessly fits into your existing development process without disrupting your flow.</li>
                </ul>
                <p>Focus on creative problem-solving while Cognotik handles the repetitive tasks that slow you down.</p>
            `
        },
        'Free & Open Source': {
            title: 'Free & Open Source',
            content: `
                <p>Cognotik is committed to the principles of open-source software development:</p>
                <ul>
                    <li><strong>Apache 2.0 License:</strong> Use, modify, and distribute Cognotik freely for both personal and commercial projects.</li>
                    <li><strong>Transparent Codebase:</strong> Review the code for security, customize it for your specific needs, or learn from its implementation.</li>
                    <li><strong>Community Contributions:</strong> Benefit from improvements and extensions created by a global community of developers.</li>
                    <li><strong>No Vendor Lock-in:</strong> Avoid dependency on proprietary solutions with hidden costs or unexpected changes.</li>
                    <li><strong>Ethical AI Development:</strong> Participate in building AI tools that respect user privacy and promote responsible use.</li>
                </ul>
                <p>The open-source nature of Cognotik ensures longevity, security, and alignment with the collaborative spirit of software development.</p>
                <p><a href="https://github.com/SimiaCryptus/Cognotik" target="_blank" rel="noopener">Visit our GitHub repository</a> to explore the code, contribute, or report issues.</p>
            `
        },
        'Secure & Privacy-First': {
            title: 'Secure & Privacy-First',
            content: `
                <p>Your data security and privacy are paramount with Cognotik's BYOK (Bring Your Own Key) model:</p>
                <ul>
                    <li><strong>Direct API Connections:</strong> Connect directly to OpenAI, Anthropic, Google, or other AI providers using your own API keys.</li>
                    <li><strong>Zero Data Storage:</strong> No code, prompts, or responses pass through our servers - everything stays between you and your chosen AI provider.</li>
                    <li><strong>Enterprise Compliance:</strong> Perfect for organizations with strict security requirements, GDPR compliance, or sensitive codebases.</li>
                    <li><strong>Complete Control:</strong> You maintain full ownership and control over all data, with no third-party access or storage.</li>
                    <li><strong>Audit Trail:</strong> All AI interactions can be logged locally for compliance and review purposes.</li>
                </ul>
                <p>Cognotik ensures your intellectual property and sensitive information remain completely under your control.</p>
            `
        },
        'Cross-Platform & Extensible': {
            title: 'Cross-Platform & Extensible',
            content: `
                <p>Cognotik is designed to fit seamlessly into your existing workflow, regardless of your platform or toolchain:</p>
                <ul>
                    <li><strong>Cross-Platform:</strong> Available on Windows, macOS, Linux, and as an IntelliJ plugin.</li>
                    <li><strong>Standalone Package:</strong> No dependencies on Java or other runtimes, making it easy to install and run.</li>
                    <li><strong>Background Daemon:</strong> Cognotik is run as a background service accessed via a web UI, allowing for easy integration with various tools.</li>
                    <li><strong>Auto-update:</strong> Automatically update to the latest version with new features and improvements.</li>
                    <li><strong>Extensible Architecture:</strong> Extend functionality with custom providers for task types, service access, and even cognitive modes.</li>
                </ul>
                <p>The extensible nature of Cognotik means it can grow with your needs and adapt to new technologies and methodologies as they emerge.</p>
            `
        },
        'Smart Version Control Integration': {
            title: 'Smart Version Control Integration',
            content: `
                <p>Cognotik ensures you maintain full control over every change made to your codebase:</p>
                <ul>
                    <li><strong>Patch-Based Review:</strong> All modifications are presented as clear, reviewable patches before application.</li>
                    <li><strong>Git Integration:</strong> Seamlessly works with Git and other version control systems for proper change tracking.</li>
                    <li><strong>Selective Application:</strong> Accept, reject, or modify individual suggestions with full transparency.</li>
                    <li><strong>Change History:</strong> Complete audit trail of all AI-suggested modifications and your decisions.</li>
                    <li><strong>Rollback Support:</strong> Easily revert any changes if needed, maintaining code integrity.</li>
                </ul>
                <p>Never worry about AI making unwanted changes - you're always in the driver's seat.</p>
            `
        },
        'Multi-Model Support': {
            title: 'Multi-Model Support',
            content: `
                <p>Choose the best AI model for each task with Cognotik's flexible multi-model architecture:</p>
                <ul>
                    <li><strong>Provider Support:</strong> Works with OpenAI GPT-4, Anthropic Claude, Google Gemini, and local models.</li>
                    <li><strong>Dynamic Switching:</strong> Seamlessly switch between models based on task requirements.</li>
                    <li><strong>Cost Optimization:</strong> Use affordable models for routine tasks and premium models for complex challenges.</li>
                    <li><strong>Automatic Selection:</strong> Let Cognotik choose the optimal model based on your requirements and budget.</li>
                    <li><strong>Model Comparison:</strong> Compare outputs from different models to choose the best solution.</li>
                    <li><strong>Future-Proof:</strong> Easy integration of new models as they become available.</li>
                </ul>
                <p>This flexibility ensures optimal performance and cost-effectiveness for every task.</p>
            `
        },
        'Built for Teams': {
            title: 'Built for Teams',
            content: `
                <p>Cognotik enhances team collaboration and maintains consistency across your development organization:</p>
                <ul>
                    <li><strong>Shared AI Assistants:</strong> Configure team-wide AI assistants with consistent behavior and knowledge.</li>
                    <li><strong>Coding Standards:</strong> Enforce team coding standards and best practices automatically.</li>
                    <li><strong>Knowledge Sharing:</strong> Share custom prompts, templates, and configurations across the team.</li>
                    <li><strong>Onboarding Acceleration:</strong> Help new developers understand codebases and practices faster.</li>
                    <li><strong>Code Review Enhancement:</strong> AI-powered code reviews ensure quality and consistency.</li>
                    <li><strong>Collaborative Workflows:</strong> Support for pair programming and collaborative problem-solving with AI.</li>
                </ul>
                <p>Transform your team's productivity while maintaining high code quality and consistency.</p>
            `
        },
        'Multi-Modal Cognitive Architecture': {
            title: 'Multi-Modal Cognitive Architecture',
            content: `
                <p>Cognotik implements a groundbreaking cognitive planning architecture that adapts to your problem domain:</p>
                <div style="margin-top:1.5em;">
                    <h4 style="color:#007bff;margin-bottom:0.5em;">🎯 TaskChat Mode - Phenomenological Cognition</h4>
                    <p>Reactive and conversational, perfect for immediate responses and interactive development. Features instant contextual responses, minimal cognitive overhead, and natural dialogue flow.</p>
                    
                    <h4 style="color:#28a745;margin-top:1em;margin-bottom:0.5em;">📋 PlanAhead Mode - Rationalist Cognition</h4>
                    <p>Comprehensive upfront planning for well-defined projects. Creates detailed execution plans with complete dependency analysis, optimized execution order, and predictable outcomes.</p>
                    
                    <h4 style="color:#ffc107;margin-top:1em;margin-bottom:0.5em;">🔄 AutoPlan Mode - Pragmatist Cognition</h4>
                    <p>Iterative planning with metacognitive awareness. Features dynamic strategy adjustment, explicit thinking status, and learning from outcomes.</p>
                    
                    <h4 style="color:#dc3545;margin-top:1em;margin-bottom:0.5em;">🎯 GoalOriented Mode - Systematist Cognition</h4>
                    <p>Hierarchical goal decomposition for complex projects. Provides hierarchical task networks, goal-level supervision, and systems thinking approach.</p>
                </div>
                <p style="margin-top:1.5em;"><strong>Why Cognitive Pluralism Matters:</strong> Different problem domains require fundamentally different approaches to thinking. Cognotik matches its cognitive approach to the natural structure of your problem, whether you're doing rapid prototyping, systematic refactoring, exploratory debugging, or architectural planning.</p>
            `
        },
        'Comprehensive Task Library': {
            title: 'Comprehensive Task Library',
            content: `
                <p>Access a rich library of specialized task types designed for every aspect of software development:</p>
                
                <h4 style="color:#007bff;margin-top:1em;">📝 Code & File Management</h4>
                <ul>
                    <li><strong>File Modification:</strong> Create or modify files with diff-based preview and style preservation</li>
                    <li><strong>File Search:</strong> Pattern-based search with regex support and contextual results</li>
                    <li><strong>Insight Analysis:</strong> Code reviews, architecture recommendations, and Q&A</li>
                </ul>
                
                <h4 style="color:#28a745;margin-top:1em;">🔧 Execution & Automation</h4>
                <ul>
                    <li><strong>Shell Commands:</strong> Safe execution with output capture and error handling</li>
                    <li><strong>Code Execution:</strong> Run snippets in controlled environments with multiple language support</li>
                    <li><strong>Command Auto-Fix:</strong> Automatic error detection and correction with approval workflow</li>
                </ul>
                
                <h4 style="color:#ffc107;margin-top:1em;">📊 Planning & Organization</h4>
                <ul>
                    <li><strong>Task Planning:</strong> Complex task breakdown with dependency management</li>
                    <li><strong>Software Graph Planning:</strong> Graph-aware planning based on code structure</li>
                    <li><strong>Foreach Iteration:</strong> Sequential processing with progress tracking</li>
                </ul>
                
                <h4 style="color:#dc3545;margin-top:1em;">🔍 Knowledge & Search</h4>
                <ul>
                    <li><strong>Embedding Search:</strong> Semantic search using AI embeddings</li>
                    <li><strong>Knowledge Indexing:</strong> Document and code indexing with parallel processing</li>
                    <li><strong>Data Compilation:</strong> Extract and compile structured data from multiple sources</li>
                </ul>
                
                <h4 style="color:#6c757d;margin-top:1em;">🌐 External Integration</h4>
                <ul>
                    <li><strong>GitHub Search:</strong> Advanced repository and code search</li>
                    <li><strong>Web Search & Analysis:</strong> Google integration with content analysis</li>
                    <li><strong>Browser Automation:</strong> Selenium-based testing and data collection</li>
                </ul>
                
                <p style="margin-top:1em;">Combine and orchestrate these tasks to create powerful workflows tailored to your specific needs.</p>
            `
        }
    };



    // Function to close the modal
    function closeModalHandler() {
        modal.style.display = 'none';
        document.body.style.overflow = 'auto'; // Re-enable scrolling
        if (previouslyFocusedElement) {
            previouslyFocusedElement.focus(); // Restore focus
            previouslyFocusedElement = null;
        }
        modal.removeEventListener('keydown', trapFocus); // Remove focus trap listener as per documentation
    }

    // Add focus trapping inside the modal
    function trapFocus(event) {
        if (event.key !== 'Tab') return;

        const focusableElements = modal.querySelectorAll(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (event.shiftKey) { // Shift + Tab
            if (document.activeElement === firstElement) {
                lastElement.focus();
                event.preventDefault();
            }
        } else { // Tab
            if (document.activeElement === lastElement) {
                firstElement.focus();
                event.preventDefault();
            }
        }
    }

    // Open modal logic
    document.querySelectorAll('.read-more-btn').forEach(button => {
        button.addEventListener('click', function () {
            const featureTitle = this.parentElement.querySelector('h3').textContent;
            const details = featureDetails[featureTitle];

            if (details && modalTitle && modalContent && closeModal) {
                previouslyFocusedElement = document.activeElement; // Store focus
                modalTitle.textContent = details.title;
                modalContent.innerHTML = details.content;
                modal.style.display = 'block';
                document.body.style.overflow = 'hidden'; // Prevent scrolling
                closeModal.focus(); // Set initial focus to the close button
                modal.addEventListener('keydown', trapFocus); // Add focus trap listener
            }
        });
    });

    // Close modal when clicking the close button
    if (closeModal) {
        closeModal.addEventListener('click', closeModalHandler);
    }

    // Close modal when clicking outside the modal content
    window.addEventListener('click', function (event) {
        if (event.target === modal) {
            closeModalHandler();
        }
    });

    // Close modal with Escape key
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && modal.style.display === 'block') {
            closeModalHandler();
        }
    });
});