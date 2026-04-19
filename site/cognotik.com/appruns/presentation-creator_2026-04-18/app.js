/* ── Presentation Creator — UI Logic ──────────────────────── */

import { parseSessionUrl, getProxyUrl, getAppRoot } from './utils/session.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { readFile, writeFile, fileExists } from './utils/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller, fetchDocopsStatus } from './utils/docops.js';
import { fetchUsageData, aggregateUsage, renderUsageSummary, formatTokenCount, formatCost } from './utils/usage.js';
import { showToast, setStatus as setUIStatus, setBadge } from './utils/ui.js';

// ── Session context ─────────────────────────────────────────
let basePath = '';
let sessionId = '';
let appId = '';
let statusPoller = null;
let trackedSessions = [];

// Try to parse session from URL
try {
  const session = parseSessionUrl();
  basePath = session.basePath;
  sessionId = session.sessionId;
  appId = session.appId;
} catch (e) {
  console.warn('Could not parse session URL, using fallback API mode');
}

// ── DOM refs ────────────────────────────────────────────────
const notesEditor       = document.getElementById('notesEditor');
const updateNotesEditor = document.getElementById('updateNotesEditor');
const draftBtn          = document.getElementById('draftBtn');
const updateBtn         = document.getElementById('updateBtn');
const previewFrame      = document.getElementById('previewFrame');
const previewEmpty      = document.getElementById('previewEmpty');
const loadingOverlay    = document.getElementById('loadingOverlay');
const loadingMessage    = document.getElementById('loadingMessage');
const loadingSessionLink = document.getElementById('loadingSessionLink');
const statusBar         = document.getElementById('statusBar');
const toastContainer    = document.getElementById('toastContainer');
const refreshPreview    = document.getElementById('refreshPreview');
const openExternal      = document.getElementById('openExternal');
const downloadBtn       = document.getElementById('downloadBtn');
const divider           = document.getElementById('divider');
const editorPanel       = document.querySelector('.editor-panel');
const previewPanel      = document.querySelector('.preview-panel');

// Model selects
const smartModelSelect  = document.getElementById('smartModelSelect');
const fastModelSelect   = document.getElementById('fastModelSelect');
const imageModelSelect  = document.getElementById('imageModelSelect');

// Session monitoring
const sessionIdDisplay  = document.getElementById('sessionIdDisplay');
const sessionLinks      = document.getElementById('sessionLinks');
const taskList          = document.getElementById('taskList');

// Usage stats
const refreshUsageBtn   = document.getElementById('refreshUsageBtn');

// ── Tab navigation ──────────────────────────────────────────
document.querySelectorAll('.nav-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const tab = btn.dataset.tab;

    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');

    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
    document.getElementById(`tab-${tab}`).classList.add('active');
  });
});

// ── Resizable divider ───────────────────────────────────────
let isDragging = false;
let startX = 0;
let startEditorWidth = 0;

divider.addEventListener('mousedown', e => {
  isDragging = true;
  startX = e.clientX;
  startEditorWidth = editorPanel.getBoundingClientRect().width;
  divider.classList.add('dragging');
  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
});

document.addEventListener('mousemove', e => {
  if (!isDragging) return;
  const delta = e.clientX - startX;
  const mainWidth = editorPanel.parentElement.getBoundingClientRect().width;
  const newEditorWidth = Math.min(
    Math.max(startEditorWidth + delta, 280),
    mainWidth - 280 - 5
  );
  editorPanel.style.flex = 'none';
  editorPanel.style.width = `${newEditorWidth}px`;
});

document.addEventListener('mouseup', () => {
  if (!isDragging) return;
  isDragging = false;
  divider.classList.remove('dragging');
  document.body.style.cursor = '';
  document.body.style.userSelect = '';
});

// ── Status bar helpers ──────────────────────────────────────
function setStatus(type, message) {
  const icons = {
    idle: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
             <circle cx="12" cy="12" r="10"/>
             <line x1="12" y1="8" x2="12" y2="12"/>
             <line x1="12" y1="16" x2="12.01" y2="16"/>
           </svg>`,
    working: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
              </svg>`,
    success: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>`,
    error: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>`,
  };
  statusBar.innerHTML = `<div class="status-${type}">${icons[type]}<span>${message}</span></div>`;
}

