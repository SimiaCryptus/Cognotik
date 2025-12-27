class CognotikHeader extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.data = null;
    }

    async connectedCallback() {
        await this.loadData();
        this.render();
    }

    async loadData() {
        try {
            // Resolve path relative to this script file to ensure correct loading
            // regardless of where the HTML page is located.
            // ../../data/tasks.json assumes: assets/scripts/components/ -> assets/data/
            const dataUrl = new URL('../../data/tasks.json', import.meta.url).href;
            
            const response = await fetch(dataUrl);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            this.data = await response.json();
        } catch (error) {
            console.error('CognotikHeader: Failed to load navigation data.', error);
            this.shadowRoot.innerHTML = `<div style="padding:1rem; color:red;">Error loading menu.</div>`;
        }
    }

    render() {
        if (!this.data) return;

        // User current browser location
        const currentPage = window.location.pathname.split('/').pop() || 'index.html';
        const { siteName, navigation } = this.data;
        // Find current item to check for code link
        const findItem = (items, id) => {
            for (const item of items) {
                if (item.url === id) return item;
                if (item.items) {
                    const found = findItem(item.items, id);
                    if (found) return found;
                }
            }
            return null;
        };
        const currentItem = findItem(navigation, currentPage);


        // Styles scoped to the Shadow DOM
        const styles = `
            <style>
                :host {
                    display: block;
                    background-color: var(--header-bg, var(--surface-color, #1e293b));
                    border-bottom: 1px solid var(--border-color, #334155);
                    font-family: var(--font-family, sans-serif);
                    position: sticky;
                    top: 0;
                    z-index: 1000;
                }
                .header-container {
                    max-width: var(--max-width, 1200px);
                    margin: 0 auto;
                    padding: 0 1.5rem;
                    height: var(--header-height, 60px);
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                }
                .brand {
                    font-weight: 700;
                    font-size: 1.25rem;
                    color: var(--primary-color, #f8fafc);
                    text-decoration: none;
                    white-space: nowrap;
                    margin-right: 1rem;
                }
                .nav-section {
                    display: flex;
                    align-items: center;
                    gap: 1rem;
                }
                .nav-menu {
                    display: flex;
                    gap: 1.5rem;
                    list-style: none;
                    margin: 0;
                    padding: 0;
                    align-items: center;
                }
                .nav-item {
                    position: relative;
                }
                .nav-link {
                    color: var(--text-color, #e2e8f0);
                    text-decoration: none;
                    font-size: 0.95rem;
                    padding: 0.5rem;
                    border-radius: 4px;
                    transition: color 0.2s, background-color 0.2s;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                }
                .nav-link:hover, .nav-link.active {
                    color: var(--accent-color, #8b5cf6);
                    background-color: rgba(255, 255, 255, 0.05);
                }
                
                /* Dropdown Styles */
                .dropdown-menu {
                    display: none;
                    position: absolute;
                    top: 100%;
                    right: 0;
                    background-color: var(--surface-color, #1e293b);
                    min-width: 240px;
                    
                    /* Updated for scrolling and columns */
                    max-height: 70vh;
                    overflow-y: auto;
                    width: max-content;
                    max-width: min(80vw, 800px);
                    
                    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    border: 1px solid var(--border-color, #334155);
                    border-radius: 4px;
                    padding: 0.5rem;
                    z-index: 1001;
                }
                .nav-item:hover .dropdown-menu {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
                    gap: 0.5rem;
                }
                .dropdown-item {
                    display: flex;
                    align-items: flex-start;
                    padding: 0.75rem 1rem;
                    color: var(--text-color, #e2e8f0);
                    text-decoration: none;
                    font-size: 0.9rem;
                    border-bottom: 1px solid var(--border-color, #334155);
                    border-radius: 4px;
                }
                .dropdown-item:last-child {
                    border-bottom: none;
                }
                .dropdown-item:hover, .dropdown-item.active {
                    background-color: rgba(255, 255, 255, 0.05);
                    color: var(--accent-color, #8b5cf6);
                }
                .dropdown-icon {
                    width: 40px;
                    height: 40px;
                    object-fit: cover;
                    border-radius: 4px;
                    margin-right: 0.75rem;
                    flex-shrink: 0;
                    background: rgba(255,255,255,0.05);
                }
                .dropdown-content {
                    flex: 1;
                }
                .dropdown-desc {
                    display: block;
                    font-size: 0.75rem;
                    color: var(--text-muted, #94a3b8);
                    margin-top: 2px;
                }
                /* Search & Actions */
                .actions {
                    display: flex;
                    align-items: center;
                    margin-left: 1rem;
                    gap: 0.5rem;
                }
                .search-container {
                    position: relative;
                }
                .search-input {
                    background: rgba(255, 255, 255, 0.05);
                    border: 1px solid var(--border-color, #334155);
                    border-radius: 4px;
                    padding: 0.4rem 0.6rem;
                    color: var(--text-color, #e2e8f0);
                    font-family: inherit;
                    font-size: 0.9rem;
                    width: 160px;
                    transition: width 0.2s, background-color 0.2s;
                }
                .search-input:focus {
                    width: 220px;
                    outline: none;
                    background: rgba(255, 255, 255, 0.1);
                    border-color: var(--accent-color, #8b5cf6);
                }
                .search-results {
                    display: none;
                    position: absolute;
                    top: 100%;
                    right: 0;
                    width: 280px;
                    background: var(--surface-color, #1e293b);
                    border: 1px solid var(--border-color, #334155);
                    border-radius: 4px;
                    margin-top: 4px;
                    max-height: 400px;
                    overflow-y: auto;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    z-index: 1002;
                }
                .search-results.active {
                    display: block;
                }
                .search-result-item {
                    display: flex;
                    align-items: center;
                    padding: 0.5rem;
                    color: var(--text-color, #e2e8f0);
                    text-decoration: none;
                    border-bottom: 1px solid var(--border-color, #334155);
                    font-size: 0.9rem;
                }
                .search-result-item:hover {
                    background: rgba(255, 255, 255, 0.05);
                }
                .search-result-icon {
                    width: 24px;
                    height: 24px;
                    object-fit: cover;
                    border-radius: 3px;
                    margin-right: 0.5rem;
                }
                .github-link {
                    display: flex;
                    align-items: center;
                    color: var(--text-color, #e2e8f0);
                    text-decoration: none;
                    font-size: 0.85rem;
                    padding: 0.4rem 0.6rem;
                    border-radius: 4px;
                    border: 1px solid transparent;
                }
                .github-link:hover {
                    background: rgba(255, 255, 255, 0.05);
                    border-color: var(--border-color, #334155);
                    color: var(--accent-color, #8b5cf6);
                }
                .github-icon {
                    width: 18px;
                    height: 18px;
                    margin-right: 0.4rem;
                    fill: currentColor;
                }
                
                /* Mobile Responsive Adjustments (Basic) */
                @media (max-width: 768px) {
                    .header-container {
                        flex-direction: column;
                        height: auto;
                        padding: 1rem;
                    }
                    .nav-section {
                        flex-direction: column;
                        width: 100%;
                    }
                    .nav-menu, .actions {
                        margin-top: 1rem;
                        flex-wrap: wrap;
                        justify-content: center;
                    }
                    .search-input {
                        width: 100%;
                    }
                }
            </style>
        `;

        // Helper to build navigation items recursively
        const buildNavItems = (items) => {
            return items.map(item => {
                if (item.type === 'link') {
                    const isActive = item.id === currentPage;
                    return `
                        <li class="nav-item">
                            <a href="${item.url}" class="nav-link ${isActive ? 'active' : ''}">${item.label}</a>
                        </li>
                    `;
                } else if (item.type === 'dropdown') {
                    // Check if any child is active to highlight the parent dropdown
                    const hasActiveChild = item.items && item.items.some(sub => sub.url === currentPage);
                    
                    const dropdownItems = item.items.map(sub => {
                        const isSubActive = sub.url === currentPage;
                        const iconHtml = sub.image ? `<img src="${sub.image}" class="dropdown-icon" alt="" loading="lazy" />` : '';
                        return `
                            <a href="${sub.url}" class="dropdown-item ${isSubActive ? 'active' : ''}">
                                ${iconHtml}
                                <div class="dropdown-content">
                                    <div style="font-weight:500">${sub.label}</div>
                                    ${sub.description ? `<span class="dropdown-desc">${sub.description}</span>` : ''}
                                </div>
                            </a>
                        `;
                    }).join('');

                    return `
                        <li class="nav-item">
                            <span class="nav-link ${hasActiveChild ? 'active' : ''}">
                                ${item.label} <span style="font-size:0.7em; margin-left:4px;">▼</span>
                            </span>
                            <div class="dropdown-menu">
                                ${dropdownItems}
                            </div>
                        </li>
                    `;
                }
                return '';
            }).join('');
        };
        // GitHub Link HTML
        let githubHtml = '';
        if (currentItem && currentItem.code) {
            githubHtml = `
                <a href="${currentItem.code}" target="_blank" class="github-link" title="View Source on GitHub">
                    <svg class="github-icon" viewBox="0 0 24 24"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>
                    <span>View Code</span>
                </a>
            `;
        }


        this.shadowRoot.innerHTML = `
            ${styles}
            <header class="header-container">
                <a href="/index.html" class="brand">${siteName}</a>
                <div class="nav-section">
                    <ul class="nav-menu">
                        ${buildNavItems(navigation)}
                    </ul>
                    <div class="actions">
                        <div class="search-container">
                            <input type="text" class="search-input" placeholder="Search..." aria-label="Search">
                            <div class="search-results"></div>
                        </div>
                        ${githubHtml}
                    </div>
                </div>
            </header>
        `;
        // Setup Search Logic
        const searchInput = this.shadowRoot.querySelector('.search-input');
        const searchResults = this.shadowRoot.querySelector('.search-results');
        if (searchInput && searchResults) {
            // Flatten items for search
            const searchableItems = [];
            const collectItems = (items) => {
                items.forEach(item => {
                    if (item.url) searchableItems.push(item);
                    if (item.items) collectItems(item.items);
                });
            };
            collectItems(navigation);
            searchInput.addEventListener('input', (e) => {
                const query = e.target.value.toLowerCase().trim();
                if (query.length < 2) {
                    searchResults.classList.remove('active');
                    return;
                }
                const matches = searchableItems.filter(item => 
                    (item.label && item.label.toLowerCase().includes(query)) || 
                    (item.description && item.description.toLowerCase().includes(query))
                );
                if (matches.length > 0) {
                    searchResults.innerHTML = matches.map(item => `
                        <a href="${item.url}" class="search-result-item">
                            ${item.image ? `<img src="${item.image}" class="search-result-icon" alt="">` : ''}
                            <div>
                                <div style="font-weight:600">${item.label}</div>
                                ${item.description ? `<div style="font-size:0.8em; opacity:0.7">${item.description}</div>` : ''}
                            </div>
                        </a>
                    `).join('');
                    searchResults.classList.add('active');
                } else {
                    searchResults.innerHTML = `<div style="padding:0.5rem; color:var(--text-muted, #94a3b8);">No results found</div>`;
                    searchResults.classList.add('active');
                }
            });
            // Close search when clicking outside
            document.addEventListener('click', (e) => {
                const path = e.composedPath();
                if (!path.includes(this.shadowRoot.querySelector('.search-container'))) {
                    searchResults.classList.remove('active');
                }
            });
        }
    }
}

// Define the custom element
customElements.define('cognotik-header', CognotikHeader);