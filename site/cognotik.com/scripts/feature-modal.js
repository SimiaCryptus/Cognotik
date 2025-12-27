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
        'The Planning Engine': {
            title: 'The Planning Engine',
            content: `
                <p>Cognotik's planning engine acts as the scheduler for your cognitive operating system. It is a recursive, mode-aware planner that breaks down high-level goals into executable sub-plans with deterministic guardrails.</p>
                <ul>
                    <li><strong>Recursive Decomposition:</strong> Automatically breaks down complex objectives into manageable sub-tasks using the <em>SubPlanningTask</em>, with explicit recursion depth control to prevent runaway loops.</li>
                    <li><strong>Cognitive Modes:</strong> Switch between different reasoning strategies per branch:
                        <ul>
                            <li><em>TaskChat:</em> Reactive and conversational for immediate needs.</li>
                            <li><em>PlanAhead:</em> Comprehensive upfront planning (Waterfall) for well-defined projects.</li>
                            <li><em>AutoPlan:</em> Iterative, self-correcting planning loops.</li>
                            <li><em>GoalOriented:</em> Hierarchical goal decomposition for complex systems.</li>
                        </ul>
                    </li>
                    <li><strong>Context Propagation:</strong> Explicitly passes context and dependencies down to sub-plans and aggregates results back up for summarization.</li>
                    <li><strong>Deterministic Execution:</strong> Built on Kotlin's structured concurrency to ensure safe, predictable execution of task graphs.</li>
                </ul>
            `
        },
        'Typed Task Ecosystem': {
            title: 'Typed Task Ecosystem',
            content: `
                <p>Cognotik provides a modular "package system" for cognition. Unlike other frameworks that treat tools as simple functions, Cognotik's tasks are typed, non-nestable modules with their own UI, configuration schema, and lifecycle.</p>
                <ul>
                    <li><strong>Reasoning Modules:</strong> A library of cognitive faculties including Symbolic Reasoning (Decision Trees, FSM), Statistical Reasoning (Causal Inference), and Meta-Reasoning (Chain of Thought).</li>
                    <li><strong>Tool Integration:</strong> Specialized tasks for running code, executing shell commands, orchestrating MCP servers, and web crawling.</li>
                    <li><strong>Writing & Output:</strong> Tasks for generating structured documents, narratives, presentations, and technical specifications.</li>
                    <li><strong>Type Safety:</strong> Every task has a strict schema, ensuring that inputs and outputs are validated and predictable.</li>
                </ul>
            `
        },
        'Local-First & Private': {
            title: 'Local-First & Private',
            content: `
                <p>Cognotik is designed for developers who demand control. Your data never leaves your infrastructure unless you send it to a model provider of your choice.</p>
                <ul>
                    <li><strong>BYOK (Bring Your Own Key):</strong> Connect directly to OpenAI, Anthropic, Google, or local models using your own API keys. No middleman servers.</li>
                    <li><strong>Local Execution:</strong> The runtime operates entirely on your machine, giving you full access to your local filesystem and tools without exposing them to the cloud.</li>
                    <li><strong>Open Source:</strong> Licensed under Apache 2.0, ensuring transparency and preventing vendor lock-in. You own the platform.</li>
                    <li><strong>Privacy by Design:</strong> Perfect for sensitive codebases and enterprise environments where data sovereignty is paramount.</li>
                </ul>
            `
        },
        'Multi-Modal Runtime': {
            title: 'Multi-Modal Runtime',
            content: `
                <p>Cognotik acts as the system call layer for AI workflows, orchestrating heterogeneous resources within a single plan.</p>
                <ul>
                    <li><strong>Multi-Model Routing:</strong> Assign different models to different tasks within the same workflow. Use Claude for reasoning, GPT-4 for coding, and a local Llama model for summarization.</li>
                    <li><strong>Polyglot Execution:</strong> Seamlessly execute Python scripts, JavaScript tools, Shell commands, and Kotlin code in a unified environment.</li>
                    <li><strong>Tool Orchestration:</strong> Manage dependencies and data flow between disparate tools, from web browsers to compilers.</li>
                    <li><strong>Hybrid Intelligence:</strong> Combine LLM-based probabilistic reasoning with deterministic algorithms (like decision trees or genetic optimization) for robust results.</li>
                </ul>
            `
        },
        'Real-Time Web UI': {
            title: 'Real-Time Web UI',
            content: `
                <p>Cognotik provides a "window manager" for your agents, offering deep visibility and control over autonomous processes.</p>
                <ul>
                    <li><strong>Task Simulators:</strong> Every task type comes with a dedicated UI for configuration, simulation, and review.</li>
                    <li><strong>Live Observability:</strong> Watch execution logs, intermediate outputs, and state changes in real-time as the agent thinks and acts.</li>
                    <li><strong>Patch-Based Review:</strong> For code modifications, Cognotik presents a clear, diff-based patch review interface. You approve or reject every change before it touches your disk.</li>
                    <li><strong>Visualizations:</strong> Rich visual outputs for complex tasks, such as decision tree graphs or optimization progress charts.</li>
                </ul>
            `
        },
        'Workspace Intelligence': {
            title: 'Workspace Intelligence',
            content: `
                <p>Cognotik integrates directly with your development environment, serving as the filesystem API for your agents.</p>
                <ul>
                    <li><strong>File Operations:</strong> Specialized tasks for searching, reading, and modifying files with awareness of project structure.</li>
                    <li><strong>Data Ingestion:</strong> Capabilities to ingest and process various data formats from your local workspace.</li>
                    <li><strong>Project Awareness:</strong> Agents can analyze your codebase to understand context, dependencies, and style patterns.</li>
                    <li><strong>Safe Modification:</strong> All file operations are sandboxed and subject to user review, ensuring AI doesn't break your build.</li>
                </ul>
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