// ===== App Directory =====
let appDirectory = [];
let appSearchQuery = '';
// Tag filter state: tag -> 'ignore' | 'require' | 'exclude'
let tagFilters = {};
// Category collapse state: category -> boolean (true = collapsed)
let collapsedCategories = {};

async function loadAppDirectory() {
    console.log('[loadAppDirectory] Loading app directory...');
    try {
        const response = await fetch('/appDirectory');
        if (response.ok) {
            appDirectory = await response.json();
            console.log('[loadAppDirectory] Loaded', appDirectory.length, 'apps');
        } else {
            console.error('[loadAppDirectory] Failed to load apps.json:', response.status);
            appDirectory = [];
        }
    } catch (error) {
        console.error('[loadAppDirectory] Error:', error);
        appDirectory = [];
    }
}

function getAllTags() {
    const tagSet = new Set();
    appDirectory.forEach(app => {
        if (Array.isArray(app.tags)) {
            app.tags.forEach(t => {
                if (t) tagSet.add(String(t));
            });
        }
    });
    return Array.from(tagSet).sort();
}

function getCategoryLabel(category) {
    if (!category) return 'Uncategorized';
    const s = String(category);
    return s.charAt(0).toUpperCase() + s.slice(1);
}

function groupAppsByCategory(apps) {
    const groups = {};
    apps.forEach(app => {
        const cat = app.category || 'uncategorized';
        if (!groups[cat]) groups[cat] = [];
        groups[cat].push(app);
    });
    return groups;
}

function renderTagFilters() {
    const container = document.getElementById('app-tag-filters');
    if (!container) return;
    const tags = getAllTags();
    if (tags.length === 0) {
        container.style.display = 'none';
        container.innerHTML = '';
        return;
    }
    container.style.display = '';
    // Clean up filters for tags that no longer exist
    Object.keys(tagFilters).forEach(t => {
        if (!tags.includes(t)) delete tagFilters[t];
    });

    const hasActiveFilters = Object.values(tagFilters).some(v => v === 'require' || v === 'exclude');
    const clearBtnHtml = hasActiveFilters
        ? `<button type="button" class="app-tag-clear-btn" id="app-tag-clear-btn">Clear filters</button>`
        : '';

    const tagsHtml = tags.map(tag => {
        const state = tagFilters[tag] || 'ignore';
        const safeTag = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(tag) : tag;
        let icon = '';
        if (state === 'require') icon = '<span class="app-tag-filter-icon" aria-hidden="true">✓</span> ';
        else if (state === 'exclude') icon = '<span class="app-tag-filter-icon" aria-hidden="true">✕</span> ';
        const title = state === 'ignore'
            ? `Click to require "${safeTag}"`
            : state === 'require'
                ? `Click to exclude "${safeTag}"`
                : `Click to ignore "${safeTag}"`;
        return `<button type="button" class="app-tag-filter app-tag-filter-${state}" data-tag="${safeTag}" title="${title}" aria-pressed="${state !== 'ignore'}">${icon}${safeTag}</button>`;
    }).join('');

    container.innerHTML = `
            <div class="app-tag-filters-label">Filter by tag:</div>
            <div class="app-tag-filters-list">${tagsHtml}</div>
            ${clearBtnHtml}
        `;

    container.querySelectorAll('.app-tag-filter').forEach(btn => {
        btn.addEventListener('click', () => {
            const tag = btn.dataset.tag;
            const current = tagFilters[tag] || 'ignore';
            const next = current === 'ignore' ? 'require' : current === 'require' ? 'exclude' : 'ignore';
            if (next === 'ignore') {
                delete tagFilters[tag];
            } else {
                tagFilters[tag] = next;
            }
            renderTagFilters();
            renderAppGrid();
            setupAppCards();
        });
    });

    const clearBtn = container.querySelector('#app-tag-clear-btn');
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            tagFilters = {};
            renderTagFilters();
            renderAppGrid();
            setupAppCards();
        });
    }
}

