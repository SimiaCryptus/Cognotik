package com.simiacryptus.cognotik.webui.servlet.render.git

import java.io.File

object GitHtml {
  fun buildGitSection(gitRoot: File?, isRepo: Boolean): String {
    if (!isRepo) {
      return """
<div class="section git-section">
<div class="section-header"><h2 class="section-title">🔀 Git Version Control</h2></div>
<div class="section-content">
<div class="git-init-prompt">
<p class="git-init-message">This directory is not yet a Git repository. Initialize one to enable version control features like commit, diff, push, pull, and more.</p>
<button class="git-button git-init-btn" onclick="gitInit()">🚀 Initialize Git Repository</button>
</div>
</div>
</div>
"""
    }
    return """
<div class="section git-section">
<div class="section-header">
<h2 class="section-title">🔀 Git Repository</h2>
<span id="git-branch-badge" class="git-branch-badge" style="display:none;"></span>
</div>
<div class="section-content">
<div class="git-controls">
<div class="git-button-group">
<button class="git-button" onclick="gitStatus()" title="Refresh status">⟳ Status</button>
<button class="git-button" onclick="gitDiff()" title="View changes">📋 Diff</button>
<button class="git-button" onclick="gitLog()" title="View commit history">📜 Log</button>
</div>
<div class="git-button-group">
<button class="git-button git-stage-btn" onclick="gitAdd('.')" title="Stage all changes">➕ Stage All</button>
<button class="git-button git-commit-btn" onclick="promptCommit()" title="Commit staged changes">✓ Commit</button>
</div>
<div class="git-button-group">
<button class="git-button git-reset-btn" onclick="confirmReset()" title="Discard all changes">⮌ Reset</button>
</div>
<div class="git-button-group">
<button class="git-button git-branch-btn" onclick="gitBranches()" title="List branches">⎇ Branches</button>
<button class="git-button git-branch-create-btn" onclick="promptCreateBranch()" title="Create new branch">⎇+ New Branch</button>
</div>
</div>
<div id="git-status-panel" class="git-panel" style="display:none;">
<h3 class="git-panel-title">Status</h3>
<pre id="git-status-content" class="git-output"></pre>
</div>
<div id="git-diff-panel" class="git-panel" style="display:none;">
<h3 class="git-panel-title">Diff</h3>
<div id="git-diff-tabs" class="git-tabs">
<button class="git-tab active" onclick="showDiffTab('unstaged')">Unstaged</button>
<button class="git-tab" onclick="showDiffTab('staged')">Staged</button>
</div>
<pre id="git-diff-content" class="git-output git-diff-output"></pre>
</div>
<div id="git-log-panel" class="git-panel" style="display:none;">
<h3 class="git-panel-title">Commit History</h3>
<pre id="git-log-content" class="git-output"></pre>
</div>
<div id="git-branches-panel" class="git-panel" style="display:none;">
<h3 class="git-panel-title">Branches</h3>
<div id="git-branches-content" class="section-content"></div>
</div>
<div id="git-output-panel" class="git-panel" style="display:none;">
<h3 class="git-panel-title" id="git-output-title">Output</h3>
<pre id="git-output-content" class="git-output"></pre>
</div>
<div id="git-commit-dialog" class="git-dialog" style="display:none;">
<div class="git-dialog-content">
<h3>Commit Changes</h3>
<textarea id="git-commit-message" class="git-commit-input" placeholder="Enter commit message..." rows="3"></textarea>
<div class="git-dialog-buttons">
<button class="git-button git-commit-btn" onclick="gitCommit()">Commit</button>
<button class="git-button git-cancel-btn" onclick="closeCommitDialog()">Cancel</button>
</div>
</div>
</div>
<div id="git-create-branch-dialog" class="git-dialog" style="display:none;">
<div class="git-dialog-content">
<h3>Create New Branch</h3>
<input type="text" id="git-new-branch-name" class="git-commit-input" placeholder="Enter branch name..." style="margin-bottom: 0.5rem;" />
<label style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.9rem; color: #495057;">
<input type="checkbox" id="git-checkout-new-branch" checked /> Switch to new branch after creation
</label>
<div class="git-dialog-buttons">
<button class="git-button git-branch-create-btn" onclick="gitCreateBranch()">Create Branch</button>
<button class="git-button git-cancel-btn" onclick="closeCreateBranchDialog()">Cancel</button>
</div>
</div>
</div>
</div>
</div>
"""
  }

  fun getGitToolbarActions(): String {
    return """<button class="zip-link" onclick="gitStatus()" style="background-color: #6f42c1;">🔀 Git Status</button>"""
  }
}