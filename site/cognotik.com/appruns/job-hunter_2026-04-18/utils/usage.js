/**
 * Usage tracking utilities
 */
(function() {
    'use strict';

    /**
     * Fetch usage data for a session
     * @param {string} sessionId - Session ID
     * @returns {Promise<Object|null>} Usage data or null
     */
    async function fetchUsageData(sessionId) {
        if (!sessionId) return null;
        
        try {
            const url = `/proxy/usage?sessionId=${encodeURIComponent(sessionId)}&format=json`;
            const resp = await fetch(url, {
                headers: { 'Accept': 'application/json' }
            });
            
            if (!resp.ok) {
                if (resp.status === 404) return null;
                throw new Error(`HTTP ${resp.status}`);
            }
            
            return await resp.json();
        } catch (e) {
            console.warn('Failed to fetch usage data:', e);
            return null;
        }
    }

    /**
     * Format token count for display
     * @param {number} n - Token count
     * @returns {string} Formatted count
     */
    function formatTokenCount(n) {
        if (n === null || n === undefined || isNaN(n)) return '—';
        if (n >= 1000000) return (n / 1000000).toFixed(2) + 'M';
        if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
        return n.toLocaleString();
    }

    /**
     * Format cost for display
     * @param {number} cost - Cost value
     * @returns {string} Formatted cost
     */
    function formatCost(cost) {
        if (cost === null || cost === undefined || isNaN(cost)) return '—';
        if (cost === 0) return '$0.00';
        if (cost < 0.001) return '< $0.001';
        if (cost < 0.01) return '$' + cost.toFixed(4);
        return '$' + cost.toFixed(4);
    }

    /**
     * Aggregate usage data from multiple sessions
     * @param {Array<string>} sessionIds - Session IDs to aggregate
     * @returns {Promise<Object>} Aggregated usage data
     */
    async function aggregateUsage(sessionIds) {
        const allModels = {};
        const sessionUsageMap = {};
        let totalPrompt = 0;
        let totalCompletion = 0;
        let totalCost = 0;
        
        // Fetch all usage data in parallel
        const results = await Promise.all(
            sessionIds.map(async (sid) => {
                const data = await fetchUsageData(sid);
                return { sessionId: sid, data };
            })
        );
        
        // Aggregate the results
        results.forEach(({ sessionId, data }) => {
            if (!data) return;
            
            sessionUsageMap[sessionId] = data;
            
            if (data.models) {
                data.models.forEach(model => {
                    const key = model.model || 'unknown';
                    if (!allModels[key]) {
                        allModels[key] = {
                            model: key,
                            prompt_tokens: 0,
                            completion_tokens: 0,
                            cost: 0
                        };
                    }
                    allModels[key].prompt_tokens += (model.prompt_tokens || 0);
                    allModels[key].completion_tokens += (model.completion_tokens || 0);
                    allModels[key].cost += (model.cost || 0);
                });
            }
            
            if (data.totals) {
                totalPrompt += (data.totals.prompt_tokens || 0);
                totalCompletion += (data.totals.completion_tokens || 0);
                totalCost += (data.totals.cost || 0);
            }
        });
        
        return {
            models: Object.values(allModels),
            totals: {
                prompt_tokens: totalPrompt,
                completion_tokens: totalCompletion,
                cost: totalCost
            },
            sessionUsageMap
        };
    }

    /**
     * Render usage summary
     * @param {Object} totals - Usage totals
     * @param {Object} elements - DOM elements for display
     */
    function renderUsageSummary(totals, elements) {
        if (totals) {
            if (elements.prompt) elements.prompt.textContent = formatTokenCount(totals.prompt_tokens || 0);
            if (elements.completion) elements.completion.textContent = formatTokenCount(totals.completion_tokens || 0);
            if (elements.total) elements.total.textContent = formatTokenCount((totals.prompt_tokens || 0) + (totals.completion_tokens || 0));
            if (elements.cost) elements.cost.textContent = formatCost(totals.cost || 0);
        } else {
            if (elements.prompt) elements.prompt.textContent = '—';
            if (elements.completion) elements.completion.textContent = '—';
            if (elements.total) elements.total.textContent = '—';
            if (elements.cost) elements.cost.textContent = '—';
        }
    }

    /**
     * Create usage table HTML
     * @param {Array} models - Model usage data
     * @param {Object} totals - Usage totals
     * @returns {string} HTML table
     */
    function createUsageTableHtml(models, totals) {
        if (!models || models.length === 0) {
            return '<p class="placeholder">No usage data available yet.</p>';
        }
        
        let html = '<table class="usage-table">';
        html += '<thead><tr>';
        html += '<th>Model</th>';
        html += '<th>Prompt Tokens</th>';
        html += '<th>Completion Tokens</th>';
        html += '<th>Total Tokens</th>';
        html += '<th>Cost</th>';
        html += '</tr></thead>';
        html += '<tbody>';
        
        // Sort by cost descending
        const sortedModels = [...models].sort((a, b) => (b.cost || 0) - (a.cost || 0));
        
        sortedModels.forEach(model => {
            const totalTokens = (model.prompt_tokens || 0) + (model.completion_tokens || 0);
            html += '<tr>';
            html += `<td class="usage-model-cell">${window.UIUtils.escapeHtml(model.model || 'Unknown')}</td>`;
            html += `<td class="usage-number-cell">${formatTokenCount(model.prompt_tokens)}</td>`;
            html += `<td class="usage-number-cell">${formatTokenCount(model.completion_tokens)}</td>`;
            html += `<td class="usage-number-cell">${formatTokenCount(totalTokens)}</td>`;
            html += `<td class="usage-cost-cell">${formatCost(model.cost)}</td>`;
            html += '</tr>';
        });
        
        // Totals row
        if (totals) {
            const totalTokens = (totals.prompt_tokens || 0) + (totals.completion_tokens || 0);
            html += '<tr class="usage-totals-row">';
            html += '<td><strong>Total</strong></td>';
            html += `<td><strong>${formatTokenCount(totals.prompt_tokens)}</strong></td>`;
            html += `<td><strong>${formatTokenCount(totals.completion_tokens)}</strong></td>`;
            html += `<td><strong>${formatTokenCount(totalTokens)}</strong></td>`;
            html += `<td><strong>${formatCost(totals.cost)}</strong></td>`;
            html += '</tr>';
        }
        
        html += '</tbody></table>';
        return html;
    }

    // Export functions
    window.UsageUtils = {
        fetchUsageData,
        formatTokenCount,
        formatCost,
        aggregateUsage,
        renderUsageSummary,
        createUsageTableHtml
    };
})();