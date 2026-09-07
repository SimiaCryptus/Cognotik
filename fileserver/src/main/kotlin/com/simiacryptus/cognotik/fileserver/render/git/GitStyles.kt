package com.simiacryptus.cognotik.fileserver.render.git

object GitStyles {
  fun getGitStyles(): String = """
.git-section {
border-color: #6f42c1;
}
.git-section .section-header {
background-color: var(--bg-surface-alt, #f3f0ff);
border-bottom-color: var(--border-color, #d4c5f9);
display: flex;
align-items: center;
justify-content: space-between;
}
.git-branch-badge {
background-color: #6f42c1;
color: white;
padding: 0.2rem 0.6rem;
border-radius: 1rem;
font-size: 0.8rem;
font-weight: 500;
}
.git-controls {
display: flex;
flex-wrap: wrap;
gap: 0.75rem;
margin-bottom: 1rem;
}
.git-button-group {
display: flex;
gap: 0.35rem;
flex-wrap: wrap;
}
.git-button {
padding: 0.4rem 0.8rem;
font-size: 0.85rem;
font-weight: 500;
color: #fff;
background-color: #6f42c1;
border: none;
border-radius: 0.25rem;
cursor: pointer;
transition: background-color 0.15s ease-in-out;
white-space: nowrap;
}
.git-button:hover {
background-color: #5a32a3;
}
.git-init-btn { background-color: #198754; }
.git-init-btn:hover { background-color: #157347; }
.git-init-prompt {
text-align: center;
padding: 1.5rem 1rem;
}
.git-init-message {
color: var(--text-secondary, #495057);
font-size: 0.95rem;
margin-bottom: 1rem;
max-width: 500px;
margin-left: auto;
margin-right: auto;
}
.git-init-btn {
padding: 0.6rem 1.5rem;
font-size: 1rem;
}
.git-stage-btn { background-color: #0d6efd; }
.git-stage-btn:hover { background-color: #0b5ed7; }
.git-commit-btn { background-color: #198754; }
.git-commit-btn:hover { background-color: #157347; }
.git-reset-btn { background-color: #dc3545; }
.git-reset-btn:hover { background-color: #bb2d3b; }
.git-branch-btn { background-color: #20c997; color: #000; }
.git-branch-btn:hover { background-color: #1aae85; }
.git-branch-create-btn { background-color: #20c997; color: #000; }
.git-branch-create-btn:hover { background-color: #1aae85; }
.git-cancel-btn { background-color: #6c757d; }
.git-cancel-btn:hover { background-color: #5a6268; }
.git-panel {
margin-top: 0.75rem;
border: 1px solid var(--border-color, #dee2e6);
border-radius: 0.25rem;
overflow: hidden;
}
.git-panel-title {
margin: 0;
padding: 0.5rem 0.75rem;
background-color: var(--bg-surface-alt, #f8f9fa);
border-bottom: 1px solid var(--border-color, #dee2e6);
font-size: 0.95rem;
font-weight: 500;
color: var(--text-primary, inherit);
}
.git-output {
margin: 0;
padding: 0.75rem;
background-color: #1e1e1e;
color: #d4d4d4;
font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
font-size: 0.82rem;
line-height: 1.5;
overflow-x: auto;
max-height: 400px;
overflow-y: auto;
white-space: pre-wrap;
word-break: break-all;
}
.git-diff-output .diff-add { color: #4ec9b0; }
.git-diff-output .diff-del { color: #f44747; }
.git-diff-output .diff-hunk { color: #569cd6; }
.git-diff-output .diff-file { color: #dcdcaa; font-weight: bold; }
.git-tabs {
display: flex;
border-bottom: 1px solid var(--border-color, #dee2e6);
background-color: var(--bg-surface-alt, #f8f9fa);
}
.git-tab {
padding: 0.4rem 1rem;
border: none;
background: none;
cursor: pointer;
font-size: 0.85rem;
color: var(--text-muted, #6c757d);
border-bottom: 2px solid transparent;
transition: all 0.15s;
}
.git-tab:hover { color: var(--text-secondary, #343a40); }
.git-tab.active {
color: #6f42c1;
border-bottom-color: #6f42c1;
font-weight: 500;
}
.git-dialog {
position: fixed;
top: 0; left: 0; right: 0; bottom: 0;
background-color: rgba(0,0,0,0.5);
display: flex;
align-items: center;
justify-content: center;
z-index: 1000;
}
.git-dialog-content {
background: var(--bg-surface, white);
color: var(--text-primary, inherit);
padding: 1.5rem;
border-radius: 0.5rem;
min-width: 400px;
max-width: 600px;
box-shadow: 0 10px 25px rgba(0,0,0,0.2);
}
.git-dialog-content h3 {
margin-top: 0;
margin-bottom: 1rem;
color: var(--text-secondary, #343a40);
}
.git-commit-input {
width: 100%;
padding: 0.5rem;
border: 1px solid var(--border-input, #ced4da);
background-color: var(--bg-surface, #fff);
color: var(--text-primary, inherit);
border-radius: 0.25rem;
font-family: inherit;
font-size: 0.9rem;
resize: vertical;
box-sizing: border-box;
}
.git-dialog-buttons {
display: flex;
gap: 0.5rem;
margin-top: 1rem;
justify-content: flex-end;
}
.git-status-modified { color: #fd7e14; }
.git-status-added { color: #198754; }
.git-status-deleted { color: #dc3545; }
.git-status-untracked { color: #6c757d; }
.git-branch-list {
list-style: none;
padding: 0;
margin: 0;
}
.git-branch-item {
display: flex;
align-items: center;
justify-content: space-between;
padding: 0.5rem 0.75rem;
border-bottom: 1px solid var(--border-color, #dee2e6);
font-size: 0.9rem;
transition: background-color 0.15s;
}
.git-branch-item:last-child { border-bottom: none; }
.git-branch-item:hover { background-color: var(--bg-hover, #f8f9fa); }
.git-branch-item.current-branch {
background-color: var(--bg-surface-alt, #f3f0ff);
font-weight: 600;
}
.git-branch-name {
display: flex;
align-items: center;
gap: 0.5rem;
}
.git-branch-current-indicator {
color: #198754;
font-weight: bold;
}
.git-branch-actions {
display: flex;
gap: 0.35rem;
}
.git-branch-action-btn {
padding: 0.2rem 0.5rem;
font-size: 0.78rem;
font-weight: 500;
color: #fff;
border: none;
border-radius: 0.2rem;
cursor: pointer;
transition: background-color 0.15s;
}
.git-branch-switch-btn { background-color: #0d6efd; }
.git-branch-switch-btn:hover { background-color: #0b5ed7; }
.git-branch-delete-btn { background-color: #dc3545; }
.git-branch-delete-btn:hover { background-color: #bb2d3b; }
.git-loading {
display: inline-block;
width: 1rem;
height: 1rem;
border: 2px solid #f3f3f3;
border-top: 2px solid #6f42c1;
border-radius: 50%;
animation: git-spin 0.8s linear infinite;
margin-right: 0.5rem;
vertical-align: middle;
}
@keyframes git-spin {
0% { transform: rotate(0deg); }
100% { transform: rotate(360deg); }
}
"""
}