function renderAppGrid() {
    const grid = document.getElementById('app-grid');
    if (!grid) return;
    grid.innerHTML = '';
    const filteredApps = filterApps(appDirectory, appSearchQuery);
    const groups = groupAppsByCategory(filteredApps);
    // Sort categories alphabetically, but put "uncategorized" last
    const categoryKeys = Object.keys(groups).sort((a, b) => {
        if (a === 'uncategorized') return 1;
        if (b === 'uncategorized') return -1;
        return a.localeCompare(b);
    });

    categoryKeys.forEach(categoryKey => {
        const apps = groups[categoryKey];
        const isCollapsed = !!collapsedCategories[categoryKey];
        const label = getCategoryLabel(categoryKey);
        const safeLabel = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(label) : label;

        const groupEl = document.createElement('div');
        groupEl.className = 'app-category-group' + (isCollapsed ? ' collapsed' : '');
        groupEl.dataset.category = categoryKey;

        const headerEl = document.createElement('div');
        headerEl.className = 'app-category-header';
        headerEl.setAttribute('role', 'button');
        headerEl.setAttribute('tabindex', '0');
        headerEl.setAttribute('aria-expanded', String(!isCollapsed));
        headerEl.innerHTML = `
                <span class="app-category-toggle-icon" aria-hidden="true">${isCollapsed ? '▶' : '▼'}</span>
                <span class="app-category-title">${safeLabel}</span>
                <span class="app-category-count">${apps.length}</span>
            `;

        const bodyEl = document.createElement('div');
        bodyEl.className = 'app-category-body';
        if (isCollapsed) bodyEl.style.display = 'none';

        const innerGrid = document.createElement('div');
        innerGrid.className = 'app-grid-inner';

        apps.forEach(app => {
            const appUrl = getAppUrl(app);
            const card = document.createElement('a');
            card.href = appUrl;
            card.className = 'app-card' + (app.cardClass ? ' ' + app.cardClass : '');
            card.id = app.id;
            // Apply per-app background image if provided
            if (app.hasBackground && app.backgroundUrl) {
                const bgUrl = getAppAssetUrl(app.backgroundUrl);
                card.classList.add('app-card-has-background');
                // Use a CSS variable so the desaturation layer (::after) can
                // render the image while the card itself stays unfiltered.
                card.style.setProperty('--app-card-bg-image', `url("${bgUrl}")`);
            }
            let badgeHtml = '';
            if (app.badge) {
                badgeHtml = `<div class="app-card-badge${app.badgeClass ? ' ' + app.badgeClass : ''}">${app.badge}</div>`;
            }
            const hasReadme = !!app.readme;
            const loggedIn = isUserLoggedIn();
            const launchBtnHtml = loggedIn
                ? `<a class="button app-card-launch-btn" href="${appUrl}" data-app-id="${app.id}">Launch Session</a>`
                : `<a class="button app-card-launch-btn" href="${appUrl}" data-app-id="${app.id}">Login to Launch</a>`;
            const hasExtras = hasReadme || app.videoUrl || (app.exampleSessions && Object.keys(app.exampleSessions).length > 0);
            const readmeHintHtml = hasExtras
                ? `<div class="app-card-readme-hint">Click for details</div>`
                : '';
            let tagsHtml = '';
            if (Array.isArray(app.tags) && app.tags.length > 0) {
                const tagItems = app.tags.map(t => {
                    const safe = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(t) : t;
                    return `<span class="app-card-tag">${safe}</span>`;
                }).join('');
                tagsHtml = `<div class="app-card-tags">${tagItems}</div>`;
            }
            // Icon: use image if hasIcon + iconUrl provided, otherwise text/emoji
            let iconContent;
            if (app.hasIcon && app.iconUrl) {
                const iconUrl = getAppAssetUrl(app.iconUrl);
                const safeName = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(app.name || '') : (app.name || '');
                iconContent = `<img src="${iconUrl}" alt="${safeName}" class="app-card-icon-img">`;
            } else {
                iconContent = app.icon || '';
            }
            card.innerHTML = `
                       <div class="app-card-content">
                           <div class="app-card-icon">${iconContent}</div>
                           <div class="app-card-body">
                          <h3>${app.name}</h3>
                          <p>${app.description}</p>
                          ${tagsHtml}
                          <div class="app-card-actions">
                              ${launchBtnHtml}
                              ${readmeHintHtml}
                          </div>
                           </div>
                       </div>
                      ${badgeHtml}
                  `;
            innerGrid.appendChild(card);
        });

        bodyEl.appendChild(innerGrid);
        groupEl.appendChild(headerEl);
        groupEl.appendChild(bodyEl);
        grid.appendChild(groupEl);

        const toggle = () => {
            collapsedCategories[categoryKey] = !collapsedCategories[categoryKey];
            const nowCollapsed = !!collapsedCategories[categoryKey];
            groupEl.classList.toggle('collapsed', nowCollapsed);
            bodyEl.style.display = nowCollapsed ? 'none' : '';
            headerEl.setAttribute('aria-expanded', String(!nowCollapsed));
            const icon = headerEl.querySelector('.app-category-toggle-icon');
            if (icon) icon.textContent = nowCollapsed ? '▶' : '▼';
        };
        headerEl.addEventListener('click', toggle);
        headerEl.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                toggle();
            }
        });
    });

    updateNoResultsMessage(filteredApps.length);
    ensureReadmeModal();
}
function getAppUrl(app) {
    if (!app) return '#';
    if (app.path) {
        // Ensure trailing slash
        return app.path.endsWith('/') ? app.path : app.path + '/';
    }
    return `/${app.id}/`;
}
function isUserLoggedIn() {
    // appState.userInfo is populated after loadUserSettings resolves
    return !!(typeof appState !== 'undefined' && appState && appState.userInfo);
}

