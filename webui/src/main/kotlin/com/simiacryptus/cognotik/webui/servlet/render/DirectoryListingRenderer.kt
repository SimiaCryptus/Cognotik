package com.simiacryptus.cognotik.webui.servlet.render

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
|<html lang="en">
|<head>
|    <meta charset="UTF-8">
|    <meta name="viewport" content="width=device-width, initial-scale=1.0">
|    <title>Directory Listing: /$currentPath</title>
|    <style>
|        body {
|            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
|            background-color: #f0f2f5;
|            color: #1c1e21;
|            margin: 0;
|            padding: 0;
|            line-height: 1.5;
|        }
|        .navbar {
|            background-color: #ffffff;
|            padding: 1rem 1.5rem;
|            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
|            margin-bottom: 1.5rem;
|            display: flex;
|            align-items: center;
|            justify-content: space-between;
|            flex-wrap: wrap;
|        }
|        .navbar-title {
|            font-size: 1.4rem;
|            font-weight: 600;
|            color: #343a40;
|            margin-right: 1rem;
|        }
|        .zip-link {
|            display: inline-block;
|            padding: 0.5rem 1rem;
|            font-size: 0.9rem;
|            font-weight: 500;
|            color: #fff;
|            background-color: #0d6efd;
|            border: none;
|            border-radius: 0.25rem;
|            text-decoration: none;
|            transition: background-color 0.15s ease-in-out;
|            white-space: nowrap;
|        }
|        .zip-link:hover {
|            background-color: #0b5ed7;
|        }
|        .upload-section {
|            background-color: #ffffff;
|            border: 1px solid #dee2e6;
|            border-radius: 0.375rem;
|            margin-bottom: 1.5rem;
|            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
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
|            border: 1px solid #ced4da;
|            border-radius: 0.25rem;
|            font-size: 0.9rem;
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
|            border: 2px dashed #ced4da;
|            border-radius: 0.25rem;
|            padding: 2rem;
|            text-align: center;
|            transition: all 0.3s ease;
|            cursor: pointer;
|            background-color: #f8f9fa;
|        }
|        .drop-zone.drag-over {
|            border-color: #0d6efd;
|            background-color: #e7f1ff;
|        }
|        .drop-zone-text {
|            color: #6c757d;
|            font-size: 0.95rem;
|            margin-bottom: 0.5rem;
|        }
|        .drop-zone-hint {
|            color: #adb5bd;
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
|            background-color: #ffffff;
|            border-radius: 0.25rem;
|            box-shadow: 0 1px 2px rgba(0,0,0,0.04);
|        }
|        .breadcrumb {
|            padding: 0; margin:0; list-style:none; display:flex; flex-wrap:wrap;
|        }
|        .section {
|            background-color: #ffffff;
|            border: 1px solid #dee2e6;
|            border-radius: 0.375rem;
|            margin-bottom: 1.5rem;
|            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
|        }
|        .section-header {
|            padding: 0.75rem 1.25rem;
|            margin-bottom: 0;
|            background-color: #f8f9fa;
|            border-bottom: 1px solid #dee2e6;
|            border-top-left-radius: calc(0.375rem - 1px);
|            border-top-right-radius: calc(0.375rem - 1px);
|        }
|        .section-title {
|            font-size: 1.2rem;
|            font-weight: 500;
|            color: #343a40;
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
|            color: #0d6efd;
|            text-decoration: none;
|            display: flex;
|            align-items: center;
|            padding: 0.45rem 0.75rem;
|            border-radius: 0.25rem;
|            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
|        }
|        .item-link:hover {
|            background-color: #e9ecef;
|            color: #0a58ca;
|        }
|        .item-link .icon {
|            margin-right: 0.7em;
|            width: 1.2em;
|            text-align: center;
|            color: #495057;
|        }
|        .item-link:hover .icon { color: #0a58ca; }
|        .empty-state {
|            color: #6c757d;
|            padding: 0.5rem 0.75rem;
|            font-style: italic;
|        }
|        .action-link {
|            margin-left: 0.5rem;
|            font-size: 0.85rem;
|            color: #6c757d;
|            text-decoration: none;
|            padding: 0.2rem 0.5rem;
|            border-radius: 0.2rem;
|            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
|        }
|        .action-link:hover {
|            background-color: #e9ecef;
|            color: #0a58ca;
|        }
|        .filesystem-path {
|            font-size: 0.75rem;
|            color: #adb5bd;
|            padding: 0.25rem 1rem 0.5rem 1rem;
|            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
|            word-break: break-all;
|            user-select: all;
|        }
|        .filesystem-path summary {
|            cursor: pointer;
|            color: #adb5bd;
|            font-size: 0.75rem;
|            outline: none;
|        }
|        .filesystem-path summary:hover {
|            color: #6c757d;
|        }
        $additionalStyles
    </style>
|    <script>
|        function setupDropZone() {
|            const dropZone = document.getElementById('drop-zone');
|            const fileInput = document.getElementById('file-input');
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
|                    showMessage('File uploaded successfully!', 'success');
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
|    $additionalScripts
|    </script>
|</head>
|<body>
|    <div class="navbar">
|        <span class="navbar-title"> File Browser</span>
|        <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
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
|                        <div class="drop-zone-hint">Maximum file size: 50MB</div>
|                    </div>
|                    <input type="file" name="file" id="file-input" class="file-input" required style="display: none;">
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