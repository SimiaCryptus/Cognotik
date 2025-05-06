document.addEventListener('DOMContentLoaded', function() {
    // Get the modal
    const modal = document.getElementById('feature-modal');
    if (!modal) return; // Exit if modal doesn't exist

    const modalTitle = document.getElementById('modal-title');
    const modalContent = document.getElementById('modal-content');
    const closeModal = document.querySelector('.close-modal');
    let previouslyFocusedElement = null; // To restore focus later
    

    // Feature details content
    const featureDetails = {
        'General-Purpose AI Agents': {
            title: 'General-Purpose AI Agents',
            content: `
                <p>Cognotik's AI agents are designed to be versatile and adaptable, capable of handling a wide range of development tasks:</p>
                <ul>
                    <li><strong>Intelligent Code Generation:</strong> Create code snippets, entire functions, or even complete modules based on natural language descriptions.</li>
                    <li><strong>Adaptive Learning:</strong> Agents learn from your codebase and previous interactions to provide increasingly relevant suggestions.</li>
                    <li><strong>Context-Aware Assistance:</strong> Understand project structure, coding standards, and dependencies to provide contextually appropriate help.</li>
                    <li><strong>Multi-Language Support:</strong> Work across programming languages, frameworks, and platforms with equal proficiency.</li>
                    <li><strong>Debugging Assistance:</strong> Identify potential issues, suggest fixes, and explain the reasoning behind errors.</li>
                    <li><strong>Documentation Generation:</strong> Automatically create documentation for your code, including comments, README files, and API docs.</li>
                </ul>
                <p>These agents serve as true co-pilots in your development process, reducing cognitive load and handling repetitive tasks while you focus on higher-level design and problem-solving.</p>
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
        'Choose Any LLM Model': {
            title: 'Choose Any LLM Model',
            content: `
                <p>Cognotik gives you the freedom to select the AI model that best fits your specific requirements:</p>
                <ul>
                    <li><strong>Multiple Provider Support:</strong> Connect to OpenAI (GPT-3.5, GPT-4), Anthropic Claude, Google Gemini, and other compatible LLMs.</li>
                    <li><strong>Model Switching:</strong> Seamlessly switch between different models based on the task at hand.</li>
                    <li><strong>Cost Optimization:</strong> Use more affordable models for routine tasks and premium models for complex challenges.</li>
                    <li><strong>Specialized Capabilities:</strong> Select models with strengths in specific domains or programming languages.</li>
                    <li><strong>Future-Proof:</strong> As new models emerge, Cognotik's architecture allows for easy integration.</li>
                    <li><strong>Local Models:</strong> Support for running local models for complete privacy and offline work.</li>
                </ul>
                <p>This flexibility ensures you're never locked into a single AI provider or model, giving you control over performance, cost, and capabilities.</p>
            `
        },
        'Cross-Platform & Extensible': {
            title: 'Cross-Platform & Extensible',
            content: `
                <p>Cognotik is designed to fit seamlessly into your existing workflow, regardless of your platform or toolchain:</p>
                <ul>
                    <li><strong>Multiple Platforms:</strong> Available on Windows, macOS, Linux, as an IntelliJ plugin, and as a web application.</li>
                    <li><strong>Plugin Architecture:</strong> Extend functionality with custom plugins for specific languages, frameworks, or workflows.</li>
                    <li><strong>API Integration:</strong> Connect with version control systems, CI/CD pipelines, issue trackers, and other development tools.</li>
                    <li><strong>Customizable UI:</strong> Adapt the interface to your preferences and workflow requirements.</li>
                    <li><strong>Team Collaboration:</strong> Share configurations, custom plugins, and AI-generated resources across your team.</li>
                </ul>
                <p>The extensible nature of Cognotik means it can grow with your needs and adapt to new technologies and methodologies as they emerge.</p>
            `
        },
        'Bring Your Own Key (BYOK) Cost Model': {
            title: 'Bring Your Own Key (BYOK) Cost Model',
            content: `
                <p>Cognotik's BYOK approach puts you in complete control of your AI usage and costs:</p>
                <ul>
                    <li><strong>Direct API Access:</strong> Use your own API keys from OpenAI, Anthropic, Google, or other providers.</li>
                    <li><strong>Cost Transparency:</strong> Pay only for what you use, with no additional markups or hidden fees.</li>
                    <li><strong>Budget Control:</strong> Set usage limits and monitor consumption to prevent unexpected costs.</li>
                    <li><strong>Data Privacy:</strong> Ensure your code and sensitive information never passes through third-party servers.</li>
                    <li><strong>Compliance:</strong> Meet organizational security and privacy requirements by maintaining direct control over data flow.</li>
                    <li><strong>Usage Analytics:</strong> Track AI usage patterns to optimize costs and identify opportunities for efficiency.</li>
                </ul>
                <p>This model is particularly valuable for teams and enterprises with specific security requirements or cost management needs.</p>
            `
        }
    };
    
    // Add click event to all "Read More" buttons
    document.querySelectorAll('.read-more-btn').forEach(button => {
        button.addEventListener('click', function() {
            const featureTitle = this.parentElement.querySelector('h3').textContent;
            const details = featureDetails[featureTitle];
            
            if (details) {
                modalTitle.textContent = details.title;
                modalContent.innerHTML = details.content;
                modal.style.display = 'block';
                document.body.style.overflow = 'hidden'; // Prevent scrolling when modal is open
            }
        });
    });
    
    // Function to close the modal
    function closeModalHandler() {
        modal.style.display = 'none';
        document.body.style.overflow = 'auto'; // Re-enable scrolling
        if (previouslyFocusedElement) {
            previouslyFocusedElement.focus(); // Restore focus
            previouslyFocusedElement = null;
        }
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
        button.addEventListener('click', function() {
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
    window.addEventListener('click', function(event) {
        if (event.target === modal) {
            closeModalHandler();
        }
    });
    
    // Close modal with Escape key
    document.addEventListener('keydown', function(event) {
        if (event.key === 'Escape' && modal.style.display === 'block') {
            closeModalHandler();
        }
    });
});