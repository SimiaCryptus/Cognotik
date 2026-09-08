package com.simiacryptus.cognotik.fileserver.render

data class DirectoryPageModel(
  val currentPath: String,
  val servletBaseHref: String,
  val zipLink: String,
  val folders: String,
  val files: String,
  val toolbarActions: String = "",
  val additionalSections: String = "",
  val additionalStyles: String = "",
  val additionalScripts: String = "",
  val actualFilePath: String = ""
)

object DirectoryListingRenderer {
  fun renderDirectoryPage(model: DirectoryPageModel): String = directoryHTML(
    currentPath = model.currentPath,
    servletBaseHref = model.servletBaseHref,
    zipLink = model.zipLink,
    folders = model.folders,
    files = model.files,
    toolbarActions = model.toolbarActions,
    additionalSections = model.additionalSections,
    additionalStyles = model.additionalStyles,
    additionalScripts = model.additionalScripts,
    actualFilePath = model.actualFilePath
  )

  fun generateBreadcrumbs(currentPath: String, servletBaseHref: String): String {
    val parts = currentPath.split("/").filter { it.isNotEmpty() }
    val breadcrumbs = StringBuilder()
    val rootLink = if (servletBaseHref.endsWith("/")) servletBaseHref else "$servletBaseHref/"
    if (parts.isEmpty()) {
      breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">Root</li>""")
    } else {
      breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink" style="color: #0d6efd; text-decoration:none;">Root</a></li>""")
    }
    var accumulatedPath = ""
    for ((index, part) in parts.withIndex()) {
      accumulatedPath += "$part/"
      if (index >= 0) {
        breadcrumbs.append("""<li style="padding-right: .5rem; color: #6c757d;">/</li>""")
      }
      if (index < parts.size - 1) {
        breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink$accumulatedPath" style="color: #0d6efd; text-decoration:none;">$part</a></li>""")
      } else {
        breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">$part</li>""")
      }
    }
    return breadcrumbs.toString()
  }

  fun directoryHTML(
    currentPath: String,
    servletBaseHref: String,
    zipLink: String,
    folders: String,
    files: String,
    toolbarActions: String = "",
    additionalSections: String = "",
    additionalStyles: String = "",
    additionalScripts: String = "",
    actualFilePath: String = ""
  ) = """
|<!DOCTYPE html>
|<html lang="en" data-theme="auto">
|<head>
|    <meta charset="UTF-8">
|    <meta name="viewport" content="width=device-width, initial-scale=1.0">
|    <title>Directory Listing: /$currentPath</title>
|    <style>
|        :root {
|            --bg-page: #f0f2f5;
|            --bg-surface: #ffffff;
|            --bg-surface-alt: #f8f9fa;
|            --bg-hover: #e9ecef;
|            --text-primary: #1c1e21;
|            --text-secondary: #343a40;
|            --text-muted: #6c757d;
|            --text-faint: #adb5bd;
|            --border-color: #dee2e6;
|            --border-input: #ced4da;
|            --link-color: #0d6efd;
|            --link-hover: #0a58ca;
|            --shadow-sm: 0 1px 2px rgba(0,0,0,0.04);
|            --shadow-md: 0 1px 3px rgba(0,0,0,0.03);
|            --shadow-lg: 0 2px 4px rgba(0,0,0,0.05);
|            --drop-zone-bg: #f8f9fa;
|            --drop-zone-hover-bg: #e7f1ff;
|            --code-color: #adb5bd;
|        }
|        html[data-theme="dark"] {
|            --bg-page: #1a1d21;
|            --bg-surface: #25282c;
|            --bg-surface-alt: #2d3035;
|            --bg-hover: #34383d;
|            --text-primary: #e4e6eb;
|            --text-secondary: #d1d3d8;
|            --text-muted: #9ba1a8;
|            --text-faint: #6c7079;
|            --border-color: #3a3f44;
|            --border-input: #4a4f55;
|            --link-color: #5a9eff;
|            --link-hover: #7ab4ff;
|            --shadow-sm: 0 1px 2px rgba(0,0,0,0.3);
|            --shadow-md: 0 1px 3px rgba(0,0,0,0.4);
|            --shadow-lg: 0 2px 4px rgba(0,0,0,0.5);
|            --drop-zone-bg: #2d3035;
|            --drop-zone-hover-bg: #2a3a4f;
|            --code-color: #6c7079;
|        }
|        @media (prefers-color-scheme: dark) {
|            html[data-theme="auto"] {
|                --bg-page: #1a1d21;
|                --bg-surface: #25282c;
|                --bg-surface-alt: #2d3035;
|                --bg-hover: #34383d;
|                --text-primary: #e4e6eb;
|                --text-secondary: #d1d3d8;
|                --text-muted: #9ba1a8;
|                --text-faint: #6c7079;
|                --border-color: #3a3f44;
|                --border-input: #4a4f55;
|                --link-color: #5a9eff;
|                --link-hover: #7ab4ff;
|                --shadow-sm: 0 1px 2px rgba(0,0,0,0.3);
|                --shadow-md: 0 1px 3px rgba(0,0,0,0.4);
|                --shadow-lg: 0 2px 4px rgba(0,0,0,0.5);
|                --drop-zone-bg: #2d3035;
|                --drop-zone-hover-bg: #2a3a4f;
|                --code-color: #6c7079;
|            }
|        }
|        body {
|            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
|            background-color: var(--bg-page);
|            color: var(--text-primary);
|            margin: 0;
|            padding: 0;
|            line-height: 1.5;
|            transition: background-color 0.2s ease, color 0.2s ease;
|        }
|        .navbar {
|            background-color: var(--bg-surface);
|            padding: 1rem 1.5rem;
|            box-shadow: var(--shadow-lg);
|            margin-bottom: 1.5rem;
|            display: flex;
|            align-items: center;
|            justify-content: space-between;
|            flex-wrap: wrap;
|        }
|        .navbar-title {
|            font-size: 1.4rem;
|            font-weight: 600;
|            color: var(--text-secondary);
|            margin-right: 1rem;
|        }
|        .theme-selector {
|            padding: 0.4rem 0.6rem;
|            font-size: 0.85rem;
|            color: var(--text-primary);
|            background-color: var(--bg-surface);
|            border: 1px solid var(--border-input);
|            border-radius: 0.25rem;
|            cursor: pointer;
|        }
|        .zip-link {
|            display: inline-block;
|            padding: 0.5rem 1rem;
|            font-size: 0.9rem;
|            font-weight: 500;
|            color: #fff;
|            background-color: var(--link-color);
|            border: none;
|            border-radius: 0.25rem;
|            text-decoration: none;
|            transition: background-color 0.15s ease-in-out;
|            white-space: nowrap;
|        }
|        .zip-link:hover {
|            background-color: var(--link-hover);
|        }
|        .upload-section {
|            background-color: var(--bg-surface);
|            border: 1px solid var(--border-color);
|            border-radius: 0.375rem;
|            margin-bottom: 1.5rem;
|            box-shadow: var(--shadow-md);
|        }
|        .upload-form {
|            display: flex;
|            gap: 0.75rem;
|            align-items: center;
|            flex-wrap: wrap;
|        }
|        .file-input {
|            flex: 1;
|            min-width: 200px;
|            padding: 0.5rem;
|            border: 1px solid var(--border-input);
|            border-radius: 0.25rem;
|            font-size: 0.9rem;
|            background-color: var(--bg-surface);
|            color: var(--text-primary);
|        }
|        .upload-button {
|            padding: 0.5rem 1.5rem;
|            font-size: 0.9rem;
|            font-weight: 500;
|            color: #fff;
|            background-color: #198754;
|            border: none;
|            border-radius: 0.25rem;
|            cursor: pointer;
|            transition: background-color 0.15s ease-in-out;
|        }
|        .upload-button:hover {
|            background-color: #157347;
|        }
|        .upload-button:disabled {
|            background-color: #6c757d;
|            cursor: not-allowed;
|        }
|        .upload-message {
|            margin-top: 0.5rem;
|            padding: 0.5rem;
|            border-radius: 0.25rem;
|            font-size: 0.9rem;
|        }
|        .upload-message.success {
|            background-color: #d1e7dd;
|            color: #0f5132;
|            border: 1px solid #badbcc;
|        }
|        .upload-message.error {
|            background-color: #f8d7da;
|            color: #842029;
|            border: 1px solid #f5c2c7;
|        }
|        .drop-zone {
|            border: 2px dashed var(--border-input);
|            border-radius: 0.25rem;
|            padding: 2rem;
|            text-align: center;
|            transition: all 0.3s ease;
|            cursor: pointer;
|            background-color: var(--drop-zone-bg);
|        }
|        .drop-zone.drag-over {
|            border-color: var(--link-color);
|            background-color: var(--drop-zone-hover-bg);
|        }
|        .drop-zone-text {
|            color: var(--text-muted);
|            font-size: 0.95rem;
|            margin-bottom: 0.5rem;
|        }
|        .drop-zone-hint {
|            color: var(--text-faint);
|            font-size: 0.85rem;
|        }
|        .container {
|            max-width: 960px;
|            margin: 0 auto;
|            padding: 0 1rem 1.5rem 1rem;
|        }
|        .breadcrumb-nav {
|            margin-bottom: 1.5rem;
|            padding: 0.75rem 1rem;
|            background-color: var(--bg-surface);
|            border-radius: 0.25rem;
|            box-shadow: var(--shadow-sm);
|        }
|        .breadcrumb {
|            padding: 0; margin:0; list-style:none; display:flex; flex-wrap:wrap;
|        }
|        .section {
|            background-color: var(--bg-surface);
|            border: 1px solid var(--border-color);
|            border-radius: 0.375rem;
|            margin-bottom: 1.5rem;
|            box-shadow: var(--shadow-md);
|        }
|        .section-header {
|            padding: 0.75rem 1.25rem;
|            margin-bottom: 0;
|            background-color: var(--bg-surface-alt);
|            border-bottom: 1px solid var(--border-color);
|            border-top-left-radius: calc(0.375rem - 1px);
|            border-top-right-radius: calc(0.375rem - 1px);
|        }
|        .section-title {
|            font-size: 1.2rem;
|            font-weight: 500;
|            color: var(--text-secondary);
|            margin: 0;
|        }
|        .section-content {
|            padding: 1.25rem;
|        }
|        .item-list {
|            list-style: none;
|            padding: 0;
|            margin: 0;
|        }
|        .item-list li {
|            margin-bottom: 0.25rem;
|        }
|        .item-list li:last-child { margin-bottom: 0; }
|        .item-link {
|            color: var(--link-color);
|            text-decoration: none;
|            display: flex;
|            align-items: center;
|            padding: 0.45rem 0.75rem;
|            border-radius: 0.25rem;
|            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
|        }
|        .item-link:hover {
|            background-color: var(--bg-hover);
|            color: var(--link-hover);
|        }
|        .item-link .icon {
|            margin-right: 0.7em;
|            width: 1.2em;
|            text-align: center;
|            color: var(--text-secondary);
|        }
|        .item-link:hover .icon { color: var(--link-hover); }
|        .empty-state {
|            color: var(--text-muted);
|            padding: 0.5rem 0.75rem;
|            font-style: italic;
|        }
|        .action-link {
|            margin-left: 0.5rem;
|            font-size: 0.85rem;
|            color: var(--text-muted);
|            text-decoration: none;
|            padding: 0.2rem 0.5rem;
|            border-radius: 0.2rem;
|            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
|        }
|        .action-link:hover {
|            background-color: var(--bg-hover);
|            color: var(--link-hover);
|        }
|        .filesystem-path {
|            font-size: 0.75rem;
|            color: var(--text-faint);
|            padding: 0.25rem 1rem 0.5rem 1rem;
|            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
|            word-break: break-all;
|            user-select: all;
|        }
|        .filesystem-path summary {
|            cursor: pointer;
|            color: var(--text-faint);
|            font-size: 0.75rem;
|            outline: none;
|        }
|        .filesystem-path summary:hover {
|            color: var(--text-muted);
|        }
|        .delete-link {
|            margin-left: 0.5rem;
|            font-size: 0.85rem;
|            color: #dc3545;
|            text-decoration: none;
|            padding: 0.2rem 0.5rem;
|            border-radius: 0.2rem;
|            cursor: pointer;
|            background: none;
|            border: 1px solid transparent;
|            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out, border-color 0.15s ease-in-out;
|        }
|        .delete-link:hover {
|            background-color: #f8d7da;
|            color: #842029;
|            border-color: #f5c2c7;
|        }
|        .delete-link:disabled {
|            opacity: 0.5;
|            cursor: not-allowed;
|        }
|        .expand-zip-label {
|            display: flex;
|            align-items: center;
|            gap: 0.4rem;
|            font-size: 0.85rem;
|            color: var(--text-muted);
|            user-select: none;
|            cursor: pointer;
|        }
        $additionalStyles
    </style>
|    <script src="/modules/theme.js"></script>
|    <script>
|        if (typeof ThemeManager !== 'undefined') {
|            ThemeManager.init();
|        }
|        function setupDropZone() {
            const dropZone = document.getElementById('drop-zone');
            const fileInput = document.getElementById('file-input');
|            dropZone.addEventListener('click', () => {
|                fileInput.click();
|            });
|            dropZone.addEventListener('dragover', (e) => {
|                e.preventDefault();
|                e.stopPropagation();
|                dropZone.classList.add('drag-over');
|            });
|            dropZone.addEventListener('dragleave', (e) => {
|                e.preventDefault();
|                e.stopPropagation();
|                dropZone.classList.remove('drag-over');
|            });
|            dropZone.addEventListener('drop', (e) => {
|                e.preventDefault();
|                e.stopPropagation();
|                dropZone.classList.remove('drag-over');
|                const files = e.dataTransfer.files;
|                if (files.length > 0) {
|                    fileInput.files = files;
|                    updateFileInputDisplay(files[0].name);
|                }
|            });
|            fileInput.addEventListener('change', (e) => {
|                if (e.target.files.length > 0) {
|                    updateFileInputDisplay(e.target.files[0].name);
|                }
|            });
|        }
|        function setupClipboardPaste() {
|            document.addEventListener('paste', (e) => {
|                const items = e.clipboardData.items;
|                for (let i = 0; i < items.length; i++) {
|                    if (items[i].kind === 'file') {
|                        e.preventDefault();
|                        const file = items[i].getAsFile();
|                        const fileInput = document.getElementById('file-input');
|                        const dataTransfer = new DataTransfer();
|                        dataTransfer.items.add(file);
|                        fileInput.files = dataTransfer.files;
|                        updateFileInputDisplay(file.name);
|                        showMessage('File pasted from clipboard: ' + file.name, 'success');
|                        break;
|                    }
|                }
|            });
|        }
|        function updateFileInputDisplay(fileName) {
|            const dropZoneText = document.querySelector('.drop-zone-text');
|            dropZoneText.innerHTML = '<strong>Selected:</strong> ' + fileName;
|        }
|        window.addEventListener('DOMContentLoaded', () => {
|            setupDropZone();
|            setupClipboardPaste();
|            const themeSelector = document.getElementById('theme-selector');
|            if (themeSelector && typeof ThemeManager !== 'undefined') {
|                ThemeManager.bindSelector(themeSelector);
|            }
|        });
|        async function uploadFile(event) {
|            event.preventDefault();
|            const form = event.target;
|            const fileInput = document.getElementById('file-input');
|            const submitButton = form.querySelector('button[type="submit"]');
|            const messageDiv = document.getElementById('upload-message');
|            if (!fileInput.files || fileInput.files.length === 0) {
|                showMessage('Please select a file to upload', 'error');
|                return;
|            }
|            const formData = new FormData(form);
|            submitButton.disabled = true;
|            submitButton.textContent = 'Uploading...';
|            messageDiv.textContent = '';
|            messageDiv.className = 'upload-message';
|            try {
|                const response = await fetch(window.location.href, {
|                    method: 'POST',
|                    body: formData
|                });
|                const text = await response.text();
|                if (response.ok) {
|                    let msg = 'File uploaded successfully!';
|                    try { const j = JSON.parse(text); if (j && j.message) msg = j.message; } catch (e) { }
|                    showMessage(msg, 'success');
|                    fileInput.value = '';
|                    const dropZoneText = document.querySelector('.drop-zone-text');
|                    dropZoneText.innerHTML = 'Click to select, drag & drop, or paste (Ctrl+V) a file here';
|                    setTimeout(() => window.location.reload(), 1500);
|                } else {
|                    showMessage(text || 'Upload failed', 'error');
|                }
|            } catch (error) {
|                showMessage('Upload failed: ' + error.message, 'error');
|            } finally {
|                submitButton.disabled = false;
|                submitButton.textContent = 'Upload';
|            }
|        }
|        function showMessage(message, type) {
|            const messageDiv = document.getElementById('upload-message');
|            messageDiv.textContent = message;
|            messageDiv.className = 'upload-message ' + type;
|        }
|        async function deleteItem(event, itemName, isFolder) {
|            event.preventDefault();
|            event.stopPropagation();
|            const itemType = isFolder ? 'folder' : 'file';
|            if (!confirm('Are you sure you want to delete this ' + itemType + ': "' + itemName + '"?' + (isFolder ? '\n\nThis will delete the folder and all its contents.' : ''))) {
|                return;
|            }
|            const button = event.currentTarget;
|            button.disabled = true;
|            const originalText = button.textContent;
|            button.textContent = 'Deleting...';
|            try {
|                const targetUrl = new URL(itemName + (isFolder ? '/' : ''), window.location.href).href;
|                const response = await fetch(targetUrl, {
|                    method: 'DELETE'
|                });
|                if (response.ok) {
|                    const listItem = button.closest('li');
|                    if (listItem) {
|                        listItem.style.transition = 'opacity 0.3s ease';
|                        listItem.style.opacity = '0';
|                        setTimeout(() => window.location.reload(), 300);
|                    } else {
|                        window.location.reload();
|                    }
|                } else {
|                    const text = await response.text();
|                    alert('Failed to delete ' + itemType + ': ' + (text || response.statusText));
|                    button.disabled = false;
|                    button.textContent = originalText;
|                }
|            } catch (error) {
|                alert('Error deleting ' + itemType + ': ' + error.message);
|                button.disabled = false;
|                button.textContent = originalText;
|            }
|        }
|    $additionalScripts
|    </script>
|</head>
|<body>
|    <div class="navbar">
|        <span class="navbar-title"> File Browser</span>
|        <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
|        <select id="theme-selector" class="theme-selector" aria-label="Theme">
|            <option value="auto">🌓 Auto</option>
|            <option value="light">☀️ Light</option>
|            <option value="dark">🌙 Dark</option>
|        </select>
|        ${if (zipLink.isNotBlank()) """<a href="$zipLink" class="zip-link">Download Current Directory as ZIP</a>""" else ""}
|        $toolbarActions
|        </div>
|    </div>
|    <div class="container">
|        <nav class="breadcrumb-nav" aria-label="breadcrumb">
|           <ol class="breadcrumb">
|               ${generateBreadcrumbs(currentPath, servletBaseHref)}
|           </ol>
|           ${if (actualFilePath.isNotBlank()) """<span class="filesystem-path" title="Filesystem path">$actualFilePath</span>""" else ""}
|        </nav>
|
|        <div class="section upload-section">
|            <div class="section-header"><h2 class="section-title">Upload File</h2></div>
|            <div class="section-content">
|                <form class="upload-form" onsubmit="uploadFile(event)" enctype="multipart/form-data">
|                    <div id="drop-zone" class="drop-zone">
|                        <div class="drop-zone-text">Click to select, drag & drop, or paste (Ctrl+V) a file here</div>
|                        <div class="drop-zone-hint">Maximum file size: 50MB &middot; .zip archives are unpacked into this folder</div>
|                    </div>
|                    <input type="file" name="file" id="file-input" class="file-input" required style="display: none;">
|                    <label class="expand-zip-label" title="Unpack uploaded .zip archives into this folder instead of storing them">
|                        <input type="checkbox" id="expand-zip" checked
|                               onchange="document.getElementById('expand-zip-input').value = this.checked ? 'true' : 'false';">
|                        Expand ZIP archives on upload
|                    </label>
|                    <input type="hidden" name="expand" id="expand-zip-input" value="true">
|                    <button type="submit" class="upload-button">Upload</button>
|                </form>
|                <div id="upload-message" class="upload-message"></div>
|            </div>
|        </div>
|
|        <div class="section">
|            <div class="section-header"><h2 class="section-title">Folders</h2></div>
|            <div class="section-content">
|                ${if (folders.isBlank()) "<p class=\"empty-state\">No sub-folders found.</p>" else "<ul class=\"item-list\">$folders</ul>"}
|            </div>
|        </div>
|
|        <div class="section">
|            <div class="section-header"><h2 class="section-title">Files</h2></div>
|            <div class="section-content">
|                ${if (files.isBlank()) "<p class=\"empty-state\">No files found.</p>" else "<ul class=\"item-list\">$files</ul>"}
|            </div>
|        </div>
|
|        $additionalSections
|
|    </div>
|</body>
|</html>
""".trimMargin()
}