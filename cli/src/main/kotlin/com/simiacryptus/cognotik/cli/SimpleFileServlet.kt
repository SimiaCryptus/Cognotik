package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.webui.servlet.FilesystemServlet
import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

open class SimpleFileServlet(
  private val baseDir: File,
  private val gitEnabled: Boolean,
  private val readOnly: Boolean = false,
  private val uiEnabled: Boolean = true,
  private val terminalEnabled: Boolean = true,
  /** true = any bare command may be spawned; false = allowlisted git only. */
  private val execPermissive: Boolean = true,
  private val maxTerminals: Int = 8,
  private val shell: List<String> = emptyList(),
  /** true = render the DocOps/AutoFix affordances (the actions must be installed). */
  private val tasksEnabled: Boolean = false,
  /** Pre-filled command in the AutoFix prompt. */
  private val defaultFixCommand: String = "",
  /** true = render the Modify (patch chat) affordances; requires ModifyFilesActions. */
  private val modifyEnabled: Boolean = false,
  /** Default state of the code-summary line numbering handed to the patch chat. */
  private val lineNumbers: Boolean = false,
) : FilesystemServlet() {
  override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File = baseDir
  override fun isGitEnabled(req: HttpServletRequest): Boolean = gitEnabled

  /**
   * The FS API is dispatched from service() and therefore bypasses the
   * doPost/doPut/doDelete overrides below; every capability (read-only mode,
   * exec, terminal) must be declared here.
   *
   * This is the *permissive* profile: it is what makes `POST /.fsapi/v1/terminal`
   * and the IDE view's terminal panel work. Tighten it for anything reachable
   * from a network.
   */
  override fun getFsApiConfig(req: HttpServletRequest) = FsApiConfig(
    readOnly = readOnly,
    /* Sub-command allowlist only matters in the hardened profile. */
    execAllowlist = if (!gitEnabled) emptyMap()
    else mapOf("git" to if (execPermissive) emptySet() else GIT_SUBCOMMANDS),
    execAllowAny = execPermissive,
    execRestrictArguments = !execPermissive,
    /* A terminal is a write operation: read-only mounts never get one. */
    terminalEnabled = terminalEnabled && !readOnly,
    maxTerminals = maxTerminals,
    terminalShell = shell,
  )

  override fun getZipLink(req: HttpServletRequest, filePath: String): String {
    val session = URLEncoder.encode(baseDir.name, StandardCharsets.UTF_8)
    val path = URLEncoder.encode(if (filePath.isBlank()) "/" else filePath, StandardCharsets.UTF_8)
    return "${req.contextPath}/zip?session=$session&path=$path"
  }

  /** docs/ui.md §21.3 — the classic listing links to the equivalent SPA path. */
  override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String {
    val ide = if (!uiEnabled) "" else {
      val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
      """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}${UI_PREFIX}/#$hash">🧭 Open in IDE view</a>"""
    }
    /* The IDE action worked on a folder selection too, so the toolbar offers the current dir. */
    val modify = if (!modifyEnabled || readOnly) "" else
      """<a class="zip-link" style="background-color:#198754;" href="#" onclick="return cognotikModify(event,null)">✏️ Modify files…</a>"""
    /* One selection drives every agentic surface, so it sits next to them. */
    val models = if (!tasksEnabled && !modifyEnabled) "" else
      """<a class="zip-link" style="background-color:#0dcaf0;color:#000;" href="#" onclick="return cognotikModels(event)">🧠 Models…</a>"""
    if (!tasksEnabled) return ide + modify + models
    val fix = if (readOnly) "" else
      """<a class="zip-link" style="background-color:#d63384;" href="#" onclick="return cognotikAutoFix(event)">🩺 AutoFix…</a>"""
    return ide + modify + models +
        """<a class="zip-link" style="background-color:#0d6efd;" href="#" onclick="return cognotikDocOps(event,'plan','')">📘 DocOps plan</a>""" +
        fix +
        """<a class="zip-link" style="background-color:#495057;" href="#" onclick="return cognotikTasks(event)">🗒 Tasks</a>"""
  }

  /** Markdown documents get direct DocOps entry points; every file gets a patch chat. */
  override fun getFileActions(file: File, req: HttpServletRequest): String {
    val rel = CliSupport.escapeJs(relativeToBase(file))
    val sb = StringBuilder()
    if (tasksEnabled && file.extension.lowercase() in setOf("md", "markdown")) {
      sb.append("""<a class="action-link" href="#" title="Plan doc-ops for this document" onclick="return cognotikDocOps(event,'plan','$rel')">📘 Plan</a>""")
      if (!readOnly) {
        sb.append("""<a class="action-link" href="#" title="Run doc-ops for this document" onclick="return cognotikDocOps(event,'run','$rel')">🚀 Run</a>""")
      }
    }
    if (modifyEnabled && !readOnly && file.isFile) {
      sb.append("""<a class="action-link" href="#" title="Open a patch chat for this file" onclick="return cognotikModify(event,'$rel')">✏️ Modify</a>""")
    }
    return sb.toString()
  }

  override fun getAdditionalSections(dir: File?, req: HttpServletRequest, currentPath: String): String {
    if (!tasksEnabled && !modifyEnabled) return ""
    val base = "${req.contextPath}${FILES_PREFIX}/${ROOT_SEGMENT}/.fsapi/v1"
    return """
            <script>
              window.COGNOTIK_FSAPI = "${CliSupport.escapeJs(base)}";
              window.COGNOTIK_PATH = "${CliSupport.escapeJs(currentPath)}";
              window.COGNOTIK_FIX_CMD = "${CliSupport.escapeJs(defaultFixCommand)}";
               window.COGNOTIK_LINE_NUMBERS = $lineNumbers;
            </script>
            <section id="cognotik-tasks" class="cognotik-tasks" style="display:none;">
              <h3 style="margin-top:0;">Cognotik tasks</h3>
              <div id="cognotik-task-status" class="cognotik-task-status">idle</div>
              <pre id="cognotik-task-output" class="cognotik-task-output"></pre>
            </section>
        """.trimIndent()
  }

  override fun getAdditionalStyles(): String {
    if (!tasksEnabled && !modifyEnabled) return ""
    return """
            .cognotik-tasks { margin: 1rem 0; padding: 0.75rem 1rem; border: 1px solid #6f42c1; border-radius: 6px; }
            .cognotik-task-status { font-weight: 600; margin-bottom: 0.5rem; }
            .cognotik-task-output { max-height: 24rem; overflow: auto; white-space: pre-wrap;
                font-family: monospace; font-size: 0.85rem; margin: 0; }
        """.trimIndent()
  }

  override fun getAdditionalScripts(): String {
    if (!tasksEnabled && !modifyEnabled) return ""
    /* Plain ES5, no template literals: this string is also a Kotlin raw string. */
    return """
            function cognotikPanel() {
              var el = document.getElementById('cognotik-tasks');
              if (el) { el.style.display = 'block'; }
              return el;
            }
            function cognotikSetOutput(text) {
              cognotikPanel();
              var pre = document.getElementById('cognotik-task-output');
              if (pre) { pre.textContent = text; pre.scrollTop = pre.scrollHeight; }
            }
            function cognotikStatus(text) {
              cognotikPanel();
              var el = document.getElementById('cognotik-task-status');
              if (el) { el.textContent = text; }
            }
            function cognotikUrl(op, params) {
              var url = (window.COGNOTIK_FSAPI || '') + '/' + op;
              var qs = [];
              for (var k in params) {
                if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
                var v = params[k];
                if (v === null || v === undefined || v === '') continue;
                if (Object.prototype.toString.call(v) === '[object Array]') {
                  for (var i = 0; i < v.length; i++) {
                    qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v[i]));
                  }
                } else {
                  qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
                }
              }
              if (qs.length) url += '?' + qs.join('&');
              return url;
            }
            function cognotikCall(method, op, params) {
              return fetch(cognotikUrl(op, params), { method: method, headers: { 'X-Fs-Api': '1' } })
                .then(function (r) {
                  return r.json().catch(function () { return {}; })
                    .then(function (j) { return { status: r.status, body: j }; });
                });
            }
            function cognotikRender(res) {
              var body = (res && res.body) || {};
              if (body.error) {
                cognotikStatus('error: ' + (body.error.code || '') + ' ' + (body.error.message || ''));
                cognotikSetOutput(JSON.stringify(body.error, null, 2));
                return null;
              }
              var task = body.task || body;
              var code = (task.exitCode === null || task.exitCode === undefined) ? '' : ' (exit ' + task.exitCode + ')';
              cognotikStatus('[' + task.id + '] ' + task.kind + ' ' + task.label + ' - ' + task.state + code);
              cognotikSetOutput(task.output || '(no output yet)');
              return task;
            }
            function cognotikPoll(id) {
              cognotikCall('GET', 'tasks', { id: id }).then(function (res) {
                var task = cognotikRender(res);
                if (task && task.state === 'running') {
                  setTimeout(function () { cognotikPoll(id); }, 1500);
                }
              }).catch(function (e) { cognotikStatus('poll failed: ' + e); });
            }
            function cognotikDocOps(ev, command, path) {
              if (ev) ev.preventDefault();
              cognotikStatus('docops ' + command + (path ? ' ' + path : '') + ' ...');
              cognotikSetOutput('');
              cognotikCall('POST', 'docops', { command: command, path: path }).then(function (res) {
                var task = cognotikRender(res);
                if (task && task.state === 'running') cognotikPoll(task.id);
              }).catch(function (e) { cognotikStatus('request failed: ' + e); });
              return false;
            }
            function cognotikAutoFix(ev) {
              if (ev) ev.preventDefault();
              var cmd = window.prompt('Command to run and fix:', window.COGNOTIK_FIX_CMD || '');
              if (!cmd) return false;
              window.COGNOTIK_FIX_CMD = cmd;
              cognotikStatus('autofix: ' + cmd + ' ...');
              cognotikSetOutput('');
              cognotikCall('POST', 'autofix', { cmd: cmd, dir: window.COGNOTIK_PATH || '' }).then(function (res) {
                var task = cognotikRender(res);
                if (task && task.state === 'running') cognotikPoll(task.id);
              }).catch(function (e) { cognotikStatus('request failed: ' + e); });
              return false;
            }
            function cognotikTasks(ev) {
              if (ev) ev.preventDefault();
              cognotikCall('GET', 'tasks', {}).then(function (res) {
                var list = ((res && res.body) || {}).tasks || [];
                cognotikStatus(list.length + ' task(s) - click an id below and poll with ?id=');
                cognotikSetOutput(list.map(function (t) {
                  return t.id + '  ' + t.state + '  ' + t.kind + '  ' + t.label;
                }).join('\n') || '(no tasks yet)');
              }).catch(function (e) { cognotikStatus('request failed: ' + e); });
              return false;
            }
            /* An empty answer means "leave unchanged": cognotikUrl drops blank values. */
            function cognotikModels(ev) {
              if (ev) ev.preventDefault();
              cognotikStatus('loading models ...');
              cognotikCall('GET', 'models', {}).then(function (res) {
                var body = (res && res.body) || {};
                if (body.error) {
                  cognotikStatus('error: ' + (body.error.message || ''));
                  return;
                }
                var list = body.available || [];
                cognotikStatus('models: smart=' + (body.smart || 'none') + ' fast=' + (body.fast || 'none'));
                cognotikSetOutput(list.length ? list.join('\n') : '(no providers configured)');
                var head = list.length ? 'Available:\n' + list.join('\n') + '\n\n' : '';
                var smart = window.prompt(head + 'Smart model (blank = keep "' + (body.smart || 'none') + '"):', body.smart || '');
                if (smart === null) return;
                var fast = window.prompt('Fast model (blank = keep "' + (body.fast || 'none') + '"):', body.fast || '');
                if (fast === null) return;
                cognotikCall('POST', 'models', { smart: smart, fast: fast }).then(function (r) {
                  var next = (r && r.body) || {};
                  if (next.error) {
                    cognotikStatus('error: ' + (next.error.message || ''));
                    return;
                  }
                  cognotikStatus(next.message || ('models: smart=' + (next.smart || 'none') + ' fast=' + (next.fast || 'none')));
                  cognotikSetOutput(JSON.stringify({ smart: next.smart, fast: next.fast }, null, 2));
                }).catch(function (e) { cognotikStatus('request failed: ' + e); });
              }).catch(function (e) { cognotikStatus('request failed: ' + e); });
              return false;
            }
        """.trimIndent()
  }

    private fun relativeToBase(file: File): String = try {
    file.canonicalFile.relativeTo(baseDir.canonicalFile).path.replace(File.separatorChar, '/')
  } catch (e: Exception) {
    file.name
  }

  companion object {

    const val ROOT_SEGMENT = "root"
    const val FILES_PREFIX = "/files"
    const val UI_PREFIX = "/ui"
  }
}