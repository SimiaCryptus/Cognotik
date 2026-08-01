/**
   * Monaco editor helpers for the Role Research app.
   *
   * Provides a thin promise-based wrapper around the global `monaco` object
   * (loaded via the AMD loader in app.html) so the rest of the app can create
   * and manage editor instances without dealing with the loader directly.
   */

  let monacoReadyPromise = null;

  // Resolve once the global `monaco` namespace is available. The AMD loader
  // and editor.main are included from app.html; we still guard against load
  // ordering by polling briefly if necessary.
  export function ensureMonaco() {
    if (monacoReadyPromise) return monacoReadyPromise;
    monacoReadyPromise = new Promise((resolve, reject) => {
      // Fast path: already present.
      if (typeof window !== "undefined" && window.monaco && window.monaco.editor) {
        resolve(window.monaco);
        return;
      }
      // If the AMD loader is present, use it to require the editor.
      if (typeof window !== "undefined" && window.require && typeof window.require.config === "function") {
        try {
           const vsBase = "https://cdn.jsdelivr.net/npm/monaco-editor@0.55.1/min/vs";
          window.require.config({
            paths: {
               vs: vsBase,
            },
          });
           // Cross-origin workers can't be created directly from a CDN URL.
           // Provide a same-origin bootstrap that imports the CDN worker and
           // points the loader base back at the CDN.
           window.MonacoEnvironment = {
             getWorkerUrl: function () {
               const proxy =
                 "self.MonacoEnvironment={baseUrl:'" + vsBase + "/'};" +
                 "importScripts('" + vsBase + "/base/worker/workerMain.js');";
               return (
                 "data:text/javascript;charset=utf-8," +
                 encodeURIComponent(proxy)
               );
             },
           };
          window.require(["vs/editor/editor.main"], () => {
            resolve(window.monaco);
          });
          return;
        } catch (_) {
          /* fall through to polling */
        }
      }
      // Fallback: poll for the global to appear.
      const start = Date.now();
      const timer = setInterval(() => {
        if (window.monaco && window.monaco.editor) {
          clearInterval(timer);
          resolve(window.monaco);
        } else if (Date.now() - start > 15000) {
          clearInterval(timer);
          reject(new Error("Monaco editor failed to load."));
        }
      }, 50);
    });
    return monacoReadyPromise;
  }

  // Map a file extension / hint to a Monaco language id.
  export function languageForHint(hint) {
    switch ((hint || "").toLowerCase()) {
      case "tex":
      case "latex":
        return "latex";
      case "json":
        return "json";
      case "md":
      case "markdown":
        return "markdown";
      case "js":
        return "javascript";
      case "html":
        return "html";
      case "css":
        return "css";
      default:
        return "plaintext";
    }
  }

  /**
   * Create a Monaco editor inside `container`.
   *
   * Returns an object exposing a textarea-like interface (`value` getter/
   * setter, `getValue`, `setValue`, `onChange`, `dispose`, `layout`).
   */
  export async function createEditor(container, options) {
    const monaco = await ensureMonaco();
    const opts = options || {};
    const editor = monaco.editor.create(container, {
      value: opts.value || "",
      language: opts.language || "plaintext",
      readOnly: !!opts.readOnly,
      automaticLayout: true,
      minimap: { enabled: false },
      wordWrap: opts.wordWrap || "on",
      scrollBeyondLastLine: false,
      fontSize: 12,
      lineNumbers: opts.lineNumbers || "on",
      renderLineHighlight: "line",
      theme: opts.theme || "vs",
      tabSize: 2,
    });

    const handle = {
      editor,
      get value() {
        return editor.getValue();
      },
      set value(v) {
        editor.setValue(v == null ? "" : String(v));
      },
      getValue() {
        return editor.getValue();
      },
      setValue(v) {
        editor.setValue(v == null ? "" : String(v));
      },
      setReadOnly(ro) {
        editor.updateOptions({ readOnly: !!ro });
      },
      setLanguage(lang) {
        const model = editor.getModel();
        if (model) monaco.editor.setModelLanguage(model, lang);
      },
      onChange(cb) {
        return editor.onDidChangeModelContent(() => cb(editor.getValue()));
      },
      layout() {
        editor.layout();
      },
      dispose() {
        editor.dispose();
      },
    };
    return handle;
  }