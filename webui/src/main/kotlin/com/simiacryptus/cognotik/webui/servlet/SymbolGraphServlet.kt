package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.net.URLEncoder

class SymbolGraphServlet(
  private val service: SymbolGraphService,
  val urlBase: String = "/symbol_index/"
) : HttpServlet() {
  companion object {
    val log = LoggerFactory.getLogger(SymbolGraphServlet::class.java)
    private const val DEFAULT_CONTENT_TYPE_HTML = "text/html; charset=UTF-8"
    private const val DEFAULT_CONTENT_TYPE_JSON = "application/json; charset=UTF-8"
  }


  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val path = req.pathInfo ?: "/"
    log.info("SymbolGraphServlet GET $path")

    resp.characterEncoding = "UTF-8"
    val acceptHeader = req.getHeader("Accept") ?: ""
    val wantsJson = acceptHeader.contains("application/json") || req.getParameter("format") == "json"
    resp.contentType = if (wantsJson) DEFAULT_CONTENT_TYPE_JSON else DEFAULT_CONTENT_TYPE_HTML

    try {
      when {
        path == "/search" -> {
          val query = req.getParameter("q")
          if (query.isNullOrBlank()) {
            if (wantsJson) {
              resp.writer.write("[]")
            } else {
              writeHtmlPage(resp, "Search Results", "<p>No query provided. Use ?q=searchterm</p>")
            }
          } else {
            val limit = req.getParameter("limit")?.toIntOrNull() ?: 100
            val symbols = service.search(query, limit)
            if (wantsJson) {
              writeSymbolsJson(resp, symbols)
            } else {
              writeSymbolsHtml(resp, symbols, "Search Results for: $query")
            }
          }
        }

        path == "/symbol" -> {
          val id = req.getParameter("id")
          if (id == null) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            if (wantsJson) {
              resp.writer.write("""{"error": "Missing id parameter"}""")
            } else {
              writeHtmlPage(resp, "Error", "<p class='error'>Missing id parameter</p>")
            }
          } else {
            val symbol = service.getSymbol(id)
            if (symbol == null) {
              resp.status = HttpServletResponse.SC_NOT_FOUND
              if (wantsJson) {
                resp.writer.write("""{"error": "Symbol not found"}""")
              } else {
                writeHtmlPage(resp, "Not Found", "<p class='error'>Symbol not found: ${escapeHtml(id)}</p>")
              }
            } else {
              if (wantsJson) {
                writeSymbolJson(resp, symbol, detailed = true)
              } else {
                writeSymbolDetailHtml(resp, symbol)
              }
            }
          }
        }

        path == "/files" -> handleFiles(resp, wantsJson)
        path == "/files/" -> handleFiles(resp, wantsJson)
        path.startsWith("/files/") -> handleFolderBrowse(path.removePrefix("/files/"), resp, wantsJson)
        path == "/languages" -> handleLanguages(resp, wantsJson)
        path == "/libraries" -> handleLibraries(resp, wantsJson)
        path == "/packages" -> handlePackages(resp, wantsJson)
        path == "/packages/" -> handlePackages(resp, wantsJson)
        path.startsWith("/packages/") -> handlePackageBrowse(path.removePrefix("/packages/"), resp, wantsJson)
        path == "/types" -> handleNodeTypes(resp, wantsJson)
        path.startsWith("/type/") -> handleTypeSymbols(path.removePrefix("/type/"), resp, wantsJson)
        path.startsWith("/file/") -> handleFileSymbols(path.removePrefix("/file/"), resp, wantsJson)
        path.startsWith("/language/") -> handleLanguageSymbols(path.removePrefix("/language/"), resp, wantsJson)
        path.startsWith("/library/") -> handleLibrarySymbols(path.removePrefix("/library/"), resp, wantsJson)
        path.startsWith("/package/") -> handlePackageSymbols(path.removePrefix("/package/"), resp, wantsJson)
        path.startsWith("/transitive-refs/") -> handleTransitiveReferences(
          path.removePrefix("/transitive-refs/"),
          req,
          resp,
          wantsJson
        )

        path.startsWith("/transitive-refby/") -> handleTransitiveReferencedBy(
          path.removePrefix("/transitive-refby/"),
          req,
          resp,
          wantsJson
        )

        else -> handleIndex(resp, wantsJson)
      }
    } catch (e: Exception) {
      log.error("Error in SymbolGraphServlet", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      if (wantsJson) {
        resp.writer.write("""{"error": "${escapeJson(e.message ?: "Unknown error")}"}""")
      } else {
        writeHtmlPage(resp, "Error", "<p class='error'>${escapeHtml(e.message ?: "Unknown error")}</p>")
      }
    }
  }

  private fun handleFiles(resp: HttpServletResponse, wantsJson: Boolean) {
    val hierarchy = service.getFolderHierarchy()
    if (wantsJson) {
      resp.writer.write(toJson(hierarchy))
    } else {
      writeFolderHierarchyHtml(resp, hierarchy, "")
    }
  }

  private fun handleFolderBrowse(folderPath: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val decodedPath = java.net.URLDecoder.decode(folderPath, "UTF-8").trimEnd('/')
    val hierarchy = service.getFolderHierarchy()
    val targetFolder = navigateToFolder(hierarchy, decodedPath)
    if (targetFolder == null) {
      resp.status = HttpServletResponse.SC_NOT_FOUND
      if (wantsJson) {
        resp.writer.write("""{"error": "Folder not found"}""")
      } else {
        writeHtmlPage(resp, "Not Found", "<p class='error'>Folder not found: ${escapeHtml(decodedPath)}</p>")
      }
    } else {
      if (wantsJson) {
        resp.writer.write(toJson(targetFolder))
      } else {
        writeFolderHierarchyHtml(resp, targetFolder, decodedPath)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun navigateToFolder(hierarchy: Map<String, Any>, path: String): Map<String, Any>? {
    if (path.isEmpty()) return hierarchy
    val parts = path.split("/").filter { it.isNotEmpty() }
    var current: Map<String, Any> = hierarchy
    for (part in parts) {
      val next = current[part]
      if (next is Map<*, *>) {
        current = next as Map<String, Any>
      } else {
        return null
      }
    }
    return current
  }

  private fun handleLanguages(resp: HttpServletResponse, wantsJson: Boolean) {
    val languages = service.listLanguages()
    if (wantsJson) writeStringsJson(resp, languages) else writeListHtml(resp, languages, "Languages", "language/")
  }

  private fun handleLibraries(resp: HttpServletResponse, wantsJson: Boolean) {
    val libraries = service.listLibraries()
    if (wantsJson) writeStringsJson(resp, libraries) else writeListHtml(resp, libraries, "Libraries", "library/")
  }

  private fun handlePackages(resp: HttpServletResponse, wantsJson: Boolean) {
    val hierarchy = service.getPackageHierarchy()
    if (wantsJson) {
      resp.writer.write(toJson(hierarchy))
    } else {
      writePackageHierarchyHtml(resp, hierarchy, "")
    }
  }

  private fun handlePackageBrowse(packagePath: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val decodedPath = java.net.URLDecoder.decode(packagePath, "UTF-8").trimEnd('/')
    val hierarchy = service.getPackageHierarchy()
    val targetPackage = navigateToPackage(hierarchy, decodedPath)
    if (targetPackage == null) {
      resp.status = HttpServletResponse.SC_NOT_FOUND
      if (wantsJson) {
        resp.writer.write("""{"error": "Package not found"}""")
      } else {
        writeHtmlPage(resp, "Not Found", "<p class='error'>Package not found: ${escapeHtml(decodedPath)}</p>")
      }
    } else {
      if (wantsJson) {
        resp.writer.write(toJson(targetPackage))
      } else {
        writePackageHierarchyHtml(resp, targetPackage, decodedPath)
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun navigateToPackage(hierarchy: Map<String, Any>, path: String): Map<String, Any>? {
    if (path.isEmpty()) return hierarchy
    val parts = path.split(".").filter { it.isNotEmpty() }
    var current: Map<String, Any> = hierarchy
    for (part in parts) {
      val next = current[part]
      if (next is Map<*, *>) {
        current = next as Map<String, Any>
      } else {
        return null
      }
    }
    return current
  }

  private fun handleNodeTypes(resp: HttpServletResponse, wantsJson: Boolean) {
    val types = service.listNodeTypes()
    if (wantsJson) writeStringsJson(resp, types) else writeListHtml(resp, types, "Symbol Types", "type/")
  }

  private fun handleTypeSymbols(nodeType: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val symbols = service.getSymbolsByNodeType(java.net.URLDecoder.decode(nodeType, "UTF-8"))
    if (wantsJson) writeSymbolsJson(resp, symbols) else writeSymbolsHtml(resp, symbols, "Symbols of Type: $nodeType")
  }

  private fun handleFileSymbols(fileId: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val symbols = service.getSymbolsByFile(java.net.URLDecoder.decode(fileId, "UTF-8"))
    if (wantsJson) writeSymbolsJson(resp, symbols) else writeSymbolsHtml(resp, symbols, "Symbols in File: $fileId")
  }

  private fun handleLanguageSymbols(lang: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val symbols = service.getSymbolsByLanguage(java.net.URLDecoder.decode(lang, "UTF-8"))
    if (wantsJson) writeSymbolsJson(resp, symbols) else writeSymbolsHtml(resp, symbols, "Symbols in Language: $lang")
  }

  private fun handleLibrarySymbols(lib: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val symbols = service.getSymbolsByLibrary(java.net.URLDecoder.decode(lib, "UTF-8"))
    if (wantsJson) writeSymbolsJson(resp, symbols) else writeSymbolsHtml(resp, symbols, "Symbols in Library: $lib")
  }

  private fun handlePackageSymbols(pkg: String, resp: HttpServletResponse, wantsJson: Boolean) {
    val symbols = service.getSymbolsByPackage(java.net.URLDecoder.decode(pkg, "UTF-8"))
    if (wantsJson) writeSymbolsJson(resp, symbols) else writeSymbolsHtml(resp, symbols, "Symbols in Package: $pkg")
  }

  private fun handleTransitiveReferences(
    symbolId: String,
    req: HttpServletRequest,
    resp: HttpServletResponse,
    wantsJson: Boolean
  ) {
    val decodedId = java.net.URLDecoder.decode(symbolId, "UTF-8")
    val limit = req.getParameter("limit")?.toIntOrNull() ?: 500
    val result = service.getTransitiveReferences(decodedId, limit)
    if (wantsJson) {
      writeTransitiveResultJson(resp, result)
    } else {
      writeTransitiveResultHtml(resp, result, decodedId, "Transitive References", "references")
    }
  }

  private fun handleTransitiveReferencedBy(
    symbolId: String,
    req: HttpServletRequest,
    resp: HttpServletResponse,
    wantsJson: Boolean
  ) {
    val decodedId = java.net.URLDecoder.decode(symbolId, "UTF-8")
    val limit = req.getParameter("limit")?.toIntOrNull() ?: 500
    val result = service.getTransitiveReferencedBy(decodedId, limit)
    if (wantsJson) {
      writeTransitiveResultJson(resp, result)
    } else {
      writeTransitiveResultHtml(resp, result, decodedId, "Transitive Referenced By", "referenced by")
    }
  }

  private fun handleIndex(resp: HttpServletResponse, wantsJson: Boolean) {
    if (wantsJson) {
      resp.writer.write("""{"endpoints": ["/search?q=<query>&limit=<n>", "/symbol?id=<id>", "/files", "/files/<path>", "/languages", "/libraries", "/packages", "/packages/<path>", "/types", "/file/<fileId>", "/language/<language>", "/library/<library>", "/package/<package>", "/type/<nodeType>", "/transitive-refs/<symbolId>?limit=<n>", "/transitive-refby/<symbolId>?limit=<n>"]}""")
    } else {
      writeIndexHtml(resp)
    }
  }


  // JSON methods
  private fun writeSymbolsJson(resp: HttpServletResponse, symbols: List<SymbolGraphService.Symbol>) {
    val writer = resp.writer
    writer.write("[")
    symbols.forEachIndexed { index, symbol ->
      if (index > 0) writer.write(",")
      writer.write(symbolToJson(symbol, detailed = false))
    }
    writer.write("]")
  }

  private fun writeSymbolJson(resp: HttpServletResponse, symbol: SymbolGraphService.Symbol, detailed: Boolean) {
    resp.writer.write(symbolToJson(symbol, detailed))
  }

  private fun symbolToJson(symbol: SymbolGraphService.Symbol, detailed: Boolean): String {
    val props = symbol.properties.toMutableMap()
    if (detailed) {
      symbol.file()?.let { props["file_obj"] = it.properties }
      symbol.language()?.let { props["language"] = it }
      symbol.packageName()?.let { props["package"] = it }
      symbol.libraryName()?.let { props["library"] = it }
      props["references"] = symbol.references().map { ref -> ref.properties }
      props["referencedBy"] = symbol.referencedBy().map { ref -> ref.properties }
      props["contains"] = symbol.contains().map { it.properties }
      symbol.containedBy()?.let { props["containedBy"] = it.properties }
    }
    return toJson(props)
  }

  private fun writeStringsJson(resp: HttpServletResponse, list: List<String>) {
    resp.writer.write(list.joinToString(",", "[", "]") { "\"${escapeJson(it)}\"" })
  }

  private fun writeTransitiveResultJson(resp: HttpServletResponse, result: SymbolGraphService.TransitiveResult) {
    val writer = resp.writer
    writer.write("{")
    writer.write("\"totalFound\":${result.totalFound},")
    writer.write("\"maxDepth\":${result.maxDepth},")
    writer.write("\"truncated\":${result.truncated},")
    writer.write("\"remainingInQueue\":${result.remainingInQueue},")
    writer.write("\"symbols\":[")
    result.symbols.forEachIndexed { index, symbol ->
      if (index > 0) writer.write(",")
      writer.write(symbolToJson(symbol, detailed = false))
    }
    writer.write("]}")
  }


  private fun toJson(map: Map<String, Any>): String {
    val sb = StringBuilder()
    sb.append("{")
    var first = true
    for ((k, v) in map) {
      if (!first) sb.append(",")
      first = false
      sb.append("\"${escapeJson(k)}\":")
      when (v) {
        is Number -> sb.append(v)
        is Boolean -> sb.append(v)
        is Map<*, *> -> sb.append(toJson(@Suppress("UNCHECKED_CAST") (v as Map<String, Any>)))
        is List<*> -> {
          sb.append("[")
          v.forEachIndexed { idx, item ->
            if (idx > 0) sb.append(",")
            when (item) {
              is Map<*, *> -> sb.append(toJson(@Suppress("UNCHECKED_CAST") (item as Map<String, Any>)))
              else -> sb.append("\"${escapeJson(item.toString())}\"")
            }
          }
          sb.append("]")
        }

        else -> sb.append("\"${escapeJson(v.toString())}\"")
      }
    }
    sb.append("}")
    return sb.toString()
  }

  private fun escapeJson(s: String): String {
    return s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("\b", "\\b")
      .replace("\u000C", "\\f")
  }

  // HTML methods
  private fun escapeHtml(s: String): String {
    return s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }

  private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

  private fun writeHtmlPage(resp: HttpServletResponse, title: String, content: String) {
    resp.writer.write(
      """
            <!DOCTYPE html>
            <html>
            <head>
                <title>$title - Symbol Graph</title>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { color: #333; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
                    h2 { color: #555; }
                    a { color: #007bff; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    .nav { background: #333; padding: 10px 20px; margin: -20px -20px 20px -20px; }
                    .nav a { color: white; margin-right: 20px; }
                    .search-box { margin: 20px 0; }
                    .search-box input { padding: 10px; width: 300px; border: 1px solid #ddd; border-radius: 4px; }
                    .search-box button { padding: 10px 20px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; }
                    .search-box button:hover { background: #0056b3; }
                    .card { background: white; border-radius: 8px; padding: 15px; margin: 10px 0; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .card h3 { margin-top: 0; }
                    .symbol-list { list-style: none; padding: 0; }
                    .symbol-list li { padding: 8px 0; border-bottom: 1px solid #eee; }
                    .symbol-list li:last-child { border-bottom: none; }
                    .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 12px; margin-left: 8px; }
                    .badge-language { background: #e3f2fd; color: #1976d2; }
                    .badge-package { background: #f3e5f5; color: #7b1fa2; }
                    .badge-library { background: #e8f5e9; color: #388e3c; }
                    .badge-visibility { background: #fff3e0; color: #f57c00; }
                    .badge-type { background: #fce4ec; color: #c2185b; }
                    .property { margin: 5px 0; }
                    .property-name { font-weight: bold; color: #666; }
                    .error { color: #d32f2f; background: #ffebee; padding: 10px; border-radius: 4px; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 15px; }
                    table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
                    th { background: #f5f5f5; font-weight: 600; }
                    .references { margin-top: 20px; }
                    code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; font-family: 'Consolas', 'Monaco', monospace; }
                    .source-code { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 8px; overflow-x: auto; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; line-height: 1.5; white-space: pre; }
                    .source-code .line-number { color: #858585; user-select: none; padding-right: 15px; text-align: right; display: inline-block; min-width: 40px; }
                    .source-code .highlight-line { background: #264f78; display: block; margin: 0 -15px; padding: 0 15px; }
                    .reference-location { color: #888; font-style: italic; display: block; margin-top: 4px; margin-left: 16px; }
                    .data-table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; margin: 10px 0; }
                    .data-table th { background: #f5f5f5; font-weight: 600; cursor: pointer; user-select: none; }
                    .data-table th:hover { background: #e8e8e8; }
                    .data-table th, .data-table td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
                    .data-table tbody tr:hover { background: #f8f9fa; }
                    .data-table .empty-row td { text-align: center; color: #888; font-style: italic; }
                    details { margin: 10px 0; }
                    details summary { cursor: pointer; padding: 8px; background: #f5f5f5; border-radius: 4px; font-weight: 500; }
                    details summary:hover { background: #e8e8e8; }
                    details[open] summary { margin-bottom: 10px; }
                    details > .data-table { margin-left: 10px; }
                </style>
            </head>
            <body>
                <div class="nav">
                    <a href="${urlBase}">Home</a>
                    <a href="${urlBase}files">Files</a>
                    <a href="${urlBase}languages">Languages</a>
                    <a href="${urlBase}libraries">Libraries</a>
                    <a href="${urlBase}packages">Packages</a>
                </div>
                <div class="container">
                    <h1>$title</h1>
                    $content
                </div>
            </body>
            </html>
        """.trimIndent()
    )
  }

  private fun writeIndexHtml(resp: HttpServletResponse) {
    val content = """
            <div class="search-box">
                <form action="${urlBase}search" method="get">
                    <input type="text" name="q" placeholder="Search symbols..." />
                    <button type="submit">Search</button>
                </form>
            </div>
            <div class="grid">
                <div class="card">
                    <h3><a href="${urlBase}files">📁 Files</a></h3>
                    <p>Browse files hierarchically by folder</p>
                </div>
                <div class="card">
                    <h3><a href="${urlBase}languages">🌐 Languages</a></h3>
                    <p>Browse symbols by programming language</p>
                </div>
                <div class="card">
                    <h3><a href="${urlBase}libraries">📚 Libraries</a></h3>
                    <p>Browse symbols by library/module</p>
                </div>
                <div class="card">
                    <h3><a href="${urlBase}packages">📦 Packages</a></h3>
                    <p>Browse packages hierarchically</p>
                </div>
                <div class="card">
                    <h3><a href="${urlBase}types">🏷️ Symbol Types</a></h3>
                    <p>Browse symbols by type (class, function, etc.)</p>
                </div>
            </div>
            <div class="card" style="margin-top: 20px;">
                <h3>API Endpoints</h3>
                <p>Add <code>?format=json</code> or use <code>Accept: application/json</code> header for JSON responses.</p>
                <ul>
                    <li><code>/search?q=&lt;query&gt;&amp;limit=&lt;n&gt;</code> - Search symbols (default limit: 100)</li>
                    <li><code>/symbol?id=&lt;id&gt;</code> - Get symbol details</li>
                    <li><code>/files</code> - Browse files hierarchically</li>
                    <li><code>/files/&lt;path&gt;</code> - Browse specific folder</li>
                    <li><code>/languages</code> - List all languages</li>
                    <li><code>/libraries</code> - List all libraries</li>
                    <li><code>/packages</code> - Browse packages hierarchically</li>
                    <li><code>/packages/&lt;path&gt;</code> - Browse specific package path</li>
                    <li><code>/types</code> - List all symbol types</li>
                    <li><code>/file/&lt;fileId&gt;</code> - Symbols in a file</li>
                    <li><code>/language/&lt;language&gt;</code> - Symbols by language</li>
                    <li><code>/library/&lt;library&gt;</code> - Symbols by library</li>
                    <li><code>/package/&lt;package&gt;</code> - Symbols by package</li>
                    <li><code>/type/&lt;nodeType&gt;</code> - Symbols by type</li>
                    <li><code>/transitive-refs/&lt;symbolId&gt;?limit=&lt;n&gt;</code> - All symbols transitively referenced (default limit: 500)</li>
                    <li><code>/transitive-refby/&lt;symbolId&gt;?limit=&lt;n&gt;</code> - All symbols that transitively reference (default limit: 500)</li>
                </ul>
            </div>
        """.trimIndent()
    writeHtmlPage(resp, "Symbol Graph Browser", content)
  }

  /**
   * Generic table rendering function for consistent UI across all tables.
   * @param headers List of column headers
   * @param rows List of rows, where each row is a list of cell contents (can contain HTML)
   * @param tableId Optional ID for the table element
   * @param emptyMessage Message to show when there are no rows
   */
  private fun renderTable(
    headers: List<String>,
    rows: List<List<String>>,
    tableId: String? = null,
    emptyMessage: String = "No data found."
  ): String {
    val idAttr = tableId?.let { " id=\"${escapeHtml(it)}\"" } ?: ""
    return buildString {
      append("<table class='data-table'$idAttr>")
      append("<thead><tr>")
      for (header in headers) {
        append("<th>${escapeHtml(header)}</th>")
      }
      append("</tr></thead>")
      append("<tbody>")
      if (rows.isEmpty()) {
        append("<tr class='empty-row'><td colspan='${headers.size}'>$emptyMessage</td></tr>")
      } else {
        for (row in rows) {
          append("<tr>")
          for (cell in row) {
            append("<td>$cell</td>") // cell content may contain HTML
          }
          append("</tr>")
        }
      }
      append("</tbody></table>")
    }
  }


  private fun writeSymbolsHtml(resp: HttpServletResponse, symbols: List<SymbolGraphService.Symbol>, title: String) {
    val content = if (symbols.isEmpty()) {
      "<p>No symbols found.</p>"
    } else {
      val rows = symbols.map { symbol ->
        val badges = buildString {
          symbol.language()?.let { append("<span class='badge badge-language'>$it</span>") }
          symbol.visibility?.let { append("<span class='badge badge-visibility'>$it</span>") }
          symbol.nodeType?.let { append("<span class='badge badge-type'>$it</span>") }
        }
        listOf(
          "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(symbol.id))}\">${escapeHtml(symbol.name ?: symbol.id)}</a>$badges",
          escapeHtml(symbol.fileId ?: ""),
          (symbol.line ?: "").toString()
        )
      }
      """
            <p>Found ${symbols.size} symbol(s)</p>
            ${renderTable(listOf("Name", "File", "Line"), rows, "symbols-table")}
            """.trimIndent()
    }
    writeHtmlPage(resp, title, content)
  }

  @Suppress("UNCHECKED_CAST")
  private fun writeFolderHierarchyHtml(resp: HttpServletResponse, hierarchy: Map<String, Any>, currentPath: String) {
    val content = buildString {
      if (currentPath.isNotEmpty()) {
        val parentPath = currentPath.substringBeforeLast("/", "")
        append("<p><a href=\"${urlBase}files/${escapeHtml(urlEncode(parentPath))}\">⬆️ Parent Folder</a></p>")
        append("<h2>📁 ${escapeHtml(currentPath)}</h2>")
      }

      // List subfolders with auto-collapse
      val collapsedFolders = getCollapsedFolderEntries(hierarchy, currentPath)
      if (collapsedFolders.isNotEmpty()) {
        val folderRows = collapsedFolders.sortedBy { it.first }.map { (displayName, fullPath) ->
          listOf(
            "📁 <a href=\"${urlBase}files/${escapeHtml(urlEncode(fullPath))}\">${escapeHtml(displayName)}</a>"
          )
        }
        append("<div class='card'><h3>Folders (${collapsedFolders.size})</h3>")
        append(renderTable(listOf("Folder Name"), folderRows, "folders-table", "No folders found."))
        append("</div>")
      }

      // List files
      val files = hierarchy["_files"] as? List<String> ?: emptyList()
      if (files.isNotEmpty()) {
        val fileRows = files.sorted().map { file ->
          val fileName = file.substringAfterLast("/")
          listOf(
            "📄 <a href=\"${urlBase}file/${escapeHtml(urlEncode(file))}\">${escapeHtml(fileName)}</a>"
          )
        }
        append("<div class='card'><h3>Files (${files.size})</h3>")
        append(renderTable(listOf("File Name"), fileRows, "files-table", "No files found."))
        append("</div>")
      }

      if (collapsedFolders.isEmpty() && files.isEmpty()) {
        append("<p>No files or folders found.</p>")
      }
    }
    writeHtmlPage(resp, if (currentPath.isEmpty()) "Files" else "Folder: $currentPath", content)
  }

  @Suppress("UNCHECKED_CAST")
  private fun getCollapsedFolderEntries(hierarchy: Map<String, Any>, currentPath: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val folders = hierarchy.keys.filter { !it.startsWith("_") }
    for (folder in folders) {
      val subHierarchy = hierarchy[folder] as? Map<String, Any> ?: continue
      val collapsedPath = getCollapsedPath(folder, subHierarchy)
      val collapsedFullPath = if (currentPath.isEmpty()) collapsedPath else "$currentPath/$collapsedPath"
      result.add(collapsedPath to collapsedFullPath)
    }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun getCollapsedPath(
    currentName: String,
    hierarchy: Map<String, Any>,
    visited: MutableSet<String> = mutableSetOf()
  ): String {
    if (!visited.add(currentName)) {
      // Cycle detected, stop collapsing
      return currentName
    }
    val subfolders = hierarchy.keys.filter { !it.startsWith("_") }
    val files = hierarchy["_files"] as? List<String> ?: emptyList()
    // If there's exactly one subfolder and no files, collapse
    if (subfolders.size == 1 && files.isEmpty()) {
      val subfolderName = subfolders.first()
      val subHierarchy = hierarchy[subfolderName] as? Map<String, Any> ?: return currentName
      return "$currentName/${getCollapsedPath(subfolderName, subHierarchy, visited)}"
    }
    return currentName
  }


  @Suppress("UNCHECKED_CAST")
  private fun writePackageHierarchyHtml(resp: HttpServletResponse, hierarchy: Map<String, Any>, currentPath: String) {
    val content = buildString {
      if (currentPath.isNotEmpty()) {
        val parentPath = currentPath.substringBeforeLast(".", "")
        append("<p><a href=\"${urlBase}packages/${escapeHtml(urlEncode(parentPath))}\">⬆️ Parent Package</a></p>")
        append("<h2>📦 ${escapeHtml(currentPath)}</h2>")
        // Link to view symbols in this package
        append("<p><a href=\"${urlBase}package/${escapeHtml(urlEncode(currentPath))}\">View symbols in this package</a></p>")
      }

      // List subpackages with auto-collapse
      val collapsedPackages = getCollapsedPackageEntries(hierarchy, currentPath)
      if (collapsedPackages.isNotEmpty()) {
        val packageRows = collapsedPackages.sortedBy { it.first }.map { (displayName, fullPath) ->
          listOf(
            "📦 <a href=\"${urlBase}packages/${escapeHtml(urlEncode(fullPath))}\">${escapeHtml(displayName)}</a>"
          )
        }
        append("<div class='card'><h3>Subpackages (${collapsedPackages.size})</h3>")
        append(renderTable(listOf("Package Name"), packageRows, "packages-table", "No subpackages found."))
        append("</div>")
      }

      // List leaf packages at this level
      val leafPackages = hierarchy["_packages"] as? List<String> ?: emptyList()
      if (leafPackages.isNotEmpty() && currentPath.isEmpty()) {
        val leafRows = leafPackages.sorted().map { pkg ->
          listOf(
            "📦 <a href=\"${urlBase}package/${escapeHtml(urlEncode(pkg))}\">${escapeHtml(pkg)}</a>"
          )
        }
        append("<div class='card'><h3>Packages (${leafPackages.size})</h3>")
        append(renderTable(listOf("Package Name"), leafRows, "leaf-packages-table", "No packages found."))
        append("</div>")
      }

      if (collapsedPackages.isEmpty() && leafPackages.isEmpty()) {
        append("<p>No subpackages found.</p>")
      }
    }
    writeHtmlPage(resp, if (currentPath.isEmpty()) "Packages" else "Package: $currentPath", content)
  }

  @Suppress("UNCHECKED_CAST")
  private fun getCollapsedPackageEntries(hierarchy: Map<String, Any>, currentPath: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val packages = hierarchy.keys.filter { !it.startsWith("_") }
    for (pkg in packages) {
      val subHierarchy = hierarchy[pkg] as? Map<String, Any> ?: continue
      // Check if we should collapse
      val collapsedPath = getCollapsedPackagePath(pkg, subHierarchy)
      val collapsedFullPath = if (currentPath.isEmpty()) collapsedPath else "$currentPath.$collapsedPath"
      result.add(collapsedPath to collapsedFullPath)
    }
    return result
  }

  @Suppress("UNCHECKED_CAST")
  private fun getCollapsedPackagePath(
    currentName: String,
    hierarchy: Map<String, Any>,
    visited: MutableSet<String> = mutableSetOf()
  ): String {
    if (!visited.add(currentName)) {
      // Cycle detected, stop collapsing
      return currentName
    }
    val subpackages = hierarchy.keys.filter { !it.startsWith("_") }
    val leafPackages = hierarchy["_packages"] as? List<String> ?: emptyList()
    // If there's exactly one subpackage and no leaf packages, collapse
    if (subpackages.size == 1 && leafPackages.isEmpty()) {
      val subpackageName = subpackages.first()
      val subHierarchy = hierarchy[subpackageName] as? Map<String, Any> ?: return currentName
      return "$currentName.${getCollapsedPackagePath(subpackageName, subHierarchy, visited)}"
    }
    return currentName
  }


  private fun writeListHtml(resp: HttpServletResponse, items: List<String>, title: String, linkPrefix: String) {
    val content = if (items.isEmpty()) {
      "<p>No items found.</p>"
    } else {
      val rows = items.map { item ->
        listOf(
          "<a href=\"${urlBase}${linkPrefix}${escapeHtml(urlEncode(item))}\">${escapeHtml(item)}</a>"
        )
      }
      """
            <p>Found ${items.size} item(s)</p>
            ${renderTable(listOf("Name"), rows, "items-table", "No items found.")}
            """.trimIndent()
    }
    writeHtmlPage(resp, title, content)
  }

  private fun writeSymbolDetailHtml(resp: HttpServletResponse, symbol: SymbolGraphService.Symbol) {
    val badges = buildString {
      symbol.language()?.let { append("<span class='badge badge-language'>$it</span>") }
      symbol.packageName()?.let { append("<span class='badge badge-package'>$it</span>") }
      symbol.libraryName()?.let { append("<span class='badge badge-library'>$it</span>") }
      symbol.visibility?.let { append("<span class='badge badge-visibility'>$it</span>") }
      symbol.nodeType?.let { append("<span class='badge badge-type'>$it</span>") }
    }
    val propertiesRows = symbol.properties.map { (key, value) ->
      listOf(escapeHtml(key), escapeHtml(value.toString()))
    }
    val properties = """
            <div class='card'><h3>Properties</h3>
            ${renderTable(listOf("Property", "Value"), propertiesRows, "properties-table", "No properties.")}
            </div>
        """.trimIndent()

    val sourceCodeHtml = buildSourceCodeHtml(symbol)
    // Contains section
    val contains = symbol.contains()
    val containsHtml = if (contains.isNotEmpty()) {
      val containsRows = contains.map { child ->
        val typeBadge = child.nodeType?.let { "<span class='badge badge-type'>$it</span>" } ?: ""
        listOf(
          "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(child.id))}\">${escapeHtml(child.name ?: child.id)}</a>$typeBadge",
          child.nodeType ?: "",
          (child.line ?: "").toString()
        )
      }
      """
            <div class='card'>
                <h3>Contains (${contains.size})</h3>
                ${
        renderTable(
          listOf("Symbol", "Type", "Line"),
          containsRows,
          "contains-table",
          "No contained symbols."
        )
      }
            </div>
            """.trimIndent()
    } else ""
    // Contained by section
    val containedByHtml = buildContainedByPathHtml(symbol)

    val references = symbol.references()
    val referencesHtml = if (references.isNotEmpty()) {
      val refRows = references.map { ref ->
        val target = ref.targetSymbol
        val typeBadge = target.nodeType?.let { "<span class='badge badge-type'>$it</span>" } ?: ""
        val locationInfo = buildReferenceLocationHtml(ref, null)
        listOf(
          "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(target.id))}\">${escapeHtml(target.name ?: target.id)}</a>$typeBadge",
          target.nodeType ?: "",
          locationInfo
        )
      }
      """
            <div class='card references'>
                <h3>References (${references.size})</h3>
                ${renderTable(listOf("Symbol", "Type", "Location"), refRows, "references-table", "No references.")}
            </div>
            """.trimIndent()
    } else ""
    val referencedBy = symbol.referencedBy()
    val referencedByHtml = if (referencedBy.isNotEmpty()) {
      val refByRows = referencedBy.map { ref ->
        val source = ref.targetSymbol
        val typeBadge = source.nodeType?.let { "<span class='badge badge-type'>$it</span>" } ?: ""
        val locationInfo = buildReferenceLocationHtml(ref, source)
        listOf(
          "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(source.id))}\">${escapeHtml(source.name ?: source.id)}</a>$typeBadge",
          source.nodeType ?: "",
          locationInfo
        )
      }
      """
            <div class='card references'>
                <h3>Referenced By (${referencedBy.size})</h3>
                ${renderTable(listOf("Symbol", "Type", "Location"), refByRows, "referenced-by-table", "No references.")}
            </div>
            """.trimIndent()
    } else ""
    // Transitive links
    val transitiveRows = listOf(
      listOf(
        "<a href=\"${urlBase}transitive-refs/${escapeHtml(urlEncode(symbol.id))}\">📤 All Transitive References</a>",
        "Everything this symbol depends on"
      ),
      listOf(
        "<a href=\"${urlBase}transitive-refby/${escapeHtml(urlEncode(symbol.id))}\">📥 All Transitive Referenced By</a>",
        "Everything that depends on this symbol"
      )
    )
    val transitiveLinksHtml = """
            <div class='card'>
                <h3>🔗 Transitive Analysis</h3>
                <p>Explore the full dependency graph:</p>
                ${renderTable(listOf("Action", "Description"), transitiveRows, "transitive-table")}
                <p><small>Tip: Add <code>?limit=N</code> to control max results (default: 500)</small></p>
            </div>
        """.trimIndent()

    val content = """
            <p>$badges</p>
            $properties
            $sourceCodeHtml
            $containedByHtml
            $containsHtml
            $transitiveLinksHtml
            $referencesHtml
            $referencedByHtml
        """.trimIndent()
    writeHtmlPage(resp, symbol.name ?: symbol.id, content)
  }

  private fun buildContainedByPathHtml(symbol: SymbolGraphService.Symbol): String {
    val path = mutableListOf<SymbolGraphService.Symbol>()
    val visited = mutableSetOf<String>()
    visited.add(symbol.id)
    var current = symbol.containedBy()
    while (current != null && current.id !in visited) {
      visited.add(current.id)
      path.add(0, current)
      current = current.containedBy()
    }
    if (path.isEmpty()) return ""
    val pathRows = path.mapIndexed { index, parent ->
      val typeBadge = parent.nodeType?.let { "<span class='badge badge-type'>$it</span>" } ?: ""
      listOf(
        (index + 1).toString(),
        "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(parent.id))}\">${escapeHtml(parent.name ?: parent.id)}</a>$typeBadge",
        parent.nodeType ?: ""
      )
    }
    return """
            <div class='card'>
                <h3>Contained By</h3>
                ${renderTable(listOf("Level", "Symbol", "Type"), pathRows, "contained-by-table")}
            </div>
        """.trimIndent()
  }

  private fun buildSourceCodeHtml(symbol: SymbolGraphService.Symbol): String {
    val fileId = symbol.fileId ?: return ""
    val startOffset = symbol.startOffset
    val endOffset = symbol.endOffset
    val symbolLine = symbol.line
    try {
      val file = File(fileId)
      if (!file.exists() || !file.isFile) {
        return ""
      }
      val content = file.readText()
      if (startOffset != null && endOffset != null && startOffset >= 0 && endOffset <= content.length && startOffset < endOffset) {
        // Extract the symbol's source code with some context
        val lines = content.lines()
        // Find line numbers for the offsets
        var currentOffset = 0
        var startLine = 0
        var endLine = lines.size - 1
        for ((index, line) in lines.withIndex()) {
          val lineEnd = currentOffset + line.length + 1 // +1 for newline
          if (currentOffset <= startOffset && startOffset < lineEnd) {
            startLine = index
          }
          if (endOffset <= lineEnd) {
            endLine = index
            break
          }
          currentOffset = lineEnd
        }
        // Add context lines (3 before and after)
        val contextBefore = 3
        val contextAfter = 3
        val displayStartLine = maxOf(0, startLine - contextBefore)
        val displayEndLine = minOf(lines.size - 1, endLine + contextAfter)
        val codeLines = StringBuilder()
        for (i in displayStartLine..displayEndLine) {
          val isHighlighted = i in startLine..endLine
          val lineContent = if (i < lines.size) escapeHtml(lines[i]) else ""
          val lineNum = i + 1
          if (isHighlighted) {
            codeLines.append("<span class='highlight-line'><span class='line-number'>$lineNum</span>$lineContent\n</span>")
          } else {
            codeLines.append("<span class='line-number'>$lineNum</span>$lineContent\n")
          }
        }
        return """
                    <div class='card'>
                        <h3>Source Code</h3>
                        <p><small>File: <a href="${urlBase}file/${escapeHtml(urlEncode(fileId))}">${escapeHtml(fileId)}</a></small></p>
                        <div class='source-code'>$codeLines</div>
                    </div>
                """.trimIndent()
      } else if (symbolLine != null && symbolLine > 0) {
        // Fall back to showing lines around the symbol line
        val lines = content.lines()
        val lineIndex = symbolLine - 1
        val contextBefore = 5
        val contextAfter = 10
        val displayStartLine = maxOf(0, lineIndex - contextBefore)
        val displayEndLine = minOf(lines.size - 1, lineIndex + contextAfter)
        val codeLines = StringBuilder()
        for (i in displayStartLine..displayEndLine) {
          val isHighlighted = i == lineIndex
          val lineContent = if (i < lines.size) escapeHtml(lines[i]) else ""
          val lineNum = i + 1
          if (isHighlighted) {
            codeLines.append("<span class='highlight-line'><span class='line-number'>$lineNum</span>$lineContent\n</span>")
          } else {
            codeLines.append("<span class='line-number'>$lineNum</span>$lineContent\n")
          }
        }
        return """
                    <div class='card'>
                        <h3>Source Code</h3>
                        <p><small>File: <a href="${urlBase}file/${escapeHtml(urlEncode(fileId))}">${escapeHtml(fileId)}</a></small></p>
                        <div class='source-code'>$codeLines</div>
                    </div>
                """.trimIndent()
      }
    } catch (e: Exception) {
      log.debug("Could not read source file: $fileId", e)
    }
    return ""
  }

  private fun buildReferenceLocationHtml(
    ref: SymbolGraphService.Reference,
    sourceSymbol: SymbolGraphService.Symbol?
  ): String {
    val parts = mutableListOf<String>()
    // Build containment path for the source symbol
    if (sourceSymbol != null) {
      val containmentPath = buildContainmentPath(sourceSymbol)
      if (containmentPath.isNotEmpty()) {
        parts.add("in $containmentPath")
      }
    }
    if (ref.file != null) {
      val fileName = ref.file.substringAfterLast("/")
      parts.add("in <a href=\"${urlBase}file/${escapeHtml(urlEncode(ref.file))}\">${escapeHtml(fileName)}</a>")
    }
    if (ref.line != null) {
      parts.add("line ${ref.line}")
    }
    if (ref.startOffset != null && ref.endOffset != null) {
      parts.add("offset ${ref.startOffset}-${ref.endOffset}")
    }
    return if (parts.isNotEmpty()) {
      "<small class='reference-location'>↳ ${parts.joinToString(", ")}</small>"
    } else ""
  }

  private fun buildContainmentPath(symbol: SymbolGraphService.Symbol): String {
    val path = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    visited.add(symbol.id)
    var current = symbol.containedBy()
    while (current != null && current.id !in visited) {
      visited.add(current.id)
      val name = current.name ?: current.id
      val link = "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(current.id))}\">${escapeHtml(name)}</a>"
      path.add(0, link)
      current = current.containedBy()
    }
    return if (path.isNotEmpty()) {
      path.joinToString(" → ")
    } else ""
  }

  private fun writeTransitiveResultHtml(
    resp: HttpServletResponse,
    result: SymbolGraphService.TransitiveResult,
    rootSymbolId: String,
    title: String,
    direction: String
  ) {
    val rootSymbol = service.getSymbol(rootSymbolId)
    val rootName = rootSymbol?.name ?: rootSymbolId
    val statsRows = listOf(
      listOf(
        "Root Symbol",
        "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(rootSymbolId))}\">${escapeHtml(rootName)}</a>"
      ),
      listOf("Total Symbols Found", result.totalFound.toString()),
      listOf("Maximum Depth", result.maxDepth.toString()),
      listOf("Results Truncated", if (result.truncated) "Yes (${result.remainingInQueue} more in queue)" else "No")
    )
    val statsHtml = """
            <div class='card'>
                <h3>📊 Statistics</h3>
                ${renderTable(listOf("Metric", "Value"), statsRows, "stats-table")}
                ${if (result.truncated) "<p class='error'>⚠️ Results were truncated. Use <code>?limit=N</code> to increase the limit.</p>" else ""}
            </div>
        """.trimIndent()
    // Group symbols by node type for better organization
    val symbolsByType = result.symbols.groupBy { it.nodeType ?: "Unknown" }
    val groupedSymbolsHtml = buildString {
      append("<div class='card'><h3>📦 Symbols by Type</h3>")
      for ((nodeType, symbols) in symbolsByType.entries.sortedBy { it.key }) {
        append("<details><summary><strong>${escapeHtml(nodeType)}</strong> (${symbols.size})</summary>")
        val typeRows = symbols.sortedBy { it.name ?: it.id }.map { symbol ->
          val badges = buildString {
            symbol.language()?.let { append("<span class='badge badge-language'>$it</span>") }
            symbol.visibility?.let { append("<span class='badge badge-visibility'>$it</span>") }
          }
          listOf(
            "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(symbol.id))}\">${escapeHtml(symbol.name ?: symbol.id)}</a>$badges",
            escapeHtml(symbol.fileId?.substringAfterLast('/') ?: ""),
            (symbol.line ?: "").toString()
          )
        }
        append(renderTable(listOf("Name", "File", "Line"), typeRows, "type-${escapeHtml(nodeType)}-table"))
        append("</details>")
      }
      append("</div>")
    }
    // Also provide a flat list grouped by file
    val symbolsByFile = result.symbols.groupBy { it.fileId ?: "Unknown" }
    val byFileHtml = buildString {
      append("<div class='card'><h3>📁 Symbols by File</h3>")
      for ((fileId, symbols) in symbolsByFile.entries.sortedBy { it.key }) {
        val fileName = fileId.substringAfterLast('/')
        append("<details><summary><strong>${escapeHtml(fileName)}</strong> (${symbols.size})</summary>")
        append("<p><small>Full path: ${escapeHtml(fileId)}</small></p>")
        val fileRows = symbols.sortedBy { it.line ?: Int.MAX_VALUE }.map { symbol ->
          val typeBadge = symbol.nodeType?.let { "<span class='badge badge-type'>$it</span>" } ?: ""
          listOf(
            "<a href=\"${urlBase}symbol?id=${escapeHtml(urlEncode(symbol.id))}\">${escapeHtml(symbol.name ?: symbol.id)}</a>$typeBadge",
            symbol.nodeType ?: "",
            (symbol.line ?: "").toString()
          )
        }
        append(renderTable(listOf("Symbol", "Type", "Line"), fileRows, "file-symbols-table"))
        append("</details>")
      }
      append("</div>")
    }
    val content = """
            <p><a href="${urlBase}symbol?id=${escapeHtml(urlEncode(rootSymbolId))}">← Back to ${escapeHtml(rootName)}</a></p>
            $statsHtml
            $groupedSymbolsHtml
            $byFileHtml
        """.trimIndent()
    writeHtmlPage(resp, "$title: $rootName", content)
  }
}