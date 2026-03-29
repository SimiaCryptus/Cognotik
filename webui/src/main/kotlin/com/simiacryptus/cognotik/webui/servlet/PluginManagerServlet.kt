package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

/*
*  /pluginManager
* */
@MultipartConfig(
  maxFileSize = 50 * 1024 * 1024L,       // 50 MB per file
  maxRequestSize = 100 * 1024 * 1024L     // 100 MB per request
)
class PluginManagerServlet(
) : HttpServlet() {
  private val pluginDirectory: File = File("./plugins")

  init {
    pluginDirectory.mkdirs()
    log.info("PluginManagerServlet initialized with plugin directory: {}", pluginDirectory.canonicalPath)
    // Ensure temp directory for multipart uploads exists
    File(System.getProperty("java.io.tmpdir")).mkdirs()
    log.debug("Temp directory for multipart uploads: {}", System.getProperty("java.io.tmpdir"))
  }

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    log.info(
      "Received GET request from {} - URI: {}, QueryString: {}",
      request.remoteAddr,
      request.requestURI,
      request.queryString
    )
    val user = authenticate(request, response) ?: return
    log.debug("Authenticated user: {}", user)
    if (!ApplicationServices.authorizationManager.isAuthorized(
        PluginManagerServlet::class.java,
        user,
        OperationType.Admin
      )
    ) {
      log.warn("Unauthorized access attempt by user: {} from IP: {}", user, request.remoteAddr)
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
      return
    }

    val acceptHeader = request.getHeader("Accept") ?: ""
    val action = request.getParameter("action")
    log.debug("GET action: {}, Accept header: {}", action, acceptHeader)

    when {
      action == "list" || acceptHeader.contains("application/json") -> {
        log.info("Listing loaded plugins")
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        val loadedPlugins = ApplicationServices.pluginManager.getLoadedPlugins()
        log.debug("Found {} loaded plugin JARs", loadedPlugins.size)
        val pluginData = loadedPlugins.map { (jarPath, plugins) ->
          log.trace("Loaded JAR: {} with {} plugins", jarPath, plugins.size)
          mapOf(
            "jar" to jarPath,
            "plugins" to plugins.map { plugin ->
              mapOf(
                "name" to plugin.pluginName,
                "class" to plugin.javaClass.name
              )
            }
          )
        }
        response.writer.write(JsonUtil.toJson(pluginData))
        log.info("Successfully returned list of {} loaded plugin JARs", loadedPlugins.size)
      }

      action == "scan" -> {
        log.info("Scanning plugin directory: {}", pluginDirectory.canonicalPath)
        response.contentType = "application/json"
        response.status = HttpServletResponse.SC_OK
        val jarFiles = pluginDirectory.listFiles { f -> f.name.endsWith(".jar") } ?: emptyArray()
        log.debug("Found {} JAR files in plugin directory", jarFiles.size)
        val available = jarFiles.map { f ->
          val isLoaded = ApplicationServices.pluginManager.isLoaded(f)
          log.trace("JAR file: {} (size: {} bytes, loaded: {})", f.name, f.length(), isLoaded)
          mapOf(
            "name" to f.name,
            "path" to f.canonicalPath,
            "size" to f.length(),
            "loaded" to isLoaded
          )
        }
        response.writer.write(JsonUtil.toJson(available))
        log.info("Successfully scanned directory, found {} JAR files", jarFiles.size)
      }

      else -> {
        log.info("Serving Plugin Manager HTML page")
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK
        response.writer.write(renderHtml())
      }
    }
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    log.info(
      "Received POST request from {} - URI: {}, ContentType: {}",
      request.remoteAddr,
      request.requestURI,
      request.contentType
    )
    val user = authenticate(request, response) ?: return
    log.debug("Authenticated user for POST: {}", user)
    if (!ApplicationServices.authorizationManager.isAuthorized(
        PluginManagerServlet::class.java,
        user,
        OperationType.Admin
      )
    ) {
      log.warn("Unauthorized POST access attempt by user: {} from IP: {}", user, request.remoteAddr)
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin access required")
      return
    }

    response.contentType = "application/json"

    // For multipart requests, we need to handle content type detection
    val contentType = request.contentType ?: ""
    log.debug("Request content type: {}", contentType)
    val action = try {
      if (contentType.contains("multipart/form-data", ignoreCase = true)) {
        log.debug("Processing multipart/form-data request")
        // For multipart, try getPart first for the action field
        try {
          val actionPart = request.getPart("action")
          val resolvedAction =
            actionPart?.inputStream?.bufferedReader()?.readText()?.trim() ?: request.getParameter("action") ?: ""
          log.debug("Resolved action from multipart request: '{}'", resolvedAction)
          resolvedAction
        } catch (e: Exception) {
          log.debug("Failed to get action from multipart part, falling back to parameter: {}", e.message)
          val fallbackAction = request.getParameter("action") ?: ""
          log.debug("Fallback action: '{}'", fallbackAction)
          fallbackAction
        }
      } else {
        val paramAction = request.getParameter("action") ?: ""
        log.debug("Action from request parameter: '{}'", paramAction)
        paramAction
      }
    } catch (e: Exception) {
      log.error("Failed to parse request action", e)
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Failed to parse request: ${e.message?.replace("\"", "\\\"")}"}""")
      return
    }
    log.info("Processing POST action: '{}' from user: {}", action, user)

    when (action) {
      "load" -> handleLoad(request, response)
      "unload" -> handleUnload(request, response)
      "upload" -> handleUpload(request, response)
      "loadDirectory" -> handleLoadDirectory(request, response)
      else -> {
        log.warn("Unknown POST action received: '{}'", action)
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("""{"error":"Unknown action: $action"}""")
      }
    }
  }

  private fun handleLoad(request: HttpServletRequest, response: HttpServletResponse) {
    val jarPath = request.getParameter("jar")
    val entryPoint = request.getParameter("entryPoint")
    log.info("handleLoad called - jarPath: {}, entryPoint: {}", jarPath, entryPoint)

    if (jarPath.isNullOrBlank()) {
      log.warn("handleLoad: Missing 'jar' parameter")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'jar' parameter"}""")
      return
    }

    val jarFile = File(jarPath).let {
      if (it.isAbsolute) it else File(pluginDirectory, jarPath)
    }
    log.debug(
      "Resolved JAR file path: {}, exists: {}, size: {} bytes",
      jarFile.canonicalPath,
      jarFile.exists(),
      if (jarFile.exists()) jarFile.length() else "N/A"
    )

    response.contentType = "application/json"
    try {
      val plugins = if (!entryPoint.isNullOrBlank()) {
        log.info("Loading plugin from JAR: {} with entry point: {}", jarFile.canonicalPath, entryPoint)
        listOf(ApplicationServices.pluginManager.loadPlugin(jarFile, entryPoint))
      } else {
        log.info("Loading all plugins from JAR: {}", jarFile.canonicalPath)
        ApplicationServices.pluginManager.loadPlugin(jarFile)
      }
      log.info(
        "Successfully loaded {} plugin(s) from JAR: {} - plugins: {}",
        plugins.size,
        jarFile.canonicalPath,
        plugins.map { it.pluginName })
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true,
            "jar" to jarFile.canonicalPath,
            "pluginsLoaded" to plugins.size,
            "plugins" to plugins.map { it.pluginName }
          )
        )
      )
    } catch (e: IllegalStateException) {
      log.warn("Plugin already loaded: {}", jarPath)
      response.status = HttpServletResponse.SC_CONFLICT
      response.writer.write("""{"error":"${e.message?.replace("\"", "\\\"")}"}""")
    } catch (e: Exception) {
      log.error("Failed to load plugin JAR: {}", jarPath, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${e.message?.replace("\"", "\\\"")}"}""")
    }
  }

  private fun handleUnload(request: HttpServletRequest, response: HttpServletResponse) {
    val jarPath = request.getParameter("jar")
    log.info("handleUnload called - jarPath: {}", jarPath)

    if (jarPath.isNullOrBlank()) {
      log.warn("handleUnload: Missing 'jar' parameter")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Missing 'jar' parameter"}""")
      return
    }

    val jarFile = File(jarPath).let {
      if (it.isAbsolute) it else File(pluginDirectory, jarPath)
    }
    log.debug("Resolved JAR file path for unload: {}", jarFile.canonicalPath)

    response.contentType = "application/json"
    try {
      log.info("Unloading plugin JAR: {}", jarFile.canonicalPath)
      ApplicationServices.pluginManager.unloadPlugin(jarFile)
      log.info("Successfully unloaded plugin JAR: {}", jarFile.canonicalPath)
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true,
            "jar" to jarFile.canonicalPath
          )
        )
      )
    } catch (e: Exception) {
      log.error("Failed to unload plugin JAR: {}", jarPath, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${e.message?.replace("\"", "\\\"")}"}""")
    }
  }

  private fun handleUpload(request: HttpServletRequest, response: HttpServletResponse) {
    log.info("handleUpload called")
    val part = try {
      request.getPart("jarFile")
    } catch (e: Exception) {
      log.error("Failed to get uploaded file part", e)
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Failed to read uploaded file: ${e.message?.replace("\"", "\\\"")}"}""")
      return
    }

    if (part == null) {
      log.warn("handleUpload: No file part found in request")
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"No file uploaded (expected part named 'jarFile')"}""")
      return
    }

    val submittedFileName = part.submittedFileName ?: "plugin.jar"
    log.debug(
      "Uploaded file name: {}, size: {} bytes, content type: {}",
      submittedFileName,
      part.size,
      part.contentType
    )
    if (!submittedFileName.endsWith(".jar")) {
      log.warn("handleUpload: Uploaded file is not a JAR: {}", submittedFileName)
      response.contentType = "application/json"
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("""{"error":"Uploaded file must be a JAR"}""")
      return
    }

    val destFile = File(pluginDirectory, submittedFileName)
    log.debug("Destination file for upload: {}", destFile.canonicalPath)
    response.contentType = "application/json"
    try {
      part.inputStream.use { input ->
        Files.copy(input, destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      log.info("Plugin JAR uploaded successfully: {} ({} bytes)", destFile.canonicalPath, destFile.length())
      val autoLoad = request.getParameter("autoLoad")?.equals("true", ignoreCase = true) ?: false
      log.debug("Auto-load after upload: {}", autoLoad)
      if (autoLoad) {
        log.info("Auto-loading uploaded plugin JAR: {}", destFile.canonicalPath)
        val plugins = ApplicationServices.pluginManager.loadPlugin(destFile)
        log.info(
          "Auto-loaded {} plugin(s) from uploaded JAR: {} - plugins: {}",
          plugins.size,
          destFile.canonicalPath,
          plugins.map { it.pluginName })
        response.status = HttpServletResponse.SC_OK
        response.writer.write(
          JsonUtil.toJson(
            mapOf(
              "success" to true,
              "file" to destFile.name,
              "path" to destFile.canonicalPath,
              "autoLoaded" to true,
              "pluginsLoaded" to plugins.size,
              "plugins" to plugins.map { it.pluginName }
            )
          )
        )
      } else {
        log.info("Plugin JAR uploaded without auto-load: {}", destFile.canonicalPath)
        response.status = HttpServletResponse.SC_OK
        response.writer.write(
          JsonUtil.toJson(
            mapOf(
              "success" to true,
              "file" to destFile.name,
              "path" to destFile.canonicalPath,
              "autoLoaded" to false
            )
          )
        )
      }
    } catch (e: Exception) {
      log.error("Failed to save or load uploaded plugin JAR: {}", submittedFileName, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${e.message?.replace("\"", "\\\"")}"}""")
    }
  }

  private fun handleLoadDirectory(request: HttpServletRequest, response: HttpServletResponse) {
    val dirPath = request.getParameter("directory")
    val directory = if (!dirPath.isNullOrBlank()) File(dirPath) else pluginDirectory
    log.info(
      "handleLoadDirectory called - directory: {}, exists: {}, isDirectory: {}",
      directory.canonicalPath,
      directory.exists(),
      directory.isDirectory
    )

    response.contentType = "application/json"
    try {
      log.info("Loading all plugins from directory: {}", directory.canonicalPath)
      val results = ApplicationServices.pluginManager.loadPluginsFromDirectory(directory)
      log.info("Loaded plugins from {} JAR(s) in directory: {}", results.size, directory.canonicalPath)
      val summary = results.map { (file, plugins) ->
        log.debug(
          "Directory load result - JAR: {}, plugins loaded: {}, plugin names: {}",
          file.canonicalPath,
          plugins.size,
          plugins.map { it.pluginName })
        mapOf(
          "jar" to file.canonicalPath,
          "pluginsLoaded" to plugins.size,
          "plugins" to plugins.map { it.pluginName }
        )
      }
      response.status = HttpServletResponse.SC_OK
      response.writer.write(
        JsonUtil.toJson(
          mapOf(
            "success" to true,
            "directory" to directory.canonicalPath,
            "jarsProcessed" to results.size,
            "results" to summary
          )
        )
      )
      log.info("Successfully processed {} JAR(s) from directory: {}", results.size, directory.canonicalPath)
    } catch (e: Exception) {
      log.error("Failed to load plugins from directory: {}", directory, e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("""{"error":"${e.message?.replace("\"", "\\\"")}"}""")
    }
  }

  private fun renderHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
            <title>Plugin Manager</title>
            <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
            <style>
                body { font-family: Arial, sans-serif; margin: 2em; background: #f5f5f5; color: #333; }
                h1 { color: #444; }
                h2 { color: #555; border-bottom: 1px solid #ccc; padding-bottom: 0.3em; }
                .card { background: #fff; border-radius: 6px; padding: 1.5em; margin-bottom: 1.5em;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
                table { width: 100%; border-collapse: collapse; }
                th, td { text-align: left; padding: 0.5em 0.75em; border-bottom: 1px solid #eee; }
                th { background: #f0f0f0; font-weight: bold; }
                tr:hover td { background: #fafafa; }
                .badge { display: inline-block; padding: 0.2em 0.6em; border-radius: 3px;
                         font-size: 0.8em; font-weight: bold; }
                .badge-loaded { background: #d4edda; color: #155724; }
                .badge-unloaded { background: #f8d7da; color: #721c24; }
                button, input[type=submit] {
                    padding: 0.4em 1em; border: none; border-radius: 4px; cursor: pointer;
                    font-size: 0.9em; margin: 0.2em;
                }
                .btn-primary { background: #007bff; color: #fff; }
                .btn-primary:hover { background: #0056b3; }
                .btn-danger { background: #dc3545; color: #fff; }
                .btn-danger:hover { background: #a71d2a; }
                .btn-success { background: #28a745; color: #fff; }
                .btn-success:hover { background: #1e7e34; }
                .btn-secondary { background: #6c757d; color: #fff; }
                .btn-secondary:hover { background: #545b62; }
                input[type=text], input[type=file] {
                    padding: 0.4em; border: 1px solid #ccc; border-radius: 4px;
                    font-size: 0.9em; width: 300px;
                }
                #message { padding: 0.75em 1em; border-radius: 4px; margin-bottom: 1em; display: none; }
                .msg-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
                .msg-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
                .msg-info { background: #d1ecf1; color: #0c5460; border: 1px solid #bee5eb; }
                pre { background: #f8f9fa; padding: 0.75em; border-radius: 4px;
                      font-size: 0.85em; overflow-x: auto; }
            </style>
        </head>
        <body>
            <h1>🔌 Plugin Manager</h1>

            <div id="message"></div>

            <!-- Loaded Plugins -->
            <div class="card">
                <h2>Loaded Plugins
                    <button class="btn-secondary" style="float:right;font-size:0.8em" onclick="refreshLoaded()">↻ Refresh</button>
                </h2>
                <div id="loadedPlugins"><em>Loading…</em></div>
            </div>

            <!-- Available JARs -->
            <div class="card">
                <h2>Available JARs in Plugin Directory
                    <button class="btn-secondary" style="float:right;font-size:0.8em" onclick="scanDirectory()">↻ Scan</button>
                </h2>
                <div id="availableJars"><em>Click Scan to list JARs…</em></div>
                <br/>
                <button class="btn-success" onclick="loadDirectory()">Load All from Directory</button>
            </div>

            <!-- Load by Path -->
            <div class="card">
                <h2>Load Plugin by Path</h2>
                <label>JAR path (relative to plugin directory or absolute):<br/>
                    <input type="text" id="jarPath" placeholder="myplugin.jar"/>
                </label><br/><br/>
                <label>Entry point class (optional):<br/>
                    <input type="text" id="entryPoint" placeholder="com.example.MyPlugin"/>
                </label><br/><br/>
                <button class="btn-primary" onclick="loadPlugin()">Load Plugin</button>
            </div>

            <!-- Upload JAR -->
            <div class="card">
                <h2>Upload Plugin JAR</h2>
                <form id="uploadForm" enctype="multipart/form-data">
                    <label>Select JAR file:<br/>
                        <input type="file" id="jarFile" name="jarFile" accept=".jar"/>
                    </label><br/><br/>
                    <label>
                        <input type="checkbox" id="autoLoad" name="autoLoad" value="true"/>
                        Auto-load after upload
                    </label><br/><br/>
                    <button type="button" class="btn-primary" onclick="uploadPlugin()">Upload</button>
                </form>
            </div>

            <script>
                function showMessage(text, type) {
                    const el = document.getElementById('message');
                    el.textContent = text;
                    el.className = 'msg-' + type;
                    el.style.display = 'block';
                    setTimeout(() => { el.style.display = 'none'; }, 6000);
                }

                function refreshLoaded() {
                    fetch('/pluginManager?action=list', { headers: { 'Accept': 'application/json' } })
                        .then(r => r.json())
                        .then(data => {
                            const container = document.getElementById('loadedPlugins');
                            if (!data || data.length === 0) {
                                container.innerHTML = '<em>No plugins currently loaded.</em>';
                                return;
                            }
                            let html = '<table><thead><tr><th>JAR</th><th>Plugins</th><th>Actions</th></tr></thead><tbody>';
                            data.forEach(entry => {
                                const pluginNames = entry.plugins.map(p => p.name + ' <small>(' + p.class + ')</small>').join('<br/>');
                                html += '<tr>'
                                    + '<td><code>' + escHtml(entry.jar) + '</code></td>'
                                    + '<td>' + (pluginNames || '<em>none</em>') + '</td>'
                                    + '<td><button class="btn-danger" onclick="unloadPlugin(' + JSON.stringify(entry.jar) + ')">Unload</button></td>'
                                    + '</tr>';
                            });
                            html += '</tbody></table>';
                            container.innerHTML = html;
                        })
                        .catch(e => {
                            document.getElementById('loadedPlugins').innerHTML = '<em>Error loading data.</em>';
                            showMessage('Error fetching loaded plugins: ' + e, 'error');
                        });
                }

                function scanDirectory() {
                    fetch('/pluginManager?action=scan', { headers: { 'Accept': 'application/json' } })
                        .then(r => r.json())
                        .then(data => {
                            const container = document.getElementById('availableJars');
                            if (!data || data.length === 0) {
                                container.innerHTML = '<em>No JAR files found in plugin directory.</em>';
                                return;
                            }
                            let html = '<table><thead><tr><th>File</th><th>Size</th><th>Status</th><th>Actions</th></tr></thead><tbody>';
                            data.forEach(entry => {
                                const sizeKb = (entry.size / 1024).toFixed(1);
                                const badge = entry.loaded
                                    ? '<span class="badge badge-loaded">Loaded</span>'
                                    : '<span class="badge badge-unloaded">Not Loaded</span>';
                                const loadBtn = entry.loaded ? '' :
                                    '<button class="btn-primary" onclick="loadPlugin(' + JSON.stringify(entry.name) + ')">Load</button>';
                                const unloadBtn = entry.loaded ?
                                    '<button class="btn-danger" onclick="unloadPlugin(' + JSON.stringify(entry.path) + ')">Unload</button>' : '';
                                html += '<tr>'
                                    + '<td><code>' + escHtml(entry.name) + '</code></td>'
                                    + '<td>' + sizeKb + ' KB</td>'
                                    + '<td>' + badge + '</td>'
                                    + '<td>' + loadBtn + unloadBtn + '</td>'
                                    + '</tr>';
                            });
                            html += '</tbody></table>';
                            container.innerHTML = html;
                        })
                        .catch(e => {
                            document.getElementById('availableJars').innerHTML = '<em>Error scanning directory.</em>';
                            showMessage('Error scanning directory: ' + e, 'error');
                        });
                }

                function loadPlugin(jarName) {
                    const jar = jarName || document.getElementById('jarPath').value.trim();
                    const entryPoint = document.getElementById('entryPoint') ? document.getElementById('entryPoint').value.trim() : '';
                    if (!jar) { showMessage('Please enter a JAR path.', 'error'); return; }
                    const body = new URLSearchParams({ action: 'load', jar });
                    if (entryPoint) body.append('entryPoint', entryPoint);
                    fetch('/pluginManager', { method: 'POST', body })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Loaded ' + data.pluginsLoaded + ' plugin(s): ' + (data.plugins || []).join(', '), 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }

                function unloadPlugin(jarPath) {
                    if (!confirm('Unload plugin JAR: ' + jarPath + '?')) return;
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'unload', jar: jarPath })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Plugin unloaded: ' + jarPath, 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }

                function loadDirectory() {
                    fetch('/pluginManager', {
                        method: 'POST',
                        body: new URLSearchParams({ action: 'loadDirectory' })
                    })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                showMessage('Processed ' + data.jarsProcessed + ' JAR(s) from ' + data.directory, 'success');
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Request failed: ' + e, 'error'));
                }

                function uploadPlugin() {
                    const fileInput = document.getElementById('jarFile');
                    const autoLoad = document.getElementById('autoLoad').checked;
                    if (!fileInput.files || fileInput.files.length === 0) {
                        showMessage('Please select a JAR file to upload.', 'error');
                        return;
                    }
                    const formData = new FormData();
                    formData.append('action', 'upload');
                    formData.append('jarFile', fileInput.files[0]);
                    formData.append('autoLoad', autoLoad ? 'true' : 'false');
                    fetch('/pluginManager', { method: 'POST', body: formData })
                        .then(r => r.json())
                        .then(data => {
                            if (data.success) {
                                let msg = 'Uploaded: ' + data.file;
                                if (data.autoLoaded) msg += ' — loaded ' + data.pluginsLoaded + ' plugin(s): ' + (data.plugins || []).join(', ');
                                showMessage(msg, 'success');
                                fileInput.value = '';
                                refreshLoaded();
                                scanDirectory();
                            } else {
                                showMessage('Error: ' + data.error, 'error');
                            }
                        })
                        .catch(e => showMessage('Upload failed: ' + e, 'error'));
                }

                function escHtml(str) {
                    return String(str)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;');
                }

                // Initial load
                refreshLoaded();
            </script>
        </body>
        </html>
    """.trimIndent()

  companion object {
    private val log = LoggerFactory.getLogger(PluginManagerServlet::class.java)
  }
}