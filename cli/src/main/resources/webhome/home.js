/*
   * Front page controller. It renders nothing that it invented itself: every value and
   * every link comes from GET /serverInfo, so the page can never disagree with the flags
   * the process was actually started with.
   */
  (function () {
    "use strict";

    const $ = (id) => document.getElementById(id);

    /* The page may be served from /home/, /proxy/home/ or an embedded context. */
    const CANDIDATES = ["../serverInfo", "./serverInfo", "/serverInfo"];

    function toast(message, isError) {
      const el = $("toast");
      if (!el) return;
      el.textContent = message;
      el.classList.toggle("error", !!isError);
      el.hidden = false;
      clearTimeout(toast._timer);
      toast._timer = setTimeout(() => { el.hidden = true; }, 4000);
    }

    async function loadInfo() {
      let lastError = null;
      for (const url of CANDIDATES) {
        try {
          const response = await fetch(url, {headers: {Accept: "application/json"}});
          if (!response.ok) continue;
          return await response.json();
        } catch (e) {
          lastError = e;
        }
      }
      if (lastError) console.warn("serverInfo unavailable", lastError);
      return null;
    }

    function row(term, value, extraClass) {
      const dt = document.createElement("dt");
      dt.textContent = term;
      const dd = document.createElement("dd");
      dd.textContent = (value === null || value === undefined || value === "") ? "—" : String(value);
      if (extraClass) dd.className = extraClass;
      return [dt, dd];
    }

    function renderConfig(info) {
      const grid = $("config-grid");
      grid.textContent = "";
      const modelCount = info.models && typeof info.models.available === "number"
        ? info.models.available : 0;
      const entries = [
        ["Served directory", info.servedDir],
        ["Bound to", `${info.host}:${info.port}`],
        ["Mode", info.readOnly ? "read-only" : "read-write"],
        ["Smart model", (info.models && info.models.smart) || "not selected"],
        ["Fast model", (info.models && info.models.fast) || "not selected"],
        ["Models available", modelCount ? `${modelCount} from your API keys` : "none (add a provider key)"],
        ["Signed in as", info.user || "anonymous"],
      ];
      entries.forEach(([term, value]) => {
        const cls = (term === "Mode" && info.readOnly) ? "muted" : null;
        row(term, value, cls).forEach((node) => grid.appendChild(node));
      });
    }

    function chip(label, enabled, offLabel) {
      const span = document.createElement("span");
      span.className = "chip " + (enabled ? "on" : "off");
      span.textContent = (enabled ? "● " : "○ ") + label + (enabled ? "" : ` (${offLabel || "off"})`);
      return span;
    }

    function renderCapabilities(info) {
      const box = $("capability-chips");
      box.textContent = "";
      [
        chip("IDE view", info.uiEnabled, "--no-ui"),
        chip("Writes & uploads", !info.readOnly, "--read-only"),
        chip("Git", info.gitEnabled, "--no-git"),
        chip("Terminal", info.terminalEnabled, "--no-terminal"),
        chip("Unrestricted exec", info.execPermissive, "allowlisted"),
        chip("DocOps / AutoFix", info.tasksEnabled, "--no-tasks"),
        chip("Patch chat", info.modifyEnabled, "--no-modify"),
      ].forEach((node) => box.appendChild(node));

      const loopback = info.host === "127.0.0.1" || info.host === "localhost" || info.host === "::1";
      const dangerous = !info.readOnly && (info.terminalEnabled || info.execPermissive || info.tasksEnabled);
      $("exposure-warning").hidden = !(dangerous && !loopback);
    }

    function renderLinks(info) {
      const paths = info.paths || {};
      const ui = $("card-ui");
      if (paths.ui) {
        ui.href = paths.ui;
        $("path-ui").textContent = paths.ui;
      }
      if (!info.uiEnabled) {
        ui.classList.add("disabled");
        ui.removeAttribute("href");
        $("note-ui").hidden = false;
      }
      if (paths.files) {
        $("card-files").href = paths.files;
        $("path-files").textContent = paths.files;
      }
    }

    function renderEndpoints(info) {
      const paths = info.paths || {};
      const origin = `http://${info.host === "0.0.0.0" || info.host === "::" ? "localhost" : info.host}:${info.port}`;
      const api = paths.fsApi || "/files/root/.fsapi/v1";
      const lines = [
        `# Describe the mount`,
        `curl ${origin}${api}/meta`,
        ``,
        `# Models (start-up flags are only the initial value)`,
        `curl ${origin}${api}/models`,
        `curl -X POST '${origin}${api}/models?smart=<id>&fast=<id>'`,
        ``,
        `# Settings, providers and keys (what settings.html drives)`,
        `curl ${origin}${paths.userSettings || "/userSettings"}/`,
      ];
      if (info.tasksEnabled) {
        lines.push(
          ``,
          `# DocOps / AutoFix - mutating commands return a task id`,
          `curl -X POST '${origin}${api}/docops?command=plan'`,
          `curl -X POST '${origin}${api}/autofix?cmd=./gradlew%20build'`,
          `curl ${origin}${api}/tasks`
        );
      }
      if (info.modifyEnabled) {
        lines.push(
          ``,
          `# Patch chat - opens a session against the selected files`,
          `curl -X POST '${origin}${api}/modify?path=src/Foo.kt'`
        );
      }
      $("endpoint-snippet").textContent = lines.join("\n");
    }

    function renderUnavailable() {
      $("served-dir").textContent =
        "Could not read the server configuration (/serverInfo). The links below use default paths.";
      $("config-grid").textContent = "";
      $("endpoint-snippet").textContent = "GET /serverInfo  # unavailable";
    }

    async function refresh(announce) {
      const info = await loadInfo();
      if (!info) {
        renderUnavailable();
        if (announce) toast("Server configuration unavailable", true);
        return;
      }
      $("served-dir").textContent =
        `Serving ${info.servedDir} on ${info.host}:${info.port} · ${info.readOnly ? "read-only" : "read-write"}`;
      renderLinks(info);
      renderConfig(info);
      renderCapabilities(info);
      renderEndpoints(info);
      if (announce) toast("Configuration reloaded");
    }

    $("reload-info").addEventListener("click", () => refresh(true));
    refresh(false);
  })();