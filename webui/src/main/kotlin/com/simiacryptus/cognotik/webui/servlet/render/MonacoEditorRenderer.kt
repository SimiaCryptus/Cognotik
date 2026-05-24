package com.simiacryptus.cognotik.webui.servlet.render
    
    object MonacoEditorRenderer {
        private fun detectLanguage(filename: String): String {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "js", "mjs", "cjs" -> "javascript"
                "ts" -> "typescript"
                "jsx" -> "javascript"
                "tsx" -> "typescript"
                "json" -> "json"
                "html", "htm" -> "html"
                "css" -> "css"
                "scss", "sass" -> "scss"
                "less" -> "less"
                "xml" -> "xml"
                "yaml", "yml" -> "yaml"
                "md", "markdown" -> "markdown"
                "py" -> "python"
                "rb" -> "ruby"
                "go" -> "go"
                "rs" -> "rust"
                "java" -> "java"
                "kt", "kts" -> "kotlin"
                "scala" -> "scala"
                "groovy", "gradle" -> "groovy"
                "c", "h" -> "c"
                "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> "cpp"
                "cs" -> "csharp"
                "php" -> "php"
                "swift" -> "swift"
                "sh", "bash", "zsh" -> "shell";
                "sql" -> "sql"
                "dockerfile" -> "dockerfile"
                "ini", "toml", "cfg", "conf" -> "ini"
                "lua" -> "lua"
                "r" -> "r"
                "pl", "pm" -> "perl"
                "txt", "log" -> "plaintext"
                else -> "plaintext"
            }
        }
    
        fun renderEditorPage(
            filename: String,
            filePath: String,
            content: String,
            readOnly: Boolean = false
        ): String {
            val language = detectLanguage(filename)
            val escapedContent = content
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("\$", "\\\$")
           .replace("</", "<\\/")
            val escapedFilename = filename
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "&quot;")
            return """
<!DOCTYPE html>
<html lang="en" data-theme="auto">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Edit: $escapedFilename</title>
<link href="https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/editor/editor.main.min.css" rel="stylesheet">
<style>
:root {
    --bg-page: #f0f2f5;
    --bg-surface: #ffffff;
    --bg-surface-alt: #f8f9fa;
    --text-primary: #1c1e21;
    --text-secondary: #343a40;
    --text-muted: #6c757d;
    --border-color: #dee2e6;
    --link-color: #0d6efd;
    --link-hover: #0a58ca;
}
html[data-theme="dark"] {
    --bg-page: #1a1d21;
    --bg-surface: #25282c;
    --bg-surface-alt: #2d3035;
    --text-primary: #e4e6eb;
    --text-secondary: #d1d3d8;
    --text-muted: #9ba1a8;
    --border-color: #3a3f44;
    --link-color: #5a9eff;
    --link-hover: #7ab4ff;
}
@media (prefers-color-scheme: dark) {
    html[data-theme="auto"] {
        --bg-page: #1a1d21;
        --bg-surface: #25282c;
        --bg-surface-alt: #2d3035;
        --text-primary: #e4e6eb;
        --text-secondary: #d1d3d8;
        --text-muted: #9ba1a8;
        --border-color: #3a3f44;
        --link-color: #5a9eff;
        --link-hover: #7ab4ff;
    }
}
* { box-sizing: border-box; }
html, body {
    margin: 0;
    padding: 0;
    height: 100%;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background-color: var(--bg-page);
    color: var(--text-primary);
}
body {
    display: flex;
    flex-direction: column;
    height: 100vh;
}
.toolbar {
    background-color: var(--bg-surface);
    border-bottom: 1px solid var(--border-color);
    padding: 0.5rem 1rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex-wrap: wrap;
}
.toolbar-title {
    font-size: 1rem;
    font-weight: 600;
    color: var(--text-secondary);
    margin-right: auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
.toolbar-title .filename {
    color: var(--link-color);
}
.toolbar-title .dirty {
    color: #dc3545;
    margin-left: 0.25rem;
}
.btn {
    padding: 0.4rem 0.9rem;
    font-size: 0.9rem;
    font-weight: 500;
    border: 1px solid var(--border-color);
    border-radius: 0.25rem;
    cursor: pointer;
    background-color: var(--bg-surface);
    color: var(--text-primary);
    transition: background-color 0.15s ease-in-out;
    text-decoration: none;
    white-space: nowrap;
}
.btn:hover { background-color: var(--bg-surface-alt); }
.btn-primary {
    background-color: var(--link-color);
    color: #fff;
    border-color: var(--link-color);
}
.btn-primary:hover {
    background-color: var(--link-hover);
    border-color: var(--link-hover);
}
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.editor-container {
    flex: 1;
    overflow: hidden;
    position: relative;
}
#editor {
    width: 100%;
    height: 100%;
}
.status-bar {
    background-color: var(--bg-surface-alt);
    border-top: 1px solid var(--border-color);
    padding: 0.25rem 1rem;
    font-size: 0.8rem;
    color: var(--text-muted);
    display: flex;
    gap: 1rem;
    align-items: center;
}
.status-message {
    margin-left: auto;
    transition: opacity 0.3s;
}
.status-message.success { color: #198754; }
.status-message.error { color: #dc3545; }
select.theme-selector, select.lang-selector {
    padding: 0.3rem 0.5rem;
    font-size: 0.85rem;
    color: var(--text-primary);
    background-color: var(--bg-surface);
    border: 1px solid var(--border-color);
    border-radius: 0.25rem;
    cursor: pointer;
}
</style>
<script src="/modules/theme.js"></script>
<script>
    if (typeof ThemeManager !== 'undefined') {
        ThemeManager.init();
    }
</script>
</head>
<body>
<div class="toolbar">
    <span class="toolbar-title">Editing: <span class="filename">$escapedFilename</span><span id="dirty-indicator" class="dirty" style="display:none;">●</span></span>
    <select id="lang-selector" class="lang-selector" title="Language">
    </select>
    <select id="theme-selector" class="theme-selector" title="Theme">
        <option value="auto">🌓 Auto</option>
        <option value="light">☀️ Light</option>
        <option value="dark">🌙 Dark</option>
    </select>
    <button id="save-btn" class="btn btn-primary" ${if (readOnly) "disabled title=\"File is read-only\"" else ""}>💾 Save</button>
    <a href="./" class="btn">↩ Back</a>
</div>
<div class="editor-container">
    <div id="editor"></div>
</div>
<div class="status-bar">
    <span id="status-position">Ln 1, Col 1</span>
    <span id="status-language">$language</span>
    ${if (readOnly) "<span style=\"color:#dc3545;\">READ-ONLY</span>" else ""}
    <span id="status-message" class="status-message"></span>
</div>

<script>
    var require = { paths: { vs: 'https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs' } };
</script>
<script src="https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/loader.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/editor/editor.main.nls.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs/editor/editor.main.min.js"></script>
<script>
    (function() {
        var initialContent = `$escapedContent`;
        var initialLanguage = '$language';
        var fileName = '$escapedFilename';
        var readOnly = ${if (readOnly) "true" else "false"};
        var originalContent = initialContent;
        var editor = null;

        function getEffectiveTheme() {
            var theme = document.documentElement.getAttribute('data-theme') || 'auto';
            if (theme === 'auto') {
                return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'vs-dark' : 'vs';
            }
            return theme === 'dark' ? 'vs-dark' : 'vs';
        }

        function updateDirty() {
            if (!editor) return;
            var current = editor.getValue();
            var dirty = current !== originalContent;
            document.getElementById('dirty-indicator').style.display = dirty ? 'inline' : 'none';
        }

        function showStatus(msg, type) {
            var el = document.getElementById('status-message');
            el.textContent = msg;
            el.className = 'status-message ' + (type || '');
            if (type === 'success') {
                setTimeout(function() {
                    if (el.textContent === msg) {
                        el.textContent = '';
                        el.className = 'status-message';
                    }
                }, 3000);
            }
        }

        function saveFile() {
            if (readOnly || !editor) return;
            var content = editor.getValue();
            var btn = document.getElementById('save-btn');
            btn.disabled = true;
            btn.textContent = '⏳ Saving...';
            showStatus('Saving...', '');
            fetch(window.location.pathname, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/octet-stream' },
                body: content
            }).then(function(response) {
                if (response.ok) {
                    originalContent = content;
                    updateDirty();
                    showStatus('Saved successfully', 'success');
                } else {
                    return response.text().then(function(text) {
                        throw new Error(text || response.statusText);
                    });
                }
            }).catch(function(err) {
                showStatus('Save failed: ' + err.message, 'error');
            }).finally(function() {
                btn.disabled = readOnly;
                btn.textContent = '💾 Save';
            });
        }

        require(['vs/editor/editor.main'], function() {
            editor = monaco.editor.create(document.getElementById('editor'), {
                value: initialContent,
                language: initialLanguage,
                theme: getEffectiveTheme(),
                automaticLayout: true,
                readOnly: readOnly,
                fontSize: 14,
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                renderWhitespace: 'selection',
                tabSize: 4
            });

            // Populate language selector
            var langSelector = document.getElementById('lang-selector');
            var languages = monaco.languages.getLanguages();
            languages.sort(function(a, b) { return a.id.localeCompare(b.id); });
            languages.forEach(function(lang) {
                var opt = document.createElement('option');
                opt.value = lang.id;
                opt.textContent = lang.id;
                if (lang.id === initialLanguage) opt.selected = true;
                langSelector.appendChild(opt);
            });
            langSelector.addEventListener('change', function() {
                var newLang = langSelector.value;
                monaco.editor.setModelLanguage(editor.getModel(), newLang);
                document.getElementById('status-language').textContent = newLang;
            });

            editor.onDidChangeModelContent(function() {
                updateDirty();
            });
            editor.onDidChangeCursorPosition(function(e) {
                document.getElementById('status-position').textContent =
                    'Ln ' + e.position.lineNumber + ', Col ' + e.position.column;
            });

            // Ctrl+S / Cmd+S
            editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, saveFile);

            // Update Monaco theme when site theme changes
            var observer = new MutationObserver(function() {
                monaco.editor.setTheme(getEffectiveTheme());
            });
            observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function() {
                monaco.editor.setTheme(getEffectiveTheme());
            });
        });

        document.getElementById('save-btn').addEventListener('click', saveFile);

        window.addEventListener('beforeunload', function(e) {
            if (!editor) return;
            if (editor.getValue() !== originalContent) {
                e.preventDefault();
                e.returnValue = '';
            }
        });

        window.addEventListener('DOMContentLoaded', function() {
            var themeSelector = document.getElementById('theme-selector');
            if (themeSelector && typeof ThemeManager !== 'undefined') {
                ThemeManager.bindSelector(themeSelector);
            }
        });
    })();
</script>
</body>
</html>
"""
        }
    }