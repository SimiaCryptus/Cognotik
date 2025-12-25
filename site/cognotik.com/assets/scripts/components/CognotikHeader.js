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

        const currentPageId = this.getAttribute('current-page');
        const { siteName, navigation } = this.data;

        // Styles scoped to the Shadow DOM
        const styles = `
            <style>
                :host {
                    display: block;
                    background-color: var(--header-bg, #f8f9fa);
                    border-bottom: 1px solid var(--border-color, #e9ecef);
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
                    color: var(--primary-color, #000);
                    text-decoration: none;
                    white-space: nowrap;
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
                    color: var(--text-color, #333);
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
                    color: var(--accent-color, #007bff);
                    background-color: rgba(0, 0, 0, 0.05);
                }
                
                /* Dropdown Styles */
                .dropdown-menu {
                    display: none;
                    position: absolute;
                    top: 100%;
                    right: 0;
                    background-color: #fff;
                    min-width: 240px;
                    
                    /* Updated for scrolling and columns */
                    max-height: 70vh;
                    overflow-y: auto;
                    width: max-content;
                    max-width: min(80vw, 800px);
                    
                    box-shadow: 0 4px 12px rgba(0,0,0,0.1);
                    border: 1px solid var(--border-color, #e9ecef);
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
                    display: block;
                    padding: 0.75rem 1rem;
                    color: var(--text-color, #333);
                    text-decoration: none;
                    font-size: 0.9rem;
                    border-bottom: 1px solid #f1f1f1;
                    border-radius: 4px;
                }
                .dropdown-item:last-child {
                    border-bottom: none;
                }
                .dropdown-item:hover, .dropdown-item.active {
                    background-color: #f8f9fa;
                    color: var(--accent-color, #007bff);
                }
                .dropdown-desc {
                    display: block;
                    font-size: 0.75rem;
                    color: #6c757d;
                    margin-top: 2px;
                }
                
                /* Mobile Responsive Adjustments (Basic) */
                @media (max-width: 768px) {
                    .header-container {
                        flex-direction: column;
                        height: auto;
                        padding: 1rem;
                    }
                    .nav-menu {
                        margin-top: 1rem;
                        flex-wrap: wrap;
                        justify-content: center;
                    }
                }
            </style>
        `;

        // Helper to build navigation items recursively
        const buildNavItems = (items) => {
            return items.map(item => {
                if (item.type === 'link') {
                    const isActive = item.id === currentPageId;
                    return `
                        <li class="nav-item">
                            <a href="${item.url}" class="nav-link ${isActive ? 'active' : ''}">${item.label}</a>
                        </li>
                    `;
                } else if (item.type === 'dropdown') {
                    // Check if any child is active to highlight the parent dropdown
                    const hasActiveChild = item.items && item.items.some(sub => sub.id === currentPageId);
                    
                    const dropdownItems = item.items.map(sub => {
                        const isSubActive = sub.id === currentPageId;
                        return `
                            <a href="${sub.url}" class="dropdown-item ${isSubActive ? 'active' : ''}">
                                <div>${sub.label}</div>
                                ${sub.description ? `<span class="dropdown-desc">${sub.description}</span>` : ''}
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

        this.shadowRoot.innerHTML = `
            ${styles}
            <header class="header-container">
                <a href="/index.html" class="brand">${siteName}</a>
                <ul class="nav-menu">
                    ${buildNavItems(navigation)}
                </ul>
            </header>
        `;
    }
}

// Define the custom element
customElements.define('cognotik-header', CognotikHeader);