function getAppAssetUrl(relativePath) {
    if (!relativePath) return '';
    // Strip any leading slash to avoid double-slashes
    const clean = String(relativePath).replace(/^\/+/, '');
    return `/appDirectory/${clean}`;
}


function filterApps(apps, query) {
    const q = (query || '').trim().toLowerCase();
    const required = Object.keys(tagFilters).filter(t => tagFilters[t] === 'require');
    const excluded = Object.keys(tagFilters).filter(t => tagFilters[t] === 'exclude');

    return apps.filter(app => {
        // Tag filters
        const appTags = Array.isArray(app.tags) ? app.tags.map(t => String(t)) : [];
        if (required.length > 0) {
            const hasAll = required.every(t => appTags.includes(t));
            if (!hasAll) return false;
        }
        if (excluded.length > 0) {
            const hasAny = excluded.some(t => appTags.includes(t));
            if (hasAny) return false;
        }

        // Text search
        if (!q) return true;
        const fields = [
            app.name,
            app.description,
            app.badge,
            app.id,
            app.type,
            app.readme,
            app.category,
            app.videoUrl,
            ...(Array.isArray(app.tags) ? app.tags : []),
            ...(app.exampleSessions && typeof app.exampleSessions === 'object'
                ? Object.keys(app.exampleSessions)
                : [])
        ];
        return fields.some(f => f && String(f).toLowerCase().includes(q));
    });
}

function updateNoResultsMessage(count) {
    const noResults = document.getElementById('app-search-no-results');
    if (!noResults) return;
    const hasActiveTagFilters = Object.values(tagFilters).some(v => v === 'require' || v === 'exclude');
    noResults.style.display = (count === 0 && (appSearchQuery.trim() || hasActiveTagFilters)) ? 'block' : 'none';
}

function setupAppSearch() {
    const input = document.getElementById('app-search-input');
    const clearBtn = document.getElementById('app-search-clear');
    // Render tag filters once on setup
    renderTagFilters();
    if (!input) return;
    const handleInput = () => {
        appSearchQuery = input.value;
        if (clearBtn) {
            clearBtn.style.display = appSearchQuery ? 'inline-block' : 'none';
        }
        renderAppGrid();
        // Re-attach card handlers since DOM was rebuilt
        setupAppCards();
    };
    input.addEventListener('input', handleInput);
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            input.value = '';
            handleInput();
        }
    });
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            input.value = '';
            input.focus();
            handleInput();
        });
    }
}


function ensureReadmeModal() {
    if (document.getElementById('app-readme-modal')) return;
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.id = 'app-readme-modal';
    modal.innerHTML = `
            <div class="modal-content modal-content-wide app-readme-modal-content">
                <span class="close" id="close-app-readme-modal">&times;</span>
                <div class="app-readme-header">
                    <div class="app-readme-icon" id="app-readme-icon"></div>
                    <div class="app-readme-title-block">
                        <h2 id="app-readme-title"></h2>
                        <p id="app-readme-description" class="app-readme-description"></p>
                    </div>
                </div>
                 <div class="app-readme-scroll-body" id="app-readme-scroll-body">
                     <div class="app-readme-body" id="app-readme-body"></div>
                     <div class="app-readme-video-section" id="app-readme-video-section" style="display:none;"></div>
                     <div class="app-readme-examples-section" id="app-readme-examples-section" style="display:none;"></div>
                 </div>
                <div class="button-group app-readme-actions">
                    <button class="button secondary" id="app-readme-close-btn">Close</button>
                     <a class="button" id="app-readme-launch-btn" href="#">Launch Session</a>
                </div>
            </div>
        `;
    document.body.appendChild(modal);

    const closeModal = () => {
        modal.style.display = 'none';
    };
    modal.querySelector('#close-app-readme-modal').addEventListener('click', closeModal);
    modal.querySelector('#app-readme-close-btn').addEventListener('click', closeModal);
    modal.addEventListener('click', (e) => {
        if (e.target === modal) closeModal();
    });
    modal.querySelector('#app-readme-launch-btn').addEventListener('click', (e) => {
        // Allow modifier-clicks (alt, ctrl, cmd, middle-click) to use default link behavior
        if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey || e.button === 1) {
            return;
        }
        closeModal();
    });
}

