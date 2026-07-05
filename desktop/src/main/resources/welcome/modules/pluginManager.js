// ===== Plugin Manager =====
    const pluginManagerState = {
        loadedPlugins: [],
        availableJars: []
    };
    let authPollingTimer = null;

    function setupPluginManagerModal() {
        const pluginManagerBtn = document.getElementById('plugin-manager-btn');
        const modal = document.getElementById('plugin-manager-modal');
        const closeBtn = document.getElementById('close-plugin-manager-modal');
        pluginManagerBtn?.addEventListener('click', () => {
            modal.style.display = 'block';
            pluginManagerRefreshLoaded();
            pluginManagerRefreshAuthChains();
        });
        closeBtn?.addEventListener('click', () => {
            modal.style.display = 'none';
        });
        window.addEventListener('click', function (event) {
            if (event.target === modal) modal.style.display = 'none';
        });
        document.querySelectorAll('[data-plugin-tab]').forEach(button => {
            button.addEventListener('click', () => {
                const tabId = button.getAttribute('data-plugin-tab');
                switchPluginTab(tabId);
            });
        });
        document.getElementById('refresh-loaded-plugins')?.addEventListener('click', pluginManagerRefreshLoaded);
        document.getElementById('scan-plugin-directory')?.addEventListener('click', pluginManagerScanDirectory);
        document.getElementById('load-all-plugins')?.addEventListener('click', pluginManagerLoadDirectory);
        document.getElementById('upload-plugin-btn')?.addEventListener('click', pluginManagerUpload);
        document.getElementById('load-plugin-by-path-btn')?.addEventListener('click', pluginManagerLoadByPath);
        document.getElementById('refresh-auth-chains')?.addEventListener('click', pluginManagerRefreshAuthChains);
    }

    function switchPluginTab(tabId) {
        document.querySelectorAll('#plugin-manager-modal .tab-content').forEach(c => c.classList.remove('active'));
        document.querySelectorAll('#plugin-manager-modal [data-plugin-tab]').forEach(b => b.classList.remove('active'));
        const content = document.getElementById(`${tabId}-tab`);
        if (content) content.classList.add('active');
        const btn = document.querySelector(`[data-plugin-tab="${tabId}"]`);
        if (btn) btn.classList.add('active');
    }

    function showPluginMessage(message, type = 'info') {
        const el = document.getElementById('plugin-manager-message');
        if (!el) return;
        el.textContent = message;
        el.className = '';
        el.style.display = 'block';
        el.style.padding = '10px 14px';
        el.style.borderRadius = '6px';
        el.style.marginBottom = '14px';
        el.style.fontWeight = '500';
        if (type === 'success') {
            el.style.background = '#d4edda';
            el.style.color = '#155724';
            el.style.border = '1px solid #c3e6cb';
        } else if (type === 'error') {
            el.style.background = '#f8d7da';
            el.style.color = '#721c24';
            el.style.border = '1px solid #f5c6cb';
        } else {
            el.style.background = '#d1ecf1';
            el.style.color = '#0c5460';
            el.style.border = '1px solid #bee5eb';
        }
        clearTimeout(el._hideTimer);
        el._hideTimer = setTimeout(() => { el.style.display = 'none'; }, 6000);
    }

    function pluginManagerJsonResponse(r) {
        if (!r.ok) {
            return r.text().then(text => {
                try { return JSON.parse(text); }
                catch (e) { throw new Error(`Server returned ${r.status}: ${text.substring(0, 200)}`); }
            });
        }
        return r.json();
    }

    function pluginManagerRefreshLoaded() {
        const container = document.getElementById('loaded-plugins-content');
        if (!container) return;
        container.innerHTML = '<em>Loading…</em>';
        fetch('/pluginManager/?action=list', { headers: { 'Accept': 'application/json' } })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                pluginManagerState.loadedPlugins = data || [];
                if (!data || data.length === 0) {
                    container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No plugins currently loaded.</p>';
                    return;
                }
                let html = `
                    <table style="width:100%; border-collapse:collapse;">
                        <thead>
                            <tr style="background:#f0f0f0;">
                                <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">JAR File</th>
                                <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">Plugins</th>
                                <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                `;
                data.forEach(entry => {
                    const pluginList = (entry.plugins || [])
                        .map(p => `<div><strong>${escapeHtml(p.name)}</strong> <small style="color:#888;">${escapeHtml(p.class)}</small></div>`)
                        .join('') || '<em style="color:#aaa;">none</em>';
                    const jarName = entry.jar ? entry.jar.split(/[\\/]/).pop() : entry.jar;
                    html += `
                        <tr style="border-bottom:1px solid #eee;">
                            <td style="padding:10px 12px; font-family:monospace; font-size:0.9em;" title="${escapeHtml(entry.jar || '')}">
                                ${escapeHtml(jarName || '')}
                            </td>
                            <td style="padding:10px 12px;">${pluginList}</td>
                            <td style="padding:10px 12px; text-align:center;">
                                <button class="button secondary" style="font-size:0.85em; padding:5px 12px; margin:2px;"
                                    data-jar-path="${escapeHtml(entry.jar || '')}" data-action="unload">
                                    Unload
                                </button>
                                <button class="button secondary" style="font-size:0.85em; padding:5px 12px; margin:2px; background:#dc3545; color:#fff;"
                                    data-jar-path="${escapeHtml(entry.jar || '')}" data-action="delete">
                                    Delete
                                </button>
                            </td>
                        </tr>
                    `;
                });
                html += '</tbody></table>';
                container.innerHTML = html;
                container.querySelectorAll('button[data-action="unload"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerUnload(btn.getAttribute('data-jar-path')));
                });
                container.querySelectorAll('button[data-action="delete"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerDelete(btn.getAttribute('data-jar-path')));
                });
            })
            .catch(e => {
                container.innerHTML = `<p style="color:#c0392b;">Error loading plugins: ${escapeHtml(e.message)}</p>`;
            });
    }

    function pluginManagerScanDirectory() {
        const container = document.getElementById('available-jars-content');
        if (!container) return;
        container.innerHTML = '<em>Scanning…</em>';
        fetch('/pluginManager/?action=scan', { headers: { 'Accept': 'application/json' } })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                pluginManagerState.availableJars = data || [];
                if (!data || data.length === 0) {
                    container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No JAR files found in plugin directory.</p>';
                    return;
                }
                let html = `
                    <table style="width:100%; border-collapse:collapse;">
                        <thead>
                            <tr style="background:#f0f0f0;">
                                <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">File</th>
                                <th style="text-align:right; padding:8px 12px; border-bottom:2px solid #ddd;">Size</th>
                                <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Status</th>
                                <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                `;
                data.forEach(entry => {
                    const sizeKb = (entry.size / 1024).toFixed(1);
                    const statusBadge = entry.loaded
                        ? '<span style="background:#d4edda;color:#155724;padding:2px 8px;border-radius:10px;font-size:0.8em;font-weight:600;">Loaded</span>'
                        : '<span style="background:#f8d7da;color:#721c24;padding:2px 8px;border-radius:10px;font-size:0.8em;font-weight:600;">Not Loaded</span>';
                    const loadBtn = !entry.loaded
                        ? `<button class="button" style="font-size:0.85em;padding:5px 12px;"
                                data-jar-name="${escapeHtml(entry.name || '')}" data-action="load">Load</button>`
                        : '';
                    const unloadBtn = entry.loaded
                        ? `<button class="button secondary" style="font-size:0.85em;padding:5px 12px;margin:2px;"
                                data-jar-path="${escapeHtml(entry.path || '')}" data-action="unload">Unload</button>`
                        : '';
                    const deleteBtn = `<button class="button secondary" style="font-size:0.85em;padding:5px 12px;margin:2px;background:#dc3545;color:#fff;"
                            data-jar-path="${escapeHtml(entry.path || '')}" data-action="delete">Delete</button>`;
                    html += `
                        <tr style="border-bottom:1px solid #eee;">
                            <td style="padding:10px 12px; font-family:monospace; font-size:0.9em;">${escapeHtml(entry.name || '')}</td>
                            <td style="padding:10px 12px; text-align:right; color:#666;">${sizeKb} KB</td>
                            <td style="padding:10px 12px; text-align:center;">${statusBadge}</td>
                            <td style="padding:10px 12px; text-align:center;">${loadBtn}${unloadBtn}${deleteBtn}</td>
                        </tr>
                    `;
                });
                html += '</tbody></table>';
                container.innerHTML = html;
                container.querySelectorAll('button[data-action="load"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerLoadJar(btn.getAttribute('data-jar-name')));
                });
                container.querySelectorAll('button[data-action="unload"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerUnload(btn.getAttribute('data-jar-path')));
                });
                container.querySelectorAll('button[data-action="delete"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerDelete(btn.getAttribute('data-jar-path')));
                });
            })
            .catch(e => {
                container.innerHTML = `<p style="color:#c0392b;">Error scanning directory: ${escapeHtml(e.message)}</p>`;
            });
    }

    function pluginManagerLoadJar(jarName) {
        if (!jarName) return;
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'load', jar: jarName }),
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success) {
                    showPluginMessage(`Loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`, 'success');
                    pluginManagerRefreshLoaded();
                    pluginManagerScanDirectory();
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    function pluginManagerUnload(jarPath) {
        if (!jarPath) return;
        const jarName = jarPath.split(/[\\/]/).pop();
        if (!confirm(`Unload plugin "${jarName}"? This may affect running sessions.`)) return;
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'unload', jar: jarPath }),
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success) {
                    showPluginMessage(`Plugin unloaded: ${jarName}`, 'success');
                    pluginManagerRefreshLoaded();
                    pluginManagerScanDirectory();
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    function pluginManagerDelete(jarPath) {
        if (!jarPath) return;
        const jarName = jarPath.split(/[\\/]/).pop();
        if (!confirm(`Delete plugin "${jarName}"?\nThis will permanently remove the file from disk. If loaded, it will be unloaded first.`)) return;
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'delete', jar: jarPath }),
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success) {
                    showPluginMessage(`Plugin deleted: ${jarName}`, 'success');
                    pluginManagerRefreshLoaded();
                    pluginManagerScanDirectory();
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    function pluginManagerLoadDirectory() {
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'loadDirectory' }),
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success) {
                    showPluginMessage(`Processed ${data.jarsProcessed} JAR(s) from ${data.directory}`, 'success');
                    pluginManagerRefreshLoaded();
                    pluginManagerScanDirectory();
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    function pluginManagerUpload() {
        const fileInput = document.getElementById('plugin-jar-file');
        const autoLoad = document.getElementById('plugin-auto-load')?.checked || false;
        const progressEl = document.getElementById('upload-progress');
        const uploadBtn = document.getElementById('upload-plugin-btn');
        if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
            showPluginMessage('Please select a JAR file to upload.', 'error');
            return;
        }
        const file = fileInput.files[0];
        if (!file.name.endsWith('.jar')) {
            showPluginMessage('Only JAR files are supported.', 'error');
            return;
        }
        const formData = new FormData();
        formData.append('action', 'upload');
        formData.append('jarFile', file);
        formData.append('autoLoad', autoLoad ? 'true' : 'false');
        if (progressEl) progressEl.style.display = 'block';
        if (uploadBtn) uploadBtn.disabled = true;
        fetch('/pluginManager/', {
            method: 'POST',
            body: formData,
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (progressEl) progressEl.style.display = 'none';
                if (uploadBtn) uploadBtn.disabled = false;
                if (data.success) {
                    let msg = `Uploaded: ${data.file}`;
                    if (data.autoLoaded) msg += ` — loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`;
                    showPluginMessage(msg, 'success');
                    fileInput.value = '';
                    pluginManagerRefreshLoaded();
                    pluginManagerScanDirectory();
                    switchPluginTab('loaded-plugins');
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => {
                if (progressEl) progressEl.style.display = 'none';
                if (uploadBtn) uploadBtn.disabled = false;
                showPluginMessage('Upload failed: ' + e.message, 'error');
            });
    }

    function pluginManagerLoadByPath() {
        const jarPath = document.getElementById('plugin-jar-path')?.value.trim();
        const entryPoint = document.getElementById('plugin-entry-point')?.value.trim();
        if (!jarPath) {
            showPluginMessage('Please enter a JAR path.', 'error');
            return;
        }
        const params = new URLSearchParams({ action: 'load', jar: jarPath });
        if (entryPoint) params.append('entryPoint', entryPoint);
        fetch('/pluginManager/', {
            method: 'POST',
            body: params,
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success) {
                    showPluginMessage(`Loaded ${data.pluginsLoaded} plugin(s): ${(data.plugins || []).join(', ')}`, 'success');
                    document.getElementById('plugin-jar-path').value = '';
                    document.getElementById('plugin-entry-point').value = '';
                    pluginManagerRefreshLoaded();
                    switchPluginTab('loaded-plugins');
                } else {
                    showPluginMessage('Error: ' + data.error, 'error');
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    // ===== Plugin Manager: Authorization Chains =====
    function pluginManagerRefreshAuthChains() {
        const container = document.getElementById('auth-chains-content');
        if (!container) return;
        container.innerHTML = '<em>Loading…</em>';
        fetch('/pluginManager/?action=authChains', { headers: { 'Accept': 'application/json' } })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                if (!data || data.length === 0) {
                    container.innerHTML = '<p style="color:#888; text-align:center; padding:20px;">No authorization chains registered. Plugins can register authorization flows when loaded.</p>';
                    return;
                }
                let html = `
                    <table style="width:100%; border-collapse:collapse;">
                        <thead>
                            <tr style="background:#f0f0f0;">
                                <th style="text-align:left; padding:8px 12px; border-bottom:2px solid #ddd;">Chain Name</th>
                                <th style="text-align:center; padding:8px 12px; border-bottom:2px solid #ddd;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                `;
                data.forEach(entry => {
                    html += `
                        <tr style="border-bottom:1px solid #eee;">
                            <td style="padding:10px 12px;">
                                <strong>🔐 ${escapeHtml(entry.name)}</strong>
                            </td>
                            <td style="padding:10px 12px; text-align:center;">
                                <button class="button" style="font-size:0.85em; padding:5px 14px;"
                                    data-chain-name="${escapeHtml(entry.name)}" data-action="start-auth">
                                    Start Authorization
                                </button>
                            </td>
                        </tr>
                    `;
                });
                html += '</tbody></table>';
                container.innerHTML = html;
                container.querySelectorAll('button[data-action="start-auth"]').forEach(btn => {
                    btn.addEventListener('click', () => pluginManagerStartAuth(btn.getAttribute('data-chain-name')));
                });
            })
            .catch(e => {
                container.innerHTML = `<p style="color:#c0392b;">Error loading authorization chains: ${escapeHtml(e.message)}</p>`;
            });
    }

    function pluginManagerStartAuth(chainName) {
        if (!chainName) return;
        showPluginMessage(`Starting authorization for "${chainName}"...`, 'info');
        fetch('/pluginManager/', {
            method: 'POST',
            body: new URLSearchParams({ action: 'startAuth', chain: chainName }),
            headers: { 'Accept': 'application/json' }
        })
            .then(pluginManagerJsonResponse)
            .then(data => {
                if (data.success && data.sessionId && data.status === 'IN_PROGRESS') {
                    pluginManagerShowAuthProgress(data.sessionId, chainName, data.currentStep, data.totalSteps);
                } else if (data.success && (data.status === 'completed' || data.status === 'COMPLETED')) {
                    showPluginMessage(`Authorization completed for "${chainName}": No steps required.`, 'success');
                    pluginManagerHideAuthProgress();
                } else if (data.status === 'FAILED') {
                    showPluginMessage(`Authorization failed for "${chainName}": ${data.failureReason || 'Unknown reason'}`, 'error');
                    pluginManagerHideAuthProgress();
                } else {
                    showPluginMessage('Error: ' + (data.error || 'Unknown error'), 'error');
                    pluginManagerHideAuthProgress();
                }
            })
            .catch(e => showPluginMessage('Request failed: ' + e.message, 'error'));
    }

    function pluginManagerShowAuthProgress(sessionId, chainName, currentStep, totalSteps) {
        const panel = document.getElementById('auth-status-panel');
        const progressFill = document.getElementById('auth-progress-fill');
        const statusText = document.getElementById('auth-status-text');
        const actionArea = document.getElementById('auth-action-area');
        if (!panel) return;
        panel.style.display = 'block';
        const pct = totalSteps > 0 ? Math.round((currentStep / totalSteps) * 100) : 0;
        progressFill.style.width = pct + '%';
        statusText.textContent = `Authorization "${chainName}" in progress — Step ${currentStep} of ${totalSteps}`;
        actionArea.innerHTML = `
            <button class="button" id="auth-open-flow-btn" style="margin-right:8px;">Open Authorization Page</button>
            <button class="button secondary" id="auth-check-status-btn" style="margin-right:8px;">Check Status</button>
            <button class="button secondary" id="auth-cancel-btn">Close</button>
        `;
        document.getElementById('auth-open-flow-btn')?.addEventListener('click', () => {
            window.open(`/pluginManager?action=authStep&sessionId=${encodeURIComponent(sessionId)}`, '_blank',
                'width=700,height=600,scrollbars=yes,resizable=yes');
        });
        document.getElementById('auth-check-status-btn')?.addEventListener('click', () => {
            pluginManagerCheckAuthStatus(sessionId, chainName);
        });
        document.getElementById('auth-cancel-btn')?.addEventListener('click', () => {
            pluginManagerHideAuthProgress();
            pluginManagerStopAuthPolling();
        });
        pluginManagerStartAuthPolling(sessionId, chainName);
    }

    function pluginManagerHideAuthProgress() {
        const panel = document.getElementById('auth-status-panel');
        if (panel) panel.style.display = 'none';
        pluginManagerStopAuthPolling();
    }

    function pluginManagerStartAuthPolling(sessionId, chainName) {
        pluginManagerStopAuthPolling();
        authPollingTimer = setInterval(() => {
            pluginManagerCheckAuthStatus(sessionId, chainName, true);
        }, 3000);
    }

    function pluginManagerStopAuthPolling() {
        if (authPollingTimer) {
            clearInterval(authPollingTimer);
            authPollingTimer = null;
        }
    }

function pluginManagerCheckAuthStatus(sessionId, chainName, silent) {
        fetch(`/pluginManager/?action=authStatus&sessionId=${encodeURIComponent(sessionId)}`, {
            headers: { 'Accept': 'application/json' }
        })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                const progressFill = document.getElementById('auth-progress-fill');
                const statusText = document.getElementById('auth-status-text');
                if (data.isComplete) {
                    pluginManagerStopAuthPolling();
                    if (data.status === 'COMPLETED') {
                        if (progressFill) progressFill.style.width = '100%';
                        if (statusText) statusText.textContent = `Authorization "${chainName}" completed successfully!`;
                        showPluginMessage(`Authorization "${chainName}" completed successfully!`, 'success');
                        setTimeout(() => pluginManagerHideAuthProgress(), 4000);
                        pluginManagerRefreshLoaded();
                    } else {
                        if (progressFill) {
                            progressFill.style.width = '100%';
                            progressFill.style.background = '#dc3545';
                        }
                        if (statusText) statusText.textContent = `Authorization "${chainName}" failed: ${data.failureReason || 'Unknown reason'}`;
                        showPluginMessage(`Authorization "${chainName}" failed: ${data.failureReason || 'Unknown reason'}`, 'error');
                    }
                } else {
                    const pct = data.totalSteps > 0 ? Math.round((data.currentStep / data.totalSteps) * 100) : 0;
                    if (progressFill) {
                        progressFill.style.width = pct + '%';
                        progressFill.style.background = '#007bff';
                    }
                    if (statusText) statusText.textContent = `Authorization "${chainName}" in progress — Step ${data.currentStep} of ${data.totalSteps}`;
                    if (!silent) {
                        showPluginMessage(`Authorization in progress: Step ${data.currentStep} of ${data.totalSteps}`, 'info');
                    }
                }
            })
            .catch(e => {
                if (!silent) {
                    showPluginMessage('Error checking auth status: ' + e.message, 'error');
                }
                pluginManagerStopAuthPolling();
            });
    }