package com.simiacryptus.cognotik.webui.servlet.render.git

object GitScripts {
  fun getGitScripts(): String = """
let gitDiffData = { unstaged: '', staged: '' };
let currentDiffTab = 'unstaged';
async function gitRequest(action, params = {}) {
const formData = new URLSearchParams();
formData.append('gitAction', action);
for (const [key, value] of Object.entries(params)) {
formData.append(key, value);
}
const response = await fetch(window.location.href, {
method: 'POST',
headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
body: formData.toString()
});
return await response.json();
}
function hideAllGitPanels() {
document.querySelectorAll('.git-panel').forEach(p => p.style.display = 'none');
}
function showGitPanel(panelId) {
hideAllGitPanels();
const panel = document.getElementById(panelId);
if (panel) panel.style.display = 'block';
}
function setGitLoading(elementId, message) {
const el = document.getElementById(elementId);
if (el) el.innerHTML = '<span class="git-loading"></span> ' + message;
}
async function gitInit() {
if (!confirm('Initialize a new Git repository in this directory?')) return;
const btn = document.querySelector('.git-init-btn');
if (btn) {
btn.disabled = true;
btn.textContent = '⏳ Initializing...';
}
try {
const result = await gitRequest('init');
if (result.success) {
window.location.reload();
} else {
alert('Error: ' + result.message);
if (btn) {
btn.disabled = false;
btn.textContent = '🚀 Initialize Git Repository';
}
}
} catch (e) {
alert('Error initializing repository: ' + e.message);
if (btn) {
btn.disabled = false;
btn.textContent = '🚀 Initialize Git Repository';
}
}
}
async function gitStatus() {
showGitPanel('git-status-panel');
setGitLoading('git-status-content', 'Loading status...');
try {
const result = await gitRequest('status');
const badge = document.getElementById('git-branch-badge');
if (badge && result.branch) {
badge.textContent = '⎇ ' + result.branch;
badge.style.display = 'inline-block';
}
const statusEl = document.getElementById('git-status-content');
if (result.status && result.status.trim()) {
statusEl.innerHTML = colorizeStatus(result.status);
} else {
statusEl.innerHTML = '<span style="color: #4ec9b0;">✓ Working tree clean</span>';
}
} catch (e) {
document.getElementById('git-status-content').textContent = 'Error: ' + e.message;
}
}
function colorizeStatus(status) {
return status.split('\n').map(line => {
if (!line.trim()) return '';
const code = line.substring(0, 2);
let cls = 'git-status-untracked';
if (code.includes('M')) cls = 'git-status-modified';
else if (code.includes('A')) cls = 'git-status-added';
else if (code.includes('D')) cls = 'git-status-deleted';
else if (code.includes('?')) cls = 'git-status-untracked';
return '<span class="' + cls + '">' + escapeHtml(line) + '</span>';
}).join('\n');
}
function escapeHtml(text) {
const div = document.createElement('div');
div.textContent = text;
return div.innerHTML;
}
async function gitDiff() {
showGitPanel('git-diff-panel');
setGitLoading('git-diff-content', 'Loading diff...');
try {
const result = await gitRequest('diff');
gitDiffData.unstaged = result.unstaged || '';
gitDiffData.staged = result.staged || '';
showDiffTab(currentDiffTab);
} catch (e) {
document.getElementById('git-diff-content').textContent = 'Error: ' + e.message;
}
}
function showDiffTab(tab) {
currentDiffTab = tab;
document.querySelectorAll('.git-tab').forEach(t => t.classList.remove('active'));
document.querySelectorAll('.git-tab').forEach(t => {
if (t.textContent.toLowerCase().includes(tab)) t.classList.add('active');
});
const content = gitDiffData[tab] || '';
const el = document.getElementById('git-diff-content');
if (content.trim()) {
el.innerHTML = colorizeDiff(content);
} else {
el.innerHTML = '<span style="color: #6c757d;">No ' + tab + ' changes</span>';
}
}
function colorizeDiff(diff) {
return diff.split('\n').map(line => {
if (line.startsWith('+++') || line.startsWith('---')) {
return '<span class="diff-file">' + escapeHtml(line) + '</span>';
} else if (line.startsWith('+')) {
return '<span class="diff-add">' + escapeHtml(line) + '</span>';
} else if (line.startsWith('-')) {
return '<span class="diff-del">' + escapeHtml(line) + '</span>';
} else if (line.startsWith('@@')) {
return '<span class="diff-hunk">' + escapeHtml(line) + '</span>';
}
return escapeHtml(line);
}).join('\n');
}
async function gitLog() {
showGitPanel('git-log-panel');
setGitLoading('git-log-content', 'Loading log...');
try {
const result = await gitRequest('log');
const el = document.getElementById('git-log-content');
el.textContent = result.log || 'No commits yet';
} catch (e) {
document.getElementById('git-log-content').textContent = 'Error: ' + e.message;
}
}
async function gitAdd(filePath) {
try {
const result = await gitRequest('add', { filePath: filePath });
showGitOutput('Stage', result.output || result.message);
gitStatus();
} catch (e) {
alert('Error staging files: ' + e.message);
}
}
function promptCommit() {
document.getElementById('git-commit-dialog').style.display = 'flex';
document.getElementById('git-commit-message').focus();
}
function closeCommitDialog() {
document.getElementById('git-commit-dialog').style.display = 'none';
document.getElementById('git-commit-message').value = '';
}
async function gitCommit() {
const message = document.getElementById('git-commit-message').value.trim();
if (!message) {
alert('Please enter a commit message');
return;
}
closeCommitDialog();
try {
const result = await gitRequest('commit', { message: message });
showGitOutput('Commit', result.output || result.message);
gitStatus();
} catch (e) {
alert('Error committing: ' + e.message);
}
}
function confirmReset() {
if (confirm('Are you sure you want to discard ALL uncommitted changes? This cannot be undone.')) {
gitReset();
}
}
async function gitReset() {
try {
const result = await gitRequest('reset');
showGitOutput('Reset', result.output || result.message);
gitStatus();
} catch (e) {
alert('Error resetting: ' + e.message);
}
}
function showGitOutput(title, content) {
showGitPanel('git-output-panel');
document.getElementById('git-output-title').textContent = title;
document.getElementById('git-output-content').textContent = content;
}
async function gitBranches() {
showGitPanel('git-branches-panel');
const contentEl = document.getElementById('git-branches-content');
contentEl.innerHTML = '<span class="git-loading"></span> Loading branches...';
try {
const result = await gitRequest('branches');
const currentBranch = result.currentBranch || '';
const badge = document.getElementById('git-branch-badge');
if (badge && currentBranch) {
badge.textContent = '⎇ ' + currentBranch;
badge.style.display = 'inline-block';
}
const branchLines = (result.branches || '').split('\n').filter(l => l.trim());
if (branchLines.length === 0) {
contentEl.innerHTML = '<p style="color: #6c757d; padding: 0.5rem;">No branches found.</p>';
return;
}
let html = '<ul class="git-branch-list">';
branchLines.forEach(line => {
const trimmed = line.trim();
const isCurrent = trimmed.startsWith('* ');
const branchName = isCurrent ? trimmed.substring(2).trim() : trimmed;
const isDetached = branchName.includes('HEAD detached') || branchName.includes('(HEAD detached');
html += '<li class="git-branch-item' + (isCurrent ? ' current-branch' : '') + '">';
html += '<span class="git-branch-name">';
if (isCurrent) {
html += '<span class="git-branch-current-indicator">●</span> ';
}
html += escapeHtml(branchName);
if (isCurrent) {
html += ' <span style="font-size:0.78rem; color:#198754;">(current)</span>';
}
html += '</span>';
html += '<span class="git-branch-actions">';
if (!isCurrent && !isDetached) {
html += '<button class="git-branch-action-btn git-branch-switch-btn" onclick="gitSwitchBranch(\'' + escapeHtml(branchName).replace(/'/g, "\\'") + '\')">Switch</button>';
html += '<button class="git-branch-action-btn git-branch-delete-btn" onclick="gitDeleteBranch(\'' + escapeHtml(branchName).replace(/'/g, "\\'") + '\')">Delete</button>';
}
html += '</span>';
html += '</li>';
});
html += '</ul>';
contentEl.innerHTML = html;
} catch (e) {
contentEl.innerHTML = '<p style="color: #dc3545;">Error: ' + escapeHtml(e.message) + '</p>';
}
}
function promptCreateBranch() {
document.getElementById('git-create-branch-dialog').style.display = 'flex';
document.getElementById('git-new-branch-name').value = '';
document.getElementById('git-new-branch-name').focus();
}
function closeCreateBranchDialog() {
document.getElementById('git-create-branch-dialog').style.display = 'none';
document.getElementById('git-new-branch-name').value = '';
}
async function gitCreateBranch() {
const branchName = document.getElementById('git-new-branch-name').value.trim();
if (!branchName) {
alert('Please enter a branch name');
return;
}
if (/[^a-zA-Z0-9_\-\/.]/.test(branchName) || branchName.startsWith('-') || branchName.includes('..')) {
alert('Invalid branch name. Use only letters, numbers, hyphens, underscores, dots, and forward slashes.');
return;
}
const checkout = document.getElementById('git-checkout-new-branch').checked;
closeCreateBranchDialog();
try {
const result = await gitRequest('create-branch', { branchName: branchName, checkout: checkout.toString() });
showGitOutput('Create Branch', result.output || result.message);
gitStatus();
gitBranches();
} catch (e) {
alert('Error creating branch: ' + e.message);
}
}
async function gitSwitchBranch(branchName) {
if (!confirm('Switch to branch "' + branchName + '"?')) return;
try {
const result = await gitRequest('switch-branch', { branchName: branchName });
showGitOutput('Switch Branch', result.output || result.message);
gitStatus();
gitBranches();
} catch (e) {
alert('Error switching branch: ' + e.message);
}
}
async function gitDeleteBranch(branchName) {
if (!confirm('Delete branch "' + branchName + '"? This cannot be undone for unmerged branches.')) return;
try {
const result = await gitRequest('delete-branch', { branchName: branchName });
showGitOutput('Delete Branch', result.output || result.message);
gitBranches();
} catch (e) {
if (confirm('Branch may not be fully merged. Force delete "' + branchName + '"?')) {
try {
const result = await gitRequest('delete-branch', { branchName: branchName, force: 'true' });
showGitOutput('Delete Branch (forced)', result.output || result.message);
gitBranches();
} catch (e2) {
alert('Error force-deleting branch: ' + e2.message);
}
}
}
}
window.addEventListener('DOMContentLoaded', () => {
if (document.getElementById('git-status-panel')) {
gitStatus();
}
});
document.addEventListener('keydown', (e) => {
const dialog = document.getElementById('git-commit-dialog');
const branchDialog = document.getElementById('git-create-branch-dialog');
if (dialog && dialog.style.display === 'flex') {
if (e.key === 'Escape') {
closeCommitDialog();
} else if (e.key === 'Enter' && e.ctrlKey) {
gitCommit();
}
} else if (branchDialog && branchDialog.style.display === 'flex') {
if (e.key === 'Escape') {
closeCreateBranchDialog();
} else if (e.key === 'Enter') {
gitCreateBranch();
}
}
});
"""
}