function showAppReadme(app) {
    ensureReadmeModal();
    const modal = document.getElementById('app-readme-modal');
    modal.dataset.currentAppId = app.id;
    const iconEl = document.getElementById('app-readme-icon');
    if (iconEl) {
        if (app.hasIcon && app.iconUrl) {
            const iconUrl = getAppAssetUrl(app.iconUrl);
            const safeName = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(app.name || '') : (app.name || '');
            iconEl.innerHTML = `<img src="${iconUrl}" alt="${safeName}" class="app-readme-icon-img">`;
        } else {
            iconEl.textContent = app.icon || '';
        }
    }
    // Apply background image to modal header if available
    // Apply background image to the entire modal content (not just header)
    const modalContent = modal.querySelector('.app-readme-modal-content');
    const headerEl = modal.querySelector('.app-readme-header');
    if (app.hasBackground && app.backgroundUrl) {
        const bgUrl = getAppAssetUrl(app.backgroundUrl);
        if (modalContent) {
            modalContent.classList.add('app-readme-modal-has-background');
            modalContent.style.setProperty('--app-readme-bg-image', `url("${bgUrl}")`);
        }
        if (headerEl) {
            // Keep the class for backwards compatibility / styling hooks,
            // but no longer set an inline background-image on the header.
            headerEl.classList.add('app-readme-header-has-background');
            headerEl.style.backgroundImage = '';
        }
    } else {
        if (modalContent) {
            modalContent.classList.remove('app-readme-modal-has-background');
            modalContent.style.removeProperty('--app-readme-bg-image');
        }
        if (headerEl) {
            headerEl.classList.remove('app-readme-header-has-background');
            headerEl.style.backgroundImage = '';
        }
    }
    document.getElementById('app-readme-title').textContent = app.name || '';
    document.getElementById('app-readme-description').textContent = app.description || '';
    const launchLink = document.getElementById('app-readme-launch-btn');
    if (launchLink) {
        launchLink.href = getAppUrl(app);
    }
    const body = document.getElementById('app-readme-body');
    const readmeContent = app.readme || '_No additional details available._';
    try {
        body.innerHTML = (typeof marked !== 'undefined' && marked.parse)
            ? marked.parse(readmeContent)
            : readmeContent;
    } catch (e) {
        console.error('[showAppReadme] Error rendering markdown:', e);
        body.textContent = readmeContent;
    }
    // ===== Video section =====
    const videoSection = document.getElementById('app-readme-video-section');
    if (videoSection) {
        if (app.videoUrl) {
            const safeUrl = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(app.videoUrl) : app.videoUrl;
            videoSection.innerHTML = `
                 <div class="app-readme-section-title">📹 Demo Video</div>
                 <div class="app-readme-video-wrapper">
                     <video class="app-readme-video" controls preload="metadata" playsinline>
                         <source src="${safeUrl}">
                         Your browser does not support the video tag.
                     </video>
                 </div>`;
            videoSection.style.display = '';
        } else {
            videoSection.innerHTML = '';
            videoSection.style.display = 'none';
        }
    }
    // ===== Example Sessions section =====
    const examplesSection = document.getElementById('app-readme-examples-section');
    if (examplesSection) {
        const examples = app.exampleSessions;
        if (examples && typeof examples === 'object' && Object.keys(examples).length > 0) {
            const linksHtml = Object.entries(examples).map(([label, url]) => {
                const safeLabel = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(label) : label;
                const safeUrl = HtmlUtils && HtmlUtils.escapeHtml ? HtmlUtils.escapeHtml(url) : url;
                return `<a class="app-readme-example-link" href="${safeUrl}" target="_blank" rel="noopener noreferrer">
                     <span class="app-readme-example-icon" aria-hidden="true">▶</span>${safeLabel}
                 </a>`;
            }).join('');
            examplesSection.innerHTML = `
                 <div class="app-readme-section-title">💡 Example Sessions</div>
                 <div class="app-readme-examples-list">${linksHtml}</div>`;
            examplesSection.style.display = '';
        } else {
            examplesSection.innerHTML = '';
            examplesSection.style.display = 'none';
        }
    }

    modal.style.display = 'block';
}


function setupAppCards() {
    const filteredApps = filterApps(appDirectory, appSearchQuery);
    filteredApps.forEach(app => {
        const element = document.getElementById(app.id);
        if (!element) return;


        element.addEventListener('click', function(e) {
            // Allow modifier-clicks (alt, ctrl, cmd, shift, middle-click) to use default link behavior
            if (e.ctrlKey || e.metaKey || e.shiftKey || e.altKey || e.button === 1) {
                return;
            }
            // If user clicked the launch button, let the link navigate normally
            if (e.target.closest('.app-card-launch-btn')) {
                return;
            }
            // For cards with extras (readme, video, examples), intercept plain clicks to show the readme modal
            const hasExtras = !!(app.readme || app.videoUrl || (app.exampleSessions && Object.keys(app.exampleSessions).length > 0));
            if (hasExtras) {
                e.preventDefault();
                showAppReadme(app);
            }
            // Otherwise, let the link navigate normally (do nothing)
        });
    });
}