// ── Toast notifications (local wrapper) ─────────────────────
function toast(message, type = 'info', duration = 4000) {
  const icons = {
    success: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>`,
    error: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </svg>`,
    info: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
             <circle cx="12" cy="12" r="10"/>
             <line x1="12" y1="8" x2="12" y2="12"/>
             <line x1="12" y1="16" x2="12.01" y2="16"/>
           </svg>`,
  };

  const toastEl = document.createElement('div');
  toastEl.className = `toast ${type}`;
  toastEl.innerHTML = `${icons[type]}<span>${message}</span>`;
  toastContainer.appendChild(toastEl);

  setTimeout(() => {
    toastEl.style.opacity = '0';
    toastEl.style.transform = 'translateX(20px)';
    toastEl.style.transition = '0.2s ease';
    setTimeout(() => toastEl.remove(), 200);
  }, duration);
}

// ── Loading state ───────────────────────────────────────────
function setLoading(active, message = 'Working...', sessionUrl = null) {
  loadingMessage.textContent = message;
  loadingOverlay.classList.toggle('hidden', !active);
  draftBtn.disabled  = active;
  updateBtn.disabled = active;
  
  if (sessionUrl) {
    loadingSessionLink.href = sessionUrl;
    loadingSessionLink.classList.remove('hidden');
  } else {
    loadingSessionLink.classList.add('hidden');
  }
}

// ── Preview helpers ─────────────────────────────────────────
function showPreview() {
   previewEmpty.classList.add('hidden');
   previewFrame.classList.remove('hidden');
   // Link directly to the file
   const fileUrl = `${basePath}/presentation.html`;
   previewFrame.src = fileUrl;
}

function clearPreview() {
  previewFrame.classList.add('hidden');
  previewEmpty.classList.remove('hidden');
}

// ── File I/O ────────────────────────────────────────────────
async function readFileContent(path) {
  if (basePath) {
    return await readFile(basePath, path);
  }
  // Fallback to direct API
  const res = await fetch(`/api/files/${encodeURIComponent(path)}`);
  if (!res.ok) throw new Error(`Failed to read ${path}: ${res.statusText}`);
  return res.text();
}

async function writeFileContent(path, content) {
  if (basePath) {
    return await writeFile(basePath, path, content);
  }
  // Fallback to direct API
  const res = await fetch(`/api/files/${encodeURIComponent(path)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'text/plain' },
    body: content,
  });
  if (!res.ok) throw new Error(`Failed to write ${path}: ${res.statusText}`);
}

// ── Model Selection ─────────────────────────────────────────
async function initModelSelection() {
  try {
    const availableModels = await loadApiProviders();
    
    // Load saved selections
    const savedSelections = loadModelSelections('presentationCreator', [
      'smartModel', 'fastModel', 'imageModel'
    ]);
    
    // Populate dropdowns
    populateModelDropdowns(
      availableModels,
      [smartModelSelect, fastModelSelect],
      savedSelections
    );
    
    // Add "None" option for image model and populate
    const imageModels = { ...availableModels };
    populateModelDropdowns(
      imageModels,
      [imageModelSelect],
      savedSelections
    );
    
    // Add change listeners to save selections
    [smartModelSelect, fastModelSelect, imageModelSelect].forEach(select => {
      select.addEventListener('change', () => {
        saveModelSelections('presentationCreator', {
          smartModel: smartModelSelect.value,
          fastModel: fastModelSelect.value,
          imageModel: imageModelSelect.value
        });
      });
    });
    
  } catch (err) {
    console.error('Failed to load models:', err);
    toast('Failed to load AI models', 'error');
  }
}

function getSelectedModels() {
  return {
    smartModel: smartModelSelect.value || 'gpt-4o',
    fastModel: fastModelSelect.value || 'gpt-4o-mini',
    imageModel: imageModelSelect.value || undefined
  };
}

// ── Session Monitoring ──────────────────────────────────────
function updateSessionDisplay() {
  if (sessionId) {
    sessionIdDisplay.textContent = sessionId.substring(0, 12) + '...';
    sessionIdDisplay.title = sessionId;
  } else {
    sessionIdDisplay.textContent = 'No active session';
  }
}

function updateTaskList(tasks) {
  if (!tasks || Object.keys(tasks).length === 0) {
    taskList.innerHTML = '<div class="task-empty">No active tasks</div>';
    return;
  }
  
  const taskHtml = Object.entries(tasks).map(([target, info]) => {
    const statusClass = info.status === 'COMPLETED' ? 'success' :
                       info.status === 'RUNNING' ? 'running' :
                       info.status === 'ERROR' || info.status === 'FAILED' ? 'error' : 'pending';
    
    const statusIcon = {
      success: '✓',
      running: '◌',
      error: '✗',
      pending: '○'
    }[statusClass];
    
    const sessionLink = info.sessionId ? 
      `<a href="${getProxyUrl(info.sessionId)}" target="_blank" class="task-link">View</a>` : '';
    
    return `
      <div class="task-item ${statusClass}">
        <span class="task-status">${statusIcon}</span>
        <span class="task-name" title="${target}">${target}</span>
        ${sessionLink}
      </div>
    `;
  }).join('');
  
  taskList.innerHTML = taskHtml;
}

