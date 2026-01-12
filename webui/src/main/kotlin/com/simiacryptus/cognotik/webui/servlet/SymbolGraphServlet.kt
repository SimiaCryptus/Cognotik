package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.apps.SymbolGraphService
import com.simiacryptus.cognotik.util.LoggerFactory
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class SymbolGraphServlet(private val service: SymbolGraphService) : HttpServlet() {

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.pathInfo ?: "/"
        log.info("SymbolGraphServlet GET $path")

        resp.contentType = "application/json"
        resp.characterEncoding = "UTF-8"

        try {
            when {
                path == "/search" -> {
                    val query = req.getParameter("q")
                    if (query.isNullOrBlank()) {
                        resp.writer.write("[]")
                    } else {
                        val symbols = service.search(query)
                        writeSymbols(resp, symbols)
                    }
                }
                path == "/symbol" -> {
                    val id = req.getParameter("id")
                    if (id == null) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.writer.write("""{"error": "Missing id parameter"}""")
                    } else {
                        val symbol = service.getSymbol(id)
                        if (symbol == null) {
                            resp.status = HttpServletResponse.SC_NOT_FOUND
                            resp.writer.write("""{"error": "Symbol not found"}""")
                        } else {
                            writeSymbol(resp, symbol, detailed = true)
                        }
                    }
                }
                path == "/files" -> writeSymbols(resp, service.getFiles())
                path == "/languages" -> writeStrings(resp, service.listLanguages())
                path == "/libraries" -> writeStrings(resp, service.listLibraries())
                path == "/packages" -> writeStrings(resp, service.listPackages())

                path.startsWith("/file/") -> {
                    val fileId = path.substring("/file/".length)
                    writeSymbols(resp, service.getSymbolsByFile(fileId))
                }
                path.startsWith("/language/") -> {
                    val lang = path.substring("/language/".length)
                    writeSymbols(resp, service.getSymbolsByLanguage(lang))
                }
                path.startsWith("/library/") -> {
                    val lib = path.substring("/library/".length)
                    writeSymbols(resp, service.getSymbolsByLibrary(lib))
                }
                path.startsWith("/package/") -> {
                    val pkg = path.substring("/package/".length)
                    writeSymbols(resp, service.getSymbolsByPackage(pkg))
                }

                else -> {
                    resp.writer.write("""{
                        "endpoints": [
                            "/search?q=<query>",
                            "/symbol?id=<id>",
                            "/files",
                            "/languages",
                            "/libraries",
                            "/packages",
                            "/file/<fileId>",
                            "/language/<language>",
                            "/library/<library>",
                            "/package/<package>"
                        ]
                    }""")
                }
            }
        } catch (e: Exception) {
            log.error("Error in SymbolGraphServlet", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("""{"error": "${escapeJson(e.message ?: "Unknown error")}"}""")
        }
    }

    private fun writeSymbols(resp: HttpServletResponse, symbols: List<SymbolGraphService.Symbol>) {
        resp.writer.write("[")
        symbols.forEachIndexed { index, symbol ->
            if (index > 0) resp.writer.write(",")
            writeSymbol(resp, symbol, detailed = false)
        }
        resp.writer.write("]")
    }

    private fun writeSymbol(resp: HttpServletResponse, symbol: SymbolGraphService.Symbol, detailed: Boolean) {
        val props = symbol.properties.toMutableMap()
        if (detailed) {
            symbol.file()?.let { props["file_obj"] = it.properties }
            symbol.language()?.let { props["language"] = it }
            symbol.packageName()?.let { props["package"] = it }
            symbol.libraryName()?.let { props["library"] = it }
        }
        resp.writer.write(toJson(props))
    }

    private fun writeStrings(resp: HttpServletResponse, list: List<String>) {
        resp.writer.write("[")
        list.forEachIndexed { index, s ->
            if (index > 0) resp.writer.write(",")
            resp.writer.write("\"${escapeJson(s)}\"")
        }
        resp.writer.write("]")
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
                is Map<*, *> -> sb.append(toJson(v as Map<String, Any>))
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

    companion object {
        val log = LoggerFactory.getLogger(SymbolGraphServlet::class.java)
    }
}