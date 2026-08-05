/**
     * Monaco editor helpers for the Role Research app.
     *
     * Monaco is vendored locally under `/lib/monaco/vs` (see lib/dir.txt); this
     * module owns the whole loading dance so callers never have to touch the AMD
     * loader, workers, or CSS:
     *
     *   - the `vs` base is resolved relative to THIS module, so the app works no
     *     matter what path it is served from (and can be overridden by setting
     *     `window.MONACO_VS_BASE` before the first `ensureMonaco()` call);
     *   - `vs/loader.js` is injected on demand if no AMD loader is present;
     *   - workers are same-origin, so no `data:`/`blob:` bootstrap proxy is needed.
     */

    /** Default: `<this module's dir>/monaco/vs` → `/lib/monaco/vs`. */
    const DEFAULT_VS_BASE = new URL("./monaco/vs", import.meta.url).href.replace(/\/+$/, "");

    /** How long to wait for `vs/editor/editor.main` once the loader is in place. */
    const LOAD_TIMEOUT_MS = 20000;

    let monacoReadyPromise = null;

    /** Allow a host page to relocate the vendored copy without editing this file. */
    export function getVsBase() {
      const override =
        typeof window !== "undefined" && typeof window.MONACO_VS_BASE === "string"
          ? window.MONACO_VS_BASE
          : null;
      return (override || DEFAULT_VS_BASE).replace(/\/+$/, "");
    }

    function loadScript(src) {
      return new Promise((resolve, reject) => {
        const existing = document.querySelector(`script[data-monaco-loader="${src}"]`);
        if (existing) {
          if (existing.dataset.loaded === "1") resolve();
          else {
            existing.addEventListener("load", () => resolve(), { once: true });
            existing.addEventListener(
              "error",
              () => reject(new Error(`Failed to load ${src}`)),
              { once: true },
            );
          }
          return;
        }
        const s = document.createElement("script");
        s.src = src;
        s.async = false;
        s.dataset.monacoLoader = src;
        s.addEventListener(
          "load",
          () => {
            s.dataset.loaded = "1";
            resolve();
          },
          { once: true },
        );
        s.addEventListener(
          "error",
          () => reject(new Error(`Failed to load ${src}`)),
          { once: true },
        );
        (document.head ?? document.documentElement).appendChild(s);
      });
    }

    /**
     * The AMD bundle pulls its own CSS through the loader's `vs/css!` plugin, but
     * adding the stylesheet up-front avoids a flash of unstyled editor and keeps
     * things working if that plugin behaviour changes between releases.
     */
    function ensureEditorCss(base) {
      const href = `${base}/editor/editor.main.css`;
      if (document.querySelector(`link[data-monaco-css="1"][href="${href}"]`)) return;
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = href;
      link.dataset.monacoCss = "1";
      (document.head ?? document.documentElement).appendChild(link);
    }

    /**
     * Workers are now served from our own origin, so the worker script can be
     * referenced directly — no cross-origin `importScripts` bootstrap required.
     */
    function configureWorkers(base) {
      const existing = window.MonacoEnvironment;
      // Respect a host-provided environment (e.g. bundled workers).
      if (existing && typeof existing.getWorkerUrl === "function") return;
      if (existing && typeof existing.getWorker === "function") return;
      window.MonacoEnvironment = {
        ...(existing || {}),
        baseUrl: `${base}/`,
        getWorkerUrl() {
          return `${base}/base/worker/workerMain.js`;
        },
      };
    }

    // Resolve once the global `monaco` namespace is available.
    export function ensureMonaco() {
      if (monacoReadyPromise) return monacoReadyPromise;

      monacoReadyPromise = (async () => {
        if (typeof window === "undefined" || typeof document === "undefined") {
          throw new Error("Monaco can only be loaded in a browser context.");
        }
        // Fast path: already present (another app on the page loaded it).
        if (window.monaco && window.monaco.editor) return window.monaco;

        const base = getVsBase();
        configureWorkers(base);
        ensureEditorCss(base);

        // Inject the AMD loader unless the page already provides one.
        if (!(window.require && typeof window.require.config === "function")) {
          await loadScript(`${base}/loader.js`);
        }
        const amdRequire = window.require;
        if (!amdRequire || typeof amdRequire.config !== "function") {
          throw new Error(`Monaco AMD loader did not initialize (${base}/loader.js).`);
        }
        amdRequire.config({ paths: { vs: base } });

        await new Promise((resolve, reject) => {
          const timer = setTimeout(
            () => reject(new Error(`Monaco editor failed to load from ${base} (timeout).`)),
            LOAD_TIMEOUT_MS,
          );
          const done = () => {
            clearTimeout(timer);
            resolve();
          };
          const fail = (err) => {
            clearTimeout(timer);
            reject(
              err instanceof Error
                ? err
                : new Error(`Monaco editor failed to load from ${base}: ${String(err)}`),
            );
          };
          try {
            amdRequire(["vs/editor/editor.main"], done, fail);
          } catch (err) {
            fail(err);
          }
        });

        if (!(window.monaco && window.monaco.editor)) {
          throw new Error("vs/editor/editor.main loaded but window.monaco is missing.");
        }
        return window.monaco;
      })();

      // A failed attempt should not poison later retries (e.g. after a reconnect).
      monacoReadyPromise.catch(() => {
        monacoReadyPromise = null;
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
        case "javascript":
          return "javascript";
        case "ts":
        case "typescript":
          return "typescript";
        case "html":
          return "html";
        case "css":
          return "css";
        case "yaml":
        case "yml":
          return "yaml";
        case "sh":
        case "bash":
        case "shell":
          return "shell";
        case "py":
        case "python":
          return "python";
        default:
          return "plaintext";
      }
    }

    /**
     * The vendored `basic-languages` set does not include every id we hand out
     * (notably `latex`), so degrade unknown ids to `plaintext` instead of letting
     * Monaco silently create a model with no tokenizer.
     */
    function resolveLanguage(monaco, lang) {
      const id = lang || "plaintext";
      try {
        const known = monaco.languages.getLanguages().some((l) => l.id === id);
        return known ? id : "plaintext";
      } catch {
        return id;
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
        language: resolveLanguage(monaco, opts.language),
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
          if (model) monaco.editor.setModelLanguage(model, resolveLanguage(monaco, lang));
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