function addSessionLink(taskId, label = 'Monitor Session') {
  if (!taskId) return;
  
  trackedSessions.push(taskId);
  
  const link = document.createElement('a');
  link.href = getProxyUrl(taskId);
  link.target = '_blank';
  link.className = 'session-monitor-link';
  link.innerHTML = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
      <polyline points="15 3 21 3 21 9"/>
      <line x1="10" y1="14" x2="21" y2="3"/>
    </svg>
    ${label}
  `;
  sessionLinks.appendChild(link);
}

function clearSessionLinks() {
  sessionLinks.innerHTML = '';
}

// ── Status Polling ──────────────────────────────────────────
function startStatusPolling() {
  if (!basePath || statusPoller) return;
  
  statusPoller = createStatusPoller(basePath, (target, taskInfo) => {
    // Update task list
    fetchDocopsStatus(basePath).then(status => {
      if (status && status.tasks) {
        updateTaskList(status.tasks);
      }
    });
    
    // Track session IDs
    if (taskInfo.sessionId && !trackedSessions.includes(taskInfo.sessionId)) {
      trackedSessions.push(taskInfo.sessionId);
    }
  });
  
  statusPoller.start();
}

function stopStatusPolling() {
  if (statusPoller) {
    statusPoller.stop();
    statusPoller = null;
  }
}

// ── Usage Statistics ────────────────────────────────────────
async function refreshUsageStats() {
  if (trackedSessions.length === 0) {
    document.getElementById('promptTokens').textContent = '—';
    document.getElementById('completionTokens').textContent = '—';
    document.getElementById('totalTokens').textContent = '—';
    document.getElementById('totalCost').textContent = '—';
    return;
  }
  
  try {
    const { totals } = await aggregateUsage(trackedSessions);
    
    renderUsageSummary(totals, {
      prompt: document.getElementById('promptTokens'),
      completion: document.getElementById('completionTokens'),
      total: document.getElementById('totalTokens'),
      cost: document.getElementById('totalCost')
    });
  } catch (err) {
    console.error('Failed to fetch usage:', err);
  }
}

refreshUsageBtn?.addEventListener('click', refreshUsageStats);

// ── Draft Presentation ──────────────────────────────────────
draftBtn.addEventListener('click', async () => {
  const notes = notesEditor.value.trim();
  if (!notes) {
    toast('Please add some notes before drafting a presentation.', 'error');
    return;
  }

  const models = getSelectedModels();
  clearSessionLinks();
     let currentSessionUrl = null;


  try {
    setLoading(true, 'Saving notes...');
    setStatus('working', 'Saving notes...');
    await writeFileContent('notes.md', notes);

       setLoading(true, 'Generating presentation...', null);
    setStatus('working', 'Generating presentation...');
    
    if (basePath && sessionId) {
      // Use DocOps API
      const taskId = await runDocOp(sessionId, 'ops/draft_presentation.md', 'presentation.html', models);
      
      // Show session link in loading overlay
         currentSessionUrl = getProxyUrl(taskId);
         setLoading(true, 'Generating presentation...', currentSessionUrl);
      addSessionLink(taskId, 'Draft Session');
      
      // Wait for completion with status updates
       await waitForTask(basePath, 'presentation.html', 600000, (target, task) => {
            const taskProxyUrl = task.sessionId ? getProxyUrl(task.sessionId) : currentSessionUrl;
            if (task.sessionId) {
              currentSessionUrl = taskProxyUrl;
            }
         setLoading(true, `${task.status}: ${target}`, taskProxyUrl);
      });
    } else {
      // Fallback to direct API
      const res = await fetch('/api/ops/draft_presentation', { method: 'POST' });
      if (!res.ok) throw new Error(`Operation failed: ${res.statusText}`);
    }

    setLoading(true, 'Loading preview...');
     showPreview();

    setStatus('success', 'Presentation drafted');
    toast('Presentation drafted successfully!', 'success');
    
    // Refresh usage stats
    await refreshUsageStats();
    
  } catch (err) {
    console.error(err);
    setStatus('error', 'Draft failed');
    toast(`Error: ${err.message}`, 'error');
  } finally {
    setLoading(false);
  }
});

// ── Update Presentation ─────────────────────────────────────
updateBtn.addEventListener('click', async () => {
  const updateNotes = updateNotesEditor.value.trim();
  if (!updateNotes) {
    toast('Please add update notes before updating the presentation.', 'error');
    return;
  }

  const models = getSelectedModels();
     let currentSessionUrl = null;


  try {
    setLoading(true, 'Saving update notes...');
    setStatus('working', 'Saving update notes...');
    await writeFileContent('update_notes.md', updateNotes);

    // Also save current notes if edited
    const notes = notesEditor.value.trim();
    if (notes) await writeFileContent('notes.md', notes);

       setLoading(true, 'Applying updates...', null);
    setStatus('working', 'Applying updates...');
    
    if (basePath && sessionId) {
      // Use DocOps API
      const taskId = await runDocOp(sessionId, 'ops/update_presentation.md', 'presentation.html', models);
      
      // Show session link
         currentSessionUrl = getProxyUrl(taskId);
         setLoading(true, 'Applying updates...', currentSessionUrl);
      addSessionLink(taskId, 'Update Session');
      
       await waitForTask(basePath, 'presentation.html', 600000, (target, task) => {
            const taskProxyUrl = task.sessionId ? getProxyUrl(task.sessionId) : currentSessionUrl;
            if (task.sessionId) {
              currentSessionUrl = taskProxyUrl;
            }
         setLoading(true, `${task.status}: ${target}`, taskProxyUrl);
      });
    } else {
      // Fallback
      const res = await fetch('/api/ops/update_presentation', { method: 'POST' });
      if (!res.ok) throw new Error(`Operation failed: ${res.statusText}`);
    }

    setLoading(true, 'Loading preview...');
     showPreview();

    setStatus('success', 'Presentation updated');
    toast('Presentation updated successfully!', 'success');
    
    // Refresh usage stats
    await refreshUsageStats();
    
  } catch (err) {
    console.error(err);
    setStatus('error', 'Update failed');
    toast(`Error: ${err.message}`, 'error');
  } finally {
    setLoading(false);
  }
});

// ── Refresh Preview ─────────────────────────────────────────
refreshPreview.addEventListener('click', async () => {
  try {
     // Check if file exists first
     if (await fileExists(basePath, 'presentation.html')) {
       showPreview();
       toast('Preview refreshed', 'info');
     } else {
       toast('No presentation file found. Draft one first.', 'error');
     }
  } catch {
    toast('No presentation file found. Draft one first.', 'error');
  }
});

// ── Open in New Tab ─────────────────────────────────────────
openExternal.addEventListener('click', () => {
   const fileUrl = `${basePath}/presentation.html`;
   window.open(fileUrl, '_blank');
});

// ── Download Presentation ───────────────────────────────────
downloadBtn?.addEventListener('click', async () => {
  try {
    const html = await readFileContent('presentation.html');
     if (!html) {
       toast('No presentation file found. Draft one first.', 'error');
       return;
     }
    const blob = new Blob([html], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'presentation.html';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast('Presentation downloaded', 'success');
  } catch {
    toast('No presentation file found. Draft one first.', 'error');
  }
});

// ── Init ────────────────────────────────────────────────────
(async () => {
  // Update session display
  updateSessionDisplay();
  
  // Initialize model selection
  await initModelSelection();
  
  // Start status polling if we have a session
  if (basePath) {
    startStatusPolling();
  }
  
  // Load existing files
  try {
    const notes = await readFileContent('notes.md');
    if (notes && notes.trim()) notesEditor.value = notes;
  } catch { /* no existing notes */ }

  try {
    const updateNotes = await readFileContent('update_notes.md');
    if (updateNotes && updateNotes.trim()) updateNotesEditor.value = updateNotes;
  } catch { /* no existing update notes */ }

  try {
     if (await fileExists(basePath, 'presentation.html')) {
       showPreview();
     }
  } catch { /* no existing presentation */ }

  // Check for existing tasks
  if (basePath) {
    try {
      const status = await fetchDocopsStatus(basePath);
      if (status && status.tasks) {
        updateTaskList(status.tasks);
        
        // Collect session IDs from existing tasks
        Object.values(status.tasks).forEach(task => {
          if (task.sessionId && !trackedSessions.includes(task.sessionId)) {
            trackedSessions.push(task.sessionId);
          }
        });
        
        // Refresh usage if we have sessions
        if (trackedSessions.length > 0) {
          await refreshUsageStats();
        }
      }
    } catch { /* no status file */ }
  }

  setStatus('idle', 'Ready');
})();

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
  stopStatusPolling();
});